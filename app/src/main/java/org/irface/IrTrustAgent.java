package org.irface;

import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.service.trust.TrustAgentService;
import android.util.Log;

public class IrTrustAgent extends TrustAgentService {
    private static final String TAG = "IRFACE";
    private static final long ATTEMPT_TIMEOUT_MS = 3000;
    private static final long MIN_GAP_MS = 1200;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static volatile IrTrustAgent sInstance;

    private BroadcastReceiver screenReceiver;
    private long lastAttempt = 0;
    private boolean awaitingSwipe = false;

    public static void requestRetry() {
        MAIN.post(() -> {
            IrTrustAgent a = sInstance;
            if (a != null) a.maybeAttempt("toque en la carita");
        });
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        Log.i(TAG, "Agente de confianza creado");
        setManagingTrust(true);
        FaceAuthenticator.get(this).preload();

        screenReceiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                if (Intent.ACTION_SCREEN_ON.equals(i.getAction())) {
                    maybeAttempt("pantalla encendida");
                } else if (Intent.ACTION_SCREEN_OFF.equals(i.getAction())) {
                    awaitingSwipe = false;
                    FaceAuthenticator.get(IrTrustAgent.this).cancel();
                    IndicatorService.set(FaceIconView.HIDDEN);
                } else if (Intent.ACTION_USER_PRESENT.equals(i.getAction())) {
                    awaitingSwipe = false;
                    FaceAuthenticator.get(IrTrustAgent.this).cancel();
                    IndicatorService.set(FaceIconView.HIDDEN);
                    grantRenewable("pantalla de bloqueo quitada");
                }
            }
        };
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_ON);
        f.addAction(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(screenReceiver, f, Context.RECEIVER_NOT_EXPORTED);

        KeyguardManager km = getSystemService(KeyguardManager.class);
        if (km != null && !km.isDeviceLocked()) grantRenewable("agente iniciado con el teléfono desbloqueado");
    }

    @Override
    public void onDestroy() {
        Log.i(TAG, "Agente de confianza destruido");
        sInstance = null;
        if (screenReceiver != null) unregisterReceiver(screenReceiver);
        FaceAuthenticator.get(this).cancel();
        IndicatorService.set(FaceIconView.HIDDEN);
        super.onDestroy();
    }

    @Override
    public void onDeviceUnlocked() {
        KeyguardManager km = getSystemService(KeyguardManager.class);
        if (awaitingSwipe && km != null && km.isKeyguardLocked()) {
            Log.i(TAG, "Desbloqueado por confianza, esperando a que deslices");
            return;
        }
        awaitingSwipe = false;
        FaceAuthenticator.get(this).cancel();
        IndicatorService.set(FaceIconView.HIDDEN);
        grantRenewable("teléfono desbloqueado");
    }

    @Override
    public void onDeviceLocked() {
        awaitingSwipe = false;
        Log.i(TAG, "Teléfono bloqueado");
    }

    @Override
    public void onUserRequestedUnlock(boolean dismissKeyguard) {
        maybeAttempt("petición del usuario");
    }

    @Override
    public void onUserMayRequestUnlock() {
        maybeAttempt("el usuario puede querer desbloquear");
    }

    @Override
    public void onTrustTimeout() {
        Log.i(TAG, "La confianza ha caducado");
    }

    private void grantRenewable(String why) {
        try {
            grantTrust("IR Face", 0, FLAG_GRANT_TRUST_TEMPORARY_AND_RENEWABLE);
            Log.i(TAG, "Confianza renovable concedida (" + why + ")");
        } catch (Throwable t) {
            Log.e(TAG, "grantTrust falló", t);
        }
    }

    private void keyguardMessage(String msg) {
        if (!Store.showMessages(this)) return;
        try {
            showKeyguardErrorMessage(msg);
        } catch (Throwable t) {
            Log.w(TAG, "showKeyguardErrorMessage no disponible: " + t);
        }
    }

    private void maybeAttempt(String why) {
        KeyguardManager km = getSystemService(KeyguardManager.class);
        PowerManager pm = getSystemService(PowerManager.class);
        if (km == null || !km.isDeviceLocked()) return;
        if (pm != null && !pm.isInteractive()) return;
        long now = SystemClock.elapsedRealtime();
        if (now - lastAttempt < MIN_GAP_MS || FaceAuthenticator.get(this).isRunning()) return;
        lastAttempt = now;
        Log.i(TAG, "Intentando desbloqueo facial: " + why);
        MAIN.postDelayed(() -> {
            KeyguardManager k = getSystemService(KeyguardManager.class);
            if (FaceAuthenticator.get(this).isRunning() && k != null && k.isDeviceLocked()) {
                IndicatorService.set(FaceIconView.SCANNING);
            }
        }, 300);

        FaceAuthenticator.get(this).start(ATTEMPT_TIMEOUT_MS, (ok, best, reason) -> {
            if (!ok) {
                KeyguardManager k0 = getSystemService(KeyguardManager.class);
                boolean stillLocked = k0 != null && k0.isDeviceLocked();
                if ("cancelado".equals(reason) || !stillLocked) {
                    IndicatorService.set(FaceIconView.HIDDEN);
                } else {
                    IndicatorService.set(FaceIconView.FAIL);
                    keyguardMessage(getString(R.string.msg_face_not_recognized));
                }
                return;
            }
            KeyguardManager k = getSystemService(KeyguardManager.class);
            if (k != null && !k.isDeviceLocked()) {
                IndicatorService.set(FaceIconView.HIDDEN);
                return;
            }
            boolean swipe = Store.requireSwipe(this);
            int flags = FLAG_GRANT_TRUST_TEMPORARY_AND_RENEWABLE;
            if (!swipe) flags |= FLAG_GRANT_TRUST_DISMISS_KEYGUARD;
            if (Store.showMessages(this)) flags |= FLAG_GRANT_TRUST_DISPLAY_MESSAGE;
            awaitingSwipe = swipe;
            try {
                grantTrust(getString(swipe ? R.string.msg_face_recognized_swipe : R.string.msg_face_unlocked), 0, flags);
                IndicatorService.set(FaceIconView.SUCCESS);
                Log.i(TAG, "Confianza concedida (" + (swipe ? "esperando deslizar" : "quitando el bloqueo") + ")");
            } catch (Throwable t) {
                awaitingSwipe = false;
                IndicatorService.set(FaceIconView.HIDDEN);
                Log.e(TAG, "grantTrust falló", t);
            }
        });
    }
}
