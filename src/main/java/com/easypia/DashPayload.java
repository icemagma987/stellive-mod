package com.easypia;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * 클라이언트(R키) -> 서버 : "대쉬 시작" 신호. 담는 데이터는 없습니다.
 */
public record DashPayload() implements CustomPacketPayload {
	public static final DashPayload INSTANCE = new DashPayload();

	public static final CustomPacketPayload.Type<DashPayload> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(EasypiaMod.MOD_ID, "dash"));

	public static final StreamCodec<RegistryFriendlyByteBuf, DashPayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
