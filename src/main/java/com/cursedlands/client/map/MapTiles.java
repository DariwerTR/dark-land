package com.cursedlands.client.map;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import com.cursedlands.world.MapData;

/**
 * Pre-coloured picture of the map cut into square tiles on several zoom levels. A pixel of level {@code L} covers
 * {@code 2^L} x {@code 2^L} blocks. Tiles are painted on demand by background threads and kept in memory, the coarse
 * levels for good (they make the whole map cheap to show), the detailed ones in a limited cache.
 *
 * <p>Pixels are opaque ARGB. Coordinates of tiles and pixels are counted from the north-west corner of the map.
 */
public final class MapTiles {
	public static final int TILE = 128;
	public static final int LEVELS = 5;
	/** First level whose tiles are never dropped. */
	private static final int PERMANENT_FROM = 2;
	/** Levels queued at start: the whole map costs 45 tiles at these. */
	private static final int WARM_FROM = 3;
	private static final int DETAIL_TILES_MAX = 720;
	/** Blocks to the neighbours used for shading at full detail: 1 would draw every step of the terrain as a contour line. */
	private static final int DETAIL_SHADE_DISTANCE = 3;

	private final MapData map;
	private final MapColors colors;
	private final int[] tilesPerSide = new int[LEVELS];
	private final Map<Long, int[]> permanent = new ConcurrentHashMap<>();
	private final Map<Long, int[]> detail;
	private final Set<Long> pending = ConcurrentHashMap.newKeySet();
	private final Set<Long> warming = ConcurrentHashMap.newKeySet();
	private final AtomicLong sequence = new AtomicLong();
	private final ThreadPoolExecutor workers;

