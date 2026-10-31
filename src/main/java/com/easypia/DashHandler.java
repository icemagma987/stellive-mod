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
	public static final int DASH_TICKS = 16;
	/** 대쉬 재사용 대기시간 (틱) */
	public static final int COOLDOWN_TICKS = 13 * 20; // 13초
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

	public static void initialize() {
		PayloadTypeRegistry.serverboundPlay().register(DashPayload.TYPE, DashPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(DashPayload.TYPE, (payload, context) -> {
			// R키: 들고 있는 아이템에 따라 다른 스킬
			if (context.player().getMainHandItem().is(ModItems.HAMMARIN)) {
				HammarinHandler.start(context.player());
			} else if (context.player().getMainHandItem().is(ModItems.SARANGI)) {
				GunHandler.startDash(context.player());
			} else {
				startDash(context.player());
			}
		});

		ServerTickEvents.END_SERVER_TICK.register(DashHandler::tick);
	}

	private static void startDash(ServerPlayer player) {
		if (player == null || player.isSpectator() || !player.isAlive()) return;
		// 이지피아를 주 손에 들고 있을 때만
		if (!player.getMainHandItem().is(ModItems.EASYPIA)) return;

		// 쿨타임 확인: 아이템 칸에 표시되는 쿨타임과 똑같은 값을 씀
		// (예전에는 서버와 클라이언트가 시간을 따로 세서, 일시정지나 렉이 있으면
		//  클라이언트는 대쉬하는데 서버는 쿨타임이라 거절 -> 피해/소리/파티클이 안 나오는 문제가 있었음)
		if (player.getCooldowns().getCooldownPercent(player.getMainHandItem(), 0.0F) > 0.0F) return;

		ServerLevel level = (ServerLevel) player.level();

		// 엔더 진주처럼 아이템 칸에 쿨타임 표시 (서버가 걸면 클라이언트에 자동으로 전달됨)
		player.getCooldowns().addCooldown(player.getMainHandItem(), COOLDOWN_TICKS);

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

				// 맞힐 때 소리: minecraft:entity.player.attack.sweep, 볼륨 10, 피치 2
				level.playSound((Player) null, target.getX(), target.getY(), target.getZ(),
					HIT_SOUND, SoundSource.PLAYERS, 10.0F, 2.0F);

				// 검궤적 3개 (칼날 색 더스트 파티클)
				spawnSlashes(level, player, target);
			}
		}
	}

	private static final SoundEvent HIT_SOUND =
		SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("minecraft", "entity.player.attack.sweep"));

	private static final SoundEvent DASH_SOUND =
		SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("minecraft", "entity.breeze.wind_burst"));

	/** 칼날 그라데이션과 같은 색: 진한 에메랄드 -> 민트 -> 거의 흰 민트 */
	private static final int[] SLASH_COLORS = {0x269E6E, 0x68D8AE, 0xD6FCEE};
	/** 검궤적 3개의 기울기 (도) */
	private static final double[] SLASH_TILTS = {-35.0, 0.0, 35.0};
	private static final int SLASH_POINTS = 32;
	/** 검궤적 두께: 나란한 줄 3개 사이 간격 */
	private static final double[] SLASH_ROWS = {-0.13, 0.0, 0.13};

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

		double radius = Math.max(1.6, target.getBbWidth() * 0.5 + 1.3);

		for (double tiltDeg : SLASH_TILTS) {
			double tilt = Math.toRadians(tiltDeg);
			// 검이 지나가는 방향 (오른쪽을 기준으로 기울임)
			Vec3 axis = right.scale(Math.cos(tilt)).add(up.scale(Math.sin(tilt)));
			// 두께 방향 (검궤적 면에 수직)
			Vec3 normal = axis.cross(forward).normalize();

			for (int i = 0; i < SLASH_POINTS; i++) {
				double t = i / (double) (SLASH_POINTS - 1);      // 0..1
				double theta = (t - 0.5) * 3.0;                   // -1.5..1.5 라디안 (더 긴 궤적)
				double edge = Math.abs(t - 0.5) * 2.0;              // 0 = 가운데, 1 = 끝
				Vec3 p = center
					.add(axis.scale(radius * Math.sin(theta)))
					.add(forward.scale(-radius * (1.0 - Math.cos(theta)) * 0.5));

				int color = edge < 0.35 ? SLASH_COLORS[2] : (edge < 0.7 ? SLASH_COLORS[1] : SLASH_COLORS[0]);
				float scale = (float) (3.0 - edge * 1.6);          // 가운데 3.0, 끝 1.4

				DustParticleOptions dust = new DustParticleOptions(color, scale);
				for (double row : SLASH_ROWS) {
					// 끝으로 갈수록 가늘어지게
					Vec3 q = p.add(normal.scale(row * (1.0 - edge * 0.6)));
					level.sendParticles(dust, q.x, q.y, q.z, 1, 0.0, 0.0, 0.0, 0.0);
				}
			}
		}
	}
}
