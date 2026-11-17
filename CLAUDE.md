# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

# Fahrtenbuch für Fernfahrer

Kurzübersicht für neue Sitzungen. Der Nutzer schreibt Deutsch, ist kein Entwickler und arbeitet
meist vom Tablet/Handy aus. Antworten auf Deutsch, in einfachen Worten.

## Was ist im Repository

| Pfad | Inhalt |
|---|---|
| `android/` | **Haupt-Projekt:** native Android-App „Fahrtenbuch“ (Kotlin, Jetpack Compose, Material 3, Room). Details: `android/README.md` |
| Wurzel (`index.html`, `app.js`, `sw.js`, …) | Ältere Web-App (PWA), wird per `.github/workflows/pages.yml` auf GitHub Pages veröffentlicht. Nicht mehr aktiv weiterentwickelt. |
| `.github/workflows/android.yml` | Baut bei PRs (Tests + unsignierte APK) und bei Merge auf `main` (Tests + signierte APK + GitHub-Release `v1.0.<Laufnummer>`) |

## Zweck der App

Zählt für die Steuererklärung eines Fernfahrers die Tage unterwegs (voll/halb, je Land),
Kilometer, Arbeitszeit für die Firma und Übernachtungen im LKW; dazu eine Fog-of-War-Karte der
gefahrenen Strecken mit Übernachtungsplätzen und Meilensteinen. Kein Server, kein Login, alle Daten
lokal; Internet nur für die OpenStreetMap-Kartenkacheln. Berechnet keine Steuerbeträge.

## Aufbau der App (`android/app/src/main/java/de/fernfahrer/fahrtenbuch/`)

- `data/` – Room-Datenbank (`AppDatabase`, Version **5**), Tabellen `days`, `tours`, `work_shifts`,
  `track_chunks` (GPS-Strecken, Punkte kompakt als BLOB; `TrackRepository`, `TrackChunks`),
  `stop_locations` (Standort bei „Arbeit beenden“ → Übernachtungsorte), `milestones` (`StopDao`);
  `Repository` (gesamte Speicherlogik: Touren, Arbeitsschichten, km-Übernahme, Tour beenden);
  `Backup` (JSON-Datensicherung, Format **5** mit Strecken, Orten, Meilensteinen, liest 1–4); `PreMigrationBackup` (Sicherung vor
  jeder Migration); `Settings` (SharedPreferences)
- `domain/` – reine Logik ohne Android: `Classifier` (voll/halb/Firmenregel/Übernachtung),
  `WorkSplitter` (Schichten an Mitternacht teilen, km anteilig verteilen), `Stats`, `Period`,
  `Countries`, `Format`; `domain/geo/`: `TrackFilter` (Punkte alle 30–50 m, ungenaue/Ausreißer verwerfen),
  `TrackCodec` (Delta-Varint), `Simplify`, `FogGeometry` (Detailstufen + Gruppen), `Overnights`
  (Übernachtungsplätze, Zusammenfassen im Umkreis 200 m); `Milestones` (Berechnung mit Datum),
  `MilestoneTracker` (erstes Mal und nach Wiederherstellen still freischalten)
- `export/` – PDF (`PdfExporter`, Android `PdfDocument`) und CSV (Excel, Semikolon, BOM), Teilen
- `location/` – `CountryLocator` (Offline-Ländererkennung aus `assets/country_borders.bin`,
  Natural Earth 1:10m, erzeugt mit `android/tools/build_country_borders.py`), `PlaceLocator` (nächster Ort
  offline aus `assets/places.bin`, GeoNames cities1000, erzeugt mit `android/tools/build_places.py`), `LocationProvider`
  (einzelne GPS-Abfrage, kein Dauer-GPS)
- `work/` – `WorkController` (Arbeit beginnen/beenden, Tour beenden, GPS-Land, Erinnerung, Aufzeichnung),
  `TrackingService` (Vordergrunddienst Typ location, Plattform-GPS), `TrackRecorder`, `Tracking` (Start/Stopp,
  Berechtigungen, Akku-Optimierung), `StopLocator` (Position beim Arbeitsende),
  `ReminderScheduler`/`ReminderReceiver` (Erinnerung nach 15 Std.), `BootReceiver`
- `ui/` – `AppRoot`: Kopfzeile mit Titel + Zahnrad (Einstellungen) und Kacheln „Fahrtenbuch“/„Karte“
  (letzte Ansicht in `Settings.lastViewMap`); untere Leiste nur im Fahrtenbuch (Heute, Verlauf, Auswertung).
  Bildschirme: `home` (Start), `history` (Verlauf), `map` (Fog-of-War-Karte mit osmdroid,
  `FogRenderer`/`FogOverlay`, `MapIcons` (LKW, Mond), `MilestonesUi` (Übersicht, Meldung mit Animation,
  Übernachtungs-Dialog), `MapViewModel`, `MapConfig` mit User-Agent/Cache), `stats` (Auswertung),
  `settings`, `editor` (Tag bearbeiten, Schicht-Dialog), `components` (km-Dialog,
  Berechtigungs-Erklärung, Länderauswahl …), `MainViewModel`, `AppRoot` (Navigation)

