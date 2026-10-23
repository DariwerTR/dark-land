package com.cursedlands.client.map;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import com.cursedlands.CursedLands;
import com.cursedlands.world.MapData;

/** The minimap in a corner of the HUD. The picture is repainted only when something it shows has changed. */
final class MinimapHud {
	private static final Identifier TEXTURE_ID = CursedLands.id("minimap");
	private static final int MARGIN = 6;
	private static final int TEXT_COLOR = 0xFFE6DEEE;

	private static final MapTexture texture = new MapTexture(TEXTURE_ID);
	private static int[] pixels = new int[0];

	// what the current picture shows
	private static boolean painted;
	private static boolean complete;
	private static int paintedSize;
	private static boolean paintedRound;
	private static boolean paintedFog;
	private static double paintedX;
	private static double paintedZ;
	private static double paintedScale;
	private static double paintedFacingX;
	private static double paintedFacingZ;
	private static double paintedYaw;
	private static int paintedRevision;

	private MinimapHud() {
	}

	static void release() {
		texture.close();
		pixels = new int[0];
		painted = false;
	}

	static void render(GuiGraphics graphics, DeltaTracker tracker) {
		try {
			draw(graphics, tracker);
		} catch (RuntimeException e) {
			MapClient.fail("minimap", e);
		}
	}

	private static void draw(GuiGraphics graphics, DeltaTracker tracker) {
		Minecraft mc = Minecraft.getInstance();
		if (!MapClient.isReady(mc) || mc.options.hideGui || mc.screen instanceof WorldMapScreen) {
			return;
		}
		MapSettings settings = MapClient.settings();
		if (!settings.minimapVisible) {
			return;
		}
		LocalPlayer player = mc.player;
		MapData data = MapClient.data();
		MapMemory memory = MapClient.memory();
		int size = settings.pixels();

		float partial = tracker.getGameTimeDeltaPartialTick(false);
		double x = player.xo + (player.getX() - player.xo) * partial;
		double z = player.zo + (player.getZ() - player.zo) * partial;
		double yaw = Math.toRadians(player.getYRot());
		// the direction the player looks, as a vector on the map (yaw 0 looks to the south, +z)
		double lookX = -Math.sin(yaw);
		double lookZ = Math.cos(yaw);
		double scale = settings.blocksPerPixel();
		double facingX = settings.rotate ? lookX : 0;
		double facingZ = settings.rotate ? lookZ : -1;

		int sx = left(graphics.guiWidth(), size, settings.corner);
		// the status effect icons of the game hang in the top right corner: keep clear of them
		int effectsRows = settings.corner == 1 && !player.getActiveEffects().isEmpty() ? 52 : 0;
		int sy = top(graphics.guiHeight(), size, settings.corner) + effectsRows;

		boolean needsPaint = !painted || !complete || size != paintedSize || settings.round != paintedRound
				|| settings.fogOfWar != paintedFog || scale != paintedScale || memory.revision() != paintedRevision
				|| Math.abs(x - paintedX) > scale * 0.25 || Math.abs(z - paintedZ) > scale * 0.25
				|| Math.abs(facingX - paintedFacingX) > 0.012 || Math.abs(facingZ - paintedFacingZ) > 0.012
				|| (!settings.rotate && Math.abs(yaw - paintedYaw) > 0.02);
		MapProjection projection = new MapProjection(size, size, x, z, scale, facingX, facingZ);
		if (needsPaint) {
			if (pixels.length != size * size) {
				pixels = new int[size * size];
			}
			texture.ensure(size, size);
			complete = MapClient.view().render(pixels, projection, settings.fogOfWar ? memory : null, settings.round);
			drawIcons(pixels, projection, settings.round, data, memory, settings.fogOfWar, x, z, lookX, lookZ, Math.max(1, size / 16));
			texture.upload(pixels);
			painted = true;
			paintedSize = size;
			paintedRound = settings.round;
			paintedFog = settings.fogOfWar;
			paintedX = x;
			paintedZ = z;
			paintedScale = scale;
			paintedFacingX = facingX;
			paintedFacingZ = facingZ;
			paintedYaw = yaw;
			paintedRevision = memory.revision();
		}

		if (!settings.round) {
			graphics.fill(sx - 2, sy - 2, sx + size + 2, sy + size + 2, 0xFF17141C);
			graphics.renderOutline(sx - 2, sy - 2, size + 4, size + 4, 0xFF6B5A78);
		}
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE_ID, sx, sy, 0f, 0f, size, size, size, size);

		// north
		double nx = projection.screenX(x, z - 1) - size / 2.0;
		double ny = projection.screenY(x, z - 1) - size / 2.0;
		double reach = size / 2.0 - 7;
		graphics.drawCenteredString(mc.font, "N", (int) Math.round(sx + size / 2.0 + nx * reach), (int) Math.round(sy + size / 2.0 + ny * reach) - 4, 0xFFE05555);

		// coordinates and biome
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		Component coordinates = Component.translatable("map.cursed_lands.coordinates", bx, bz);
		Component biome = MapClient.biomeName(bx, bz);
		int lines = 2;
		int textY = settings.corner >= 2 ? sy - 4 - lines * 10 : sy + size + 5;
		int center = sx + size / 2;
		graphics.drawCenteredString(mc.font, coordinates, center, textY, TEXT_COLOR);
		graphics.drawCenteredString(mc.font, biome, center, textY + 10, TEXT_COLOR);
	}

	/** Spawn, the player's marker and the arrow that stands for the player, drawn into the painted pixels. */
	static void drawIcons(int[] px, MapProjection projection, boolean round, MapData data, MapMemory memory, boolean fog,
			double playerX, double playerZ, double lookX, double lookZ, int unit) {
		int w = projection.width;
		int h = projection.height;
		for (MapPlaces.Place place : MapClient.places().all()) {
			if (!MapPlaces.SPAWN.equals(place.kind())) {
				continue;
			}
			if (fog && !memory.isExploredAt(place.x() - data.originX, place.z() - data.originZ)) {
				continue;
			}
			double ix = projection.screenX(place.x(), place.z());
			double iy = projection.screenY(place.x(), place.z());
			if (ix >= 0 && iy >= 0 && ix < w && iy < h) {
				MapIcons.ring(px, w, h, ix, iy, unit * 0.55 + 1.5, MapIcons.SPAWN, MapIcons.SPAWN_OUTLINE);
			}
		}
		if (memory.hasPin()) {
			double wx = memory.pinX() + data.originX;
			double wz = memory.pinZ() + data.originZ;
			double[] at = MapIcons.toRim(w, h, round, projection.screenX(wx, wz), projection.screenY(wx, wz), unit * 0.5 + 2);
			MapIcons.diamond(px, w, h, at[0], at[1], unit * 0.5 + 1.0, MapIcons.PIN, MapIcons.PIN_OUTLINE);
		}
		double right = -projection.facingZ;
		double rightZ = projection.facingX;
		double dirX = lookX * right + lookZ * rightZ;
		double dirY = -(lookX * projection.facingX + lookZ * projection.facingZ);
		MapIcons.arrow(px, w, h, projection.screenX(playerX, playerZ), projection.screenY(playerX, playerZ), dirX, dirY,
				unit * 0.5 + 1.5, MapIcons.PLAYER, MapIcons.PLAYER_OUTLINE);
	}

	private static int left(int screenWidth, int size, int corner) {
		return corner == 0 || corner == 2 ? MARGIN : screenWidth - size - MARGIN;
	}

	private static int top(int screenHeight, int size, int corner) {
		return corner < 2 ? MARGIN : screenHeight - size - MARGIN;
	}
}
