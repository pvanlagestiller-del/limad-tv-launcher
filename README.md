# LiMaD TV Launcher

Aufgeräumter Launcher im Apple-TV-Stil für den Fire TV Stick 4K (2. Gen, M3N6RA, Fire OS 8).

## Funktionen
- Oben: Datum und Uhr, darunter eine Reihe großer Favoriten-Kacheln, danach „Alle Apps“ als Raster.
- Fokus-Effekt: Die Kachel wird größer, bekommt Schatten und einen weißen Rand, der Name wird eingeblendet.
- **OK lang drücken:** zu Favoriten hinzufügen, entfernen, nach links/rechts verschieben, App-Info.
- Standard-Favoriten beim ersten Start (über den App-Namen erkannt): JW, Netflix, Prime Video, YouTube, Disney+.
- Die Home-Taste und die Amazon-Startseite werden per Bedienungshilfen-Dienst auf LiMaD TV umgeleitet.
- Die Kachel „Einstellungen“ öffnet die Fire-TV-Einstellungen.
- **Wetter** oben neben der Uhr (DWD-Daten über Bright Sky, alle 30 Minuten aktualisiert). OK öffnet LiMeteo, langes Drücken auf OK ändert den Ort (Stadt oder PLZ).
- **LiMeteo-Kachel**: öffnet https://limeteo.netlify.app im Vollbild. Die Zurück-Taste blättert zurück bzw. schließt.

## 1. APK bauen
Den Ordner in ein GitHub-Repo pushen. Die Action **Build APK** läuft automatisch, danach unter *Actions → Artifacts* die Datei `app-release.apk` herunterladen.

Lokal (Android SDK + Gradle 8.10):
```bash
gradle :app:assembleRelease
# -> app/build/outputs/apk/release/app-release.apk
```

## 2. Stick vorbereiten
Einstellungen → Mein Fire TV → Info → 7× auf den Gerätenamen klicken → Entwickleroptionen:
- ADB-Debugging: **An**
- IP-Adresse: Einstellungen → Mein Fire TV → Info → Netzwerk

Beim ersten `adb connect` auf dem TV „Zulassen“ bestätigen.

## 3. Installieren
```bash
cd scripts
./install.sh 192.168.1.50 ../app-release.apk
./debloat.sh 192.168.1.50                 # Werbung, Telemetrie, Alexa aus
./debloat.sh 192.168.1.50 --block-updates # optional: Fire-OS-Updates blockieren
```
Rückgängig machen: `./restore.sh 192.168.1.50`

## Apps
- Aus dem Amazon Appstore: JW Broadcasting, Netflix, Prime Video, YouTube, Disney+, ARD, ZDF, Joyn, RTL+, WOW, Apple TV+, Paramount+, DAZN, MagentaTV, waipu.tv/Zattoo, Spotify, Amazon Music, VLC.
- Per Downloader/ADB: SmartTube, Aurora Store.

## Hinweise
- `com.amazon.tv.launcher` wird **nicht** deaktiviert. Ohne ihn starten Einstellungen und Ersteinrichtung nicht.
- Wenn Fire OS den Bedienungshilfen-Dienst nach einem Update oder Neustart abschaltet, `install.sh` erneut ausführen.
- `--block-updates` stoppt auch Sicherheitsupdates. Vor größeren Updates `restore.sh` ausführen, dann updaten und anschließend erneut debloaten.
- Die APK ist mit dem Debug-Key signiert. Baust du auf einem anderen Rechner, vorher deinstallieren: `adb uninstall de.limad.tvlauncher`.
