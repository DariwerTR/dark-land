"""In-memory model of the map and the writer of the cursed_lands_map/ directory (see clm.py for the file format)."""
import json
import os

import numpy as np

import clm

# Surface kinds of the "surface" layer; must match MapChunkGenerator.Surface.
GRASS, SOIL, GRAVEL, SAND, ROCK, PATH, MUD, ASH, COARSE, DEEPROCK = range(10)
# Biome indices of the "biomes" layer, in the order written to meta.json (paths in the cursed_lands namespace).
BIOMES = ["cursed_lands", "dead_forest", "necropolis", "blood_canyon", "fog_far", "fog_edge", "fog_wall",
          "spire_peaks", "ash_wastes", "bone_valley", "blood_fen"]
B_CURSED, B_DEAD, B_NECRO, B_CANYON, B_FOG_FAR, B_FOG_EDGE, B_FOG_WALL, B_SPIRE, B_ASH, B_BONE, B_FEN = range(11)

FOG_WALL_WIDTH = 120      # blocks from the edge: the dense wall (fog_wall biome)
FOG_EDGE_WIDTH = 190      # blocks from the edge: thick fog (fog_edge biome)
FOG_FAR_WIDTH = 280       # blocks from the edge: the first haze (fog_far biome)
BORDER_MARGIN = 40        # blocks from the edge: the fog turns players back


class MapModel:
    def __init__(self, size, origin_x, origin_z, sea_level=63):
        assert size % 256 == 0 and origin_x % 4 == 0 and origin_z % 4 == 0
        self.size, self.origin_x, self.origin_z, self.sea_level = size, origin_x, origin_z, sea_level
        self.heights = np.full((size, size), sea_level + 4, dtype=np.int16)
        self.water = np.zeros((size, size), dtype=np.int16)
        self.surface = np.zeros((size, size), dtype=np.uint8)
        self.biomes = np.zeros((size // 4, size // 4), dtype=np.uint8)

    def world_to_px(self, x, z):
        return x - self.origin_x, z - self.origin_z

    def edge_distance(self):
        """Distance in blocks from the nearest map edge for every block column."""
        i = np.arange(self.size, dtype=np.float32) + 0.5
        d1 = np.minimum(i, self.size - i)
        return np.minimum(d1[:, None], d1[None, :])

    def apply_fog_ring(self):
        """Biome bands along the map edge: fog_far (haze), fog_edge (thick), fog_wall (dense wall); the fog distance of each is in its biome file."""
        q = self.size // 4
        c = (np.arange(q, dtype=np.float32) + 0.5) * 4
        d1 = np.minimum(c, self.size - c)
        d = np.minimum(d1[:, None], d1[None, :])
        self.biomes[d < FOG_FAR_WIDTH] = B_FOG_FAR
        self.biomes[d < FOG_EDGE_WIDTH] = B_FOG_EDGE
        self.biomes[d < FOG_WALL_WIDTH] = B_FOG_WALL

    def check(self):
        assert self.heights.min() >= -48 and self.heights.max() <= 316, "heights out of the build range"
        wet = self.water > 0
        assert (self.water[wet] > self.heights[wet]).all(), "water surface must be above the ground"
        assert self.biomes.max() < len(BIOMES)

    def save(self, out_dir, extra=None):
        self.check()
        os.makedirs(out_dir, exist_ok=True)
        sizes = {
            "heights": clm.write_layer(f"{out_dir}/heights.bin", self.heights, True),
            "water": clm.write_layer(f"{out_dir}/water.bin", self.water, True),
            "surface": clm.write_layer(f"{out_dir}/surface.bin", self.surface, False),
            "biomes": clm.write_layer(f"{out_dir}/biomes.bin", self.biomes, False),
        }
        meta = {"format": 1, "size": self.size, "origin_x": self.origin_x, "origin_z": self.origin_z,
                "sea_level": self.sea_level, "border_margin": BORDER_MARGIN, "biomes": BIOMES}
        meta.update(extra or {})
        with open(f"{out_dir}/meta.json", "w", encoding="utf-8", newline="\n") as f:
            json.dump(meta, f, ensure_ascii=False, indent=2)
            f.write("\n")
        return sizes
