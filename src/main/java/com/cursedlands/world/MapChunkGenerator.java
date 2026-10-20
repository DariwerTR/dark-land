package com.cursedlands.world;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;

import com.cursedlands.registry.ModBlocks;

/**
 * Builds the terrain of the finite map straight from {@link MapData}: stone up to the ground height of the
 * column, a surface made of blocks chosen by the "surface" layer, water up to the water level of the column.
 * There is no noise: everything that is not in the map files (caves, ores, vegetation) comes from the biome features.
 *
 * <p>Surface kinds of the "surface" layer (top block, filler block under it, filler depth):
 * 0 grass (cursed grass, cursed soil, 3), 1 soil (3), 2 gravel (2), 3 sand (3), 4 rock (stone), 5 path (dirt path, dirt, 2),
 * 6 mud (3), 7 ash (ash block, cursed soil, 2), 8 coarse dirt (3), 9 deep rock (deepslate). tools/map_factory/mapmodel.py has the same numbers.
 */
public final class MapChunkGenerator extends ChunkGenerator {
	public static final MapCodec<MapChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(instance ->
			instance.group(RegistryOps.retrieveGetter(Registries.BIOME))
					.apply(instance, instance.stable(MapChunkGenerator::new)));

	/** Must match dimension_type/cursed_lands.json. */
	public static final int MIN_Y = -64;
	public static final int HEIGHT = 384;
	public static final int MAX_Y = MIN_Y + HEIGHT - 1;
	public static final int SEA_LEVEL = 63;
	private static final int SECTIONS = HEIGHT >> 4;

	private static final BlockState AIR = Blocks.AIR.defaultBlockState();
	private static final BlockState WATER = Blocks.WATER.defaultBlockState();
	private static final BlockState STONE = Blocks.STONE.defaultBlockState();
	private static final BlockState DEEPSLATE = Blocks.DEEPSLATE.defaultBlockState();
	private static final BlockState BEDROCK = Blocks.BEDROCK.defaultBlockState();

	private static final BlockState[] TOP = new BlockState[256];
	private static final BlockState[] FILLER = new BlockState[256];
	private static final int[] DEPTH = new int[256];

	static {
		for (int kind = 0; kind < 256; kind++) {
			surface(kind, ModBlocks.CURSED_GRASS_BLOCK, ModBlocks.CURSED_SOIL, 3);
		}
		surface(1, ModBlocks.CURSED_SOIL, ModBlocks.CURSED_SOIL, 3);
		surface(2, Blocks.GRAVEL, Blocks.GRAVEL, 2);
		surface(3, Blocks.SAND, Blocks.SAND, 3);
		surface(4, Blocks.STONE, Blocks.STONE, 0);
		surface(5, Blocks.DIRT_PATH, Blocks.DIRT, 2);
		surface(6, Blocks.MUD, Blocks.MUD, 3);
		surface(7, ModBlocks.ASH_BLOCK, ModBlocks.CURSED_SOIL, 2);
		surface(8, Blocks.COARSE_DIRT, Blocks.COARSE_DIRT, 3);
		surface(9, Blocks.DEEPSLATE, Blocks.DEEPSLATE, 0);
	}

	private static void surface(int kind, Block top, Block filler, int depth) {
		TOP[kind] = top.defaultBlockState();
		FILLER[kind] = filler.defaultBlockState();
		DEPTH[kind] = depth;
	}

	public MapChunkGenerator(HolderGetter<Biome> biomes) {
		super(new MapBiomeSource(biomes));
		MapData map = MapData.get();
		if (map.seaLevel != SEA_LEVEL) {
			throw new IllegalStateException("The map was made for sea level " + map.seaLevel + " but the generator uses " + SEA_LEVEL);
		}
	}

	@Override
	protected MapCodec<? extends ChunkGenerator> codec() {
		return CODEC;
	}

