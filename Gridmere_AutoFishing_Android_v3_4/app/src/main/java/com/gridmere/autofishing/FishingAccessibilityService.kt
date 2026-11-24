package com.gridmere.autofishing

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.roundToInt

class FishingAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: FishingAccessibilityService? = null
            private set
    }

    private val handler = Handler(Looper.getMainLooper())
    private val gestureBusy = AtomicBoolean(false)
    @Volatile private var direction: String? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = setDirection(null)

    override fun onDestroy() {
        setDirection(null)
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun tapPull() {
        val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
        tapPercent(
            prefs.getFloat("pull_x", 50f),
            prefs.getFloat("pull_y", 90f),
            90L
        )
    }

    fun setDirection(next: String?) {
        getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE).edit().putString("direction", next).apply()
        if (next != "left" && next != "right") {
            direction = null
            handler.removeCallbacks(directionLoop)
            return
        }
        if (direction != next) {
            direction = next
            handler.removeCallbacks(directionLoop)
            handler.post(directionLoop)
        }
    }

    private val directionLoop = object : Runnable {
        override fun run() {
            val current = direction ?: return
            val prefs = getSharedPreferences(MainActivity.PREFS, MODE_PRIVATE)
            val x = if (current == "left") prefs.getFloat("left_x", 20f)
                    else prefs.getFloat("right_x", 80f)
            val y = if (current == "left") prefs.getFloat("left_y", 90f)
                    else prefs.getFloat("right_y", 90f)
            holdPercent(x, y, 150L)
            if (direction != null) handler.postDelayed(this, 155L)
        }
    }

    fun tapPercent(xPercent: Float, yPercent: Float, durationMs: Long = 90L) {
        val (width, height) = screenSize()
        val x = (width * (xPercent.coerceIn(0f, 100f) / 100f)).coerceIn(1f, (width - 1).toFloat())
        val y = (height * (yPercent.coerceIn(0f, 100f) / 100f)).coerceIn(1f, (height - 1).toFloat())
        dispatchPoint(x, y, durationMs)
    }

    private fun holdPercent(xPercent: Float, yPercent: Float, durationMs: Long) {
        val (width, height) = screenSize()
        val x = (width * (xPercent.coerceIn(0f, 100f) / 100f)).coerceIn(1f, (width - 1).toFloat())
        val y = (height * (yPercent.coerceIn(0f, 100f) / 100f)).coerceIn(1f, (height - 1).toFloat())
        dispatchPoint(x, y, durationMs)
    }

    private fun dispatchPoint(x: Float, y: Float, durationMs: Long) {
        if (!gestureBusy.compareAndSet(false, true)) return
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, durationMs.coerceAtLeast(60L)))
            .build()
        val accepted = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                gestureBusy.set(false)
            }
            override fun onCancelled(gestureDescription: GestureDescription?) {
                gestureBusy.set(false)
            }
        }, handler)
        if (!accepted) gestureBusy.set(false)
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> {
        return if (android.os.Build.VERSION.SDK_INT >= 30) {
            val bounds = (getSystemService(WINDOW_SERVICE) as WindowManager).currentWindowMetrics.bounds
            bounds.width() to bounds.height()
        } else {
            val metrics = DisplayMetrics()
            (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
            metrics.widthPixels to metrics.heightPixels
        }
    }
}
