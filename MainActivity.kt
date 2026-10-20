package com.example.flashbeat

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaPlayer
import android.media.audiofx.Visualizer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    private val pickAudio = 101
    private val permissionRequest = 102
    private var player: MediaPlayer? = null
    private var visualizer: Visualizer? = null
    private var cameraId: String? = null
    private var flashOn = false
    private var lastBeatAt = 0L
    private var previousEnergy = 0.0
    private var sensitivity = 55
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var status: TextView
    private lateinit var playButton: Button
    private lateinit var sensitivityLabel: TextView
    private var selectedUri: Uri? = null
    private var hasAudioPermission = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = android.graphics.Color.rgb(15, 18, 28)
        window.navigationBarColor = android.graphics.Color.rgb(15, 18, 28)
        buildUi()
        findFlashCamera()
        requestNeededPermissions()
    }

    private fun buildUi() {
        val bg = android.graphics.Color.rgb(15, 18, 28)
        val white = android.graphics.Color.WHITE
        val accent = android.graphics.Color.rgb(0, 200, 170)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(28, 32, 28, 32)
            setBackgroundColor(bg)
        }
        val title = TextView(this).apply {
            text = "FLASH BEAT"
            textSize = 30f
            setTextColor(accent)
            gravity = Gravity.CENTER
        }
        val subtitle = TextView(this).apply {
            text = "موسیقی با نور هماهنگ می‌شود"
            textSize = 17f
            setTextColor(white)
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 26)
        }
        status = TextView(this).apply {
            text = "ابتدا یک آهنگ انتخاب کنید"
            textSize = 16f
            setTextColor(white)
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 22)
        }
        val choose = makeButton("🎵 انتخاب آهنگ", accent)
        choose.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "audio/*"
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
            }
            startActivityForResult(intent, pickAudio)
        }
        playButton = makeButton("▶ پخش و هماهنگی فلش", accent)
        playButton.setOnClickListener { togglePlayback() }
        val stop = makeButton("■ توقف و خاموش کردن فلش", android.graphics.Color.rgb(180, 65, 75))
        stop.setOnClickListener { stopPlayback() }
        sensitivityLabel = TextView(this).apply {
            text = "حساسیت تشخیص ضرب: $sensitivity%"
            textSize = 15f
            setTextColor(white)
            gravity = Gravity.CENTER
            setPadding(0, 22, 0, 4)
        }
        val seek = SeekBar(this).apply {
            max = 100
            progress = sensitivity
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                    sensitivity = value.coerceAtLeast(5)
                    sensitivityLabel.text = "حساسیت تشخیص ضرب: $sensitivity%"
                }
                override fun onStartTrackingTouch(bar: SeekBar?) {}
                override fun onStopTrackingTouch(bar: SeekBar?) {}
            })
        }
        val note = TextView(this).apply {
            text = "نکته: برای استفاده از فلش، مجوز دوربین و صدا لازم است. تشخیص ضرب تقریبی است و در هر آهنگی یکسان عمل نمی‌کند."
            textSize = 12f
            setTextColor(android.graphics.Color.LTGRAY)
            gravity = Gravity.CENTER
            setPadding(0, 22, 0, 0)
        }
        listOf(title, subtitle, status, choose, playButton, stop, sensitivityLabel, seek, note).forEach {
            layout.addView(it)
        }
        setContentView(layout)
    }

    private fun makeButton(label: String, color: Int): Button =
        Button(this).apply {
            text = label
            textSize = 15f
            setTextColor(android.graphics.Color.WHITE)
            setBackgroundTintList(android.content.res.ColorStateList.valueOf(color))
            isAllCaps = false
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            lp.setMargins(0, 7, 0, 7)
            layoutParams = lp
        }

    private fun requestNeededPermissions() {
        val needed = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.CAMERA)
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED)
            needed.add(Manifest.permission.RECORD_AUDIO)
        if (needed.isNotEmpty()) requestPermissions(needed.toTypedArray(), permissionRequest)
        else hasAudioPermission = true
    }

    private fun findFlashCamera() {
        try {
            val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cameraId = manager.cameraIdList.firstOrNull { id ->
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true &&
                manager.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
            }
        } catch (_: Exception) {
            cameraId = null
        }
    }

    @Deprecated("Deprecated in Android; retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickAudio && resultCode == RESULT_OK) {
            selectedUri = data?.data
            if (selectedUri != null) {
                try {
                    contentResolver.takePersistableUriPermission(
                        selectedUri!!, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: Exception) {}
                status.text = "آهنگ انتخاب شد؛ آمادهٔ پخش"
                startSong()
            }
        }
    }

    private fun togglePlayback() {
        val p = player
        if (p == null) {
            if (selectedUri == null) {
                Toast.makeText(this, "ابتدا آهنگ انتخاب کنید", Toast.LENGTH_SHORT).show()
                return
            }
            startSong()
        } else if (p.isPlaying) {
            p.pause()
            stopVisualizer()
            turnFlashOff()
            playButton.text = "▶ ادامه و هماهنگی فلش"
            status.text = "موسیقی متوقف موقت شد"
        } else {
            p.start()
            startVisualizer()
            playButton.text = "Ⅱ مکث"
            status.text = "در حال تحلیل ریتم آهنگ…"
        }
    }

    private fun startSong() {
        val uri = selectedUri ?: return
        stopPlayback()
        try {
            player = MediaPlayer().apply {
                setDataSource(this@MainActivity, uri)
                setOnPreparedListener { mp ->
                    mp.start()
                    startVisualizer()
                    playButton.text = "Ⅱ مکث"
                    status.text = "در حال پخش و تشخیص ضرب"
                }
                setOnCompletionListener {
                    stopPlayback()
                    status.text = "آهنگ تمام شد"
                }
                prepareAsync()
            }
            status.text = "در حال آماده‌سازی آهنگ…"
        } catch (e: Exception) {
            status.text = "پخش این فایل ممکن نشد"
            Toast.makeText(this, "فرمت فایل پشتیبانی نشد یا فایل در دسترس نیست", Toast.LENGTH_LONG).show()
        }
    }

    private fun startVisualizer() {
        stopVisualizer()
        val p = player ?: return
        if (!hasAudioPermission || checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            status.text = "برای تحلیل ریتم، مجوز صدا لازم است"
            return
        }
        try {
            val v = Visualizer(p.audioSessionId)
            v.captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
            v.setDataCaptureListener(object : Visualizer.OnDataCaptureListener {
                override fun onWaveFormDataCapture(viz: Visualizer?, waveform: ByteArray?, samplingRate: Int) {
                    if (waveform == null || player?.isPlaying != true) return
                    var sum = 0.0
                    for (b in waveform) {
                        val centered = (b.toInt() and 0xFF) - 128
                        sum += centered * centered
                    }
                    val energy = sum / waveform.size.coerceAtLeast(1)
                    val now = System.currentTimeMillis()
                    val threshold = previousEnergy * (1.10 + (100 - sensitivity) / 80.0)
                    if (previousEnergy > 0 && energy > threshold && energy > 100.0 &&
                        now - lastBeatAt > 220L) {
                        lastBeatAt = now
                        pulseFlash()
                    }
                    previousEnergy = previousEnergy * 0.65 + energy * 0.35
                }
                override fun onFftDataCapture(viz: Visualizer?, fft: ByteArray?, samplingRate: Int) {}
            }, Visualizer.getMaxCaptureRate() / 2, true, false)
            v.enabled = true
            visualizer = v
        } catch (_: Exception) {
            status.text = "تحلیل صدا در این دستگاه در دسترس نیست"
        }
    }

    private fun pulseFlash() {
        val id = cameraId ?: return
        try {
            val manager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
            if (!flashOn) {
                manager.setTorchMode(id, true)
                flashOn = true
                handler.postDelayed({
                    try {
                        manager.setTorchMode(id, false)
                    } catch (_: Exception) {}
                    flashOn = false
                }, 90L)
            }
        } catch (_: Exception) {
            turnFlashOff()
        }
    }

    private fun stopVisualizer() {
        try { visualizer?.enabled = false } catch (_: Exception) {}
        try { visualizer?.release() } catch (_: Exception) {}
        visualizer = null
        previousEnergy = 0.0
    }

    private fun turnFlashOff() {
        handler.removeCallbacksAndMessages(null)
        try {
            cameraId?.let { (getSystemService(Context.CAMERA_SERVICE) as CameraManager).setTorchMode(it, false) }
        } catch (_: Exception) {}
        flashOn = false
    }

    private fun stopPlayback() {
        stopVisualizer()
        turnFlashOff()
        try { player?.stop() } catch (_: Exception) {}
        try { player?.release() } catch (_: Exception) {}
        player = null
        playButton.text = "▶ پخش و هماهنگی فلش"
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, results)
        if (requestCode == permissionRequest) {
            hasAudioPermission = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "بدون مجوز دوربین، فلش قابل استفاده نیست", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        // Safety: do not leave the torch flashing when the app is backgrounded.
        turnFlashOff()
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
    }
}
