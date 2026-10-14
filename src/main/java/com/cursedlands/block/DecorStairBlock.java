package com.cursedlands.block;

import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/** Stairs with a public constructor: the vanilla one is protected and cannot be called from other packages. */
public class DecorStairBlock extends StairBlock {
	public DecorStairBlock(BlockState baseState, Properties properties) {
		super(baseState, properties);
	}
}
