#!/usr/bin/env bash
set -euo pipefail

# Simple macOS packaging script: creates a minimal .app bundle and packages into a DMG
# Usage: ./macos-package.sh [output-dir]

OUTDIR=${1:-.}
APP_NAME=Paint
BUNDLE_ID=io.github.ozkanpakdil.paint
WORKDIR=$(mktemp -d /tmp/paint-macos-XXXX)

echo "Building macOS app in $WORKDIR"

BIN=target/paint
if [ ! -x "$BIN" ]; then
  if [ -x target/native/paint ]; then
    BIN=target/native/paint
  else
    echo "Native binary not found. Build with GraalVM native-image first." >&2
    exit 1
  fi
fi

APPDIR="$WORKDIR/${APP_NAME}.app/Contents"
mkdir -p "$APPDIR/MacOS" "$APPDIR/Resources"
cp "$BIN" "$APPDIR/MacOS/paint"
chmod 0755 "$APPDIR/MacOS/paint"

# Info.plist
cat > "$APPDIR/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple Computer//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>CFBundleName</key>
  <string>${APP_NAME}</string>
  <key>CFBundleExecutable</key>
  <string>paint</string>
  <key>CFBundleIdentifier</key>
  <string>${BUNDLE_ID}</string>
  <key>CFBundleVersion</key>
  <string>1.0.0</string>
  <key>CFBundlePackageType</key>
  <string>APPL</string>
  <key>CFBundleIconFile</key>
  <string>app.icns</string>
</dict>
</plist>
PLIST

# Create icns from PNG if available
if [ -f src/main/resources/images/app.png ]; then
  ICONSET="$WORKDIR/icon.iconset"
  rm -rf "$ICONSET" && mkdir -p "$ICONSET"
  sips -z 16 16 src/main/resources/images/app.png --out "$ICONSET/icon_16x16.png"
  sips -z 32 32 src/main/resources/images/app.png --out "$ICONSET/icon_16x16@2x.png"
  sips -z 32 32 src/main/resources/images/app.png --out "$ICONSET/icon_32x32.png"
  sips -z 64 64 src/main/resources/images/app.png --out "$ICONSET/icon_32x32@2x.png"
  sips -z 128 128 src/main/resources/images/app.png --out "$ICONSET/icon_128x128.png"
  sips -z 256 256 src/main/resources/images/app.png --out "$ICONSET/icon_128x128@2x.png"
  sips -z 256 256 src/main/resources/images/app.png --out "$ICONSET/icon_256x256.png"
  sips -z 512 512 src/main/resources/images/app.png --out "$ICONSET/icon_256x256@2x.png"
  sips -z 512 512 src/main/resources/images/app.png --out "$ICONSET/icon_512x512.png"
  sips -z 1024 1024 src/main/resources/images/app.png --out "$ICONSET/icon_512x512@2x.png"
  iconutil -c icns "$ICONSET" -o "$APPDIR/Resources/app.icns" || true
fi

DMG_NAME="${OUTDIR}/paint-macos.dmg"
hdiutil create -volname "Paint" -srcfolder "$WORKDIR/${APP_NAME}.app" -ov -format UDZO "$DMG_NAME"

echo "Created $DMG_NAME"
