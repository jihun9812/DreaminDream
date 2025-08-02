package com.example.dreamindream

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.recyclerview.widget.RecyclerView

class DreamPreviewAdapter(
    private val dreamList: List<DreamEntry>,
    private val onItemClick: (DreamEntry) -> Unit
) : RecyclerView.Adapter<DreamPreviewAdapter.DreamViewHolder>() {

    inner class DreamViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val card: CardView = view.findViewById(R.id.cardDream)
        val previewText: TextView = view.findViewById(R.id.textDreamPreview)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DreamViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dream_preview, parent, false)
        return DreamViewHolder(view)
    }

    override fun onBindViewHolder(holder: DreamViewHolder, position: Int) {
        val dreamEntry = dreamList[position]
        val preview = dreamEntry.dream.lines().firstOrNull()?.take(50) ?: "(제목 없음)"
        holder.previewText.text = preview.trim() + "..."

        holder.card.setOnClickListener {
            onItemClick(dreamEntry)
        }
    }

    override fun getItemCount(): Int = dreamList.size
}
