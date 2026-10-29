package com.easypia.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.easypia.ModItems;

import net.minecraft.world.item.ItemCooldowns;
import net.minecraft.world.item.ItemStack;

/**
 * 이지피아의 스킬 쿨타임은 아이템 칸에 "표시"만 하고,
 * 우클릭 막기는 쿨타임 중에도 계속 쓸 수 있게 합니다.
 *
 * (바닐라 쿨타임은 원래 우클릭 사용을 막지만, 쿨타임 표시는
 *  getCooldownPercent 를 쓰기 때문에 isOnCooldown 만 false 로 바꿔도 표시는 그대로 나옵니다.)
 */
@Mixin(ItemCooldowns.class)
public class ItemCooldownsMixin {
	@Inject(method = "isOnCooldown", at = @At("HEAD"), cancellable = true)
	private void stellive$skillCooldownDoesNotBlockUse(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (stack.is(ModItems.EASYPIA)) {
			cir.setReturnValue(false);
		}
	}
}
