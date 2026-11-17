#!/usr/bin/env python3
"""
Erzeugt app/src/main/assets/places.bin: Orte in Europa (und Umgebung) für die Anzeige
„nächster Ort“ bei Übernachtungsplätzen – komplett offline.

Quelle: GeoNames „cities1000“ (alle Orte ab 1000 Einwohnern), Lizenz CC BY 4.0
(https://www.geonames.org). Bezogen über das PyPI-Paket reverse_geocoder 1.5.1
(Datei reverse_geocoder/rg_cities1000.csv), z. B. per `pip download --no-deps reverse_geocoder==1.5.1`.

Aufruf:
  python3 -I tools/build_places.py <rg_cities1000.csv> <ausgabe.bin>

Binärformat (Little Endian), gelesen von PlaceLocator.kt:
  "FBPL"  Kennung
  u8      Formatversion (1)
  u32     Anzahl Orte (nach Breite sortiert)
  je Ort: i32 Breite, i32 Länge (Grad x 100000), 2 Byte ISO-Ländercode (ASCII),
          u8 Länge des Namens in Byte, Name (UTF-8)
"""
import csv
import struct
import sys

LAT = (33.0, 72.0)
LON = (-26.0, 46.0)


def main(src, dst):
    rows = []
    with open(src, encoding="utf-8") as f:
        for r in csv.DictReader(f):
            lat, lon = float(r["lat"]), float(r["lon"])
            if not (LAT[0] <= lat <= LAT[1] and LON[0] <= lon <= LON[1]):
                continue
            cc = (r["cc"] or "??")[:2].upper()
            name = r["name"].strip().encode("utf-8")[:255]
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
