package com.example.domotika

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.*
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class NfcActivity : AppCompatActivity() {

    private lateinit var nfcAdapter: NfcAdapter
    private lateinit var listView: ListView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc)

        listView = findViewById(R.id.list_data)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this) ?: run {
            Toast.makeText(this, "NFC no disponible", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (!nfcAdapter.isEnabled) {
            Toast.makeText(this, "Activa NFC en ajustes", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
        }
    }

    /** Llamado desde android:onClick del botón Herramientas */
    fun openTools(view: View) {
        startActivity(Intent(this, ToolsActivity::class.java))
    }

    override fun onResume() {
        super.onResume()
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
        val filters = arrayOf(
            IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
                try { addDataType("*/*") } catch (_: Exception) {}
            },
            IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        )
        val techLists = arrayOf(arrayOf(Ndef::class.java.name))
        nfcAdapter.enableForegroundDispatch(this, pi, filters, techLists)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleTag(intent)
    }

    private fun handleTag(intent: Intent) {
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG) ?: return
        val dataList = mutableListOf<String>()

        // Número de serie (UID)
        val hexId = tag.id.joinToString(":") { "%02X".format(it) }
        dataList.add("Número de serie: $hexId")

        // Tecnologías soportadas
        dataList.add("Tecnologías: ${tag.techList.joinToString(", ")}")

        // ATQA y SAK via NfcA
        NfcA.get(tag)?.apply {
            try {
                connect()
                dataList.add("ATQA: " + atqa.joinToString(":") { "%02X".format(it) })
                dataList.add("SAK: 0x%02X".format(sak))
            } catch(e: Exception) {
                dataList.add("Error NfcA: ${e.message}")
            } finally {
                try { close() } catch (_: Exception) {}
            }
        }

        // ATS via IsoDep
        IsoDep.get(tag)?.apply {
            try {
                connect()
                historicalBytes?.takeIf { it.isNotEmpty() }?.let {
                    dataList.add("ATS: " + it.joinToString(":") { b -> "%02X".format(b) })
                } ?: dataList.add("ATS: no disponible")
            } catch(e: Exception) {
                dataList.add("Error IsoDep: ${e.message}")
            } finally {
                try { close() } catch (_: Exception) {}
            }
        }

        // Memoria para MifareClassic / Ultralight
        MifareClassic.get(tag)?.apply {
            dataList.add("Tipo etiqueta: MIFARE Classic")
            dataList.add("Memoria total: ${size} bytes")
            dataList.add("Sectores: $sectorCount, Bloques: $blockCount")
        } ?: MifareUltralight.get(tag)?.apply {
            dataList.add("Tipo etiqueta: MIFARE Ultralight")
            dataList.add("Páginas máximas: ${maxTransceiveLength / 4}")
        }

        // Formato NDEF y registros
        Ndef.get(tag)?.apply {
            dataList.add("Formato: NDEF")
            dataList.add("Tamaño NDEF máx.: $maxSize bytes")
            dataList.add("Escritura posible: ${if(isWritable) "Sí" else "No"}")
            dataList.add("Solo lectura posible: ${if(!isWritable) "Sí" else "No"}")
            try {
                connect()
                cachedNdefMessage?.records?.forEachIndexed { idx, rec ->
                    val text = parseTextRecord(rec)
                        ?: rec.payload.joinToString(" ") { b -> "%02X".format(b) }
                    dataList.add("Registro ${idx+1}: $text")
                } ?: dataList.add("No hay mensajes NDEF almacenados")
            } catch(e: Exception) {
                dataList.add("Error leyendo NDEF: ${e.message}")
            } finally {
                try { close() } catch (_: Exception) {}
            }
        } ?: dataList.add("Formato: no NDEF")

        // Usar el nuevo layout de fila para la lista
        val adapter = ArrayAdapter(
            this,
            R.layout.item_nfc_data,
            R.id.text1,
            dataList
        )
        listView.adapter = adapter
    }

    private fun parseTextRecord(record: NdefRecord): String? {
        return try {
            if (record.tnf == NdefRecord.TNF_WELL_KNOWN &&
                record.type.contentEquals(NdefRecord.RTD_TEXT)) {
                val payload = record.payload
                val encoding = if ((payload[0].toInt() and 0x80) == 0) "UTF-8" else "UTF-16"
                val langLen = payload[0].toInt() and 0x3F
                String(payload, langLen+1, payload.size-langLen-1, charset(encoding))
            } else null
        } catch(_ : Exception) {
            null
        }
    }
}
