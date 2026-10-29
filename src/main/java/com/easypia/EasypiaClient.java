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
import net.minecraft.world.phys.Vec3;

/**
 * 클라이언트 쪽: R키 입력 + 플레이어 이동(대쉬)
 *
 * 대쉬 속도 표 (블록/틱). 앞부분은 일정한 속도, 끝부분은 부드럽게 감속해서
 * 중간에 뚝 끊기는 느낌이 없도록 했습니다. 합계 약 8.3칸 (이전의 약 1.5배)
 */
public class EasypiaClient implements ClientModInitializer {
	private static final double[] DASH_SPEEDS = {1.1, 1.1, 1.1, 1.1, 1.1, 1.1, 0.8, 0.5, 0.3};

	private static final KeyMapping.Category CATEGORY =
		KeyMapping.Category.register(Identifier.fromNamespaceAndPath(EasypiaMod.MOD_ID, "stellive"));

	private static KeyMapping dashKey;

	private int dashStep = -1;          // -1 = 대쉬 중 아님
	private Vec3 dashDir = Vec3.ZERO;   // 가로 방향 (길이 1)
	private double dashUp = 0;          // 세로 성분
	private long lastDashTick = -1000;
	private long clientTicks = 0;

	@Override
	public void onInitializeClient() {
		dashKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.stellive.dash",
			InputConstants.KEY_R,
			CATEGORY
		));

		ClientTickEvents.END_CLIENT_TICK.register(this::onTick);
	}

	private void onTick(Minecraft client) {
		clientTicks++;
		LocalPlayer player = client.player;

		while (dashKey.consumeClick()) {
			if (player == null || player.isSpectator()) continue;
			if (!player.getMainHandItem().is(ModItems.EASYPIA)) continue;
			if (clientTicks - lastDashTick < DashHandler.COOLDOWN_TICKS) continue;
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
			return;
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
}
