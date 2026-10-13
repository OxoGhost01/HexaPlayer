"""
HexaDL server: a tiny local server that wraps the spotDL CLI so the
HexaPlayer Android app can download and sync Spotify playlists/albums/tracks
onto the phone.

It is designed to run **inside Termux on the same phone** as HexaPlayer, bound
to 127.0.0.1. spotDL writes the audio straight into the phone's Music folder
(HEXADL_ROOT), so HexaPlayer just has to rescan, no files are copied over the
network. (It also runs fine on a remote server if you ever want that.)

Run (Termux):  hexadl        # the launcher created by setup.sh
Run (manual):  HEXADL_ROOT=~/storage/shared/Music/HexaPlayer python hexadl_server.py
"""

import functools
import hashlib
import json
import os
import re
import shutil
import subprocess
import threading
import time
import uuid
from pathlib import Path
from typing import Dict, List, Optional

from fastapi import Depends, FastAPI, Header, HTTPException, Query
from fastapi.responses import FileResponse
from pydantic import BaseModel

# --------------------------------------------------------------------------- #
# Configuration (all overridable via environment variables)
# --------------------------------------------------------------------------- #

ROOT = Path(os.environ.get("HEXADL_ROOT", "./music")).resolve()
SYNC_DIR = Path(os.environ.get("HEXADL_SYNC_DIR", str(ROOT / ".hexadl" / "syncs"))).resolve()
TOKEN = os.environ.get("HEXADL_TOKEN", "")  # empty = auth disabled (fine for localhost)
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


# --------------------------------------------------------------------------- #
# Auth
# --------------------------------------------------------------------------- #

def require_token(x_token: Optional[str] = Header(default=None)) -> None:
    if TOKEN and x_token != TOKEN:
        raise HTTPException(status_code=401, detail="Invalid or missing X-Token")


# --------------------------------------------------------------------------- #
# Job tracking
# --------------------------------------------------------------------------- #

class Job:
    def __init__(self, job_id: str, kind: str, url: str, name: str):
        self.id = job_id
        self.kind = kind            # "download" | "sync"
        self.url = url
        self.name = name            # requested folder name ("" = auto)
        self.status = "running"     # running | done | error
        self.done = 0
        self.total = 0
        self.message = "Starting…"
        self.error: Optional[str] = None
        self.started_at = time.time()
        self.folders: List[str] = []   # top-level folders the job produced
        self.log_tail: List[str] = []

    def as_dict(self) -> dict:
        return {
            "id": self.id,
            "kind": self.kind,
            "url": self.url,
            "name": self.name,
            "status": self.status,
            "done": self.done,
            "total": self.total,
            "message": self.message,
            "error": self.error,
            "folders": self.folders,
            "log": self.log_tail[-8:],
        }


JOBS: Dict[str, Job] = {}
JOBS_LOCK = threading.Lock()

# spotDL prints one of these per song when it finishes / skips one.
_DONE_RE = re.compile(r'^(Downloaded|Skipping|Skip |Error downloading)', re.IGNORECASE)
_TOTAL_RE = re.compile(r'Found (\d+) songs', re.IGNORECASE)


def _run_job(job: Job, cmd: List[str]) -> None:
    try:
        proc = subprocess.Popen(
            cmd,
            cwd=str(ROOT),
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1,
        )
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
        code = proc.wait()
        if code != 0:
            job.status = "error"
            job.error = job.message or f"spotdl exited with code {code}"
            return
    except Exception as exc:  # noqa: BLE001
        job.status = "error"
        job.error = str(exc)
        return

    # Discover which folders were produced (files newer than job start).
    cutoff = job.started_at - 5
    touched = set()
    for p in ROOT.rglob("*"):
        if p.is_file() and p.suffix.lower() in AUDIO_EXTS:
            try:
                if p.stat().st_mtime >= cutoff:
                    rel = p.relative_to(ROOT)
                    touched.add(rel.parts[0] if len(rel.parts) > 1 else "")
            except OSError:
                pass
    job.folders = sorted(touched)
    job.status = "done"
    job.message = f"Finished: {job.done} track(s)"


def _sync_file_for(url: str) -> Path:
    digest = hashlib.sha1(url.encode("utf-8")).hexdigest()[:16]
    return SYNC_DIR / f"{digest}.spotdl"


def _output_template(folder_name: str, is_collection: bool) -> str:
    """Build spotDL --output. folder_name overrides auto playlist naming."""
    if folder_name:
        safe = folder_name.strip().strip("/").replace("..", "")
        return f"{safe}/{{artists}} - {{title}}.{{output-ext}}"
    if is_collection:
        return "{list-name}/{artists} - {title}.{output-ext}"
    return "{artists} - {title}.{output-ext}"


def _is_collection(url: str) -> bool:
    return any(k in url for k in ("playlist", "album", "artist"))


