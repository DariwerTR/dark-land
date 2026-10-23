package com.cursedlands.client.map;

import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

import net.minecraft.client.KeyMapping;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;

import com.cursedlands.CursedLands;

/** Key bindings of the map (they appear in the controls screen under "Cursed Lands Map"). */
final class MapKeys {
	static KeyMapping openMap;
	static KeyMapping zoomIn;
	static KeyMapping zoomOut;
	static KeyMapping toggleMinimap;

	private MapKeys() {
	}

	static void register() {
		KeyMapping.Category category = KeyMapping.Category.register(CursedLands.id("map"));
		openMap = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.cursed_lands.open_map", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_M, category));
		zoomIn = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.cursed_lands.minimap_zoom_in", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_EQUAL, category));
		zoomOut = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.cursed_lands.minimap_zoom_out", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_MINUS, category));
		toggleMinimap = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.cursed_lands.minimap_toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, category));
	}
}
