package com.easypia;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.mojang.serialization.Codec;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * 피아 광석 생성
 * - 한 번 생길 때 1개(80%) 또는 붙어 있는 2개(20%)
 * - 일반 돌(minecraft:stone)만 바꿈
 * - "청크당 70% 확률로 한 번"은 placed_feature/pia_ore.json 에서 정합니다
 */
public class PiaOreFeature extends Feature<NoneFeatureConfiguration> {
	public PiaOreFeature(Codec<NoneFeatureConfiguration> codec) {
		super(codec);
	}

	@Override
	public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
		WorldGenLevel level = context.level();
		BlockPos origin = context.origin();
		RandomSource random = context.random();

		if (!level.getBlockState(origin).is(Blocks.STONE)) return false;
		level.setBlock(origin, ModBlocks.PIA_ORE.defaultBlockState(), 2);

		// 20% 확률로 바로 옆에 하나 더
		if (random.nextFloat() < 0.2F) {
			List<Direction> dirs = new ArrayList<>(List.of(Direction.values()));
			Collections.shuffle(dirs, new java.util.Random(random.nextLong()));
			for (Direction dir : dirs) {
				BlockPos next = origin.relative(dir);
				if (level.getBlockState(next).is(Blocks.STONE)) {
					level.setBlock(next, ModBlocks.PIA_ORE.defaultBlockState(), 2);
					break;
				}
			}
		}
		return true;
	}
}
