package com.cursedlands.world.tree;

import java.util.Random;

/** Building blocks of the tree species: trunks, roots, branches, leaf clusters, hanging vines and fungi. */
public final class TreeShapes {
	private TreeShapes() {
	}

	// ---- trunk

	/** Description of a trunk; {@link #trunk} fills in the spine arrays. */
	public static final class Trunk {
		public int height;
		public double rBase = 1.0;
		public double rTop = 0.5;
		/** Exponent of the taper: above 1 keeps the trunk thick for longer. */
		public double taper = 1.2;
		public double flare;
		public double flareHeight = 3.0;
		/** Direction of the lean (radians), total horizontal shift at the top, exponent of the curve. */
		public double leanAz;
		public double lean;
		public double leanPow = 1.6;
		public double wobble;
		public double wobbleLen = 8.0;
		/** A trunk of two by two blocks: the centre lies between four columns, on the side given by quadX and quadZ. */
		public boolean wide2;
		public int quadX = 1;
		public int quadZ = 1;
		public int startY;
		public TreeMat mat = TreeMat.LOG_BLACK;

		/** Spine column and radius of every layer, indexed by y (valid from startY to height). */
		public int[] sx;
		public int[] sz;
		public double[] radius;
	}

	public static void trunk(TreeCanvas c, Trunk t) {
		int n = t.height;
		t.sx = new int[n + 1];
		t.sz = new int[n + 1];
		t.radius = new double[n + 1];
		double dxTotal = Math.cos(t.leanAz) * t.lean;
		double dzTotal = Math.sin(t.leanAz) * t.lean;
		double phaseX = c.rnd.nextDouble() * Math.PI * 2;
		double phaseZ = c.rnd.nextDouble() * Math.PI * 2;
		double ox = t.wide2 ? 0.5 * t.quadX : 0.0;
		double oz = t.wide2 ? 0.5 * t.quadZ : 0.0;
		int px = 0;
		int pz = 0;
		for (int y = t.startY; y <= n; y++) {
			double u = n == 0 ? 0 : (double) y / n;
			double fx = dxTotal * Math.pow(u, t.leanPow);
			double fz = dzTotal * Math.pow(u, t.leanPow);
			if (t.wobble > 0) {
				fx += t.wobble * (Math.sin(Math.PI * 2 * y / t.wobbleLen + phaseX) - Math.sin(phaseX));
				fz += t.wobble * (Math.sin(Math.PI * 2 * y / (t.wobbleLen * 1.3) + phaseZ) - Math.sin(phaseZ));
			}
			int ix = clamp((int) Math.round(fx), px - 1, px + 1);
			int iz = clamp((int) Math.round(fz), pz - 1, pz + 1);
			if (y == t.startY) {
				ix = 0;
				iz = 0;
			} else if (ix != px && iz != pz) {
				// never step in both directions in one layer: the one that is further from its target goes first
				if (Math.abs(fx - px) >= Math.abs(fz - pz)) {
					iz = pz;
				} else {
					ix = px;
				}
			}
			t.sx[y] = ix;
			t.sz[y] = iz;
			double r = t.rTop + (t.rBase - t.rTop) * Math.pow(Math.max(0, 1.0 - (double) y / Math.max(1, n)), t.taper);
			if (t.flare > 0 && y < t.flareHeight) {
				double f = 1.0 - y / t.flareHeight;
				r += t.flare * f * f;
			}
			t.radius[y] = r;

			if (y > t.startY && (ix != px || iz != pz)) {
				c.log(ix, y - 1, iz, ix != px ? TreeCanvas.AXIS_X : TreeCanvas.AXIS_Z, t.mat, false, true);
			}
			int rad = (int) Math.ceil(r + 1.2);
			for (int dz = -rad; dz <= rad; dz++) {
				for (int dx = -rad; dx <= rad; dx++) {
					double dist = Math.hypot(dx - ox, dz - oz);
					double jitter = (c.hash01(dx * 7 + 1, dz * 13 + 5) - 0.5) * 0.34
							+ 0.12 * c.noise(ix + dx, y, iz + dz, 2.5);
					if (dist <= r + jitter || (dx == 0 && dz == 0)) {
						c.log(ix + dx, y, iz + dz, TreeCanvas.AXIS_Y, t.mat, false, true);
					}
				}
			}
			px = ix;
			pz = iz;
		}
	}

