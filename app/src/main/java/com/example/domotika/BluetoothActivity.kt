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
        }
        registerReceiver(bluetoothReceiver, filter)

        // Configurar botón para búsqueda (por si quiere reiniciar búsqueda)
        btnAddBluetooth.setOnClickListener { startBluetoothDiscovery() }

        // Aquí llamamos a la función que chequea permisos y comienza descubrimiento si puede
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

        val dialog = Dialog(this)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(false)

        val deviceNameTextView = TextView(this)
        deviceNameTextView.text = "Conectando con ${device.name ?: "Dispositivo Bluetooth"}"
        deviceNameTextView.textSize = 18f
        deviceNameTextView.setTextColor(resources.getColor(android.R.color.white))

        val statusTextView = TextView(this)
        statusTextView.text = "Estableciendo conexión..."
        statusTextView.textSize = 14f
        statusTextView.setTextColor(resources.getColor(android.R.color.darker_gray))

        val cancelButton = MaterialButton(this)
        cancelButton.text = "Cancelar"
        cancelButton.setOnClickListener {
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
        dialog.show()

        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            statusTextView.text = "Estableciendo vínculo..."
        }, 1500)

        handler.postDelayed({
            statusTextView.text = "Configurando dispositivo..."
        }, 3000)

        handler.postDelayed({
            dialog.dismiss()
            Toast.makeText(this, "¡${device.name ?: "Dispositivo"} conectado con éxito!", Toast.LENGTH_SHORT).show()
        }, 4500)
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
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                Toast.makeText(this, "Bluetooth activado correctamente", Toast.LENGTH_SHORT).show()
                startBluetoothDiscovery()  // empezar búsqueda si se activó Bluetooth
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
