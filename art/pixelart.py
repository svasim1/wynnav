"""Wynnav's pixel art. Every sprite is an exact pixel grid (no anti-aliasing) in one shared palette.

Run `python3 art/pixelart.py` to regenerate the textures and logo, or add `--preview` to also write
enlarged previews to art/preview/.
"""
import os
import struct
import zlib

import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import branding  # noqa: E402

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src", "main", "resources", "assets", "wynnav")
OUT = os.path.join(ASSETS, "textures", "gui")
PREVIEW = os.path.join(ROOT, "art", "preview")

# One small palette for everything; light comes from the top left.
PAL = {
    ".": None,
    "K": (0x0B, 0x0F, 0x14),  # outline
    "D": (0x18, 0x22, 0x2E),  # deep navy
    "N": (0x26, 0x36, 0x4A),  # navy
    "n": (0x34, 0x4A, 0x63),  # navy light
    "P": (0xF4, 0xE6, 0xBE),  # parchment light
    "p": (0xDE, 0xC6, 0x8E),  # parchment
    "q": (0xB4, 0x96, 0x60),  # parchment shadow
    "G": (0x6C, 0xB4, 0x4C),  # grass
    "g": (0x44, 0x80, 0x38),  # grass dark
    "B": (0x56, 0x9C, 0xE0),  # water
    "b": (0x30, 0x68, 0xAC),  # water dark
    "R": (0xE0, 0x48, 0x3E),  # red
    "r": (0x9C, 0x28, 0x26),  # red dark
    "Y": (0xFF, 0xD8, 0x66),  # gold
    "y": (0xC8, 0x92, 0x2C),  # gold dark
    "O": (0xF5, 0x9E, 0x3A),  # orange
    "o": (0xB8, 0x64, 0x1E),  # orange dark
    "W": (0xFF, 0xFF, 0xFF),  # white
    "S": (0xC4, 0xCC, 0xD6),  # light grey
    "s": (0x8A, 0x94, 0xA0),  # grey
}


def png(path, rows, scale=1, background=None):
    h = len(rows)
    w = len(rows[0])
    raw = bytearray()
    for y in range(h * scale):
        raw.append(0)
        for x in range(w * scale):
            c = rows[y // scale][x // scale]
            if c is None:
                raw += bytes(background + (255,)) if background else b"\0\0\0\0"
            elif len(c) == 4:
                raw += bytes(c)
            else:
                raw += bytes(c + (255,))
    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xFFFFFFFF)
    data = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w * scale, h * scale, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(data)


def grid(text):
    lines = [l for l in text.strip("\n").split("\n")]
    width = max(len(l) for l in lines)
    assert all(len(l) == width for l in lines), "ragged grid"
    return [list(l) for l in lines]


def colors(g):
    return [[PAL[c] for c in row] for row in g]


def outline(g, fill_char_set, outline_char="K"):
    """Adds a 1px outline (4-neighbour) around all non-empty pixels."""
    h, w = len(g), len(g[0])
    out = [row[:] for row in g]
    for y in range(h):
        for x in range(w):
            if g[y][x] != ".":
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < w and 0 <= ny < h and g[ny][nx] in fill_char_set:
                    out[y][x] = outline_char
                    break
    return out


def show(g):
    return "\n".join("".join(r) for r in g)


# ---------------------------------------------------------------- player pointer (15x15)

# Pointing north; the game rotates it to any angle. Gold rim and tip, white body lit on the left.
PLAYER_POINTER = grid("""
.......K.......
......KYK......
......KYK......
.....KYWyK.....
.....KYWyK.....
....KYWWSyK....
....KYWWSyK....
...KYWWWSSyK...
...KYWWWSSyK...
..KYWWWKSSSyK..
..KYWWK.KSSyK..
.KYWWK...KSSyK.
.KYYK.....KyyK.
.KKK.......KKK.
...............
""")


# ---------------------------------------------------------------- waypoint (tinted body + untinted shading)

WAYPOINT_FILL = grid("""
....#....
...###...
..#####..
.#######.
#########
.#######.
..#####..
...###...
....#....
""")

WAYPOINT_SHADE = grid("""
....K....
...KHK...
..KHH.K..
.KH....K.
KH.....dK
.K....dK.
..K..dK..
...KdK...
....K....
""")

WAYPOINT_RING = grid("""
......Y......
.....YKY.....
....YK.KY....
...YK...KY...
..YK.....KY..
.YK.......KY.
YK.........KY
.YK.......KY.
..YK.....KY..
...YK...KY...
....YK.KY....
.....YKY.....
......Y......
""")


def waypoint_textures():
    fill = [[(255, 255, 255) if c == "#" else None for c in row] for row in WAYPOINT_FILL]
    shade = []
    for row in WAYPOINT_SHADE:
        out = []
        for c in row:
            out.append({"K": (0x0B, 0x0F, 0x14, 255), "H": (255, 255, 255, 150), "d": (0, 0, 0, 90)}.get(c))
        shade.append(out)
    return fill, shade, colors(WAYPOINT_RING)


# ---------------------------------------------------------------- recenter icon (16x16, for a button)

RECENTER = grid("""
.......KK.......
.......WK.......
.......WK.......
.....KKWKKK.....
....KWW..WWK....
...KW......SK...
...KW..KK..SK...
KWWW..KWSK..SSSK
KKKK..KSsK..KKKK
...KW..KK..SK...
...KW......SK...
....KSS..SSK....
.....KKSKKK.....
.......SK.......
.......SK.......
.......KK.......
""")

# ---------------------------------------------------------------- camp and world event (11x11, full colour)

CAMP = grid("""
.....K.....
....KOK....
....KOK....
...KOOoK...
...KOOoK...
..KOOKooK..
..KOKDKoK..
.KOOKDKooK.
.KOKDDDKoK.
KOOKDDDKooK
KKKKKKKKKKK
""")

EVENT = grid("""
.....K.....
....KRK....
.K..KRK..K.
.KRKRYRKRK.
..KRYYYRK..
KRRYYWYYRRK
..KRYYYRK..
.KRKRYRKRK.
.K..KRK..K.
....KRK....
.....K.....
""")

def main():
    png(os.path.join(OUT, "player_arrow.png"), colors(PLAYER_POINTER))
    fill, shade, ring = waypoint_textures()
    png(os.path.join(OUT, "waypoint.png"), fill)
    png(os.path.join(OUT, "waypoint_shade.png"), shade)
    png(os.path.join(OUT, "waypoint_ring.png"), ring)
    png(os.path.join(OUT, "recenter.png"), colors(RECENTER))
    png(os.path.join(OUT, "camp.png"), colors(CAMP))
    png(os.path.join(OUT, "event.png"), colors(EVENT))
    # Mod icon (Mod Menu) and the README images; see art/branding.py.
    icon = branding.colors(branding.icon())
    png(os.path.join(ASSETS, "icon.png"), icon, scale=4)
    png(os.path.join(ROOT, "docs", "logo.png"), icon, scale=16)
    png(os.path.join(ROOT, "docs", "banner.png"), branding.colors(branding.banner()), scale=4)

    if "--preview" in sys.argv:
        bg = (0x2A, 0x33, 0x3D)
        png(os.path.join(PREVIEW, "icon.png"), icon, scale=8, background=bg)
        for name, g in [("pointer", PLAYER_POINTER), ("recenter", RECENTER), ("camp", CAMP), ("event", EVENT), ("ring", WAYPOINT_RING)]:
            png(os.path.join(PREVIEW, name + ".png"), colors(g), scale=12, background=bg)


if __name__ == "__main__":
    main()
