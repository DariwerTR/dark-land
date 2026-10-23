package com.cursedlands.client.map;

import java.util.List;
import java.util.function.Supplier;

import org.lwjgl.glfw.GLFW;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import com.cursedlands.CursedLands;
import com.cursedlands.world.MapData;

/**
 * The full-screen map. Drag to move, wheel to zoom, right click puts a marker (or takes it away), the buttons at the bottom
 * change the settings of the map. The game goes on behind it.
 */
public class WorldMapScreen extends Screen {
	private static final Identifier TEXTURE_ID = CursedLands.id("world_map");
	private static final int TOP_BAR = 15;
	private static final int BUTTON_HEIGHT = 16;
	private static final int BUTTON_GAP = 3;
	private static final double MIN_SCALE = 0.25;
	private static final double START_SCALE = 2.0;
	private static final int TEXT = 0xFFE6DEEE;
	private static final int GOLD = 0xFFF1E6B0;

	private record Btn(Supplier<Component> label, Runnable action) {
	}

	private final MapTexture texture = new MapTexture(TEXTURE_ID);
	private int[] pixels = new int[0];
	private List<Btn> buttons = List.of();
	private boolean placed;
	private boolean dragging;

	// layout of the buttons, recomputed whenever it is needed
	private int buttonWidth;
	private int[] buttonX = new int[0];
	private int[] buttonY = new int[0];
	private int rows = 1;

	private double centerX;
	private double centerZ;
	private double scale = START_SCALE;

	// what the current picture shows
	private boolean painted;
	private boolean complete;
	private int paintedWidth;
	private int paintedHeight;
	private double paintedX;
	private double paintedZ;
	private double paintedScale;
	private boolean paintedFog;
	private int paintedRevision;
	private double paintedPlayerX;
	private double paintedPlayerZ;
	private double paintedYaw;

	public WorldMapScreen() {
		super(Component.translatable("map.cursed_lands.title"));
	}

	@Override
	protected void init() {
		texture.ensure(width, height);
		pixels = new int[width * height];
		painted = false;
		MapSettings s = MapClient.settings();
		buttons = List.of(
				new Btn(() -> Component.translatable("map.cursed_lands.button.fog", state(s.fogOfWar)), () -> s.fogOfWar = !s.fogOfWar),
				new Btn(() -> Component.translatable("map.cursed_lands.button.minimap", Component.translatable(s.minimapVisible ? "map.cursed_lands.shown" : "map.cursed_lands.hidden")),
						() -> s.minimapVisible = !s.minimapVisible),
				new Btn(() -> Component.translatable("map.cursed_lands.button.shape", Component.translatable(s.round ? "map.cursed_lands.round" : "map.cursed_lands.square")),
						() -> s.round = !s.round),
				new Btn(() -> Component.translatable("map.cursed_lands.button.rotate", Component.translatable(s.rotate ? "map.cursed_lands.facing" : "map.cursed_lands.north")),
						() -> s.rotate = !s.rotate),
				new Btn(() -> Component.translatable("map.cursed_lands.button.size", Component.translatable("map.cursed_lands.size." + s.size)), s::cycleSize),
				new Btn(() -> Component.translatable("map.cursed_lands.button.corner", Component.translatable("map.cursed_lands.corner." + s.corner)), s::cycleCorner),
				new Btn(() -> Component.translatable("map.cursed_lands.button.center"), this::centerOnPlayer));
		if (!placed) {
			placed = true;
			centerOnPlayer();
		}
	}

	@Override
	public void removed() {
		texture.close();
		super.removed();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** The map covers the whole screen, so there is no background to draw. */
	@Override
	public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
	}

