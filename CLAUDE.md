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

## Aufbau der App – welche Datei wofür (`android/app/src/main/java/de/fernfahrer/fahrtenbuch/`)

Grundsatz: **`data/`** speichert, **`domain/`** rechnet (reine Logik ohne Android, gut testbar),
**`work/`** und **`location/`** sprechen mit Android-Diensten (GPS, Benachrichtigungen, Wecker),
**`ui/`** zeigt an. In `ui/` je Bildschirm: `…Screen.kt` (Aufbau der Seite), `…ViewModel.kt` / `…State.kt`
(Zustand und Abläufe), weitere Dateien mit den einzelnen Karten/Bausteinen (nur Aussehen).

**Start:** `FahrtenbuchApp.kt` (erzeugt alle Dienste, Sicherung vor Migration), `MainActivity.kt`
(Einstieg, „Arbeit beenden“ aus der Benachrichtigung).

**`data/` – Speichern**
| Datei | Zuständig für |
|---|---|
| `AppDatabase.kt` | Room-Datenbank, Version **8**, alle Migrationen (`MIGRATIONS`) |
| `DayEntity.kt`, `TourEntity.kt`, `WorkShiftEntity.kt` | Tabellen `days` (mit `countries` = alle Länder des Tages, `travelText` = eigener Reiseanlass, null = automatisch), `tours`, `work_shifts` |
| `TrackChunkEntity.kt` | Tabelle `track_chunks` (GPS-Strecke, Punkte als BLOB) + `TrackChunkMeta` (ohne Punkte) |
| `StopLocationEntity.kt`, `MilestoneEntity.kt` | Tabellen `stop_locations` (alte Standorte bis v5, werden nicht mehr angelegt), `milestones` |
| `OvernightPlaceEntity.kt` | Tabellen `overnight_places` (Platz mit Name, Sternen, Ausstattung, Notiz) und `overnight_stays` (einzelne Übernachtungen); `OvernightPlace` |
| `ExpenseEntities.kt` | Tabellen `expense_years` (Jahr + Übernachtungspauschale) und `expense_rates` (Jahr + Land: voller/halber Tag), Beträge in Cent, null = leer; `ExpenseYear` |
| `ExpenseDao.kt`, `ExpenseRepository.kt` | Spesentabellen lesen/speichern; `ensureYear` legt ein Jahr als Kopie des jüngsten früheren Jahres an (sonst leer); `book`/`getBook()` = alle Jahre als `ExpenseBook` |
| `OvernightImport.kt` | einmalige Übernahme der bis v5 berechneten Übernachtungsplätze (ohne heute) nach Update/Wiederherstellen alter Sicherung |
| `FahrtenbuchDao.kt`, `TrackDao.kt`, `StopDao.kt` | Datenbank-Abfragen (Tage/Touren/Schichten, Strecken, Standorte/Meilensteine/Übernachtungsplätze) |
| `Converters.kt` | Datum ↔ Text für Room |
| `Repository.kt` | gesamte Speicherlogik für Tage, Touren, Arbeitsschichten (Arbeit beginnen/beenden, km-Übernahme, Tour beenden), Länder per GPS/Grenzübertritt, Übernachtungsplätze, Export/Import aller Daten |
| `RepositoryResults.kt` | Ergebnis-Typen `StartResult`, `EndTourResult` |
| `TrackRepository.kt`, `TrackChunks.kt` | Strecken speichern/laden; Streckenstück aus Punkten bauen |
| `Settings.kt` | Einstellungen (SharedPreferences), u. a. Name (`driverName`, in der App „Name“), Verbrauch, automatisches Backup (Ordner-URI, letztes Backup), Erinnerung an die Abrechnung; `rules` ist fest die gesetzliche Regel |
| `BackupModels.kt` | Datenmodelle der Sicherung (`BackupData` inkl. `expenseYears`, `WorkSettings`, `MapSettings` mit Verbrauch, `AutoBackupSettings`, `LegacyWork`) |
| `Backup.kt` | Datensicherung als JSON, Format **8** (liest 1–7), Spesentabellen unter `expenseTables`, Reiseanlass je Tag `travelText` |
| `BackupFiles.kt` | Sicherungsdatei lesen/schreiben (Speicherort vom Nutzer gewählt) |
| `BackupFolder.kt` | Ordner für automatische Backups (`SafBackupFolder`: Android-Ordnerauswahl, auch Google Drive) |
| `PreMigrationBackup.kt` | automatische Sicherung vor jeder Migration (Kopie + JSON unter Download/Fahrtenbuch) |
| `OldDatabaseReader.kt` | liest alte Datenbanken roh per SQL (für `PreMigrationBackup`) |

