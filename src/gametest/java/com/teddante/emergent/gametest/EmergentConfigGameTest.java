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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;

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

    @GameTest(maxTicks = 40)
    public void materialReactionsToggleEnablesFirePlacementHeating(GameTestHelper context) {
        BlockPos firePos = TEST_POS;
        BlockPos stonePos = firePos.relative(Direction.EAST);
        boolean materialReactions = EmergentConfig.get().materialReactions;

        try {
            EmergentConfig.get().materialReactions = true;
            context.setBlock(firePos.below(), Blocks.NETHERRACK);
            context.setBlock(stonePos, Blocks.STONE);
            context.setBlock(firePos, Blocks.FIRE.defaultBlockState());

            context.assertTrue(
                    EnvironmentalExposure.heat(context.getLevel(), context.absolutePos(stonePos), context.getBlockState(stonePos)) > 0.0,
                    "enabled material reactions should let fire placement write stored heat");
        } finally {
            EmergentConfig.get().materialReactions = materialReactions;
        }

        context.succeed();
    }

    @GameTest(maxTicks = 40)
    public void materialReactionsToggleGatesEntityTrafficWear(GameTestHelper context) {
        BlockPos disabledPos = TEST_POS;
        BlockPos enabledPos = TEST_POS.relative(Direction.EAST, 3);
        boolean materialReactions = EmergentConfig.get().materialReactions;

        try {
            context.setBlock(disabledPos, Blocks.GRASS_BLOCK);
            context.setBlock(disabledPos.above(), Blocks.AIR);
            EmergentConfig.get().materialReactions = false;
            walkCowOver(context, disabledPos, 160);

            context.setBlock(enabledPos, Blocks.GRASS_BLOCK);
            context.setBlock(enabledPos.above(), Blocks.AIR);
            EmergentConfig.get().materialReactions = true;
            walkCowOver(context, enabledPos, 160);

            context.assertBlockPresent(Blocks.GRASS_BLOCK, disabledPos);
            context.assertBlockPresent(Blocks.DIRT_PATH, enabledPos);
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

    private static void walkCowOver(GameTestHelper context, BlockPos groundPos, int steps) {
        Cow cow = context.spawn(EntityType.COW, Vec3.atBottomCenterOf(groundPos.above()));
        for (int i = 0; i < steps; i++) {
            double direction = i % 2 == 0 ? 1.0 : -1.0;
            Vec3 movement = new Vec3(direction * 0.2, -0.05, 0.0);
            cow.setOnGround(true);
            cow.setDeltaMovement(movement);
            cow.move(MoverType.SELF, movement);
        }
        cow.discard();
    }
}
