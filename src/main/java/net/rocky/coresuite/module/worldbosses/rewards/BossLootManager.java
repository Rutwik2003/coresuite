package net.rocky.coresuite.module.worldbosses.rewards;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.worldbosses.boss.BossType;
import net.rocky.coresuite.module.worldbosses.combat.BossDamageTracker;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.security.SecureRandom;
import java.util.List;
import java.util.logging.Level;

public class BossLootManager {

    private final CoreSuitePlugin plugin;
    private final BossItemRegistry itemRegistry;
    private final FileConfiguration config;
    private final BossDiscoveryManager discoveryManager;
    private final BossRewardMailbox mailbox;
    private final SecureRandom random = new SecureRandom();

    public BossLootManager(CoreSuitePlugin plugin, BossItemRegistry itemRegistry, FileConfiguration config) {
        this(plugin, itemRegistry, config, null);
    }

    public BossLootManager(CoreSuitePlugin plugin, BossItemRegistry itemRegistry, FileConfiguration config, BossDiscoveryManager discoveryManager) {
        this.plugin = plugin;
        this.itemRegistry = itemRegistry;
        this.config = config;
        this.discoveryManager = discoveryManager;
        this.mailbox = new BossRewardMailbox(plugin);
    }

    public void distributeRewards(BossType bossType, BossDamageTracker damageTracker, String bossDisplayName) {
        try {
            List<BossDamageTracker.DamageEntry> sortedDamagers = damageTracker.getSortedDamagers();
            if (sortedDamagers.isEmpty()) return;

            // Record discovery unlock for all participating damagers
            if (discoveryManager != null) {
                for (BossDamageTracker.DamageEntry entry : sortedDamagers) {
                    discoveryManager.recordParticipation(entry.uuid(), bossType);
                }
            }

            ConfigurationSection lootSec = config.getConfigurationSection(bossType.getConfigKey() + ".loot");
            if (lootSec == null) lootSec = new org.bukkit.configuration.MemoryConfiguration();

            String shardId = bossType.getShardId();
            double totalDmg = damageTracker.getTotalDamage();

            // Broadcast Victory Header
            Bukkit.broadcast(TextUtil.colorize("&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            Bukkit.broadcast(TextUtil.colorize("  &6&l⚔ WORLD BOSS SLAIN: &r" + bossDisplayName));
            Bukkit.broadcast(TextUtil.colorize("  &7Total Damage Inflicted: &c" + String.format("%,.0f", totalDmg) + " HP"));
            Bukkit.broadcast(TextUtil.colorize(""));

            // Rank 1 (MVP)
            BossDamageTracker.DamageEntry mvp = sortedDamagers.get(0);
            Bukkit.broadcast(TextUtil.colorize("  &e&l🥇 MVP: &f" + mvp.name() + " &8- &c" + String.format("%,.0f", mvp.damage()) + " DMG &8(&e" + String.format("%.1f", mvp.percentage()) + "%&8)"));

            // Rank 2
            if (sortedDamagers.size() >= 2) {
                BossDamageTracker.DamageEntry rank2 = sortedDamagers.get(1);
                Bukkit.broadcast(TextUtil.colorize("  &f&l🥈 2nd: &f" + rank2.name() + " &8- &c" + String.format("%,.0f", rank2.damage()) + " DMG &8(&e" + String.format("%.1f", rank2.percentage()) + "%&8)"));
            }

            // Rank 3
            if (sortedDamagers.size() >= 3) {
                BossDamageTracker.DamageEntry rank3 = sortedDamagers.get(2);
                Bukkit.broadcast(TextUtil.colorize("  &6&l🥉 3rd: &f" + rank3.name() + " &8- &c" + String.format("%,.0f", rank3.damage()) + " DMG &8(&e" + String.format("%.1f", rank3.percentage()) + "%&8)"));
            }

            for (int i = 0; i < sortedDamagers.size(); i++) {
                BossDamageTracker.DamageEntry entry = sortedDamagers.get(i);
                java.util.List<ItemStack> items = new java.util.ArrayList<>();
                java.util.List<BossRewardMailbox.Reward> rewards = new java.util.ArrayList<>();
                addTier(lootSec.getConfigurationSection("participation"), shardId, true, items, rewards);
                if (i < 3) {
                    addTier(lootSec.getConfigurationSection(i == 0 ? "rank-1" : "rank-2-3"), shardId, false, items, rewards);
                    ConfigurationSection rare = lootSec.getConfigurationSection("rare-drops");
                    if (rare != null) {
                        if (random.nextDouble() * 100 < rare.getDouble("crate-key-chance", 0)) {
                            String command = rare.getString("crate-key-command", "");
                            if (!command.isBlank()) rewards.add(BossRewardMailbox.Reward.command(command));
                        }
                        if (random.nextDouble() * 100 < rare.getDouble("signature-weapon-chance", 0)) {
                            String weapon = switch (bossType) { case VALERIUS -> "vanguards_cleaver"; case AETHELGARD -> "crown_of_the_forest"; case XOLTHOS -> "widows_fang"; };
                            items.add(itemRegistry.createSignatureWeapon(weapon));
                        }
                    }
                }
                rewards.add(0, BossRewardMailbox.Reward.items(items));
                mailbox.enqueue(entry.uuid(), rewards);
            }
            Bukkit.broadcast(TextUtil.colorize("  &aAll " + sortedDamagers.size() + " participants earned loot! &7Unclaimed rewards are saved: &e/boss claim"));

            Bukkit.broadcast(TextUtil.colorize("&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));

            // Persist stats in DB
            plugin.getDatabaseManager().executeAsync(() -> {
                try {
                    long now = System.currentTimeMillis();
                    plugin.getDatabaseManager().execute(
                        "INSERT INTO coresuite_boss_kills (boss_type, mvp_uuid, mvp_name, total_damage, kill_time) VALUES (?, ?, ?, ?, ?)",
                        bossType.name(), mvp.uuid().toString(), mvp.name(), totalDmg, now
                    );
                    plugin.getDatabaseManager().execute(
                        "INSERT INTO coresuite_boss_stats (uuid, player_name, kills, mvps) VALUES (?, ?, 1, 1) " +
                        "ON CONFLICT(uuid) DO UPDATE SET kills = kills + 1, mvps = mvps + 1, player_name = excluded.player_name",
                        mvp.uuid().toString(), mvp.name()
                    );
                    for (int i = 1; i < sortedDamagers.size(); i++) {
                        BossDamageTracker.DamageEntry entry = sortedDamagers.get(i);
                        plugin.getDatabaseManager().execute(
                            "INSERT INTO coresuite_boss_stats (uuid, player_name, kills, mvps) VALUES (?, ?, 1, 0) " +
                            "ON CONFLICT(uuid) DO UPDATE SET kills = kills + 1, player_name = excluded.player_name",
                            entry.uuid().toString(), entry.name()
                        );
                    }
                } catch (Throwable t) {
                    plugin.getLogger().warning("[WorldBosses] Error persisting kill stats to DB: " + t.getMessage());
                }
            });
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "[WorldBosses] Error distributing boss rewards", t);
        }
    }

    public void initializeMailbox() { mailbox.initialize(); }
    public void inspectPendingRewards(org.bukkit.command.CommandSender sender) { mailbox.inspectPending(sender); }
    public void claimRewards(Player player) { mailbox.claim(player); }

    private void addTier(ConfigurationSection tier, String shardId, boolean participation, java.util.List<ItemStack> items, java.util.List<BossRewardMailbox.Reward> rewards) {
        int shards = tier == null ? 0 : tier.getInt("shards", 0);
        if (participation) shards = Math.max(1, shards);
        for (int remaining = Math.min(2304, Math.max(0, shards)); remaining > 0; remaining -= 64)
            items.add(itemRegistry.createShard(shardId, Math.min(64, remaining)));
        if (tier == null) return;
        double currency = tier.getDouble("currency", 0);
        if (Double.isFinite(currency) && currency > 0) rewards.add(BossRewardMailbox.Reward.currency(currency));
        for (String command : tier.getStringList("commands")) if (command != null && !command.isBlank()) rewards.add(BossRewardMailbox.Reward.command(command));
    }
}
