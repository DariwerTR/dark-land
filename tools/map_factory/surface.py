"""Surface kinds (the "surface" layer) and biomes (the "biomes" layer) of the big map, derived from the relief, the water and the region map."""
import numpy as np
from scipy.ndimage import distance_transform_edt, gaussian_filter
from scipy.special import ndtr

import mapmodel as mm
from noisegen import vnoise

G, S_, GR, SA, RK, PA, MU, AS, CO, DR = (mm.GRASS, mm.SOIL, mm.GRAVEL, mm.SAND, mm.ROCK, mm.PATH, mm.MUD, mm.ASH, mm.COARSE, mm.DEEPROCK)

# region name: (base surface kind, [(kind, share of the region covered by patches of it)], slope above which the ground turns into rock);
# every patch layer has a noise field of its own, so the patches do not nest into each other
REGION_SURFACE = {
    "Плато Трона": (G, [(S_, 0.22), (CO, 0.08)], 0.95),
    "Хребет Шпилей": (G, [(RK, 0.35), (GR, 0.18), (S_, 0.15)], 0.55),
    "Разломы": (CO, [(S_, 0.30), (MU, 0.10), (G, 0.18)], 0.80),
    "Некрополь": (S_, [(G, 0.35), (AS, 0.15), (GR, 0.12)], 0.95),
    "Мёртвый лес": (G, [(S_, 0.30), (CO, 0.10), (MU, 0.05)], 0.95),
    "Пепельная пустошь": (AS, [(CO, 0.28), (RK, 0.10), (GR, 0.08)], 0.70),
    "Холмы Аббатства": (G, [(S_, 0.22), (CO, 0.08)], 0.90),
    "Костяная долина": (CO, [(GR, 0.25), (G, 0.30), (SA, 0.08)], 0.95),
    "Вороний край": (G, [(S_, 0.25), (CO, 0.08)], 0.90),
    "Топи у озера": (G, [(MU, 0.35), (S_, 0.12)], 1.1),
    "Равнина Тракта": (G, [(S_, 0.12), (GR, 0.06)], 1.0),
    "Предгорья": (G, [(RK, 0.25), (GR, 0.15), (S_, 0.12)], 0.70),
    "Южные ворота": (G, [(S_, 0.08), (GR, 0.05)], 1.0),
}


def patch_field(shape, rng):
    """Smooth patch values uniform in 0..1."""
    nz = 0.6 * vnoise(shape, 140.0, rng) + 0.8 * vnoise(shape, 70.0, rng) + 0.55 * vnoise(shape, 28.0, rng) + 0.25 * vnoise(shape, 11.0, rng)
    return ndtr(nz / float(nz.std())).astype(np.float32)


def region_map(dominant, shape, rng):
    """Region index for every block column; the edges of the 4 block macro cells are ragged by a few blocks."""
    n = shape[0]
    q = dominant.shape[0]
    out = np.empty(shape, np.uint8)
    nz = vnoise(shape, 9.0, rng) * 2.6
    nx = vnoise(shape, 9.0, rng) * 2.6
    step = 512
    xs = np.arange(n, dtype=np.float32)
    for z0 in range(0, n, step):
        z1 = min(z0 + step, n)
        zi = np.clip(((np.arange(z0, z1, dtype=np.float32)[:, None] + nz[z0:z1]) / 4).astype(np.int32), 0, q - 1)
        xi = np.clip(((xs[None, :] + nx[z0:z1]) / 4).astype(np.int32), 0, q - 1)
        out[z0:z1] = dominant[zi, xi]
    return out


def build_surface(H, WATER, dominant, regions, rng, log=print):
    n = H.shape[0]
    names = [r[0] for r in regions]
    log("surface: slope")
    Hs = gaussian_filter(H, 1.5)
    gz, gx = np.gradient(Hs)
    slope = np.hypot(gx, gz).astype(np.float32)
    del gx, gz, Hs
    log("surface: patches")
    jag = vnoise((n, n), 17.0, rng)
    reg = region_map(dominant, (n, n), rng)
    kind = np.zeros((n, n), np.uint8)
    thr = np.ones((n, n), np.float32)
    for i, name in enumerate(names):
        base, _, rock_slope = REGION_SURFACE[name]
        m = reg == i
        kind[m] = base
        thr[m] = rock_slope
        log(f"  {name}: {int(m.sum()) / n / n * 100:.1f}% of the map")
    p = None
    for layer in range(3):
        p = patch_field((n, n), rng)
        for i, name in enumerate(names):
            patches = REGION_SURFACE[name][1]
            if layer < len(patches):
                kind_id, share = patches[layer]
                kind[(reg == i) & (p > 1.0 - share)] = kind_id
    log("surface: rock and altitude")
    steep = slope > thr * (1.0 + 0.18 * jag)
    kind[steep] = RK
    high = H > 172.0 + 14.0 * jag
    kind[high & (kind != RK)] = RK
    deep = ((steep & (H > 150.0)) | (H > 222.0 + 8.0 * jag)) & (p > 0.45)
    kind[deep] = DR
    kind[(slope > 0.9) & (H < 56.0)] = DR                      # canyon and lake walls below the sea level
    del steep, high, deep, slope, thr
    log("surface: shores")
    wet = WATER > 0
    dw = distance_transform_edt(~wet).astype(np.float32)
    shore = vnoise((n, n), 8.0, rng)
    near = (dw > 0) & (dw <= 1.8 + 0.9 * shore) & (kind != RK) & (kind != DR)
    kind[near] = SA
    ring = (dw > 0) & (dw <= 4.8 + 1.6 * shore) & (kind != RK) & (kind != DR) & (kind != SA) & (p > 0.35)
    kind[ring] = GR
    fen = np.array([i for i, nm in enumerate(names) if nm == "Топи у озера"])
    del near, ring, shore
    # the fen is muddy around its water
    reg_fen = reg == fen[0]
    del reg
    mud = reg_fen & (dw > 0) & (dw <= 9.0 + 6.0 * p) & (kind != RK) & (kind != DR)
    kind[mud] = MU
    del mud, reg_fen, dw
    log("surface: the beds")
    depth = np.where(wet, WATER.astype(np.float32) - H, 0.0)
    bed = np.where(depth <= 2.2 + 1.0 * jag, SA, GR).astype(np.uint8)
    kind[wet] = bed[wet]
    del bed, depth
    # the floors of the canyons
    kind[wet & (H < 45.5)] = DR
    return kind


def build_biomes(dominant, regions):
    table = np.array([mm.BIOMES.index(r[2]) for r in regions], np.uint8)
    return table[dominant]
