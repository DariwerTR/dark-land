package com.cursedlands.client.map;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Choices of the player about the map, kept in a small properties file. Pure code: the file is given by the caller. */
public final class MapSettings {
	/** Blocks per pixel of the minimap zoom modes, from the closest to the widest. */
	public static final double[] ZOOMS = {0.5, 1.0, 2.0, 4.0, 8.0};
	/** Side of the minimap in GUI pixels. */
	public static final int[] SIZES = {96, 128, 176};
	/** Corners of the minimap: top left, top right, bottom left, bottom right. */
	public static final int CORNERS = 4;

	public boolean minimapVisible = true;
	public boolean round = true;
	/** True: the top of the minimap is where the player looks; false: north is at the top. */
	public boolean rotate = false;
	public boolean fogOfWar = true;
	public int zoom = 1;
	public int size = 1;
	public int corner = 1;

	public double blocksPerPixel() {
		return ZOOMS[zoom];
	}

	public int pixels() {
		return SIZES[size];
	}

	public void zoomIn() {
		zoom = Math.max(0, zoom - 1);
	}

	public void zoomOut() {
		zoom = Math.min(ZOOMS.length - 1, zoom + 1);
	}

	public void cycleSize() {
		size = (size + 1) % SIZES.length;
	}

	public void cycleCorner() {
		corner = (corner + 1) % CORNERS;
	}

	/** Reads the file if it exists; a missing or damaged file leaves the defaults. */
	public static MapSettings load(Path file) {
		MapSettings settings = new MapSettings();
		if (!Files.isRegularFile(file)) {
			return settings;
		}
		Properties p = new Properties();
		try (InputStream in = Files.newInputStream(file)) {
			p.load(in);
		} catch (IOException | IllegalArgumentException e) {
			return settings;
		}
		settings.minimapVisible = bool(p, "minimap", settings.minimapVisible);
		settings.round = bool(p, "round", settings.round);
		settings.rotate = bool(p, "rotate", settings.rotate);
		settings.fogOfWar = bool(p, "fog_of_war", settings.fogOfWar);
		settings.zoom = integer(p, "zoom", settings.zoom, ZOOMS.length);
		settings.size = integer(p, "size", settings.size, SIZES.length);
		settings.corner = integer(p, "corner", settings.corner, CORNERS);
		return settings;
	}

	public void save(Path file) throws IOException {
		Properties p = new Properties();
		p.setProperty("minimap", Boolean.toString(minimapVisible));
		p.setProperty("round", Boolean.toString(round));
		p.setProperty("rotate", Boolean.toString(rotate));
		p.setProperty("fog_of_war", Boolean.toString(fogOfWar));
		p.setProperty("zoom", Integer.toString(zoom));
		p.setProperty("size", Integer.toString(size));
		p.setProperty("corner", Integer.toString(corner));
		Path parent = file.getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
		try (OutputStream out = Files.newOutputStream(file)) {
			p.store(out, "Cursed Lands map: zoom 0-4 (blocks per pixel 0.5 1 2 4 8), size 0-2, corner 0-3 (top left, top right, bottom left, bottom right)");
		}
	}

	private static boolean bool(Properties p, String key, boolean fallback) {
		String value = p.getProperty(key);
		return value == null ? fallback : Boolean.parseBoolean(value.trim());
	}

	private static int integer(Properties p, String key, int fallback, int count) {
		String value = p.getProperty(key);
		if (value == null) {
			return fallback;
		}
		try {
			int v = Integer.parseInt(value.trim());
			return v >= 0 && v < count ? v : fallback;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}
}
