package com.cursedlands.world.feature;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

import com.cursedlands.world.tree.TreeSpecies;

/**
 * Configuration of {@link ProceduralTreeFeature}: the species to grow and optional overrides of its size.
 * A height of 0 keeps the default of the species; {@code leaf_density} scales the foliage (1 = as designed).
 */
public record ProceduralTreeConfig(String species, int heightMin, int heightMax, float leafDensity) implements FeatureConfiguration {
	private static final Codec<String> SPECIES = Codec.STRING.validate(
			name -> TreeSpecies.exists(name) ? DataResult.success(name) : DataResult.error(() -> "Unknown tree species: " + name));

	public static final Codec<ProceduralTreeConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			SPECIES.fieldOf("species").forGetter(ProceduralTreeConfig::species),
			Codec.INT.optionalFieldOf("height_min", 0).forGetter(ProceduralTreeConfig::heightMin),
			Codec.INT.optionalFieldOf("height_max", 0).forGetter(ProceduralTreeConfig::heightMax),
			Codec.FLOAT.optionalFieldOf("leaf_density", 1.0F).forGetter(ProceduralTreeConfig::leafDensity)
	).apply(instance, ProceduralTreeConfig::new));

	public TreeSpecies.Params params() {
		return new TreeSpecies.Params(this.heightMin, this.heightMax, this.leafDensity);
	}
}
