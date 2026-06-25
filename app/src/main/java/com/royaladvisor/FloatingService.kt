package com.royaladvisor

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.Base64
import android.view.*
import android.widget.*
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

class FloatingService : Service() {

    private lateinit var wm: WindowManager
    private lateinit var floatView: View
    private var overlayView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null

    private val MODELS = listOf(
        "gemini-2.5-flash",
        "gemini-2.0-flash",
        "gemini-2.0-flash-lite",
        "gemini-flash-latest"
    )

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("resultCode", -1) ?: -1
        val data = intent?.getParcelableExtra<Intent>("data")

        if (resultCode != -1 && data != null) {
            val mgr = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mgr.getMediaProjection(resultCode, data)
            setupImageReader()
        }
        return START_STICKY
    }

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
        params.x = 20; params.y = 300
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
            captureAndAnalyze()
        }
    }

    private fun setupImageReader() {
        val dm = resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        val dpi = dm.densityDpi

        imageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "RoyalAdvisor", w, h, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
    }

    private fun captureAndAnalyze() {
        showToast("📸 Делаю скриншот...")
        handler.postDelayed({
            val bitmap = captureScreen()
            if (bitmap != null) {
                showToast("🤖 Анализирую...")
                Thread { analyzeWithFallback(bitmap, 0) }.start()
            } else {
                showToast("❌ Не удалось сделать скриншот")
            }
        }, 500)
    }

    private fun captureScreen(): Bitmap? {
        return try {
            val image = imageReader?.acquireLatestImage() ?: return null
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width
            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height, Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)
            image.close()
            Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
        } catch (e: Exception) { null }
    }

    private fun analyzeWithFallback(bitmap: Bitmap, modelIndex: Int) {
        if (modelIndex >= MODELS.size) {
            handler.post { showDialog("❌ Все модели недоступны. Попробуй позже.", false) }
            return
        }
        try {
            val result = callGemini(bitmap, MODELS[modelIndex])
            handler.post {
                removeOverlay()
                showDialog(result, false)
                drawArrow(result)
            }
        } catch (e: Exception) {
            val msg = e.message ?: ""
            if (msg.contains("429") || msg.contains("quota") || msg.contains("RESOURCE_EXHAUSTED")) {
                showToast("⚠️ Квота ${MODELS[modelIndex]} кончилась, пробую следующую...")
                analyzeWithFallback(bitmap, modelIndex + 1)
            } else {
                handler.post { showDialog("❌ Ошибка: $msg", false) }
            }
        }
    }

    private fun callGemini(bitmap: Bitmap, model: String): String {
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        val b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP)

        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$model:generateContent?key=${BuildConfig.GEMINI_API_KEY}")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 20000
        conn.readTimeout = 20000

        val prompt = """Ты эксперт по Royal Kingdom (match-3).
Посмотри на скриншот и скажи:
🎯 ЦЕЛЬ: [что нужно собрать]
⚔️ ХОД: [конкретно куда ходить — цвет и позиция на доске]
💡 ПОЧЕМУ: [одно предложение]
🔥 ПАУЭРАП: [есть ли готовый]
📋 ДАЛЕЕ:
• [приоритет 1]
• [приоритет 2]
Отвечай кратко на русском."""

        val body = JSONObject()
        val contents = JSONArray()
        val content = JSONObject()
        val parts = JSONArray()
        parts.put(JSONObject().apply {
            put("inline_data", JSONObject().apply {
                put("mime_type", "image/jpeg")
                put("data", b64)
            })
        })
        parts.put(JSONObject().apply { put("text", prompt) })
        content.put("parts", parts)
        contents.put(content)
        body.put("contents", contents)
        body.put("generationConfig", JSONObject().apply {
            put("maxOutputTokens", 512)
            put("temperature", 0.1)
        })

        conn.outputStream.write(body.toString().toByteArray())

        val responseCode = conn.responseCode
        if (responseCode != 200) {
            val error = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $responseCode"
            throw Exception(error)
        }

        val response = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(response)
        return json.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")
            .getJSONObject(0)
            .getString("text")
    }

    private fun drawArrow(analysisText: String) {
        removeOverlay()
        val dm = resources.displayMetrics
        val canvas = android.graphics.Canvas()
        val overlay = object : View(this) {
            override fun onDraw(c: android.graphics.Canvas) {
                val paint = Paint().apply {
                    color = Color.argb(200, 255, 215, 0)
                    strokeWidth = 12f
                    style = Paint.Style.STROKE
                    isAntiAlias = true
                }
                val cx = dm.widthPixels / 2f
                val cy = dm.heightPixels * 0.7f
                c.drawCircle(cx, cy, 60f, paint)
                paint.style = Paint.Style.FILL
                paint.color = Color.argb(180, 255, 215, 0)
                val path = Path()
                path.moveTo(cx, cy - 80f)
                path.lineTo(cx - 30f, cy - 40f)
                path.lineTo(cx + 30f, cy - 40f)
                path.close()
                c.drawPath(path, paint)
                paint.color = Color.BLACK
                paint.textSize = 28f
                paint.style = Paint.Style.FILL
                c.drawText("👑 Ходи сюда!", cx - 80f, cy + 100f, paint)
            }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        )

        overlayView = overlay
        wm.addView(overlay, params)
        handler.postDelayed({ removeOverlay() }, 5000)
    }

    private fun removeOverlay() {
        overlayView?.let { try { wm.removeView(it) } catch (e: Exception) {} }
        overlayView = null
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

    private fun showToast(text: String) {
        handler.post { Toast.makeText(this, text, Toast.LENGTH_SHORT).show() }
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
        removeOverlay()
        virtualDisplay?.release()
        mediaProjection?.stop()
        if (::floatView.isInitialized) try { wm.removeView(floatView) } catch (e: Exception) {}
    }
}
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
