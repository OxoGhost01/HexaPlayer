"""
HexaDL server: a tiny local server that wraps the spotDL CLI so the
HexaPlayer Android app can download and sync Spotify playlists/albums/tracks
onto the phone.

It is designed to run **inside Termux on the same phone** as HexaPlayer, bound
to 127.0.0.1. spotDL writes the audio straight into the phone's Music folder
(HEXADL_ROOT), so HexaPlayer just has to rescan, no files are copied over the
network.

Run (Termux):  hexadl        # the launcher created by setup.sh
Run (manual):  HEXADL_ROOT=~/storage/shared/Music/HexaPlayer python hexadl_server.py
"""

import functools
import hashlib
import importlib.metadata
import json
import os
import re
import shutil
import signal
import subprocess
import threading
import time
import uuid
from pathlib import Path
from typing import Dict, List, Optional

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

# --------------------------------------------------------------------------- #
# Configuration (all overridable via environment variables)
# --------------------------------------------------------------------------- #

# Bump when the app needs a newer server; the app asks users to re-run setup below its minimum
SERVER_VERSION = 2

ROOT = Path(os.environ.get("HEXADL_ROOT", "./music")).resolve()
# Jobs may write anywhere under shared storage (the app's Music Library folder)
SHARED = Path(os.environ.get("HEXADL_SHARED", "/storage/emulated/0")).resolve()
SYNC_DIR = Path(os.environ.get("HEXADL_SYNC_DIR", str(ROOT / ".hexadl" / "syncs"))).resolve()
SPOTDL_BIN = os.environ.get("HEXADL_SPOTDL_BIN", "spotdl")
# Default to opus + no re-encode: YouTube Music already serves opus, so this
# keeps the original stream instead of spotDL's default of transcoding it
# down to 128k mp3 (a second lossy pass for no benefit). Override via
# HEXADL_SPOTDL_ARGS, e.g. "--format mp3 --bitrate 320k", if mp3 is needed
# for compatibility. Later flags win, so an override replaces this default.
DEFAULT_ARGS = ["--format", "opus", "--bitrate", "disable"]
# Extra CLI args applied to every job, e.g. "--format mp3 --bitrate 320k"
EXTRA_ARGS = DEFAULT_ARGS + os.environ.get("HEXADL_SPOTDL_ARGS", "").split()

AUDIO_EXTS = {".mp3", ".m4a", ".flac", ".opus", ".ogg", ".wav"}

ROOT.mkdir(parents=True, exist_ok=True)
SYNC_DIR.mkdir(parents=True, exist_ok=True)

app = FastAPI(title="HexaDL", description="spotDL companion server for HexaPlayer")

# The server exits after this many minutes without requests or running jobs
# (0 = never), so the wake lock is not held all day.
IDLE_MINUTES = float(os.environ.get("HEXADL_IDLE_MINUTES", "10"))
_last_activity = time.time()


@app.middleware("http")
async def _touch(request, call_next):
    global _last_activity
    _last_activity = time.time()
    return await call_next(request)


# --------------------------------------------------------------------------- #
# Job tracking
# --------------------------------------------------------------------------- #

class Job:
    def __init__(self, job_id: str, kind: str, url: str, name: str, out_dir: Path):
        self.id = job_id
        self.out_dir = out_dir
        self.kind = kind            # "download" | "sync"
        self.url = url
        self.name = name            # requested folder name ("" = auto)
        self.status = "running"     # running | done | error
        self.done = 0
        self.failed = 0
        self.total = 0
        self.message = "Starting…"
        self.error: Optional[str] = None
        self.started_at = time.time()
        self.folders: List[str] = []   # top-level folders the job produced
        self.files: List[str] = []     # absolute paths of the audio files it wrote
        self.proc: Optional[subprocess.Popen] = None
        self.stop_requested = False
        self.last_song_at = time.time()  # when spotDL last reported a finished song
        self.log_tail: List[str] = []

    def as_dict(self) -> dict:
        return {
            "id": self.id,
            "kind": self.kind,
            "url": self.url,
            "name": self.name,
            "status": self.status,
            "done": self.done,
            "failed": self.failed,
            "total": self.total,
            "message": self.message,
            "error": self.error,
            "folders": self.folders,
            "files": self.files,
            "log": self.log_tail[-8:],
        }


JOBS: Dict[str, Job] = {}
JOBS_LOCK = threading.Lock()

# spotDL prints one of these per song when it finishes / skips one.
_DONE_RE = re.compile(r'^(Downloaded|Skipping|Skip )', re.IGNORECASE)
# Per-song failures, e.g. "AudioProviderError: YT-DLP download error - <url>"
# or "Muse - Falling Down generated an exception: ..." (Spotify lookup failed)
_FAILED_RE = re.compile(r'^(\w+Error: |Error downloading)| generated an exception: ')
_TOTAL_RE = re.compile(r'Found (\d+) songs', re.IGNORECASE)


