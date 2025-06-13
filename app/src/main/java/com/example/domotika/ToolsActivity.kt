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
import android.widget.Spinner
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.switchmaterial.SwitchMaterial
import com.google.android.material.textfield.TextInputLayout
import java.util.*

class ToolsActivity : AppCompatActivity() {

    private lateinit var nfcAdapter: NfcAdapter
    private var writingMode = false
    private var deletingMode = false
    private var wifiWritingMode = false
    private var writeDialog: AlertDialog? = null

    private lateinit var textId: TextView
    private lateinit var textContent: TextView
    private lateinit var editInput: EditText
    private lateinit var btnWrite: MaterialButton
    private lateinit var btnDelete: MaterialButton
    private lateinit var inputLayout: TextInputLayout

    // Campos WiFi
    private lateinit var editSsid: EditText
    private lateinit var editPassword: EditText
    private lateinit var autoSecurityType: Spinner
    private lateinit var switchHiddenSsid: SwitchMaterial
    private lateinit var btnConfigureWifi: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_tools)

        // Vinculación de las vistas existentes
        textId = findViewById<TextView>(R.id.text_id)
        textContent = findViewById<TextView>(R.id.text_content)
        inputLayout = findViewById<TextInputLayout>(R.id.input_layout)
        editInput = findViewById<EditText>(R.id.edit_input)
        btnWrite = findViewById<MaterialButton>(R.id.btn_write)
        btnDelete = findViewById<MaterialButton>(R.id.btn_delete)

        // Vinculación de las vistas WiFi
        editSsid = findViewById<EditText>(R.id.edit_ssid)
        autoSecurityType = findViewById<Spinner>(R.id.auto_security_type)
        editPassword = findViewById<EditText>(R.id.edit_password)
        switchHiddenSsid = findViewById<SwitchMaterial>(R.id.switch_hidden_ssid)
        btnConfigureWifi = findViewById<MaterialButton>(R.id.btn_configure_wifi)

        // Configurar lista desplegable para tipo de seguridad
        setupSecurityTypeDropdown()

        // Verifica si NFC está disponible
        nfcAdapter = NfcAdapter.getDefaultAdapter(this) ?: run {
            Toast.makeText(this, "NFC no disponible", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        if (!nfcAdapter.isEnabled) {
            Toast.makeText(this, "Activa NFC primero", Toast.LENGTH_SHORT).show()
            startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
        }

        // Configuración del botón Escribir (texto normal)
        btnWrite.setOnClickListener {
            val text = editInput.text.toString().trim()
            if (text.isEmpty()) {
                Toast.makeText(this, "Ingresa texto", Toast.LENGTH_SHORT).show()
            } else {
                writingMode = true
                wifiWritingMode = false
                deletingMode = false
                editInput.isEnabled = false
                showWritePrompt("Listo para escribir texto", "Acerca la tarjeta NFC para escribir el texto...")
            }
        }

        // Configuración del botón Borrar
        btnDelete.setOnClickListener {
            deletingMode = true
            wifiWritingMode = false
            writingMode = false
            editInput.isEnabled = false
            showDeletePrompt()
        }

        // Configuración del botón WiFi
        btnConfigureWifi.setOnClickListener {
            val ssid = editSsid.text.toString().trim()
            val password = editPassword.text.toString().trim()
            val securityType = autoSecurityType.selectedItem.toString()

            if (ssid.isEmpty()) {
                Toast.makeText(this, "Ingresa el nombre de la red", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Validar contraseña solo si no es Abierta
            if (!securityType.contains("Abierta") && password.isEmpty()) {
                Toast.makeText(this, "Ingresa la contraseña", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            wifiWritingMode = true
            writingMode = false
            deletingMode = false

            // Deshabilitar campos WiFi
            editSsid.isEnabled = false
            editPassword.isEnabled = false
            autoSecurityType.isEnabled = false
            switchHiddenSsid.isEnabled = false

            showWritePrompt("Listo para escribir WiFi", "Acerca la tarjeta NFC para configurar la red WiFi...")
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
        val tag = intent.getParcelableExtra<Tag>(NfcAdapter.EXTRA_TAG) ?: return

        when {
            wifiWritingMode -> {
                val ssid = editSsid.text.toString()
                val password = editPassword.text.toString()
                val securityType = autoSecurityType.selectedItem.toString()
                val isHidden = switchHiddenSsid.isChecked

                val success = writeWifiToTag(tag, ssid, password, securityType, isHidden)
                if (success) {
                    wifiWritingMode = false
                    writeDialog?.dismiss()
                    Toast.makeText(this, "Configuración WiFi guardada", Toast.LENGTH_SHORT).show()
                    enableWifiFields()
                }
            }
            writingMode -> {
                val success = writeTextToTag(tag, editInput.text.toString(), Locale.getDefault())
                if (success) {
                    writingMode = false
                    writeDialog?.dismiss()
                    Toast.makeText(this, "Texto guardado", Toast.LENGTH_SHORT).show()
                    editInput.isEnabled = true
                }
            }
            deletingMode -> {
                val success = deleteTag(tag)
                if (success) {
                    deletingMode = false
                    writeDialog?.dismiss()
                    Toast.makeText(this, "Etiqueta borrada", Toast.LENGTH_SHORT).show()
                    editInput.isEnabled = true
                }
            }
        }
    }

    // Configurar lista desplegable para tipo de seguridad
    private fun setupSecurityTypeDropdown() {
        val securityTypes = arrayOf("WPA/WPA2 Personal", "WPA2", "WPA", "WEP", "Abierta (Sin contraseña)")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, securityTypes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        autoSecurityType.adapter = adapter
        autoSecurityType.setSelection(0) // Seleccionar WPA/WPA2 Personal por defecto
    }

    // Función para escribir configuración WiFi en la etiqueta NFC (FORMATO BINARIO WPS)
    private fun writeWifiToTag(tag: Tag, ssid: String, password: String, securityType: String, isHidden: Boolean): Boolean {
        return try {
            // Crear configuración WPS en formato binario (como NFC Tools)
            val wpsData = createWpsData(ssid, password, securityType)

            // Crear registro MIME con formato binario WPS
            val wifiRecord = NdefRecord.createMime("application/vnd.wfa.wsc", wpsData)

            // Crear mensaje con UN SOLO registro
            val message = NdefMessage(arrayOf(wifiRecord))

            val ndef = Ndef.get(tag) ?: throw Exception("Tag no NDEF")
            ndef.connect()
            if (!ndef.isWritable) throw Exception("Tag protegido")
            if (message.toByteArray().size > ndef.maxSize)
                throw Exception("Mensaje muy grande")
            ndef.writeNdefMessage(message)
            ndef.close()
            true
        } catch (e: Exception) {
            Toast.makeText(this, "Error WiFi: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    // Crear datos WPS en formato binario (FORMATO COMPLETO Y CORRECTO)
    private fun createWpsData(ssid: String, password: String, securityType: String): ByteArray {
        val data = mutableListOf<Byte>()

        // Credential
        data.addAll(byteArrayOf(0x10.toByte(), 0x0E.toByte()).toList()) // Credential attribute

        // Calcular el tamaño total del credential (se añadirá después)
        val credentialStart = data.size
        data.addAll(byteArrayOf(0x00, 0x00).toList()) // Placeholder para longitud

        // Network Index (opcional pero recomendado)
        data.addAll(byteArrayOf(0x10.toByte(), 0x26.toByte()).toList()) // Network Index
        data.addAll(byteArrayOf(0x00, 0x01).toList()) // Length
        data.add(0x01) // Index value

        // SSID (REQUERIDO)
        data.addAll(byteArrayOf(0x10.toByte(), 0x45.toByte()).toList()) // SSID attribute
        val ssidBytes = ssid.toByteArray(Charsets.UTF_8)
        data.addAll(byteArrayOf((ssidBytes.size shr 8).toByte(), (ssidBytes.size and 0xFF).toByte()).toList())
        data.addAll(ssidBytes.toList())

        // Authentication Type (REQUERIDO)
        data.addAll(byteArrayOf(0x10.toByte(), 0x03.toByte()).toList())
        data.addAll(byteArrayOf(0x00, 0x02).toList()) // Length
        val authType = when (getSecurityType(securityType)) {
            "WPA" -> byteArrayOf(0x00.toByte(), 0x22.toByte()) // WPA2-Personal
            "WEP" -> byteArrayOf(0x00.toByte(), 0x01.toByte()) // WEP
            "nopass" -> byteArrayOf(0x00.toByte(), 0x01.toByte()) // Open
            else -> byteArrayOf(0x00.toByte(), 0x22.toByte()) // Default WPA2
        }
        data.addAll(authType.toList())

        // Encryption Type (REQUERIDO)
        data.addAll(byteArrayOf(0x10.toByte(), 0x0F.toByte()).toList())
        data.addAll(byteArrayOf(0x00, 0x02).toList()) // Length
        val encType = when (getSecurityType(securityType)) {
            "WPA" -> byteArrayOf(0x00.toByte(), 0x08.toByte()) // TKIP+AES
            "WEP" -> byteArrayOf(0x00.toByte(), 0x01.toByte()) // WEP
            "nopass" -> byteArrayOf(0x00.toByte(), 0x01.toByte()) // None
            else -> byteArrayOf(0x00.toByte(), 0x08.toByte()) // Default TKIP+AES
        }
        data.addAll(encType.toList())

        // Network Key (Password) - Solo si hay contraseña
        if (password.isNotEmpty() && getSecurityType(securityType) != "nopass") {
            data.addAll(byteArrayOf(0x10.toByte(), 0x27.toByte()).toList()) // Network Key attribute
            val passwordBytes = password.toByteArray(Charsets.UTF_8)
            data.addAll(byteArrayOf((passwordBytes.size shr 8).toByte(), (passwordBytes.size and 0xFF).toByte()).toList())
            data.addAll(passwordBytes.toList())
        }

        // MAC Address (REQUERIDO - usar dirección ficticia)
        data.addAll(byteArrayOf(0x10.toByte(), 0x20.toByte()).toList()) // MAC Address attribute
        data.addAll(byteArrayOf(0x00, 0x06).toList()) // Length
        data.addAll(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()).toList()) // Broadcast MAC

        // Calcular y establecer la longitud real del credential
        val credentialLength = data.size - credentialStart - 2
        data[credentialStart] = ((credentialLength shr 8) and 0xFF).toByte()
        data[credentialStart + 1] = (credentialLength and 0xFF).toByte()

        return data.toByteArray()
    }

    // Función auxiliar para obtener el tipo de seguridad correcto
    private fun getSecurityType(securityType: String): String {
        return when {
            securityType.contains("WPA") -> "WPA"
            securityType.contains("WEP") -> "WEP"
            securityType.contains("Abierta") || securityType.contains("Open") -> "nopass"
            else -> "WPA"
        }
    }

    // Función para escribir texto en la etiqueta NFC (REEMPLAZA TODO)
    private fun writeTextToTag(tag: Tag, text: String, locale: Locale): Boolean {
        return try {
            // Crear UN SOLO registro de texto (reemplaza cualquier WiFi anterior)
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

    // Habilitar campos WiFi después de escribir
    private fun enableWifiFields() {
        editSsid.isEnabled = true
        editPassword.isEnabled = true
        autoSecurityType.isEnabled = true
        switchHiddenSsid.isEnabled = true
    }

    // Muestra el mensaje cuando se activa el modo de escritura
    private fun showWritePrompt(title: String, message: String) {
        writeDialog = AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setCancelable(true)
            .setIcon(android.R.drawable.ic_dialog_info)
            .setPositiveButton("Listo") { dialog, _ ->
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                // Cancelar la escritura
                writingMode = false
                wifiWritingMode = false
                deletingMode = false
                editInput.isEnabled = true
                enableWifiFields()
                dialog.dismiss()
            }
            .create().also { it.show() }
    }

    // Muestra el mensaje cuando se activa el modo de borrado
    private fun showDeletePrompt() {
        writeDialog = AlertDialog.Builder(this)
            .setTitle("Listo para borrar")
            .setMessage("Acerca la tarjeta NFC para borrar el contenido...")
            .setCancelable(true)
            .setIcon(android.R.drawable.ic_dialog_info)
            .setPositiveButton("Borrar") { dialog, _ ->
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar") { dialog, _ ->
                deletingMode = false
                editInput.isEnabled = true
                dialog.dismiss()
            }
            .create().also { it.show() }
    }

    // Función para borrar el contenido de la etiqueta NFC
    private fun deleteTag(tag: Tag): Boolean {
        return try {
            val ndef = Ndef.get(tag) ?: throw Exception("Tag no NDEF")
            ndef.connect()
            if (!ndef.isWritable) throw Exception("Tag protegido")

            // Crear un registro vacío para "borrar" el contenido
            val emptyRecord = NdefRecord.createTextRecord("en", "")
            val message = NdefMessage(arrayOf(emptyRecord))

            ndef.writeNdefMessage(message)
            ndef.close()
            true
        } catch (e: Exception) {
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }
}