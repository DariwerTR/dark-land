package com.cursedlands.world.feature;

import java.util.Random;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;

import com.cursedlands.CursedLands;
import com.cursedlands.block.FacingDecorBlock;
import com.cursedlands.block.HangingVineBlock;
import com.cursedlands.registry.ModBlocks;
import com.cursedlands.world.tree.TreeCanvas;
import com.cursedlands.world.tree.TreeMat;
import com.cursedlands.world.tree.TreeSite;
import com.cursedlands.world.tree.TreeSpecies;

/**
 * Grows one tree of the Cursed Lands. The shape comes from {@link TreeSpecies} (pure Java, no Minecraft classes);
 * this class is the thin layer that lets the generator look at the world and turns the finished voxels into blocks.
 *
 * <p>The tree stands on the first free block above the ground at the feature position and reaches at most
 * {@link TreeCanvas#R} blocks to every side, so it always stays inside the 3x3 chunks that may be written while the
 * chunk of the origin is decorated.
 */
public class ProceduralTreeFeature extends Feature<ProceduralTreeConfig> {
	/** Ground blocks a trunk may grow into (roots, the foot of the trunk): see data/cursed_lands/tags/block. */
	private static final TagKey<Block> TREE_SOIL = TagKey.create(Registries.BLOCK, CursedLands.id("tree_soil"));
	private static final TagKey<Block> TREE_ROCK = TagKey.create(Registries.BLOCK, CursedLands.id("tree_rock"));

	/** How far above and below the origin a column is searched for the ground. */
	private static final int SCAN_UP = 6;
	private static final int SCAN_DOWN = 6;

	/** 1 (update) + 2 (send to clients) + 16 (no neighbour shape updates): the shapes are set by the generator. */
	private static final int FLAGS = 19;

	private static final Direction[] FUNGUS_FACING = { Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST };

	private static int reported;

	public ProceduralTreeFeature(Codec<ProceduralTreeConfig> codec) {
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<ProceduralTreeConfig> context) {
		WorldGenLevel level = context.level();
		BlockPos origin = context.origin();
		ProceduralTreeConfig config = context.config();
		if (!fitsRegion(level, origin)) {
			return false;
		}
		Random random = new Random(context.random().nextLong());
		try {
			TreeCanvas canvas = TreeSpecies.generate(config.species(), random, new WorldSite(level, origin), config.params());
			return canvas != null && build(level, origin, canvas);
		} catch (RuntimeException e) {
			// A broken tree must never stop the generation of a chunk; report the first few and carry on.
			if (reported++ < 8) {
				CursedLands.LOGGER.warn("Tree '{}' at {} could not be generated", config.species(), origin, e);
			}
			return false;
		}
	}

	/** While a chunk is generated only the chunk itself and its neighbours may be touched. */
	private static boolean fitsRegion(WorldGenLevel level, BlockPos origin) {
		if (level instanceof WorldGenRegion region) {
			ChunkPos center = region.getCenter();
			int r = TreeCanvas.R;
			return origin.getX() - r >= center.getMinBlockX() - 16
					&& origin.getX() + r <= center.getMaxBlockX() + 16
					&& origin.getZ() - r >= center.getMinBlockZ() - 16
					&& origin.getZ() + r <= center.getMaxBlockZ() + 16;
		}
		return true;
	}

