package com.teddante.emergent;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Emergent implements ModInitializer {
	public static final String MOD_ID = "emergent";

	// This logger is used to write text to the console and the log file.
	// It is considered best practice to use your mod id as the logger's name.
	// That way, it's clear which mod wrote info, warnings, and errors.
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
		EmergentConfig.load();
		ServerTickEvents.START_LEVEL_TICK.register(EmergentProfiler::startLevelTick);
		ServerTickEvents.END_LEVEL_TICK.register(EnvironmentalScheduler::tickWorld);
		ServerTickEvents.END_LEVEL_TICK.register(EmergentProfiler::endLevelTick);
		LOGGER.info("Emergent mod initialized.");
		if (EmergentProfiler.enabled()) {
			LOGGER.info("Emergent profiler enabled. Slow tick threshold: {} ms", EmergentProfiler.slowTickMillis());
			LOGGER.info(
					"Emergent finite fluid work budget: {} cells/tick (override with -D{}=<cells>)",
					FiniteFluidBudgetSettings.activeTickBudget(),
					FiniteFluidBudgetSettings.ACTIVE_TICK_BUDGET_PROPERTY);
			LOGGER.info(
					"Emergent finite fluid chunk work budget: {} cells/chunk/tick (override with -D{}=<cells>)",
					FiniteFluidBudgetSettings.activeChunkTickBudget(),
					FiniteFluidBudgetSettings.ACTIVE_CHUNK_TICK_BUDGET_PROPERTY);
		}
	}
}
