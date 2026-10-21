"""Colour previews of a written cursed_lands_map/ directory: surface kinds under a hillshade, water, optional biome and fog tint.
Usage: python3 mapview.py <map dir> <out png> [overview|x0,z0,size] [--biomes]"""
import json
import sys

import numpy as np
from PIL import Image

import clm
import preview_big

SURFACE_RGB = {0: (62, 74, 50), 1: (88, 66, 50), 2: (112, 108, 104), 3: (176, 160, 118), 4: (118, 116, 122), 5: (140, 112, 76),
               6: (60, 46, 42), 7: (84, 78, 80), 8: (112, 88, 62), 9: (60, 62, 76)}
BIOME_RGB = [(60, 140, 60), (150, 100, 40), (120, 120, 160), (190, 40, 40), (200, 200, 200), (170, 170, 170), (130, 130, 130),
             (110, 170, 220), (120, 90, 90), (230, 220, 160), (150, 60, 120)]


def load(map_dir):
    meta = json.load(open(f"{map_dir}/meta.json", encoding="utf-8"))
    return meta, clm.read_layer(f"{map_dir}/heights.bin"), clm.read_layer(f"{map_dir}/water.bin"), clm.read_layer(f"{map_dir}/surface.bin"), clm.read_layer(f"{map_dir}/biomes.bin")


def render(H, W, S, B, path, window=None, biomes=False, f=1):
    h = H.astype(np.float32)
    if window:
        from scipy.ndimage import gaussian_filter
        x0, z0, n = window
        sl = (slice(z0, z0 + n), slice(x0, x0 + n))
        h, w, k = gaussian_filter(h[sl], 1.2), W[sl], S[sl]
        b = np.repeat(np.repeat(B, 4, 0), 4, 1)[sl] if biomes else None
    else:
        n = h.shape[0] // f
        h = h.reshape(n, f, n, f).mean(axis=(1, 3))
        w = W.reshape(n, f, n, f).max(axis=(1, 3))
        k = S[::f, ::f]
        b = np.repeat(np.repeat(B, 4, 0), 4, 1)[::f, ::f] if biomes else None
    cell = float(f) if not window else 1.0
    pal = np.array([SURFACE_RGB[i] for i in range(10)], np.float32)
    rgb = pal[k]
    if biomes and b is not None:
        bp = np.array(BIOME_RGB, np.float32)
        rgb = rgb * 0.55 + bp[b] * 0.45
    rgb = rgb * (0.30 + 1.0 * preview_big.hillshade(h, cell, exag=2.2 if window else 3.0))[..., None]
    wet = w > 0
    rgb[wet] = rgb[wet] * 0.30 + np.array([60, 110, 150]) * 0.70
    Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8)).save(path)


if __name__ == "__main__":
    meta, H, W, S, B = load(sys.argv[1])
    spec = sys.argv[3] if len(sys.argv) > 3 else "overview"
    biomes = "--biomes" in sys.argv
    if spec == "overview":
        render(H, W, S, B, sys.argv[2], None, biomes, f=6)
    else:
        x0, z0, n = (int(v) for v in spec.split(","))
        render(H, W, S, B, sys.argv[2], (x0, z0, n), biomes)
