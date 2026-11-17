#!/usr/bin/env bash
# Re-enables everything debloat.sh disabled.
# Usage: ./restore.sh <IP-des-Sticks>
set -euo pipefail

IP="${1:?Usage: restore.sh <IP-des-Sticks>}"
DEV="${IP}:5555"
STATE="$(dirname "$0")/disabled-${IP}.txt"

[[ -f "$STATE" ]] || { echo "Keine Liste gefunden: $STATE"; exit 1; }

adb connect "$DEV"
adb -s "$DEV" wait-for-device

while read -r p; do
    [[ -z "$p" ]] && continue
    adb -s "$DEV" shell pm enable "$p" >/dev/null && echo "aktiviert: $p"
done <"$STATE"

rm -f "$STATE"
echo "Wiederhergestellt."
