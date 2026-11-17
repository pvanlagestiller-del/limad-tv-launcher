#!/usr/bin/env bash
# Disables Amazon ads, telemetry and Alexa on Fire OS (no root needed).
# Everything is reversible with restore.sh.
# Usage: ./debloat.sh <IP-des-Sticks> [--block-updates]
set -euo pipefail

IP="${1:?Usage: debloat.sh <IP-des-Sticks> [--block-updates]}"
DEV="${IP}:5555"
STATE="$(dirname "$0")/disabled-${IP}.txt"

PACKAGES=(
    # Telemetry / crash reports / metrics
    com.amazon.device.crashmanager
    com.amazon.client.metrics
    com.amazon.client.metrics.api
    com.amazon.dp.logger
    com.amazon.tifobserver
    # Ads / promotions
    com.amazon.aria
    com.amazon.device.sale.service
    # Alexa
    com.amazon.vizzini
    com.amazon.alexa.externalmediaplayer.fireos
    # Misc services not needed
    com.amazon.ags.app
    com.amazon.whisperplay.contentservice
    com.amazon.sharingservice.android.client.proxy
    com.amazon.wifilocker
    com.amazon.bueller.photos
)

if [[ "${2:-}" == "--block-updates" ]]; then
    PACKAGES+=(
        com.amazon.device.software.ota
        com.amazon.device.software.ota.override
        com.amazon.tv.forcedotaupdater.v2
    )
fi

adb connect "$DEV"
adb -s "$DEV" wait-for-device
INSTALLED="$(adb -s "$DEV" shell pm list packages | tr -d '\r' | sed 's/^package://')"

touch "$STATE"
for p in "${PACKAGES[@]}"; do
    if ! grep -qx "$p" <<<"$INSTALLED"; then
        echo "überspringe (nicht vorhanden): $p"
        continue
    fi
    if adb -s "$DEV" shell pm disable-user --user 0 "$p" 2>&1 | tr -d '\r' | grep -q "disabled"; then
        grep -qx "$p" "$STATE" || echo "$p" >>"$STATE"
        echo "deaktiviert: $p"
    else
        echo "nicht möglich (geschützt): $p"
    fi
done

echo "Liste gespeichert in $STATE (für restore.sh)."
