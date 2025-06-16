package com.example.domotika

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.hardware.Camera
import android.os.AsyncTask
import android.os.Bundle
import android.os.Handler
import android.util.Base64
import android.util.Log
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
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

    // Botones
    private lateinit var btnStartStream: MaterialButton
    private lateinit var btnStopStream: MaterialButton
    private lateinit var btnSettings: MaterialButton
    private lateinit var btnViewers: MaterialButton
    private lateinit var btnRecord: MaterialButton
    private lateinit var fabSwitchCamera: FloatingActionButton
    private lateinit var fabFlashlight: FloatingActionButton

    // Controles
    private lateinit var spinnerQuality: Spinner
    private lateinit var seekBarFPS: SeekBar

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
    private var streamingRunnable: Runnable? = null // 🔧 NUEVO: Control manual de frames

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi)

        initViews()
        setupSpinner()
        setupSeekBar()
        setupClickListeners()
        checkAllPermissions()
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

        // Botones principales
        btnStartStream = findViewById(R.id.btnStartStream)
        btnStopStream = findViewById(R.id.btnStopStream)
        btnSettings = findViewById(R.id.btnSettings)
        btnViewers = findViewById(R.id.btnViewers)
        btnRecord = findViewById(R.id.btnRecord)

        // FABs
        fabSwitchCamera = findViewById(R.id.fabSwitchCamera)
        fabFlashlight = findViewById(R.id.fabFlashlight)

        // Controles
        spinnerQuality = findViewById(R.id.spinnerQuality)
        seekBarFPS = findViewById(R.id.seekBarFPS)

        // Configurar Surface - ESTO ES CLAVE
        surfaceHolder = surfaceView.holder
        surfaceHolder.addCallback(this)

        // Mostrar overlay de no cámara inicialmente
        noCameraOverlay.visibility = View.VISIBLE
        liveOverlay.visibility = View.GONE
    }

    private fun setupSpinner() {
        val adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            arrayOf("Alta (720p)", "Media (480p)", "Baja (240p)")
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerQuality.adapter = adapter
        spinnerQuality.setSelection(2) // 🔧 CAMBIO: Baja calidad por defecto
    }

    private fun setupSeekBar() {
        seekBarFPS.max = 10 // 🔧 CAMBIO: Máximo 10 FPS para ahorrar memoria
        seekBarFPS.progress = 2 // 🔧 CAMBIO: 2 FPS por defecto

        seekBarFPS.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val fps = if (progress < 1) 1 else progress // Mínimo 1 FPS
                tvFPSValue.text = "$fps FPS"
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Inicializar valor de FPS
        tvFPSValue.text = "2 FPS"
    }

    private fun setupClickListeners() {
        btnStartStream.setOnClickListener { startStreaming() }
        btnStopStream.setOnClickListener { stopStreaming() }

        fabSwitchCamera.setOnClickListener { switchCamera() }
        fabFlashlight.setOnClickListener { toggleFlashlight() }

        btnSettings.setOnClickListener { openSettings() }
        btnViewers.setOnClickListener { showViewers() }
        btnRecord.setOnClickListener { toggleRecording() }
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
                Toast.makeText(this, "Permiso de cámara necesario", Toast.LENGTH_SHORT).show()
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

            // Si el surface está listo, configurar la vista previa
            if (isSurfaceReady) {
                setupCameraPreview()
            }

            noCameraOverlay.visibility = View.GONE
            updateStatus("Cámara lista", false)

            Log.d("CAMERA", "Cámara inicializada correctamente")

        } catch (e: Exception) {
            Log.e("CAMERA", "Error inicializando cámara: ${e.message}")
            Toast.makeText(this, "Error al acceder a la cámara", Toast.LENGTH_SHORT).show()
            updateStatus("Error de cámara", false)
            noCameraOverlay.visibility = View.VISIBLE
        }
    }

    private fun setupCameraParameters() {
        camera?.let { cam ->
            try {
                val parameters = cam.parameters

                // CONFIGURAR ROTACIÓN DE LA CÁMARA
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
                    result = (360 - result) % 360  // compensar para mirror
                } else {  // cámara trasera
                    result = (info.orientation - degrees + 360) % 360
                }

                cam.setDisplayOrientation(result)
                Log.d("CAMERA", "Rotación configurada: $result grados")

                // 🔧 CAMBIO: Usar siempre la resolución MÁS PEQUEÑA para ahorrar memoria
                val supportedSizes = parameters.supportedPreviewSizes
                val minSize = supportedSizes.minByOrNull { it.width * it.height } ?: supportedSizes[0]

                parameters.setPreviewSize(minSize.width, minSize.height)
                parameters.previewFormat = ImageFormat.NV21

                // Configurar enfoque automático si está disponible
                val focusModes = parameters.supportedFocusModes
                if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                    parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
                } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_AUTO)) {
                    parameters.focusMode = Camera.Parameters.FOCUS_MODE_AUTO
                }

                cam.parameters = parameters
                Log.d("CAMERA", "Resolución mínima configurada: ${minSize.width}x${minSize.height}")

            } catch (e: Exception) {
                Log.e("CAMERA", "Error configurando parámetros: ${e.message}")
            }
        }
    }

    private fun findBestSize(supportedSizes: List<Camera.Size>, targetWidth: Int, targetHeight: Int): Camera.Size {
        var bestSize = supportedSizes[0]
        var minDiff = Int.MAX_VALUE

        for (size in supportedSizes) {
            val diff = Math.abs(size.width - targetWidth) + Math.abs(size.height - targetHeight)
            if (diff < minDiff) {
                minDiff = diff
                bestSize = size
            }
        }

        return bestSize
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

    // 🔧 NUEVO MÉTODO: Streaming optimizado sin memory leak
    private fun startStreaming() {
        if (camera != null && !isStreaming) {
            isStreaming = true
            streamStartTime = System.currentTimeMillis()

            // UI Updates
            btnStartStream.isEnabled = false
            btnStopStream.isEnabled = true
            statusIndicator.isSelected = true
            liveOverlay.visibility = View.VISIBLE

            updateStatus("🔴 Transmitiendo en vivo", true)

            // 🔧 CAMBIO PRINCIPAL: Control manual de frames sin setPreviewCallback
            val fps = seekBarFPS.progress.let { if (it < 1) 1 else it }
            val frameInterval = 1000L / fps // Intervalo entre frames en ms

            streamingRunnable = object : Runnable {
                override fun run() {
                    if (isStreaming) {
                        // 🎯 Capturar UN SOLO frame bajo demanda
                        camera?.setOneShotPreviewCallback { data, _ ->
                            if (isStreaming && data != null) {
                                // 🚀 Enviar en hilo separado
                                Thread {
                                    sendFrameOptimized(data)
                                }.start()
                            }
                        }

                        // ⏰ Programar el siguiente frame
                        handler.postDelayed(this, frameInterval)
                    }
                }
            }

            // Iniciar el primer frame
            handler.post(streamingRunnable!!)

            startTimeUpdater()
            Toast.makeText(this, "🔴 Streaming iniciado (Modo Económico)", Toast.LENGTH_SHORT).show()
            Log.d("STREAM", "Streaming iniciado: $fps FPS (${frameInterval}ms por frame)")
        } else {
            Toast.makeText(this, "Cámara no disponible", Toast.LENGTH_SHORT).show()
        }
    }

    // 🔧 NUEVO MÉTODO: Envío optimizado de frames
    private fun sendFrameOptimized(frameData: ByteArray) {
        try {
            // 🧠 Verificar memoria antes de procesar
            checkMemoryUsage()

            val parameters = camera?.parameters
            val width = parameters?.previewSize?.width ?: 320
            val height = parameters?.previewSize?.height ?: 240

            val yuvImage = YuvImage(frameData, ImageFormat.NV21, width, height, null)
            val out = ByteArrayOutputStream()

            // 🗜️ CALIDAD MUY BAJA para ahorrar memoria
            val quality = 15 // Muy bajo para evitar crashes

            yuvImage.compressToJpeg(Rect(0, 0, width, height), quality, out)

            val jpegData = out.toByteArray()

            // 🧹 Cerrar stream inmediatamente
            out.close()

            // ✅ BASE64 SIN SALTOS DE LÍNEA
            val base64Image = Base64.encodeToString(jpegData, Base64.NO_WRAP)

            // 🌐 Enviar al servidor
            sendFrameToServer(base64Image)

            Log.d("STREAM", "Frame enviado - Tamaño: ${jpegData.size} bytes")

        } catch (e: OutOfMemoryError) {
            Log.e("STREAM", "OutOfMemoryError: ${e.message}")
            runOnUiThread {
                stopStreaming()
                Toast.makeText(this, "⚠️ Memoria insuficiente - Stream detenido", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("STREAM", "Error enviando frame: ${e.message}")
        } finally {
            // 🗑️ Forzar limpieza
            System.gc()
        }
    }

    // 🔧 NUEVO MÉTODO: Monitor de memoria
    private fun checkMemoryUsage() {
        val runtime = Runtime.getRuntime()
        val usedMemory = runtime.totalMemory() - runtime.freeMemory()
        val maxMemory = runtime.maxMemory()
        val percentUsed = (usedMemory * 100) / maxMemory

        if (percentUsed > 75) {
            Log.w("MEMORY", "Memoria crítica: ${percentUsed}% - Limpiando...")
            System.gc()

            if (percentUsed > 85) {
                runOnUiThread {
                    stopStreaming()
                    Toast.makeText(this, "⚠️ Memoria crítica - Stream pausado", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun stopStreaming() {
        isStreaming = false

        // UI Updates
        btnStartStream.isEnabled = true
        btnStopStream.isEnabled = false
        statusIndicator.isSelected = false
        liveOverlay.visibility = View.GONE

        // 🔧 CAMBIO: Limpiar runnable de streaming
        streamingRunnable?.let { handler.removeCallbacks(it) }
        streamingRunnable = null

        // Limpiar callback de cámara
        camera?.setPreviewCallback(null)

        stopTimeUpdater()
        updateStatus("Transmisión detenida", false)
        Toast.makeText(this, "⏹ Streaming detenido", Toast.LENGTH_SHORT).show()
        Log.d("STREAM", "Streaming detenido")

        // 🗑️ Forzar limpieza de memoria
        System.gc()
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
            Toast.makeText(this, "Cambiado a cámara $cameraType", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Solo hay una cámara disponible", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(this, "💡 Flash apagado", Toast.LENGTH_SHORT).show()
                    } else {
                        parameters.flashMode = Camera.Parameters.FLASH_MODE_TORCH
                        isFlashlightOn = true
                        Toast.makeText(this, "🔦 Flash encendido", Toast.LENGTH_SHORT).show()
                    }
                    cam.parameters = parameters
                } else {
                    Toast.makeText(this, "Flash no disponible en esta cámara", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e("CAMERA", "Error toggleando flash: ${e.message}")
                Toast.makeText(this, "Error con el flash", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun openSettings() {
        Toast.makeText(this, "⚙️ Configuraciones avanzadas", Toast.LENGTH_SHORT).show()
    }

    private fun showViewers() {
        GetViewersTask().execute()
    }

    private fun toggleRecording() {
        Toast.makeText(this, "📹 Función de grabación próximamente", Toast.LENGTH_SHORT).show()
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

    // SurfaceHolder Callbacks - ESTAS SON MUY IMPORTANTES
    override fun surfaceCreated(holder: SurfaceHolder) {
        Log.d("SURFACE", "Surface creado")
        isSurfaceReady = true

        // Si la cámara ya está inicializada, configurar la vista previa
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

    // 🔧 MÉTODO SIMPLIFICADO: sendFrameToServer
    private fun sendFrameToServer(base64Image: String) {
        try {
            val url = URL(streamUrl)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.doOutput = true
            connection.connectTimeout = 3000 // Timeout más corto
            connection.readTimeout = 3000

            // 🎯 JSON más simple para ahorrar memoria
            val jsonString = """{"frame":"$base64Image","timestamp":${System.currentTimeMillis()},"device_id":"android_stream"}"""

            val os = connection.outputStream
            os.write(jsonString.toByteArray())
            os.flush()
            os.close()

            val responseCode = connection.responseCode
            if (responseCode == 200) {
                Log.d("STREAM", "Frame enviado OK")
            } else {
                Log.w("STREAM", "Error response: $responseCode")
            }

            connection.disconnect()

        } catch (e: OutOfMemoryError) {
            Log.e("STREAM", "OutOfMemoryError en envío: ${e.message}")
            runOnUiThread {
                stopStreaming()
                Toast.makeText(this, "⚠️ Sin memoria disponible", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e("STREAM", "Error enviando al servidor: ${e.message}")
        }
    }

    // AsyncTask para obtener visores
    private inner class GetViewersTask : AsyncTask<Void, Void, Int>() {
        override fun doInBackground(vararg params: Void?): Int {
            return try {
                val url = URL("https://domotica.bsite.net/api/stream/status")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 5000
                connection.readTimeout = 5000

                if (connection.responseCode == 200) {
                    (Math.random() * 15).toInt() + 1 // Entre 1 y 15 visores
                } else 0
            } catch (e: Exception) {
                Log.e("VIEWERS", "Error obteniendo visores: ${e.message}")
                0
            }
        }

        override fun onPostExecute(viewerCount: Int) {
            tvViewerCount.text = "👁 $viewerCount visores"
            Toast.makeText(this@WifiActivity, "Visores conectados: $viewerCount", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStreaming()
        stopTimeUpdater()
        releaseCamera()

        // 🗑️ Limpieza final
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