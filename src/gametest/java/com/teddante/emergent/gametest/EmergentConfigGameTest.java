package com.teddante.emergent.gametest;

import com.teddante.emergent.EmergentConfig;
import com.teddante.emergent.EnvironmentalExposure;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

import java.lang.reflect.Method;
import java.util.List;

public class EmergentConfigGameTest {
    private static final BlockPos TEST_POS = new BlockPos(2, 3, 2);

    @GameTest(maxTicks = 20)
    public void unrestrictedEnchantmentsToggleAllowsExclusiveEnchantments(GameTestHelper context) {
        boolean unrestrictedEnchantments = EmergentConfig.get().unrestrictedEnchantments;
        Holder.Reference<Enchantment> sharpness = context.getLevel()
                .registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SHARPNESS);
        Holder.Reference<Enchantment> smite = context.getLevel()
                .registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SMITE);

        try {
            EmergentConfig.get().unrestrictedEnchantments = false;
            context.assertFalse(EnchantmentHelper.isEnchantmentCompatible(List.of(sharpness), smite),
                    "disabled unrestricted enchantments should keep vanilla exclusive enchantment rules");

            EmergentConfig.get().unrestrictedEnchantments = true;
            context.assertTrue(EnchantmentHelper.isEnchantmentCompatible(List.of(sharpness), smite),
                    "enabled unrestricted enchantments should allow otherwise exclusive enchantments");
        } finally {
            EmergentConfig.get().unrestrictedEnchantments = unrestrictedEnchantments;
        }

        context.succeed();
    }

    @GameTest(maxTicks = 40)
    public void materialReactionsToggleDisablesFireTickHeating(GameTestHelper context) throws ReflectiveOperationException {
        BlockPos firePos = TEST_POS;
        BlockPos stonePos = firePos.relative(Direction.EAST);
        boolean materialReactions = EmergentConfig.get().materialReactions;

        try {
            EmergentConfig.get().materialReactions = false;
            context.setBlock(firePos.below(), Blocks.NETHERRACK);
            context.setBlock(firePos, Blocks.FIRE.defaultBlockState());
            context.setBlock(stonePos, Blocks.STONE);

            invokeFireTick(context.getLevel(), context.absolutePos(firePos), context.getBlockState(firePos));

            context.assertTrue(
                    EnvironmentalExposure.heat(context.getLevel(), context.absolutePos(stonePos), context.getBlockState(stonePos)) == 0.0,
                    "disabled material reactions should stop fire ticks from writing stored heat");
        } finally {
            EmergentConfig.get().materialReactions = materialReactions;
        }

        context.succeed();
    }

    private static void invokeFireTick(ServerLevel level, BlockPos pos, BlockState state) throws ReflectiveOperationException {
        Method tick = FireBlock.class.getDeclaredMethod("tick", BlockState.class, ServerLevel.class, BlockPos.class, RandomSource.class);
        tick.setAccessible(true);
        tick.invoke(Blocks.FIRE, state, level, pos, RandomSource.create(7));
    }
}
