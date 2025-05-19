package com.example.domotika

import android.os.Bundle
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

        }
    }
}


