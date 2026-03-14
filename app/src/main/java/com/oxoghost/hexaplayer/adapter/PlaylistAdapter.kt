package com.oxoghost.hexaplayer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.oxoghost.hexaplayer.R
import com.oxoghost.hexaplayer.data.Playlist
import com.oxoghost.hexaplayer.databinding.ItemPlaylistBinding

class PlaylistAdapter(
    private val onClick: (Playlist) -> Unit,
    private val onMore: (Playlist, View) -> Unit
) : ListAdapter<Playlist, PlaylistAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(val binding: ItemPlaylistBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ItemPlaylistBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(b)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val playlist = getItem(position)
        with(holder.binding) {
            tvPlaylistName.text = playlist.name
            tvSongCount.text = "${playlist.songIds.size} songs"

            if (playlist.coverUri != null) {
                ivPlaylistCover.setPadding(0, 0, 0, 0)
                ivPlaylistCover.clearColorFilter()
                ivPlaylistCover.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                ivPlaylistCover.load(playlist.coverUri) {
                    crossfade(true)
                    placeholder(R.drawable.ic_playlist)
                    error(R.drawable.ic_playlist)
                }
            } else {
                val pad = (10 * root.resources.displayMetrics.density).toInt()
                ivPlaylistCover.setPadding(pad, pad, pad, pad)
                ivPlaylistCover.scaleType = android.widget.ImageView.ScaleType.CENTER_INSIDE
                val tv = android.util.TypedValue()
                root.context.theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, tv, true)
                ivPlaylistCover.setColorFilter(tv.data)
                ivPlaylistCover.setImageResource(R.drawable.ic_playlist)
            }

            root.setOnClickListener { onClick(playlist) }
            btnMore.setOnClickListener { onMore(playlist, it) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Playlist>() {
            override fun areItemsTheSame(a: Playlist, b: Playlist) = a.id == b.id
            override fun areContentsTheSame(a: Playlist, b: Playlist) = a == b
        }
    }
}
