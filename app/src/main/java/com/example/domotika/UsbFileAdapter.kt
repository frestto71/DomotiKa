package com.example.domotika

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class UsbFileAdapter(
    private var files: List<FileItem>,
    private val onClick: (FileItem) -> Unit
) : RecyclerView.Adapter<UsbFileAdapter.UsbFileViewHolder>() {

    data class FileItem(
        val name: String,
        val isDirectory: Boolean,
        val uriString: String,
        val size: Long,
        val lastModified: Long,
        val mimeType: String? // nuevo campo
    )


    inner class UsbFileViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameText: TextView = itemView.findViewById(R.id.file_name)
        val typeText: TextView = itemView.findViewById(R.id.file_type)
        // Puedes agregar TextViews para tamaño y fecha si quieres mostrar
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UsbFileViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_usb_file, parent, false)
        return UsbFileViewHolder(view)
    }

    override fun onBindViewHolder(holder: UsbFileViewHolder, position: Int) {
        val file = files[position]
        holder.nameText.text = file.name
        holder.typeText.text = if (file.isDirectory) "Carpeta" else "Archivo"
        // Opcional: muestra tamaño y fecha formateada aquí si agregas TextViews
        holder.itemView.setOnClickListener { onClick(file) }
    }

    override fun getItemCount(): Int = files.size

    fun updateFiles(newFiles: List<FileItem>) {
        files = newFiles
        notifyDataSetChanged()
    }
}
