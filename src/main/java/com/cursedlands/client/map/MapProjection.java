package com.cursedlands.client.map;

/**
 * Maps world coordinates (x east, z south) to a rectangle of screen pixels and back.
 *
 * <p>The map is centred on {@code (centerX, centerZ)}, one screen pixel covers {@code blocksPerPixel} blocks, and the
 * direction {@code (facingX, facingZ)} (a unit vector in the world) points to the top of the screen. North up is {@code (0, -1)}.
 */
public final class MapProjection {
	public final int width;
	public final int height;
	public final double centerX;
	public final double centerZ;
	public final double blocksPerPixel;
	public final double facingX;
	public final double facingZ;

	public MapProjection(int width, int height, double centerX, double centerZ, double blocksPerPixel, double facingX, double facingZ) {
		this.width = width;
		this.height = height;
		this.centerX = centerX;
		this.centerZ = centerZ;
		this.blocksPerPixel = blocksPerPixel;
		double length = Math.sqrt(facingX * facingX + facingZ * facingZ);
		if (length < 1.0e-6) {
			this.facingX = 0;
			this.facingZ = -1;
		} else {
			this.facingX = facingX / length;
			this.facingZ = facingZ / length;
		}
	}

	/** North at the top of the screen. */
	public static MapProjection northUp(int width, int height, double centerX, double centerZ, double blocksPerPixel) {
		return new MapProjection(width, height, centerX, centerZ, blocksPerPixel, 0, -1);
	}

	/** To the right of the top direction, in world coordinates. */
	private double rightX() {
		return -facingZ;
	}

	private double rightZ() {
		return facingX;
	}

	/** World x of the middle of the screen pixel {@code (sx, sy)} (fractions allowed). */
	public double worldX(double sx, double sy) {
		double dx = sx - width / 2.0;
		double dy = sy - height / 2.0;
		return centerX + (rightX() * dx - facingX * dy) * blocksPerPixel;
	}

	public double worldZ(double sx, double sy) {
		double dx = sx - width / 2.0;
		double dy = sy - height / 2.0;
		return centerZ + (rightZ() * dx - facingZ * dy) * blocksPerPixel;
	}

	/** Screen x of a world point. */
	public double screenX(double wx, double wz) {
		double ox = (wx - centerX) / blocksPerPixel;
		double oz = (wz - centerZ) / blocksPerPixel;
		return width / 2.0 + ox * rightX() + oz * rightZ();
	}

	public double screenY(double wx, double wz) {
		double ox = (wx - centerX) / blocksPerPixel;
		double oz = (wz - centerZ) / blocksPerPixel;
		return height / 2.0 - (ox * facingX + oz * facingZ);
	}
}
