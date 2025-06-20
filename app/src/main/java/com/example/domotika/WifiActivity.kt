package com.example.domotika

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.Camera
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.os.PowerManager
import android.util.Base64
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

class WifiActivity : AppCompatActivity(), SurfaceHolder.Callback {

    companion object {
        private const val CAMERA_PERMISSION_CODE = 100
        private const val AUDIO_PERMISSION_CODE = 101
        private const val STORAGE_PERMISSION_CODE = 102
    }

    // Views
    private lateinit var surfaceView: SurfaceView
    private lateinit var surfaceHolder: SurfaceHolder
    private lateinit var noCameraOverlay: LinearLayout
    private lateinit var liveOverlay: LinearLayout
    private lateinit var tvStreamStatus: TextView
    private lateinit var tvStreamTime: TextView
    private lateinit var tvViewerCount: TextView
    private lateinit var tvFPSValue: TextView
    private lateinit var statusIndicator: View

    // 🆕 VISTAS PARA TOKENS
    private lateinit var tvStreamToken: TextView
    private lateinit var btnCopyToken: MaterialButton
    private lateinit var btnGenerateToken: MaterialButton

    // Botones principales
    private lateinit var btnStartStream: MaterialButton
    private lateinit var btnStopStream: MaterialButton
    private lateinit var btnOpenViewer: MaterialButton
    private lateinit var fabSwitchCamera: FloatingActionButton
    private lateinit var fabFlashlight: FloatingActionButton

    // Controles
    private lateinit var spinnerQuality: Spinner
    private lateinit var seekBarFPS: SeekBar

    // 🔑 VARIABLE DE TOKEN
    private var streamToken: String? = null

    // Cámara y streaming
    private var camera: Camera? = null
    private var isStreaming = false
    private var isFlashlightOn = false
    private var currentCameraId = Camera.CameraInfo.CAMERA_FACING_BACK
    private var isSurfaceReady = false
    private val streamUrl = "https://domotica.bsite.net/api/stream"

    // Timer y control de memoria
    private val handler = Handler()
    private var streamStartTime = 0L
    private var timeUpdateRunnable: Runnable? = null
    private var streamingRunnable: Runnable? = null