## Zusammenspiel (über mehrere Dateien)

- **Einstieg:** `FahrtenbuchApp.onCreate` erzeugt alles von Hand (keine DI): zuerst
  `PreMigrationBackup.runIfNeeded`, dann `AppDatabase`, `Repository`, `TrackRepository`,
  `LocationProvider`, `ReminderScheduler`, `Tracking`, `WorkController`, `MapConfig.init`.
  ViewModels holen sich diese Objekte über ihre `Factory` aus der App.
- **Arbeitszeit ist der zentrale Auslöser:** Alle Wege zu „Arbeit beginnen/beenden“ und „Tour beenden“
  (Startbildschirm, Benachrichtigung, Editor) laufen über `WorkController`. Er plant die Erinnerung,
  startet/stoppt per `TrackingControl.sync()` den `TrackingService`, ermittelt das Land per GPS und
  speichert über `StopLocator` den Standort beim Arbeitsende. `onForeground()` (beim Öffnen der App)
  und `BootReceiver` gleichen alles wieder an die laufende Schicht an.
- **Einordnung der Tage wird nie gespeichert,** sondern immer neu berechnet:
  `Repository.classifiedDays` = `Classifier.classifyAll(days, tours, rules)`. Darauf bauen Auswertung,
  Export, Übernachtungsplätze (`ClassifiedDay.overnight`) und Meilensteine auf.
- **Karte:** `TrackingService` → `TrackRecorder` (Filter `TrackFilter`, Stücke à 500 Punkte, jedes Folgestück
  beginnt mit dem letzten Punkt des vorigen) → `track_chunks`. `MapViewModel` beobachtet nur die Metadaten,
  dekodiert geänderte Stücke neu und baut `FogGeometry`; `FogOverlay`/`FogRenderer` zeichnen den Nebel.
  Übernachtungsplätze = Übernachtungstage + `stop_locations` (sonst letzter GPS-Punkt des Tages).
- **Meilensteine:** `MainViewModel` berechnet sie laufend aus Tagen und Strecken (`Milestones.evaluate`),
  `MilestoneTracker` speichert neue in `milestones` und entscheidet, ob eine Meldung erscheint.
- **Datensicherung:** Neue Tabellen/Felder müssen an drei Stellen ergänzt werden: `Backup` (JSON hin und
  zurück), `Repository.exportData`/`replaceAll` und `PreMigrationBackup.readOldDatabase` (liest alte
  Datenbanken roh per SQL, Tabellen/Spalten jeweils optional).

## Wichtige Regeln (unbedingt einhalten)

1. **Room-Migrationen nie destruktiv.** Jede Schemaänderung bekommt eine eigene `Migration` in
   `AppDatabase.MIGRATIONS`. Niemals `fallbackToDestructiveMigration` oder Löschen/Neuanlegen der
   Datenbank. Bestehende Spalten nicht entfernen. `AppDatabase.VERSION` erhöhen, das exportierte
   Schema (`android/app/schemas/…/<n>.json`) mit committen, die Migration in `MigrationTest` mit
   einer Datenbank im alten Stand **mit Einträgen** testen (Gegenprobe: ohne Migration muss der Test
   fehlschlagen). `PreMigrationBackup` sichert vor jeder Migration automatisch.
2. **Datensicherung abwärtskompatibel halten:** neues Feld → `Backup.VERSION` erhöhen, alte
   Formate müssen weiter ladbar sein (Tests in `BackupAndCsvTest`).
3. **Signierschlüssel nicht ändern.** Die APK wird mit einem geheimen Schlüssel aus den
   GitHub-Secrets `SIGNING_KEYSTORE_BASE64` und `SIGNING_PASSWORD` signiert (Alias `fahrtenbuch`,
   Zertifikat SHA-256 beginnt mit `2ec33a…`). Keine Schlüsseldateien ins Repository (`*.jks` steht
   in `.gitignore`). Ein anderer Schlüssel würde Updates über die installierte App verhindern.
4. **Selbst mergen, wenn grün:** Änderungen auf einem Branch, Pull Request öffnen, warten bis der
   GitHub-Build (`build`) grün und der PR konfliktfrei ist, dann selbst mergen. Nach dem Merge prüfen,
   dass das Release mit APK erschienen ist (Signatur `2ec33a…`), und dem Nutzer den Download-Link
   nennen. Schlägt der Build fehl, zuerst den Fehler beheben, dann mergen.
5. Vor größeren Änderungen offene Fragen klären; der Nutzer bestätigt den Start oft ausdrücklich.
6. App-Texte komplett Deutsch. Daten bleiben lokal auf dem Handy.

## Bauen und testen

