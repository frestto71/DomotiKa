package com.example.domotika

import android.content.*
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class UsbActivity : AppCompatActivity() {

    private lateinit var usbStatus: TextView
    private lateinit var usbManager: UsbManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: UsbFileAdapter
    private lateinit var btnSort: com.google.android.material.button.MaterialButton

    private val REQUEST_CODE_OPEN_DOCUMENT_TREE = 42
    private val REQUEST_CODE_MOVE_TO_FOLDER = 1001

    private var currentRootUri: Uri? = null
    private var moveSourceUri: Uri? = null
    private var moveFileName: String? = null

    private var currentSort: SortOption = SortOption.NAME

    private var mediaPlayer: MediaPlayer? = null  // MediaPlayer para audio

    enum class SortOption {
        NAME, SIZE, DATE
    }

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    val device: UsbDevice? = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    device?.let {
                        usbStatus.text = "Dispositivo USB conectado:\n" +
                                "Nombre: ${it.deviceName}\n" +
                                "ID: ${it.deviceId}\n" +
                                "Clase: ${it.deviceClass}"
                    }
                    openFolderSelector()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    usbStatus.text = "Dispositivo USB desconectado"
                    adapter.updateFiles(emptyList())
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_usb)

        usbStatus = findViewById(R.id.usb_status)
        recyclerView = findViewById(R.id.usb_recycler)
        btnSort = findViewById(R.id.btn_sort)
        usbManager = getSystemService(USB_SERVICE) as UsbManager

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = UsbFileAdapter(emptyList()) { fileItem ->
            showFileOptionsDialog(fileItem)
        }
        recyclerView.adapter = adapter

        registerReceiver(
            usbReceiver,
            IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
        )

        btnSort.setOnClickListener {
            showSortOptionsDialog()
        }

        openFolderSelector()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
        mediaPlayer?.release()
        mediaPlayer = null
    }

    private fun openFolderSelector() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            )
        }
        startActivityForResult(intent, REQUEST_CODE_OPEN_DOCUMENT_TREE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK) {
            when (requestCode) {
                REQUEST_CODE_OPEN_DOCUMENT_TREE -> {
                    data?.data?.let { uri ->
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                        )
                        usbStatus.text = "Mostrando archivos en: ${uri.path}"
                        currentRootUri = uri
                        listFilesFromUri(uri)
                    }
                }
                REQUEST_CODE_MOVE_TO_FOLDER -> {
                    val targetUri = data?.data
                    if (targetUri != null && moveSourceUri != null && moveFileName != null) {
                        moveFile(moveSourceUri!!, targetUri, moveFileName!!)
                    }
                }
            }
        }
    }

    private fun showSortOptionsDialog() {
        val options = arrayOf("Nombre", "Tamaño", "Fecha")
        AlertDialog.Builder(this)
            .setTitle("Ordenar por")
            .setSingleChoiceItems(options, currentSort.ordinal) { dialog, which ->
                currentSort = SortOption.values()[which]
                dialog.dismiss()
                currentRootUri?.let { listFilesFromUri(it) }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun listFilesFromUri(uri: Uri) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
            uri,
            DocumentsContract.getTreeDocumentId(uri)
        )

        val cursor = contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED
            ),
            null, null, null
        )

        val files = mutableListOf<UsbFileAdapter.FileItem>()
        cursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(0)
                val mime = it.getString(1)
                val docId = it.getString(2)
                val size = it.getLong(3)
                val lastModified = it.getLong(4)
                val isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val childUri = DocumentsContract.buildDocumentUriUsingTree(uri, docId)
                // PASAMOS MIME TIPO AL FILEITEM
                files.add(UsbFileAdapter.FileItem(name, isDirectory, childUri.toString(), size, lastModified, mime))
            }
        }

        if (files.isEmpty()) {
            usbStatus.text = "Carpeta vacía"
        }

        val sortedFiles = sortFiles(files)

        adapter.updateFiles(sortedFiles)
    }

    private fun sortFiles(files: List<UsbFileAdapter.FileItem>): List<UsbFileAdapter.FileItem> {
        return when (currentSort) {
            SortOption.NAME -> files.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            SortOption.SIZE -> files.sortedWith(compareBy({ !it.isDirectory }, { it.size }))
            SortOption.DATE -> files.sortedWith(compareBy({ !it.isDirectory }, { it.lastModified }))
        }
    }

    private fun showFileOptionsDialog(fileItem: UsbFileAdapter.FileItem) {
        val uri = Uri.parse(fileItem.uriString)
        val optionsList = mutableListOf("Eliminar archivo", "Renombrar archivo", "Mover archivo")

        // Si es audio, agregamos opción reproducir
        if (fileItem.mimeType?.startsWith("audio") == true) {
            optionsList.add("Reproducir audio")
        }

        val options = optionsList.toTypedArray()

        AlertDialog.Builder(this, R.style.CustomDialogTheme)
            .setTitle("Opciones para: ${fileItem.name}")
            .setItems(options) { _, which ->
                when (options[which]) {
                    "Eliminar archivo" -> deleteFile(uri)
                    "Renombrar archivo" -> renameFile(uri, fileItem.name)
                    "Mover archivo" -> {
                        moveSourceUri = uri
                        moveFileName = fileItem.name
                        selectTargetFolderForMove()
                    }
                    "Reproducir audio" -> playAudio(uri)
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun playAudio(uri: Uri) {
        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(this@UsbActivity, uri)
                setOnPreparedListener { start() }
                setOnCompletionListener {
                    it.release()
                    mediaPlayer = null
                    Toast.makeText(this@UsbActivity, "Reproducción finalizada", Toast.LENGTH_SHORT).show()
                }
                prepareAsync()
            }
            Toast.makeText(this, "Reproduciendo audio...", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error al reproducir audio: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun deleteFile(uri: Uri) {
        try {
            val deleted = DocumentsContract.deleteDocument(contentResolver, uri)
            if (deleted) {
                Toast.makeText(this, "Archivo eliminado", Toast.LENGTH_SHORT).show()
                currentRootUri?.let { listFilesFromUri(it) }
            } else {
                Toast.makeText(this, "No se pudo eliminar", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error al eliminar: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun renameFile(uri: Uri, oldName: String) {
        val input = EditText(this)
        input.setText(oldName)

        AlertDialog.Builder(this)
            .setTitle("Renombrar archivo")
            .setView(input)
            .setPositiveButton("Aceptar") { _, _ ->
                try {
                    val newUri = DocumentsContract.renameDocument(contentResolver, uri, input.text.toString())
                    if (newUri != null) {
                        Toast.makeText(this, "Archivo renombrado", Toast.LENGTH_SHORT).show()
                        currentRootUri?.let { listFilesFromUri(it) }
                    } else {
                        Toast.makeText(this, "No se pudo renombrar", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Error al renombrar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun selectTargetFolderForMove() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        startActivityForResult(Intent.createChooser(intent, "Seleccionar carpeta destino"), REQUEST_CODE_MOVE_TO_FOLDER)
    }

    private fun moveFile(sourceUri: Uri, targetParentUri: Uri, fileName: String) {
        try {
            val documentId = DocumentsContract.getTreeDocumentId(targetParentUri)
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(targetParentUri, documentId)

            val input = contentResolver.openInputStream(sourceUri)
            val newUri = DocumentsContract.createDocument(
                contentResolver,
                documentUri,
                "application/octet-stream",
                fileName
            )

            if (input != null && newUri != null) {
                val output = contentResolver.openOutputStream(newUri)
                if (output != null) {
                    input.copyTo(output)
                    input.close()
                    output.close()

                    DocumentsContract.deleteDocument(contentResolver, sourceUri)
                    Toast.makeText(this, "Archivo movido", Toast.LENGTH_SHORT).show()
                    currentRootUri?.let { listFilesFromUri(it) }
                } else {
                    Toast.makeText(this, "No se pudo abrir destino", Toast.LENGTH_SHORT).show()
                }
            } else {
                Toast.makeText(this, "No se pudo crear archivo destino", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error al mover archivo: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
