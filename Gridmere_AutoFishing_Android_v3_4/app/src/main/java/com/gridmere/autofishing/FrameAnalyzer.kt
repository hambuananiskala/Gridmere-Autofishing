package com.gridmere.autofishing

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Screen-only Android port of the Gridmere PC v3.4 detector rules. */
class FrameAnalyzer(private val context: Context) {
    private data class Blob(val area: Int, val x: Int, val y: Int, val width: Int, val height: Int)
    private data class Detection(val centerX: Float, val width: Float)
    private data class Progress(val x: Float, val fraction: Float)
    private data class Geometry(val scale: Float, val offsetX: Float, val offsetY: Float)

    private var lastAnalyzedAt = 0L
    private var enabledBefore = false
    private var phase = "STOPPED"
    private var strikeCount = 0
    private var lastBiteScan = 0L
    private var lastProgressScan = 0L
    private var lastProgressSeenAt = 0L
    private var missingBoxSince = 0L
    private var waitMinigameStarted = 0L
    private var previousFishX: Float? = null
    private var previousFishAt = 0L
    private var fishVelocity = 0f
    private var previousBoxX: Float? = null
    private var progressFullCount = 0
    private var progressMissingCount = 0
    private var lastStatus = ""
    private var lastPublishAt = 0L

    fun process(bitmap: Bitmap) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAnalyzedAt < 55L) return
        lastAnalyzedAt = now

        val prefs = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        val enabled = prefs.getBoolean("auto_enabled", false)
        if (!enabled) {
            if (enabledBefore) FishingAccessibilityService.instance?.setDirection(null)
            enabledBefore = false
            phase = "STOPPED"
            resetTracking()
            publish("NONAKTIF — tekan MULAI AUTO FISH", null)
            return
        }
        if (FishingAccessibilityService.instance == null) {
            publish("Accessibility belum aktif", null)
            return
        }
        if (!enabledBefore) {
            enabledBefore = true
            phase = "WAIT_BITE"
            strikeCount = 0
            waitMinigameStarted = now
            resetTracking()
            prefs.edit().putFloat("progress", -1f).putString("direction", null).apply()
            publish("AKTIF — menunggu tanda sambaran", null)
        }

        val width = bitmap.width
        val height = bitmap.height
        if (width < 100 || height < 100) return
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val g = geometry(width, height)

        val box = detectPlayerBox(pixels, width, height, g, previousBoxX)
        if (box != null && phase in setOf("WAIT_BITE", "WAIT_MINIGAME", "MINIGAME")) {
            if (phase != "MINIGAME") {
                previousFishX = null
                previousFishAt = 0L
                fishVelocity = 0f
                previousBoxX = null
                missingBoxSince = 0L
                lastProgressSeenAt = now
                progressFullCount = 0
                phase = "MINIGAME"
                setPhase("MINIGAME", "MINIGAME — mengikuti posisi ikan")
            }
            previousBoxX = box.centerX
            missingBoxSince = 0L
            trackFish(pixels, width, height, g, box, now, prefs)
        } else {
            FishingAccessibilityService.instance?.setDirection(null)
            if (phase == "MINIGAME" && missingBoxSince == 0L) missingBoxSince = now
        }

        // The progress marker is checked independently from the player-box outline.
        if (phase == "WAIT_MINIGAME" || phase == "MINIGAME") {
            if (now - lastProgressScan >= 55L) {
                lastProgressScan = now
                val marker = detectProgress(pixels, width, height, g)
                if (marker == null) {
                    // Match PC v3.4: one missed marker frame is tolerated; two misses
                    // in a row clear the near-full confirmation counter.
                    progressMissingCount++
                    if (progressMissingCount >= 2) progressFullCount = 0
                } else {
                    progressMissingCount = 0
                    lastProgressSeenAt = now
                    prefs.edit().putFloat("progress", marker.fraction * 100f).apply()
                    if (phase == "WAIT_MINIGAME") {
                        phase = "MINIGAME"
                        missingBoxSince = if (box == null) now else 0L
                        setPhase("MINIGAME", "MINIGAME terdeteksi — memantau progres")
                    }
                    if (phase == "MINIGAME" && marker.fraction >= 0.985f) {
                        progressFullCount++
                    } else {
                        progressFullCount = 0
                    }
                    if (phase == "MINIGAME" && progressFullCount >= 2) {
                        progressFullCount = 0
                        progressMissingCount = 0
                        FishingAccessibilityService.instance?.setDirection(null)
                        FishingAccessibilityService.instance?.tapPull()
                        phase = "WAIT_RESULT"
                        setPhase("WAIT_RESULT", "PROGRES ≥98,5% terkonfirmasi 2 kali — tombol tarik ditekan")
                        publish("Tarikan dikirim setelah progres hampir penuh", marker.fraction * 100f)
                    }
                }
            }
        }

        if (phase == "WAIT_BITE" && now - lastBiteScan >= 90L) {
            lastBiteScan = now
            if (detectStrike(pixels, width, height)) strikeCount++ else strikeCount = 0
            if (strikeCount >= 2) {
                strikeCount = 0
                FishingAccessibilityService.instance?.tapPull()
                waitMinigameStarted = now
                phase = "WAIT_MINIGAME"
                setPhase("WAIT_MINIGAME", "TANDA SAMBARAN — tombol tarik pertama ditekan")
            }
        }

        if (phase == "WAIT_MINIGAME" && now - waitMinigameStarted > 16_000L) {
            phase = "WAIT_BITE"
            strikeCount = 0
            setPhase("WAIT_BITE", "Minigame tidak terdeteksi — kembali menunggu sambaran")
        }

        if (phase == "MINIGAME" && box == null && missingBoxSince > 0L) {
            val noProgressFor = if (lastProgressSeenAt > 0L) now - lastProgressSeenAt else now - missingBoxSince
            if (now - missingBoxSince >= 4_500L && noProgressFor >= 3_000L) {
                FishingAccessibilityService.instance?.setDirection(null)
                phase = "WAIT_RESULT"
                setPhase("WAIT_RESULT", "Area minigame hilang — menunggu hasil tangkapan")
            }
        }

        // WAIT_RESULT is deliberately held until the user handles the result dialog.
        // Re-enable automation after closing it to begin another cycle safely.
    }

    private fun geometry(width: Int, height: Int): Geometry {
        val scale = min(width / 1920f, height / 1080f)
        return Geometry(scale, (width - 1920f * scale) / 2f, (height - 1080f * scale) / 2f)
    }

    private fun detectStrike(p: IntArray, w: Int, h: Int): Boolean {
        val bandH = max(20, (h * 0.065f).toInt())
        val x0 = (w * 0.04f).toInt()
        val x1 = (w * 0.96f).toInt()
        val sampleStep = max(1, (x1 - x0) / 360)
        var topDark = 0
        var bottomDark = 0
        var count = 0
        for (x in x0 until x1 step sampleStep) {
            for (dy in 0 until bandH step max(1, bandH / 12)) {
                if (maxRgb(p[dy * w + x]) < 28) topDark++
                val by = h - bandH + dy
                if (by in 0 until h && maxRgb(p[by * w + x]) < 28) bottomDark++
                count++
            }
        }
        return count > 0 && topDark.toFloat() / count >= 0.20f && bottomDark.toFloat() / count >= 0.20f
    }

    private fun trackFish(
        pixels: IntArray, width: Int, height: Int, g: Geometry,
        box: Detection, now: Long, prefs: android.content.SharedPreferences
    ) {
        val fish = detectFish(pixels, width, height, g, previousFishX)
        if (fish == null) {
            previousFishX = null
            previousFishAt = 0L
            fishVelocity = 0f
            publish("MINIGAME — mencari ikon ikan", prefs.getFloat("progress", -1f))
            return
        }

        val priorX = previousFishX
        if (priorX != null && previousFishAt > 0L) {
            val dt = max(0.001f, (now - previousFishAt) / 1000f)
            val instant = ((fish.centerX - priorX) / dt).coerceIn(-1800f, 1800f)
            fishVelocity = 0.60f * fishVelocity + 0.40f * instant
        } else fishVelocity = 0f

        val response = 4f
        val lead = 0.050f + (response - 1f) * 0.022f
        val barLeft = g.offsetX + 550f * g.scale
        val barRight = g.offsetX + 1370f * g.scale
        val predicted = (fish.centerX + fishVelocity * lead).coerceIn(barLeft, barRight)
        val error = predicted - box.centerX
        val safeMargin = max(3f * g.scale, (box.width - fish.width) * 0.5f - 3f * g.scale)
        val deadzone = max(2f * g.scale, min(7f * g.scale, safeMargin * (0.34f / response)))
        val releaseZone = max(1.3f * g.scale, deadzone * 0.42f)
        val current = prefs.getString("direction", null)
        val desired = when (current) {
            "left" -> if (error < -releaseZone) "left" else null
            "right" -> if (error > releaseZone) "right" else null
            else -> when {
                error < -deadzone -> "left"
                error > deadzone -> "right"
                else -> null
            }
        }
        FishingAccessibilityService.instance?.setDirection(desired)
        previousFishX = fish.centerX
        previousFishAt = now
        prefs.edit().putString("direction", desired).apply()
        publish(if (desired == null) "IKAN DI DALAM KOTAK — menjaga posisi" else "MENGIKUTI IKAN — ${desired.uppercase()}", prefs.getFloat("progress", -1f))
    }

    private fun detectPlayerBox(
        p: IntArray, w: Int, h: Int, g: Geometry, previousX: Float?
    ): Detection? {
        val s = g.scale
        val laneX = g.offsetX + 450f * s
        val laneY = g.offsetY + 60f * s
        val x0 = (laneX + 15f * s).toInt().coerceIn(0, w - 1)
        val y0 = (laneY + 20f * s).toInt().coerceIn(0, h - 1)
        val x1 = (laneX + 990f * s).toInt().coerceIn(x0 + 1, w)
        val y1 = (laneY + 108f * s).toInt().coerceIn(y0 + 1, h)
        val blobs = components(p, w, x0, y0, x1, y1) { c ->
            val r = red(c); val gg = green(c); val b = blue(c)
            gg > 145 && r > 55 && r < 190 && gg > r * 1.15f && gg > b * 1.30f
        }
        val candidates = blobs.filter { blob ->
            val aspect = blob.width.toFloat() / max(1, blob.height)
            blob.area >= max(100, (500f * s * s).toInt()) &&
                blob.width in (85f * s).toInt().coerceAtLeast(1)..(145f * s).toInt().coerceAtLeast(2) &&
                blob.height in (45f * s).toInt().coerceAtLeast(1)..(110f * s).toInt().coerceAtLeast(2) &&
                aspect in 1.12f..1.75f
        }
        if (candidates.isEmpty()) return null
        val chosen = if (previousX == null) candidates.maxByOrNull { it.area }!!
                     else candidates.minByOrNull { abs((it.x + it.width / 2f) - previousX) }!!
        return Detection(chosen.x + chosen.width / 2f, chosen.width.toFloat())
    }

    private fun detectFish(
        p: IntArray, w: Int, h: Int, g: Geometry, previousX: Float?
    ): Detection? {
        val s = g.scale
        val laneX = g.offsetX + 450f * s
        val laneY = g.offsetY + 60f * s
        val x0 = (laneX + 45f * s).toInt().coerceIn(0, w - 1)
        val y0 = (laneY + 25f * s).toInt().coerceIn(0, h - 1)
        val x1 = (laneX + 995f * s).toInt().coerceIn(x0 + 1, w)
        val y1 = (laneY + 80f * s).toInt().coerceIn(y0 + 1, h)
        val blobs = components(p, w, x0, y0, x1, y1) { c ->
            val r = red(c); val gg = green(c); val b = blue(c)
            (b in 88..128 && gg in 24..60 && r <= 14) || (gg >= 225 && r <= 45 && b <= 50)
        }
        val candidates = blobs.filter { blob ->
            val aspect = blob.width.toFloat() / max(1, blob.height)
            blob.area >= max(65, (250f * s * s).toInt()) &&
                blob.width >= 28f * s && blob.width <= 120f * s &&
                blob.height >= 15f * s && blob.height <= 70f * s &&
                aspect in 0.80f..2.50f
        }
        if (candidates.isEmpty()) return null
        val chosen = if (previousX == null) candidates.maxByOrNull { it.area }!!
                     else candidates.minByOrNull { abs((it.x + it.width / 2f) - previousX) }!!
        return Detection(chosen.x + chosen.width / 2f, chosen.width.toFloat())
    }

    private fun detectProgress(p: IntArray, w: Int, h: Int, g: Geometry): Progress? {
        val s = g.scale
        val originX = g.offsetX + 540f * s
        val originY = g.offsetY + 156f * s
        val x0 = originX.toInt().coerceIn(0, w - 1)
        val y0 = originY.toInt().coerceIn(0, h - 1)
        val x1 = (g.offsetX + 1380f * s).toInt().coerceIn(x0 + 1, w)
        val y1 = (g.offsetY + 190f * s).toInt().coerceIn(y0 + 1, h)
        val blobs = components(p, w, x0, y0, x1, y1) { c ->
            blue(c) > 180 && green(c) > 220 && red(c) > 185
        }
        val minHeight = max(8, (12f * s).toInt())
        val minArea = max(18, (24f * s * s).toInt())
        val maxWidth = max(7, (9f * s).toInt())
        val candidates = blobs.filter {
            it.area >= minArea && it.height >= minHeight && it.width in 1..maxWidth
        }.mapNotNull {
            // components() returns absolute screen coordinates (x0 was added there).
            // Use the marker's advancing right edge, as PC v3.4 does. Do not add
            // originX again: that caused the Android v3.3 detector to double-offset it.
            val screenX = (it.x + it.width).toFloat()
            val barLeft = g.offsetX + 550f * s
            val barRight = g.offsetX + 1370f * s
            val fraction = (screenX - barLeft) / max(1f, barRight - barLeft)
            if (fraction in 0.02f..1.04f) Progress(screenX, fraction) else null
        }
        return candidates.maxByOrNull { it.fraction }
    }

    private fun components(
        p: IntArray, screenWidth: Int, x0: Int, y0: Int, x1: Int, y1: Int,
        matches: (Int) -> Boolean
    ): List<Blob> {
        val rw = x1 - x0
        val rh = y1 - y0
        if (rw <= 0 || rh <= 0) return emptyList()
        val total = rw * rh
        val mask = BooleanArray(total)
        for (y in 0 until rh) {
            var src = (y0 + y) * screenWidth + x0
            val dst = y * rw
            for (x in 0 until rw) mask[dst + x] = matches(p[src++])
        }
        val visited = BooleanArray(total)
        val queue = IntArray(total)
        val result = ArrayList<Blob>()
        for (start in 0 until total) {
            if (!mask[start] || visited[start]) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            visited[start] = true
            var minX = rw; var minY = rh; var maxX = -1; var maxY = -1
            var area = 0
            while (head < tail) {
                val index = queue[head++]
                val x = index % rw
                val y = index / rw
                area++
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx; val ny = y + dy
                    if (nx !in 0 until rw || ny !in 0 until rh) continue
                    val ni = ny * rw + nx
                    if (mask[ni] && !visited[ni]) {
                        visited[ni] = true
                        queue[tail++] = ni
                    }
                }
            }
            if (maxX >= minX && maxY >= minY) {
                result.add(Blob(area, x0 + minX, y0 + minY, maxX - minX + 1, maxY - minY + 1))
            }
        }
        return result
    }

    private fun resetTracking() {
        previousFishX = null
        previousFishAt = 0L
        fishVelocity = 0f
        previousBoxX = null
        progressFullCount = 0
        progressMissingCount = 0
        missingBoxSince = 0L
        lastProgressSeenAt = 0L
    }

    private fun setPhase(value: String, status: String) {
        phase = value
        context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
            .edit().putString("phase", value).putString("status", status).apply()
        lastStatus = status
    }

    private fun publish(status: String, progress: Float?) {
        val prefs = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE)
        val now = SystemClock.elapsedRealtime()
        if (status != lastStatus || now - lastPublishAt >= 300L) {
            val editor = prefs.edit().putString("phase", phase).putString("status", status)
            if (progress != null) editor.putFloat("progress", progress)
            editor.apply()
            lastStatus = status
            lastPublishAt = now
        }
    }

    private fun maxRgb(color: Int) = max(red(color), max(green(color), blue(color)))
    private fun red(color: Int) = (color shr 16) and 0xFF
    private fun green(color: Int) = (color shr 8) and 0xFF
    private fun blue(color: Int) = color and 0xFF
}
