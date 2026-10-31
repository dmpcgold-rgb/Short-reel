# ClipQueue — ReelShort-Merkliste und Offline-Player (Android 10+)

**Version 1.1.0 — Android-Projekt (Quellcode, noch keine APK).**

## Was geht?

- **Videodateien dauerhaft sichern:** Berechtigte, lokal verfügbare Videos (MP4, MOV, M4V, WebM) über Android-Dateiauswahl oder die Android-Teilen-Funktion importieren. Anders als die Vorversion kopiert ClipQueue die Dateien tatsächlich nach **Filme/ClipQueue** (MediaStore) und behält eigene Kopien, selbst wenn die ursprüngliche Quelle später gelöscht wird.
- **Offline-Playlist:** Serienfolgen in beliebiger Reihenfolge speichern, automatisch hintereinander spielen, hoch/runter wischen, Pause, Fortschrittsleiste, Wiederholung. Die Reihenfolge bleibt nach dem Neustart erhalten.
- **ReelShort-Serien merken:** Von reelshort.com geteilte Links einfügen, mit selbst gewähltem Serientitel speichern und später in ReelShort öffnen. **Ein gemerkter Link ist kein heruntergeladenes Video und funktioniert nur online.**
- **Direkte freigegebene Videodatei:** HTTPS-Links, die wirklich eine Videodatei statt einer ReelShort-Webseite zurückgeben, können ebenfalls unter Filme/ClipQueue heruntergeladen werden.

## Wichtige Einschränkung

ReelShort-Seitenlinks oder geschützte Streaming-Folgen werden **nicht** in MP4 umgewandelt. Es gibt weder DRM-Umgehung noch Scraping, Passwort-/Coins-Umgehung, Konto-Token-Abfrage oder Zugriff auf den App-Cache. Die offiziellen ReelShort-Nutzungsbedingungen untersagen Downloads ohne ausdrückliche Erlaubnis (https://www.reelshort.com/web-terms.html). Eine bereits als Videodatei vorhandene, erlaubte Datei lässt sich speichern und offline wiedergeben; ein normaler ReelShort-Link lässt sich lediglich als Link merken. ClipQueue ist nicht mit ReelShort verbunden.

## APK bauen

1. Projekt als Android-Studio-Projekt öffnen, JDK 17 und Android SDK 36 bereitstellen.
2. `gradle :app:testDebugUnitTest :app:assembleDebug` ausführen oder GitHub Actions Workflow `Android APK` starten.
3. GitHub Actions bietet die Debug-APK unter `ClipQueue-Android-Debug-APK` an.
4. Diese Projektversion wurde hier **nicht mit Android SDK gebaut und nicht auf einem Samsung getestet**.

## Technik

- Android Gradle Plugin 8.13.2, Java 17, Media3/ExoPlayer.
- Speichern über `MediaStore` mit `IS_PENDING` und Entfernung unvollständiger Kopien; max. 2 GB pro Videodatei.
- Speicherung läuft in einem Executor, App während des Kopierens offen lassen. Die Einträge werden einzeln zur Playlist hinzugefügt.
- `ACTION_SEND` unterstützt Textlinks und `video/*`-Dateien.
- Videos sind unter **Filme/ClipQueue** separat von der App-Datenbank sichtbar. „Aus Playlist entfernen“ löscht die Dateien nicht.
