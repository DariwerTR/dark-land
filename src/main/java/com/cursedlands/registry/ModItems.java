package com.cursedlands.registry;

import java.util.function.Function;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;

import com.cursedlands.CursedLands;

/** Items and the creative tab of the mod. */
public final class ModItems {
	public static final Item ASH = register("ash", Item::new, new Item.Properties());
	public static final Item DARKNESS_CRYSTAL = register("darkness_crystal", Item::new, new Item.Properties());
	public static final Item RUINS_HEART = register("ruins_heart", Item::new, new Item.Properties());

	public static final ResourceKey<CreativeModeTab> CREATIVE_TAB_KEY = ResourceKey.create(
			BuiltInRegistries.CREATIVE_MODE_TAB.key(), CursedLands.id("main")
	);

	public static final CreativeModeTab CREATIVE_TAB = FabricItemGroup.builder()
			.icon(() -> new ItemStack(DARKNESS_CRYSTAL))
			.title(Component.translatable("itemGroup.cursed_lands"))
			.displayItems((params, output) -> {
				output.accept(ModBlocks.BLOOD_MOSS_BLOCK);
				output.accept(ModBlocks.BLACK_WOOD_LOG);
				output.accept(ModBlocks.DARKNESS_CRYSTAL_BLOCK);
				output.accept(ModBlocks.GOTHIC_BRICKS);
				output.accept(ModBlocks.CRACKED_GOTHIC_BRICKS);
				output.accept(ModBlocks.MOSSY_GOTHIC_BRICKS);
				output.accept(ModBlocks.CHISELED_GOTHIC_BRICKS);
				output.accept(ModBlocks.GOTHIC_PILLAR);
				output.accept(ModBlocks.GOTHIC_BRICK_STAIRS);
				output.accept(ModBlocks.GOTHIC_BRICK_SLAB);
				output.accept(ModBlocks.GOTHIC_BRICK_WALL);
				output.accept(ModBlocks.BLOOD_GLASS);
				output.accept(ModBlocks.BLOOD_GLASS_PANE);
				output.accept(ModBlocks.RUSTED_BARS);
				output.accept(ModBlocks.DARK_PLANKS);
				output.accept(ModBlocks.DARK_PLANK_STAIRS);
				output.accept(ModBlocks.DARK_PLANK_SLAB);
				output.accept(ModBlocks.BLOOD_CARPET);
				output.accept(ModBlocks.BONE_LAMP);
				output.accept(ModBlocks.BONE_BRICKS);
				output.accept(ModBlocks.ASH_BLOCK);
				output.accept(ModBlocks.CANDELABRA);
				output.accept(ModBlocks.CHANDELIER);
				output.accept(ModBlocks.RELIQUARY_LANTERN);
				output.accept(ModBlocks.BONE_PILE);
				output.accept(ModBlocks.GOTHIC_FINIAL);
				output.accept(ModBlocks.ALTAR_TABLE);
				output.accept(ModBlocks.GARGOYLE);
				output.accept(ModBlocks.TOMBSTONE);
				output.accept(ModBlocks.WALL_SKULL_LAMP);
				output.accept(ModBlocks.GOTHIC_BENCH);
				output.accept(ModBlocks.GOTHIC_ROOF_TILES);
				output.accept(ModBlocks.GOTHIC_ROOF_TILE_STAIRS);
				output.accept(ModBlocks.GOTHIC_ROOF_TILE_SLAB);
				output.accept(ModBlocks.GOTHIC_ARCH);
				output.accept(ModBlocks.GOTHIC_BUTTRESS);
				output.accept(ModBlocks.GOTHIC_BUTTRESS_CAP);
				output.accept(ModBlocks.GOTHIC_CORNICE);
				output.accept(ModBlocks.GOTHIC_CORBEL);
				output.accept(ModBlocks.BATTLEMENT);
				output.accept(ModBlocks.ARROW_SLIT);
				output.accept(ModBlocks.TRACERY_WINDOW);
				output.accept(ModBlocks.GOTHIC_SHAFT);
				output.accept(ModBlocks.COLUMN_BASE);
				output.accept(ModBlocks.COLUMN_CAPITAL);
				output.accept(ModBlocks.GOTHIC_BOOKSHELF);
				output.accept(ModBlocks.GOTHIC_TABLE);
				output.accept(ModBlocks.GOTHIC_CHAIR);
				output.accept(ModBlocks.GOTHIC_THRONE);
				output.accept(ModBlocks.STONE_SARCOPHAGUS);
				output.accept(ModBlocks.WALL_TAPESTRY);
				output.accept(ModBlocks.WALL_SCONCE);
				output.accept(ModBlocks.IRON_BRAZIER);
				output.accept(ModBlocks.BONE_BRICK_STAIRS);
				output.accept(ModBlocks.BONE_BRICK_SLAB);
				output.accept(ModBlocks.BONE_BRICK_WALL);
				output.accept(ModBlocks.OSSUARY_WALL);
				output.accept(ModBlocks.DARK_BEAM);
				output.accept(ModBlocks.WROUGHT_IRON_FENCE);
				output.accept(ModBlocks.CURSED_GRASS_BLOCK);
				output.accept(ModBlocks.CURSED_SOIL);
				output.accept(ModBlocks.WITHERED_LEAVES);
				output.accept(ModBlocks.BLOOD_MOSS_CARPET);
				output.accept(ModBlocks.WITHERED_GRASS);
				output.accept(ModBlocks.SHADOW_MUSHROOM);
				output.accept(ModBlocks.BRACKET_FUNGUS);
				output.accept(ModBlocks.BLOOD_THORN);
				output.accept(ModBlocks.GHOST_LILY);
				output.accept(ModBlocks.NIGHTSHADE);
				output.accept(ModBlocks.GLOOM_VINE);
				output.accept(ModBlocks.SHADOW_MUSHROOM_BLOCK);
				output.accept(ModBlocks.SHADOW_MUSHROOM_STEM);
				output.accept(ASH);
				output.accept(DARKNESS_CRYSTAL);
				output.accept(RUINS_HEART);
			})
			.build();

	private ModItems() {
	}

	private static <T extends Item> T register(String name, Function<Item.Properties, T> factory, Item.Properties settings) {
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, CursedLands.id(name));
		T item = factory.apply(settings.setId(itemKey));
		return Registry.register(BuiltInRegistries.ITEM, itemKey, item);
	}

	public static void initialize() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, CREATIVE_TAB_KEY, CREATIVE_TAB);
	}
}
