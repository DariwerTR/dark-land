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
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Vine hanging from a ceiling or from another vine; the lowest block of a column shows the bud (tip = true). */
public class HangingVineBlock extends Block {
	public static final BooleanProperty TIP = BooleanProperty.create("tip");
	private final VoxelShape shape;

	public HangingVineBlock(Properties properties, VoxelShape shape) {
		super(properties);
		this.shape = shape;
		this.registerDefaultState(this.stateDefinition.any().setValue(TIP, true));
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return this.shape;
	}

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(TIP);
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		BlockPos above = pos.above();
		BlockState support = level.getBlockState(above);
		// leaves have no sturdy faces, but the vines of the trees hang from them
		return support.is(this) || support.is(BlockTags.LEAVES) || support.isFaceSturdy(level, above, Direction.DOWN);
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos, Direction direction,
			BlockPos neighborPos, BlockState neighborState, RandomSource random) {
		if (!state.canSurvive(level, pos)) {
			return Blocks.AIR.defaultBlockState();
		}
		if (direction == Direction.DOWN) {
			return state.setValue(TIP, !neighborState.is(this));
		}
		return state;
	}
}
