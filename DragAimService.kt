package com.zeta.ffpanel

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Point
import android.hardware.input.InputManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import com.zeta.ffpanel.MainActivity.Companion.isDraggingEnabled
import kotlin.math.*

class DragAimService : AccessibilityService() {
    private lateinit var nativePatch: NativeAimPatch
    private val handler = Handler(Looper.getMainLooper())
    private var lastEnemyScreenPos: Pair<Int, Int>? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        nativePatch = NativeAimPatch()
        if (isRooted()) {
            nativePatch.initAimPatch()
            Log.i("ZO", "Native aim patch loaded")
        }
        // Bắt đầu thread đọc vị trí enemy từ memory
        startEnemyTracker()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Không cần xử lý event, ta tự quét memory
    }

    private fun startEnemyTracker() {
        Thread {
            while (isDraggingEnabled) {
                if (!isRooted()) {
                    Thread.sleep(500)
                    continue
                }
                val enemyPos = nativePatch.getClosestEnemyScreenPosition()
                if (enemyPos != null && enemyPos.first > 0 && enemyPos.second > 0) {
                    lastEnemyScreenPos = enemyPos
                    performDragAim()
                } else {
                    lastEnemyScreenPos = null
                }
                Thread.sleep(30) // 30ms mỗi lần quét (~33 fps)
            }
        }.start()
    }

    private fun performDragAim() {
        val enemy = lastEnemyScreenPos ?: return
        // Lấy center màn hình (crosshair mặc định ở giữa)
        val metrics = resources.displayMetrics
        val centerX = metrics.widthPixels / 2
        val centerY = metrics.heightPixels / 2
        val dx = enemy.first - centerX
        val dy = enemy.second - centerY

        // Chỉ kéo nếu enemy ở trong khoảng 250px so với tâm (giả lập "kéo tâm nhẹ")
        val distance = sqrt((dx*dx + dy*dy).toDouble()).toFloat()
        if (distance > 300f) return

        // Tốc độ kéo tỉ lệ với khoảng cách, tối đa 80px mỗi lần
        val maxStep = 80f
        var stepX = (dx * 0.3f).coerceIn(-maxStep, maxStep).toInt()
        var stepY = (dy * 0.3f).coerceIn(-maxStep, maxStep).toInt()
        if (abs(stepX) < 5 && abs(stepY) < 5) return

        // Tạo cử chỉ kéo từ vị trí center đến center+step
        val gestureBuilder = GestureDescription.Builder()
        val path = Path()
        val fromX = centerX.toFloat()
        val fromY = centerY.toFloat()
        val toX = (centerX + stepX).toFloat()
        val toY = (centerY + stepY).toFloat()
        path.moveTo(fromX, fromY)
        path.lineTo(toX, toY)
        gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 50))
        val gesture = gestureBuilder.build()
        dispatchGesture(gesture, null, null)
        Log.d("ZO", "Kéo tâm: dx=$stepX, dy=$stepY")
    }

    private fun isRooted(): Boolean {
        return try {
            Runtime.getRuntime().exec("su").waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }

    override fun onInterrupt() {
        isDraggingEnabled = false
    }

    override fun onDestroy() {
        nativePatch.cleanup()
        super.onDestroy()
    }
}