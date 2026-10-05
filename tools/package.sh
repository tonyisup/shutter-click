#!/usr/bin/env bash
set -euo pipefail
task_root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$task_root"
simulator=false; release=false
for option in "$@"; do
    case "$option" in
        --simulator) simulator=true ;;
        --release) release=true ;;
        *) printf 'Usage: tools/package.sh [--simulator] [--release]\n' >&2; exit 1 ;;
    esac
done
./gradlew -PciqSimulator=false :core:test :android-app:assembleDebug :android-app:lintDebug
tools/build-watch.sh
mkdir -p dist
artifacts=(dist/ShutterClick-android.apk dist/ShutterClick.prg)
cp android-app/build/outputs/apk/debug/android-app-debug.apk dist/ShutterClick-android.apk
cp watch/build/ShutterClick.prg dist/ShutterClick.prg
if [[ "$simulator" == true ]]; then
    ./gradlew -PciqSimulator=true :android-app:assembleDebug :android-app:lintDebug
    cp android-app/build/outputs/apk/debug/android-app-debug.apk dist/ShutterClick-simulator.apk
    artifacts+=(dist/ShutterClick-simulator.apk)
fi
if [[ "$release" == true ]]; then
    if [[ ! -f keystore.properties ]]; then
        printf 'Create keystore.properties for release signing; see keystore.properties.example.\n' >&2
        exit 1
    fi
    # Store uploads: a Play bundle, a matching APK for device checks, and the Connect IQ package.
    ./gradlew -PciqSimulator=false :android-app:bundleRelease :android-app:assembleRelease
    tools/build-watch.sh --store
    cp android-app/build/outputs/bundle/release/android-app-release.aab dist/ShutterClick-release.aab
    cp android-app/build/outputs/apk/release/android-app-release.apk dist/ShutterClick-release.apk
    cp watch/build/ShutterClick.iq dist/ShutterClick.iq
    artifacts+=(dist/ShutterClick-release.aab dist/ShutterClick-release.apk dist/ShutterClick.iq)
fi
shasum -a 256 "${artifacts[@]}" > dist/SHA256SUMS
