package com.example.domotika

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
    private var devices: List<BluetoothDevice>,
    private val listener: OnDeviceClickListener
) : RecyclerView.Adapter<BluetoothDeviceAdapter.DeviceViewHolder>() {

    interface OnDeviceClickListener {
        fun onDeviceClick(device: BluetoothDevice)
    }

    fun updateDevices(newDevices: List<BluetoothDevice>) {
        this.devices = newDevices
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
            if (ActivityCompat.checkSelfPermission(
                    itemView.context,
                    android.Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                deviceName.text = "Dispositivo Bluetooth"
                deviceAddress.text = device.address
            } else {
                val name = device.name
                deviceName.text = if (name != null && name.isNotEmpty()) name else "Dispositivo Desconocido"
                deviceAddress.text = device.address
            }

            btnConnect.setOnClickListener { listener.onDeviceClick(device) }
        }
    }
}