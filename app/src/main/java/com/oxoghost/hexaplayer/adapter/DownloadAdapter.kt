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
import com.oxoghost.hexaplayer.R
import com.oxoghost.hexaplayer.data.JobKind
import com.oxoghost.hexaplayer.data.JobPhase
import com.oxoghost.hexaplayer.data.SpotdlJob

class DownloadAdapter(
    private val onStop: (SpotdlJob) -> Unit,
    private val onRetry: (SpotdlJob) -> Unit
) : ListAdapter<SpotdlJob, DownloadAdapter.ViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<SpotdlJob>() {
            override fun areItemsTheSame(a: SpotdlJob, b: SpotdlJob) = a.id == b.id
            override fun areContentsTheSame(a: SpotdlJob, b: SpotdlJob) = a == b
        }
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ImageView = itemView.findViewById(R.id.iv_job_icon)
        val title: TextView = itemView.findViewById(R.id.tv_job_title)
        val status: TextView = itemView.findViewById(R.id.tv_job_status)
        val progress: ProgressBar = itemView.findViewById(R.id.progress_job)
        val action: ImageButton = itemView.findViewById(R.id.btn_job_action)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_download, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val job = getItem(position)
        val ctx = holder.itemView.context

        holder.icon.setImageResource(
            if (job.kind == JobKind.SYNC) R.drawable.ic_repeat else R.drawable.ic_download
        )
        val kindLabel = if (job.kind == JobKind.SYNC)
            ctx.getString(R.string.dl_kind_sync) else ctx.getString(R.string.dl_kind_download)
        holder.title.text = "$kindLabel · ${job.label}"

        when (job.phase) {
            JobPhase.QUEUED -> {
                holder.status.text = job.message
                holder.progress.isIndeterminate = true
            }
            JobPhase.SERVER -> {
                val counter = if (job.serverTotal > 0) " ${job.serverDone}/${job.serverTotal}"
                else if (job.serverDone > 0) " ${job.serverDone}" else ""
                holder.status.text = ctx.getString(R.string.dl_phase_server) + counter +
                    if (job.message.isNotBlank()) "\n${job.message}" else ""
                if (job.serverTotal > 0) {
                    holder.progress.isIndeterminate = false
                    holder.progress.progress = (job.serverDone * 100 / job.serverTotal).coerceIn(0, 100)
                } else {
                    holder.progress.isIndeterminate = true
                }
            }
            JobPhase.IMPORTING -> {
                holder.status.text = ctx.getString(R.string.dl_phase_importing)
                holder.progress.isIndeterminate = true
            }
            JobPhase.DONE -> {
                holder.status.text = "✓ ${job.message}"
                holder.progress.isIndeterminate = false
                holder.progress.progress = 100
            }
            JobPhase.STOPPED -> {
                holder.status.text = ctx.getString(R.string.dl_status_stopped, job.message)
                holder.progress.isIndeterminate = false
                holder.progress.progress = 0
            }
            JobPhase.ERROR -> {
                holder.status.text = ctx.getString(R.string.dl_status_error, job.error ?: "unknown")
                holder.progress.isIndeterminate = false
                holder.progress.progress = 0
            }
        }

        when (job.phase) {
            JobPhase.SERVER -> holder.action.bindAction(R.drawable.ic_close, R.string.dl_stop) { onStop(job) }
            JobPhase.STOPPED, JobPhase.ERROR ->
                holder.action.bindAction(R.drawable.ic_replay, R.string.dl_retry) { onRetry(job) }
            else -> holder.action.visibility = View.GONE
        }
    }

    private fun ImageButton.bindAction(icon: Int, label: Int, onClick: () -> Unit) {
        visibility = View.VISIBLE
        setImageResource(icon)
        contentDescription = context.getString(label)
        setOnClickListener { onClick() }
    }
}
