package com.easypia;

import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Unit;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;

public class ModItems {
	/**
	 * 이지피아 (철검 텍스처)
	 *
	 * 검의 최종 공격력 = 1(기본) + attackDamage + 재질 보너스(철 = 2)
	 *   -> 1 + 8 + 2 = 11
	 * 공격속도 = 4.0(기본) + attackSpeed
	 *   -> 4.0 + (-2.4) = 1.6
	 * 내구도 무한 = UNBREAKABLE 컴포넌트
	 *
	 * 인챈트: 이 아이템은 바닐라 인챈트 대상 태그에 들어 있지 않아서
	 * 인챈트 테이블/모루로는 인챈트할 수 없습니다.
	 */
	public static final Item EASYPIA = register(
		"easypia",
		Item::new,
		new Item.Properties()
			.sword(ToolMaterial.IRON, 8.0F, -2.4F)
			.component(DataComponents.UNBREAKABLE, Unit.INSTANCE)
	);

	// 크리에이티브 인벤토리 전용 탭 (오른쪽 위 페이지 넘기기에서 보이는 탭)
	public static final ResourceKey<CreativeModeTab> STELLIVE_TAB_KEY = ResourceKey.create(
		BuiltInRegistries.CREATIVE_MODE_TAB.key(),
		Identifier.fromNamespaceAndPath(EasypiaMod.MOD_ID, "stellive")
	);

	public static final CreativeModeTab STELLIVE_TAB = FabricCreativeModeTab.builder()
		.icon(() -> new ItemStack(EASYPIA))
		.title(Component.translatable("creativeTab.stellive"))
		.displayItems((params, output) -> {
			output.accept(EASYPIA);
		})
		.build();

	public static <T extends Item> T register(String name, Function<Item.Properties, T> itemFactory, Item.Properties settings) {
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(EasypiaMod.MOD_ID, name));
		T item = itemFactory.apply(settings.setId(itemKey));
		Registry.register(BuiltInRegistries.ITEM, itemKey, item);
		return item;
	}

	public static void initialize() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, STELLIVE_TAB_KEY, STELLIVE_TAB);
	}
}
