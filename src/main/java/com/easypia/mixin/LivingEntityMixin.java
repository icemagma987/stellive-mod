package com.easypia.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.easypia.GauntletHandler;
import com.easypia.ModItems;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * 건틀릿을 왼손에 든 엔티티는, 무기를 든 상대에게 받는 피해가 50% 줄어듭니다.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@WrapMethod(method = "hurtServer")
	private boolean stellive$gauntletReducesWeaponDamage(ServerLevel level, DamageSource source, float amount, Operation<Boolean> original) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (self.getOffhandItem().is(ModItems.GAUNTLET)
			&& source.getEntity() instanceof LivingEntity attacker
			&& attacker != self
			&& GauntletHandler.isHoldingWeapon(attacker)) {
			amount *= 0.5F;
		}
		return original.call(level, source, amount);
	}

	/**
	 * 이지피아 + 건틀릿으로 공격하면 도끼처럼 상대 방패를 5초 동안 못 쓰게 합니다.
	 * (이지피아 자신의 막기는 disableCooldownScale 0 이라 깨지지 않음)
	 */
	@Inject(method = "getSecondsToDisableBlocking", at = @At("RETURN"), cancellable = true)
	private void stellive$easypiaSetBreaksShields(CallbackInfoReturnable<Float> cir) {
		LivingEntity self = (LivingEntity) (Object) this;
		if (GauntletHandler.hasSet(self) && cir.getReturnValue() < 5.0F) {
			cir.setReturnValue(5.0F);
		}
	}
}
