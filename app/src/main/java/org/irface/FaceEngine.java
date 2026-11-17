package org.irface;

import android.content.Context;

import org.opencv.core.Mat;
import org.opencv.core.Size;
import org.opencv.objdetect.FaceDetectorYN;
import org.opencv.objdetect.FaceRecognizerSF;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class FaceEngine {
    static final String DET_MODEL = "face_detection_yunet_2023mar.onnx";
    static final String REC_MODEL = "face_recognition_sface_2021dec.onnx";

    public static class Face {
        public final Mat row;
        public final float x, y, w, h, score;
        public final float[] landmarks = new float[10];

        Face(Mat row, float[] d) {
            this.row = row;
            x = d[0]; y = d[1]; w = d[2]; h = d[3];
            System.arraycopy(d, 4, landmarks, 0, 10);
            score = d[14];
        }
        public void release() { row.release(); }
    }

    private static FaceEngine sInstance;

    public static synchronized FaceEngine get(Context ctx) throws IOException {
        if (sInstance == null) sInstance = new FaceEngine(ctx.getApplicationContext());
        return sInstance;
    }

    private final FaceDetectorYN detector;
    private final FaceRecognizerSF recognizer;
    private int inW = -1, inH = -1;

    private FaceEngine(Context ctx) throws IOException {
        String det = copyAsset(ctx, DET_MODEL, 100_000);
        String rec = copyAsset(ctx, REC_MODEL, 10_000_000);
        detector = FaceDetectorYN.create(det, "", new Size(320, 320), 0.8f, 0.3f, 5);
        recognizer = FaceRecognizerSF.create(rec, "");
    }

    public synchronized List<Face> detect(Mat img) {
        if (img.cols() != inW || img.rows() != inH) {
            inW = img.cols();
            inH = img.rows();
            detector.setInputSize(new Size(inW, inH));
        }
        Mat faces = new Mat();
        detector.detect(img, faces);
        List<Face> out = new ArrayList<>();
        float[] d = new float[15];
        for (int i = 0; i < faces.rows(); i++) {
            faces.get(i, 0, d);
            out.add(new Face(faces.row(i).clone(), d.clone()));
        }
        faces.release();
        out.sort((a, b) -> Float.compare(b.w * b.h, a.w * a.h));
        return out;
    }

    public synchronized float[] embedding(Mat img, Face face) {
        Mat aligned = new Mat();
        Mat feat = new Mat();
        recognizer.alignCrop(img, face.row, aligned);
        recognizer.feature(aligned, feat);
        float[] v = new float[(int) feat.total()];
        feat.get(0, 0, v);
        aligned.release();
        feat.release();
        double n = 0;
        for (float f : v) n += f * f;
        n = Math.sqrt(n);
        if (n > 0) for (int i = 0; i < v.length; i++) v[i] /= (float) n;
        return v;
    }

    public static double cosine(float[] a, float[] b) {
        double s = 0;
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) s += a[i] * b[i];
        return s;
    }

    private static String copyAsset(Context ctx, String name, long minBytes) throws IOException {
        File out = new File(ctx.getFilesDir(), name);
        try (InputStream in = ctx.getAssets().open(name)) {
            if (!out.exists() || out.length() < minBytes) {
                try (FileOutputStream fo = new FileOutputStream(out)) {
                    byte[] buf = new byte[1 << 16];
                    int r;
                    while ((r = in.read(buf)) > 0) fo.write(buf, 0, r);
                }
            }
        } catch (IOException e) {
            throw new IOException("Falta el modelo " + name + " en assets (ver README)", e);
        }
        if (out.length() < minBytes) {
            throw new IOException("El modelo " + name + " está incompleto (" + out.length()
                    + " bytes). Probablemente descargaste el puntero de Git LFS en vez del archivo real.");
        }
        return out.getAbsolutePath();
    }
}
