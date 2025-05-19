package com.example.domotika

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.widget.TextView
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.widget.Toolbar
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.card.MaterialCardView
import com.google.android.material.navigation.NavigationView
import java.text.SimpleDateFormat
import java.util.*
import android.graphics.Color
import android.content.Intent

class MainActivity : AppCompatActivity() {

    private lateinit var timeTextView: TextView
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var navView: NavigationView
    private lateinit var toggle: ActionBarDrawerToggle

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
        window.decorView.systemUiVisibility = (android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE)

        setContentView(R.layout.activity_main)

        timeTextView = findViewById(R.id.timeText)

        // Inicializa drawerLayout y navView ANTES de usarlos
        drawerLayout = findViewById(R.id.drawer_layout)
        navView = findViewById(R.id.nav_view)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        // Configurar toggle y añadirlo al DrawerLayout
        toggle = ActionBarDrawerToggle(this, drawerLayout, toolbar,
            R.string.open_drawer, R.string.close_drawer)
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        // Cambiar color icono hamburguesa y título
        toggle.drawerArrowDrawable.color = Color.parseColor("#00FFE0")
        toolbar.setTitleTextColor(Color.parseColor("#00FFE0"))

        navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_inicio -> {
                    drawerLayout.closeDrawers()
                    true
                }
                else -> false
            }
        }

        // Asignar efecto lift a las tarjetas MaterialCardView
        val cardView1 = findViewById<MaterialCardView>(R.id.cardView1)
        val cardView2 = findViewById<MaterialCardView>(R.id.cardView2)
        val cardView3 = findViewById<MaterialCardView>(R.id.cardView3)
        val cardView4 = findViewById<MaterialCardView>(R.id.cardView4)


        setLiftEffect(cardView1)
        setLiftEffect(cardView2)
        setLiftEffect(cardView3)
        setLiftEffect(cardView4)

        // Abrir nueva Activity al hacer clic
        cardView1.setOnClickListener {
            startActivity(Intent(this, WifiActivity::class.java))
        }

        cardView2.setOnClickListener {
            startActivity(Intent(this, BluetoothActivity::class.java))
        }

        cardView3.setOnClickListener {
            startActivity(Intent(this, InfraredActivity::class.java))
        }

        cardView4.setOnClickListener {
            startActivity(Intent(this, UsbActivity::class.java))
        }



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


