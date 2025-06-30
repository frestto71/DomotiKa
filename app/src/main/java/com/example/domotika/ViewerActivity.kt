package com.example.domotika

import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ViewerActivity : AppCompatActivity() {

    // Views principales
    private lateinit var imageStreamView: ImageView
    private lateinit var loadingLayout: LinearLayout
    private lateinit var connectionLayout: ScrollView
    private lateinit var streamContainer: LinearLayout
    private lateinit var overlayControls: LinearLayout

    // Cards y contenedores
    private lateinit var videoCard: androidx.cardview.widget.CardView
    private lateinit var controlsCard: androidx.cardview.widget.CardView

    // Views de conexión
    private lateinit var etToken: EditText
    private lateinit var btnConnect: MaterialButton
    private lateinit var btnDisconnect: MaterialButton
    private lateinit var btnDisconnectOverlay: MaterialButton
    private lateinit var btnBack: MaterialButton
    // ❌ ELIMINADO: btnStreams

    // Control de orientación
    private lateinit var fabOrientation: FloatingActionButton

    // Views de información (normales)
    private lateinit var tvStatus: TextView
    private lateinit var tvFPS: TextView
    private lateinit var tvFrameCount: TextView
    private lateinit var tvConnectionTime: TextView

    // Views de información (overlay)
    private lateinit var tvStatusOverlay: TextView
    private lateinit var tvFPSOverlay: TextView
    private lateinit var tvFrameCountOverlay: TextView
    private lateinit var tvConnectionTimeOverlay: TextView

    // ❌ ELIMINADO: streamsLayout

    // Variables de control
    private var isConnected = false
    private var currentToken: String? = null
    private var frameCount = 0
    private var connectionStartTime = 0L
    private var lastFrameTime = System.currentTimeMillis()
    private var fpsCounter = 0

    // 🆕 CONTROL DE ORIENTACIÓN
    private var isLandscape = false
    private var overlayVisible = false

    // Handlers
    private val handler = Handler(Looper.getMainLooper())
    private var fetchRunnable: Runnable? = null
    private var timeRunnable: Runnable? = null

    // URLs
    private val streamUrl = "https://domotica.bsite.net/api/stream"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viewer)

        // 🔧 CONFIGURAR PANTALLA COMPLETA DESPUÉS DE setContentView
        setupFullscreen()

        initViews()
        setupClickListeners()
        setupOrientationControls()

        // Auto-focus en el input de token
        etToken.requestFocus()

        // 🆕 MENSAJE DE BIENVENIDA ELEGANTE
        Toast.makeText(this, "✨ Visualizador iniciado - Listo para streams", Toast.LENGTH_SHORT).show()
    }

    private fun setupFullscreen() {
        try {
            // Ocultar barra de estado y navegación
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )

            // Para API 30+
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                try {
                    window.insetsController?.let { controller ->
                        controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                        controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    }
                } catch (e: Exception) {
                    Log.w("VIEWER", "No se pudo configurar insetsController: ${e.message}")
                    useDeprecatedFullscreen()
                }
            } else {
                useDeprecatedFullscreen()
            }

            // Mantener pantalla encendida
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        } catch (e: Exception) {
            Log.e("VIEWER", "Error configurando pantalla completa: ${e.message}")
            useBasicFullscreen()
        }
    }

    @Suppress("DEPRECATION")
    private fun useDeprecatedFullscreen() {
        window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                        View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                )
    }

    private fun useBasicFullscreen() {
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun initViews() {
        // Views principales
        imageStreamView = findViewById(R.id.imageStreamView)
        loadingLayout = findViewById(R.id.loadingLayout)
        connectionLayout = findViewById(R.id.connectionLayout)
        streamContainer = findViewById(R.id.streamContainer)
        overlayControls = findViewById(R.id.overlayControls)

        // Cards
        videoCard = findViewById(R.id.videoCard)
        controlsCard = findViewById(R.id.controlsCard)

        // Views de conexión
        etToken = findViewById(R.id.etToken)
        btnConnect = findViewById(R.id.btnConnect)
        btnDisconnect = findViewById(R.id.btnDisconnect)
        btnDisconnectOverlay = findViewById(R.id.btnDisconnectOverlay)
        btnBack = findViewById(R.id.btnBack)
        // ❌ ELIMINADO: btnStreams = findViewById(R.id.btnStreams)

        // Control de orientación
        fabOrientation = findViewById(R.id.fabOrientation)

        // Views de información (normales)
        tvStatus = findViewById(R.id.tvStatus)
        tvFPS = findViewById(R.id.tvFPS)
        tvFrameCount = findViewById(R.id.tvFrameCount)
        tvConnectionTime = findViewById(R.id.tvConnectionTime)

        // Views de información (overlay)
        tvStatusOverlay = findViewById(R.id.tvStatusOverlay)
        tvFPSOverlay = findViewById(R.id.tvFPSOverlay)
        tvFrameCountOverlay = findViewById(R.id.tvFrameCountOverlay)
        tvConnectionTimeOverlay = findViewById(R.id.tvConnectionTimeOverlay)

        // ❌ ELIMINADO: streamsLayout = findViewById(R.id.streamsLayout)

        // Estado inicial
        showConnectionScreen()
    }

    private fun setupClickListeners() {
        btnConnect.setOnClickListener { connectToStream() }
        btnDisconnect.setOnClickListener { disconnectFromStream() }
        btnDisconnectOverlay.setOnClickListener { disconnectFromStream() }
        btnBack.setOnClickListener { finish() }
        // ❌ ELIMINADO: btnStreams.setOnClickListener { toggleStreamsList() }

        // Click en la imagen para mostrar/ocultar controles
        imageStreamView.setOnClickListener { toggleControls() }

        // Enter en el EditText para conectar
        etToken.setOnEditorActionListener { _, _, _ ->
            connectToStream()
            true
        }
    }

    // 🆕 CONFIGURAR CONTROLES DE ORIENTACIÓN
    private fun setupOrientationControls() {
        fabOrientation.setOnClickListener { toggleOrientation() }

        // Configurar orientación por defecto (vertical)
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        isLandscape = false
    }

    // 🆕 ALTERNAR ENTRE VERTICAL Y HORIZONTAL
    private fun toggleOrientation() {
        if (isLandscape) {
            // Cambiar a vertical
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            isLandscape = false
            showVerticalMode()
            Toast.makeText(this, "📱 Modo vertical activado", Toast.LENGTH_SHORT).show()
        } else {
            // Cambiar a horizontal
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
            isLandscape = true
            showHorizontalMode()
            Toast.makeText(this, "📺 Modo horizontal activado - Toca para controles", Toast.LENGTH_SHORT).show()
        }
    }

    // 🆕 MOSTRAR MODO VERTICAL (CON CONTROLES ABAJO)
    private fun showVerticalMode() {
        streamContainer.orientation = LinearLayout.VERTICAL
        controlsCard.visibility = View.VISIBLE
        overlayControls.visibility = View.GONE
        overlayVisible = false

        // Ajustar márgenes del video card
        val layoutParams = videoCard.layoutParams as LinearLayout.LayoutParams
        layoutParams.setMargins(16, 16, 16, 16)
        videoCard.layoutParams = layoutParams

        Log.d("ORIENTATION", "Modo vertical activado")
    }

    // 🆕 MOSTRAR MODO HORIZONTAL (FULLSCREEN CON OVERLAY)
    private fun showHorizontalMode() {
        controlsCard.visibility = View.GONE
        overlayControls.visibility = View.VISIBLE
        overlayVisible = true

        // Expandir video card a pantalla completa
        val layoutParams = videoCard.layoutParams as LinearLayout.LayoutParams
        layoutParams.setMargins(0, 0, 0, 0)
        layoutParams.height = LinearLayout.LayoutParams.MATCH_PARENT
        layoutParams.weight = 1f
        videoCard.layoutParams = layoutParams

        Log.d("ORIENTATION", "Modo horizontal activado")
    }

    private fun connectToStream() {
        val token = etToken.text.toString().trim()

        if (token.isEmpty()) {
            Toast.makeText(this, "⚠️ Ingresa un token válido para continuar", Toast.LENGTH_SHORT).show()
            return
        }

        if (isConnected) {
            disconnectFromStream()
        }

        currentToken = token
        isConnected = true
        frameCount = 0
        connectionStartTime = System.currentTimeMillis()

        // UI
        showLoadingScreen()
        updateStatus("🔗 Conectando a $token...")

        // Registrar viewer
        registerViewer()

        // Iniciar fetch de frames
        startFetching()
        startTimeUpdater()

        Toast.makeText(this, "🔗 Conectando a stream: $token\n📱 Pantalla permanecerá activa", Toast.LENGTH_LONG).show()
        Log.d("VIEWER", "Conectando a token: $token")
    }

    private fun disconnectFromStream() {
        isConnected = false
        currentToken = null

        // Detener fetch
        stopFetching()
        stopTimeUpdater()

        // UI
        showConnectionScreen()
        updateStatus("📭 Desconectado del stream")
        resetCounters()
    }

    private fun startFetching() {
        fetchRunnable = object : Runnable {
            override fun run() {
                if (isConnected && currentToken != null) {
                    Thread {
                        fetchFrame()
                    }.start()

                    // Próximo fetch en 400ms (2.5 FPS) - Optimizado para estabilidad
                    handler.postDelayed(this, 400)
                }
            }
        }
        handler.post(fetchRunnable!!)
    }

    private fun stopFetching() {
        fetchRunnable?.let { handler.removeCallbacks(it) }
        fetchRunnable = null
    }

    private fun fetchFrame() {
        try {
            val url = URL("$streamUrl/$currentToken")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = 3000
            connection.readTimeout = 3000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().readText()
                val json = JSONObject(response)

                if (json.getBoolean("success") && json.has("frame")) {
                    val frameBase64 = json.getString("frame")
                    val streamName = json.optString("stream_name", "Stream")

                    // Decodificar y mostrar imagen
                    val imageBytes = Base64.decode(frameBase64, Base64.DEFAULT)
                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)

                    runOnUiThread {
                        imageStreamView.setImageBitmap(bitmap)
                        showStreamScreen()
                        updateStatus("🔴 $streamName")
                        updateFPS()
                        frameCount++
                    }

                } else {
                    runOnUiThread {
                        updateStatus("📭 Stream sin transmisión activa")
                    }
                }
            } else {
                runOnUiThread {
                    updateStatus("❌ Error de servidor: ${connection.responseCode}")
                }
            }

            connection.disconnect()

        } catch (e: Exception) {
            runOnUiThread {
                updateStatus("💥 Error de conexión de red")
            }
            Log.e("VIEWER", "Error fetching frame: ${e.message}")
        }
    }

    private fun registerViewer() {
        Thread {
            try {
                val url = URL("$streamUrl/connect/$currentToken")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.doOutput = true

                val response = connection.responseCode
                Log.d("VIEWER", "Viewer registrado correctamente: $response")
                connection.disconnect()

            } catch (e: Exception) {
                Log.e("VIEWER", "Error registrando viewer: ${e.message}")
            }
        }.start()
    }

    private fun updateFPS() {
        fpsCounter++
        val now = System.currentTimeMillis()
        val timeDiff = (now - lastFrameTime) / 1000.0

        if (timeDiff >= 1.0) {
            val fps = (fpsCounter / timeDiff).toInt()

            // Actualizar ambos displays
            tvFPS.text = "$fps"
            tvFPSOverlay.text = "FPS: $fps"

            fpsCounter = 0
            lastFrameTime = now
        }
    }

    private fun startTimeUpdater() {
        timeRunnable = object : Runnable {
            override fun run() {
                if (isConnected) {
                    val elapsed = System.currentTimeMillis() - connectionStartTime
                    val minutes = (elapsed / 60000).toInt()
                    val seconds = ((elapsed % 60000) / 1000).toInt()
                    val timeString = String.format("%02d:%02d", minutes, seconds)

                    // Actualizar ambos displays
                    tvConnectionTime.text = timeString
                    tvConnectionTimeOverlay.text = "Tiempo: $timeString"

                    handler.postDelayed(this, 1000)
                }
            }
        }
        handler.post(timeRunnable!!)
    }

    private fun stopTimeUpdater() {
        timeRunnable?.let { handler.removeCallbacks(it) }
        timeRunnable = null
    }

    // 🆕 ACTUALIZAR ESTADO EN AMBOS DISPLAYS
    private fun updateStatus(status: String) {
        tvStatus.text = status
        tvStatusOverlay.text = status
    }

    private fun resetCounters() {
        frameCount = 0
        fpsCounter = 0
        tvFPS.text = "0"
        tvFPSOverlay.text = "FPS: 0"
        tvFrameCount.text = "0"
        tvConnectionTime.text = "00:00"
        tvConnectionTimeOverlay.text = "Tiempo: 00:00"
    }

    private fun showConnectionScreen() {
        connectionLayout.visibility = View.VISIBLE
        streamContainer.visibility = View.GONE
        loadingLayout.visibility = View.GONE
        overlayControls.visibility = View.GONE
    }

    private fun showLoadingScreen() {
        connectionLayout.visibility = View.GONE
        streamContainer.visibility = View.GONE
        loadingLayout.visibility = View.VISIBLE
        overlayControls.visibility = View.GONE
    }

    private fun showStreamScreen() {
        connectionLayout.visibility = View.GONE
        loadingLayout.visibility = View.GONE
        streamContainer.visibility = View.VISIBLE

        // Mostrar controles según orientación
        if (isLandscape) {
            showHorizontalMode()
        } else {
            showVerticalMode()
        }
    }

    // 🆕 ALTERNAR CONTROLES EN MODO HORIZONTAL
    private fun toggleControls() {
        if (isLandscape) {
            overlayVisible = !overlayVisible
            overlayControls.visibility = if (overlayVisible) View.VISIBLE else View.GONE
        }
    }

    // ❌ ELIMINADOS TODOS LOS MÉTODOS RELACIONADOS CON STREAMS:
    // - toggleStreamsList()
    // - loadActiveStreams()
    // - populateStreamsList()

    override fun onDestroy() {
        super.onDestroy()
        disconnectFromStream()
    }

    override fun onBackPressed() {
        if (isConnected) {
            disconnectFromStream()
        } else {
            super.onBackPressed()
        }
    }

    // 🆕 MANEJAR CAMBIOS DE CONFIGURACIÓN
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)

        // Actualizar el estado interno cuando el sistema cambie la orientación
        isLandscape = newConfig.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

        if (isConnected) {
            // Reconfigurar la UI según la nueva orientación
            if (isLandscape) {
                showHorizontalMode()
            } else {
                showVerticalMode()
            }
        }

        Log.d("ORIENTATION", "Configuración cambiada - Landscape: $isLandscape")
    }
}
