package com.cursedlands.world.tree;

import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;

import com.cursedlands.world.tree.TreeShapes.Branch;
import com.cursedlands.world.tree.TreeShapes.Trunk;

/**
 * The tree species of the Cursed Lands. Every species is a recipe that draws one tree on a {@link TreeCanvas};
 * all sizes are drawn from the random generator of the canvas, so two trees of one species never look alike.
 */
public final class TreeSpecies {
	private TreeSpecies() {
	}

	/** Optional overrides from the feature configuration; negative or zero values keep the species default. */
	public record Params(int heightMin, int heightMax, double leafDensity) {
		public static final Params DEFAULT = new Params(-1, -1, 1.0);

		int height(Random r, int lo, int hi) {
			int a = this.heightMin > 0 ? this.heightMin : lo;
			int b = this.heightMax > 0 ? Math.max(a, this.heightMax) : Math.max(a, hi);
			return a + r.nextInt(b - a + 1);
		}

		double leaf(double density) {
			return Math.max(0.0, Math.min(1.0, density * (this.leafDensity > 0 ? this.leafDensity : 1.0)));
		}
	}

	private interface Recipe {
		void draw(TreeCanvas c, Params p);
	}

	private static final Map<String, Recipe> RECIPES = new TreeMap<>();

	static {
		RECIPES.put("black_oak", TreeSpecies::blackOak);
		RECIPES.put("snag", TreeSpecies::snag);
		RECIPES.put("stump", TreeSpecies::stump);
		RECIPES.put("black_spruce", (c, p) -> spruce(c, p, false));
		RECIPES.put("dead_spruce", (c, p) -> spruce(c, p, true));
		RECIPES.put("cypress", TreeSpecies::cypress);
		RECIPES.put("willow", TreeSpecies::willow);
		RECIPES.put("mangrove", TreeSpecies::mangrove);
		RECIPES.put("bone_birch", (c, p) -> birch(c, p, false));
		RECIPES.put("dead_birch", (c, p) -> birch(c, p, true));
		RECIPES.put("charred", TreeSpecies::charred);
		RECIPES.put("wind_pine", TreeSpecies::windPine);
		RECIPES.put("blood_maple", TreeSpecies::bloodMaple);
		RECIPES.put("thornbrush", TreeSpecies::thornbrush);
	}

	public static Set<String> names() {
		return RECIPES.keySet();
	}

	public static boolean exists(String name) {
		return RECIPES.containsKey(name);
	}

	/** Draws a tree and cleans it up; returns null when the species is unknown or the tree cannot stand here. */
	public static TreeCanvas generate(String name, Random rnd, TreeSite site, Params params) {
		Recipe recipe = RECIPES.get(name);
		if (recipe == null) {
			return null;
		}
		TreeCanvas c = new TreeCanvas(rnd, site);
		if (c.ground(0, 0) != 0) {
			return null;
		}
		recipe.draw(c, params == null ? Params.DEFAULT : params);
		return c.finish() ? c : null;
	}

	// ---- helpers

