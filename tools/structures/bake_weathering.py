"""Bakes the random block replacement of `minecraft:rule` structure processors into the structure templates.

Why: a rule processor reads the block at every position of the template (level.getBlockState). During world generation the
game only gives a structure piece access to the chunks around the one being decorated, so a template larger than about one
chunk crashes the chunk ("Requested chunk unavailable during world generation"). `/place structure` does not have this limit,
which is why it worked in the sandbox. Replacements that do not depend on the world can be done once, in the template.

Reads the template pools to find which processor list belongs to which template, applies the rules with a seeded random generator,
then removes the rule processors from the list (other processors, e.g. block_rot, stay: they do not read the world).
Running it again does nothing, because no rule processors are left.

Usage: python3 bake_weathering.py <project root>"""
import json
import os
import sys

import numpy as np

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import nbtlite as nbt


def read_json(path):
    with open(path, encoding="utf-8") as f:
        return json.load(f)


def write_json(path, data):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def palette_index(palette, name, create=False):
    for i, entry in enumerate(palette):
        if entry["Name"][1] == name and "Properties" not in entry:
            return i
    if not create:
        return None
    palette.append({"Name": (nbt.STRING, name)})
    return len(palette) - 1


def bake(template_path, rules, seed):
    root = nbt.load(template_path)
    palette = root["palette"][1][1]
    blocks = root["blocks"][1][1]
    states = np.array([b["state"][1] for b in blocks], np.int64)
    rng = np.random.default_rng(seed)
    changed = {}
    done = np.zeros(len(blocks), bool)           # the first matching rule wins, like in the processor
    for source, probability, target in rules:
        src = palette_index(palette, source)
        if src is None:
            continue
        candidates = np.flatnonzero((states == src) & ~done)
        hit = candidates[rng.random(len(candidates)) < probability]
        if len(hit) == 0:
            continue
        dst = palette_index(palette, target, create=True)
        for i in hit:
            blocks[i]["state"] = (nbt.INT, dst)
        done[hit] = True
        changed[f"{source} -> {target}"] = len(hit)
    nbt.save(template_path, root)
    return changed


def main(root_dir):
    data = f"{root_dir}/src/main/resources/data/cursed_lands"
    lists = {}
    for name in os.listdir(f"{data}/worldgen/processor_list"):
        lists[f"cursed_lands:{name[:-5]}"] = read_json(f"{data}/worldgen/processor_list/{name}")
    templates = {}
    for name in sorted(os.listdir(f"{data}/worldgen/template_pool")):
        for element in read_json(f"{data}/worldgen/template_pool/{name}")["elements"]:
            e = element["element"]
            if e.get("element_type") == "minecraft:single_pool_element" and isinstance(e.get("processors"), str):
                templates.setdefault(e["processors"], []).append(e["location"])
    for list_id, plist in lists.items():
        rule_procs = [p for p in plist["processors"] if p["processor_type"] == "minecraft:rule"]
        if not rule_procs:
            print(f"{list_id}: no rule processors, nothing to do")
            continue
        rules = []
        for proc in rule_procs:
            for rule in proc["rules"]:
                inp, loc = rule["input_predicate"], rule["location_predicate"]
                if inp["predicate_type"] != "minecraft:random_block_match" or loc["predicate_type"] != "minecraft:always_true" or "output_nbt" in rule:
                    raise SystemExit(f"{list_id}: a rule that depends on the world or on block data cannot be baked: {rule}")
                if rule["output_state"].get("Properties"):
                    raise SystemExit(f"{list_id}: output states with properties are not supported")
                rules.append((inp["block"], inp["probability"], rule["output_state"]["Name"]))
        for loc in templates.get(list_id, []):
            path = f"{data}/structure/{loc.partition(':')[2]}.nbt"
            seed = int.from_bytes(loc.encode("utf-8"), "little") % (2 ** 32)
            print(f"{list_id} -> {loc}: {bake(path, rules, seed)}")
        plist["processors"] = [p for p in plist["processors"] if p["processor_type"] != "minecraft:rule"]
        write_json(f"{data}/worldgen/processor_list/{list_id.partition(':')[2]}.json", plist)


if __name__ == "__main__":
    main(sys.argv[1])
