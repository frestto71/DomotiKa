package com.example.domotika

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class InfraredActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_infrared)

        // Listener del botón
        val addInfraredButton = findViewById<Button>(R.id.btn_add_infrared)
        addInfraredButton.setOnClickListener {
            // Aquí puedes implementar la lógica de agregar producto
        }
    }
}
