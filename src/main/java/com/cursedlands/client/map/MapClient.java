package com.cursedlands.client.map;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.loader.api.FabricLoader;

import com.cursedlands.CursedLands;
import com.cursedlands.network.MapDimensionsPayload;
import com.cursedlands.world.MapData;

/**
 * The in-game map: a minimap on the HUD and a full-screen map on a key. This class holds the state shared by both
 * (what the player has explored, settings, tiles of the picture) and runs the keys and the saving.
 *
 * <p>The map exists only in dimensions that the server reports as built by the Cursed Lands map generator
 * ({@link MapDimensionsPayload}); elsewhere nothing is drawn and the keys do nothing.
 */
public final class MapClient {
	/** Blocks around the player that count as seen. */
	private static final double REVEAL_RADIUS = 56.0;
	private static final long SAVE_INTERVAL_MS = 60_000L;
	private static final int TILE_THREADS = 2;

	private static MapSettings settings = new MapSettings();
	private static MapPlaces places;
	private static Set<String> dimensions = Set.of();
	private static ClientLevel checkedLevel;
	private static boolean checkedActive;
	private static boolean failed;

	private static MapTiles tiles;
	private static MapView view;
	private static MapMemory memory;
	private static Path memoryFile;
	private static String memoryKey = "";
	private static int savedRevision;
	private static long lastSave;

	private MapClient() {
	}

