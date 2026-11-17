#!/usr/bin/env bash
# Installs LiMaD TV on a Fire TV stick and enables the home-button service.
# Usage: ./install.sh <IP-des-Sticks> [pfad/zur.apk]
set -euo pipefail

IP="${1:?Usage: install.sh <IP-des-Sticks> [apk]}"
APK="${2:-app-release.apk}"
DEV="${IP}:5555"
PKG="de.limad.tvlauncher"
SVC="${PKG}/${PKG}.HomeButtonService"

adb connect "$DEV"
adb -s "$DEV" wait-for-device
adb -s "$DEV" install -r "$APK"

CUR="$(adb -s "$DEV" shell settings get secure enabled_accessibility_services | tr -d '\r')"
if [[ -z "$CUR" || "$CUR" == "null" ]]; then
    NEW="$SVC"
elif [[ "$CUR" == *"$SVC"* ]]; then
    NEW="$CUR"
else
    NEW="${CUR}:${SVC}"
fi

adb -s "$DEV" shell settings put secure enabled_accessibility_services "$NEW"
adb -s "$DEV" shell settings put secure accessibility_enabled 1
adb -s "$DEV" shell am start -n "${PKG}/.MainActivity"

echo "Fertig. Home-Taste drücken zum Testen."
