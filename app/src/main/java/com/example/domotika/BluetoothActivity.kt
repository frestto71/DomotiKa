package com.example.domotika

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
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
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import android.app.Dialog
import android.os.Handler
import android.os.Looper
import android.view.animation.AnimationUtils
import android.widget.ImageView

class BluetoothActivity : AppCompatActivity(), BluetoothDeviceAdapter.OnDeviceClickListener {

    companion object {
        private const val REQUEST_ENABLE_BT = 1
        private const val REQUEST_PERMISSIONS = 2
    }

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var devicesList: RecyclerView
    private lateinit var scanningLayout: LinearLayout
    private lateinit var emptyView: ScrollView
    private lateinit var btnAddBluetooth: MaterialButton
    private lateinit var emptyMessageText: TextView

    private val discoveredDevices = HashSet<BluetoothDevice>()
    private lateinit var deviceAdapter: BluetoothDeviceAdapter

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
            // El dispositivo no soporta Bluetooth
            Toast.makeText(this, "Este dispositivo no soporta Bluetooth", Toast.LENGTH_LONG).show()
            emptyMessageText.text = "Bluetooth no disponible en este dispositivo."
            btnAddBluetooth.isEnabled = false
            return
        }

        // Configurar botón para buscar dispositivos
        btnAddBluetooth.setOnClickListener { startBluetoothDiscovery() }

        // Registrar receptor para descubrimiento de dispositivos
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_STARTED)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        }
        registerReceiver(bluetoothReceiver, filter)

        // Verificar si Bluetooth está habilitado
        checkBluetoothEnabled()
    }

    private fun checkBluetoothEnabled() {
        if (bluetoothAdapter?.isEnabled == false) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestBluetoothPermissions()
                return
            }
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
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
        // Verificar permisos
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestBluetoothPermissions()
                return
            }
        } else if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestBluetoothPermissions()
            return
        }

        // Cancelar descubrimiento previo si existe
        if (bluetoothAdapter.isDiscovering) {
            bluetoothAdapter.cancelDiscovery()
        }

        // Limpiar lista y mostrar indicador de carga
        discoveredDevices.clear()
        deviceAdapter.updateDevices(ArrayList())
        showScanningUI()

        // Iniciar descubrimiento
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
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestBluetoothPermissions()
            return
        }

        // Crear diálogo personalizado desde cero sin depender de un layout existente
        val dialog = Dialog(this)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(false)

        // Inflar un layout simple para el diálogo
        val contentView = layoutInflater.inflate(R.layout.activity_bluetooth, null)
        dialog.setContentView(contentView)

        // Crear elementos básicos para el diálogo
        val deviceNameTextView = TextView(this)
        deviceNameTextView.text = "Conectando con ${device.name ?: "Dispositivo Bluetooth"}"
        deviceNameTextView.textSize = 18f
        deviceNameTextView.setTextColor(resources.getColor(android.R.color.white))

        val statusTextView = TextView(this)
        statusTextView.text = "Estableciendo conexión..."
        statusTextView.textSize = 14f
        statusTextView.setTextColor(resources.getColor(android.R.color.darker_gray))

        // Agregar botón de cancelar
        val cancelButton = MaterialButton(this)
        cancelButton.text = "Cancelar"
        cancelButton.setOnClickListener {
            dialog.dismiss()
            Toast.makeText(this, "Conexión cancelada", Toast.LENGTH_SHORT).show()
        }

        // Crear el layout manualmente y agregar vistas
        val dialogLayout = LinearLayout(this)
        dialogLayout.orientation = LinearLayout.VERTICAL
        dialogLayout.setPadding(32, 32, 32, 32)
        dialogLayout.addView(deviceNameTextView)
        dialogLayout.addView(statusTextView)
        dialogLayout.addView(cancelButton)

        // Establecer el layout como contenido del diálogo
        dialog.setContentView(dialogLayout)

        // Mostrar el diálogo
        dialog.show()

        // Simulación de conexión
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            statusTextView.text = "Estableciendo vínculo..."
        }, 1500)

        handler.postDelayed({
            statusTextView.text = "Configurando dispositivo..."
        }, 3000)

        handler.postDelayed({
            dialog.dismiss()
            Toast.makeText(
                this,
                "¡${device.name ?: "Dispositivo"} conectado con éxito!",
                Toast.LENGTH_SHORT
            ).show()
        }, 4500)
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            when (action) {
                BluetoothAdapter.ACTION_DISCOVERY_STARTED -> {
                    showScanningUI()
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    if (discoveredDevices.isEmpty()) {
                        showEmptyView()
                        emptyMessageText.text = "No se encontraron dispositivos Bluetooth."
                    } else {
                        showDevicesList()
                    }
                }
                BluetoothDevice.ACTION_FOUND -> {
                    // Se encontró un dispositivo
                    val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                    if (device != null && !discoveredDevices.contains(device)) {
                        if (ActivityCompat.checkSelfPermission(
                                context,
                                Manifest.permission.BLUETOOTH_CONNECT
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            return
                        }
                        discoveredDevices.add(device)
                        val deviceList = ArrayList(discoveredDevices)
                        deviceAdapter.updateDevices(deviceList)
                        showDevicesList()
                    }
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                // Bluetooth activado, podemos proceder
                Toast.makeText(this, "Bluetooth activado correctamente", Toast.LENGTH_SHORT).show()
            } else {
                // Usuario rechazó activar Bluetooth
                Toast.makeText(this, "La función Bluetooth es necesaria para esta característica", Toast.LENGTH_LONG).show()
                emptyMessageText.text = "Bluetooth desactivado. Por favor, actívelo para buscar dispositivos."
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS) {
            val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (allGranted) {
                // Permisos concedidos, podemos proceder
                startBluetoothDiscovery()
            } else {
                // Permisos denegados
                Toast.makeText(this, "Los permisos son necesarios para utilizar esta característica", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Cancelar descubrimiento y desregistrar receptor
        if (bluetoothAdapter?.isDiscovering == true) {
            if (ActivityCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) == PackageManager.PERMISSION_GRANTED
            ) {
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