#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
sdk=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}
platform="${ANDROID_PLATFORM:-android-36}"
tools="$sdk/build-tools/${ANDROID_BUILD_TOOLS:-36.0.0}"
jar="$sdk/platforms/$platform/android.jar"
out="$PWD/.build"
key="$out/debug.keystore"
if [[ ! -f "$jar" || ! -x "$tools/aapt2" ]]; then
    printf 'Missing Android SDK platform %s or build tools at %s.\n' "$platform" "$tools" >&2
    exit 1
fi

mkdir -p "$out/classes" "$out/dex" "$out/generated"
"$tools/aapt2" compile --dir res -o "$out/resources.zip"
"$tools/aapt2" link -I "$jar" --manifest AndroidManifest.xml -A assets \
    --java "$out/generated" -o "$out/resources.apk" "$out/resources.zip"
mapfile -t sources < <(find src "$out/generated" -name '*.java' -type f | sort)
javac --release 8 -classpath "$jar" -d "$out/classes" "${sources[@]}"
mapfile -t classes < <(find "$out/classes" -name '*.class' -type f | sort)
"$tools/d8" --release --min-api 26 --lib "$jar" --output "$out/dex" "${classes[@]}"
cp "$out/resources.apk" "$out/unsigned.apk"
(cd "$out/dex" && zip -q "$out/unsigned.apk" classes.dex)
"$tools/zipalign" -f 4 "$out/unsigned.apk" "$out/aligned.apk"
if [[ ! -f "$key" ]]; then
    keytool -genkeypair -keystore "$key" -storepass android -keypass android \
        -alias chargescope-dev -dname "CN=ChargeScope Development" -keyalg RSA \
        -keysize 2048 -validity 10000 >/dev/null
fi
"$tools/apksigner" sign --ks "$key" --ks-pass pass:android --key-pass pass:android \
    --v4-signing-enabled false --out ChargeScope.apk "$out/aligned.apk"
"$tools/apksigner" verify ChargeScope.apk
printf 'Built %s/ChargeScope.apk\n' "$PWD"
