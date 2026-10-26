"""Writes the world generation data of the trees: one configured feature per species, one species mix per biome,
the placed features that scatter them, and the edits of the biome files.

Usage: python3 tools/trees/gen_worldgen.py   (run from the repository root; safe to run again)

The species of the mixes are drawn by `minecraft:random_selector`, which tries its entries in order with a chance each.
The script turns the wanted shares into those chances. Density is given as trees per chunk (attempts: an attempt fails
when the ground is wrong or the trunk is blocked) for the clearings and for the groves; a noise with a scale of about
200 blocks decides which of the two applies at a place.
"""
import json
import os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..")
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "cursed_lands", "worldgen")

SPECIES = ["black_oak", "snag", "stump", "black_spruce", "dead_spruce", "cypress", "willow", "mangrove",
           "bone_birch", "dead_birch", "charred", "wind_pine", "blood_maple", "thornbrush"]

# mix name -> shares of the species (and of the huge mushrooms); the shares are normalised
MIXES = {
    "cursed_lands": {"black_oak": 42, "black_spruce": 14, "snag": 16, "bone_birch": 8, "cypress": 6, "dead_birch": 4, "stump": 5, "thornbrush": 5},
    "dead_forest": {"black_spruce": 26, "black_oak": 22, "dead_spruce": 14, "snag": 14, "dead_birch": 8, "bone_birch": 4, "cypress": 4,
                    "stump": 5, "thornbrush": 3, "huge_shadow_mushroom": 3, "huge_shadow_parasol": 2},
    "necropolis": {"cypress": 40, "snag": 22, "dead_spruce": 10, "dead_birch": 12, "black_oak": 8, "stump": 8},
    "blood_canyon": {"blood_maple": 30, "thornbrush": 28, "snag": 14, "charred": 10, "wind_pine": 10, "stump": 8},
    "spire_peaks": {"wind_pine": 44, "dead_spruce": 18, "black_spruce": 12, "snag": 16, "stump": 5, "thornbrush": 5},
    "ash_wastes": {"charred": 45, "snag": 22, "thornbrush": 18, "stump": 10, "dead_birch": 5},
    "bone_valley": {"bone_birch": 30, "dead_birch": 22, "snag": 16, "wind_pine": 8, "black_oak": 8, "stump": 8, "thornbrush": 8},
    "blood_fen": {"willow": 30, "cypress": 18, "snag": 14, "black_oak": 12, "dead_birch": 6, "stump": 8, "thornbrush": 6,
                  "huge_shadow_mushroom": 3, "huge_shadow_parasol": 3},
}

# placed feature -> (mix, attempts per chunk in clearings, attempts in groves, ground tag, max water depth)
PLACED = {
    "trees_cursed_lands": ("cursed_lands", 2, 7, "tree_soil", 0),
    "trees_dead_forest": ("dead_forest", 6, 15, "tree_soil", 0),
    "trees_necropolis": ("necropolis", 1, 4, "tree_soil", 0),
    "trees_blood_canyon": ("blood_canyon", 1, 5, "tree_ground", 0),
    "trees_spire_peaks": ("spire_peaks", 1, 5, "tree_ground", 0),
    "trees_ash_wastes": ("ash_wastes", 1, 3, "tree_ground", 0),
    "trees_bone_valley": ("bone_valley", 1, 5, "tree_ground", 0),
    "trees_blood_fen": ("blood_fen", 3, 9, "tree_soil", 1),
}

# biome -> placed features that replace the old tree features at the start of the "vegetal decoration" step
BIOME_TREES = {
    "cursed_lands": ["trees_cursed_lands", "fallen_logs"],
    "fog_far": ["trees_cursed_lands", "fallen_logs"],
    "fog_edge": ["trees_cursed_lands", "fallen_logs"],
    "fog_wall": ["trees_cursed_lands", "fallen_logs"],
    "dead_forest": ["trees_dead_forest", "fallen_logs"],
    "necropolis": ["trees_necropolis", "fallen_logs"],
    "blood_canyon": ["trees_blood_canyon", "fallen_charred_logs"],
    "spire_peaks": ["trees_spire_peaks"],
    "ash_wastes": ["trees_ash_wastes", "fallen_charred_logs"],
    "bone_valley": ["trees_bone_valley", "fallen_logs"],
    "blood_fen": ["trees_blood_fen", "mangrove_mud", "fallen_logs"],
}
OLD_TREE_FEATURES = {"cursed_lands:dead_trees_dense", "cursed_lands:dead_trees_sparse"}
NEW_TREE_FEATURES = {"cursed_lands:" + n for n in list(PLACED) + ["mangrove_mud", "fallen_logs", "fallen_charred_logs"]}

EXISTING_FEATURES = {  # configured features of the mod that a mix may use by their own name
    "huge_shadow_mushroom": "cursed_lands:huge_shadow_mushroom",
    "huge_shadow_parasol": "cursed_lands:huge_shadow_parasol",
}


def write(rel, obj):
    path = os.path.join(DATA, rel)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def feature_id(name):
    return EXISTING_FEATURES.get(name, "cursed_lands:tree/" + name)


