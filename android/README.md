# Fahrtenbuch – Android-App für Fernfahrer

Native Android-App (Kotlin, Jetpack Compose, Material 3), mit der du für die Steuererklärung
zählst, **wie viele Tage du unterwegs warst**: volle und halbe Tage in Deutschland und je Land
im Ausland, dazu die gefahrenen Kilometer. Die App zählt nur, sie berechnet keine Steuerbeträge.

- Komplett **offline**, keine Anmeldung, kein Server. Alle Daten liegen in einer Room-Datenbank auf dem Handy.
- Komplett auf Deutsch, helles und dunkles Design, dynamische Farben (Material You ab Android 12).
- Mindestversion: Android 8.0.

## Funktionen

| Bereich | Was die App kann |
|---|---|
| **Heute** | Großer Button „Heute erfassen“, „Mehrtägige Tour starten“, während einer Tour „Heute erfassen“ und „Tour beenden“. Karten mit der Zusammenfassung von Woche und Jahr. |
| **Eingabe** | Datum (Standard: heute), Beginn und Ende der Abwesenheit, Land (Suche, zuletzt genutzte Länder oben, Deutschland vorausgewählt), Kilometerstand morgens und abends mit automatischer Berechnung, Notiz. Fehlende Tage einer Tour werden auf Wunsch ergänzt. |
| **Einordnung** | Automatisch (siehe unten), bei jedem Tag manuell änderbar: Auto / Voll / Halb / Keiner. |
| **Verlauf** | Alle Tage nach Monaten, antippen zum Bearbeiten oder Löschen, Tage nachtragen. |
| **Auswertung** | Woche, Monat, Jahr oder gesamt. Volle und halbe Tage in Deutschland und je Auslandsland, Tage gesamt, km, Diagramme „Tage pro Land“ und „Kilometer pro Tag/Woche/Monat/Jahr“. |
| **Export** | Jahresübersicht als **PDF** und **CSV** (für Excel) über das Android-Teilen-Menü. |
| **Datensicherung** | Alle Daten als Datei speichern und wiederherstellen (für einen Handywechsel). |

### So ordnet die App Tage ein

- **Voller Tag:** ein Tag mitten in einer mehrtägigen Tour (24 Stunden von zu Hause weg).
- **Halber Tag:** An- oder Abreisetag einer Tour, oder eine eintägige Fahrt mit **mehr als 8 Stunden** Abwesenheit.
- **Zählt nicht:** eintägige Fahrt mit 8 Stunden oder weniger.

Eine Tour, die am selben Tag beginnt und endet, zählt wie eine eintägige Fahrt.

## APK herunterladen und installieren

Bei jedem Merge auf `main` baut GitHub Actions (`.github/workflows/android.yml`) automatisch eine
signierte APK und stellt sie als **Release** bereit:

1. Auf GitHub im Repository rechts auf **Releases** tippen, oder direkt
   `https://github.com/<besitzer>/<repo>/releases/latest` öffnen.
2. Unter **Assets** die Datei `Fahrtenbuch-1.0.X.apk` herunterladen.
3. Die Datei öffnen. Beim ersten Mal fragt Android, ob der Browser „unbekannte Apps installieren“ darf:
   erlauben, zurück und **Installieren** tippen.
4. Updates werden genauso installiert. Die Daten bleiben dabei erhalten.

## Signierung

Jede APK wird mit demselben **geheimen** Schlüssel signiert, damit sich neue Versionen als Update
installieren lassen. Der Schlüssel liegt **nicht** im Repository, sondern in zwei GitHub-Secrets
(`Settings → Secrets and variables → Actions → Repository secrets`):

| Secret | Inhalt |
|---|---|
| `SIGNING_KEYSTORE_BASE64` | Die Keystore-Datei (`.jks`) als Base64-Text |
| `SIGNING_PASSWORD` | Passwort des Keystores (gleichzeitig Passwort des Schlüssels) |

Der Schlüssel-Alias ist `fahrtenbuch`.

- Bei einem Merge auf `main` **ohne** diese Secrets bricht der Workflow mit einer Fehlermeldung ab.
  Es wird nie eine unsignierte App veröffentlicht.
- Bei Pull Requests ohne Secrets wird nur eine unsignierte Test-APK gebaut.
- **Bewahre Keystore und Passwort sicher auf** (z. B. im Passwort-Manager). Gehen sie verloren,
  lässt sich die App nicht mehr aktualisieren, sondern nur deinstallieren und neu installieren.
- Ein früherer Schlüssel lag öffentlich in `app/signing/`. Er wurde entfernt und darf nicht mehr
  verwendet werden. Mit ihm signierte Versionen (bis 1.0.2) müssen einmal deinstalliert werden,
  bevor die neue Version installiert werden kann. Vorher eine Datensicherung speichern!

Lokal signiert bauen:

```bash
SIGNING_KEYSTORE_PATH=/pfad/zum/keystore.jks SIGNING_PASSWORD=… ./gradlew assembleRelease
```

## Selbst bauen

Voraussetzungen: JDK 17 oder neuer und das Android SDK (Platform 35).

```bash
cd android
./gradlew testDebugUnitTest     # Tests
./gradlew assembleRelease       # ohne Signier-Variablen: app-release-unsigned.apk
```

## Aufbau

```
app/src/main/java/de/fernfahrer/fahrtenbuch/
├── data/      Room-Datenbank (Tage, Touren), Repository, Datensicherung, Einstellungen
├── domain/    Einordnung (voll/halb), Zeiträume, Auswertung, Länder, Formatierung
├── export/    PDF- und CSV-Export, Teilen
└── ui/        Compose-Oberfläche: Heute, Verlauf, Auswertung, Einstellungen, Eingabe
```
