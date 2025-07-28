package com.example.domotika

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import android.app.Dialog
import kotlinx.coroutines.*
import java.io.IOException
import java.util.*

class BluetoothActivity : AppCompatActivity(), BluetoothDeviceAdapter.OnDeviceClickListener {

    companion object {
        private const val REQUEST_ENABLE_BT = 1
        private const val REQUEST_PERMISSIONS = 2

        // UUIDs para diferentes tipos de dispositivos
        private val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Serial Port Profile
        private val GENERIC_UUID: UUID = UUID.fromString("00001801-0000-1000-8000-00805F9B34FB") // Generic Attribute
        private val DEVICE_INFO_UUID: UUID = UUID.fromString("0000180A-0000-1000-8000-00805F9B34FB") // Device Information

        private val COMMON_UUIDS = listOf(SPP_UUID, GENERIC_UUID, DEVICE_INFO_UUID)
    }

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var devicesList: RecyclerView
    private lateinit var scanningLayout: LinearLayout
    private lateinit var emptyView: ScrollView
    private lateinit var btnAddBluetooth: MaterialButton
    private lateinit var emptyMessageText: TextView

    private val discoveredDevices = HashSet<BluetoothDevice>()
    private lateinit var deviceAdapter: BluetoothDeviceAdapter

    // Para manejar conexiones activas
    private var bluetoothSocket: BluetoothSocket? = null
    private var connectionJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth)

        // Inicializar vistas
        devicesList = findViewById(R.id.bluetooth_devices_list)
        scanningLayout = findViewById(R.id.scanning_layout)
        emptyView = findViewById(R.id.empty_view)
        btnAddBluetooth = findViewById(R.id.btn_add_bluetooth)
        emptyMessageText = findViewById(R.id.empty_message_bluetooth)

        // Configurar RecyclerView
        devicesList.layoutManager = LinearLayoutManager(this)
        deviceAdapter = BluetoothDeviceAdapter(ArrayList(), this)
        devicesList.adapter = deviceAdapter

        // Obtener adaptador Bluetooth
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Este dispositivo no soporta Bluetooth", Toast.LENGTH_LONG).show()
            emptyMessageText.text = "Bluetooth no disponible en este dispositivo."
            btnAddBluetooth.isEnabled = false
            return
        }

        // Registrar receptor para descubrimiento de dispositivos
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        }
        registerReceiver(bluetoothReceiver, filter)

        // Configurar botón para búsqueda
        btnAddBluetooth.setOnClickListener { startBluetoothDiscovery() }

        // Chequear permisos y comenzar descubrimiento
        checkPermissionsAndStartDiscovery()
    }

    private fun checkPermissionsAndStartDiscovery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) {
                requestBluetoothPermissions()
            } else {
                startBluetoothDiscovery()
            }
        } else {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestBluetoothPermissions()
            } else {
                startBluetoothDiscovery()
            }
        }
    }

    private fun requestBluetoothPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                REQUEST_PERMISSIONS
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ),
                REQUEST_PERMISSIONS
            )
        }
    }

    private fun startBluetoothDiscovery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED
            ) {
                requestBluetoothPermissions()
                return
            }
        } else if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestBluetoothPermissions()
            return
        }

        if (!bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            return
        }

        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }

        discoveredDevices.clear()
        deviceAdapter.updateDevices(ArrayList())
        showScanningUI()
        bluetoothAdapter.startDiscovery()
    }

    private fun showScanningUI() {
        emptyView.visibility = View.GONE
        devicesList.visibility = View.GONE
        scanningLayout.visibility = View.VISIBLE
    }

    private fun showDevicesList() {
        emptyView.visibility = View.GONE
        scanningLayout.visibility = View.GONE
        devicesList.visibility = View.VISIBLE
    }

    private fun showEmptyView() {
        devicesList.visibility = View.GONE
        scanningLayout.visibility = View.GONE
        emptyView.visibility = View.VISIBLE
    }

    override fun onDeviceClick(device: BluetoothDevice) {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            requestBluetoothPermissions()
            return
        }

        // Cancelar descubrimiento para mejorar la conexión
        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }

        // Detectar tipo de dispositivo
        val deviceClass = device.bluetoothClass
        val deviceType = detectDeviceType(device, deviceClass)

        when (deviceType) {
            DeviceType.AUDIO_DEVICE -> handleAudioDevice(device)
            DeviceType.LAPTOP_DEVICE -> connectToLaptopDirectly(device)
            DeviceType.DATA_DEVICE -> connectToDevice(device)
            DeviceType.UNKNOWN -> showDeviceTypeDialog(device)
        }
    }

    private enum class DeviceType {
        AUDIO_DEVICE,
        DATA_DEVICE,
        LAPTOP_DEVICE,
        UNKNOWN
    }

    private fun detectDeviceType(device: BluetoothDevice, deviceClass: android.bluetooth.BluetoothClass?): DeviceType {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return DeviceType.UNKNOWN
        }

        // Verificar por nombre del dispositivo
        val deviceName = device.name?.lowercase() ?: ""

        // Palabras clave para dispositivos de audio
        val audioKeywords = listOf("headphone", "headset", "earbuds", "speaker", "audio", "beats", "airpods", "sony", "bose", "jbl")

        // Palabras clave para laptops/computadoras - AMPLIADA
        val laptopKeywords = listOf(
            "laptop", "notebook", "computer", "pc", "desktop",
            "macbook", "thinkpad", "dell", "hp", "lenovo",
            "asus", "acer", "surface", "windows", "mac",
            "gaming", "workstation", "tower", "mini-pc"
        )

        when {
            audioKeywords.any { deviceName.contains(it) } -> return DeviceType.AUDIO_DEVICE
            laptopKeywords.any { deviceName.contains(it) } -> return DeviceType.LAPTOP_DEVICE
        }

        // Verificar por clase de dispositivo
        deviceClass?.let { btClass ->
            val majorDeviceClass = btClass.majorDeviceClass

            when (majorDeviceClass) {
                android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO -> {
                    return DeviceType.AUDIO_DEVICE
                }
                android.bluetooth.BluetoothClass.Device.Major.COMPUTER -> {
                    return DeviceType.LAPTOP_DEVICE
                }
                android.bluetooth.BluetoothClass.Device.Major.PERIPHERAL,
                android.bluetooth.BluetoothClass.Device.Major.PHONE -> {
                    return DeviceType.DATA_DEVICE
                }
                else -> {
                    return DeviceType.UNKNOWN
                }
            }
        }

        return DeviceType.UNKNOWN
    }

    private fun connectToLaptopDirectly(device: BluetoothDevice) {
        val deviceName = if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            device.name ?: "Laptop"
        } else {
            "Laptop"
        }

        AlertDialog.Builder(this)
            .setTitle("💻 Laptop Detectada")
            .setMessage("$deviceName parece ser una laptop.\n\n¿Quieres conectarte como mouse virtual?\n\n" +
                    "✅ Se abrirá una pantalla de touchpad\n" +
                    "✅ Controla el cursor con tu dedo\n" +
                    "✅ Click izquierdo/derecho y scroll\n" +
                    "✅ Funciona en tiempo real")
            .setPositiveButton("🖱️ Sí, usar como Mouse") { _, _ ->
                connectToDeviceAsLaptop(device)
            }
            .setNegativeButton("📱 Conexión Normal") { _, _ ->
                connectToDevice(device)
            }
            .setNeutralButton("Cancelar", null)
            .show()
    }

    private fun connectToDeviceAsLaptop(device: BluetoothDevice) {
        val dialog = createConnectionDialog(device)
        dialog.show()

        connectionJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val deviceName = if (ActivityCompat.checkSelfPermission(this@BluetoothActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    device.name ?: "Laptop"
                } else {
                    "Laptop"
                }

                withContext(Dispatchers.Main) {
                    updateDialogStatus(dialog, "Verificando laptop...")
                }

                // Verificar emparejamiento
                val bondState = device.bondState
                if (bondState != BluetoothDevice.BOND_BONDED) {
                    withContext(Dispatchers.Main) {
                        updateDialogStatus(dialog, "Emparejando con laptop...")
                    }

                    if (ActivityCompat.checkSelfPermission(this@BluetoothActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        device.createBond()
                    }
                    delay(3000)
                }

                withContext(Dispatchers.Main) {
                    updateDialogStatus(dialog, "Conectando como mouse virtual...")
                }

                // Crear socket
                bluetoothSocket = if (ActivityCompat.checkSelfPermission(this@BluetoothActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    tryCreateSocket(device)
                } else {
                    throw SecurityException("Sin permisos de Bluetooth")
                }

                bluetoothSocket?.let { socket ->
                    try {
                        socket.connect()

                        withContext(Dispatchers.Main) {
                            updateDialogStatus(dialog, "Configurando touchpad virtual...")
                        }

                        delay(1000)

                        // Abrir touchpad
                        withContext(Dispatchers.Main) {
                            dialog.dismiss()
                            openTouchpadActivity(device, socket)
                        }

                    } catch (e: IOException) {
                        // Método alternativo
                        withContext(Dispatchers.Main) {
                            updateDialogStatus(dialog, "Intentando conexión alternativa...")
                        }

                        try {
                            socket.close()
                            val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                            bluetoothSocket = method.invoke(device, 1) as BluetoothSocket
                            bluetoothSocket?.connect()

                            withContext(Dispatchers.Main) {
                                dialog.dismiss()
                                openTouchpadActivity(device, bluetoothSocket!!)
                            }

                        } catch (e2: Exception) {
                            withContext(Dispatchers.Main) {
                                dialog.dismiss()
                                Toast.makeText(this@BluetoothActivity, "Error al conectar: ${e2.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    Toast.makeText(this@BluetoothActivity, "Error de conexión: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun openTouchpadActivity(device: BluetoothDevice, socket: BluetoothSocket) {
        val deviceName = if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            device.name ?: "Laptop"
        } else {
            "Laptop"
        }

        Toast.makeText(this, "¡Mouse virtual activado para $deviceName!", Toast.LENGTH_LONG).show()

        // Pasar socket al manager
        BluetoothSocketManager.setSocket(socket)

        val intent = Intent(this, TouchpadActivity::class.java).apply {
            putExtra(TouchpadActivity.EXTRA_DEVICE_NAME, deviceName)
        }

        startActivity(intent)
    }

    private fun handleAudioDevice(device: BluetoothDevice) {
        val deviceName = if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            device.name ?: "Dispositivo de audio"
        } else {
            "Dispositivo de audio"
        }

        AlertDialog.Builder(this)
            .setTitle("🎵 Dispositivo de Audio Detectado")
            .setMessage("$deviceName parece ser un dispositivo de audio (audífonos/altavoz).\n\n" +
                    "Para conectar dispositivos de audio, ve a:\n" +
                    "Configuración → Bluetooth → Buscar dispositivos\n\n" +
                    "Los dispositivos de audio requieren emparejamiento del sistema Android.")
            .setPositiveButton("Abrir Configuración") { _, _ ->
                try {
                    val intent = Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this, "No se pudo abrir la configuración", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Intentar Conectar Igual") { _, _ ->
                connectToDevice(device)
            }
            .setNeutralButton("Cancelar", null)
            .show()
    }

    private fun showDeviceTypeDialog(device: BluetoothDevice) {
        val deviceName = if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            device.name ?: "Dispositivo desconocido"
        } else {
            "Dispositivo Bluetooth"
        }

        AlertDialog.Builder(this)
            .setTitle("❓ Tipo de Dispositivo")
            .setMessage("¿Qué tipo de dispositivo es $deviceName?")
            .setPositiveButton("🎵 Dispositivo de Audio") { _, _ ->
                handleAudioDevice(device)
            }
            .setNegativeButton("💻 Laptop/Computadora") { _, _ ->
                connectToLaptopDirectly(device)
            }
            .setNeutralButton("📱 Dispositivo de Datos") { _, _ ->
                connectToDevice(device)
            }
            .show()
    }

    // Resto de métodos igual que antes...
    private fun connectToDevice(device: BluetoothDevice) {
        val dialog = createConnectionDialog(device)
        dialog.show()

        connectionJob = CoroutineScope(Dispatchers.IO).launch {
            try {
                val deviceName = if (ActivityCompat.checkSelfPermission(this@BluetoothActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    device.name ?: "Dispositivo Bluetooth"
                } else {
                    "Dispositivo Bluetooth"
                }

                withContext(Dispatchers.Main) {
                    updateDialogStatus(dialog, "Verificando dispositivo...")
                }

                val bondState = device.bondState
                if (bondState != BluetoothDevice.BOND_BONDED) {
                    withContext(Dispatchers.Main) {
                        updateDialogStatus(dialog, "Iniciando emparejamiento...")
                    }

                    if (ActivityCompat.checkSelfPermission(this@BluetoothActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                        device.createBond()
                    }
                    delay(3000)
                }

                withContext(Dispatchers.Main) {
                    updateDialogStatus(dialog, "Estableciendo conexión...")
                }

                bluetoothSocket = if (ActivityCompat.checkSelfPermission(this@BluetoothActivity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                    tryCreateSocket(device)
                } else {
                    throw SecurityException("Sin permisos de Bluetooth")
                }

                bluetoothSocket?.let { socket ->
                    try {
                        socket.connect()

                        withContext(Dispatchers.Main) {
                            updateDialogStatus(dialog, "Configurando comunicación...")
                        }

                        delay(1000)

                        withContext(Dispatchers.Main) {
                            dialog.dismiss()
                            Toast.makeText(this@BluetoothActivity, "¡$deviceName conectado con éxito!", Toast.LENGTH_LONG).show()
                            handleSuccessfulConnection(device, socket)
                        }

                    } catch (e: IOException) {
                        withContext(Dispatchers.Main) {
                            updateDialogStatus(dialog, "Intentando método alternativo...")
                        }

                        try {
                            socket.close()
                            val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                            bluetoothSocket = method.invoke(device, 1) as BluetoothSocket
                            bluetoothSocket?.connect()

                            withContext(Dispatchers.Main) {
                                dialog.dismiss()
                                Toast.makeText(this@BluetoothActivity, "¡$deviceName conectado con éxito!", Toast.LENGTH_LONG).show()
                                handleSuccessfulConnection(device, bluetoothSocket!!)
                            }

                        } catch (e2: Exception) {
                            withContext(Dispatchers.Main) {
                                dialog.dismiss()
                                Toast.makeText(this@BluetoothActivity, "Error al conectar: ${e2.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    dialog.dismiss()
                    Toast.makeText(this@BluetoothActivity, "Error de conexión: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun createConnectionDialog(device: BluetoothDevice): Dialog {
        val dialog = Dialog(this)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(false)

        val deviceName = if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
            device.name ?: "Dispositivo Bluetooth"
        } else {
            "Dispositivo Bluetooth"
        }

        val deviceNameTextView = TextView(this)
        deviceNameTextView.text = "Conectando con $deviceName"
        deviceNameTextView.textSize = 18f
        deviceNameTextView.setTextColor(resources.getColor(android.R.color.white))

        val statusTextView = TextView(this)
        statusTextView.text = "Iniciando conexión..."
        statusTextView.textSize = 14f
        statusTextView.setTextColor(resources.getColor(android.R.color.darker_gray))
        statusTextView.id = 12345

        val cancelButton = MaterialButton(this)
        cancelButton.text = "Cancelar"
        cancelButton.setOnClickListener {
            connectionJob?.cancel()
            bluetoothSocket?.close()
            dialog.dismiss()
            Toast.makeText(this, "Conexión cancelada", Toast.LENGTH_SHORT).show()
        }

        val dialogLayout = LinearLayout(this)
        dialogLayout.orientation = LinearLayout.VERTICAL
        dialogLayout.setPadding(32, 32, 32, 32)
        dialogLayout.addView(deviceNameTextView)
        dialogLayout.addView(statusTextView)
        dialogLayout.addView(cancelButton)

        dialog.setContentView(dialogLayout)
        return dialog
    }

    private fun updateDialogStatus(dialog: Dialog, status: String) {
        val statusTextView = dialog.findViewById<TextView>(12345)
        statusTextView?.text = status
    }

    private fun tryCreateSocket(device: BluetoothDevice): BluetoothSocket? {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            return null
        }

        return try {
            device.createRfcommSocketToServiceRecord(SPP_UUID)
        } catch (e: Exception) {
            for (uuid in COMMON_UUIDS.drop(1)) {
                try {
                    return device.createRfcommSocketToServiceRecord(uuid)
                } catch (ex: Exception) {
                    continue
                }
            }
            try {
                val method = device.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                method.invoke(device, 1) as BluetoothSocket
            } catch (ex: Exception) {
                throw e
            }
        }
    }

    private fun handleSuccessfulConnection(device: BluetoothDevice, socket: BluetoothSocket) {
        // Lógica adicional para dispositivos normales
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            when (action) {
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> showScanningUI()
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    if (discoveredDevices.isEmpty()) {
                        showEmptyView()
                        emptyMessageText.text = "No se encontraron dispositivos Bluetooth."
                    } else {
                        showDevicesList()
                    }
                }
                BluetoothDevice.ACTION_FOUND -> {
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null && !discoveredDevices.contains(device)) {
                        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                            return
                        }
                        discoveredDevices.add(device)
                        deviceAdapter.updateDevices(ArrayList(discoveredDevices))
                        showDevicesList()
                    }
                }
                BluetoothDevice.ACTION_BOND_STATE_CHANGED -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    val bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, BluetoothDevice.BOND_NONE)

                    when (bondState) {
                        BluetoothDevice.BOND_BONDED -> {
                            // Dispositivo emparejado exitosamente
                        }
                        BluetoothDevice.BOND_NONE -> {
                            // Emparejamiento falló
                        }
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Bluetooth activado correctamente", Toast.LENGTH_SHORT).show()
                startBluetoothDiscovery()
            } else {
                Toast.makeText(this, "La función Bluetooth es necesaria para esta característica", Toast.LENGTH_LONG).show()
                emptyMessageText.text = "Bluetooth desactivado. Por favor, actívelo para buscar dispositivos."
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            if (allGranted) {
                startBluetoothDiscovery()
            } else {
                Toast.makeText(this, "Los permisos son necesarios para utilizar esta característica", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        connectionJob?.cancel()
        bluetoothSocket?.close()

        if (bluetoothAdapter.isDiscovering) {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED) {
                bluetoothAdapter.cancelDiscovery()
            }
        }
        try {
            unregisterReceiver(bluetoothReceiver)
        } catch (e: IllegalArgumentException) {
            // Receptor no registrado
        }
    }
}