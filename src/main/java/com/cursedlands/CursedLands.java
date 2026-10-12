package com.cursedlands;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Mod entry point. Registration of blocks, items and world generation
 * components is added here step by step.
 */
public class CursedLands implements ModInitializer {
	public static final String MOD_ID = "cursed_lands";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		LOGGER.info("Cursed Lands initialized");
	}
}
