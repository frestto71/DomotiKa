package com.example.domotika

import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// ========================================
// CLASES DE DATOS PARA LA API
// ========================================

// Para TV (minúsculas)
data class TvDevice(
    val id: String,
    val brand: String,
    val model: String,
    val protocol: String,
    val buttons: TvButtons
)

data class TvButtons(
    val power: String,
    val mute: String,
    val vol_up: String,
    val vol_down: String,
    val menu: String,
    val source: String,
    val aspect: String,
    val color_mode: String,
    val exit: String,
    val up: String,
    val down: String,
    val left: String,
    val right: String,
    val ok: String,
    val channel_up: String,
    val channel_down: String
)

// Para Proyectores (mayúsculas)
data class ProjectorDevice(
    val Id: String,
    val Brand: String,
    val Model: String,
    val Protocol: String,
    val Buttons: ProjectorButtons
)

data class ProjectorButtons(
    val Power: String,
    val Mute: String,
    val VolUp: String,
    val VolDown: String,
    val Menu: String,
    val Source: String,
    val Aspect: String,
    val ColorMode: String,
    val Exit: String,
    val Up: String,
    val Down: String,
    val Left: String,
    val Right: String,
    val Ok: String
)

// ========================================
// CONVERSOR DE CÓDIGOS HEX A PULSOS IR
// ========================================

class IrCodeConverter {
    companion object {
        // Convierte código hexadecimal a pulsos IR
        fun hexToIrPulses(hexCode: String, protocol: String = "NEC2"): IntArray {
            return when (protocol.uppercase()) {
                "NEC", "NEC2" -> convertNecProtocol(hexCode)
                else -> convertNecProtocol(hexCode) // Por defecto NEC
            }
        }

        private fun convertNecProtocol(hexCode: String): IntArray {
            // Remover prefijos si existen
            val cleanHex = hexCode.replace("0x", "").replace("C1AA", "")

            // Convertir a número
            val code = cleanHex.toLongOrNull(16) ?: 0L

            // Crear pulsos NEC estándar
            val pulses = mutableListOf<Int>()

            // Header NEC: 9ms encendido, 4.5ms apagado
            pulses.add(9000)
            pulses.add(4500)

            // Convertir cada bit (16 bits para códigos cortos)
            for (i in 15 downTo 0) {
                val bit = (code shr i) and 1
                if (bit == 1L) {
                    // Bit 1: 560µs encendido, 1690µs apagado
                    pulses.add(560)
                    pulses.add(1690)
                } else {
                    // Bit 0: 560µs encendido, 560µs apagado
                    pulses.add(560)
                    pulses.add(560)
                }
            }

            // Bit de parada
            pulses.add(560)

            return pulses.toIntArray()
        }
    }
}

// ========================================
// SERVICIO DE RED PARA DESCARGAR CÓDIGOS
// ========================================

class IrCodeService {
    companion object {
        private const val BASE_URL = "https://doordor.bsite.net/api"

        suspend fun downloadTvCodes(): List<TvDevice> = withContext(Dispatchers.IO) {
            val endpoint = "$BASE_URL/tv"
            Log.d("IrCodeService", "Descargando códigos TV desde: $endpoint")

            try {
                val url = URL(endpoint)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                Log.d("IrCodeService", "Conectando a API TV...")
                val responseCode = connection.responseCode
                Log.d("IrCodeService", "Código de respuesta TV: $responseCode")

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().readText()
                    Log.d("IrCodeService", "Respuesta TV recibida, longitud: ${response.length}")
                    Log.d("IrCodeService", "Primeros 200 chars TV: ${response.take(200)}")
                    val devices = parseTvDevices(response)
                    Log.d("IrCodeService", "Dispositivos TV parseados: ${devices.size}")
                    devices
                } else {
                    Log.e("IrCodeService", "Error HTTP TV: $responseCode")
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e("IrCodeService", "Error descargando códigos TV", e)
                emptyList()
            }
        }

        suspend fun downloadProjectorCodes(): List<ProjectorDevice> = withContext(Dispatchers.IO) {
            val endpoint = "$BASE_URL/projector"
            Log.d("IrCodeService", "Descargando códigos Proyector desde: $endpoint")

            try {
                val url = URL(endpoint)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 10000
                connection.readTimeout = 10000

                Log.d("IrCodeService", "Conectando a API Proyector...")
                val responseCode = connection.responseCode
                Log.d("IrCodeService", "Código de respuesta Proyector: $responseCode")

                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = connection.inputStream.bufferedReader().readText()
                    Log.d("IrCodeService", "Respuesta Proyector recibida, longitud: ${response.length}")
                    Log.d("IrCodeService", "Primeros 200 chars Proyector: ${response.take(200)}")
                    val devices = parseProjectorDevices(response)
                    Log.d("IrCodeService", "Dispositivos Proyector parseados: ${devices.size}")
                    devices
                } else {
                    Log.e("IrCodeService", "Error HTTP Proyector: $responseCode")
                    emptyList()
                }
            } catch (e: Exception) {
                Log.e("IrCodeService", "Error descargando códigos Proyector", e)
                emptyList()
            }
        }

