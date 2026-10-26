import java.io.*;
import java.nio.file.*;
import java.util.*;
import com.cursedlands.world.tree.*;

/** Simulates the tree placed features on a window of the real map: same attempts, filters, species mixes and
 *  terrain/obstacle rules as the game layer (ProceduralTreeFeature), without Minecraft. */
public class ForestSim {
	static final int YH = 400;
	static int W, H;
	static short[] h, water;
	static byte[] kind, biome, occ;
	static String[] biomeNames;
	static Map<String, Mix> mixes = new HashMap<>();
	static Map<String, Placed> placed = new HashMap<>();
	static Map<String, String[]> biomeTrees = new HashMap<>();

	static class Mix { String def; List<String> names = new ArrayList<>(); List<Double> chance = new ArrayList<>(); }
	static class Placed { String mix; int lo, hi; String tag; int water; }

	static int idx(int x, int y, int z) { return (y * H + z) * W + x; }

	static boolean inWorld(int x, int y, int z) { return x >= 0 && x < W && z >= 0 && z < H && y >= 0 && y < YH; }

	static boolean isSoil(int k) { return k == 0 || k == 1 || k == 6 || k == 7 || k == 8; }
	static boolean isRock(int k) { return k == 2 || k == 3 || k == 4 || k == 9; }

	/** 0 air, 1 water, 2 solid terrain, 3 tree log, 4 leaf, 5 vine/fungus */
	static int block(int x, int y, int z) {
		if (!inWorld(x, y, z)) return 2;
		byte o = occ[idx(x, y, z)];
		if (o != 0) {
			TreeMat m = TreeMat.values()[o - 1];
			return m.isLog() ? 3 : m.isLeaf() ? 4 : 5;
		}
		int col = z * W + x;
		if (y <= h[col]) return 2;
		if (water[col] > h[col] && y <= water[col]) return 1;
		return 0;
	}

	static boolean replaceable(int b) { return b == 0 || b == 1 || b == 4 || b == 5; }
	static boolean groundBlock(int b) { return b == 2; }

	static class Site implements TreeSite {
		final int ox, oy, oz;
		Site(int ox, int oy, int oz) { this.ox = ox; this.oy = oy; this.oz = oz; }
		public int groundAt(int x, int z) {
			for (int y = 6; y >= -6; y--) {
				if (replaceable(block(ox + x, oy + y, oz + z)) && groundBlock(block(ox + x, oy + y - 1, oz + z))) return y;
			}
			return NO_GROUND;
		}
		public boolean canReplace(int x, int y, int z) {
			int wy = oy + y;
			return wy >= 0 && wy < YH && replaceable(block(ox + x, wy, oz + z));
		}
	}

	static double noise(double x, double z) {
		double s = 200.0;
		double fx = x / s, fz = z / s;
		int ix = (int) Math.floor(fx), iz = (int) Math.floor(fz);
		double tx = fx - ix, tz = fz - iz;
		tx = tx * tx * (3 - 2 * tx); tz = tz * tz * (3 - 2 * tz);
		double a = lat(ix, iz), b = lat(ix + 1, iz), c = lat(ix, iz + 1), d = lat(ix + 1, iz + 1);
		return (a + (b - a) * tx) * (1 - tz) + (c + (d - c) * tx) * tz;
	}
	static double lat(int x, int z) {
		int hh = x * 374761393 + z * 668265263 + 12345;
		hh = (hh ^ (hh >>> 13)) * 1274126177; hh ^= hh >>> 16;
		return ((hh & 0xFFFF) / 32767.5 - 1.0) * 0.8;
	}

	static void readCfg(String path) throws IOException {
		for (String line : Files.readAllLines(Paths.get(path))) {
			String[] t = line.trim().split("\\s+");
			switch (t[0]) {
				case "biomes": biomeNames = Arrays.copyOfRange(t, 1, t.length); break;
				case "mix": {
					Mix m = new Mix(); m.def = t[2];
					for (int i = 3; i < t.length; i++) { String[] p = t[i].split(":"); m.names.add(p[0]); m.chance.add(Double.parseDouble(p[1])); }
					mixes.put(t[1], m); break;
				}
				case "placed": { Placed p = new Placed(); p.mix = t[2]; p.lo = Integer.parseInt(t[3]); p.hi = Integer.parseInt(t[4]); p.tag = t[5]; p.water = Integer.parseInt(t[6]); placed.put(t[1], p); break; }
				case "biome": biomeTrees.put(t[1], Arrays.copyOfRange(t, 2, t.length)); break;
			}
		}
	}

	static class Stat { int tried, ok, logs, leaves, hsum, nullc; long nanos; int minH = 999, maxH; }
	static Map<String, Stat> stats = new TreeMap<>();

