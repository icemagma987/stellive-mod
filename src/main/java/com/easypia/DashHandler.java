package com.easypia;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;

/**
 * 서버 쪽 대쉬 처리
 *
 * - 이동(밀어내기)은 클라이언트가 하고, 서버는 대쉬하는 동안
 *   플레이어 주변 엔티티에게 피해 + 파티클을 처리합니다.
 * - 한 번 대쉬할 때 같은 엔티티는 한 번만 맞습니다.
 */
public class DashHandler {
	/** 대쉬 판정이 유지되는 시간 (틱, 20틱 = 1초) */
	public static final int DASH_TICKS = 8;
	/** 대쉬 재사용 대기시간 (틱) */
	public static final int COOLDOWN_TICKS = 20;
	/** 플레이어 주변 몇 칸까지 맞는지 */
	private static final double HIT_RANGE = 1.5;
	private static final float DASH_DAMAGE = 22.0F;
	private static final float EXECUTE_DAMAGE = 999.0F;
	/** 체력이 이 비율 이하면 999 피해 */
	private static final float EXECUTE_HEALTH_RATIO = 0.3F;

	private static class DashState {
		int ticksLeft = DASH_TICKS;
		final Set<UUID> alreadyHit = new HashSet<>();
	}

	private static final Map<UUID, DashState> DASHING = new HashMap<>();
	private static final Map<UUID, Long> LAST_DASH = new HashMap<>();

	public static void initialize() {
		PayloadTypeRegistry.serverboundPlay().register(DashPayload.TYPE, DashPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(DashPayload.TYPE, (payload, context) -> startDash(context.player()));

		ServerTickEvents.END_SERVER_TICK.register(DashHandler::tick);
	}

	private static void startDash(ServerPlayer player) {
		if (player == null || player.isSpectator() || !player.isAlive()) return;
		// 이지피아를 주 손에 들고 있을 때만
		if (!player.getMainHandItem().is(ModItems.EASYPIA)) return;

		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();
		Long last = LAST_DASH.get(player.getUUID());
		if (last != null && now - last < COOLDOWN_TICKS) return;
		LAST_DASH.put(player.getUUID(), now);

		DASHING.put(player.getUUID(), new DashState());

		// 출발할 때 바닥에 구름 파티클 한 번 크게
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(),
			12, 0.4, 0.02, 0.4, 0.03);
	}

	private static void tick(MinecraftServer server) {
		if (DASHING.isEmpty()) return;

		Iterator<Map.Entry<UUID, DashState>> it = DASHING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, DashState> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			DashState state = entry.getValue();

			if (player == null || !player.isAlive() || state.ticksLeft-- <= 0) {
				it.remove();
				continue;
			}

			ServerLevel level = (ServerLevel) player.level();

			// 대쉬하는 동안 발밑에 구름 파티클
			level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(),
				4, 0.3, 0.02, 0.3, 0.02);

			// 근처 엔티티 공격
			AABB area = player.getBoundingBox().inflate(HIT_RANGE);
			List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, area,
				e -> e != player && e.isAlive() && !e.isSpectator() && !state.alreadyHit.contains(e.getUUID()));

			for (LivingEntity target : targets) {
				state.alreadyHit.add(target.getUUID());

				float damage = target.getHealth() <= target.getMaxHealth() * EXECUTE_HEALTH_RATIO
					? EXECUTE_DAMAGE
					: DASH_DAMAGE;

				target.hurtServer(level, player.damageSources().playerAttack(player), damage);

				// 검궤적 파티클 3개
				level.sendParticles(ParticleTypes.SWEEP_ATTACK,
					target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
					3, 0.5, 0.3, 0.5, 0.0);
			}
		}
	}
}