	private static int clamp(int v, int lo, int hi) {
		return v < lo ? lo : v > hi ? hi : v;
	}

	// ---- lines

	public interface LineVisitor {
		/** t runs from 0 at the start to 1 at the end. */
		void visit(int x, int y, int z, int axis, double t);
	}

	/** Walks a 6-connected line of voxels from (x0, y0, z0) to (x1, y1, z1) (the start is visited too). */
	public static void line6(int x0, int y0, int z0, int x1, int y1, int z1, LineVisitor v) {
		int dx = x1 - x0;
		int dy = y1 - y0;
		int dz = z1 - z0;
		int ax = Math.abs(dx);
		int ay = Math.abs(dy);
		int az = Math.abs(dz);
		int n = ax + ay + az;
		int x = x0;
		int y = y0;
		int z = z0;
		int firstAxis = ay >= ax && ay >= az ? TreeCanvas.AXIS_Y : ax >= az ? TreeCanvas.AXIS_X : TreeCanvas.AXIS_Z;
		v.visit(x, y, z, firstAxis, 0.0);
		if (n == 0) {
			return;
		}
		double inf = Double.POSITIVE_INFINITY;
		double tx = ax == 0 ? inf : 0.5 / ax;
		double ty = ay == 0 ? inf : 0.5 / ay;
		double tz = az == 0 ? inf : 0.5 / az;
		double dtx = ax == 0 ? inf : 1.0 / ax;
		double dty = ay == 0 ? inf : 1.0 / ay;
		double dtz = az == 0 ? inf : 1.0 / az;
		for (int i = 1; i <= n; i++) {
			int axis;
			if (tx <= ty && tx <= tz) {
				x += Integer.signum(dx);
				tx += dtx;
				axis = TreeCanvas.AXIS_X;
			} else if (ty <= tz) {
				y += Integer.signum(dy);
				ty += dty;
				axis = TreeCanvas.AXIS_Y;
			} else {
				z += Integer.signum(dz);
				tz += dtz;
				axis = TreeCanvas.AXIS_Z;
			}
			v.visit(x, y, z, axis, (double) i / n);
		}
	}

	/** Places a ball of logs: radius up to 0.6 is a single voxel, 1.0 a cross, 1.5 a thick lump. */
	public static void ball(TreeCanvas c, int x, int y, int z, double r, int axis, TreeMat m) {
		c.log(x, y, z, axis, m);
		if (r < 0.6) {
			return;
		}
		int rad = (int) Math.ceil(r);
		double lim = r * r + 0.05;
		for (int dz = -rad; dz <= rad; dz++) {
			for (int dy = -rad; dy <= rad; dy++) {
				for (int dx = -rad; dx <= rad; dx++) {
					if (dx * dx + dy * dy + dz * dz <= lim) {
						c.log(x + dx, y + dy, z + dz, axis, m);
					}
				}
			}
		}
	}

	// ---- branches

	public static final class Branch {
		public double r0 = 1.0;
		public double r1 = 0.5;
		/** Radians added to the elevation at every step: positive bends upwards, negative droops. */
		public double upturn;
		/** Random drift of the azimuth per step. */
		public double wobble = 0.12;
		public double subProb = 0.3;
		public double subFrom = 0.35;
		public double subTo = 0.9;
		public double subLen = 0.5;
		public double subSpread = 0.9;
		public double subLift = 0.2;
		public int maxDepth = 2;
		public int minGap = 2;
		public TreeMat mat = TreeMat.LOG_BLACK;
	}

