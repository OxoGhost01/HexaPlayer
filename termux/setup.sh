#!/data/data/com.termux/files/usr/bin/bash
#
# One-time setup for the HexaPlayer downloader (spotDL) inside Termux.
# Safe to run again: it updates everything and keeps your synced playlists.
#
# Run it with:
#   apt update && apt full-upgrade -y && curl -fsSL https://raw.githubusercontent.com/OxoGhost01/HexaPlayer/main/termux/setup.sh | bash
#
# The upgrade runs first because a half-upgraded Termux breaks curl itself.
# apt-get is used instead of pkg because pkg needs a working curl to pick a mirror.
#
set -e

# soundcloud-v2 pins curl_cffi<0.16, whose Android builds only load on Python
# 3.13. 0.16.3+ has real builds for each Python, so force it past the pin.
fix_curl_cffi() {
  echo "==> Installing a curl_cffi build that matches this Python..."
  pip install --upgrade --no-deps "curl_cffi>=0.16.3" </dev/null
}

# Everything lives in main() so bash reads the whole script before running it.
# With "curl | bash", stdin is the script itself, so nothing may read from it.
main() {
REPO_RAW="https://raw.githubusercontent.com/OxoGhost01/HexaPlayer/main/termux"
APT_OPTS=(-y -o Dpkg::Options::=--force-confnew)
export DEBIAN_FRONTEND=noninteractive

echo "==> Installing HexaPlayer downloader (spotDL) in Termux"

echo "==> Upgrading Termux packages..."
# A full upgrade is needed, otherwise new packages like ffmpeg link against an
# old library and fail with CANNOT LINK.
apt-get update </dev/null
dpkg --configure -a </dev/null || true
apt-get full-upgrade "${APT_OPTS[@]}" </dev/null
apt-get install "${APT_OPTS[@]}" python python-pip ffmpeg curl \
  build-essential rust cmake ninja </dev/null

echo "==> Installing spotDL (10 to 30 minutes the first time, parts are compiled)..."
# maturin (used to build pydantic-core) needs the Android API level on Termux.
export ANDROID_API_LEVEL="$(getprop ro.build.version.sdk)"
pip install --upgrade spotdl fastapi uvicorn </dev/null
fix_curl_cffi

# pip may reuse parts it compiled for an older Python (Termux upgrades Python
# from time to time), which then fail to load. Rebuild everything if so.
if ! spotdl --version >/dev/null 2>&1; then
  echo "==> spotDL does not start, rebuilding it for this Python (can take a while)..."
  pip install --force-reinstall --no-cache-dir spotdl fastapi uvicorn </dev/null
  fix_curl_cffi
  if ! spotdl --version >/dev/null 2>&1; then
    echo "!! spotDL still fails to start. Run 'spotdl --version' to see why."
    exit 1
  fi
fi

echo "==> Installing Deno (needed by many YouTube tracks)..."
apt-get install "${APT_OPTS[@]}" deno </dev/null || \
  echo "   (Deno could not be installed, some tracks may fail. See README.)"

echo "==> Requesting access to shared storage (tap 'Allow' if prompted)..."
termux-setup-storage </dev/null || true
sleep 3

echo "==> Letting HexaPlayer start the downloader..."
mkdir -p "$HOME/.termux"
PROPS="$HOME/.termux/termux.properties"
touch "$PROPS"
if grep -qE '^[[:space:]]*allow-external-apps' "$PROPS"; then
  sed -i -E 's/^[[:space:]]*allow-external-apps.*/allow-external-apps = true/' "$PROPS"
else
  echo "allow-external-apps = true" >> "$PROPS"
fi
termux-reload-settings </dev/null 2>/dev/null || true

echo "==> Fetching the HexaDL server..."
mkdir -p "$HOME/.hexadl"
curl -fsSL "$REPO_RAW/hexadl_server.py" -o "$HOME/.hexadl/hexadl_server.py"

echo "==> Creating the 'hexadl' launcher..."
mkdir -p "$PREFIX/bin"
cat > "$PREFIX/bin/hexadl" <<'LAUNCHER'
#!/data/data/com.termux/files/usr/bin/bash
# Keep the CPU awake while downloading
termux-wake-lock 2>/dev/null || true
export HEXADL_HOST=127.0.0.1
export HEXADL_PORT=47823
export HEXADL_ROOT="$HOME/storage/shared/Music/HexaPlayer"
if (exec 3<>"/dev/tcp/127.0.0.1/$HEXADL_PORT") 2>/dev/null; then
  if curl -fs -m 15 "http://127.0.0.1:$HEXADL_PORT/health" >/dev/null 2>&1; then
    echo "HexaDL is already running (HexaPlayer starts it in the background)."
    echo "Nothing to do. To stop it: pkill -f hexadl_server.py"
    exit 0
  fi
  echo "Port $HEXADL_PORT is used by another program."
  echo "Running processes: $(pgrep -af hexadl_server.py 2>/dev/null || echo none found)"
  exit 1
fi
if ! spotdl --version >/dev/null 2>&1; then
  echo "spotDL fails to start (usually after Termux updated Python)."
  echo "Run the HexaPlayer setup command again to repair it."
  exit 1
fi
mkdir -p "$HEXADL_ROOT"
echo "HexaDL running. Leave this open, switch to HexaPlayer, and download."
echo "Music goes to: Music/HexaPlayer"
exec python "$HOME/.hexadl/hexadl_server.py"
LAUNCHER
chmod +x "$PREFIX/bin/hexadl"

echo ""
echo "============================================================"
echo " Done! Go back to HexaPlayer, open the Download tab and"
echo " allow it to start Termux (setup guide, step 3)."
echo " You can also start the downloader by typing:   hexadl"
echo "============================================================"
}

main "$@"
