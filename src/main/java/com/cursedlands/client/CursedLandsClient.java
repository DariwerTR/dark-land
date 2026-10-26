package com.cursedlands.client;

import net.minecraft.client.renderer.chunk.ChunkSectionLayer;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.BlockRenderLayerMap;

import com.cursedlands.client.map.MapClient;
import com.cursedlands.registry.ModBlocks;

/** Client-only setup: render layers of blocks with transparent textures, the in-game map. */
public class CursedLandsClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		BlockRenderLayerMap.putBlocks(ChunkSectionLayer.TRANSLUCENT, ModBlocks.BLOOD_GLASS, ModBlocks.BLOOD_GLASS_PANE, ModBlocks.TRACERY_WINDOW);
		BlockRenderLayerMap.putBlock(ModBlocks.RUSTED_BARS, ChunkSectionLayer.CUTOUT);
		BlockRenderLayerMap.putBlock(ModBlocks.IRON_BRAZIER, ChunkSectionLayer.CUTOUT);
		// stage 10: vegetation with transparent texels (cutout)
		BlockRenderLayerMap.putBlocks(ChunkSectionLayer.CUTOUT,
				ModBlocks.WITHERED_GRASS,
				ModBlocks.SHADOW_MUSHROOM,
				ModBlocks.BLOOD_THORN,
				ModBlocks.GHOST_LILY,
				ModBlocks.NIGHTSHADE,
				ModBlocks.BRACKET_FUNGUS,
				ModBlocks.GLOOM_VINE,
				ModBlocks.WITHERED_LEAVES,
				ModBlocks.DARK_NEEDLES,
				ModBlocks.CRIMSON_LEAVES,
				ModBlocks.ASHEN_LEAVES,
				ModBlocks.SHADOW_MUSHROOM_BLOCK
		);
		// stage 13: minimap and full-screen map
		MapClient.initialize();
	}
}
