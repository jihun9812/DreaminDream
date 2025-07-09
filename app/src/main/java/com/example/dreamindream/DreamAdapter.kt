package com.example.dreamindream

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class DreamAdapter(
    private val items: List<DreamEntry>
) : RecyclerView.Adapter<DreamAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val dreamText: TextView = view.findViewById(R.id.dreamText)
        val resultText: TextView = view.findViewById(R.id.resultText)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dream, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = items[position]
        holder.dreamText.text = "• ${entry.dream}"
        holder.resultText.text = "→ ${entry.result}"
    }

    override fun getItemCount(): Int = items.size
}
