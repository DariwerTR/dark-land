package com.cursedlands.block;

import net.minecraft.world.level.block.IronBarsBlock;

/** Bars with a public constructor: the vanilla one is protected and cannot be called from other packages. */
public class DecorBarsBlock extends IronBarsBlock {
	public DecorBarsBlock(Properties properties) {
		super(properties);
	}
}
