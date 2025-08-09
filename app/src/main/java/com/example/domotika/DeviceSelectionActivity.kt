package com.example.domotika

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.json.JSONObject

data class DeviceItem(
    val icon: String,
    val name: String,
    val subtitle: String
)

class CustomDeviceAdapter(
    private val context: DeviceSelectionActivity,
    private val devices: List<DeviceItem>
) : BaseAdapter() {

    override fun getCount(): Int = devices.size
    override fun getItem(position: Int): DeviceItem = devices[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.device_list_item, parent, false)

        val device = devices[position]

        view.findViewById<TextView>(R.id.tv_device_name).text = device.name
        view.findViewById<TextView>(R.id.tv_device_subtitle).text = device.subtitle

        return view
    }
}

class DeviceSelectionActivity : AppCompatActivity() {
    private var deviceType: String = ""
    private var tvDevices: List<TvDevice> = emptyList()
    private var projectorDevices: List<ProjectorDevice> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_device_selection)

        deviceType = intent.getStringExtra("device_type") ?: ""

        val titleTextView = findViewById<TextView>(R.id.tv_selection_title)
        val loadingView = findViewById<View>(R.id.loading_view)
        val listView = findViewById<ListView>(R.id.lv_devices)
        val cardContainer = findViewById<View>(R.id.card_devices_container)

        titleTextView.text = when (deviceType) {
            "tv" -> "Selecciona tu TV"
            "projector" -> "Selecciona tu Proyector"
            else -> "Selecciona tu dispositivo"
        }

        // Limpiar datos anteriores
        tvDevices = emptyList()
        projectorDevices = emptyList()

        // Mostrar loading
        loadingView.visibility = View.VISIBLE
        cardContainer.visibility = View.GONE

        // Descargar datos con logs
        lifecycleScope.launch {
            try {
                Log.d("DeviceSelection", "Descargando datos para tipo: $deviceType")

                when (deviceType) {
                    "tv" -> {
                        Log.d("DeviceSelection", "Llamando API de TV...")
                        tvDevices = IrCodeService.downloadTvCodes()
                        Log.d("DeviceSelection", "TV devices descargados: ${tvDevices.size}")
                        tvDevices.forEach { device ->
                            Log.d("DeviceSelection", "TV Device: ${device.brand} ${device.model}")
                        }
                        showTvDevices(tvDevices, listView)
                    }
                    "projector" -> {
                        Log.d("DeviceSelection", "Llamando API de Proyectores...")
                        projectorDevices = IrCodeService.downloadProjectorCodes()
                        Log.d("DeviceSelection", "Projector devices descargados: ${projectorDevices.size}")
                        projectorDevices.forEach { device ->
                            Log.d("DeviceSelection", "Projector Device: ${device.Brand} ${device.Model}")
                        }
                        showProjectorDevices(projectorDevices, listView)
                    }
                }

                loadingView.visibility = View.GONE
                cardContainer.visibility = View.VISIBLE

            } catch (e: Exception) {
                Log.e("DeviceSelection", "Error al cargar dispositivos", e)
                loadingView.visibility = View.GONE
                Toast.makeText(this@DeviceSelectionActivity,
                    "Error al cargar dispositivos: ${e.message}",
                    Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun showTvDevices(devices: List<TvDevice>, listView: ListView) {
        Log.d("DeviceSelection", "Mostrando ${devices.size} dispositivos TV")
        val adapter = CustomDeviceAdapter(this, devices.map {
            DeviceItem("📺", "${it.brand} ${it.model}", "TV - Códigos IR disponibles")
        })
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedDevice = devices[position]
            Log.d("DeviceSelection", "Seleccionado TV: ${selectedDevice.brand} ${selectedDevice.model}")
            openIrControl("📺 ${selectedDevice.brand} ${selectedDevice.model}", selectedDevice)
        }
    }

    private fun showProjectorDevices(devices: List<ProjectorDevice>, listView: ListView) {
        Log.d("DeviceSelection", "Mostrando ${devices.size} dispositivos Proyector")
        val adapter = CustomDeviceAdapter(this, devices.map {
            DeviceItem("🎥", "${it.Brand} ${it.Model}", "Proyector - Códigos IR disponibles")
        })
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedDevice = devices[position]
            Log.d("DeviceSelection", "Seleccionado Proyector: ${selectedDevice.Brand} ${selectedDevice.Model}")
            openIrControl("🎥 ${selectedDevice.Brand} ${selectedDevice.Model}", selectedDevice)
        }
    }

    private fun openIrControl(deviceName: String, device: Any) {
        val intent = Intent(this, InfraredControlActivity::class.java)
        intent.putExtra("dispositivo_nombre", deviceName)
        intent.putExtra("device_type", deviceType)

        // Serializar el dispositivo según tipo
        when (device) {
            is TvDevice -> {
                intent.putExtra("device_data", serializeTvDevice(device))
                Log.d("DeviceSelection", "Enviando datos TV: ${device.brand} ${device.model}")
            }
            is ProjectorDevice -> {
                intent.putExtra("device_data", serializeProjectorDevice(device))
                Log.d("DeviceSelection", "Enviando datos Proyector: ${device.Brand} ${device.Model}")
            }
        }

        startActivity(intent)
    }

    private fun serializeTvDevice(device: TvDevice): String {
        val json = JSONObject().apply {
            put("id", device.id)
            put("brand", device.brand)
            put("model", device.model)
            put("protocol", device.protocol)
            put("buttons", JSONObject().apply {
                put("power", device.buttons.power)
                put("mute", device.buttons.mute)
                put("vol_up", device.buttons.vol_up)
                put("vol_down", device.buttons.vol_down)
                put("menu", device.buttons.menu)
                put("source", device.buttons.source)
                put("aspect", device.buttons.aspect)
                put("color_mode", device.buttons.color_mode)
                put("exit", device.buttons.exit)
                put("up", device.buttons.up)
                put("down", device.buttons.down)
                put("left", device.buttons.left)
                put("right", device.buttons.right)
                put("ok", device.buttons.ok)
            })
        }
        return json.toString()
    }

    private fun serializeProjectorDevice(device: ProjectorDevice): String {
        val json = JSONObject().apply {
            put("Id", device.Id)
            put("Brand", device.Brand)
            put("Model", device.Model)
            put("Protocol", device.Protocol)
            put("Buttons", JSONObject().apply {
                put("Power", device.Buttons.Power)
                put("Mute", device.Buttons.Mute)
                put("VolUp", device.Buttons.VolUp)
                put("VolDown", device.Buttons.VolDown)
                put("Menu", device.Buttons.Menu)
                put("Source", device.Buttons.Source)
                put("Aspect", device.Buttons.Aspect)
                put("ColorMode", device.Buttons.ColorMode)
                put("Exit", device.Buttons.Exit)
                put("Up", device.Buttons.Up)
                put("Down", device.Buttons.Down)
                put("Left", device.Buttons.Left)
                put("Right", device.Buttons.Right)
                put("Ok", device.Buttons.Ok)
            })
        }
        return json.toString()
    }
}