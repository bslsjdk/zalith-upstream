package io.github.juanluci.starling;

/** Minimal stub: the engine only needs makeReport's JNI symbol to resolve. */
public class DiagnosticsActivity {
    /** Native report builder (implemented by libhorizon_android.so). */
    private static native String makeReport(Object activity, String priv, String game, boolean export);
}