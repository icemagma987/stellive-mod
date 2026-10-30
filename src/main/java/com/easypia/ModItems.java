package com.easypia;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.item.v1.DefaultItemComponentEvents;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Unit;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ToolMaterial;
import net.minecraft.world.item.component.BlocksAttacks;
import net.minecraft.world.item.component.ItemAttributeModifiers;
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

	/** 피아 (피아 광석에서 나오는 재료) */
	public static final Item PIA = register("pia", Item::new, new Item.Properties());

	/**
	 * 함마린 (빗자루)
	 * - 공격력 5.5 = 1(기본) + 4.5,  공격속도 1.7 = 4.0 - 2.3
	 * - 내구도 무한 (툴팁 숨김), 인챈트 불가
	 * - R키 스킬 (HammarinHandler)
	 */
	public static final Item HAMMARIN = register("hammarin", Item::new,
		new Item.Properties()
			.stacksTo(1)
			.component(DataComponents.UNBREAKABLE, Unit.INSTANCE)
			.component(DataComponents.TOOLTIP_DISPLAY, TooltipDisplay.DEFAULT.withHidden(DataComponents.UNBREAKABLE, true))
			.attributes(ItemAttributeModifiers.builder()
				.add(Attributes.ATTACK_DAMAGE,
					new AttributeModifier(Identifier.fromNamespaceAndPath("minecraft", "base_attack_damage"), 4.5, AttributeModifier.Operation.ADD_VALUE),
					EquipmentSlotGroup.MAINHAND)
				.add(Attributes.ATTACK_SPEED,
					new AttributeModifier(Identifier.fromNamespaceAndPath("minecraft", "base_attack_speed"), -2.3, AttributeModifier.Operation.ADD_VALUE),
					EquipmentSlotGroup.MAINHAND)
				.build()));

	/**
	 * 건틀릿
	 * - 왼손(보조 손)에 들면 공격 피해 +2
	 * - 주 손 이지피아 + 왼손 건틀릿 = 저항 1, 속도 1 (GauntletHandler)
	 * - 무기를 든 상대에게 받는 피해 50% 감소 (mixin/LivingEntityMixin)
	 */
	public static final Item GAUNTLET = register("gauntlet", Item::new,
		new Item.Properties()
			.stacksTo(1)
			.attributes(ItemAttributeModifiers.builder()
				.add(Attributes.ATTACK_DAMAGE,
					new AttributeModifier(Identifier.fromNamespaceAndPath(EasypiaMod.MOD_ID, "gauntlet_attack_damage"),
						2.0, AttributeModifier.Operation.ADD_VALUE),
					EquipmentSlotGroup.OFFHAND)
				.build()));

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
			output.accept(HAMMARIN);
			output.accept(GAUNTLET);
			output.accept(PIA);
			output.accept(ModBlocks.PIA_ORE_ITEM);
		})
		.build();

	/**
	 * 바닐라 방패와 같은 값으로 직접 만든 막기 설정 (disableCooldownScale 만 0).
	 *
	 * 주의: Items.SHIELD.components() 는 아이템 등록 중에는 아직 준비되지 않아서
	 * (Components not bound yet) 복사하면 게임이 켜지다 터집니다. 그래서 값을 직접 적습니다.
	 *
	 * - 막기 딜레이 없음, 정면 90도, 피해 100% 차단 (바닐라 방패와 동일)
	 * - disableCooldownScale = 0 이면 도끼 등 어떤 공격으로도 막기가 비활성화되지 않습니다.
	 * - 내구도는 무한이라 막다가 부서지지도 않습니다.
	 */
	private static BlocksAttacks undisableableShieldBlocking() {
		return new BlocksAttacks(
			0.0F, // 막기 딜레이 없음 (우클릭 즉시 막힘)
			0.0F,
			List.of(new BlocksAttacks.DamageReduction(90.0F, Optional.empty(), 0.0F, 1.0F)),
			new BlocksAttacks.ItemDamageFunction(3.0F, 1.0F, 1.0F),
			Optional.empty(), // 26.3 에서는 태그가 아니라 HolderSet 이라 등록 시점에 만들 수 없음
			Optional.of(Holder.direct(SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(EasypiaMod.MOD_ID, "easypia.block")))), // 금속 소리 (sounds.json: block.chain.hit, 피치 2)
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

	/** 건틀릿 이름 색: 이지피아의 "지피아" 부분 색을 그대로 사용 */
	private static final String GAUNTLET_NAME = "건틀릿";
	private static final int[] GAUNTLET_NAME_COLORS = {
		0x34B07A, // 건 (= 지)
		0x62D3A2, // 틀 (= 피)
		0x9EEBCB  // 릿 (= 아)
	};

	/** 함마린 이름 색: 왼쪽 진한 파랑 -> 오른쪽 연한 하늘색 (리본 색) */
	private static final String HAMMARIN_NAME = "함마린";
	private static final int[] HAMMARIN_NAME_COLORS = {
		0x1E50C8, // 함
		0x4A8CF0, // 마
		0x9CC8FF  // 린
	};

	private static Component gradientName(String text, int[] colors) {
		MutableComponent name = Component.empty();
		for (int i = 0; i < text.length(); i++) {
			int color = colors[Math.min(i, colors.length - 1)];
			name.append(Component.literal(String.valueOf(text.charAt(i))).withColor(color));
		}
		return name;
	}

	public static void initialize() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, STELLIVE_TAB_KEY, STELLIVE_TAB);

		// 아이템 기본 이름을 그라데이션 이름으로 교체
		DefaultItemComponentEvents.MODIFY.register(context -> {
			context.modify(EASYPIA, builder -> builder.set(DataComponents.ITEM_NAME, gradientName(EASYPIA_NAME, EASYPIA_NAME_COLORS)));
			context.modify(HAMMARIN, builder -> builder.set(DataComponents.ITEM_NAME, gradientName(HAMMARIN_NAME, HAMMARIN_NAME_COLORS)));
			context.modify(GAUNTLET, builder -> builder.set(DataComponents.ITEM_NAME, gradientName(GAUNTLET_NAME, GAUNTLET_NAME_COLORS)));
		});
	}
}