	private static boolean build(WorldGenLevel level, BlockPos origin, TreeCanvas canvas) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int[] placed = new int[1];
		canvas.forEach((x, y, z, mat, aux, root) -> {
			pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
			BlockState old = level.getBlockState(pos);
			int ground = canvas.ground(x, z);
			boolean underground = ground != TreeSite.NO_GROUND && y < ground;
			if (underground ? !canReplaceGround(old) : !canReplace(old)) {
				return;
			}
			BlockState state = stateOf(canvas, x, y, z, mat, aux, old);
			if (level.setBlock(pos, state, FLAGS)) {
				placed[0]++;
			}
		});
		return placed[0] > 0;
	}

	private static BlockState stateOf(TreeCanvas canvas, int x, int y, int z, TreeMat mat, int aux, BlockState old) {
		switch (mat) {
			case LOG_BLACK:
				return log(ModBlocks.BLACK_WOOD_LOG, aux);
			case LOG_PALE:
				return log(ModBlocks.PALE_BIRCH_LOG, aux);
			case LOG_CHARRED:
				return log(ModBlocks.CHARRED_LOG, aux);
			case LOG_PINE:
				return log(ModBlocks.PINE_LOG, aux);
			case LEAF_WITHERED:
				return leaves(ModBlocks.WITHERED_LEAVES, aux, old);
			case LEAF_NEEDLES:
				return leaves(ModBlocks.DARK_NEEDLES, aux, old);
			case LEAF_CRIMSON:
				return leaves(ModBlocks.CRIMSON_LEAVES, aux, old);
			case LEAF_ASHEN:
				return leaves(ModBlocks.ASHEN_LEAVES, aux, old);
			case VINE:
				return ModBlocks.GLOOM_VINE.defaultBlockState()
						.setValue(HangingVineBlock.TIP, canvas.get(x, y - 1, z) != TreeMat.VINE);
			case FUNGUS:
				return ModBlocks.BRACKET_FUNGUS.defaultBlockState().setValue(FacingDecorBlock.FACING, FUNGUS_FACING[aux & 3]);
			default:
				throw new IllegalStateException("Unhandled tree material " + mat);
		}
	}

	private static BlockState log(Block block, int axis) {
		Direction.Axis a = axis == TreeCanvas.AXIS_X ? Direction.Axis.X : axis == TreeCanvas.AXIS_Z ? Direction.Axis.Z : Direction.Axis.Y;
		return block.defaultBlockState().setValue(RotatedPillarBlock.AXIS, a);
	}

	private static BlockState leaves(Block block, int distance, BlockState old) {
		return block.defaultBlockState()
				.setValue(LeavesBlock.DISTANCE, Math.max(1, Math.min(7, distance)))
				.setValue(LeavesBlock.PERSISTENT, false)
				.setValue(LeavesBlock.WATERLOGGED, old.getFluidState().is(FluidTags.WATER));
	}

	/** Air, water, snow, plants and leaves give way to a tree; lava does not. */
	static boolean canReplace(BlockState state) {
		if (state.isAir()) {
			return true;
		}
		if (state.is(Blocks.LAVA)) {
			return false;
		}
		return state.canBeReplaced() || state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(BlockTags.LEAVES);
	}

	/** Below the surface roots and the foot of a trunk may take the place of soil and loose rock. */
	static boolean canReplaceGround(BlockState state) {
		return canReplace(state) || state.is(TREE_SOIL) || state.is(TREE_ROCK) || state.is(BlockTags.DIRT);
	}

	/** Solid, natural ground: not air, not a fluid, not a plant, not part of another tree. */
	static boolean isGround(BlockState state) {
		return !state.isAir()
				&& !state.is(Blocks.LAVA)
				&& !canReplace(state)
				&& !state.is(BlockTags.LEAVES)
				&& !state.is(BlockTags.LOGS);
	}

	/** Lets the tree generator look at the blocks around the origin. */
	private static final class WorldSite implements TreeSite {
		private final LevelReader level;
		private final BlockPos origin;
		private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		private final int minY;
		private final int maxY;

		WorldSite(LevelReader level, BlockPos origin) {
			this.level = level;
			this.origin = origin;
			this.minY = level.getMinY();
			this.maxY = level.getMaxY();
		}

		private BlockState stateAt(int x, int y, int z) {
			int wy = this.origin.getY() + y;
			if (wy < this.minY || wy > this.maxY) {
				return Blocks.VOID_AIR.defaultBlockState();
			}
			return this.level.getBlockState(this.cursor.set(this.origin.getX() + x, wy, this.origin.getZ() + z));
		}

		@Override
		public int groundAt(int x, int z) {
			for (int y = SCAN_UP; y >= -SCAN_DOWN; y--) {
				if (ProceduralTreeFeature.canReplace(stateAt(x, y, z)) && isGround(stateAt(x, y - 1, z))) {
					return y;
				}
			}
			return NO_GROUND;
		}

		@Override
		public boolean canReplace(int x, int y, int z) {
			int wy = this.origin.getY() + y;
			return wy >= this.minY && wy <= this.maxY && ProceduralTreeFeature.canReplace(stateAt(x, y, z));
		}
	}
}
