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
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 서버 쪽 대쉬 처리
 *
 * - 이동(밀어내기)은 클라이언트가 하고, 서버는 대쉬하는 동안
 *   플레이어 주변 엔티티에게 피해 + 파티클을 처리합니다.
 * - 한 번 대쉬할 때 같은 엔티티는 한 번만 맞습니다.
 */
public class DashHandler {
	/** 대쉬 판정이 유지되는 시간 (틱, 20틱 = 1초) */
	public static final int DASH_TICKS = 12;
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

		// 출발 소리: minecraft:entity.breeze.wind_burst, 볼륨 10, 피치 2
		level.playSound((Player) null, player.getX(), player.getY(), player.getZ(),
			DASH_SOUND, SoundSource.PLAYERS, 10.0F, 2.0F);

		// 출발할 때만 바닥에 구름 파티클
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(),
			14, 0.45, 0.02, 0.45, 0.02);

		// 뒤쪽으로 날아가는 구름 몇 개 (count 0 = 지정한 방향으로 날림)
		Vec3 look = player.getLookAngle();
		Vec3 back = new Vec3(-look.x, 0, -look.z);
		if (back.lengthSqr() < 1.0E-4) back = new Vec3(0, 0, 1);
		back = back.normalize();
		for (int i = 0; i < 6; i++) {
			double spread = (i - 2.5) * 0.25;
			double dx = back.x - back.z * spread;
			double dz = back.z + back.x * spread;
			double dy = 0.15 + level.getRandom().nextDouble() * 0.2;
			level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.2, player.getZ(),
				0, dx, dy, dz, 0.35);
		}
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

				// 검궤적 3개 (칼날 색 더스트 파티클)
				spawnSlashes(level, player, target);
			}
		}
	}

	private static final SoundEvent DASH_SOUND =
		SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("minecraft", "entity.breeze.wind_burst"));

	/** 칼날 그라데이션과 같은 색: 진한 에메랄드 -> 민트 -> 거의 흰 민트 */
	private static final int[] SLASH_COLORS = {0x269E6E, 0x68D8AE, 0xD6FCEE};
	/** 검궤적 3개의 기울기 (도) */
	private static final double[] SLASH_TILTS = {-35.0, 0.0, 35.0};
	private static final int SLASH_POINTS = 20;

	/**
	 * 대상 몸 앞을 가로지르는 초승달 모양 검궤적 3개를 더스트 파티클로 그립니다.
	 * 가운데는 밝고 크게, 끝으로 갈수록 진하고 작게.
	 */
	private static void spawnSlashes(ServerLevel level, ServerPlayer player, LivingEntity target) {
		Vec3 center = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ());
		Vec3 toTarget = new Vec3(center.x - player.getX(), 0, center.z - player.getZ());
		if (toTarget.lengthSqr() < 1.0E-4) toTarget = player.getLookAngle().multiply(1, 0, 1);
		if (toTarget.lengthSqr() < 1.0E-4) toTarget = new Vec3(0, 0, 1);
		Vec3 forward = toTarget.normalize();
		Vec3 right = new Vec3(-forward.z, 0, forward.x);
		Vec3 up = new Vec3(0, 1, 0);

		double radius = Math.max(1.2, target.getBbWidth() * 0.5 + 0.9);

		for (double tiltDeg : SLASH_TILTS) {
			double tilt = Math.toRadians(tiltDeg);
			// 검이 지나가는 방향 (오른쪽을 기준으로 기울임)
			Vec3 axis = right.scale(Math.cos(tilt)).add(up.scale(Math.sin(tilt)));

			for (int i = 0; i < SLASH_POINTS; i++) {
				double t = i / (double) (SLASH_POINTS - 1);      // 0..1
				double theta = (t - 0.5) * 2.2;                   // -1.1..1.1 라디안
				double edge = Math.abs(t - 0.5) * 2.0;              // 0 = 가운데, 1 = 끝
				Vec3 p = center
					.add(axis.scale(radius * Math.sin(theta)))
					.add(forward.scale(-radius * (1.0 - Math.cos(theta)) * 0.5));

				int color = edge < 0.35 ? SLASH_COLORS[2] : (edge < 0.7 ? SLASH_COLORS[1] : SLASH_COLORS[0]);
				float scale = (float) (2.6 - edge * 1.4);          // 가운데 2.6, 끝 1.2

				level.sendParticles(new DustParticleOptions(color, scale), p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
			}
		}
	}
}
