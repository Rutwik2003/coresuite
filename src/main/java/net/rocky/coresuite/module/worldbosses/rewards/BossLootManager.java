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
    private final SecureRandom random = new SecureRandom();

    public BossLootManager(CoreSuitePlugin plugin, BossItemRegistry itemRegistry, FileConfiguration config) {
        this(plugin, itemRegistry, config, null);
    }

    public BossLootManager(CoreSuitePlugin plugin, BossItemRegistry itemRegistry, FileConfiguration config, BossDiscoveryManager discoveryManager) {
        this.plugin = plugin;
        this.itemRegistry = itemRegistry;
        this.config = config;
        this.discoveryManager = discoveryManager;
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
            if (lootSec == null) return;

            String shardId = bossType.getShardId();
            double totalDmg = damageTracker.getTotalDamage();

            // Broadcast Victory Header
            Bukkit.broadcast(TextUtil.colorize("&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            Bukkit.broadcast(TextUtil.colorize("  &6&l⚔ WORLD BOSS SLAIN: &r" + bossDisplayName));
            Bukkit.broadcast(TextUtil.colorize("  &7Total Damage Inflicted: &c" + String.format("%,.0f", totalDmg) + " HP"));
            Bukkit.broadcast(TextUtil.colorize(""));

            // Rank 1 (MVP)
            BossDamageTracker.DamageEntry mvp = sortedDamagers.get(0);
            Player mvpPlayer = Bukkit.getPlayer(mvp.uuid());
            Bukkit.broadcast(TextUtil.colorize("  &e&l🥇 MVP: &f" + mvp.name() + " &8- &c" + String.format("%,.0f", mvp.damage()) + " DMG &8(&e" + String.format("%.1f", mvp.percentage()) + "%&8)"));

            if (mvpPlayer != null && mvpPlayer.isOnline()) {
                rewardPlayer(mvpPlayer, lootSec.getConfigurationSection("rank-1"), shardId, true, bossType);
            }

            // Rank 2
            if (sortedDamagers.size() >= 2) {
                BossDamageTracker.DamageEntry rank2 = sortedDamagers.get(1);
                Bukkit.broadcast(TextUtil.colorize("  &f&l🥈 2nd: &f" + rank2.name() + " &8- &c" + String.format("%,.0f", rank2.damage()) + " DMG &8(&e" + String.format("%.1f", rank2.percentage()) + "%&8)"));
                Player p2 = Bukkit.getPlayer(rank2.uuid());
                if (p2 != null && p2.isOnline()) {
                    rewardPlayer(p2, lootSec.getConfigurationSection("rank-2-3"), shardId, false, bossType);
                }
            }

            // Rank 3
            if (sortedDamagers.size() >= 3) {
                BossDamageTracker.DamageEntry rank3 = sortedDamagers.get(2);
                Bukkit.broadcast(TextUtil.colorize("  &6&l🥉 3rd: &f" + rank3.name() + " &8- &c" + String.format("%,.0f", rank3.damage()) + " DMG &8(&e" + String.format("%.1f", rank3.percentage()) + "%&8)"));
                Player p3 = Bukkit.getPlayer(rank3.uuid());
                if (p3 != null && p3.isOnline()) {
                    rewardPlayer(p3, lootSec.getConfigurationSection("rank-2-3"), shardId, false, bossType);
                }
            }

            // Qualified Participants (Rank 4+ who dealt >= 5% damage)
            int participantCount = 0;
            for (int i = 3; i < sortedDamagers.size(); i++) {
                BossDamageTracker.DamageEntry entry = sortedDamagers.get(i);
                if (entry.percentage() >= 5.0) {
                    Player p = Bukkit.getPlayer(entry.uuid());
                    if (p != null && p.isOnline()) {
                        rewardPlayer(p, lootSec.getConfigurationSection("participation"), shardId, false, bossType);
                        participantCount++;
                    }
                }
            }

            if (participantCount > 0) {
                Bukkit.broadcast(TextUtil.colorize("  &7...plus &a" + participantCount + " &7qualified hunter(s) received participation loot!"));
            }

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

    private void rewardPlayer(Player player, ConfigurationSection tierSec, String shardId, boolean isMvp, BossType bossType) {
        if (tierSec == null) return;
        try {
            double currency = tierSec.getDouble("currency", 0.0);
            if (currency > 0 && plugin.getEconomyManager().isAvailable()) {
                plugin.getEconomyManager().deposit(player, currency);
                TextUtil.sendMessage(player, "&8[&4&lBoss&8] &a+ " + plugin.getEconomyManager().format(currency) + " &7for your combat bravery!");
            }

            int shards = tierSec.getInt("shards", 0);
            if (shards > 0) {
                ItemStack shardItem = itemRegistry.createShard(shardId, shards);
                giveItemOrDrop(player, shardItem);
                String shardName = getSafeItemName(shardItem, shardId + " Shard");
                TextUtil.sendMessage(player, "&8[&4&lBoss&8] &e+ " + shards + "x " + shardName + " &7added to your inventory!");
            }

            List<String> commands = tierSec.getStringList("commands");
            for (String cmd : commands) {
                if (cmd != null && !cmd.isBlank()) {
                    dispatchRewardCommand(cmd.replace("%player%", player.getName()));
                }
            }

            // Check rare RNG drops for Top Damagers
            ConfigurationSection rareSec = config.getConfigurationSection(bossType.getConfigKey() + ".loot.rare-drops");
            if (rareSec != null) {
                double keyChance = rareSec.getDouble("crate-key-chance", 0.0);
                if (keyChance > 0 && (random.nextDouble() * 100.0) <= keyChance) {
                    String cmd = rareSec.getString("crate-key-command", "");
                    if (!cmd.isEmpty()) {
                        dispatchRewardCommand(cmd.replace("%player%", player.getName()));
                        TextUtil.sendMessage(player, "&8[&6&lLoot&8] &6✦ RARE DROP! &aYou received a bonus high-tier Crate Key!");
                        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
                    }
                }

                double weaponChance = rareSec.getDouble("signature-weapon-chance", 0.0);
                if (weaponChance > 0 && (random.nextDouble() * 100.0) <= weaponChance) {
                    String weaponId = switch (bossType) {
                        case VALERIUS -> "vanguards_cleaver";
                        case AETHELGARD -> "crown_of_the_forest";
                        case XOLTHOS -> "widows_fang";
                    };
                    ItemStack weapon = itemRegistry.createSignatureWeapon(weaponId);
                    giveItemOrDrop(player, weapon);
                    String weaponName = getSafeItemName(weapon, "Mythic Weapon");
                    Bukkit.broadcast(TextUtil.colorize("&6&l✦ MYTHIC DROP! &f" + player.getName() + " &7found the rare &r" + weaponName + "&7 from &r" + bossType.getDefaultDisplayName() + "&7!"));
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.0f);
                }
            }

            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "[WorldBosses] Failed to reward player " + player.getName(), t);
        }
    }

    private void dispatchRewardCommand(String cmd) {
        if (cmd == null || cmd.isBlank()) return;
        String trimmed = cmd.trim();
        if (trimmed.startsWith("/")) trimmed = trimmed.substring(1);
        String base = trimmed.split(" ")[0].toLowerCase();

        if (base.equals("crate") || base.equals("crates") || base.equals("crazycrates")) {
            boolean hasCratePlugin = Bukkit.getPluginManager().isPluginEnabled("ExcellentCrates")
                    || Bukkit.getPluginManager().isPluginEnabled("CrazyCrates")
                    || Bukkit.getPluginManager().isPluginEnabled("GoldenCrates");
            if (!hasCratePlugin) {
                plugin.getLogger().info("[WorldBosses] Skipped crate reward command (no crate plugin installed): " + trimmed);
                return;
            }
        }

        try {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), trimmed);
        } catch (Throwable t) {
            plugin.getLogger().warning("[WorldBosses] Failed to execute reward command: " + trimmed + " (" + t.getMessage() + ")");
        }
    }

    private String getSafeItemName(ItemStack item, String fallback) {
        if (item == null || !item.hasItemMeta()) return fallback;
        if (item.getItemMeta().displayName() != null) {
            return LegacyComponentSerializer.legacySection().serialize(item.getItemMeta().displayName());
        }
        if (item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        return fallback;
    }

    private void giveItemOrDrop(Player player, ItemStack item) {
        var leftover = player.getInventory().addItem(item);
        if (!leftover.isEmpty()) {
            for (ItemStack drop : leftover.values()) {
                player.getWorld().dropItemNaturally(player.getLocation(), drop);
            }
            TextUtil.sendMessage(player, "&c[!] Inventory full! Items were dropped at your feet.");
        }
    }
}
