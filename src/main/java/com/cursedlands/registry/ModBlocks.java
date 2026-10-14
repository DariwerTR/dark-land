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

import com.cursedlands.CursedLands;
import com.cursedlands.block.DecorBarsBlock;
import com.cursedlands.block.DecorStairBlock;

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
