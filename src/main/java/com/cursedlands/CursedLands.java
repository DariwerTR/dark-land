package com.cursedlands;

import net.minecraft.resources.Identifier;

import net.fabricmc.api.ModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.cursedlands.registry.ModBlocks;
import com.cursedlands.registry.ModItems;
import com.cursedlands.registry.ModNetworking;
import com.cursedlands.registry.ModWorld;

/**
 * Mod entry point. Registration of blocks, items and world generation
 * components is added here step by step.
 */
public class CursedLands implements ModInitializer {
	public static final String MOD_ID = "cursed_lands";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		// Blocks must be registered before items: block items and the creative tab refer to them.
		ModBlocks.initialize();
		ModItems.initialize();
		ModWorld.initialize();
		ModNetworking.initialize();

		LOGGER.info("Cursed Lands initialized");
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
