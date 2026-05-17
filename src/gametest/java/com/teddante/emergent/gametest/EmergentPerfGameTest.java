package com.teddante.emergent.gametest;

import com.teddante.emergent.EmergentConfig;
import com.teddante.emergent.EnvironmentalScheduler;
import com.teddante.emergent.TrafficWearPhysics;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

import java.lang.reflect.Method;

public class EmergentPerfGameTest implements CustomTestMethodInvoker {
    private static final boolean ENABLED = Boolean.getBoolean("emergent.perfScenarios");

    @GameTest(maxTicks = 100, padding = 6)
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

    @GameTest(maxTicks = 180, padding = 20)
    public void stressLargeSettlingFiniteWaterField(GameTestHelper context) {
        if (!ENABLED) {
            context.succeed();
            return;
        }

        BlockPos origin = new BlockPos(2, 3, 2);
        int size = 18;
        final int[] expectedWaterAmount = {0};
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
                int amount = 2 + Math.floorMod(x * 3 + z * 5 + (x / 8) + (z / 8), 7);
                context.setBlock(pos, Fluids.WATER.getFlowing(amount, false).createLegacyBlock());
                expectedWaterAmount[0] += amount;
                context.getLevel().scheduleTick(context.absolutePos(pos), Fluids.WATER, 1);
            }
        }

        for (int tick = 12; tick <= 72; tick += 12) {
            context.runAtTickTime(tick, () -> {
                for (int x = 0; x < size; x++) {
                    for (int z = 0; z < size; z++) {
                        context.getLevel().scheduleTick(context.absolutePos(origin.offset(x, 0, z)), Fluids.WATER, 1);
                    }
                }
            });
        }

        context.runAtTickTime(120, () -> {
            int actualWaterAmount = 0;
            for (int x = -4; x <= size + 4; x++) {
                for (int y = -2; y <= 2; y++) {
                    for (int z = -4; z <= size + 4; z++) {
                        actualWaterAmount += context.getBlockState(origin.offset(x, y, z)).getFluidState().getAmount();
                    }
                }
            }
            if (actualWaterAmount != expectedWaterAmount[0]) {
                context.fail("large finite water field did not conserve volume; expected="
                        + expectedWaterAmount[0] + " actual=" + actualWaterAmount);
                return;
            }
            for (int x = -1; x <= size; x++) {
                if (!context.getBlockState(origin.offset(x, 0, -1)).is(Blocks.STONE)
                        || !context.getBlockState(origin.offset(x, 0, size)).is(Blocks.STONE)) {
                    context.fail("large finite water field escaped its north/south basin wall");
                    return;
                }
            }
            for (int z = 0; z < size; z++) {
                if (!context.getBlockState(origin.offset(-1, 0, z)).is(Blocks.STONE)
                        || !context.getBlockState(origin.offset(size, 0, z)).is(Blocks.STONE)) {
                    context.fail("large finite water field escaped its east/west basin wall");
                    return;
                }
            }
        });

        context.runAtTickTime(130, () -> {
            for (int x = -1; x <= size; x++) {
                if (!context.getBlockState(origin.offset(x, 0, -1)).is(Blocks.STONE)
                        || !context.getBlockState(origin.offset(x, 0, size)).is(Blocks.STONE)) {
                    context.fail("large finite water field damaged its basin during settling");
                    return;
                }
            }
            for (int z = 0; z < size; z++) {
                if (!context.getBlockState(origin.offset(-1, 0, z)).is(Blocks.STONE)
                        || !context.getBlockState(origin.offset(size, 0, z)).is(Blocks.STONE)) {
                    context.fail("large finite water field damaged its basin during settling");
                    return;
                }
            }
        });

        context.runAtTickTime(140, () -> {
            int waterAmountBeforeWake = 0;
            for (int x = -4; x <= size + 4; x++) {
                for (int y = -2; y <= 2; y++) {
                    for (int z = -4; z <= size + 4; z++) {
                        waterAmountBeforeWake += context.getBlockState(origin.offset(x, y, z)).getFluidState().getAmount();
                    }
                }
            }
            if (waterAmountBeforeWake != expectedWaterAmount[0]) {
                context.fail("large finite water field lost volume before wake; expected="
                        + expectedWaterAmount[0] + " actual=" + waterAmountBeforeWake);
                return;
            }
            for (int x = 0; x < size; x++) {
                for (int z = 0; z < size; z++) {
                    context.getLevel().scheduleTick(context.absolutePos(origin.offset(x, 0, z)), Fluids.WATER, 1);
                }
            }
        });

        context.runAtTickTime(170, context::succeed);
    }

    @GameTest(maxTicks = 80, padding = 8)
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

    @GameTest(maxTicks = 100, padding = 6)
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

    @GameTest(maxTicks = 80, padding = 8)
    public void stressTrafficWearContactPatches(GameTestHelper context) {
        if (!ENABLED) {
            context.succeed();
            return;
        }

        BlockPos origin = new BlockPos(2, 3, 2);
        int size = 10;
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                BlockPos pos = origin.offset(x, 0, z);
                Block block = Math.floorMod(x + z, 4) == 0 ? Blocks.MUD
                        : Math.floorMod(x * 3 + z, 5) == 0 ? Blocks.FARMLAND
                        : Blocks.GRASS_BLOCK;
                context.setBlock(pos, block);
                context.setBlock(pos.above(), Blocks.AIR);
            }
        }

        for (int tick = 1; tick <= 36; tick++) {
            final int step = tick;
            context.runAtTickTime(tick, () -> {
                for (int lane = 0; lane < 4; lane++) {
                    int x = Math.floorMod(step + lane * 2, size - 2);
                    int z = Math.floorMod(step * 2 + lane * 3, size - 2);
                    BlockPos base = context.absolutePos(origin.offset(x, 0, z));
                    AABB footprint = new AABB(
                            base.getX(),
                            base.getY() + 1.0,
                            base.getZ(),
                            base.getX() + 2.4,
                            base.getY() + 2.8,
                            base.getZ() + 1.8);
                    TrafficWearPhysics.applyContactPatchTraffic(context.getLevel(), footprint, 1.8, 1.0);
                }
            });
        }

        context.runAtTickTime(60, () -> {
            int transformed = 0;
            for (int x = 0; x < size; x++) {
                for (int z = 0; z < size; z++) {
                    BlockState state = context.getBlockState(origin.offset(x, 0, z));
                    if (state.is(Blocks.DIRT_PATH) || state.is(Blocks.DIRT) || state.is(Blocks.PACKED_MUD)) {
                        transformed++;
                    }
                }
            }
            context.assertTrue(transformed > 0, "traffic stress should compact at least some walked surfaces");
            context.succeed();
        });
    }

    @GameTest(maxTicks = 120, padding = 6)
    public void stressThermalFluidReactions(GameTestHelper context) {
        if (!ENABLED) {
            context.succeed();
            return;
        }

        BlockPos origin = new BlockPos(2, 3, 2);
        int size = 8;
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                BlockPos pos = origin.offset(x, 0, z);
                context.setBlock(pos.below(), Blocks.STONE);
                context.setBlock(pos, Math.floorMod(x + z, 2) == 0 ? Blocks.LAVA : Blocks.WATER);
                context.getLevel().scheduleTick(context.absolutePos(pos), context.getBlockState(pos).getFluidState().getType(), 1);
            }
        }

        for (int tick = 8; tick <= 48; tick += 8) {
            context.runAtTickTime(tick, () -> {
                for (int x = 0; x < size; x++) {
                    for (int z = 0; z < size; z++) {
                        BlockPos pos = origin.offset(x, 0, z);
                        if (!context.getBlockState(pos).getFluidState().isEmpty()) {
                            context.getLevel().scheduleTick(
                                    context.absolutePos(pos),
                                    context.getBlockState(pos).getFluidState().getType(),
                                    1);
                        }
                    }
                }
            });
        }

        context.runAtTickTime(90, () -> {
            int solidified = 0;
            for (int x = 0; x < size; x++) {
                for (int z = 0; z < size; z++) {
                    BlockState state = context.getBlockState(origin.offset(x, 0, z));
                    if (state.is(Blocks.OBSIDIAN) || state.is(Blocks.COBBLESTONE) || state.is(Blocks.STONE)) {
                        solidified++;
                    }
                }
            }
            context.assertTrue(solidified > 0, "lava/water thermal stress should solidify at least some fluid cells");
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
