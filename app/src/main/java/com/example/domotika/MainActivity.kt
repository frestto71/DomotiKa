package com.example.domotika

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.MotionEvent
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
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

        // Asignar efecto lift a las tarjetas MaterialCardView
        val cardView1 = findViewById<MaterialCardView>(R.id.cardView1)
        val cardView2 = findViewById<MaterialCardView>(R.id.cardView2)
        val cardView3 = findViewById<MaterialCardView>(R.id.cardView3)
        val cardView4 = findViewById<MaterialCardView>(R.id.cardView4)

        setLiftEffect(cardView1)
        setLiftEffect(cardView2)
        setLiftEffect(cardView3)
        setLiftEffect(cardView4)

        // Iniciar actualización de la hora
        handler.post(updateTimeRunnable)
    }

    private fun setLiftEffect(cardView: MaterialCardView) {
        cardView.setOnTouchListener { v, event ->
            when(event.action) {
                MotionEvent.ACTION_DOWN -> v.animate().translationZ(12f).setDuration(150).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> v.animate().translationZ(5f).setDuration(150).start()
            }
            false
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Detener actualizaciones para evitar fugas de memoria
        handler.removeCallbacks(updateTimeRunnable)
    }
}
