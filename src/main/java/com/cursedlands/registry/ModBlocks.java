package com.cursedlands.registry;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import com.cursedlands.CursedLands;

/** Decorative blocks of the Cursed Lands biomes. */
public final class ModBlocks {
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

	private ModBlocks() {
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
