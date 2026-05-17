package com.teddante.emergent.gametest;

import com.teddante.emergent.EmergentConfig;
import com.teddante.emergent.EnvironmentalExposure;
import com.teddante.emergent.FireWetness;
import com.teddante.emergent.MaterialPhysicsProfiles;
import com.teddante.emergent.MaterialReactions;
import com.teddante.emergent.TrafficWearPhysics;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;

public class EmergentFireGameTest implements CustomTestMethodInvoker {
    private static final BlockPos TEST_POS = new BlockPos(2, 3, 2);

    @GameTest(maxTicks = 20)
    public void grassBlockCanScorchToDirt(GameTestHelper context) {
        context.setBlock(TEST_POS, Blocks.GRASS_BLOCK);

        for (int seed = 0; seed < 128 && !context.getBlockState(TEST_POS).is(Blocks.DIRT); seed++) {
            MaterialReactions.tryScorchToDirtFromFire(
                    context.getLevel(),
                    context.absolutePos(TEST_POS),
                    context.getBlockState(TEST_POS),
                    RandomSource.create(seed));
        }

        context.assertBlockPresent(Blocks.DIRT, TEST_POS);
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void wetGrassBlockCanResistScorching(GameTestHelper context) {
        context.setBlock(TEST_POS, Blocks.GRASS_BLOCK);
        context.setBlock(TEST_POS.relative(Direction.EAST), Blocks.WATER);

        boolean resisted = MaterialReactions.tryScorchToDirtFromFire(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS),
                RandomSource.create(0));

        context.assertTrue(resisted, "wet scorched-surface reaction should be handled");
        context.assertBlockPresent(Blocks.GRASS_BLOCK, TEST_POS);
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void organicBlocksBurnAway(GameTestHelper context) {
        context.setBlock(TEST_POS.below(), Blocks.GRASS_BLOCK);
        context.setBlock(TEST_POS, Blocks.SHORT_GRASS);

        boolean reacted = MaterialReactions.tryBurnAwayFromFire(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS),
                RandomSource.create(1));

        context.assertTrue(reacted, "tagged organic block should react to fire");
        context.assertBlockPresent(Blocks.AIR, TEST_POS);
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void dryFlashFuelBurnsAway(GameTestHelper context) {
        context.setBlock(TEST_POS.below(), Blocks.NETHERRACK);
        context.setBlock(TEST_POS, Blocks.LEAF_LITTER);

        boolean reacted = MaterialReactions.tryFlashBurnFromFire(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS),
                RandomSource.create(2));

        context.assertTrue(reacted, "tagged flash fuel should react to fire");
        context.assertBlockPresent(Blocks.AIR, TEST_POS);
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void logsCharWithoutLosingAxis(GameTestHelper context) {
        context.setBlock(TEST_POS, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X));

        boolean reacted = MaterialReactions.exposeToFire(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS),
                10.0f,
                RandomSource.create(3));

        context.assertTrue(reacted, "char-tagged log should react to fire");
        context.assertBlockPresent(Blocks.STRIPPED_OAK_LOG, TEST_POS);
        context.assertTrue(context.getBlockState(TEST_POS).getValue(RotatedPillarBlock.AXIS) == Direction.Axis.X,
                "charred log should preserve the original axis");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void sustainedFireExposureCharsLogDeterministically(GameTestHelper context) {
        context.setBlock(TEST_POS, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));
        RandomSource random = RandomSource.create(12);

        for (int i = 0; i < 4; i++) {
            MaterialReactions.exposeToFire(
                    context.getLevel(),
                    context.absolutePos(TEST_POS),
                    context.getBlockState(TEST_POS),
                    1.0f,
                    random);
            context.assertBlockPresent(Blocks.OAK_LOG, TEST_POS);
        }

