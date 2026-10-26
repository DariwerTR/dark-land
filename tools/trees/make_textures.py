"""Draws the 16x16 textures of the tree blocks (logs and leaves) into the resource pack.

Usage: python3 tools/trees/make_textures.py [output directory]
The default output is src/main/resources/assets/cursed_lands/textures/block. The drawing is deterministic (fixed seeds),
so running the script again gives the same files.
"""
import os
import random
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_OUT = os.path.join(HERE, "..", "..", "src", "main", "resources", "assets", "cursed_lands", "textures", "block")
N = 16


def clamp(v):
    return max(0, min(255, int(v)))


def shade(c, k):
    return (clamp(c[0] * k), clamp(c[1] * k), clamp(c[2] * k), 255)


# ---------------------------------------------------------------- logs

def bark(palette, seed, gaps=None, marks=None, marks_n=0, embers=None, embers_n=0):
    """Vertical bark: every column has its own base tone, small steps up and down along the column."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (N, N))
    px = img.load()
    for x in range(N):
        tone = rnd.randrange(len(palette))
        for y in range(N):
            if rnd.random() < 0.28:
                tone = max(0, min(len(palette) - 1, tone + rnd.choice((-1, 1))))
            px[x, y] = palette[tone] + (255,)
    if gaps:
        for x in range(rnd.randrange(3), N, 4):
            for y in range(N):
                if rnd.random() < 0.8:
                    px[x, y] = gaps + (255,)
    if marks:
        for _ in range(marks_n):
            w = rnd.randrange(2, 5)
            x0 = rnd.randrange(0, N - w + 1)
            y0 = rnd.randrange(N)
            for i in range(w):
                px[x0 + i, y0] = marks[rnd.randrange(len(marks))] + (255,)
    if embers:
        for _ in range(embers_n):
            x = rnd.randrange(N)
            y = rnd.randrange(N)
            for i in range(rnd.randrange(1, 4)):
                if y + i < N:
                    px[x, y + i] = embers[rnd.randrange(len(embers))] + (255,)
    return img


def rings(outer, bark_w, wood, line, center, seed, speckle=None, speckle_n=0):
    """End grain: bark border, wood with growth rings (concentric squares), a darker heart."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (N, N))
    px = img.load()
    for y in range(N):
        for x in range(N):
            d = min(x, y, N - 1 - x, N - 1 - y)
            if d < bark_w:
                col = outer[(x + y) % len(outer)]
            elif d >= 6:
                col = center
            elif (d - bark_w) % 2 == 1:
                col = line
            else:
                col = wood
            px[x, y] = col + (255,)
    for _ in range(speckle_n):
        x = rnd.randrange(2, N - 2)
        y = rnd.randrange(2, N - 2)
        px[x, y] = speckle[rnd.randrange(len(speckle))] + (255,)
    return img


# ---------------------------------------------------------------- leaves

def leafy(palette, coverage, seed, kind="blob"):
    """Cutout foliage: clusters of leaves with a lit upper left side; transparent gaps between them."""
    rnd = random.Random(seed)
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    px = img.load()
    filled = 0
    guard = 0
    while filled < coverage * N * N and guard < 4000:
        guard += 1
        x = rnd.randrange(N)
        y = rnd.randrange(N)
        if kind == "needle":
            dx, dy = rnd.choice(((1, 1), (-1, 1), (0, 1), (1, 1), (-1, 1)))
            ln = rnd.randrange(3, 6)
            cells = [((x + dx * i) % N, (y + dy * i) % N) for i in range(ln)]
        else:
            cells = [(x, y)]
            for _ in range(rnd.randrange(2, 5)):
                cx, cy = cells[rnd.randrange(len(cells))]
                dx, dy = rnd.choice(((1, 0), (0, 1), (-1, 0), (0, -1)))
                cells.append(((cx + dx) % N, (cy + dy) % N))
        base = rnd.randrange(len(palette))
        for i, (cx, cy) in enumerate(cells):
            tone = base + (1 if i == 0 else 0) - (1 if cx > x or cy > y else 0)
            tone = max(0, min(len(palette) - 1, tone))
            if px[cx, cy][3] == 0:
                filled += 1
            px[cx, cy] = palette[tone] + (255,)
    # a few dark pixels inside the foliage give it depth
    dark = shade(palette[0], 0.7)
    for _ in range(int(coverage * 10)):
        x = rnd.randrange(N)
        y = rnd.randrange(N)
        if px[x, y][3] == 255:
            px[x, y] = dark
    return img


# ---------------------------------------------------------------- the set

def build():
    out = {}

    # pale birch: bone-white bark with dark horizontal scars
    pale = [(150, 144, 134), (172, 166, 154), (192, 186, 172), (208, 202, 188)]
    out["pale_birch_log"] = bark(pale, 11, marks=[(46, 40, 40), (66, 60, 58), (88, 82, 76)], marks_n=17)
    out["pale_birch_log_top"] = rings([(178, 172, 158), (196, 190, 176)], 1, (170, 142, 106), (146, 118, 86), (124, 98, 70), 12,
                                      speckle=[(150, 124, 92)], speckle_n=6)

    # charred: soot black with glowing cracks
    soot = [(14, 11, 13), (22, 18, 20), (31, 26, 28), (42, 36, 38)]
    out["charred_log"] = bark(soot, 21, gaps=(10, 8, 9), embers=[(196, 78, 26), (150, 52, 20), (226, 118, 40)], embers_n=7)
    out["charred_log_top"] = rings([(22, 18, 20), (30, 25, 27)], 1, (40, 33, 35), (26, 21, 23), (84, 40, 24), 22,
                                   speckle=[(196, 78, 26), (150, 52, 20)], speckle_n=4)

    # pine: rough red-brown bark in plates
    pine = [(58, 40, 34), (74, 50, 40), (90, 62, 48), (106, 76, 58)]
    out["pine_log"] = bark(pine, 31, gaps=(36, 24, 22))
    out["pine_log_top"] = rings([(70, 48, 38), (82, 56, 44)], 2, (176, 136, 98), (148, 110, 78), (122, 88, 62), 32,
                                speckle=[(130, 96, 68)], speckle_n=5)

    # leaves
    out["dark_needles"] = leafy([(14, 26, 22), (22, 40, 32), (32, 56, 42), (46, 76, 54)], 0.55, 41, kind="needle")
    out["crimson_leaves"] = leafy([(86, 14, 22), (118, 20, 28), (150, 30, 36), (180, 50, 44)], 0.55, 51)
    out["ashen_leaves"] = leafy([(62, 58, 58), (88, 84, 82), (114, 110, 106), (140, 136, 130)], 0.5, 61)
    return out


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_OUT
    os.makedirs(out_dir, exist_ok=True)
    for name, img in build().items():
        img.save(os.path.join(out_dir, name + ".png"))
        print("wrote", name + ".png")


if __name__ == "__main__":
    main()