**`domain/` – Logik ohne Android**
| Datei | Zuständig für |
|---|---|
| `Classification.kt` | `Classifier`: voller/halber Tag, Firmenregel (nur für die Reisekostenabrechnung), Übernachtung im LKW |
| `DayCountries.kt` | mehrere Länder pro Tag; **zentrale Regel** `countingCountry(day)` = letztes ausländisches Land (Ausland hat Vorrang), sonst DE; überall über `ClassifiedDay.country` nutzen |
| `Expenses.kt` | `Euro` (Eingabe „14,00“ ↔ Cent, `money` „1.234,56 €“), `ExpenseRate`, `ExpenseTables` (Kopierquelle, auswählbare Jahre, Länder ohne Betrag) |
| `EuroCalc.kt` | Euro-Berechnung: `ExpenseBook` (Tabelle je Jahr), `Nights` (Pauschale am **Tag mit „Übernachtung im LKW“** = Abend, an dem man sich schlafen legt; Ankunftstag nein; Kartenpunkt egal), `EuroCalc.summarize`/`perDay`/`missingYears`; fehlende Beträge = null, nie 0 € |
| `TravelReport.kt` | monatliche Reisekostenabrechnung: `TravelReports.build` (Zeilen, Summen, fehlende Beträge, laufender Tag weggelassen), automatischer Reiseanlass `autoText` (je Arbeitsbeginn „Abfahrt <Ort>“, je Arbeitsende „Ankunft <Ort>“ am Tag des Geschehens, „Übernachtung …“ genau an Tagen mit Pauschale), Dateiname |
| `WorkSplitter.kt` | Schichten an Mitternacht teilen, km anteilig verteilen |
| `Stats.kt`, `Period.kt` | Auswertung je Woche/Monat/Jahr (Übernachtungen wie `Nights`, `shortDays` = eintägig unter 8 Std.) |
| `Countries.kt`, `Format.kt` | Ländernamen/Flaggen, deutsche Formatierung |
| `Milestones.kt` | Meilensteine berechnen (mit Datum des Erreichens) |
| `MilestoneInputs.kt` | Eingabe für die Meilensteine aus Tagen und GPS-Strecken |
| `MilestoneTracker.kt` | neue Meilensteine speichern; erstes Mal und nach Wiederherstellen still |
| `geo/Geo.kt` | Entfernungen, Web-Mercator |
| `geo/TrackFilter.kt` | welche GPS-Messung ein Streckenpunkt wird (30–50 m, ungenaue/Ausreißer verwerfen) |
| `geo/TrackCodec.kt` | kompaktes Speicherformat der Punkte (Delta-Varint) |
| `geo/Simplify.kt`, `geo/FogGeometry.kt` | Linien vereinfachen; Strecken in Detailstufen und Gruppen für den Nebel |
| `geo/Overnights.kt` | alte Berechnung der Übernachtungsplätze (nur noch für `OvernightImport`), Umkreis 200 m |
| `geo/BorderDetector.kt` | Grenzübertritt erst nach 5 Min. **und** 3 km im neuen Land |

**`export/`:** `YearExport.kt` (Jahresübersicht erzeugen, `ExportFormat`), `PdfExporter.kt`, `CsvExporter.kt`
(Excel, Semikolon, BOM; beide mit Euro-Beträgen), `Share.kt` (Teilen-Menü). Reisekostenabrechnung:
`TravelReportSource.kt` (sammelt Tage nach **Firmenregel** `COMPANY_RULES`, Arbeitszeiten, Spesentabellen, Orte aus
GPS-Strecke je Arbeitsbeginn/-ende (±45 Min.) und Übernachtungsplätzen), `TravelReportLayout.kt` (Spalten, Zellen, Seitenaufteilung – ohne Android, testbar),
`TravelReportPdf.kt` (zeichnet die Seiten; `drawPage` auch für Vorschaubilder).

**`location/`:** `LocationProvider.kt` (einzelne GPS-Abfrage, lädt die Offline-Daten), `CountryLocator.kt`
(Land aus `assets/country_borders.bin`, Natural Earth, Skript `tools/build_country_borders.py`),
`PlaceLocator.kt` (nächster Ort aus `assets/places.bin`, GeoNames, Skript `tools/build_places.py`).

