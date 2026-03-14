package com.oxoghost.hexaplayer.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.oxoghost.hexaplayer.R
import com.oxoghost.hexaplayer.data.Song
import com.oxoghost.hexaplayer.databinding.ItemSongBinding
import com.oxoghost.hexaplayer.util.toTimeString

class SongAdapter(
    private val onSongClick: (Song) -> Unit,
    private val onMoreClick: (Song, View) -> Unit
) : ListAdapter<Song, SongAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(val binding: ItemSongBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(b)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val song = getItem(position)
        with(holder.binding) {
            tvTitle.text = song.title
            tvArtist.text = song.artist
            tvDuration.text = song.duration.toTimeString()
            ivAlbumArt.load(artUri(song)) {
                placeholder(R.drawable.ic_music_note)
                error(R.drawable.ic_music_note)
            }
            root.setOnClickListener { onSongClick(song) }
            btnMore.setOnClickListener { onMoreClick(song, it) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Song>() {
            override fun areItemsTheSame(a: Song, b: Song) = a.id == b.id
            override fun areContentsTheSame(a: Song, b: Song) = a == b
        }

        fun albumArtUri(albumId: Long): Uri =
            Uri.parse("content://media/external/audio/albumart/$albumId")

        fun artUri(song: Song): Uri =
            song.customCoverUri?.let { Uri.parse(it) } ?: albumArtUri(song.albumId)
    }
}