    // 🆕 CONTROL DE PANTALLA Y MEMORIA AVANZADO
    private var wakeLock: PowerManager.WakeLock? = null
    private var memoryCleanupRunnable: Runnable? = null
    private var isStreamPaused = false
    private var pauseCount = 0
    private val frameBuffer = mutableListOf<ByteArrayOutputStream>() // Pool de objetos reutilizables

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi)

        // 🔧 MANTENER PANTALLA ENCENDIDA
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 🔧 CONFIGURAR WAKE LOCK
        setupWakeLock()

        initViews()
        setupSpinner()
        setupSeekBar()
        setupClickListeners()
        checkAllPermissions()

        // 🆕 INICIAR LIMPIEZA AUTOMÁTICA DE MEMORIA
        startMemoryCleanup()
    }

    // 🆕 CONFIGURAR WAKE LOCK PARA MANTENER DISPOSITIVO ACTIVO
    private fun setupWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Domotika::StreamWakeLock"
        )
    }

    // 🆕 LIMPIEZA AUTOMÁTICA DE MEMORIA CADA 30 SEGUNDOS
    private fun startMemoryCleanup() {
        memoryCleanupRunnable = object : Runnable {
            override fun run() {
                performMemoryCleanup()
                handler.postDelayed(this, 30000) // Cada 30 segundos
            }
        }
        handler.post(memoryCleanupRunnable!!)
    }

    // 🆕 LIMPIEZA AGRESIVA DE MEMORIA
    private fun performMemoryCleanup() {
        try {
            // Limpiar buffer de frames
            frameBuffer.clear()

            // Forzar recolección de basura múltiple
            System.gc()
            Thread.sleep(100)
            System.runFinalization()
            System.gc()

            val runtime = Runtime.getRuntime()
            val usedMemory = runtime.totalMemory() - runtime.freeMemory()
            val maxMemory = runtime.maxMemory()
            val percentUsed = (usedMemory * 100) / maxMemory

            Log.d("MEMORY_CLEANUP", "Memoria después de limpieza: ${percentUsed}%")

            // Si sigue alta, pausa temporal del stream
            if (percentUsed > 85 && isStreaming && !isStreamPaused) {
                pauseStreamTemporarily()
            } else if (percentUsed < 60 && isStreamPaused) {
                resumeStreamFromPause()
            }

        } catch (e: Exception) {
            Log.e("MEMORY_CLEANUP", "Error en limpieza: ${e.message}")
        }
    }

    // 🆕 PAUSAR STREAM TEMPORALMENTE POR MEMORIA
    private fun pauseStreamTemporarily() {
        isStreamPaused = true
        pauseCount++

        runOnUiThread {
            updateStatus("⏸️ Pausado por optimización (${pauseCount}) - Limpiando memoria...", true)
            Toast.makeText(this, "⏸️ Pausa automática #${pauseCount} - Optimizando rendimiento", Toast.LENGTH_SHORT).show()
        }

        // Parar captura temporalmente
        camera?.setPreviewCallback(null)

        Log.w("STREAM_PAUSE", "Stream pausado temporalmente #${pauseCount}")

        // Limpieza agresiva durante la pausa
        handler.postDelayed({
            repeat(3) {
                System.gc()
                System.runFinalization()
                Thread.sleep(200)
            }
        }, 1000)
    }

    // 🆕 REANUDAR STREAM DESPUÉS DE PAUSA
    private fun resumeStreamFromPause() {
        if (isStreamPaused && isStreaming) {
            isStreamPaused = false

            runOnUiThread {
                updateStatus("🔴 Transmitiendo: $streamToken (Reanudado)", true)
                Toast.makeText(this, "✨ Stream reanudado - Sistema optimizado", Toast.LENGTH_SHORT).show()
            }

            Log.i("STREAM_RESUME", "Stream reanudado después de pausa #${pauseCount}")
        }
    }

    private fun initViews() {
        // Views principales
        surfaceView = findViewById(R.id.surfaceView)
        noCameraOverlay = findViewById(R.id.noCameraOverlay)
        liveOverlay = findViewById(R.id.liveOverlay)

        // TextViews
        tvStreamStatus = findViewById(R.id.tvStreamStatus)
        tvStreamTime = findViewById(R.id.tvStreamTime)
        tvViewerCount = findViewById(R.id.tvViewerCount)
        tvFPSValue = findViewById(R.id.tvFPSValue)
        statusIndicator = findViewById(R.id.statusIndicator)

        // 🆕 VISTAS DE TOKEN
        tvStreamToken = findViewById(R.id.tvStreamToken)
        btnCopyToken = findViewById(R.id.btnCopyToken)
        btnGenerateToken = findViewById(R.id.btnGenerateToken)

        // Botones principales
        btnStartStream = findViewById(R.id.btnStartStream)
        btnStopStream = findViewById(R.id.btnStopStream)
        btnOpenViewer = findViewById(R.id.btnOpenViewer)

        // FABs
        fabSwitchCamera = findViewById(R.id.fabSwitchCamera)
        fabFlashlight = findViewById(R.id.fabFlashlight)

        // Controles
        spinnerQuality = findViewById(R.id.spinnerQuality)
        seekBarFPS = findViewById(R.id.seekBarFPS)

        // Configurar Surface
        surfaceHolder = surfaceView.holder
        surfaceHolder.addCallback(this)

        // Mostrar overlay de no cámara inicialmente
        noCameraOverlay.visibility = View.VISIBLE
        liveOverlay.visibility = View.GONE

        // 🆕 INICIALIZAR ESTADO DE BOTONES DE TOKEN
        btnCopyToken.isEnabled = false
    }

    // 🔧 SPINNER CON CONFIGURACIÓN MEJORADA
    private fun setupSpinner() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            arrayOf("Alta (720p)", "Media (480p)", "Baja (320p)")
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerQuality.adapter = adapter
        spinnerQuality.setSelection(2) // 🔧 Calidad baja por defecto para estabilidad

        spinnerQuality.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (camera != null && !isStreaming) {
                    setupCameraParameters()
                    setupCameraPreview()

                    val qualityName = when(position) {
                        0 -> "Alta (720p)"
                        1 -> "Media (480p)"
                        2 -> "Baja (320p)"
                        else -> "Baja"
                    }
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    // 🔧 CONFIGURACIÓN CONSERVADORA PARA ESTABILIDAD
    private fun setupSeekBar() {
        seekBarFPS.max = 10 // Máximo 10 FPS para estabilidad
        seekBarFPS.progress = 3 // 3 FPS por defecto

        seekBarFPS.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val fps = if (progress < 1) 1 else progress
                tvFPSValue.text = "$fps FPS"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        tvFPSValue.text = "3 FPS"
    }

    private fun setupClickListeners() {
        btnStartStream.setOnClickListener { startStreaming() }
        btnStopStream.setOnClickListener { stopStreaming() }

        // 🆕 LISTENERS PARA TOKENS
        btnGenerateToken.setOnClickListener { generateNewToken() }
        btnCopyToken.setOnClickListener { copyTokenToClipboard() }

        // FABs de cámara
        fabSwitchCamera.setOnClickListener { switchCamera() }
        fabFlashlight.setOnClickListener { toggleFlashlight() }

        // Botón para abrir viewer
        btnOpenViewer.setOnClickListener { openViewer() }
    }

    // 🆕 GENERAR TOKEN ÚNICO
    private fun generateNewToken() {
        streamToken = generateUniqueToken()
        tvStreamToken.text = "Token: $streamToken"
        btnCopyToken.isEnabled = true

        Toast.makeText(this, "✨ Token generado exitosamente: $streamToken", Toast.LENGTH_LONG).show()
        Log.d("TOKEN", "Nuevo token generado: $streamToken")
    }

    // 🆕 COPIAR TOKEN AL PORTAPAPELES
    private fun copyTokenToClipboard() {
        streamToken?.let { token ->
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Stream Token", token)
            clipboard.setPrimaryClip(clip)

        }
    }

    // 🆕 GENERAR TOKEN ÚNICO
    private fun generateUniqueToken(): String {
        val timestamp = System.currentTimeMillis()
        val random = (1000..9999).random()
        val deviceId = android.os.Build.MODEL.replace(" ", "").take(3).uppercase()

        return "${deviceId}-${timestamp.toString().takeLast(6)}-${random}"
    }

    private fun checkAllPermissions() {
        val permissionsNeeded = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.CAMERA)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.RECORD_AUDIO)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        // 🆕 PERMISO PARA WAKE LOCK
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.WAKE_LOCK) != PackageManager.PERMISSION_GRANTED) {
            permissionsNeeded.add(Manifest.permission.WAKE_LOCK)
        }

        if (permissionsNeeded.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsNeeded.toTypedArray(), CAMERA_PERMISSION_CODE)
        } else {
            initializeCamera()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_CODE) {
            var cameraGranted = false
            for (i in permissions.indices) {
                if (permissions[i] == Manifest.permission.CAMERA && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    cameraGranted = true
                    break
                }
            }

            if (cameraGranted) {
                initializeCamera()
            } else {
                Toast.makeText(this, "🔐 Se requiere permiso de cámara para continuar", Toast.LENGTH_SHORT).show()
                updateStatus("Sin permisos de cámara", false)
            }
        }
    }

    private fun initializeCamera() {
        if (camera != null) {
            releaseCamera()
        }

        try {
            camera = Camera.open(currentCameraId)
            setupCameraParameters()

            if (isSurfaceReady) {
                setupCameraPreview()
            }

            noCameraOverlay.visibility = View.GONE
            updateStatus("Cámara lista para streaming", false)

            Log.d("CAMERA", "Cámara inicializada correctamente")

        } catch (e: Exception) {
            Log.e("CAMERA", "Error inicializando cámara: ${e.message}")
            Toast.makeText(this, "⚠️ Error al acceder a la cámara", Toast.LENGTH_SHORT).show()
            updateStatus("Error de cámara", false)
            noCameraOverlay.visibility = View.VISIBLE
        }
    }

    // 🔧 CONFIGURACIÓN MÁS CONSERVADORA PARA EVITAR CRASHES
    private fun setupCameraParameters() {
        camera?.let { cam ->
            try {
                val parameters = cam.parameters

                val info = Camera.CameraInfo()
                Camera.getCameraInfo(currentCameraId, info)

                val rotation = windowManager.defaultDisplay.rotation
                var degrees = 0
                when (rotation) {
                    Surface.ROTATION_0 -> degrees = 0
                    Surface.ROTATION_90 -> degrees = 90
                    Surface.ROTATION_180 -> degrees = 180
                    Surface.ROTATION_270 -> degrees = 270
                }

                var result: Int
                if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
                    result = (info.orientation + degrees) % 360
                    result = (360 - result) % 360
                } else {
                    result = (info.orientation - degrees + 360) % 360
                }

                cam.setDisplayOrientation(result)

                // 🔧 RESOLUCIONES MÁS CONSERVADORAS
                val supportedSizes = parameters.supportedPreviewSizes
                val selectedSize = when (spinnerQuality.selectedItemPosition) {
                    0 -> findBestSizeForQuality(supportedSizes, "HIGH")
                    1 -> findBestSizeForQuality(supportedSizes, "MEDIUM")
                    2 -> findBestSizeForQuality(supportedSizes, "LOW")
                    else -> findBestSizeForQuality(supportedSizes, "LOW") // Por defecto: baja
                }

                parameters.setPreviewSize(selectedSize.width, selectedSize.height)
                parameters.previewFormat = ImageFormat.NV21

                val focusModes = parameters.supportedFocusModes
                if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                    parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
                } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    parameters.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
                }

                cam.parameters = parameters
                Log.d("CAMERA", "Resolución configurada: ${selectedSize.width}x${selectedSize.height}")

            } catch (e: Exception) {
                Log.e("CAMERA", "Error configurando parámetros: ${e.message}")
            }
        }
    }

    // 🔧 TAMAÑOS MÁS CONSERVADORES
    private fun findBestSizeForQuality(supportedSizes: List<Camera.Size>, quality: String): Camera.Size {
        val sortedSizes = supportedSizes.sortedByDescending { it.width * it.height }

        return when (quality) {
            "HIGH" -> {
                // Máximo 800x600 para evitar crashes
                sortedSizes.find {
                    it.width <= 800 && it.height <= 600 && it.width >= 640
                } ?: sortedSizes[sortedSizes.size / 2]
            }
            "MEDIUM" -> {
                // Máximo 640x480
                sortedSizes.find {
                    it.width <= 640 && it.height <= 480 && it.width >= 480
                } ?: sortedSizes[sortedSizes.size * 2 / 3]
            }
            "LOW" -> {
                // Máximo 480x360, mínimo aceptable
                sortedSizes.find {
                    it.width <= 480 && it.height <= 360 && it.width >= 240
                } ?: sortedSizes.last()
            }
            else -> sortedSizes.last() // El más pequeño disponible
        }
    }

    private fun setupCameraPreview() {
        camera?.let { cam ->
            try {
                cam.setPreviewDisplay(surfaceHolder)
                cam.startPreview()
                Log.d("CAMERA", "Vista previa iniciada")
            } catch (e: IOException) {
                Log.e("CAMERA", "Error configurando vista previa: ${e.message}")
            }
        }
    }

    private fun releaseCamera() {
        camera?.let { cam ->
            try {
                cam.setPreviewCallback(null)
                cam.stopPreview()
                cam.release()
                Log.d("CAMERA", "Cámara liberada")
            } catch (e: Exception) {
                Log.e("CAMERA", "Error liberando cámara: ${e.message}")
            }
        }
        camera = null
    }

    private fun startStreaming() {
        if (camera != null && !isStreaming) {

            if (streamToken == null) {
                generateNewToken()
            }

            isStreaming = true
            isStreamPaused = false
            pauseCount = 0
            streamStartTime = System.currentTimeMillis()

            // 🔧 ACTIVAR WAKE LOCK AL INICIAR STREAM
            try {
                wakeLock?.acquire(10*60*1000L /*10 minutos*/)
                Log.d("WAKE_LOCK", "Wake lock activado")
            } catch (e: Exception) {
                Log.e("WAKE_LOCK", "Error activando wake lock: ${e.message}")
            }

            // UI Updates
            btnStartStream.isEnabled = false
            btnStopStream.isEnabled = true
            statusIndicator.isSelected = true
            liveOverlay.visibility = View.VISIBLE

            updateStatus("🔴 Transmisión en vivo: $streamToken", true)
            registerStreamOnServer()

            val fps = seekBarFPS.progress.let { if (it < 1) 1 else it }
            val frameInterval = 1000L / fps

            streamingRunnable = object : Runnable {
                override fun run() {
                    if (isStreaming) {
                        // Solo capturar si no está pausado
                        if (!isStreamPaused) {
                            camera?.setOneShotPreviewCallback { data, _ ->
                                if (isStreaming && data != null && !isStreamPaused) {
                                    Thread {
                                        sendFrameOptimized(data)
                                    }.start()
                                }
                            }
                        }

                        handler.postDelayed(this, frameInterval)
                    }
                }
            }

            handler.post(streamingRunnable!!)
            startTimeUpdater()

            Toast.makeText(this, "Pantalla permanecerá activa", Toast.LENGTH_LONG).show()
        } else {
            Toast.makeText(this, "⚠️ Cámara no disponible", Toast.LENGTH_SHORT).show()
        }
    }

    private fun registerStreamOnServer() {
        Thread {
            try {
                val url = URL("https://domotica.bsite.net/api/stream/register")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val jsonData = """
                {
                    "token": "$streamToken",
                    "device_id": "android_${android.os.Build.MODEL}",
                    "stream_name": "Cámara ${if (currentCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT) "Frontal" else "Trasera"}",
                    "timestamp": ${System.currentTimeMillis()}
                }
                """.trimIndent()

                connection.outputStream.write(jsonData.toByteArray())
                connection.outputStream.flush()

                val responseCode = connection.responseCode
                Log.d("REGISTER", "Stream registrado: $responseCode")

                connection.disconnect()
            } catch (e: Exception) {
                Log.e("REGISTER", "Error registrando stream: ${e.message}")
            }
        }.start()
    }

    // 🔧 ENVÍO ULTRA-OPTIMIZADO CON REUTILIZACIÓN DE OBJETOS
    private fun sendFrameOptimized(frameData: ByteArray) {
        try {
            val parameters = camera?.parameters
            val width = parameters?.previewSize?.width ?: 320
            val height = parameters?.previewSize?.height ?: 240

            val yuvImage = YuvImage(frameData, ImageFormat.NV21, width, height, null)

            // 🔧 REUTILIZAR OUTPUTSTREAM SI ES POSIBLE
            val out = if (frameBuffer.isNotEmpty()) {
                frameBuffer.removeAt(0).apply { reset() }
            } else {
                ByteArrayOutputStream()
            }

            // 🔧 CALIDAD MUY CONSERVADORA
            val quality = when (spinnerQuality.selectedItemPosition) {
                0 -> 40  // Alta: 40%
                1 -> 25  // Media: 25%
                2 -> 15  // Baja: 15%
                else -> 15
            }

            yuvImage.compressToJpeg(Rect(0, 0, width, height), quality, out)
            val jpegData = out.toByteArray()

            // 🔧 REUTILIZAR EL OUTPUTSTREAM
            if (frameBuffer.size < 3) { // Máximo 3 en el pool
                frameBuffer.add(out)
            } else {
                out.close()
            }

            val base64Image = Base64.encodeToString(jpegData, Base64.NO_WRAP)
            sendFrameToServer(base64Image)

            Log.d("STREAM", "Frame enviado - ${width}x${height} - Q:$quality% - Size:${jpegData.size}b")

        } catch (e: OutOfMemoryError) {
            Log.e("STREAM", "OutOfMemoryError: ${e.message}")
            // No detener, solo pausar temporalmente
            pauseStreamTemporarily()
        } catch (e: Exception) {
            Log.e("STREAM", "Error enviando frame: ${e.message}")
        } finally {
            // 🔧 LIMPIEZA MÁS SUAVE
            if (System.currentTimeMillis() % 10 == 0L) { // Solo cada 10 frames
                System.gc()
            }
        }
    }

    private fun stopStreaming() {
        isStreaming = false
        isStreamPaused = false

        // 🔧 LIBERAR WAKE LOCK AL DETENER STREAM
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
                Log.d("WAKE_LOCK", "Wake lock liberado")
            }
        } catch (e: Exception) {
            Log.e("WAKE_LOCK", "Error liberando wake lock: ${e.message}")
        }

        // UI Updates
        btnStartStream.isEnabled = true
        btnStopStream.isEnabled = false
        statusIndicator.isSelected = false
        liveOverlay.visibility = View.GONE

        streamingRunnable?.let { handler.removeCallbacks(it) }
        streamingRunnable = null

        camera?.setPreviewCallback(null)
        unregisterStreamFromServer()

        stopTimeUpdater()
        updateStatus("Transmisión finalizada", false)

        // 🔧 LIMPIEZA FINAL MÁS AGRESIVA
        frameBuffer.clear()
        repeat(3) {
            System.gc()
            System.runFinalization()
        }
    }

    private fun unregisterStreamFromServer() {
        Thread {
            try {
                val url = URL("https://domotica.bsite.net/api/stream/unregister")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val jsonData = """{"token": "$streamToken"}"""
                connection.outputStream.write(jsonData.toByteArray())
                connection.outputStream.flush()

                Log.d("UNREGISTER", "Stream desregistrado: $streamToken")
                connection.disconnect()
            } catch (e: Exception) {
                Log.e("UNREGISTER", "Error desregistrando: ${e.message}")
            }
        }.start()
    }

    private fun switchCamera() {
        if (Camera.getNumberOfCameras() > 1) {
            currentCameraId = if (currentCameraId == Camera.CameraInfo.CAMERA_FACING_BACK) {
                Camera.CameraInfo.CAMERA_FACING_FRONT
            } else {
                Camera.CameraInfo.CAMERA_FACING_BACK
            }

            releaseCamera()
            initializeCamera()

            val cameraType = if (currentCameraId == Camera.CameraInfo.CAMERA_FACING_FRONT) "frontal" else "trasera"
            Toast.makeText(this, "📹 Cambiado a cámara $cameraType", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "📱 Solo hay una cámara disponible", Toast.LENGTH_SHORT).show()
        }
    }

    private fun toggleFlashlight() {
        camera?.let { cam ->
            try {
                val parameters = cam.parameters
                val flashModes = parameters.supportedFlashModes

                if (flashModes != null && flashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                    if (isFlashlightOn) {
                        parameters.flashMode = Camera.Parameters.FLASH_MODE_OFF
                        isFlashlightOn = false
                        Toast.makeText(this, "💡 Flash desactivado", Toast.LENGTH_SHORT).show()
                    } else {
                        parameters.flashMode = Camera.Parameters.FLASH_MODE_TORCH
                        isFlashlightOn = true
                        Toast.makeText(this, "🔦 Flash activado", Toast.LENGTH_SHORT).show()
                    }
                    cam.parameters = parameters
                } else {
                    Toast.makeText(this, "⚠️ Flash no disponible en esta cámara", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("CAMERA", "Error toggleando flash: ${e.message}")
                Toast.makeText(this, "⚠️ Error al controlar el flash", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // 🆕 ABRIR ACTIVITY VIEWER
    private fun openViewer() {
        val intent = Intent(this, ViewerActivity::class.java)
        startActivity(intent)
    }

    private fun startTimeUpdater() {
        timeUpdateRunnable = object : Runnable {
            override fun run() {
                if (isStreaming) {
                    val elapsed = System.currentTimeMillis() - streamStartTime
                    val timeString = formatTime(elapsed)
                    tvStreamTime.text = timeString
                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(timeUpdateRunnable!!)
    }

    private fun stopTimeUpdater() {
        timeUpdateRunnable?.let { handler.removeCallbacks(it) }
        tvStreamTime.text = "00:00:00"
    }

    private fun formatTime(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs)
    }

    private fun updateStatus(status: String, isLive: Boolean) {
        tvStreamStatus.text = status
        tvStreamStatus.setTextColor(
            if (isLive) resources.getColor(android.R.color.holo_red_light)
            else resources.getColor(android.R.color.white)
        )
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d("SURFACE", "Surface creado")
        isSurfaceReady = true

        if (camera != null) {
            setupCameraPreview()
        }
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        Log.d("SURFACE", "Surface cambiado: ${width}x${height}")

        camera?.let { cam ->
            try {
                cam.stopPreview()
                setupCameraPreview()
            } catch (e: IOException) {
                Log.e("SURFACE", "Error en surface changed: ${e.message}")
            }
        }
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) {
        Log.d("SURFACE", "Surface destruido")
        isSurfaceReady = false
        releaseCamera()
    }

    private fun sendFrameToServer(base64Image: String) {
        try {
            val url = URL(streamUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            val jsonString = """
            {
                "token": "$streamToken",
                "frame": "$base64Image",
                "timestamp": ${System.currentTimeMillis()},
                "device_id": "android_stream"
            }
            """.trimIndent()

            val os = connection.outputStream
            os.write(jsonString.toByteArray())
            os.flush()
            os.close()

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                Log.d("STREAM", "Frame enviado OK para token: $streamToken")
            } else {
                Log.w("STREAM", "Error response: $responseCode")
            }

            connection.disconnect()

        } catch (e: OutOfMemoryError) {
            Log.e("STREAM", "OutOfMemoryError en envío: ${e.message}")
            pauseStreamTemporarily()
        } catch (e: Exception) {
            Log.e("STREAM", "Error enviando al servidor: ${e.message}")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStreaming()
        stopTimeUpdater()
        releaseCamera()

        // 🔧 LIMPIAR MEMORIA Y RUNNABLES
        memoryCleanupRunnable?.let { handler.removeCallbacks(it) }
        frameBuffer.clear()

        // 🔧 LIBERAR WAKE LOCK SI ESTÁ ACTIVO
        try {
            if (wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            Log.e("WAKE_LOCK", "Error en onDestroy: ${e.message}")
        }

        System.gc()
    }

    override fun onPause() {
        super.onPause()
        if (camera != null) {
            camera?.stopPreview()
        }
    }

    override fun onResume() {
        super.onResume()
        if (camera != null && isSurfaceReady) {
            setupCameraPreview()
        } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            initializeCamera()
        }
    }
}