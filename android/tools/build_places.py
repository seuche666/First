#!/usr/bin/env python3
"""
Erzeugt app/src/main/assets/places.bin: Orte in Europa (und Umgebung) für die Anzeige
„nächster Ort“ bei Übernachtungsplätzen – komplett offline.

Quelle: GeoNames „cities1000“ (alle Orte ab 1000 Einwohnern, Namen in Landessprache),
Lizenz CC BY 4.0 (https://www.geonames.org). Bezogen über das PyPI-Paket geonamescache 3.0.2
(Datei geonamescache/data/cities1000.json), z. B. per `pip download --no-deps geonamescache==3.0.2`.
Einige Großstädte tragen dort englische Namen; sie werden durch die deutschen ersetzt (GERMAN).

Aufruf:
  python3 -I tools/build_places.py <cities1000.json> <ausgabe.bin>

Binärformat (Little Endian), gelesen von PlaceLocator.kt:
  "FBPL"  Kennung
  u8      Formatversion (1)
  u32     Anzahl Orte (nach Breite sortiert)
  je Ort: i32 Breite, i32 Länge (Grad x 100000), 2 Byte ISO-Ländercode (ASCII),
          u8 Länge des Namens in Byte, Name (UTF-8)
"""
import json
import struct
import sys

LAT = (33.0, 72.0)
LON = (-26.0, 46.0)

# Deutsche Namen für Städte, die GeoNames mit englischem Namen führt
GERMAN = {
    "Munich": "München", "Nuremberg": "Nürnberg", "Vienna": "Wien", "Prague": "Prag", "Warsaw": "Warschau",
    "Rome": "Rom", "Milan": "Mailand", "Brussels": "Brüssel", "Copenhagen": "Kopenhagen", "Bucharest": "Bukarest",
    "Belgrade": "Belgrad", "Athens": "Athen", "Naples": "Neapel", "Genoa": "Genua",
    "Florence": "Florenz", "Venice": "Venedig", "Lisbon": "Lissabon", "Antwerp": "Antwerpen", "Gothenburg": "Göteborg",
    "The Hague": "Den Haag", "Ghent": "Gent", "Bruges": "Brügge", "Zurich": "Zürich", "Geneva": "Genf",
    "Lucerne": "Luzern", "Moscow": "Moskau", "Kyiv": "Kiew", "Seville": "Sevilla", "Saint Petersburg": "Sankt Petersburg",
    "Hanover": "Hannover", "Brunswick": "Braunschweig", "Cologne": "Köln",
    "Padua": "Padua", "Mantua": "Mantua", "Syracuse": "Syrakus",
}


def main(src, dst):
    rows = []
    with open(src, encoding="utf-8") as f:
        cities = json.load(f)
    for c in (cities.values() if isinstance(cities, dict) else cities):
        lat, lon = float(c["latitude"]), float(c["longitude"])
        if not (LAT[0] <= lat <= LAT[1] and LON[0] <= lon <= LON[1]):
            continue
        cc = (c.get("countrycode") or "??")[:2].upper()
        name = c["name"].strip()
        name = GERMAN.get(name, name).encode("utf-8")[:255]
        if not name:
            continue
        rows.append((round(lat * 100000), round(lon * 100000), cc, name))
    rows.sort()
    with open(dst, "wb") as out:
        out.write(b"FBPL")
        out.write(struct.pack("<BI", 1, len(rows)))
        for lat, lon, cc, name in rows:
            out.write(struct.pack("<ii", lat, lon))
            out.write(cc.encode("ascii", "replace")[:2].ljust(2, b"?"))
            out.write(struct.pack("<B", len(name)))
            out.write(name)
    print(f"{len(rows)} Orte geschrieben nach {dst}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
