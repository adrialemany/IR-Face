package org.irface;

import android.Manifest;
import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.imgcodecs.Imgcodecs;
import org.opencv.imgproc.Imgproc;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements IrCamera.Listener {
    private static final String TAG = "IRFACE";
    private static final int ENROLL_SAMPLES = 15;
    private static final long SESSION_TIMEOUT_MS = 25_000;
    private static final float MIN_DET_SCORE = 0.85f;
    private static final int MIN_FACE_PX = 110;

    enum Mode { IDLE, ENROLL, VERIFY }

    private ImageView preview;
    private TextView status;
    private TextView detail;
    private Button rotButton, thrLabel;

    private HandlerThread camThread;
    private Handler camHandler;
    private IrCamera camera;
    private FaceEngine engine;
    private final IrLed led = IrLed.get();

    private volatile Mode mode = Mode.IDLE;
    private volatile int rotation = -1;
    private volatile double threshold = Store.DEFAULT_THRESHOLD;
    private volatile boolean saveNext = false;
    private volatile long modeStart = 0;

    private List<float[]> enrolled = new ArrayList<>();
    private final List<float[]> enrollBuffer = new ArrayList<>();
    private int frameCount = 0;
    private int consecutiveMatches = 0;
    private double bestScoreSession = -1;

    private static String fmt(double v) { return String.format(Locale.US, "%.3f", v); }
    private static String fmt2(double v) { return String.format(Locale.US, "%.2f", v); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        rotation = Store.rotation(this);
        threshold = Store.threshold(this);
        buildUi();

        if (!OpenCVLoader.initLocal()) {
            setStatus(getString(R.string.status_opencv_fail), Color.RED);
            return;
        }
        try {
            engine = FaceEngine.get(this);
        } catch (Exception e) {
            setStatus(e.getMessage(), Color.RED);
            Log.e(TAG, "Modelos", e);
            return;
        }
        camThread = new HandlerThread("ircam-ui");
        camThread.start();
        camHandler = new Handler(camThread.getLooper());
        camHandler.post(() -> {
            enrolled = Store.loadEnrolled(this);
            boolean root = led.init();
            runOnUiThread(() -> setDetail(getString(root ? R.string.detail_root_ok : R.string.detail_no_root)));
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (engine == null) return;
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, 1);
            return;
        }
        startCamera();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            setStatus(getString(R.string.status_no_camera_perm), Color.RED);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (camHandler != null) {
            camHandler.post(() -> {
                setMode(Mode.IDLE);
                if (camera != null) camera.close();
                camera = null;
            });
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (camThread != null) camThread.quitSafely();
    }

    private void startCamera() {
        camHandler.post(() -> {
            if (camera != null) return;
            if (FaceAuthenticator.get(this).isRunning()) FaceAuthenticator.get(this).cancel();
            camera = new IrCamera(this, camHandler, this);
            camera.open(Store.CAMERA_ID);
        });
    }

    private void setMode(Mode m) {
        mode = m;
        modeStart = SystemClock.elapsedRealtime();
        consecutiveMatches = 0;
        bestScoreSession = -1;
        if (m == Mode.ENROLL) enrollBuffer.clear();
        led.set(m != Mode.IDLE);
    }

    @Override
    public void onFrame(Mat gray) {
        long t0 = SystemClock.elapsedRealtime();
        frameCount++;

        if (mode != Mode.IDLE && t0 - modeStart > SESSION_TIMEOUT_MS) {
            Mode was = mode;
            double best = bestScoreSession;
            setMode(Mode.IDLE);
            postStatus(was == Mode.ENROLL
                    ? getString(R.string.enroll_cancelled)
                    : getString(R.string.verify_end, fmt(best)),
                    Color.YELLOW);
        }

        int rot = rotation >= 0 ? rotation : (camera != null ? camera.getSensorOrientation() : 0);
        Mat up = FaceAuthenticator.rotate(gray, rot);
        Mat img = new Mat();
        Imgproc.cvtColor(up, img, Imgproc.COLOR_GRAY2RGB);

        if (saveNext) {
            saveNext = false;
            saveImage(up);
        }

        List<FaceEngine.Face> faces = engine.detect(img);
        FaceEngine.Face main = faces.isEmpty() ? null : faces.get(0);
        double score = Double.NaN;
        Scalar boxColor = new Scalar(255, 255, 0);

        if (main != null && mode == Mode.ENROLL) {
            boolean good = faces.size() == 1 && main.score >= MIN_DET_SCORE && main.w >= MIN_FACE_PX;
            if (good && frameCount % 3 == 0) {
                enrollBuffer.add(engine.embedding(img, main));
                postStatus(getString(R.string.enrolling_progress, enrollBuffer.size(), ENROLL_SAMPLES), Color.CYAN);
                if (enrollBuffer.size() >= ENROLL_SAMPLES) {
                    enrolled.addAll(enrollBuffer);
                    Store.saveEnrolled(this, enrolled);
                    setMode(Mode.IDLE);
                    postStatus(getString(R.string.enrolled_done, enrolled.size()), Color.GREEN);
                }
            } else if (!good) {
                postStatus(getString(faces.size() > 1 ? R.string.enroll_multi_face : R.string.enroll_center), Color.YELLOW);
            }
            boxColor = new Scalar(0, 200, 255);
        } else if (main != null && mode == Mode.VERIFY) {
            if (enrolled.isEmpty()) {
                setMode(Mode.IDLE);
                postStatus(getString(R.string.verify_need_enroll), Color.YELLOW);
            } else {
                float[] e = engine.embedding(img, main);
                score = -1;
                for (float[] ref : enrolled) score = Math.max(score, FaceEngine.cosine(e, ref));
                bestScoreSession = Math.max(bestScoreSession, score);
                boolean match = score >= threshold;
                consecutiveMatches = match ? consecutiveMatches + 1 : 0;
                boxColor = match ? new Scalar(0, 255, 0) : new Scalar(255, 0, 0);
                Log.i(TAG, String.format(Locale.US, "verify score=%.4f det=%.2f w=%.0f thr=%.2f",
                        score, main.score, main.w, threshold));
                postStatus(consecutiveMatches >= 2
                                ? getString(R.string.recognized, fmt(score), fmt2(threshold))
                                : getString(R.string.not_match, fmt(score), fmt2(threshold)),
                        consecutiveMatches >= 2 ? Color.GREEN : Color.RED);
            }
        } else if (main == null && mode != Mode.IDLE) {
            consecutiveMatches = 0;
            postStatus(getString(mode == Mode.ENROLL ? R.string.enroll_no_face : R.string.verify_no_face), Color.YELLOW);
        }

        for (FaceEngine.Face f : faces) {
            Imgproc.rectangle(img, new Point(f.x, f.y), new Point(f.x + f.w, f.y + f.h),
                    f == main ? boxColor : new Scalar(128, 128, 128), 2);
            for (int i = 0; i < 5; i++) {
                Imgproc.circle(img, new Point(f.landmarks[2 * i], f.landmarks[2 * i + 1]), 3,
                        new Scalar(255, 0, 255), -1);
            }
        }
        if (!Double.isNaN(score) && main != null) {
            Imgproc.putText(img, fmt(score),
                    new Point(main.x, Math.max(20, main.y - 8)), Imgproc.FONT_HERSHEY_SIMPLEX, 0.8, boxColor, 2);
        }
        long dt = SystemClock.elapsedRealtime() - t0;
        // Línea de diagnóstico (técnica): se deja sin traducir a propósito.
        final String info = String.format(Locale.US, "rot %d°  faces %d  det %.2f  %d ms  LED %s  samples %d",
                rot, faces.size(), main != null ? main.score : 0f, dt,
                led.isAvailable() ? (led.isOn() ? "ON" : "off") : "no root", enrolled.size());

        Bitmap bmp = Bitmap.createBitmap(img.cols(), img.rows(), Bitmap.Config.ARGB_8888);
        Utils.matToBitmap(img, bmp);
        for (FaceEngine.Face f : faces) f.release();
        img.release();
        if (up != gray) up.release();

        runOnUiThread(() -> {
            preview.setImageBitmap(bmp);
            if (mode == Mode.IDLE || frameCount % 5 == 0) detail.setText(info);
        });
    }

    @Override
    public void onCameraError(String message) {
        Log.e(TAG, message);
        postStatus(message, Color.RED);
    }

    @Override
    public void onCameraInfo(String message) {
        Log.i(TAG, message);
        postStatus(message + "\n" + getString(R.string.ready), Color.WHITE);
    }

    private void saveImage(Mat up) {
        File dir = getExternalFilesDir(null);
        String name = "ir_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".png";
        File f = new File(dir, name);
        boolean ok = Imgcodecs.imwrite(f.getAbsolutePath(), up);
        postStatus(ok ? getString(R.string.img_saved, f.getAbsolutePath()) : getString(R.string.img_save_fail),
                ok ? Color.GREEN : Color.RED);
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setFitsSystemWindows(true);

        preview = new ImageView(this);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(preview, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        status.setPadding(24, 12, 24, 4);
        status.setText(getString(R.string.status_starting));
        root.addView(status);

        detail = new TextView(this);
        detail.setTextColor(Color.LTGRAY);
        detail.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        detail.setPadding(24, 0, 24, 12);
        root.addView(detail);

        LinearLayout row1 = row(root);
        addButton(row1, getString(R.string.btn_enroll), v -> post(() -> {
            setMode(Mode.ENROLL);
            postStatus(getString(R.string.status_enrolling), Color.CYAN);
        }));
        addButton(row1, getString(R.string.btn_verify), v -> post(() -> {
            setMode(Mode.VERIFY);
            postStatus(getString(R.string.status_verifying), Color.CYAN);
        }));
        addButton(row1, getString(R.string.btn_stop), v -> post(() -> {
            setMode(Mode.IDLE);
            postStatus(getString(R.string.status_stopped), Color.WHITE);
        }));

        LinearLayout row2 = row(root);
        rotButton = addButton(row2, rotLabel(), v -> {
            int r = rotation < 0 ? 0 : (rotation + 90) % 360;
            if (rotation == 270) r = -1; // vuelve a automático
            rotation = r;
            Store.setRotation(this, r);
            rotButton.setText(rotLabel());
        });
        addButton(row2, getString(R.string.btn_threshold_minus), v -> changeThreshold(-0.02));
        thrLabel = addButton(row2, thrText(), v -> { });
        addButton(row2, getString(R.string.btn_threshold_plus), v -> changeThreshold(+0.02));

        LinearLayout row3 = row(root);
        addButton(row3, getString(R.string.btn_save_image), v -> saveNext = true);
        addButton(row3, getString(R.string.btn_clear_enroll), v -> post(() -> {
            enrolled.clear();
            Store.clearEnrolled(this);
            postStatus(getString(R.string.status_enroll_cleared), Color.YELLOW);
        }));
        addButton(row3, getString(R.string.btn_trust_agents), v -> openTrustAgents());

        addCheck(root, getString(R.string.chk_require_swipe), Store.requireSwipe(this),
                (b, on) -> Store.setRequireSwipe(this, on));
        addCheck(root, getString(R.string.chk_show_messages), Store.showMessages(this),
                (b, on) -> Store.setShowMessages(this, on));
        addCheck(root, getString(R.string.chk_animated_icon), Store.indicatorEnabled(this), (b, on) -> {
            if (revertingCheck) return;
            if (!Store.setIndicatorEnabled(this, on)) {
                revertingCheck = true;
                b.setChecked(!on);
                revertingCheck = false;
                setStatus(getString(R.string.status_icon_change_fail), Color.RED);
            } else {
                setStatus(getString(on ? R.string.status_icon_on : R.string.status_icon_off), Color.WHITE);
            }
        });

        LinearLayout row4 = row(root);
        addButton(row4, getString(R.string.btn_test_icon), v -> demoIcon());
        addButton(row4, getString(R.string.btn_icon_up), v -> moveIcon(-8));
        addButton(row4, getString(R.string.btn_icon_down), v -> moveIcon(+8));

        LinearLayout row5 = row(root);
        addButton(row5, getString(R.string.btn_size_minus), v -> resizeIcon(-6));
        addButton(row5, getString(R.string.btn_size_plus), v -> resizeIcon(+6));

        setContentView(root);
    }

    private void addCheck(LinearLayout parent, String text, boolean checked,
                          android.widget.CompoundButton.OnCheckedChangeListener l) {
        android.widget.CheckBox cb = new android.widget.CheckBox(this);
        cb.setText(text);
        cb.setTextColor(Color.WHITE);
        cb.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        cb.setChecked(checked);
        cb.setPadding(16, 0, 16, 0);
        cb.setOnCheckedChangeListener(l);
        parent.addView(cb);
    }

    private final Handler uiHandler = new Handler(android.os.Looper.getMainLooper());
    private boolean revertingCheck = false;

    private void demoIcon() {
        if (!IndicatorService.isRunning()) {
            setStatus(getString(R.string.status_enable_icon_first), Color.YELLOW);
            return;
        }
        uiHandler.removeCallbacksAndMessages(null);
        IndicatorService.demo(FaceIconView.HIDDEN);
        uiHandler.postDelayed(() -> IndicatorService.demo(FaceIconView.SCANNING), 100);
        uiHandler.postDelayed(() -> IndicatorService.demo(FaceIconView.FAIL), 1600);
        uiHandler.postDelayed(() -> IndicatorService.demo(FaceIconView.SCANNING), 4000);
        uiHandler.postDelayed(() -> IndicatorService.demo(FaceIconView.SUCCESS), 5400);
        uiHandler.postDelayed(() -> IndicatorService.demo(FaceIconView.HIDDEN), 7500);
    }

    private void resizeIcon(int deltaDp) {
        int v = Math.max(24, Math.min(120, Store.iconSizeDp(this) + deltaDp));
        Store.setIconSizeDp(this, v);
        setStatus(getString(R.string.status_icon_size, v), Color.WHITE);
        demoIcon();
    }

    private void moveIcon(int deltaDp) {
        int v = Math.max(0, Math.min(400, Store.iconOffsetDp(this) + deltaDp));
        Store.setIconOffsetDp(this, v);
        setStatus(getString(R.string.status_icon_pos, v), Color.WHITE);
        demoIcon();
    }

    private void openTrustAgents() {
        Intent i = new Intent().setComponent(new ComponentName("com.android.settings",
                "com.android.settings.Settings$TrustAgentSettingsActivity"));
        try {
            startActivity(i);
        } catch (Exception e) {
            startActivity(new Intent(Settings.ACTION_SECURITY_SETTINGS));
        }
    }

    private String rotLabel() {
        return rotation < 0 ? getString(R.string.rot_auto) : getString(R.string.rot_deg, rotation);
    }
    private String thrText() { return fmt2(threshold); }

    private void changeThreshold(double d) {
        threshold = Math.max(0.10, Math.min(0.95, threshold + d));
        Store.setThreshold(this, threshold);
        thrLabel.setText(thrText());
    }

    private LinearLayout row(LinearLayout parent) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER);
        parent.addView(r, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    private Button addButton(LinearLayout row, String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        b.setOnClickListener(l);
        row.addView(b, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return b;
    }

    private void post(Runnable r) { if (camHandler != null) camHandler.post(r); }

    private void setStatus(String s, int color) { status.setText(s); status.setTextColor(color); }
    private void setDetail(String s) { detail.setText(s); }
    private void postStatus(String s, int color) { runOnUiThread(() -> setStatus(s, color)); }
}
