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
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import java.util.*

class NfcActivity : AppCompatActivity() {

    private lateinit var nfcAdapter: NfcAdapter
    private var writingMode = false
    private var writeDialog: AlertDialog? = null

    private lateinit var textId: TextView
    private lateinit var textContent: TextView
    private lateinit var editInput: EditText
    private lateinit var btnWrite: MaterialButton
    private lateinit var btnClear: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc)

        textId = findViewById(R.id.text_id)
        textContent = findViewById(R.id.text_content)
        editInput = findViewById(R.id.edit_input)
        btnWrite = findViewById(R.id.btn_write)
        btnClear = findViewById(R.id.btn_clear_tags)

        btnClear.setOnClickListener { resetUI() }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this) ?: run {
            showToast("NFC no disponible")
            finish()
            return
        }
        if (!nfcAdapter.isEnabled) {
            showToast("Activa NFC primero")
            startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
        }

        btnWrite.setOnClickListener {
            val text = editInput.text.toString().trim()
            if (text.isEmpty()) {
                showToast("Ingresa un texto antes de escribir")
            } else {
                writingMode = true
                editInput.visibility = View.GONE
                btnWrite.visibility = View.GONE
                showWritePrompt()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_MUTABLE)

        // 3 filtros para captar cualquier tag/NDEF
        val filters = arrayOf(
            IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
                try {
                    addDataType("*/*")
                } catch (e: Exception) {
                    // Ignorar si MIME no válido
                }
            },
            IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED),
            IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        )

        // Sólo Ndef; si quieres otras techs añade más arrays aquí
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

        when (intent.action) {
            NfcAdapter.ACTION_NDEF_DISCOVERED,
            NfcAdapter.ACTION_TECH_DISCOVERED,
            NfcAdapter.ACTION_TAG_DISCOVERED -> {
                val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG)
                tag?.let {
                    val success = writeTextToTag(it, editInput.text.toString(), Locale.getDefault())
                    if (success) {
                        writingMode = false
                        writeDialog?.dismiss()
                        showToast("Escritura completada")
                        resetUI()
                    }
                }
            }
        }
    }

    private fun showWritePrompt() {
        writeDialog = AlertDialog.Builder(this)
            .setTitle("Listo para escribir")
            .setMessage("Acerca la tarjeta NFC ahora…")
            .setCancelable(false)
            .create()
        writeDialog?.show()
    }

    /**
     * Devuelve true si escribió correctamente, false si hubo error
     */
    private fun writeTextToTag(tag: Tag, text: String, locale: Locale): Boolean {
        return try {
            val record = NdefRecord.createTextRecord(locale.language, text)
            val message = NdefMessage(arrayOf(record))
            val ndef = Ndef.get(tag) ?: throw Exception("Tag no NDEF")
            ndef.connect()
            if (!ndef.isWritable) throw Exception("Tag protegido contra escritura")
            if (message.toByteArray().size > ndef.maxSize)
                throw Exception("Mensaje demasiado grande (${message.toByteArray().size} > ${ndef.maxSize})")
            ndef.writeNdefMessage(message)
            ndef.close()
            true
        } catch (e: Exception) {
            showToast("Error escritura: ${e.message}")
            false
        }
    }

    private fun resetUI() {
        writingMode = false
        editInput.visibility = View.VISIBLE
        btnWrite.visibility = View.VISIBLE
        textId.visibility = View.GONE
        textContent.visibility = View.GONE
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
