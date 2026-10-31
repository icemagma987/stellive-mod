package com.easypia.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.easypia.GunHandler;

import net.minecraft.client.player.AbstractClientPlayer;

/** 사랑이 조준(우클릭) 중에는 화면을 살짝 확대합니다. */
@Mixin(AbstractClientPlayer.class)
public abstract class AbstractClientPlayerMixin {
	@Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
	private void stellive$aimZoom(CallbackInfoReturnable<Float> cir) {
		AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
		if (GunHandler.isAiming(self)) {
			cir.setReturnValue(cir.getReturnValue() * 0.75F);
		}
	}
}
