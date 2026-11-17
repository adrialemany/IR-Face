package org.irface;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;

public class FaceIconView extends View {
    public static final int HIDDEN = 0, SCANNING = 1, FAIL = 2, SUCCESS = 3;

    public interface Listener { void onFailAnimationDone(); }

    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path mouth = new Path();
    private final Path check = new Path();
    private final Path checkPart = new Path();
    private final float density;
    private int sizePx, padPx;

    private float pulse = 1f;
    private float faceAlpha = 1f;
    private float sad = 0f;
    private float checkProgress = 0f;
    private float shakeX = 0f;
    private int state = HIDDEN;

    private ValueAnimator pulseAnim, mainAnim, shakeAnim;
    private Listener listener;

    public FaceIconView(Context c) {
        super(c);
        density = c.getResources().getDisplayMetrics().density;
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        stroke.setColor(Color.WHITE);
        stroke.setShadowLayer(dp(2), 0, 0, 0x80000000);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(Color.WHITE);
        fill.setShadowLayer(dp(2), 0, 0, 0x80000000);
        setLayerType(LAYER_TYPE_SOFTWARE, null);
        setSizeDp(60);
    }

    public void setSizeDp(int sizeDp) {
        sizePx = dp(sizeDp);
        padPx = Math.max(dp(8), sizePx / 4);
        stroke.setStrokeWidth(Math.max(dp(1.5f), sizePx / 20f));
        requestLayout();
        invalidate();
    }

    public void setListener(Listener l) { listener = l; }

    private int dp(float v) { return Math.round(v * density); }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(sizePx + 2 * padPx, sizePx + padPx);
    }

    public void setState(int s) {
        cancelAll();
        state = s;
        shakeX = 0f;
        switch (s) {
            case SCANNING:
                faceAlpha = 1f; sad = 0f; checkProgress = 0f;
                setAlpha(1f);
                pulseAnim = ValueAnimator.ofFloat(1f, 0.5f);
                pulseAnim.setDuration(650);
                pulseAnim.setRepeatMode(ValueAnimator.REVERSE);
                pulseAnim.setRepeatCount(ValueAnimator.INFINITE);
                pulseAnim.addUpdateListener(a -> { pulse = (float) a.getAnimatedValue(); invalidate(); });
                pulseAnim.start();
                break;

            case FAIL:
                pulse = 1f; faceAlpha = 1f; checkProgress = 0f;
                setAlpha(1f);
                mainAnim = ValueAnimator.ofFloat(0f, 1f);
                mainAnim.setDuration(220);
                mainAnim.addUpdateListener(a -> { sad = (float) a.getAnimatedValue(); invalidate(); });
                mainAnim.start();
                float d = sizePx / 6f;
                shakeAnim = ValueAnimator.ofFloat(0, -d, d, -0.8f * d, 0.8f * d, -0.5f * d, 0.5f * d, -0.2f * d, 0);
                shakeAnim.setDuration(700);
                shakeAnim.setInterpolator(new LinearInterpolator());
                shakeAnim.addUpdateListener(a -> { shakeX = (float) a.getAnimatedValue(); invalidate(); });
                shakeAnim.addListener(new AnimatorListenerAdapter() {
                    @Override public void onAnimationEnd(Animator a) {
                        if (state != FAIL) return;
                        animate().alpha(0f).setStartDelay(900).setDuration(300).withEndAction(() -> {
                            if (state == FAIL && listener != null) listener.onFailAnimationDone();
                        }).start();
                    }
                });
                shakeAnim.start();
                break;

            case SUCCESS:
                pulse = 1f; sad = 0f;
                setAlpha(1f);
                mainAnim = ValueAnimator.ofFloat(0f, 1f);
                mainAnim.setDuration(520);
                mainAnim.setInterpolator(new DecelerateInterpolator());
                mainAnim.addUpdateListener(a -> {
                    float t = (float) a.getAnimatedValue();
                    faceAlpha = Math.max(0f, 1f - t * 3f);
                    checkProgress = Math.max(0f, Math.min(1f, (t - 0.25f) / 0.75f));
                    invalidate();
                });
                mainAnim.start();
                break;

            default:
                faceAlpha = 1f; sad = 0f; checkProgress = 0f; pulse = 1f;
        }
        invalidate();
    }

    private void cancelAll() {
        if (pulseAnim != null) pulseAnim.cancel();
        if (mainAnim != null) mainAnim.cancel();
        if (shakeAnim != null) shakeAnim.cancel();
        animate().cancel();
        pulseAnim = mainAnim = shakeAnim = null;
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelAll();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float cx = getWidth() / 2f + shakeX;
        float cy = padPx / 2f + sizePx / 2f;
        float r = sizePx * 0.42f;
        int base = Math.round(255 * pulse);

        stroke.setAlpha(base);
        canvas.drawCircle(cx, cy, r, stroke);

        if (faceAlpha > 0f) {
            int a = Math.round(base * faceAlpha);
            fill.setAlpha(a);
            stroke.setAlpha(a);
            float eyeY = cy - r * 0.22f;
            canvas.drawCircle(cx - r * 0.36f, eyeY, r * 0.09f, fill);
            canvas.drawCircle(cx + r * 0.36f, eyeY, r * 0.09f, fill);
            float my = cy + r * 0.32f;
            float ctrl = my + r * 0.30f * (1f - 2f * sad);
            mouth.reset();
            mouth.moveTo(cx - r * 0.38f, my);
            mouth.quadTo(cx, ctrl, cx + r * 0.38f, my);
            canvas.drawPath(mouth, stroke);
        }

        if (checkProgress > 0f) {
            check.reset();
            check.moveTo(cx - r * 0.45f, cy + r * 0.02f);
            check.lineTo(cx - r * 0.12f, cy + r * 0.36f);
            check.lineTo(cx + r * 0.50f, cy - r * 0.30f);
            PathMeasure pm = new PathMeasure(check, false);
            checkPart.reset();
            pm.getSegment(0, pm.getLength() * checkProgress, checkPart, true);
            stroke.setAlpha(255);
            canvas.drawPath(checkPart, stroke);
        }
        stroke.setAlpha(255);
    }
}
