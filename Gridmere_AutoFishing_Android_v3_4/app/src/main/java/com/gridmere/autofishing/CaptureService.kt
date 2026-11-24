package com.gridmere.autofishing

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.WindowManager

class CaptureService : Service() {
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var workerThread: HandlerThread? = null
    private var workerHandler: Handler? = null
    private var analyzer: FrameAnalyzer? = null
    private var lastFrameAt = 0L

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopCapture()
            stopSelf()
            return START_NOT_STICKY
        }

        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())
        if (projection != null) return START_NOT_STICKY

        val resultCode = intent?.getIntExtra("resultCode", 0) ?: 0
        val data = if (Build.VERSION.SDK_INT >= 33) {
            intent?.getParcelableExtra("projectionData", Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            (intent?.getParcelableExtra("projectionData") as? Intent)
        }
        if (data == null || resultCode == 0) {
            stopSelf()
            return START_NOT_STICKY
        }

        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection = manager.getMediaProjection(resultCode, data)
        val metrics = displayMetrics()
        workerThread = HandlerThread("GridmereFrameAnalyzer").also { it.start() }
        workerHandler = Handler(workerThread!!.looper)
        analyzer = FrameAnalyzer(applicationContext)
        reader = ImageReader.newInstance(metrics.widthPixels, metrics.heightPixels, PixelFormat.RGBA_8888, 2)
        reader?.setOnImageAvailableListener({ imageReader ->
            val image = try { imageReader.acquireLatestImage() } catch (_: Exception) { null }
            if (image != null) processImage(image)
        }, workerHandler)

        projection?.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                stopCapture()
                stopSelf()
            }
        }, workerHandler)

        virtualDisplay = projection?.createVirtualDisplay(
            "GridmereAutoFishing34",
            metrics.widthPixels,
            metrics.heightPixels,
            metrics.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader?.surface,
            null,
            workerHandler
        )
        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit()
            .putBoolean("capture_active", virtualDisplay != null)
            .putString("status", "Tangkapan layar aktif")
            .apply()
        return START_NOT_STICKY
    }

    private fun processImage(image: Image) {
        try {
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastFrameAt < 55L) return
            lastFrameAt = now
            val plane = image.planes.firstOrNull() ?: return
            val buffer = plane.buffer
            val pixelStride = plane.pixelStride
            val rowStride = plane.rowStride
            val paddedWidth = rowStride / pixelStride
            val fullBitmap = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
            buffer.rewind()
            fullBitmap.copyPixelsFromBuffer(buffer)
            val frame = if (paddedWidth == image.width) fullBitmap
                        else Bitmap.createBitmap(fullBitmap, 0, 0, image.width, image.height).also { fullBitmap.recycle() }
            analyzer?.process(frame)
            frame.recycle()
        } catch (error: Exception) {
            getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit()
                .putString("status", "Kesalahan pembacaan layar: ${error.message ?: "unknown"}")
                .apply()
        } finally {
            image.close()
        }
    }

    @Suppress("DEPRECATION")
    private fun displayMetrics(): DisplayMetrics {
        val metrics = DisplayMetrics()
        (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
        return metrics
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Gridmere AutoFishing screen capture",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= 26) Notification.Builder(this, CHANNEL_ID)
                      else @Suppress("DEPRECATION") Notification.Builder(this)
        return builder
            .setContentTitle("Gridmere AutoFishing 3.4")
            .setContentText("Pemantauan layar berjalan. Gunakan tombol Berhenti di aplikasi.")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
    }

    private fun stopCapture() {
        try { FishingAccessibilityService.instance?.setDirection(null) } catch (_: Exception) { }
        try { virtualDisplay?.release() } catch (_: Exception) { }
        virtualDisplay = null
        try { reader?.setOnImageAvailableListener(null, null) } catch (_: Exception) { }
        try { reader?.close() } catch (_: Exception) { }
        reader = null
        val oldProjection = projection
        projection = null
        try { oldProjection?.stop() } catch (_: Exception) { }
        try { workerThread?.quitSafely() } catch (_: Exception) { }
        workerThread = null
        workerHandler = null
        analyzer = null
    }

    override fun onDestroy() {
        stopCapture()
        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit()
            .putBoolean("capture_active", false)
            .putBoolean("auto_enabled", false)
            .putString("phase", "STOPPED").apply()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "gridmere_capture_34"
        private const val NOTIFICATION_ID = 734
        const val ACTION_STOP = "com.gridmere.autofishing.STOP_CAPTURE"
    }
}
