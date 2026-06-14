package com.zeta.ffpanel

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private lateinit var btnToggleDrag: Button
    companion object {
        var isDraggingEnabled = false
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnToggleDrag = findViewById(R.id.btnDragAim)

        // Xin quyền overlay và accessibility
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            startActivityForResult(intent, 100)
        }

        btnToggleDrag.setOnClickListener {
            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "MÀY PHẢI BẬT ACCESSIBILITY SERVICE TRONG CÀI ĐẶT", Toast.LENGTH_LONG).show()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                return@setOnClickListener
            }
            isDraggingEnabled = !isDraggingEnabled
            val status = if (isDraggingEnabled) "ĐÃ BẬT" else "ĐÃ TẮT"
            btnToggleDrag.text = "KÉO TÂM: $status"
            Toast.makeText(this, "Kéo tâm $status", Toast.LENGTH_SHORT).show()
            if (isDraggingEnabled) {
                startService(Intent(this, DragAimService::class.java))
            } else {
                stopService(Intent(this, DragAimService::class.java))
            }
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "${packageName}/${DragAimService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        return enabledServices?.contains(service) == true
    }

    override fun onDestroy() {
        stopService(Intent(this, DragAimService::class.java))
        super.onDestroy()
    }
}