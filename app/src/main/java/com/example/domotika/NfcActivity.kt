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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import android.view.LayoutInflater
import android.view.View
import android.graphics.Rect  // Esta es la clase que falta


class NfcActivity : AppCompatActivity() {

    private lateinit var nfcAdapter: NfcAdapter
    private lateinit var recyclerTags: RecyclerView
    private lateinit var adapter: TagsAdapter
    private val tagsList = mutableListOf<NfcTag>()
    private var emulatedTagId: String? = null  // Variable para almacenar el ID de la tarjeta emulada

    // Variables para manejar el botón de emulación de tarjeta de `item_tag.xml`
    private var btnEmulateFromItemTag: MaterialButton? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nfc)  // Cargar layout principal de la actividad

        recyclerTags = findViewById(R.id.recycler_tags)
        recyclerTags.layoutManager = LinearLayoutManager(this)

        // Configurar el adaptador
        adapter = TagsAdapter(tagsList)
        recyclerTags.adapter = adapter

        // Configurar la separación entre los elementos del RecyclerView
        recyclerTags.addItemDecoration(SpacingItemDecoration(12))  // 12dp de separación

        // Botón de limpiar etiquetas
        val btnClear = findViewById<MaterialButton>(R.id.btn_clear_tags)
        btnClear.setOnClickListener {
            tagsList.clear()
            adapter.notifyDataSetChanged()
            showCustomToast("Lista de etiquetas limpiada")
        }

        // Inicializar el adaptador NFC
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            showCustomToast("NFC no está disponible en este dispositivo")
            finish()
            return
        }

        if (!nfcAdapter.isEnabled) {
            showCustomToast("Por favor activa NFC en configuración")
            startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
        }
    }

    override fun onResume() {
        super.onResume()

        // Inflar `item_tag.xml` solo cuando se detecta una tarjeta
        val itemTagView = LayoutInflater.from(this).inflate(R.layout.item_tag, null)
        btnEmulateFromItemTag = itemTagView.findViewById(R.id.btn_emulate)

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

                // Procesar el mensaje NDEF si está presente
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
                    val techList = it.techList.joinToString(", ")
                    text = "Etiqueta RFID detectada. Tecnologías: $techList"
                }

                val nfcTag = NfcTag(tagId, text)
                if (!tagsList.any { t -> t.id == tagId }) {
                    tagsList.add(nfcTag)
                    adapter.notifyItemInserted(tagsList.size - 1)
                    showCustomToast("Etiqueta detectada: $tagId")

                    // Mostrar el botón solo cuando se detecta una tarjeta
                    btnEmulateFromItemTag?.visibility = View.VISIBLE

                    // Configurar el click del botón de emulación
                    btnEmulateFromItemTag?.setOnClickListener {
                        emulatedTagId = "EMULATED_TAG_ID_${System.currentTimeMillis()}"
                        showCustomToast("Emulando tarjeta con ID: $emulatedTagId")

                        // Actualizar los textos de la UI con el ID de la tarjeta emulada
                        val textId = findViewById<TextView>(R.id.text_id)
                        val textContent = findViewById<TextView>(R.id.text_content)
                        textId.text = "ID de tarjeta emulada: $emulatedTagId"
                        textContent.text = "Contenido de la tarjeta emulada"
                    }
                } else {
                    showCustomToast("Etiqueta ya está en la lista")
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

    // Mostrar Toast personalizado
    private fun showCustomToast(message: String) {
        val layoutInflater = LayoutInflater.from(applicationContext)
        val customView = layoutInflater.inflate(R.layout.custom_toast, findViewById(android.R.id.content), false)
        val toastMessage = customView.findViewById<TextView>(R.id.toast_message)
        toastMessage.text = message

        val toast = Toast(applicationContext)
        toast.duration = Toast.LENGTH_SHORT
        toast.view = customView
        toast.show()
    }

    // ItemDecoration personalizado para agregar separación entre ítems
    class SpacingItemDecoration(private val space: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            super.getItemOffsets(outRect, view, parent, state)
            outRect.bottom = space
        }
    }

    data class NfcTag(val id: String, val content: String)
}
