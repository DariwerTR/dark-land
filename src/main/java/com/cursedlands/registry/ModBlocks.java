package com.cursedlands.registry;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StainedGlassBlock;
import net.minecraft.world.level.block.StainedGlassPaneBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

import com.cursedlands.CursedLands;
import com.cursedlands.block.DecorBarsBlock;
import com.cursedlands.block.DecorShapeBlock;
import com.cursedlands.block.DecorStairBlock;
import com.cursedlands.block.FacingDecorBlock;

/** Decorative blocks of the Cursed Lands biomes and buildings. */
public final class ModBlocks {
	// ---- stage 2: natural blocks
	public static final Block BLOOD_MOSS_BLOCK = register(
			"blood_moss_block",
			Block::new,
			BlockBehaviour.Properties.of().sound(SoundType.MOSS).strength(0.1F)
	);

	public static final Block BLACK_WOOD_LOG = register(
			"black_wood_log",
			RotatedPillarBlock::new,
			BlockBehaviour.Properties.of().sound(SoundType.WOOD).strength(2.0F)
	);

	public static final Block DARKNESS_CRYSTAL_BLOCK = register(
			"darkness_crystal_block",
			Block::new,
			BlockBehaviour.Properties.of().sound(SoundType.AMETHYST).strength(1.5F).lightLevel(state -> 5)
	);

	// ---- stage 6: decor for building exteriors and interiors.
	// Order matters: stairs and slabs refer to the blocks declared above them.
	public static final Block GOTHIC_BRICKS = register(
			"gothic_bricks",
			Block::new,
			stone()
	);

	public static final Block CRACKED_GOTHIC_BRICKS = register(
			"cracked_gothic_bricks",
			Block::new,
			stone()
	);

	public static final Block MOSSY_GOTHIC_BRICKS = register(
			"mossy_gothic_bricks",
			Block::new,
			stone()
	);

	public static final Block CHISELED_GOTHIC_BRICKS = register(
			"chiseled_gothic_bricks",
			Block::new,
			stone()
	);

	public static final Block GOTHIC_PILLAR = register(
			"gothic_pillar",
			RotatedPillarBlock::new,
			stone()
	);

	public static final Block GOTHIC_BRICK_STAIRS = register(
			"gothic_brick_stairs",
			settings -> new DecorStairBlock(GOTHIC_BRICKS.defaultBlockState(), settings),
			stone()
	);

	public static final Block GOTHIC_BRICK_SLAB = register(
			"gothic_brick_slab",
			SlabBlock::new,
			stone()
	);

	public static final Block GOTHIC_BRICK_WALL = register(
			"gothic_brick_wall",
			WallBlock::new,
			stone()
	);

	public static final Block BLOOD_GLASS = register(
			"blood_glass",
			settings -> new StainedGlassBlock(DyeColor.RED, settings),
			glass()
	);

	public static final Block BLOOD_GLASS_PANE = register(
			"blood_glass_pane",
			settings -> new StainedGlassPaneBlock(DyeColor.RED, settings),
			glass()
	);

	public static final Block RUSTED_BARS = register(
			"rusted_bars",
			DecorBarsBlock::new,
			bars()
	);

	public static final Block DARK_PLANKS = register(
			"dark_planks",
			Block::new,
			planks()
	);

	public static final Block DARK_PLANK_STAIRS = register(
			"dark_plank_stairs",
			settings -> new DecorStairBlock(DARK_PLANKS.defaultBlockState(), settings),
			planks()
	);

	public static final Block DARK_PLANK_SLAB = register(
			"dark_plank_slab",
			SlabBlock::new,
			planks()
	);

	public static final Block BLOOD_CARPET = register(
			"blood_carpet",
			CarpetBlock::new,
			carpet()
	);

	public static final Block BONE_LAMP = register(
			"bone_lamp",
			Block::new,
			lamp()
	);

	public static final Block BONE_BRICKS = register(
			"bone_bricks",
			Block::new,
			boneBricks()
	);

	public static final Block ASH_BLOCK = register(
			"ash_block",
			Block::new,
			ashBlock()
	);

