#!/usr/bin/env bash
# Build deb + ipa for jailbroken iOS (no Apple signing).
# Embeds WidgetKit appex under PlugIns/.
set -euo pipefail
export LANG=C.UTF-8

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export THEOS="${THEOS:-/home/arroghie/theos}"
export PATH="$THEOS/bin:$PATH"
cd "$ROOT"

VERSION="$(grep -E '^Version:' control | awk '{print $2}')"
APP_NAME="XMUTimeTable"
WIDGET_NAME="XMUTimeTableWidget"
OUT_DIR="$(cd "$ROOT/.." && pwd)/artifacts"
mkdir -p "$OUT_DIR"

echo "==> Building $APP_NAME $VERSION (app + widget appex)"
make clean >/dev/null 2>&1 || true
make package FINALPACKAGE=1

DEB=""
for f in packages/*.deb; do
  [ -f "$f" ] || continue
  if echo "$f" | grep -q "_${VERSION}_"; then DEB="$f"; fi
done
if [ -z "$DEB" ]; then DEB="$(ls -1t packages/*.deb | head -1)"; fi
echo "==> using deb: $DEB"
echo "==> deb: $DEB"
cp -f "$DEB" "$OUT_DIR/"

# Prefer staged tree (contains PlugIns from appex stage)
APP_SRC=""
for cand in ".theos/_/Applications/${APP_NAME}.app" ".theos/obj/${APP_NAME}.app"; do
  if [ -d "$cand" ]; then APP_SRC="$cand"; break; fi
done
if [ -z "$APP_SRC" ]; then
  echo "ERROR: app bundle not found" >&2
  exit 1
fi

echo "==> App bundle: $APP_SRC"
echo "==> Contents:"
find "$APP_SRC" -maxdepth 3 -type f | sort

# Ensure widget appex is inside the app for IPA
if [ ! -d "$APP_SRC/PlugIns/${WIDGET_NAME}.appex" ]; then
  STAGED_APP=".theos/_/Applications/${APP_NAME}.app"
  if [ -d "$STAGED_APP/PlugIns/${WIDGET_NAME}.appex" ]; then
    mkdir -p "$APP_SRC/PlugIns"
    cp -a "$STAGED_APP/PlugIns/${WIDGET_NAME}.appex" "$APP_SRC/PlugIns/"
  elif [ -d ".theos/obj/${WIDGET_NAME}.appex" ]; then
    mkdir -p "$APP_SRC/PlugIns"
    cp -a ".theos/obj/${WIDGET_NAME}.appex" "$APP_SRC/PlugIns/"
  else
    echo "WARN: widget appex missing" >&2
  fi
fi

if [ ! -f "$APP_SRC/Info.plist" ]; then
  cp -a XMUTimeTable/Resources/Info.plist "$APP_SRC/Info.plist"
fi

echo "==> Packaging IPA"
TMP="$(mktemp -d)"
mkdir -p "$TMP/Payload"
cp -a "$APP_SRC" "$TMP/Payload/"
IPA="$OUT_DIR/${APP_NAME}_${VERSION}.ipa"
rm -f "$IPA"
( cd "$TMP" && zip -r9 "$IPA" Payload >/dev/null )
rm -rf "$TMP"

echo "==> Verify IPA contains widget"
unzip -l "$IPA" | grep -E 'appex|Info.plist|XMUTimeTable'

echo "==> Verify deb contains widget"
dpkg-deb -c "$DEB" | grep -E 'appex|PlugIns|Applications' || true

echo "==> Artifacts"
ls -la "$OUT_DIR"
echo "DONE"
