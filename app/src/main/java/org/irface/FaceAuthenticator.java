package org.irface;

import android.content.Context;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.imgproc.Imgproc;

import java.util.List;
import java.util.Locale;

public class FaceAuthenticator implements IrCamera.Listener {
    private static final String TAG = "IRFACE";
    private static final int REQUIRED_CONSECUTIVE = 2;
    private static final float MIN_DET_SCORE = 0.80f;

    public interface Callback {
        void onResult(boolean success, double bestScore, String reason);
    }

    private static FaceAuthenticator sInstance;

    public static synchronized FaceAuthenticator get(Context ctx) {
        if (sInstance == null) sInstance = new FaceAuthenticator(ctx.getApplicationContext());
        return sInstance;
    }

    private final Context app;
    private final Handler handler;
    private final Handler main = new Handler(Looper.getMainLooper());

    private IrCamera camera;
    private Callback callback;
    private boolean running = false;
    private List<float[]> refs;
    private double threshold;
    private int rotationPref;
    private int consecutive;
    private double best;
    private long startedAt;
    private int frames;
    private FaceEngine engine;

    private final Runnable timeout = () -> finish(false, "tiempo agotado");

    private FaceAuthenticator(Context app) {
        this.app = app;
        HandlerThread t = new HandlerThread("irface-auth");
        t.start();
        handler = new Handler(t.getLooper());
    }

    public void preload() {
        handler.post(() -> {
            ensureEngine();
            IrLed.get().init();
        });
    }

    public boolean isRunning() { return running; }

    public void start(long timeoutMs, Callback cb) {
        handler.post(() -> {
            if (running) {
                Log.i(TAG, "Ya hay un intento en curso");
                return;
            }
            if (!ensureEngine()) {
                deliver(cb, false, -1, "no se pudieron cargar los modelos");
                return;
            }
            refs = Store.loadEnrolled(app);
            if (refs.isEmpty()) {
                deliver(cb, false, -1, "no hay ninguna cara registrada");
                return;
            }
            threshold = Store.threshold(app);
            rotationPref = Store.rotation(app);
            callback = cb;
            running = true;
            consecutive = 0;
            best = -1;
            frames = 0;
            startedAt = SystemClock.elapsedRealtime();
            IrLed led = IrLed.get();
            if (!led.isAvailable()) led.init();
            led.set(true);
            camera = new IrCamera(app, handler, this);
            camera.open(Store.CAMERA_ID);
            handler.postDelayed(timeout, timeoutMs);
            Log.i(TAG, "Intento de desbloqueo iniciado (umbral " + threshold + ")");
        });
    }

    public void cancel() {
        handler.post(() -> {
            if (running) finish(false, "cancelado");
        });
    }

    private boolean ensureEngine() {
        if (engine != null) return true;
        try {
            if (!OpenCVLoader.initLocal()) return false;
            engine = FaceEngine.get(app);
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Error cargando modelos", e);
            return false;
        }
    }

    @Override
    public void onFrame(Mat gray) {
        if (!running) return;
        frames++;
        int rot = rotationPref >= 0 ? rotationPref : camera.getSensorOrientation();
        Mat up = rotate(gray, rot);
        Mat img = new Mat();
        Imgproc.cvtColor(up, img, Imgproc.COLOR_GRAY2RGB);
        List<FaceEngine.Face> faces = engine.detect(img);
        if (!faces.isEmpty() && faces.get(0).score >= MIN_DET_SCORE) {
            float[] e = engine.embedding(img, faces.get(0));
            double s = -1;
            for (float[] r : refs) s = Math.max(s, FaceEngine.cosine(e, r));
            best = Math.max(best, s);
            consecutive = s >= threshold ? consecutive + 1 : 0;
            Log.i(TAG, String.format(Locale.US, "auth frame=%d score=%.4f consec=%d", frames, s, consecutive));
        } else {
            consecutive = 0;
        }
        for (FaceEngine.Face f : faces) f.release();
        img.release();
        if (up != gray) up.release();
        if (consecutive >= REQUIRED_CONSECUTIVE) finish(true, "reconocido");
    }

    @Override
    public void onCameraError(String message) {
        Log.e(TAG, message);
        if (running) finish(false, message);
    }

    @Override
    public void onCameraInfo(String message) {
        Log.i(TAG, message);
    }

    private void finish(boolean ok, String reason) {
        if (!running) return;
        running = false;
        handler.removeCallbacks(timeout);
        if (camera != null) camera.close();
        camera = null;
        IrLed.get().set(false);
        long ms = SystemClock.elapsedRealtime() - startedAt;
        Log.i(TAG, String.format(Locale.US, "Intento terminado: %s (%s), mejor=%.4f, %d fotogramas, %d ms",
                ok ? "OK" : "fallo", reason, best, frames, ms));
        Callback cb = callback;
        callback = null;
        deliver(cb, ok, best, reason);
    }

    private void deliver(Callback cb, boolean ok, double bestScore, String reason) {
        if (cb != null) main.post(() -> cb.onResult(ok, bestScore, reason));
    }

    static Mat rotate(Mat src, int deg) {
        int d = ((deg % 360) + 360) % 360;
        if (d == 0) return src;
        Mat dst = new Mat();
        if (d == 90) Core.rotate(src, dst, Core.ROTATE_90_CLOCKWISE);
        else if (d == 180) Core.rotate(src, dst, Core.ROTATE_180);
        else Core.rotate(src, dst, Core.ROTATE_90_COUNTERCLOCKWISE);
        return dst;
    }
}
