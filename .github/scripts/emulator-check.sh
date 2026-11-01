#!/bin/bash
# Installs the release APK on an emulator, opens it as each phone (and again after a restart),
# and saves screenshots plus any crash from the log in $OUT.
APK=app/build/outputs/apk/release/app-release.apk
PKG=com.darksoul.darksride
OUT=${OUT:-/tmp/emu}
mkdir -p "$OUT"
adb install -r "$APK" > "$OUT/install.txt" 2>&1

shot() { sleep "${2:-5}"; adb exec-out screencap -p > "$OUT/$1.png"; }
tap() {
  adb shell uiautomator dump /sdcard/ui.xml > /dev/null 2>&1
  adb pull /sdcard/ui.xml "$OUT/ui.xml" > /dev/null 2>&1
  python3 .github/scripts/tap.py "$1" "$OUT/ui.xml" >> "$OUT/steps.txt"
}
start() { adb shell am start -W -n "$PKG/.MainActivity" >> "$OUT/steps.txt" 2>&1; }
restart() { adb shell am force-stop "$PKG"; start; }

adb logcat -c
# Bike phone
adb shell pm clear "$PKG" > /dev/null
start; shot 1-first-start 6
tap "handlebar hq"; shot 2-ride-picked
restart; shot 3-ride-reopened 6
tap "settings"; shot 4-ride-settings
# Main phone
adb shell pm clear "$PKG" > /dev/null
start; sleep 4
tap "pocket sherpa"; shot 5-main-picked
restart; shot 6-main-reopened 6
tap "garage"; shot 7-main-garage 3
tap "documents"; shot 8-main-documents 3
tap "settings"; shot 9-main-settings 3

adb logcat -d -b crash > "$OUT/crash.txt" 2>&1
adb logcat -d | grep -E "AndroidRuntime|FATAL|darksride" | tail -300 > "$OUT/log.txt"
echo "done"
