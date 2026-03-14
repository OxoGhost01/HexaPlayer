package com.oxoghost.hexaplayer.ui.queue

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.oxoghost.hexaplayer.adapter.QueueAdapter
import com.oxoghost.hexaplayer.databinding.FragmentQueueBinding
import com.oxoghost.hexaplayer.viewmodel.MusicViewModel
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class QueueFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentQueueBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MusicViewModel by activityViewModels()

    private val queueAdapter = QueueAdapter(
        onItemClick = { exoIndex ->
            viewModel.playQueueItemAt(exoIndex)
            dismiss()
        },
        onRemove = { exoIndex ->
            viewModel.removeFromQueue(exoIndex)
        },
        onMove = { from, to ->
            viewModel.moveQueueItem(from, to)
        }
    )

    private val dragCallback = object : ItemTouchHelper.SimpleCallback(
        ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
    ) {
        private var dragFrom = -1
        private var dragTo   = -1

        override fun isLongPressDragEnabled() = false

        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            val item = queueAdapter.getItemAt(viewHolder.bindingAdapterPosition)
            val canDrag = when (item) {
                is QueueAdapter.Item.SongEntry ->
                    item.isUserQueue || queueAdapter.dragEnabled
                else -> false
            }
            return if (canDrag)
                makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
            else
                makeMovementFlags(0, 0)
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            val fromItem = queueAdapter.getItemAt(viewHolder.bindingAdapterPosition)
            val toItem   = queueAdapter.getItemAt(target.bindingAdapterPosition)
            if (fromItem !is QueueAdapter.Item.SongEntry ||
                toItem   !is QueueAdapter.Item.SongEntry) return false
            if (fromItem.isUserQueue != toItem.isUserQueue) return false

            val from = viewHolder.bindingAdapterPosition
            val to   = target.bindingAdapterPosition
            if (dragFrom == -1) dragFrom = from
            dragTo = to
            queueAdapter.moveItem(from, to)
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            if (dragFrom != -1 && dragTo != -1 && dragFrom != dragTo) {
                viewModel.moveQueueItem(dragFrom, dragTo)
            }
            dragFrom = -1
            dragTo   = -1
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQueueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val itemTouchHelper = ItemTouchHelper(dragCallback)
        itemTouchHelper.attachToRecyclerView(binding.rvQueue)
        queueAdapter.itemTouchHelper = itemTouchHelper

        binding.rvQueue.layoutManager = LinearLayoutManager(requireContext())
        binding.rvQueue.adapter = queueAdapter

        viewModel.shuffleMode.observe(viewLifecycleOwner) { shuffled ->
            queueAdapter.dragEnabled = !shuffled
            binding.tvShuffleNote.visibility = if (shuffled) View.VISIBLE else View.GONE
        }

        viewModel.currentQueue.observe(viewLifecycleOwner)       { rebuildDisplay() }
        viewModel.currentQueueIndex.observe(viewLifecycleOwner)   { rebuildDisplay() }
        viewModel.userQueueLive.observe(viewLifecycleOwner)       { rebuildDisplay() }
        viewModel.currentSong.observe(viewLifecycleOwner)         { rebuildDisplay() }
        viewModel.isPlayingFromUserQueue.observe(viewLifecycleOwner) { rebuildDisplay() }
    }

    private fun rebuildDisplay() {
        val ctxQueue        = viewModel.currentQueue.value ?: emptyList()
        val ctxIdx          = viewModel.currentQueueIndex.value ?: 0
        val uqAll           = viewModel.userQueueLive.value ?: emptyList()
        val playingFromUq   = viewModel.isPlayingFromUserQueue.value ?: false
        val currentSong     = viewModel.currentSong.value

        // When a uq item is currently playing, it appears at the top ("Now Playing").
        // Drop it from the "Next in queue" list so it doesn't appear twice.
        val uqUpcoming = if (playingFromUq && uqAll.isNotEmpty()) uqAll.drop(1) else uqAll

        val items = mutableListOf<QueueAdapter.Item>()

        // ── 1. Now Playing ────────────────────────────────────────────────
        if (currentSong != null) {
            items.add(QueueAdapter.Item.Header("Now Playing"))
            // exoIndex of the currently-playing item
            val exoCurrentIdx = if (playingFromUq) {
                ctxIdx + 1   // first uq slot (the playing uq item)
            } else {
                ctxIdx       // context item
            }
            items.add(
                QueueAdapter.Item.SongEntry(
                    song        = currentSong,
                    isUserQueue = playingFromUq,
                    exoIndex    = exoCurrentIdx,
                    isCurrent   = true
                )
            )
        }

        // ── 2. Next in queue (user queue, upcoming only) ──────────────────
        if (uqUpcoming.isNotEmpty()) {
            items.add(QueueAdapter.Item.Header("Next in queue"))
            uqUpcoming.forEachIndexed { i, song ->
                // If playing from uq: upcoming uq starts at slot ctxIdx+2 (slot ctxIdx+1 is playing)
                // If not playing from uq: upcoming uq starts at ctxIdx+1
                val uqOffset = if (playingFromUq) i + 2 else i + 1
                val exoIndex = ctxIdx + uqOffset
                items.add(
                    QueueAdapter.Item.SongEntry(
                        song        = song,
                        isUserQueue = true,
                        exoIndex    = exoIndex,
                        isCurrent   = false
                    )
                )
            }
            items.add(QueueAdapter.Item.Separator)
        }

        // ── 3. Remaining context queue ────────────────────────────────────
        val remainingCtx = ctxQueue.drop(ctxIdx + 1)
        if (remainingCtx.isNotEmpty()) {
            val uqCount = uqAll.size
            val label = if (uqAll.isNotEmpty()) "Next from context" else "Up next"
            items.add(QueueAdapter.Item.Header(label))
            remainingCtx.forEachIndexed { i, song ->
                val exoIndex = ctxIdx + 1 + uqCount + i
                items.add(
                    QueueAdapter.Item.SongEntry(
                        song        = song,
                        isUserQueue = false,
                        exoIndex    = exoIndex,
                        isCurrent   = false
                    )
                )
            }
        }

        queueAdapter.submitItems(items)

        val isEmpty = currentSong == null && uqAll.isEmpty() && ctxQueue.isEmpty()
        binding.tvEmptyQueue.visibility = if (isEmpty) View.VISIBLE else View.GONE
        binding.rvQueue.visibility      = if (isEmpty) View.GONE else View.VISIBLE

        val total = ctxQueue.size + uqAll.size
        binding.tvQueueTitle.text = if (total == 0) "Queue" else "Queue · $total songs"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
