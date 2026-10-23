package com.cursedlands.registry;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import com.cursedlands.network.MapDimensionsPayload;

/** Packets of the mod. */
public final class ModNetworking {
	private ModNetworking() {
	}

	public static void initialize() {
		PayloadTypeRegistry.playS2C().register(MapDimensionsPayload.TYPE, MapDimensionsPayload.CODEC);
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> sender.sendPacket(MapDimensionsPayload.of(server)));
	}
}
