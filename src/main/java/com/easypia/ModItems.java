package com.easypia;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Unit;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.TooltipDisplay;

public class ModItems {
	/**
	 * 이지피아 (전용 3D 모델)
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
			// 내구도 무한은 유지하고, 툴팁의 "부서지지 않음" 문구만 숨깁니다
			.component(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.UNBREAKABLE, true))
			// 우클릭으로 방패처럼 막기 (성능은 바닐라 방패와 동일, 도끼 등으로 비활성화되지 않음)
			.component(DataComponents.BLOCKS_ATTACKS, undisableableShieldBlocking())
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

	/**
	 * 바닐라 방패와 같은 값으로 직접 만든 막기 설정 (disableCooldownScale 만 0).
	 *
	 * 주의: Items.SHIELD.components() 는 아이템 등록 중에는 아직 준비되지 않아서
	 * (Components not bound yet) 복사하면 게임이 켜지다 터집니다. 그래서 값을 직접 적습니다.
	 *
	 * - 막기 딜레이 0.25초, 정면 90도, 피해 100% 차단 (바닐라 방패와 동일)
	 * - disableCooldownScale = 0 이면 도끼 등 어떤 공격으로도 막기가 비활성화되지 않습니다.
	 * - 내구도는 무한이라 막다가 부서지지도 않습니다.
	 */
	private static BlocksAttacks undisableableShieldBlocking() {
		return new BlocksAttacks(
			0.25F,
			0.0F,
			List.of(new BlocksAttacks.DamageReduction(90.0F, Optional.empty(), 0.0F, 1.0F)),
			new BlocksAttacks.ItemDamageFunction(3.0F, 1.0F, 1.0F),
			Optional.of(DamageTypeTags.BYPASSES_SHIELD),
			Optional.of(SoundEvents.SHIELD_BLOCK),
			Optional.of(SoundEvents.SHIELD_BREAK)
		);
	}

	public static <T extends Item> T register(String name, Function<Item.Properties, T> itemFactory, Item.Properties settings) {
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(EasypiaMod.MOD_ID, name));
		T item = itemFactory.apply(settings.setId(itemKey));
		Registry.register(BuiltInRegistries.ITEM, itemKey, item);
		return item;
	}

	/**
	 * 이름 색: 왼쪽은 진한 초록, 오른쪽으로 갈수록 칼날 색에 가까운 연한 민트 초록
	 */
	private static final String EASYPIA_NAME = "이지피아";
	private static final int[] EASYPIA_NAME_COLORS = {
		0x1E8C5A, // 이 - 진한 초록
		0x34B07A, // 지
		0x62D3A2, // 피
		0x9EEBCB  // 아 - 연한 민트 초록
	};

	private static Component gradientName() {
		MutableComponent name = Component.empty();
		for (int i = 0; i < EASYPIA_NAME.length(); i++) {
			int color = EASYPIA_NAME_COLORS[Math.min(i, EASYPIA_NAME_COLORS.length - 1)];
			name.append(Component.literal(String.valueOf(EASYPIA_NAME.charAt(i))).withColor(color));
		}
		return name;
	}

	public static void initialize() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, STELLIVE_TAB_KEY, STELLIVE_TAB);

		// 아이템 기본 이름을 그라데이션 이름으로 교체
		DefaultItemComponentEvents.MODIFY.register(context ->
			context.modify(EASYPIA, builder -> builder.set(DataComponents.ITEM_NAME, gradientName()))
		);
	}
}
