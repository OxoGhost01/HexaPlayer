package com.example.hexaplayer.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.hexaplayer.databinding.ItemLyricBinding

class LyricsAdapter : RecyclerView.Adapter<LyricsAdapter.ViewHolder>() {

    private var lines: List<String> = emptyList()
    private var highlightIndex: Int = -1

    inner class ViewHolder(val binding: ItemLyricBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ItemLyricBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(b)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val isActive = position == highlightIndex
        holder.binding.tvLyric.text = lines[position]
        holder.binding.tvLyric.alpha = if (isActive) 1.0f else 0.38f
        holder.binding.tvLyric.textSize = if (isActive) 20f else 18f
    }

    override fun getItemCount() = lines.size

    fun setLines(newLines: List<String>) {
        lines = newLines
        highlightIndex = -1
        notifyDataSetChanged()
    }

    fun setHighlight(index: Int) {
        if (index == highlightIndex) return
        val old = highlightIndex
        highlightIndex = index
        if (old in lines.indices) notifyItemChanged(old)
        if (index in lines.indices) notifyItemChanged(index)
    }
}
