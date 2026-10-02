package net.rocky.coresuite.module.worldbosses.rewards;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.worldbosses.boss.BossType;
import net.rocky.coresuite.module.worldbosses.combat.BossDamageTracker;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class BossLootManager {
    public void openRewardHistory(Player player) { mailbox.openHistory(player); }
    public void deferForWorldChange(Player player) { mailbox.deferForWorldChange(player); }

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
            double minimumDamage = minimumDamage(bossType);
            int minimumContribution = minimumContribution(bossType);
            List<BossDamageTracker.DamageEntry> sortedDamagers = damageTracker.getQualifiedParticipants(minimumDamage, minimumContribution);
            if (sortedDamagers.isEmpty()) {
                Bukkit.broadcast(TextUtil.colorize("&8[&4&lBoss&8] &eNo player met the contribution requirement; no boss rewards were issued."));
                return;
            }

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

            java.util.Set<java.util.UUID> qualified = sortedDamagers.stream().map(BossDamageTracker.DamageEntry::uuid).collect(java.util.stream.Collectors.toSet());
            for (BossDamageTracker.Role role : BossDamageTracker.Role.values()) {
                String leader = damageTracker.roleLeader(role, qualified);
                if (leader != null) Bukkit.broadcast(TextUtil.colorize("  &b" + role.name().replace('_', ' ') + ": &f" + leader));
            }
            for (int i = 0; i < sortedDamagers.size(); i++) {
                BossDamageTracker.DamageEntry entry = sortedDamagers.get(i);
                double multiplier = BossRewardBalance.dynamicMultiplier(config, damageTracker.getPeakPlayers(),
                        sortedDamagers.size(), entry.percentage(), damageTracker.getContribution(entry.uuid()));
                java.util.List<ItemStack> shardItems = new java.util.ArrayList<>();
                java.util.List<ItemStack> extraItems = new java.util.ArrayList<>();
                java.util.List<BossRewardMailbox.Reward> rewards = new java.util.ArrayList<>();
                addTier(lootSec.getConfigurationSection("participation"), shardId, true, shardItems, extraItems, rewards, multiplier);
                if (i < 3) {
                    ConfigurationSection rankTier = null;
                    if (i == 0) rankTier = lootSec.getConfigurationSection("rank-1");
                    else if (i == 1) rankTier = lootSec.isConfigurationSection("rank-2") ? lootSec.getConfigurationSection("rank-2") : lootSec.getConfigurationSection("rank-2-3");
                    else if (i == 2) rankTier = lootSec.isConfigurationSection("rank-3") ? lootSec.getConfigurationSection("rank-3") : lootSec.getConfigurationSection("rank-2-3");
                    if (rankTier != null) {
                        addTier(rankTier, shardId, false, shardItems, extraItems, rewards, multiplier);
                    }
                }
                if (i == 0) {
                    for (String command : lootSec.getStringList("rank-1.extra-key-commands"))
                        if (!command.isBlank()) rewards.add(BossRewardMailbox.Reward.command(command));
                }
                int shardTotal = shardItems.stream().mapToInt(ItemStack::getAmount).sum();
                java.util.List<ItemStack> finalItems = new java.util.ArrayList<>();
                int cappedShards = BossRewardBalance.cappedShards(shardTotal, i);
                if (cappedShards > 0) finalItems.add(itemRegistry.createShard(shardId, cappedShards));
                finalItems.addAll(extraItems);
                ConfigurationSection rare = lootSec.getConfigurationSection("rare-drops");
                if (rare != null) {
                    for (var bonus : (i < 2) ? rare.getMapList("bonus-keys") : java.util.List.<java.util.Map<?, ?>>of()) {
                        if (bonus.get("chance") instanceof Number chance && bonus.get("command") instanceof String command
                                && !command.isBlank() && random.nextDouble() * 100 < BossRewardBalance.rareChance(chance.doubleValue(), 1))
                            rewards.add(BossRewardMailbox.Reward.command(command));
                    }
                    if (i < 2 && random.nextDouble() * 100 < BossRewardBalance.rareChance(rare.getDouble("crate-key-chance", 0), 1)) {
                        String command = rare.getString("crate-key-command", "");
                        if (!command.isBlank()) rewards.add(BossRewardMailbox.Reward.command(command));
                    }
                    // Mythic equipment is shop-only, including with legacy drop configs.
                }
                double currency = rewards.stream().filter(reward -> "CURRENCY".equals(reward.kind()))
                        .mapToDouble(reward -> Double.parseDouble(reward.payload())).sum();
                rewards.removeIf(reward -> "CURRENCY".equals(reward.kind()));
                currency = BossRewardBalance.dynamicCurrency(currency, config.getDouble("arena.portals.entry-fee", 50000), multiplier);
                if (currency > 0) rewards.add(BossRewardMailbox.Reward.currency(currency));
                rewards.add(0, BossRewardMailbox.Reward.items(finalItems));
                java.util.List<BossRewardMailbox.Reward> receipts = new java.util.ArrayList<>();
                for (int component = 0; component < rewards.size(); component++) {
                    BossRewardMailbox.Reward reward = rewards.get(component);
                    String identity = damageTracker.getEncounterId() + ":" + entry.uuid() + ":" + component;
                    receipts.add(new BossRewardMailbox.Reward(java.util.UUID.nameUUIDFromBytes(
                            identity.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString(), reward.kind(), reward.payload()));
                }
                mailbox.enqueue(entry.uuid(), receipts);
                var magic = plugin.getModuleManager().getModule("grimoire");
                if (magic instanceof net.rocky.coresuite.module.grimoire.GrimoireModule grimoire && grimoire.isEnabled())
                    grimoire.awardBossResearch(entry.uuid(), damageTracker.getEncounterId().toString(), i == 0 ? 3 : 2);
                Player recipient = Bukkit.getPlayer(entry.uuid());
                if (recipient != null && multiplier > 1) TextUtil.sendMessage(recipient,
                        "&6Boss reward bonus: &e+" + String.format("%.0f", (multiplier - 1) * 100)
                                + "% &7to cash above the entry-fee allowance and shard amounts, based on encounter size and your damage/support contribution (shard caps: MVP 6, top 3 4, others 2).");
            }
            Bukkit.broadcast(TextUtil.colorize("  &aAll " + sortedDamagers.size() + " qualified participants earned loot! &7Check chat for your reward details."));

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
                        (plugin.getDatabaseManager().isMySQL()
                                ? "ON DUPLICATE KEY UPDATE kills = kills + 1, mvps = mvps + 1, player_name = ?"
                                : "ON CONFLICT(uuid) DO UPDATE SET kills = kills + 1, mvps = mvps + 1, player_name = ?"),
                        mvp.uuid().toString(), mvp.name(), mvp.name()
                    );
                    for (int i = 1; i < sortedDamagers.size(); i++) {
                        BossDamageTracker.DamageEntry entry = sortedDamagers.get(i);
                        plugin.getDatabaseManager().execute(
                            "INSERT INTO coresuite_boss_stats (uuid, player_name, kills, mvps) VALUES (?, ?, 1, 0) " +
                            (plugin.getDatabaseManager().isMySQL()
                                    ? "ON DUPLICATE KEY UPDATE kills = kills + 1, player_name = ?"
                                    : "ON CONFLICT(uuid) DO UPDATE SET kills = kills + 1, player_name = ?"),
                            entry.uuid().toString(), entry.name(), entry.name()
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
    public void reconcileReward(org.bukkit.command.CommandSender sender, String id, boolean delivered) { mailbox.reconcile(sender, id, delivered); }
    public void claimRewards(Player player) { mailbox.claim(player); }

    private double minimumDamage(BossType type) {
        return Math.max(0, config.getDouble(type.getConfigKey() + ".loot.participation.minimum-damage",
                config.getDouble("rewards.participation.minimum-damage", 20.0)));
    }
    private int minimumContribution(BossType type) {
        return Math.max(1, config.getInt(type.getConfigKey() + ".loot.participation.minimum-contribution",
                config.getInt("rewards.participation.minimum-contribution", 3)));
    }

    private void addTier(ConfigurationSection tier, String shardId, boolean participation,
                         java.util.List<ItemStack> shardItems,
                         java.util.List<ItemStack> extraItems,
                         java.util.List<BossRewardMailbox.Reward> rewards,
                         double multiplier) {
        int shards = tier == null ? 0 : tier.getInt("shards", 0);
        if (participation) shards = Math.max(1, shards);
        shards = (int) Math.floor(Math.max(0, shards) * multiplier);
        for (int remaining = Math.min(2304, Math.max(0, shards)); remaining > 0; remaining -= 64)
            shardItems.add(itemRegistry.createShard(shardId, Math.min(64, remaining)));
        if (tier == null) return;
        extraItems.addAll(parseConfiguredItems(tier));
        double currency = tier.getDouble("currency", 0);
        if (Double.isFinite(currency) && currency > 0) rewards.add(BossRewardMailbox.Reward.currency(currency));
        for (String command : tier.getStringList("commands")) if (command != null && !command.isBlank()) rewards.add(BossRewardMailbox.Reward.command(command));
    }

    public static List<ItemStack> parseConfiguredItems(ConfigurationSection tier) {
        return parseConfiguredItems(tier, ItemStack::new);
    }

    public static <T> List<T> parseConfiguredItems(ConfigurationSection tier, java.util.function.BiFunction<Material, Integer, T> creator) {
        if (tier == null || creator == null) return List.of();
        List<String> entries = tier.getStringList("items");
        if (entries.isEmpty()) return List.of();
        List<T> result = new ArrayList<>();
        for (String entry : entries) {
            if (entry == null || entry.isBlank()) continue;
            String[] parts = entry.trim().split(":");
            if (parts.length < 2) continue;
            String matName = parts[0].trim().toUpperCase(java.util.Locale.ROOT);
            Material material = Material.matchMaterial(matName);
            if (material == null || material == Material.AIR || material == Material.CAVE_AIR || material == Material.VOID_AIR) continue;

            String countStr = parts[1].trim();
            double chance = 100.0;
            if (parts.length >= 3) {
                String chanceStr = parts[2].trim().replace("%", "");
                try {
                    chance = Double.parseDouble(chanceStr);
                } catch (NumberFormatException ignored) { continue; }
                if (!Double.isFinite(chance) || chance < 0 || chance > 100) continue;
            }

            if (chance < 100.0 && java.util.concurrent.ThreadLocalRandom.current().nextDouble(100.0) >= chance) {
                continue;
            }

            int count;
            try {
                if (!countStr.matches("\\d+(?:-\\d+)?")) continue;
                String[] range = countStr.split("-");
                int min = Math.min(2304, Integer.parseInt(range[0]));
                int max = range.length == 1 ? min : Math.min(2304, Integer.parseInt(range[1]));
                if (min > max) { int swap = min; min = max; max = swap; }
                count = min == max ? min : java.util.concurrent.ThreadLocalRandom.current().nextInt(min, max + 1);
            } catch (NumberFormatException invalid) { continue; }

            int maxStack = 64;
            try {
                maxStack = Math.max(1, material.getMaxStackSize());
            } catch (Throwable ignored) {
                if (material.name().contains("TOTEM")) maxStack = 1;
            }
            while (count > 0) {
                int toAdd = Math.min(count, maxStack);
                try {
                    T item = creator.apply(material, toAdd);
                    if (item != null) result.add(item);
                } catch (IllegalArgumentException invalidItem) { break; }
                count -= toAdd;
            }
        }
        return result;
    }
}