	public MapTiles(MapData map, int threads) {
		this.map = map;
		this.colors = new MapColors(map);
		for (int level = 0; level < LEVELS; level++) {
			tilesPerSide[level] = (map.size + (TILE << level) - 1) / (TILE << level);
		}
		detail = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
			@Override
			protected boolean removeEldestEntry(Map.Entry<Long, int[]> eldest) {
				return size() > DETAIL_TILES_MAX;
			}
		});
		workers = threads <= 0 ? null : new ThreadPoolExecutor(threads, threads, 0L, TimeUnit.MILLISECONDS, new PriorityBlockingQueue<>(), runnable -> {
			Thread thread = new Thread(runnable, "cursed-lands-map-tiles");
			thread.setDaemon(true);
			thread.setPriority(Thread.MIN_PRIORITY + 1);
			return thread;
		});
	}

	public int tilesPerSide(int level) {
		return tilesPerSide[level];
	}

	public MapData map() {
		return map;
	}

	public void close() {
		if (workers != null) {
			workers.shutdownNow();
		}
		permanent.clear();
		detail.clear();
		pending.clear();
		warming.clear();
	}

	/** The painted tile, or {@code null} if it is not ready yet (then it is queued for painting). */
	public int[] peek(int level, int tx, int tz) {
		if (tx < 0 || tz < 0 || tx >= tilesPerSide[level] || tz >= tilesPerSide[level]) {
			return null;
		}
		long key = key(level, tx, tz);
		int[] tile = level >= PERMANENT_FROM ? permanent.get(key) : detail.get(key);
		if (tile == null) {
			schedule(level, tx, tz, key, false);
		}
		return tile;
	}

	/** The tile without queueing, for fallbacks. */
	public int[] peekQuiet(int level, int tx, int tz) {
		if (tx < 0 || tz < 0 || tx >= tilesPerSide[level] || tz >= tilesPerSide[level]) {
			return null;
		}
		long key = key(level, tx, tz);
		return level >= PERMANENT_FROM ? permanent.get(key) : detail.get(key);
	}

	/** The tile, painted right now on the calling thread if it is not ready. */
	public int[] get(int level, int tx, int tz) {
		int[] tile = peekQuiet(level, tx, tz);
		if (tile == null && tx >= 0 && tz >= 0 && tx < tilesPerSide[level] && tz < tilesPerSide[level]) {
			tile = paint(level, tx, tz);
			store(level, key(level, tx, tz), tile);
		}
		return tile;
	}

	/** Queues all tiles of the coarsest levels (the whole map at a glance) behind anything the screen asks for. */
	public void warmUp() {
		for (int level = LEVELS - 1; level >= WARM_FROM; level--) {
			for (int tz = 0; tz < tilesPerSide[level]; tz++) {
				for (int tx = 0; tx < tilesPerSide[level]; tx++) {
					if (peekQuiet(level, tx, tz) == null) {
						schedule(level, tx, tz, key(level, tx, tz), true);
					}
				}
			}
		}
	}

	private void schedule(int level, int tx, int tz, long key, boolean background) {
		if (workers == null) {
			return;
		}
		if (background) {
			if (!pending.add(key)) {
				return;
			}
			warming.add(key);
		} else if (!pending.add(key) && !warming.remove(key)) {
			// already queued for the screen; a tile that waits in the background queue is asked for again, now with priority
			return;
		}
		try {
			workers.execute(new Job(level, tx, tz, key, background ? 1 : 0, sequence.incrementAndGet()));
		} catch (RejectedExecutionException e) {
			pending.remove(key);
			warming.remove(key);
		}
	}

	/** Painting of one tile. The screen's requests go first, the newest first (the player has moved on), background ones in order. */
	private final class Job implements Runnable, Comparable<Job> {
		private final int level;
		private final int tx;
		private final int tz;
		private final long key;
		private final int priority;
		private final long order;

		Job(int level, int tx, int tz, long key, int priority, long order) {
			this.level = level;
			this.tx = tx;
			this.tz = tz;
			this.key = key;
			this.priority = priority;
			this.order = order;
		}

		@Override
		public void run() {
			try {
				if (peekQuiet(level, tx, tz) == null) {
					store(level, key, paint(level, tx, tz));
				}
			} catch (RuntimeException e) {
				// a broken tile must not take the thread down; it stays unavailable and is queued again when the screen asks for it
			} finally {
				warming.remove(key);
				pending.remove(key);
			}
		}

		@Override
		public int compareTo(Job other) {
			if (priority != other.priority) {
				return Integer.compare(priority, other.priority);
			}
			return priority == 0 ? Long.compare(other.order, order) : Long.compare(order, other.order);
		}
	}

	private void store(int level, long key, int[] tile) {
		if (level >= PERMANENT_FROM) {
			permanent.put(key, tile);
		} else {
			detail.put(key, tile);
		}
	}

	private static long key(int level, int tx, int tz) {
		return ((long) level << 40) | ((long) tx << 20) | tz;
	}

	private int[] paint(int level, int tx, int tz) {
		int[] pixels = new int[TILE * TILE];
		int scale = 1 << level;
		int step = Math.max(1, scale / 2);
		int originX = map.originX;
		int originZ = map.originZ;
		for (int j = 0; j < TILE; j++) {
			int wz = originZ + (tz * TILE + j) * scale;
			for (int i = 0; i < TILE; i++) {
				int wx = originX + (tx * TILE + i) * scale;
				if (level == 0) {
					pixels[j * TILE + i] = colors.cell(wx, wz, DETAIL_SHADE_DISTANCE);
				} else {
					int r = 0;
					int g = 0;
					int b = 0;
					for (int a = 0; a < 2; a++) {
						for (int c = 0; c < 2; c++) {
							int color = colors.cell(wx + c * step, wz + a * step, step);
							r += (color >> 16) & 0xFF;
							g += (color >> 8) & 0xFF;
							b += color & 0xFF;
						}
					}
					pixels[j * TILE + i] = 0xFF000000 | ((r >> 2) << 16) | ((g >> 2) << 8) | (b >> 2);
				}
			}
		}
		return pixels;
	}
}
