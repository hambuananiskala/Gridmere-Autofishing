package com.gridmere.autofishing

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    companion object { const val PREFS = "gridmere_auto_fishing_33" }

    private val captureRequest = 503
    private val notificationRequest = 504
    private lateinit var statusView: TextView
    private lateinit var phaseView: TextView
    private lateinit var progressView: TextView
    private lateinit var leftX: EditText
    private lateinit var leftY: EditText
    private lateinit var rightX: EditText
    private lateinit var rightY: EditText
    private lateinit var pullX: EditText
    private lateinit var pullY: EditText
    private val uiHandler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        buildUi()
        loadSettings()
        uiHandler.post(uiRefresh)
    }

    private fun buildUi() {
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 22, 22, 24)
            setBackgroundColor(0xFFF4F6F8.toInt())
        }
        val scroll = ScrollView(this).apply { addView(root) }
        val title = TextView(this).apply {
            text = "GRIDMERE AUTO FISHING"
            textSize = 21f
            setTextColor(0xFF152238.toInt())
            setPadding(0, 0, 0, 4)
        }
        root.addView(title)
        root.addView(TextView(this).apply {
            text = "Android v3.4 beta  •  logika progres PC v3.4"
            textSize = 13f
            setTextColor(0xFF536273.toInt())
        })

        statusView = TextView(this).apply {
            text = "Status: belum aktif"
            textSize = 16f
            setTextColor(0xFF1C6B3B.toInt())
            setPadding(0, 18, 0, 4)
        }
        phaseView = TextView(this).apply { text = "Fase: STOPPED"; textSize = 13f; setTextColor(0xFF374151.toInt()) }
        progressView = TextView(this).apply { text = "Progress: —"; textSize = 13f; setTextColor(0xFF374151.toInt()) }
        root.addView(statusView)
        root.addView(phaseView)
        root.addView(progressView)

        root.addView(sectionLabel("1. AKTIFKAN LAYANAN"))
        root.addView(actionButton("Buka pengaturan Accessibility") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(actionButton("Izinkan tangkapan layar") {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            startActivityForResult(manager.createScreenCaptureIntent(), captureRequest)
        })
        root.addView(actionButton("MULAI AUTO FISH") {
            saveSettings()
            val captureReady = prefs.getBoolean("capture_active", false)
            val accessibilityReady = FishingAccessibilityService.instance != null
            if (!captureReady) {
                toast("Izinkan tangkapan layar terlebih dahulu.")
            } else if (!accessibilityReady) {
                toast("Aktifkan Accessibility dahulu.")
            } else {
                prefs.edit().putBoolean("auto_enabled", true).apply()
                toast("Auto Fishing aktif. Buka Gridmere.")
            }
        })
        root.addView(actionButton("BERHENTI / EMERGENCY STOP") {
            prefs.edit().putBoolean("auto_enabled", false).apply()
            FishingAccessibilityService.instance?.setDirection(null)
            toast("Auto Fishing dihentikan.")
        })
        root.addView(actionButton("HENTIKAN TANGKAPAN LAYAR") {
            stopService(Intent(this, CaptureService::class.java).setAction(CaptureService.ACTION_STOP))
            prefs.edit().putBoolean("capture_active", false).putBoolean("auto_enabled", false).apply()
            FishingAccessibilityService.instance?.setDirection(null)
        })

        root.addView(sectionLabel("2. POSISI TOMBOL GAME"))
        root.addView(TextView(this).apply {
            text = "Masukkan posisi pusat tombol sebagai persentase layar (0–100). Contoh X 20, Y 90 berarti kiri bawah. Sesuaikan dengan tata letak Gridmere di HP."
            textSize = 12f; setTextColor(0xFF536273.toInt())
        })
        leftX = numberField("Kiri X (%)", "20")
        leftY = numberField("Kiri Y (%)", "90")
        rightX = numberField("Kanan X (%)", "80")
        rightY = numberField("Kanan Y (%)", "90")
        pullX = numberField("Tarik X (%)", "50")
        pullY = numberField("Tarik Y (%)", "90")
        root.addView(twoColumnRow(leftX, leftY))
        root.addView(twoColumnRow(rightX, rightY))
        root.addView(twoColumnRow(pullX, pullY))
        root.addView(actionButton("SIMPAN PENGATURAN") { saveSettings(); toast("Pengaturan tersimpan.") })

        root.addView(sectionLabel("3. PERBAIKAN v3.4"))
        root.addView(TextView(this).apply {
            text = "• Ambang tarik: 98,5%\n• Harus terdeteksi 2 kali berturut-turut\n• Posisi dihitung dari ujung kanan marker\n• Satu frame marker yang terlewat ditoleransi\n• Dua frame hilang berturut-turut mereset konfirmasi\n• Kehilangan kotak ditoleransi 4,5 detik\n• Marker progres tetap dipantau selama 3 detik grace period"
            textSize = 13f; setTextColor(0xFF263445.toInt()); setPadding(0, 4, 0, 8)
        })
        root.addView(TextView(this).apply {
            text = "Catatan: Android beta ini mengikuti aturan progres PC v3.4. Perhitungan marker progres sudah diperbaiki agar tidak menambahkan offset area dua kali dan memakai ujung kanan marker. Posisi tombol serta tata letak minigame tetap perlu diuji pada perangkatmu. Dialog hasil dan cast pertama masih perlu dikonfirmasi manual. Awasi satu siklus uji sebelum digunakan lebih lama."
            textSize = 12f; setTextColor(0xFF8A4B12.toInt()); setPadding(0, 8, 0, 18)
        })
        setContentView(scroll)
    }

    private fun sectionLabel(value: String) = TextView(this).apply {
        text = value; textSize = 14f; setTextColor(0xFF152238.toInt())
        setPadding(0, 18, 0, 7)
    }

    private fun actionButton(label: String, action: () -> Unit) = Button(this).apply {
        text = label; isAllCaps = false
        setOnClickListener { action() }
    }

    private fun numberField(label: String, default: String): EditText {
        return EditText(this).apply {
            hint = label; setText(default); textSize = 13f
            inputType = android.text.InputType.TYPE_CLASS_NUMBER or android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
            setSingleLine(true)
        }
    }

    private fun twoColumnRow(first: EditText, second: EditText): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(first, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginEnd = 8 })
        addView(second, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun loadSettings() {
        val p = getSharedPreferences(PREFS, MODE_PRIVATE)
        leftX.setText(p.getFloat("left_x", 20f).toString())
        leftY.setText(p.getFloat("left_y", 90f).toString())
        rightX.setText(p.getFloat("right_x", 80f).toString())
        rightY.setText(p.getFloat("right_y", 90f).toString())
        pullX.setText(p.getFloat("pull_x", 50f).toString())
        pullY.setText(p.getFloat("pull_y", 90f).toString())
    }

    private fun saveSettings() {
        val p = getSharedPreferences(PREFS, MODE_PRIVATE)
        p.edit()
            .putFloat("left_x", value(leftX, 20f)).putFloat("left_y", value(leftY, 90f))
            .putFloat("right_x", value(rightX, 80f)).putFloat("right_y", value(rightY, 90f))
            .putFloat("pull_x", value(pullX, 50f)).putFloat("pull_y", value(pullY, 90f))
            .apply()
    }

    private fun value(field: EditText, fallback: Float): Float =
        (field.text.toString().toFloatOrNull() ?: fallback).coerceIn(0f, 100f)

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), notificationRequest)
        }
    }

    @Deprecated("Used to support the MediaProjection consent activity on older Android versions")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == captureRequest && resultCode == RESULT_OK && data != null) {
            val serviceIntent = Intent(this, CaptureService::class.java).apply {
                putExtra("resultCode", resultCode)
                putExtra("projectionData", data)
            }
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(serviceIntent) else startService(serviceIntent)
            toast("Tangkapan layar aktif.")
        }
    }

    private val uiRefresh = object : Runnable {
        override fun run() {
            val p = getSharedPreferences(PREFS, MODE_PRIVATE)
            val enabled = p.getBoolean("auto_enabled", false)
            statusView.text = if (enabled) "Status: AUTO FISH AKTIF" else "Status: NONAKTIF"
            phaseView.text = "Fase: ${p.getString("phase", "STOPPED") ?: "STOPPED"}"
            val progress = p.getFloat("progress", -1f)
            progressView.text = if (progress >= 0f) "Progress: %.1f%%".format(progress) else "Progress: —"
            uiHandler.postDelayed(this, 450L)
        }
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        uiHandler.removeCallbacks(uiRefresh)
        super.onDestroy()
    }
}
