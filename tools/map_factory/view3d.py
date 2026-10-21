"""Perspective preview of the map as seen from a point on it (a height field ray marcher; blocks are shown as 1x1 columns).
Usage: python3 view3d.py <map dir> <out png> <x> <z> <yaw deg, 0 = north, 90 = east> [eye above ground=2] [pitch=0] [fov=75] [tmax=1500] [fog=1100]
Coordinates x, z are map pixels (add origin to get world coordinates)."""
import sys

import numpy as np
from PIL import Image
from scipy.ndimage import gaussian_filter

import clm
from mapview import SURFACE_RGB

SKY_TOP = np.array([28, 22, 40], np.float32)
FOG = np.array([70, 52, 66], np.float32)
WATER = np.array([58, 88, 118], np.float32)


def render(H, W, S, cam_x, cam_z, yaw, eye=2.0, pitch=0.0, fov=75.0, size=(1000, 560), tmax=1500.0, fog=1100.0, sun=(0.55, 0.62, 0.45)):
    n = H.shape[0]
    x0, x1 = int(max(cam_x - tmax, 0)), int(min(cam_x + tmax, n))
    z0, z1 = int(max(cam_z - tmax, 0)), int(min(cam_z + tmax, n))
    Hw = H[z0:z1, x0:x1].astype(np.float32)
    Ww = W[z0:z1, x0:x1].astype(np.float32)
    Sw = S[z0:z1, x0:x1]
    top = np.maximum(Hw, Ww)
    Hs = gaussian_filter(Hw, 0.9)
    gz, gx = np.gradient(Hs)
    cam_y = float(H[int(cam_z), int(cam_x)]) + eye
    w, h = size
    t_fov = np.tan(np.radians(fov) / 2)
    yw, pt = np.radians(yaw), np.radians(pitch)
    fwd = np.array([np.sin(yw) * np.cos(pt), np.sin(pt), -np.cos(yw) * np.cos(pt)], np.float32)
    right = np.array([np.cos(yw), 0, np.sin(yw)], np.float32)
    up = np.cross(right, fwd).astype(np.float32)
    ii, jj = np.meshgrid(np.arange(w, dtype=np.float32), np.arange(h, dtype=np.float32))
    u = (ii + 0.5 - w / 2) / (w / 2) * t_fov
    v = -(jj + 0.5 - h / 2) / (w / 2) * t_fov
    d = fwd[None, None, :] + u[..., None] * right[None, None, :] + v[..., None] * up[None, None, :]
    d /= np.linalg.norm(d, axis=-1, keepdims=True)
    d = d.reshape(-1, 3)
    m = d.shape[0]
    hit_t = np.full(m, -1.0, np.float32)
    hit_ix = np.zeros(m, np.int32)
    hit_iz = np.zeros(m, np.int32)
    active = np.arange(m)
    t = 1.0
    px, py, pz = cam_x - x0, cam_y, cam_z - z0
    while t < tmax and active.size:
        dd = d[active]
        ix = np.rint(px + dd[:, 0] * t).astype(np.int32)
        iz = np.rint(pz + dd[:, 2] * t).astype(np.int32)
        y = py + dd[:, 1] * t
        inside = (ix >= 0) & (iz >= 0) & (ix < Hw.shape[1]) & (iz < Hw.shape[0])
        ixc = np.clip(ix, 0, Hw.shape[1] - 1)
        izc = np.clip(iz, 0, Hw.shape[0] - 1)
        hit = inside & (y <= top[izc, ixc] + 0.5)
        gone = ~inside | ((y > 330) & (dd[:, 1] > 0))
        hi = active[hit]
        hit_t[hi] = t
        hit_ix[hi] = ixc[hit]
        hit_iz[hi] = izc[hit]
        active = active[~(hit | gone)]
        t += 1.0 + t * 0.004
    pal = np.array([SURFACE_RGB[i] for i in range(10)], np.float32)
    # sky
    ys = np.clip(-(d[:, 1]), -1, 1)
    sky = FOG[None, :] * np.clip(1 - 3.2 * np.maximum(d[:, 1], 0), 0, 1)[:, None] + SKY_TOP[None, :] * np.clip(3.2 * np.maximum(d[:, 1], 0), 0, 1)[:, None]
    img = sky.copy()
    ok = hit_t > 0
    ix, iz, tt = hit_ix[ok], hit_iz[ok], hit_t[ok]
    wet = Ww[iz, ix] > Hw[iz, ix]
    nrm = np.stack([-gx[iz, ix], np.ones(ix.shape, np.float32), -gz[iz, ix]], axis=1)
    nrm /= np.linalg.norm(nrm, axis=1, keepdims=True)
    nrm[wet] = np.array([0, 1, 0], np.float32)
    sunv = np.array(sun, np.float32); sunv /= np.linalg.norm(sunv)
    lam = np.clip(nrm @ sunv, 0, 1)
    col = pal[Sw[iz, ix]] * (0.30 + 0.85 * lam)[:, None]
    depth = np.clip(Ww[iz, ix] - Hw[iz, ix], 0, 20)
    wc = WATER[None, :] * (1.0 - 0.025 * depth[:, None]) * (0.6 + 0.5 * lam[:, None])
    col[wet] = wc[wet]
    f = np.exp(-tt / fog)[:, None]
    img[ok] = col * f + FOG[None, :] * (1 - f)
    out = np.clip(img, 0, 255).reshape(h, w, 3).astype(np.uint8)
    return out


if __name__ == "__main__":
    a = sys.argv
    M = a[1]
    H, W, S = clm.read_layer(f"{M}/heights.bin"), clm.read_layer(f"{M}/water.bin"), clm.read_layer(f"{M}/surface.bin")
    x, z, yaw = float(a[3]), float(a[4]), float(a[5])
    eye = float(a[6]) if len(a) > 6 else 2.0
    pitch = float(a[7]) if len(a) > 7 else 0.0
    fov = float(a[8]) if len(a) > 8 else 75.0
    tmax = float(a[9]) if len(a) > 9 else 1500.0
    fog = float(a[10]) if len(a) > 10 else 1100.0
    Image.fromarray(render(H, W, S, x, z, yaw, eye, pitch, fov, tmax=tmax, fog=fog)).save(a[2])
