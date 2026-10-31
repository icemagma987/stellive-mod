package com.easypia;

import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 클라이언트 쪽: R키 입력 + 플레이어 이동(대쉬)
 *
 * 대쉬 속도 표 (블록/틱). 앞부분은 일정한 속도, 끝부분은 부드럽게 감속해서
 * 중간에 뚝 끊기는 느낌이 없도록 했습니다. 합계 약 8.8칸
 */
public class EasypiaClient implements ClientModInitializer {
	private static final double[] DASH_SPEEDS = {
		1.1, 1.1, 1.1,                                   // 최고 속도 3틱
		1.0, 0.9, 0.8, 0.7, 0.6, 0.5, 0.4, 0.3, 0.2, 0.1 // 감속 10틱
	};

	private static final KeyMapping.Category CATEGORY =
		KeyMapping.Category.register(Identifier.fromNamespaceAndPath(EasypiaMod.MOD_ID, "stellive"));

	private static KeyMapping dashKey;

	private int dashStep = -1;          // -1 = 대쉬 중 아님
	private Vec3 dashDir = Vec3.ZERO;   // 가로 방향 (길이 1)
	private double dashUp = 0;          // 세로 성분
	private long lastDashTick = -1000;
	private long clientTicks = 0;

	// ---- 사랑이 (권총) ----
	/** 뒤로 대쉬 속도 (블록/틱): 부드럽게 감속 */
	private static final double[] BACK_SPEEDS = {1.0, 0.95, 0.85, 0.75, 0.65, 0.55, 0.45, 0.35, 0.25, 0.15, 0.08};
	/** 뒤로 대쉬 위쪽 속도: 처음에 살짝 떠오름 */
	private static final double[] BACK_UP = {0.34, 0.28, 0.22, 0.16, 0.11, 0.07, 0.04, 0.02, 0.0, 0.0, 0.0};
	private int backStep = -1;
	private Vec3 backDir = Vec3.ZERO;
	private long lastShotTick = -1000;
	private boolean reloading = false;

	@Override
	public void onInitializeClient() {
		dashKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.stellive.dash",
			InputConstants.KEY_R,
			CATEGORY
		));

		ClientTickEvents.START_CLIENT_TICK.register(this::onStartTick);
		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
	}

	private void onTick(Minecraft client) {
		clientTicks++;
		LocalPlayer player = client.player;

		while (dashKey.consumeClick()) {
			if (player == null || player.isSpectator()) continue;
			// 사랑이: 뒤로 대쉬
			if (player.getMainHandItem().is(ModItems.SARANGI)) {
				if (player.getCooldowns().getCooldownPercent(player.getMainHandItem(), 0.0F) > 0.0F) continue;
				if (backStep >= 0) continue;
				Vec3 look = player.getLookAngle();
				Vec3 flat = new Vec3(look.x, 0, look.z);
				if (flat.lengthSqr() < 1.0E-4) {
					float yaw = player.getYRot() * ((float) Math.PI / 180F);
					flat = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
				}
				backDir = flat.normalize().scale(-1.0);
				backStep = 0;
				ClientPlayNetworking.send(DashPayload.INSTANCE);
				continue;
			}
			// 함마린: 이동 없이 서버에 스킬 신호만 보냄
			if (player.getMainHandItem().is(ModItems.HAMMARIN)) {
				if (player.getCooldowns().getCooldownPercent(player.getMainHandItem(), 0.0F) <= 0.0F) {
					ClientPlayNetworking.send(DashPayload.INSTANCE);
				}
				continue;
			}
			if (!player.getMainHandItem().is(ModItems.EASYPIA)) continue;
			if (player.getCooldowns().getCooldownPercent(player.getMainHandItem(), 0.0F) > 0.0F) continue;
			if (dashStep >= 0) continue;

			lastDashTick = clientTicks;
			Vec3 look = player.getLookAngle();
			Vec3 flat = new Vec3(look.x, 0, look.z);
			if (flat.lengthSqr() < 1.0E-4) {
				// 바로 위/아래를 보고 있으면 몸이 향한 방향으로
				float yaw = player.getYRot() * ((float) Math.PI / 180F);
				flat = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
			}
			// 가로 속도는 항상 일정하게 (아래를 봐도 바닥에 막혀 느려지지 않도록)
			dashDir = flat.normalize();
			// 세로는 살짝만 따라감
			dashUp = Math.max(-0.2, Math.min(0.3, look.y * 0.4));
			dashStep = 0;

			ClientPlayNetworking.send(DashPayload.INSTANCE);
		}

		if (player == null) {
			dashStep = -1;
			backStep = -1;
			return;
		}

		// 사랑이 뒤로 대쉬 (매 틱 속도를 직접 정해서 끊기지 않게)
		if (backStep >= 0) {
			double speed = BACK_SPEEDS[backStep];
			player.setDeltaMovement(backDir.x * speed, BACK_UP[backStep], backDir.z * speed);
			player.resetFallDistance();
			backStep++;
			if (backStep >= BACK_SPEEDS.length) backStep = -1;
		}

		// 사랑이 장전 상태 (탄을 다 쓰면 장전 시작, 내구도 막대가 다 차면 끝)
		ItemStack held = player.getMainHandItem();
		if (held.is(ModItems.SARANGI)) {
			if (held.getDamageValue() >= held.getMaxDamage()) reloading = true;
			else if (held.getDamageValue() == 0) reloading = false;
		}

		if (dashStep >= 0) {
			double speed = DASH_SPEEDS[dashStep];
			double y = dashUp * speed;
			// 땅 위에서 아래로 향하면 바닥에 걸려 끊기므로 세로 속도를 0으로
			if (player.onGround() && y < 0) y = 0;
			player.setDeltaMovement(dashDir.x * speed, y, dashDir.z * speed);
			player.resetFallDistance(); // 대쉬 중 낙하 피해 방지

			dashStep++;
			if (dashStep >= DASH_SPEEDS.length) dashStep = -1;
		}
	}

	/**
	 * 사랑이 좌클릭 발사.
	 * 게임이 좌클릭을 처리하기 전(틱 시작)에 클릭을 가져가서 블록 부수기/근접 공격 대신 총을 쏩니다.
	 */
	private void onStartTick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || client.screen != null) return;
		ItemStack held = player.getMainHandItem();
		if (!held.is(ModItems.SARANGI)) return;

		boolean clicked = false;
		while (client.options.keyAttack.consumeClick()) clicked = true;
		if (!clicked || player.isSpectator()) return;

		if (clientTicks - lastShotTick < GunHandler.FIRE_INTERVAL) return;
		if (reloading || held.getDamageValue() >= held.getMaxDamage()) return;
		lastShotTick = clientTicks;

		ClientPlayNetworking.send(FirePayload.INSTANCE);

		// 반동: 화면이 위로 튀고 좌우로 살짝 흔들림 (조준 중이면 줄어듦)
		boolean aiming = GunHandler.isAiming(player);
		float pitchKick = aiming ? 1.0F : 2.8F;
		float yawKick = (player.getRandom().nextFloat() * 2.0F - 1.0F) * (aiming ? 0.3F : 0.9F);
		player.setXRot(player.getXRot() - pitchKick);
		player.setYRot(player.getYRot() + yawKick);
	}
}
