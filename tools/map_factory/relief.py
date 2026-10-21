"""Macro relief of the big map on a coarse grid (CELL blocks per cell): regions, mountain rim, throne plateau, hills."""
import numpy as np
from scipy.ndimage import gaussian_filter

from design import REGIONS, BUMPS, THRONE
import hills as hillmod
from noisegen import fbm, ridged, smoothstep, warp, eroded_fbm

CELL = 4


def grid(D):
    M = D.size // CELL
    x = ((np.arange(M, dtype=np.float32) + 0.5) * CELL)[None, :]
    z = ((np.arange(M, dtype=np.float32) + 0.5) * CELL)[:, None]
    return M, np.broadcast_to(x, (M, M)), np.broadcast_to(z, (M, M))


def region_weights(D, rng, X, Z, tau=300.0, warp=230.0):
    """Soft Voronoi weights of the regions (list of M x M arrays) and the index of the dominant one."""
    M = X.shape[0]
    wl = 950.0 / CELL
    wx = fbm((M, M), wl, rng, 4) * warp
    wz = fbm((M, M), wl, rng, 4) * warp
    ds = []
    for _, (sx, sz), *_ in REGIONS:
        px, pz = D.px(sx, sz)
        ds.append(np.hypot(X + wx - px, Z + wz - pz))
    dmin = np.minimum.reduce(ds)
    ws = [np.exp(-(d - dmin) / tau) for d in ds]
    tot = np.add.reduce(ws)
    ws = [w / tot for w in ws]
    return ws, np.argmin(np.stack(ds), axis=0).astype(np.uint8)


def macro_relief(D, rng, log=print):
    M, X, Z = grid(D)
    S = D.size
    log("regions")
    ws, dominant = region_weights(D, rng, X, Z)
    def unit(a):
        return (a - a.mean()) / a.std()
    fields = {
        "plain": unit(warp(eroded_fbm((M, M), 560 / CELL, rng, 6, damp=1.1), rng, 700 / CELL, 90 / CELL)),
        "hills": unit(warp(eroded_fbm((M, M), 900 / CELL, rng, 6, damp=0.9), rng, 900 / CELL, 160 / CELL)),
        "flat": fbm((M, M), 320 / CELL, rng, 4),
    }
    r = warp(ridged((M, M), 760 / CELL, rng, 6), rng, 800 / CELL, 140 / CELL)
    fields["ridge"] = (np.maximum(r, 0) ** 1.35 - 0.2) * 2.2
    u = np.clip(0.5 + 0.30 * fbm((M, M), 640 / CELL, rng, 4), 0, 1)
    L = 5.0
    stepped = (np.floor(u * L) + smoothstep(0.30, 0.70, u * L - np.floor(u * L))) / L
    fields["mesa"] = (stepped - 0.5) * 2.2 + 0.25 * fbm((M, M), 120 / CELL, rng, 3)

    log("heights")
    h = np.zeros((M, M), dtype=np.float32)
    rim_amp = np.zeros((M, M), dtype=np.float32)
    cont = fbm((M, M), 3200 / CELL, rng, 2) * 8.0
    for w, (_, _, _, h0, amp, kind, rim) in zip(ws, REGIONS):
        gain = {"plain": 1.0, "hills": 0.8}.get(kind, 1.0)
        h += w * (h0 + gain * amp * fields[kind] + (0 if kind == "flat" else cont))
        rim_amp += w * rim

    h += hillmod.scatter(D, rng, X, Z, ws, CELL, log)
    h += hillmod.spires(D, rng, X, Z, ws, CELL, log)

    # the mountain rim around the whole map: a crest about 420 blocks inside the edge
    e = np.minimum(np.minimum(X, S - X), np.minimum(Z, S - Z))
    crest = np.exp(-(((e - 430.0) / 270.0) ** 2))
    rr = ridged((M, M), 520 / CELL, rng, 5)
    h += rim_amp * crest * (55.0 + 70.0 * rr)

    # hills and the like
    for _, pos, hh, sig in BUMPS:
        px, pz = D.px(*pos)
        h += hh * np.exp(-((X - px) ** 2 + (Z - pz) ** 2) / (2 * sig * sig))

    # the throne plateau
    px, pz = D.px(*THRONE["pos"])
    rw = np.hypot(X - px + 45 * fbm((M, M), 300 / CELL, rng, 3), Z - pz + 45 * fbm((M, M), 300 / CELL, rng, 3))
    t = smoothstep(THRONE["radius"] + THRONE["skirt"], THRONE["radius"], rw)
    target = THRONE["top"] + 1.4 * fbm((M, M), 140 / CELL, rng, 3)
    h = h * (1 - t) + target * t
    h = 65.0 + 5.0 * np.log1p(np.exp((h - 65.0) / 5.0))        # soft floor: no accidental seas, lakes are carved on purpose
    h = gaussian_filter(h, 0.8)
    return dict(h=h.astype(np.float32), dominant=dominant, weights=ws, M=M)
