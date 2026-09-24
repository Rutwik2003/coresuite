package net.rocky.coresuite.module.worldbosses.rewards;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BossRewardBalanceTest {
    @Test void shardCapsApplyToTheCombinedPayout() {
        assertEquals(6, BossRewardBalance.cappedShards(15, 0));
        assertEquals(4, BossRewardBalance.cappedShards(12, 1));
        assertEquals(4, BossRewardBalance.cappedShards(8, 2));
        assertEquals(2, BossRewardBalance.cappedShards(6, 3));
        assertEquals(1, BossRewardBalance.cappedShards(1, 0));
    }
    @Test void mvpKeysUpgradeOnceWithoutChangingCustomRewards() {
        var config = new YamlConfiguration();
        config.set("valerius.loot.rank-1.commands", java.util.List.of("crates key give %player% Uncommon 1"));
        config.set("aethelgard.loot.rank-1.commands", java.util.List.of("custom reward %player%"));
        config.set("xolthos.loot.rank-1.commands", java.util.List.of("crates key give %player% Rare 1", "crates key give %player% potions 1", "crates key give %player% enchanter 1"));
        config.set("xolthos.loot.rare-drops.crate-key-chance", 6);
        assertTrue(BossRewardBalance.upgradeKeyBalance(config));
        assertEquals(java.util.List.of("crates key give %player% enchanter 1"), config.getStringList("valerius.loot.rank-1.commands"));
        assertEquals(java.util.List.of("custom reward %player%"), config.getStringList("aethelgard.loot.rank-1.commands"));
        assertEquals(java.util.List.of("crates key give %player% potions 1"), config.getStringList("xolthos.loot.rank-1.commands"));
        assertEquals(2, config.getDouble("xolthos.loot.rare-drops.crate-key-chance"));
        assertFalse(BossRewardBalance.upgradeKeyBalance(config));
    }
    @Test void mythicPricesUpgradeExistingConfigsOnceAndPreserveCustomValues() {
        var config = new YamlConfiguration();
        config.set("rewards.participation-balance-upgraded", true);
        String path = "exchange.trades.";
        config.set(path + "vanguard-cleaver.required-shards", 90);
        config.set(path + "vanguard-cleaver.required-currency", 2500000);
        config.set(path + "crown-of-forest.required-shards", 100);
        config.set(path + "crown-of-forest.required-currency", 3500000);
        config.set(path + "widows-fang.required-shards", 110);
        config.set(path + "widows-fang.required-currency", 5000000);
        assertTrue(BossRewardBalance.upgradeMythicPrices(config));
        assertEquals(120, config.getInt(path + "vanguard-cleaver.required-shards"));
        assertEquals(4000000, config.getInt(path + "vanguard-cleaver.required-currency"));
        assertEquals(140, config.getInt(path + "crown-of-forest.required-shards"));
        assertEquals(5500000, config.getInt(path + "crown-of-forest.required-currency"));
        assertEquals(160, config.getInt(path + "widows-fang.required-shards"));
        assertEquals(7500000, config.getInt(path + "widows-fang.required-currency"));
        config.set(path + "vanguard-cleaver.required-currency", 2500000);
        assertFalse(BossRewardBalance.upgradeMythicPrices(config));
        assertEquals(2500000, config.getInt(path + "vanguard-cleaver.required-currency"));
        var custom = new YamlConfiguration();
        custom.set(path + "widows-fang.required-currency", 9000000);
        custom.set(path + "widows-fang.required-shards", 200);
        BossRewardBalance.upgradeMythicPrices(custom);
        assertEquals(9000000, custom.getInt(path + "widows-fang.required-currency"));
        assertEquals(200, custom.getInt(path + "widows-fang.required-shards"));
    }
    @Test void dynamicRewardsRecognizeSupportAndKeepFeeAllowanceUnmultiplied() {
        var config = new YamlConfiguration();
        assertEquals(1.25, BossRewardBalance.dynamicMultiplier(config, 1, 1, 100, 0));
        assertEquals(1.4, BossRewardBalance.dynamicMultiplier(config, 4, 4, 25, 0), .00001);
        assertEquals(1.4, BossRewardBalance.dynamicMultiplier(config, 4, 4, 0, 12), .00001);
        assertEquals(1.5, BossRewardBalance.dynamicMultiplier(config, 20, 20, 100, 100000), .00001);
        assertEquals(92000, BossRewardBalance.dynamicCurrency(80000, 50000, 1.4));
        assertEquals(57000, BossRewardBalance.dynamicCurrency(55000, 50000, 1.4));
        config.set("rewards.dynamic.enabled", false);
        assertEquals(1, BossRewardBalance.dynamicMultiplier(config, 20, 20, 100, 100000));
    }
    @Test void upgradesStockParticipationAndPreservesCustomRewards() {
        var config = new YamlConfiguration();
        config.set("valerius.loot.participation.currency", 5000);
        config.set("valerius.loot.participation.shards", 1);
        config.set("aethelgard.loot.participation.currency", 12345);
        config.set("aethelgard.loot.participation.shards", 7);
        assertTrue(BossRewardBalance.upgrade(config));
        assertEquals(55000, config.getInt("valerius.loot.participation.currency"));
        assertEquals(2, config.getInt("valerius.loot.participation.shards"));
        assertEquals(12345, config.getInt("aethelgard.loot.participation.currency"));
        assertEquals(7, config.getInt("aethelgard.loot.participation.shards"));
        assertFalse(BossRewardBalance.upgrade(config));
    }
    @Test void rareRewardsRemainBoundedAndKeyMessagesAreSpecific() {
        assertEquals(8, BossRewardBalance.rareChance(8, 1));
        assertEquals(8, BossRewardBalance.rareChance(8, 3));
        assertEquals(2.4, BossRewardBalance.rareChance(8, 10), .00001);
        assertEquals(0, BossRewardBalance.rareChance(Double.NaN, 4));
        assertEquals("2 × Rare Crate Keys", BossRewardMailbox.keyDescription("crates key give Rocky Rare 2", "Rocky"));
        assertEquals("1 × enchanter Crate Key", BossRewardMailbox.keyDescription("crate key give Rocky enchanter 1", "Rocky"));
    }
}
