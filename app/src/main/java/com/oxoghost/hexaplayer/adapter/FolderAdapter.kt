package com.oxoghost.hexaplayer.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.oxoghost.hexaplayer.R
import com.oxoghost.hexaplayer.data.Song
import com.oxoghost.hexaplayer.databinding.ItemFolderBinding
import com.oxoghost.hexaplayer.databinding.ItemSongBinding
import com.oxoghost.hexaplayer.util.toTimeString

/** Mixed adapter that shows folder entries and song entries for the folder browser. */
class FolderAdapter(
    private val onFolderClick: (path: String, displayName: String) -> Unit,
    private val onSongClick: (Song) -> Unit,
    private val onSongMore: (Song, android.view.View) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class Item {
        data class FolderItem(val path: String, val displayName: String, val childCount: Int) : Item()
        data class SongItem(val song: Song) : Item()
    }

    private var items: List<Item> = emptyList()

    fun submitItems(newItems: List<Item>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun getSongAt(position: Int): Song? =
        (items.getOrNull(position) as? Item.SongItem)?.song

    override fun getItemViewType(position: Int) = when (items[position]) {
        is Item.FolderItem -> TYPE_FOLDER
        is Item.SongItem -> TYPE_SONG
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_FOLDER) {
            FolderViewHolder(ItemFolderBinding.inflate(inflater, parent, false))
        } else {
            SongViewHolder(ItemSongBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is Item.FolderItem -> (holder as FolderViewHolder).bind(item)
            is Item.SongItem -> (holder as SongViewHolder).bind(item.song)
        }
    }

    inner class FolderViewHolder(private val b: ItemFolderBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(item: Item.FolderItem) {
            b.tvFolderName.text = item.displayName
            b.tvItemCount.text = "${item.childCount} items"
            b.root.setOnClickListener { onFolderClick(item.path, item.displayName) }
        }
    }

    inner class SongViewHolder(private val b: ItemSongBinding) :
        RecyclerView.ViewHolder(b.root) {
        fun bind(song: Song) {
            b.tvTitle.text = song.title
            b.tvArtist.text = song.artist
            b.tvDuration.text = song.duration.toTimeString()
            b.ivAlbumArt.load(SongAdapter.albumArtUri(song.albumId)) {
                placeholder(R.drawable.ic_music_note)
                error(R.drawable.ic_music_note)
            }
            b.root.setOnClickListener { onSongClick(song) }
            b.btnMore.setOnClickListener { onSongMore(song, it) }
        }
    }

    companion object {
        const val TYPE_FOLDER = 0
        const val TYPE_SONG = 1
    }
}
