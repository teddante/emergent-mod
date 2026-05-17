package com.teddante.emergent.gametest;

import com.teddante.emergent.EmergentConfig;
import com.teddante.emergent.DynamicExperience;
import com.teddante.emergent.ExperienceEnergy;
import com.teddante.emergent.EnvironmentalExposure;
import com.teddante.emergent.ExplosionEnvironmentPhysics;
import com.teddante.emergent.FireWetness;
import com.teddante.emergent.ImpactPhysics;
import com.teddante.emergent.MaterialPhysicsProfiles;
import com.teddante.emergent.MaterialReactions;
import com.teddante.emergent.StructuralStressPhysics;
import com.teddante.emergent.ThermalPhysics;
import com.teddante.emergent.TrafficWearPhysics;
import net.fabricmc.fabric.api.gametest.v1.CustomTestMethodInvoker;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.cow.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.EnchantedItemInUse;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.enchantment.LevelBasedValue;
import net.minecraft.world.item.enchantment.effects.Ignite;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.FireBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.entity.SculkCatalystBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.BlockPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
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
    public void charredLogLeavesSurfaceAshResidue(GameTestHelper context) {
        BlockPos supportPos = TEST_POS.below();
        context.setBlock(supportPos, Blocks.DIRT);
        context.setBlock(TEST_POS, Blocks.OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z));

        MaterialReactions.exposeToFire(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS),
                16.0f,
                RandomSource.create(42));

        context.assertBlockPresent(Blocks.STRIPPED_OAK_LOG, TEST_POS);
        context.assertTrue(
                EnvironmentalExposure.ashResidue(context.getLevel(), context.absolutePos(supportPos), context.getBlockState(supportPos)) > 0.0,
                "charred wood should shed surface ash into the same residue memory used by rain and growth");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void charAshIsLessThanWholeLogBurnAsh(GameTestHelper context) {
        double charAsh = MaterialPhysicsProfiles.ashKilogramsFromCharredSurface(Blocks.OAK_LOG.defaultBlockState());
        double burnAsh = MaterialPhysicsProfiles.ashKilogramsFromBurnedBlock(Blocks.OAK_LOG.defaultBlockState());

        context.assertTrue(charAsh > 0.0, "a charred wood surface should leave some ash residue");
        context.assertTrue(charAsh < burnAsh,
                "surface charring should produce less ash than consuming the whole log");
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
    public void humidClimateAddsMoreRainMoistureThanAridClimate(GameTestHelper context) {
        double aridMoisture = EnvironmentalExposure.rainfallSurfaceMoisture(Blocks.GRASS_BLOCK.defaultBlockState(), 0.001, 0.55);
        double humidMoisture = EnvironmentalExposure.rainfallSurfaceMoisture(Blocks.GRASS_BLOCK.defaultBlockState(), 0.001, 1.35);

        context.assertTrue(humidMoisture > aridMoisture,
                "humid tagged biomes should let the same rain sample store more surface moisture than arid tagged biomes");
        context.assertTrue(EnvironmentalExposure.climateMoistureFactor(0.0) > 0.0,
                "climate moisture factors should clamp instead of allowing impossible zero humidity");
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
    public void airExposureUsesBlockScaleSurfaceArea(GameTestHelper context) {
        assertClose(EnvironmentalExposure.airExposureAreaSquareMeters(true, 0), 1.0,
                "a sky-open top surface should expose one square metre of active surface");
        assertClose(EnvironmentalExposure.airExposureAreaSquareMeters(false, 4), 0.2,
                "four open sides should expose the one metre perimeter times the five centimetre active surface depth");
        context.assertTrue(EnvironmentalExposure.airExposureFactor(true, 4)
                        > EnvironmentalExposure.airExposureFactor(true, 0),
                "edge-exposed surfaces should exchange slightly more air than a flat open surface");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void sideExposedAirDriesShelteredSurface(GameTestHelper context) {
        BlockPos sealedPos = TEST_POS;
        BlockPos sideExposedPos = TEST_POS.relative(Direction.EAST, 2);
        context.setBlock(sealedPos, Blocks.GRASS_BLOCK);
        context.setBlock(sideExposedPos, Blocks.GRASS_BLOCK);
        EnvironmentalExposure.addMoisture(context.getLevel(), context.absolutePos(sealedPos), context.getBlockState(sealedPos), 0.5);
        EnvironmentalExposure.addMoisture(context.getLevel(), context.absolutePos(sideExposedPos), context.getBlockState(sideExposedPos), 0.5);

        EnvironmentalExposure.applyAmbientSurfaceExchange(
                context.getLevel(),
                context.absolutePos(sealedPos),
                context.getBlockState(sealedPos),
                0.7f,
                false,
                0,
                1.0,
                0.0,
                EnvironmentalExposure.airExposureFactor(false, 0));
        EnvironmentalExposure.applyAmbientSurfaceExchange(
                context.getLevel(),
                context.absolutePos(sideExposedPos),
                context.getBlockState(sideExposedPos),
                0.7f,
                false,
                0,
                1.0,
                0.0,
                EnvironmentalExposure.airExposureFactor(false, 4));

        context.assertTrue(
                EnvironmentalExposure.moisture(context.getLevel(), context.absolutePos(sideExposedPos), context.getBlockState(sideExposedPos))
                        < EnvironmentalExposure.moisture(context.getLevel(), context.absolutePos(sealedPos), context.getBlockState(sealedPos)),
                "side-open air exposure should dry a sheltered active surface more than a sealed surface");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void humidClimateSlowsAmbientDrying(GameTestHelper context) {
        BlockPos aridPos = TEST_POS;
        BlockPos humidPos = TEST_POS.relative(Direction.EAST, 2);
        context.setBlock(aridPos, Blocks.GRASS_BLOCK);
        context.setBlock(humidPos, Blocks.GRASS_BLOCK);
        EnvironmentalExposure.addMoisture(context.getLevel(), context.absolutePos(aridPos), context.getBlockState(aridPos), 0.5);
        EnvironmentalExposure.addMoisture(context.getLevel(), context.absolutePos(humidPos), context.getBlockState(humidPos), 0.5);

        EnvironmentalExposure.applyAmbientSurfaceExchange(
                context.getLevel(),
                context.absolutePos(aridPos),
                context.getBlockState(aridPos),
                1.2f,
                true,
                0,
                0.55);
        EnvironmentalExposure.applyAmbientSurfaceExchange(
                context.getLevel(),
                context.absolutePos(humidPos),
                context.getBlockState(humidPos),
                1.2f,
                true,
                0,
                1.35);

        context.assertTrue(
                EnvironmentalExposure.moisture(context.getLevel(), context.absolutePos(humidPos), context.getBlockState(humidPos))
                        > EnvironmentalExposure.moisture(context.getLevel(), context.absolutePos(aridPos), context.getBlockState(aridPos)),
                "humid tagged biomes should dry surfaces more slowly than arid tagged biomes");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void sunlitWarmSurfaceAddsStoredHeat(GameTestHelper context) {
        context.setBlock(TEST_POS, Blocks.STONE);

        EnvironmentalExposure.applyAmbientSurfaceExchange(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS),
                1.2f,
                true,
                0,
                1.0,
                1.0);

        context.assertTrue(EnvironmentalExposure.heat(context.getLevel(), context.absolutePos(TEST_POS), context.getBlockState(TEST_POS)) > 0.0,
                "sunlit warm surfaces should add stored heat exposure");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void solarHeatingNeedsDaylightAndSkyExposure(GameTestHelper context) {
        assertClose(EnvironmentalExposure.solarHeatExposure(1.2f, true, 0.0, 1.0), 0.0,
                "night should not add solar heat");
        assertClose(EnvironmentalExposure.solarHeatExposure(1.2f, false, 1.0, 1.0), 0.0,
                "shade should not add direct solar heat");
        context.assertTrue(EnvironmentalExposure.solarHeatExposure(1.2f, true, 1.0, 1.0) > 0.0,
                "daylit sky exposure should add solar heat in warm climates");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void humidClimateBuffersSolarHeating(GameTestHelper context) {
        double aridHeat = EnvironmentalExposure.solarHeatExposure(1.2f, true, 1.0, 0.55);
        double humidHeat = EnvironmentalExposure.solarHeatExposure(1.2f, true, 1.0, 1.35);

        context.assertTrue(aridHeat > humidHeat,
                "humid climate moisture should buffer direct solar heating compared with arid air");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void thermalConductivityProfilesOrderMaterials(GameTestHelper context) {
        double copperConductivity = MaterialPhysicsProfiles.thermalConductivity(Blocks.COPPER_BLOCK.defaultBlockState());
        double stoneConductivity = MaterialPhysicsProfiles.thermalConductivity(Blocks.STONE.defaultBlockState());
        double logConductivity = MaterialPhysicsProfiles.thermalConductivity(Blocks.OAK_LOG.defaultBlockState());

        context.assertTrue(copperConductivity > stoneConductivity,
                "metallic copper should conduct heat better than stone");
        context.assertTrue(stoneConductivity > logConductivity,
                "stone should conduct heat better than wood");
        context.assertTrue(ThermalPhysics.thermalCoupling(Blocks.COPPER_BLOCK.defaultBlockState(), Blocks.STONE.defaultBlockState())
                        > ThermalPhysics.thermalCoupling(Blocks.OAK_LOG.defaultBlockState(), Blocks.STONE.defaultBlockState()),
                "thermal coupling should reflect both materials rather than a flat neighbor rule");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void storedHeatConductsIntoAdjacentSolid(GameTestHelper context) {
        BlockPos targetPos = TEST_POS.relative(Direction.EAST);
        context.setBlock(TEST_POS, Blocks.COPPER_BLOCK);
        context.setBlock(targetPos, Blocks.STONE);
        EnvironmentalExposure.addHeat(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS),
                1.0);

        boolean conducted = ThermalPhysics.conductStoredTemperature(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS));

        context.assertTrue(conducted, "stored heat should conduct from a hot solid into adjacent solids");
        context.assertTrue(EnvironmentalExposure.heat(context.getLevel(), context.absolutePos(targetPos), context.getBlockState(targetPos)) > 0.0,
                "adjacent stone should receive stored heat through conduction");
        context.assertTrue(EnvironmentalExposure.heat(context.getLevel(), context.absolutePos(TEST_POS), context.getBlockState(TEST_POS)) < 1.0,
                "source block should lose the conducted heat");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void conductiveSourceTransfersMoreHeatThanWood(GameTestHelper context) {
        BlockPos copperSourcePos = TEST_POS;
        BlockPos copperTargetPos = copperSourcePos.relative(Direction.EAST);
        BlockPos woodSourcePos = TEST_POS.relative(Direction.SOUTH, 2);
        BlockPos woodTargetPos = woodSourcePos.relative(Direction.EAST);
        context.setBlock(copperSourcePos, Blocks.COPPER_BLOCK);
        context.setBlock(copperTargetPos, Blocks.STONE);
        context.setBlock(woodSourcePos, Blocks.OAK_LOG);
        context.setBlock(woodTargetPos, Blocks.STONE);
        EnvironmentalExposure.addHeat(context.getLevel(), context.absolutePos(copperSourcePos), context.getBlockState(copperSourcePos), 1.0);
        EnvironmentalExposure.addHeat(context.getLevel(), context.absolutePos(woodSourcePos), context.getBlockState(woodSourcePos), 1.0);

        ThermalPhysics.conductStoredTemperature(context.getLevel(), context.absolutePos(copperSourcePos), context.getBlockState(copperSourcePos));
        ThermalPhysics.conductStoredTemperature(context.getLevel(), context.absolutePos(woodSourcePos), context.getBlockState(woodSourcePos));

        context.assertTrue(
                EnvironmentalExposure.heat(context.getLevel(), context.absolutePos(copperTargetPos), context.getBlockState(copperTargetPos))
                        > EnvironmentalExposure.heat(context.getLevel(), context.absolutePos(woodTargetPos), context.getBlockState(woodTargetPos)),
                "a conductive source should move more heat into the same target material than wood");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void conductedStoredHeatCanMeltAdjacentSnowLayer(GameTestHelper context) {
        BlockPos snowPos = TEST_POS.relative(Direction.EAST);
        BlockPos supportPos = snowPos.below();
        context.setBlock(TEST_POS, Blocks.COPPER_BLOCK);
        context.setBlock(supportPos, Blocks.DIRT);
        context.setBlock(snowPos, Blocks.SNOW);
        EnvironmentalExposure.addHeat(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS),
                5.0);

        ThermalPhysics.conductStoredTemperature(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS));

        context.assertBlockPresent(Blocks.AIR, snowPos);
        context.assertTrue(EnvironmentalExposure.moisture(context.getLevel(), context.absolutePos(supportPos), context.getBlockState(supportPos)) > 0.0,
                "snow melted by conducted heat should become stored surface moisture on its support");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void storedColdConductsIntoAdjacentSolid(GameTestHelper context) {
        BlockPos targetPos = TEST_POS.relative(Direction.EAST);
        context.setBlock(TEST_POS, Blocks.COPPER_BLOCK);
        context.setBlock(targetPos, Blocks.STONE);
        EnvironmentalExposure.addCold(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS),
                1.0);

        ThermalPhysics.conductStoredTemperature(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS));

        context.assertTrue(EnvironmentalExposure.cold(context.getLevel(), context.absolutePos(targetPos), context.getBlockState(targetPos)) > 0.0,
                "stored cold should conduct into adjacent solids through the same temperature bridge");
        context.assertTrue(EnvironmentalExposure.cold(context.getLevel(), context.absolutePos(TEST_POS), context.getBlockState(TEST_POS)) < 1.0,
                "source block should lose the conducted cold");
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
    public void sensibleFireHeatScalesWithContactHeat(GameTestHelper context) {
        double lowHeat = MaterialPhysicsProfiles.sensibleFireHeat(Blocks.STONE.defaultBlockState(), 0.75);
        double highHeat = MaterialPhysicsProfiles.sensibleFireHeat(Blocks.STONE.defaultBlockState(), 1.5);

        assertClose(highHeat, lowHeat * 2.0, "sensible fire heat should scale linearly with contact heat");
        context.assertTrue(lowHeat > 0.0, "solid non-fluid blocks should be able to store sensible heat from fire");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void fireExposurePreheatsNonReactiveStoneWithoutHandlingSpread(GameTestHelper context) {
        context.setBlock(TEST_POS, Blocks.STONE);

        boolean handled = MaterialReactions.exposeToFire(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS),
                1.35f,
                RandomSource.create(8));

        context.assertFalse(handled, "non-reactive stone should not cancel vanilla fire spread as a handled fire reaction");
        context.assertTrue(EnvironmentalExposure.heat(context.getLevel(), context.absolutePos(TEST_POS), context.getBlockState(TEST_POS)) > 0.0,
                "non-reactive stone should still store sensible heat from nearby fire");
        context.assertBlockPresent(Blocks.STONE, TEST_POS);
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void fireHeatedStoneCanGainThermalStressWhenQuenched(GameTestHelper context) {
        context.setBlock(TEST_POS, Blocks.STONE);

        for (int i = 0; i < 4; i++) {
            MaterialReactions.exposeToFire(
                    context.getLevel(),
                    context.absolutePos(TEST_POS),
                    context.getBlockState(TEST_POS),
                    1.35f,
                    RandomSource.create(9 + i));
        }

        EnvironmentalExposure.addMoisture(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS),
                1.0);

        context.assertTrue(
                EnvironmentalExposure.structuralStress(context.getLevel(), context.absolutePos(TEST_POS), context.getBlockState(TEST_POS)) > 0.0,
                "water quenching fire-heated stone should feed thermal shock into structural stress memory");
        context.assertBlockPresent(Blocks.STONE, TEST_POS);
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
    public void aridClimateIncreasesVegetationHeatStress(GameTestHelper context) {
        double aridStress = MaterialPhysicsProfiles.vegetationClimateStress(
                Blocks.GRASS_BLOCK.defaultBlockState(),
                0.0,
                1.0,
                1.2f,
                true,
                0.55);
        double humidStress = MaterialPhysicsProfiles.vegetationClimateStress(
                Blocks.GRASS_BLOCK.defaultBlockState(),
                0.0,
                1.0,
                1.2f,
                true,
                1.35);

        context.assertTrue(aridStress > humidStress,
                "arid tagged climates should intensify dry heat stress compared with humid tagged climates");
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
    public void sharedStructuralStressResolverFracturesStone(GameTestHelper context) {
        context.setBlock(TEST_POS, Blocks.STONE);
        EnvironmentalExposure.addStructuralStress(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS),
                MaterialPhysicsProfiles.structuralStressThreshold(context.getBlockState(TEST_POS)) * 1.05);

        boolean resolved = StructuralStressPhysics.tryResolve(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS));

        context.assertTrue(resolved, "structural stress should resolve independently of the source that created it");
        context.assertBlockPresent(Blocks.COBBLESTONE, TEST_POS);
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void repeatedBlockImpactsCanAccumulateAndFractureStone(GameTestHelper context) {
        context.setBlock(TEST_POS, Blocks.STONE);
        double threshold = MaterialPhysicsProfiles.structuralStressThreshold(context.getBlockState(TEST_POS));

        boolean firstImpactResolved = ImpactPhysics.applyBlockImpactStress(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                threshold * 0.45);

        context.assertFalse(firstImpactResolved, "a sub-threshold impact should weaken stone without immediately fracturing it");
        context.assertBlockPresent(Blocks.STONE, TEST_POS);
        context.assertTrue(
                EnvironmentalExposure.structuralStress(context.getLevel(), context.absolutePos(TEST_POS), context.getBlockState(TEST_POS)) > 0.0,
                "impact stress should enter shared structural memory");

        boolean secondImpactResolved = ImpactPhysics.applyBlockImpactStress(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                threshold * 0.60);

        context.assertTrue(secondImpactResolved, "repeated impacts should resolve through the shared structural failure path");
        context.assertBlockPresent(Blocks.COBBLESTONE, TEST_POS);
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void explosionExposureUsesVanillaDoubleRadiusFalloff(GameTestHelper context) {
        assertClose(ExplosionEnvironmentPhysics.exposureRadius(4.0F), 8.0,
                "explosion exposure should use vanilla double-radius reach");

        double near = ExplosionEnvironmentPhysics.explosionFalloff(1.0, 4.0F);
        double middle = ExplosionEnvironmentPhysics.explosionFalloff(4.0, 4.0F);
        double edge = ExplosionEnvironmentPhysics.explosionFalloff(8.0, 4.0F);

        context.assertTrue(near > middle, "blast exposure should decay with distance");
        context.assertTrue(middle > edge, "blast exposure should reach zero at the vanilla exposure edge");
        assertClose(edge, 0.0, "blast exposure should be zero at the exposure edge");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void explosionExposureAddsSharedHeatAndStress(GameTestHelper context) {
        context.setBlock(TEST_POS, Blocks.STONE);

        ExplosionEnvironmentPhysics.applyExplosionExposure(
                context.getLevel(),
                Vec3.atCenterOf(context.absolutePos(TEST_POS)),
                2.0F);

        double heat = EnvironmentalExposure.heat(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS));
        double stress = EnvironmentalExposure.structuralStress(
                context.getLevel(),
                context.absolutePos(TEST_POS),
                context.getBlockState(TEST_POS));
        double threshold = MaterialPhysicsProfiles.structuralStressThreshold(context.getBlockState(TEST_POS));

        context.assertTrue(heat > 0.0, "blast aftermath should leave residual heat");
        context.assertTrue(stress > 0.0, "blast aftermath should leave structural stress");
        context.assertTrue(stress < threshold, "one modest blast should weaken stone without instantly fracturing it");
        context.assertBlockPresent(Blocks.STONE, TEST_POS);
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void repeatedExplosionExposureCanFractureStone(GameTestHelper context) {
        context.setBlock(TEST_POS, Blocks.STONE);
        Vec3 center = Vec3.atCenterOf(context.absolutePos(TEST_POS));

        ExplosionEnvironmentPhysics.applyExplosionExposure(context.getLevel(), center, 4.0F);
        ExplosionEnvironmentPhysics.applyExplosionExposure(context.getLevel(), center, 4.0F);

        context.assertBlockPresent(Blocks.COBBLESTONE, TEST_POS);
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

    @GameTest(maxTicks = 20)
    public void repeatedTrafficRegressesCropGrowth(GameTestHelper context) {
        BlockPos cropPos = TEST_POS.above();
        context.setBlock(TEST_POS, Blocks.FARMLAND);
        context.setBlock(cropPos, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 3));

        for (int i = 0; i < 6; i++) {
            TrafficWearPhysics.applyTraffic(context.getLevel(), context.absolutePos(TEST_POS), context.getBlockState(TEST_POS), 1.0);
        }

        context.assertTrue(context.getBlockState(cropPos).getValue(CropBlock.AGE) < 3,
                "repeated foot traffic should damage crop growth before the plant disappears");
        context.assertBlockPresent(Blocks.FARMLAND, TEST_POS);
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void wetSoilMakesCropsTrampleSooner(GameTestHelper context) {
        BlockPos wetGroundPos = TEST_POS;
        BlockPos dryGroundPos = TEST_POS.relative(Direction.EAST, 2);
        BlockPos wetCropPos = wetGroundPos.above();
        BlockPos dryCropPos = dryGroundPos.above();
        context.setBlock(wetGroundPos, Blocks.FARMLAND);
        context.setBlock(dryGroundPos, Blocks.FARMLAND);
        context.setBlock(wetCropPos, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 3));
        context.setBlock(dryCropPos, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 3));
        EnvironmentalExposure.addMoisture(
                context.getLevel(),
                context.absolutePos(wetGroundPos),
                context.getBlockState(wetGroundPos),
                1.0);

        for (int i = 0; i < 4; i++) {
            TrafficWearPhysics.applyTraffic(context.getLevel(), context.absolutePos(wetGroundPos), context.getBlockState(wetGroundPos), 1.0);
            TrafficWearPhysics.applyTraffic(context.getLevel(), context.absolutePos(dryGroundPos), context.getBlockState(dryGroundPos), 1.0);
        }

        context.assertTrue(context.getBlockState(wetCropPos).getValue(CropBlock.AGE) < 3,
                "wet soil should make crop stems more vulnerable to trampling");
        context.assertTrue(context.getBlockState(dryCropPos).getValue(CropBlock.AGE) == 3,
                "dry soil should keep the same crop standing under the same light traffic dose");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void trafficCompactsFarmlandUnderCropCanopy(GameTestHelper context) {
        BlockPos cropPos = TEST_POS.above();
        context.setBlock(TEST_POS, Blocks.FARMLAND);
        context.setBlock(cropPos, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 7));

        for (int i = 0; i < 18; i++) {
            TrafficWearPhysics.applyTraffic(context.getLevel(), context.absolutePos(TEST_POS), context.getBlockState(TEST_POS), 1.0);
        }

        context.assertBlockPresent(Blocks.DIRT, TEST_POS);
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
    public void dynamicExperienceScalesWithBodyEnergy(GameTestHelper context) {
        int cowEnergy = DynamicExperience.baseExperienceFromMeasurements(10.0, 5.0, 0.0, 0.0);
        int zombieEnergy = DynamicExperience.baseExperienceFromMeasurements(20.0, 4.0, 2.0, 0.0);
        int ravagerEnergy = DynamicExperience.baseExperienceFromMeasurements(100.0, 40.0, 0.0, 0.0);

        context.assertTrue(cowEnergy > 0, "living body energy should produce experience");
        context.assertTrue(zombieEnergy > cowEnergy,
                "a tougher living body should release more experience than a small passive body");
        context.assertTrue(ravagerEnergy > zombieEnergy,
                "a high-health massive entity should release substantially more experience");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void experienceEnergyUsesVanillaLevelCurve(GameTestHelper context) {
        context.assertTrue(ExperienceEnergy.pointsNeededForNextLevel(0) == 7,
                "vanilla level 0 should need seven raw XP points");
        context.assertTrue(ExperienceEnergy.pointsNeededForNextLevel(15) == 37,
                "vanilla level 15 should start the middle XP curve");
        context.assertTrue(ExperienceEnergy.pointsNeededForNextLevel(30) == 112,
                "vanilla level 30 should start the high XP curve");
        context.assertTrue(ExperienceEnergy.pointsForLevel(16) == ExperienceEnergy.pointsForLevel(15) + 37,
                "raw XP storage should advance by the exact vanilla next-level cost");
        context.assertTrue(ExperienceEnergy.pointsForLevel(32) == ExperienceEnergy.pointsForLevel(31)
                + ExperienceEnergy.pointsNeededForNextLevel(31),
                "raw XP storage should follow the high-level vanilla curve exactly");
        context.assertTrue(ExperienceEnergy.levelForPoints(ExperienceEnergy.pointsForLevel(30)) == 30,
                "raw XP points should map back to the same vanilla level threshold");
        context.assertTrue(ExperienceEnergy.levelForPoints(ExperienceEnergy.pointsForLevel(30) + 111) == 30,
                "points just below the next threshold should stay in the same vanilla level");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void experienceEnergyConvertsLevelCostsToRawPoints(GameTestHelper context) {
        int lowCost = ExperienceEnergy.rawPointsForWholeLevelCost(10, 3);
        int highCost = ExperienceEnergy.rawPointsForWholeLevelCost(30, 3);
        int exactHighCost = ExperienceEnergy.pointsForLevel(30) - ExperienceEnergy.pointsForLevel(27);

        context.assertTrue(lowCost > 0,
                "a visible level cost should map to raw XP points");
        context.assertTrue(highCost > lowCost,
                "the same visible level cost should represent more raw XP at higher levels");
        context.assertTrue(highCost == exactHighCost,
                "whole-level cost conversion should use the vanilla nonlinear level curve");
        context.assertTrue(ExperienceEnergy.wholeLevelsAffordableFromRawPoints(30, highCost - 1) == 2,
                "raw XP just below a three-level high-level cost should only afford two whole levels");
        context.assertTrue(ExperienceEnergy.wholeLevelsAffordableFromRawPoints(30, highCost) == 3,
                "raw XP equal to a three-level high-level cost should afford exactly three whole levels");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void experienceEnergyPreservesProgressWhenSpendingRawCosts(GameTestHelper context) {
        int rawBefore = ExperienceEnergy.rawPointsAtLevelProgress(30, 0.5F);
        int rawCost = ExperienceEnergy.rawPointsForWholeLevelCost(30, 3);
        ExperienceEnergy.LevelProgress after = ExperienceEnergy.progressAfterWholeLevelCost(30, 0.5F, 3);
        int rawAfter = ExperienceEnergy.rawPointsAtLevelProgress(after.level(), after.progress());

        context.assertTrue(Math.abs(rawAfter - (rawBefore - rawCost)) <= 1,
                "spending a whole-level cost as raw XP should preserve fractional progress energy within Minecraft's progress-bar precision");
        context.assertTrue(after.level() == 27 && after.progress() > 0.0F,
                "half a high-level bar should remain as progress after a three-level raw XP spend");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void enchantmentEnergyBudgetUsesVanillaAnvilCosts(GameTestHelper context) {
        Holder<Enchantment> sharpness = context.getLevel().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SHARPNESS);
        Holder<Enchantment> mending = context.getLevel().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.MENDING);
        ItemEnchantments.Mutable mutable = new ItemEnchantments.Mutable(ItemEnchantments.EMPTY);
        mutable.set(sharpness, 3);
        mutable.set(mending, 1);
        ItemEnchantments enchantments = mutable.toImmutable();

        int expectedLevelBudget = sharpness.value().getAnvilCost() * 3 + mending.value().getAnvilCost();
        int levelBudget = ExperienceEnergy.enchantmentLevelBudget(enchantments);
        int rawBudget = ExperienceEnergy.enchantmentEnergyBudgetPoints(enchantments, 30);

        context.assertTrue(levelBudget == expectedLevelBudget,
                "enchantment energy budget should use vanilla anvil costs as the rarity/work measure");
        context.assertTrue(rawBudget == ExperienceEnergy.rawPointsForWholeLevelCost(30, expectedLevelBudget),
                "enchantment budget should convert visible work levels through the shared raw-XP curve");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void enchantedBooksUseVanillaHalfCostForApplicationEnergy(GameTestHelper context) {
        Holder<Enchantment> sharpness = context.getLevel().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SHARPNESS);
        ItemStack sword = new ItemStack(Items.DIAMOND_SWORD);
        ItemStack book = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantmentHelper.updateEnchantments(sword, enchantments -> enchantments.set(sharpness, 4));
        EnchantmentHelper.updateEnchantments(book, enchantments -> enchantments.set(sharpness, 4));

        int itemCost = ExperienceEnergy.enchantmentApplicationLevelCost(sword);
        int bookCost = ExperienceEnergy.enchantmentApplicationLevelCost(book);

        context.assertTrue(itemCost == sharpness.value().getAnvilCost() * 4,
                "applied item enchantment work should use the full vanilla anvil fee");
        context.assertTrue(bookCost == Math.max(1, sharpness.value().getAnvilCost() / 2) * 4,
                "stored book application work should use vanilla's half-cost enchanted-book rule");
        context.assertTrue(ExperienceEnergy.enchantmentApplicationEnergyCostPoints(book, 30)
                == ExperienceEnergy.rawPointsForWholeLevelCost(30, bookCost),
                "book application work should convert through the same raw-XP energy curve");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void mergedEnchantmentLevelsConserveStoredEnergyBudget(GameTestHelper context) {
        Holder<Enchantment> sharpness = context.getLevel().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SHARPNESS);
        int anvilCost = sharpness.value().getAnvilCost();

        int equalLevelMerge = ExperienceEnergy.mergedEnchantmentLevelFromEnergy(5, 5, anvilCost);
        int unevenLevelMerge = ExperienceEnergy.mergedEnchantmentLevelFromEnergy(4, 5, anvilCost);
        int cappedMerge = ExperienceEnergy.mergedEnchantmentLevelFromEnergy(
                ExperienceEnergy.MAX_ENCHANTMENT_LEVEL,
                1,
                anvilCost);

        context.assertTrue(equalLevelMerge == 10,
                "two equal enchantment levels should combine their stored work budget rather than only adding one level");
        context.assertTrue(unevenLevelMerge == 9,
                "uneven enchantment levels should preserve both stored budgets instead of only keeping the larger level");
        context.assertTrue(cappedMerge == ExperienceEnergy.MAX_ENCHANTMENT_LEVEL,
                "energy-conserving enchantment merges should still respect Minecraft's component level range");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void boundlessMendingRepairOutputScalesWithStoredEnergy(GameTestHelper context) {
        Holder<Enchantment> mending = context.getLevel().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.MENDING);
        ItemStack ordinaryPick = new ItemStack(Items.DIAMOND_PICKAXE);
        ItemStack energeticPick = new ItemStack(Items.DIAMOND_PICKAXE);
        EnchantmentHelper.updateEnchantments(ordinaryPick, enchantments -> enchantments.set(mending, 1));
        EnchantmentHelper.updateEnchantments(energeticPick, enchantments -> enchantments.set(mending, 4));

        int ordinaryRepair = EnchantmentHelper.modifyDurabilityToRepairFromXp(context.getLevel(), ordinaryPick, 3);
        int energeticRepair = EnchantmentHelper.modifyDurabilityToRepairFromXp(context.getLevel(), energeticPick, 3);
        double outputRatio = ExperienceEnergy.enchantmentOutputEnergyRatio(
                4,
                mending.value().getMaxLevel(),
                mending.value().getAnvilCost());

        context.assertTrue(ordinaryRepair == 6,
                "vanilla-level Mending should still repair two durability per raw XP point");
        context.assertTrue(outputRatio == 4.0,
                "Mending IV stores four times the vanilla repair enchantment work budget");
        context.assertTrue(energeticRepair == ordinaryRepair * 4,
                "boundless repair output should spend raw XP with power proportional to stored repair energy");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void disabledBoundlessEnchantingKeepsVanillaMendingRate(GameTestHelper context) {
        Holder<Enchantment> mending = context.getLevel().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.MENDING);
        ItemStack energeticPick = new ItemStack(Items.DIAMOND_PICKAXE);
        EnchantmentHelper.updateEnchantments(energeticPick, enchantments -> enchantments.set(mending, 4));

        boolean previous = EmergentConfig.get().boundlessEnchanting;
        try {
            EmergentConfig.get().boundlessEnchanting = false;
            int repair = EnchantmentHelper.modifyDurabilityToRepairFromXp(context.getLevel(), energeticPick, 3);
            context.assertTrue(repair == 6,
                    "when boundless enchanting is disabled, high stored Mending work should not alter vanilla repair rate");
        } finally {
            EmergentConfig.get().boundlessEnchanting = previous;
        }

        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void boundlessFlameIgniteDurationScalesWithStoredEnergy(GameTestHelper context) {
        Holder<Enchantment> flame = context.getLevel().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.FLAME);
        ItemStack ordinaryBow = new ItemStack(Items.BOW);
        ItemStack energeticBow = new ItemStack(Items.BOW);
        EnchantmentHelper.updateEnchantments(ordinaryBow, enchantments -> enchantments.set(flame, 1));
        EnchantmentHelper.updateEnchantments(energeticBow, enchantments -> enchantments.set(flame, 4));

        float vanillaSeconds = 100.0F;
        float ordinarySeconds = ExperienceEnergy.igniteDurationFromStoredEnergy(ordinaryBow, 1, vanillaSeconds);
        float energeticSeconds = ExperienceEnergy.igniteDurationFromStoredEnergy(energeticBow, 4, vanillaSeconds);

        context.assertTrue(ordinarySeconds == vanillaSeconds,
                "vanilla-level Flame should keep vanilla's projectile burn duration");
        context.assertTrue(energeticSeconds == vanillaSeconds * 4.0F,
                "boundless Flame should turn stored enchantment work into longer projectile ignition");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void igniteEffectObeysBoundlessEnchantingConfigGate(GameTestHelper context) {
        Holder<Enchantment> flame = context.getLevel().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.FLAME);
        ItemStack energeticBow = new ItemStack(Items.BOW);
        EnchantmentHelper.updateEnchantments(energeticBow, enchantments -> enchantments.set(flame, 4));
        LivingEntity target = context.spawn(EntityType.COW, Vec3.atBottomCenterOf(TEST_POS.above()));
        Ignite ignite = new Ignite(LevelBasedValue.constant(100.0F));
        EnchantedItemInUse item = new EnchantedItemInUse(energeticBow, null, null, ignored -> {
        });

        boolean previous = EmergentConfig.get().boundlessEnchanting;
        try {
            EmergentConfig.get().boundlessEnchanting = false;
            ignite.apply(context.getLevel(), 4, item, target, target.position());
            context.assertTrue(target.getRemainingFireTicks() == 2000,
                    "when boundless enchanting is disabled, constant ignite effects should keep vanilla duration");

            target.setRemainingFireTicks(0);
            EmergentConfig.get().boundlessEnchanting = true;
            ignite.apply(context.getLevel(), 4, item, target, target.position());
            context.assertTrue(target.getRemainingFireTicks() == 8000,
                    "when boundless enchanting is enabled, constant ignite effects should scale through the real vanilla effect path");
        } finally {
            EmergentConfig.get().boundlessEnchanting = previous;
        }

        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void anvilMergesEnchantmentEnergyBudgets(GameTestHelper context) {
        Holder<Enchantment> sharpness = context.getLevel().registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SHARPNESS);
        Player player = context.makeMockPlayer(GameType.CREATIVE);
        AnvilMenu menu = new AnvilMenu(0, player.getInventory());
        ItemStack baseSword = new ItemStack(Items.DIAMOND_SWORD);
        ItemStack additionSword = new ItemStack(Items.DIAMOND_SWORD);
        EnchantmentHelper.updateEnchantments(baseSword, enchantments -> enchantments.set(sharpness, 5));
        EnchantmentHelper.updateEnchantments(additionSword, enchantments -> enchantments.set(sharpness, 5));

        menu.getSlot(AnvilMenu.INPUT_SLOT).set(baseSword);
        menu.getSlot(AnvilMenu.ADDITIONAL_SLOT).set(additionSword);
        ItemStack result = menu.getSlot(AnvilMenu.RESULT_SLOT).getItem();
        int resultLevel = EnchantmentHelper.getEnchantmentsForCrafting(result).getLevel(sharpness);

        context.assertTrue(resultLevel == 10,
                "anvil output should merge both inputs' stored enchantment work budget through the real menu path");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void dynamicExperienceDelegatesToExperienceEnergy(GameTestHelper context) {
        int dynamicReward = DynamicExperience.baseExperienceFromMeasurements(20.0, 4.0, 2.0, 0.0);
        int sharedEnergy = ExperienceEnergy.livingDeathEnergyPoints(20.0, 4.0, 2.0, 0.0);

        context.assertTrue(dynamicReward == sharedEnergy,
                "dynamic entity XP should use the shared raw experience-energy model");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void livingEntityExperienceUsesDynamicFormula(GameTestHelper context) {
        LivingEntity zombie = context.spawn(EntityType.ZOMBIE, Vec3.atBottomCenterOf(TEST_POS.above()));
        LivingEntity ravager = context.spawn(EntityType.RAVAGER, Vec3.atBottomCenterOf(TEST_POS.relative(Direction.EAST, 2).above()));

        int zombieReward = zombie.getExperienceReward(context.getLevel(), null);
        int ravagerReward = ravager.getExperienceReward(context.getLevel(), null);

        context.assertTrue(zombieReward == DynamicExperience.rewardAfterVanillaProcessing(zombie, 5),
                "the central vanilla experience query should use the dynamic physical formula");
        context.assertTrue(ravagerReward > zombieReward,
                "sculk catalysts and XP orbs should see more charge from a larger, tougher entity");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void sculkCatalystChargeUsesDynamicExperienceEnergy(GameTestHelper context) {
        BlockPos catalystPos = TEST_POS;
        context.setBlock(catalystPos, Blocks.SCULK_CATALYST);

        LivingEntity zombie = context.spawn(EntityType.ZOMBIE, Vec3.atBottomCenterOf(TEST_POS.above()));
        LivingEntity ravager = context.spawn(EntityType.RAVAGER, Vec3.atBottomCenterOf(TEST_POS.relative(Direction.EAST, 2).above()));
        int zombieReward = zombie.getExperienceReward(context.getLevel(), null);
        int ravagerReward = ravager.getExperienceReward(context.getLevel(), null);

        SculkCatalystBlockEntity.CatalystListener zombieListener = new SculkCatalystBlockEntity.CatalystListener(
                Blocks.SCULK_CATALYST.defaultBlockState(),
                new BlockPositionSource(context.absolutePos(catalystPos)));
        SculkCatalystBlockEntity.CatalystListener ravagerListener = new SculkCatalystBlockEntity.CatalystListener(
                Blocks.SCULK_CATALYST.defaultBlockState(),
                new BlockPositionSource(context.absolutePos(catalystPos)));

        boolean zombieHandled = zombieListener.handleGameEvent(
                context.getLevel(),
                GameEvent.ENTITY_DIE,
                GameEvent.Context.of(zombie),
                zombie.position());
        boolean ravagerHandled = ravagerListener.handleGameEvent(
                context.getLevel(),
                GameEvent.ENTITY_DIE,
                GameEvent.Context.of(ravager),
                ravager.position());

        context.assertTrue(zombieHandled && ravagerHandled,
                "sculk catalyst listeners should consume living death events");
        context.assertTrue(totalSculkCharge(zombieListener) == zombieReward,
                "sculk catalyst charge should use the same dynamic XP-energy reward as dropped orbs");
        context.assertTrue(totalSculkCharge(ravagerListener) == ravagerReward,
                "larger dynamic XP rewards should become matching sculk spread charge");
        context.assertTrue(ravagerReward > zombieReward,
                "a larger, tougher entity should feed more sculk charge than a zombie");
        context.assertTrue(zombie.wasExperienceConsumed() && ravager.wasExperienceConsumed(),
                "sculk catalysts should mark the living death energy as consumed to prevent duplicate XP drops");
        context.succeed();
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
    public void storedSoilMoistureIncreasesRainGrowthChance(GameTestHelper context) {
        BlockPos dryCropPos = TEST_POS;
        BlockPos wetCropPos = TEST_POS.relative(Direction.EAST, 2);
        context.setBlock(dryCropPos.below(), Blocks.FARMLAND);
        context.setBlock(wetCropPos.below(), Blocks.FARMLAND);
        context.setBlock(dryCropPos, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 0));
        context.setBlock(wetCropPos, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 0));
        EnvironmentalExposure.addMoisture(
                context.getLevel(),
                context.absolutePos(wetCropPos.below()),
                context.getBlockState(wetCropPos.below()),
                1.0);

        float dryChance = MaterialReactions.rainGrowthChance(
                context.getLevel(),
                context.absolutePos(dryCropPos),
                context.getBlockState(dryCropPos));
        float wetChance = MaterialReactions.rainGrowthChance(
                context.getLevel(),
                context.absolutePos(wetCropPos),
                context.getBlockState(wetCropPos));

        context.assertTrue(wetChance > dryChance,
                "stored soil moisture should make rain-assisted crop growth more likely");
        context.succeed();
    }

    @GameTest(maxTicks = 20)
    public void successfulRainGrowthConsumesStoredSoilMoisture(GameTestHelper context) {
        BlockPos soilPos = TEST_POS.below();
        context.setBlock(soilPos, Blocks.FARMLAND);
        context.setBlock(TEST_POS, Blocks.WHEAT.defaultBlockState().setValue(CropBlock.AGE, 0));
        EnvironmentalExposure.addMoisture(
                context.getLevel(),
                context.absolutePos(soilPos),
                context.getBlockState(soilPos),
                1.0);

        RandomSource random = RandomSource.create(17);
        for (int attempt = 0; attempt < 512 && context.getBlockState(TEST_POS).getValue(CropBlock.AGE) == 0; attempt++) {
            MaterialReactions.tryRainGrow(
                    context.getLevel(),
                    context.absolutePos(TEST_POS),
                    context.getBlockState(TEST_POS),
                    random);
        }

        context.assertTrue(context.getBlockState(TEST_POS).getValue(CropBlock.AGE) > 0,
                "test setup should force at least one rain-growth event");
        context.assertTrue(
                EnvironmentalExposure.moisture(context.getLevel(), context.absolutePos(soilPos), context.getBlockState(soilPos)) < 1.0,
                "successful growth should draw from stored soil moisture instead of treating it as a free permanent bonus");
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

    private static int totalSculkCharge(SculkCatalystBlockEntity.CatalystListener listener) {
        return listener.getSculkSpreader().getCursors().stream()
                .mapToInt(cursor -> cursor.getCharge())
                .sum();
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
