#!/usr/bin/env bash
# Crea IrFace-magisk.zip a partir del APK compilado
set -euo pipefail
cd "$(dirname "$0")"

# Usa el APK más reciente entre release y debug (Android Studio "Build APK(s)" genera el debug)
APK=""
for f in app/build/outputs/apk/release/app-release.apk app/build/outputs/apk/debug/app-debug.apk; do
  if [ -f "$f" ] && { [ -z "$APK" ] || [ "$f" -nt "$APK" ]; }; then APK="$f"; fi
done
if [ -z "$APK" ]; then
  echo "No encuentro ningún APK. Compila primero (Android Studio: Build > Build APK(s))." >&2
  exit 1
fi
echo "Usando $APK"
command -v zip >/dev/null && command -v unzip >/dev/null || {
  echo "Faltan zip/unzip:  sudo apt install zip unzip" >&2; exit 1; }

OUT=build-module
rm -rf "$OUT"
mkdir -p "$OUT"
cp -r magisk-module/. "$OUT/"
rm -f "$OUT/system/priv-app/IrFace/.placeholder"
mkdir -p "$OUT/system/priv-app/IrFace/lib/arm64"
cp "$APK" "$OUT/system/priv-app/IrFace/IrFace.apk"
unzip -j -o -q "$APK" 'lib/arm64-v8a/*' -d "$OUT/system/priv-app/IrFace/lib/arm64/"
chmod 755 "$OUT"/*.sh "$OUT/META-INF/com/google/android/update-binary"

rm -f IrFace-magisk.zip
(cd "$OUT" && zip -qr9 ../IrFace-magisk.zip .)
echo "Listo: $(pwd)/IrFace-magisk.zip"
