#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

SDK="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
ADB="$SDK/platform-tools/adb"
EMULATOR="$SDK/emulator/emulator"
APP_ID="app.simplifymy.launcher.debug"
AVD="${1:-}"

if [ -z "$($ADB devices | sed '1d' | grep -w device || true)" ]; then
    AVD="${AVD:-$($EMULATOR -list-avds | head -1)}"
    [ -n "$AVD" ] || { echo "No device attached and no AVD found. Create one in Android Studio." >&2; exit 1; }
    echo "Starting emulator $AVD..."
    "$EMULATOR" -avd "$AVD" -netdelay none -netspeed full >/dev/null 2>&1 &
    $ADB wait-for-device
    until [ "$($ADB shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = "1" ]; do sleep 2; done
fi

./gradlew :app:installDebug
$ADB shell cmd role add-role-holder android.app.role.HOME "$APP_ID"
$ADB shell input keyevent KEYCODE_HOME
echo "$APP_ID is the home app"
