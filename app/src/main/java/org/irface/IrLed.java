package org.irface;

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

public class IrLed {
    private static final String TAG = "IRFACE";
    static final String LED_PATH = "/sys/class/leds/ir_torch/brightness";
    static final int LED_ON_VALUE = 127;

    private static final IrLed sInstance = new IrLed();
    public static IrLed get() { return sInstance; }
    private IrLed() { }

    private Process proc;
    private BufferedWriter writer;
    private volatile boolean available = false;
    private volatile boolean on = false;

    public boolean isAvailable() { return available; }
    public boolean isOn() { return on; }

    public synchronized boolean init() {
        if (available) return true;
        try {
            Process p = new ProcessBuilder("su").redirectErrorStream(true).start();
            BufferedWriter w = new BufferedWriter(new OutputStreamWriter(p.getOutputStream()));
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            w.write("if [ -w " + LED_PATH + " ]; then echo IRFACE_OK; else echo IRFACE_NOLED; fi\n");
            w.flush();
            String line = r.readLine();
            if (line != null && line.trim().equals("IRFACE_OK")) {
                proc = p;
                writer = w;
                available = true;
                Log.i(TAG, "Root OK, emisor IR controlable");
                return true;
            }
            Log.w(TAG, "Sin acceso al emisor IR: " + line);
            p.destroy();
        } catch (Exception e) {
            Log.w(TAG, "No hay su/root: " + e);
        }
        available = false;
        return false;
    }

    public synchronized void set(boolean enable) {
        if (!available || writer == null) return;
        try {
            writer.write("echo " + (enable ? LED_ON_VALUE : 0) + " > " + LED_PATH + "\n");
            writer.flush();
            on = enable;
        } catch (Exception e) {
            Log.w(TAG, "Error escribiendo el LED: " + e);
            available = false;
        }
    }

    public synchronized void close() {
        set(false);
        try {
            if (writer != null) {
                writer.write("exit\n");
                writer.flush();
            }
        } catch (Exception ignored) { }
        if (proc != null) proc.destroy();
        proc = null;
        writer = null;
        available = false;
    }
}
