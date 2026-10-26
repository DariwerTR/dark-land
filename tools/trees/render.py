#!/usr/bin/env python3
"""Preview of generated trees: voxel dumps written by TreeDump are drawn as an isometric view plus a front elevation.

usage: render.py dump.json out.png [columns] [scale]
Colours approximate the block textures; leaves are tinted per voxel to hint at texture.
"""
import json, math, sys
from PIL import Image, ImageDraw

COL = {
    0: (62, 50, 58), 1: (214, 206, 188), 2: (62, 58, 60), 3: (104, 70, 52),
    4: (112, 104, 88), 5: (30, 58, 56), 6: (128, 28, 40), 7: (178, 172, 138),
    8: (58, 92, 70), 9: (190, 168, 130),
}
LEAFY = (4, 5, 6, 7, 8)
GROUND = (74, 86, 62)
DIRT = (58, 48, 42)
BG = (30, 32, 38)
R = 12


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


def tint(col, x, y, z, m):
    if m in LEAFY:
        h = ((x * 73856093) ^ (y * 19349663) ^ (z * 83492791)) & 255
        return shade(col, 0.82 + 0.36 * h / 255.0)
    return col


def ground_cells(tree):
    g = tree["g"]
    out = []
    for zi in range(2 * R + 1):
        for xi in range(2 * R + 1):
            gy = g[zi * (2 * R + 1) + xi]
            if gy < -50:
                continue
            for yy in range(gy - 3, gy):
                out.append((xi - R, yy, zi - R, GROUND if yy == gy - 1 else DIRT, -1))
    return out


def cells_of(tree, ground=True):
    cells = ground_cells(tree) if ground else []
    for x, y, z, m, aux, root in tree["v"]:
        cells.append((x, y, z, COL[m], m))
    return cells


def iso(tree, s, yaw=0):
    cw, sh = s * math.cos(math.radians(30)), s * 0.5

    def rot(x, z):
        for _ in range(yaw % 4):
            x, z = -z, x
        return x, z

    items = []
    for x, y, z, col, m in cells_of(tree):
        rx, rz = rot(x, z)
        items.append((rx + rz + 0.001 * y, rx, y, rz, col, m, (x, y, z)))
    items.sort(key=lambda t: t[0])

    def P(rx, ry, rz):
        return ((rx - rz) * cw, (rx + rz) * sh - ry * s)

    polys = []
    for _, rx, y, rz, col, m, (x, yy, z) in items:
        col = tint(col, x, yy, z, m)
        x0, x1, y0, y1, z0, z1 = rx - .5, rx + .5, y - .5, y + .5, rz - .5, rz + .5
        polys.append(([P(x0, y1, z0), P(x1, y1, z0), P(x1, y1, z1), P(x0, y1, z1)], shade(col, 1.10)))
        polys.append(([P(x0, y1, z1), P(x1, y1, z1), P(x1, y0, z1), P(x0, y0, z1)], shade(col, 0.78)))
        polys.append(([P(x1, y1, z0), P(x1, y1, z1), P(x1, y0, z1), P(x1, y0, z0)], shade(col, 0.56)))
    return polys


def front(tree, s, axis=0):
    """Elevation looking along z (axis 0) or along x (axis 1); nearer voxels are brighter."""
    items = []
    for x, y, z, col, m in cells_of(tree):
        u, d = (x, z) if axis == 0 else (z, x)
        items.append((d, u, y, col, m, (x, y, z)))
    items.sort(key=lambda t: -t[0])
    polys = []
    for d, u, y, col, m, (x, yy, z) in items:
        col = tint(col, x, yy, z, m)
        k = 0.62 + 0.38 * (d + R) / (2 * R)
        polys.append(([(u * s - s / 2, -(y * s) - s / 2), (u * s + s / 2, -(y * s) - s / 2),
                       (u * s + s / 2, -(y * s) + s / 2), (u * s - s / 2, -(y * s) + s / 2)], shade(col, k)))
    return polys


def draw(polys, margin=8, ref=None):
    xs = [p[0] for poly, _ in polys for p in poly]
    ys = [p[1] for poly, _ in polys for p in poly]
    x0, x1, y0, y1 = min(xs), max(xs), min(ys), max(ys)
    if ref:
        x0, x1, y0, y1 = ref
    w, h = int(x1 - x0 + 2 * margin), int(y1 - y0 + 2 * margin)
    img = Image.new("RGB", (w, h), BG)
    d = ImageDraw.Draw(img)
    for poly, col in polys:
        d.polygon([(px - x0 + margin, py - y0 + margin) for px, py in poly], fill=col)
    return img


def main():
    dump, out = sys.argv[1], sys.argv[2]
    cols = int(sys.argv[3]) if len(sys.argv) > 3 else 2
    s = float(sys.argv[4]) if len(sys.argv) > 4 else 12.0
    trees = json.load(open(dump))
    if not trees:
        print("no trees")
        return
    rows = []
    for t in trees:
        # ground is only drawn near the tree so that the crops stay small
        t2 = dict(t)
        views = [draw(iso(t2, s, 0)), draw(front(t2, s, 0))]
        w = sum(v.size[0] for v in views) + 10
        h = max(v.size[1] for v in views)
        tile = Image.new("RGB", (w, h), BG)
        x = 0
        for v in views:
            tile.paste(v, (x, h - v.size[1]))
            x += v.size[0] + 10
        rows.append(tile)
    tw = max(r.size[0] for r in rows) + 20
    th = max(r.size[1] for r in rows) + 20
    nr = (len(rows) + cols - 1) // cols
    sheet = Image.new("RGB", (tw * cols, th * nr), BG)
    for i, r in enumerate(rows):
        sheet.paste(r, ((i % cols) * tw + 10, (i // cols) * th + 10 + th - 20 - r.size[1]))
    sheet.save(out)
    print("wrote", out, sheet.size)


if __name__ == "__main__":
    main()
