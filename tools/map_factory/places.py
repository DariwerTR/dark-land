"""Writes cursed_lands_map/places.json: named places for the in-game map (labels on the full-screen map).
Positions come from design.py (sketch coordinates scaled to the map and shifted so that the spawn is at world 0,0).
Names are not stored here: the game looks them up as map.cursed_lands.place.<id> in the lang files.

Usage: python3 places.py <project root>"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import design as dz  # noqa: E402

# id, sketch position, kind. kind: spawn (always shown), region (large areas, shown when zoomed out), landmark (shown when zoomed in)
REGION_IDS = {
    "Плато Трона": "throne_plateau",
    "Хребет Шпилей": "spire_ridge",
    "Разломы": "rifts",
    "Некрополь": "necropolis",
    "Мёртвый лес": "dead_forest",
    "Пепельная пустошь": "ash_wastes",
    "Холмы Аббатства": "abbey_hills",
    "Костяная долина": "bone_valley",
    "Вороний край": "raven_reach",
    "Топи у озера": "lakeside_fens",
    "Равнина Тракта": "highway_plain",
    "Предгорья": "foothills",
}
BUMP_IDS = {
    "Висельный холм": "gallows_hill",
    "Каменный круг": "stone_circle",
    "Шахты Древнего стража": "guardian_mines",
    "Башни Шпилей": "spire_towers",
    "Вороний Брод": "raven_ford",
}
LAKE_IDS = {"Кровавое озеро": "blood_lake", "Багровый тарн": "crimson_tarn"}


def build(size=6144):
    d = dz.Design(size)
    places = [dict(id="outpost", x=0, z=0, kind="spawn")]

    def add(pid, pos, kind):
        px, pz = d.px(*pos)
        x, z = d.world(px, pz)
        places.append(dict(id=pid, x=int(round(x)), z=int(round(z)), kind=kind))

    for name, seed, *_ in dz.REGIONS:
        if name in REGION_IDS:  # the Southern Gate region lies on the spawn: the outpost label covers it
            add(REGION_IDS[name], seed, "region")
    for name, pos, *_ in dz.BUMPS:
        if name in BUMP_IDS:
            add(BUMP_IDS[name], pos, "landmark")
    for lake in dz.LAKES:
        add(LAKE_IDS[lake["name"]], lake["pos"], "landmark")
    river = dz.RIVERS[0]
    add("blood_river", river["pts"][len(river["pts"]) // 2], "landmark")
    return places


if __name__ == "__main__":
    root = sys.argv[1]
    places = build()
    with open(f"{root}/src/main/resources/cursed_lands_map/places.json", "w", encoding="utf-8", newline="\n") as f:
        json.dump({"format": 1, "places": places}, f, indent=2)
        f.write("\n")
    print(len(places), "places")
