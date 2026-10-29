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
 */
public class EasypiaClient implements ClientModInitializer {
	/** 대쉬 속도 (블록/틱). 1.1 x 6틱 = 약 6~7칸 */
	private static final double DASH_SPEED = 1.1;
	/** 실제로 밀어주는 시간 (틱) */
	private static final int PUSH_TICKS = 6;

	private static final KeyMapping.Category CATEGORY =
		KeyMapping.Category.register(Identifier.fromNamespaceAndPath(EasypiaMod.MOD_ID, "stellive"));

	private static KeyMapping dashKey;

	private int pushTicksLeft = 0;
	private Vec3 dashDir = Vec3.ZERO;
	private long lastDashTick = -1000;
	private long clientTicks = 0;

	@Override
	public void onInitializeClient() {
		dashKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
			"key.stellive.dash",
			InputConstants.Type.KEYSYM,
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
			if (pushTicksLeft > 0) continue;

			lastDashTick = clientTicks;
			Vec3 look = player.getLookAngle();
			// 위/아래로 너무 날아가지 않게 세로 방향은 조금만
			double y = Math.max(-0.3, Math.min(0.35, look.y));
			dashDir = new Vec3(look.x, y, look.z).normalize();
			pushTicksLeft = PUSH_TICKS;

			ClientPlayNetworking.send(DashPayload.INSTANCE);
		}

		if (player == null) {
			pushTicksLeft = 0;
			return;
		}

		if (pushTicksLeft > 0) {
			pushTicksLeft--;
			if (pushTicksLeft > 0) {
				player.setDeltaMovement(dashDir.scale(DASH_SPEED));
			} else {
				// 대쉬가 끝나면 너무 멀리 미끄러지지 않게 속도를 줄임
				player.setDeltaMovement(dashDir.scale(0.25));
			}
			player.resetFallDistance(); // 대쉬 중 낙하 피해 방지
		}
	}
}
