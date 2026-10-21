"""Design of the big map. Positions are taken from the approved 4096 sketch (centre 0,0, +z to the south) and scaled to the real size;
sizes of features are in blocks. The map origin is shifted so that the spawn outpost lies at world (0, 0)."""
import numpy as np


class Design:
    def __init__(self, size=6144):
        self.size = size
        self.s = size / 4096.0
        self.c = size / 2.0
        spawn_z = int((self.c + 1700 * self.s) // 4 * 4)
        self.spawn_px = (int(self.c), spawn_z)
        self.origin = (-int(self.c), -spawn_z)

    def px(self, dx, dz):
        """Sketch coordinates to map pixels (x, z)."""
        return (self.c + dx * self.s, self.c + dz * self.s)

    def line(self, pts):
        return np.array([self.px(*p) for p in pts], dtype=np.float64)

    def world(self, px, pz):
        return px + self.origin[0], pz + self.origin[1]


# name, sketch seed, biome, mean height, relief, noise kind, how much the mountain rim rises at the edge behind it
REGIONS = [
    ("Плато Трона", (0, -150), "cursed_lands", 92, 6, "plain", 0.6),
    ("Хребет Шпилей", (-100, -1500), "spire_peaks", 150, 62, "ridge", 1.5),
    ("Разломы", (1050, 100), "blood_canyon", 88, 3, "flat", 0.8),
    ("Некрополь", (-120, 1150), "necropolis", 76, 9, "plain", 0.3),
    ("Мёртвый лес", (-1050, -50), "dead_forest", 80, 11, "plain", 0.9),
    ("Пепельная пустошь", (950, -950), "ash_wastes", 96, 20, "mesa", 0.9),
    ("Холмы Аббатства", (-950, -950), "dead_forest", 92, 18, "hills", 0.9),
    ("Костяная долина", (1000, 1000), "bone_valley", 71, 7, "plain", 0.35),
    ("Вороний край", (-350, 350), "cursed_lands", 88, 14, "hills", 0.7),
    ("Топи у озера", (-650, 1350), "blood_fen", 65, 2.5, "flat", 0.2),
    ("Равнина Тракта", (300, 400), "cursed_lands", 78, 6, "plain", 0.5),
    ("Предгорья", (400, -1100), "spire_peaks", 112, 22, "hills", 1.0),
    ("Южные ворота", (0, 1700), "cursed_lands", 74, 5, "plain", 0.3),
]

# explicit landforms: name, sketch position, kind, parameters in blocks
BUMPS = [  # name, position, height (blocks), sigma (blocks)
    ("Висельный холм", (350, -600), 26, 95),
    ("Каменный круг", (500, 500), 11, 70),
    ("Холм Аббатства", (-950, -950), 24, 140),
    ("Шахты Древнего стража", (-300, -700), 34, 150),
    ("Башни Шпилей", (-300, -1450), 40, 130),
    ("Вороний Брод", (-400, 400), -6, 90),
]

THRONE = dict(pos=(0, -150), top=112.0, radius=250.0, skirt=40.0)

# the Blood River and its tributaries: sketch points from the source downstream; width = channel half width at start and end (blocks)
RIVERS = [
    dict(name="Река Крови", pts=[(300, -1450), (200, -1100), (-100, -900), (-350, -600), (-450, -250), (-420, -50), (-480, 250), (-600, 600), (-700, 950)],
         w0=3.0, w1=8.0, depth=3.0),
    dict(name="Ручей Аббатства", pts=[(-900, -800), (-720, -640), (-540, -430), (-450, -250)], w0=2.0, w1=3.5, depth=2.2),
    dict(name="Ручей Шпилей", pts=[(-520, -1350), (-430, -1050), (-380, -820), (-350, -600)], w0=2.0, w1=3.0, depth=2.5),
    dict(name="Тарная вода", pts=[(560, 330), (250, 470), (-20, 620), (-300, 700), (-600, 600)], w0=2.0, w1=4.0, depth=2.2),
]

# lakes: name, centre, radii (x, z) in blocks, water level, depth below the level at the middle, rotation (degrees)
LAKES = [
    dict(name="Кровавое озеро", pos=(-700, 1000), rx=290.0, rz=205.0, level=64, depth=17, rot=12),
    dict(name="Багровый тарн", pos=(300, -1450), rx=95.0, rz=65.0, level=80, depth=9, rot=-20),
]

# scattered hills per region: mean spacing (blocks), height range, sigma range (blocks), share of hollows (basins instead of hills)
HILLS = {
    "Плато Трона": (520, 8, 18, 110, 200, 0.1),
    "Некрополь": (300, 8, 24, 80, 170, 0.18),
    "Мёртвый лес": (240, 12, 32, 80, 190, 0.2),
    "Холмы Аббатства": (200, 16, 38, 80, 190, 0.15),
    "Вороний край": (210, 14, 36, 80, 180, 0.2),
    "Равнина Тракта": (340, 6, 16, 110, 220, 0.2),
    "Костяная долина": (420, 5, 14, 110, 220, 0.3),
    "Пепельная пустошь": (380, 8, 22, 70, 140, 0.1),
    "Предгорья": (200, 14, 38, 80, 180, 0.1),
    "Топи у озера": (700, 2, 5, 80, 160, 0.5),
    "Южные ворота": (420, 5, 12, 110, 220, 0.2),
}

# sharp rock spires: region name -> (count, height range, sigma range in blocks)
SPIRES = {
    "Хребет Шпилей": (90, 30, 95, 14, 34),
    "Предгорья": (30, 14, 50, 16, 40),
}

# rift fissures: sketch points, half width and floor height (blocks); floors under the sea level are flooded with (blood) water
FISSURES = [
    dict(pts=[(780, 60), (900, 115), (1010, 70), (1110, 135), (1210, 90), (1320, 140)], width=12, floor=44),
    dict(pts=[(740, 310), (850, 355), (960, 300), (1070, 372), (1210, 330)], width=14, floor=42),
    dict(pts=[(880, -140), (1000, -95), (1100, -150), (1210, -100), (1340, -135)], width=11, floor=46),
    dict(pts=[(1010, 72), (1040, 190), (1000, 300)], width=7, floor=50),
]
