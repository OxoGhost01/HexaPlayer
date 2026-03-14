package com.oxoghost.hexaplayer.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.oxoghost.hexaplayer.R
import com.oxoghost.hexaplayer.data.DownloadItem
import com.oxoghost.hexaplayer.data.DownloadStatus

class DownloadAdapter(
    private val onCancel: (DownloadItem) -> Unit
) : ListAdapter<DownloadItem, DownloadAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DownloadItem>() {
            override fun areItemsTheSame(a: DownloadItem, b: DownloadItem) = a.id == b.id
            override fun areContentsTheSame(a: DownloadItem, b: DownloadItem) = a == b
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val ivCover: ImageView = itemView.findViewById(R.id.iv_download_cover)
        val tvTitle: TextView = itemView.findViewById(R.id.tv_download_title)
        val tvArtist: TextView = itemView.findViewById(R.id.tv_download_artist)
        val tvStatus: TextView = itemView.findViewById(R.id.tv_download_status)
        val progressBar: ProgressBar = itemView.findViewById(R.id.progress_download)
        val btnCancel: ImageButton = itemView.findViewById(R.id.btn_cancel_download)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_download, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.tvTitle.text = item.title
        holder.tvArtist.text = item.artist
        holder.ivCover.load(item.imageUrl) {
            placeholder(R.drawable.ic_music_note)
            error(R.drawable.ic_music_note)
        }
        val ctx = holder.itemView.context

        when (item.status) {
            DownloadStatus.QUEUED -> {
                holder.tvStatus.text = ctx.getString(R.string.dl_status_queued)
                holder.progressBar.isIndeterminate = false
                holder.progressBar.progress = 0
                holder.btnCancel.visibility = View.VISIBLE
            }
            DownloadStatus.DOWNLOADING -> {
                holder.tvStatus.text = if (item.progress > 0)
                    ctx.getString(R.string.dl_status_downloading_pct, item.progress)
                else
                    ctx.getString(R.string.dl_status_preparing)
                holder.progressBar.isIndeterminate = item.progress == 0
                holder.progressBar.progress = item.progress
                holder.btnCancel.visibility = View.VISIBLE
            }
            DownloadStatus.DONE -> {
                holder.tvStatus.text = ctx.getString(R.string.dl_status_done)
                holder.progressBar.isIndeterminate = false
                holder.progressBar.progress = 100
                holder.btnCancel.visibility = View.GONE
            }
            DownloadStatus.SKIPPED -> {
                holder.tvStatus.text = ctx.getString(R.string.dl_status_skipped)
                holder.progressBar.isIndeterminate = false
                holder.progressBar.progress = 100
                holder.btnCancel.visibility = View.GONE
            }
            DownloadStatus.ERROR -> {
                holder.tvStatus.text = ctx.getString(R.string.dl_status_error, item.errorMessage ?: "unknown")
                holder.progressBar.isIndeterminate = false
                holder.progressBar.progress = 0
                holder.btnCancel.visibility = View.GONE
            }
            DownloadStatus.CANCELLED -> {
                holder.tvStatus.text = ctx.getString(R.string.dl_status_cancelled)
                holder.progressBar.isIndeterminate = false
                holder.progressBar.progress = 0
                holder.btnCancel.visibility = View.GONE
            }
        }

        holder.btnCancel.setOnClickListener { onCancel(item) }
    }
}