**`work/` – Hintergrund**
| Datei | Zuständig für |
|---|---|
| `WorkController.kt` | zentrale Abläufe Arbeit beginnen/beenden, Tour beenden (Erinnerung, Aufzeichnung, GPS-Land, Standort) |
| `TrackingService.kt` | Vordergrunddienst der Streckenaufzeichnung (Plattform-GPS, Benachrichtigung) |
| `TrackRecorder.kt` | Punkte filtern und in Stücken speichern |
| `Tracking.kt` | Aufzeichnung starten/stoppen, Berechtigungen, Akku-Optimierung |
| `StopLocator.kt` | Position beim Arbeitsende (für den Übernachtungsplatz mit Haken) |
| `BorderWatcher.kt` | Streckenpunkte → `BorderDetector` → `WorkController.onBorderCrossed` |
| `ReportReminder.kt` | Erinnerung am 1. um 18 Uhr „Reisekostenabrechnung für … erstellen?“ (WorkManager, nachholen bis zum 7.), öffnet die Vorschau des Vormonats |
| `AutoBackup.kt` | automatisches Backup: Fälligkeit, Schreiben, alte löschen, WorkManager-Auftrag (`AutoBackupWorker`, alle 6 Std. prüfen), Fehler-Benachrichtigung |
| `ReminderScheduler.kt`, `ReminderReceiver.kt` | Erinnerung „Arbeit läuft seit 15 Std.“ |
| `BootReceiver.kt` | nach Neustart/Update Erinnerung und Aufzeichnung wiederherstellen |

**`ui/` – Oberfläche**
| Datei | Zuständig für |
|---|---|
| `AppRoot.kt` | Navigation, untere Leiste (nur Fahrtenbuch: Heute, Verlauf, Auswertung), Meilenstein-Meldung |
| `MainHeader.kt` | Kopfzeile: Titel, Zahnrad, Kacheln „Fahrtenbuch“/„Karte“ |
| `MainViewModel.kt` | gemeinsamer Zustand und Aktionen (Arbeitszeit, km-Abfrage, Export, Sicherung, Einstellungen) |
| `MilestoneState.kt` | Meilensteine und Warteschlange der Meldungen (Teil des `MainViewModel`) |
| `UiModels.kt` | `ActiveTour`, `KmPrompt` |
| `theme/Theme.kt` | Farben und Schriften |
| `components/Common.kt` | `SectionCard`, `StatTile`, `DayTypeBadge` u. a. |
| `components/KmDialog.kt` | km-Abfrage beim Beenden, bei „Arbeit beenden“ mit Haken „Übernachtungsplatz auf der Karte speichern“ |
| `components/StartWorkFlow.kt` | „Arbeit beginnen“ mit Berechtigungs-Erklärung (inkl. „Immer erlauben“) |
| `components/CountryPicker.kt`, `components/Pickers.kt` | Länderauswahl, Datum-/Uhrzeit-Dialoge |
| `home/HomeScreen.kt` | Startbildschirm „Heute“ |
| `home/ActionCards.kt`, `home/WorkCard.kt`, `home/SummaryCards.kt` | Erfassen-/Tour-Karten, Arbeitszeit-Karte, Woche/Jahr |
| `history/HistoryScreen.kt` | Verlauf |
| `stats/StatsScreen.kt`, `stats/StatsWidgets.kt`, `stats/Charts.kt` | Auswertung, Zeitraum-Auswahl/Auslandstabelle, Diagramme |
| `stats/EuroCard.kt` | Karte „Spesen in Euro“ (je Land, Übernachtungspauschale, Gesamt bzw. „unvollständig“ + Knopf zur Spesentabelle) |
| `report/ReportScreen.kt`, `report/ReportViewModel.kt`, `report/TravelTextDialog.kt` | Reisekostenabrechnung (Route `abrechnung?monat=…`): Monat, Vorschau, Reiseanlass ändern/zurücksetzen, Warnung bei fehlenden Beträgen, PDF teilen/speichern |
| `editor/EditorScreen.kt` | Eintrag-Bildschirm (Aufbau, Dialoge) |
| `editor/EditorSections.kt` | Abschnitte: Abwesenheit, Arbeitszeit, Länder (mehrere), km, fehlende Tage, Übernachtung, Einordnung, Notiz |
| `editor/EditorWidgets.kt` | kleine Bausteine (Feldkarte, Zeit-Kachel, Umschalter …) |
| `editor/EditorViewModel.kt`, `editor/EditorState.kt`, `editor/EditorRules.kt` | Zustand und Speichern; Zustands-Klassen; Eingabeprüfung |
| `editor/ShiftDialog.kt` | Arbeitszeit bearbeiten |
| `settings/SettingsScreen.kt` | Einstellungen (Fahrer, Sicherung, Regeln, Über), Dialoge |
| `settings/WorkSettingsCard.kt`, `settings/MapSettingsCard.kt`, `settings/SettingsWidgets.kt` | Karten Arbeitszeit und Karte (inkl. Dieselverbrauch); Schalter/Berechtigungs-Hinweise |
| `settings/AutoBackupSection.kt` | automatisches Backup im Bereich Datensicherung |
| `expenses/ExpenseScreen.kt`, `expenses/ExpenseDialogs.kt`, `expenses/ExpenseViewModel.kt` | Spesentabelle (eigener Bildschirm, Route `spesentabelle?jahr=…`, aus den Einstellungen und „Betrag fehlt“): Jahr-Auswahl, Pauschale, Länderbeträge, Hinweis auf fehlende Beträge; Dialoge mit Euro-Feld |
| `map/MapScreen.kt` | Kartenansicht (Aufbau, Hinweise, Statistik, Dialoge) |
| `map/FogMap.kt` | osmdroid-Karte mit Nebel, LKW, Übernachtungs-Symbolen (mit Sternen), Knöpfen und „Versetzen“-Modus |
| `map/MapViewModel.kt` | Strecken laden, `FogGeometry` bauen, Statistik (inkl. Liter Diesel aus Tacho-km), Übernachtungsplätze bearbeiten |
| `map/FogRenderer.kt`, `map/FogOverlay.kt` | Nebel zeichnen (unabhängig von osmdroid) bzw. als osmdroid-Ebene |
| `map/MapIcons.kt` | LKW- und Mond-Symbol |
| `map/MapWidgets.kt` | Aufzeichnungs-Anzeige, Hinweiskarte, OSM-Namensnennung, drei Statistik-Kacheln |
| `map/MilestonesSheet.kt`, `map/MilestoneCelebration.kt`, `map/OvernightSheet.kt` | Meilenstein-Übersicht, Meldung mit Animation, Übernachtungsplatz (Umbenennen, Versetzen, Löschen, Notizen, einzelne Übernachtungen) |
| `map/TrackingPermissions.kt` | Berechtigungs-Stand und -Texte der Aufzeichnung |
| `map/MapConfig.kt` | osmdroid: User-Agent, Kachel-Zwischenspeicher |

