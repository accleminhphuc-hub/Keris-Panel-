package com.zeta.ffpanel;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Point;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import androidx.core.math.MathUtils;

public class DragAimService extends AccessibilityService {
    private NativeAimPatch nativePatch;
    private Handler handler = new Handler(Looper.getMainLooper());
    private Point lastEnemyScreenPos = new Point(-1, -1);
    private boolean running = true;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        nativePatch = new NativeAimPatch();
        if (isRooted()) {
            nativePatch.initAimPatch();
            Log.i("ZO", "Native aim patch loaded");
        } else {
            Log.e("ZO", "No root - kéo tâm chỉ dùng gesture mù");
        }
        startEnemyTracker();
    }

    private void startEnemyTracker() {
        new Thread(() -> {
            while (running && MainActivity.isDraggingEnabled()) {
                if (isRooted() && nativePatch != null) {
                    Point pos = nativePatch.getClosestEnemyScreenPosition();
                    if (pos != null && pos.x > 0 && pos.y > 0) {
                        lastEnemyScreenPos = pos;
                        performDragAim();
                    } else {
                        lastEnemyScreenPos = new Point(-1, -1);
                    }
                }
                try { Thread.sleep(30); } catch (InterruptedException e) { break; }
            }
        }).start();
    }

    private void performDragAim() {
        if (lastEnemyScreenPos.x < 0) return;
        int[] screenSize = getScreenSize();
        int centerX = screenSize[0] / 2;
        int centerY = screenSize[1] / 2;
        int dx = lastEnemyScreenPos.x - centerX;
        int dy = lastEnemyScreenPos.y - centerY;
        double distance = Math.hypot(dx, dy);
        if (distance > 300) return;  // chỉ kéo khi enemy gần tâm

        int maxStep = 80;
        int stepX = MathUtils.clamp((int)(dx * 0.3), -maxStep, maxStep);
        int stepY = MathUtils.clamp((int)(dy * 0.3), -maxStep, maxStep);
        if (Math.abs(stepX) < 5 && Math.abs(stepY) < 5) return;

        // Tạo gesture kéo
        GestureDescription.Builder builder = new GestureDescription.Builder();
        Path path = new Path();
        path.moveTo(centerX, centerY);
        path.lineTo(centerX + stepX, centerY + stepY);
        builder.addStroke(new GestureDescription.StrokeDescription(path, 0, 50));
        dispatchGesture(builder.build(), null, null);
        Log.d("ZO", "Kéo tâm: " + stepX + ", " + stepY);
    }

    private int[] getScreenSize() {
        return new int[]{
                getResources().getDisplayMetrics().widthPixels,
                getResources().getDisplayMetrics().heightPixels
        };
    }

    private boolean isRooted() {
        try {
            return Runtime.getRuntime().exec("su").waitFor() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {}

    @Override
    public void onInterrupt() {
        running = false;
    }

    @Override
    public void onDestroy() {
        running = false;
        if (nativePatch != null) nativePatch.cleanup();
        super.onDestroy();
    }
}