	public static void main(String[] a) throws Exception {
		String region = a[0], cfg = a[1], out = a[2];
		long seed = Long.parseLong(a[3]);
		readCfg(cfg);
		try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(region)))) {
			W = in.readInt(); H = in.readInt();
			h = new short[W * H]; water = new short[W * H]; kind = new byte[W * H]; biome = new byte[W * H];
			for (int i = 0; i < W * H; i++) h[i] = in.readShort();
			in.readFully(kind);
			for (int i = 0; i < W * H; i++) water[i] = in.readShort();
			in.readFully(biome);
		}
		if (Boolean.getBoolean("wet")) for (int i = 0; i < W * H; i++) if (kind[i] == 6) water[i] = (short) (h[i] + 1);
		occ = new byte[W * H * YH];
		Random rnd = new Random(seed);
		int chunks = 0, attempts = 0;
		Map<String, Integer> treesPerBiome = new TreeMap<>();
		int[] bounds = new int[4];
		for (int cz = 1; cz < H / 16 - 1; cz++) {
			for (int cx = 1; cx < W / 16 - 1; cx++) {
				chunks++;
				// biomes present in the chunk (quart resolution), feature lists in order
				Set<Integer> present = new TreeSet<>();
				for (int z = 0; z < 16; z += 4) for (int x = 0; x < 16; x += 4) present.add((int) biome[(cz * 16 + z) * W + cx * 16 + x]);
				double nz = noise(cx * 16, cz * 16);
				for (int b : present) {
					String bn = biomeNames[b];
					String[] feats = biomeTrees.get(bn);
					if (feats == null) continue;
					for (String pf : feats) {
						if (pf.equals("fallen_logs") || pf.equals("fallen_charred_logs")) continue;
						Placed p; String species = null; int count; int maxWater; String tag;
						if (pf.equals("mangrove_mud")) { p = null; count = Boolean.getBoolean("mangrove") ? 40 : (nz < 0 ? 0 : 4); maxWater = 1; tag = "mud"; species = "mangrove"; }
						else { p = placed.get(pf); count = nz < 0 ? p.lo : p.hi; maxWater = p.water; tag = p.tag; }
						for (int i = 0; i < count; i++) {
							int x = cx * 16 + rnd.nextInt(16), z = cz * 16 + rnd.nextInt(16);
							int col = z * W + x;
							if (biome[col] != b) continue;
							int depth = water[col] > h[col] ? water[col] - h[col] : 0;
							if (depth > maxWater) continue;
							// heightmap: the first free block above the highest blocking block (leaves and logs block motion)
							int top = h[col];
							for (int y = Math.min(YH - 1, top + 60); y > top; y--) { int bl = block(x, y, z); if (bl == 3 || bl == 4) { top = y; break; } }
							int k = kind[col];
							boolean soilOk;
							if (top > h[col]) soilOk = false; // standing on leaves or a log
							else if (tag.equals("mud")) soilOk = k == 6;
							else if (tag.equals("tree_soil")) soilOk = isSoil(k);
							else soilOk = isSoil(k) || isRock(k);
							if (!soilOk) continue;
							attempts++;
							if (species == null) species = pick(mixes.get(p.mix), rnd);
							if (species == null) continue; // a huge mushroom: not simulated
							Stat st = stats.computeIfAbsent(bn + "/" + species, q -> new Stat());
							st.tried++;
							int oy = top + 1;
							Site site = new Site(x, oy, z);
							long t0 = System.nanoTime();
							TreeCanvas c = TreeSpecies.generate(species, new Random(rnd.nextLong()), site, TreeSpecies.Params.DEFAULT);
							st.nanos += System.nanoTime() - t0;
							if (c == null) { st.nullc++; continue; }
							st.ok++;
							st.logs += c.count(TreeMat.Kind.LOG); st.leaves += c.count(TreeMat.Kind.LEAF);
							int hh = c.topY(); st.hsum += hh; st.minH = Math.min(st.minH, hh); st.maxH = Math.max(st.maxH, hh);
							treesPerBiome.merge(bn, 1, Integer::sum);
							final int fx = x, fy = oy, fz = z;
							final Site fs = site; final TreeCanvas fc = c;
							c.forEach((vx, vy, vz, mat, aux, root) -> {
								int wx = fx + vx, wy = fy + vy, wz = fz + vz;
								if (!inWorld(wx, wy, wz)) { bounds[0]++; return; }
								int g = fc.ground(vx, vz);
								boolean under = g != TreeSite.NO_GROUND && vy < g;
								int bl = block(wx, wy, wz);
								if (under ? !(replaceable(bl) || bl == 2) : !replaceable(bl)) return;
								occ[idx(wx, wy, wz)] = (byte) (mat.ordinal() + 1);
							});
						}
					}
				}
			}
		}
		System.out.printf("region %s: %d chunks, %d placement attempts that passed the filters%n", region, chunks, attempts);
		System.out.println("trees per chunk by biome: " + treesPerBiome + "  (chunks=" + chunks + ")");
		for (Map.Entry<String, Stat> e : stats.entrySet()) {
			Stat s = e.getValue();
			System.out.printf("  %-28s tried %4d ok %4d (%3.0f%%)  logs %5.1f leaves %6.1f height %4.1f [%d..%d]  %.2f ms/tree%n", e.getKey(), s.tried, s.ok,
				100.0 * s.ok / Math.max(1, s.tried), s.ok == 0 ? 0 : (double) s.logs / s.ok, s.ok == 0 ? 0 : (double) s.leaves / s.ok,
				s.ok == 0 ? 0 : (double) s.hsum / s.ok, s.ok == 0 ? 0 : s.minH, s.maxH, s.nanos / 1e6 / Math.max(1, s.tried));
		}
		System.out.println("voxels outside the region: " + bounds[0]);

		// world checks: leaves that would decay (distance 7), logs without a path to the ground
		int[] dist = new int[W * H * YH];
		ArrayDeque<int[]> q = new ArrayDeque<>();
		int logs = 0, leaves = 0;
		for (int y = 0; y < YH; y++) for (int z = 0; z < H; z++) for (int x = 0; x < W; x++) {
			byte o = occ[idx(x, y, z)];
			if (o == 0) continue;
			TreeMat m = TreeMat.values()[o - 1];
			if (m.isLog()) { logs++; q.add(new int[] { x, y, z }); } else if (m.isLeaf()) leaves++;
		}
		int[] dx = { 1, -1, 0, 0, 0, 0 }, dy = { 0, 0, 1, -1, 0, 0 }, dz = { 0, 0, 0, 0, 1, -1 };
		while (!q.isEmpty()) {
			int[] p = q.poll();
			int d = dist[idx(p[0], p[1], p[2])];
			if (d >= 6) continue;
			for (int i = 0; i < 6; i++) {
				int nx = p[0] + dx[i], ny = p[1] + dy[i], nz = p[2] + dz[i];
				if (!inWorld(nx, ny, nz)) continue;
				byte o = occ[idx(nx, ny, nz)];
				if (o == 0 || !TreeMat.values()[o - 1].isLeaf() || dist[idx(nx, ny, nz)] != 0) continue;
				dist[idx(nx, ny, nz)] = d + 1; q.add(new int[] { nx, ny, nz });
			}
		}
		int decaying = 0;
		for (int i = 0; i < occ.length; i++) if (occ[i] != 0 && TreeMat.values()[occ[i] - 1].isLeaf() && dist[i] == 0) decaying++;
		boolean[] seen = new boolean[occ.length];
		for (int y = 0; y < YH; y++) for (int z = 0; z < H; z++) for (int x = 0; x < W; x++) {
			byte o = occ[idx(x, y, z)];
			if (o != 0 && TreeMat.values()[o - 1].isLog() && y <= h[z * W + x] + 1) { seen[idx(x, y, z)] = true; q.add(new int[] { x, y, z }); }
		}
		while (!q.isEmpty()) {
			int[] p = q.poll();
			for (int i = 0; i < 6; i++) {
				int nx = p[0] + dx[i], ny = p[1] + dy[i], nz = p[2] + dz[i];
				if (!inWorld(nx, ny, nz)) continue;
				int j = idx(nx, ny, nz);
				if (seen[j] || occ[j] == 0 || !TreeMat.values()[occ[j] - 1].isLog()) continue;
				seen[j] = true; q.add(new int[] { nx, ny, nz });
			}
		}
		int floating = 0;
		for (int i = 0; i < occ.length; i++) if (occ[i] != 0 && TreeMat.values()[occ[i] - 1].isLog() && !seen[i]) floating++;
		System.out.printf("world: %d logs, %d leaves; leaves that would decay: %d (%.2f%%); floating logs: %d%n", logs, leaves, decaying, 100.0 * decaying / Math.max(1, leaves), floating);

		// scene dump
		try (PrintWriter w = new PrintWriter(new FileWriter(out))) {
			w.print("{\"W\":" + W + ",\"H\":" + H + ",\"h\":[");
			for (int i = 0; i < W * H; i++) w.print((i > 0 ? "," : "") + h[i]);
			w.print("],\"k\":[");
			for (int i = 0; i < W * H; i++) w.print((i > 0 ? "," : "") + kind[i]);
			w.print("],\"w\":[");
			for (int i = 0; i < W * H; i++) w.print((i > 0 ? "," : "") + water[i]);
			w.print("],\"v\":[");
			boolean first = true;
			for (int y = 0; y < YH; y++) for (int z = 0; z < H; z++) for (int x = 0; x < W; x++) {
				byte o = occ[idx(x, y, z)];
				if (o == 0) continue;
				w.print((first ? "" : ",") + "[" + x + "," + y + "," + z + "," + (o - 1) + "]"); first = false;
			}
			w.print("]}");
		}
	}

	static String pick(Mix m, Random r) {
		for (int i = 0; i < m.names.size(); i++) {
			if (r.nextFloat() < m.chance.get(i)) return m.names.get(i).startsWith("huge_") ? null : m.names.get(i);
		}
		return m.def;
	}
}
