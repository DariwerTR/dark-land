"""Small test map (default 1536x1536) in the final file format: relief, hill, mesa, lake, river, ravine, road and the fog ring.

Usage: python3 testmap.py <out dir> [size] [preview.png]
Checks the stage A machinery in the game (generator, biomes, fog wall, spawn); it is not the final map.
"""
import sys

import numpy as np
from scipy.ndimage import gaussian_filter, binary_dilation

import mapmodel as mm
from mapmodel import MapModel


def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0.0, 1.0)
    return t * t * (3 - 2 * t)


class Poly:
    """A polyline in pixel coordinates: distance to it and the position along it (0..1) for every pixel."""

    def __init__(self, pts, n):
        self.pts = np.asarray(pts, dtype=np.float32)
        yy, xx = np.mgrid[0:n, 0:n].astype(np.float32)
        best = np.full((n, n), 1e9, dtype=np.float32)
        along = np.zeros((n, n), dtype=np.float32)
        seg = np.diff(self.pts, axis=0)
        ln = np.hypot(seg[:, 0], seg[:, 1])
        total = ln.sum()
        cum = np.concatenate([[0], np.cumsum(ln)])
        for k in range(len(seg)):
            (x0, z0), (dx, dz) = self.pts[k], seg[k]
            t = np.clip(((xx - x0) * dx + (yy - z0) * dz) / (ln[k] ** 2), 0, 1)
            d = np.hypot(xx - (x0 + t * dx), yy - (z0 + t * dz))
            m = d < best
            best[m] = d[m]
            along[m] = ((cum[k] + t * ln[k]) / total)[m]
        self.dist, self.along = best, along


def wiggle(rng, a, b, n=7, amp=40):
    """A winding path from a to b."""
    a, b = np.asarray(a, np.float32), np.asarray(b, np.float32)
    d = b - a
    nrm = np.array([-d[1], d[0]]) / np.hypot(*d)
    out = []
    for i in range(n):
        t = i / (n - 1)
        out.append(a + d * t + nrm * rng.uniform(-amp, amp) * np.sin(np.pi * t))
    return out


def build(size):
    N = size
    c = N // 2
    m = MapModel(N, -c, -c)
    rng = np.random.default_rng(7)

    def field(sigma):
        f = gaussian_filter(rng.standard_normal((N, N)).astype(np.float32), sigma, mode="reflect")
        return f / f.std()

    yy, xx = np.mgrid[0:N, 0:N].astype(np.float32)
    h = 76 + 6 * field(120) + 3.5 * field(46) + 1.5 * field(16) + 0.4 * field(5)

    # spawn plateau at world (0,0)
    r = np.hypot(xx - c, yy - c)
    h = 71 + (h - 71) * smoothstep(34, 100, r)
    # hill to the north-east
    hx, hz = c + 0.27 * N, c - 0.25 * N
    h += 52 * np.exp(-((xx - hx) ** 2 + (yy - hz) ** 2) / (2 * (0.075 * N) ** 2))
    # mesa to the south-west
    mx, mz = c - 0.25 * N, c + 0.22 * N
    rm = np.hypot(xx - mx, yy - mz) + 9 * field(10)
    h = h + (112 + 2 * field(14) - h) * (1 - smoothstep(0.075 * N, 0.081 * N, rm))
    # lake to the north-west (water is added at the end wherever the ground is below the sea level)
    lx, lz = c - 0.27 * N, c - 0.20 * N
    h -= 24 * np.exp(-((xx - lx) ** 2 + (yy - lz) ** 2) / (2 * (0.05 * N) ** 2))
    # south-eastern plateau with a ravine across it (the floor stays above the sea level)
    px_, pz_ = c + 0.26 * N, c + 0.24 * N
    h += 30 * (1 - smoothstep(0.10 * N, 0.17 * N, np.hypot(xx - px_, yy - pz_) + 14 * field(12)))
    rav = Poly(wiggle(rng, (c + 0.12 * N, c + 0.34 * N), (c + 0.42 * N, c + 0.14 * N), 8, 30), N)
    prof = np.exp(-(rav.dist / 9.0) ** 4)
    cut = prof * smoothstep(0.0, 0.14, rav.along) * (1 - smoothstep(0.86, 1.0, rav.along))
    h = np.maximum(h - 40 * cut, np.minimum(h, 66.0 + 2 * (1 - cut)))
    # river from the hill foot into the lake
    riv = Poly(wiggle(rng, (hx - 0.03 * N, hz + 0.06 * N), (lx + 0.02 * N, lz + 0.01 * N), 9, 70), N)
    level = 87 - 24 * riv.along
    bed = level - 2.5
    d = riv.dist
    profile = np.where(d < 4, bed + (d / 4) ** 2 * 2.5, level + 0.5 + (d - 4) * 0.55)
    h = np.where(d < 30, np.minimum(h, profile), h)

    # road: spawn -> hill foot -> mesa (graded: the ground under it follows a smoothed line)
    road = Poly([(c, c), (c + 0.10 * N, c - 0.06 * N), (c + 0.20 * N, c - 0.17 * N), (hx - 0.02 * N, hz + 0.09 * N)], N)
    road2 = Poly([(c, c), (c - 0.10 * N, c + 0.07 * N), (mx + 0.07 * N, mz - 0.04 * N)], N)
    smooth = gaussian_filter(h, 6)
    for rd in (road, road2):
        w = 1 - smoothstep(2.0, 6.5, rd.dist)
        h = h * (1 - w) + smooth * w

    H = np.rint(h).astype(np.int16)
    sea = m.sea_level
    water = np.zeros((N, N), dtype=np.int16)
    lvl = np.rint(level).astype(np.int16)
    river = (d < 5) & (H < lvl)
    water[river] = lvl[river]
    lake = H < sea
    water[lake & ~river] = sea
    m.heights, m.water = H, water

    # biomes (4x4 cells): nearest of a few jittered seeds
    q = N // 4
    qy, qx = np.mgrid[0:q, 0:q].astype(np.float32) * 4 + 2
    seeds = [(mm.B_CURSED, c + 0.0 * N, c + 0.0 * N), (mm.B_CURSED, c + 0.3 * N, c + 0.3 * N), (mm.B_DEAD, c - 0.32 * N, c - 0.05 * N),
             (mm.B_DEAD, c - 0.1 * N, c - 0.33 * N), (mm.B_NECRO, c - 0.02 * N, c + 0.3 * N), (mm.B_CANYON, c + 0.30 * N, c + 0.20 * N),
             (mm.B_NECRO, c + 0.28 * N, c - 0.30 * N)]
    def qfield(sigma):
        f = gaussian_filter(rng.standard_normal((q, q)).astype(np.float32), sigma, mode="reflect")
        return f / f.std()
    wx_, wz_ = qx + 70 * qfield(18) + 18 * qfield(6), qy + 70 * qfield(18) + 18 * qfield(6)
    dist = np.stack([np.hypot(wx_ - sx, wz_ - sz) for (_, sx, sz) in seeds])
    ids = np.array([s[0] for s in seeds], dtype=np.uint8)
    m.biomes = ids[np.argmin(dist, axis=0)].astype(np.uint8)
    m.apply_fog_ring()

    # surface kinds
    gz, gx = np.gradient(H.astype(np.float32))
    slope = gaussian_filter(np.hypot(gx, gz), 1.5)
    bio = np.kron(m.biomes, np.ones((4, 4), dtype=np.uint8))
    surf = np.zeros((N, N), dtype=np.uint8)                      # GRASS
    patch = field(9)
    surf[(bio == mm.B_DEAD) & (patch > 0.3)] = mm.SOIL
    surf[(bio == mm.B_NECRO) & (patch > -0.2)] = mm.ASH
    surf[bio == mm.B_CANYON] = mm.COARSE
    surf[(slope > 1.0) | ((H > 124) & (slope > 0.55))] = mm.ROCK
    surf[prof > 0.35] = mm.DEEPROCK
    wet = water > 0
    near = binary_dilation(wet, iterations=3)
    surf[near & ~wet] = mm.GRAVEL
    surf[binary_dilation(lake & ~river, iterations=3) & ~wet] = mm.SAND
    surf[wet] = np.where(river[wet], mm.GRAVEL, np.where(field(7)[wet] > 0.4, mm.MUD, mm.SAND))
    # the path goes last, and not through water
    on_road = ((road.dist < 1.6) | (road2.dist < 1.6)) & ~wet
    surf[on_road] = mm.PATH
    m.surface = surf
    return m


