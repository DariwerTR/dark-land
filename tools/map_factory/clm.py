"""Reader and writer of the Cursed Lands layer files ("CLM1"); the Java reader is com.cursedlands.world.MapData.

File layout (big endian): b"CLM1", u8 type (1 = uint8, 2 = int16), u8 0, u16 tile size, u32 width, u32 height,
then (u32 offset, u32 length) per tile (row by row), then the tiles. A tile is a zlib stream of tile*tile cells, row by row.
int16 tiles are stored as two byte planes (all high bytes, then all low bytes) of the 2D difference
(difference along x, then along z, int16 wraps). A tile of length 0 is all zeros.
"""
import struct
import zlib

import numpy as np

MAGIC = b"CLM1"


def _encode(tile, wide):
    if wide:
        v = tile.astype(np.int16)
        d = v.copy()
        d[:, 1:] = v[:, 1:] - v[:, :-1]
        e = d.copy()
        e[1:, :] = d[1:, :] - d[:-1, :]
        u = e.view(np.uint16)
        raw = (u >> 8).astype(np.uint8).tobytes() + (u & 0xFF).astype(np.uint8).tobytes()
    else:
        raw = tile.astype(np.uint8).tobytes()
    return zlib.compress(raw, 9)


def write_layer(path, arr, wide, tile=256):
    """arr is indexed [z, x]; it is padded to whole tiles by repeating the edge (the reader clamps anyway)."""
    arr = np.asarray(arr)
    h, w = arr.shape
    if wide:
        if arr.min() < -32768 or arr.max() > 32767:
            raise ValueError("int16 layer out of range")
    elif arr.min() < 0 or arr.max() > 255:
        raise ValueError("uint8 layer out of range")
    tx, tz = -(-w // tile), -(-h // tile)
    padded = np.pad(arr, ((0, tz * tile - h), (0, tx * tile - w)), mode="edge")
    blobs = []
    for j in range(tz):
        for i in range(tx):
            t = padded[j * tile:(j + 1) * tile, i * tile:(i + 1) * tile]
            blobs.append(b"" if not t.any() else _encode(t, wide))
    head = MAGIC + bytes([2 if wide else 1, 0]) + struct.pack(">H", tile) + struct.pack(">II", w, h)
    pos = len(head) + 8 * len(blobs)
    index = b""
    for b in blobs:
        index += struct.pack(">II", pos, len(b))
        pos += len(b)
    with open(path, "wb") as f:
        f.write(head + index + b"".join(blobs))
    return pos


def read_layer(path):
    """Returns the whole layer as a [z, x] array (used for checks and previews)."""
    data = open(path, "rb").read()
    assert data[:4] == MAGIC, "bad magic"
    wide = data[4] == 2
    tile = struct.unpack(">H", data[6:8])[0]
    w, h = struct.unpack(">II", data[8:16])
    tx, tz = -(-w // tile), -(-h // tile)
    out = np.zeros((tz * tile, tx * tile), dtype=np.int16 if wide else np.uint8)
    for j in range(tz):
        for i in range(tx):
            off, ln = struct.unpack(">II", data[16 + 8 * (j * tx + i):24 + 8 * (j * tx + i)])
            if ln == 0:
                continue
            raw = zlib.decompress(data[off:off + ln])
            if wide:
                n = tile * tile
                hi = np.frombuffer(raw[:n], dtype=np.uint8).astype(np.uint16)
                lo = np.frombuffer(raw[n:], dtype=np.uint8).astype(np.uint16)
                v = ((hi << 8) | lo).astype(np.uint16).view(np.int16).reshape(tile, tile).copy()
                v = np.cumsum(v, axis=0, dtype=np.int16)
                v = np.cumsum(v, axis=1, dtype=np.int16)
            else:
                v = np.frombuffer(raw, dtype=np.uint8).reshape(tile, tile)
            out[j * tile:(j + 1) * tile, i * tile:(i + 1) * tile] = v
    return out[:h, :w]
