"""Smooth value noise built from small random grids stretched with cubic interpolation (fast for large maps)."""
import numpy as np
from scipy.ndimage import zoom


def vnoise(shape, wavelength, rng, dtype=np.float32):
    """Noise of about unit standard deviation; wavelength in cells (grid spacing of the random lattice)."""
    h, w = shape
    gh, gw = max(4, int(np.ceil(h / wavelength)) + 2), max(4, int(np.ceil(w / wavelength)) + 2)
    g = rng.standard_normal((gh, gw)).astype(np.float32)
    # stretch the lattice over the whole map, the lattice cell is `wavelength` cells wide
    out = zoom(g[1:gh - 1, 1:gw - 1], (h / (gh - 2), w / (gw - 2)), order=3, mode="reflect", grid_mode=True, output=dtype)
    out = out[:h, :w]
    if out.shape != (h, w):
        out = np.pad(out, ((0, h - out.shape[0]), (0, w - out.shape[1])), mode="edge")
    return out / max(float(out.std()), 1e-6)


def fbm(shape, wavelength, rng, octaves=5, persistence=0.5, dtype=np.float32):
    """Fractal sum of vnoise; the first octave has the given wavelength, each next one half of it."""
    total = np.zeros(shape, dtype=dtype)
    norm = 0.0
    amp = 1.0
    wl = float(wavelength)
    for _ in range(octaves):
        if wl < 2.0:
            break
        total += amp * vnoise(shape, wl, rng, dtype)
        norm += amp * amp
        amp *= persistence
        wl /= 2.0
    return total / float(np.sqrt(norm))


def ridged(shape, wavelength, rng, octaves=5, persistence=0.5, dtype=np.float32):
    """Ridge noise in [0, 1]: sharp crests where the underlying noise crosses zero, octaves get weighted by the previous ones."""
    total = np.zeros(shape, dtype=dtype)
    amp, norm, wl = 1.0, 0.0, float(wavelength)
    weight = np.ones(shape, dtype=dtype)
    for _ in range(octaves):
        if wl < 2.0:
            break
        n = 1.0 - np.abs(vnoise(shape, wl, rng, dtype)) * 0.8
        n = np.clip(n, 0, 1) ** 2
        n *= weight
        weight = np.clip(n * 1.6, 0, 1)
        total += n * amp
        norm += amp
        amp *= persistence
        wl /= 2.0
    return total / norm


def smoothstep(a, b, x):
    t = np.clip((x - a) / (b - a), 0.0, 1.0)
    return t * t * (3 - 2 * t)


def warp(field, rng, wavelength, amount):
    """Samples `field` at coordinates displaced by smooth noise (amount in cells): twists straight noise into organic shapes."""
    from scipy.ndimage import map_coordinates
    h, w = field.shape
    dz = vnoise((h, w), wavelength, rng) * amount
    dx = vnoise((h, w), wavelength, rng) * amount
    zz = np.arange(h, dtype=np.float32)[:, None] + dz
    xx = np.arange(w, dtype=np.float32)[None, :] + dx
    return map_coordinates(field, [zz, xx], order=1, mode="reflect").astype(field.dtype)


def eroded_fbm(shape, wavelength, rng, octaves=6, persistence=0.5, damp=1.4, dtype=np.float32):
    """fbm whose small octaves are damped where the large ones are steep: flat valley floors and detailed slopes, like eroded land."""
    total = np.zeros(shape, dtype=dtype)
    gx_acc = np.zeros(shape, dtype=dtype)
    gz_acc = np.zeros(shape, dtype=dtype)
    amp, norm, wl = 1.0, 0.0, float(wavelength)
    for _ in range(octaves):
        if wl < 2.0:
            break
        n = vnoise(shape, wl, rng, dtype)
        gz, gx = np.gradient(n)
        gx_acc += gx * wl * amp
        gz_acc += gz * wl * amp
        total += amp * n / (1.0 + damp * (gx_acc ** 2 + gz_acc ** 2))
        norm += amp * amp
        amp *= persistence
        wl /= 2.0
    return total / float(np.sqrt(norm))
