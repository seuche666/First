# CLAUDE.md – Fahrtenbuch für Fernfahrer

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
Kilometer, Arbeitszeit für die Firma und Übernachtungen im LKW. Komplett offline, kein Server,
kein Login. Berechnet keine Steuerbeträge.

## Aufbau der App (`android/app/src/main/java/de/fernfahrer/fahrtenbuch/`)

- `data/` – Room-Datenbank (`AppDatabase`, Version **3**), Tabellen `days`, `tours`, `work_shifts`;
  `Repository` (gesamte Speicherlogik: Touren, Arbeitsschichten, km-Übernahme, Tour beenden);
  `Backup` (JSON-Datensicherung, Format **3**, liest 1 und 2); `PreMigrationBackup` (Sicherung vor
  jeder Migration); `Settings` (SharedPreferences)
- `domain/` – reine Logik ohne Android: `Classifier` (voll/halb/Firmenregel/Übernachtung),
  `WorkSplitter` (Schichten an Mitternacht teilen, km anteilig verteilen), `Stats`, `Period`,
  `Countries`, `Format`
- `export/` – PDF (`PdfExporter`, Android `PdfDocument`) und CSV (Excel, Semikolon, BOM), Teilen
- `location/` – `CountryLocator` (Offline-Ländererkennung aus `assets/country_borders.bin`,
  Natural Earth 1:10m, erzeugt mit `android/tools/build_country_borders.py`), `LocationProvider`
  (einzelne GPS-Abfrage, kein Dauer-GPS)
- `work/` – `WorkController` (Arbeit beginnen/beenden, Tour beenden, GPS-Land, Erinnerung),
  `ReminderScheduler`/`ReminderReceiver` (Erinnerung nach 15 Std.), `BootReceiver`
- `ui/` – Compose-Bildschirme: `home` (Start), `history` (Verlauf), `stats` (Auswertung),
  `settings`, `editor` (Tag bearbeiten, Schicht-Dialog), `components` (km-Dialog,
  Berechtigungs-Erklärung, Länderauswahl …), `MainViewModel`, `AppRoot` (Navigation)

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
./gradlew assembleRelease        # ohne Signier-Variablen: app-release-unsigned.apk
```

Hinweise für die Cloud-Umgebung:
- Android-SDK liegt (falls vorhanden) unter `/opt/android-sdk`; sonst Kommandozeilen-Tools von
  `dl.google.com` laden (`platforms;android-35`, `build-tools;35.0.0`) und `android/local.properties`
  mit `sdk.dir=…` anlegen (nicht committen).
- Maven Central liefert hier oft HTTP 429. Lokal hilft ein Gradle-Init-Skript
  (`~/.gradle/init.d/`), das den Spiegel `https://maven-central.storage-download.googleapis.com/maven2/`
  für Plugins, Abhängigkeiten und `robolectric.dependency.repo.url` einträgt. Nicht ins Projekt
  übernehmen.
- Oberfläche prüfen: temporärer Robolectric-Screenshot-Test (nicht committen). PDF-Erzeugung lässt
  sich unter Robolectric nicht testen.

## Zuletzt geändert

- **PR #6 / Release 1.0.8:** Arbeitszeit als Schichten über Mitternacht (automatische Teilung),
  Start-km automatisch, km-Abfrage bei „Arbeit beenden“/„Tour beenden“, Erinnerung nach 15 Std.,
  Land per GPS offline (Handauswahl hat Vorrang), Übernachtung im LKW, Datenbank v3 mit Migration
  und Sicherung vor Update, 64 Tests.
- **PR #5 / 1.0.6:** Firmenregel (eintägig ≤ 8 Std. als halber Tag), Arbeitszeit pro Tag, DB v2.
- **PR #4 / 1.0.4:** Signierung nur noch über GitHub-Secrets.
- **PR #3 / 1.0.2:** erste native Android-App.

## Offen / geplant

- **Fog-of-War-Karte** (noch nicht begonnen, Nutzer hat den Start noch nicht bestätigt). Abgestimmter
  Plan: GPS-Aufzeichnung nur während laufender Arbeitszeit als Vordergrunddienst (Plattform-GPS, kein
  Google), Punkt alle 30–50 m, ungenaue Punkte verwerfen; Reiter „Karte“ mit osmdroid +
  OpenStreetMap-Standardkacheln (Attribution, eigener User-Agent, kein Massen-Download); dunkelgrauer
  Nebel mit weichen Rändern, Korridor 100 m/500 m/1 km/5 km (Mindestbreite in Pixeln), Knöpfe
  „Mein Standort“ und „Europa“; Statistik (aufgedeckte km², befahrene Länder, GPS-Strecke); Land
  jedes Tages aus letztem Punkt vor 24 Uhr (Handauswahl hat Vorrang); Strecken kompakt speichern und
  immer in die Datensicherung; „Alle Strecken löschen“ mit Rückfrage; Berechtigungen inkl.
  „Immer erlauben“ erklären, Hinweis Akku-Optimierung; abschaltbar; eigener PR, neue DB-Version mit
  Migration.
