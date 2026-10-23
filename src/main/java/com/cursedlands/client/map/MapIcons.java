package com.cursedlands.client.map;

/** Draws small markers straight into the pixels of a painted map window. Pixels that are transparent (outside a round window) are left alone. */
public final class MapIcons {
	public static final int PLAYER = 0xFFF4E9B8;
	public static final int PLAYER_OUTLINE = 0xFF120E14;
	public static final int PIN = 0xFFD9323C;
	public static final int PIN_OUTLINE = 0xFFFFF2F2;
	public static final int SPAWN = 0xFFB9E6A0;
	public static final int SPAWN_OUTLINE = 0xFF10200C;

	// The arrow points up: tip, right wing, notch, left wing (unit size)
	private static final double[] ARROW_X = {0.0, 0.72, 0.0, -0.72};
	private static final double[] ARROW_Y = {-1.0, 0.85, 0.42, 0.85};

	private MapIcons() {
	}

	/**
	 * Arrow showing where the player looks.
	 *
	 * @param dirX direction on the screen (x to the right, y down), need not be a unit vector
	 */
	public static void arrow(int[] px, int width, int height, double cx, double cy, double dirX, double dirY, double size, int fill, int outline) {
		double length = Math.sqrt(dirX * dirX + dirY * dirY);
		if (length < 1.0e-6) {
			dirX = 0;
			dirY = -1;
		} else {
			dirX /= length;
			dirY /= length;
		}
		polygon(px, width, height, cx, cy, dirX, dirY, size * 1.42, outline);
		polygon(px, width, height, cx, cy, dirX, dirY, size, fill);
	}

	/** Filled diamond with a one pixel outline. */
	public static void diamond(int[] px, int width, int height, double cx, double cy, double radius, int fill, int outline) {
		diamondShape(px, width, height, cx, cy, radius + 1.2, outline);
		diamondShape(px, width, height, cx, cy, radius, fill);
	}

	/** Diamond ring: the map shows through the middle. */
	public static void ring(int[] px, int width, int height, double cx, double cy, double radius, int color, int outline) {
		double outer = radius + 1.2;
		for (int y = (int) Math.floor(cy - outer); y <= (int) Math.ceil(cy + outer); y++) {
			for (int x = (int) Math.floor(cx - outer); x <= (int) Math.ceil(cx + outer); x++) {
				double d = Math.abs(x + 0.5 - cx) + Math.abs(y + 0.5 - cy);
				if (d > outer || d <= radius - 3.2) {
					continue;
				}
				put(px, width, height, x, y, d > radius || d <= radius - 2.0 ? outline : color);
			}
		}
	}

	/**
	 * Point of a window's rim on the line from its centre to {@code (x, y)}, for markers that lie outside the window.
	 *
	 * @return {@code {x, y}} of the point at the rim, moved {@code inset} pixels inwards; the point itself if it is already inside
	 */
	public static double[] toRim(int width, int height, boolean round, double x, double y, double inset) {
		double cx = width / 2.0;
		double cy = height / 2.0;
		double dx = x - cx;
		double dy = y - cy;
		double reachX = cx - inset;
		double reachY = cy - inset;
		double scale;
		if (round) {
			double reach = Math.min(reachX, reachY);
			double d = Math.sqrt(dx * dx + dy * dy);
			scale = d <= reach || d == 0 ? 1.0 : reach / d;
		} else {
			double sx = Math.abs(dx) <= reachX || dx == 0 ? 1.0 : reachX / Math.abs(dx);
			double sy = Math.abs(dy) <= reachY || dy == 0 ? 1.0 : reachY / Math.abs(dy);
			scale = Math.min(sx, sy);
		}
		return new double[]{cx + dx * scale, cy + dy * scale};
	}

	private static void polygon(int[] px, int width, int height, double cx, double cy, double dirX, double dirY, double size, int color) {
		// rotate the unit arrow (pointing up) so that its tip points along (dirX, dirY)
		double rx = -dirY;
		double ry = dirX;
		int n = ARROW_X.length;
		double[] xs = new double[n];
		double[] ys = new double[n];
		double minX = Double.MAX_VALUE;
		double maxX = -Double.MAX_VALUE;
		double minY = Double.MAX_VALUE;
		double maxY = -Double.MAX_VALUE;
		for (int i = 0; i < n; i++) {
			// local x is to the right of the arrow, local y is towards the back (opposite to the tip)
			double lx = ARROW_X[i] * size;
			double ly = ARROW_Y[i] * size;
			xs[i] = cx + rx * lx - dirX * ly;
			ys[i] = cy + ry * lx - dirY * ly;
			minX = Math.min(minX, xs[i]);
			maxX = Math.max(maxX, xs[i]);
			minY = Math.min(minY, ys[i]);
			maxY = Math.max(maxY, ys[i]);
		}
		for (int y = (int) Math.floor(minY); y <= (int) Math.ceil(maxY); y++) {
			for (int x = (int) Math.floor(minX); x <= (int) Math.ceil(maxX); x++) {
				if (inside(xs, ys, x + 0.5, y + 0.5)) {
					put(px, width, height, x, y, color);
				}
			}
		}
	}

	/** Even-odd test. */
	private static boolean inside(double[] xs, double[] ys, double x, double y) {
		boolean in = false;
		for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
			if ((ys[i] > y) != (ys[j] > y) && x < (xs[j] - xs[i]) * (y - ys[i]) / (ys[j] - ys[i]) + xs[i]) {
				in = !in;
			}
		}
		return in;
	}

	private static void diamondShape(int[] px, int width, int height, double cx, double cy, double radius, int color) {
		for (int y = (int) Math.floor(cy - radius); y <= (int) Math.ceil(cy + radius); y++) {
			for (int x = (int) Math.floor(cx - radius); x <= (int) Math.ceil(cx + radius); x++) {
				if (Math.abs(x + 0.5 - cx) + Math.abs(y + 0.5 - cy) <= radius) {
					put(px, width, height, x, y, color);
				}
			}
		}
	}

	private static void put(int[] px, int width, int height, int x, int y, int color) {
		if (x < 0 || y < 0 || x >= width || y >= height) {
			return;
		}
		int i = y * width + x;
		if ((px[i] >>> 24) != 0) {
			px[i] = color;
		}
	}
}
