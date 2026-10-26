#!/usr/bin/env python3
"""Isometric view of a simulated forest: terrain columns plus the voxels of the trees.

usage: render_scene.py scene.json out.png [x0 z0 size [scale]]
scene.json is written by the forest simulation (heights "h", surface kinds "k", water "w", voxels "v": [x, y, z, material]).
Materials are the TreeMat ordinals; colours approximate the block textures.
"""
import json
import math
import sys

from PIL import Image, ImageDraw

COL = {
    0: (62, 50, 58), 1: (214, 206, 188), 2: (62, 58, 60), 3: (104, 70, 52),
    4: (112, 104, 88), 5: (30, 58, 56), 6: (128, 28, 40), 7: (150, 146, 140),
    8: (58, 92, 70), 9: (190, 168, 130),
}
LEAFY = (4, 5, 6, 7, 8)
TERRAIN = {0: (66, 74, 54), 1: (62, 50, 44), 2: (112, 108, 106), 3: (176, 164, 128), 4: (112, 112, 118), 5: (122, 100, 74),
           6: (62, 48, 42), 7: (86, 82, 82), 8: (100, 78, 62), 9: (66, 66, 76)}
WATER = (40, 54, 92)
BG = (28, 30, 36)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


def main():
    scene = json.load(open(sys.argv[1]))
    out = sys.argv[2]
    W, H = scene["W"], scene["H"]
    x0 = int(sys.argv[3]) if len(sys.argv) > 3 else 0
    z0 = int(sys.argv[4]) if len(sys.argv) > 4 else 0
    size = int(sys.argv[5]) if len(sys.argv) > 5 else min(W, H)
    s = float(sys.argv[6]) if len(sys.argv) > 6 else 5.0
    hs, ks, ws = scene["h"], scene["k"], scene["w"]
    x1, z1 = min(W, x0 + size), min(H, z0 + size)

    vox = {}
    ymax = 0
    for x, y, z, m in scene["v"]:
        if x0 <= x < x1 and z0 <= z < z1:
            vox[(x, y, z)] = m
            ymax = max(ymax, y)
    ymin = min(hs[z * W + x] for z in range(z0, z1) for x in range(x0, x1))

    cw, sh = s * math.cos(math.radians(30)), s * 0.5
    width = int((x1 - x0 + z1 - z0) * cw + 40)
    height = int((x1 - x0 + z1 - z0) * sh + (ymax - ymin + 6) * s + 40)
    img = Image.new("RGB", (width, height), BG)
    d = ImageDraw.Draw(img)
    ox = (z1 - z0) * cw + 20
    oy = (ymax - ymin + 4) * s + 10

    def sc(x, y, z):
        return ox + (x - x0 - (z - z0)) * cw, oy + ((x - x0) + (z - z0)) * sh - (y - ymin + 0) * s

    def cube_faces(x, y, z, col, top=True, east=True, south=True, k=1.0):
        # the cube spans [x, x+1] x [y, y+1] x [z, z+1]
        if top:
            d.polygon([sc(x, y + 1, z), sc(x + 1, y + 1, z), sc(x + 1, y + 1, z + 1), sc(x, y + 1, z + 1)], fill=shade(col, 1.0 * k))
        if east:
            d.polygon([sc(x + 1, y + 1, z), sc(x + 1, y + 1, z + 1), sc(x + 1, y, z + 1), sc(x + 1, y, z)], fill=shade(col, 0.78 * k))
        if south:
            d.polygon([sc(x, y + 1, z + 1), sc(x + 1, y + 1, z + 1), sc(x + 1, y, z + 1), sc(x, y, z + 1)], fill=shade(col, 0.6 * k))

    def hv(x, z):
        return hs[z * W + x] if 0 <= x < W and 0 <= z < H else -999

    for diag in range((x1 - x0) + (z1 - z0) - 1):
        for xi in range(max(0, diag - (z1 - z0) + 1), min(diag, x1 - x0 - 1) + 1):
            x = x0 + xi
            z = z0 + diag - xi
            col = z * W + x
            gh = hs[col]
            # terrain column: top face and the sides that face the camera
            tcol = TERRAIN.get(ks[col], (80, 80, 80))
            top = gh + 1
            d.polygon([sc(x, top, z), sc(x + 1, top, z), sc(x + 1, top, z + 1), sc(x, top, z + 1)], fill=tcol)
            ne = max(hv(x + 1, z) + 1, top - 40)
            if ne < top:
                d.polygon([sc(x + 1, top, z), sc(x + 1, top, z + 1), sc(x + 1, ne, z + 1), sc(x + 1, ne, z)], fill=shade(tcol, 0.72))
            ns = max(hv(x, z + 1) + 1, top - 40)
            if ns < top:
                d.polygon([sc(x, top, z + 1), sc(x + 1, top, z + 1), sc(x + 1, ns, z + 1), sc(x, ns, z + 1)], fill=shade(tcol, 0.55))
            if ws[col] > gh:
                wl = ws[col]
                d.polygon([sc(x, wl + 1, z), sc(x + 1, wl + 1, z), sc(x + 1, wl + 1, z + 1), sc(x, wl + 1, z + 1)], fill=WATER)
            for y in range(gh + 1, ymax + 1):
                m = vox.get((x, y, z))
                if m is None:
                    continue
                top = (x, y + 1, z) not in vox
                east = (x + 1, y, z) not in vox
                south = (x, y, z + 1) not in vox
                if not (top or east or south):
                    continue
                c = COL[m]
                k = 1.0
                if m in LEAFY:
                    hsh = ((x * 73856093) ^ (y * 19349663) ^ (z * 83492791)) & 255
                    k = 0.8 + 0.4 * hsh / 255.0
                cube_faces(x, y, z, c, top, east, south, k)
    img.save(out)
    print(out, img.size)


if __name__ == "__main__":
    main()
