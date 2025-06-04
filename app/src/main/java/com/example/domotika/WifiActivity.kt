package com.example.domotika

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.widget.Toast

class WifiActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_wifi)

        // Vincula el botón con su ID
        val addWifiButton = findViewById<Button>(R.id.btn_add_wifi)

        // Listener del botón
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

            // Inflar layout personalizado con buscador y lista
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

            val dialog = AlertDialog.Builder(this)
                .setTitle("Buscar producto WiFi")
                .setView(dialogView)
                .setNegativeButton("Cancelar", null)
                .create()

            listView.setOnItemClickListener { _, _, position, _ ->
                val seleccionado = adapter.getItem(position)
                if (seleccionado == "📷 Cámara WiFi") {
                    dialog.dismiss()
                    mostrarDialogoConexionCamara()
                } else {
                    Toast.makeText(this, "Seleccionaste: $seleccionado", Toast.LENGTH_SHORT).show()
                }
            }

            dialog.show()
        }
    }

    private fun mostrarDialogoConexionCamara() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_conectar_camara, null)

        val linearLayout = dialogView.findViewById<LinearLayout>(R.id.dialog_root_linear_layout)

        val etIp = dialogView.findViewById<EditText>(R.id.et_ip)
        val etPuerto = dialogView.findViewById<EditText>(R.id.et_puerto)
        val etUsuario = dialogView.findViewById<EditText>(R.id.et_usuario)
        val etContrasena = dialogView.findViewById<EditText>(R.id.et_contrasena)

        val etLink = EditText(this).apply {
            hint = "Link directo (opcional)"
            setHintTextColor(0xFF777777.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            background = getDrawable(R.drawable.edit_text_background_dark)
            setPadding(12, 12, 12, 12)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 24
            }
        }

        linearLayout.addView(etLink, linearLayout.childCount - 1)

        val btnConectar = dialogView.findViewById<Button>(R.id.btn_conectar)

        val builder = AlertDialog.Builder(this)
            .setView(dialogView)
            .setNegativeButton("Cancelar", null)

        val dialog = builder.create()

        btnConectar.setOnClickListener {
            val ip = etIp.text.toString().trim()
            val puerto = etPuerto.text.toString().trim()
            val usuario = etUsuario.text.toString().trim()
            val contrasena = etContrasena.text.toString().trim()
            val linkDirecto = etLink.text.toString().trim()

            if (linkDirecto.isEmpty()) {
                if (ip.isEmpty() || puerto.isEmpty() || usuario.isEmpty() || contrasena.isEmpty()) {
                    Toast.makeText(this, "Completa IP, puerto, usuario y contraseña o usa link directo", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
            }

            dialog.dismiss()
            val intent = android.content.Intent(this, CameraPlayerActivity::class.java)
            intent.putExtra("ip", ip)
            intent.putExtra("puerto", puerto)
            intent.putExtra("usuario", usuario)
            intent.putExtra("contrasena", contrasena)
            intent.putExtra("linkDirecto", linkDirecto)
            startActivity(intent)
        }

        dialog.show()
    }
}
