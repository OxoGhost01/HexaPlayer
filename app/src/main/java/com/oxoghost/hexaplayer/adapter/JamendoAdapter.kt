package com.oxoghost.hexaplayer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.oxoghost.hexaplayer.R
import com.oxoghost.hexaplayer.data.JamendoTrack
import com.google.android.material.button.MaterialButton

class JamendoAdapter(
    private val onDownload: (JamendoTrack) -> Unit
) : ListAdapter<JamendoTrack, JamendoAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<JamendoTrack>() {
            override fun areItemsTheSame(a: JamendoTrack, b: JamendoTrack) = a.id == b.id
            override fun areContentsTheSame(a: JamendoTrack, b: JamendoTrack) = a == b
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivCover: ImageView = itemView.findViewById(R.id.iv_jamendo_cover)
        val tvName: TextView = itemView.findViewById(R.id.tv_jamendo_name)
        val tvArtist: TextView = itemView.findViewById(R.id.tv_jamendo_artist)
        val btnDownload: MaterialButton = itemView.findViewById(R.id.btn_jamendo_download)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_jamendo_track, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val track = getItem(position)
        holder.tvName.text = track.name
        holder.tvArtist.text = track.artistName
        holder.ivCover.load(track.albumImage) {
            placeholder(R.drawable.ic_music_note)
            error(R.drawable.ic_music_note)
        }
        holder.btnDownload.setOnClickListener { onDownload(track) }
    }
}
