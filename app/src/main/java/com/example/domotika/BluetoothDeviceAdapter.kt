package com.example.domotika

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.content.pm.PackageManager
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class BluetoothDeviceAdapter(
    private var devices: MutableList<BluetoothDevice>,
    private val listener: OnDeviceClickListener
) : RecyclerView.Adapter<BluetoothDeviceAdapter.DeviceViewHolder>() {

    interface OnDeviceClickListener {
        fun onDeviceClick(device: BluetoothDevice)
    }

    fun updateDevices(newDevices: List<BluetoothDevice>) {
        devices.clear()
        devices.addAll(newDevices)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_bluetooth_device, parent, false)
        return DeviceViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.bind(device, listener)
    }

    override fun getItemCount(): Int = devices.size

    class DeviceViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val deviceName: TextView = itemView.findViewById(R.id.device_name)
        private val deviceAddress: TextView = itemView.findViewById(R.id.device_address)
        private val btnConnect: MaterialButton = itemView.findViewById(R.id.btn_connect)

        fun bind(device: BluetoothDevice, listener: OnDeviceClickListener) {
            // Como ahora solo llegan dispositivos con nombres válidos, simplificamos la lógica
            val hasBluetoothPermission = ActivityCompat.checkSelfPermission(
                itemView.context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

            if (hasBluetoothPermission) {
                // Ya sabemos que el dispositivo tiene nombre válido por el filtro anterior
                deviceName.text = device.name
            } else {
                deviceName.text = "Dispositivo Bluetooth"
            }

            deviceAddress.text = device.address

            // Detectar tipo de dispositivo
            val deviceType = detectDeviceType(device)

            // Actualizar el estado del botón según el tipo y estado de emparejamiento
            val bondState = device.bondState
            when {
                bondState == BluetoothDevice.BOND_BONDING -> {
                    btnConnect.text = "Emparejando..."
                    btnConnect.isEnabled = false
                }
                deviceType == "Audio" -> {
                    btnConnect.text = if (bondState == BluetoothDevice.BOND_BONDED) "Configurar" else "Emparejar"
                    btnConnect.isEnabled = true
                }
                bondState == BluetoothDevice.BOND_BONDED -> {
                    btnConnect.text = "Conectar"
                    btnConnect.isEnabled = true
                }
                else -> {
                    btnConnect.text = "Vincular"
                    btnConnect.isEnabled = true
                }
            }

            btnConnect.setOnClickListener {
                listener.onDeviceClick(device)
            }
        }

        private fun detectDeviceType(device: BluetoothDevice): String {
            if (ActivityCompat.checkSelfPermission(
                    itemView.context,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED) {
                return "Datos"
            }

            // Verificar por nombre del dispositivo
            val deviceName = device.name?.lowercase() ?: ""
            val audioKeywords = listOf("headphone", "headset", "earbuds", "speaker", "audio", "beats", "airpods", "sony", "bose", "jbl")

            if (audioKeywords.any { deviceName.contains(it) }) {
                return "Audio"
            }

            // Verificar por clase de dispositivo
            device.bluetoothClass?.let { btClass ->
                val majorDeviceClass = btClass.majorDeviceClass

                when (majorDeviceClass) {
                    android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO -> {
                        return "Audio"
                    }
                    android.bluetooth.BluetoothClass.Device.Major.COMPUTER,
                    android.bluetooth.BluetoothClass.Device.Major.PERIPHERAL,
                    android.bluetooth.BluetoothClass.Device.Major.PHONE -> {
                        return "Datos"
                    }
                    else -> {
                        // Para otros tipos de dispositivos
                        return "Datos"
                    }
                }
            }

            return "Datos"
        }
    }
}