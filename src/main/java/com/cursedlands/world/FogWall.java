package com.cursedlands.world;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec3;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

/**
 * The edge of the finite map. The fog biomes make the fog dense towards the edge; here the players who walk
 * too far into it are brought back: closer than {@link MapData#borderMargin} blocks to the edge a player is put
 * {@link #PUSH} blocks further inside, facing the middle of the map. Spectators may pass.
 */
public final class FogWall {
	/** How often the players are checked, in ticks. */
	private static final int CHECK_INTERVAL = 4;
	/** How far inside the turn-back line a player is put. */
	private static final int PUSH = 28;
	/** Width of the band in front of the line where the fog also darkens the screen. */
	private static final int DARKNESS_BAND = 70;

	private FogWall() {
	}

	public static void initialize() {
		ServerTickEvents.END_WORLD_TICK.register(FogWall::tick);
	}

	private static void tick(ServerLevel level) {
		if (level.getGameTime() % CHECK_INTERVAL != 0 || !(level.getChunkSource().getGenerator() instanceof MapChunkGenerator)) {
			return;
		}
		MapData map = MapData.get();
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) {
				continue;
			}
			double toLine = map.edgeDistance(player.getX(), player.getZ()) - map.borderMargin;
			if (toLine < 0) {
				turnBack(level, player, map);
			} else if (toLine < DARKNESS_BAND) {
				player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 60, 0, false, false, false));
			}
		}
	}

	private static void turnBack(ServerLevel level, ServerPlayer player, MapData map) {
		double inset = map.borderMargin + PUSH;
		double x = Math.max(map.originX + inset, Math.min(map.originX + map.size - 1 - inset, player.getX()));
		double z = Math.max(map.originZ + inset, Math.min(map.originZ + map.size - 1 - inset, player.getZ()));
		double dx = map.originX + map.size / 2.0 - x;
		double dz = map.originZ + map.size / 2.0 - z;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		double y = Math.max(map.ground(bx, bz), map.water(bx, bz)) + 1;
		if (player.isFallFlying()) {
			player.stopFallFlying();
		}
		player.teleport(new TeleportTransition(level, new Vec3(x, y, z), Vec3.ZERO, yaw, 0.0F, TeleportTransition.DO_NOTHING));
		player.displayClientMessage(Component.translatable("message.cursed_lands.fog_turnback"), true);
	}
}
