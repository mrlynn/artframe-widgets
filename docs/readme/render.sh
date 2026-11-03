#!/bin/sh
# Renders banner.html to banner.png at 2x with headless Chrome. Run from anywhere.
set -eu
cd "$(dirname "$0")"
CHROME="${CHROME:-/Applications/Google Chrome.app/Contents/MacOS/Google Chrome}"
"$CHROME" --headless=new --disable-gpu --hide-scrollbars --allow-file-access-from-files \
  --force-device-scale-factor=2 --window-size=1280,460 --virtual-time-budget=8000 \
  --screenshot="$PWD/banner.png" "file://$PWD/banner.html" 2>/dev/null
# Print which web fonts actually loaded, so a fallback face can't slip through.
"$CHROME" --headless=new --disable-gpu --allow-file-access-from-files --virtual-time-budget=8000 \
  --dump-dom "file://$PWD/banner.html" 2>/dev/null | grep -o '<title>[^<]*</title>'
