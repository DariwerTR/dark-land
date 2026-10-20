package com.cursedlands.world;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

import com.cursedlands.CursedLands;

/** Biomes of the finite map: one index per 4x4 block cell from the "biomes" layer, the climate sampler is not used. */
public final class MapBiomeSource extends BiomeSource {
	public static final MapCodec<MapBiomeSource> CODEC = RecordCodecBuilder.mapCodec(instance ->
			instance.group(RegistryOps.retrieveGetter(Registries.BIOME))
					.apply(instance, instance.stable(MapBiomeSource::new)));

	private final MapData map;
	private final List<Holder<Biome>> palette;

	public MapBiomeSource(HolderGetter<Biome> biomes) {
		map = MapData.get();
		List<Holder<Biome>> list = new ArrayList<>();
		for (String path : map.biomes) {
			list.add(biomes.getOrThrow(ResourceKey.create(Registries.BIOME, CursedLands.id(path))));
		}
		if (list.isEmpty()) {
			throw new IllegalStateException("The Cursed Lands map lists no biomes");
		}
		palette = List.copyOf(list);
	}

	@Override
	public MapCodec<? extends BiomeSource> codec() {
		return CODEC;
	}

	@Override
	public Stream<Holder<Biome>> collectPossibleBiomes() {
		return palette.stream();
	}

	@Override
	public Holder<Biome> getNoiseBiome(int quartX, int quartY, int quartZ, Climate.Sampler sampler) {
		int index = map.biome(quartX, quartZ);
		return palette.get(index < palette.size() ? index : 0);
	}
}