# --------------------------------------------------------------------------- #
# Request models
# --------------------------------------------------------------------------- #

class JobRequest(BaseModel):
    url: str
    kind: str = "download"          # "download" | "sync"
    folder: Optional[str] = None    # override folder name; None/"" = auto


# --------------------------------------------------------------------------- #
# Endpoints
# --------------------------------------------------------------------------- #

@functools.lru_cache(maxsize=1)
def _spotdl_version() -> str:
    try:
        return subprocess.check_output([SPOTDL_BIN, "--version"], text=True).strip()
    except Exception:  # noqa: BLE001
        return "?"


@app.get("/health")
def health(_: None = Depends(require_token)) -> dict:
    return {"ok": True, "spotdl": _spotdl_version(), "root": str(ROOT)}


@app.post("/jobs")
def create_job(req: JobRequest, _: None = Depends(require_token)) -> dict:
    url = req.url.strip()
    if not url:
        raise HTTPException(status_code=400, detail="Missing url")

    kind = req.kind if req.kind in ("download", "sync") else "download"
    folder = (req.folder or "").strip()
    job_id = uuid.uuid4().hex[:12]
    job = Job(job_id, kind, url, folder)

    output = _output_template(folder, _is_collection(url))
    base = [SPOTDL_BIN, "--output", output, *EXTRA_ARGS]

    if kind == "sync":
        sync_file = _sync_file_for(url)
        if sync_file.exists():
            # Re-sync from the saved file (adds new, removes deleted tracks).
            cmd = [*base, "sync", str(sync_file)]
        else:
            # First run: download everything and create the sync file.
            cmd = [*base, "sync", url, "--save-file", str(sync_file)]
        # Remember the human name/url alongside the sync file for /syncs.
        _write_sync_meta(sync_file, url, folder)
    else:
        cmd = [*base, "download", url]

    with JOBS_LOCK:
        JOBS[job_id] = job
    threading.Thread(target=_run_job, args=(job, cmd), daemon=True).start()
    return {"id": job_id}


@app.get("/jobs/{job_id}")
def get_job(job_id: str, _: None = Depends(require_token)) -> dict:
    job = JOBS.get(job_id)
    if not job:
        raise HTTPException(status_code=404, detail="No such job")
    return job.as_dict()


@app.get("/jobs")
def list_jobs(_: None = Depends(require_token)) -> dict:
    with JOBS_LOCK:
        items = [j.as_dict() for j in JOBS.values()]
    items.sort(key=lambda j: j["id"])
    return {"jobs": items}


@app.get("/tree")
def tree(folder: Optional[str] = Query(default=None), _: None = Depends(require_token)) -> dict:
    """List audio files (relative paths + size + mtime), optionally scoped to a folder."""
    base = ROOT
    if folder:
        candidate = (ROOT / folder).resolve()
        if not str(candidate).startswith(str(ROOT)):
            raise HTTPException(status_code=400, detail="Invalid folder")
        base = candidate
    files = []
    if base.exists():
        for p in base.rglob("*"):
            if p.is_file() and p.suffix.lower() in AUDIO_EXTS:
                try:
                    st = p.stat()
                    files.append({
                        "path": str(p.relative_to(ROOT)),
                        "size": st.st_size,
                        "mtime": int(st.st_mtime),
                    })
                except OSError:
                    pass
    files.sort(key=lambda f: f["path"])
    return {"files": files}


@app.get("/file")
def get_file(path: str, _: None = Depends(require_token)):
    target = (ROOT / path).resolve()
    if not str(target).startswith(str(ROOT)) or not target.is_file():
        raise HTTPException(status_code=404, detail="No such file")
    if target.suffix.lower() not in AUDIO_EXTS:
        raise HTTPException(status_code=400, detail="Not an audio file")
    return FileResponse(str(target), filename=target.name)


@app.get("/syncs")
def list_syncs(_: None = Depends(require_token)) -> dict:
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
                "name": name,
            })
        except Exception:  # noqa: BLE001
            pass
    out.sort(key=lambda s: s["name"].lower())
    return {"syncs": out}


# --------------------------------------------------------------------------- #
# Sync metadata helpers
# --------------------------------------------------------------------------- #

def _write_sync_meta(sync_file: Path, url: str, folder: str) -> None:
    meta = sync_file.with_suffix(".json")
    meta.write_text(json.dumps({"url": url, "folder": folder}), encoding="utf-8")


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


if __name__ == "__main__":
    import uvicorn

    host = os.environ.get("HEXADL_HOST", "127.0.0.1")
    port = int(os.environ.get("HEXADL_PORT", "8000"))
    if not shutil.which(SPOTDL_BIN):
        print(f"WARNING: '{SPOTDL_BIN}' not found on PATH, jobs will fail.")
    print(f"HexaDL serving on http://{host}:{port}  (downloads -> {ROOT})")
    uvicorn.run(app, host=host, port=port)
