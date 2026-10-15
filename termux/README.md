# HexaPlayer downloader: Termux setup

HexaPlayer can download and sync Spotify playlists, albums and tracks using
[spotDL](https://github.com/spotDL/spotify-downloader). spotDL is a Python tool,
so it runs inside **Termux** (a free Linux environment for Android) right on your
phone. HexaPlayer talks to it over `127.0.0.1` and the music lands in your
`Music/HexaPlayer` folder. Nothing leaves your device.

You only do this **once**. If you never want the downloader, you can ignore all
of this: the rest of the app works without it.

The same steps are shown inside the app: **Download tab, Setup guide**.

## 1. Install Termux

Install Termux from one of these (same app, pick either):

- **[F-Droid](https://f-droid.org/packages/com.termux/)**: tap **Download APK**.
- **[GitHub, latest release](https://github.com/termux/termux-app/releases/latest)**:
  tap the file ending in `universal.apk`.

> ⚠️ **Do not** use the Termux from the Google Play Store, it won't work
> with this setup.

## 2. Run the setup command

Open Termux and paste this one line, then press Enter:

```bash
apt update && apt full-upgrade -y && curl -fsSL https://raw.githubusercontent.com/OxoGhost01/HexaPlayer/main/termux/setup.sh | bash
```

It installs Python, FFmpeg, spotDL and Deno, asks for storage access (tap
**Allow**), lets HexaPlayer start Termux commands (`allow-external-apps`), and
creates a `hexadl` command. The first run takes 10 to 30 minutes because some
spotDL parts are compiled on the phone. Wait until it prints **Done!**.

## 3. Let HexaPlayer start the downloader

In **HexaPlayer, Download tab, Setup guide**, tap **Allow** in step 3. From then
on, HexaPlayer starts the downloader in the background by itself when you start
a download or a sync. Termux shows a notification while it runs, and the
downloader stops by itself after 10 minutes without downloads (set
`HEXADL_IDLE_MINUTES` in `$PREFIX/bin/hexadl` to change that, `0` = never).

Prefer to do it by hand? Open Termux and type:

```bash
hexadl
```

Leave Termux running, switch to **HexaPlayer, Download**, tap **Test
connection** (it should say *Connected*), then paste a Spotify link and hit
**Download** or **Sync**. Press `Ctrl + C` in Termux to stop it.

## Tips

- **`CANNOT LINK EXECUTABLE` (curl, ffmpeg, ...):** your Termux packages are
  half upgraded. Run `apt update && apt full-upgrade -y` (not `pkg`, which needs
  a working curl), press Enter if asked about a config file, then run the setup
  command again.

- **Keep it running:** downloads only work while `hexadl` is running in Termux.
  The launcher grabs a wake-lock so the phone won't pause it while the screen is
  off.
- **Auto-start on boot (optional):** install the
  [Termux:Boot](https://github.com/termux/termux-boot) add-on and drop a script
  in `~/.termux/boot/` that runs `hexadl`.
- **Quality:** downloads default to `opus` with no re-encoding
  (`--format opus --bitrate disable`). This keeps YouTube Music's original
  audio stream intact instead of re-compressing it down to spotDL's default
  128k mp3, which is a strictly better result. If you need mp3 for
  compatibility with something that can't play opus, override it by setting
  `export HEXADL_SPOTDL_ARGS="--format mp3 --bitrate 320k"` before running
  `hexadl` (or add it inside `$PREFIX/bin/hexadl`); later flags win over the
  defaults.
- **Deno:** modern YouTube needs a JS runtime to unlock many tracks. The setup
  script installs it; if some songs fail with a signature/nsig error, run
  `pkg install deno` manually.

## What the server exposes (localhost only)

`GET /health`, `POST /jobs`, `GET /jobs/{id}`, `POST /jobs/{id}/stop`, `GET /jobs`,
`GET /syncs`. See `hexadl_server.py`. It binds to `127.0.0.1` by default, so it's
only reachable from the phone itself.
