package com.cursedlands.client.map;

import com.cursedlands.world.MapData;

/** Paints a window of the map into an array of ARGB pixels from the tiles of {@link MapTiles}. */
public final class MapView {
	/** Colour of the area not seen yet. */
	public static final int FOG = 0xFF0A0A0F;
	/** Colour of a spot whose tile is still being painted. */
	public static final int PENDING = 0xFF16141B;
	/** Outline of a round window. */
	public static final int RING = 0xFF14121A;

	private static final int TILE_SHIFT = 7;

	static {
		if (MapTiles.TILE != 1 << TILE_SHIFT) {
			throw new IllegalStateException("MapView expects tiles of " + (1 << TILE_SHIFT) + " pixels");
		}
	}

	private final MapTiles tiles;
	private final MapData map;

	public MapView(MapTiles tiles) {
		this.tiles = tiles;
		this.map = tiles.map();
	}

	/** Zoom level of the tiles that suit this scale: the coarsest one whose pixel is not larger than a screen pixel. */
	public static int levelFor(double blocksPerPixel) {
		int level = 0;
		while (level + 1 < MapTiles.LEVELS && (1 << (level + 1)) <= blocksPerPixel) {
			level++;
		}
		return level;
	}

	/**
	 * Paints the window.
	 *
	 * @param out    {@code width * height} pixels, row by row
	 * @param memory the explored area, or {@code null} to show the whole map
	 * @param round  paint a disc (pixels outside it become transparent, the rim gets an outline)
	 * @return true if every tile needed was ready (otherwise the picture has gaps and should be painted again later)
	 */
	public boolean render(int[] out, MapProjection view, MapMemory memory, boolean round) {
		int width = view.width;
		int height = view.height;
		int level = levelFor(view.blocksPerPixel);
		double bpp = view.blocksPerPixel;
		double rightX = -view.facingZ * bpp;
		double rightZ = view.facingX * bpp;
		double downX = -view.facingX * bpp;
		double downZ = -view.facingZ * bpp;
		double mapOriginX = map.originX;
		double mapOriginZ = map.originZ;
		int size = map.size;
		double radius = Math.min(width, height) / 2.0;
		double radius2 = radius * radius;
		double rim2 = (radius - 1.5) * (radius - 1.5);

		boolean ready = true;
		int cachedKey = -1;
		int cachedLevel = 0;
		int[] cachedTile = null;
		for (int y = 0; y < height; y++) {
			double dy = y + 0.5 - height / 2.0;
			double rowX = view.centerX - mapOriginX + downX * dy;
			double rowZ = view.centerZ - mapOriginZ + downZ * dy;
			for (int x = 0; x < width; x++) {
				double dx = x + 0.5 - width / 2.0;
				if (round) {
					double d2 = dx * dx + dy * dy;
					if (d2 > radius2) {
						out[y * width + x] = 0;
						continue;
					}
					if (d2 > rim2) {
						out[y * width + x] = RING;
						continue;
					}
				}
				double mx = rowX + rightX * dx;
				double mz = rowZ + rightZ * dx;
				int px = (int) Math.floor(mx);
				int pz = (int) Math.floor(mz);
				int color;
				if (px < 0 || pz < 0 || px >= size || pz >= size) {
					color = MapColors.OUTSIDE;
				} else {
					int lv = level;
					int tx = (px >> lv) >> TILE_SHIFT;
					int tz = (pz >> lv) >> TILE_SHIFT;
					int key = (lv << 24) | (tx << 12) | tz;
					if (key != cachedKey) {
						cachedTile = tiles.peek(lv, tx, tz);
						cachedKey = key;
						cachedLevel = lv;
						if (cachedTile == null) {
							// use a coarser tile as a stand-in until the right one is painted
							for (int coarser = lv + 1; coarser < MapTiles.LEVELS && cachedTile == null; coarser++) {
								cachedTile = tiles.peekQuiet(coarser, (px >> coarser) >> TILE_SHIFT, (pz >> coarser) >> TILE_SHIFT);
								if (cachedTile != null) {
									cachedLevel = coarser;
								}
							}
							ready = false;
						}
					}
					if (cachedTile == null) {
						color = PENDING;
					} else {
						int l = cachedLevel;
						int mask = MapTiles.TILE - 1;
						color = cachedTile[(((pz >> l) & mask) << TILE_SHIFT) | ((px >> l) & mask)];
					}
					if (memory != null) {
						float seen = memory.coverage(mx, mz);
						if (seen < 1f) {
							color = mix(FOG, color, seen);
						}
					}
				}
				out[y * width + x] = color;
			}
		}
		return ready;
	}

	/** Linear blend: {@code t} 0 gives {@code a}, 1 gives {@code b}. */
	static int mix(int a, int b, float t) {
		int r = ((a >> 16) & 0xFF) + (int) ((((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = ((a >> 8) & 0xFF) + (int) ((((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = (a & 0xFF) + (int) (((b & 0xFF) - (a & 0xFF)) * t);
		return 0xFF000000 | (r << 16) | (g << 8) | bl;
	}
}
