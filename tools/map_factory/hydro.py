"""Rivers, lakes and rift fissures carved into the full resolution height grid (float32, [z, x]) together with the water layer (int16)."""
import numpy as np
from scipy.interpolate import splprep, splev, CubicSpline
from scipy.ndimage import distance_transform_edt, uniform_filter1d, gaussian_filter

from noisegen import vnoise, smoothstep


def polyline(P, rng, spacing=1.0, meander=0.0, knot=150.0, taper=300.0):
    """Smooth path through the points P (n x 2, pixels) resampled every `spacing` blocks, optionally with meanders."""
    P = np.asarray(P, np.float64)
    tck, _ = splprep([P[:, 0], P[:, 1]], s=0, k=min(3, len(P) - 1))
    u = np.linspace(0, 1, 40000)
    x, z = splev(u, tck)
    seg = np.hypot(np.diff(x), np.diff(z))
    s = np.concatenate([[0], np.cumsum(seg)])
    n = int(s[-1] / spacing)
    ss = np.linspace(0, s[-1], n)
    x, z = np.interp(ss, s, x), np.interp(ss, s, z)
    if meander > 0:
        nk = max(4, int(ss[-1] / knot))
        kk = np.linspace(0, ss[-1], nk)
        off = CubicSpline(kk, rng.standard_normal(nk) * meander)(ss)
        off *= smoothstep(0, taper, ss) * smoothstep(0, taper, ss[-1] - ss)
        dx, dz = np.gradient(x), np.gradient(z)
        norm = np.hypot(dx, dz)
        x, z = x - dz / norm * off, z + dx / norm * off
    s = np.concatenate([[0], np.cumsum(np.hypot(np.diff(x), np.diff(z)))])
    return x, z, s


def window(shape, xs, zs, pad):
    h, w = shape
    x0, x1 = int(max(xs.min() - pad, 0)), int(min(xs.max() + pad + 1, w))
    z0, z1 = int(max(zs.min() - pad, 0)), int(min(zs.max() + pad + 1, h))
    return z0, z1, x0, x1


def carve_river(H, WATER, xs, zs, s, w0, w1, depth, level_end=None, level_start=None, log=print, valley=200.0, slope=0.55):
    """Cuts a channel along the path. The water level only ever falls downstream and follows the terrain; banks stay above it.
    Returns the water level along the path (integer Y)."""
    n = len(xs)
    ix = np.clip(np.rint(xs).astype(int), 0, H.shape[1] - 1)
    iz = np.clip(np.rint(zs).astype(int), 0, H.shape[0] - 1)
    ground = H[iz, ix].astype(np.float64)
    smooth = uniform_filter1d(ground, size=int(300 / max(s[-1] / n, 1e-6)), mode="nearest")
    L = smooth - depth - 1.0
    if level_start is not None:
        L = np.minimum(L, level_start)
    L = np.minimum.accumulate(L)
    if level_end is not None:
        ramp = int(min(250, s[-1] * 0.25) / (s[-1] / n))
        L = np.maximum(L, level_end)
        t = np.clip((np.arange(n) - (n - 1 - ramp)) / max(ramp, 1), 0, 1)
        L = L * (1 - t) + level_end * t
        L = np.minimum.accumulate(L)
    L = np.floor(L).astype(np.int32)
    width = w0 + (w1 - w0) * (s / s[-1]) ** 0.8
    z0, z1, x0, x1 = window(H.shape, xs, zs, valley)
    mask = np.zeros((z1 - z0, x1 - x0), bool)
    idxmap = np.full(mask.shape, -1, np.int32)
    mask[iz - z0, ix - x0] = True
    idxmap[iz - z0, ix - x0] = np.arange(n, dtype=np.int32)
    d, (nz, nx) = distance_transform_edt(~mask, return_indices=True)
    near = idxmap[nz, nx]
    d = d.astype(np.float32)
    Lp = L[near].astype(np.float32)
    wp = width[near].astype(np.float32)
    T = H[z0:z1, x0:x1]
    # profile of the valley: channel, then banks rising with `slope`
    out = d - wp
    bank = Lp + 1.0 + np.maximum(out, 0) * slope
    cut = np.minimum(T, bank)
    levee = Lp + 1.0 - np.maximum(out - 4.0, 0) * 0.5
    newG = np.maximum(cut, levee)
    inside = d <= wp
    bed = Lp - depth * (1.0 - (d / np.maximum(wp, 0.5)) ** 2)
    newG = np.where(inside, bed, newG)
    use = d < valley
    G = np.where(use, newG, T)
    H[z0:z1, x0:x1] = G
    wet = inside & (G < Lp)
    cur = WATER[z0:z1, x0:x1]
    WATER[z0:z1, x0:x1] = np.where(wet, np.maximum(cur, Lp.astype(np.int16)), cur)
    if log:
        log(f"  river: {n} points, {s[-1]:.0f} blocks, water level {L[0]} -> {L[-1]}, widths {w0}-{w1}")
    return L


