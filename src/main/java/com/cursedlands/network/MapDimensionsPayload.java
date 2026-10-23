package com.cursedlands.network;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

import com.cursedlands.CursedLands;
import com.cursedlands.world.MapChunkGenerator;

/**
 * Sent to a player who joins: which dimensions of this world are built by the Cursed Lands map generator (ids separated by
 * semicolons). The client cannot see the generator, and the in-game map only makes sense in these dimensions.
 */
public record MapDimensionsPayload(String dimensions) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<MapDimensionsPayload> TYPE = new CustomPacketPayload.Type<>(CursedLands.id("map_dimensions"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MapDimensionsPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.STRING_UTF8, MapDimensionsPayload::dimensions, MapDimensionsPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}

	/** The payload for the dimensions of this server that use {@link MapChunkGenerator}. */
	public static MapDimensionsPayload of(MinecraftServer server) {
		StringBuilder ids = new StringBuilder();
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getChunkSource().getGenerator() instanceof MapChunkGenerator) {
				if (ids.length() > 0) {
					ids.append(';');
				}
				ids.append(level.dimension().identifier());
			}
		}
		return new MapDimensionsPayload(ids.toString());
	}
}
