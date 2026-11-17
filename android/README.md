# Fahrtenbuch – Android-App für Fernfahrer

Native Android-App (Kotlin, Jetpack Compose, Material 3), mit der du für die Steuererklärung
zählst, **wie viele Tage du unterwegs warst**: volle und halbe Tage in Deutschland und je Land
im Ausland, dazu die gefahrenen Kilometer. Die App zählt nur, sie berechnet keine Steuerbeträge.

- **Offline**, keine Anmeldung, kein Server. Alle Daten liegen in einer Room-Datenbank auf dem Handy.
  Internet wird nur für die Kartenbilder von OpenStreetMap genutzt (es werden keine eigenen Daten übertragen).
- Komplett auf Deutsch, helles und dunkles Design, dynamische Farben (Material You ab Android 12).
- Mindestversion: Android 8.0.

## Funktionen

| Bereich | Was die App kann |
|---|---|
| **Heute** | Großer Button „Heute erfassen“, „Mehrtägige Tour starten“, während einer Tour „Heute erfassen“, „Arbeit beginnen“, „Arbeit beenden“ (trägt mit einem Tipp die aktuelle Uhrzeit ein) und „Tour beenden“. Karten mit der Zusammenfassung von Woche und Jahr. |
| **Eingabe** | Datum (Standard: heute), Beginn und Ende der Abwesenheit, **Arbeitsbeginn und Arbeitsende** (für die Firma, ohne Einfluss auf die Steuer-Einordnung), Land (Suche, zuletzt genutzte Länder oben, Deutschland vorausgewählt), Kilometerstand morgens und abends mit automatischer Berechnung, Notiz. Fehlende Tage einer Tour werden auf Wunsch ergänzt. |
| **Einordnung** | Automatisch (siehe unten), bei jedem Tag manuell änderbar: Auto / Voll / Halb / Keiner. |
| **Verlauf** | Alle Tage nach Monaten mit Arbeitszeit, antippen zum Bearbeiten oder Löschen, Tage nachtragen. |
| **Auswertung** | Woche, Monat, Jahr oder gesamt. Volle und halbe Tage in Deutschland und je Auslandsland, Tage gesamt, km, Diagramme „Tage pro Land“ und „Kilometer pro Tag/Woche/Monat/Jahr“. |
| **Export** | Jahresübersicht als **PDF** und **CSV** (für Excel) über das Android-Teilen-Menü, mit Arbeitszeiten, Begründung je Tag und der aktiven Zählregel. |
| **Karte** | Fog-of-War-Karte: anfangs dunkelgrau verdeckt, überall wo du mit dem LKW gefahren bist freigelegt. Darunter aufgedeckte Fläche (km²), befahrene Länder und Strecke laut GPS. |
| **Datensicherung** | Alle Daten (inkl. GPS-Strecken) als Datei speichern und wiederherstellen (für einen Handywechsel). |

### So ordnet die App Tage ein

- **Voller Tag:** ein Tag mitten in einer mehrtägigen Tour (24 Stunden von zu Hause weg).
- **Halber Tag:** An- oder Abreisetag einer Tour, oder eine eintägige Fahrt mit **mehr als 8 Stunden** Abwesenheit.
- **Zählt nicht:** eintägige Fahrt mit 8 Stunden oder weniger.

**Firmenregel (Einstellungen, Standard: aus):** „Eintägige Fahrten unter 8 Stunden als halben Tag zählen“.
Ist sie an, zählt jede eintägige Fahrt als halber Tag. Weil das Finanzamt dafür mehr als 8 Stunden
verlangt, werden solche Tage in der Begründung, in der Auswertung und im Export (PDF: †, CSV: Spalte
„Firmenregel“) gekennzeichnet, und die aktive Regel steht im Kopf des Exports.

**Arbeitszeit (für die Firma):** „Arbeit beginnen“ / „Arbeit beenden“ auf dem Startbildschirm (mit und
ohne Tour). Eine begonnene Arbeitszeit läuft durchgehend weiter – auch über Mitternacht und mehrere
Tage – und wird automatisch an den Tagesgrenzen aufgeteilt (z. B. Sonntag 23:30–24:00, Montag
00:00–10:00). Der Startbildschirm zeigt, seit wann die Arbeit läuft. Tourstart beginnt die Arbeitszeit
automatisch, Tourende beendet sie mit. Arbeitszeiten haben keinen Einfluss auf die Steuer-Einordnung.

**Kilometer:** Der Start-km eines neuen Tages bzw. Arbeitsbeginns ist automatisch der letzte bekannte
km-Stand. Beim „Arbeit beenden“ und „Tour beenden“ fragt ein kleines Fenster nach dem km-Stand (mit
„Später“). Bei Arbeitszeiten über Mitternacht werden die km anteilig nach Arbeitszeit auf die Tage
verteilt (Summe stimmt genau, Kennzeichnung „~“ bzw. „km anteilig geschätzt“ im Export).

