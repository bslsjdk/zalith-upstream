/*
 * RCL Horizon engine host - lean rewrite of the official StarlingActivity.
 *
 * The JNI symbols in libhorizon_android.so are bound to the exact class name
 * io.github.juanluci.starling.StarlingActivity, so package/name are fixed.
 * We implement every method the engine calls back through JNI (the full method
 * table was extracted from horizon-client/src/android.rs), but drop the parts
 * RCL does not use (updater, keystore secret sealing, build-server dialog,
 * native-mod permission prompts): those return safe defaults.
 */
package io.github.juanluci.starling;

import android.app.AlertDialog;
import android.app.NativeActivity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.system.ErrnoException;
import android.system.Os;
import android.view.InputDevice;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

import java.io.File;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;

public class StarlingActivity extends NativeActivity {
    private static final String TAG = "rcl-horizon";

    public static final String EXTRA_GAME_DIR = "io.github.juanluci.starling.GAME_DIR";
    private static final int PICK_FILE = 1;
    private static final int PICK_DOCUMENT = 4101;

    /** Native methods implemented by libhorizon_android.so. */
    private static native void onImeHeight(int pixels);
    private static native void onSafeInsets(int left, int top, int right, int bottom);
    private static native void onStorage(String folder);
    private static native void onPointerCaptureLost();
    private native void nativeDocumentResult(int request, int result, String uri);