## Zusammenspiel (über mehrere Dateien)

- **Einstieg:** `FahrtenbuchApp.onCreate` erzeugt alles von Hand (keine DI): zuerst
  `PreMigrationBackup.runIfNeeded`, dann `AppDatabase`, `Repository`, `TrackRepository`,
  `LocationProvider`, `ReminderScheduler`, `Tracking`, `WorkController`, `MapConfig.init`,
  `AutoBackup.schedule`. `MainViewModel.onForeground` stößt einmalig `OvernightImport` an.
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
  Übernachtungsplätze sind **fest gespeichert** (`overnight_places`/`overnight_stays`) und entstehen nur bei
  „Arbeit beenden“ mit Haken (`WorkController.endWork(savePlace = true)`); im Umkreis 200 m → bestehender Platz.
  Unabhängig von der Steuer-Zählung `ClassifiedDay.overnight`.
- **Länder:** Verlauf, Startseite, Auswertung, CSV/PDF zeigen pro Tag nur `ClassifiedDay.country`
  (Ausland hat Vorrang, aus `days.countries` neu berechnet); alle Länder nur im Editor. `days.countryCode` = zählendes Land, `days.countries` = Reihenfolge aller Länder. Während der
  Arbeitszeit: `TrackingService` → `BorderWatcher` → `WorkController.onBorderCrossed` →
  `Repository.recordBorderCrossing` (nicht bei `countryManual`).
- **Meilensteine:** `MilestoneState` (im `MainViewModel`) berechnet sie laufend aus Tagen und Strecken
  (`MilestoneInputs` → `Milestones.evaluate`), `MilestoneTracker` speichert neue in `milestones` und
  entscheidet, ob eine Meldung erscheint.
- **Automatisches Backup:** gleiche Daten wie manuell (`FahrtenbuchApp.fullBackupData`), Ordner per
  `OpenDocumentTree` mit dauerhafter Berechtigung; Dateien `Fahrtenbuch-Auto-<Datum>_<Zeit>.json`.
