package com.cursedlands.registry;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;

import com.cursedlands.CursedLands;
import com.cursedlands.world.FogWall;
import com.cursedlands.world.MapBiomeSource;
import com.cursedlands.world.MapChunkGenerator;

/** World generation components of the finite map: chunk generator, biome source and the fog wall at the edge. */
public final class ModWorld {
	private ModWorld() {
	}

	public static void initialize() {
		Registry.register(BuiltInRegistries.CHUNK_GENERATOR, CursedLands.id("map"), MapChunkGenerator.CODEC);
		Registry.register(BuiltInRegistries.BIOME_SOURCE, CursedLands.id("map"), MapBiomeSource.CODEC);
		FogWall.initialize();
	}
}