def selector(shares):
    """random_selector config that draws every entry with its share: the biggest share is the default."""
    total = float(sum(shares.values()))
    order = sorted(shares.items(), key=lambda kv: (-kv[1], kv[0]))
    default = order[0][0]
    rest = order[1:]
    remaining = 1.0
    entries = []
    for name, share in rest:
        p = share / total
        entries.append({"chance": round(p / remaining, 5), "feature": {"feature": feature_id(name), "placement": []}})
        remaining -= p
    return {"default": {"feature": feature_id(default), "placement": []}, "features": entries}


def ground_filter(tag):
    return {"type": "minecraft:block_predicate_filter",
            "predicate": {"type": "minecraft:matching_block_tag", "offset": [0, -1, 0], "tag": "cursed_lands:" + tag}}


def surface_modifiers(tag, water):
    return [
        {"type": "minecraft:in_square"},
        {"type": "minecraft:surface_water_depth_filter", "max_water_depth": water},
        {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"},
        {"type": "minecraft:biome"},
        ground_filter(tag),
    ]


def fallen(block, decorators, stump_decorators):
    return {"type": "minecraft:fallen_tree", "config": {
        "log_decorators": decorators,
        "log_length": {"type": "minecraft:uniform", "max_inclusive": 8, "min_inclusive": 4},
        "stump_decorators": stump_decorators,
        "trunk_provider": {"type": "minecraft:simple_state_provider", "state": {"Name": block, "Properties": {"axis": "y"}}},
    }}


def mushrooms(probability):
    return [{"type": "minecraft:attached_to_logs",
             "block_provider": {"type": "minecraft:simple_state_provider", "state": {"Name": "cursed_lands:shadow_mushroom"}},
             "directions": ["up"], "probability": probability}]


def main():
    for species in SPECIES:
        write("configured_feature/tree/%s.json" % species,
              {"type": "cursed_lands:procedural_tree", "config": {"species": species}})

    for mix, shares in MIXES.items():
        for name in shares:
            assert name in SPECIES or name in EXISTING_FEATURES, name
        write("configured_feature/mix/%s.json" % mix, {"type": "minecraft:random_selector", "config": selector(shares)})

    for name, (mix, low, high, tag, water) in PLACED.items():
        write("placed_feature/%s.json" % name, {
            "feature": "cursed_lands:mix/" + mix,
            "placement": [{"type": "minecraft:noise_threshold_count", "noise_level": 0.0, "below_noise": low, "above_noise": high}]
                         + surface_modifiers(tag, water)})

    # mangroves stand on mud only
    write("placed_feature/mangrove_mud.json", {
        "feature": "cursed_lands:tree/mangrove",
        "placement": [{"type": "minecraft:noise_threshold_count", "noise_level": 0.0, "below_noise": 0, "above_noise": 4},
                      {"type": "minecraft:in_square"},
                      {"type": "minecraft:surface_water_depth_filter", "max_water_depth": 1},
                      {"type": "minecraft:heightmap", "heightmap": "OCEAN_FLOOR"},
                      {"type": "minecraft:biome"},
                      {"type": "minecraft:block_predicate_filter",
                       "predicate": {"type": "minecraft:matching_blocks", "offset": [0, -1, 0], "blocks": "minecraft:mud"}}]})

    # fallen logs: the old black one stays, the others are new
    write("configured_feature/fallen_pale_birch_log.json", fallen("cursed_lands:pale_birch_log", mushrooms(0.1), []))
    write("configured_feature/fallen_pine_log.json", fallen("cursed_lands:pine_log", mushrooms(0.08), []))
    write("configured_feature/fallen_charred_log.json", fallen("cursed_lands:charred_log", [], []))
    write("configured_feature/fallen_log_mix.json", {"type": "minecraft:random_selector", "config": {
        "default": {"feature": "cursed_lands:fallen_black_log", "placement": []},
        "features": [
            {"chance": 0.3, "feature": {"feature": "cursed_lands:fallen_pale_birch_log", "placement": []}},
            {"chance": 0.25, "feature": {"feature": "cursed_lands:fallen_pine_log", "placement": []}},
        ]}})
    write("placed_feature/fallen_logs.json", {
        "feature": "cursed_lands:fallen_log_mix",
        "placement": [{"type": "minecraft:rarity_filter", "chance": 4}] + surface_modifiers("tree_soil", 0)})
    write("placed_feature/fallen_charred_logs.json", {
        "feature": "cursed_lands:fallen_charred_log",
        "placement": [{"type": "minecraft:rarity_filter", "chance": 5}] + surface_modifiers("tree_ground", 0)})

    # biomes: the new tree features replace the old ones at the head of the step, the rest of the list is untouched
    for biome, trees in BIOME_TREES.items():
        path = os.path.join(DATA, "biome", biome + ".json")
        with open(path, encoding="utf-8") as f:
            data = json.load(f)
        step = data["features"][9]
        kept = [f for f in step if f not in OLD_TREE_FEATURES and f not in NEW_TREE_FEATURES]
        data["features"][9] = ["cursed_lands:" + t for t in trees] + kept
        with open(path, "w", encoding="utf-8") as f:
            json.dump(data, f, indent=2)
            f.write("\n")
    print("done")


if __name__ == "__main__":
    main()
