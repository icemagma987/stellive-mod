package com.easypia;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * 함마린 R키 스킬
 * - 3초 동안 신속 3
 * - 3초 동안 주변(3칸) 적에게 매 틱 0.15 피해 (3초 = 60틱 다 맞으면 9 피해)
 * - 0.5초마다 넉백, 맞은 적에게 크리티컬 파티클
 * - 재사용 대기 10초 (아이템 칸에 표시)
 */
public class HammarinHandler {
	public static final int SKILL_TICKS = 60;            // 3초
	public static final int COOLDOWN_TICKS = 10 * 20;    // 10초
	private static final float TOTAL_DAMAGE = 9.0F;
	private static final float DAMAGE_PER_TICK = TOTAL_DAMAGE / SKILL_TICKS; // 0.15
	private static final double RANGE = 3.0;
	private static final int KNOCKBACK_EVERY = 10;       // 0.5초마다
	private static final double KNOCKBACK_STRENGTH = 0.5;

	/** 매 틱 피해가 무적 시간에 막히지 않도록 만든 전용 피해 종류 (data/stellive/damage_type/hammarin.json) */
	private static final ResourceKey<DamageType> DAMAGE_TYPE =
		ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(EasypiaMod.MOD_ID, "hammarin"));

	private static final Map<UUID, Integer> ACTIVE = new HashMap<>();

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(HammarinHandler::tick);
	}

	public static void start(ServerPlayer player) {
		if (player == null || player.isSpectator() || !player.isAlive()) return;
		if (!player.getMainHandItem().is(ModItems.HAMMARIN)) return;
		if (player.getCooldowns().getCooldownPercent(player.getMainHandItem(), 0.0F) > 0.0F) return;

		player.getCooldowns().addCooldown(player.getMainHandItem(), COOLDOWN_TICKS);
		// 신속 3 = amplifier 2
		player.addEffect(new MobEffectInstance(MobEffects.SPEED, SKILL_TICKS, 2, false, false, true)); // 파티클 없음, 아이콘만 표시
		ACTIVE.put(player.getUUID(), 0);
	}

	private static void tick(MinecraftServer server) {
		if (ACTIVE.isEmpty()) return;

		Iterator<Map.Entry<UUID, Integer>> it = ACTIVE.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Integer> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			int t = entry.getValue();
			if (player == null || !player.isAlive() || t >= SKILL_TICKS) {
				it.remove();
				continue;
			}
			entry.setValue(t + 1);

			ServerLevel level = (ServerLevel) player.level();
			DamageSource source = new DamageSource(
				level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DAMAGE_TYPE), player);

			AABB area = player.getBoundingBox().inflate(RANGE);
			List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class, area,
				e -> e != player && e.isAlive() && !e.isSpectator() && isEnemy(e, player));

			for (LivingEntity target : targets) {
				target.hurtServer(level, source, DAMAGE_PER_TICK);

				if (t % KNOCKBACK_EVERY == 0) {
					// 플레이어 반대쪽으로 밀어냄
					Vec3 away = new Vec3(target.getX() - player.getX(), 0, target.getZ() - player.getZ());
					if (away.lengthSqr() < 1.0E-4) away = new Vec3(1, 0, 0);
					away = away.normalize().scale(KNOCKBACK_STRENGTH);
					target.push(away.x, target.onGround() ? 0.25 : 0.0, away.z);
				}

				level.sendParticles(ParticleTypes.CRIT,
					target.getX(), target.getY() + target.getBbHeight() * 0.5, target.getZ(),
					3, 0.3, 0.3, 0.3, 0.1);
			}
		}
	}

	/**
	 * 맞는 대상: 이지피아처럼 살아있는 엔티티 전부
	 * (아이템 액자, 그림 등은 원래 살아있는 엔티티가 아니라 안 맞음)
	 * 제외: 갑옷 거치대, 내가 길들인 동물
	 */
	private static final Identifier ARMOR_STAND_ID = Identifier.fromNamespaceAndPath("minecraft", "armor_stand");

	private static boolean isEnemy(LivingEntity e, ServerPlayer player) {
		if (ARMOR_STAND_ID.equals(BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()))) return false;
		if (e instanceof OwnableEntity owned && owned.getOwner() == player) return false;
		return true;
	}
}
