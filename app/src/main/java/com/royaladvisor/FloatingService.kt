package com.royaladvisor

import android.app.AlertDialog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Button
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class FloatingService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var floatView: android.view.View
    private val API_KEY = BuildConfig.GEMINI_API_KEY
    private val handler = Handler(android.os.Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        startForeground(1, buildNotification())
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        floatView = LayoutInflater.from(this).inflate(R.layout.floating_button, null)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        params.gravity = Gravity.TOP or Gravity.START
        params.x = 20
        params.y = 300
        wm.addView(floatView, params)

        var ox = 0; var oy = 0; var ix = 0; var iy = 0
        floatView.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    ox = params.x; oy = params.y
                    ix = e.rawX.toInt(); iy = e.rawY.toInt()
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = ox + (e.rawX.toInt() - ix)
                    params.y = oy + (e.rawY.toInt() - iy)
                    wm.updateViewLayout(floatView, params)
                }
            }
            false
        }

        floatView.findViewById<Button>(R.id.btnAnalyze).setOnClickListener {
            analyze()
        }
    }

    private fun analyze() {
        showDialog("⏳ Анализирую...", true)
        Thread {
            try {
                val result = callGemini()
                handler.post { showDialog(result, false) }
            } catch (e: Exception) {
                handler.post { showDialog("❌ Ошибка: ${e.message}", false) }
            }
        }.start()
    }

    private fun callGemini(): String {
    val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$API_KEY")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 15000

        val prompt = "Ты эксперт по Royal Kingdom (match-3). Дай совет по стратегии:\n🎯 ЦЕЛЬ:\n⚔️ ХОД:\n💡 ПОЧЕМУ:\n🔥 ПАУЭРАП:\n📋 ДАЛЕЕ:\n•\n•\nОтвечай кратко на русском."

        val body = JSONObject()
        val contents = JSONArray()
        val content = JSONObject()
        val parts = JSONArray()
        val part = JSONObject()
        part.put("text", prompt)
        parts.put(part)
        content.put("parts", parts)
        contents.put(content)
        body.put("contents", contents)
        val config = JSONObject()
        config.put("maxOutputTokens", 512)
        config.put("temperature", 0.1)
        body.put("generationConfig", config)

        conn.outputStream.write(body.toString().toByteArray())
        val response = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(response)
        return json.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
    }

    private fun showDialog(text: String, loading: Boolean) {
        val builder = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
            .setTitle("👑 Royal Advisor")
            .setMessage(text)
        if (!loading) builder.setPositiveButton("OK", null)
        val dialog = builder.create()
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.show()
    }

    private fun buildNotification(): Notification {
        val channel = NotificationChannel("royal", "Royal Advisor", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        return NotificationCompat.Builder(this, "royal")
            .setContentTitle("Royal Advisor активен 👑")
            .setSmallIcon(android.R.drawable.star_on)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatView.isInitialized) wm.removeView(floatView)
    }
}
