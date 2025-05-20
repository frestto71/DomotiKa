package com.example.domotika

import android.content.*
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class UsbActivity : AppCompatActivity() {

    private lateinit var usbStatus: TextView
    private lateinit var usbManager: UsbManager
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: FileAdapter

    private val REQUEST_CODE_OPEN_DOCUMENT_TREE = 42

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
        usbManager = getSystemService(USB_SERVICE) as UsbManager

        recyclerView.layoutManager = LinearLayoutManager(this)
        adapter = FileAdapter(emptyList()) { fileItem ->
            // Al hacer click en carpeta, abrirla
            if (fileItem.isDirectory) {
                fileItem.uriString.let { uriStr ->
                    val uri = Uri.parse(uriStr)
                    listFilesFromUri(uri)
                    usbStatus.text = "Mostrando archivos en: $uriStr"
                }
            } else {
                Toast.makeText(this, "Archivo seleccionado: ${fileItem.name}", Toast.LENGTH_SHORT).show()
            }
        }
        recyclerView.adapter = adapter

        registerReceiver(
            usbReceiver,
            IntentFilter().apply {
                addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
            }
        )

        openFolderSelector()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(usbReceiver)
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
        if (requestCode == REQUEST_CODE_OPEN_DOCUMENT_TREE && resultCode == RESULT_OK) {
            data?.data?.let { uri ->
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                usbStatus.text = "Mostrando archivos en: ${uri.path}"
                listFilesFromUri(uri)
            }
        }
    }

    private fun listFilesFromUri(uri: Uri) {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))

        val cursor = contentResolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            null, null, null
        )

        val files = mutableListOf<FileAdapter.FileItem>()
        cursor?.use {
            while (it.moveToNext()) {
                val name = it.getString(0)
                val mime = it.getString(1)
                val docId = it.getString(2)
                val isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val childUri = DocumentsContract.buildDocumentUriUsingTree(uri, docId)
                files.add(FileAdapter.FileItem(name, isDirectory, childUri.toString()))
            }
        }

        if (files.isEmpty()) {
            usbStatus.text = "Carpeta vacía"
        }

        adapter.updateFiles(files)
    }
}
