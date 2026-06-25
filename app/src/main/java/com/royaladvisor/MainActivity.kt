package com.royaladvisor

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val OVERLAY_PERMISSION_REQUEST = 1001
    private val MEDIA_PROJECTION_REQUEST = 1002

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivityForResult(
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                    OVERLAY_PERMISSION_REQUEST
                )
                Toast.makeText(this, "Разреши отображение поверх других приложений!", Toast.LENGTH_LONG).show()
            } else {
                requestMediaProjection()
            }
        }

        findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, FloatingService::class.java))
            Toast.makeText(this, "Остановлено", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestMediaProjection() {
        val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(mgr.createScreenCaptureIntent(), MEDIA_PROJECTION_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            OVERLAY_PERMISSION_REQUEST -> {
                if (Settings.canDrawOverlays(this)) requestMediaProjection()
                else Toast.makeText(this, "Нужно разрешение!", Toast.LENGTH_SHORT).show()
            }
            MEDIA_PROJECTION_REQUEST -> {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    val intent = Intent(this, FloatingService::class.java)
                    intent.putExtra("resultCode", resultCode)
                    intent.putExtra("data", data)
                    startService(intent)
                    Toast.makeText(this, "Royal Advisor запущен! 👑", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }
}