    private boolean pointerWanted;
    private SensorManager sensors;
    private final float[] gyroTurned = new float[3];
    private long gyroLast;
    private final SensorEventListener gyroListener = new SensorEventListener() {
        @Override public void onSensorChanged(SensorEvent event) {
            long last = gyroLast;
            gyroLast = event.timestamp;
            float seconds = (float) ((event.timestamp - last) * 1e-9);
            if (seconds <= 0 || seconds > 0.1f) return;
            synchronized (gyroTurned) {
                gyroTurned[0] += event.values[0] * seconds;
                gyroTurned[1] += event.values[1] * seconds;
                gyroTurned[2] += event.values[2] * seconds;
            }
        }
        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) { }
    };

    private void loadGame() {
        try {
            Os.setenv("RUST_MIN_STACK", android.os.Process.is64Bit() ? "16777216" : "8388608", false);
        } catch (ErrnoException ignored) { }
        System.loadLibrary("horizon_android");
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        loadGame();
        String folder = null;
        Intent intent = getIntent();
        if (intent != null) folder = intent.getStringExtra(EXTRA_GAME_DIR);
        if (folder == null) {
            try {
                if (Build.VERSION.SDK_INT >= 30 ? Environment.isExternalStorageManager()
                        : checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    folder = new File(Environment.getExternalStorageDirectory(), "rcl-horizon").getPath();
                }
            } catch (Throwable t) { folder = null; }
        }
        onStorage(folder);
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemBars();
    }

    @Override protected void onResume() { super.onResume(); }
    @Override protected void onPause() { super.onPause(); }
    @Override public void onConfigurationChanged(android.content.res.Configuration c) { super.onConfigurationChanged(c); }
    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    // ----- engine callbacks -----
    public void setDisplayPreferences(int cutout, int orientations, int rotation, int width, int height) { }
    public void capturePointer(final boolean capture) {
        pointerWanted = capture;
        View decor = getWindow().getDecorView();
        if (capture) decor.requestPointerCapture(); else decor.releasePointerCapture();
    }
    @Override public void onPointerCaptureChanged(boolean hasCapture) {
        super.onPointerCaptureChanged(hasCapture);
        if (!hasCapture && pointerWanted) { pointerWanted = false; onPointerCaptureLost(); }
    }
    public int padVendor(int deviceId) {
        InputDevice d = InputDevice.getDevice(deviceId);
        return d == null ? 0 : d.getVendorId();
    }
    public String padName(int deviceId) {
        InputDevice d = InputDevice.getDevice(deviceId);
        return d == null ? null : d.getName();
    }
    @SuppressWarnings("deprecation")
    public void vibratePad(int deviceId, int millis, int strong, int weak) {
        InputDevice d = InputDevice.getDevice(deviceId);
        if (d == null || millis <= 0) return;
        try {
            if (Build.VERSION.SDK_INT >= 31) {
                android.os.VibratorManager vm = d.getVibratorManager();
                int[] ids = vm.getVibratorIds();
                if (ids.length == 0) return;
                android.os.VibrationEffect e = android.os.VibrationEffect.createOneShot(millis, Math.min(Math.max(strong, weak), 255));
                vm.vibrate(android.os.CombinedVibration.startParallel().addVibrator(ids[0], e).combine());
            } else {
                android.os.Vibrator v = d.getVibrator();
                v.vibrate(android.os.VibrationEffect.createOneShot(millis, Math.min(Math.max(strong, weak), 255)));
            }
        } catch (Throwable ignored) { }
    }
    public boolean gyroscope(boolean on) {
        if (on) {
            if (sensors == null) sensors = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            Sensor g = sensors == null ? null : sensors.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            if (g == null) return false;
            gyroLast = 0;
            sensors.registerListener(gyroListener, g, 5000);
            return true;
        } else {
            if (sensors != null) sensors.unregisterListener(gyroListener);
            return true;
        }
    }
    @SuppressWarnings("deprecation")
    public float[] takeGyro() {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        synchronized (gyroTurned) {
            float[] out = {gyroTurned[0], gyroTurned[1], gyroTurned[2], rotation};
            gyroTurned[0] = gyroTurned[1] = gyroTurned[2] = 0;
            return out;
        }
    }
    @SuppressWarnings("deprecation")
    public void vibrate(int millis) {
        try {
            android.os.Vibrator v = (android.os.Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
            if (v != null) v.vibrate(android.os.VibrationEffect.createOneShot(millis, android.os.VibrationEffect.DEFAULT_AMPLITUDE));
        } catch (Throwable ignored) { }
    }
    public void hideKeyboard() {
        try {
            InputMethodManager im = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (im != null) im.hideSoftInputFromWindow(getWindow().getDecorView().getWindowToken(), 0);
        } catch (Throwable ignored) { }
    }
    public void firstFrame() { drawn = true; }
    private volatile boolean drawn = false;
    private TextMirror mirror;
    public void showTextMirror(String text, int cursor, int anchor, int maxLength, int flags, int generation) {
        runOnUiThread(() -> {
            if (mirror == null) {
                mirror = new TextMirror(this);
                addContentView(mirror, new android.view.ViewGroup.LayoutParams(1, 1));
            }
            mirror.show(text, cursor, anchor, maxLength, flags, generation);
        });
    }
    public void updateTextMirror(String text, int cursor, int anchor, int maxLength, int flags, int generation) {
        runOnUiThread(() -> { if (mirror != null) mirror.update(text, cursor, anchor, maxLength, flags, generation); });
    }
    public void hideTextMirror() {
        runOnUiThread(() -> {
            if (mirror != null) mirror.hide();
            View game = getWindow().getDecorView();
            game.setFocusableInTouchMode(true);
            game.requestFocus();
        });
    }

    public void openDiagnostics() { }
    public void shareDiagnostic(String name) { }
    public void askBuildServer(String current) { }
    public boolean horizonAndroidInstalled() { return false; }
    public boolean importFromHorizonAndroid() { return false; }
    public void openFileAccessSettings() { }
    public boolean fileAccessAllowed() {
        return Build.VERSION.SDK_INT >= 30 ? Environment.isExternalStorageManager() : true;
    }
    public boolean openFolder(String path) { return false; }
    public void keepWorking(String reason, String line, boolean on) { }
    public boolean openLink(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); return true; }
        catch (Throwable t) { return false; }
    }
    public String[] dnsServers() {
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
            Network n = cm == null ? null : cm.getActiveNetwork();
            LinkProperties lp = n == null ? null : cm.getLinkProperties(n);
            List<InetAddress> dns = lp == null ? null : lp.getDnsServers();
            if (dns == null) return new String[0];
            String[] out = new String[dns.size()];
            for (int i = 0; i < dns.size(); i++) out[i] = dns.get(i).getHostAddress();
            return out;
        } catch (Throwable t) { return new String[0]; }
    }
    public String clipboardText() {
        try {
            ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData d = cb == null ? null : cb.getPrimaryClip();
            return (d != null && d.getItemCount() > 0 && d.getItemAt(0).getText() != null)
                    ? d.getItemAt(0).getText().toString() : "";
        } catch (Throwable t) { return ""; }
    }
    public boolean setClipboardText(String text) {
        try {
            ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            if (cb == null) return false;
            cb.setPrimaryClip(ClipData.newPlainText("rcl", text));
            return true;
        } catch (Throwable t) { return false; }
    }
    public float maxRefreshRate() {
        try { return getWindowManager().getDefaultDisplay().getRefreshRate(); }
        catch (Throwable t) { return 60f; }
    }
    public String versionName() {
        try { return getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Throwable t) { return "26.3"; }
    }
    public boolean releaseBuild() { return true; }
    public boolean installUpdate(String name) { return false; }
    public byte[] sealSecret(byte[] data) { return data; }
    public byte[] openSecret(byte[] sealed) { return sealed; }
    public void restart() { finish(); }
    public void restart(String message) { finish(); }
    public void pickDocument() { }
    public boolean pickFile() { return false; }

    @Override protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (req == PICK_DOCUMENT) {
            nativeDocumentResult(req, result, data == null || data.getData() == null ? null : data.getData().toString());
        }
    }
    @Override protected void onDestroy() {
        android.os.Process.killProcess(android.os.Process.myPid());
        super.onDestroy();
    }
    private void hideSystemBars() {
        try {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        } catch (Throwable ignored) { }
    }
}