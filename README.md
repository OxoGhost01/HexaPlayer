# HexaPlayer

A **free**, open-source music player for Android. (No ads, no tracking, no shit)

![GitHub Release](https://img.shields.io/github/v/release/oxoghost01/hexaplayer?style=for-the-badge)
![GitHub Repo stars](https://img.shields.io/github/stars/oxoghost01/hexaplayer?style=for-the-badge)
![GitHub Downloads (all assets, latest release)](https://img.shields.io/github/downloads/oxoghost01/hexaplayer/latest/total?style=for-the-badge)

HexaPlayer plays the music already on your phone, fast and without distractions. It also comes with an optional downloader to bring your Spotify playlists, albums and tracks onto your phone and keep them in sync, all locally.

## Features

### Two interfaces
- **Terminal** or **Material**: two complete interfaces with the same features, switched live in Settings > Appearance
- **Terminal flavors**: 7 color themes, plus your own made with the built-in color picker, and pixel-art covers
- **Material colors**: 7 palettes, your wallpaper colors (Android 12+) or the colors of the album playing
- **Pure black** background for AMOLED screens

### Your library
- **Browse** by Songs, Albums, Artists, Playlists or Folders, with sorting everywhere
- **Search** songs, albums, artists and the lyrics you've already opened
- **Album, artist and playlist pages** with play counts
- **Multi-select** (long press and drag): play next, queue, add to a playlist or edit tags together
- **Edit song info** (title, artist, album, album artist, year, track, disc, genre, cover): written into the file itself
- **Playlists** with custom covers, drag to reorder, add a whole album at once
- **Choose your library folder** to only show the music you care about

### Playback
- **Full-screen player** with seek bar, shuffle, repeat, lyrics, queue and sleep timer
- **Remembers your queue**: pick up where you left off, even after a restart
- **Smart queue**: playing a song queues the rest of its album; Play Next, Add to Queue, drag and drop to reorder
- **Lyrics**: synced (LRC) and plain lyrics from [LRCLIB](https://lrclib.net), with a per-song offset
- **Equalizer**: every band of your device, presets and your own presets, bass boost, virtualizer, loudness and reverb
- **Sleep timer**: 15 / 30 / 45 / 60 minutes or any length you type
- **Open with**: play any audio file from another app without touching your queue
- **Swipe the app away to stop the music**, unless Android Auto is using it

### Your stats
- **Recently played** and **most played** on Home, kept only on your phone
- **Wrapped**: your listening by week, month or year, any time. Share it as an image or save your top 50 as a playlist

### Download & Sync (optional)
- Paste a **Spotify link** (playlist, album or track) and the music lands in your library folder, named after the playlist or album
- **Sync** keeps a playlist up to date: new songs are added, removed ones are deleted. Synced playlists can be re-synced in one tap
- **Stop** any download, **run it again** later: songs already on your phone are skipped. Follow each song live while it downloads
- Runs [spotDL](https://github.com/spotDL/spotify-downloader) inside **Termux**, on your phone. No account, no server, nothing leaves your device
- HexaPlayer starts the downloader only when you need it, and it stops by itself when idle (or with the Stop button)
- A step by step **setup guide** is built into the app. Details in the [Termux guide](termux/README.md)

### Everywhere else
- **Android Auto**: browse Songs, Albums, Artists, Playlists and Folders, with voice search
- **Widgets**: 4×1 and 5×1 home screen widgets that follow your interface and colors, with a Resume button
- **In-app updates**: get notified and install new versions directly

## Screenshots

### Terminal

<p align="center">
  <img src="img/terminal/home.jpg" width="24%" alt="Home" />
  <img src="img/terminal/library.jpg" width="24%" alt="Library" />
  <img src="img/terminal/album.jpg" width="24%" alt="Album" />
  <img src="img/terminal/search.jpg" width="24%" alt="Search" />
</p>

<p align="center">
  <img src="img/terminal/player.jpg" width="24%" alt="Player" />
  <img src="img/terminal/lyrics.jpg" width="24%" alt="Lyrics" />
  <img src="img/terminal/queue.jpg" width="24%" alt="Queue" />
  <img src="img/terminal/wrapped.jpg" width="24%" alt="Wrapped" />
</p>

<p align="center">
  <img src="img/terminal/settings.jpg" width="24%" alt="Settings" />
  <img src="img/terminal/appearance.jpg" width="24%" alt="Appearance" />
  <img src="img/terminal/eq.jpg" width="24%" alt="Equalizer" />
  <img src="img/terminal/download.jpg" width="24%" alt="Download &amp; Sync" />
</p>

### Material

<p align="center">
  <img src="img/material/home.jpg" width="24%" alt="Home" />
  <img src="img/material/library.jpg" width="24%" alt="Library" />
  <img src="img/material/album.jpg" width="24%" alt="Album" />
  <img src="img/material/search.jpg" width="24%" alt="Search" />
</p>

<p align="center">
  <img src="img/material/player.jpg" width="24%" alt="Player" />
  <img src="img/material/lyrics.jpg" width="24%" alt="Lyrics" />
  <img src="img/material/queue.jpg" width="24%" alt="Queue" />
  <img src="img/material/wrapped.jpg" width="24%" alt="Wrapped" />
</p>

<p align="center">
  <img src="img/material/settings.jpg" width="24%" alt="Settings" />
  <img src="img/material/appearance.jpg" width="24%" alt="Appearance" />
  <img src="img/material/eq.jpg" width="24%" alt="Equalizer" />
  <img src="img/material/download.jpg" width="24%" alt="Download &amp; Sync" />
</p>

### Widgets

<p align="center">
  <img src="img/widgets.png" width="60%" alt="Terminal and Material widgets" />
</p>

## Download

Grab the latest APK from the [Releases page](https://github.com/OxoGhost01/HexaPlayer/releases/latest) and install it. The app then updates itself.

- Android 8.1 or newer
- Access to your audio files (asked on first launch)
- For Download & Sync only: the free [Termux](https://f-droid.org/packages/com.termux/) app (from F-Droid or GitHub, not the Play Store)

## Permissions

HexaPlayer only asks for what it needs to play your music, nothing more.

| Permission | Why | When |
|---|---|---|
| **Music and audio** (`READ_MEDIA_AUDIO`, or `READ_EXTERNAL_STORAGE` on Android 12 and older) | Find and play the songs on your phone | First launch |
| **Notifications** (`POST_NOTIFICATIONS`, Android 13+) | Show the playback controls notification and update alerts | First launch |
| **Run in the background** (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK`) | Keep the music playing with the screen off or the app in the background, and work with Android Auto | Granted automatically |
| **Internet** (`INTERNET`) | Fetch lyrics from LRCLIB, check GitHub for updates, and talk to the downloader running on your own phone | Granted automatically |
| **Install apps** (`REQUEST_INSTALL_PACKAGES`) | Install HexaPlayer updates from inside the app | Only when you tap Update |
| **Run commands in Termux** (`com.termux.permission.RUN_COMMAND`) | Start the downloader in Termux for you | Only if you enable Download & Sync |
| **Storage write** (`WRITE_EXTERNAL_STORAGE`, Android 9 and older only) | Save downloaded music, and edit song info | Only if you use Download & Sync or edit tags |

When you edit a song's info, Android asks you to confirm the change for those files, each time. The tags are written into the files themselves.

Your play history (for Home and Wrapped) is stored only on your phone, in the app's private storage.

No location, contacts, camera, microphone or account access. No analytics, no ads, and nothing about you or your music is ever sent anywhere.

## Planned Features

- **Song recognition**: find the name of a song playing around you

## Tech Stack

| Layer | Library |
|---|---|
| Language | Kotlin 2.4 |
| UI | Jetpack Compose (Material 3 and a custom terminal design system) |
| Widgets | Jetpack Glance |
| Media | Media3 1.11 (ExoPlayer, MediaSession, MediaLibraryService) |
| Image loading | Coil 3 |
| Tags | [TagLib for Android](https://github.com/Kyant0/taglib) |
| Persistence | Room (play history, lyrics cache), DataStore (appearance), SharedPreferences |
| Architecture | MVVM: ViewModel + LiveData + Repository |
| Downloader | spotDL in Termux, behind a small local FastAPI server ([termux/](termux/)) |

## Building

1. Clone the repo
2. Open it in a recent Android Studio (Android SDK 37 and JDK 17)
3. `Build → Make Project`, no extra setup needed

```bash
git clone https://github.com/OxoGhost01/HexaPlayer.git
```

## License

[Non-Commercial License](LICENSE): free to use and modify for any non-commercial purpose.
