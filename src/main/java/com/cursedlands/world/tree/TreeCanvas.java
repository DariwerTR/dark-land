package com.cursedlands.world.tree;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Voxel canvas of one tree. Coordinates are relative to the tree origin (the free block above the ground where the
 * trunk starts); the canvas covers {@code -R..R} horizontally, which keeps every tree inside the 3x3 chunks that are
 * available while a chunk is being decorated.
 *
 * <p>Species code draws logs, leaves, vines and fungi here with the helpers of {@link TreeShapes}; {@link #finish()}
 * then removes what cannot exist (blocked by the terrain, cut off from the trunk, too far from a log to survive).
 * The class knows nothing about Minecraft, so the same code runs in the game and in the offline preview.
 */
public final class TreeCanvas {
	public static final int R = 12;
	public static final int Y_MIN = -8;
	public static final int Y_MAX = 44;
	public static final int W = 2 * R + 1;
	public static final int HT = Y_MAX - Y_MIN + 1;

	public static final int AXIS_X = 0;
	public static final int AXIS_Y = 1;
	public static final int AXIS_Z = 2;

	/** Leaves farther than this (steps through leaves) from any log would decay in the game. */
	public static final int MAX_LEAF_DISTANCE = 6;

	private static final int UNKNOWN = Integer.MAX_VALUE - 1;

	private final TreeMat[] mat = new TreeMat[W * W * HT];
	private final byte[] aux = new byte[W * W * HT];
	private final boolean[] root = new boolean[W * W * HT];
	private final boolean[] core = new boolean[W * W * HT];
	private final int[] groundCache = new int[W * W];
	private final int noiseSeed;

	public final Random rnd;
	public final TreeSite site;
	/** Ends of the branches grown by {@link TreeShapes#branch}: x, y, z, depth, azimuth, elevation, length. */
	public final List<double[]> tips = new ArrayList<>();
	/** Points along grown branches (second half of every branch, every other step): x, y, z, depth, progress. */
	public final List<double[]> nodes = new ArrayList<>();

	public TreeCanvas(Random rnd, TreeSite site) {
		this.rnd = rnd;
		this.site = site;
		this.noiseSeed = rnd.nextInt();
		java.util.Arrays.fill(this.groundCache, UNKNOWN);
	}

	// ---- addressing

	public static boolean inside(int x, int y, int z) {
		return x >= -R && x <= R && z >= -R && z <= R && y >= Y_MIN && y <= Y_MAX;
	}

	private static int idx(int x, int y, int z) {
		return ((y - Y_MIN) * W + (z + R)) * W + (x + R);
	}

	public TreeMat get(int x, int y, int z) {
		return inside(x, y, z) ? this.mat[idx(x, y, z)] : null;
	}

	/** Log axis (AXIS_*), leaf distance to the nearest log, or fungus direction (0 north, 1 east, 2 south, 3 west). */
	public int aux(int x, int y, int z) {
		return inside(x, y, z) ? this.aux[idx(x, y, z)] : 0;
	}

	/** Voxels that may replace ground blocks (roots, the foot of the trunk). */
	public boolean isRoot(int x, int y, int z) {
		return inside(x, y, z) && this.root[idx(x, y, z)];
	}

	public boolean isLog(int x, int y, int z) {
		TreeMat m = get(x, y, z);
		return m != null && m.isLog();
	}

	public boolean isEmpty(int x, int y, int z) {
		return inside(x, y, z) && this.mat[idx(x, y, z)] == null;
	}

	// ---- ground

	/** Cached {@link TreeSite#groundAt}; {@link TreeSite#NO_GROUND} outside the canvas. */
	public int ground(int x, int z) {
		if (x < -R || x > R || z < -R || z > R) {
			return TreeSite.NO_GROUND;
		}
		int i = (z + R) * W + (x + R);
		if (this.groundCache[i] == UNKNOWN) {
			this.groundCache[i] = this.site.groundAt(x, z);
		}
		return this.groundCache[i];
	}

	// ---- writing

	/** Places a log. It replaces leaves, vines and fungi, never another log. Returns false when nothing was written. */
	public boolean log(int x, int y, int z, int axis, TreeMat m) {
		return log(x, y, z, axis, m, false, false);
	}

	public boolean log(int x, int y, int z, int axis, TreeMat m, boolean rootVoxel, boolean coreVoxel) {
		if (!inside(x, y, z)) {
			return false;
		}
		int i = idx(x, y, z);
		TreeMat old = this.mat[i];
		if (old != null && old.isLog()) {
			if (rootVoxel) {
				this.root[i] = true;
			}
			if (coreVoxel) {
				this.core[i] = true;
			}
			return false;
		}
		this.mat[i] = m;
		this.aux[i] = (byte) axis;
		this.root[i] = rootVoxel;
		this.core[i] = coreVoxel;
		return true;
	}

	/** Places a leaf in an empty voxel. */
	public boolean leaf(int x, int y, int z, TreeMat m) {
		if (!inside(x, y, z)) {
			return false;
		}
		int i = idx(x, y, z);
		if (this.mat[i] != null) {
			return false;
		}
		this.mat[i] = m;
		this.aux[i] = 1;
		return true;
	}

	public boolean put(int x, int y, int z, TreeMat m, int auxValue) {
		if (!inside(x, y, z) || this.mat[idx(x, y, z)] != null) {
			return false;
		}
		int i = idx(x, y, z);
		this.mat[i] = m;
		this.aux[i] = (byte) auxValue;
		return true;
	}

	private void clear(int i) {
		this.mat[i] = null;
		this.aux[i] = 0;
		this.root[i] = false;
		this.core[i] = false;
	}

	// ---- noise

	/** Smooth value noise in [-1, 1] with features of about {@code scale} blocks. */
	public double noise(double x, double y, double z, double scale) {
		double fx = x / scale;
		double fy = y / scale;
		double fz = z / scale;
		int ix = (int) Math.floor(fx);
		int iy = (int) Math.floor(fy);
		int iz = (int) Math.floor(fz);
		double tx = smooth(fx - ix);
		double ty = smooth(fy - iy);
		double tz = smooth(fz - iz);
		double v000 = lattice(ix, iy, iz);
		double v100 = lattice(ix + 1, iy, iz);
		double v010 = lattice(ix, iy + 1, iz);
		double v110 = lattice(ix + 1, iy + 1, iz);
		double v001 = lattice(ix, iy, iz + 1);
		double v101 = lattice(ix + 1, iy, iz + 1);
		double v011 = lattice(ix, iy + 1, iz + 1);
		double v111 = lattice(ix + 1, iy + 1, iz + 1);
		double a = lerp(lerp(v000, v100, tx), lerp(v010, v110, tx), ty);
		double b = lerp(lerp(v001, v101, tx), lerp(v011, v111, tx), ty);
		return lerp(a, b, tz);
	}

	private double lattice(int x, int y, int z) {
		int h = this.noiseSeed;
		h ^= x * 0x27d4eb2d;
		h = Integer.rotateLeft(h, 13) * 0x165667b1;
		h ^= y * 0x9e3779b1;
		h = Integer.rotateLeft(h, 11) * 0x85ebca6b;
		h ^= z * 0xc2b2ae35;
		h = Integer.rotateLeft(h, 15) * 0x27d4eb2f;
		h ^= h >>> 15;
		h *= 0x2c1b3c6d;
		h ^= h >>> 12;
		return ((h & 0xFFFF) / 32767.5) - 1.0;
	}

	/** Stable pseudo-random number in [0, 1) for a pair of integers (does not consume the random generator). */
	public double hash01(int a, int b) {
		int h = this.noiseSeed ^ (a * 0x9e3779b1);
		h = Integer.rotateLeft(h, 13) * 0x85ebca6b ^ (b * 0xc2b2ae35);
		h ^= h >>> 16;
		h *= 0x7feb352d;
		h ^= h >>> 15;
		return (h & 0xFFFFFF) / (double) 0x1000000;
	}

	private static double smooth(double t) {
		return t * t * (3 - 2 * t);
	}

	private static double lerp(double a, double b, double t) {
		return a + (b - a) * t;
	}

	// ---- finishing

	/**
	 * Removes everything that cannot exist: voxels the site refuses, parts cut off from the trunk, leaves out of
	 * reach of a log, vines and fungi without support. Returns false when the tree cannot stand here (the trunk is
	 * blocked), in which case nothing should be placed.
	 */
	public boolean finish() {
		// 1. the foot of the trunk follows the ground: fill the columns under raised trunk voxels
		anchorFoot();

		// 2. blocked voxels; the trunk must be clear
		for (int y = Y_MIN; y <= Y_MAX; y++) {
			for (int z = -R; z <= R; z++) {
				for (int x = -R; x <= R; x++) {
					int i = idx(x, y, z);
					if (this.mat[i] == null) {
						continue;
					}
					int g = ground(x, z);
					boolean underground = g != TreeSite.NO_GROUND && y < g;
					if (underground) {
						if (!this.root[i] && !this.core[i]) {
							clear(i);
						}
						continue;
					}
					if (!this.site.canReplace(x, y, z)) {
						if (this.core[i]) {
							return false;
						}
						clear(i);
					}
				}
			}
		}

		// 3. only logs connected to the ground survive (a log touches the ground when it is not above the first free block)
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		boolean[] seen = new boolean[this.mat.length];
		for (int y = Y_MIN; y <= Y_MAX; y++) {
			for (int z = -R; z <= R; z++) {
				for (int x = -R; x <= R; x++) {
					int i = idx(x, y, z);
					if (this.mat[i] == null || !this.mat[i].isLog()) {
						continue;
					}
					int g = ground(x, z);
					if (g != TreeSite.NO_GROUND && y <= g) {
						seen[i] = true;
						queue.add(new int[] { x, y, z });
					}
				}
			}
		}
		if (queue.isEmpty()) {
			return false;
		}
		while (!queue.isEmpty()) {
			int[] p = queue.poll();
			for (int d = 0; d < 6; d++) {
				int nx = p[0] + DX[d];
				int ny = p[1] + DY[d];
				int nz = p[2] + DZ[d];
				if (!inside(nx, ny, nz)) {
					continue;
				}
				int j = idx(nx, ny, nz);
				if (!seen[j] && this.mat[j] != null && this.mat[j].isLog()) {
					seen[j] = true;
					queue.add(new int[] { nx, ny, nz });
				}
			}
		}
		for (int i = 0; i < this.mat.length; i++) {
			if (this.mat[i] != null && this.mat[i].isLog() && !seen[i]) {
				clear(i);
			}
		}

		// 4. leaves: breadth-first distance from the logs through leaves
		int[] dist = new int[this.mat.length];
		ArrayDeque<int[]> front = new ArrayDeque<>();
		for (int y = Y_MIN; y <= Y_MAX; y++) {
			for (int z = -R; z <= R; z++) {
				for (int x = -R; x <= R; x++) {
					int i = idx(x, y, z);
					if (this.mat[i] != null && this.mat[i].isLog()) {
						front.add(new int[] { x, y, z });
					}
				}
			}
		}
		while (!front.isEmpty()) {
			int[] p = front.poll();
			int i = idx(p[0], p[1], p[2]);
			int dHere = dist[i];
			if (dHere >= MAX_LEAF_DISTANCE) {
				continue;
			}
			for (int d = 0; d < 6; d++) {
				int nx = p[0] + DX[d];
				int ny = p[1] + DY[d];
				int nz = p[2] + DZ[d];
				if (!inside(nx, ny, nz)) {
					continue;
				}
				int j = idx(nx, ny, nz);
				if (this.mat[j] != null && this.mat[j].isLeaf() && dist[j] == 0) {
					dist[j] = dHere + 1;
					front.add(new int[] { nx, ny, nz });
				}
			}
		}
		for (int i = 0; i < this.mat.length; i++) {
			if (this.mat[i] != null && this.mat[i].isLeaf()) {
				if (dist[i] == 0) {
					clear(i);
				} else {
					this.aux[i] = (byte) dist[i];
				}
			}
		}

		// 5. vines hang from something, fungi sit on a log
		for (int y = Y_MAX; y >= Y_MIN; y--) {
			for (int z = -R; z <= R; z++) {
				for (int x = -R; x <= R; x++) {
					int i = idx(x, y, z);
					TreeMat m = this.mat[i];
					if (m == TreeMat.VINE) {
						TreeMat above = get(x, y + 1, z);
						if (above == null || above == TreeMat.FUNGUS) {
							clear(i);
						}
					} else if (m == TreeMat.FUNGUS) {
						int dir = this.aux[i];
						if (!isLog(x - STEP_X[dir], y, z - STEP_Z[dir])) {
							clear(i);
						}
					}
				}
			}
		}
		return true;
	}

	/** Fills the columns below trunk voxels at the foot, so that a trunk on a slope reaches the ground on every side. */
	private void anchorFoot() {
		for (int y = 0; y <= 1; y++) {
			for (int z = -R; z <= R; z++) {
				for (int x = -R; x <= R; x++) {
					int i = idx(x, y, z);
					if (this.mat[i] == null || !this.mat[i].isLog() || !this.core[i]) {
						continue;
					}
					int g = ground(x, z);
					if (g == TreeSite.NO_GROUND) {
						continue;
					}
					for (int yy = Math.max(g, y - 6); yy < y; yy++) {
						log(x, yy, z, AXIS_Y, this.mat[i], true, true);
					}
				}
			}
		}
	}

	static final int[] DX = { 1, -1, 0, 0, 0, 0 };
	static final int[] DY = { 0, 0, 1, -1, 0, 0 };
	static final int[] DZ = { 0, 0, 0, 0, 1, -1 };
	/** Direction index 0 north (-z), 1 east (+x), 2 south (+z), 3 west (-x): the step a fungus takes away from its log. */
	public static final int[] STEP_X = { 0, 1, 0, -1 };
	public static final int[] STEP_Z = { -1, 0, 1, 0 };

	// ---- queries for the game layer

	public interface VoxelVisitor {
		void visit(int x, int y, int z, TreeMat mat, int aux, boolean root);
	}

	/** Visits every voxel in a stable order: logs first (bottom up), then leaves, vines and fungi. */
	public void forEach(VoxelVisitor visitor) {
		for (int pass = 0; pass < 3; pass++) {
			for (int y = Y_MIN; y <= Y_MAX; y++) {
				for (int z = -R; z <= R; z++) {
					for (int x = -R; x <= R; x++) {
						int i = idx(x, y, z);
						TreeMat m = this.mat[i];
						if (m == null) {
							continue;
						}
						int p = m.isLog() ? 0 : m.isLeaf() ? 1 : 2;
						if (p == pass) {
							visitor.visit(x, y, z, m, this.aux[i], this.root[i]);
						}
					}
				}
			}
		}
	}

	public int count(TreeMat.Kind kind) {
		int n = 0;
		for (TreeMat m : this.mat) {
			if (m != null && m.kind == kind) {
				n++;
			}
		}
		return n;
	}

	/** Highest voxel of any kind, or Y_MIN - 1 when the canvas is empty. */
	public int topY() {
		for (int y = Y_MAX; y >= Y_MIN; y--) {
			for (int z = -R; z <= R; z++) {
				for (int x = -R; x <= R; x++) {
					if (this.mat[idx(x, y, z)] != null) {
						return y;
					}
				}
			}
		}
		return Y_MIN - 1;
	}
}
