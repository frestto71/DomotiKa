package com.example.domotika

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton

class NfcActivity : AppCompatActivity() {

    private lateinit var nfcAdapter: NfcAdapter
    private lateinit var recyclerTags: RecyclerView
    private lateinit var adapter: TagsAdapter
    private val tagsList = mutableListOf<NfcTag>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc)

        recyclerTags = findViewById(R.id.recycler_tags)
        recyclerTags.layoutManager = LinearLayoutManager(this)
        adapter = TagsAdapter(tagsList)
        recyclerTags.adapter = adapter

        val btnClear = findViewById<MaterialButton>(R.id.btn_clear_tags)
        btnClear.setOnClickListener {
            tagsList.clear()
            adapter.notifyDataSetChanged()
            Toast.makeText(this, "Lista de etiquetas limpiada", Toast.LENGTH_SHORT).show()
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC no está disponible en este dispositivo", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        if (!nfcAdapter.isEnabled) {
            Toast.makeText(this, "Por favor activa NFC en configuración", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)
        val filters = arrayOf(
            IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        )
        val techLists = arrayOf(arrayOf<String>(Ndef::class.java.name))

        nfcAdapter.enableForegroundDispatch(this, pendingIntent, filters, techLists)
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null) return

        val action = intent.action
        if (action == NfcAdapter.ACTION_TAG_DISCOVERED || action == NfcAdapter.ACTION_NDEF_DISCOVERED) {
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            tag?.let {
                val tagId = it.id.joinToString(":") { byte -> "%02X".format(byte) }
                var text = "Etiqueta detectada, sin mensaje"

                val ndef = Ndef.get(it)
                if (ndef != null) {
                    val ndefMessage = ndef.cachedNdefMessage
                    if (ndefMessage != null) {
                        text = ndefMessage.records.joinToString("; ") { record ->
                            parseTextRecord(record) ?: "Registro no compatible"
                        }
                    }
                    text = "Etiqueta NFC con mensaje NDEF: $text"
                } else {
                    // Sin mensaje NDEF, chequear tipo de RFID
                    val techList = it.techList.joinToString(", ")
                    text = when {
                        it.techList.contains("android.nfc.tech.MifareClassic") ->
                            "Etiqueta RFID tipo MIFARE detectada. Tecnologías: $techList"
                        it.techList.contains("android.nfc.tech.IsoDep") ->
                            "Etiqueta RFID tipo ISO-DEP detectada. Tecnologías: $techList"
                        else ->
                            "Etiqueta RFID sin NDEF detectada. Tecnologías: $techList"
                    }
                }

                val nfcTag = NfcTag(tagId, text)
                if (!tagsList.any { t -> t.id == tagId }) {
                    tagsList.add(nfcTag)
                    adapter.notifyItemInserted(tagsList.size - 1)
                    Toast.makeText(this, "Etiqueta detectada: $tagId", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Etiqueta ya está en la lista", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun parseTextRecord(record: NdefRecord): String? {
        return try {
            if (record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_TEXT)) {
                val payload = record.payload
                val textEncoding = if ((payload[0].toInt() and 128) == 0) "UTF-8" else "UTF-16"
                val languageCodeLength = payload[0].toInt() and 63
                String(payload, languageCodeLength + 1, payload.size - languageCodeLength - 1, charset(textEncoding))
            } else null
        } catch (e: Exception) {
            null
        }
    }

    data class NfcTag(val id: String, val content: String)
}
