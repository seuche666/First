#!/usr/bin/env python3
"""
Erzeugt app/src/main/assets/country_borders.bin aus den Natural-Earth-Ländergrenzen.

Quellen (npm-Pakete, z. B. per `npm pack world-atlas@2.0.2 i18n-iso-countries@7.11.0`):
  - world-atlas 2.0.2, countries-10m.json (TopoJSON, Natural Earth 1:10m, Public Domain; Paket: ISC)
  - i18n-iso-countries 7.11.0, codes.json (Zuordnung ISO-Nummer -> Alpha-2; MIT)

Aufruf:
  python3 -I tools/build_country_borders.py <countries-10m.json> <codes.json> <ausgabe.bin>

Binärformat (Little Endian), gelesen von CountryLocator.kt:
  "FBGB"  Kennung
  u8      Formatversion (2)
  u16     Anzahl Länder
  je Land:  2 Byte ISO-Code (ASCII), u16 Anzahl Ringe
    je Ring: u32 Anzahl Punkte, 4 x i32 Begrenzungsrechteck (minLon, minLat, maxLon, maxLat),
             danach je Punkt die Differenz zum Vorgängerpunkt (Länge, Breite) als
             ZigZag-Varint (der erste Punkt relativ zu 0,0).
  Alle Koordinaten in Grad x 100000 (Auflösung ca. 1 m).
Alle Ringe eines Landes (Außenringe und Löcher) werden mit der Gerade-Ungerade-Regel ausgewertet.
"""
import json
import struct
import sys

SCALE = 100_000


def decode_arcs(topo):
    sx, sy = topo["transform"]["scale"]
    tx, ty = topo["transform"]["translate"]
    arcs = []
    for arc in topo["arcs"]:
        x = y = 0
        pts = []
        for dx, dy in arc:
            x += dx
            y += dy
            pts.append((x * sx + tx, y * sy + ty))
        arcs.append(pts)
    return arcs


def ring_coords(ring, arcs):
    out = []
    for idx in ring:
        pts = arcs[idx] if idx >= 0 else list(reversed(arcs[~idx]))
        if out:
            pts = pts[1:]  # gemeinsamer Punkt zwischen zwei Bögen
        out.extend(pts)
    return out


def main(topo_path, codes_path, out_path):
    topo = json.load(open(topo_path, encoding="utf-8"))
    codes = json.load(open(codes_path, encoding="utf-8"))
    numeric_to_alpha2 = {row[2]: row[0] for row in codes}  # [alpha2, alpha3, numeric]
    arcs = decode_arcs(topo)

    countries = {}
    skipped = []
    for geom in topo["objects"]["countries"]["geometries"]:
        name = geom.get("properties", {}).get("name", "")
        num = geom.get("id")
        code = numeric_to_alpha2.get(num) if num else None
        if code is None and name == "Kosovo":
            code = "XK"
        if code is None:
            skipped.append(name)
            continue
        if geom["type"] == "Polygon":
            polys = [geom["arcs"]]
        elif geom["type"] == "MultiPolygon":
            polys = geom["arcs"]
        else:
            continue
        rings = countries.setdefault(code, [])
        for poly in polys:
            for ring in poly:
                pts = ring_coords(ring, arcs)
                ints = [(round(lon * SCALE), round(lat * SCALE)) for lon, lat in pts]
                # doppelte Folgepunkte nach dem Runden entfernen
                dedup = [ints[0]]
                for p in ints[1:]:
                    if p != dedup[-1]:
                        dedup.append(p)
                if len(dedup) >= 4:
                    rings.append(dedup)

    def varint(v):
        v = (v << 1) ^ (v >> 63)  # ZigZag
        out = bytearray()
        while True:
            b = v & 0x7F
            v >>= 7
            if v:
                out.append(b | 0x80)
            else:
                out.append(b)
                return out

    with open(out_path, "wb") as f:
        f.write(b"FBGB")
        f.write(struct.pack("<BH", 2, len(countries)))
        total = 0
        for code in sorted(countries):
            rings = countries[code]
            f.write(code.encode("ascii"))
            f.write(struct.pack("<H", len(rings)))
            for ring in rings:
                xs = [p[0] for p in ring]
                ys = [p[1] for p in ring]
                f.write(struct.pack("<I4i", len(ring), min(xs), min(ys), max(xs), max(ys)))
                buf = bytearray()
                px = py = 0
                for x, y in ring:
                    buf += varint(x - px)
                    buf += varint(y - py)
                    px, py = x, y
                f.write(buf)
                total += len(ring)
    print(f"{len(countries)} Länder, {total} Punkte geschrieben; übersprungen ohne ISO-Code: {sorted(set(skipped))}")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    main(*sys.argv[1:])
