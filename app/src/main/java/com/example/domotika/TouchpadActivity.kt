package com.example.domotika

import android.bluetooth.BluetoothSocket
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.IOException

class TouchpadActivity : AppCompatActivity() {

    private lateinit var touchpadView: View
    private lateinit var leftClickBtn: MaterialButton
    private lateinit var rightClickBtn: MaterialButton
    private lateinit var scrollUpBtn: MaterialButton
    private lateinit var scrollDownBtn: MaterialButton
    private lateinit var toolbar: Toolbar

    private var bluetoothSocket: BluetoothSocket? = null
    private var communicationJob: Job? = null

    // Variables para el tracking del mouse
    private var lastX = 0f
    private var lastY = 0f
    private var isTracking = false

    // Sensibilidad del mouse (ajustable)
    private val mouseSensitivity = 2.5f

    // Control de errores mejorado
    private var connectionRetries = 0
    private val maxRetries = 3
    private var isInitialized = false

    companion object {
        const val EXTRA_BLUETOOTH_SOCKET = "bluetooth_socket"
        const val EXTRA_DEVICE_NAME = "device_name"
        private const val TAG = "TouchpadActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_touchpad)

        Log.i(TAG, "🖱️ Iniciando TouchpadActivity")

        // Obtener el socket del intent O del manager - CORREGIDO para evitar deprecation
        bluetoothSocket = BluetoothSocketManager.getCurrentSocket()


        // Validación mejorada de conexión
        if (!validateConnection()) {
            return
        }

        val deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME) ?: "Laptop"
        Log.i(TAG, "📱 Conectado a: $deviceName")

        initializeViews(deviceName)
        setupTouchpadListener()
        setupButtonListeners()

