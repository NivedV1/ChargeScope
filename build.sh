#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
sdk=${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Android/Sdk}}
platform="${ANDROID_PLATFORM:-android-36}"
tools="$sdk/build-tools/${ANDROID_BUILD_TOOLS:-36.0.0}"
jar="$sdk/platforms/$platform/android.jar"
out="$PWD/.build"
manifest=AndroidManifest.xml
apk=ChargeScope.apk
asset_args=(-A assets -A assets-vendor)
case "${1:-}" in
    --autodetect-debug)
        out="$out/autodetect"
        manifest="$out/AndroidManifest.xml"
        apk=ChargeScope-Autodetect-Debug.apk
        asset_args=(-A assets)
        mkdir -p "$out"
        sed -e 's/package="dev.chargescope.monitor"/package="dev.chargescope.monitor.autodetect"/' \
            -e 's/android:label="ChargeScope"/android:label="ChargeScope Autodetect" android:debuggable="true"/' \
            AndroidManifest.xml > "$manifest"
        ;;
    '') ;;
    *) printf 'Usage: %s [--autodetect-debug]\n' "$0" >&2; exit 2 ;;
esac
key="$out/debug.keystore"
if [[ ! -f "$jar" || ! -x "$tools/aapt2" ]]; then
    printf 'Missing Android SDK platform %s or build tools at %s.\n' "$platform" "$tools" >&2
    exit 1
fi

mkdir -p "$out/classes" "$out/dex" "$out/generated"
"$tools/aapt2" compile --dir res -o "$out/resources.zip"
"$tools/aapt2" link -I "$jar" --manifest "$manifest" --custom-package dev.chargescope.monitor "${asset_args[@]}" \
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
    --v4-signing-enabled false --out "$apk" "$out/aligned.apk"
"$tools/apksigner" verify "$apk"
printf 'Built %s/%s\n' "$PWD" "$apk"