	/**
	 * Grows a branch step by step from (x, y, z). Direction is given by azimuth (radians in the x-z plane) and
	 * elevation (radians above the horizontal). Child branches fork off along its second half. The end of every
	 * branch is added to {@code c.tips}.
	 */
	public static void branch(TreeCanvas c, double x, double y, double z, double az, double el, double length, Branch b, int depth) {
		Random rnd = c.rnd;
		int steps = Math.max(1, (int) Math.round(length));
		double px = x;
		double py = y;
		double pz = z;
		int vx = (int) Math.round(px);
		int vy = (int) Math.round(py);
		int vz = (int) Math.round(pz);
		int lastSub = -100;
		for (int i = 1; i <= steps; i++) {
			el += b.upturn + rnd.nextGaussian() * 0.05;
			az += rnd.nextGaussian() * b.wobble;
			el = Math.max(-1.45, Math.min(1.45, el));
			px += Math.cos(el) * Math.cos(az);
			py += Math.sin(el);
			pz += Math.cos(el) * Math.sin(az);
			if (Math.abs(px) > TreeCanvas.R - 3 || Math.abs(pz) > TreeCanvas.R - 3 || py > TreeCanvas.Y_MAX - 3) {
				// keep the whole branch (and the crown around its end) inside the canvas
				px -= Math.cos(el) * Math.cos(az);
				py -= Math.sin(el);
				pz -= Math.cos(el) * Math.sin(az);
				break;
			}
			int nx = (int) Math.round(px);
			int ny = (int) Math.round(py);
			int nz = (int) Math.round(pz);
			double progress = (double) i / steps;
			double r = b.r0 + (b.r1 - b.r0) * progress;
			line6(vx, vy, vz, nx, ny, nz, (lx, ly, lz, axis, t) -> ball(c, lx, ly, lz, r, axis, b.mat));
			vx = nx;
			vy = ny;
			vz = nz;
			if (progress >= 0.45 && progress < 1.0 && i % 2 == 0) {
				c.nodes.add(new double[] { px, py, pz, depth, progress });
			}
			if (depth < b.maxDepth && progress >= b.subFrom && progress <= b.subTo && i - lastSub >= b.minGap
					&& rnd.nextDouble() < b.subProb) {
				lastSub = i;
				double side = rnd.nextBoolean() ? 1 : -1;
				double subAz = az + side * (b.subSpread * (0.6 + 0.6 * rnd.nextDouble()));
				double subEl = el + b.subLift * (rnd.nextDouble() * 2 - 0.6);
				double subLength = Math.max(1.5, (steps - i) * b.subLen * (0.75 + 0.5 * rnd.nextDouble()));
				Branch child = copy(b);
				child.r0 = Math.max(0.5, Math.min(r * 0.8, b.r1 + 0.15));
				child.r1 = 0.5;
				branch(c, px, py, pz, subAz, subEl, subLength, child, depth + 1);
			}
		}
		c.tips.add(new double[] { px, py, pz, depth, az, el, length });
	}

	private static Branch copy(Branch b) {
		Branch n = new Branch();
		n.r0 = b.r0;
		n.r1 = b.r1;
		n.upturn = b.upturn;
		n.wobble = b.wobble;
		n.subProb = b.subProb;
		n.subFrom = b.subFrom;
		n.subTo = b.subTo;
		n.subLen = b.subLen;
		n.subSpread = b.subSpread;
		n.subLift = b.subLift;
		n.maxDepth = b.maxDepth;
		n.minGap = b.minGap;
		n.mat = b.mat;
		return n;
	}

	// ---- roots

