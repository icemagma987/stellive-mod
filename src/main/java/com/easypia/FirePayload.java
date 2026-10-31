package com.easypia;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** 클라이언트(좌클릭) -> 서버 : "사랑이 발사" 신호 */
public record FirePayload() implements CustomPacketPayload {
	public static final FirePayload INSTANCE = new FirePayload();

	public static final CustomPacketPayload.Type<FirePayload> TYPE =
		new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(EasypiaMod.MOD_ID, "fire"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FirePayload> CODEC = StreamCodec.unit(INSTANCE);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