        MaterialReactions.exposeToFire(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS),
                6.0f,
                random);

        context.assertBlockPresent(Blocks.STRIPPED_OAK_LOG, TEST_POS);
        context.assertTrue(context.getBlockState(TEST_POS).getValue(RotatedPillarBlock.AXIS) == Direction.Axis.Z,
                "deterministic fire exposure should preserve log axis when charring");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void wetSurfaceNeedsMoreHeatBeforeScorching(GameTestHelper context) {
        BlockPos waterPos = TEST_POS.relative(Direction.EAST);
        context.setBlock(TEST_POS, Blocks.GRASS_BLOCK);
        context.setBlock(waterPos, Blocks.WATER);
        RandomSource random = RandomSource.create(13);

        for (int i = 0; i < 5; i++) {
            MaterialReactions.exposeToFire(
                    context.getLevel(),
                    context.absolutePos(TEST_POS),
                    context.getBlockState(TEST_POS),
                    1.5f,
                    random);
        }

        context.assertBlockPresent(Blocks.GRASS_BLOCK, TEST_POS);
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void storedMoistureDampensLaterFireExposure(GameTestHelper context) {
        context.setBlock(TEST_POS, Blocks.GRASS_BLOCK);
        EnvironmentalExposure.addMoisture(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS),
                0.75);
        context.assertTrue(FireWetness.getWetness(context.getLevel(), context.absolutePos(TEST_POS)) >= 0.74f,
                "stored surface moisture should feed the same wetness model used by fire");

        RandomSource random = RandomSource.create(14);
        for (int i = 0; i < 5; i++) {
            MaterialReactions.exposeToFire(
                    context.getLevel(),
                    context.absolutePos(TEST_POS),
                    context.getBlockState(TEST_POS),
                    1.5f,
                    random);
        }

        context.assertBlockPresent(Blocks.GRASS_BLOCK, TEST_POS);
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void rainfallMoistureUsesSurfaceMaterialAbsorption(GameTestHelper context) {
        double grassMoisture = EnvironmentalExposure.rainfallSurfaceMoisture(Blocks.GRASS_BLOCK.defaultBlockState(), 0.001);
        double stoneMoisture = EnvironmentalExposure.rainfallSurfaceMoisture(Blocks.STONE.defaultBlockState(), 0.001);

        context.assertTrue(grassMoisture > stoneMoisture,
                "absorbent soil should store more rainwater than stone at the same rainfall depth");
        context.assertTrue(grassMoisture > 0.04 && grassMoisture < 0.06,
                "one millimetre of rain should wet the active soil surface by a small physical amount");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void hotExposedAirDriesStoredSurfaceMoisture(GameTestHelper context) {
        context.setBlock(TEST_POS, Blocks.GRASS_BLOCK);
        EnvironmentalExposure.addMoisture(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS),
                0.5);

        EnvironmentalExposure.applyAmbientSurfaceExchange(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS),
                2.0f,
                true,
                0);

        context.assertTrue(EnvironmentalExposure.moisture(context.getLevel(), context.absolutePos(TEST_POS), context.getBlockState(TEST_POS)) < 0.5,
                "hot sky-exposed ambient exchange should dry stored surface moisture");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void localHeatPreheatsAndDriesSurfaceExposure(GameTestHelper context) {
        context.setBlock(TEST_POS, Blocks.OAK_LOG);
        EnvironmentalExposure.addMoisture(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS),
                0.5);

        EnvironmentalExposure.applyAmbientSurfaceExchange(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS),
                0.8f,
                false,
                2);

        context.assertTrue(EnvironmentalExposure.heat(context.getLevel(), context.absolutePos(TEST_POS), context.getBlockState(TEST_POS)) > 0.0,
                "local heat should add stored heat exposure");
        context.assertTrue(EnvironmentalExposure.moisture(context.getLevel(), context.absolutePos(TEST_POS), context.getBlockState(TEST_POS)) < 0.5,
                "local heat should dry stored moisture");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void coldExposureCoolsStoredHeat(GameTestHelper context) {
        context.setBlock(TEST_POS, Blocks.STONE);
        EnvironmentalExposure.addHeat(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS),
                1.0);
        EnvironmentalExposure.addCold(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS),
                0.4);

        context.assertTrue(EnvironmentalExposure.cold(context.getLevel(), context.absolutePos(TEST_POS), context.getBlockState(TEST_POS)) > 0.0,
                "cold exposure should accumulate as shared environmental state");
        context.assertTrue(EnvironmentalExposure.heat(context.getLevel(), context.absolutePos(TEST_POS), context.getBlockState(TEST_POS)) < 1.0,
                "cold exposure should cool stored heat");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void snowfallAddsColdAndMoistureToSurface(GameTestHelper context) {
        context.setBlock(TEST_POS, Blocks.GRASS_BLOCK);

        EnvironmentalExposure.addSnowfall(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS));

        context.assertTrue(EnvironmentalExposure.cold(context.getLevel(), context.absolutePos(TEST_POS), context.getBlockState(TEST_POS)) > 0.0,
                "snowfall should add cold exposure");
        context.assertTrue(EnvironmentalExposure.moisture(context.getLevel(), context.absolutePos(TEST_POS), context.getBlockState(TEST_POS)) > 0.0,
                "snowfall should add stored surface moisture");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void burnedOrganicMatterLeavesAshResidueOnSoil(GameTestHelper context) {
        BlockPos leavesPos = TEST_POS.above();
        context.setBlock(TEST_POS, Blocks.DIRT);
        context.setBlock(leavesPos, Blocks.OAK_LEAVES);

        boolean reacted = MaterialReactions.tryBurnAwayFromFire(
                context.getLevel(),
                context.absolutePos(leavesPos),
                context.getBlockState(leavesPos),
                RandomSource.create(21));

        context.assertTrue(reacted, "burn-away organic matter should react to fire");
        context.assertBlockPresent(Blocks.AIR, leavesPos);
        context.assertTrue(EnvironmentalExposure.ashResidue(context.getLevel(), context.absolutePos(TEST_POS), context.getBlockState(TEST_POS)) > 0.0,
                "burned organic matter should leave ash residue on the supporting soil");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void ashResidueCanEnrichLaterRainGrowth(GameTestHelper context) {
        context.setBlock(TEST_POS.below(), Blocks.FARMLAND);
        context.setBlock(TEST_POS, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 0));
        EnvironmentalExposure.addAshResidue(
                context.getLevel(),
                context.absolutePos(TEST_POS.below()),
                context.getBlockState(TEST_POS.below()),
                2.0);

        context.assertTrue(
                EnvironmentalExposure.ashGrowthBonus(context.getLevel(), context.absolutePos(TEST_POS.below()), context.getBlockState(TEST_POS.below())) > 0.0,
                "ash residue should be available as a small growth bonus for later rain growth");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void waterContactCarriesAshAsSuspendedSediment(GameTestHelper context) {
        BlockPos waterPos = TEST_POS.above();
        context.setBlock(TEST_POS, Blocks.DIRT);
        context.setBlock(waterPos, Blocks.WATER);
        EnvironmentalExposure.addAshResidue(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS),
                2.0);

        double washedAsh = EnvironmentalExposure.washAshIntoWater(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS),
                context.absolutePos(waterPos),
                context.getBlockState(waterPos),
                8);

        context.assertTrue(washedAsh > 0.0,
                "water contacting ash should pick up some residue as suspended fine sediment");
        context.assertTrue(
                EnvironmentalExposure.ashResidue(context.getLevel(), context.absolutePos(TEST_POS), context.getBlockState(TEST_POS)) < 2.0,
                "ash runoff should consume residue from the surface it contacted");
        context.assertTrue(
                EnvironmentalExposure.suspendedSediment(context.getLevel(), context.absolutePos(waterPos), context.getBlockState(waterPos)) == washedAsh,
                "washed ash mass should enter the same suspended sediment memory used by hydraulic erosion");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void ashRunoffScalesWithWaterVolume(GameTestHelper context) {
        double thinFilmCapacity = EnvironmentalExposure.ashRunoffCapacityKilograms(1);
        double sourceCapacity = EnvironmentalExposure.ashRunoffCapacityKilograms(8);

        context.assertTrue(sourceCapacity > thinFilmCapacity,
                "larger water volumes should carry more ash residue than thin films");
        assertClose(sourceCapacity, EnvironmentalExposure.fluidAmountCubicMeters(8) * 2.0,
                "ash runoff should be expressed as suspended-solids mass per cubic metre of water");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void moistureRelievesAccumulatedVegetationStress(GameTestHelper context) {
        context.setBlock(TEST_POS, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 2));
        EnvironmentalExposure.addVegetationStress(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS),
                0.6);
        EnvironmentalExposure.addMoisture(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS),
                0.5);

        context.assertTrue(
                EnvironmentalExposure.vegetationStress(context.getLevel(), context.absolutePos(TEST_POS), context.getBlockState(TEST_POS)) < 0.6,
                "stored moisture should relieve accumulated vegetation stress");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void dryHeatCanRegressCropGrowth(GameTestHelper context) {
        context.setBlock(TEST_POS.below(), Blocks.FARMLAND);
        context.setBlock(TEST_POS, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 3));
        EnvironmentalExposure.addHeat(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS),
                2.0);

        MaterialReactions.tryClimateStress(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS),
                1.2f,
                true);

        context.assertTrue(context.getBlockState(TEST_POS).getValue(CropBlock.AGE) < 3,
                "dry heat should be able to regress crop growth before killing the plant outright");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void wetCropResistsDryHeatStress(GameTestHelper context) {
        context.setBlock(TEST_POS.below(), Blocks.FARMLAND);
        context.setBlock(TEST_POS, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 3));
        EnvironmentalExposure.addMoisture(
                context.getLevel(),
                context.absolutePos(TEST_POS.below()),
                context.getBlockState(TEST_POS.below()),
                1.0);
        EnvironmentalExposure.addHeat(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS),
                2.0);

        for (int i = 0; i < 4; i++) {
            MaterialReactions.tryClimateStress(
                    context.getLevel(),
                    context.absolutePos(TEST_POS),
                    context.getBlockState(TEST_POS),
                    1.2f,
                    true);
        }

        context.assertTrue(context.getBlockState(TEST_POS).getValue(CropBlock.AGE) == 3,
                "nearby stored soil moisture should stop dry heat from stressing the crop");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void repeatedDryHeatCanTurnGrassBlockToDirt(GameTestHelper context) {
        context.setBlock(TEST_POS, Blocks.GRASS_BLOCK);

        for (int i = 0; i < 4 && context.getBlockState(TEST_POS).is(Blocks.GRASS_BLOCK); i++) {
            EnvironmentalExposure.addHeat(
                    context.getLevel(),
                    context.absolutePos(TEST_POS),
                    context.getBlockState(TEST_POS),
                    2.0);
            MaterialReactions.tryClimateStress(
                    context.getLevel(),
                    context.absolutePos(TEST_POS),
                    context.getBlockState(TEST_POS),
                    1.2f,
                    true);
        }

        context.assertBlockPresent(Blocks.DIRT, TEST_POS);
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void structuralStressAccumulatesInExposureMemory(GameTestHelper context) {
        context.setBlock(TEST_POS, Blocks.GLASS);
        double threshold = MaterialPhysicsProfiles.structuralStressThreshold(context.getBlockState(TEST_POS));
        double stress = EnvironmentalExposure.addStructuralStress(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS),
                threshold * 0.5);
        stress = EnvironmentalExposure.addStructuralStress(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS),
                threshold * 0.51);

        context.assertTrue(stress > threshold, "repeated impacts should be able to accumulate structural stress");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void waterQuenchingHotBrittleMaterialAddsStructuralStress(GameTestHelper context) {
        context.setBlock(TEST_POS, Blocks.GLASS);
        EnvironmentalExposure.addHeat(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS),
                1.0);
        EnvironmentalExposure.addMoisture(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS),
                0.25);

        context.assertTrue(
                EnvironmentalExposure.structuralStress(context.getLevel(), context.absolutePos(TEST_POS), context.getBlockState(TEST_POS)) > 0.0,
                "rapid water cooling should feed thermal shock into the same structural stress memory");
        context.assertBlockPresent(Blocks.GLASS, TEST_POS);
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void repeatedHotStoneQuenchingFracturesToCobblestone(GameTestHelper context) {
        context.setBlock(TEST_POS, Blocks.STONE);
        for (int i = 0; i < 6 && context.getBlockState(TEST_POS).is(Blocks.STONE); i++) {
            EnvironmentalExposure.addHeat(
                    context.getLevel(),
                    context.absolutePos(TEST_POS),
                    context.getBlockState(TEST_POS),
                    2.0);
            EnvironmentalExposure.addMoisture(
                    context.getLevel(),
                    context.absolutePos(TEST_POS),
                    context.getBlockState(TEST_POS),
                    1.0);
        }

        context.assertBlockPresent(Blocks.COBBLESTONE, TEST_POS);
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void repeatedHotGlassQuenchingCanShatter(GameTestHelper context) {
        context.setBlock(TEST_POS, Blocks.GLASS);
        for (int i = 0; i < 4 && context.getBlockState(TEST_POS).is(Blocks.GLASS); i++) {
            EnvironmentalExposure.addHeat(
                    context.getLevel(),
                    context.absolutePos(TEST_POS),
                    context.getBlockState(TEST_POS),
                    1.0);
            EnvironmentalExposure.addMoisture(
                    context.getLevel(),
                    context.absolutePos(TEST_POS),
                    context.getBlockState(TEST_POS),
                    1.0);
        }

        context.assertBlockPresent(Blocks.AIR, TEST_POS);
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void denseFuelCanSustainFireAboveIt(GameTestHelper context) {
        BlockPos firePos = TEST_POS.above();
        context.setBlock(TEST_POS, Blocks.COAL_BLOCK);
        context.setBlock(firePos, Blocks.AIR);

        boolean reacted = MaterialReactions.trySustainFire(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS),
                RandomSource.create(4));

        context.assertTrue(reacted, "sustaining fuel should react to fire");
        context.assertBlockPresent(Blocks.FIRE, firePos);
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void wetnessSeesWaterloggedBlocksAndNearbyWater(GameTestHelper context) {
        context.setBlock(TEST_POS, Blocks.CAMPFIRE.defaultBlockState()
                .setValue(CampfireBlock.LIT, false)
                .setValue(BlockStateProperties.WATERLOGGED, true));
        context.assertTrue(FireWetness.getWetness(context.getLevel(), context.absolutePos(TEST_POS)) >= 0.95f,
                "waterlogged blocks should be almost fully wet");

        BlockPos dryPos = TEST_POS.relative(Direction.EAST, 2);
        context.setBlock(dryPos, Blocks.STONE);
        context.setBlock(dryPos.below(), Blocks.WATER);
        context.assertTrue(FireWetness.getWetness(context.getLevel(), context.absolutePos(dryPos)) >= 0.65f,
                "water below a block should strongly dampen ignition");

        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void fireReactionCategoriesCoverExpectedVanillaBlocks(GameTestHelper context) {
        context.assertTrue(MaterialReactions.canReactToFire(Blocks.GRASS_BLOCK.defaultBlockState()),
                "grass block should be a reactive scorch surface");
        context.assertTrue(MaterialReactions.canReactToFire(Blocks.OAK_LEAVES.defaultBlockState()),
                "leaves should be a reactive burn-away organic");
        context.assertTrue(MaterialReactions.canReactToFire(Blocks.LEAF_LITTER.defaultBlockState()),
                "leaf litter should be a reactive flash fuel");
        context.assertTrue(MaterialReactions.canReactToFire(Blocks.COAL_BLOCK.defaultBlockState()),
                "coal block should sustain fire");
        context.assertTrue(!MaterialReactions.canReactToFire(Blocks.STONE.defaultBlockState()),
                "stone should not be treated as a fire-reactive material");
        context.succeed();
    }

    @GameTest(maxTicks = 80)
    public void tickingFireDoesNotScorchDisconnectedGrass(GameTestHelper context) {
        BlockPos firePos = TEST_POS;
        BlockPos grassPos = firePos.relative(Direction.EAST, 2);
        context.setBlock(firePos.below(), Blocks.OAK_LOG);
        context.setBlock(firePos, Blocks.FIRE.defaultBlockState().setValue(FireBlock.AGE, 15));
        context.setBlock(firePos.relative(Direction.EAST), Blocks.AIR);
        context.setBlock(grassPos, Blocks.GRASS_BLOCK);

        for (int tick = 1; tick <= 36; tick++) {
            context.runAtTickTime(tick, () -> {
                if (!context.getBlockState(firePos).is(Blocks.FIRE)) {
                    context.setBlock(firePos, Blocks.FIRE.defaultBlockState().setValue(FireBlock.AGE, 15));
                }
                context.getLevel().scheduleTick(context.absolutePos(firePos), Blocks.FIRE, 1);
            });
        }

        context.runAtTickTime(40, () -> {
            context.assertBlockPresent(Blocks.GRASS_BLOCK, grassPos);
            context.succeed();
        });
    }

    @GameTest(maxTicks = 20)
    public void repeatedTrafficCompactsGrassIntoPath(GameTestHelper context) {
        context.setBlock(TEST_POS, Blocks.GRASS_BLOCK);
        context.setBlock(TEST_POS.above(), Blocks.AIR);

        for (int i = 0; i < 24; i++) {
            TrafficWearPhysics.applyTraffic(context.getLevel(), context.absolutePos(TEST_POS), context.getBlockState(TEST_POS), 1.0);
        }

        context.assertBlockPresent(Blocks.DIRT_PATH, TEST_POS);
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void trafficWearUsesContactAreaAndBodyHeightUnits(GameTestHelper context) {
        assertClose(
                EnvironmentalExposure.trafficWearFromContact(1.0, 1.0, 1.8),
                1.8,
                "one metre of travel over one square metre should scale by body-height pressure proxy");
        assertClose(
                EnvironmentalExposure.trafficWearFromContact(2.0, 0.25, 1.0),
                0.5,
                "traffic wear should scale with partial block contact area");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void wetTrafficCompactsSoonerThanDryTraffic(GameTestHelper context) {
        BlockPos wetPos = TEST_POS;
        BlockPos dryPos = TEST_POS.relative(Direction.EAST, 2);
        context.setBlock(wetPos, Blocks.GRASS_BLOCK);
        context.setBlock(wetPos.above(), Blocks.AIR);
        context.setBlock(dryPos, Blocks.GRASS_BLOCK);
        context.setBlock(dryPos.above(), Blocks.AIR);
        EnvironmentalExposure.addMoisture(
                context.getLevel(),
                context.absolutePos(wetPos),
                context.getBlockState(wetPos),
                1.0);

        for (int i = 0; i < 16; i++) {
            TrafficWearPhysics.applyTraffic(context.getLevel(), context.absolutePos(wetPos), context.getBlockState(wetPos), 1.0);
            TrafficWearPhysics.applyTraffic(context.getLevel(), context.absolutePos(dryPos), context.getBlockState(dryPos), 1.0);
        }

        context.assertBlockPresent(Blocks.DIRT_PATH, wetPos);
        context.assertBlockPresent(Blocks.GRASS_BLOCK, dryPos);
        context.succeed();
    }

    @GameTest(maxTicks = 220)
    public void movingEntityDoesNotCompactCoveredGround(GameTestHelper context) {
        context.setBlock(TEST_POS, Blocks.DIRT);
        context.setBlock(TEST_POS.above(), Blocks.STONE);
        Cow cow = context.spawn(EntityType.COW, Vec3.atBottomCenterOf(TEST_POS.above(2)));
        cow.setNoGravity(true);

        for (int tick = 1; tick <= 200; tick++) {
            final int step = tick;
            context.runAtTickTime(tick, () -> {
                double direction = step % 2 == 0 ? 1.0 : -1.0;
                Vec3 movement = new Vec3(direction * 0.35, 0.0, 0.0);
                cow.setOnGround(true);
                cow.setDeltaMovement(movement);
                cow.move(MoverType.SELF, movement);
            });
        }

        context.runAtTickTime(210, () -> {
            context.assertBlockPresent(Blocks.DIRT, TEST_POS);
            context.succeed();
        });
    }

    @GameTest(maxTicks = 20)
    public void wideTrafficCompactsWholeContactPatch(GameTestHelper context) {
        BlockPos base = TEST_POS;
        for (int x = 0; x <= 1; x++) {
            for (int z = 0; z <= 1; z++) {
                BlockPos pos = base.offset(x, 0, z);
                context.setBlock(pos, Blocks.GRASS_BLOCK);
                context.setBlock(pos.above(), Blocks.AIR);
            }
        }

        BlockPos absoluteBase = context.absolutePos(base);
        AABB footprint = new AABB(
                absoluteBase.getX(),
                absoluteBase.getY() + 1.0,
                absoluteBase.getZ(),
                absoluteBase.getX() + 2.0,
                absoluteBase.getY() + 2.8,
                absoluteBase.getZ() + 2.0);

        for (int i = 0; i < 12; i++) {
            TrafficWearPhysics.applyContactPatchTraffic(context.getLevel(), footprint, 1.8, 1.0);
        }

        for (int x = 0; x <= 1; x++) {
            for (int z = 0; z <= 1; z++) {
                context.assertBlockPresent(Blocks.DIRT_PATH, base.offset(x, 0, z));
            }
        }
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void trafficWearResetsWhenGroundChanges(GameTestHelper context) {
        context.setBlock(TEST_POS, Blocks.GRASS_BLOCK);
        context.setBlock(TEST_POS.above(), Blocks.AIR);

        for (int i = 0; i < 12; i++) {
            TrafficWearPhysics.applyTraffic(context.getLevel(), context.absolutePos(TEST_POS), context.getBlockState(TEST_POS), 1.0);
        }

        context.setBlock(TEST_POS, Blocks.MOSS_BLOCK);
        TrafficWearPhysics.applyTraffic(context.getLevel(), context.absolutePos(TEST_POS), context.getBlockState(TEST_POS), 1.0);
        context.assertBlockPresent(Blocks.MOSS_BLOCK, TEST_POS);
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void rainCanOxidizeTaggedCopper(GameTestHelper context) {
        context.setBlock(TEST_POS, Blocks.COPPER_BLOCK);

        RandomSource random = RandomSource.create(6);
        for (int attempt = 0; attempt < 256 && !context.getBlockState(TEST_POS).is(Blocks.EXPOSED_COPPER); attempt++) {
            MaterialReactions.tryRainOxidize(
                    context.getLevel(),
                    context.absolutePos(TEST_POS),
                    context.getBlockState(TEST_POS),
                    random);
        }

        context.assertBlockPresent(Blocks.EXPOSED_COPPER, TEST_POS);
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void rainCanGrowTaggedCrops(GameTestHelper context) {
        context.setBlock(TEST_POS.below(), Blocks.FARMLAND);
        context.setBlock(TEST_POS, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 0));

        RandomSource random = RandomSource.create(7);
        for (int attempt = 0; attempt < 512 && context.getBlockState(TEST_POS).getValue(CropBlock.AGE) == 0; attempt++) {
            MaterialReactions.tryRainGrow(
                    context.getLevel(),
                    context.absolutePos(TEST_POS),
                    context.getBlockState(TEST_POS),
                    random);
        }

        context.assertTrue(context.getBlockState(TEST_POS).getValue(CropBlock.AGE) > 0,
                "rain growth should be able to advance a tagged crop");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void waterShortsPoweredConductiveNeighbors(GameTestHelper context) {
        BlockPos wirePos = TEST_POS.relative(Direction.EAST);
        context.setBlock(TEST_POS, Blocks.WATER);
        context.setBlock(wirePos.below(), Blocks.STONE);
        context.setBlock(wirePos, Blocks.REDSTONE_WIRE.defaultBlockState().setValue(RedStoneWireBlock.POWER, 15));

        MaterialReactions.shortConductiveNeighbors(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                RandomSource.create(5));

        context.assertTrue(context.getBlockState(wirePos).getValue(RedStoneWireBlock.POWER) == 0,
                "water should short powered conductive neighbors");
        context.succeed();
    }

    private static void assertClose(double actual, double expected, String message) {
        if (Math.abs(actual - expected) > 1.0E-6) {
            throw new AssertionError(message + ": " + actual + " != " + expected);
        }
    }

    @Override
    public void invokeTestMethod(GameTestHelper context, Method method) throws ReflectiveOperationException {
        boolean materialReactions = EmergentConfig.get().materialReactions;
        boolean wetnessFireDampening = EmergentConfig.get().wetnessFireDampening;

        EmergentConfig.get().materialReactions = true;
        EmergentConfig.get().wetnessFireDampening = true;

        try {
            method.invoke(this, context);
        } finally {
            EmergentConfig.get().materialReactions = materialReactions;
            EmergentConfig.get().wetnessFireDampening = wetnessFireDampening;
        }
    }
}
