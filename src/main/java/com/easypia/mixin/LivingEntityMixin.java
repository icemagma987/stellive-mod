package com.easypia.mixin;

import org.spongepowered.asm.mixin.Mixin;

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
}
