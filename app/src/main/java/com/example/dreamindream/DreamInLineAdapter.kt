// file: app/src/main/java/com/example/dreamindream/DreamInlineAdapter.kt
package com.example.dreamindream

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class DreamInlineAdapter(
    private val items: MutableList<DreamEntry>,
    private val onOpen: (DreamEntry) -> Unit,
    private val onDelete: (position: Int, DreamEntry) -> Unit
) : RecyclerView.Adapter<DreamInlineAdapter.VH>() {

    inner class VH(v: View) : RecyclerView.ViewHolder(v) {
        val text: TextView = v.findViewById(R.id.textDream)
        val btnDelete: ImageButton = v.findViewById(R.id.btnDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dream_inline, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val item = items[position]
        h.text.text = item.dream

        // 카드 전체 터치 = 열기
        h.itemView.setOnClickListener { onOpen(item) }

        // X 아이콘 = 삭제 확인 후 삭제
        h.btnDelete.setOnClickListener { onDelete(position, item) }
    }

    override fun getItemCount(): Int = items.size

    fun replaceAll(newItems: List<DreamEntry>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun removeAt(pos: Int) {
        if (pos in 0 until items.size) {
            items.removeAt(pos)
            notifyItemRemoved(pos)
        }
    }
}
