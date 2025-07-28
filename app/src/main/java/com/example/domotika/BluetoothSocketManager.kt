package com.example.domotika

import android.bluetooth.BluetoothSocket
import android.util.Log
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

object BluetoothSocketManager {
    private const val TAG = "BluetoothSocketManager"

    private var currentSocket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private var inputStream: InputStream? = null
    private var isConnected = false

    // Coroutine scope para manejo de conexión
    private val managerScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Establece una nueva conexión socket
     */
    fun setSocket(socket: BluetoothSocket): Boolean {
        return try {
            // Cerrar conexión anterior sin enviar "disconnect"
            closeCurrentSocket(sendDisconnect = false)

            // Validar que el socket esté conectado
            if (!socket.isConnected) {
                Log.e(TAG, "❌ Socket no está conectado")
                return false
            }

            currentSocket = socket
            outputStream = socket.outputStream
            inputStream = socket.inputStream
            isConnected = true

            Log.i(TAG, "✅ Socket establecido correctamente")
            Log.i(TAG, "📱 Dispositivo: ${socket.remoteDevice?.name ?: "Desconocido"}")

            // Enviar mensaje de prueba
            testConnection()

            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error estableciendo socket: ${e.message}")
            cleanup()
            false
        }
    }

    /**
     * Envía datos através del socket con reintentos
     */
    fun sendData(data: String): Boolean {
        if (!isSocketConnected()) {
            Log.e(TAG, "❌ No hay conexión activa para enviar: $data")
            return false
        }

        return try {
            val output = outputStream ?: run {
                Log.e(TAG, "❌ OutputStream es null")
                return false
            }

            val bytes = data.toByteArray(Charsets.UTF_8)
            output.write(bytes)
            output.flush()

            Log.d(TAG, "✅ Enviado: ${data.take(50)}${if(data.length > 50) "..." else ""}")
            true

        } catch (e: IOException) {
            Log.e(TAG, "❌ Error de IO enviando datos: ${e.message}")
            handleConnectionError()
            false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error enviando datos: ${e.message}")
            false
        }
    }

    /**
     * Envía datos de forma asíncrona (para movimientos del mouse)
     */
    fun sendDataAsync(data: String, onResult: ((Boolean) -> Unit)? = null) {
        managerScope.launch {
            val result = sendData(data)
            onResult?.invoke(result)
        }
    }

    /**
     * Verifica si hay una conexión activa y funcional
     */
    fun isSocketConnected(): Boolean {
        return try {
            currentSocket?.let { socket ->
                isConnected &&
                        socket.isConnected &&
                        outputStream != null &&
                        inputStream != null
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error verificando conexión: ${e.message}")
            false
        }
    }

    /**
     * Obtiene el socket actual
     */
    fun getCurrentSocket(): BluetoothSocket? = currentSocket

    /**
     * Obtiene información del dispositivo conectado
     */
    fun getConnectedDeviceInfo(): String {
        return currentSocket?.remoteDevice?.let { device ->
            "${device.name} (${device.address})"
        } ?: "No conectado"
    }

    /**
     * Test de conexión enviando ping
     */
    private fun testConnection(): Boolean {
        return try {
            val testMessage = "ping\n"
            val output = outputStream ?: return false

            output.write(testMessage.toByteArray())
            output.flush()

            Log.i(TAG, "✅ Test de conexión exitoso")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Test de conexión falló: ${e.message}")
            false
        }
    }

    /**
     * Maneja errores de conexión
     */
    private fun handleConnectionError() {
        Log.w(TAG, "⚠️ Manejando error de conexión...")
        isConnected = false

        // Intentar reconectar si es posible
        currentSocket?.let { socket ->
            if (socket.isConnected) {
                try {
                    outputStream = socket.outputStream
                    inputStream = socket.inputStream
                    isConnected = true
                    Log.i(TAG, "✅ Reconexión exitosa")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Falló la reconexión: ${e.message}")
                    cleanup()
                }
            } else {
                cleanup()
            }
        }
    }

    /**
     * Cierra la conexión actual
     */
    fun closeCurrentSocket(sendDisconnect: Boolean = true) {
        try {
            Log.i(TAG, "🔌 Cerrando conexión...")

            if (isConnected && sendDisconnect) {
                try {
                    sendData("disconnect\n")
                } catch (e: Exception) {
                    // Ignorar errores al desconectar
                }
            }

            cleanup()
            Log.i(TAG, "✅ Conexión cerrada")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cerrando socket: ${e.message}")
            cleanup()
        }
    }

    /**
     * Limpia recursos
     */
    private fun cleanup() {
        try {
            outputStream?.close()
            inputStream?.close()
            currentSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error en cleanup: ${e.message}")
        } finally {
            outputStream = null
            inputStream = null
            currentSocket = null
            isConnected = false
        }
    }

    /**
     * Obtiene estadísticas de conexión
     */
    fun getConnectionStats(): Map<String, Any> {
        return mapOf(
            "isConnected" to isSocketConnected(),
            "deviceInfo" to getConnectedDeviceInfo(),
            "hasOutputStream" to (outputStream != null),
            "hasInputStream" to (inputStream != null),
            "socketConnected" to (currentSocket?.isConnected ?: false)
        )
    }
}
