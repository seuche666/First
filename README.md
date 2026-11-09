# Fahrtenbuch – Handy-App für den Fernverkehr (PWA)

Ein Fahrtenbuch als **Progressive Web App**. Du installierst es direkt aus dem Browser auf den
Startbildschirm, ohne App Store. Danach startet es wie eine normale App im Vollbild und
funktioniert auch **offline**, also auch ohne Netz unterwegs.

**Funktionen**

- **Fahrt starten / beenden** mit einem Tipp: Uhrzeit wird automatisch eingetragen, Startort,
  km-Stand und Kennzeichen werden von der letzten Fahrt übernommen.
- **Fahrt nachtragen** und jede Fahrt nachträglich bearbeiten oder löschen.
- Pro Fahrt: Kennzeichen, Start- und Zielort, Abfahrt und Ankunft (auch über Nacht),
  km-Stand Start/Ende, Auftrag/Kunde, Notiz.
- Automatisch berechnet: gefahrene km und Fahrtdauer.
- Monatsübersicht (km, Fahrten, Stunden) und Liste nach Monaten gruppiert.
- **Export als CSV-Tabelle** (öffnet direkt in Excel) mit Fahrername.
- **Datensicherung** speichern und wieder laden.
- Automatischer Dark Mode.

Die Daten bleiben nur auf deinem Gerät (im Browser-Speicher). Speichere deshalb regelmäßig eine
Datensicherung über das Menü ⋮.

## Auf dem Handy installieren

Die App muss über **HTTPS** erreichbar sein. Am einfachsten geht das mit GitHub Pages:

1. Diesen Branch in `main` mergen.
2. Auf GitHub im Repository: **Settings → Pages → Build and deployment → Source: „GitHub Actions“**.
3. Der Workflow `.github/workflows/pages.yml` veröffentlicht die App. Die Adresse lautet dann
   `https://<dein-github-name>.github.io/<repo-name>/`.
4. Diese Adresse auf dem Handy öffnen:
   - **Android (Chrome):** auf „Installieren“ in der App tippen, oder Menü ⋮ → „App installieren“.
   - **iPhone (Safari):** Teilen-Symbol ⬆︎ → „Zum Home-Bildschirm“.

## Lokal ausprobieren

```bash
python3 -m http.server 8000
```

Danach <http://localhost:8000> im Browser öffnen.

## Dateien

| Datei | Zweck |
|---|---|
| `index.html` | Oberfläche |
| `styles.css` | Design (hell und dunkel) |
| `app.js` | Logik, Speichern, Export, Installations-Button |
| `manifest.webmanifest` | Name, Icons, Vollbild: macht die Seite installierbar |
| `sw.js` | Service Worker für den Offline-Betrieb |
| `icons/` | App-Icons |

Wenn du die App änderst, erhöhe in `sw.js` die Versionsnummer (z. B. `fahrtenbuch-v2` → `fahrtenbuch-v3`),
damit die installierten Apps die neue Version laden.
