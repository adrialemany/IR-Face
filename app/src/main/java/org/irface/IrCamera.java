package org.irface;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.util.Log;
import android.util.Size;

import org.opencv.core.CvType;
import org.opencv.core.Mat;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;

public class IrCamera {
    private static final String TAG = "IRFACE";

    public interface Listener {
        void onFrame(Mat gray);
        void onCameraError(String message);
        void onCameraInfo(String message);
    }

    private final Context ctx;
    private final Handler handler;
    private final Listener listener;

    private CameraDevice device;
    private CameraCaptureSession session;
    private ImageReader reader;
    private int sensorOrientation = 0;
    private byte[] buffer;

    public IrCamera(Context ctx, Handler handler, Listener listener) {
        this.ctx = ctx;
        this.handler = handler;
        this.listener = listener;
    }

    public int getSensorOrientation() { return sensorOrientation; }

    @SuppressLint("MissingPermission")
    public void open(String cameraId) {
        CameraManager mgr = ctx.getSystemService(CameraManager.class);
        try {
            String[] ids = mgr.getCameraIdList();
            if (!Arrays.asList(ids).contains(cameraId)) {
                listener.onCameraError(ctx.getString(R.string.cam_not_visible, cameraId, String.join(",", ids)));
                return;
            }
            CameraCharacteristics ch = mgr.getCameraCharacteristics(cameraId);
            Integer so = ch.get(CameraCharacteristics.SENSOR_ORIENTATION);
            sensorOrientation = so != null ? so : 0;
            StreamConfigurationMap map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Size chosen = null;
            if (map != null) {
                Size[] sizes = map.getOutputSizes(ImageFormat.YUV_420_888);
                if (sizes != null) {
                    for (Size s : sizes) {
                        if (s.getWidth() == 640 && s.getHeight() == 480) chosen = s;
                    }
                    if (chosen == null && sizes.length > 0) {
                        chosen = sizes[0];
                        for (Size s : sizes) {
                            if ((long) s.getWidth() * s.getHeight() > (long) chosen.getWidth() * chosen.getHeight()) chosen = s;
                        }
                    }
                }
            }
            if (chosen == null) chosen = new Size(640, 480);
            listener.onCameraInfo(ctx.getString(R.string.cam_info, cameraId,
                    chosen.getWidth(), chosen.getHeight(), sensorOrientation));

            reader = ImageReader.newInstance(chosen.getWidth(), chosen.getHeight(), ImageFormat.YUV_420_888, 3);
            reader.setOnImageAvailableListener(r -> {
                Image img = r.acquireLatestImage();
                if (img == null) return;
                Mat gray;
                try {
                    gray = yToMat(img);
                } finally {
                    img.close();
                }
                listener.onFrame(gray);
                gray.release();
            }, handler);

            mgr.openCamera(cameraId, new CameraDevice.StateCallback() {
                @Override public void onOpened(CameraDevice d) {
                    device = d;
                    startSession();
                }
                @Override public void onDisconnected(CameraDevice d) {
                    d.close();
                    device = null;
                    listener.onCameraError(ctx.getString(R.string.cam_disconnected));
                }
                @Override public void onError(CameraDevice d, int error) {
                    d.close();
                    device = null;
                    listener.onCameraError(ctx.getString(R.string.cam_error, error));
                }
            }, handler);
        } catch (CameraAccessException | SecurityException e) {
            listener.onCameraError(ctx.getString(R.string.cam_open_fail, String.valueOf(e)));
        }
    }

    @SuppressWarnings("deprecation")
    private void startSession() {
        try {
            device.createCaptureSession(Collections.singletonList(reader.getSurface()),
                    new CameraCaptureSession.StateCallback() {
                        @Override public void onConfigured(CameraCaptureSession s) {
                            session = s;
                            try {
                                CaptureRequest.Builder b = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
                                b.addTarget(reader.getSurface());
                                b.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                                b.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
                                s.setRepeatingRequest(b.build(), null, handler);
                            } catch (CameraAccessException | IllegalStateException e) {
                                listener.onCameraError(ctx.getString(R.string.cam_capture_fail, String.valueOf(e)));
                            }
                        }
                        @Override public void onConfigureFailed(CameraCaptureSession s) {
                            listener.onCameraError(ctx.getString(R.string.cam_session_fail));
                        }
                    }, handler);
        } catch (CameraAccessException | IllegalStateException e) {
            listener.onCameraError(ctx.getString(R.string.cam_session_create_fail, String.valueOf(e)));
        }
    }

    private Mat yToMat(Image img) {
        int w = img.getWidth();
        int h = img.getHeight();
        Image.Plane plane = img.getPlanes()[0];
        ByteBuffer buf = plane.getBuffer();
        int rowStride = plane.getRowStride();
        if (buffer == null || buffer.length != w * h) buffer = new byte[w * h];
        if (rowStride == w) {
            buf.get(buffer, 0, w * h);
        } else {
            for (int y = 0; y < h; y++) {
                buf.position(y * rowStride);
                buf.get(buffer, y * w, w);
            }
        }
        Mat m = new Mat(h, w, CvType.CV_8UC1);
        m.put(0, 0, buffer);
        return m;
    }

    public void close() {
        try { if (session != null) session.close(); } catch (Exception ignored) { }
        try { if (device != null) device.close(); } catch (Exception ignored) { }
        try { if (reader != null) reader.close(); } catch (Exception ignored) { }
        session = null;
        device = null;
        reader = null;
        Log.i(TAG, "Cámara cerrada");
    }
}