	// ---- stage 8: decor blocks with custom models and shapes
	public static final Block CANDELABRA = register(
			"candelabra",
			settings -> new DecorShapeBlock(settings, Shapes.or(Block.box(1.5, 7.5, 6.5, 14.5, 16, 9.5), Block.box(5, 0, 5, 11, 3, 11), Block.box(7, 3, 7, 9, 12, 9))),
			BlockBehaviour.Properties.of().sound(SoundType.IRON).strength(1.5F, 3.0F).noOcclusion().lightLevel(state -> 12)
	);

	public static final Block CHANDELIER = register(
			"chandelier",
			settings -> new DecorShapeBlock(settings, Block.box(2, 4, 2, 14, 16, 14)),
			BlockBehaviour.Properties.of().sound(SoundType.IRON).strength(1.5F, 3.0F).noOcclusion().lightLevel(state -> 15)
	);

	public static final Block RELIQUARY_LANTERN = register(
			"reliquary_lantern",
			settings -> new DecorShapeBlock(settings, Block.box(3.5, 0, 3.5, 12.5, 16, 12.5)),
			BlockBehaviour.Properties.of().sound(SoundType.IRON).strength(1.5F, 3.0F).noOcclusion().lightLevel(state -> 14)
	);

	public static final Block BONE_PILE = register(
			"bone_pile",
			settings -> new DecorShapeBlock(settings, Block.box(1, 0, 1, 15, 7, 15)),
			BlockBehaviour.Properties.of().sound(SoundType.BONE_BLOCK).strength(1.5F, 3.0F).noOcclusion()
	);

	public static final Block GOTHIC_FINIAL = register(
			"gothic_finial",
			settings -> new DecorShapeBlock(settings, Block.box(4, 0, 4, 12, 16, 12)),
			BlockBehaviour.Properties.of().sound(SoundType.DEEPSLATE_BRICKS).strength(2.0F, 6.0F).noOcclusion().requiresCorrectToolForDrops()
	);

	public static final Block ALTAR_TABLE = register(
			"altar_table",
			settings -> new FacingDecorBlock(settings, new VoxelShape[]{
				Shapes.or(Block.box(0, 10, 1, 16, 14, 15), Block.box(12.5, 0, 11.5, 15.5, 10, 14.5), Block.box(0.5, 0, 11.5, 3.5, 10, 14.5), Block.box(12.5, 0, 2.5, 15.5, 10, 5.5), Block.box(0.5, 0, 2.5, 3.5, 10, 5.5)),
				Shapes.or(Block.box(1, 10, 0, 15, 14, 16), Block.box(1.5, 0, 12.5, 4.5, 10, 15.5), Block.box(1.5, 0, 0.5, 4.5, 10, 3.5), Block.box(10.5, 0, 12.5, 13.5, 10, 15.5), Block.box(10.5, 0, 0.5, 13.5, 10, 3.5)),
				Shapes.or(Block.box(0, 10, 1, 16, 14, 15), Block.box(0.5, 0, 1.5, 3.5, 10, 4.5), Block.box(12.5, 0, 1.5, 15.5, 10, 4.5), Block.box(0.5, 0, 10.5, 3.5, 10, 13.5), Block.box(12.5, 0, 10.5, 15.5, 10, 13.5)),
				Shapes.or(Block.box(1, 10, 0, 15, 14, 16), Block.box(11.5, 0, 0.5, 14.5, 10, 3.5), Block.box(11.5, 0, 12.5, 14.5, 10, 15.5), Block.box(2.5, 0, 0.5, 5.5, 10, 3.5), Block.box(2.5, 0, 12.5, 5.5, 10, 15.5))
			}),
			BlockBehaviour.Properties.of().sound(SoundType.DEEPSLATE_BRICKS).strength(2.0F, 6.0F).noOcclusion().requiresCorrectToolForDrops()
	);

	public static final Block GARGOYLE = register(
			"gargoyle",
			settings -> new FacingDecorBlock(settings, new VoxelShape[]{
				Block.box(2, 0, 0, 14, 15, 14),
				Block.box(2, 0, 2, 16, 15, 14),
				Block.box(2, 0, 2, 14, 15, 16),
				Block.box(0, 0, 2, 14, 15, 14)
			}),
			BlockBehaviour.Properties.of().sound(SoundType.DEEPSLATE_BRICKS).strength(2.0F, 6.0F).noOcclusion().requiresCorrectToolForDrops()
	);

