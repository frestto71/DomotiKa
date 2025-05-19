package com.example.domotika

import android.content.*
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class UsbActivity : AppCompatActivity() {

    private lateinit var usbStatus: TextView
    private lateinit var usbManager: UsbManager

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (UsbManager.ACTION_USB_DEVICE_ATTACHED == action) {
                val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                device?.let {
                    usbStatus.text = "Dispositivo conectado:\n" +
                            "Nombre: ${it.deviceName}\n" +
                            "ID: ${it.deviceId}\n" +
                            "Clase: ${it.deviceClass}"
                }
            } else if (UsbManager.ACTION_USB_DEVICE_DETACHED == action) {
                usbStatus.text = "Dispositivo USB desconectado"
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_usb)

        usbStatus = findViewById(R.id.usb_status)
        usbManager = getSystemService(USB_SERVICE) as UsbManager

        val filter = IntentFilter()
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        registerReceiver(usbReceiver, filter)
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
    }
}
