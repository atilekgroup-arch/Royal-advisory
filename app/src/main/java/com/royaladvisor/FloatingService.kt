package com.royaladvisor

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class FloatingService : Service() {
    private lateinit var wm: WindowManager
    private lateinit var floatView: View
    private val API_KEY = "AQ.Ab8RN6Kw4FfzjviPXv1U31kKiR5xdxbXNMcW8l-BhyafuYn5DA"

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
        ).apply { gravity = Gravity.TOP or Gravity.START; x = 20; y = 300 }

        wm.addView(floatView, params)

        var ox = 0; var oy = 0; var ix = 0; var iy = 0
        floatView.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { ox=params.x; oy=params.y; ix=e.rawX.toInt(); iy=e.rawY.toInt() }
                MotionEvent.ACTION_MOVE -> { params.x=ox+(e.rawX.toInt()-ix); params.y=oy+(e.rawY.toInt()-iy); wm.updateViewLayout(floatView, params) }
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
                android.os.Handler(mainLooper).post { showDialog(result, false) }
            } catch (e: Exception) {
                android.os.Handler(mainLooper).post { showDialog("❌ Ошибка: ${e.message}", false) }
            }
        }.start()
    }

    private fun callGemini(): String {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-latest:generateContent?key=$API_KEY")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 15000
        conn.readTimeout = 15000

        val prompt = "Ты эксперт по Royal Kingdom (match-3). Дай совет по стратегии:\n🎯 ЦЕЛЬ:\n⚔️ ХОД:\n💡 ПОЧЕМУ:\n🔥 ПАУЭРАП:\n📋 ДАЛЕЕ:\n•\n•\nОтвечай кратко на русском."

        val body = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 512)
                put("temperature", 0.1)
            })
        }

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
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
            .setTitle("👑 Royal Advisor")
            .setMessage(text)
            .apply { if (!loading) setPositiveButton("OK", null) }
            .create()
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.show()
    }

    private fun buildNotification(): Notification {
        val ch = NotificationChannel("royal", "Royal Advisor", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        return NotificationCompat.Builder(this, "royal")
            .setContentTitle("Royal Advisor активен 👑")
            .setSmallIcon(android.R.drawable.star_on)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::floatView.isInitialized) wm.removeView(floatView)
    }
}        var ox = 0; var oy = 0; var ix = 0; var iy = 0
        floatView.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { ox=params.x; oy=params.y; ix=e.rawX.toInt(); iy=e.rawY.toInt() }
                MotionEvent.ACTION_MOVE -> {
                    params.x = ox+(e.rawX.toInt()-ix)
                    params.y = oy+(e.rawY.toInt()-iy)
                    wm.updateViewLayout(floatView, params)
                }
            }
            false
        }

        floatView.findViewById<Button>(R.id.btnAnalyze).setOnClickListener {
            showResult(analyze())
        }
    }

    private fun analyze(): String {
        return """
👑 ROYAL ADVISOR

⚔️ ЛУЧШИЙ ХОД:
Сделай матч снизу доски — создаст каскад и откроет препятствия сверху.

💡 СТРАТЕГИЯ:
• Приоритет на нижние ряды
• Создавай пауэрапы рядом с препятствиями
• Rocket + Electro = удар по всей доске
• В Kingdom уровнях бей под воинами

🔥 ПАУЭРАПЫ:
• 4 в ряд = Rocket 🚀
• 5 в ряд = Electro ⚡
• L/T форма = Dynamite 💣
• Квадрат 2x2 = Spinner 🌀
        """.trimIndent()
    }

    private fun showResult(text: String) {
        val dialog = AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog)
            .setTitle("👑 Royal Advisor")
            .setMessage(text)
            .setPositiveButton("OK", null)
            .create()
        dialog.window?.setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY)
        dialog.show()
    }

    private fun buildNotification(): Notification {
        val ch = NotificationChannel("royal", "Royal Advisor", NotificationManager.IMPORTANCE_LOW)
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
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