	@Override
	public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState, StructureManager structureManager, ChunkAccess chunk) {
		MapData map = MapData.get();
		int x0 = chunk.getPos().getMinBlockX();
		int z0 = chunk.getPos().getMinBlockZ();
		int[] ground = new int[256];
		int[] water = new int[256];
		int[] kind = new int[256];
		int highest = MIN_Y;
		for (int lz = 0; lz < 16; lz++) {
			for (int lx = 0; lx < 16; lx++) {
				int i = lz * 16 + lx;
				int g = groundAt(map, x0 + lx, z0 + lz);
				ground[i] = g;
				water[i] = wetLevel(map.water(x0 + lx, z0 + lz), g);
				kind[i] = map.surface(x0 + lx, z0 + lz);
				highest = Math.max(highest, Math.max(g, water[i]));
			}
		}

		for (int s = 0; s < SECTIONS; s++) {
			int sectionY = MIN_Y + (s << 4);
			if (sectionY > highest) {
				break;
			}
			LevelChunkSection section = chunk.getSection(s);
			for (int lz = 0; lz < 16; lz++) {
				for (int lx = 0; lx < 16; lx++) {
					int i = lz * 16 + lx;
					int last = Math.min(sectionY + 15, Math.max(ground[i], water[i]));
					for (int y = sectionY; y <= last; y++) {
						section.setBlockState(lx, y & 15, lz, stateAt(x0 + lx, y, z0 + lz, ground[i], water[i], kind[i]), false);
					}
				}
			}
		}

		Heightmap oceanFloor = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.OCEAN_FLOOR_WG);
		Heightmap worldSurface = chunk.getOrCreateHeightmapUnprimed(Heightmap.Types.WORLD_SURFACE_WG);
		for (int lz = 0; lz < 16; lz++) {
			for (int lx = 0; lx < 16; lx++) {
				int i = lz * 16 + lx;
				BlockState top = stateAt(x0 + lx, ground[i], z0 + lz, ground[i], water[i], kind[i]);
				oceanFloor.update(lx, ground[i], lz, top);
				worldSurface.update(lx, ground[i], lz, top);
				if (water[i] > 0) {
					worldSurface.update(lx, water[i], lz, WATER);
				}
			}
		}
		return CompletableFuture.completedFuture(chunk);
	}

	@Override
	public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState, BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk) {
	}

	@Override
	public void buildSurface(WorldGenRegion region, StructureManager structureManager, RandomState randomState, ChunkAccess chunk) {
	}

	@Override
	public void spawnOriginalMobs(WorldGenRegion region) {
	}

	@Override
	public int getGenDepth() {
		return HEIGHT;
	}

	@Override
	public int getSeaLevel() {
		return SEA_LEVEL;
	}

	@Override
	public int getMinY() {
		return MIN_Y;
	}

	@Override
	public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level, RandomState randomState) {
		MapData map = MapData.get();
		int g = groundAt(map, x, z);
		int w = wetLevel(map.water(x, z), g);
		boolean floor = type == Heightmap.Types.OCEAN_FLOOR_WG || type == Heightmap.Types.OCEAN_FLOOR;
		return (floor || w == 0 ? g : w) + 1;
	}

	@Override
	public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
		MapData map = MapData.get();
		int g = groundAt(map, x, z);
		int w = wetLevel(map.water(x, z), g);
		int kind = map.surface(x, z);
		int top = Math.max(g, w);
		BlockState[] column = new BlockState[top - MIN_Y + 1];
		for (int y = MIN_Y; y <= top; y++) {
			column[y - MIN_Y] = stateAt(x, y, z, g, w, kind);
		}
		return new NoiseColumn(MIN_Y, column);
	}

	@Override
	public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
		MapData map = MapData.get();
		info.add(String.format("Cursed Lands map: ground %d, water %d, surface %d, edge %.0f", groundAt(map, pos.getX(), pos.getZ()),
				map.water(pos.getX(), pos.getZ()), map.surface(pos.getX(), pos.getZ()), map.edgeDistance(pos.getX(), pos.getZ())));
	}

	/** Ground height kept inside the build range with room for the bedrock below and a block above. */
	private static int groundAt(MapData map, int x, int z) {
		return Math.max(MIN_Y + 8, Math.min(MAX_Y - 1, map.ground(x, z)));
	}

	/** Water surface of a column, 0 if it is dry (no water entry or the water does not reach above the ground). */
	private static int wetLevel(int water, int ground) {
		return water > ground && water > 0 ? Math.min(water, MAX_Y) : 0;
	}

	private static BlockState stateAt(int x, int y, int z, int ground, int water, int kind) {
		if (y > ground) {
			return water > 0 && y <= water ? WATER : AIR;
		}
		if (y < MIN_Y + 5 && random(x, y, z) < (MIN_Y + 5 - y) / 5.0F) {
			return BEDROCK;
		}
		int depth = ground - y;
		if (depth == 0) {
			return water > 0 ? FILLER[kind] : TOP[kind];
		}
		if (depth <= DEPTH[kind]) {
			return FILLER[kind];
		}
		if (y <= 0 || (y < 8 && random(x, y, z) < (8 - y) / 8.0F)) {
			return DEEPSLATE;
		}
		return STONE;
	}

	/** Cheap position hash in [0, 1): the same block for the same position, whatever the order of generation. */
	private static float random(int x, int y, int z) {
		long h = (x * 341873128712L) ^ (z * 132897987541L) ^ (y * 7919L);
		h = (h ^ (h >>> 33)) * 0xff51afd7ed558ccdL;
		h = (h ^ (h >>> 33)) * 0xc4ceb9fe1a85ec53L;
		h ^= h >>> 33;
		return (h & 0xFFFFFF) / (float) 0x1000000;
	}
}
