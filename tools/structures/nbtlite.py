"""Type preserving NBT reader/writer (gzip, big endian) for structure templates.
Values: a compound is a dict name -> (tag, payload); a list payload is (element tag, [payloads]); the other payloads are plain Python values."""
import gzip
import struct

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)
_FMT = {BYTE: ">b", SHORT: ">h", INT: ">i", LONG: ">q", FLOAT: ">f", DOUBLE: ">d"}


class _Reader:
    def __init__(self, data):
        self.data, self.i = data, 0

    def take(self, n):
        out = self.data[self.i:self.i + n]
        self.i += n
        return out

    def payload(self, tag):
        if tag in _FMT:
            return struct.unpack(_FMT[tag], self.take(struct.calcsize(_FMT[tag])))[0]
        if tag == STRING:
            return self.take(struct.unpack(">H", self.take(2))[0]).decode("utf-8")
        if tag == BYTE_ARRAY:
            return bytes(self.take(struct.unpack(">i", self.take(4))[0]))
        if tag == INT_ARRAY:
            n = struct.unpack(">i", self.take(4))[0]
            return list(struct.unpack(f">{n}i", self.take(4 * n)))
        if tag == LONG_ARRAY:
            n = struct.unpack(">i", self.take(4))[0]
            return list(struct.unpack(f">{n}q", self.take(8 * n)))
        if tag == LIST:
            etag, n = struct.unpack(">bi", self.take(5))
            return (etag, [self.payload(etag) for _ in range(n)])
        if tag == COMPOUND:
            out = {}
            while True:
                t = struct.unpack(">b", self.take(1))[0]
                if t == END:
                    return out
                name = self.take(struct.unpack(">H", self.take(2))[0]).decode("utf-8")
                out[name] = (t, self.payload(t))
        raise ValueError(f"unknown tag {tag}")


def _write(out, tag, v):
    if tag in _FMT:
        out += struct.pack(_FMT[tag], v)
    elif tag == STRING:
        b = v.encode("utf-8")
        out += struct.pack(">H", len(b)) + b
    elif tag == BYTE_ARRAY:
        out += struct.pack(">i", len(v)) + bytes(v)
    elif tag == INT_ARRAY:
        out += struct.pack(">i", len(v)) + struct.pack(f">{len(v)}i", *v)
    elif tag == LONG_ARRAY:
        out += struct.pack(">i", len(v)) + struct.pack(f">{len(v)}q", *v)
    elif tag == LIST:
        etag, items = v
        out += struct.pack(">bi", etag, len(items))
        for item in items:
            _write(out, etag, item)
    elif tag == COMPOUND:
        for name, (t, p) in v.items():
            out += struct.pack(">b", t)
            b = name.encode("utf-8")
            out += struct.pack(">H", len(b)) + b
            _write(out, t, p)
        out += b"\x00"
    else:
        raise ValueError(f"unknown tag {tag}")


def loads(raw):
    r = _Reader(raw)
    tag = struct.unpack(">b", r.take(1))[0]
    r.take(struct.unpack(">H", r.take(2))[0])
    return r.payload(tag)


def dumps(root):
    out = bytearray(struct.pack(">b", COMPOUND) + struct.pack(">H", 0))
    _write(out, COMPOUND, root)
    return bytes(out)


def load(path):
    with open(path, "rb") as f:
        return loads(gzip.decompress(f.read()))


def save(path, root):
    with open(path, "wb") as f:
        f.write(gzip.compress(dumps(root), mtime=0))