        private fun parseTvDevices(jsonString: String): List<TvDevice> {
            val devices = mutableListOf<TvDevice>()
            try {
                Log.d("IrCodeService", "Parseando TV devices...")
                val jsonArray = JSONArray(jsonString)
                Log.d("IrCodeService", "JSON Array length: ${jsonArray.length()}")

                for (i in 0 until jsonArray.length()) {
                    try {
                        val deviceJson = jsonArray.getJSONObject(i)
                        val buttonsJson = deviceJson.getJSONObject("buttons")

                        Log.d("IrCodeService", "Parseando dispositivo TV #$i: ${deviceJson.optString("brand")} ${deviceJson.optString("model")}")

                        val buttons = TvButtons(
                            power = buttonsJson.optString("power", ""),
                            mute = buttonsJson.optString("mute", ""),
                            vol_up = buttonsJson.optString("vol_up", ""),
                            vol_down = buttonsJson.optString("vol_down", ""),
                            menu = buttonsJson.optString("menu", ""),
                            source = buttonsJson.optString("input", ""), // La API usa "input" en lugar de "source"
                            aspect = buttonsJson.optString("aspect", ""),
                            color_mode = buttonsJson.optString("color_mode", ""),
                            exit = buttonsJson.optString("exit", ""),
                            up = buttonsJson.optString("up", ""),
                            down = buttonsJson.optString("down", ""),
                            left = buttonsJson.optString("left", ""),
                            right = buttonsJson.optString("right", ""),
                            ok = buttonsJson.optString("ok", ""),
                            channel_up = buttonsJson.optString("channel_up", ""),
                            channel_down = buttonsJson.optString("channel_down", "")
                        )

                        val device = TvDevice(
                            id = deviceJson.optString("id", ""),
                            brand = deviceJson.optString("brand", ""),
                            model = deviceJson.optString("model", ""),
                            protocol = deviceJson.optString("protocol", "NEC"),
                            buttons = buttons
                        )

                        devices.add(device)
                        Log.d("IrCodeService", "Dispositivo TV agregado: ${device.brand} ${device.model}")

                    } catch (e: Exception) {
                        Log.e("IrCodeService", "Error parseando dispositivo TV #$i", e)
                        // Continuar con el siguiente dispositivo
                    }
                }

                Log.d("IrCodeService", "Total dispositivos TV parseados: ${devices.size}")

            } catch (e: Exception) {
                Log.e("IrCodeService", "Error general parseando TV devices", e)
            }
            return devices
        }

        private fun parseProjectorDevices(jsonString: String): List<ProjectorDevice> {
            val devices = mutableListOf<ProjectorDevice>()
            try {
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val deviceJson = jsonArray.getJSONObject(i)
                    val buttonsJson = deviceJson.getJSONObject("Buttons")

                    val buttons = ProjectorButtons(
                        Power = buttonsJson.getString("Power"),
                        Mute = buttonsJson.getString("Mute"),
                        VolUp = buttonsJson.getString("VolUp"),
                        VolDown = buttonsJson.getString("VolDown"),
                        Menu = buttonsJson.getString("Menu"),
                        Source = buttonsJson.getString("Source"),
                        Aspect = buttonsJson.optString("Aspect", ""),
                        ColorMode = buttonsJson.optString("ColorMode", ""),
                        Exit = buttonsJson.getString("Exit"),
                        Up = buttonsJson.getString("Up"),
                        Down = buttonsJson.getString("Down"),
                        Left = buttonsJson.getString("Left"),
                        Right = buttonsJson.getString("Right"),
                        Ok = buttonsJson.getString("Ok")
                    )

                    val device = ProjectorDevice(
                        Id = deviceJson.getString("Id"),
                        Brand = deviceJson.getString("Brand"),
                        Model = deviceJson.getString("Model"),
                        Protocol = deviceJson.getString("Protocol"),
                        Buttons = buttons
                    )

                    devices.add(device)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            return devices
        }
    }
}