def carve_lake(H, WATER, cx, cz, rx, rz, rot_deg, level, depth, rng, log=print, bank_slope=0.4, wobble=0.17):
    """A lake with a smooth organic outline (an ellipse whose radius is modulated by a few harmonics, so it has bays and capes but no
    detached ponds), a bowl-shaped bed and banks that stay above the water."""
    R = max(rx, rz)
    pad = R * 1.9 + 60
    z0, z1 = int(max(cz - pad, 0)), int(min(cz + pad, H.shape[0]))
    x0, x1 = int(max(cx - pad, 0)), int(min(cx + pad, H.shape[1]))
    zz, xx = np.mgrid[z0:z1, x0:x1].astype(np.float32)
    a = np.radians(rot_deg)
    u = ((xx - cx) * np.cos(a) + (zz - cz) * np.sin(a)) / rx
    v = (-(xx - cx) * np.sin(a) + (zz - cz) * np.cos(a)) / rz
    theta = np.arctan2(v, u)
    shape = np.ones_like(theta)
    for k in range(2, 9):
        shape += wobble / k ** 0.7 * np.cos(k * theta + rng.uniform(0, 2 * np.pi))
    shape = np.maximum(shape, 0.55)
    q = np.sqrt(u * u + v * v) / shape
    q = q * (1 + 0.025 * vnoise(q.shape, 45.0, rng))            # a little roughness of the shore line itself
    T = H[z0:z1, x0:x1]
    r_eff = np.float32(np.sqrt(rx * rz))
    out = (q - 1.0) * r_eff                                   # blocks outside the shore
    bed = level - depth * np.clip(1.0 - q ** 2, 0, 1) ** 0.7 - 1.0
    bank = level + 1.0 + np.maximum(out, 0) * bank_slope
    levee = level + 1.0 - np.maximum(out - 6.0, 0) * 0.5
    G = np.where(q < 1.0, np.minimum(T, bed), np.maximum(np.minimum(T, bank), levee))
    use = q < 1.0 + 240.0 / r_eff
    H[z0:z1, x0:x1] = np.where(use, G, T)
    cur = WATER[z0:z1, x0:x1]
    WATER[z0:z1, x0:x1] = np.where((q < 1.0) & (G < level), np.maximum(cur, np.int16(level)), cur)
    if log:
        log(f"  lake at ({cx:.0f}, {cz:.0f}) {rx:.0f}x{rz:.0f}, level {level}, depth {depth}")


