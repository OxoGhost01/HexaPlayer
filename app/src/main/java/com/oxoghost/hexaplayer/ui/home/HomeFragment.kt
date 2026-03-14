package com.oxoghost.hexaplayer.ui.home

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AnimationUtils
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.PopupMenu
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.oxoghost.hexaplayer.R
import com.oxoghost.hexaplayer.adapter.AlbumAdapter
import com.oxoghost.hexaplayer.adapter.ArtistAdapter
import com.oxoghost.hexaplayer.adapter.FolderAdapter
import com.oxoghost.hexaplayer.adapter.PlaylistAdapter
import com.oxoghost.hexaplayer.adapter.SongAdapter
import com.oxoghost.hexaplayer.data.Album
import com.oxoghost.hexaplayer.data.Artist
import com.oxoghost.hexaplayer.data.Playlist
import com.oxoghost.hexaplayer.data.Song
import com.oxoghost.hexaplayer.databinding.DialogCreatePlaylistBinding
import com.oxoghost.hexaplayer.databinding.DialogEditSongBinding
import com.oxoghost.hexaplayer.databinding.FragmentHomeBinding
import com.oxoghost.hexaplayer.util.themeColor
import com.oxoghost.hexaplayer.viewmodel.MusicViewModel
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import coil.load

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MusicViewModel by activityViewModels()

    sealed class DetailEntry {
        data class ArtistDetail(val artist: Artist) : DetailEntry()
        data class AlbumDetail(val album: Album) : DetailEntry()
        data class PlaylistDetail(val playlist: Playlist) : DetailEntry()
    }

    private var currentDetail: DetailEntry? = null

    enum class SortMode { NAME_ASC, NAME_DESC, DATE_DESC, DATE_ASC }

    private var searchQuery = ""
    private var sortMode = SortMode.NAME_ASC
    private var isSearchVisible = false

    private var currentDisplayedSongs: List<Song> = emptyList()

    companion object {
        const val FILTER_ALL = 0
        const val FILTER_ALBUMS = 1
        const val FILTER_ARTISTS = 2
        const val FILTER_PLAYLISTS = 3
        const val FILTER_FOLDERS = 4
    }

    private var currentFilter = FILTER_FOLDERS
    private var spinnerInitialized = false

    private val folderStack = ArrayDeque<Pair<String, String>>()

    private var pendingSongUri: Uri? = null
    private var pendingMetadataValues: ContentValues? = null

    private val writeRequestLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = pendingSongUri ?: return@registerForActivityResult
            val values = pendingMetadataValues ?: return@registerForActivityResult
            pendingSongUri = null
            pendingMetadataValues = null
            try {
                requireContext().contentResolver.update(uri, values, null, null)
                viewModel.loadLibrary()
                Snackbar.make(requireView(), getString(R.string.song_info_updated), Snackbar.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Snackbar.make(requireView(), getString(R.string.metadata_permission_needed), Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private val songAdapter = SongAdapter(
        onSongClick = { song ->
            val albums = viewModel.albums.value ?: emptyList()
            val album = albums.firstOrNull { a -> a.songs.any { it.id == song.id } }
            if (album != null) {
                val idx = album.songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                viewModel.playQueue(album.songs, idx)
            } else {
                val songs = viewModel.songs.value ?: return@SongAdapter
                viewModel.playQueue(songs, songs.indexOf(song))
            }
        },
        onMoreClick = { song, anchor -> showSongMenu(song, anchor) }
    )

    private val detailSongAdapter = SongAdapter(
        onSongClick = { song -> playFromCurrentDetail(song) },
        onMoreClick = { song, anchor -> showSongMenu(song, anchor) }
    )

    private val albumAdapter = AlbumAdapter { album ->
        enterDetail(DetailEntry.AlbumDetail(album))
    }

    private val artistAdapter = ArtistAdapter { artist ->
        enterDetail(DetailEntry.ArtistDetail(artist))
    }

    private val playlistAdapter = PlaylistAdapter(
        onClick = { playlist -> enterDetail(DetailEntry.PlaylistDetail(playlist)) },
        onMore = { playlist, anchor -> showPlaylistMenu(playlist, anchor) }
    )

    private val folderAdapter = FolderAdapter(
        onFolderClick = { path, name -> navigateIntoFolder(path, name) },
        onSongClick = { song ->
            viewModel.playQueue(currentDisplayedSongs, currentDisplayedSongs.indexOf(song))
        },
        onSongMore = { song, anchor -> showSongMenu(song, anchor) }
    )

    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            when {
                isSearchVisible -> hideSearch()
                currentDetail != null -> exitDetail()
                folderStack.isNotEmpty() -> {
                    folderStack.removeLast()
                    updateFolderDisplay()
                    updateBackCallback()
                }
            }
        }
    }

    private var pendingCoverCallback: ((Uri) -> Unit)? = null
    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) { }
            pendingCoverCallback?.invoke(uri)
            pendingCoverCallback = null
        }
    }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, backCallback)
        setupSpinner()
        setupRecyclerView()
        setupSearch()
        setupSort()
        setupBreadcrumb()
        setupPlayControls()
        setupSwipeToPlayNext()
        observeViewModel()
    }


    private fun setupSpinner() {
        val filters = listOf(
            getString(R.string.filter_all_songs),
            getString(R.string.filter_albums),
            getString(R.string.filter_artists),
            getString(R.string.filter_playlists),
            getString(R.string.filter_folders)
        )
        val adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, filters
        ).also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }

        binding.spinnerFilter.adapter = adapter
        binding.spinnerFilter.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>, v: View?, pos: Int, id: Long
                ) {
                    if (!spinnerInitialized) { spinnerInitialized = true; return }
                    if (pos == currentFilter) return
                    currentFilter = pos
                    currentDetail = null
                    folderStack.clear()
                    hideSearchUI()
                    sortMode = SortMode.NAME_ASC
                    updateDisplay()
                    updateBackCallback()
                }
                override fun onNothingSelected(parent: AdapterView<*>) {}
            }
        binding.spinnerFilter.setSelection(currentFilter)
    }

    private fun setupRecyclerView() {
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupSwipeToPlayNext() {
        val accentColor = requireContext().themeColor(com.google.android.material.R.attr.colorPrimary)

        val callback = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.RIGHT) {
            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, t: RecyclerView.ViewHolder) = false

            override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                val adapter = recyclerView.adapter
                return when {
                    adapter === songAdapter || adapter === detailSongAdapter -> ItemTouchHelper.RIGHT
                    adapter === folderAdapter &&
                        folderAdapter.getItemViewType(viewHolder.adapterPosition) == FolderAdapter.TYPE_SONG -> ItemTouchHelper.RIGHT
                    else -> 0
                }
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.adapterPosition
                val song: Song? = when (binding.recyclerView.adapter) {
                    songAdapter -> songAdapter.currentList.getOrNull(pos)
                    detailSongAdapter -> detailSongAdapter.currentList.getOrNull(pos)
                    folderAdapter -> folderAdapter.getSongAt(pos)
                    else -> null
                }
                binding.recyclerView.adapter?.notifyItemChanged(pos)
                if (song != null) {
                    viewModel.addToQueueLast(song)
                    Snackbar.make(requireView(), getString(R.string.added_to_queue), Snackbar.LENGTH_SHORT).show()
                }
            }

            override fun onChildDraw(
                c: Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder,
                dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX > 0) {
                    val itemView = viewHolder.itemView
                    val paint = Paint().apply { color = accentColor; alpha = (dX / itemView.width * 160).toInt().coerceIn(0, 160) }
                    c.drawRect(itemView.left.toFloat(), itemView.top.toFloat(),
                        itemView.left + dX, itemView.bottom.toFloat(), paint)
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX * 0.4f, dY, actionState, isCurrentlyActive)
            }
        }
        ItemTouchHelper(callback).attachToRecyclerView(binding.recyclerView)
    }

    private fun setupSearch() {
        binding.btnSearch.setOnClickListener {
            isSearchVisible = true
            binding.rowSearch.visibility = View.VISIBLE
            binding.btnSearch.visibility = View.GONE
            binding.etSearch.requestFocus()
            val imm = requireContext().getSystemService(InputMethodManager::class.java)
            imm.showSoftInput(binding.etSearch, InputMethodManager.SHOW_IMPLICIT)
            updateBackCallback()
        }
        binding.btnClearSearch.setOnClickListener { hideSearch() }
        binding.etSearch.addTextChangedListener { text ->
            searchQuery = text?.toString() ?: ""
            updateDisplay()
        }
    }

    private fun setupSort() {
        binding.btnSort.setOnClickListener {
            sortMode = nextSortMode()
            updateSortButton()
            updateDisplay()
        }
    }

    private fun setupBreadcrumb() {
        binding.btnFolderBack.setOnClickListener {
            when {
                currentDetail != null -> exitDetail()
                folderStack.isNotEmpty() -> {
                    folderStack.removeLast()
                    updateFolderDisplay()
                    updateBackCallback()
                }
            }
        }
        binding.btnAddPlaylist.setOnClickListener {
            val detail = currentDetail
            if (detail is DetailEntry.PlaylistDetail) {
                showAddSongsDialog(detail.playlist)
            } else {
                showCreatePlaylistDialog()
            }
        }
    }

    private fun setupPlayControls() {
        binding.btnPlayAll.setOnClickListener {
            if (currentDisplayedSongs.isNotEmpty()) {
                viewModel.playQueue(currentDisplayedSongs, 0)
            }
        }
        binding.btnShuffleAll.setOnClickListener {
            if (currentDisplayedSongs.isNotEmpty()) {
                viewModel.playQueueShuffled(currentDisplayedSongs)
            }
        }
    }

    private fun observeViewModel() {
        viewModel.songs.observe(viewLifecycleOwner) { updateDisplay() }
        viewModel.albums.observe(viewLifecycleOwner) {
            if (currentFilter == FILTER_ALBUMS && currentDetail == null) updateDisplay()
        }
        viewModel.artists.observe(viewLifecycleOwner) {
            if (currentFilter == FILTER_ARTISTS && currentDetail == null) updateDisplay()
        }
        viewModel.playlists.observe(viewLifecycleOwner) { updateDisplay() }
    }


    private fun enterDetail(detail: DetailEntry) {
        currentDetail = detail
        sortMode = SortMode.NAME_ASC
        hideSearchUI()
        updateDisplay()
        updateBackCallback()
    }

    private fun exitDetail() {
        currentDetail = null
        sortMode = SortMode.NAME_ASC
        hideSearchUI()
        updateDisplay()
        updateBackCallback()
    }

    private fun navigateIntoFolder(path: String, displayName: String) {
        folderStack.addLast(path to displayName)
        updateFolderDisplay()
        updateBackCallback()
    }

    private fun updateBackCallback() {
        backCallback.isEnabled =
            isSearchVisible || currentDetail != null || folderStack.isNotEmpty()
    }


    private fun updateDisplay() {
        updateBackCallback()
        val detail = currentDetail
        if (detail != null) {
            showDetailView(detail)
            return
        }
        when (currentFilter) {
            FILTER_ALL -> showAllSongs()
            FILTER_ALBUMS -> showAlbums()
            FILTER_ARTISTS -> showArtists()
            FILTER_PLAYLISTS -> showPlaylists()
            FILTER_FOLDERS -> showFolders()
        }
    }

    private fun showDetailView(detail: DetailEntry) {
        when (detail) {
            is DetailEntry.ArtistDetail -> {
                val songs = detail.artist.songs
                    .filter { it.matchesSearch(searchQuery) }
                    .applySongSort(sortMode)
                setDetailHeader(detail.artist.name, addButton = false)
                submitSongs(detailSongAdapter, songs)
                setSearchSortVisible(search = true, sort = true, playlistSort = false)
            }
            is DetailEntry.AlbumDetail -> {
                setDetailHeader(detail.album.title, addButton = false)
                submitSongs(detailSongAdapter, detail.album.songs)
                setSearchSortVisible(search = false, sort = false, playlistSort = false)
            }
            is DetailEntry.PlaylistDetail -> {
                val updated = viewModel.playlists.value
                    ?.firstOrNull { it.id == detail.playlist.id } ?: detail.playlist
                val songs = viewModel.songsForPlaylist(updated)
                    .filter { it.matchesSearch(searchQuery) }
                    .applySongSort(sortMode)
                setDetailHeader(updated.name, addButton = true)
                submitSongs(detailSongAdapter, songs)
                setSearchSortVisible(search = true, sort = true, playlistSort = false)
            }
        }
    }

    private fun setDetailHeader(title: String, addButton: Boolean) {
        binding.folderBreadcrumb.visibility = View.VISIBLE
        binding.tvCurrentFolder.text = title
        binding.btnAddPlaylist.visibility = if (addButton) View.VISIBLE else View.GONE
    }

    private fun showAllSongs() {
        val songs = (viewModel.songs.value ?: emptyList())
            .filter { it.matchesSearch(searchQuery) }
            .applySongSort(sortMode)
        binding.folderBreadcrumb.visibility = View.GONE
        binding.btnAddPlaylist.visibility = View.GONE
        submitSongs(songAdapter, songs)
        setSearchSortVisible(search = true, sort = true, playlistSort = false)
    }

    private fun showAlbums() {
        val albums = (viewModel.albums.value ?: emptyList())
            .filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true)
            }
            .let { list ->
                when (sortMode) {
                    SortMode.NAME_ASC -> list.sortedBy { it.title }
                    SortMode.NAME_DESC -> list.sortedByDescending { it.title }
                    else -> list
                }
            }
        if (binding.recyclerView.adapter !== albumAdapter) {
            binding.recyclerView.adapter = albumAdapter
            binding.recyclerView.layoutAnimation =
                AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_anim_items)
        }
        albumAdapter.submitList(albums)
        setPlayControlsHeader(emptyList())
        showEmpty(albums.isEmpty())
        binding.folderBreadcrumb.visibility = View.GONE
        binding.btnAddPlaylist.visibility = View.GONE
        setSearchSortVisible(search = true, sort = true, playlistSort = false)
    }

    private fun showArtists() {
        val artists = (viewModel.artists.value ?: emptyList())
            .filter { it.name.contains(searchQuery, ignoreCase = true) }
            .let { list ->
                when (sortMode) {
                    SortMode.NAME_ASC -> list.sortedBy { it.name }
                    SortMode.NAME_DESC -> list.sortedByDescending { it.name }
                    else -> list
                }
            }
        if (binding.recyclerView.adapter !== artistAdapter) {
            binding.recyclerView.adapter = artistAdapter
            binding.recyclerView.layoutAnimation =
                AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_anim_items)
        }
        artistAdapter.submitList(artists)
        setPlayControlsHeader(emptyList())
        showEmpty(artists.isEmpty())
        binding.folderBreadcrumb.visibility = View.GONE
        binding.btnAddPlaylist.visibility = View.GONE
        setSearchSortVisible(search = true, sort = true, playlistSort = false)
    }

    private fun showPlaylists() {
        val playlists = (viewModel.playlists.value ?: emptyList())
            .filter { it.name.contains(searchQuery, ignoreCase = true) }
            .let { list ->
                when (sortMode) {
                    SortMode.NAME_ASC -> list.sortedBy { it.name }
                    SortMode.NAME_DESC -> list.sortedByDescending { it.name }
                    SortMode.DATE_DESC -> list.sortedByDescending { it.createdAt }
                    SortMode.DATE_ASC -> list.sortedBy { it.createdAt }
                }
            }
        if (binding.recyclerView.adapter !== playlistAdapter) {
            binding.recyclerView.adapter = playlistAdapter
            binding.recyclerView.layoutAnimation =
                AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_anim_items)
        }
        playlistAdapter.submitList(playlists)
        setPlayControlsHeader(emptyList())
        showEmpty(false)
        binding.folderBreadcrumb.visibility = View.GONE
        binding.btnAddPlaylist.visibility = View.VISIBLE
        setSearchSortVisible(search = true, sort = true, playlistSort = true)
    }

    private fun showFolders() {
        if (binding.recyclerView.adapter !== folderAdapter) {
            binding.recyclerView.adapter = folderAdapter
            binding.recyclerView.layoutAnimation =
                AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_anim_items)
        }
        updateFolderDisplay()
        binding.btnAddPlaylist.visibility = View.GONE
        setSearchSortVisible(search = true, sort = true, playlistSort = false)
    }

    private fun submitSongs(adapter: SongAdapter, songs: List<Song>) {
        if (binding.recyclerView.adapter !== adapter) {
            binding.recyclerView.adapter = adapter
            binding.recyclerView.layoutAnimation =
                AnimationUtils.loadLayoutAnimation(requireContext(), R.anim.layout_anim_items)
        }
        adapter.submitList(songs)
        setPlayControlsHeader(songs)
        showEmpty(songs.isEmpty())
    }

    private fun setPlayControlsHeader(songs: List<Song>) {
        currentDisplayedSongs = songs
        val show = songs.isNotEmpty()
        binding.headerPlayControls.visibility = if (show) View.VISIBLE else View.GONE
        binding.dividerPlayControls.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            binding.tvItemsCount.text = "${songs.size} songs"
        }
    }

    private fun setSearchSortVisible(search: Boolean, sort: Boolean, playlistSort: Boolean) {
        binding.btnSearch.visibility =
            if (search && !isSearchVisible) View.VISIBLE else View.GONE
        binding.btnSort.visibility = if (sort) View.VISIBLE else View.GONE
        if (sort) updateSortButton()
        if (!playlistSort && (sortMode == SortMode.DATE_ASC || sortMode == SortMode.DATE_DESC)) {
            sortMode = SortMode.NAME_ASC
            updateSortButton()
        }
    }

    private fun updateFolderDisplay() {
        val songs = viewModel.songs.value ?: emptyList()
        val baseFolder = viewModel.musicFolder.trim().let { f ->
            when {
                f.isBlank() -> ""
                f.endsWith("/") -> f
                else -> "$f/"
            }
        }
        val currentPath = folderStack.lastOrNull()?.first ?: baseFolder
        val (subfolders, directSongs) =
            viewModel.musicRepository.getFolderContents(songs, currentPath)

        val filteredFolders = subfolders.filter { folderPath ->
            val name = folderPath.removeSuffix("/").substringAfterLast('/')
            searchQuery.isBlank() || name.contains(searchQuery, ignoreCase = true)
        }
        val filteredSongs = directSongs.filter { it.matchesSearch(searchQuery) }
            .let { list ->
                when (sortMode) {
                    SortMode.NAME_ASC -> list.sortedBy { it.title }
                    SortMode.NAME_DESC -> list.sortedByDescending { it.title }
                    else -> list
                }
            }

        val folderItems = filteredFolders.map { folderPath ->
            val name = folderPath.removeSuffix("/").substringAfterLast('/')
            val (subSubs, subSongs) =
                viewModel.musicRepository.getFolderContents(songs, folderPath)
            FolderAdapter.Item.FolderItem(folderPath, name, subSubs.size + subSongs.size)
        }
        val songItems = filteredSongs.map { FolderAdapter.Item.SongItem(it) }
        val allItems: List<FolderAdapter.Item> = folderItems + songItems
        folderAdapter.submitItems(allItems)

        currentDisplayedSongs = filteredSongs
        val hasSongs = filteredSongs.isNotEmpty()
        binding.headerPlayControls.visibility = if (hasSongs) View.VISIBLE else View.GONE
        binding.dividerPlayControls.visibility = if (hasSongs) View.VISIBLE else View.GONE
        if (hasSongs) binding.tvItemsCount.text = "${filteredSongs.size} songs"

        showEmpty(allItems.isEmpty())

        if (folderStack.isEmpty()) {
            binding.folderBreadcrumb.visibility = View.GONE
        } else {
            binding.folderBreadcrumb.visibility = View.VISIBLE
            binding.tvCurrentFolder.text = folderStack.joinToString(" / ") { it.second }
        }
    }

    private fun playFromCurrentDetail(song: Song) {
        when (val detail = currentDetail) {
            is DetailEntry.ArtistDetail -> {
                val songs = detail.artist.songs
                viewModel.playQueue(songs, songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0))
            }
            is DetailEntry.AlbumDetail -> {
                val songs = detail.album.songs
                viewModel.playQueue(songs, songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0))
            }
            is DetailEntry.PlaylistDetail -> {
                val pl = viewModel.playlists.value
                    ?.firstOrNull { it.id == detail.playlist.id } ?: detail.playlist
                val songs = viewModel.songsForPlaylist(pl)
                viewModel.playQueue(songs, songs.indexOfFirst { it.id == song.id }.coerceAtLeast(0))
            }
            null -> {}
        }
    }


    private fun nextSortMode(): SortMode {
        val isPlaylistCtx = currentFilter == FILTER_PLAYLISTS && currentDetail == null
        return when (sortMode) {
            SortMode.NAME_ASC -> SortMode.NAME_DESC
            SortMode.NAME_DESC -> if (isPlaylistCtx) SortMode.DATE_DESC else SortMode.NAME_ASC
            SortMode.DATE_DESC -> SortMode.DATE_ASC
            SortMode.DATE_ASC -> SortMode.NAME_ASC
        }
    }

    private fun updateSortButton() {
        binding.btnSort.text = when (sortMode) {
            SortMode.NAME_ASC -> getString(R.string.sort_name_asc)
            SortMode.NAME_DESC -> getString(R.string.sort_name_desc)
            SortMode.DATE_DESC -> getString(R.string.sort_date_desc)
            SortMode.DATE_ASC -> getString(R.string.sort_date_asc)
        }
    }


    private fun hideSearch() {
        hideSearchUI()
        updateDisplay()
        updateBackCallback()
    }

    private fun hideSearchUI() {
        isSearchVisible = false
        searchQuery = ""
        binding.rowSearch.visibility = View.GONE
        binding.etSearch.setText("")
        val imm = requireContext().getSystemService(InputMethodManager::class.java)
        imm.hideSoftInputFromWindow(binding.etSearch.windowToken, 0)
        val detail = currentDetail
        val showSearch = when {
            detail is DetailEntry.AlbumDetail -> false
            else -> true
        }
        binding.btnSearch.visibility = if (showSearch) View.VISIBLE else View.GONE
    }


    private fun showEmpty(empty: Boolean) {
        binding.recyclerView.visibility = if (empty) View.GONE else View.VISIBLE
        binding.tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
    }

    private fun Song.matchesSearch(query: String): Boolean {
        if (query.isBlank()) return true
        return title.contains(query, ignoreCase = true) ||
               artist.contains(query, ignoreCase = true)
    }

    private fun List<Song>.applySongSort(mode: SortMode): List<Song> = when (mode) {
        SortMode.NAME_ASC -> sortedBy { it.title }
        SortMode.NAME_DESC -> sortedByDescending { it.title }
        else -> this
    }


    private fun pickPlaylistCover(onPicked: (Uri) -> Unit) {
        pendingCoverCallback = onPicked
        imagePicker.launch(arrayOf("image/*"))
    }


    private fun showSongMenu(song: Song, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 0, 0, getString(R.string.add_to_playlist))
        popup.menu.add(0, 1, 1, getString(R.string.play_next))
        popup.menu.add(0, 2, 2, getString(R.string.play_last))
        popup.menu.add(0, 3, 3, getString(R.string.edit_song_info))
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                0 -> showAddToPlaylistDialog(song)
                1 -> {
                    viewModel.addToQueueNext(song)
                    Snackbar.make(requireView(), getString(R.string.plays_next), Snackbar.LENGTH_SHORT).show()
                }
                2 -> {
                    viewModel.addToQueueLast(song)
                    Snackbar.make(requireView(), getString(R.string.added_to_queue), Snackbar.LENGTH_SHORT).show()
                }
                3 -> showEditSongDialog(song)
            }
            true
        }
        popup.show()
    }

    private fun showEditSongDialog(song: Song) {
        val dialogView = DialogEditSongBinding.inflate(LayoutInflater.from(requireContext()))
        dialogView.etTitle.setText(song.title)
        dialogView.etArtist.setText(song.artist)
        dialogView.etAlbum.setText(song.album)

        dialogView.ivSongCover.load(SongAdapter.artUri(song)) {
            placeholder(R.drawable.ic_music_note)
            error(R.drawable.ic_music_note)
        }
        if (song.customCoverUri != null) {
            dialogView.btnClearCover.visibility = View.VISIBLE
        }

        var selectedCoverUri: Uri? = null

        dialogView.btnPickCover.setOnClickListener {
            pendingCoverCallback = { uri ->
                selectedCoverUri = uri
                dialogView.ivSongCover.load(uri) {
                    placeholder(R.drawable.ic_music_note)
                    error(R.drawable.ic_music_note)
                }
                dialogView.btnClearCover.visibility = View.VISIBLE
            }
            imagePicker.launch(arrayOf("image/*"))
        }

        dialogView.btnClearCover.setOnClickListener {
            selectedCoverUri = null
            viewModel.clearCustomCover(song.id)
            dialogView.ivSongCover.load(SongAdapter.albumArtUri(song.albumId)) {
                placeholder(R.drawable.ic_music_note)
                error(R.drawable.ic_music_note)
            }
            dialogView.btnClearCover.visibility = View.GONE
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.edit_song_dialog_title))
            .setView(dialogView.root)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val title = dialogView.etTitle.text?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: song.title
                val artist = dialogView.etArtist.text?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: song.artist
                val album = dialogView.etAlbum.text?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: song.album
                if (selectedCoverUri != null) {
                    viewModel.setCustomCover(song.id, selectedCoverUri.toString())
                }
                doUpdateSongMetadata(song, title, artist, album)
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun doUpdateSongMetadata(song: Song, title: String, artist: String, album: String) {
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.ARTIST, artist)
            put(MediaStore.Audio.Media.ALBUM, album)
        }
        try {
            requireContext().contentResolver.update(song.uri, values, null, null)
            viewModel.loadLibrary()
            Snackbar.make(requireView(), getString(R.string.song_info_updated), Snackbar.LENGTH_SHORT).show()
        } catch (e: SecurityException) {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    @Suppress("NewApi")
                    val req = MediaStore.createWriteRequest(
                        requireContext().contentResolver, listOf(song.uri)
                    )
                    pendingMetadataValues = values
                    pendingSongUri = song.uri
                    writeRequestLauncher.launch(
                        IntentSenderRequest.Builder(req.intentSender).build()
                    )
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> {
                    @Suppress("NewApi")
                    val sender = (e as? android.app.RecoverableSecurityException)
                        ?.userAction?.actionIntent?.intentSender
                    if (sender != null) {
                        pendingMetadataValues = values
                        pendingSongUri = song.uri
                        writeRequestLauncher.launch(IntentSenderRequest.Builder(sender).build())
                    } else {
                        Snackbar.make(requireView(), getString(R.string.metadata_permission_needed), Snackbar.LENGTH_SHORT).show()
                    }
                }
                else -> Snackbar.make(requireView(), getString(R.string.metadata_permission_needed), Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun showAddToPlaylistDialog(song: Song) {
        val playlists = viewModel.playlists.value ?: emptyList()
        val names = (listOf(getString(R.string.new_playlist)) + playlists.map { it.name })
            .toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.add_to_playlist))
            .setItems(names) { _, which ->
                if (which == 0) {
                    showCreatePlaylistDialog { playlist ->
                        viewModel.addSongToPlaylist(playlist.id, song.id)
                    }
                } else {
                    viewModel.addSongToPlaylist(playlists[which - 1].id, song.id)
                    Snackbar.make(
                        requireView(), getString(R.string.song_added), Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
            .show()
    }

    private fun showCreatePlaylistDialog(onCreated: ((Playlist) -> Unit)? = null) {
        val dialogView = DialogCreatePlaylistBinding.inflate(LayoutInflater.from(requireContext()))
        var selectedCoverUri: Uri? = null

        dialogView.btnPickImage.setOnClickListener {
            pickPlaylistCover { uri ->
                selectedCoverUri = uri
                dialogView.ivPlaylistCover.load(uri) {
                    crossfade(true)
                    placeholder(R.drawable.ic_playlist)
                    error(R.drawable.ic_playlist)
                }
                dialogView.ivPlaylistCover.setPadding(0, 0, 0, 0)
                dialogView.ivPlaylistCover.clearColorFilter()
                dialogView.ivPlaylistCover.scaleType =
                    android.widget.ImageView.ScaleType.CENTER_CROP
                dialogView.btnPickImage.text = getString(R.string.change_cover)
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.create_playlist))
            .setView(dialogView.root)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val name = dialogView.etName.text?.toString()?.trim() ?: ""
                if (name.isNotEmpty()) {
                    val playlist = viewModel.createPlaylist(name, selectedCoverUri?.toString())
                    onCreated?.invoke(playlist)
                    Snackbar.make(
                        requireView(), getString(R.string.playlist_created), Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showEditPlaylistDialog(playlist: Playlist) {
        val dialogView = DialogCreatePlaylistBinding.inflate(LayoutInflater.from(requireContext()))
        var selectedCoverUri: Uri? = null

        dialogView.etName.setText(playlist.name)

        if (playlist.coverUri != null) {
            dialogView.ivPlaylistCover.load(playlist.coverUri) {
                crossfade(true)
                placeholder(R.drawable.ic_playlist)
            }
            dialogView.ivPlaylistCover.setPadding(0, 0, 0, 0)
            dialogView.ivPlaylistCover.clearColorFilter()
            dialogView.ivPlaylistCover.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
            dialogView.btnPickImage.text = getString(R.string.change_cover)
        }

        dialogView.btnPickImage.setOnClickListener {
            pickPlaylistCover { uri ->
                selectedCoverUri = uri
                dialogView.ivPlaylistCover.load(uri) {
                    crossfade(true)
                    placeholder(R.drawable.ic_playlist)
                }
                dialogView.ivPlaylistCover.setPadding(0, 0, 0, 0)
                dialogView.ivPlaylistCover.clearColorFilter()
                dialogView.ivPlaylistCover.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
                dialogView.btnPickImage.text = getString(R.string.change_cover)
            }
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.edit_playlist))
            .setView(dialogView.root)
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val name = dialogView.etName.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
                viewModel.editPlaylist(
                    playlistId = playlist.id,
                    newName = name,
                    newCoverUri = selectedCoverUri?.toString()
                )
                Snackbar.make(requireView(), getString(R.string.playlist_updated), Snackbar.LENGTH_SHORT).show()
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showAddSongsDialog(playlist: Playlist) {
        val allSongs = viewModel.songs.value ?: emptyList()
        val currentIds = playlist.songIds.toSet()
        val available = allSongs.filter { it.id !in currentIds }
        if (available.isEmpty()) {
            Snackbar.make(
                requireView(), getString(R.string.no_songs_to_add), Snackbar.LENGTH_SHORT
            ).show()
            return
        }
        val names = available.map { "${it.title}  ·  ${it.artist}" }.toTypedArray()
        val checked = BooleanArray(available.size) { false }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.add_songs))
            .setMultiChoiceItems(names, checked) { _, which, isChecked ->
                checked[which] = isChecked
            }
            .setPositiveButton(getString(R.string.ok)) { _, _ ->
                val selected = available.filterIndexed { i, _ -> checked[i] }
                selected.forEach { viewModel.addSongToPlaylist(playlist.id, it.id) }
                if (selected.isNotEmpty()) {
                    val updated = viewModel.playlists.value
                        ?.firstOrNull { it.id == playlist.id }
                    if (updated != null && currentDetail is DetailEntry.PlaylistDetail) {
                        currentDetail = DetailEntry.PlaylistDetail(updated)
                    }
                    Snackbar.make(
                        requireView(), getString(R.string.songs_added), Snackbar.LENGTH_SHORT
                    ).show()
                    updateDisplay()
                }
            }
            .setNegativeButton(getString(R.string.cancel), null)
            .show()
    }

    private fun showPlaylistMenu(playlist: Playlist, anchor: View) {
        val popup = PopupMenu(requireContext(), anchor)
        popup.menu.add(0, 0, 0, getString(R.string.edit_playlist))
        popup.menu.add(0, 1, 1, getString(R.string.delete_playlist))
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                0 -> showEditPlaylistDialog(playlist)
                1 -> {
                    viewModel.deletePlaylist(playlist.id)
                    Snackbar.make(
                        requireView(), getString(R.string.playlist_deleted), Snackbar.LENGTH_SHORT
                    ).show()
                }
            }
            true
        }
        popup.show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
