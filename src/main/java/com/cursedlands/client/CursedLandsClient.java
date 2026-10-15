package com.cursedlands.client;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;

import com.cursedlands.registry.ModBlocks;

/** Client-only setup: render layers of blocks with transparent textures. */
public class CursedLandsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		BlockRenderLayerMap.putBlocks(ChunkSectionLayer.TRANSLUCENT, ModBlocks.BLOOD_GLASS, ModBlocks.BLOOD_GLASS_PANE, ModBlocks.TRACERY_WINDOW);
		BlockRenderLayerMap.putBlock(ModBlocks.RUSTED_BARS, ChunkSectionLayer.CUTOUT);
		BlockRenderLayerMap.putBlock(ModBlocks.IRON_BRAZIER, ChunkSectionLayer.CUTOUT);
	}
}