def carve_fissure(H, WATER, xs, zs, s, width, floor, wall=6, rng=None, water_level=63, log=print):
    """A canyon with near vertical walls: flat floor, width varies along the path, the ends close up. Floors below the water level are flooded."""
    n = len(xs)
    ix = np.clip(np.rint(xs).astype(int), 0, H.shape[1] - 1)
    iz = np.clip(np.rint(zs).astype(int), 0, H.shape[0] - 1)
    pad = float(width * 2.2 + 20)
    z0, z1, x0, x1 = window(H.shape, xs, zs, pad)
    mask = np.zeros((z1 - z0, x1 - x0), bool)
    idxmap = np.full(mask.shape, -1, np.int32)
    mask[iz - z0, ix - x0] = True
    idxmap[iz - z0, ix - x0] = np.arange(n, dtype=np.int32)
    d, (nz, nx) = distance_transform_edt(~mask, return_indices=True)
    near = idxmap[nz, nx]
    t = s / s[-1]
    w = width * (0.75 + 0.25 * np.sin(s / 140.0 + 1.3) + 0.15 * np.sin(s / 53.0)) * smoothstep(0.0, 0.12, t) * smoothstep(0.0, 0.12, 1 - t)
    wp = w[near].astype(np.float32)
    edge = (d / np.maximum(wp, 0.5)).astype(np.float32)
    jag = vnoise(d.shape, 14.0, rng) * 0.10
    jag2 = vnoise(d.shape, 37.0, rng) * 0.22
    pf = np.exp(-np.maximum(edge + jag, 0) ** (2 * wall)) * (wp > 1.0)              # the sheer lower wall and the floor
    pl = np.exp(-np.maximum(edge * 0.55 + jag2, 0) ** 8) * (wp > 1.0)               # the upper ledge, wider than the canyon
    floor_h = floor + 1.5 * vnoise(d.shape, 22.0, rng)
    T = H[z0:z1, x0:x1]
    drop = np.maximum(T - floor_h, 0)
    G = T - drop * np.minimum(0.45 * pl + 0.55 * pf, 1.0)
    H[z0:z1, x0:x1] = G
    wet = (G < water_level) & (pl > 0.02)                                           # the whole canyon below the water line is flooded
    cur = WATER[z0:z1, x0:x1]
    WATER[z0:z1, x0:x1] = np.where(wet, np.maximum(cur, np.int16(water_level)), cur)
    if log:
        log(f"  fissure: {s[-1]:.0f} blocks, width {width}, floor {floor}")


def snap_end(xs, zs, tx, tz, length=220.0):
    """Moves the end of a path onto (tx, tz); the shift fades in over the last `length` blocks (a tributary reaching the middle of its river)."""
    s = np.concatenate([[0], np.cumsum(np.hypot(np.diff(xs), np.diff(zs)))])
    w = smoothstep(s[-1] - length, s[-1], s)
    xs = xs + (tx - xs[-1]) * w
    zs = zs + (tz - zs[-1]) * w
    s = np.concatenate([[0], np.cumsum(np.hypot(np.diff(xs), np.diff(zs)))])
    return xs, zs, s


def roughen(H, WATER, before, rng, log=print):
    """Carving leaves smooth surfaces; gives back the fine relief of the terrain to everything that was reshaped (not next to the water)."""
    delta = np.abs(H - before)
    mask = gaussian_filter(np.minimum(delta, 4.0) / 4.0, 5.0)
    del delta
    wet = WATER > 0
    ramp = np.clip((distance_transform_edt(~wet).astype(np.float32) - 1.5) / 8.0, 0.0, 1.0)
    del wet
    mask *= ramp
    del ramp
    S = H.shape[0]
    for wl, amp in ((40, 1.1), (20, 0.8), (10, 0.5), (5, 0.28)):
        H += mask * amp * vnoise(H.shape, wl, rng)
    if log:
        log("  roughened reshaped ground")


def flatten(H, WATER, cx, cz, r_flat, r_skirt, level=None, log=print):
    """A level pad of radius r_flat with a smooth skirt of width r_skirt; returns the pad level (integer Y)."""
    pad = r_flat + r_skirt + 4
    z0, z1 = int(max(cz - pad, 0)), int(min(cz + pad + 1, H.shape[0]))
    x0, x1 = int(max(cx - pad, 0)), int(min(cx + pad + 1, H.shape[1]))
    zz, xx = np.mgrid[z0:z1, x0:x1].astype(np.float32)
    d = np.hypot(xx - cx, zz - cz)
    T = H[z0:z1, x0:x1]
    if level is None:
        level = int(np.rint(np.median(T[d <= r_flat])))
    w = 1.0 - smoothstep(r_flat, r_flat + r_skirt, d)
    H[z0:z1, x0:x1] = T * (1 - w) + np.float32(level) * w
    if log:
        log(f"  pad at ({cx:.0f}, {cz:.0f}) radius {r_flat}+{r_skirt}, level {level}")
    return level
