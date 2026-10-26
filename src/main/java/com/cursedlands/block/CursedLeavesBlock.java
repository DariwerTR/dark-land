package com.cursedlands.block;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.world.level.block.UntintedParticleLeavesBlock;

/**
 * Leaves of the trees of the Cursed Lands. They behave like vanilla leaves (they decay when no log is near), but a block
 * that is placed without properties (by a command, or from a structure template) is persistent: the old structures
 * keep their leaves. The trees of the world generator set {@code persistent = false} themselves.
 */
public class CursedLeavesBlock extends UntintedParticleLeavesBlock {
	public CursedLeavesBlock(float leafParticleChance, ParticleOptions leafParticle, Properties properties) {
		super(leafParticleChance, leafParticle, properties);
		this.registerDefaultState(this.defaultBlockState().setValue(PERSISTENT, true));
	}
}
