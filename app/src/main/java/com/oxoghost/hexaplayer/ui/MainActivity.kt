package com.oxoghost.hexaplayer.ui

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import com.oxoghost.hexaplayer.R
import com.oxoghost.hexaplayer.adapter.SongAdapter
import com.oxoghost.hexaplayer.databinding.ActivityMainBinding
import com.oxoghost.hexaplayer.ui.download.DownloadFragment
import com.oxoghost.hexaplayer.ui.equalizer.EqualizerFragment
import com.oxoghost.hexaplayer.ui.home.HomeFragment
import com.oxoghost.hexaplayer.ui.player.PlayerFragment
import com.oxoghost.hexaplayer.ui.settings.SettingsFragment
import com.oxoghost.hexaplayer.util.themeColor
import com.oxoghost.hexaplayer.viewmodel.MusicViewModel
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MusicViewModel by viewModels()

    private var homeFragment: Fragment = HomeFragment()
    private var downloadFragment: Fragment = DownloadFragment()
    private var equalizerFragment: Fragment = EqualizerFragment()
    private var settingsFragment: Fragment = SettingsFragment()
    private var activeFragment: Fragment = homeFragment

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        if (grants.values.any { it }) onPermissionGranted()
        else showPermissionView(true)
    }

    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* notification is optional */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyAccentTheme()
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            binding.fragmentContainer.updatePadding(top = bars.top)
            binding.bottomNav.updatePadding(bottom = bars.bottom)
            insets
        }

        if (savedInstanceState == null) {
            setupFragments()
        } else {
            homeFragment = supportFragmentManager.findFragmentByTag("home") ?: homeFragment
            downloadFragment = supportFragmentManager.findFragmentByTag("download") ?: downloadFragment
            equalizerFragment = supportFragmentManager.findFragmentByTag("equalizer") ?: equalizerFragment
            settingsFragment = supportFragmentManager.findFragmentByTag("settings") ?: settingsFragment
            activeFragment = listOf(homeFragment, downloadFragment, equalizerFragment, settingsFragment)
                .firstOrNull { !it.isHidden } ?: homeFragment
        }
        setupBottomNav()
        setupPlayerBar()
        setupPermissionView()

        viewModel.connectToService()

        if (hasAudioPermission()) {
            onPermissionGranted()
        } else {
            showPermissionView(true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun applyAccentTheme() {
        val prefs = getSharedPreferences("hexa_prefs", MODE_PRIVATE)
        val themeRes = when (prefs.getInt("accent_index", 0)) {
            1 -> R.style.Theme_HexaPlayer_DeepPurple
            2 -> R.style.Theme_HexaPlayer_Blue
            3 -> R.style.Theme_HexaPlayer_Teal
            4 -> R.style.Theme_HexaPlayer_Pink
            5 -> R.style.Theme_HexaPlayer_Orange
            else -> R.style.Theme_HexaPlayer
        }
        setTheme(themeRes)
    }

    private fun setupFragments() {
        supportFragmentManager.beginTransaction()
            .add(R.id.fragment_container, homeFragment, "home")
            .add(R.id.fragment_container, downloadFragment, "download").hide(downloadFragment)
            .add(R.id.fragment_container, equalizerFragment, "equalizer").hide(equalizerFragment)
            .add(R.id.fragment_container, settingsFragment, "settings").hide(settingsFragment)
            .commit()
    }

    private fun setupBottomNav() {
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> switchTo(homeFragment)
                R.id.nav_download -> switchTo(downloadFragment)
                R.id.nav_equalizer -> switchTo(equalizerFragment)
                R.id.nav_settings -> switchTo(settingsFragment)
            }
            true
        }
        val primary = themeColor(com.google.android.material.R.attr.colorPrimary)
        val unselected = ContextCompat.getColor(this, R.color.colorTextSecondary)
        val navTint = ColorStateList(
            arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
            intArrayOf(primary, unselected)
        )
        binding.bottomNav.itemIconTintList = navTint
        binding.bottomNav.itemTextColor = navTint
    }

    private fun switchTo(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.fade_in, R.anim.fade_out)
            .hide(activeFragment)
            .show(fragment)
            .commit()
        activeFragment = fragment
    }

    private fun setupPlayerBar() {
        observePlayback()
        setupPlayerBarGestures()

        binding.playerBar.btnPlayPause.setOnClickListener { viewModel.togglePlayPause() }
        binding.playerBar.btnNext.setOnClickListener { viewModel.playNext() }
    }

    private fun observePlayback() {
        viewModel.currentSong.observe(this) { song ->
            if (song == null) {
                if (binding.playerBar.root.visibility == View.GONE) return@observe
                binding.playerBar.root.visibility = View.GONE
                resetPlayerBarBackground()
            } else {
                if (binding.playerBar.root.visibility != View.VISIBLE) {
                    binding.playerBar.root.visibility = View.VISIBLE
                }
                binding.playerBar.tvTitle.text = song.title
                binding.playerBar.tvArtist.text = song.artist
                loadPlayerBarArtWithPalette(song)
                val art = binding.playerBar.ivAlbumArt
                art.animate().scaleX(0.88f).scaleY(0.88f).setDuration(80).withEndAction {
                    art.animate().scaleX(1f).scaleY(1f).setDuration(160).start()
                }.start()
            }
        }

        viewModel.isPlaying.observe(this) { playing ->
            binding.playerBar.btnPlayPause.setImageResource(
                if (playing) R.drawable.ic_pause else R.drawable.ic_play_arrow
            )
        }

        viewModel.currentPosition.observe(this) { pos ->
            val duration = viewModel.duration.value ?: 0L
            if (duration > 0) {
                binding.playerBar.progressBar.progress = (pos * 1000 / duration).toInt()
            }
        }
    }

    private fun loadPlayerBarArtWithPalette(song: com.oxoghost.hexaplayer.data.Song) {
        val uri = SongAdapter.artUri(song)
        imageLoader.enqueue(
            ImageRequest.Builder(this)
                .data(uri)
                .allowHardware(false)
                .target(binding.playerBar.ivAlbumArt)
                .placeholder(R.drawable.ic_music_note)
                .error(R.drawable.ic_music_note)
                .listener(
                    onSuccess = { _, result ->
                        val bmp = (result.drawable as? BitmapDrawable)?.bitmap
                        if (bmp != null) applyPlayerBarPalette(bmp) else resetPlayerBarBackground()
                    },
                    onError = { _, _ -> resetPlayerBarBackground() }
                )
                .build()
        )
    }

    private fun applyPlayerBarPalette(bitmap: Bitmap) {
        Palette.from(bitmap).generate { palette ->
            val swatch = palette?.darkVibrantSwatch
                ?: palette?.darkMutedSwatch
                ?: palette?.dominantSwatch
            if (swatch != null) {
                val bgBase = ContextCompat.getColor(this, R.color.colorPlayerBar)
                val tinted = ColorUtils.blendARGB(swatch.rgb, bgBase, 0.40f)
                val gd = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadii = floatArrayOf(60f, 60f, 60f, 60f, 0f, 0f, 0f, 0f)
                    setColor(tinted)
                }
                binding.playerBar.root.background = gd
            } else {
                resetPlayerBarBackground()
            }
        }
    }

    private fun resetPlayerBarBackground() {
        binding.playerBar.root.setBackgroundResource(R.drawable.bg_player_bar)
    }

    private fun setupPlayerBarGestures() {
        val gestureDetector = GestureDetector(
            this,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onFling(
                    e1: MotionEvent?, e2: MotionEvent,
                    velocityX: Float, velocityY: Float
                ): Boolean {
                    if (e1 == null) return false
                    val deltaX = e2.x - e1.x
                    if (abs(deltaX) > 80 && abs(velocityX) > 100) {
                        animatePlayerBarSwipe(deltaX < 0) {
                            if (deltaX < 0) viewModel.playNext() else viewModel.playPrevious()
                        }
                        return true
                    }
                    return false
                }

                override fun onSingleTapUp(e: MotionEvent): Boolean {
                    if (viewModel.currentSong.value != null) openFullscreenPlayer()
                    return true
                }
            }
        )

        binding.playerBar.root.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            v.onTouchEvent(event)
            true
        }
    }

    private fun animatePlayerBarSwipe(toLeft: Boolean, onSwipe: () -> Unit) {
        val content = binding.playerBar.contentRow
        val dir = if (toLeft) -1f else 1f
        val offset = binding.playerBar.root.width.toFloat().coerceAtLeast(80f) * 0.35f

        content.animate()
            .translationX(dir * offset)
            .alpha(0f)
            .setDuration(130)
            .withEndAction {
                onSwipe()
                content.translationX = -dir * offset
                content.animate()
                    .translationX(0f)
                    .alpha(1f)
                    .setDuration(200)
                    .start()
            }
            .start()
    }

    private fun openFullscreenPlayer() {
        if (supportFragmentManager.findFragmentByTag("player") != null) return
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(R.anim.slide_up, R.anim.slide_down, R.anim.slide_up, R.anim.slide_down)
            .add(android.R.id.content, PlayerFragment(), "player")
            .addToBackStack("player")
            .commit()
    }

    private fun hasAudioPermission(): Boolean {
        val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_AUDIO
        else
            Manifest.permission.READ_EXTERNAL_STORAGE
        return ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAudioPermission() {
        val perms = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        else
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        permissionLauncher.launch(perms)
    }

    private fun onPermissionGranted() {
        showPermissionView(false)
        viewModel.loadLibrary()
    }

    private fun showPermissionView(show: Boolean) {
        binding.permissionView.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun setupPermissionView() {
        binding.btnGrantPermission.setOnClickListener { requestAudioPermission() }
    }
}
