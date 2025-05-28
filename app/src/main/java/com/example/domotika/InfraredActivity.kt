package com.example.domotika

import android.content.Intent
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ListView
import android.widget.SearchView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class InfraredActivity : AppCompatActivity() {
    private val dispositivosInfrared = arrayOf(
        "📺 Televisor",
        "📻 Radio",
        "🎥 Proyector",  // agregado
        "❄️ Aire acondicionado",
        "🔈 Equipo de sonido",
        "💡 Lámpara con control remoto",
        "🧴 Ventilador",
        "🎮 Consola de videojuegos"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_infrared)

        val addInfraredButton = findViewById<Button>(R.id.btn_add_infrared)
        addInfraredButton.setOnClickListener {
            mostrarDialogoDispositivos()
        }
    }

    private fun mostrarDialogoDispositivos() {
        val dialogView = layoutInflater.inflate(R.layout.dialog_buscar_producto, null)
        val searchView = dialogView.findViewById<SearchView>(R.id.searchView)
        val listView = dialogView.findViewById<ListView>(R.id.listView)

        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, dispositivosInfrared.toMutableList())
        listView.adapter = adapter

        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?) = false
            override fun onQueryTextChange(newText: String?): Boolean {
                adapter.filter.filter(newText)
                return true
            }
        })

        val dialog = AlertDialog.Builder(this)
            .setTitle("Selecciona un dispositivo Infrarrojo")
            .setView(dialogView)
            .setNegativeButton("Cancelar", null)
            .create()

        listView.setOnItemClickListener { _, _, position, _ ->
            val seleccionado = adapter.getItem(position)
            dialog.dismiss()
            if (seleccionado != null) {
                abrirControlDispositivo(seleccionado)
            }
        }

        dialog.show()
    }

    private fun abrirControlDispositivo(nombreDispositivo: String) {
        val intent = Intent(this, InfraredControlActivity::class.java)
        intent.putExtra("dispositivo_nombre", nombreDispositivo)
        startActivity(intent)
    }
}
