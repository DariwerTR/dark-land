package com.cursedlands.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Small plant without collision: stands on soil (tag minecraft:dirt) or on any block with a sturdy top, and drops when the support is gone. */
public class DecorPlantBlock extends Block {
	public enum Support { SOIL, STURDY }

	private final VoxelShape shape;
	private final Support support;

	public DecorPlantBlock(Properties properties, VoxelShape shape, Support support) {
		super(properties);
		this.shape = shape;
		this.support = support;
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return this.shape;
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos below = pos.below();
		BlockState ground = level.getBlockState(below);
		return this.support == Support.SOIL ? ground.is(BlockTags.DIRT) : ground.isFaceSturdy(level, below, Direction.UP);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
			BlockPos neighborPos, BlockState neighborState, RandomSource random) {
		return state.canSurvive(level, pos)
				? super.updateShape(state, level, ticks, pos, direction, neighborPos, neighborState, random)
				: Blocks.AIR.defaultBlockState();
	}
}
