"""Shaded-relief previews of the elevation grid."""
import numpy as np
from PIL import Image


def hillshade(h, cell, azimuth=315.0, altitude=40.0, exag=2.2):
    gy, gx = np.gradient(h.astype(np.float32), cell)
    slope = np.arctan(exag * np.hypot(gx, gy))
    aspect = np.arctan2(-gx, gy)
    az, alt = np.radians(360 - azimuth + 90), np.radians(altitude)
    sh = np.sin(alt) * np.cos(slope) + np.cos(alt) * np.sin(slope) * np.cos(az - aspect)
    return np.clip(sh, 0, 1)


def tint(h, water=None, sea=63):
    """Hypsometric tint: low green-brown, high grey-white."""
    stops = [(40, (38, 54, 66)), (63, (62, 82, 62)), (85, (86, 98, 66)), (115, (118, 104, 80)), (160, (132, 124, 118)), (220, (176, 174, 184)), (300, (238, 238, 244))]
    ys = np.array([s[0] for s in stops], dtype=np.float32)
    out = np.zeros(h.shape + (3,), dtype=np.float32)
    for c in range(3):
        out[..., c] = np.interp(h, ys, [s[1][c] for s in stops])
    return out


def render(h, cell, path, water=None, scale=1, overlay=None, contour=None):
    rgb = tint(h) * (0.30 + 0.95 * hillshade(h, cell))[..., None]
    if contour:
        rgb = contours(rgb, h, contour)
    if water is not None:
        wet = water > 0
        rgb[wet] = rgb[wet] * 0.25 + np.array([60, 110, 150]) * 0.75
    if overlay is not None:
        rgb = overlay(rgb)
    im = Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8))
    if scale != 1:
        im = im.resize((int(im.width * scale), int(im.height * scale)), Image.LANCZOS)
    im.save(path)


def contours(rgb, h, step=10, strength=0.35):
    """Darken the pixels where the height crosses a multiple of `step`."""
    q = np.floor(h / step)
    edge = np.zeros(h.shape, bool)
    edge[:-1, :] |= q[:-1, :] != q[1:, :]
    edge[:, :-1] |= q[:, :-1] != q[:, 1:]
    rgb[edge] *= (1 - strength)
    return rgb
