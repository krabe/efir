# Efir (Android)

Live-TV-Player für Android mit zwei Quellen:
- **LimeHD-API** (Port von limehd_core.py, ohne Proxy): Senderliste, Kategorien, Stream-URL
- **M3U-Playlist** (+ optional XMLTV-Programmführer)

Außerdem: Suche, Gruppen, Qualitätsbegrenzung, Vollbild. Abgelaufene Stream-Token werden bei
einem Player-Fehler automatisch neu geholt.

## APK bauen
**Android Studio:** Ordner öffnen, Gradle-Sync, *Build > Build APK(s)*.
**GitHub:** Ordner in ein Repo pushen; unter *Actions > build-apk* liegt `app-debug.apk`.

## Hinweise
- Die LimeHD-Schnittstelle ist inoffiziell und kann sich ändern.
- Feldnamen für Name/Logo/Kategorie der Senderliste sind angenommen (siehe LimeApi.kt).
- Programmführer der LimeHD-API ist noch nicht eingebaut.
