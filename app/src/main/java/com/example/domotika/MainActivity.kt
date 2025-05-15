package com.example.domotika

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var timeTextView: TextView
    private val handler = Handler(Looper.getMainLooper())
    private val timeFormat = SimpleDateFormat("hh:mm:ss a", Locale.getDefault())

    private val updateTimeRunnable = object : Runnable {
        override fun run() {
            val currentTime = timeFormat.format(Date())
            timeTextView.text = currentTime
            handler.postDelayed(this, 1000) // Actualiza cada segundo
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Ocultar barra de estado y navegación
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
        supportActionBar?.hide()

        setContentView(R.layout.activity_main)

        timeTextView = findViewById(R.id.timeText)

        // Iniciar actualización de la hora
        handler.post(updateTimeRunnable)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Detener actualizaciones para evitar fugas de memoria
        handler.removeCallbacks(updateTimeRunnable)
    }
}
