#!/usr/bin/env bash
# Builds build/korea-slingshot.apk without Gradle or Android Studio.
# Needs: JDK 8+, npm, and the Android tools from Ubuntu/Debian packages:
#   sudo apt-get install aapt zipalign apksigner dalvik-exchange android-sdk-platform-23
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
WEB="$(cd "$HERE/.." && pwd)"
OUT="$HERE/build"
ANDROID_JAR="${ANDROID_JAR:-/usr/lib/android-sdk/platforms/android-23/android.jar}"
MIN_SDK=24
TARGET_SDK=34
VERSION_CODE="${VERSION_CODE:-2}"
VERSION_NAME="${VERSION_NAME:-1.1}"
KEYSTORE="${KEYSTORE:-$HERE/signing/korea-slingshot.jks}"
KS_ALIAS="${KS_ALIAS:-korea-slingshot}"
KS_PASS="${KS_PASS:-korea-slingshot}"

for tool in aapt zipalign apksigner dalvik-exchange javac keytool npm; do
  command -v "$tool" >/dev/null || { echo "missing tool: $tool" >&2; exit 1; }
done
[ -f "$ANDROID_JAR" ] || { echo "android.jar not found at $ANDROID_JAR" >&2; exit 1; }

rm -rf "$OUT"
mkdir -p "$OUT/assets/www/data" "$OUT/assets/www/vendor" "$OUT/gen" "$OUT/classes" "$OUT/dex" "$OUT/npm"

echo "1/6 web assets"
# Bundle the map libraries so the app works without the CDNs.
( cd "$OUT/npm" && npm pack --silent d3@7.9.0 topojson-client@3.1.0 >/dev/null )
tar -xzf "$OUT/npm/d3-7.9.0.tgz" -C "$OUT/npm" package/dist/d3.min.js
cp "$OUT/npm/package/dist/d3.min.js" "$OUT/assets/www/vendor/d3.min.js"
tar -xzf "$OUT/npm/topojson-client-3.1.0.tgz" -C "$OUT/npm" package/dist/topojson-client.min.js
cp "$OUT/npm/package/dist/topojson-client.min.js" "$OUT/assets/www/vendor/topojson-client.min.js"
cp "$WEB/data/korea.topo.json" "$OUT/assets/www/data/"
sed \
  -e 's#https://cdnjs.cloudflare.com/ajax/libs/d3/7.9.0/d3.min.js#vendor/d3.min.js#' \
  -e 's#https://cdn.jsdelivr.net/npm/topojson-client@3.1.0/dist/topojson-client.min.js#vendor/topojson-client.min.js#' \
  "$WEB/index.html" > "$OUT/assets/www/index.html"
grep -q 'src="vendor/d3.min.js"' "$OUT/assets/www/index.html"
grep -q 'src="vendor/topojson-client.min.js"' "$OUT/assets/www/index.html"

echo "2/6 resources"
aapt package -f -m \
  -J "$OUT/gen" \
  -M "$HERE/AndroidManifest.xml" \
  -S "$HERE/res" \
  -A "$OUT/assets" \
  -I "$ANDROID_JAR" \
  --min-sdk-version "$MIN_SDK" \
  --target-sdk-version "$TARGET_SDK" \
  --version-code "$VERSION_CODE" \
  --version-name "$VERSION_NAME" \
  -F "$OUT/app.unsigned.apk"

echo "3/6 java"
javac -nowarn -Xlint:-options -source 8 -target 8 \
  -bootclasspath "$ANDROID_JAR" -classpath "$ANDROID_JAR" \
  -d "$OUT/classes" \
  $(find "$HERE/src" "$OUT/gen" -name '*.java')

echo "4/6 dex"
dalvik-exchange --dex --min-sdk-version="$MIN_SDK" --output="$OUT/dex/classes.dex" "$OUT/classes"
( cd "$OUT/dex" && aapt add "$OUT/app.unsigned.apk" classes.dex >/dev/null )

echo "5/6 align"
zipalign -f -p 4 "$OUT/app.unsigned.apk" "$OUT/app.aligned.apk"

echo "6/6 sign"
if [ ! -f "$KEYSTORE" ]; then
  mkdir -p "$(dirname "$KEYSTORE")"
  keytool -genkeypair -noprompt -keystore "$KEYSTORE" -alias "$KS_ALIAS" \
    -keyalg RSA -keysize 2048 -validity 10000 \
    -storepass "$KS_PASS" -keypass "$KS_PASS" \
    -dname "CN=Korea Slingshot, O=3000won, C=KR"
fi
apksigner sign --ks "$KEYSTORE" --ks-key-alias "$KS_ALIAS" \
  --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --min-sdk-version "$MIN_SDK" \
  --out "$OUT/korea-slingshot.apk" "$OUT/app.aligned.apk"
apksigner verify --min-sdk-version "$MIN_SDK" "$OUT/korea-slingshot.apk"

echo "built $OUT/korea-slingshot.apk ($(du -h "$OUT/korea-slingshot.apk" | cut -f1))"
