package org.irface;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;
import android.view.Gravity;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

public class IndicatorService extends AccessibilityService {
    private static final String TAG = "IRFACE";
    private static final long WATCHDOG_MS = 120;
    private static volatile IndicatorService sInstance;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private WindowManager wm;
    private KeyguardManager km;
    private PowerManager pm;
    private FaceIconView view;
    private boolean attached = false;
    private boolean demo = false;
    private BroadcastReceiver receiver;

    public static void set(int state) {
        MAIN.post(() -> {
            IndicatorService s = sInstance;
            if (s == null) return;
            s.demo = false;
            s.apply(state);
        });
    }

    public static void demo(int state) {
        MAIN.post(() -> {
            IndicatorService s = sInstance;
            if (s == null) return;
            s.demo = state != FaceIconView.HIDDEN;
            s.apply(state);
        });
    }

    public static boolean isRunning() { return sInstance != null; }

    private final Runnable watchdog = new Runnable() {
        @Override public void run() {
            if (!attached) return;
            if (!demo && !onLockscreen()) {
                apply(FaceIconView.HIDDEN);
                return;
            }
            MAIN.postDelayed(this, WATCHDOG_MS);
        }
    };

    private boolean onLockscreen() {
        return km != null && km.isKeyguardLocked() && (pm == null || pm.isInteractive());
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        wm = getSystemService(WindowManager.class);
        km = getSystemService(KeyguardManager.class);
        pm = getSystemService(PowerManager.class);
        view = new FaceIconView(this);
        view.setListener(() -> apply(FaceIconView.HIDDEN));
        view.setOnClickListener(v -> IrTrustAgent.requestRetry());
        receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                demo = false;
                apply(FaceIconView.HIDDEN);
            }
        };
        IntentFilter f = new IntentFilter(Intent.ACTION_SCREEN_OFF);
        f.addAction(Intent.ACTION_USER_PRESENT);
        registerReceiver(receiver, f, Context.RECEIVER_NOT_EXPORTED);
        sInstance = this;
        Log.i(TAG, "Indicador de pantalla de bloqueo activo");
    }

    private void apply(int state) {
        if (state != FaceIconView.HIDDEN && !demo && !onLockscreen()) {
            state = FaceIconView.HIDDEN;
        }
        if (state == FaceIconView.HIDDEN) {
            MAIN.removeCallbacks(watchdog);
            if (attached) {
                try { wm.removeViewImmediate(view); } catch (Exception ignored) { }
                attached = false;
            }
            view.setState(FaceIconView.HIDDEN);
            return;
        }
        if (!attached) {
            view.setSizeDp(Store.iconSizeDp(this));
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.y = Math.round(getResources().getDisplayMetrics().density * Store.iconOffsetDp(this));
            lp.setTitle("IrFaceIndicator");
            try {
                wm.addView(view, lp);
                attached = true;
            } catch (Exception e) {
                Log.e(TAG, "No se pudo mostrar el indicador", e);
                return;
            }
        }
        view.setState(state);
        MAIN.removeCallbacks(watchdog);
        MAIN.postDelayed(watchdog, WATCHDOG_MS);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (attached && !demo && !onLockscreen()) apply(FaceIconView.HIDDEN);
    }

    @Override public void onInterrupt() { }

    @Override
    public boolean onUnbind(Intent intent) {
        sInstance = null;
        demo = false;
        apply(FaceIconView.HIDDEN);
        if (receiver != null) {
            try { unregisterReceiver(receiver); } catch (Exception ignored) { }
            receiver = null;
        }
        Log.i(TAG, "Indicador de pantalla de bloqueo desactivado");
        return super.onUnbind(intent);
    }
}
