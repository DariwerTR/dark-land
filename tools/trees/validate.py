"""Offline checks of the resources and the world generation data (no Minecraft needed).

Usage: python3 tools/trees/validate.py   (from the repository root). Exit code 1 when a check fails.

What is checked:
  * every JSON file of the resources parses;
  * every block registered in ModBlocks has a blockstate, an item definition, a loot table and both language entries;
  * every model refers to textures and parent models that exist in the mod (or in "minecraft:");
  * every feature a biome or a placed feature uses exists, and tree species exist in TreeSpecies;
  * the order of the features is the same in all biomes (a cycle would stop the game at start);
  * every id in the tag files is a registered block or item of the mod.
"""
import glob
import json
import os
import re
import sys

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
RES = os.path.join(ROOT, "src", "main", "resources")
JAVA = os.path.join(ROOT, "src", "main", "java", "com", "cursedlands")
errors = []


def err(msg):
    errors.append(msg)
    print("ERROR:", msg)


def load(path):
    try:
        with open(path, encoding="utf-8") as f:
            return json.load(f)
    except Exception as e:  # noqa: BLE001
        err("%s does not parse: %s" % (os.path.relpath(path, RES), e))
        return None


def main():
    # 1. every json parses
    jsons = {}
    for path in glob.glob(os.path.join(RES, "**", "*.json"), recursive=True):
        d = load(path)
        if d is not None:
            jsons[os.path.relpath(path, RES).replace(os.sep, "/")] = d
    print("json files:", len(jsons))

    # 2. blocks
    src = open(os.path.join(JAVA, "registry", "ModBlocks.java"), encoding="utf-8").read()
    blocks = re.findall(r'register\(\s*"([a-z0-9_]+)"', src)
    print("registered blocks:", len(blocks))
    if len(set(blocks)) != len(blocks):
        err("duplicate block names")
    en = jsons.get("assets/cursed_lands/lang/en_us.json", {})
    ru = jsons.get("assets/cursed_lands/lang/ru_ru.json", {})
    items_with_own_json = set()
    for b in blocks:
        for rel in ("assets/cursed_lands/blockstates/%s.json" % b, "assets/cursed_lands/items/%s.json" % b,
                    "data/cursed_lands/loot_table/blocks/%s.json" % b):
            if rel not in jsons:
                err("missing " + rel)
        for lang, name in ((en, "en_us"), (ru, "ru_ru")):
            if "block.cursed_lands.%s" % b not in lang:
                err("missing %s entry for block %s" % (name, b))
    if set(en) != set(ru):
        err("language files differ: %s" % sorted(set(en) ^ set(ru))[:10])

    # 3. models: textures and parents
    for rel, d in jsons.items():
        if not rel.startswith("assets/cursed_lands/models/"):
            continue
        for key, val in (d.get("textures") or {}).items():
            if isinstance(val, str) and val.startswith("cursed_lands:"):
                if not os.path.exists(os.path.join(RES, "assets/cursed_lands/textures", val.split(":")[1] + ".png")):
                    err("%s: missing texture %s" % (rel, val))
        parent = d.get("parent", "")
        if parent.startswith("cursed_lands:") and ("assets/cursed_lands/models/%s.json" % parent.split(":")[1]) not in jsons:
            err("%s: missing parent %s" % (rel, parent))
    for rel, d in jsons.items():
        if rel.startswith("assets/cursed_lands/blockstates/"):
            text = json.dumps(d)
            for m in re.findall(r'"model": "cursed_lands:([a-z0-9_/]+)"', text):
                if ("assets/cursed_lands/models/%s.json" % m) not in jsons:
                    err("%s: missing model %s" % (rel, m))
        if rel.startswith("assets/cursed_lands/items/"):
            text = json.dumps(d)
            for m in re.findall(r'"model": "cursed_lands:([a-z0-9_/]+)"', text):
                if ("assets/cursed_lands/models/%s.json" % m) not in jsons:
                    err("%s: missing model %s" % (rel, m))

    # 4. tree species
    species_src = open(os.path.join(JAVA, "world", "tree", "TreeSpecies.java"), encoding="utf-8").read()
    species = set(re.findall(r'RECIPES\.put\("([a-z_]+)"', species_src))
    print("tree species:", len(species))
    configured = {k[len("data/cursed_lands/worldgen/configured_feature/"):-5] for k in jsons
                  if k.startswith("data/cursed_lands/worldgen/configured_feature/")}
    placed = {k[len("data/cursed_lands/worldgen/placed_feature/"):-5] for k in jsons
              if k.startswith("data/cursed_lands/worldgen/placed_feature/")}
    used_species = set()
    for name in configured:
        d = jsons["data/cursed_lands/worldgen/configured_feature/%s.json" % name]
        if d.get("type") == "cursed_lands:procedural_tree":
            sp = d["config"]["species"]
            used_species.add(sp)
            if sp not in species:
                err("configured feature %s: unknown species %s" % (name, sp))
    for sp in species - used_species:
        err("species %s has no configured feature" % sp)

    def refs(obj):
        if isinstance(obj, dict):
            f = obj.get("feature")
            if isinstance(f, str):
                yield f
            for v in obj.values():
                yield from refs(v)
        elif isinstance(obj, list):
            for v in obj:
                yield from refs(v)

    for name in configured:
        for r in refs(jsons["data/cursed_lands/worldgen/configured_feature/%s.json" % name]):
            if r.startswith("cursed_lands:") and r[13:] not in configured:
                err("configured feature %s refers to missing configured feature %s" % (name, r))
    for name in placed:
        f = jsons["data/cursed_lands/worldgen/placed_feature/%s.json" % name]["feature"]
        if isinstance(f, str) and f.startswith("cursed_lands:") and f[13:] not in configured:
            err("placed feature %s refers to missing configured feature %s" % (name, f))

    # 5. biomes: features exist, global order has no cycle
    edges = {}
    biomes = {k: v for k, v in jsons.items() if k.startswith("data/cursed_lands/worldgen/biome/")}
    for rel, d in biomes.items():
        for step, feats in enumerate(d["features"]):
            for a, b in zip(feats, feats[1:]):
                edges.setdefault((step, a), set()).add((step, b))
            for f in feats:
                if f.startswith("cursed_lands:") and f[13:] not in placed:
                    err("%s: unknown placed feature %s" % (rel, f))
                edges.setdefault((step, f), set())
    state = {}

    def visit(node, path):
        state[node] = 1
        for nxt in sorted(edges[node]):
            if state.get(nxt) == 1:
                err("feature order cycle: %s" % " -> ".join(p[1] for p in path + [node, nxt]))
                return
            if nxt not in state:
                visit(nxt, path + [node])
        state[node] = 2

    for node in sorted(edges):
        if node not in state:
            visit(node, [])
    print("biomes:", len(biomes), "placed features:", len(placed), "configured:", len(configured))
    unused = sorted(p for p in placed if not any("cursed_lands:" + p in b["features"][i] for b in biomes.values() for i in range(len(b["features"]))))
    if unused:
        print("note: placed features no biome uses (safe to delete):", ", ".join(unused))

    # 6. tags
    registered = set("cursed_lands:" + b for b in blocks) | {"cursed_lands:%s" % i for i in
                                                              ("ash", "darkness_crystal", "ruins_heart")}
    for rel, d in jsons.items():
        if "/tags/block/" in rel or "/tags/item/" in rel:
            for v in d.get("values", []):
                vid = v["id"] if isinstance(v, dict) else v
                if vid.startswith("#"):
                    continue
                if vid.startswith("cursed_lands:") and vid not in registered:
                    err("%s: unknown id %s" % (rel, vid))

    print("OK" if not errors else "%d error(s)" % len(errors))
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