	@Override
	public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
		try {
			draw(graphics, mouseX, mouseY);
		} catch (RuntimeException e) {
			MapClient.fail("map screen", e);
			onClose();
		}
	}

	private void draw(GuiGraphics graphics, int mouseX, int mouseY) {
		MapData data = MapClient.data();
		MapMemory memory = MapClient.memory();
		MapView view = MapClient.view();
		LocalPlayer player = minecraft.player;
		graphics.fill(0, 0, width, height, 0xFF050507);
		if (data == null || memory == null || view == null || player == null) {
			return;
		}
		MapSettings settings = MapClient.settings();
		clampView(data);

		double yaw = Math.toRadians(player.getYRot());
		boolean fog = settings.fogOfWar;
		boolean needsPaint = !painted || !complete || width != paintedWidth || height != paintedHeight || fog != paintedFog
				|| scale != paintedScale || centerX != paintedX || centerZ != paintedZ || memory.revision() != paintedRevision
				|| Math.abs(player.getX() - paintedPlayerX) > 0.5 || Math.abs(player.getZ() - paintedPlayerZ) > 0.5
				|| Math.abs(yaw - paintedYaw) > 0.02;
		MapProjection projection = MapProjection.northUp(width, height, centerX, centerZ, scale);
		if (needsPaint) {
			complete = view.render(pixels, projection, fog ? memory : null, false);
			MinimapHud.drawIcons(pixels, projection, false, data, memory, fog, player.getX(), player.getZ(), -Math.sin(yaw), Math.cos(yaw), 6);
			for (MapPlaces.Place place : MapClient.places().all()) {
				if (!MapPlaces.SPAWN.equals(place.kind()) && labelVisible(place, data, memory, fog)) {
					MapIcons.diamond(pixels, width, height, projection.screenX(place.x(), place.z()), projection.screenY(place.x(), place.z()), 2.0, 0xFFEAE2F0, 0xFF140F18);
				}
			}
			texture.upload(pixels);
			painted = true;
			paintedWidth = width;
			paintedHeight = height;
			paintedFog = fog;
			paintedScale = scale;
			paintedX = centerX;
			paintedZ = centerZ;
			paintedRevision = memory.revision();
			paintedPlayerX = player.getX();
			paintedPlayerZ = player.getZ();
			paintedYaw = yaw;
		}
		graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE_ID, 0, 0, 0f, 0f, width, height, width, height);

		for (MapPlaces.Place place : MapClient.places().all()) {
			if (!labelVisible(place, data, memory, fog)) {
				continue;
			}
			Component name = Component.translatable("map.cursed_lands.place." + place.id());
			int sx = (int) Math.round(projection.screenX(place.x(), place.z()));
			int sy = (int) Math.round(projection.screenY(place.x(), place.z()));
			if (sx < -80 || sy < -10 || sx > width + 80 || sy > height + 10) {
				continue;
			}
			int color = MapPlaces.REGION.equals(place.kind()) ? GOLD : MapPlaces.SPAWN.equals(place.kind()) ? 0xFFB9E6A0 : TEXT;
			graphics.drawString(font, name, sx - font.width(name) / 2, sy + 5, color, true);
		}

		// top bar: title and what is under the cursor
		graphics.fill(0, 0, width, TOP_BAR, 0xB0000000);
		graphics.drawString(font, getTitle(), 6, 4, GOLD, true);
		layoutButtons();
		if (mouseY >= TOP_BAR && mouseY < height - barHeight()) {
			String info = cursorInfo(data, memory, fog, projection, mouseX, mouseY);
			graphics.drawString(font, info, width - 6 - font.width(info), 4, TEXT, true);
		}

		// bottom bar: hint and buttons
		int barTop = height - barHeight();
		graphics.fill(0, barTop, width, height, 0xB0000000);
		graphics.drawCenteredString(font, Component.translatable("map.cursed_lands.hint"), width / 2, barTop + 3, 0xFF9C94A8);
		for (int i = 0; i < buttons.size(); i++) {
			int bx = buttonX[i];
			int by = buttonY[i];
			boolean hover = mouseX >= bx && mouseX < bx + buttonWidth && mouseY >= by && mouseY < by + BUTTON_HEIGHT;
			graphics.fill(bx, by, bx + buttonWidth, by + BUTTON_HEIGHT, hover ? 0xE0453B52 : 0xC0261F2E);
			graphics.renderOutline(bx, by, buttonWidth, BUTTON_HEIGHT, hover ? 0xFFB9A6D0 : 0xFF6B5A78);
			graphics.drawCenteredString(font, buttons.get(i).label().get(), bx + buttonWidth / 2, by + 4, 0xFFF0EAF6);
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		layoutButtons();
		if (event.button() == 0) {
			for (int i = 0; i < buttons.size(); i++) {
				if (mx >= buttonX[i] && mx < buttonX[i] + buttonWidth && my >= buttonY[i] && my < buttonY[i] + BUTTON_HEIGHT) {
					buttons.get(i).action().run();
					MapClient.saveSettings();
					painted = false;
					return true;
				}
			}
		}
		if (my < TOP_BAR || my >= height - barHeight()) {
			return true;
		}
		if (event.button() == 0) {
			dragging = true;
			return true;
		}
		if (event.button() == 1) {
			togglePin(mx, my);
			return true;
		}
		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (event.button() == 0) {
			dragging = false;
		}
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (dragging && event.button() == 0) {
			centerX -= dragX * scale;
			centerZ -= dragY * scale;
			return true;
		}
		return super.mouseDragged(event, dragX, dragY);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0) {
			zoomAt(mouseX, mouseY, Math.pow(0.8, scrollY));
			return true;
		}
		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (MapKeys.openMap != null && MapKeys.openMap.matches(event)) {
			onClose();
			return true;
		}
		int key = event.key();
		if (key == GLFW.GLFW_KEY_EQUAL || key == GLFW.GLFW_KEY_KP_ADD) {
			zoomAt(width / 2.0, height / 2.0, 0.74);
			return true;
		}
		if (key == GLFW.GLFW_KEY_MINUS || key == GLFW.GLFW_KEY_KP_SUBTRACT) {
			zoomAt(width / 2.0, height / 2.0, 1.35);
			return true;
		}
		if (key == GLFW.GLFW_KEY_SPACE || key == GLFW.GLFW_KEY_C) {
			centerOnPlayer();
			return true;
		}
		return super.keyPressed(event);
	}

	private static Component state(boolean on) {
		return Component.translatable(on ? "map.cursed_lands.on" : "map.cursed_lands.off");
	}

	private void centerOnPlayer() {
		LocalPlayer player = minecraft == null ? null : minecraft.player;
		if (player != null) {
			centerX = player.getX();
			centerZ = player.getZ();
		}
	}

	private double maxScale(MapData data) {
		return Math.max(START_SCALE, data.size * 1.06 / Math.max(1, Math.min(width, height)));
	}

	private void zoomAt(double mx, double my, double factor) {
		MapData data = MapClient.data();
		if (data == null) {
			return;
		}
		double worldX = centerX + (mx - width / 2.0) * scale;
		double worldZ = centerZ + (my - height / 2.0) * scale;
		scale = Math.max(MIN_SCALE, Math.min(maxScale(data), scale * factor));
		centerX = worldX - (mx - width / 2.0) * scale;
		centerZ = worldZ - (my - height / 2.0) * scale;
	}

	private void clampView(MapData data) {
		scale = Math.max(MIN_SCALE, Math.min(maxScale(data), scale));
		centerX = Math.max(data.originX, Math.min(data.originX + data.size, centerX));
		centerZ = Math.max(data.originZ, Math.min(data.originZ + data.size, centerZ));
	}

	private void togglePin(double mx, double my) {
		MapData data = MapClient.data();
		MapMemory memory = MapClient.memory();
		if (data == null || memory == null) {
			return;
		}
		MapProjection projection = MapProjection.northUp(width, height, centerX, centerZ, scale);
		if (memory.hasPin()) {
			double pinX = projection.screenX(memory.pinX() + data.originX, memory.pinZ() + data.originZ);
			double pinY = projection.screenY(memory.pinX() + data.originX, memory.pinZ() + data.originZ);
			if (Math.abs(pinX - mx) <= 8 && Math.abs(pinY - my) <= 8) {
				memory.clearPin();
				return;
			}
		}
		double worldX = projection.worldX(mx, my);
		double worldZ = projection.worldZ(mx, my);
		if (data.inside((int) Math.floor(worldX), (int) Math.floor(worldZ))) {
			memory.setPin((int) Math.floor(worldX) - data.originX, (int) Math.floor(worldZ) - data.originZ);
		}
	}

	private boolean labelVisible(MapPlaces.Place place, MapData data, MapMemory memory, boolean fog) {
		switch (place.kind()) {
			case MapPlaces.SPAWN:
				return true;
			case MapPlaces.REGION:
				if (scale < 1.5) {
					return false;
				}
				break;
			default:
				if (scale > 7.0) {
					return false;
				}
				break;
		}
		return !fog || memory.isExploredAt(place.x() - data.originX, place.z() - data.originZ);
	}

	private String cursorInfo(MapData data, MapMemory memory, boolean fog, MapProjection projection, int mouseX, int mouseY) {
		int x = (int) Math.floor(projection.worldX(mouseX, mouseY));
		int z = (int) Math.floor(projection.worldZ(mouseX, mouseY));
		String coordinates = Component.translatable("map.cursed_lands.coordinates", x, z).getString();
		if (!data.inside(x, z)) {
			return coordinates + "  " + Component.translatable("map.cursed_lands.outside").getString();
		}
		if (fog && !memory.isExploredAt(x - data.originX, z - data.originZ)) {
			return coordinates + "  " + Component.translatable("map.cursed_lands.unexplored").getString();
		}
		int ground = data.ground(x, z);
		int water = data.water(x, z);
		return coordinates + "  " + Component.translatable("map.cursed_lands.height", Math.max(ground, water)).getString()
				+ "  " + MapClient.biomeName(x, z).getString();
	}

	private int barHeight() {
		layoutButtons();
		return 14 + rows * (BUTTON_HEIGHT + BUTTON_GAP) + BUTTON_GAP;
	}

	private void layoutButtons() {
		int count = buttons.size();
		int widest = 40;
		for (Btn button : buttons) {
			widest = Math.max(widest, font.width(button.label().get()) + 14);
		}
		buttonWidth = Math.min(widest, Math.max(40, width - 8));
		int perRow = Math.max(1, (width - BUTTON_GAP) / (buttonWidth + BUTTON_GAP));
		rows = Math.max(1, (count + perRow - 1) / perRow);
		if (buttonX.length != count) {
			buttonX = new int[count];
			buttonY = new int[count];
		}
		int barTop = height - (14 + rows * (BUTTON_HEIGHT + BUTTON_GAP) + BUTTON_GAP);
		for (int row = 0; row < rows; row++) {
			int inRow = Math.min(perRow, count - row * perRow);
			int rowWidth = inRow * buttonWidth + (inRow - 1) * BUTTON_GAP;
			int startX = (width - rowWidth) / 2;
			for (int i = 0; i < inRow; i++) {
				int index = row * perRow + i;
				buttonX[index] = startX + i * (buttonWidth + BUTTON_GAP);
				buttonY[index] = barTop + 14 + row * (BUTTON_HEIGHT + BUTTON_GAP);
			}
		}
	}
}
