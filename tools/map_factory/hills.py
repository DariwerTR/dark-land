"""Scattered hills: elongated gaussian bumps whose density and size depend on the region; gives the rolling land its readable summits."""
import numpy as np

from design import REGIONS, HILLS, SPIRES


def scatter(D, rng, X, Z, weights, cell, log=print):
    M = X.shape[0]
    S = D.size
    out = np.zeros((M, M), np.float32)
    names = [r[0] for r in REGIONS]
    count = 0
    for i, name in enumerate(names):
        if name not in HILLS:
            continue
        spacing, hmin, hmax, smin, smax, hollow = HILLS[name]
        w = weights[i]
        n = int(w.sum() * cell * cell / (spacing * spacing))
        # candidates uniformly, accepted by the weight of the region at that point
        cand = int(n * 3 + 200)
        px = rng.random(cand) * S
        pz = rng.random(cand) * S
        wi = w[np.clip((pz / cell).astype(int), 0, M - 1), np.clip((px / cell).astype(int), 0, M - 1)]
        acc = rng.random(cand) < wi
        px, pz = px[acc][:n], pz[acc][:n]
        for x, z in zip(px, pz):
            sig = rng.uniform(smin, smax)
            aspect = rng.uniform(1.0, 2.4)
            ang = rng.uniform(0, np.pi)
            hh = rng.uniform(hmin, hmax) * (-0.6 if rng.random() < hollow else 1.0)
            r = 3.0 * sig * aspect
            x0, x1 = int(max((x - r) / cell, 0)), int(min((x + r) / cell + 1, M))
            z0, z1 = int(max((z - r) / cell, 0)), int(min((z + r) / cell + 1, M))
            if x1 <= x0 or z1 <= z0:
                continue
            xs = X[0, x0:x1] - x
            zs = Z[z0:z1, 0] - z
            ca, sa = np.cos(ang), np.sin(ang)
            u = (xs[None, :] * ca + zs[:, None] * sa) / (sig * aspect)
            v = (-xs[None, :] * sa + zs[:, None] * ca) / sig
            out[z0:z1, x0:x1] += hh * np.exp(-0.5 * (u * u + v * v))
            count += 1
    if log:
        log(f"  {count} hills")
    return out


def spires(D, rng, X, Z, weights, cell, log=print):
    """Sharp rock spires (cusp-tipped, slightly elongated cones) scattered where the regions of SPIRES dominate."""
    M = X.shape[0]
    S = D.size
    out = np.zeros((M, M), np.float32)
    names = [r[0] for r in REGIONS]
    total = 0
    for i, name in enumerate(names):
        if name not in SPIRES:
            continue
        count, hmin, hmax, smin, smax = SPIRES[name]
        w = weights[i]
        placed = 0
        tries = 0
        while placed < count and tries < count * 400:
            tries += 1
            x, z = rng.random() * S, rng.random() * S
            if rng.random() >= w[min(int(z / cell), M - 1), min(int(x / cell), M - 1)] ** 2:
                continue
            sig = rng.uniform(smin, smax)
            aspect = rng.uniform(1.0, 1.7)
            ang = rng.uniform(0, np.pi)
            hh = hmin + (hmax - hmin) * rng.random() ** 1.6
            r = 5.0 * sig * aspect
            x0, x1 = int(max((x - r) / cell, 0)), int(min((x + r) / cell + 1, M))
            z0, z1 = int(max((z - r) / cell, 0)), int(min((z + r) / cell + 1, M))
            if x1 <= x0 or z1 <= z0:
                continue
            xs = X[0, x0:x1] - x
            zs = Z[z0:z1, 0] - z
            ca, sa = np.cos(ang), np.sin(ang)
            u = (xs[None, :] * ca + zs[:, None] * sa) / (sig * aspect)
            v = (-xs[None, :] * sa + zs[:, None] * ca) / sig
            q = np.hypot(u, v)
            out[z0:z1, x0:x1] += hh * np.exp(-q ** 1.15)
            placed += 1
        total += placed
    if log:
        log(f"  {total} spires")
    return out
