package com.example.domotika

import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.os.Bundle
import android.provider.Settings
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.util.*

class ToolsActivity : AppCompatActivity() {

    private lateinit var nfcAdapter: NfcAdapter
    private var writingMode = false
    private var writeDialog: AlertDialog? = null

    private lateinit var textId: TextView
    private lateinit var textContent: TextView
    private lateinit var editInput: EditText
    private lateinit var btnWrite: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tools)

        textId = findViewById(R.id.text_id)
        textContent = findViewById(R.id.text_content)
        editInput = findViewById(R.id.edit_input)
        btnWrite = findViewById(R.id.btn_write)

        nfcAdapter = NfcAdapter.getDefaultAdapter(this) ?: run {
            Toast.makeText(this, "NFC no disponible", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (!nfcAdapter.isEnabled) {
            Toast.makeText(this, "Activa NFC primero", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
        }

        btnWrite.setOnClickListener {
            val text = editInput.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(this, "Ingresa texto", Toast.LENGTH_SHORT).show()
            } else {
                writingMode = true
                editInput.isEnabled = false
                showWritePrompt()
            }
        }
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
        if (!writingMode) return
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG) ?: return
        val success = writeTextToTag(tag, editInput.text.toString(), Locale.getDefault())
        if (success) {
            writingMode = false
            writeDialog?.dismiss()
            Toast.makeText(this, "Escritura completada", Toast.LENGTH_SHORT).show()
            editInput.isEnabled = true
        }
    }

    private fun showWritePrompt() {
        writeDialog = AlertDialog.Builder(this)
            .setTitle("Listo para escribir")
            .setMessage("Acerca la tarjeta NFC...")
            .setCancelable(false)
            .create().also { it.show() }
    }

    private fun writeTextToTag(tag: Tag, text: String, locale: Locale): Boolean {
        return try {
            val record = NdefRecord.createTextRecord(locale.language, text)
            val message = NdefMessage(arrayOf(record))
            val ndef = Ndef.get(tag) ?: throw Exception("Tag no NDEF")
            ndef.connect()
            if (!ndef.isWritable) throw Exception("Tag protegido")
            if (message.toByteArray().size > ndef.maxSize)
                throw Exception("Mensaje grande")
            ndef.writeNdefMessage(message)
            ndef.close()
            true
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }
}
