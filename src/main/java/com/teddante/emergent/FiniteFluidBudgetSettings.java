package com.teddante.emergent;

public final class FiniteFluidBudgetSettings {
    public static final String ACTIVE_TICK_BUDGET_PROPERTY = "emergent.finiteFluid.activeTickBudget";
    public static final String ACTIVE_CHUNK_TICK_BUDGET_PROPERTY = "emergent.finiteFluid.activeChunkTickBudget";
    private static final int DEFAULT_ACTIVE_TICK_BUDGET = 256;
    private static final int DEFAULT_ACTIVE_CHUNK_TICK_BUDGET = 64;
    private static final int DEFAULT_BUDGET_DEFER_SPREAD_TICKS = 4;
    private static final int INSPECTION_BUDGET_MULTIPLIER = 8;

    private FiniteFluidBudgetSettings() {
    }

    public static int activeTickBudget() {
        return Integer.getInteger(ACTIVE_TICK_BUDGET_PROPERTY, DEFAULT_ACTIVE_TICK_BUDGET);
    }

    public static int activeChunkTickBudget() {
        return Integer.getInteger(ACTIVE_CHUNK_TICK_BUDGET_PROPERTY, DEFAULT_ACTIVE_CHUNK_TICK_BUDGET);
    }

    public static int inspectionTickBudget() {
        return scaledInspectionBudget(activeTickBudget());
    }

    public static int inspectionChunkTickBudget() {
        return scaledInspectionBudget(activeChunkTickBudget());
    }

    public static int budgetDeferSpreadTicks() {
        return DEFAULT_BUDGET_DEFER_SPREAD_TICKS;
    }

    private static int scaledInspectionBudget(int activeBudget) {
        if (activeBudget <= 0) {
            return 0;
        }
        if (activeBudget > Integer.MAX_VALUE / INSPECTION_BUDGET_MULTIPLIER) {
            return Integer.MAX_VALUE;
        }
        return activeBudget * INSPECTION_BUDGET_MULTIPLIER;
    }
}
