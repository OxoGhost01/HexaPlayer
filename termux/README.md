# HexaPlayer downloader: Termux setup

HexaPlayer can download and sync Spotify playlists, albums and tracks using
[spotDL](https://github.com/spotDL/spotify-downloader). spotDL is a Python tool,
so it runs inside **Termux** (a free Linux environment for Android) right on your
phone. HexaPlayer talks to it over `127.0.0.1` and the music goes straight into
your library folder. Nothing leaves your device, no account is needed.

You only set this up **once**. If you never want the downloader, ignore all of
this: the rest of the app works without it.

The same steps are shown inside the app: **Download tab, Setup guide**.

## 1. Install Termux

Install Termux from one of these (same app, pick either):

- **[F-Droid](https://f-droid.org/packages/com.termux/)**: tap **Download APK**.
- **[GitHub, latest release](https://github.com/termux/termux-app/releases/latest)**:
  tap the file ending in `universal.apk`.

> ⚠️ **Do not** use the Termux from the Google Play Store, it won't work
> with this setup.

Open Termux once and wait until it has finished preparing.

## 2. Run the setup command

In Termux, paste this one line, then press Enter:

```bash
apt update && apt full-upgrade -y && curl -fsSL https://raw.githubusercontent.com/OxoGhost01/HexaPlayer/main/termux/setup.sh | bash
```

It updates Termux, installs Python, FFmpeg, spotDL and Deno, asks for storage
access (tap **Allow**), lets HexaPlayer start Termux commands, and creates the
`hexadl` command. The first run takes **10 to 30 minutes** because some parts
of spotDL are compiled on the phone. Wait until it prints **Done!**.

## 3. Let HexaPlayer start the downloader

In **HexaPlayer, Download tab, Setup guide**, tap **Allow** in step 3. That's it:

- HexaPlayer starts the downloader in the background when you press
  **Sync**, **Download**, **Re-sync** or **Run again**.
- Termux shows a notification while it runs.
- It stops by itself after **10 minutes** without downloads, so it doesn't
  drain your battery.

The **Downloader** card on the Download tab tells you whether it's running, and
**Test connection** shows *Success* or *Failure*.

## Using it

- **Sync** (main button): downloads everything missing from the link and
  remembers it. Later re-syncs add new songs and delete the ones you removed
  from the Spotify playlist (only files the sync downloaded itself). Synced
  playlists are listed below the buttons with a **Re-sync** button.
- **Download**: one-shot download of the link, nothing is remembered.
- **Where files go**: your **Music Library** folder from HexaPlayer's Settings,
  or `Music/HexaPlayer` if none is set. Playlists and albums get a folder named
  after them, or a custom name if you turn that on. Files are named after the
  song title; artist, album and cover are stored in the file's tags.
- **Stop / Run again**: a running job can be stopped at any time. Running it
  again skips the songs already on your phone.
- Failed songs are retried once automatically. A few songs can still fail when
  spotDL can't find them on YouTube Music or YouTube.

## Updating or repairing

Run the setup command from step 2 again, at any time. It is safe to repeat:
it updates Termux, spotDL and the downloader, and keeps your synced playlists.
When the downloader is too old for your version of HexaPlayer, the Downloader
card turns red and asks you to do this. After updating, stop the old
downloader with `pkill -f hexadl_server.py` (or wait 10 minutes): the app
starts the new one next time.

## Troubleshooting

- **`CANNOT LINK EXECUTABLE` (curl, ffmpeg, ...)**: your Termux packages are
  half upgraded. Run `apt update && apt full-upgrade -y` (not `pkg`, which needs
  a working curl), press Enter if asked about a config file, then run the setup
  command again.
- **"spotDL fails to start"** (often after Termux updated Python): run the setup
  command again, it rebuilds what is needed.
- **Downloads stop when the screen is off**: disable battery optimization for
  Termux in Android's settings.
- **Running it by hand**: type `hexadl` in Termux. If HexaPlayer already
  started it, it tells you so and exits.

## Advanced

The `hexadl` launcher lives in `$PREFIX/bin/hexadl`. You can add `export` lines
to it for these variables (the setup command rewrites this file, so re-apply
your changes after updating):

- `HEXADL_SPOTDL_ARGS`: extra spotDL options, added after the defaults
  (`--format opus --bitrate disable --threads 2`); later options win. Example:
  `--format mp3 --bitrate 320k` for mp3 files, or `--threads 4` for faster but
  less reliable downloads.
- `HEXADL_IDLE_MINUTES`: minutes without downloads before the server stops
  (default `10`, `0` = never).
- `HEXADL_PORT`: `47823`. HexaPlayer always uses this port.

**Why opus?** It's the audio format YouTube actually streams. Keeping it as is
avoids a second lossy conversion, so it's the best quality you can get from this
source, and Android plays it natively.

The server (`hexadl_server.py`) only listens on `127.0.0.1` and exposes
`GET /health`, `POST /jobs`, `GET /jobs`, `GET /jobs/{id}`,
`POST /jobs/{id}/stop` and `GET /syncs`.
