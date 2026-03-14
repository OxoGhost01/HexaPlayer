package com.oxoghost.hexaplayer.adapter

import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.oxoghost.hexaplayer.R
import com.oxoghost.hexaplayer.data.Song
import com.oxoghost.hexaplayer.databinding.ItemQueueSongBinding
import com.oxoghost.hexaplayer.databinding.ItemQueueSeparatorBinding

/**
 * Adapter for the queue bottom-sheet.
 *
 * Item types:
 *  - [Item.SongEntry]  – a song in either the user queue or the context queue
 *  - [Item.Separator]  – dashed divider between the two sections
 *  - [Item.Header]     – section label ("Next in queue" / "Next from context")
 */
class QueueAdapter(
    private val onItemClick: (exoIndex: Int) -> Unit,
    private val onRemove: (exoIndex: Int) -> Unit,
    private val onMove: (from: Int, to: Int) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class Item {
        data class SongEntry(
            val song: Song,
            val isUserQueue: Boolean,
            val exoIndex: Int,
            val isCurrent: Boolean
        ) : Item()

        data class Header(val text: String) : Item()
        object Separator : Item()
    }

    companion object {
        const val TYPE_SONG      = 0
        const val TYPE_SEPARATOR = 1
        const val TYPE_HEADER    = 2
    }

    private var items: List<Item> = emptyList()
    var dragEnabled: Boolean = true          // context-queue drag on/off (shuffle)
    var itemTouchHelper: ItemTouchHelper? = null

    fun submitItems(newItems: List<Item>) {
        items = newItems
        notifyDataSetChanged()
    }

    fun getItemAt(pos: Int): Item? = items.getOrNull(pos)

    /** Move within the adapter list (called during drag). */
    fun moveItem(from: Int, to: Int) {
        val mutable = items.toMutableList()
        mutable.add(to, mutable.removeAt(from))
        items = mutable
        notifyItemMoved(from, to)
    }

    override fun getItemViewType(position: Int) = when (items[position]) {
        is Item.SongEntry -> TYPE_SONG
        is Item.Separator -> TYPE_SEPARATOR
        is Item.Header    -> TYPE_HEADER
    }

    override fun getItemCount() = items.size

    // ── ViewHolder classes ────────────────────────────────────────────────────
    inner class SongVH(val binding: ItemQueueSongBinding) : RecyclerView.ViewHolder(binding.root)
    inner class SeparatorVH(binding: ItemQueueSeparatorBinding) : RecyclerView.ViewHolder(binding.root)
    inner class HeaderVH(val view: View) : RecyclerView.ViewHolder(view) {
        val tv = view.findViewById<android.widget.TextView>(R.id.tv_queue_section_label)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_SONG -> SongVH(
                ItemQueueSongBinding.inflate(inflater, parent, false)
            )
            TYPE_SEPARATOR -> SeparatorVH(
                ItemQueueSeparatorBinding.inflate(inflater, parent, false)
            )
            else -> HeaderVH(
                inflater.inflate(R.layout.item_queue_header, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is Item.SongEntry -> bindSong(holder as SongVH, item)
            is Item.Header    -> (holder as HeaderVH).tv.text = item.text
            is Item.Separator -> { /* static view, nothing to bind */ }
        }
    }

    private fun bindSong(holder: SongVH, item: Item.SongEntry) {
        val b = holder.binding
        b.tvTitle.text  = item.song.title
        b.tvArtist.text = item.song.artist
        b.ivAlbumArt.load(SongAdapter.albumArtUri(item.song.albumId)) {
            placeholder(R.drawable.ic_music_note)
            error(R.drawable.ic_music_note)
        }

        b.playingIndicator.visibility = if (item.isCurrent) View.VISIBLE else View.INVISIBLE
        b.ivNowPlaying.visibility     = if (item.isCurrent) View.VISIBLE else View.GONE
        b.tvTitle.setTextColor(
            holder.itemView.context.getColor(
                if (item.isCurrent) R.color.colorPrimary else R.color.colorText
            )
        )

        holder.itemView.setOnClickListener { onItemClick(item.exoIndex) }
        b.btnRemove.setOnClickListener   { onRemove(item.exoIndex) }

        // Drag handle: always visible for user-queue; for context only when drag enabled
        val canDrag = item.isUserQueue || dragEnabled
        b.ivDragHandle.visibility = if (canDrag) View.VISIBLE else View.GONE
        b.ivDragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN && canDrag) {
                itemTouchHelper?.startDrag(holder)
            }
            false
        }
    }
}
