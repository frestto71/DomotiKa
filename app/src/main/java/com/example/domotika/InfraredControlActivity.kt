package com.example.domotika

import android.content.Context
import android.hardware.ConsumerIrManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity

class InfraredControlActivity : AppCompatActivity() {

    private var irManager: ConsumerIrManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_infrared_control)

        // Obtener el servicio IR de forma segura
        irManager = getSystemService(Context.CONSUMER_IR_SERVICE) as? ConsumerIrManager

        if (irManager == null || !irManager!!.hasIrEmitter()) {
            // Mostrar diálogo si no tiene emisor IR o el servicio no existe
            AlertDialog.Builder(this)
                .setTitle("Error")
                .setMessage("Este dispositivo no tiene emisor infrarrojo.")
                .setPositiveButton("OK") { dialog, _ ->
                    dialog.dismiss()
                    finish()
                }
                .setCancelable(false)
                .show()
            return
        }

        val nombreDispositivo = intent.getStringExtra("dispositivo_nombre") ?: "Dispositivo"

        val tvTitulo = findViewById<TextView>(R.id.tv_dispositivo_nombre)
        val btnEncender = findViewById<Button>(R.id.btn_encender)
        val btnApagar = findViewById<Button>(R.id.btn_apagar)

        tvTitulo.text = nombreDispositivo

        btnEncender.setOnClickListener {
            enviarCodigoIR(nombreDispositivo, true)
        }

        btnApagar.setOnClickListener {
            enviarCodigoIR(nombreDispositivo, false)
        }
    }

    private fun enviarCodigoIR(dispositivo: String, encender: Boolean) {
        val codigo = when (dispositivo) {
            "📺 Televisor" -> if (encender) codigoEncenderTV else codigoApagarTV
            "🎥 Proyector" -> if (encender) codigoEncenderProyector else codigoApagarProyector
            "❄️ Aire acondicionado" -> if (encender) codigoEncenderAire else codigoApagarAire
            else -> null
        }

        if (codigo == null) {
            Toast.makeText(this, "Código IR no disponible para $dispositivo", Toast.LENGTH_SHORT).show()
            return
        }

        irManager?.transmit(codigo.frecuencia, codigo.patron)
        Toast.makeText(this, (if (encender) "Encendiendo" else "Apagando") + " $dispositivo", Toast.LENGTH_SHORT).show()
    }

    private val codigoEncenderTV = CodigoIR(38000, intArrayOf(
        9000,4500, 560,560, 560,560, 560,560, 560,1690, 560,560, 560,1690, 560,560, 560,1690, 560,1690,
        560,560, 560,560, 560,560, 560,1690, 560,1690, 560,1690, 560,1690, 560,1690, 560,1690, 560,560,
        560,560, 560,560, 560,1690, 560,1690, 560,1690, 560,560, 560,560, 560,1690, 560,1690, 560,560,
        560,1690, 560,560, 560,1690, 560,1690, 560,560, 560,1690, 560,1690, 560,1690, 560,1690, 560,4500,
        9000,2250, 560,1690, 560,4500, 560,2250, 560,560, 560,1690, 560,2250, 560,2250, 560,2250, 560,2250
    ))

    private val codigoApagarTV = CodigoIR(38000, intArrayOf(
        9000,4500, 560,560, 560,560, 560,560, 560,1690, 560,560, 560,1690, 560,560, 560,1690, 560,1690,
        560,560, 560,560, 560,560, 560,1690, 560,1690, 560,1690, 560,1690, 560,1690, 560,1690, 560,560,
        560,560, 560,560, 560,1690, 560,1690, 560,1690, 560,560, 560,560, 560,1690, 560,1690, 560,560,
        560,1690, 560,560, 560,1690, 560,1690, 560,560, 560,1690, 560,1690, 560,1690, 560,1690, 560,4500,
        9000,2250, 560,1690, 560,4500, 560,2250, 560,560, 560,1690, 560,2250, 560,2250, 560,2250, 560,2250
    ))

    private val codigoEncenderProyector = CodigoIR(38000, intArrayOf(
        4500,4500, 560,1690, 560,560, 560,1690, 560,1690, 560,560, 560,560, 560,560, 560,1690, 560,560,
        560,1690, 560,1690, 560,1690, 560,1690, 560,1690, 560,1690, 560,560, 560,560, 560,560, 560,1690,
        560,1690, 560,1690, 560,560, 560,560, 560,1690, 560,1690, 560,560, 560,1690, 560,560, 560,1690
    ))

    private val codigoApagarProyector = CodigoIR(38000, intArrayOf(
        4500,4500, 560,1690, 560,560, 560,1690, 560,1690, 560,560, 560,560, 560,560, 560,1690, 560,560,
        560,1690, 560,1690, 560,1690, 560,1690, 560,1690, 560,1690, 560,560, 560,560, 560,560, 560,1690,
        560,1690, 560,1690, 560,560, 560,560, 560,1690, 560,1690, 560,560, 560,1690, 560,560, 560,1690
    ))

    private val codigoEncenderAire = CodigoIR(38000, intArrayOf(
        4500,4500, 560,560, 560,1690, 560,560, 560,1690, 560,1690, 560,1690, 560,1690, 560,1690, 560,1690,
        560,1690, 560,560, 560,560, 560,560, 560,1690, 560,1690, 560,1690, 560,560, 560,560, 560,1690,
        560,1690, 560,1690, 560,560, 560,560, 560,1690, 560,1690, 560,560, 560,1690, 560,560, 560,1690
    ))

    private val codigoApagarAire = CodigoIR(38000, intArrayOf(
        4500,4500, 560,560, 560,1690, 560,560, 560,1690, 560,1690, 560,1690, 560,1690, 560,1690, 560,1690,
        560,1690, 560,560, 560,560, 560,560, 560,1690, 560,1690, 560,1690, 560,560, 560,560, 560,1690,
        560,1690, 560,1690, 560,560, 560,560, 560,1690, 560,1690, 560,560, 560,1690, 560,560, 560,1690
    ))

    data class CodigoIR(val frecuencia: Int, val patron: IntArray)
}
