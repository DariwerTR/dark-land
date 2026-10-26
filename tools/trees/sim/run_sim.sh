#!/bin/sh
# Forest simulation on windows of the real map. Needs a JDK, python3 with numpy and Pillow.
# usage: tools/trees/sim/run_sim.sh WORK_DIR [seed]    -> WORK_DIR/scene_<biome>.json and statistics on stdout
set -e
ROOT="$(cd "$(dirname "$0")/../../.." && pwd)"
WORK="${1:?work directory}"
SEED="${2:-1}"
mkdir -p "$WORK/classes"
python3 "$ROOT/tools/trees/sim/export_regions.py" "$WORK" > "$WORK/export.log"
javac -d "$WORK/classes" -sourcepath "$ROOT/src/main/java" "$ROOT"/src/main/java/com/cursedlands/world/tree/*.java "$ROOT/tools/trees/sim/ForestSim.java"
for r in dead_forest cursed_lands necropolis blood_canyon spire_peaks ash_wastes bone_valley blood_fen; do
	java -Xmx3g -cp "$WORK/classes" ForestSim "$WORK/region_$r.bin" "$WORK/sim.cfg" "$WORK/scene_$r.json" "$SEED"
	echo
done
echo "view: python3 $ROOT/tools/trees/render_scene.py $WORK/scene_dead_forest.json out.png 48 48 96 6"
