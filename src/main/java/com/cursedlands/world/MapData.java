package com.cursedlands.world;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * Raster data of the finite Cursed Lands map. This class knows nothing about Minecraft: it only reads the
 * files that tools/map_factory writes into {@code cursed_lands_map/} inside the jar.
 *
 * <p>Layers (all in one coordinate system, pixel (0,0) is the north-west corner of the map):
 * <ul>
 *   <li>{@code heights}  int16, 1 cell per block: Y of the topmost solid block</li>
 *   <li>{@code water}    int16, 1 cell per block: Y of the water surface, 0 where there is no water</li>
 *   <li>{@code surface}  uint8, 1 cell per block: surface kind, see {@link MapChunkGenerator}</li>
 *   <li>{@code biomes}   uint8, 1 cell per 4x4 blocks: index into {@link #biomes}</li>
 * </ul>
 *
 * <p>Layer file ("CLM1", big endian): magic, type (1 = uint8, 2 = int16), pad, tile size in cells (u16),
 * width, height (u32), then one (offset, length) u32 pair per tile (row by row), then the tiles.
 * A tile is a zlib stream of tile*tile cells, row by row; int16 tiles are stored as two byte planes
 * (all high bytes, then all low bytes) of the 2D difference (delta along x, then along z). A tile of length 0 is all zeros.
 */
public final class MapData {
	public static final String RESOURCE_DIR = "/cursed_lands_map/";
	public static final int FORMAT = 1;

	private static volatile MapData instance;

	/** Side of the map in blocks. */
	public final int size;
	/** World coordinates of the north-west corner (pixel 0,0). Multiples of 4, so that biome cells line up. */
	public final int originX;
	public final int originZ;
	public final int seaLevel;
	/** Distance from the map edge (in blocks) at which the fog wall turns players back. */
	public final int borderMargin;
	/** Biome path (in the cursed_lands namespace) for every index of the biome layer. */
	public final List<String> biomes;

	private final Layer heights;
	private final Layer water;
	private final Layer surface;
	private final Layer biomeCells;

	public static MapData get() {
		MapData data = instance;
		if (data == null) {
			synchronized (MapData.class) {
				data = instance;
				if (data == null) {
					data = load(name -> {
						try (InputStream in = MapData.class.getResourceAsStream(RESOURCE_DIR + name)) {
							if (in == null) {
								throw new IllegalStateException("Cursed Lands map file is missing from the mod jar: " + RESOURCE_DIR + name);
							}
							return in.readAllBytes();
						} catch (IOException e) {
							throw new UncheckedIOException(e);
						}
					});
					instance = data;
				}
			}
		}
		return data;
	}

	/** Loads the map through the given reader (file name inside the map directory to its bytes). */
	public static MapData load(Function<String, byte[]> reader) {
		return new MapData(reader);
	}

	private MapData(Function<String, byte[]> reader) {
		JsonObject meta = JsonParser.parseReader(new InputStreamReader(
				new java.io.ByteArrayInputStream(reader.apply("meta.json")), StandardCharsets.UTF_8)).getAsJsonObject();
		int format = meta.get("format").getAsInt();
		if (format != FORMAT) {
			throw new IllegalStateException("Unsupported Cursed Lands map format " + format + ", expected " + FORMAT);
		}
		size = meta.get("size").getAsInt();
		originX = meta.get("origin_x").getAsInt();
		originZ = meta.get("origin_z").getAsInt();
		seaLevel = meta.get("sea_level").getAsInt();
		borderMargin = meta.get("border_margin").getAsInt();
		if (size % 4 != 0 || originX % 4 != 0 || originZ % 4 != 0) {
			throw new IllegalStateException("Map size and origin must be multiples of 4");
		}
		List<String> names = new ArrayList<>();
		JsonArray array = meta.getAsJsonArray("biomes");
		for (JsonElement element : array) {
			names.add(element.getAsString());
		}
		biomes = Collections.unmodifiableList(names);

		heights = new Layer("heights", reader.apply("heights.bin"), 2, size, 48);
		water = new Layer("water", reader.apply("water.bin"), 2, size, 48);
		surface = new Layer("surface", reader.apply("surface.bin"), 1, size, 96);
		biomeCells = new Layer("biomes", reader.apply("biomes.bin"), 1, size / 4, 64);
	}

	/** True if the block column lies on the map. */
	public boolean inside(int x, int z) {
		int px = x - originX;
		int pz = z - originZ;
		return px >= 0 && pz >= 0 && px < size && pz < size;
	}

	/** Y of the topmost solid block. Outside the map the nearest edge column is used. */
	public int ground(int x, int z) {
		return heights.get(x - originX, z - originZ);
	}

	/** Y of the water surface, 0 if the column is dry. */
	public int water(int x, int z) {
		return water.get(x - originX, z - originZ);
	}

	/** Surface kind of the column (see MapChunkGenerator). */
	public int surface(int x, int z) {
		return surface.get(x - originX, z - originZ);
	}

	/** Biome index for the 4x4 cell given in quart coordinates (block coordinate shifted right by 2). */
	public int biome(int quartX, int quartZ) {
		return biomeCells.get(quartX - (originX >> 2), quartZ - (originZ >> 2));
	}

	/** Distance in blocks from the nearest map edge, negative outside the map. */
	public double edgeDistance(double x, double z) {
		double px = x - originX;
		double pz = z - originZ;
		return Math.min(Math.min(px, size - px), Math.min(pz, size - pz));
	}

	/** One tiled raster layer. */
	private static final class Layer {
		private static final int MAGIC = 0x434C4D31; // "CLM1"
		private static final int CACHE_TILES_MAX_BYTES = 8 * 1024 * 1024;

		private final String name;
		private final boolean wide;
		private final int tile;
		private final int width;
		private final int height;
		private final int tilesX;
		private final byte[] file;
		private final int[] offsets;
		private final int[] lengths;
		private final int maxTiles;
		private final Map<Integer, Object> cache;

		Layer(String name, byte[] file, int expectedType, int expectedSize, int cacheTiles) {
			this.name = name;
			this.file = file;
			if (file.length < 16) {
				throw new IllegalStateException(name + ": file too short");
			}
			if (readInt(file, 0) != MAGIC) {
				throw new IllegalStateException(name + ": bad magic");
			}
			int type = file[4] & 0xFF;
			if (type != expectedType) {
				throw new IllegalStateException(name + ": cell type " + type + ", expected " + expectedType);
			}
			wide = type == 2;
			tile = ((file[6] & 0xFF) << 8) | (file[7] & 0xFF);
			width = readInt(file, 8);
			height = readInt(file, 12);
			if (width != expectedSize || height != expectedSize) {
				throw new IllegalStateException(name + ": " + width + "x" + height + " cells, expected " + expectedSize);
			}
			tilesX = (width + tile - 1) / tile;
			int tilesZ = (height + tile - 1) / tile;
			offsets = new int[tilesX * tilesZ];
			lengths = new int[tilesX * tilesZ];
			for (int i = 0; i < offsets.length; i++) {
				offsets[i] = readInt(file, 16 + i * 8);
				lengths[i] = readInt(file, 20 + i * 8);
				if (lengths[i] < 0 || offsets[i] < 0 || (long) offsets[i] + lengths[i] > file.length) {
					throw new IllegalStateException(name + ": tile " + i + " points outside the file");
				}
			}
			int tileBytes = tile * tile * (wide ? 2 : 1);
			// Tiles are decoded on demand and kept in a small LRU cache, the compressed file stays in memory.
			maxTiles = Math.max(8, Math.min(cacheTiles, CACHE_TILES_MAX_BYTES * 4 / tileBytes));
			cache = new LinkedHashMap<>(64, 0.75f, true) {
				@Override
				protected boolean removeEldestEntry(Map.Entry<Integer, Object> eldest) {
					return size() > maxTiles;
				}
			};
		}

		int get(int cx, int cz) {
			if (cx < 0) cx = 0; else if (cx >= width) cx = width - 1;
			if (cz < 0) cz = 0; else if (cz >= height) cz = height - 1;
			int index = (cz / tile) * tilesX + (cx / tile);
			int local = (cz % tile) * tile + (cx % tile);
			Object data = tile(index);
			return wide ? ((short[]) data)[local] : ((byte[]) data)[local] & 0xFF;
		}

		private Object tile(int index) {
			synchronized (cache) {
				Object cached = cache.get(index);
				if (cached != null) {
					return cached;
				}
			}
			Object decoded = decode(index);
			synchronized (cache) {
				cache.put(index, decoded);
			}
			return decoded;
		}

		private Object decode(int index) {
			int cells = tile * tile;
			if (lengths[index] == 0) {
				return wide ? new short[cells] : new byte[cells];
			}
			byte[] raw = new byte[cells * (wide ? 2 : 1)];
			Inflater inflater = new Inflater();
			try {
				inflater.setInput(file, offsets[index], lengths[index]);
				int done = 0;
				while (done < raw.length) {
					int n = inflater.inflate(raw, done, raw.length - done);
					if (n == 0 && (inflater.finished() || inflater.needsInput() || inflater.needsDictionary())) {
						break;
					}
					done += n;
				}
				if (done != raw.length) {
					throw new IllegalStateException(name + ": tile " + index + " has " + done + " bytes, expected " + raw.length);
				}
			} catch (DataFormatException e) {
				throw new IllegalStateException(name + ": tile " + index + " is corrupt", e);
			} finally {
				inflater.end();
			}
			if (!wide) {
				return raw;
			}
			short[] values = new short[cells];
			for (int i = 0; i < cells; i++) {
				values[i] = (short) (((raw[i] & 0xFF) << 8) | (raw[cells + i] & 0xFF));
			}
			// undo the 2D difference: running sum along z, then along x (arithmetic wraps like int16)
			for (int z = 1; z < tile; z++) {
				for (int x = 0; x < tile; x++) {
					values[z * tile + x] += values[(z - 1) * tile + x];
				}
			}
			for (int z = 0; z < tile; z++) {
				for (int x = 1; x < tile; x++) {
					values[z * tile + x] += values[z * tile + x - 1];
				}
			}
			return values;
		}

		private static int readInt(byte[] b, int at) {
			return ((b[at] & 0xFF) << 24) | ((b[at + 1] & 0xFF) << 16) | ((b[at + 2] & 0xFF) << 8) | (b[at + 3] & 0xFF);
		}
	}
}