        // Inicializar comunicación con reintentos
        initializeCommunication()
    }

    private fun validateConnection(): Boolean {
        // Verificar socket
        if (bluetoothSocket == null) {
            Log.e(TAG, "❌ Socket es null")
            showErrorAndFinish("Error: No hay socket Bluetooth")
            return false
        }

        // Verificar si está conectado
        if (!bluetoothSocket!!.isConnected) {
            Log.e(TAG, "❌ Socket no está conectado")
            showErrorAndFinish("Error: Socket no conectado")
            return false
        }

        // Establecer socket en el manager - CORREGIDO: usar setSocket en lugar de setCurrentSocket
        if (!BluetoothSocketManager.isSocketConnected()) {
            if (!BluetoothSocketManager.setSocket(bluetoothSocket!!)) {
                Log.e(TAG, "❌ Error estableciendo socket en manager")
                showErrorAndFinish("Error: No se pudo establecer la conexión")
                return false
            }
        }


        // Verificar conexión final
        if (!BluetoothSocketManager.isSocketConnected()) {
            Log.e(TAG, "❌ Manager reporta desconexión")
            showErrorAndFinish("Error: Conexión no válida")
            return false
        }

        Log.i(TAG, "✅ Conexión validada exitosamente")
        return true
    }

    private fun showErrorAndFinish(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        finish()
    }

    private fun initializeViews(deviceName: String) {
        toolbar = findViewById(R.id.toolbar)
        touchpadView = findViewById(R.id.touchpad_area)
        leftClickBtn = findViewById(R.id.btn_left_click)
        rightClickBtn = findViewById(R.id.btn_right_click)
        scrollUpBtn = findViewById(R.id.btn_scroll_up)
        scrollDownBtn = findViewById(R.id.btn_scroll_down)

        setSupportActionBar(toolbar)
        supportActionBar?.title = "🖱️ Mouse Virtual - $deviceName"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        Log.i(TAG, "✅ Views inicializadas")
    }

    private fun setupTouchpadListener() {
        touchpadView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    lastX = event.x
                    lastY = event.y
                    isTracking = true
                    Log.d(TAG, "👆 Touchpad DOWN en (${event.x}, ${event.y})")
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    if (isTracking && isInitialized) {
                        val deltaX = (event.x - lastX) * mouseSensitivity
                        val deltaY = (event.y - lastY) * mouseSensitivity

                        // Solo enviar si hay movimiento significativo
                        if (kotlin.math.abs(deltaX) > 1 || kotlin.math.abs(deltaY) > 1) {
                            sendMouseMove(deltaX, deltaY)
                        }

                        lastX = event.x
                        lastY = event.y
                    }
                    true
                }

                MotionEvent.ACTION_UP -> {
                    isTracking = false
                    Log.d(TAG, "👆 Touchpad UP")
                    true
                }

                else -> false
            }
        }

        // Detectar tap para click izquierdo
        touchpadView.setOnClickListener {
            if (isInitialized) {
                Log.d(TAG, "👆 Tap detectado - enviando click izquierdo")
                sendMouseClick("left")
            }
        }

        Log.i(TAG, "✅ Touchpad listener configurado")
    }

    private fun setupButtonListeners() {
        leftClickBtn.setOnClickListener {
            if (isInitialized) {
                Log.d(TAG, "🔘 Botón click izquierdo")
                sendMouseClick("left")
            }
        }

        rightClickBtn.setOnClickListener {
            if (isInitialized) {
                Log.d(TAG, "🔘 Botón click derecho")
                sendMouseClick("right")
            }
        }

        scrollUpBtn.setOnClickListener {
            if (isInitialized) {
                Log.d(TAG, "🔘 Scroll arriba")
                sendMouseScroll("up")
            }
        }

        scrollDownBtn.setOnClickListener {
            if (isInitialized) {
                Log.d(TAG, "🔘 Scroll abajo")
                sendMouseScroll("down")
            }
        }

        Log.i(TAG, "✅ Botones configurados")
    }

    private fun initializeCommunication() {
        communicationJob = CoroutineScope(Dispatchers.IO).launch {
            var success = false

            // Reintentos de inicialización
            for (attempt in 1..maxRetries) {
                try {
                    Log.i(TAG, "🔄 Intento de inicialización $attempt/$maxRetries")

                    // Verificar conexión antes de enviar
                    if (!BluetoothSocketManager.isSocketConnected()) {
                        Log.w(TAG, "⚠️ Conexión perdida, reestableciendo...")
                        if (bluetoothSocket != null && bluetoothSocket!!.isConnected) {
                            BluetoothSocketManager.setSocket(bluetoothSocket!!)
                        } else {
                            throw IOException("Socket desconectado")
                        }
                    }

                    // Enviar comando de inicialización
                    val initCommand = JSONObject().apply {
                        put("type", "init")
                        put("data", "touchpad")
                        put("timestamp", System.currentTimeMillis())
                        put("device", "android")
                    }.toString()

                    Log.i(TAG, "📤 Enviando inicialización: $initCommand")

                    if (BluetoothSocketManager.sendData("$initCommand\n")) {
                        // Esperar un poco para que el servidor procese
                        delay(500)

                        // Enviar comando de prueba
                        val testCommand = JSONObject().apply {
                            put("type", "test")
                            put("message", "conexion_ok")
                            put("timestamp", System.currentTimeMillis())
                        }.toString()

                        if (BluetoothSocketManager.sendData("$testCommand\n")) {
                            success = true
                            break
                        }
                    }

                } catch (e: Exception) {
                    Log.w(TAG, "⚠️ Intento $attempt falló: ${e.message}")
                    if (attempt < maxRetries) {
                        delay(1000) // Esperar antes del siguiente intento
                    }
                }
            }

            // Actualizar UI según resultado
            withContext(Dispatchers.Main) {
                if (success) {
                    isInitialized = true
                    connectionRetries = 0
                    Toast.makeText(this@TouchpadActivity, "🖱️ Mouse virtual activado", Toast.LENGTH_SHORT).show()
                    Log.i(TAG, "✅ Mouse virtual inicializado correctamente")
                } else {
                    Toast.makeText(this@TouchpadActivity, "❌ Error: No se pudo conectar al servidor", Toast.LENGTH_LONG).show()
                    Log.e(TAG, "❌ Inicialización falló después de $maxRetries intentos")
                    finish()
                }
            }
        }
    }

    private fun sendMouseMove(deltaX: Float, deltaY: Float) {
        if (!isInitialized) return

        val command = JSONObject().apply {
            put("type", "mouse_move")
            put("deltaX", deltaX.toInt())
            put("deltaY", deltaY.toInt())
            put("timestamp", System.currentTimeMillis())
        }

        // Envío asíncrono para movimientos (sin bloquear UI)
        BluetoothSocketManager.sendDataAsync("$command\n") { success ->
            if (!success) {
                Log.w(TAG, "⚠️ Fallo enviando movimiento")
            }
        }
    }

    private fun sendMouseClick(button: String) {
        if (!isInitialized) return

        val command = JSONObject().apply {
            put("type", "mouse_click")
            put("button", button)
            put("timestamp", System.currentTimeMillis())
        }

        Log.d(TAG, "🖱️ Enviando click: $button")

        CoroutineScope(Dispatchers.IO).launch {
            val success = BluetoothSocketManager.sendData("$command\n")

            withContext(Dispatchers.Main) {
                if (success) {
                    // Feedback visual
                    when (button) {
                        "left" -> animateButton(leftClickBtn)
                        "right" -> animateButton(rightClickBtn)
                    }
                } else {
                    Log.w(TAG, "⚠️ Click no enviado")
                    handleSendError("Click no enviado")
                }
            }
        }
    }

    private fun sendMouseScroll(direction: String) {
        if (!isInitialized) return

        val command = JSONObject().apply {
            put("type", "mouse_scroll")
            put("direction", direction)
            put("amount", 3)
            put("timestamp", System.currentTimeMillis())
        }

        Log.d(TAG, "🖱️ Enviando scroll: $direction")

        CoroutineScope(Dispatchers.IO).launch {
            val success = BluetoothSocketManager.sendData("$command\n")

            withContext(Dispatchers.Main) {
                if (success) {
                    // Feedback visual
                    when (direction) {
                        "up" -> animateButton(scrollUpBtn)
                        "down" -> animateButton(scrollDownBtn)
                    }
                } else {
                    Log.w(TAG, "⚠️ Scroll no enviado")
                    handleSendError("Scroll no enviado")
                }
            }
        }
    }

    private fun handleSendError(message: String) {
        connectionRetries++

        if (connectionRetries >= maxRetries) {
            Toast.makeText(this, "❌ Conexión perdida. Cerrando...", Toast.LENGTH_LONG).show()
            Log.e(TAG, "❌ Demasiados errores, cerrando actividad")
            finish()
        } else {
            // Intentar reconectar
            Log.w(TAG, "⚠️ $message - Intento $connectionRetries/$maxRetries")

            if (connectionRetries == 1) {
                Toast.makeText(this, "⚠️ Problema de conexión, reintentando...", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun animateButton(button: MaterialButton) {
        button.animate()
            .scaleX(0.95f)
            .scaleY(0.95f)
            .setDuration(100)
            .withEndAction {
                button.animate()
                    .scaleX(1.0f)
                    .scaleY(1.0f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "🔚 Cerrando TouchpadActivity")

        // Marcar como no inicializado
        isInitialized = false

        // Enviar comando de desconexión
        if (BluetoothSocketManager.isSocketConnected()) {
            try {
                val disconnectCommand = JSONObject().apply {
                    put("type", "disconnect")
                    put("timestamp", System.currentTimeMillis())
                }.toString()

                BluetoothSocketManager.sendData("$disconnectCommand\n")
            } catch (e: Exception) {
                Log.w(TAG, "Error enviando desconexión: ${e.message}")
            }
        }

        // Cancelar trabajos
        communicationJob?.cancel()

        // Cerrar socket
        BluetoothSocketManager.closeCurrentSocket()

        Log.i(TAG, "✅ TouchpadActivity cerrada correctamente")
    }
}