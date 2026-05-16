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

    private static void containCell(GameTestHelper context, BlockPos pos) {
        context.setBlock(pos.below(), Blocks.STONE);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            context.setBlock(pos.relative(direction), Blocks.STONE);
        }
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