def _run_job(job: Job, cmd: List[str]) -> None:
    try:
        proc = subprocess.Popen(
            cmd,
            cwd=str(job.out_dir),
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
            # spotDL wraps its output at the terminal width; keep one message per line
            env={**os.environ, "COLUMNS": "1000"},
            start_new_session=True,
        )
        job.proc = proc
        assert proc.stdout is not None
        for raw in proc.stdout:
            line = raw.rstrip()
            if not line:
                continue
            job.log_tail.append(line)
            job.message = line[:200]
            m = _TOTAL_RE.search(line)
            if m:
                job.total = int(m.group(1))
            if _DONE_RE.match(line):
                job.done += 1
                job.last_song_at = time.time()
            elif _FAILED_RE.search(line):
                job.done += 1
                job.failed += 1
                job.last_song_at = time.time()
        code = proc.wait()
        if job.stop_requested:
            _collect_files(job, drop_recent=True)
            job.status = "stopped"
            job.message = f"Stopped after {job.done - job.failed} track(s)"
            return
        if code != 0:
            job.status = "error"
            job.error = job.message or f"spotdl exited with code {code}"
            return
    except Exception as exc:  # noqa: BLE001
        job.status = "error"
        job.error = str(exc)
        return

    _collect_files(job)
    job.status = "done"
    ok = job.done - job.failed
    job.message = f"Finished: {ok} track(s)" + (f", {job.failed} failed" if job.failed else "")


def _collect_files(job: Job, drop_recent: bool = False) -> None:
    """Find the audio files this job wrote (newer than its start)."""
    cutoff = job.started_at - 5
    # A killed job may leave a half-written file, and a retry would skip it
    # as "already exists". Drop what was written after the last finished song.
    recent = job.last_song_at
    files, touched = [], set()
    for p in job.out_dir.rglob("*"):
        if p.is_file() and p.suffix.lower() in AUDIO_EXTS:
            try:
                mtime = p.stat().st_mtime
                if mtime < cutoff:
                    continue
                if drop_recent and mtime > recent:
                    p.unlink()
                    continue
                rel = p.relative_to(job.out_dir)
                touched.add(rel.parts[0] if len(rel.parts) > 1 else "")
                files.append(str(p))
            except OSError:
                pass
    job.folders = sorted(touched)
    job.files = sorted(files)


def _resolve_out_dir(root: Optional[str]) -> Path:
    """Output folder for a job: the app's library folder, or ROOT by default."""
    if not root or not root.strip().strip("/"):
        return ROOT
    target = (SHARED / root.strip().strip("/")).resolve()
    if target != SHARED and SHARED not in target.parents:
        raise HTTPException(status_code=400, detail="Invalid root folder")
    target.mkdir(parents=True, exist_ok=True)
    return target


def _sync_file_for(url: str) -> Path:
    digest = hashlib.sha1(url.encode("utf-8")).hexdigest()[:16]
    return SYNC_DIR / f"{digest}.spotdl"


def _output_template(folder_name: str, is_collection: bool) -> str:
    """Build spotDL --output. folder_name overrides auto playlist naming."""
    if folder_name:
        safe = folder_name.strip().strip("/").replace("..", "")
        return f"{safe}/{{title}}.{{output-ext}}"
    if is_collection:
        return "{list-name}/{title}.{output-ext}"
    return "{title}.{output-ext}"


def _is_collection(url: str) -> bool:
    return any(k in url for k in ("playlist", "album", "artist"))


# --------------------------------------------------------------------------- #
# Request models
# --------------------------------------------------------------------------- #

class JobRequest(BaseModel):
    url: str
    kind: str = "download"          # "download" | "sync"
    folder: Optional[str] = None    # override folder name; None/"" = auto
    root: Optional[str] = None      # library folder relative to shared storage; None = ROOT


# --------------------------------------------------------------------------- #
# Endpoints
# --------------------------------------------------------------------------- #

@functools.lru_cache(maxsize=1)
def _spotdl_version() -> str:
    # Read from package metadata: running "spotdl --version" takes seconds on a phone.
    try:
        return importlib.metadata.version("spotdl")
    except Exception:  # noqa: BLE001
        return "?"


@app.get("/health")
def health() -> dict:
    return {"ok": True, "spotdl": _spotdl_version(), "server": SERVER_VERSION, "root": str(ROOT)}


