package com.example.domotika

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class BluetoothActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_bluetooth)

        // Listener del botón
        val addBluetoothButton = findViewById<Button>(R.id.btn_add_bluetooth)

        addBluetoothButton.setOnClickListener {
            // Aquí puedes implementar tu lógica futura (abrir formulario, agregar producto, etc.)
        }
    }
}
