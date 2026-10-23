package com.cursedlands.client.map;

import com.mojang.blaze3d.platform.NativeImage;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/** A texture the map paints into: ARGB pixels in, a texture the GUI can draw out. Render thread only. */
final class MapTexture {
	private final Identifier id;
	private DynamicTexture texture;
	private int width;
	private int height;

	MapTexture(Identifier id) {
		this.id = id;
	}

	Identifier id() {
		return id;
	}

	/** Makes sure the texture has this size; a texture of another size is replaced (and its contents lost). */
	void ensure(int newWidth, int newHeight) {
		if (texture != null && newWidth == width && newHeight == height) {
			return;
		}
		close();
		texture = new DynamicTexture(id::toString, newWidth, newHeight, false);
		width = newWidth;
		height = newHeight;
		Minecraft.getInstance().getTextureManager().register(id, texture);
	}

	/** Copies the pixels (ARGB, {@code width * height}) into the texture and uploads them. */
	void upload(int[] argb) {
		if (texture == null) {
			return;
		}
		NativeImage image = texture.getPixels();
		if (image == null) {
			return;
		}
		for (int y = 0; y < height; y++) {
			int row = y * width;
			for (int x = 0; x < width; x++) {
				int c = argb[row + x];
				// the image wants ABGR: swap red and blue
				image.setPixelABGR(x, y, (c & 0xFF00FF00) | ((c >> 16) & 0xFF) | ((c & 0xFF) << 16));
			}
		}
		texture.upload();
	}

	void close() {
		if (texture != null) {
			Minecraft.getInstance().getTextureManager().release(id);
			texture = null;
		}
	}
}
