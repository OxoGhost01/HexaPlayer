package com.oxoghost.hexaplayer.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.oxoghost.hexaplayer.R
import com.oxoghost.hexaplayer.data.Album
import com.oxoghost.hexaplayer.databinding.ItemAlbumBinding

class AlbumAdapter(
    private val onClick: (Album) -> Unit
) : ListAdapter<Album, AlbumAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(val binding: ItemAlbumBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ItemAlbumBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(b)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val album = getItem(position)
        with(holder.binding) {
            tvAlbumName.text = album.title
            tvArtist.text = album.artist
            ivAlbumArt.load(SongAdapter.albumArtUri(album.id)) {
                placeholder(R.drawable.ic_music_note)
                error(R.drawable.ic_music_note)
            }
            root.setOnClickListener { onClick(album) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Album>() {
            override fun areItemsTheSame(a: Album, b: Album) = a.id == b.id
            override fun areContentsTheSame(a: Album, b: Album) = a == b
        }
    }
}
