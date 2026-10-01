package net.rocky.coresuite.module.worldbosses.rewards;

import org.bukkit.configuration.ConfigurationSection;
import java.util.List;

/** Runs before additive merging so legacy custom rank rewards are not shadowed. */
public final class BossLootMigration {
    private BossLootMigration() {}
    public static boolean upgrade(ConfigurationSection target, ConfigurationSection defaults) {
        if (target.getInt("rewards.loot-balance-version", 0) >= 1) return false;
        String[] bosses = {"valerius", "aethelgard", "xolthos", "borealis"};
        int[][] oldCash = {{25000,15000,15000,55000},{35000,20000,20000,60000},
                {50000,30000,30000,65000},{40000,22000,22000,60000}};
        int[][] recentCash = {{75000,50000,40000,20000},{80000,50000,40000,20000},
                {90000,55000,45000,20000},{85000,50000,40000,20000}};
        int[][] oldShards = {{4,2,2,2},{4,2,2,3},{5,3,3,4},{4,2,2,2}};
        String[] tiers = {"rank-1", "rank-2", "rank-3", "participation"};
        List<List<String>> recentItems = List.of(
            List.of("DIAMOND:16-32","GOLD_INGOT:16-32","EMERALD:16-32","NETHERITE_INGOT:1-2","TOTEM_OF_UNDYING:1-2","EXPERIENCE_BOTTLE:32-64"),
            List.of("DIAMOND:8-16","GOLD_INGOT:8-16","EMERALD:8-16","NETHERITE_SCRAP:2-4","TOTEM_OF_UNDYING:1:50%","EXPERIENCE_BOTTLE:16-32"),
            List.of("DIAMOND:6-12","GOLD_INGOT:6-12","EMERALD:6-12","NETHERITE_SCRAP:1-2","TOTEM_OF_UNDYING:1:25%","EXPERIENCE_BOTTLE:12-24"),
            List.of("DIAMOND:2-4","GOLD_INGOT:4-8","EMERALD:4-8","EXPERIENCE_BOTTLE:8-16"));
        for (int b = 0; b < bosses.length; b++) {
            String root = bosses[b] + ".loot.";
            var legacy = target.getConfigurationSection(root + "rank-2-3");
            for (int t = 0; t < tiers.length; t++) {
                String path = root + tiers[t] + ".";
                if ((t == 1 || t == 2) && legacy != null && !target.contains(root + tiers[t])) {
                    for (String key : legacy.getKeys(true)) if (!legacy.isConfigurationSection(key))
                        target.set(path + key, legacy.get(key));
                }
                replaceNumber(target, defaults, path + "currency", oldCash[b][t], recentCash[b][t]);
                replaceNumber(target, defaults, path + "shards", oldShards[b][t], t == 0 ? 5 : t == 3 ? 1 : 2);
                if ((t == 1 || t == 2) && target.getStringList(path + "commands").equals(
                        List.of("crates key give %player% " + (b == 0 ? "Common" : b == 1 ? "Uncommon" : "Rare") + " 1")))
                    target.set(path + "commands", defaults.get(path + "commands"));
                if (target.getStringList(path + "items").equals(recentItems.get(t)))
                    target.set(path + "items", defaults.get(path + "items"));
            }
            replaceNumber(target, defaults, root + "rare-drops.crate-key-chance", 2, .5);
            String command = root + "rare-drops.crate-key-command";
            if ("crates key give %player% enchanter 1".equals(target.getString(command)))
                target.set(command, defaults.get(command));
            var oldBonus = List.of(java.util.Map.of("chance", 2.0, "command", "crates key give %player% enchanter 1"));
            if (target.getMapList(root + "rare-drops.bonus-keys").equals(oldBonus))
                target.set(root + "rare-drops.bonus-keys", null);
        }
        replaceNumber(target, defaults, "rewards.participation.minimum-damage", 20, 150);
        // Supersedes the old migration which would otherwise raise fresh participation shards.
        target.set("rewards.participation-balance-upgraded", true);
        target.set("rewards.loot-balance-version", 1);
        return true;
    }
    private static void replaceNumber(ConfigurationSection target, ConfigurationSection defaults,
                                      String path, double oldValue, double recentValue) {
        if (target.contains(path) && defaults.contains(path)
                && (target.getDouble(path) == oldValue || target.getDouble(path) == recentValue))
            target.set(path, defaults.get(path));
    }
}
