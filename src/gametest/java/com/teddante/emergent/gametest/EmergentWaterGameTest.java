package com.teddante.emergent.gametest;

import com.teddante.emergent.EmergentConfig;
import com.teddante.emergent.EnvironmentalScheduler;
import com.teddante.emergent.EnvironmentalExposure;
import com.teddante.emergent.ErosionPhysics;
import com.teddante.emergent.FireWetness;
import com.teddante.emergent.MaterialPhysicsProfiles;
import com.teddante.emergent.ThermalPhysics;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import java.lang.reflect.Method;

public class EmergentWaterGameTest implements CustomTestMethodInvoker {
    private static final BlockPos WATER_POS = new BlockPos(2, 3, 2);
    private static final BlockPos BELOW_WATER_POS = WATER_POS.below();

    @GameTest(maxTicks = 20)
    public void fluidAmountsMapToMinecraftBlockScale(GameTestHelper context) {
        assertClose(EnvironmentalExposure.fluidAmountCubicMeters(8), 1.0, "full source should be one cubic metre");
        assertClose(EnvironmentalExposure.fluidAmountLiters(8), 1_000.0, "full source should be one thousand litres");
        assertClose(EnvironmentalExposure.fluidAmountCubicMeters(1), 0.125, "one fluid amount should be one eighth cubic metre");
        assertClose(EnvironmentalExposure.fluidAmountLiters(1), 125.0, "one fluid amount should be one hundred and twenty five litres");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void environmentEvaporationRemovesAnyFiniteWaterAmount(GameTestHelper context) {
        context.assertTrue(ThermalPhysics.evaporateWaterInEvaporatingEnvironment(true, 8) == 0,
                "a water-evaporating environment should remove source water like vanilla Nether bucket placement");
        context.assertTrue(ThermalPhysics.evaporateWaterInEvaporatingEnvironment(true, 4) == 0,
                "a water-evaporating environment should remove partial finite water");
        context.assertTrue(ThermalPhysics.evaporateWaterInEvaporatingEnvironment(true, 1) == 0,
                "a water-evaporating environment should remove thin finite water films");
        context.assertTrue(ThermalPhysics.evaporateWaterInEvaporatingEnvironment(false, 4) == 4,
                "ordinary environments should leave finite water to normal heat, cold, and flow rules");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void overworldEnvironmentDoesNotForceWaterEvaporation(GameTestHelper context) {
        context.setBlock(WATER_POS, Blocks.WATER.defaultBlockState());

        int remaining = ThermalPhysics.evaporateWaterInEvaporatingEnvironment(
                context.getLevel(),
                context.absolutePos(WATER_POS),
                context.getBlockState(WATER_POS).getFluidState().getAmount());

        context.assertTrue(remaining == 8,
                "the default GameTest overworld should not use Nether-style environmental water evaporation");
        context.assertBlockPresent(Blocks.WATER, WATER_POS);
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void schedulerProbabilityAccumulatesDelayedSamples(GameTestHelper context) {
        double singleSampleChance = 0.1;
        double threeSampleChance = EnvironmentalScheduler.probabilityOverSamples(singleSampleChance, 3);

        assertClose(
                EnvironmentalScheduler.probabilityOverSamples(singleSampleChance, 1),
                singleSampleChance,
                "one queued sample should preserve the original chance");
        assertClose(
                threeSampleChance,
                1.0 - Math.pow(0.9, 3),
                "queued samples should combine as independent physical opportunities");
        context.assertTrue(threeSampleChance > singleSampleChance && threeSampleChance < singleSampleChance * 3.0,
                "accumulated probability should grow without becoming a linear certainty");
        context.succeed();
    }

    @GameTest(maxTicks = 30)
    public void schedulerCoalescesRepeatedSurfaceSamples(GameTestHelper context) {
        BlockPos samplePos = new BlockPos(6, 3, 6);

        EnvironmentalScheduler.enqueueSurfaceWeatherSample(context.getLevel(), context.absolutePos(samplePos));
        EnvironmentalScheduler.enqueueSurfaceWeatherSample(context.getLevel(), context.absolutePos(samplePos));

        context.assertTrue(
                EnvironmentalScheduler.pendingSurfaceWeatherSampleWeightForTests(context.getLevel(), context.absolutePos(samplePos)) == 2,
                "repeated samples for the same surface should merge into one weighted pending job");

        context.runAfterDelay(12, () -> {
            EnvironmentalScheduler.tickWorldForTests(context.getLevel());
            context.assertTrue(
                    EnvironmentalScheduler.pendingSurfaceWeatherSampleWeightForTests(context.getLevel(), context.absolutePos(samplePos)) == 0,
                    "due environmental samples should drain from the scheduler queue");
            context.succeed();
        });
    }

    @GameTest(maxTicks = 20)
    public void batchedRainfallMatchesRepeatedSingleSamples(GameTestHelper context) {
        BlockPos repeatedPos = new BlockPos(5, 3, 2);
        BlockPos batchedPos = new BlockPos(7, 3, 2);
        context.setBlock(repeatedPos, Blocks.DIRT.defaultBlockState());
        context.setBlock(batchedPos, Blocks.DIRT.defaultBlockState());

        for (int i = 0; i < 4; i++) {
            EnvironmentalExposure.addRainfall(
                    context.getLevel(),
                    context.absolutePos(repeatedPos),
                    context.getBlockState(repeatedPos),
                    1.0);
        }
        EnvironmentalExposure.addRainfall(
                context.getLevel(),
                context.absolutePos(batchedPos),
                context.getBlockState(batchedPos),
                1.0,
                4);

        assertClose(
                EnvironmentalExposure.moisture(context.getLevel(), context.absolutePos(batchedPos), context.getBlockState(batchedPos)),
                EnvironmentalExposure.moisture(context.getLevel(), context.absolutePos(repeatedPos), context.getBlockState(repeatedPos)),
                "batched rain samples should store the same moisture as repeated vanilla precipitation samples");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void batchedAmbientDryingMatchesRepeatedSingleSamples(GameTestHelper context) {
        BlockPos repeatedPos = new BlockPos(5, 3, 3);
        BlockPos batchedPos = new BlockPos(7, 3, 3);
        context.setBlock(repeatedPos, Blocks.DIRT.defaultBlockState());
        context.setBlock(batchedPos, Blocks.DIRT.defaultBlockState());
        EnvironmentalExposure.addMoisture(context.getLevel(), context.absolutePos(repeatedPos), context.getBlockState(repeatedPos), 0.7);
        EnvironmentalExposure.addMoisture(context.getLevel(), context.absolutePos(batchedPos), context.getBlockState(batchedPos), 0.7);

        for (int i = 0; i < 4; i++) {
            EnvironmentalExposure.applyAmbientSurfaceExchange(
                    context.getLevel(),
                    context.absolutePos(repeatedPos),
                    context.getBlockState(repeatedPos),
                    1.0F,
                    true,
                    0,
                    1.0,
                    1.0,
                    1.0);
        }
        EnvironmentalExposure.applyAmbientSurfaceExchange(
                context.getLevel(),
                context.absolutePos(batchedPos),
                context.getBlockState(batchedPos),
                1.0F,
                true,
                0,
                1.0,
                1.0,
                1.0,
                4);

        assertClose(
                EnvironmentalExposure.moisture(context.getLevel(), context.absolutePos(batchedPos), context.getBlockState(batchedPos)),
                EnvironmentalExposure.moisture(context.getLevel(), context.absolutePos(repeatedPos), context.getBlockState(repeatedPos)),
                "batched ambient drying should remove the same moisture as repeated single samples");
        context.succeed();
    }

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
    public void finiteWaterWetsSurfaceUnderSettledPuddle(GameTestHelper context) {
        context.setBlock(WATER_POS, Blocks.WATER.defaultBlockState());
        context.setBlock(BELOW_WATER_POS, Blocks.AIR);
        containCell(context, BELOW_WATER_POS);
        context.getLevel().scheduleTick(context.absolutePos(WATER_POS), Fluids.WATER, Fluids.WATER.getTickDelay(context.getLevel()));

        context.runAfterDelay(10, () -> {
            context.assertBlockPresent(Blocks.WATER, BELOW_WATER_POS);
            context.assertTrue(FireWetness.getWetness(context.getLevel(), context.absolutePos(BELOW_WATER_POS.below())) >= 0.74f,
                    "finite water should leave the contacted surface wet for later fire/traffic physics");
            context.succeed();
        });
    }

    @GameTest(maxTicks = 20)
    public void standingWaterContactWetsSupportingSurface(GameTestHelper context) {
        BlockPos surfacePos = WATER_POS.below();
        context.setBlock(surfacePos, Blocks.DIRT.defaultBlockState());
        context.setBlock(WATER_POS, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 7));

        EnvironmentalExposure.applyStandingWaterContact(
                context.getLevel(),
                context.absolutePos(WATER_POS),
                context.getBlockState(WATER_POS).getFluidState().getAmount());

        context.assertTrue(
                EnvironmentalExposure.moisture(context.getLevel(), context.absolutePos(surfacePos), context.getBlockState(surfacePos)) > 0.0,
                "thin standing water should store contact moisture on the block it rests on");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void rainPuddleReadinessDependsOnSurfaceAbsorption(GameTestHelper context) {
        double stoneThreshold = EnvironmentalExposure.rainPuddleSaturationThreshold(Blocks.STONE.defaultBlockState());
        double dirtThreshold = EnvironmentalExposure.rainPuddleSaturationThreshold(Blocks.DIRT.defaultBlockState());

        context.assertTrue(stoneThreshold < dirtThreshold,
                "hard low-absorption surfaces should be able to release visible puddles before soil saturates");
        context.assertTrue(EnvironmentalExposure.rainPuddleReadiness(Blocks.STONE.defaultBlockState(), stoneThreshold) > 0.0,
                "hard surfaces should become puddle-ready once their thin surface film is saturated");
        context.assertTrue(EnvironmentalExposure.rainPuddleReadiness(Blocks.DIRT.defaultBlockState(), stoneThreshold) == 0.0,
                "absorbent soil should keep soaking rain at the same stored moisture level");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void rainPuddleReleaseConsumesStoredMoisture(GameTestHelper context) {
        BlockPos surfacePos = WATER_POS.below();
        context.setBlock(surfacePos, Blocks.STONE.defaultBlockState());
        double moisture = EnvironmentalExposure.rainPuddleSaturationThreshold(context.getBlockState(surfacePos))
                + EnvironmentalExposure.rainPuddleMoistureCost(context.getBlockState(surfacePos), 1);
        EnvironmentalExposure.addMoisture(
                context.getLevel(),
                context.absolutePos(surfacePos),
                context.getBlockState(surfacePos),
                moisture);

        boolean released = EnvironmentalExposure.tryReleaseRainPuddleMoisture(
                context.getLevel(),
                context.absolutePos(surfacePos),
                context.getBlockState(surfacePos),
                1);

        context.assertTrue(released, "a saturated hard surface should be able to release a shallow rain puddle");
        assertClose(
                EnvironmentalExposure.moisture(context.getLevel(), context.absolutePos(surfacePos), context.getBlockState(surfacePos)),
                moisture - EnvironmentalExposure.rainPuddleMoistureCost(context.getBlockState(surfacePos), 1),
                "forming a rain puddle should draw down stored surface moisture instead of creating free water memory");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void rainPuddleContactWashesAshIntoSuspendedSediment(GameTestHelper context) {
        BlockPos surfacePos = WATER_POS.below();
        context.setBlock(surfacePos, Blocks.DIRT.defaultBlockState());
        context.setBlock(WATER_POS, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 7));
        EnvironmentalExposure.addAshResidue(
                context.getLevel(),
                context.absolutePos(surfacePos),
                context.getBlockState(surfacePos),
                1.0);

        EnvironmentalExposure.applyStandingWaterContact(
                context.getLevel(),
                context.absolutePos(WATER_POS),
                context.getBlockState(WATER_POS).getFluidState().getAmount());

        context.assertTrue(
                EnvironmentalExposure.ashResidue(context.getLevel(), context.absolutePos(surfacePos), context.getBlockState(surfacePos)) < 1.0,
                "a rain-created puddle should consume some surface ash residue");
        context.assertTrue(
                EnvironmentalExposure.suspendedSediment(context.getLevel(), context.absolutePos(WATER_POS), context.getBlockState(WATER_POS)) > 0.0,
                "washed ash should enter the same suspended sediment store used by erosion and deposition");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void suspendedSedimentTransfersProportionallyWithMovedWater(GameTestHelper context) {
        BlockPos targetPos = WATER_POS.relative(Direction.EAST);
        context.setBlock(WATER_POS, Blocks.WATER.defaultBlockState());
        context.setBlock(targetPos, Fluids.WATER.getFlowing(4, false).createLegacyBlock());
        EnvironmentalExposure.addSuspendedSediment(
                context.getLevel(),
                context.absolutePos(WATER_POS),
                context.getBlockState(WATER_POS),
                80.0);

        double movedSediment = EnvironmentalExposure.transferSuspendedSediment(
                context.getLevel(),
                context.absolutePos(WATER_POS),
                context.getBlockState(WATER_POS),
                context.absolutePos(targetPos),
                context.getBlockState(targetPos),
                4,
                8);

        assertClose(movedSediment, 40.0, "half the water volume should carry half the suspended sediment");
        assertClose(
                EnvironmentalExposure.suspendedSediment(context.getLevel(), context.absolutePos(WATER_POS), context.getBlockState(WATER_POS)),
                40.0,
                "source water should retain the unmoved suspended sediment mass");
        assertClose(
                EnvironmentalExposure.suspendedSediment(context.getLevel(), context.absolutePos(targetPos), context.getBlockState(targetPos)),
                40.0,
                "target water should receive the moved suspended sediment mass");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void suspendedSedimentConcentrationScalesWithFiniteWaterVolume(GameTestHelper context) {
        assertClose(
                EnvironmentalExposure.sedimentConcentrationKilogramsPerCubicMeter(100.0, 8),
                100.0,
                "one hundred kilograms in a full cubic metre should be one hundred kilograms per cubic metre");
        assertClose(
                EnvironmentalExposure.sedimentConcentrationKilogramsPerCubicMeter(100.0, 4),
                200.0,
                "the same sediment in half the water volume should double concentration");
        assertClose(
                EnvironmentalExposure.sedimentMassFraction(50.0, 8),
                0.05,
                "fifty kilograms of sediment in one cubic metre of water should be a five percent mass fraction");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void sedimentLoadIncreasesHydraulicAbrasionSmoothly(GameTestHelper context) {
        assertClose(
                EnvironmentalExposure.hydraulicAbrasionMultiplier(0.0, 8),
                1.0,
                "clear water should keep baseline hydraulic wear");
        context.assertTrue(
                EnvironmentalExposure.hydraulicAbrasionMultiplier(5.0, 8)
                        < EnvironmentalExposure.hydraulicAbrasionMultiplier(50.0, 8),
                "heavier suspended sediment should increase abrasion more than light suspended sediment");
        assertClose(
                EnvironmentalExposure.hydraulicAbrasionMultiplier(50.0, 8),
                1.75,
                "a five percent sediment mass fraction should reach half of the modeled abrasion saturation");
        context.succeed();
    }

    @GameTest(maxTicks = 40)
    public void fallingFiniteWaterCarriesSuspendedSedimentDownstream(GameTestHelper context) {
        context.setBlock(WATER_POS, Blocks.WATER.defaultBlockState());
        context.setBlock(BELOW_WATER_POS, Blocks.AIR);
        containCell(context, BELOW_WATER_POS);
        EnvironmentalExposure.addSuspendedSediment(
                context.getLevel(),
                context.absolutePos(WATER_POS),
                context.getBlockState(WATER_POS),
                64.0);
        context.getLevel().scheduleTick(context.absolutePos(WATER_POS), Fluids.WATER, Fluids.WATER.getTickDelay(context.getLevel()));

        context.runAfterDelay(10, () -> {
            context.assertBlockPresent(Blocks.WATER, BELOW_WATER_POS);
            assertClose(
                    EnvironmentalExposure.suspendedSediment(context.getLevel(), context.absolutePos(BELOW_WATER_POS), context.getBlockState(BELOW_WATER_POS)),
                    64.0,
                    "falling finite water should carry its suspended sediment into the destination cell");
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

    @GameTest(maxTicks = 40)
    public void equalFiniteWaterLayerStaysQuietAndConservesMass(GameTestHelper context) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                BlockPos pos = WATER_POS.offset(x, 0, z);
                context.setBlock(pos.below(), Blocks.STONE);
                context.setBlock(pos, Blocks.STONE);
            }
        }
        context.setBlock(WATER_POS, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 4));
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            context.setBlock(WATER_POS.relative(direction), Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 4));
        }

        int initialMass = totalFluidAmount(context, WATER_POS, 1, Fluids.WATER);
        context.getLevel().scheduleTick(context.absolutePos(WATER_POS), Fluids.WATER, Fluids.WATER.getTickDelay(context.getLevel()));

        context.runAfterDelay(10, () -> {
            context.assertTrue(totalFluidAmount(context, WATER_POS, 1, Fluids.WATER) == initialMass,
                    "an equal finite-water layer should wake quietly without creating or deleting volume");
            context.assertTrue(countFluidCells(context, WATER_POS, 1, Fluids.WATER) == 5,
                    "an equal finite-water layer should not spread into new cells");
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
    public void partialWaterloggableNeighborDoesNotBlockOtherFlowPath(GameTestHelper context) {
        BlockPos sourcePos = WATER_POS;
        BlockPos firstTarget = sourcePos.relative(Direction.EAST);
        BlockPos secondTarget = firstTarget.relative(Direction.EAST);
        BlockPos waterloggableNeighbor = firstTarget.relative(Direction.NORTH);

        context.setBlock(sourcePos.below(), Blocks.STONE);
        context.setBlock(firstTarget.below(), Blocks.STONE);
        context.setBlock(secondTarget.below(), Blocks.STONE);
        context.setBlock(waterloggableNeighbor.below(), Blocks.STONE);
        context.setBlock(sourcePos, Blocks.AIR);
        context.setBlock(firstTarget, Blocks.AIR);
        context.setBlock(secondTarget, Blocks.AIR);
        context.setBlock(sourcePos.relative(Direction.NORTH), Blocks.STONE);
        context.setBlock(sourcePos.relative(Direction.SOUTH), Blocks.STONE);
        context.setBlock(sourcePos.relative(Direction.WEST), Blocks.STONE);
        context.setBlock(firstTarget.relative(Direction.SOUTH), Blocks.STONE);
        context.setBlock(secondTarget.relative(Direction.NORTH), Blocks.STONE);
        context.setBlock(secondTarget.relative(Direction.SOUTH), Blocks.STONE);
        context.setBlock(secondTarget.relative(Direction.EAST), Blocks.STONE);
        context.setBlock(waterloggableNeighbor, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
        context.setBlock(sourcePos, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 3));
        context.assertTrue(fluidAmount(context, sourcePos, Fluids.WATER) == 5,
                "test fixture should start with five finite water units");

        context.getLevel().scheduleTick(context.absolutePos(sourcePos), Fluids.WATER, Fluids.WATER.getTickDelay(context.getLevel()));

        context.runAfterDelay(30, () -> {
            int sourceAmount = fluidAmount(context, sourcePos, Fluids.WATER);
            int firstTargetAmount = fluidAmount(context, firstTarget, Fluids.WATER);
            int secondTargetAmount = fluidAmount(context, secondTarget, Fluids.WATER);
            int waterloggedNeighborAmount = fluidAmount(context, waterloggableNeighbor, Fluids.WATER);
            context.assertTrue(secondTargetAmount > 0,
                    "a partial water layer beside a waterloggable block should still keep flowing into other open lower-pressure cells; amounts="
                            + sourceAmount + "," + firstTargetAmount + "," + secondTargetAmount + "," + waterloggedNeighborAmount);
            context.assertFalse(context.getBlockState(waterloggableNeighbor).getValue(BlockStateProperties.WATERLOGGED),
                    "partial finite water should not waterlog a block unless it has source-equivalent volume");
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

    @GameTest(maxTicks = 20)
    public void lavaContactHeatScalesWithFiniteVolume(GameTestHelper context) {
        double thinLavaHeat = ThermalPhysics.lavaContactHeat(2);
        double sourceLavaHeat = ThermalPhysics.lavaContactHeat(8);

        assertClose(sourceLavaHeat, thinLavaHeat * 4.0,
                "lava contact heating should scale with finite lava volume");
        context.assertTrue(sourceLavaHeat > thinLavaHeat,
                "a source-equivalent lava block should heat contacts more strongly than a shallow lava layer");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void evaporationCoolingScalesWithFiniteWaterVolume(GameTestHelper context) {
        assertClose(
                ThermalPhysics.evaporationCooling(8),
                ThermalPhysics.evaporationCooling(1) * 8.0,
                "evaporation cooling should scale with the finite volume of water removed");
        context.assertTrue(
                ThermalPhysics.evaporationCooling(2) > ThermalPhysics.lavaContactHeat(2),
                "evaporating water should remove substantial stored heat at the same block-scale volume");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void finiteWaterThermalWorkPredicateIgnoresOrdinaryQuietWater(GameTestHelper context) {
        context.setBlock(WATER_POS.below(), Blocks.STONE);
        context.setBlock(WATER_POS, Fluids.WATER.getFlowing(2, false).createLegacyBlock());

        boolean mayChange = ThermalPhysics.finiteWaterMayChangeThermally(
                context.getLevel(),
                context.absolutePos(WATER_POS),
                2);

        context.assertFalse(mayChange,
                "quiet shallow water without heat, cold, or evaporating environment should skip thermal work");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void finiteWaterThermalWorkPredicateKeepsRealThermalContacts(GameTestHelper context) {
        BlockPos magmaPos = WATER_POS.relative(Direction.EAST);
        context.setBlock(WATER_POS.below(), Blocks.STONE);
        context.setBlock(magmaPos.below(), Blocks.STONE);
        context.setBlock(WATER_POS, Fluids.WATER.getFlowing(2, false).createLegacyBlock());
        context.setBlock(magmaPos, Blocks.MAGMA_BLOCK);

        boolean mayChange = ThermalPhysics.finiteWaterMayChangeThermally(
                context.getLevel(),
                context.absolutePos(WATER_POS),
                2);

        context.assertTrue(mayChange,
                "shallow water beside a heat source should still run the thermal pass");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void sourceWaterThermalWorkPredicateIgnoresAdjacentHeatEvaporation(GameTestHelper context) {
        BlockPos magmaPos = WATER_POS.relative(Direction.EAST);
        context.setBlock(WATER_POS.below(), Blocks.STONE);
        context.setBlock(magmaPos.below(), Blocks.STONE);
        context.setBlock(WATER_POS, Blocks.WATER);
        context.setBlock(magmaPos, Blocks.MAGMA_BLOCK);

        boolean mayChange = ThermalPhysics.finiteWaterMayChangeThermally(
                context.getLevel(),
                context.absolutePos(WATER_POS),
                8);

        context.assertFalse(mayChange,
                "source-equivalent water should not scan heat evaporation that cannot affect a full cubic metre");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void lavaContactAddsStoredHeatToAdjacentStone(GameTestHelper context) {
        BlockPos stonePos = WATER_POS.relative(Direction.EAST);
        context.setBlock(WATER_POS, Blocks.LAVA.defaultBlockState());
        context.setBlock(stonePos, Blocks.STONE.defaultBlockState());

        ThermalPhysics.applyLavaContactHeat(
                context.getLevel(),
                context.absolutePos(WATER_POS),
                8);

        context.assertTrue(
                EnvironmentalExposure.heat(context.getLevel(), context.absolutePos(stonePos), context.getBlockState(stonePos)) > 0.0,
                "lava contact should add shared stored heat to adjacent solid blocks");
        context.assertBlockPresent(Blocks.STONE, stonePos);
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void lavaContactCanMeltAdjacentSnowLayer(GameTestHelper context) {
        BlockPos snowPos = WATER_POS.relative(Direction.EAST);
        context.setBlock(WATER_POS, Blocks.LAVA.defaultBlockState());
        context.setBlock(snowPos.below(), Blocks.DIRT.defaultBlockState());
        context.setBlock(snowPos, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1));

        ThermalPhysics.applyLavaContactHeat(
                context.getLevel(),
                context.absolutePos(WATER_POS),
                8);

        context.assertBlockPresent(Blocks.AIR, snowPos);
        context.assertTrue(
                EnvironmentalExposure.moisture(context.getLevel(), context.absolutePos(snowPos.below()), context.getBlockState(snowPos.below())) > 0.0,
                "snow melted by lava contact should leave moisture on the supporting surface");
        context.succeed();
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

    @GameTest(maxTicks = 40)
    public void shallowWaterEvaporatesNextToMagma(GameTestHelper context) {
        BlockPos magmaPos = WATER_POS.relative(Direction.EAST);
        context.setBlock(WATER_POS.below(), Blocks.STONE);
        context.setBlock(magmaPos.below(), Blocks.STONE);
        context.setBlock(WATER_POS, Fluids.WATER.getFlowing(2, false).createLegacyBlock());
        context.setBlock(magmaPos, Blocks.MAGMA_BLOCK.defaultBlockState());
        context.getLevel().scheduleTick(context.absolutePos(WATER_POS), Fluids.WATER, Fluids.WATER.getTickDelay(context.getLevel()));

        context.runAfterDelay(10, () -> {
            context.assertBlockPresent(Blocks.AIR, WATER_POS);
            context.assertBlockPresent(Blocks.MAGMA_BLOCK, magmaPos);
            context.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void shallowWaterEvaporatesNextToFire(GameTestHelper context) {
        BlockPos firePos = WATER_POS.relative(Direction.EAST);
        context.setBlock(WATER_POS.below(), Blocks.STONE);
        context.setBlock(firePos.below(), Blocks.NETHERRACK);
        context.setBlock(WATER_POS, Fluids.WATER.getFlowing(2, false).createLegacyBlock());
        context.setBlock(firePos, Blocks.FIRE.defaultBlockState());
        context.getLevel().scheduleTick(context.absolutePos(WATER_POS), Fluids.WATER, Fluids.WATER.getTickDelay(context.getLevel()));

        context.runAfterDelay(10, () -> {
            context.assertBlockPresent(Blocks.AIR, WATER_POS);
            context.assertBlockPresent(Blocks.FIRE, firePos);
            context.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void storedHeatEvaporatesShallowWaterWithoutAdjacentHeatSource(GameTestHelper context) {
        context.setBlock(WATER_POS.below(), Blocks.STONE);
        context.setBlock(WATER_POS, Fluids.WATER.getFlowing(2, false).createLegacyBlock());
        EnvironmentalExposure.addHeat(
                context.getLevel(),
                context.absolutePos(WATER_POS),
                context.getBlockState(WATER_POS),
                0.5);
        context.getLevel().scheduleTick(context.absolutePos(WATER_POS), Fluids.WATER, Fluids.WATER.getTickDelay(context.getLevel()));

        context.runAfterDelay(10, () -> {
            context.assertBlockPresent(Blocks.AIR, WATER_POS);
            context.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void evaporationCoolsStoredHeatOnSupportingSurface(GameTestHelper context) {
        BlockPos supportPos = WATER_POS.below();
        context.setBlock(supportPos, Blocks.STONE);
        context.setBlock(WATER_POS, Fluids.WATER.getFlowing(2, false).createLegacyBlock());
        EnvironmentalExposure.addHeat(
                context.getLevel(),
                context.absolutePos(supportPos),
                context.getBlockState(supportPos),
                0.5);
        context.getLevel().scheduleTick(context.absolutePos(WATER_POS), Fluids.WATER, Fluids.WATER.getTickDelay(context.getLevel()));

        context.runAfterDelay(10, () -> {
            context.assertBlockPresent(Blocks.AIR, WATER_POS);
            context.assertTrue(
                    EnvironmentalExposure.heat(context.getLevel(), context.absolutePos(supportPos), context.getBlockState(supportPos)) < 0.5,
                    "water evaporated by stored heat should cool the hot supporting surface");
            context.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void storedColdFreezesShallowWaterAsThinFrozenLayer(GameTestHelper context) {
        context.setBlock(WATER_POS.below(), Blocks.STONE);
        context.setBlock(WATER_POS, Fluids.WATER.getFlowing(2, false).createLegacyBlock());
        EnvironmentalExposure.addCold(
                context.getLevel(),
                context.absolutePos(WATER_POS),
                context.getBlockState(WATER_POS),
                0.7);
        context.getLevel().scheduleTick(context.absolutePos(WATER_POS), Fluids.WATER, Fluids.WATER.getTickDelay(context.getLevel()));

        context.runAfterDelay(10, () -> {
            context.assertBlockPresent(Blocks.SNOW, WATER_POS);
            context.assertTrue(context.getBlockState(WATER_POS).getValue(SnowLayerBlock.LAYERS) == 2,
                    "a shallow two-amount puddle should freeze into a two-layer surface rather than a full ice block");
            context.succeed();
        });
    }

    @GameTest(maxTicks = 20)
    public void storedColdFreezesMoistSurfaceAsSnowLayer(GameTestHelper context) {
        BlockPos supportPos = WATER_POS.below();
        context.setBlock(supportPos, Blocks.DIRT);
        context.setBlock(WATER_POS, Blocks.AIR);
        EnvironmentalExposure.addMoisture(
                context.getLevel(),
                context.absolutePos(supportPos),
                context.getBlockState(supportPos),
                ThermalPhysics.surfaceMoistureForSnowLayer(context.getBlockState(supportPos)) * 2.0);
        EnvironmentalExposure.addCold(
                context.getLevel(),
                context.absolutePos(supportPos),
                context.getBlockState(supportPos),
                0.7);

        boolean froze = ThermalPhysics.tryFreezeMoistSurface(
                context.getLevel(),
                context.absolutePos(supportPos),
                context.getBlockState(supportPos));

        context.assertTrue(froze, "stored moisture and cold should be able to freeze into visible snow cover");
        context.assertBlockPresent(Blocks.SNOW, WATER_POS);
        context.assertTrue(context.getBlockState(WATER_POS).getValue(SnowLayerBlock.LAYERS) == 2,
                "stored surface moisture should determine the number of snow layers formed");
        context.assertTrue(
                EnvironmentalExposure.moisture(context.getLevel(), context.absolutePos(supportPos), context.getBlockState(supportPos)) < 0.01,
                "freezing surface moisture should consume the stored liquid water memory");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void frostWedgingStressFollowsPorousMaterialPhysics(GameTestHelper context) {
        double frozenMoisture = ThermalPhysics.surfaceMoistureForSnowLayer(Blocks.STONE.defaultBlockState()) * 2.0;
        double stoneStress = MaterialPhysicsProfiles.frostWedgingStress(Blocks.STONE.defaultBlockState(), frozenMoisture);
        double sandstoneStress = MaterialPhysicsProfiles.frostWedgingStress(Blocks.SANDSTONE.defaultBlockState(), frozenMoisture);
        double woodStress = MaterialPhysicsProfiles.frostWedgingStress(Blocks.OAK_LOG.defaultBlockState(), frozenMoisture);

        context.assertTrue(stoneStress > 0.0,
                "freezing water in rock pores should add structural stress");
        context.assertTrue(
                sandstoneStress / MaterialPhysicsProfiles.structuralStressThreshold(Blocks.SANDSTONE.defaultBlockState())
                        > stoneStress / MaterialPhysicsProfiles.structuralStressThreshold(Blocks.STONE.defaultBlockState()),
                "more porous sedimentary rock should take a larger fraction of its fracture threshold than dense stone");
        context.assertTrue(woodStress == 0.0,
                "ordinary wood should not use the rock frost-wedging fracture path");
        context.assertTrue(MaterialPhysicsProfiles.frostWedgingStress(Blocks.STONE.defaultBlockState(), 0.0) == 0.0,
                "frost wedging should require frozen moisture");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void freezingMoistRockAddsStructuralStress(GameTestHelper context) {
        BlockPos supportPos = WATER_POS.below();
        context.setBlock(supportPos, Blocks.STONE);
        context.setBlock(WATER_POS, Blocks.AIR);
        EnvironmentalExposure.addMoisture(
                context.getLevel(),
                context.absolutePos(supportPos),
                context.getBlockState(supportPos),
                ThermalPhysics.surfaceMoistureForSnowLayer(context.getBlockState(supportPos)) * 2.0);
        EnvironmentalExposure.addCold(
                context.getLevel(),
                context.absolutePos(supportPos),
                context.getBlockState(supportPos),
                0.7);

        boolean froze = ThermalPhysics.tryFreezeMoistSurface(
                context.getLevel(),
                context.absolutePos(supportPos),
                context.getBlockState(supportPos));

        context.assertTrue(froze, "stored moisture and cold should freeze on exposed rock");
        context.assertBlockPresent(Blocks.SNOW, WATER_POS);
        context.assertTrue(
                EnvironmentalExposure.structuralStress(context.getLevel(), context.absolutePos(supportPos), context.getBlockState(supportPos)) > 0.0,
                "frozen pore water should add rock stress through the shared structural memory");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void storedHeatPreventsMoistSurfaceFreezing(GameTestHelper context) {
        BlockPos supportPos = WATER_POS.below();
        context.setBlock(supportPos, Blocks.DIRT);
        context.setBlock(WATER_POS, Blocks.AIR);
        EnvironmentalExposure.addMoisture(
                context.getLevel(),
                context.absolutePos(supportPos),
                context.getBlockState(supportPos),
                ThermalPhysics.surfaceMoistureForSnowLayer(context.getBlockState(supportPos)) * 2.0);
        EnvironmentalExposure.addCold(
                context.getLevel(),
                context.absolutePos(supportPos),
                context.getBlockState(supportPos),
                0.7);
        EnvironmentalExposure.addHeat(
                context.getLevel(),
                context.absolutePos(supportPos),
                context.getBlockState(supportPos),
                0.5);

        boolean froze = ThermalPhysics.tryFreezeMoistSurface(
                context.getLevel(),
                context.absolutePos(supportPos),
                context.getBlockState(supportPos));

        context.assertFalse(froze, "stored heat should stop a wet surface from freezing until it cools");
        context.assertBlockPresent(Blocks.AIR, WATER_POS);
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void storedHeatMeltsSnowLayerIntoSurfaceMoisture(GameTestHelper context) {
        context.setBlock(WATER_POS.below(), Blocks.DIRT);
        context.setBlock(WATER_POS, Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, 1));
        EnvironmentalExposure.addHeat(
                context.getLevel(),
                context.absolutePos(WATER_POS),
                context.getBlockState(WATER_POS),
                0.3);

        boolean melted = ThermalPhysics.tryMeltFrozenSurface(
                context.getLevel(),
                context.absolutePos(WATER_POS),
                context.getBlockState(WATER_POS));

        context.assertTrue(melted, "stored heat should melt exposed snow layers");
        context.assertBlockPresent(Blocks.AIR, WATER_POS);
        context.assertTrue(EnvironmentalExposure.moisture(
                context.getLevel(),
                context.absolutePos(WATER_POS.below()),
                context.getBlockState(WATER_POS.below())) > 0.0,
                "melted snow should become stored surface moisture on the supporting block");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void storedHeatMeltsIceToWaterSource(GameTestHelper context) {
        context.setBlock(WATER_POS, Blocks.ICE.defaultBlockState());
        EnvironmentalExposure.addHeat(
                context.getLevel(),
                context.absolutePos(WATER_POS),
                context.getBlockState(WATER_POS),
                1.1);

        boolean melted = ThermalPhysics.tryMeltFrozenSurface(
                context.getLevel(),
                context.absolutePos(WATER_POS),
                context.getBlockState(WATER_POS));

        context.assertTrue(melted, "stored heat should melt normal ice");
        context.assertBlockPresent(Blocks.WATER, WATER_POS);
        context.assertTrue(context.getBlockState(WATER_POS).getValue(LiquidBlock.LEVEL) == 0,
                "melted ice should become a source-equivalent water block");
        context.succeed();
    }

    @GameTest(maxTicks = 40)
    public void waterSourceQuenchesLavaSourceToObsidian(GameTestHelper context) {
        BlockPos lavaPos = WATER_POS.relative(Direction.EAST);
        context.setBlock(WATER_POS.below(), Blocks.STONE);
        context.setBlock(lavaPos.below(), Blocks.STONE);
        context.setBlock(WATER_POS, Blocks.WATER.defaultBlockState());
        context.setBlock(lavaPos, Blocks.LAVA.defaultBlockState());
        context.getLevel().scheduleTick(context.absolutePos(WATER_POS), Fluids.WATER, Fluids.WATER.getTickDelay(context.getLevel()));

        context.runAfterDelay(10, () -> {
            context.assertBlockPresent(Blocks.WATER, WATER_POS);
            context.assertBlockPresent(Blocks.OBSIDIAN, lavaPos);
            context.succeed();
        });
    }

    @GameTest(maxTicks = 40)
    public void waterFlowQuenchesLavaFlowToCobblestone(GameTestHelper context) {
        BlockPos lavaPos = WATER_POS.relative(Direction.EAST);
        context.setBlock(WATER_POS.below(), Blocks.STONE);
        context.setBlock(lavaPos.below(), Blocks.STONE);
        context.setBlock(WATER_POS, Fluids.WATER.getFlowing(6, false).createLegacyBlock());
        context.setBlock(lavaPos, Fluids.LAVA.getFlowing(4, false).createLegacyBlock());
        context.getLevel().scheduleTick(context.absolutePos(WATER_POS), Fluids.WATER, Fluids.WATER.getTickDelay(context.getLevel()));

        context.runAfterDelay(10, () -> {
            context.assertBlockPresent(Blocks.WATER, WATER_POS);
            context.assertBlockPresent(Blocks.COBBLESTONE, lavaPos);
            context.succeed();
        });
    }

    @GameTest(maxTicks = 80)
    public void lavaSourceBesideWaterSolidifiesLikeVanillaContact(GameTestHelper context) {
        BlockPos waterPos = WATER_POS.relative(Direction.EAST);
        context.setBlock(WATER_POS.below(), Blocks.STONE);
        context.setBlock(waterPos.below(), Blocks.STONE);
        context.setBlock(WATER_POS, Blocks.LAVA.defaultBlockState());
        context.setBlock(waterPos, Blocks.WATER.defaultBlockState());
        context.getLevel().scheduleTick(context.absolutePos(WATER_POS), Fluids.LAVA, Fluids.LAVA.getTickDelay(context.getLevel()));

        context.runAfterDelay(40, () -> {
            context.assertBlockPresent(Blocks.OBSIDIAN, WATER_POS);
            context.assertBlockPresent(Blocks.WATER, waterPos);
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
    public void structuralStressLowersErosionThreshold(GameTestHelper context) {
        context.setBlock(WATER_POS, Blocks.STONE.defaultBlockState());
        double intactThreshold = ErosionPhysics.erosionThreshold(
                context.getLevel(),
                context.absolutePos(WATER_POS),
                context.getBlockState(WATER_POS));

        EnvironmentalExposure.addStructuralStress(
                context.getLevel(),
                context.absolutePos(WATER_POS),
                context.getBlockState(WATER_POS),
                MaterialPhysicsProfiles.structuralStressThreshold(context.getBlockState(WATER_POS)) * 0.75);
        double stressedThreshold = ErosionPhysics.erosionThreshold(
                context.getLevel(),
                context.absolutePos(WATER_POS),
                context.getBlockState(WATER_POS));

        context.assertTrue(stressedThreshold < intactThreshold,
                "pre-stressed material should require less hydraulic wear to erode");
        assertClose(
                ErosionPhysics.structuralStressErosionFactor(0.0, 1.0),
                1.0,
                "unstressed material should keep its full erosion threshold");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void preStressedStoneErodesBeforeIntactStone(GameTestHelper context) {
        BlockPos stressedWaterPos = new BlockPos(1, 2, 1);
        BlockPos stressedTargetPos = stressedWaterPos.relative(Direction.EAST);
        BlockPos intactWaterPos = new BlockPos(1, 4, 1);
        BlockPos intactTargetPos = intactWaterPos.relative(Direction.EAST);
        context.setBlock(stressedWaterPos, Blocks.WATER.defaultBlockState());
        context.setBlock(stressedTargetPos, Blocks.STONE.defaultBlockState());
        context.setBlock(intactWaterPos, Blocks.WATER.defaultBlockState());
        context.setBlock(intactTargetPos, Blocks.STONE.defaultBlockState());

        double stressThreshold = MaterialPhysicsProfiles.structuralStressThreshold(context.getBlockState(stressedTargetPos));
        EnvironmentalExposure.addStructuralStress(
                context.getLevel(),
                context.absolutePos(stressedTargetPos),
                context.getBlockState(stressedTargetPos),
                stressThreshold * 0.9);
        int repetitions = (int) Math.ceil(ErosionPhysics.erosionThreshold(
                context.getLevel(),
                context.absolutePos(stressedTargetPos),
                context.getBlockState(stressedTargetPos)) / 10.0);

        applyFlowErosion(context, stressedWaterPos, Direction.EAST, 8, repetitions);
        applyFlowErosion(context, intactWaterPos, Direction.EAST, 8, repetitions);

        context.assertBlockPresent(Blocks.COBBLESTONE, stressedTargetPos);
        context.assertBlockPresent(Blocks.STONE, intactTargetPos);
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void storedMoistureLowersSoftMaterialErosionThreshold(GameTestHelper context) {
        context.setBlock(WATER_POS, Blocks.SAND.defaultBlockState());
        double dryThreshold = ErosionPhysics.erosionThreshold(
                context.getLevel(),
                context.absolutePos(WATER_POS),
                context.getBlockState(WATER_POS));

        EnvironmentalExposure.addMoisture(
                context.getLevel(),
                context.absolutePos(WATER_POS),
                context.getBlockState(WATER_POS),
                1.0);
        double wetThreshold = ErosionPhysics.erosionThreshold(
                context.getLevel(),
                context.absolutePos(WATER_POS),
                context.getBlockState(WATER_POS));

        context.assertTrue(wetThreshold < dryThreshold,
                "stored moisture should reduce cohesion and lower the erosion threshold for soft material");
        context.assertTrue(ErosionPhysics.moistureErosionFactor(Blocks.STONE.defaultBlockState(), 1.0)
                        > ErosionPhysics.moistureErosionFactor(Blocks.SAND.defaultBlockState(), 1.0),
                "dense rock should be less moisture-weakened than absorbent soft material");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void wetSandWashesAwayBeforeDrySand(GameTestHelper context) {
        BlockPos waterPos = new BlockPos(1, 2, 1);
        BlockPos targetPos = waterPos.relative(Direction.EAST);
        context.setBlock(waterPos, Blocks.WATER.defaultBlockState());
        context.setBlock(targetPos, Blocks.SAND.defaultBlockState());
        int movedAmount = 1;
        double wearPerImpulse = EnvironmentalExposure.hydraulicWearFromMovedWater(movedAmount, 1.0, 1.25);
        double wetThreshold = ErosionPhysics.erosionThreshold(
                context.getLevel(),
                context.absolutePos(targetPos),
                context.getBlockState(targetPos))
                * ErosionPhysics.moistureErosionFactor(context.getBlockState(targetPos), 1.0);
        int wetErosionRepetitions = Math.max(1, (int) Math.ceil(wetThreshold / wearPerImpulse));

        applyFlowErosion(context, waterPos, Direction.EAST, movedAmount, wetErosionRepetitions);
        context.assertBlockPresent(Blocks.SAND, targetPos);
        EnvironmentalExposure.clearHydraulicWear(context.getLevel(), context.absolutePos(targetPos));

        EnvironmentalExposure.addMoisture(
                context.getLevel(),
                context.absolutePos(targetPos),
                context.getBlockState(targetPos),
                1.0);
        applyFlowErosion(context, waterPos, Direction.EAST, movedAmount, wetErosionRepetitions);

        context.assertBlockPresent(Blocks.AIR, targetPos);
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void erosionAccumulatesWearBeforeWashingAwayWeakMaterial(GameTestHelper context) {
        BlockPos waterPos = new BlockPos(1, 2, 1);
        BlockPos targetPos = waterPos.relative(Direction.EAST);
        context.setBlock(waterPos, Blocks.WATER.defaultBlockState());
        context.setBlock(targetPos, Blocks.SAND.defaultBlockState());

        applyFlowErosion(context, waterPos, Direction.EAST, 8, 2);
        context.assertBlockPresent(Blocks.SAND, targetPos);

        applyFlowErosion(context, waterPos, Direction.EAST, 8, 4);
        context.assertBlockPresent(Blocks.AIR, targetPos);
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void erosionWearResetsWhenTargetBlockChanges(GameTestHelper context) {
        BlockPos waterPos = new BlockPos(1, 2, 1);
        BlockPos targetPos = waterPos.relative(Direction.EAST);
        context.setBlock(waterPos, Blocks.WATER.defaultBlockState());
        context.setBlock(targetPos, Blocks.SAND.defaultBlockState());

        applyFlowErosion(context, waterPos, Direction.EAST, 8, 2);
        context.assertBlockPresent(Blocks.SAND, targetPos);

        context.setBlock(targetPos, Blocks.GLASS.defaultBlockState());
        applyFlowErosion(context, waterPos, Direction.EAST, 1, 1);
        context.assertBlockPresent(Blocks.GLASS, targetPos);
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
    public void sedimentLadenWaterAbradesStoneThatClearWaterCannot(GameTestHelper context) {
        BlockPos clearWaterPos = new BlockPos(1, 2, 1);
        BlockPos clearTargetPos = clearWaterPos.relative(Direction.EAST);
        BlockPos abrasiveWaterPos = new BlockPos(1, 4, 1);
        BlockPos abrasiveTargetPos = abrasiveWaterPos.relative(Direction.EAST);
        context.setBlock(clearWaterPos, Blocks.WATER.defaultBlockState());
        context.setBlock(clearTargetPos, Blocks.STONE.defaultBlockState());
        context.setBlock(abrasiveWaterPos, Blocks.WATER.defaultBlockState());
        context.setBlock(abrasiveTargetPos, Blocks.STONE.defaultBlockState());

        double sedimentKilograms = 250.0;
        EnvironmentalExposure.addSuspendedSediment(
                context.getLevel(),
                context.absolutePos(abrasiveWaterPos),
                context.getBlockState(abrasiveWaterPos),
                sedimentKilograms);

        int movedAmount = 8;
        double clearWear = EnvironmentalExposure.hydraulicWearFromMovedWater(movedAmount, 1.0, 1.25);
        double abrasiveMultiplier = EnvironmentalExposure.hydraulicAbrasionMultiplier(sedimentKilograms, 8);
        int repetitions = Math.max(1, (int) Math.ceil(ErosionPhysics.erosionThreshold(
                context.getLevel(),
                context.absolutePos(abrasiveTargetPos),
                context.getBlockState(abrasiveTargetPos)) / (clearWear * abrasiveMultiplier)));

        applyFlowErosion(context, clearWaterPos, Direction.EAST, movedAmount, repetitions);
        applyFlowErosion(context, abrasiveWaterPos, Direction.EAST, movedAmount, repetitions);

        context.assertBlockPresent(Blocks.STONE, clearTargetPos);
        context.assertBlockPresent(Blocks.COBBLESTONE, abrasiveTargetPos);
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

    @GameTest(maxTicks = 20)
    public void erosionSedimentCanDepositFromSettledShallowWater(GameTestHelper context) {
        BlockPos waterPos = new BlockPos(1, 2, 1);
        context.setBlock(waterPos.below(), Blocks.STONE);
        context.setBlock(waterPos, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 7));
        EnvironmentalExposure.addSuspendedSediment(
                context.getLevel(),
                context.absolutePos(waterPos),
                context.getBlockState(waterPos),
                70.0);

        boolean deposited = ErosionPhysics.tryDepositSediment(
                context.getLevel(),
                context.absolutePos(waterPos),
                Fluids.WATER,
                1);

        context.assertTrue(deposited, "settled shallow water should be able to deposit carried sediment");
        context.assertBlockPresent(Blocks.MUD, waterPos);
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void fineSettledSedimentCanDepositClay(GameTestHelper context) {
        BlockPos waterPos = new BlockPos(1, 2, 1);
        context.setBlock(waterPos.below(), Blocks.STONE);
        context.setBlock(waterPos, Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 7));
        EnvironmentalExposure.addSuspendedSediment(
                context.getLevel(),
                context.absolutePos(waterPos),
                context.getBlockState(waterPos),
                50.0);

        boolean deposited = ErosionPhysics.tryDepositSediment(
                context.getLevel(),
                context.absolutePos(waterPos),
                Fluids.WATER,
                1);

        context.assertTrue(deposited, "quiet shallow water should be able to settle fine suspended sediment");
        context.assertBlockPresent(Blocks.CLAY, waterPos);
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void sedimentDepositProfileIncludesFineAndCoarseProducts(GameTestHelper context) {
        context.assertTrue(MaterialPhysicsProfiles.sedimentDepositState(20.0, 8).is(Blocks.DIRT),
                "light dispersed sediment should deposit as general soil");
        context.assertTrue(MaterialPhysicsProfiles.sedimentDepositState(50.0, 1).is(Blocks.CLAY),
                "near-threshold fine sediment in settled water should deposit as clay");
        context.assertTrue(MaterialPhysicsProfiles.sedimentDepositState(70.0, 1).is(Blocks.MUD),
                "moderate saturated sediment should deposit as mud");
        context.assertTrue(MaterialPhysicsProfiles.sedimentDepositState(100.0, 1).is(Blocks.SAND),
                "heavier bed-load sediment should deposit as sand");
        context.assertTrue(MaterialPhysicsProfiles.sedimentDepositState(130.0, 1).is(Blocks.GRAVEL),
                "very heavy coarse sediment should deposit as gravel");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void materialProfilesUsePhysicallyOrderedDensities(GameTestHelper context) {
        context.assertTrue(
                MaterialPhysicsProfiles.densityKilogramsPerCubicMeter(Blocks.STONE.defaultBlockState())
                        > MaterialPhysicsProfiles.densityKilogramsPerCubicMeter(Blocks.DIRT.defaultBlockState()),
                "stone should be denser than soil");
        context.assertTrue(
                MaterialPhysicsProfiles.densityKilogramsPerCubicMeter(Blocks.DIRT.defaultBlockState())
                        > MaterialPhysicsProfiles.densityKilogramsPerCubicMeter(Blocks.OAK_LEAVES.defaultBlockState()),
                "soil should be denser than leaves");
        context.succeed();
    }

    private static void applyFlowErosion(GameTestHelper context, BlockPos waterPos, Direction direction, int movedAmount, int repetitions) {
        for (int i = 0; i < repetitions; i++) {
            ErosionPhysics.attemptFlowErosion(
                    context.getLevel(),
                    context.absolutePos(waterPos),
                    context.getBlockState(waterPos).getFluidState(),
                    direction,
                    movedAmount);
        }
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

    private static void assertClose(double actual, double expected, String message) {
        if (Math.abs(actual - expected) > 1.0E-6) {
            throw new AssertionError(message + ": " + actual + " != " + expected);
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
