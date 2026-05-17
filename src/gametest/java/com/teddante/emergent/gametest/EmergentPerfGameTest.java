package com.teddante.emergent.gametest;

import com.teddante.emergent.EmergentConfig;
import com.teddante.emergent.EnvironmentalScheduler;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.material.Fluids;

import java.lang.reflect.Method;

public class EmergentPerfGameTest implements CustomTestMethodInvoker {
    private static final boolean ENABLED = Boolean.getBoolean("emergent.perfScenarios");

    @GameTest(maxTicks = 100)
    public void stressStableFiniteFluidWakeups(GameTestHelper context) {
        if (!ENABLED) {
            context.succeed();
            return;
        }

        BlockPos origin = new BlockPos(2, 3, 2);
        int size = 8;
        for (int x = -1; x <= size; x++) {
            context.setBlock(origin.offset(x, 0, -1), Blocks.STONE);
            context.setBlock(origin.offset(x, 0, size), Blocks.STONE);
        }
        for (int z = 0; z < size; z++) {
            context.setBlock(origin.offset(-1, 0, z), Blocks.STONE);
            context.setBlock(origin.offset(size, 0, z), Blocks.STONE);
        }

        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                BlockPos pos = origin.offset(x, 0, z);
                context.setBlock(pos.below(), Blocks.STONE);
                context.setBlock(pos, Blocks.LAVA);
            }
        }

        for (int tick = 1; tick <= 60; tick++) {
            context.runAtTickTime(tick, () -> {
                for (int x = 0; x < size; x++) {
                    for (int z = 0; z < size; z++) {
                        context.getLevel().scheduleTick(context.absolutePos(origin.offset(x, 0, z)), Fluids.LAVA, 1);
                    }
                }
            });
        }

        context.runAtTickTime(80, () -> {
            context.assertBlockPresent(Blocks.LAVA, origin.offset(size / 2, 0, size / 2));
            context.succeed();
        });
    }

    @GameTest(maxTicks = 80)
    public void stressSurfaceWeatherQueue(GameTestHelper context) {
        if (!ENABLED) {
            context.succeed();
            return;
        }

        BlockPos origin = new BlockPos(2, 3, 2);
        int size = 12;
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                BlockPos pos = origin.offset(x, 0, z);
                context.setBlock(pos, Blocks.GRASS_BLOCK);
                context.setBlock(pos.above(), Blocks.AIR);
                EnvironmentalScheduler.enqueueSurfaceWeatherSample(context.getLevel(), context.absolutePos(pos.above()));
            }
        }

        for (int tick = 1; tick <= 16; tick++) {
            context.runAtTickTime(tick, () -> EnvironmentalScheduler.tickWorldForTests(context.getLevel()));
        }

        context.runAtTickTime(24, () -> {
            context.succeed();
        });
    }

    @GameTest(maxTicks = 100)
    public void stressFireReactionScans(GameTestHelper context) {
        if (!ENABLED) {
            context.succeed();
            return;
        }

        BlockPos origin = new BlockPos(2, 3, 2);
        int size = 7;
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                BlockPos fuelPos = origin.offset(x, 0, z);
                BlockPos firePos = fuelPos.above();
                context.setBlock(fuelPos, Blocks.OAK_LOG);
                context.setBlock(firePos, Blocks.FIRE.defaultBlockState().setValue(FireBlock.AGE, 15));
            }
        }

        for (int tick = 1; tick <= 45; tick++) {
            context.runAtTickTime(tick, () -> {
                for (int x = 0; x < size; x++) {
                    for (int z = 0; z < size; z++) {
                        BlockPos firePos = origin.offset(x, 1, z);
                        if (!context.getBlockState(firePos).is(Blocks.FIRE)) {
                            context.setBlock(firePos, Blocks.FIRE.defaultBlockState().setValue(FireBlock.AGE, 15));
                        }
                        context.getLevel().scheduleTick(context.absolutePos(firePos), Blocks.FIRE, 1);
                    }
                }
            });
        }

        context.runAtTickTime(70, () -> {
            context.assertBlockPresent(Blocks.FIRE, origin.above());
            context.succeed();
        });
    }

    @Override
    public void invokeTestMethod(GameTestHelper context, Method method) throws ReflectiveOperationException {
        boolean finiteWaterFlow = EmergentConfig.get().finiteWaterFlow;
        boolean materialReactions = EmergentConfig.get().materialReactions;
        boolean rainAccumulation = EmergentConfig.get().rainAccumulation;

        EmergentConfig.get().finiteWaterFlow = true;
        EmergentConfig.get().materialReactions = true;
        EmergentConfig.get().rainAccumulation = true;

        try {
            method.invoke(this, context);
        } finally {
            EmergentConfig.get().finiteWaterFlow = finiteWaterFlow;
            EmergentConfig.get().materialReactions = materialReactions;
            EmergentConfig.get().rainAccumulation = rainAccumulation;
        }
    }
}
