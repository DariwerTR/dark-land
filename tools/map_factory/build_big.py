"""Builds the big map. Usage: python3 build_big.py <out dir> <cache dir> [size] [from-stage]

Stages (each is cached in <cache dir> so that a later stage can be redone alone):
  1 macro relief   2 full resolution relief + detail   3 water (lakes, rivers, rift fissures)   4 surface and biomes   5 write files
"""
import os
import sys
import time

import numpy as np
from scipy.ndimage import zoom, gaussian_filter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import design as ds
import hydro
import mapmodel as mm
import relief
import surface
from noisegen import vnoise


def log(*a):
    print(time.strftime("%H:%M:%S"), *a, flush=True)


def stage_macro(D, rng, cache):
    R = relief.macro_relief(D, rng, log)
    np.save(f"{cache}/macro.npy", R["h"])
    np.save(f"{cache}/dominant.npy", R["dominant"])
    return R["h"], R["dominant"]


def stage_full(D, macro, dominant, rng, cache):
    S = D.size
    cell = relief.CELL
    log("upsample")
    H = zoom(macro, cell, order=3, mode="reflect", grid_mode=True, output=np.float32)
    gy, gx = np.gradient(macro, cell)
    slope = np.hypot(gx, gy).astype(np.float32)
    rough = np.clip(0.45 + 2.2 * slope, 0.45, 1.6)
    flat_regions = [i for i, r in enumerate(ds.REGIONS) if r[0] in ("Топи у озера", "Разломы", "Костяная долина")]
    rough[np.isin(dominant, flat_regions)] *= 0.5
    rough = zoom(gaussian_filter(rough, 1.5), cell, order=1, mode="reflect", grid_mode=True, output=np.float32)
    log("detail")
    for wl, amp in ((160, 2.6), (80, 2.0), (40, 1.4), (20, 0.9), (10, 0.55), (5, 0.3)):
        H += rough * amp * vnoise((S, S), wl, rng)
        log(f"  octave {wl}")
    del rough
    log("mountain crests")
    mt = zoom(np.clip((macro - 135.0) / 70.0, 0, 1), cell, order=1, mode="reflect", grid_mode=True, output=np.float32)
    mt = gaussian_filter(mt, 6.0)
    for wl, amp in ((130, 7.0), (65, 4.2), (32, 2.4)):
        n = vnoise((S, S), wl, rng)
        H += mt * amp * (0.8 - 1.2 * np.abs(n))                # sharp crests, rounded gullies
        log(f"  crest octave {wl}")
    del mt, n
    log("rough flanks")
    gz, gx = np.gradient(gaussian_filter(H, 1.5))
    sm = np.clip((np.hypot(gx, gz) - 0.45) / 0.6, 0, 1).astype(np.float32)
    del gx, gz
    for wl, amp in ((24, 4.0), (12, 2.4), (6, 1.4)):
        H += sm * amp * vnoise((S, S), wl, rng)
    del sm
    peak = H > 225.0                                          # the build limit is Y 319: the highest crests are compressed softly
    H[peak] = 225.0 + 75.0 * np.tanh((H[peak] - 225.0) / 75.0)
    del peak
    np.save(f"{cache}/full.npy", H)
    return H


def tarn_level(H, D):
    lk = ds.LAKES[1]
    px, pz = D.px(*lk["pos"])
    patch = H[int(pz) - 40:int(pz) + 41, int(px) - 40:int(px) + 41]
    return int(min(lk["level"], np.floor(patch.mean()) - 3))


