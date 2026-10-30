package com.easypia;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 건틀릿 효과
 * - 주 손 이지피아 + 왼손 건틀릿이면 저항 1, 속도 1 (조건이 풀리면 바로 사라짐)
 */
public class GauntletHandler {
	private static int tickCounter = 0;

	public static void initialize() {
		ServerTickEvents.END_SERVER_TICK.register(GauntletHandler::tick);
	}

	private static void tick(MinecraftServer server) {
		if (++tickCounter % 10 != 0) return; // 0.5초마다 확인

		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			boolean active = player.isAlive()
				&& player.getOffhandItem().is(ModItems.GAUNTLET)
				&& player.getMainHandItem().is(ModItems.EASYPIA);

			update(player, MobEffects.RESISTANCE, active);
			update(player, MobEffects.SPEED, active);
		}
	}

	/** 건틀릿이 준 효과는 "무한 + 은은한(ambient)" 으로 표시해서 구분합니다. */
	private static void update(ServerPlayer player, Holder<MobEffect> effect, boolean active) {
		MobEffectInstance current = player.getEffect(effect);
		boolean ours = current != null && current.isAmbient() && current.isInfiniteDuration() && current.getAmplifier() == 0;

		if (active) {
			if (current == null) {
				// 레벨 1 = amplifier 0, 무한 지속, 파티클 없음, 아이콘 표시
				player.addEffect(new MobEffectInstance(effect, MobEffectInstance.INFINITE_DURATION, 0, true, false, true));
			}
		} else if (ours) {
			player.removeEffect(effect);
		}
	}

	/** 공격한 상대가 무기를 들고 있는지 */
	public static boolean isHoldingWeapon(LivingEntity attacker) {
		ItemStack stack = attacker.getMainHandItem();
		if (stack.isEmpty()) return false;
		return stack.has(DataComponents.WEAPON)
			|| stack.is(ItemTags.SWORDS)
			|| stack.is(ItemTags.AXES)
			|| stack.is(Items.BOW)
			|| stack.is(Items.CROSSBOW)
			|| stack.is(Items.TRIDENT)
			|| stack.is(Items.MACE);
	}
}
