package com.easypia;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * 사랑이(권총) 서버 처리
 * - 좌클릭 발사: 7 피해, 머리 11 피해, 애쉬 파티클 궤적
 * - 탄창 17발 (내구도 막대로 표시), 다 쓰면 1.5초 자동 장전
 * - 조준(우클릭) 중이면 탄퍼짐 감소
 * - R키: 뒤로 대쉬 (재사용 대기 4초). 대쉬 후 2초 안에 맞히면 피해 2배 + 구속 2 (6초)
 */
public class GunHandler {
	public static final int MAGAZINE = 17;
	public static final int FIRE_INTERVAL = 4;          // 발사 간격 (틱)
	public static final int RELOAD_TICKS = 30;          // 장전 1.5초
	public static final int DASH_COOLDOWN = 4 * 20;     // 4초
	private static final int BUFF_TICKS = 2 * 20;       // 대쉬 후 2초
	private static final double RANGE = 64.0;
	private static final float DAMAGE = 7.0F;
	private static final float HEAD_DAMAGE = 11.0F;
	private static final float SPREAD_HIP = 2.5F;       // 도 (조준 안 함)
	private static final float SPREAD_AIM = 0.5F;       // 도 (조준 중)

	private static final ResourceKey<DamageType> BULLET =
		ResourceKey.create(Registries.DAMAGE_TYPE, Identifier.fromNamespaceAndPath(EasypiaMod.MOD_ID, "bullet"));
	private static final SoundEvent FIRE_SOUND =
		SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("minecraft", "block.ancient_debris.break"));
	private static final SoundEvent DASH_SOUND =
		SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("minecraft", "entity.breeze.slide"));

	private static final Map<UUID, Long> LAST_SHOT = new HashMap<>();
	private static final Map<UUID, Long> LAST_DASH = new HashMap<>();
	private static final Map<UUID, Integer> RELOADING = new HashMap<>();

	public static void initialize() {
		PayloadTypeRegistry.serverboundPlay().register(FirePayload.TYPE, FirePayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(FirePayload.TYPE, (payload, context) -> fire(context.player()));

		ServerTickEvents.END_SERVER_TICK.register(GunHandler::tickReload);

		// 총을 들고 좌클릭해도 블록을 부수지 않게
		AttackBlockCallback.EVENT.register((player, level, hand, pos, direction) ->
			player.getMainHandItem().is(ModItems.SARANGI) ? InteractionResult.FAIL : InteractionResult.PASS);
		PlayerBlockBreakEvents.BEFORE.register((level, player, pos, state, blockEntity) ->
			!player.getMainHandItem().is(ModItems.SARANGI));
	}

	public static boolean isAiming(Player player) {
		return player.isUsingItem() && player.getUseItem().is(ModItems.SARANGI);
	}

	// ---------------- 발사 ----------------
	private static void fire(ServerPlayer player) {
		if (player == null || !player.isAlive() || player.isSpectator()) return;
		ItemStack stack = player.getMainHandItem();
		if (!stack.is(ModItems.SARANGI)) return;

		ServerLevel level = (ServerLevel) player.level();
		long now = level.getGameTime();
		UUID id = player.getUUID();
		if (RELOADING.containsKey(id)) return;
		Long last = LAST_SHOT.get(id);
		if (last != null && now - last < FIRE_INTERVAL) return;

		int used = stack.getDamageValue();
		if (used >= MAGAZINE) {
			RELOADING.put(id, 0);
			return;
		}
		LAST_SHOT.put(id, now);
		stack.setDamageValue(used + 1);
		if (used + 1 >= MAGAZINE) RELOADING.put(id, 0);   // 마지막 발 -> 자동 장전

		// 탄퍼짐
		RandomSource random = player.getRandom();
		float spread = isAiming(player) ? SPREAD_AIM : SPREAD_HIP;
		Vec3 dir = Vec3.directionFromRotation(
			player.getXRot() + (random.nextFloat() * 2.0F - 1.0F) * spread,
			player.getYRot() + (random.nextFloat() * 2.0F - 1.0F) * spread);
		Vec3 start = player.getEyePosition();
		Vec3 end = start.add(dir.scale(RANGE));

		// 블록에 막히는 지점
		BlockHitResult blockHit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
		Vec3 stop = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();

		// 그 전에 맞는 엔티티 (가장 가까운 것)
		LivingEntity target = null;
		Vec3 hitPos = null;
		double best = start.distanceToSqr(stop);
		AABB path = new AABB(start, stop).inflate(1.0);
		for (Entity e : level.getEntities(player, path, e -> e instanceof LivingEntity le && le.isAlive() && !e.isSpectator())) {
			Optional<Vec3> hit = e.getBoundingBox().inflate(0.15).clip(start, stop);
			if (hit.isPresent()) {
				double d = start.distanceToSqr(hit.get());
				if (d < best) {
					best = d;
					target = (LivingEntity) e;
					hitPos = hit.get();
				}
			}
		}
		if (hitPos != null) stop = hitPos;

		// 총알 궤적: 애쉬 파티클 (눈앞 1.2칸부터 그려서 시야를 가리지 않음)
		Vec3 seg = stop.subtract(start);
		double len = seg.length();
		Vec3 step = len > 0 ? seg.scale(1.0 / len) : dir;
		for (double d = 1.2; d < len; d += 0.5) {
			Vec3 p = start.add(step.scale(d));
			level.sendParticles(ParticleTypes.ASH, p.x, p.y, p.z, 2, 0.02, 0.02, 0.02, 0.0);
		}

		// 발사 소리: minecraft:block.ancient_debris.break, 볼륨 10, 피치 2
		level.playSound((Player) null, player.getX(), player.getY(), player.getZ(),
			FIRE_SOUND, SoundSource.PLAYERS, 10.0F, 2.0F);

		if (target != null) {
			boolean headshot = hitPos.y >= target.getEyeY() - 0.2;
			float damage = headshot ? HEAD_DAMAGE : DAMAGE;
			Long dashed = LAST_DASH.get(id);
			boolean buffed = dashed != null && now - dashed <= BUFF_TICKS;
			if (buffed) damage *= 2.0F;

			DamageSource source = new DamageSource(
				level.registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(BULLET), player);
			target.hurtServer(level, source, damage);

			if (buffed) {
				// 구속 2 = amplifier 1, 6초
				target.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 6 * 20, 1));
			}
		}
	}

	// ---------------- 장전 ----------------
	private static void tickReload(MinecraftServer server) {
		if (RELOADING.isEmpty()) return;
		Iterator<Map.Entry<UUID, Integer>> it = RELOADING.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<UUID, Integer> entry = it.next();
			ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
			if (player == null) {
				it.remove();
				continue;
			}
			ItemStack stack = player.getMainHandItem();
			if (!stack.is(ModItems.SARANGI)) continue;   // 총을 들고 있을 때만 장전 진행

			int progress = entry.getValue() + 1;
			if (progress >= RELOAD_TICKS) {
				stack.setDamageValue(0);
				it.remove();
			} else {
				entry.setValue(progress);
				// 내구도 막대가 차오르는 모습으로 장전 진행 표시
				stack.setDamageValue(MAGAZINE - (progress * MAGAZINE) / RELOAD_TICKS);
			}
		}
	}

	// ---------------- R키 뒤로 대쉬 ----------------
	public static void startDash(ServerPlayer player) {
		if (player == null || player.isSpectator() || !player.isAlive()) return;
		ItemStack stack = player.getMainHandItem();
		if (!stack.is(ModItems.SARANGI)) return;
		if (player.getCooldowns().getCooldownPercent(stack, 0.0F) > 0.0F) return;

		player.getCooldowns().addCooldown(stack, DASH_COOLDOWN);
		ServerLevel level = (ServerLevel) player.level();
		LAST_DASH.put(player.getUUID(), level.getGameTime());

		// 소리: minecraft:entity.breeze.slide, 볼륨 10, 피치 2
		level.playSound((Player) null, player.getX(), player.getY(), player.getZ(),
			DASH_SOUND, SoundSource.PLAYERS, 10.0F, 2.0F);

		// 발밑 구름 + 앞쪽(대쉬 반대 방향)으로 날아가는 구름
		level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.1, player.getZ(),
			12, 0.4, 0.02, 0.4, 0.02);
		Vec3 look = player.getLookAngle();
		Vec3 fwd = new Vec3(look.x, 0, look.z);
		if (fwd.lengthSqr() < 1.0E-4) fwd = new Vec3(0, 0, 1);
		fwd = fwd.normalize();
		for (int i = 0; i < 6; i++) {
			double spread = (i - 2.5) * 0.25;
			double dx = fwd.x - fwd.z * spread;
			double dz = fwd.z + fwd.x * spread;
			double dy = 0.1 + level.getRandom().nextDouble() * 0.2;
			level.sendParticles(ParticleTypes.CLOUD, player.getX(), player.getY() + 0.3, player.getZ(),
				0, dx, dy, dz, 0.35);
		}
	}
}
