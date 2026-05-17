package com.teddante.emergent;

public final class FiniteFluidBudgetSettings {
    public static final String ACTIVE_TICK_BUDGET_PROPERTY = "emergent.finiteFluid.activeTickBudget";
    private static final int DEFAULT_ACTIVE_TICK_BUDGET = 4096;
    private static final int DEFAULT_BUDGET_DEFER_SPREAD_TICKS = 4;

    private FiniteFluidBudgetSettings() {
    }

    public static int activeTickBudget() {
        return Integer.getInteger(ACTIVE_TICK_BUDGET_PROPERTY, DEFAULT_ACTIVE_TICK_BUDGET);
    }

    public static int budgetDeferSpreadTicks() {
        return DEFAULT_BUDGET_DEFER_SPREAD_TICKS;
    }
}