	private static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}

	private static double clamp(double v, double lo, double hi) {
		return v < lo ? lo : v > hi ? hi : v;
	}

	/** Start of a branch: on the surface of the trunk, on the side it grows toward. */
	private static double[] onTrunk(Trunk t, int y, double az) {
		int yy = Math.max(t.startY, Math.min(t.height, y));
		double reach = Math.max(0.0, t.radius[yy] * 0.8);
		return new double[] { t.sx[yy] + Math.cos(az) * reach, yy, t.sz[yy] + Math.sin(az) * reach };
	}

	// ---- black oak: the broad, gnarled old tree of the dead forest

	private static void blackOak(TreeCanvas c, Params p) {
		Random r = c.rnd;
		int h = p.height(r, 12, 19);
		boolean bare = r.nextDouble() < 0.25;
		Trunk t = new Trunk();
		t.height = h;
		t.rBase = h >= 16 ? 1.9 : h >= 13 ? 1.6 : 1.3;
		t.rTop = 0.5;
		t.taper = 1.8;
		t.flare = 1.1;
		t.flareHeight = 3.5;
		t.leanAz = r.nextDouble() * Math.PI * 2;
		t.lean = r.nextDouble() * 2.5;
		t.wobble = 0.5 + 0.5 * r.nextDouble();
		t.wobbleLen = 6 + 4 * r.nextDouble();
		t.mat = TreeMat.LOG_BLACK;
		TreeShapes.trunk(c, t);
		TreeShapes.roots(c, t, 5 + r.nextInt(3), 3, 6, 0.6, TreeMat.LOG_BLACK);

		int n = 6 + r.nextInt(4);
		double az0 = r.nextDouble() * Math.PI * 2;
		Branch b = new Branch();
		b.upturn = -0.03;
		b.wobble = 0.14;
		b.subProb = 0.55;
		b.subFrom = 0.3;
		b.subLen = 0.6;
		b.subSpread = 0.85;
		b.maxDepth = 3;
		for (int i = 0; i < n; i++) {
			double u = 0.38 + 0.54 * (i + r.nextDouble() * 0.8) / n;
			double v = (u - 0.38) / 0.54;
			int y = (int) Math.round(u * h);
			double az = az0 + i * 2.39996 + (r.nextDouble() - 0.5) * 0.5;
			double el = 0.40 + 0.55 * v + (r.nextDouble() - 0.5) * 0.2;
			double len = clamp(h * (0.62 - 0.30 * v) * (0.8 + 0.4 * r.nextDouble()), 3.0, 8.5);
			double[] s = onTrunk(t, y, az);
			b.r0 = Math.max(0.5, t.radius[Math.min(h, y)] * 0.8);
			b.r1 = 0.5;
			TreeShapes.branch(c, s[0], s[1], s[2], az, el, len, b, 0);
		}
		if (!bare) {
			double d = p.leaf(0.70);
			for (double[] tip : c.tips) {
				double rr = tip[3] == 0 ? 3.3 : 2.6;
				TreeShapes.blob(c, tip[0], tip[1] + 0.3, tip[2], rr, rr * 0.72, rr, d, 0.65, TreeMat.LEAF_WITHERED);
			}
			for (double[] node : c.nodes) {
				if (node[3] >= 1 && r.nextDouble() < 0.55) {
					TreeShapes.blob(c, node[0], node[1] + 0.3, node[2], 2.3, 1.7, 2.3, d, 0.65, TreeMat.LEAF_WITHERED);
				}
			}
			TreeShapes.blob(c, t.sx[h], h + 1.2, t.sz[h], 3.2, 2.2, 3.2, d, 0.7, TreeMat.LEAF_WITHERED);
			TreeShapes.vines(c, 0.05, 2, 6, 6);
		}
		TreeShapes.fungi(c, 0.05, 3, 2, h - 2);
	}

	// ---- snag: a standing dead trunk with broken top and stubs of branches

	private static void snag(TreeCanvas c, Params p) {
		Random r = c.rnd;
		int h = p.height(r, 12, 24);
		int hb = Math.max(6, (int) Math.round(h * (0.78 + 0.22 * r.nextDouble())));
		Trunk t = new Trunk();
		t.height = hb;
		t.rBase = hb >= 14 ? 1.65 : hb >= 10 ? 1.25 : 0.9;
		t.wide2 = t.rBase < 1.0 && r.nextBoolean();
		t.quadX = r.nextBoolean() ? 1 : -1;
		t.quadZ = r.nextBoolean() ? 1 : -1;
		t.rTop = 0.5;
		t.taper = 1.0;
		t.flare = 0.8;
		t.flareHeight = 3.0;
		t.leanAz = r.nextDouble() * Math.PI * 2;
		t.lean = r.nextDouble() * 2.0;
		t.wobble = 0.3 + 0.3 * r.nextDouble();
		t.wobbleLen = 6 + 5 * r.nextDouble();
		t.mat = TreeMat.LOG_BLACK;
		TreeShapes.trunk(c, t);
		TreeShapes.roots(c, t, 3 + r.nextInt(3), 2, 5, 0.45, TreeMat.LOG_BLACK);

		// splintered top
		int spikes = 1 + r.nextInt(3);
		for (int i = 0; i < spikes; i++) {
			int ox = r.nextInt(3) - 1;
			int oz = r.nextInt(3) - 1;
			int len = 1 + r.nextInt(4);
			for (int k = 1; k <= len; k++) {
				c.log(t.sx[hb] + ox, hb + k - (i == 0 ? 0 : 1), t.sz[hb] + oz, TreeCanvas.AXIS_Y, TreeMat.LOG_BLACK);
			}
		}

		// stubs and limbs
		Branch b = new Branch();
		b.r0 = 0.5;
		b.r1 = 0.5;
		b.upturn = 0.03;
		b.wobble = 0.2;
		b.subProb = 0.3;
		b.subFrom = 0.45;
		b.maxDepth = 1;
		int stubs = 4 + r.nextInt(5);
		for (int i = 0; i < stubs; i++) {
			int y = (int) Math.round(hb * (0.22 + 0.74 * r.nextDouble()));
			double az = r.nextDouble() * Math.PI * 2;
			double[] s = onTrunk(t, y, az);
			b.r0 = Math.max(0.5, Math.min(0.9, t.radius[y] * 0.6));
			TreeShapes.branch(c, s[0], s[1], s[2], az, 0.1 + 0.7 * r.nextDouble(), 2.0 + 3.5 * r.nextDouble(), b, 0);
		}
		int limbs = 1 + r.nextInt(2);
		for (int i = 0; i < limbs; i++) {
			int y = (int) Math.round(hb * (0.45 + 0.4 * r.nextDouble()));
			double az = r.nextDouble() * Math.PI * 2;
			double[] s = onTrunk(t, y, az);
			b.r0 = Math.max(0.5, Math.min(1.0, t.radius[y] * 0.7));
			b.maxDepth = 2;
			TreeShapes.branch(c, s[0], s[1], s[2], az, 0.45 + 0.3 * r.nextDouble(), 5 + r.nextInt(4), b, 0);
		}
		if (r.nextDouble() < 0.25) {
			int k = 0;
			for (double[] tip : c.tips) {
				if (k++ < 2) {
					TreeShapes.blob(c, tip[0], tip[1], tip[2], 1.5, p.leaf(0.5), TreeMat.LEAF_WITHERED);
				}
			}
		}
		TreeShapes.fungi(c, 0.10, 4, 2, hb - 1);
	}

	// ---- stump

	private static void stump(TreeCanvas c, Params p) {
		Random r = c.rnd;
		int h = p.height(r, 1, 3);
		Trunk t = new Trunk();
		t.height = h;
		t.rBase = 1.2 + 0.5 * r.nextDouble();
		t.rTop = t.rBase * 0.85;
		t.taper = 1.0;
		t.flare = 0.9;
		t.flareHeight = 2.0;
		t.mat = TreeMat.LOG_BLACK;
		TreeShapes.trunk(c, t);
		TreeShapes.roots(c, t, 4 + r.nextInt(2), 2, 4, 0.5, TreeMat.LOG_BLACK);
		int spikes = r.nextInt(3);
		for (int i = 0; i < spikes; i++) {
			int ox = r.nextInt(3) - 1;
			int oz = r.nextInt(3) - 1;
			c.log(ox, h + 1, oz, TreeCanvas.AXIS_Y, TreeMat.LOG_BLACK);
			if (r.nextBoolean()) {
				c.log(ox, h + 2, oz, TreeCanvas.AXIS_Y, TreeMat.LOG_BLACK);
			}
		}
		TreeShapes.fungi(c, 0.25, 2, 0, h);
	}

	// ---- spruce: a tall conifer with drooping tiers

	private static void spruce(TreeCanvas c, Params p, boolean dead) {
		Random r = c.rnd;
		int h = dead ? p.height(r, 12, 22) : p.height(r, 16, 28);
		Trunk t = new Trunk();
		t.height = h;
		t.rBase = h >= 20 ? 1.4 : 1.1;
		t.rTop = 0.5;
		t.taper = 2.2;
		t.flare = 0.7;
		t.flareHeight = 3.0;
		t.leanAz = r.nextDouble() * Math.PI * 2;
		t.lean = r.nextDouble() * 1.6;
		t.wobble = 0.25;
		t.wobbleLen = 9 + 5 * r.nextDouble();
		t.mat = TreeMat.LOG_PINE;
		TreeShapes.trunk(c, t);
		TreeShapes.roots(c, t, 3 + r.nextInt(2), 2, 4, 0.4, TreeMat.LOG_PINE);

		int y0 = Math.max(3, (int) Math.round(h * (dead ? 0.30 : 0.16)));
		double rMax = clamp(0.30 * h, 4.0, 7.5);
		int boughs = 6 + r.nextInt(3);
		double phase = r.nextDouble() * Math.PI * 2;
		double droop = 0.32 + 0.10 * r.nextDouble();
		double density = p.leaf(dead ? 0.0 : 0.92);

		if (dead) {
			// skeletal tiers of bare boughs with the last tufts of needles at their ends
			Branch b = new Branch();
			b.r0 = 0.5;
			b.r1 = 0.5;
			b.upturn = -0.05;
			b.wobble = 0.12;
			b.subProb = 0.35;
			b.subFrom = 0.4;
			b.maxDepth = 1;
			b.mat = TreeMat.LOG_PINE;
			int y = y0;
			int step = 0;
			while (y <= h - 2) {
				double u = (double) (y - y0) / Math.max(1, h - y0);
				double len = rMax * Math.pow(1.0 - u, 0.85) + 0.8;
				int n = 3 + r.nextInt(3);
				double off = r.nextDouble() * Math.PI * 2;
				for (int k = 0; k < n; k++) {
					double az = off + k * Math.PI * 2 / n + (r.nextDouble() - 0.5) * 0.5;
					double[] s = onTrunk(t, y, az);
					TreeShapes.branch(c, s[0], s[1], s[2], az, 0.12 + 0.2 * r.nextDouble(), len * (0.7 + 0.4 * r.nextDouble()), b, 0);
				}
				y += (step++ % 3 == 2) ? 1 : 2;
			}
			double d = p.leaf(0.5);
			for (double[] tip : c.tips) {
				if (r.nextDouble() < 0.6) {
					TreeShapes.blob(c, tip[0], tip[1], tip[2], 1.5, 1.0, 1.5, d, 0.8, TreeMat.LEAF_NEEDLES);
				}
			}
			c.log(t.sx[h], h + 1, t.sz[h], TreeCanvas.AXIS_Y, TreeMat.LOG_PINE);
			return;
		}

		// the crown is a cone cut into drooping tiers with the outline of a star
		for (int y = y0; y <= h + 1; y++) {
			double u = (double) (y - y0) / (h + 1 - y0);
			double cone = rMax * Math.pow(1.0 - u, 0.85) + 0.8;
			int cx = t.sx[Math.min(y, h)];
			int cz = t.sz[Math.min(y, h)];
			int rad = (int) Math.ceil(cone * 1.4) + 1;
			for (int dz = -rad; dz <= rad; dz++) {
				for (int dx = -rad; dx <= rad; dx++) {
					double d = Math.hypot(dx, dz);
					double th = Math.atan2(dz, dx);
					double star = 1.0 + 0.30 * Math.cos(boughs * th + phase + 0.9 * y);
					double tier = Math.max(0.15, 0.50 + 0.50 * Math.cos((y + droop * d * 1.6) * Math.PI * 2 / 2.6 + phase * 0.5));
					double lim = cone * tier * star;
					boolean core = d <= 1.0;
					if (!core && d > lim) {
						continue;
					}
					double prob = density * (core ? 1.0 : 1.0 - 0.4 * d / cone);
					if (r.nextDouble() < prob) {
						c.leaf(cx + dx, y, cz + dz, TreeMat.LEAF_NEEDLES);
					}
				}
			}
		}
		// stubs of the boughs hold the needles to the trunk
		int step = 0;
		for (int y = y0; y <= h - 3; y += (step++ % 3 == 2) ? 1 : 2) {
			double u = (double) (y - y0) / Math.max(1, h - y0);
			double cone = rMax * Math.pow(1.0 - u, 0.85) + 0.8;
			int n = cone > 3.0 ? 4 : 3;
			double off = r.nextDouble() * Math.PI * 2;
			for (int k = 0; k < n; k++) {
				double az = off + k * Math.PI * 2 / n;
				int reach = cone > 3.5 ? 2 : 1;
				for (int s = 1; s <= reach; s++) {
					int axis = Math.abs(Math.cos(az)) >= Math.abs(Math.sin(az)) ? TreeCanvas.AXIS_X : TreeCanvas.AXIS_Z;
					c.log(t.sx[y] + (int) Math.round(Math.cos(az) * (s + 0.6)), y, t.sz[y] + (int) Math.round(Math.sin(az) * (s + 0.6)), axis, TreeMat.LOG_PINE);
				}
			}
		}
		// dead twigs on the bare lower trunk
		Branch b = new Branch();
		b.r0 = 0.5;
		b.r1 = 0.5;
		b.maxDepth = 0;
		b.mat = TreeMat.LOG_PINE;
		int twigs = 2 + r.nextInt(3);
		for (int i = 0; i < twigs; i++) {
			int yy = 2 + r.nextInt(Math.max(1, y0 - 1));
			double az = r.nextDouble() * Math.PI * 2;
			double[] s = onTrunk(t, yy, az);
			TreeShapes.branch(c, s[0], s[1], s[2], az, -0.1 + 0.4 * r.nextDouble(), 1.5 + r.nextDouble() * 1.5, b, 1);
		}
	}

	// ---- cypress: the narrow dark flame of the graveyards

	private static void cypress(TreeCanvas c, Params p) {
		Random r = c.rnd;
		int h = p.height(r, 14, 24);
		Trunk t = new Trunk();
		t.height = h;
		t.rBase = h >= 17 ? 1.05 : 0.5;
		t.rTop = 0.5;
		t.taper = 1.2;
		t.flare = 0.5;
		t.flareHeight = 2.5;
		t.leanAz = r.nextDouble() * Math.PI * 2;
		t.lean = r.nextDouble() * 1.4;
		t.wobble = 0.3;
		t.wobbleLen = 8 + 4 * r.nextDouble();
		t.mat = TreeMat.LOG_BLACK;
		TreeShapes.trunk(c, t);
		TreeShapes.roots(c, t, 3, 2, 3, 0.3, TreeMat.LOG_BLACK);

		int yc = Math.max(3, (int) Math.round(h * 0.22));
		int top = h + 2;
		double rMax = 1.8 + 0.05 * h + 0.5 * r.nextDouble();
		double density = p.leaf(0.93);
		for (int y = yc; y <= top; y++) {
			double u = (double) (y - yc) / (top - yc);
			double f = u < 0.3 ? Math.sin(u / 0.3 * Math.PI / 2) * 0.88 + 0.12 : Math.pow(1.0 - (u - 0.3) / 0.7, 0.85);
			double radius = rMax * f;
			int cx = t.sx[Math.min(y, h)];
			int cz = t.sz[Math.min(y, h)];
			int rad = (int) Math.ceil(radius + 1.5);
			for (int dz = -rad; dz <= rad; dz++) {
				for (int dx = -rad; dx <= rad; dx++) {
					double d = Math.hypot(dx, dz) + 0.7 * c.noise(cx + dx, y, cz + dz, 2.2);
					if (d <= radius + 0.35 && r.nextDouble() < density) {
						c.leaf(cx + dx, y, cz + dz, TreeMat.LEAF_NEEDLES);
					}
				}
			}
		}
	}

	// ---- weeping willow: arching limbs and curtains of drooping leaves

	private static void willow(TreeCanvas c, Params p) {
		Random r = c.rnd;
		int h = p.height(r, 6, 9);
		Trunk t = new Trunk();
		t.height = h;
		t.rBase = 1.8;
		t.rTop = 1.0;
		t.taper = 1.0;
		t.flare = 1.0;
		t.flareHeight = 3.0;
		t.leanAz = r.nextDouble() * Math.PI * 2;
		t.lean = 1.0 + r.nextDouble() * 2.5;
		t.wobble = 0.5;
		t.mat = TreeMat.LOG_BLACK;
		TreeShapes.trunk(c, t);
		TreeShapes.roots(c, t, 5 + r.nextInt(2), 3, 6, 0.6, TreeMat.LOG_BLACK);

		int limbs = 4 + r.nextInt(3);
		double az0 = r.nextDouble() * Math.PI * 2;
		Branch b = new Branch();
		b.r0 = 1.0;
		b.r1 = 0.5;
		b.upturn = -0.11;
		b.wobble = 0.10;
		b.subProb = 0.45;
		b.subFrom = 0.35;
		b.subLen = 0.6;
		b.maxDepth = 2;
		for (int i = 0; i < limbs; i++) {
			double az = az0 + i * Math.PI * 2 / limbs + (r.nextDouble() - 0.5) * 0.6;
			double[] s = onTrunk(t, h - r.nextInt(3), az);
			TreeShapes.branch(c, s[0], s[1], s[2], az, 0.9 + 0.2 * r.nextDouble(), 7 + r.nextInt(3), b, 0);
		}
		double d = p.leaf(0.82);
		for (double[] tip : c.tips) {
			TreeShapes.blob(c, tip[0], tip[1] + 0.8, tip[2], 2.6, 1.7, 2.6, d, 0.7, TreeMat.LEAF_WITHERED);
		}
		for (double[] node : c.nodes) {
			if (r.nextDouble() < 0.6) {
				TreeShapes.blob(c, node[0], node[1] + 0.6, node[2], 2.2, 1.5, 2.2, d, 0.7, TreeMat.LEAF_WITHERED);
			}
		}
		TreeShapes.blob(c, t.sx[h], h + 1.5, t.sz[h], 3.0, 1.8, 3.0, d, 0.7, TreeMat.LEAF_WITHERED);
		// curtains
		java.util.List<int[]> hooks = new java.util.ArrayList<>();
		c.forEach((x, y, z, m, aux, root) -> {
			if (m.isLeaf() && c.isEmpty(x, y - 1, z)) {
				hooks.add(new int[] { x, y, z });
			}
		});
		for (int[] hk : hooks) {
			if (r.nextDouble() < 0.45) {
				TreeShapes.hang(c, hk[0], hk[1], hk[2], 1 + r.nextInt(2), 3 + r.nextInt(6), TreeMat.LEAF_WITHERED);
			}
		}
		TreeShapes.fungi(c, 0.06, 2, 1, h - 1);
	}

	// ---- blood mangrove: a crown on arching stilt roots

	private static void mangrove(TreeCanvas c, Params p) {
		Random r = c.rnd;
		int lift = 3 + r.nextInt(3);
		int h = lift + p.height(r, 7, 10);
		Trunk t = new Trunk();
		t.startY = lift;
		t.height = h;
		t.rBase = 1.0;
		t.rTop = 0.5;
		t.taper = 1.2;
		t.flare = 0.0;
		t.leanAz = r.nextDouble() * Math.PI * 2;
		t.lean = r.nextDouble() * 1.8;
		t.wobble = 0.35;
		t.mat = TreeMat.LOG_BLACK;
		TreeShapes.trunk(c, t);
		int arches = 4 + r.nextInt(3);
		double start = r.nextDouble() * Math.PI * 2;
		for (int i = 0; i < arches; i++) {
			double az = start + (i + (r.nextDouble() - 0.5) * 0.5) * Math.PI * 2 / arches;
			TreeShapes.stilt(c, t.sx[lift], t.sz[lift], lift + 1, az, 4 + r.nextInt(3), TreeMat.LOG_BLACK);
		}
		// a few thin prop roots from higher up the trunk
		for (int i = 0; i < 2; i++) {
			double az = r.nextDouble() * Math.PI * 2;
			TreeShapes.stilt(c, t.sx[lift + 1], t.sz[lift + 1], lift + 3, az, 3 + r.nextInt(2), TreeMat.LOG_BLACK);
		}

		int leaders = 2 + r.nextInt(3);
		double az0 = r.nextDouble() * Math.PI * 2;
		Branch b = new Branch();
		b.r0 = 0.8;
		b.r1 = 0.5;
		b.upturn = 0.0;
		b.wobble = 0.12;
		b.subProb = 0.45;
		b.maxDepth = 1;
		for (int i = 0; i < leaders; i++) {
			double az = az0 + i * Math.PI * 2 / leaders + (r.nextDouble() - 0.5) * 0.7;
			double[] s = onTrunk(t, h - 3 - r.nextInt(3), az);
			TreeShapes.branch(c, s[0], s[1], s[2], az, 0.6 + 0.4 * r.nextDouble(), 4 + r.nextInt(3), b, 0);
		}
		double d = p.leaf(0.82);
		for (double[] tip : c.tips) {
			TreeShapes.blob(c, tip[0], tip[1] + 0.4, tip[2], 3.2, 2.0, 3.2, d, 0.6, TreeMat.LEAF_CRIMSON);
		}
		TreeShapes.blob(c, t.sx[h], h + 0.8, t.sz[h], 3.4, 2.0, 3.4, d, 0.6, TreeMat.LEAF_CRIMSON);
		TreeShapes.vines(c, 0.07, 2, 6, 6);
	}

	// ---- bone birch: pale, slender, with a thin airy crown

	private static void birch(TreeCanvas c, Params p, boolean dead) {
		Random r = c.rnd;
		int h = p.height(r, 11, 19);
		Trunk t = new Trunk();
		t.height = h;
		t.rBase = h >= 14 ? 0.8 : 0.5;
		t.wide2 = t.rBase >= 0.75;
		t.quadX = r.nextBoolean() ? 1 : -1;
		t.quadZ = r.nextBoolean() ? 1 : -1;
		t.rTop = 0.5;
		t.taper = 1.0;
		t.flare = 0.3;
		t.flareHeight = 2.0;
		t.leanAz = r.nextDouble() * Math.PI * 2;
		t.lean = r.nextDouble() * 2.2;
		t.wobble = 0.55;
		t.wobbleLen = 7 + 4 * r.nextDouble();
		t.mat = TreeMat.LOG_PALE;
		TreeShapes.trunk(c, t);
		TreeShapes.roots(c, t, 3, 2, 3, 0.25, TreeMat.LOG_PALE);

		int n = 7 + r.nextInt(5);
		double az0 = r.nextDouble() * Math.PI * 2;
		Branch b = new Branch();
		b.r0 = 0.5;
		b.r1 = 0.5;
		b.upturn = -0.05;
		b.wobble = 0.14;
		b.subProb = 0.35;
		b.subFrom = 0.45;
		b.subLen = 0.55;
		b.maxDepth = 1;
		b.mat = TreeMat.LOG_PALE;
		for (int i = 0; i < n; i++) {
			double u = 0.35 + 0.60 * (i + r.nextDouble() * 0.8) / n;
			int y = (int) Math.round(u * h);
			double az = az0 + i * 2.39996 + (r.nextDouble() - 0.5) * 0.5;
			double len = 2.4 + 3.6 * (1.0 - u) + r.nextDouble();
			double[] s = onTrunk(t, y, az);
			TreeShapes.branch(c, s[0], s[1], s[2], az, 0.7 + 0.5 * r.nextDouble(), len, b, 0);
		}
		if (!dead) {
			double d = p.leaf(0.55);
			for (double[] tip : c.tips) {
				double rr = 1.5 + 0.5 * r.nextDouble();
				TreeShapes.blob(c, tip[0], tip[1] + 0.3, tip[2], rr, rr * 0.8, rr, d, 0.7, TreeMat.LEAF_ASHEN);
			}
			TreeShapes.blob(c, t.sx[h], h + 0.5, t.sz[h], 2.0, 1.7, 2.0, d, 0.7, TreeMat.LEAF_ASHEN);
		}
	}

	// ---- charred tree: what a fire left standing

	private static void charred(TreeCanvas c, Params p) {
		Random r = c.rnd;
		int h = p.height(r, 8, 16);
		Trunk t = new Trunk();
		t.height = h;
		t.rBase = h >= 12 ? 1.9 : h >= 9 ? 1.5 : 1.2;
		t.rTop = 0.5;
		t.taper = 1.2;
		t.flare = 0.8;
		t.flareHeight = 2.5;
		t.leanAz = r.nextDouble() * Math.PI * 2;
		t.lean = r.nextDouble() * 2.5;
		t.wobble = 0.4;
		t.mat = TreeMat.LOG_CHARRED;
		TreeShapes.trunk(c, t);
		TreeShapes.roots(c, t, 3 + r.nextInt(2), 2, 4, 0.5, TreeMat.LOG_CHARRED);

		Branch b = new Branch();
		b.r0 = 0.6;
		b.r1 = 0.5;
		b.upturn = -0.09;
		b.wobble = 0.2;
		b.subProb = 0.2;
		b.maxDepth = 1;
		b.mat = TreeMat.LOG_CHARRED;
		int stubs = 5 + r.nextInt(4);
		for (int i = 0; i < stubs; i++) {
			int y = (int) Math.round(h * (0.3 + 0.62 * r.nextDouble()));
			double az = r.nextDouble() * Math.PI * 2;
			double[] s = onTrunk(t, y, az);
			TreeShapes.branch(c, s[0], s[1], s[2], az, 0.1 + 0.6 * r.nextDouble(), 2 + 2.5 * r.nextDouble(), b, 0);
		}
		if (r.nextDouble() < 0.3 && h >= 8) {
			// a second leader: the trunk was split by lightning before the fire
			int y = (int) Math.round(h * 0.6);
			double az = r.nextDouble() * Math.PI * 2;
			double[] s = onTrunk(t, y, az);
			b.r0 = 0.8;
			b.upturn = 0.03;
			TreeShapes.branch(c, s[0], s[1], s[2], az, 1.25, h * 0.45, b, 0);
		}
		int spikes = 1 + r.nextInt(2);
		for (int i = 0; i < spikes; i++) {
			int len = 1 + r.nextInt(3);
			int ox = r.nextInt(3) - 1;
			int oz = r.nextInt(3) - 1;
			for (int k = 1; k <= len; k++) {
				c.log(t.sx[h] + ox, h + k - 1, t.sz[h] + oz, TreeCanvas.AXIS_Y, TreeMat.LOG_CHARRED);
			}
		}
	}

	// ---- windswept pine: leaning, with the crown blown to one side

	private static void windPine(TreeCanvas c, Params p) {
		Random r = c.rnd;
		int h = p.height(r, 8, 16);
		Trunk t = new Trunk();
		t.height = h;
		t.rBase = 1.1;
		t.rTop = 0.5;
		t.taper = 1.2;
		t.flare = 0.6;
		t.flareHeight = 3.0;
		t.leanAz = r.nextDouble() * Math.PI * 2;
		t.lean = 3.0 + r.nextDouble() * 3.0;
		t.leanPow = 1.3;
		t.wobble = 0.8;
		t.wobbleLen = 6 + 3 * r.nextDouble();
		t.mat = TreeMat.LOG_PINE;
		TreeShapes.trunk(c, t);
		TreeShapes.roots(c, t, 4, 3, 5, 0.5, TreeMat.LOG_PINE);

		int n = 7 + r.nextInt(4);
		Branch b = new Branch();
		b.r0 = 0.6;
		b.r1 = 0.5;
		b.upturn = 0.02;
		b.wobble = 0.12;
		b.subProb = 0.3;
		b.maxDepth = 1;
		b.mat = TreeMat.LOG_PINE;
		for (int i = 0; i < n; i++) {
			double u = 0.38 + 0.58 * (i + r.nextDouble() * 0.8) / n;
			int y = (int) Math.round(u * h);
			double az = t.leanAz + r.nextGaussian() * 0.9;
			double lee = Math.cos(az - t.leanAz);
			double len = (2.2 + 2.4 * (1.0 - u)) * (0.7 + 0.45 * (lee + 1.0) * 0.5 + 0.2 * r.nextDouble());
			double[] s = onTrunk(t, y, az);
			TreeShapes.branch(c, s[0], s[1], s[2], az, 0.12 + 0.4 * r.nextDouble(), len, b, 0);
		}
		double d = p.leaf(0.82);
		for (double[] tip : c.tips) {
			TreeShapes.blob(c, tip[0], tip[1] + 0.2, tip[2], 2.4, 1.0, 2.4, d, 0.8, TreeMat.LEAF_NEEDLES);
		}
		TreeShapes.blob(c, t.sx[h], h + 0.5, t.sz[h], 2.2, 1.7, 2.2, d, 0.8, TreeMat.LEAF_NEEDLES);
	}

	// ---- blood maple: a round, dense crown of dark crimson

	private static void bloodMaple(TreeCanvas c, Params p) {
		Random r = c.rnd;
		int h = p.height(r, 8, 13);
		Trunk t = new Trunk();
		t.height = h;
		t.rBase = 1.15 + 0.25 * r.nextDouble();
		t.rTop = 0.5;
		t.taper = 1.3;
		t.flare = 0.7;
		t.flareHeight = 3.0;
		t.leanAz = r.nextDouble() * Math.PI * 2;
		t.lean = r.nextDouble() * 1.5;
		t.wobble = 0.4;
		t.mat = TreeMat.LOG_BLACK;
		TreeShapes.trunk(c, t);
		TreeShapes.roots(c, t, 4, 2, 4, 0.5, TreeMat.LOG_BLACK);

		int n = 6 + r.nextInt(4);
		double az0 = r.nextDouble() * Math.PI * 2;
		Branch b = new Branch();
		b.upturn = 0.0;
		b.wobble = 0.12;
		b.subProb = 0.5;
		b.subLen = 0.55;
		b.maxDepth = 2;
		for (int i = 0; i < n; i++) {
			double u = 0.5 + 0.45 * (i + r.nextDouble() * 0.8) / n;
			double v = (u - 0.5) / 0.45;
			int y = (int) Math.round(u * h);
			double az = az0 + i * 2.39996 + (r.nextDouble() - 0.5) * 0.4;
			double el = 0.45 + 0.5 * v;
			double len = 3.0 + 2.5 * (1.0 - v) * (0.8 + 0.4 * r.nextDouble());
			double[] s = onTrunk(t, y, az);
			b.r0 = Math.max(0.5, t.radius[Math.min(h, y)] * 0.7);
			b.r1 = 0.5;
			TreeShapes.branch(c, s[0], s[1], s[2], az, el, len, b, 0);
		}
		double d = p.leaf(0.88);
		for (double[] tip : c.tips) {
			double rr = 2.4 + 0.7 * r.nextDouble();
			TreeShapes.blob(c, tip[0], tip[1] + 0.3, tip[2], rr, rr * 0.8, rr, d, 0.7, TreeMat.LEAF_CRIMSON);
		}
		TreeShapes.blob(c, t.sx[h], h + 1.0, t.sz[h], 3.0, 2.2, 3.0, d, 0.7, TreeMat.LEAF_CRIMSON);
	}

	// ---- thornbrush: low understory shrub

	private static void thornbrush(TreeCanvas c, Params p) {
		Random r = c.rnd;
		int h = p.height(r, 2, 4);
		Trunk t = new Trunk();
		t.height = h;
		t.rBase = 0.5;
		t.rTop = 0.5;
		t.taper = 1.0;
		t.lean = r.nextDouble() * 1.0;
		t.leanAz = r.nextDouble() * Math.PI * 2;
		t.mat = TreeMat.LOG_BLACK;
		TreeShapes.trunk(c, t);

		int n = 5 + r.nextInt(4);
		double az0 = r.nextDouble() * Math.PI * 2;
		Branch b = new Branch();
		b.r0 = 0.5;
		b.r1 = 0.5;
		b.upturn = 0.0;
		b.wobble = 0.2;
		b.subProb = 0.4;
		b.maxDepth = 1;
		for (int i = 0; i < n; i++) {
			int y = 1 + r.nextInt(Math.max(1, h));
			double az = az0 + i * 2.39996;
			TreeShapes.branch(c, t.sx[Math.min(h, y)], y, t.sz[Math.min(h, y)], az, 0.3 + 0.6 * r.nextDouble(), 2.0 + 1.2 * r.nextDouble(), b, 0);
		}
		double d = p.leaf(0.78);
		for (double[] tip : c.tips) {
			TreeShapes.blob(c, tip[0], tip[1], tip[2], 1.5 + 0.4 * r.nextDouble(), d, TreeMat.LEAF_WITHERED);
		}
		TreeShapes.blob(c, t.sx[h], h + 0.4, t.sz[h], 1.7, d, TreeMat.LEAF_WITHERED);
	}
}