@app.post("/jobs")
def create_job(req: JobRequest) -> dict:
    # Drop "?si=..." share codes so the same playlist always maps to one sync file
    raw_url = req.url.strip()
    url = raw_url.split("?")[0].split("#")[0]
    if not url:
        raise HTTPException(status_code=400, detail="Missing url")

    kind = req.kind if req.kind in ("download", "sync") else "download"
    folder = (req.folder or "").strip()
    root = (req.root or "").strip().strip("/")
    out_dir = _resolve_out_dir(root)
    job_id = uuid.uuid4().hex[:12]
    job = Job(job_id, kind, url, folder, out_dir)

    output = _output_template(folder, _is_collection(url))
    base = [SPOTDL_BIN, "--output", output, *EXTRA_ARGS]

    if kind == "sync":
        sync_file = _sync_file_for(url)
        legacy = _sync_file_for(raw_url)
        if legacy != sync_file and legacy.exists() and not sync_file.exists():
            # Saved before share codes were stripped: move it to the clean URL
            legacy.rename(sync_file)
            legacy.with_suffix(".json").unlink(missing_ok=True)
        if sync_file.exists():
            # Re-sync from the saved file (adds new, removes deleted tracks).
            cmd = [*base, "sync", str(sync_file)]
        else:
            # First run: download everything and create the sync file.
            cmd = [*base, "sync", url, "--save-file", str(sync_file)]
        # Remember the human name/url alongside the sync file for /syncs.
        _write_sync_meta(sync_file, url, folder, root)
    else:
        cmd = [*base, "download", url]

    with JOBS_LOCK:
        JOBS[job_id] = job
    threading.Thread(target=_run_job, args=(job, cmd), daemon=True).start()
    return {"id": job_id}


@app.get("/jobs/{job_id}")
def get_job(job_id: str) -> dict:
    job = JOBS.get(job_id)
    if not job:
        raise HTTPException(status_code=404, detail="No such job")
    return job.as_dict()


@app.post("/jobs/{job_id}/stop")
def stop_job(job_id: str) -> dict:
    job = JOBS.get(job_id)
    if not job:
        raise HTTPException(status_code=404, detail="No such job")
    if job.status == "running" and job.proc is not None:
        job.stop_requested = True
        try:
            # spotDL spawns ffmpeg, so stop the whole process group
            os.killpg(job.proc.pid, signal.SIGTERM)
        except ProcessLookupError:
            pass
    return job.as_dict()


@app.get("/jobs")
def list_jobs() -> dict:
    with JOBS_LOCK:
        items = [j.as_dict() for j in JOBS.values()]
    items.sort(key=lambda j: j["id"])
    return {"jobs": items}


@app.get("/syncs")
def list_syncs() -> dict:
    """Saved sync playlists, so the app can re-sync them with one tap."""
    out = []
    for meta in SYNC_DIR.glob("*.json"):
        try:
            data = json.loads(meta.read_text(encoding="utf-8"))
            spotdl_file = meta.with_suffix(".spotdl")
            name = "Playlist"
            if spotdl_file.exists():
                name = _read_sync_name(spotdl_file) or data.get("folder") or name
            out.append({
                "url": data.get("url", ""),
                "folder": data.get("folder", ""),
                "root": data.get("root", ""),
                "name": name,
            })
        except Exception:  # noqa: BLE001
            pass
    out.sort(key=lambda s: s["name"].lower())
    return {"syncs": out}


# --------------------------------------------------------------------------- #
# Sync metadata helpers
# --------------------------------------------------------------------------- #

def _write_sync_meta(sync_file: Path, url: str, folder: str, root: str) -> None:
    meta = sync_file.with_suffix(".json")
    meta.write_text(json.dumps({"url": url, "folder": folder, "root": root}), encoding="utf-8")


def _read_sync_name(sync_file: Path) -> Optional[str]:
    """Pull a friendly playlist name out of the .spotdl file's first song."""
    try:
        data = json.loads(sync_file.read_text(encoding="utf-8"))
        songs = data.get("songs") or []
        if songs:
            return songs[0].get("list_name") or None
    except Exception:  # noqa: BLE001
        return None
    return None


def _idle_watchdog(server) -> None:
    global _last_activity
    while not server.should_exit:
        time.sleep(30)
        with JOBS_LOCK:
            busy = any(j.status == "running" for j in JOBS.values())
        if busy:
            _last_activity = time.time()
        elif time.time() - _last_activity > IDLE_MINUTES * 60:
            print(f"No downloads for {IDLE_MINUTES:g} minutes, stopping. HexaPlayer restarts it when needed.")
            server.should_exit = True


if __name__ == "__main__":
    import uvicorn

    host = os.environ.get("HEXADL_HOST", "127.0.0.1")
    port = int(os.environ.get("HEXADL_PORT", "47823"))
    if not shutil.which(SPOTDL_BIN):
        print(f"WARNING: '{SPOTDL_BIN}' not found on PATH, jobs will fail.")
    print(f"HexaDL serving on http://{host}:{port}  (downloads -> {ROOT})")
    server = uvicorn.Server(uvicorn.Config(app, host=host, port=port))
    if IDLE_MINUTES > 0:
        threading.Thread(target=_idle_watchdog, args=(server,), daemon=True).start()
    server.run()
