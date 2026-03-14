package com.oxoghost.hexaplayer.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.oxoghost.hexaplayer.data.Artist
import com.oxoghost.hexaplayer.databinding.ItemArtistBinding

class ArtistAdapter(
    private val onClick: (Artist) -> Unit
) : ListAdapter<Artist, ArtistAdapter.ViewHolder>(DIFF) {

    inner class ViewHolder(val binding: ItemArtistBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val b = ItemArtistBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(b)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val artist = getItem(position)
        with(holder.binding) {
            tvArtistName.text = artist.name
            tvInfo.text = "${artist.albums.size} albums · ${artist.songs.size} songs"
            root.setOnClickListener { onClick(artist) }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Artist>() {
            override fun areItemsTheSame(a: Artist, b: Artist) = a.name == b.name
            override fun areContentsTheSame(a: Artist, b: Artist) = a == b
        }
    }
}
