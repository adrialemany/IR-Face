package org.irface;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import android.provider.Settings;
import android.util.Log;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

public final class Store {
    private static final String TAG = "IRFACE";
    public static final float DEFAULT_THRESHOLD = 0.60f;
    public static final String CAMERA_ID = "5";

    private Store() { }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences("irface", Context.MODE_PRIVATE);
    }

    public static double threshold(Context c) { return prefs(c).getFloat("threshold_v2", DEFAULT_THRESHOLD); }
    public static void setThreshold(Context c, double t) { prefs(c).edit().putFloat("threshold_v2", (float) t).apply(); }

    public static int rotation(Context c) { return prefs(c).getInt("rotation", -1); }
    public static void setRotation(Context c, int r) { prefs(c).edit().putInt("rotation", r).apply(); }

    public static boolean requireSwipe(Context c) { return prefs(c).getBoolean("require_swipe", true); }
    public static void setRequireSwipe(Context c, boolean v) { prefs(c).edit().putBoolean("require_swipe", v).apply(); }

    public static boolean showMessages(Context c) { return prefs(c).getBoolean("show_messages", true); }
    public static void setShowMessages(Context c, boolean v) { prefs(c).edit().putBoolean("show_messages", v).apply(); }

    public static int iconOffsetDp(Context c) { return prefs(c).getInt("icon_offset_dp", 64); }
    public static void setIconOffsetDp(Context c, int v) { prefs(c).edit().putInt("icon_offset_dp", v).apply(); }

    public static int iconSizeDp(Context c) { return prefs(c).getInt("icon_size_dp", 60); }
    public static void setIconSizeDp(Context c, int v) { prefs(c).edit().putInt("icon_size_dp", v).apply(); }

    private static final ComponentName INDICATOR =
            new ComponentName("org.irface", "org.irface.IndicatorService");

    public static boolean indicatorEnabled(Context c) {
        String list = Settings.Secure.getString(c.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (list == null) return false;
        for (String s : list.split(":")) {
            ComponentName cn = ComponentName.unflattenFromString(s);
            if (INDICATOR.equals(cn)) return true;
        }
        return false;
    }

    public static boolean setIndicatorEnabled(Context c, boolean enable) {
        try {
            String list = Settings.Secure.getString(c.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            List<String> items = new ArrayList<>();
            if (list != null) {
                for (String s : list.split(":")) {
                    if (s.isEmpty()) continue;
                    if (INDICATOR.equals(ComponentName.unflattenFromString(s))) continue;
                    items.add(s);
                }
            }
            if (enable) items.add(INDICATOR.flattenToString());
            Settings.Secure.putString(c.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, String.join(":", items));
            if (!items.isEmpty()) {
                Settings.Secure.putInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 1);
            }
            return true;
        } catch (SecurityException e) {
            Log.e(TAG, "Sin permiso para cambiar la accesibilidad", e);
            return false;
        }
    }

    private static File file(Context c) { return new File(c.getApplicationContext().getFilesDir(), "enrolled.bin"); }

    public static synchronized List<float[]> loadEnrolled(Context c) {
        List<float[]> out = new ArrayList<>();
        File f = file(c);
        if (!f.exists()) return out;
        try (DataInputStream in = new DataInputStream(new FileInputStream(f))) {
            int n = in.readInt();
            int dim = in.readInt();
            for (int i = 0; i < n; i++) {
                float[] v = new float[dim];
                for (int j = 0; j < dim; j++) v[j] = in.readFloat();
                out.add(v);
            }
        } catch (Exception e) {
            Log.w(TAG, "No se pudo leer el registro", e);
            out.clear();
        }
        return out;
    }

    public static synchronized void saveEnrolled(Context c, List<float[]> list) {
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(file(c)))) {
            out.writeInt(list.size());
            out.writeInt(list.isEmpty() ? 0 : list.get(0).length);
            for (float[] v : list) for (float x : v) out.writeFloat(x);
        } catch (Exception e) {
            Log.e(TAG, "No se pudo guardar el registro", e);
        }
    }

    public static synchronized void clearEnrolled(Context c) {
        file(c).delete();
    }
}
