package com.example.domotika

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class WifiActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi)

        val addWifiButton = findViewById<Button>(R.id.btn_add_wifi)

        addWifiButton.setOnClickListener {
            val productosWifi = arrayOf(
                "📷 Cámara WiFi",
                "💡 Foco Inteligente",
                "🔌 Enchufe WiFi",
                "📺 Smart TV",
                "📡 Repetidor de señal",
                "🚪 Cerradura Inteligente",
                "🔒 Timbre con cámara",
                "🎛️ Termostato WiFi",
                "🌡️ Sensor de temperatura WiFi",
                "🚿 Regadera inteligente",
                "🪑 Sensor de movimiento",
                "🕹️ Control remoto universal",
                "🧯 Detector de humo WiFi",
                "🛏️ Cortinas motorizadas",
                "🎧 Bocina inteligente",
                "🪟 Sensor de puertas/ventanas",
                "💨 Purificador de aire WiFi",
                "🌀 Ventilador inteligente",
                "🐾 Alimentador automático para mascotas",
                "🌱 Sistema de riego inteligente",
                "🧺 Lavadora conectada",
                "🔊 Sistema de sonido multiroom",
                "📶 Módem/Router inteligente",
                "🔋 Panel solar con WiFi",
                "🧠 Asistente virtual (Alexa, Google Nest)"
            )

            // Inflar el layout personalizado
            val dialogView = layoutInflater.inflate(R.layout.dialog_buscar_producto, null)
            val searchView = dialogView.findViewById<SearchView>(R.id.searchView)
            val listView = dialogView.findViewById<ListView>(R.id.listView)

            val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, productosWifi.toMutableList())
            listView.adapter = adapter

            // Filtro en tiempo real
            searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean = false

                override fun onQueryTextChange(newText: String?): Boolean {
                    adapter.filter.filter(newText)
                    return true
                }
            })

            // Al seleccionar un producto
            listView.setOnItemClickListener { _, _, position, _ ->
                val seleccionado = adapter.getItem(position)
                Toast.makeText(this, "Seleccionaste: $seleccionado", Toast.LENGTH_SHORT).show()
            }

            // Crear y mostrar el diálogo
            AlertDialog.Builder(this)
                .setTitle("Buscar producto WiFi")
                .setView(dialogView)
                .setNegativeButton("Cancelar", null)
                .show()
        }
    }
}
