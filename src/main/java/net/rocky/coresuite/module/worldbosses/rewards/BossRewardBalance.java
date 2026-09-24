package net.rocky.coresuite.module.worldbosses.rewards;

import org.bukkit.configuration.file.FileConfiguration;

public final class BossRewardBalance {
    private BossRewardBalance() {}
    /** Rank is zero-based; cap the combined participation/rank/dynamic payout. */
    public static int cappedShards(int total, int rank) {
        return Math.max(0, Math.min(total, rank == 0 ? 6 : rank < 3 ? 4 : 2));
    }
    public static boolean upgradeKeyBalance(FileConfiguration config) {
        if (config.getBoolean("rewards.key-scarcity-upgraded", false)) return false;
        String prefix = "crates key give %player% ";
        String[] bosses = {"valerius", "aethelgard", "xolthos"};
        String[] oldMvp = {"Uncommon", "Rare", "Rare"};
        String[] newMvp = {"enchanter", "potions", "potions"};
        for (int i = 0; i < bosses.length; i++) {
            String path = bosses[i] + ".loot.";
            var previous = i == 2 ? java.util.List.of(prefix + "Rare 1", prefix + "potions 1", prefix + "enchanter 1")
                    : java.util.List.of(prefix + oldMvp[i] + " 1");
            if (config.getStringList(path + "rank-1.commands").equals(previous))
                config.set(path + "rank-1.commands", java.util.List.of(prefix + newMvp[i] + " 1"));
            String rare = path + "rare-drops.";
            if (config.getDouble(rare + "crate-key-chance") == (i == 2 ? 6 : 8))
                config.set(rare + "crate-key-chance", 2.0);
        }
        config.set("rewards.key-scarcity-upgraded", true);
        return true;
    }
    public static double dynamicMultiplier(FileConfiguration config, int peakPlayers, int qualifiedPlayers, double damagePercent, int contribution) {
        if (!config.getBoolean("rewards.dynamic.enabled", true)) return 1;
        double group = Math.min(.25, Math.max(0, Math.min(20, peakPlayers) - 1) * .05);
        double damageEffort = Double.isFinite(damagePercent) ? Math.max(0, damagePercent) * Math.max(1, qualifiedPlayers) / 100 : 0;
        double supportEffort = Math.max(0, contribution) / 12.0;
        double effort = Math.min(1, Math.max(damageEffort, supportEffort));
        return 1 + group + .25 * effort;
    }
    public static double dynamicCurrency(double base, double fee, double multiplier) {
        if (!Double.isFinite(base) || base <= 0) return 0;
        double protectedAmount = Double.isFinite(fee) ? Math.max(0, Math.min(base, fee)) : 0;
        return Math.floor(protectedAmount + (base - protectedAmount) * Math.max(1, Math.min(1.5, multiplier)));
    }
    public static boolean upgrade(FileConfiguration config) {
        if (config.getBoolean("rewards.participation-balance-upgraded", false)) return false;
        String[] bosses = {"valerius", "aethelgard", "xolthos"};
        double[] previous = {5000, 7500, 10000};
        for (int i = 0; i < bosses.length; i++) {
            String path = bosses[i] + ".loot.participation.";
            if (config.getDouble(path + "currency") == previous[i]) config.set(path + "currency", 55000 + i * 5000);
            if (config.getInt(path + "shards") == 1) config.set(path + "shards", 2 + i);
        }
        config.set("rewards.participation-balance-upgraded", true);
        return true;
    }

    public static boolean upgradeMythicPrices(FileConfiguration config) {
        if (config.getBoolean("exchange.mythic-prices-upgraded", false)) return false;
        String[] trades = {"vanguard-cleaver", "crown-of-forest", "widows-fang"};
        int[] oldShards = {90, 100, 110}, newShards = {120, 140, 160};
        double[] oldCash = {2500000, 3500000, 5000000}, newCash = {4000000, 5500000, 7500000};
        for (int i = 0; i < trades.length; i++) {
            String path = "exchange.trades." + trades[i] + ".";
            if (config.getInt(path + "required-shards") == oldShards[i])
                config.set(path + "required-shards", newShards[i]);
            if (config.getDouble(path + "required-currency") == oldCash[i])
                config.set(path + "required-currency", newCash[i]);
        }
        config.set("exchange.mythic-prices-upgraded", true);
        return true;
    }

    /** Everyone gets the same chance; large groups don't multiply the rare-loot supply without bound. */
    public static double rareChance(double configuredPercent, int participants) {
        if (!Double.isFinite(configuredPercent)) return 0;
        return Math.max(0, Math.min(100, configuredPercent)) * Math.min(1, 3.0 / Math.max(1, participants));
    }
}
