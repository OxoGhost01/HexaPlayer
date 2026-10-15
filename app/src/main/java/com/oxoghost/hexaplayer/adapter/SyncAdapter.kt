package com.oxoghost.hexaplayer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.oxoghost.hexaplayer.R
import com.oxoghost.hexaplayer.data.SyncEntry

class SyncAdapter(
    private val onResync: (SyncEntry) -> Unit
) : ListAdapter<SyncEntry, SyncAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SyncEntry>() {
            override fun areItemsTheSame(a: SyncEntry, b: SyncEntry) = a.url == b.url
            override fun areContentsTheSame(a: SyncEntry, b: SyncEntry) = a == b
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.tv_sync_name)
        val resync: MaterialButton = itemView.findViewById(R.id.btn_resync)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_sync, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val entry = getItem(position)
        holder.name.text = entry.name
        holder.resync.setOnClickListener { onResync(entry) }
    }
}