```bash
cd android
./gradlew testDebugUnitTest      # Unit- und Robolectric-Tests (Migration, Repository, Erinnerung …)
./gradlew testDebugUnitTest --tests '*MigrationTest'                       # eine Testklasse
./gradlew testDebugUnitTest --tests '*MigrationTest.updateVonVersion4Auf5*' # ein einzelner Test
./gradlew assembleRelease        # ohne Signier-Variablen: app-release-unsigned.apk (mit R8)
```

Kein eigener Lint-Schritt; `assembleRelease` führt `lintVitalRelease` aus. Testergebnisse:
`app/build/test-results/testDebugUnitTest/*.xml`.

Tests (alle unter `app/src/test`, kein Gerät nötig):
- Robolectric-Tests laufen mit `@Config(sdk = [34])`; der Nebel und die Kartensymbole mit
  `@GraphicsMode(NATIVE)` (echte Grafik, Pixel werden geprüft).
- `MigrationTest` legt alte Datenbanken exakt nach `app/schemas/…/<n>.json` an (Arbeitsverzeichnis der
  Tests ist `android/app`, daher relative Pfade wie `schemas/…` oder `src/main/assets/places.bin`).
- GPS im Test: `ShadowLocationManager.simulateLocation` filtert nach Mindestintervall – den Messungen
  daher steigende `elapsedRealtimeNanos` geben (siehe `TrackingServiceTest`).
- `WorkController` hat mehrere optionale Lambda-Parameter (`clock`, `tracking`, `stopLocator`); im Test
  immer benannt übergeben (`clock = { now }`), sonst bindet die nachgestellte Lambda an den letzten.

Hinweise für die Cloud-Umgebung:
- Android-SDK liegt (falls vorhanden) unter `/opt/android-sdk`; sonst Kommandozeilen-Tools von
  `dl.google.com` laden (`platforms;android-35`, `build-tools;35.0.0`) und `android/local.properties`
  mit `sdk.dir=…` anlegen (nicht committen).
- Maven Central liefert hier oft HTTP 429. Lokal hilft ein Gradle-Init-Skript
  (`~/.gradle/init.d/`), das den Spiegel `https://maven-central.storage-download.googleapis.com/maven2/`
  für Plugins, Abhängigkeiten und `robolectric.dependency.repo.url` einträgt. Nicht ins Projekt
  übernehmen.
- Oberfläche prüfen: temporärer Robolectric-Screenshot-Test (nicht committen), z. B.
  `Robolectric.buildActivity(MainActivity::class.java).setup()` mit `@GraphicsMode(NATIVE)`, danach
  `window.decorView.draw(Canvas(bitmap))`. Kartenkacheln laden dort nicht. PDF-Erzeugung lässt sich unter
  Robolectric nicht testen.
- Signatur eines Releases prüfen: `apksigner verify --print-certs <apk>` (build-tools 35.0.0).
- Datendateien in `assets/` werden mit den Skripten in `android/tools/` erzeugt (Quellen und Lizenzen im
  Kopf der Skripte); die Ergebnisdateien sind eingecheckt.

## Zuletzt geändert

- **Umschalter Fahrtenbuch/Karte, LKW-Symbol, Übernachtungsplätze, Meilensteine (DB v5, Backup 5):**
  Einstellungen über Zahnrad, km²-Kachel entfernt (Statistik: km laut GPS, Länder), Ortsnamen offline.

- **Fog-of-War-Karte (DB v4, Backup-Format 4):** Reiter „Karte“ (osmdroid + OSM-Kacheln), Nebel mit weichem
  Rand, Korridor 100 m/500 m/1 km/5 km, Aufzeichnung als Vordergrunddienst während der Arbeitszeit,
  Statistik (km², Länder, GPS-km), „Alle Strecken löschen“. Neu: Berechtigungen `INTERNET` (nur Kacheln),
  `ACCESS_BACKGROUND_LOCATION`, `FOREGROUND_SERVICE_LOCATION`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
  Robolectric-Tests mit echter Grafik (`@GraphicsMode(NATIVE)`) für den Nebel.

- **PR #6 / Release 1.0.8:** Arbeitszeit als Schichten über Mitternacht (automatische Teilung),
  Start-km automatisch, km-Abfrage bei „Arbeit beenden“/„Tour beenden“, Erinnerung nach 15 Std.,
  Land per GPS offline (Handauswahl hat Vorrang), Übernachtung im LKW, Datenbank v3 mit Migration
  und Sicherung vor Update, 64 Tests.
- **PR #5 / 1.0.6:** Firmenregel (eintägig ≤ 8 Std. als halber Tag), Arbeitszeit pro Tag, DB v2.
- **PR #4 / 1.0.4:** Signierung nur noch über GitHub-Secrets.
- **PR #3 / 1.0.2:** erste native Android-App.

## Offen / geplant

- Land jedes Tages aus dem letzten GPS-Punkt vor 24 Uhr ableiten (war im ersten Karten-Plan, noch nicht umgesetzt).
