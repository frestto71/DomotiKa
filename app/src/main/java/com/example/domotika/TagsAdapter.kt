package com.example.domotika

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TagsAdapter(private val tags: List<NfcActivity.NfcTag>) : RecyclerView.Adapter<TagsAdapter.TagViewHolder>() {

    class TagViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val idText: TextView = view.findViewById(R.id.text_id)
        val contentText: TextView = view.findViewById(R.id.text_content)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tag, parent, false)
        return TagViewHolder(view)
    }

    override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
        val tag = tags[position]
        holder.idText.text = "ID: ${tag.id}"
        holder.contentText.text = "Contenido: ${tag.content}"
    }

    override fun getItemCount(): Int = tags.size
}