	/** Registers keys, the minimap and the packet receiver. Called once from the client entry point. */
	public static void initialize() {
		settings = MapSettings.load(settingsFile());
		places = MapPlaces.load();
		MapKeys.register();
		ClientPlayNetworking.registerGlobalReceiver(MapDimensionsPayload.TYPE, (payload, context) -> setDimensions(payload.dimensions()));
		ClientTickEvents.END_CLIENT_TICK.register(MapClient::tick);
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> disconnect());
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, CursedLands.id("minimap"), MinimapHud::render);
	}

	static MapSettings settings() {
		return settings;
	}

	static MapPlaces places() {
		return places;
	}

	/** The picture of the map, {@code null} while the map is not active. */
	static MapView view() {
		return view;
	}

	static MapData data() {
		return tiles == null ? null : tiles.map();
	}

	/** What the player has explored, {@code null} while the map is not active. */
	static MapMemory memory() {
		return memory;
	}

	/** True if the player is in a dimension that has the map. */
	static boolean isActive(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null || failed) {
			return false;
		}
		if (level != checkedLevel) {
			checkedLevel = level;
			checkedActive = dimensions.contains(level.dimension().identifier().toString());
		}
		return checkedActive;
	}

	/** True if the map is ready to draw (active and loaded by the first tick). */
	static boolean isReady(Minecraft mc) {
		return mc.player != null && isActive(mc) && view != null && memory != null;
	}

	/**
	 * Switches the map off until the next world after an error in it, so that a fault of the map never takes the game down
	 * (the map is only a convenience, and it has not been tried in every situation).
	 */
	static void fail(String where, RuntimeException e) {
		if (!failed) {
			CursedLands.LOGGER.error("The map failed in {} and is switched off until the next world", where, e);
		}
		failed = true;
	}

	static void saveSettings() {
		try {
			settings.save(settingsFile());
		} catch (IOException e) {
			CursedLands.LOGGER.warn("Could not save the map settings", e);
		}
	}

	/** Name of the biome at the block, for the screen. */
	static Component biomeName(int x, int z) {
		MapData data = data();
		if (data == null || !data.inside(x, z)) {
			return Component.translatable("map.cursed_lands.outside");
		}
		int index = data.biome(x >> 2, z >> 2);
		String name = index >= 0 && index < data.biomes.size() ? data.biomes.get(index) : data.biomes.get(0);
		if (name.startsWith("fog_")) {
			return Component.translatable("map.cursed_lands.fog");
		}
		return Component.translatable("biome.cursed_lands." + name);
	}

	private static void setDimensions(String list) {
		dimensions = Arrays.stream(list.split(";")).filter(id -> !id.isEmpty()).collect(Collectors.toUnmodifiableSet());
		checkedLevel = null;
	}

	private static void tick(Minecraft mc) {
		try {
			tickMap(mc);
		} catch (RuntimeException e) {
			fail("tick", e);
		}
	}

	private static void tickMap(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (player == null || !isActive(mc)) {
			leaveDimension();
			return;
		}
		if (tiles == null) {
			tiles = new MapTiles(MapData.get(), TILE_THREADS);
			view = new MapView(tiles);
			tiles.warmUp();
		}
		String key = keyFor(mc);
		if (!key.equals(memoryKey)) {
			enterDimension(key);
		}

		MapData data = tiles.map();
		memory.reveal(player.getX() - data.originX, player.getZ() - data.originZ, REVEAL_RADIUS);

		boolean changed = false;
		while (MapKeys.zoomIn.consumeClick()) {
			settings.zoomIn();
			changed = true;
		}
		while (MapKeys.zoomOut.consumeClick()) {
			settings.zoomOut();
			changed = true;
		}
		while (MapKeys.toggleMinimap.consumeClick()) {
			settings.minimapVisible = !settings.minimapVisible;
			changed = true;
		}
		if (changed) {
			saveSettings();
		}
		while (MapKeys.openMap.consumeClick()) {
			if (mc.screen == null) {
				mc.setScreen(new WorldMapScreen());
			}
		}

		long now = System.currentTimeMillis();
		if (now - lastSave > SAVE_INTERVAL_MS) {
			saveMemory();
			lastSave = now;
		}
	}

	private static void enterDimension(String key) {
		leaveDimension();
		MapData data = tiles.map();
		Path file = FabricLoader.getInstance().getGameDir().resolve("cursed_lands_map").resolve(key + ".clx");
		MapMemory loaded = new MapMemory(data.size);
		if (Files.isRegularFile(file)) {
			try (DataInputStream in = new DataInputStream(new BufferedInputStream(new GZIPInputStream(Files.newInputStream(file))))) {
				loaded.read(in);
			} catch (IOException e) {
				CursedLands.LOGGER.warn("Could not read the explored area of the map from {}, starting anew", file, e);
				loaded = new MapMemory(data.size);
			}
		}
		memory = loaded;
		memoryFile = file;
		memoryKey = key;
		savedRevision = memory.revision();
		lastSave = System.currentTimeMillis();
	}

	private static void leaveDimension() {
		if (memory == null) {
			return;
		}
		saveMemory();
		memory = null;
		memoryFile = null;
		memoryKey = "";
	}

	private static void saveMemory() {
		if (memory == null || memoryFile == null || memory.revision() == savedRevision) {
			return;
		}
		Path temporary = memoryFile.resolveSibling(memoryFile.getFileName() + ".tmp");
		try {
			Files.createDirectories(memoryFile.getParent());
			try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(temporary))))) {
				memory.write(out);
			}
			Files.move(temporary, memoryFile, StandardCopyOption.REPLACE_EXISTING);
			savedRevision = memory.revision();
		} catch (IOException e) {
			CursedLands.LOGGER.warn("Could not save the explored area of the map to {}", memoryFile, e);
		}
	}

	private static void disconnect() {
		leaveDimension();
		if (tiles != null) {
			tiles.close();
			tiles = null;
			view = null;
		}
		dimensions = Set.of();
		checkedLevel = null;
		failed = false;
		MinimapHud.release();
	}

	/** Key of the explored area: the world (a save folder or a server address) and the dimension. */
	private static String keyFor(Minecraft mc) {
		String world;
		MinecraftServer server = mc.getSingleplayerServer();
		if (server != null) {
			Path name = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize().getFileName();
			world = "world_" + (name == null ? "unknown" : name.toString());
		} else {
			ServerData data = mc.getCurrentServer();
			world = "server_" + (data == null ? "unknown" : data.ip);
		}
		String key = world + "__" + mc.level.dimension().identifier();
		key = key.replaceAll("[^A-Za-z0-9._-]", "_");
		return key.length() > 100 ? key.substring(0, 100) : key;
	}

	private static Path settingsFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("cursed_lands_map.properties");
	}
}