- **Spesentabelle:** `FahrtenbuchApp.ensureExpenseTableForThisYear` (aus `MainViewModel.onForeground`) legt das
  aktuelle Jahr an (Kopie des Vorjahres = Jahreswechsel). Jahre sind danach unabhängig. Euro: jeder Tag mit der Tabelle
  seines Jahres (`EuroCalc`).
- **Zwei Zählregeln:** Auswertung und Jahresexport (Steuer) immer gesetzlich (`Settings.rules`, kein Schalter mehr);
  die Reisekostenabrechnung für die Firma mit Firmenregel (`TravelReportSource.COMPANY_RULES`: eintägig bis 8 Std. = halb).
- **Reisekostenabrechnung:** Auswertung → `ReportScreen` → `ReportViewModel` → `TravelReportSource.build` →
  `TravelReports.build` → `TravelReportPdf`. Ohne vollständige Beträge keine PDF. Erinnerung `ReportReminder` →
  `MainActivity` (`ACTION_OPEN_REPORT`) → `FahrtenbuchApp.reportRequests` → `AppRoot` öffnet die Vorschau.
- **Datensicherung:** Neue Tabellen/Felder müssen an drei Stellen ergänzt werden: `Backup` (JSON hin und
  zurück, Modelle in `BackupModels`), `Repository.exportData`/`replaceAll` und `OldDatabaseReader`
  (liest alte Datenbanken roh per SQL, Tabellen/Spalten jeweils optional).

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
7. **Wenn du Dateien hinzufügst, umbenennst oder die Struktur änderst, aktualisiere diese CLAUDE.md am
   Ende der Aufgabe.**

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

- **Korrektur Übernachtungspauschale:** gehört zum Tag mit „Übernachtung im LKW“ (Abend), nicht zum Folgetag – überall
  (Abrechnung, Auswertung, Jahres-PDF/CSV), rückwirkend da immer neu berechnet. Reiseanlass aus den Arbeitszeiten
  (Abfahrt/Ankunft am Tag, an dem es passiert). Test `OvernightRuleTest` (Tour beenden, Kartenpunkt-Haken).

- **Euro-Berechnung und Reisekostenabrechnung (DB v8, Backup 8):** Euro je Land, Übernachtungspauschale und Gesamt in
  der Auswertung (fehlende Beträge nie als 0 €), Euro auch im Jahres-PDF/CSV; monatliche Reisekostenabrechnung als PDF
  (Vorschau, Reiseanlass änderbar und gespeichert in `days.travelText`, Seitenumbruch mit Tabellenkopf, Teilen/Speichern,
  Dateiname `Reisekosten_JJJJ-MM_Name.pdf`), Erinnerung am 1. um 18 Uhr; Firmenregel nur noch in der Abrechnung,
  Schalter entfernt.

- **Vorarbeiten Spesen (DB v7, Backup 7):** Länder-Regel „Ausland hat Vorrang“ als zentrale Funktion
  (`DayCountries.countingCountry`/`ClassifiedDay.country`), überall nur noch ein Land pro Tag (auch PDF/CSV, CSV ohne
  Spalte „Alle Länder“); Spesentabelle je Jahr (Pauschale LKW, Beträge je Land, automatische Kopie zum Jahreswechsel,
  Hinweis auf fehlende Beträge); Feld „Fahrer“ heißt jetzt „Name“. Euro-Berechnung/Spesenbericht folgen später.

- **Übernachtungsplätze fest + bearbeitbar, Land automatisch, Diesel, automatisches Backup (DB v6, Backup 6):**
  Mond-Punkt wandert nicht mehr mit; Platz nur per Haken beim „Arbeit beenden“; Umbenennen, Versetzen,
  Löschen, Notizen (Sterne, Ausstattung, Freitext), einzelne Übernachtungen löschbar; mehrere Länder pro Tag
  (Ausland hat Vorrang), Grenzerkennung offline; Kachel „Liter Diesel“ (Standard 27 l/100 km);
  automatisches Backup per WorkManager (neue Abhängigkeit `androidx.work`).

- **Aufteilung in kleinere Dateien (ohne Funktionsänderung):** Entitäten je Datei, Sicherungs-Modelle,
  Export und Datei-Ein-/Ausgabe aus dem `MainViewModel` herausgelöst (`YearExport`, `BackupFiles`,
  `MilestoneState`), Bildschirme in Aufbau + Bausteine geteilt, Eingabeprüfung in `EditorRules`.

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
