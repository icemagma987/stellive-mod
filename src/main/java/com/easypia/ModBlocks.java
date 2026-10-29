package com.easypia;

import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.material.MapColor;

public class ModBlocks {
	/**
	 * 피아 광석
	 * - 단단함 24 (고대 잔해 30의 0.8배)
	 * - 폭발 저항 1200 (고대 잔해와 같음) -> 폭발로 부서지지 않음
	 * - 네더라이트 곡괭이로만 캘 수 있음 (data/minecraft/tags/block/incorrect_for_*.json 참고)
	 */
	public static final Block PIA_ORE = register("pia_ore",
		BlockBehaviour.Properties.of()
			.mapColor(MapColor.STONE)
			.requiresCorrectToolForDrops()
			.strength(24.0F, 1200.0F)
			.sound(SoundType.STONE));

	public static final Item PIA_ORE_ITEM = registerBlockItem("pia_ore", PIA_ORE);

	/** 월드 생성 (일반 돌 속에만, 청크당 70% 확률로 한 번, data/stellive/worldgen 참고) */
	public static final ResourceKey<PlacedFeature> PIA_ORE_PLACED = ResourceKey.create(
		Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath(EasypiaMod.MOD_ID, "pia_ore"));

	private static Block register(String name, BlockBehaviour.Properties properties) {
		ResourceKey<Block> key = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(EasypiaMod.MOD_ID, name));
		Block block = new Block(properties.setId(key));
		return Registry.register(BuiltInRegistries.BLOCK, key, block);
	}

	private static Item registerBlockItem(String name, Block block) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(EasypiaMod.MOD_ID, name));
		Item item = new BlockItem(block, new Item.Properties().setId(key).useBlockDescriptionPrefix());
		return Registry.register(BuiltInRegistries.ITEM, key, item);
	}

	public static void initialize() {
		BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(),
			GenerationStep.Decoration.UNDERGROUND_ORES, PIA_ORE_PLACED);
	}
}
