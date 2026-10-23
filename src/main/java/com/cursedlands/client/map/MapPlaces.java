package com.cursedlands.client.map;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import com.cursedlands.world.MapData;

/** Named places shown as labels on the full map, read from {@code cursed_lands_map/places.json}. */
public final class MapPlaces {
	public static final String SPAWN = "spawn";
	public static final String REGION = "region";
	public static final String LANDMARK = "landmark";

	/** A place. The name is the lang key {@code map.cursed_lands.place.<id>}. */
	public record Place(String id, int x, int z, String kind) {
	}

	private final List<Place> places;

	private MapPlaces(List<Place> places) {
		this.places = Collections.unmodifiableList(places);
	}

	public List<Place> all() {
		return places;
	}

	/** Reads the file from the mod jar; a missing or broken file gives an empty list (the map works without labels). */
	public static MapPlaces load() {
		try (InputStream in = MapPlaces.class.getResourceAsStream(MapData.RESOURCE_DIR + "places.json")) {
			if (in == null) {
				return new MapPlaces(new ArrayList<>());
			}
			return parse(new InputStreamReader(in, StandardCharsets.UTF_8));
		} catch (IOException | RuntimeException e) {
			return new MapPlaces(new ArrayList<>());
		}
	}

	static MapPlaces parse(java.io.Reader reader) {
		List<Place> list = new ArrayList<>();
		JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
		for (JsonElement element : root.getAsJsonArray("places")) {
			JsonObject o = element.getAsJsonObject();
			list.add(new Place(o.get("id").getAsString(), o.get("x").getAsInt(), o.get("z").getAsInt(), o.get("kind").getAsString()));
		}
		return new MapPlaces(list);
	}
}