	public static final Block TOMBSTONE = register(
			"tombstone",
			settings -> new FacingDecorBlock(settings, new VoxelShape[]{
				Block.box(3, 0, 5, 13, 16, 11),
				Block.box(5, 0, 3, 11, 16, 13),
				Block.box(3, 0, 5, 13, 16, 11),
				Block.box(5, 0, 3, 11, 16, 13)
			}),
			BlockBehaviour.Properties.of().sound(SoundType.DEEPSLATE_BRICKS).strength(2.0F, 6.0F).noOcclusion().requiresCorrectToolForDrops()
	);

	public static final Block WALL_SKULL_LAMP = register(
			"wall_skull_lamp",
			settings -> new FacingDecorBlock(settings, new VoxelShape[]{
				Block.box(2.5, 1.5, 0, 13.5, 15.5, 10),
				Block.box(6, 1.5, 2.5, 16, 15.5, 13.5),
				Block.box(2.5, 1.5, 6, 13.5, 15.5, 16),
				Block.box(0, 1.5, 2.5, 10, 15.5, 13.5)
			}),
			BlockBehaviour.Properties.of().sound(SoundType.BONE_BLOCK).strength(1.5F, 3.0F).noOcclusion().lightLevel(state -> 10)
	);

	public static final Block GOTHIC_BENCH = register(
			"gothic_bench",
			settings -> new FacingDecorBlock(settings, new VoxelShape[]{
				Block.box(0, 0, 2, 16, 15, 13),
				Block.box(3, 0, 0, 14, 15, 16),
				Block.box(0, 0, 3, 16, 15, 14),
				Block.box(2, 0, 0, 13, 15, 16)
			}),
			BlockBehaviour.Properties.of().sound(SoundType.WOOD).strength(2.0F, 3.0F).noOcclusion()
	);

	private ModBlocks() {
	}

	// Each helper returns a new Properties object: setId() mutates it, so it must not be shared between blocks.
	private static BlockBehaviour.Properties stone() {
		return BlockBehaviour.Properties.of().sound(SoundType.DEEPSLATE_BRICKS).strength(2.0F, 6.0F).requiresCorrectToolForDrops();
	}

	private static BlockBehaviour.Properties planks() {
		return BlockBehaviour.Properties.of().sound(SoundType.WOOD).strength(2.0F, 3.0F);
	}

	private static BlockBehaviour.Properties boneBricks() {
		return BlockBehaviour.Properties.of().sound(SoundType.BONE_BLOCK).strength(2.0F, 6.0F).requiresCorrectToolForDrops();
	}

	private static BlockBehaviour.Properties ashBlock() {
		return BlockBehaviour.Properties.of().sound(SoundType.SAND).strength(0.5F);
	}

	private static BlockBehaviour.Properties lamp() {
		return BlockBehaviour.Properties.of().sound(SoundType.BONE_BLOCK).strength(1.0F).lightLevel(state -> 13);
	}

	private static BlockBehaviour.Properties carpet() {
		return BlockBehaviour.Properties.of().sound(SoundType.WOOL).strength(0.1F);
	}

	private static BlockBehaviour.Properties bars() {
		return BlockBehaviour.Properties.of().sound(SoundType.IRON).strength(5.0F, 6.0F).requiresCorrectToolForDrops().noOcclusion();
	}

	private static BlockBehaviour.Properties glass() {
		return BlockBehaviour.Properties.of()
				.sound(SoundType.GLASS)
				.strength(0.3F)
				.noOcclusion()
				.isValidSpawn((state, level, pos, type) -> false)
				.isRedstoneConductor((state, level, pos) -> false)
				.isSuffocating((state, level, pos) -> false)
				.isViewBlocking((state, level, pos) -> false);
	}

	private static Block register(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties settings) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, CursedLands.id(name));
		Block block = factory.apply(settings.setId(blockKey));

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, CursedLands.id(name));
		BlockItem blockItem = new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix());
		Registry.register(BuiltInRegistries.ITEM, itemKey, blockItem);

		return Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
	}

	/** Forces the class to load so that the blocks above get registered. */
	public static void initialize() {
	}
}
