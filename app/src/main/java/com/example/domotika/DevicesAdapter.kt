package com.example.domotika


import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

data class Device(
    val ip: String,
    val mac: String?,
    val name: String?
)

class DevicesAdapter(
    private val devices: List<Device>,
    private val onConnectClick: (Device) -> Unit
) : RecyclerView.Adapter<DevicesAdapter.DeviceViewHolder>() {

    inner class DeviceViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val deviceName: TextView = view.findViewById(R.id.device_name)
        val deviceAddress: TextView = view.findViewById(R.id.device_address)
        val btnConnect: MaterialButton = view.findViewById(R.id.btn_connect)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.device_item, parent, false)
        return DeviceViewHolder(view)
    }

    override fun getItemCount() = devices.size

    override fun onBindViewHolder(holder: DeviceViewHolder, position: Int) {
        val device = devices[position]
        holder.deviceName.text = device.name ?: "Dispositivo desconocido"
        holder.deviceAddress.text = device.mac ?: device.ip
        holder.btnConnect.setOnClickListener { onConnectClick(device) }
    }
}