def preview(m, path):
    from PIL import Image
    N = m.size
    H = m.heights.astype(np.float32)
    gz, gx = np.gradient(H)
    shade = np.clip(0.78 + 1.6 * (-gx - gz) / 2.0 * 0.5, 0.35, 1.25)
    pal = {mm.GRASS: (66, 74, 52), mm.SOIL: (84, 66, 52), mm.GRAVEL: (118, 114, 108), mm.SAND: (150, 138, 104), mm.ROCK: (104, 102, 106),
           mm.PATH: (190, 160, 110), mm.MUD: (70, 56, 46), mm.ASH: (96, 92, 96), mm.COARSE: (120, 62, 52), mm.DEEPROCK: (62, 60, 66)}
    lut = np.zeros((256, 3), dtype=np.float32)
    for k, v in pal.items():
        lut[k] = v
    img = lut[m.surface] * shade[..., None]
    wet = m.water > 0
    depth = np.clip((m.water - m.heights).astype(np.float32), 0, 14) / 14
    img[wet] = (np.array([60, 90, 120]) * (1 - 0.6 * depth[wet, None]))
    bio = np.kron(m.biomes, np.ones((4, 4), dtype=np.uint8))
    for b, k in ((mm.B_FOG_FAR, 0.2), (mm.B_FOG_EDGE, 0.5), (mm.B_FOG_WALL, 0.85)):
        img[bio == b] = img[bio == b] * (1 - k) + np.array([80, 72, 92]) * k
    # the turn-back line and the world origin
    ed = m.edge_distance()
    img[np.abs(ed - mm.BORDER_MARGIN) < 0.8] = (230, 80, 80)
    c = N // 2
    img[c - 6:c + 7, c - 1:c + 2] = (255, 255, 255)
    img[c - 1:c + 2, c - 6:c + 7] = (255, 255, 255)
    Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).resize((N // 2, N // 2), Image.LANCZOS).save(path)


if __name__ == "__main__":
    out = sys.argv[1]
    size = int(sys.argv[2]) if len(sys.argv) > 2 else 1536
    m = build(size)
    sizes = m.save(out, {"generator": "testmap", "spawn": [0, 0]})
    print("map", size, "layers (bytes):", sizes, "total", sum(sizes.values()))
    if len(sys.argv) > 3:
        preview(m, sys.argv[3])
