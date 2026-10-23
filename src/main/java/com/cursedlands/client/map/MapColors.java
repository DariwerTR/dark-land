package com.cursedlands.client.map;

import com.cursedlands.world.MapData;

/**
 * Colour of one map cell for the in-game map. Knows nothing about Minecraft: it only reads {@link MapData}.
 *
 * <p>The colour is the surface kind (tinted by the biome for grass), flooded cells take the water colour darkened by depth,
 * relief is shaded as if the light came from the north-west, the high peaks of the Spire Ridge are snow-capped, and the fog
 * biomes at the edge of the map fade towards fog. Cells outside the map are {@link #OUTSIDE}.
 */
public final class MapColors {
	/** Colour outside the map (opaque). */
	public static final int OUTSIDE = 0xFF050507;

	// Indices of the biome layer, same order as "biomes" in meta.json.
	private static final String[] BIOMES = {
			"cursed_lands", "dead_forest", "necropolis", "blood_canyon", "fog_far", "fog_edge", "fog_wall",
			"spire_peaks", "ash_wastes", "bone_valley", "blood_fen"
	};
	// Grass colour of the biome (as in the biome file) brightened so that it reads on a map.
	private static final int[] GRASS = {
			0x6c6a4c, 0x585b49, 0x5f6657, 0x7c4a3c, 0x6c6a4c, 0x6c6a4c, 0x6c6a4c,
			0x5f686c, 0x7a6a56, 0x827c64, 0x65603f
	};
	// Water colour of the biome (as in the biome file) brightened.
	private static final int[] WATER = {
			0x8a4a60, 0x655b83, 0x5f6c96, 0xb82a38, 0x8a4a60, 0x8a4a60, 0x8a4a60,
			0x626290, 0x806268, 0x66627e, 0x923a50
	};
	// Fog colour of the biome and how far the colours are pulled to it (0 none, 1 fully).
	private static final int[] FOG = {
			0, 0, 0, 0, 0x4a3a4c, 0x5a4a5e, 0x6e6074, 0, 0, 0, 0
	};
	private static final float[] FOG_AMOUNT = {
			0f, 0f, 0f, 0f, 0.28f, 0.50f, 0.74f, 0f, 0f, 0f, 0f
	};

	// Surface kinds of the surface layer (see MapChunkGenerator): grass, soil, gravel, sand, rock, path, mud, ash, coarse, deepslate.
	private static final int[] SURFACE = {
			-1, 0x7a5a3c, 0x8c8884, 0xc4b080, 0x7c7c82, 0xa08a5c, 0x54443a, 0x6a625e, 0x6e563a, 0x52525c
	};

	private static final int SNOW = 0xe4e8f0;
	private static final int SNOW_LINE = 205;
	private static final int SNOW_FULL = 245;
	private static final int SPIRE_PEAKS = 7;

	private final MapData map;

	public MapColors(MapData map) {
		this.map = map;
		if (map.biomes.size() != BIOMES.length) {
			throw new IllegalStateException("The map has " + map.biomes.size() + " biomes, the map colours know " + BIOMES.length);
		}
		for (int i = 0; i < BIOMES.length; i++) {
			if (!map.biomes.get(i).equals(BIOMES[i])) {
				throw new IllegalStateException("Biome " + i + " is " + map.biomes.get(i) + ", the map colours expect " + BIOMES[i]);
			}
		}
	}

	/**
	 * Colour of the block column.
	 *
	 * @param distance distance in blocks to the neighbours used for the relief shading (1 for full detail)
	 */
	public int cell(int x, int z, int distance) {
		if (!map.inside(x, z)) {
			return OUTSIDE;
		}
		int ground = map.ground(x, z);
		int water = map.water(x, z);
		int biome = map.biome(x >> 2, z >> 2);
		if (biome >= BIOMES.length) {
			biome = 0;
		}

		int r;
		int g;
		int b;
		if (water > ground) {
			int depth = water - ground;
			float deep = Math.min(1f, depth / 14f);
			float k = 1f - 0.62f * deep;
			int c = WATER[biome];
			r = (int) (((c >> 16) & 0xFF) * k);
			g = (int) (((c >> 8) & 0xFF) * k);
			b = (int) (((c) & 0xFF) * k);
			// a faint lighter rim in the shallows reads as a shore
			if (depth <= 1) {
				r += 14;
				g += 14;
				b += 14;
			}
		} else {
			int kind = map.surface(x, z);
			int c = kind <= 0 || kind >= SURFACE.length ? GRASS[biome] : SURFACE[kind];
			if (kind == 1 || kind == 6 || kind == 8) {
				// bare earth is pulled towards the grass around it, otherwise the patches shout on the map
				c = blend(c, GRASS[biome], 0.42f);
			}
			r = (c >> 16) & 0xFF;
			g = (c >> 8) & 0xFF;
			b = c & 0xFF;
			if (biome == SPIRE_PEAKS && ground > SNOW_LINE) {
				float t = Math.min(1f, (ground - SNOW_LINE) / (float) (SNOW_FULL - SNOW_LINE));
				r += (int) ((((SNOW >> 16) & 0xFF) - r) * t);
				g += (int) ((((SNOW >> 8) & 0xFF) - g) * t);
				b += (int) (((SNOW & 0xFF) - b) * t);
			}
			float shade = shade(x, z, distance);
			// higher ground is slightly lighter, the lowlands slightly darker
			shade *= 0.92f + 0.16f * clamp01((ground - 55) / 180f);
			r = (int) (r * shade);
			g = (int) (g * shade);
			b = (int) (b * shade);
		}

		float fog = FOG_AMOUNT[biome];
		if (fog > 0f) {
			int f = FOG[biome];
			r += (int) ((((f >> 16) & 0xFF) - r) * fog);
			g += (int) ((((f >> 8) & 0xFF) - g) * fog);
			b += (int) (((f & 0xFF) - b) * fog);
		}
		return 0xFF000000 | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
	}

	/** Brightness factor from the slope, light comes from the north-west. */
	private float shade(int x, int z, int d) {
		float gx = (map.ground(x + d, z) - map.ground(x - d, z)) / (2f * d);
		float gz = (map.ground(x, z + d) - map.ground(x, z - d)) / (2f * d);
		// the height range of the map is large, so the slope is taken with a gain and a soft limit
		float s = (gx + gz) * 0.5f;
		float steep = (float) Math.sqrt(gx * gx + gz * gz);
		float lit = 1f + 0.55f * (float) Math.tanh(s * 1.6f);
		float rock = 1f - 0.12f * (float) Math.tanh(steep * 1.2f);
		return Math.max(0.35f, lit * rock);
	}

	private static int blend(int a, int b, float t) {
		int r = ((a >> 16) & 0xFF) + (int) ((((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
		int g = ((a >> 8) & 0xFF) + (int) ((((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
		int bl = (a & 0xFF) + (int) (((b & 0xFF) - (a & 0xFF)) * t);
		return (r << 16) | (g << 8) | bl;
	}

	private static float clamp01(float v) {
		return v < 0f ? 0f : Math.min(v, 1f);
	}

	private static int clamp(int v) {
		return v < 0 ? 0 : Math.min(v, 255);
	}
}
