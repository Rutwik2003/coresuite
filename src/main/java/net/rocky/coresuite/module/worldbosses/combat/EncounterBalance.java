package net.rocky.coresuite.module.worldbosses.combat;

/** Shared finite bounds for adaptive encounter health and healing. */
public final class EncounterBalance {
    private EncounterBalance() {}
    public static double finite(double value, double fallback, double min, double max) {
        return Double.isFinite(value) ? Math.max(min, Math.min(max, value)) : fallback;
    }
    public static double health(double base, int players, double soloFactor, double extraFactor) {
        return finite(base, 1500, 1, 10000000) * finite(soloFactor, .6, .1, 5)
                * (1 + (Math.max(1, Math.min(20, players)) - 1) * finite(extraFactor, .5, 0, 2));
    }
    public static double rescale(double current, double previousMaximum, double nextMaximum) {
        // Joining players increase capacity, never restore HP already removed by the raid.
        return finite(current, 0, 0, finite(nextMaximum, previousMaximum, 1, 1000000000));
    }
    public static double healing(double current, double maximum, double requested, double spentFraction, double budgetFraction) {
        if (!Double.isFinite(requested) || requested <= 0 || current <= 0) return 0;
        double budget = Math.max(0, finite(budgetFraction, 1.0, 0, 10.0) - Math.max(0, spentFraction)) * maximum;
        return Math.max(0, Math.min(requested, Math.min(maximum - current, budget)));
    }
}
