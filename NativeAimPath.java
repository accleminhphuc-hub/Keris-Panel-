package com.zeta.ffpanel;

import android.graphics.Point;

public class NativeAimPatch {
    static {
        System.loadLibrary("ffaim");
    }

    public native boolean initAimPatch();
    public native Point getClosestEnemyScreenPosition();
    public native void cleanup();
}