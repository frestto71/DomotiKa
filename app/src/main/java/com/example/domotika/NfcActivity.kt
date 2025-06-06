package com.example.domotika

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.Ndef
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.provider.Settings
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.domotika.hce.CardEmulationService
import com.google.android.material.button.MaterialButton

class NfcActivity : AppCompatActivity() {

    private lateinit var nfcAdapter: NfcAdapter
    private var tagDetected = false
    private var detectedTagId: String? = null
    private var detectedTagTech: String? = null

    private lateinit var textId: TextView
    private lateinit var textContent: TextView
    private lateinit var btnEmulate: MaterialButton
    private lateinit var btnClear: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc)

        textId = findViewById(R.id.text_id)
        textContent = findViewById(R.id.text_content)
        btnEmulate = findViewById(R.id.btn_emulate)
        btnClear = findViewById(R.id.btn_clear_tags)

        btnClear.setOnClickListener {
            // Reiniciar detección
            tagDetected = false
            detectedTagId = null
            detectedTagTech = null

            textId.visibility = TextView.GONE
            textContent.visibility = TextView.GONE
            btnEmulate.visibility = MaterialButton.GONE

            showToast("Se reinició la detección")
        }

        // Inicializar NFC
        nfcAdapter = NfcAdapter.getDefaultAdapter(this) ?: run {
            showToast("NFC no disponible en este dispositivo")
            finish()
            return
        }
        if (!nfcAdapter.isEnabled) {
            showToast("Por favor, activa NFC")
            startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
        }

        // Click de Emular
        btnEmulate.setOnClickListener {
            detectedTagId?.let { id ->  // Emula solo el ID de la tarjeta detectada
                detectedTagTech?.let { tech ->
                    // 1) Emulación con el ID y la tecnología de la tarjeta detectada
                    val record = buildTextNdef("$id | Tecnología: $tech", "es")  // Emula el ID y la tecnología de la tarjeta

                    // 2) Asegúrate de que el servicio HCE esté levantado para responder al SELECT del lector
                    val svc = Intent(this, CardEmulationService::class.java)
                    startService(svc)  // Esto activa el servicio HCE

                    // 3) Le pasa el NDEF al servicio HCE para que el teléfono lo emule
                    val cardEmulationService = CardEmulationService()
                    cardEmulationService.setNdefPayload(record)

                    // 4) Mostrar en la pantalla que estamos emulando la tarjeta
                    textContent.text = "Emulando tarjeta con ID: $id\nTecnología: $tech"
                    textContent.visibility = TextView.VISIBLE

                    // 5) Mostrar un Toast que le indique al usuario que se está emulando
                    showToast("Emulación en proceso... ID: $id, Tecnología: $tech")

                    // 6) Opcional: Agregar alerta adicional
                    showAlert("Emulación iniciada", "Tu teléfono ahora está emulando la tarjeta con ID: $id y Tecnología: $tech")
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::nfcAdapter.isInitialized) {
            val intent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_MUTABLE
            )
            val filters = arrayOf(
                IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED),
                IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
            )
            val techLists = arrayOf(arrayOf<String>(Ndef::class.java.name, IsoDep::class.java.name))
            nfcAdapter.enableForegroundDispatch(this, pendingIntent, filters, techLists)
        }
    }

    override fun onPause() {
        super.onPause()
        if (::nfcAdapter.isInitialized) {
            nfcAdapter.disableForegroundDispatch(this)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent == null || tagDetected) return // Si ya detectamos uno, ignoramos

        val action = intent.action
        if (action == NfcAdapter.ACTION_TECH_DISCOVERED ||
            action == NfcAdapter.ACTION_NDEF_DISCOVERED ||
            action == NfcAdapter.ACTION_TAG_DISCOVERED) {

            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            tag?.let {
                val idBytes = it.id
                val tagId = idBytes.joinToString(":") { byte -> "%02X".format(byte) }
                detectedTagId = tagId
                tagDetected = true

                // Detectar la tecnología NFC utilizada por la tarjeta
                val technologies = it.techList.joinToString(", ")
                detectedTagTech = technologies

                // Mostrar en pantalla
                textId.text = "ID detectado: $tagId"
                textId.visibility = TextView.VISIBLE

                // Mostrar el payload NDEF (si lo hay)
                val ndef = Ndef.get(it)
                val contenidoTxt = if (ndef != null && ndef.cachedNdefMessage != null) {
                    ndef.cachedNdefMessage.records.joinToString("; ") { record ->
                        parseTextRecord(record) ?: "Registro no compatible"
                    }.let { "NDEF: $it" }
                } else {
                    "RFID/Pure NFC sin mensajes NDEF"
                }
                textContent.text = contenidoTxt
                textContent.visibility = TextView.VISIBLE

                // Mostrar botón emular
                btnEmulate.visibility = MaterialButton.VISIBLE

                showToast("Etiqueta detectada: $tagId\nTecnología: $technologies")
            }
        }
    }

    private fun parseTextRecord(record: android.nfc.NdefRecord): String? {
        return try {
            if (record.tnf == android.nfc.NdefRecord.TNF_WELL_KNOWN &&
                record.type.contentEquals(android.nfc.NdefRecord.RTD_TEXT)) {
                val payload = record.payload
                val textEncoding = if ((payload[0].toInt() and 0x80) == 0) "UTF-8" else "UTF-16"
                val languageCodeLength = payload[0].toInt() and 0x3F
                String(
                    payload, languageCodeLength + 1,
                    payload.size - languageCodeLength - 1,
                    charset(textEncoding)
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    // Función para construir el NDEF con el ID exacto de la tarjeta
    private fun buildTextNdef(text: String, language: String = "es"): ByteArray {
        val languageBytes = language.toByteArray(Charsets.US_ASCII)
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val statusByte = (languageBytes.size and 0x3F).toByte()
        val payload = ByteArray(1 + languageBytes.size + textBytes.size)
        payload[0] = statusByte.toByte()
        System.arraycopy(languageBytes, 0, payload, 1, languageBytes.size)
        System.arraycopy(textBytes, 0, payload, 1 + languageBytes.size, textBytes.size)

        val type = byteArrayOf(0x54)  // 'T'
        val header: Byte = (0xD1).toByte() // MB=1, ME=1, CF=0, SR=1, IL=0, TNF=0x01
        val typeLength = 1
        val payloadLength = payload.size

        val record = ByteArray(1 + 1 + 1 + typeLength + payloadLength)
        var idx = 0
        record[idx++] = header
        record[idx++] = typeLength.toByte()
        record[idx++] = payloadLength.toByte()
        record[idx++] = type[0]
        System.arraycopy(payload, 0, record, idx, payloadLength)
        return record
    }

    // Función para mostrar alerta
    private fun showAlert(title: String, message: String) {
        val builder = android.app.AlertDialog.Builder(this)
        builder.setTitle(title)
        builder.setMessage(message)
        builder.setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()  // Cerrar la alerta cuando el usuario haga clic en "OK"
        }
        builder.create().show()
    }
}