**Erinnerung:** Läuft eine Arbeitszeit länger als 15 Stunden (einstellbar, abschaltbar), erscheint
„Arbeit läuft seit 15 Std. – vergessen zu beenden?“ mit „Arbeit beenden“ und „Läuft noch“ (erneut nach
3 Stunden).

**Land per GPS (offline):** Bei Arbeitsbeginn, Arbeitsende und beim Öffnen der App nach Mitternacht
während einer laufenden Arbeitszeit wird das Land einmalig per GPS ermittelt – kein Dauer-GPS, kein
Internet. Die Ländergrenzen liegen in der App (`assets/country_borders.bin`, Natural Earth 1:10m; erzeugt
mit `tools/build_country_borders.py`). Genauigkeit an Grenzen ca. 1 km; von Hand gewählte Länder haben
immer Vorrang. Abschaltbar in den Einstellungen.

**Übernachtung im LKW:** Schalter pro Tag; bei mehrtägiger Tour automatisch für jede Nacht (außer am
Rückreisetag). Anzahl in Auswertung, PDF und CSV.

Eine Tour, die am selben Tag beginnt und endet, zählt wie eine eintägige Fahrt.

**Karte (Fog of War):** Eigener Reiter „Karte“ mit OpenStreetMap-Karte (osmdroid, ohne Google, ohne
API-Schlüssel). Darüber liegt dunkelgrauer Nebel, der entlang der gefahrenen Strecken mit weichem Rand
freigelegt wird. Breite des Korridors in den Einstellungen: 100 m (Standard), 500 m, 1 km oder 5 km – wirkt
rückwirkend, weil die Rohpunkte gespeichert sind. Beim Rauszoomen bleibt eine Mindestbreite von ein paar
Pixeln; die Strecken werden je Zoomstufe vereinfacht, damit die Karte auch mit vielen Monaten Daten flüssig
bleibt. Knöpfe „Mein Standort“ und „Europa“. Namensnennung „© OpenStreetMap-Mitwirkende“ mit Link; eigener
User-Agent, Kacheln werden zwischengespeichert, kein Massen-Download (Nutzungsbedingungen der OSM-Kacheln).

**Streckenaufzeichnung:** Startet automatisch bei „Arbeit beginnen“ und stoppt bei „Arbeit beenden“/„Tour
beenden“ (abschaltbar). Läuft als Vordergrunddienst mit Benachrichtigung „Strecke wird aufgezeichnet“, nur
mit dem GPS des Handys – funktioniert komplett offline, auch in Funklöchern. Messung jede Sekunde, gespeichert
wird etwa alle 30–50 m; ungenaue Messungen (> 25 m Ungenauigkeit) und Ausreißer werden verworfen, nach einer
Unterbrechung wird nur plausibel verbunden. Die Punkte werden kompakt (≈ 3–4 Byte pro Punkt) lokal gespeichert
und alle 10 Punkte gesichert. Die App erklärt die Standort-Berechtigung inkl. „Immer erlauben“ (nötig, damit
Android die Aufzeichnung nach Neustart/Update fortsetzen kann) und weist auf die Akku-Optimierung hin (Knopf
direkt zur Android-Einstellung). Ohne Berechtigung funktioniert der Rest der App normal. In den Einstellungen:
„Alle Strecken löschen“ (mit Rückfrage).

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

## Datenbank und Datensicherung

- Room-Datenbank Version 4 (Arbeitsschichten, Übernachtung, Land-Herkunft, GPS-Strecken in `track_chunks`).
  Für jede Änderung gibt es eine Migration (1 → 2 → 3 → 4); es wird nie `fallbackToDestructiveMigration` verwendet. Bisherige
  Arbeitszeiten pro Tag (auch von Hand an Mitternacht getrennte) werden unverändert als Schichten
  übernommen; die alten Spalten bleiben zusätzlich erhalten.
- **Vor jeder Migration** legt die App automatisch eine Sicherung an: die unveränderten
  Datenbankdateien im App-Speicher und eine wiederherstellbare Datensicherung unter
  `Download/Fahrtenbuch/` (ab Android 10).
- `MigrationTest` prüft das Update an Datenbanken im Stand der Versionen 1, 2 und 3 mit echten Einträgen
  (Gegenprobe: ohne Migration 3 → 4 schlägt das Öffnen fehl).
- Datensicherung Format-Version 4 (mit GPS-Strecken, Punkte als Base64); Sicherungen der Formate 1–3 lassen
  sich weiterhin laden – vorhandene Strecken bleiben dabei erhalten.

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
│   └── geo/   GPS-Filter, Speicherformat, Vereinfachung, Nebel-Geometrie, Flächenberechnung
├── export/    PDF- und CSV-Export, Teilen
├── work/      Arbeitszeit, Erinnerung, Streckenaufzeichnung (Vordergrunddienst)
└── ui/        Compose-Oberfläche: Heute, Verlauf, Karte, Auswertung, Einstellungen, Eingabe
```