def stage_water(D, H, rng, cache):
    S = D.size
    WATER = np.zeros((S, S), np.int16)
    before = H.copy()
    log("lakes")
    levels = {}
    for k, lk in enumerate(ds.LAKES):
        cx, cz = D.px(*lk["pos"])
        level = lk["level"] if k == 0 else tarn_level(H, D)
        levels[lk["name"]] = level
        hydro.carve_lake(H, WATER, cx, cz, lk["rx"], lk["rz"], lk["rot"], level, lk["depth"], rng, log)
    log("rivers")
    paths = {}
    for k, rv in enumerate(ds.RIVERS):
        P = D.line(rv["pts"])
        xs, zs, s = hydro.polyline(P, rng, spacing=1.0, meander=28.0 if k == 0 else 18.0)
        if k == 0:
            L = hydro.carve_river(H, WATER, xs, zs, s, rv["w0"], rv["w1"], rv["depth"], level_end=ds.LAKES[0]["level"],
                                  level_start=levels["Багровый тарн"], log=log)
        else:
            mx, mz, ML = paths["main"]
            j = int(np.argmin(np.hypot(mx - xs[-1], mz - zs[-1])))
            xs, zs, s = hydro.snap_end(xs, zs, mx[j], mz[j])
            d = np.hypot(mx - xs[-1], mz - zs[-1])
            j = int(np.argmin(d))
            L = hydro.carve_river(H, WATER, xs, zs, s, rv["w0"], rv["w1"], rv["depth"], level_end=int(ML[j]), log=log)
            log(f"    joins the main river at ({mx[j]:.0f}, {mz[j]:.0f}), {d[j]:.1f} blocks off")
        if k == 0:
            paths["main"] = (xs, zs, L)
        np.save(f"{cache}/river{k}.npy", np.stack([xs, zs, L.astype(np.float64)]))
    log("fissures")
    for fs in ds.FISSURES:
        P = D.line(fs["pts"])
        xs, zs, s = hydro.polyline(P, rng, spacing=1.0, meander=16.0, knot=60.0, taper=60.0)
        hydro.carve_fissure(H, WATER, xs, zs, s, fs["width"], fs["floor"], rng=rng, log=log)
    hydro.roughen(H, WATER, before, rng, log)
    del before
    log("spawn pad")
    sx, sz = D.spawn_px
    hydro.flatten(H, WATER, sx, sz, 36, 80, log=log)
    np.save(f"{cache}/H_water.npy", H)
    np.save(f"{cache}/water.npy", WATER)
    return H, WATER


def main():
    out, cache = sys.argv[1], sys.argv[2]
    size = int(sys.argv[3]) if len(sys.argv) > 3 else 6144
    start = int(sys.argv[4]) if len(sys.argv) > 4 else 1
    os.makedirs(cache, exist_ok=True)
    D = ds.Design(size)
    rng = np.random.default_rng(21)
    macro = dominant = H = WATER = None
    if start <= 1:
        macro, dominant = stage_macro(D, rng, cache)
    else:
        macro, dominant = np.load(f"{cache}/macro.npy"), np.load(f"{cache}/dominant.npy")
    rng = np.random.default_rng(22)
    if start <= 2:
        H = stage_full(D, macro, dominant, rng, cache)
    elif start == 3:
        H = np.load(f"{cache}/full.npy")
    rng = np.random.default_rng(23)
    if start <= 3:
        H, WATER = stage_water(D, H, rng, cache)
    else:
        H, WATER = np.load(f"{cache}/H_water.npy"), np.load(f"{cache}/water.npy")
    log("done through stage 3", H.shape, float(H.min()), float(H.max()), int((WATER > 0).sum()), "water cells")
    if start <= 5:
        rng = np.random.default_rng(24)
        if start <= 4:
            kind = surface.build_surface(H, WATER, dominant, ds.REGIONS, rng, log)
            np.save(f"{cache}/surface.npy", kind)
        else:
            kind = np.load(f"{cache}/surface.npy")
        stage_write(D, H, WATER, kind, dominant, out)


def stage_write(D, H, WATER, kind, dominant, out):
    S = D.size
    model = mm.MapModel(S, D.origin[0], D.origin[1])
    wet = WATER > 0
    Hi = np.rint(H).astype(np.int16)
    Hi[wet] = np.minimum(Hi[wet], WATER[wet] - 1)               # the water surface must be above the ground
    model.heights = Hi
    model.water = np.where(wet, WATER, 0).astype(np.int16)
    model.surface = kind
    model.biomes = surface.build_biomes(dominant, ds.REGIONS)
    model.apply_fog_ring()
    sizes = model.save(f"{out}/cursed_lands_map", extra={"generator": "map_factory/build_big.py", "spawn": [0, 0]})
    log("written", {k: f"{v / 1e6:.2f} MB" for k, v in sizes.items()})


if __name__ == "__main__":
    main()
