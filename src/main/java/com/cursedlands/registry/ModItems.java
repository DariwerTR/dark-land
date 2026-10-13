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
