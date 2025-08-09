package com.example.domotika

import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class InfraredActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.ir_menu)

        // Configurar los botones de los dispositivos
        val btnTelevisores = findViewById<LinearLayout>(R.id.btn_televisores)
        val btnProyectores = findViewById<LinearLayout>(R.id.btn_proyectores)
        val btnAires = findViewById<LinearLayout>(R.id.btn_aires)

        // Click listener para televisores - abre selección de dispositivos
        btnTelevisores.setOnClickListener {
            val intent = Intent(this, DeviceSelectionActivity::class.java)
            intent.putExtra("device_type", "tv")
            startActivity(intent)
        }

        // Click listener para proyectores - abre selección de dispositivos
        btnProyectores.setOnClickListener {
            val intent = Intent(this, DeviceSelectionActivity::class.java)
            intent.putExtra("device_type", "projector")
            startActivity(intent)
        }

        // Aires acondicionados - funcionalidad futura
        btnAires.setOnClickListener {
            Toast.makeText(this, "Función de aires acondicionados próximamente", Toast.LENGTH_SHORT).show()
        }
    }
}