	/**
	 * Roots that run out of the base of the trunk along the ground: a raised ridge close to the trunk, then flush with
	 * the surface, then diving into the ground. They follow the terrain and may replace ground blocks.
	 */
	public static void roots(TreeCanvas c, Trunk t, int count, int lenMin, int lenMax, double bump, TreeMat m) {
		Random rnd = c.rnd;
		double start = rnd.nextDouble() * Math.PI * 2;
		double edge = t.rBase + t.flare * 0.6;
		for (int i = 0; i < count; i++) {
			double az = start + (i + (rnd.nextDouble() - 0.5) * 0.7) * Math.PI * 2 / count;
			int len = lenMin + rnd.nextInt(Math.max(1, lenMax - lenMin + 1));
			double cosA = Math.cos(az);
			double sinA = Math.sin(az);
			int axis = Math.abs(cosA) >= Math.abs(sinA) ? TreeCanvas.AXIS_X : TreeCanvas.AXIS_Z;
			int lastX = Integer.MIN_VALUE;
			int lastZ = Integer.MIN_VALUE;
			int[] lastY = { Integer.MIN_VALUE };
			int cells = 0;
			int total = (int) Math.ceil(edge + len);
			// first pass: collect the cells of the root
			int[] xs = new int[total * 3 + 8];
			int[] zs = new int[total * 3 + 8];
			double s = Math.max(0.0, edge - 0.9);
			int cx0 = t.sx[0];
			int cz0 = t.sz[0];
			while (s <= edge + len) {
				int x = cx0 + (int) Math.round(cosA * s);
				int z = cz0 + (int) Math.round(sinA * s);
				if (x != lastX || z != lastZ) {
					if (lastX != Integer.MIN_VALUE && x != lastX && z != lastZ) {
						xs[cells] = x;
						zs[cells] = lastZ;
						cells++;
					}
					xs[cells] = x;
					zs[cells] = z;
					cells++;
					lastX = x;
					lastZ = z;
				}
				s += 0.5;
			}
			for (int k = 0; k < cells; k++) {
				int g = c.ground(xs[k], zs[k]);
				if (g == TreeSite.NO_GROUND) {
					break;
				}
				double q = cells <= 1 ? 0 : (double) k / (cells - 1);
				int drop = q < 0.34 ? 0 : q < 0.72 ? 1 : 2;
				int y = g - drop;
				if (lastY[0] != Integer.MIN_VALUE) {
					int lo = Math.min(lastY[0], y);
					int hi = Math.max(lastY[0], y);
					for (int yy = lo; yy <= hi; yy++) {
						c.log(xs[k], yy, zs[k], yy == y ? axis : TreeCanvas.AXIS_Y, m, true, false);
					}
				} else {
					c.log(xs[k], y, zs[k], axis, m, true, false);
				}
				if (k <= 1 && rnd.nextDouble() < bump) {
					c.log(xs[k], g + 1, zs[k], TreeCanvas.AXIS_Y, m, true, false);
				}
				lastY[0] = y;
			}
		}
	}

	/**
	 * A stilt root: an arch that leaves the trunk at height topY, curves outward and down, and enters the ground
	 * {@code len} blocks away. The part below the surface may replace ground blocks.
	 */
	public static void stilt(TreeCanvas c, int cx, int cz, int topY, double az, int len, TreeMat m) {
		double cosA = Math.cos(az);
		double sinA = Math.sin(az);
		int ex = cx + (int) Math.round(cosA * len);
		int ez = cz + (int) Math.round(sinA * len);
		int gEnd = c.ground(ex, ez);
		if (gEnd == TreeSite.NO_GROUND) {
			return;
		}
		int bottom = gEnd - 1;
		int vx = cx;
		int vy = topY;
		int vz = cz;
		for (double s = 0.5; s <= len + 0.01; s += 0.5) {
			double q = s / len;
			int nx = cx + (int) Math.round(cosA * s);
			int nz = cz + (int) Math.round(sinA * s);
			int ny = (int) Math.round(topY - (topY - bottom) * Math.pow(q, 1.6));
			double r = s < 1.6 ? 1.0 : 0.5;
			int gHere = c.ground(nx, nz);
			final boolean underground = gHere != TreeSite.NO_GROUND && ny < gHere;
			line6(vx, vy, vz, nx, ny, nz, (lx, ly, lz, axis, t) -> {
				ball(c, lx, ly, lz, r, axis, m);
				if (underground) {
					c.log(lx, ly, lz, axis, m, true, false);
				}
			});
			vx = nx;
			vy = ny;
			vz = nz;
		}
		// dive into the ground so that the root cannot hang above a dip
		for (int y = vy - 1; y >= bottom - 2; y--) {
			c.log(vx, y, vz, TreeCanvas.AXIS_Y, m, true, false);
		}
	}

	// ---- leaves

