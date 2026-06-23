package com.royaladvisor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
                Toast.makeText(this, "Разреши отображение поверх других приложений!", Toast.LENGTH_LONG).show()
            } else {
                startService(Intent(this, FloatingService::class.java))
                Toast.makeText(this, "Кнопка запущена! Можешь открыть Royal Kingdom 👑", Toast.LENGTH_LONG).show()
                finish()
            }
        }
        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, FloatingService::class.java))
            Toast.makeText(this, "Остановлено", Toast.LENGTH_SHORT).show()
        }
    }
}
