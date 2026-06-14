package com.zeta.ffpanel;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {
    private Button btnDragAim;
    private static boolean isDraggingEnabled = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        btnDragAim = findViewById(R.id.btnDragAim);

        // Xin quyền overlay nếu cần
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, 100);
        }

        btnDragAim.setOnClickListener(v -> {
            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "MÀY PHẢI BẬT ACCESSIBILITY SERVICE TRONG CÀI ĐẶT", Toast.LENGTH_LONG).show();
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
                return;
            }
            isDraggingEnabled = !isDraggingEnabled;
            String status = isDraggingEnabled ? "ĐÃ BẬT" : "ĐÃ TẮT";
            btnDragAim.setText("KÉO TÂM: " + status);
            Toast.makeText(this, "Kéo tâm " + status, Toast.LENGTH_SHORT).show();

            Intent serviceIntent = new Intent(this, DragAimService.class);
            if (isDraggingEnabled) {
                startService(serviceIntent);
            } else {
                stopService(serviceIntent);
            }
        });
    }

    private boolean isAccessibilityServiceEnabled() {
        String service = getPackageName() + "/" + DragAimService.class.getCanonicalName();
        String enabledServices = Settings.Secure.getString(getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        return enabledServices != null && enabledServices.contains(service);
    }

    public static boolean isDraggingEnabled() {
        return isDraggingEnabled;
    }

    @Override
    protected void onDestroy() {
        stopService(new Intent(this, DragAimService.class));
        super.onDestroy();
    }
}