	/**
	 * A lumpy cluster of leaves. {@code density} is the share of voxels kept inside; the outline is perturbed by noise
	 * and thinned toward the edge. {@code bottom} scales the lower half (1 keeps a sphere, 0.6 flattens the underside).
	 */
	public static void blob(TreeCanvas c, double cx, double cy, double cz, double rx, double ry, double rz, double density,
			double bottom, TreeMat m) {
		Random rnd = c.rnd;
		cx = Math.max(-TreeCanvas.R + rx, Math.min(TreeCanvas.R - rx, cx));
		cz = Math.max(-TreeCanvas.R + rz, Math.min(TreeCanvas.R - rz, cz));
		int x0 = (int) Math.floor(cx - rx - 1);
		int x1 = (int) Math.ceil(cx + rx + 1);
		int y0 = (int) Math.floor(cy - ry - 1);
		int y1 = (int) Math.ceil(cy + ry + 1);
		int z0 = (int) Math.floor(cz - rz - 1);
		int z1 = (int) Math.ceil(cz + rz + 1);
		for (int z = z0; z <= z1; z++) {
			for (int y = y0; y <= y1; y++) {
				for (int x = x0; x <= x1; x++) {
					double ux = (x - cx) / rx;
					double uy = (y - cy) / (y < cy ? ry * bottom : ry);
					double uz = (z - cz) / rz;
					double d = Math.sqrt(ux * ux + uy * uy + uz * uz);
					double limit = 1.0 + 0.30 * c.noise(x, y, z, 2.6);
					if (d > limit) {
						continue;
					}
					double p = d < 0.55 ? 1.0 : 1.0 - (d - 0.55) / Math.max(0.05, limit - 0.55) * 0.7;
					if (rnd.nextDouble() < density * p) {
						c.leaf(x, y, z, m);
					}
				}
			}
		}
	}

	/** Leaf blob of equal radius in all directions, flattened underneath. */
	public static void blob(TreeCanvas c, double cx, double cy, double cz, double r, double density, TreeMat m) {
		blob(c, cx, cy, cz, r, r * 0.72, r, density, 0.7, m);
	}

	/** A column of leaves hanging from (x, y, z) downward, ending in vines. */
	public static void hang(TreeCanvas c, int x, int y, int z, int leaves, int vines, TreeMat leaf) {
		int yy = y - 1;
		for (int i = 0; i < leaves; i++, yy--) {
			if (!c.isEmpty(x, yy, z)) {
				return;
			}
			int g = c.ground(x, z);
			if (g != TreeSite.NO_GROUND && yy < g) {
				return;
			}
			c.leaf(x, yy, z, leaf);
		}
		for (int i = 0; i < vines; i++, yy--) {
			int g = c.ground(x, z);
			if (!c.isEmpty(x, yy, z) || (g != TreeSite.NO_GROUND && yy < g)) {
				return;
			}
			c.put(x, yy, z, TreeMat.VINE, 0);
		}
	}

	// ---- decoration

	/** Hangs vine columns from leaves and logs that have air below them. */
	public static void vines(TreeCanvas c, double prob, int lenMin, int lenMax, int maxColumns) {
		Random rnd = c.rnd;
		int[] placed = { 0 };
		c.forEach((x, y, z, m, aux, root) -> {
			if (placed[0] >= maxColumns || !(m.isLeaf() || m.isLog()) || root) {
				return;
			}
			if (!c.isEmpty(x, y - 1, z) || rnd.nextDouble() >= prob) {
				return;
			}
			int len = lenMin + rnd.nextInt(Math.max(1, lenMax - lenMin + 1));
			int g = c.ground(x, z);
			boolean any = false;
			for (int i = 1; i <= len; i++) {
				int yy = y - i;
				if (!c.isEmpty(x, yy, z) || (g != TreeSite.NO_GROUND && yy < g)) {
					break;
				}
				c.put(x, yy, z, TreeMat.VINE, 0);
				any = true;
			}
			if (any) {
				placed[0]++;
			}
		});
	}

	/** Shelf fungi on the sides of trunk logs above the roots. */
	public static void fungi(TreeCanvas c, double prob, int maxCount, int minY, int maxY) {
		Random rnd = c.rnd;
		int[] placed = { 0 };
		c.forEach((x, y, z, m, aux, root) -> {
			if (placed[0] >= maxCount || !m.isLog() || root || y < minY || y > maxY || aux != TreeCanvas.AXIS_Y) {
				return;
			}
			if (rnd.nextDouble() >= prob) {
				return;
			}
			int dir = rnd.nextInt(4);
			int fx = x + TreeCanvas.STEP_X[dir];
			int fz = z + TreeCanvas.STEP_Z[dir];
			if (c.isEmpty(fx, y, fz) && c.put(fx, y, fz, TreeMat.FUNGUS, dir)) {
				placed[0]++;
			}
		});
	}
}
