package com.example.domotika

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class InfraredControlActivity : AppCompatActivity() {

    private var irManager: ConsumerIrManager? = null
    private var isDeviceOn = false

    // Variables para códigos dinámicos
    private var deviceType: String = ""
    private var currentTvDevice: TvDevice? = null
    private var currentProjectorDevice: ProjectorDevice? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_infrared_control)

        // Obtener el servicio IR de forma segura
        irManager = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

        if (irManager == null || !irManager!!.hasIrEmitter()) {
            // Mostrar diálogo si no tiene emisor IR o el servicio no existe
            AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage("Este dispositivo no tiene emisor infrarrojo.")
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                    finish()
                }
                .setCancelable(false)
                .show()
            return
        }

        // Obtener datos del dispositivo
        val nombreDispositivo = intent.getStringExtra("dispositivo_nombre") ?: "Dispositivo"
        deviceType = intent.getStringExtra("device_type") ?: ""
        val deviceDataJson = intent.getStringExtra("device_data") ?: ""

        // Parsear dispositivo según tipo
        if (deviceDataJson.isNotEmpty()) {
            try {
                Log.d("InfraredControl", "Parseando datos del dispositivo tipo: $deviceType")
                when (deviceType) {
                    "tv" -> {
                        currentTvDevice = parseTvDeviceFromJson(deviceDataJson)
                        Log.d("InfraredControl", "TV Device cargado: ${currentTvDevice?.brand} ${currentTvDevice?.model}")
                    }
                    "projector" -> {
                        currentProjectorDevice = parseProjectorDeviceFromJson(deviceDataJson)
                        Log.d("InfraredControl", "Projector Device cargado: ${currentProjectorDevice?.Brand} ${currentProjectorDevice?.Model}")
                    }
                }
            } catch (e: Exception) {
                Log.e("InfraredControl", "Error al cargar datos del dispositivo", e)
                Toast.makeText(this, "Error al cargar datos del dispositivo", Toast.LENGTH_SHORT).show()
                // No hacer finish, usar códigos por defecto
            }
        } else {
            Log.w("InfraredControl", "No hay datos de dispositivo, se usarán códigos por defecto")
        }

        // Configurar UI
        setupUI(nombreDispositivo)
    }

    private fun setupUI(nombreDispositivo: String) {
        // Usar los elementos que SÍ existen en el XML
        val tvMarca = findViewById<TextView>(R.id.tv_marca)
        val btnPower = findViewById<ImageButton>(R.id.btn_power)

        // Configurar otros botones del control remoto
        val btnVolPlus = findViewById<Button>(R.id.btn_vol_plus)
        val btnVolMinus = findViewById<Button>(R.id.btn_vol_minus)
        val btnChPlus = findViewById<Button>(R.id.btn_ch_plus)
        val btnChMinus = findViewById<Button>(R.id.btn_ch_minus)
        val btnMute = findViewById<ImageButton>(R.id.btn_mute)
        val btnOk = findViewById<Button>(R.id.btn_ok)

        // Botones direccionales
        val btnUp = findViewById<ImageButton>(R.id.btn_up)
        val btnDown = findViewById<ImageButton>(R.id.btn_down)
        val btnLeft = findViewById<ImageButton>(R.id.btn_left)
        val btnRight = findViewById<ImageButton>(R.id.btn_right)

        // Botones numéricos
        val btn0 = findViewById<Button>(R.id.btn_0)
        val btn1 = findViewById<Button>(R.id.btn_1)
        val btn2 = findViewById<Button>(R.id.btn_2)
        val btn3 = findViewById<Button>(R.id.btn_3)
        val btn4 = findViewById<Button>(R.id.btn_4)
        val btn5 = findViewById<Button>(R.id.btn_5)
        val btn6 = findViewById<Button>(R.id.btn_6)
        val btn7 = findViewById<Button>(R.id.btn_7)
        val btn8 = findViewById<Button>(R.id.btn_8)
        val btn9 = findViewById<Button>(R.id.btn_9)

        // Otros botones
        val btnMenu = findViewById<ImageButton>(R.id.btn_menu)
        val btnHome = findViewById<ImageButton>(R.id.btn_home)
        val btnBack = findViewById<ImageButton>(R.id.btn_back)
        val btnInfo = findViewById<ImageButton>(R.id.btn_info)
        val btnExit = findViewById<ImageButton>(R.id.btn_exit)
        val btnInput = findViewById<ImageButton>(R.id.btn_input)

        // Configurar el título con el nombre del dispositivo
        tvMarca.text = nombreDispositivo

        // Configurar botón de power (encender/apagar)
        btnPower.setOnClickListener {
            isDeviceOn = !isDeviceOn
            enviarCodigoIR("power")
        }

        // Configurar otros botones del control remoto
        btnVolPlus.setOnClickListener { enviarCodigoIR("vol_up") }
        btnVolMinus.setOnClickListener { enviarCodigoIR("vol_down") }
        btnChPlus.setOnClickListener { enviarCodigoIR("ch_up") }
        btnChMinus.setOnClickListener { enviarCodigoIR("ch_down") }
        btnMute.setOnClickListener { enviarCodigoIR("mute") }
        btnOk.setOnClickListener { enviarCodigoIR("ok") }

        // Botones direccionales
        btnUp.setOnClickListener { enviarCodigoIR("up") }
        btnDown.setOnClickListener { enviarCodigoIR("down") }
        btnLeft.setOnClickListener { enviarCodigoIR("left") }
        btnRight.setOnClickListener { enviarCodigoIR("right") }

        // Botones numéricos
        btn0.setOnClickListener { enviarCodigoIR("0") }
        btn1.setOnClickListener { enviarCodigoIR("1") }
        btn2.setOnClickListener { enviarCodigoIR("2") }
        btn3.setOnClickListener { enviarCodigoIR("3") }
        btn4.setOnClickListener { enviarCodigoIR("4") }
        btn5.setOnClickListener { enviarCodigoIR("5") }
        btn6.setOnClickListener { enviarCodigoIR("6") }
        btn7.setOnClickListener { enviarCodigoIR("7") }
        btn8.setOnClickListener { enviarCodigoIR("8") }
        btn9.setOnClickListener { enviarCodigoIR("9") }

        // Otros botones
        btnMenu.setOnClickListener { enviarCodigoIR("menu") }
        btnHome.setOnClickListener { enviarCodigoIR("home") }
        btnBack.setOnClickListener { enviarCodigoIR("back") }
        btnInfo.setOnClickListener { enviarCodigoIR("info") }
        btnExit.setOnClickListener { enviarCodigoIR("exit") }
        btnInput.setOnClickListener { enviarCodigoIR("source") }
    }

    private fun enviarCodigoIR(button: String) {
        val hexCode = getHexCodeForButton(button)
        if (hexCode.isNotEmpty()) {
            val protocol = when (deviceType) {
                "tv" -> currentTvDevice?.protocol ?: "NEC2"
                "projector" -> currentProjectorDevice?.Protocol ?: "NEC2"
                else -> "NEC2"
            }

            val pulses = IrCodeConverter.hexToIrPulses(hexCode, protocol)
            irManager?.transmit(38000, pulses)

            Toast.makeText(this, "Enviando: $button", Toast.LENGTH_SHORT).show()
        } else {
            // Usar códigos por defecto si no hay códigos de la API
            enviarCodigoPorDefecto(button)
        }
    }

    private fun getHexCodeForButton(button: String): String {
        Log.d("InfraredControl", "Buscando código para botón: $button en tipo: $deviceType")

        return when (deviceType) {
            "tv" -> {
                currentTvDevice?.buttons?.let { buttons ->
                    Log.d("InfraredControl", "Dispositivo TV: ${currentTvDevice?.brand} ${currentTvDevice?.model}")
                    val code = when (button) {
                        "power" -> buttons.power
                        "mute" -> buttons.mute
                        "vol_up" -> buttons.vol_up
                        "vol_down" -> buttons.vol_down
                        "ch_up" -> buttons.channel_up
                        "ch_down" -> buttons.channel_down
                        "menu" -> buttons.menu
                        "source" -> buttons.source
                        "exit" -> buttons.exit
                        "up" -> buttons.up
                        "down" -> buttons.down
                        "left" -> buttons.left
                        "right" -> buttons.right
                        "ok" -> buttons.ok
                        else -> ""
                    }
                    Log.d("InfraredControl", "Código TV encontrado para $button: $code")
                    code
                } ?: run {
                    Log.d("InfraredControl", "No hay dispositivo TV cargado")
                    ""
                }
            }
            "projector" -> {
                currentProjectorDevice?.Buttons?.let { buttons ->
                    Log.d("InfraredControl", "Dispositivo Proyector: ${currentProjectorDevice?.Brand} ${currentProjectorDevice?.Model}")
                    val code = when (button) {
                        "power" -> buttons.Power
                        "mute" -> buttons.Mute
                        "vol_up" -> buttons.VolUp
                        "vol_down" -> buttons.VolDown
                        "menu" -> buttons.Menu
                        "source" -> buttons.Source
                        "exit" -> buttons.Exit
                        "up" -> buttons.Up
                        "down" -> buttons.Down
                        "left" -> buttons.Left
                        "right" -> buttons.Right
                        "ok" -> buttons.Ok
                        else -> ""
                    }
                    Log.d("InfraredControl", "Código Proyector encontrado para $button: $code")
                    code
                } ?: run {
                    Log.d("InfraredControl", "No hay dispositivo Proyector cargado")
                    ""
                }
            }
            else -> {
                Log.d("InfraredControl", "Tipo de dispositivo desconocido: $deviceType")
                ""
            }
        }
    }

    // Métodos de respaldo usando códigos por defecto
    private fun enviarCodigoPorDefecto(button: String) {
        val codigo = when (button) {
            "power" -> CodigoIR(38000, codigoDefaultPower)
            "vol_up" -> CodigoIR(38000, codigoVolumenMas)
            "vol_down" -> CodigoIR(38000, codigoVolumenMenos)
            "ch_up" -> CodigoIR(38000, codigoCanalMas)
            "ch_down" -> CodigoIR(38000, codigoCanalMenos)
            "mute" -> CodigoIR(38000, codigoMute)
            "ok" -> CodigoIR(38000, codigoOK)
            "up" -> CodigoIR(38000, codigoArriba)
            "down" -> CodigoIR(38000, codigoAbajo)
            "left" -> CodigoIR(38000, codigoIzquierda)
            "right" -> CodigoIR(38000, codigoDerecha)
            "menu" -> CodigoIR(38000, codigoMenu)
            "back" -> CodigoIR(38000, codigoBack)
            "info" -> CodigoIR(38000, codigoInfo)
            "exit" -> CodigoIR(38000, codigoExit)
            "source" -> CodigoIR(38000, codigoInput)
            "0" -> CodigoIR(38000, codigo0)
            "1" -> CodigoIR(38000, codigo1)
            "2" -> CodigoIR(38000, codigo2)
            "3" -> CodigoIR(38000, codigo3)
            "4" -> CodigoIR(38000, codigo4)
            "5" -> CodigoIR(38000, codigo5)
            "6" -> CodigoIR(38000, codigo6)
            "7" -> CodigoIR(38000, codigo7)
            "8" -> CodigoIR(38000, codigo8)
            "9" -> CodigoIR(38000, codigo9)
            else -> null
        }

        if (codigo != null) {
            Log.d("InfraredControl", "Enviando código por defecto para: $button")
            irManager?.transmit(codigo.frecuencia, codigo.patron)
            Toast.makeText(this, "Enviando código por defecto: $button", Toast.LENGTH_SHORT).show()
        } else {
            Log.w("InfraredControl", "No hay código disponible para: $button")
            Toast.makeText(this, "Código no disponible para: $button", Toast.LENGTH_SHORT).show()
        }
    }

    private fun parseTvDeviceFromJson(json: String): TvDevice {
        val jsonObj = JSONObject(json)
        val buttonsObj = jsonObj.getJSONObject("buttons")

        val buttons = TvButtons(
            power = buttonsObj.optString("power", ""),
            mute = buttonsObj.optString("mute", ""),
            vol_up = buttonsObj.optString("vol_up", ""),
            vol_down = buttonsObj.optString("vol_down", ""),
            menu = buttonsObj.optString("menu", ""),
            source = buttonsObj.optString("source", ""),
            aspect = buttonsObj.optString("aspect", ""),
            color_mode = buttonsObj.optString("color_mode", ""),
            exit = buttonsObj.optString("exit", ""),
            up = buttonsObj.optString("up", ""),
            down = buttonsObj.optString("down", ""),
            left = buttonsObj.optString("left", ""),
            right = buttonsObj.optString("right", ""),
            ok = buttonsObj.optString("ok", ""),
            channel_up = buttonsObj.optString("channel_up", ""),
            channel_down = buttonsObj.optString("channel_down", "")
        )

        return TvDevice(
            id = jsonObj.optString("id", ""),
            brand = jsonObj.optString("brand", ""),
            model = jsonObj.optString("model", ""),
            protocol = jsonObj.optString("protocol", "NEC"),
            buttons = buttons
        )
    }

    private fun parseProjectorDeviceFromJson(json: String): ProjectorDevice {
        val jsonObj = JSONObject(json)
        val buttonsObj = jsonObj.getJSONObject("Buttons")

        val buttons = ProjectorButtons(
            Power = buttonsObj.getString("Power"),
            Mute = buttonsObj.getString("Mute"),
            VolUp = buttonsObj.getString("VolUp"),
            VolDown = buttonsObj.getString("VolDown"),
            Menu = buttonsObj.getString("Menu"),
            Source = buttonsObj.getString("Source"),
            Aspect = buttonsObj.optString("Aspect", ""),
            ColorMode = buttonsObj.optString("ColorMode", ""),
            Exit = buttonsObj.getString("Exit"),
            Up = buttonsObj.getString("Up"),
            Down = buttonsObj.getString("Down"),
            Left = buttonsObj.getString("Left"),
            Right = buttonsObj.getString("Right"),
            Ok = buttonsObj.getString("Ok")
        )

        return ProjectorDevice(
            Id = jsonObj.getString("Id"),
            Brand = jsonObj.getString("Brand"),
            Model = jsonObj.getString("Model"),
            Protocol = jsonObj.getString("Protocol"),
            Buttons = buttons
        )
    }

    // Códigos IR por defecto
    private val codigoDefaultPower = intArrayOf(
        9050, 6250, 50, 2150, 100, 750, 550, 450, 750, 350, 800, 300, 800, 300, 850, 1750, 150, 2050, 100,
        800, 550, 1850, 150, 750, 550, 1900, 100, 800, 550, 1850, 150, 750, 550, 450, 750, 350, 750, 350, 800,
        300, 800, 1750, 200, 700, 600, 450, 700, 1800, 150, 2100, 50, 2150, 100, 2100, 100, 2150, 50, 800, 550,
        1850, 150, 2100, 50, 800, 550
    )

    private val codigoVolumenMas = intArrayOf(
        9000, 4500, 560, 560, 560, 560, 560, 1690, 560, 560, 560, 560, 560, 560, 560, 560, 560, 1690
    )

    private val codigoVolumenMenos = intArrayOf(
        9000, 4500, 560, 560, 560, 560, 560, 1690, 560, 560, 560, 560, 560, 560, 560, 1690, 560, 560
    )

    private val codigoCanalMas = intArrayOf(
        9000, 4500, 560, 560, 560, 1690, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 1690
    )

    private val codigoCanalMenos = intArrayOf(
        9000, 4500, 560, 560, 560, 1690, 560, 560, 560, 560, 560, 560, 560, 560, 560, 1690, 560, 560
    )

    private val codigoMute = intArrayOf(
        9000, 4500, 560, 1690, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 1690
    )

    private val codigoOK = intArrayOf(
        9000, 4500, 560, 1690, 560, 1690, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560
    )

    private val codigoArriba = intArrayOf(
        9000, 4500, 560, 1690, 560, 1690, 560, 1690, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560
    )

    private val codigoAbajo = intArrayOf(
        9000, 4500, 560, 1690, 560, 1690, 560, 1690, 560, 1690, 560, 560, 560, 560, 560, 560, 560, 560
    )

    private val codigoIzquierda = intArrayOf(
        9000, 4500, 560, 1690, 560, 1690, 560, 560, 560, 1690, 560, 560, 560, 560, 560, 560, 560, 560
    )

    private val codigoDerecha = intArrayOf(
        9000, 4500, 560, 1690, 560, 1690, 560, 560, 560, 560, 560, 1690, 560, 560, 560, 560, 560, 560
    )

    private val codigo0 = intArrayOf(9000, 4500, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560)
    private val codigo1 = intArrayOf(9000, 4500, 560, 1690, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560)
    private val codigo2 = intArrayOf(9000, 4500, 560, 560, 560, 1690, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560)
    private val codigo3 = intArrayOf(9000, 4500, 560, 1690, 560, 1690, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560)
    private val codigo4 = intArrayOf(9000, 4500, 560, 560, 560, 560, 560, 1690, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560)
    private val codigo5 = intArrayOf(9000, 4500, 560, 1690, 560, 560, 560, 1690, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560)
    private val codigo6 = intArrayOf(9000, 4500, 560, 560, 560, 1690, 560, 1690, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560)
    private val codigo7 = intArrayOf(9000, 4500, 560, 1690, 560, 1690, 560, 1690, 560, 560, 560, 560, 560, 560, 560, 560, 560, 560)
    private val codigo8 = intArrayOf(9000, 4500, 560, 560, 560, 560, 560, 560, 560, 1690, 560, 560, 560, 560, 560, 560, 560, 560)
    private val codigo9 = intArrayOf(9000, 4500, 560, 1690, 560, 560, 560, 560, 560, 1690, 560, 560, 560, 560, 560, 560, 560, 560)

    private val codigoMenu = intArrayOf(9000, 4500, 560, 560, 560, 1690, 560, 1690, 560, 1690, 560, 560, 560, 560, 560, 560, 560, 560)
    private val codigoHome = intArrayOf(9000, 4500, 560, 1690, 560, 1690, 560, 1690, 560, 1690, 560, 560, 560, 560, 560, 560, 560, 560)
    private val codigoBack = intArrayOf(9000, 4500, 560, 560, 560, 560, 560, 1690, 560, 1690, 560, 560, 560, 560, 560, 560, 560, 560)
    private val codigoInfo = intArrayOf(9000, 4500, 560, 1690, 560, 560, 560, 1690, 560, 1690, 560, 560, 560, 560, 560, 560, 560, 560)
    private val codigoExit = intArrayOf(9000, 4500, 560, 560, 560, 1690, 560, 560, 560, 1690, 560, 560, 560, 560, 560, 560, 560, 560)
    private val codigoInput = intArrayOf(9000, 4500, 560, 1690, 560, 1690, 560, 560, 560, 1690, 560, 560, 560, 560, 560, 560, 560, 560)

    data class CodigoIR(val frecuencia: Int, val patron: IntArray)
}