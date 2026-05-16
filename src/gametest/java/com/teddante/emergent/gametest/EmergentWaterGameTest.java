package com.teddante.emergent.gametest;

import com.teddante.emergent.EmergentConfig;
import com.teddante.emergent.ErosionPhysics;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import java.lang.reflect.Method;

public class EmergentWaterGameTest implements CustomTestMethodInvoker {
    private static final BlockPos WATER_POS = new BlockPos(2, 3, 2);
    private static final BlockPos BELOW_WATER_POS = WATER_POS.below();

    @GameTest(maxTicks = 40)
    public void finiteWaterPrefersDownwardFlow(GameTestHelper context) {
        context.setBlock(WATER_POS, Blocks.WATER.defaultBlockState());
        context.setBlock(BELOW_WATER_POS, Blocks.AIR);
        containCell(context, BELOW_WATER_POS);
        context.getLevel().scheduleTick(context.absolutePos(WATER_POS), Fluids.WATER, Fluids.WATER.getTickDelay(context.getLevel()));

        context.runAfterDelay(10, () -> {
            context.assertBlockPresent(Blocks.WATER, BELOW_WATER_POS);
            context.assertBlockPresent(Blocks.AIR, WATER_POS);
            context.assertTrue(context.getBlockState(BELOW_WATER_POS).getValue(LiquidBlock.LEVEL) == 0,
                    "finite water should move a source downward as a conserved source block");
            context.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void waterMassConservedWhenFallingIntoPartialColumn(GameTestHelper context) {
        context.setBlock(WATER_POS, Fluids.WATER.getFlowing(6, false).createLegacyBlock());
        context.setBlock(BELOW_WATER_POS, Fluids.WATER.getFlowing(5, false).createLegacyBlock());
        containCell(context, BELOW_WATER_POS);
        int initialMass = fluidAmount(context, WATER_POS, Fluids.WATER) + fluidAmount(context, BELOW_WATER_POS, Fluids.WATER);
        context.getLevel().scheduleTick(context.absolutePos(WATER_POS), Fluids.WATER, Fluids.WATER.getTickDelay(context.getLevel()));

        context.runAfterDelay(10, () -> {
            int finalMass = totalFluidAmount(context, WATER_POS, 1, Fluids.WATER) + fluidAmount(context, BELOW_WATER_POS, Fluids.WATER);
            context.assertTrue(finalMass == initialMass,
                    "finite water falling into a partial column should conserve volume: " + finalMass + " != " + initialMass);
            context.assertTrue(fluidAmount(context, BELOW_WATER_POS, Fluids.WATER) == 8,
                    "gravity should fill the lower cell before leaving water above it");
            context.assertTrue(totalFluidAmount(context, WATER_POS, 1, Fluids.WATER) == 3,
                    "remaining water volume should be conserved in the upper local basin");
            context.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void flatWaterSpreadConservesMassInLocalBasin(GameTestHelper context) {
        prepareFlatBasin(context, WATER_POS, 1);
        context.setBlock(WATER_POS, Blocks.WATER.defaultBlockState());
        context.getLevel().scheduleTick(context.absolutePos(WATER_POS), Fluids.WATER, Fluids.WATER.getTickDelay(context.getLevel()));

        context.runAfterDelay(15, () -> {
            int totalMass = totalFluidAmount(context, WATER_POS, 1, Fluids.WATER);
            context.assertTrue(totalMass == 8, "flat finite-water spread should conserve one source volume: " + totalMass);
            context.assertTrue(countFluidCells(context, WATER_POS, 1, Fluids.WATER) == 5,
                    "one source should settle into the center and four horizontal neighbors");
            context.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void thinWaterLayerSettlesOnFlatPlane(GameTestHelper context) {
        context.setBlock(WATER_POS.below(), Blocks.STONE);
        context.setBlock(WATER_POS, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 7));
        context.getLevel().scheduleTick(context.absolutePos(WATER_POS), Fluids.WATER, Fluids.WATER.getTickDelay(context.getLevel()));

        context.runAfterDelay(10, () -> {
            context.assertBlockPresent(Blocks.WATER, WATER_POS);
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                context.assertBlockPresent(Blocks.AIR, WATER_POS.relative(direction));
            }
            context.succeed();
        });
    }

    @GameTest(maxTicks = 80)
    public void flatLavaSpreadConservesMassInLocalBasin(GameTestHelper context) {
        prepareFlatBasin(context, WATER_POS, 1);
        context.setBlock(WATER_POS, Blocks.LAVA.defaultBlockState());
        context.getLevel().scheduleTick(context.absolutePos(WATER_POS), Fluids.LAVA, Fluids.LAVA.getTickDelay(context.getLevel()));

        context.runAfterDelay(40, () -> {
            int totalMass = totalFluidAmount(context, WATER_POS, 1, Fluids.LAVA);
            context.assertTrue(totalMass == 8, "flat finite-lava spread should conserve one source volume: " + totalMass);
            context.assertTrue(countFluidCells(context, WATER_POS, 1, Fluids.LAVA) == 5,
                    "one lava source should settle into the center and four horizontal neighbors");
            context.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void sourceWaterUsesVanillaWaterloggingHooks(GameTestHelper context) {
        BlockPos campfirePos = BELOW_WATER_POS;
        context.setBlock(WATER_POS, Blocks.WATER.defaultBlockState());
        context.setBlock(campfirePos.below(), Blocks.STONE);
        context.setBlock(campfirePos, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true));
        context.getLevel().scheduleTick(context.absolutePos(WATER_POS), Fluids.WATER, Fluids.WATER.getTickDelay(context.getLevel()));

        context.runAfterDelay(10, () -> {
            BlockState campfireState = context.getBlockState(campfirePos);
            context.assertTrue(campfireState.is(Blocks.CAMPFIRE), "water should not replace the campfire block");
            context.assertTrue(campfireState.getValue(BlockStateProperties.WATERLOGGED), "source water should waterlog the campfire");
            context.assertFalse(campfireState.getValue(CampfireBlock.LIT), "campfire waterlogging should use vanilla extinguish behavior");
            context.assertBlockPresent(Blocks.AIR, WATER_POS);
            context.succeed();
        });
    }

    @GameTest(maxTicks = 80)
    public void finiteLavaUsesSlowerDownwardFlow(GameTestHelper context) {
        context.setBlock(WATER_POS, Blocks.LAVA.defaultBlockState());
        context.setBlock(BELOW_WATER_POS, Blocks.AIR);
        containCell(context, BELOW_WATER_POS);
        context.getLevel().scheduleTick(context.absolutePos(WATER_POS), Fluids.LAVA, Fluids.LAVA.getTickDelay(context.getLevel()));

        context.runAfterDelay(40, () -> {
            context.assertBlockPresent(Blocks.LAVA, BELOW_WATER_POS);
            context.assertBlockPresent(Blocks.AIR, WATER_POS);
            context.assertTrue(context.getBlockState(BELOW_WATER_POS).getValue(LiquidBlock.LEVEL) == 0,
                    "finite lava should move a source downward as a conserved source block");
            context.succeed();
        });
    }

    @GameTest(maxTicks = 80)
    public void lavaSolidifiesWhenDroppingIntoWater(GameTestHelper context) {
        context.setBlock(WATER_POS, Blocks.LAVA.defaultBlockState());
        context.setBlock(BELOW_WATER_POS, Blocks.WATER.defaultBlockState());
        containCell(context, BELOW_WATER_POS);
        context.getLevel().scheduleTick(context.absolutePos(WATER_POS), Fluids.LAVA, Fluids.LAVA.getTickDelay(context.getLevel()));

        context.runAfterDelay(40, () -> {
            context.assertBlockPresent(Blocks.STONE, BELOW_WATER_POS);
            context.assertBlockPresent(Blocks.LAVA, WATER_POS);
            context.succeed();
        });
    }

    @GameTest(maxTicks = 20)
    public void erosionDegradesTaggedMaterialUnderHighFlowImpulse(GameTestHelper context) {
        BlockPos waterPos = new BlockPos(1, 2, 1);
        BlockPos targetPos = waterPos.relative(Direction.EAST);
        context.setBlock(waterPos, Blocks.WATER.defaultBlockState());
        context.setBlock(targetPos, Blocks.STONE.defaultBlockState());

        ErosionPhysics.attemptFlowErosion(
                context.getLevel(),
                context.absolutePos(waterPos),
                context.getBlockState(waterPos).getFluidState(),
                Direction.EAST,
                10_000);

        context.assertBlockPresent(Blocks.COBBLESTONE, targetPos);
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void erosionDoesNotActWithoutMovedWater(GameTestHelper context) {
        BlockPos waterPos = new BlockPos(1, 2, 1);
        BlockPos targetPos = waterPos.relative(Direction.EAST);
        context.setBlock(waterPos, Blocks.WATER.defaultBlockState());
        context.setBlock(targetPos, Blocks.STONE.defaultBlockState());

        ErosionPhysics.attemptFlowErosion(
                context.getLevel(),
                context.absolutePos(waterPos),
                context.getBlockState(waterPos).getFluidState(),
                Direction.EAST,
                0);

        context.assertBlockPresent(Blocks.STONE, targetPos);
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void erosionDoesNotBreakUnbreakableBlocks(GameTestHelper context) {
        BlockPos waterPos = new BlockPos(1, 2, 1);
        BlockPos targetPos = waterPos.relative(Direction.EAST);
        context.setBlock(waterPos, Blocks.WATER.defaultBlockState());
        context.setBlock(targetPos, Blocks.BEDROCK.defaultBlockState());

        ErosionPhysics.attemptFlowErosion(
                context.getLevel(),
                context.absolutePos(waterPos),
                context.getBlockState(waterPos).getFluidState(),
                Direction.EAST,
                1_000_000);

        context.assertBlockPresent(Blocks.BEDROCK, targetPos);
        context.succeed();
    }

    private static void containCell(GameTestHelper context, BlockPos pos) {
        context.setBlock(pos.below(), Blocks.STONE);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            context.setBlock(pos.relative(direction), Blocks.STONE);
        }
    }

    private static void prepareFlatBasin(GameTestHelper context, BlockPos center, int radius) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                BlockPos pos = center.offset(x, 0, z);
                context.setBlock(pos.below(), Blocks.STONE);
                context.setBlock(pos, Blocks.AIR);
            }
        }
    }

    private static int totalFluidAmount(GameTestHelper context, BlockPos center, int radius, Fluid fluid) {
        int total = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                total += fluidAmount(context, center.offset(x, 0, z), fluid);
            }
        }
        return total;
    }

    private static int countFluidCells(GameTestHelper context, BlockPos center, int radius, Fluid fluid) {
        int count = 0;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (fluidAmount(context, center.offset(x, 0, z), fluid) > 0) {
                    count++;
                }
            }
        }
        return count;
    }

    private static int fluidAmount(GameTestHelper context, BlockPos pos, Fluid fluid) {
        FluidState fluidState = context.getBlockState(pos).getFluidState();
        return fluidState.getType().isSame(fluid) ? fluidState.getAmount() : 0;
    }

    @Override
    public void invokeTestMethod(GameTestHelper context, Method method) throws ReflectiveOperationException {
        boolean finiteWaterFlow = EmergentConfig.get().finiteWaterFlow;
        boolean hydraulicErosion = EmergentConfig.get().hydraulicErosion;
        boolean materialReactions = EmergentConfig.get().materialReactions;

        EmergentConfig.get().finiteWaterFlow = true;
        EmergentConfig.get().hydraulicErosion = true;
        EmergentConfig.get().materialReactions = true;

        try {
            method.invoke(this, context);
        } finally {
            EmergentConfig.get().finiteWaterFlow = finiteWaterFlow;
            EmergentConfig.get().hydraulicErosion = hydraulicErosion;
            EmergentConfig.get().materialReactions = materialReactions;
        }
    }
}
