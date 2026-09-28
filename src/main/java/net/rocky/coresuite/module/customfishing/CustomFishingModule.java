/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.Chunk
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.OfflinePlayer
 *  org.bukkit.Sound
 *  org.bukkit.block.Biome
 *  org.bukkit.block.Block
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandExecutor
 *  org.bukkit.command.CommandSender
 *  org.bukkit.command.PluginCommand
 *  org.bukkit.command.TabCompleter
 *  org.bukkit.configuration.file.YamlConfiguration
 *  org.bukkit.enchantments.Enchantment
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.inventory.ClickType
 *  org.bukkit.event.inventory.InventoryAction
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.event.inventory.InventoryCloseEvent
 *  org.bukkit.event.player.PlayerFishEvent
 *  org.bukkit.event.player.PlayerFishEvent$State
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.inventory.meta.SkullMeta
 *  org.bukkit.persistence.PersistentDataContainer
 *  org.bukkit.persistence.PersistentDataType
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.potion.PotionEffect
 *  org.bukkit.potion.PotionEffectType
 *  org.bukkit.util.StringUtil
 */
package net.rocky.coresuite.module.customfishing;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.gui.GuiButton;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.Module;
import net.rocky.coresuite.module.customfishing.BaitManager;
import net.rocky.coresuite.module.customfishing.FishBagManager;
import net.rocky.coresuite.module.customfishing.FishingCollectionManager;
import net.rocky.coresuite.module.customfishing.FishingEnchantManager;
import net.rocky.coresuite.module.customfishing.FishingEventManager;
import net.rocky.coresuite.module.customfishing.FishingLeaderboardManager;
import net.rocky.coresuite.module.customfishing.FishingTournamentManager;
import net.rocky.coresuite.module.customfishing.LegendaryNPCManager;
import net.rocky.coresuite.module.customfishing.LegendaryQuestManager;
import net.rocky.coresuite.module.customfishing.WanderingAnglerManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.StringUtil;

public class CustomFishingModule
extends Module
implements Listener,
CommandExecutor,
TabCompleter {
    private final Map<UUID, Object> dataSessions = new ConcurrentHashMap<>();
    private final Map<UUID, FishData> playerData = new ConcurrentHashMap<UUID, FishData>();
    private final Map<UUID, Long> lastFishTime = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, Integer> fishStreak = new ConcurrentHashMap<UUID, Integer>();
    private final Map<UUID, Long> actionbarOverride = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, Map<Long, AtomicInteger>> chunkCatches = new ConcurrentHashMap<UUID, Map<Long, AtomicInteger>>();
    private final Random random = new Random();
    private final FishingEnchantManager enchantManager;
    private FishBagManager fishBagManager;
    private FishingEventManager eventManager;
    private FishingLeaderboardManager leaderboardManager;
    private WanderingAnglerManager wanderingAnglerManager;
    private FishingTournamentManager tournamentManager;
    private BaitManager baitManager;
    private LegendaryNPCManager legendaryNPCManager;
    private LegendaryQuestManager legendaryQuestManager;
    private FishingCollectionManager collectionManager;
    private NamespacedKey speciesKey;
    private NamespacedKey weightKey;
    private NamespacedKey priceKey;

    public FishBagManager getFishBagManager() {
        return this.fishBagManager;
    }

    public BaitManager getBaitManager() {
        return this.baitManager;
    }

    public CustomFishingModule(CoreSuitePlugin plugin) {
        super(plugin, "CustomFishing", "custom-fishing");
        this.enchantManager = new FishingEnchantManager(plugin);
    }

    @Override
    public void onEnable() {
        int configVersion = this.config.getInt("config-version", 1);
        if (configVersion < 2) {
            // Missing bait keys were merged by the shared loader; keep configured bait values.
            this.config.set("config-version", 2);
            this.saveConfig();
        }
        boolean saved = false;
        if (!this.config.contains("fatigue.recovery-time-minutes")) {
            this.config.set("fatigue.recovery-time-minutes", 60);
            saved = true;
        }
        if (!this.config.contains("pond-event.fatigue-heal-amount")) {
            this.config.set("pond-event.fatigue-heal-amount", 5.0);
            saved = true;
        }
        if (saved) {
            this.saveConfig();
        }
        this.speciesKey = new NamespacedKey((Plugin)this.plugin, "fish_species");
        this.weightKey = new NamespacedKey((Plugin)this.plugin, "fish_weight");
        this.priceKey = new NamespacedKey((Plugin)this.plugin, "fish_price");
        this.initDatabase();
        if (this.config.getBoolean("maintenance.remove-retired-fish", false)) this.cleanupRemovedFish();
        this.migratePondConfig();
        this.fishBagManager = new FishBagManager(this.plugin, this);
        this.eventManager = new FishingEventManager(this.plugin, this);
        this.leaderboardManager = new FishingLeaderboardManager(this.plugin, this);
        this.wanderingAnglerManager = new WanderingAnglerManager(this.plugin, this);
        this.tournamentManager = new FishingTournamentManager(this.plugin, this);
        this.baitManager = new BaitManager(this.plugin, this);
        this.legendaryNPCManager = new LegendaryNPCManager(this.plugin, this);
        this.legendaryQuestManager = new LegendaryQuestManager(this.plugin, this);
        this.collectionManager = new FishingCollectionManager(this.plugin, this);
        this.registerListener(this);
        this.registerListener(this.wanderingAnglerManager);
        this.registerListener(this.baitManager);
        this.registerListener(this.legendaryNPCManager);
        this.registerListener(this.legendaryQuestManager);
        this.registerListener(this.collectionManager);
        PluginCommand command = this.plugin.getCommand("fish");
        if (command != null) {
            command.setExecutor((CommandExecutor)this);
            command.setTabCompleter((TabCompleter)this);
        }
        this.registerTask(Bukkit.getScheduler().runTaskTimerAsynchronously((Plugin)this.plugin, () -> {
            for (Map<Long, AtomicInteger> worldMap : this.chunkCatches.values()) {
                for (Map.Entry<Long, AtomicInteger> entry : worldMap.entrySet()) {
                    if (entry.getValue().addAndGet(-5) > 0) continue;
                    worldMap.remove(entry.getKey());
                }
            }
        }, 600L, 600L));
        this.registerTask(Bukkit.getScheduler().runTaskTimer((Plugin)this.plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                ItemStack mainHand = player.getInventory().getItemInMainHand();
                ItemStack offHand = player.getInventory().getItemInOffHand();
                if ((mainHand == null || mainHand.getType() != Material.FISHING_ROD) && (offHand == null || offHand.getType() != Material.FISHING_ROD)) continue;
                this.sendFishingActionBar(player);
            }
        }, 20L, 20L));
        for (Player p : Bukkit.getOnlinePlayers()) {
            this.loadData(p);
            this.fishBagManager.loadBag(p.getUniqueId());
        }
    }

    private void cleanupRemovedFish() {
        String[] removed = new String[]{"drowned_king_fish", "fish_barrel", "fin_shady", "fishlin_brute", "malfisha", "moaf", "nameless_fish", "necromancer_fish", "pain_eel", "zombie_fish"};
        boolean configChanged = false;
        for (String f : removed) {
            if (!this.config.contains("species." + f)) continue;
            this.config.set("species." + f, null);
            configChanged = true;
        }
        if (configChanged) {
            this.saveConfig();
        }
        this.plugin.getDatabaseManager().executeAsync(() -> {
            for (String f : removed) {
                this.plugin.getDatabaseManager().execute("DELETE FROM coresuite_fish_logbook WHERE species_id = ?", f);
            }
        });
    }

    private void migratePondConfig() {
        boolean configChanged = false;
        String enter = this.config.getString("pond-event.enter-message", "");
        if (enter.contains("â") || enter.contains("\n") || enter.contains("You entered")) {
            this.config.set("pond-event.enter-message", "&a✦ Legendary Fishing Pond Nearby! Cast your bobber into the glowing ring for bonus rewards!");
            configChanged = true;
        }
        String leave = this.config.getString("pond-event.leave-message", "");
        if (leave.contains("â") || leave.contains("\n") || leave.contains("You left")) {
            this.config.set("pond-event.leave-message", "&7✦ You moved away from the Legendary Fishing Pond.");
            configChanged = true;
        }
        if (!this.config.contains("max-fish-price") || this.config.getDouble("max-fish-price") == 50000.0) {
            this.config.set("max-fish-price", -1.0);
            configChanged = true;
        }
        List<String> cthulhuBiomes = this.config.getStringList("species.the_fish_of_cthulu.biomes");
        if (cthulhuBiomes.isEmpty()) {
            this.config.set("species.the_fish_of_cthulu.biomes", Arrays.asList("OCEAN", "DEEP_OCEAN", "WARM_OCEAN", "DEEP_DARK"));
            configChanged = true;
        }
        List<String> rewards = this.config.getStringList("rewards");
        boolean needsRewardsFix = false;
        if (rewards != null && !rewards.isEmpty()) {
            for (String r : rewards) {
                if (r.contains("Old Boot") || r.contains("5.0|Rare") || r.contains("\n") || r.contains("\r")) {
                    needsRewardsFix = true;
                    break;
                }
            }
        }
        if (needsRewardsFix || rewards == null || rewards.isEmpty()) {
            this.config.set("rewards", Arrays.asList(
                "60.0|Uncommon|&a&lUncommon Crate Key|entity.player.levelup|crate key give {player} uncommon 1",
                "26.0|Rare|&b&lRare Crate Key|entity.player.levelup|crate key give {player} rare 1",
                "10.0|Epic|&d&lPotions Crate Key|ui.toast.challenge_complete|crate key give {player} potions 1",
                "2.0|Legendary|&6&lElite Crate Key|ui.toast.challenge_complete|crate key give {player} elite 1",
                "2.0|Mythical|&c&lSpawner Crate Key|ui.toast.challenge_complete|crate key give {player} spawner 1"
            ));
            configChanged = true;
        }
        if (!this.config.contains("treasure.base-chance")) {
            this.config.set("treasure.base-chance", 2.0);
            configChanged = true;
        }
        if (!this.config.contains("treasure.pond-multiplier")) {
            this.config.set("treasure.pond-multiplier", 1.5);
            configChanged = true;
        }
        if (!this.config.contains("junk.base-chance")) {
            this.config.set("junk.base-chance", 4.0);
            configChanged = true;
        }
        if (this.config.getConfigurationSection("species") != null) {
            for (String key : this.config.getConfigurationSection("species").getKeys(false)) {
                String spPath = "species." + key + ".";
                String rarity = this.config.getString(spPath + "rarity", "");
                if ("Mythical".equalsIgnoreCase(rarity)) {
                    double ppk = this.config.getDouble(spPath + "price-per-kg", 10.0);
                    if (ppk < 2000.0) {
                        this.config.set(spPath + "price-per-kg", 8000.0);
                        configChanged = true;
                    }
                }
            }
        }
        if (this.config.contains("baits.mythic_chum.rarity-bias") &&
            "epic".equalsIgnoreCase(this.config.getString("baits.mythic_chum.rarity-bias"))) {
            this.config.set("baits.mythic_chum.rarity-bias", "mythical");
            configChanged = true;
        }
        if (configChanged) {
            this.saveConfig();
        }
    }

    public double getFishValue(double weight, double pricePerKg) {
        double total = weight * pricePerKg;
        double maxPrice = this.config.getDouble("max-fish-price", -1.0);
        if (maxPrice > 0.0 && total > maxPrice) {
            return maxPrice;
        }
        return total;
    }

    @Override
    public void onDisable() {
        PluginCommand command;
        if (this.eventManager != null) {
            this.eventManager.shutdown();
        }
        if (this.wanderingAnglerManager != null) {
            this.wanderingAnglerManager.shutdown();
        }
        if (this.tournamentManager != null) {
            this.tournamentManager.shutdown();
        }
        if (this.legendaryNPCManager != null) {
            this.legendaryNPCManager.shutdown();
        }
        if (this.baitManager != null) {
            this.baitManager.shutdown();
        }
        if (this.legendaryQuestManager != null) {
            this.legendaryQuestManager.shutdown();
        }
        if ((command = this.plugin.getCommand("fish")) != null && command.getExecutor() == this) {
            command.setExecutor(null);
            command.setTabCompleter(null);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            this.saveData(p.getUniqueId());
            if (this.fishBagManager != null && p.getOpenInventory().getTopInventory().getHolder() instanceof FishBagManager.BagHolder) p.closeInventory();
        }
        if (this.fishBagManager != null) this.fishBagManager.shutdown();
        this.dataSessions.clear();
        this.playerData.clear();
        this.lastFishTime.clear();
        this.fishStreak.clear();
        this.actionbarOverride.clear();
        this.chunkCatches.clear();
    }

    private void initDatabase() {
        this.plugin.getDatabaseManager().execute("CREATE TABLE IF NOT EXISTS coresuite_fishing (uuid TEXT PRIMARY KEY, player_name TEXT, level INTEGER DEFAULT 1, xp INTEGER DEFAULT 0, total_catches INTEGER DEFAULT 0, bag_pages INTEGER DEFAULT 0, fatigue_score REAL DEFAULT 0, fatigue_last_update BIGINT DEFAULT 0, legendary_pity INTEGER DEFAULT 0)", new Object[0]);
        this.plugin.getDatabaseManager().execute("CREATE TABLE IF NOT EXISTS coresuite_active_baits (uuid TEXT PRIMARY KEY, bait_id TEXT, bait_type TEXT, casts_remaining INTEGER, expires_at BIGINT, catch_bonus REAL, weight_bonus REAL, rarity_bias TEXT)", new Object[0]);
        this.plugin.getDatabaseManager().execute("CREATE TABLE IF NOT EXISTS coresuite_bait_stock (uuid TEXT, bait_id TEXT, bought_amount INTEGER DEFAULT 0, last_restock BIGINT DEFAULT 0, PRIMARY KEY (uuid, bait_id))", new Object[0]);
        this.plugin.getDatabaseManager().executeAsync(() -> {
            Statement s;
            Connection c2;
            try {
                c2 = this.plugin.getDatabaseManager().getConnection();
                try {
                    s = c2.createStatement();
                    try {
                        s.execute("ALTER TABLE coresuite_fishing ADD COLUMN bag_pages INTEGER DEFAULT 0");
                    }
                    finally {
                        if (s != null) {
                            s.close();
                        }
                    }
                }
                finally {
                    if (c2 != null) {
                        c2.close();
                    }
                }
            }
            catch (Exception c2_alt_alt) {
                // empty catch block
            }
            try {
                c2 = this.plugin.getDatabaseManager().getConnection();
                try {
                    s = c2.createStatement();
                    try {
                        s.execute("ALTER TABLE coresuite_fishing ADD COLUMN fatigue_score REAL DEFAULT 0");
                    }
                    finally {
                        if (s != null) {
                            s.close();
                        }
                    }
                }
                finally {
                    if (c2 != null) {
                        c2.close();
                    }
                }
            }
            catch (Exception c3) {
                // empty catch block
            }
            try {
                c2 = this.plugin.getDatabaseManager().getConnection();
                try {
                    s = c2.createStatement();
                    try {
                        s.execute("ALTER TABLE coresuite_fishing ADD COLUMN fatigue_last_update BIGINT DEFAULT 0");
                    }
                    finally {
                        if (s != null) {
                            s.close();
                        }
                    }
                }
                finally {
                    if (c2 != null) {
                        c2.close();
                    }
                }
            }
            catch (Exception c4) {
                // empty catch block
            }
            try {
                c2 = this.plugin.getDatabaseManager().getConnection();
                try {
                    s = c2.createStatement();
                    try {
                        s.execute("ALTER TABLE coresuite_fishing ADD COLUMN legendary_pity INTEGER DEFAULT 0");
                    }
                    finally {
                        if (s != null) {
                            s.close();
                        }
                    }
                }
                finally {
                    if (c2 != null) {
                        c2.close();
                    }
                }
            }
            catch (Exception exception) {
                // empty catch block
            }
        });
        this.plugin.getDatabaseManager().execute("CREATE TABLE IF NOT EXISTS coresuite_fish_logbook (uuid TEXT, species_id TEXT, max_weight REAL, PRIMARY KEY(uuid, species_id))", new Object[0]);
        this.plugin.getDatabaseManager().execute("CREATE TABLE IF NOT EXISTS coresuite_fish_bag (uuid VARCHAR(36) PRIMARY KEY, inventory_data TEXT)", new Object[0]);
    }

    private void loadData(Player player) {
        UUID uuid = player.getUniqueId();
        String playerName = player.getName();
        Object session = new Object();
        dataSessions.put(uuid, session);
        playerData.remove(uuid);
        plugin.getDatabaseManager().executeAsync(() -> {
            FishData loaded;
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT level, xp, total_catches, bag_pages, fatigue_score, fatigue_last_update, legendary_pity FROM coresuite_fishing WHERE uuid = ?")) {
                stmt.setString(1, uuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        loaded = new FishData(rs.getInt("level"), rs.getInt("xp"), rs.getInt("total_catches"), rs.getInt("bag_pages"), rs.getDouble("fatigue_score"), rs.getLong("fatigue_last_update"), rs.getInt("legendary_pity"));
                    } else {
                        loaded = new FishData(1, 0, 0, 0);
                    }
                }
                // Persist a new record before exposing it to commands or catches.
                try (PreparedStatement insert = conn.prepareStatement("INSERT INTO coresuite_fishing (uuid, player_name) SELECT ?, ? WHERE NOT EXISTS (SELECT 1 FROM coresuite_fishing WHERE uuid = ?)")) {
                    insert.setString(1, uuid.toString());
                    insert.setString(2, playerName);
                    insert.setString(3, uuid.toString());
                    insert.executeUpdate();
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Fishing data unavailable for " + uuid, e);
                return;
            }
            if (dataSessions.get(uuid) != session || !plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (dataSessions.get(uuid) == session) playerData.put(uuid, loaded);
            });
        });
    }

    private void saveData(UUID uuid) {
        FishData data = this.playerData.get(uuid);
        if (data != null) {
            this.plugin.getDatabaseManager().execute("UPDATE coresuite_fishing SET level = ?, xp = ?, total_catches = ?, bag_pages = ?, fatigue_score = ?, fatigue_last_update = ?, legendary_pity = ? WHERE uuid = ?", data.level, data.xp, data.totalCatches, data.bagPages, data.fatigueScore, data.fatigueLastUpdate, data.legendaryPity, uuid.toString());
        }
    }

    private void saveDataAsync(UUID uuid) {
        this.saveData(uuid);
    }

    private double calculateFatigue(Player player, FishData data) {
        int threshold;
        long now = System.currentTimeMillis();
        long elapsed = now - data.fatigueLastUpdate;
        if (elapsed > 0L) {
            if (data.fatigueScore > 0.0) {
                int decayMinutes = this.config.getInt("fatigue.recovery-time-minutes", 45);
                double decayMs = (double)decayMinutes * 60000.0;
                double decayRate = 100.0 / decayMs;
                double decay = (double)elapsed * decayRate;
                double oldScore = data.fatigueScore;
                data.fatigueScore = Math.max(0.0, data.fatigueScore - decay);
                if (decay >= 1.0 && player != null && elapsed >= 180000L) {
                    double recovered = Math.min(oldScore, decay);
                    if (data.fatigueScore == 0.0) {
                        TextUtil.sendMessage((CommandSender)player, "&a&lYou feel fully rested! &aYour fatigue has completely faded.");
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 2.0f);
                    } else {
                        TextUtil.sendMessage((CommandSender)player, "&aYou recovered &2" + String.format("%.1f", recovered) + "% &afatigue while resting.");
                    }
                }
            }
            data.fatigueLastUpdate = now;
        }
        threshold = this.config.getInt("fatigue.threshold", 50);
        if (data.fatigueScore <= (double)threshold) {
            return 0.0;
        }
        double fatiguePercent = ((data.fatigueScore - (double)threshold) / (100.0 - (double)threshold)) * 0.70;
        return Math.min(0.70, Math.max(0.0, fatiguePercent));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        this.loadData(event.getPlayer());
        this.fishBagManager.loadBag(event.getPlayer().getUniqueId());
        if (this.legendaryQuestManager != null) {
            this.legendaryQuestManager.loadPlayer(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        FishData data;
        if (this.legendaryQuestManager != null) {
            this.legendaryQuestManager.unloadPlayer(event.getPlayer().getUniqueId());
        }
        UUID quitUuid = event.getPlayer().getUniqueId();
        this.dataSessions.remove(quitUuid);
        this.saveData(quitUuid);
        if (event.getPlayer().getOpenInventory().getTopInventory().getHolder() instanceof FishBagManager.BagHolder) event.getPlayer().closeInventory();
        this.fishBagManager.unloadBag(event.getPlayer().getUniqueId());
        this.playerData.remove(event.getPlayer().getUniqueId());
        this.lastFishTime.remove(event.getPlayer().getUniqueId());
        this.fishStreak.remove(event.getPlayer().getUniqueId());
        this.actionbarOverride.remove(event.getPlayer().getUniqueId());
        if (this.collectionManager != null) {
            this.collectionManager.unloadPlayer(event.getPlayer().getUniqueId());
        }
        if (this.legendaryNPCManager != null) {
            this.legendaryNPCManager.removeNPC(event.getPlayer().getUniqueId());
        }
        if (this.eventManager != null) {
            this.eventManager.unloadPlayer(event.getPlayer().getUniqueId());
        }
    }

    private void saveCurrentBagPage(Player player, Inventory inv, int page) {
        ItemStack[] bag = this.fishBagManager.getBag(player.getUniqueId());
        if (bag == null || page < 0 || page >= 10) return;
        for (int i = 0; i < 45; i++) bag[page * 45 + i] = inv.getItem(i);
        this.fishBagManager.updateBag(player.getUniqueId(), bag);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof FishBagManager.BagHolder holder
                && holder.belongsTo(this.fishBagManager) && holder.isCurrent(event.getPlayer().getUniqueId())) {
            saveCurrentBagPage((Player) event.getPlayer(), event.getInventory(), holder.page);
            fishBagManager.saveBag(holder.owner);
        }
    }

    @EventHandler
    public void onInventoryDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof FishBagManager.BagHolder holder)) return;
        if (!holder.belongsTo(fishBagManager) || !holder.isCurrent(event.getWhoClicked().getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        for (int slot : event.getRawSlots()) {
            if (slot < 54 && (slot >= 45 || !isFishItem(event.getOldCursor()))) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof FishBagManager.BagHolder holder)) return;
        Player player = (Player) event.getWhoClicked();
        if (!holder.belongsTo(fishBagManager) || !holder.isCurrent(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }
        int slot = event.getRawSlot();
        if (slot >= 45 && slot < 54) {
            event.setCancelled(true);
            // Inventory transitions must run after the current click transaction finishes.
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!holder.isCurrent(player.getUniqueId()) || player.getOpenInventory().getTopInventory() != top) return;
                FishData data = playerData.get(player.getUniqueId());
                if (data == null) return;
                int pages = Math.max(1, Math.min(10, data.bagPages));
                int page = holder.page;
                if (slot == 45 && page > 0) fishBagManager.openBag(player, pages, page - 1);
                else if (slot == 53 && page + 1 < pages) fishBagManager.openBag(player, pages, page + 1);
                else if (slot == 46 || slot == 47 || slot == 51) {
                    fishBagManager.sortOpenBag(player, this, slot == 46 ? FishBagManager.SortMode.SPECIES
                            : slot == 47 ? FishBagManager.SortMode.RARITY : FishBagManager.SortMode.WEIGHT, pages);
                } else if (slot == 48 && pages < 10) player.performCommand("fish buybag");
                else if (slot == 49) player.performCommand("fish");
            });
            return;
        }
        if (slot >= 0 && slot < 45) {
            ItemStack incoming = event.getCursor();
            if (event.getClick() == ClickType.NUMBER_KEY) incoming = player.getInventory().getItem(event.getHotbarButton());
            else if (event.getClick() == ClickType.SWAP_OFFHAND) incoming = player.getInventory().getItemInOffHand();
            if (incoming != null && !incoming.getType().isAir() && !isFishItem(incoming)) event.setCancelled(true);
        }
        if (event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY && slot >= 54 && !isFishItem(event.getCurrentItem())) event.setCancelled(true);
        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) event.setCancelled(true);
    }

    public ItemStack createFishItem(String name, String materialName, double weight, double pricePerKg) {
        return this.createFishItem(name, materialName, weight, pricePerKg, "");
    }

    public ItemStack createFishItem(String name, String materialName, double weight, double pricePerKg, String itemModel) {
        Material mat;
        try {
            mat = Material.valueOf((String)materialName.toUpperCase());
        }
        catch (IllegalArgumentException e) {
            mat = Material.COD;
        }
        ItemStack fish = new ItemStack(mat);
        ItemMeta meta = fish.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.colorize(name));
            ArrayList<Component> lore = new ArrayList<Component>();
            lore.add(TextUtil.colorize("&7Weight: &b" + weight + " kg"));
            double val = this.getFishValue(weight, pricePerKg);
            if (pricePerKg > 0.0) {
                lore.add(TextUtil.colorize("&7Value: &a" + this.plugin.getEconomyManager().format(val)));
            }
            meta.lore((java.util.List<net.kyori.adventure.text.Component>)(java.util.List<?>)lore);
            if (itemModel != null && !itemModel.isEmpty()) {
                net.rocky.coresuite.common.compat.PaperCompat.setItemModel(meta, NamespacedKey.fromString((String)itemModel));
            }
            meta.getPersistentDataContainer().set(this.speciesKey, PersistentDataType.STRING, ChatColor.stripColor((String)ChatColor.translateAlternateColorCodes((char)'&', (String)name)));
            meta.getPersistentDataContainer().set(this.weightKey, PersistentDataType.DOUBLE, weight);
            meta.getPersistentDataContainer().set(this.priceKey, PersistentDataType.DOUBLE, pricePerKg);
            fish.setItemMeta(meta);
        }
        return fish;
    }

    private boolean isFishItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        return item.getItemMeta().getPersistentDataContainer().has(this.speciesKey, PersistentDataType.STRING);
    }

    private void checkAndUpdatePersonalBest(Player player, String speciesId, double weight) {
        this.plugin.getDatabaseManager().executeAsync(() -> {
            block33: {
                try (Connection conn = this.plugin.getDatabaseManager().getConnection();
                     PreparedStatement stmt = conn.prepareStatement("SELECT max_weight FROM coresuite_fish_logbook WHERE uuid = ? AND species_id = ?");){
                    stmt.setString(1, player.getUniqueId().toString());
                    stmt.setString(2, speciesId);
                    try (ResultSet rs = stmt.executeQuery();){
                        if (rs.next()) {
                            double max = rs.getDouble("max_weight");
                            if (!(weight > max)) break block33;
                            try (PreparedStatement update = conn.prepareStatement("UPDATE coresuite_fish_logbook SET max_weight = ? WHERE uuid = ? AND species_id = ?");){
                                update.setDouble(1, weight);
                                update.setString(2, player.getUniqueId().toString());
                                update.setString(3, speciesId);
                                update.executeUpdate();
                            }
                            player.sendMessage(TextUtil.colorize(this.config.getString("prefix", "&8[&b&lFishing&8] &r") + "&aNew Personal Best for " + speciesId + "! &8(&b" + weight + " kg&8)"));
                            break block33;
                        }
                        try (PreparedStatement insert = conn.prepareStatement("INSERT INTO coresuite_fish_logbook (uuid, species_id, max_weight) VALUES (?, ?, ?)");){
                            insert.setString(1, player.getUniqueId().toString());
                            insert.setString(2, speciesId);
                            insert.setDouble(3, weight);
                            insert.executeUpdate();
                        }
                    }
                }
                catch (SQLException e) {
                    this.plugin.getLogger().log(Level.SEVERE, "Failed to update personal best", e);
                }
            }
        });
    }

    @EventHandler(ignoreCancelled=true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.FISHING) {
            if (this.eventManager != null && this.eventManager.getActivePond() != null) {
                Player player = event.getPlayer();
                Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                    if (event.getHook() != null && event.getHook().isValid() && player.isOnline()) {
                        Location hookLoc = event.getHook().getLocation();
                        if (this.eventManager.isLocationInPond(hookLoc, 1.5) || this.eventManager.isPlayerInOrNearPond(player)) {
                            this.actionbarOverride.put(player.getUniqueId(), System.currentTimeMillis() + 3000L);
                            TextUtil.sendActionBar(player, "&b✦ Bobber in Legendary Pond! &a(2x Catch & Weight Active)");
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.5f);
                        }
                    }
                }, 15L);
            }
            return;
        }
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            double multCap;
            Player player = event.getPlayer();
            UUID uuid = player.getUniqueId();

            if (!playerData.containsKey(uuid) || fishBagManager.getBag(uuid) == null) {
                event.setCancelled(true);
                TextUtil.sendMessage(player, "&cYour fishing data is not available yet. Please try again shortly.");
                return;
            }
            if (player.getOpenInventory().getTopInventory().getHolder() instanceof FishBagManager.BagHolder) player.closeInventory();
            // 1. Determine Legendary Pond status first
            double eventRewardMult = 1.0;
            double eventWeightMult = 1.0;
            boolean inPond = false;
            if (this.eventManager != null && this.eventManager.getActivePond() != null) {
                Location hookLoc = event.getHook() != null ? event.getHook().getLocation() : null;
                Location playerLoc = player.getLocation();
                Location pondLoc = this.eventManager.getActivePond();
                if (pondLoc != null && pondLoc.getWorld() != null) {
                    double radius = this.config.getDouble("pond-event.radius", 5.0);
                    boolean hookIn = hookLoc != null && hookLoc.getWorld().getName().equals(pondLoc.getWorld().getName())
                            && (Math.pow(hookLoc.getX() - pondLoc.getX(), 2) + Math.pow(hookLoc.getZ() - pondLoc.getZ(), 2) <= Math.pow(radius + 1.5, 2))
                            && Math.abs(hookLoc.getY() - pondLoc.getY()) <= 10.0;
                    boolean playerIn = playerLoc.getWorld().getName().equals(pondLoc.getWorld().getName())
                            && (Math.pow(playerLoc.getX() - pondLoc.getX(), 2) + Math.pow(playerLoc.getZ() - pondLoc.getZ(), 2) <= Math.pow(radius + 3.5, 2))
                            && Math.abs(playerLoc.getY() - pondLoc.getY()) <= 10.0;
                    boolean setIn = this.eventManager.getPlayersInPond() != null && this.eventManager.getPlayersInPond().contains(uuid);

                    if (hookIn || playerIn || setIn) {
                        inPond = true;
                        eventRewardMult = this.config.getDouble("pond-event.reward-multiplier", 2.0);
                        eventWeightMult = this.config.getDouble("pond-event.weight-multiplier", 2.0);
                    }
                }
            }

            // 2. Anti-AFK Overfishing check (ONLY when NOT in a Legendary Pond)
            if (!inPond) {
                Chunk pChunk = player.getLocation().getChunk();
                Map worldMap = this.chunkCatches.computeIfAbsent(player.getWorld().getUID(), k -> new ConcurrentHashMap());
                long pKey = (long)pChunk.getX() & 0xFFFFFFFFL | ((long)pChunk.getZ() & 0xFFFFFFFFL) << 32;
                ((java.util.concurrent.atomic.AtomicInteger)worldMap.computeIfAbsent(pKey, k -> new java.util.concurrent.atomic.AtomicInteger(0))).incrementAndGet();
                int radiusSum = 0;
                for (int dx = -2; dx <= 2; ++dx) {
                    for (int dz = -2; dz <= 2; ++dz) {
                        long ck = (long)(pChunk.getX() + dx) & 0xFFFFFFFFL | ((long)(pChunk.getZ() + dz) & 0xFFFFFFFFL) << 32;
                        AtomicInteger count = (AtomicInteger)worldMap.get(ck);
                        if (count == null) continue;
                        radiusSum += count.get();
                    }
                }
                int maxCatches = this.config.getInt("anti-afk.max-catches-per-area", 60);
                if (radiusSum > maxCatches) {
                    this.actionbarOverride.put(player.getUniqueId(), System.currentTimeMillis() + 3000L);
                    TextUtil.sendActionBar(player, "&c[!] These waters are overfished! Move to a new spot or wait for fish to return.");
                    player.playSound(player.getLocation(), Sound.ENTITY_FISH_SWIM, 1.0f, 0.5f);
                    if (event.getCaught() != null) {
                        event.getCaught().remove();
                    }
                    return;
                }
            }

            // 3. Ensure player data is present
            FishData data = this.playerData.get(uuid);
            if (data == null) return;

            int streakCap = 6;
            if (data.level >= 10) {
                streakCap = 15;
            } else if (data.level >= 5) {
                streakCap = 10;
            }
            long now = System.currentTimeMillis();
            long last = this.lastFishTime.getOrDefault(uuid, 0L);
            int streak = this.fishStreak.getOrDefault(uuid, 0);
            int timeout = this.config.getInt("streak-timeout", 45);
            streak = now - last < (long)timeout * 1000L ? ++streak : 1;
            this.lastFishTime.put(uuid, now);
            if (streak > streakCap) {
                streak = streakCap;
            }
            this.fishStreak.put(uuid, streak);

            double fatiguePercent = this.calculateFatigue(player, data);
            double fatigueMult = 1.0 - fatiguePercent;
            if (data.fatigueScore >= 85.0) {
                if (this.random.nextDouble() < 0.4) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0, false, false, true));
                }
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 200, 0, false, false, true));
            } else if (data.fatigueScore >= 75.0) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 160, 0, false, false, true));
            }
            if (data.fatigueScore >= 50.0 && data.fatigueScore < 60.0) {
                String fatigueMsg = this.config.getString("fatigue.message", "&c&oGetting tired...");
                TextUtil.sendActionBar(player, fatigueMsg);
            }
            if (data.fatigueScore >= 50.0) {
                double slipChance = ((data.fatigueScore - 50.0) / 50.0) * 0.70;
                if (this.random.nextDouble() < slipChance) {
                    TextUtil.sendActionBar(player, "&c[!] The fish slipped off your hook due to extreme fatigue!");
                    player.playSound(player.getLocation(), Sound.ENTITY_FISH_SWIM, 1.0f, 0.8f);
                    if (event.getCaught() != null) {
                        event.getCaught().remove();
                    }
                    return;
                }
            }
            double streakBonus = Math.pow(streak, 0.6) * 0.045;
            ItemStack rod = player.getInventory().getItemInMainHand();
            if (rod == null || rod.getType() != Material.FISHING_ROD) {
                rod = player.getInventory().getItemInOffHand();
            }
            int lureLvl = this.enchantManager.getEnchantLevel(rod, FishingEnchantManager.CustomEnchant.LURE_OF_THE_DEEP);
            int heavyLvl = this.enchantManager.getEnchantLevel(rod, FishingEnchantManager.CustomEnchant.HEAVYWEIGHT);
            int doubleHookLvl = this.enchantManager.getEnchantLevel(rod, FishingEnchantManager.CustomEnchant.DOUBLE_HOOK);
            int treasureLvl = this.enchantManager.getEnchantLevel(rod, FishingEnchantManager.CustomEnchant.TREASURE_HUNTER);
            double tierMult = this.enchantManager.getRodTier(rod).getEnchantMultiplier();
            BaitManager.ActiveBait activeBait = this.baitManager != null ? this.baitManager.getActiveBait(uuid) : null;
            double baitCatchBonus = activeBait != null ? activeBait.catchChanceBonus : 0.0;
            double baitWeightBonus = activeBait != null ? activeBait.weightBonus : 0.0;
            int luck = rod != null ? rod.getEnchantmentLevel(Enchantment.LUCK_OF_THE_SEA) : 0;
            double mult = (1.0 + (double)luck * 0.15 + streakBonus + baitCatchBonus) * fatigueMult;
            if (mult > (multCap = this.config.getDouble("streak.multiplier-cap", 5.0))) {
                mult = multCap;
            }
            this.sendFishingActionBar(player);
            boolean hasCompassOrTracker = false;
            if (this.legendaryQuestManager != null && this.legendaryQuestManager.getStage(player.getUniqueId()) > 0) {
                hasCompassOrTracker = true;
            }
            if (!hasCompassOrTracker) {
                NamespacedKey cKey = new NamespacedKey((Plugin)this.plugin, "legendary_compass");
                NamespacedKey tKey = new NamespacedKey((Plugin)this.plugin, "legendary_tracker");
                for (ItemStack invItem : player.getInventory().getContents()) {
                    PersistentDataContainer pdc;
                    if (invItem == null || !invItem.hasItemMeta() || !(pdc = invItem.getItemMeta().getPersistentDataContainer()).has(cKey, PersistentDataType.STRING) && !pdc.has(tKey, PersistentDataType.STRING)) continue;
                    hasCompassOrTracker = true;
                    break;
                }
            }
            if (!hasCompassOrTracker) {
                double baseDropChance = this.config.getDouble("legendary-quest.map-drop-chance", 1.0E-4);
                double bonusChance = 0.0;
                if (this.enchantManager != null) {
                    int thLevel = this.enchantManager.getEnchantLevel(player.getInventory().getItemInMainHand(), FishingEnchantManager.CustomEnchant.TREASURE_HUNTER);
                    bonusChance += (double)thLevel * 1.0E-4;
                }
                double finalChance = baseDropChance + bonusChance;
                finalChance = Math.min(finalChance, 0.001);
                boolean gotMap = false;
                int pityThreshold = this.config.getInt("legendary-quest.pity-threshold", 2500);
                if (this.random.nextDouble() <= finalChance) {
                    gotMap = true;
                } else if (data.legendaryPity >= pityThreshold) {
                    gotMap = true;
                }
                if (gotMap && this.legendaryQuestManager != null) {
                    data.legendaryPity = 0;
                    this.legendaryQuestManager.giveMap(player);
                    if (event.getCaught() != null) {
                        event.getCaught().remove();
                    }
                    return;
                }
                if (fatiguePercent < 0.75) {
                    ++data.legendaryPity;
                }
            } else if (fatiguePercent < 0.75) {
                ++data.legendaryPity;
            }

            // 4. Roll Treasure / Rewards (Single Weighted Roll, MMO Balanced)
            double baseTreasureChance = this.config.getDouble("treasure.base-chance", 2.0);
            double enchantBonus = ((double)treasureLvl * 0.35 * (0.8 + 0.2 * tierMult)) + ((double)luck * 0.20) + Math.min(0.25, streakBonus * 0.5);
            double finalTreasureChance = (baseTreasureChance + enchantBonus) * fatigueMult;
            if (inPond) {
                double pondMult = this.config.getDouble("treasure.pond-multiplier", 1.5);
                finalTreasureChance *= pondMult;
            }
            if (eventRewardMult > 1.0) {
                finalTreasureChance *= eventRewardMult;
            }
            finalTreasureChance = Math.min(finalTreasureChance, 10.0);

            if ((inPond || event.getHook() == null || this.isOpenWater(event.getHook().getLocation()))
                    && this.random.nextDouble() * 100.0 <= finalTreasureChance) {
                List rawRewards = this.config.getStringList("rewards");
                List<CrateReward> validRewards = new ArrayList<>();
                double totalRewardWeight = 0.0;
                if (rawRewards != null) {
                    for (String rewardStr : (java.util.List<String>)(java.util.List<?>)rawRewards) {
                        String cleanStr = rewardStr.replace("\r", "").replace("\n", " ").replaceAll("\\s+", " ").trim();
                        String[] parts = cleanStr.split("\\|", -1);
                        if (parts.length < 4) continue;
                        try {
                            double w = Double.parseDouble(parts[0]);
                            String r = parts[1];
                            String name = parts[2];
                            String soundStr = parts[3];
                            String cmd = parts.length > 4 ? parts[4] : "";
                            if (r.equalsIgnoreCase("junk") || name.toLowerCase().contains("boot")) continue;
                            validRewards.add(new CrateReward(w, r, name, soundStr, cmd));
                            totalRewardWeight += w;
                        } catch (NumberFormatException ignored) {}
                    }
                }

                if (!validRewards.isEmpty() && totalRewardWeight > 0.0) {
                    double roll = this.random.nextDouble() * totalRewardWeight;
                    double cumulative = 0.0;
                    CrateReward chosenReward = validRewards.get(validRewards.size() - 1);
                    for (CrateReward cr : validRewards) {
                        cumulative += cr.weight;
                        if (roll <= cumulative) {
                            chosenReward = cr;
                            break;
                        }
                    }

                    if (!chosenReward.command.isEmpty()) {
                        String parsedCmd = chosenReward.command.replace("{player}", player.getName());
                        Bukkit.dispatchCommand((CommandSender)Bukkit.getConsoleSender(), parsedCmd);
                    }
                    try {
                        Sound sound = net.rocky.coresuite.common.compat.PaperCompat.sound(chosenReward.sound.toUpperCase().replace(".", "_"));
                        player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
                    } catch (IllegalArgumentException ignored) {}

                    String prefix = this.config.getString("prefix", "&8[&b&lFishing&8] &r");
                    if (chosenReward.rarity.equalsIgnoreCase("legendary") || chosenReward.rarity.equalsIgnoreCase("mythical")) {
                        TextUtil.sendTitle(player, "&d&l✦ Legendary Treasure ✦", chosenReward.name + "&7!", 10, 100, 20);
                        Bukkit.broadcast((Component)TextUtil.colorize(prefix + "&f" + player.getName() + " &7reeled in a " + chosenReward.name + "&7 from the deep! &8(&d" + chosenReward.rarity + "&8)"));
                    } else if (chosenReward.rarity.equalsIgnoreCase("epic")) {
                        TextUtil.sendTitle(player, "&5&l✦ Epic Treasure ✦", chosenReward.name + "&7!", 10, 80, 20);
                    } else {
                        TextUtil.sendTitle(player, "&a&l✦ Reward Found ✦", chosenReward.name + "&7!", 10, 80, 20);
                    }
                    if (event.getCaught() != null) {
                        event.getCaught().remove();
                    }
                    return;
                }
            }

            // 5. Junk Roll (Old Boot) - only in open water/wild, never in pond, suppressed to 0 with good rods
            double junkChance = 0.0;
            if (!inPond) {
                double baseJunkChance = this.config.getDouble("junk.base-chance", 4.0);
                double junkReduction = ((double)luck * 1.5) + ((double)treasureLvl * 1.5) + ((tierMult - 1.0) * 5.0);
                junkChance = Math.max(0.0, baseJunkChance - junkReduction);
            }

            if (junkChance > 0.0 && this.random.nextDouble() * 100.0 <= junkChance) {
                ItemStack junkItem = new ItemStack(Material.LEATHER, 1);
                ItemMeta meta = junkItem.getItemMeta();
                if (meta != null) {
                    meta.displayName(TextUtil.colorize("&8Old Boot"));
                    meta.lore(Arrays.asList(
                        TextUtil.colorize("&7A waterlogged, muddy boot dredged from the deep."),
                        TextUtil.colorize("&8[Junk Item - Sell to Fishmonger]"),
                        TextUtil.colorize("&7Value: &a" + this.plugin.getEconomyManager().format(5.0))
                    ));
                    meta.getPersistentDataContainer().set(this.speciesKey, PersistentDataType.STRING, "old_boot");
                    meta.getPersistentDataContainer().set(this.weightKey, PersistentDataType.DOUBLE, 1.0);
                    meta.getPersistentDataContainer().set(this.priceKey, PersistentDataType.DOUBLE, 5.0);
                    junkItem.setItemMeta(meta);
                }
                player.getInventory().addItem(junkItem);
                String catchMsg = this.config.getString("messages.junk-catch-message", "&7You caught an &8Old Boot&7... not exactly treasure.");
                TextUtil.sendActionBar(player, catchMsg.replace("{name}", "Old Boot"));
                player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 0.8f);
                if (event.getCaught() != null) {
                    event.getCaught().remove();
                }
                return;
            }

            // 6. Roll and Generate Custom Fish Species
            if (this.config.getConfigurationSection("species") != null) {
                Biome hookBiome = event.getHook() != null ? event.getHook().getLocation().getBlock().getBiome() : null;

                Map<String, List<String>> biomeSpeciesByRarity = new HashMap<>();
                Map<String, List<String>> globalSpeciesByRarity = new HashMap<>();
                for (String key : this.config.getConfigurationSection("species").getKeys(false)) {
                    String spPath = "species." + key + ".";
                    String r = this.config.getString(spPath + "rarity", "Common").toLowerCase();
                    globalSpeciesByRarity.computeIfAbsent(r, k -> new ArrayList<>()).add(key);

                    List<String> biomes = this.config.getStringList(spPath + "biomes");
                    boolean matchesBiome = true;
                    if (!inPond && biomes != null && !biomes.isEmpty() && hookBiome != null) {
                        matchesBiome = false;
                        for (String b2 : biomes) {
                            if (((org.bukkit.Keyed) hookBiome).getKey().getKey().equalsIgnoreCase(b2)) {
                                matchesBiome = true;
                                break;
                            }
                        }
                    }
                    if (matchesBiome) {
                        biomeSpeciesByRarity.computeIfAbsent(r, k -> new ArrayList<>()).add(key);
                    }
                }

                // Stage 1: Balanced MMO relative weights (Hypixel / OSRS style rarity distribution)
                double mythicalWeight = 0.10;
                double legendaryWeight = 1.20;
                double epicWeight = 5.00;
                double rareWeight = 16.00;
                double uncommonWeight = 32.00;
                double commonWeight = 55.00;

                double gearBoost = ((double)luck * 0.06)
                        + ((double)lureLvl * 0.07 * tierMult)
                        + (Math.sqrt(Math.max(0, (double)streak)) * 0.03)
                        + (baitCatchBonus * 0.30);

                if (activeBait != null && activeBait.rarityBias != null && !activeBait.rarityBias.isEmpty()) {
                    String bias = activeBait.rarityBias.toLowerCase();
                    if (bias.equals("mythical")) {
                        mythicalWeight *= 1.8;
                        legendaryWeight *= 1.4;
                    } else if (bias.equals("legendary")) {
                        legendaryWeight *= 1.8;
                        epicWeight *= 1.3;
                    } else if (bias.equals("epic")) {
                        epicWeight *= 1.6;
                        legendaryWeight *= 1.2;
                    } else if (bias.equals("rare")) {
                        rareWeight *= 1.5;
                        epicWeight *= 1.2;
                    }
                }

                // Gear boost scaling with diminishing returns
                mythicalWeight *= (1.0 + gearBoost * 1.2);
                legendaryWeight *= (1.0 + gearBoost * 1.0);
                epicWeight *= (1.0 + gearBoost * 0.8);
                rareWeight *= (1.0 + gearBoost * 0.5);

                // Common and uncommon gently compressed (never eliminated)
                commonWeight /= (1.0 + gearBoost * 0.45);
                uncommonWeight /= (1.0 + gearBoost * 0.15);

                // Pond bonus: upper tiers boosted, common reduced by 35% (preserving ~25% common in pond)
                if (inPond) {
                    mythicalWeight *= 2.0;
                    legendaryWeight *= 2.0;
                    epicWeight *= 1.8;
                    rareWeight *= 1.5;
                    commonWeight *= 0.65;
                    uncommonWeight *= 0.85;
                }

                // Fatigue degradation
                if (data.fatigueScore >= 80.0) {
                    mythicalWeight = 0.0;
                    legendaryWeight = 0.0;
                    epicWeight = 0.0;
                } else if (data.fatigueScore >= 50.0) {
                    double scale = 1.0 - ((data.fatigueScore - 50.0) / 30.0);
                    mythicalWeight *= Math.max(0.0, scale);
                    legendaryWeight *= Math.max(0.0, scale);
                    epicWeight *= Math.max(0.0, 0.3 + 0.7 * scale);
                }

                // Dynamic normalized relative roll
                double totalWeight = mythicalWeight + legendaryWeight + epicWeight + rareWeight + uncommonWeight + commonWeight;
                double roll = this.random.nextDouble() * totalWeight;
                String targetRarity;
                double cumulative = 0.0;
                if (roll < (cumulative += mythicalWeight)) {
                    targetRarity = "mythical";
                } else if (roll < (cumulative += legendaryWeight)) {
                    targetRarity = "legendary";
                } else if (roll < (cumulative += epicWeight)) {
                    targetRarity = "epic";
                } else if (roll < (cumulative += rareWeight)) {
                    targetRarity = "rare";
                } else if (roll < (cumulative += uncommonWeight)) {
                    targetRarity = "uncommon";
                } else {
                    targetRarity = "common";
                }

                // Stage 2: Pick species in target rarity, with graceful fallback ladder (downgrade only)
                List<String> candidates = biomeSpeciesByRarity.get(targetRarity);
                if (candidates == null || candidates.isEmpty()) {
                    candidates = globalSpeciesByRarity.get(targetRarity);
                }
                if (candidates == null || candidates.isEmpty()) {
                    String[] fallbackHierarchy = new String[]{"mythical", "legendary", "epic", "rare", "uncommon", "common"};
                    int startIdx = 0;
                    for (int i = 0; i < fallbackHierarchy.length; i++) {
                        if (fallbackHierarchy[i].equals(targetRarity)) {
                            startIdx = i;
                            break;
                        }
                    }
                    for (int i = startIdx + 1; i < fallbackHierarchy.length; i++) {
                        String lowerRarity = fallbackHierarchy[i];
                        candidates = biomeSpeciesByRarity.get(lowerRarity);
                        if (candidates == null || candidates.isEmpty()) {
                            candidates = globalSpeciesByRarity.get(lowerRarity);
                        }
                        if (candidates != null && !candidates.isEmpty()) break;
                    }
                }
                if (candidates == null || candidates.isEmpty()) {
                    candidates = globalSpeciesByRarity.get("common");
                }
                if (candidates == null || candidates.isEmpty()) {
                    candidates = new ArrayList<>(this.config.getConfigurationSection("species").getKeys(false));
                }

                if (!candidates.isEmpty()) {
                    String selectedKey = candidates.get(this.random.nextInt(candidates.size()));
                    String spPath = "species." + selectedKey + ".";
                    try {
                        ItemStack[] bag;
                        String rarity = this.config.getString(spPath + "rarity", "Common");
                        String name = this.config.getString(spPath + "name", selectedKey);
                        if (name == null || name.isEmpty()) {
                            name = selectedKey;
                        }
                        if (!name.contains("&")) {
                            String color = "&7";
                            switch (rarity.toLowerCase()) {
                                case "uncommon": color = "&a"; break;
                                case "rare": color = "&9"; break;
                                case "epic": color = "&5"; break;
                                case "legendary": color = "&6"; break;
                                case "mythical": color = "&c&l"; break;
                            }
                            name = color + name;
                        }
                        double minWeight = this.config.getDouble(spPath + "min-weight", 1.0);
                        double maxWeight = this.config.getDouble(spPath + "max-weight", 10.0);
                        double pricePerKg = this.config.getDouble(spPath + "price-per-kg", 10.0);
                        String material = this.config.getString(spPath + "material", "COD");
                        String itemModel = this.config.getString(spPath + "item-model", "");
                        int cmd = this.config.getInt(spPath + "custom-model-data", -1);

                        double weight = minWeight + (maxWeight - minWeight) * this.random.nextDouble();
                        weight *= (1.0 + (double)heavyLvl * 0.05 * tierMult + baitWeightBonus) * eventWeightMult;
                        weight = (double)Math.round(weight * 100.0) / 100.0;

                        double addFatigue = 0.0;
                        if (!inPond) {
                            switch (rarity.toLowerCase()) {
                                case "uncommon": addFatigue = 1.0; break;
                                case "rare": addFatigue = 2.5; break;
                                case "epic": addFatigue = 5.0; break;
                                case "legendary": addFatigue = 15.0; break;
                                case "mythical": addFatigue = 30.0; break;
                                default: addFatigue = 0.5; break;
                            }
                            addFatigue += weight * 0.02;
                            int tirelessLvl = this.enchantManager.getEnchantLevel(rod, FishingEnchantManager.CustomEnchant.TIRELESS_CASTER);
                            if (tirelessLvl > 0) {
                                double reduction = (double)tirelessLvl * 0.25;
                                addFatigue *= 1.0 - reduction;
                            }
                            double oldFatigue = data.fatigueScore;
                            data.fatigueScore = Math.min(100.0, data.fatigueScore + addFatigue);
                            addFatigue = data.fatigueScore - oldFatigue;
                        } else {
                            double healAmount = this.config.getDouble("pond-event.fatigue-heal-amount", 5.0);
                            double oldScore = data.fatigueScore;
                            data.fatigueScore = Math.max(0.0, data.fatigueScore - healAmount);
                            addFatigue = -(oldScore - data.fatigueScore);
                        }
                        data.fatigueLastUpdate = System.currentTimeMillis();

                        ItemStack fishItem = this.createFishItem(name, material, weight, pricePerKg, itemModel);

                        // Double Hook Safeguard
                        int amount = 1;
                        boolean highTierDoubleHook = false;
                        if (doubleHookLvl > 0 && this.random.nextDouble() * 100.0 <= (double)doubleHookLvl * 5.0 * tierMult) {
                            if (rarity.equalsIgnoreCase("epic") || rarity.equalsIgnoreCase("legendary") || rarity.equalsIgnoreCase("mythical")) {
                                highTierDoubleHook = true;
                                player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.5f);
                                TextUtil.sendActionBar(player, "&d&lDouble Hook! &eAwarded bonus Angler XP for " + rarity + " catch!");
                            } else {
                                amount = 2;
                            }
                        }
                        fishItem.setAmount(amount);

                        HashMap leftover = player.getInventory().addItem(new ItemStack[]{fishItem});
                        if (!leftover.isEmpty() && (bag = this.fishBagManager.getBag(player.getUniqueId())) != null) {
                            ItemStack itemToAdd = ((ItemStack)leftover.get(0)).clone();
                            boolean addedToBag = false;
                            FishData bagData = this.playerData.get(player.getUniqueId());
                            int unlockedPages = bagData != null ? Math.max(0, bagData.bagPages) : 0;
                            int maxSlots = Math.min(bag.length, unlockedPages * 45);
                            for (int i = 0; i < maxSlots; ++i) {
                                if (bag[i] != null && bag[i].getType() != Material.AIR) continue;
                                bag[i] = itemToAdd;
                                this.fishBagManager.updateBag(player.getUniqueId(), bag);
                                addedToBag = true;
                                leftover.clear();
                                break;
                            }
                            if (!addedToBag) {
                                for (ItemStack drop : (java.util.Collection<org.bukkit.inventory.ItemStack>)(java.util.Collection<?>)leftover.values()) {
                                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                                }
                                TextUtil.sendMessage((CommandSender)player, "&cYour inventory and bag are full! The fish dropped on the ground!");
                            }
                        }

                        Object fatigueMsg = "";
                        int thresholdCheck = this.config.getInt("fatigue.threshold", 50);
                        if (inPond) {
                            if (addFatigue < 0.0) {
                                fatigueMsg = " &a(" + String.format("%.1f", addFatigue) + "% Fatigue &8| &aTotal: " + String.format("%.1f", data.fatigueScore) + "%&a)";
                            } else {
                                fatigueMsg = " &a(0.0% Fatigue &8| &aWell Rested!)";
                            }
                        } else {
                            fatigueMsg = " &8(&c+" + String.format("%.1f", addFatigue) + "% Fatigue &8| &cTotal: " + String.format("%.1f", data.fatigueScore) + "%&8)";
                        }
                        player.sendMessage(TextUtil.colorize(this.config.getString("prefix", "&8[&b&lFishing&8] &r") + "&7You caught a &e" + name + " &8(&b" + weight + " kg&8)" + (String)fatigueMsg));
                        ++data.totalCatches;

                        if (inPond) {
                            this.actionbarOverride.put(player.getUniqueId(), System.currentTimeMillis() + 3000L);
                            int displayFatigue = (int)Math.round(data.fatigueScore);
                            TextUtil.sendActionBar(player, "&a✦ Legendary Pond Catch! &e(2x Bonus & &b-5% Fatigue &8| &b" + displayFatigue + "%&e)");
                        } else if (data.fatigueScore > (double)thresholdCheck) {
                            this.actionbarOverride.put(player.getUniqueId(), System.currentTimeMillis() + 3000L);
                            TextUtil.sendActionBar(player, "&cYou are too fatigued to gain XP! Rest or find a Legendary Pond.");
                        } else {
                            int earnedXp = this.config.getInt("leveling.xp-per-catch", 1);
                            if (streak > 1 && this.random.nextDouble() * 100.0 <= (double)streak * 5.0) {
                                earnedXp += this.random.nextInt(3) + 1;
                            }
                            if (amount == 2 || highTierDoubleHook) {
                                earnedXp *= 2;
                            }
                            data.xp += earnedXp;
                            int needed = data.level * this.config.getInt("leveling.xp-formula-multiplier", 20);
                            if (data.xp >= needed) {
                                data.xp -= needed;
                                ++data.level;
                                String lvlMsg = this.config.getString("messages.level-up", "&e&lLevel Up! &7You are now Angler Level &b{level}");
                                player.sendMessage(TextUtil.colorize(lvlMsg.replace("{level}", String.valueOf(data.level))));
                                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                            }
                        }
                        if (event.getCaught() != null) {
                            event.getCaught().remove();
                        }
                        if (rarity.equalsIgnoreCase("mythical")) {
                            TextUtil.sendTitle(player, "&c&l✦ MYTHICAL CATCH ✦", name + " &8(&b" + weight + "kg&8)", 10, 80, 20);
                            Bukkit.broadcast(TextUtil.colorize(this.config.getString("prefix", "&8[&b&lFishing&8] &r") + "&c&l✦ &f" + player.getName() + " &7just reeled in a Mythical &e" + name + " &8(&b" + weight + " kg&8)&7!"));
                            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                        } else if (rarity.equalsIgnoreCase("legendary")) {
                            TextUtil.sendTitle(player, "&6&l✦ LEGENDARY CATCH ✦", name + " &8(&b" + weight + "kg&8)", 10, 70, 20);
                            Bukkit.broadcast(TextUtil.colorize(this.config.getString("prefix", "&8[&b&lFishing&8] &r") + "&6✦ &f" + player.getName() + " &7reeled in a Legendary &e" + name + " &8(&b" + weight + " kg&8)&7!"));
                            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
                        } else if (weight > maxWeight * 0.75) {
                            TextUtil.sendTitle(player, "&b&lHeavy Catch!", name + " &7- &b" + weight + "kg", 10, 60, 20);
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                        } else {
                            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                        }
                        String speciesId = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&', name));
                        this.checkAndUpdatePersonalBest(player, speciesId, weight);
                        this.leaderboardManager.checkServerRecord(player, speciesId, weight, name);
                        if (this.tournamentManager != null) {
                            this.tournamentManager.recordCatch(player, weight);
                        }
                        if (this.baitManager != null && activeBait != null) {
                            this.baitManager.consumeCast(player);
                        }
                        this.saveDataAsync(uuid);
                        this.playerData.put(uuid, data);
                        return;
                    }
                    catch (Exception ex) {
                        this.plugin.getLogger().warning("Error catching custom fish: " + ex.getMessage());
                        ex.printStackTrace();
                        if (event.getCaught() != null) {
                            event.getCaught().remove();
                        }
                    }
                }
            }
            if (event.getCaught() != null) {
                event.getCaught().remove();
            }
            this.saveDataAsync(uuid);
            this.playerData.put(uuid, data);
        }
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("fish")) {
            if (!(sender instanceof Player)) {
                return true;
            }
            Player player = (Player)sender;
            if (!playerData.containsKey(player.getUniqueId()) || fishBagManager.getBag(player.getUniqueId()) == null) {
                TextUtil.sendMessage(player, "&cYour fishing data is not available yet. Please try again shortly.");
                return true;
            }
            if (args.length > 0) {
                if (args[0].equalsIgnoreCase("quest")) {
                    if (args.length < 2) {
                        TextUtil.sendMessage((CommandSender)player, "&cUsage: /fish quest <1|2|3|cancel>");
                        return true;
                    }
                    if (this.legendaryQuestManager != null && this.legendaryQuestManager.isInChatState(player.getUniqueId())) {
                        this.legendaryQuestManager.processChatSync(player, player.getUniqueId(), args[1]);
                    } else {
                        TextUtil.sendMessage((CommandSender)player, "&cYou are not in an active dialogue with the Ancient Angler.");
                    }
                    return true;
                }
                if (args[0].equalsIgnoreCase("top")) {
                    this.openTopGUI(player);
                    return true;
                }
                if (args[0].equalsIgnoreCase("sell")) {
                    this.openSellGUI(player, 0);
                    return true;
                }
                if (args[0].equalsIgnoreCase("pond")) {
                    if (this.eventManager != null) {
                        if (this.eventManager.getActivePond() != null) {
                            Location loc = this.eventManager.getActivePond();
                            TextUtil.sendMessage((CommandSender)player, this.config.getString("prefix", "&8[&b&lFishing&8] &r") + "&bThe Legendary Fish Pond is currently active at &e" + loc.getBlockX() + ", " + loc.getBlockZ() + "&b!");
                        } else if (this.eventManager.getFormingPond() != null) {
                            Location loc = this.eventManager.getFormingPond();
                            TextUtil.sendMessage((CommandSender)player, this.config.getString("prefix", "&8[&b&lFishing&8] &r") + "&eA Legendary Fish Pond is currently &6forming &eat &b" + loc.getBlockX() + ", " + loc.getBlockZ() + "&e!");
                        } else {
                            TextUtil.sendMessage((CommandSender)player, this.config.getString("prefix", "&8[&b&lFishing&8] &r") + "&cThere is no Legendary Fish Pond active right now.");
                        }
                    } else {
                        TextUtil.sendMessage((CommandSender)player, this.config.getString("prefix", "&8[&b&lFishing&8] &r") + "&cThe Legendary Fish Pond event is currently disabled.");
                    }
                    return true;
                }
                if (args[0].equalsIgnoreCase("fatigue")) {
                    FishData data = this.playerData.get(player.getUniqueId());
                    if (data == null) {
                        return true;
                    }
                    this.calculateFatigue(player, data);
                    int display = (int)Math.round(data.fatigueScore);
                    if (display <= 0) {
                        TextUtil.sendMessage((CommandSender)player, this.config.getString("prefix", "&8[&b&lFishing&8] &r") + "&aYou are fully rested and have no fishing fatigue.");
                    } else {
                        String color = display >= 85 ? "&4" : (display >= 75 ? "&c" : (display >= 50 ? "&6" : "&e"));
                        TextUtil.sendMessage((CommandSender)player, this.config.getString("prefix", "&8[&b&lFishing&8] &r") + "&7Current Fatigue: " + color + display + "%");
                        if (display >= 85) {
                            TextUtil.sendMessage((CommandSender)player, this.config.getString("prefix", "&8[&b&lFishing&8] &r") + "&7&o(You are experiencing severe blackouts)");
                        } else if (display >= 75) {
                            TextUtil.sendMessage((CommandSender)player, this.config.getString("prefix", "&8[&b&lFishing&8] &r") + "&7&o(You are experiencing sea sickness)");
                        }
                        if (display >= 80) {
                            TextUtil.sendMessage((CommandSender)player, this.config.getString("prefix", "&8[&b&lFishing&8] &r") + "&c&o(Legendary & Mythical catches are locked out!)");
                        } else if (display >= 50) {
                            TextUtil.sendMessage((CommandSender)player, this.config.getString("prefix", "&8[&b&lFishing&8] &r") + "&e&o(Fish may slip off the hook due to tiredness)");
                        }
                    }
                    return true;
                }
                if (args[0].equalsIgnoreCase("tournament") || args[0].equalsIgnoreCase("tourney")) {
                    if (this.tournamentManager != null) {
                        this.tournamentManager.openTournamentGUI(player);
                    }
                    return true;
                }
                if (args[0].equalsIgnoreCase("bait")) {
                    if (this.baitManager != null) {
                        this.baitManager.openBaitShopGUI(player);
                    }
                    return true;
                }
                if (args[0].equalsIgnoreCase("hof") || args[0].equalsIgnoreCase("halloffame")) {
                    if (this.leaderboardManager != null) {
                        this.leaderboardManager.openHallOfFameGUI(player);
                    }
                    return true;
                }
                if (args[0].equalsIgnoreCase("buybag")) {
                    int reqLevel;
                    int currentPages;
                    FishData bData = this.playerData.get(player.getUniqueId());
                    int n = currentPages = bData != null ? bData.bagPages : 0;
                    if (currentPages >= 10) {
                        TextUtil.sendMessage((CommandSender)player, "&cYou have already reached the maximum amount of bag pages.");
                        return true;
                    }
                    int nextPage = currentPages + 1;
                    double cost = this.config.getDouble("bag-pages." + nextPage + ".price", -1.0);
                    if (cost == -1.0) {
                        cost = 10000.0 + (double)nextPage * 15000.0;
                    }
                    if ((reqLevel = this.config.getInt("bag-pages." + nextPage + ".level-required", -1)) == -1) {
                        reqLevel = nextPage * 5;
                    }
                    if (nextPage > 1 && bData != null && bData.level < reqLevel) {
                        TextUtil.sendMessage((CommandSender)player, "&cYou need Fishing Level &a" + reqLevel + " &cto purchase this page.");
                        return true;
                    }
                    double bal = this.plugin.getEconomyManager().getBalance((OfflinePlayer)player);
                    if (bal < cost) {
                        TextUtil.sendMessage((CommandSender)player, "&cYou do not have enough money. Cost: &a" + this.plugin.getEconomyManager().format(cost));
                        return true;
                    }
                    if (!this.plugin.getEconomyManager().withdraw(player, cost)) {
                        TextUtil.sendMessage(player, "&cPayment failed. Nothing was purchased.");
                        return true;
                    }
                    if (bData == null) {
                        bData = new FishData(1, 0, 0, 0);
                    }
                    bData.bagPages = nextPage;
                    this.playerData.put(player.getUniqueId(), bData);
                    TextUtil.sendMessage((CommandSender)player, "&aSuccessfully purchased Fish Bag Page " + nextPage + " for &e" + this.plugin.getEconomyManager().format(cost) + "!");
                    player.closeInventory();
                    return true;
                }
                if (args[0].equalsIgnoreCase("bag")) {
                    FishData bData = this.playerData.get(player.getUniqueId());
                    if (bData == null || bData.bagPages <= 0) {
                        this.openBuyBagGUI(player);
                        return true;
                    }
                    this.fishBagManager.openBag(player, bData.bagPages, 0);
                    return true;
                }
                if (args[0].equalsIgnoreCase("info")) {
                    this.collectionManager.openCollectionGUI(player);
                    return true;
                }
                if (args[0].equalsIgnoreCase("log")) {
                    this.collectionManager.openCollectionGUI(player);
                    return true;
                }
                if (args[0].equalsIgnoreCase("shop")) {
                    this.openShopGUI(player);
                    return true;
                }
                if (args[0].equalsIgnoreCase("admin")) {
                    if (!player.hasPermission("coresuite.customfishing.admin")) {
                        TextUtil.sendMessage((CommandSender)player, "&cNo permission.");
                        return true;
                    }
                    if (args.length < 2) {
                        TextUtil.sendMessage((CommandSender)player, "&cUsage: /fish admin <setlevel|addxp|fatigue|setcatches|clearlog|forcepond|give> [player] [args]");
                        return true;
                    }
                    String sub = args[1].toLowerCase();
                    if (sub.equals("legendary")) {
                        if (args.length > 2 && args[2].equalsIgnoreCase("givemap")) {
                            Player target;
                            Player player2 = target = args.length > 3 ? Bukkit.getPlayer((String)args[3]) : player;
                            if (target != null && this.legendaryQuestManager != null) {
                                this.legendaryQuestManager.giveMap(target);
                                TextUtil.sendMessage((CommandSender)player, "&aGave Legendary Fisherman's Compass to " + target.getName());
                            } else {
                                TextUtil.sendMessage((CommandSender)player, "&cPlayer not found.");
                            }
                        } else if (args.length > 3 && args[2].equalsIgnoreCase("setstage")) {
                            Player target = Bukkit.getPlayer((String)args[3]);
                            if (target != null && this.legendaryQuestManager != null) {
                                try {
                                    int stage = Integer.parseInt(args[4]);
                                    this.legendaryQuestManager.setStage(target.getUniqueId(), stage);
                                    TextUtil.sendMessage((CommandSender)player, "&aSet " + target.getName() + "'s legendary quest stage to " + stage);
                                }
                                catch (Exception e) {
                                    TextUtil.sendMessage((CommandSender)player, "&cUsage: /fish admin legendary setstage <player> <stage>");
                                }
                            } else {
                                TextUtil.sendMessage((CommandSender)player, "&cPlayer not found.");
                            }
                        } else if (args.length > 2 && args[2].equalsIgnoreCase("reset")) {
                            Player target;
                            Player player3 = target = args.length > 3 ? Bukkit.getPlayer((String)args[3]) : player;
                            if (target != null && this.legendaryQuestManager != null) {
                                this.legendaryQuestManager.setStage(target.getUniqueId(), 0);
                                this.legendaryNPCManager.removeNPC(target.getUniqueId());
                                TextUtil.sendMessage((CommandSender)player, "&aReset " + target.getName() + "'s legendary quest progress.");
                            } else {
                                TextUtil.sendMessage((CommandSender)player, "&cPlayer not found.");
                            }
                        } else {
                            TextUtil.sendMessage((CommandSender)player, "&cUsage: /fish admin legendary <givemap|setstage|reset>");
                        }
                        return true;
                    }
                    if (sub.equals("clearanglers")) {
                        if (this.wanderingAnglerManager != null) {
                            int removed = this.wanderingAnglerManager.clearAllAnglers();
                            TextUtil.sendMessage((CommandSender)player, "&aSuccessfully removed &e" + removed + " &aWandering Angler(s) from all loaded chunks.");
                        } else {
                            TextUtil.sendMessage((CommandSender)player, "&cWandering Angler feature is disabled.");
                        }
                        return true;
                    }
                    if (sub.equals("spawnangler")) {
                        if (this.wanderingAnglerManager != null) {
                            this.wanderingAnglerManager.spawnAngler(player.getLocation());
                            TextUtil.sendMessage((CommandSender)player, "&aForce-spawned a Wandering Angler!");
                        } else {
                            TextUtil.sendMessage((CommandSender)player, "&cWandering Angler feature is disabled.");
                        }
                        return true;
                    }
                    if (sub.equals("forcepond")) {
                        if (this.eventManager != null) {
                            this.eventManager.forceStartEvent(player.getLocation());
                            TextUtil.sendMessage((CommandSender)player, "&aForce-started a Legendary Fish Pond near your location!");
                        }
                        return true;
                    }
                    if (args.length < 3) {
                        TextUtil.sendMessage((CommandSender)player, "&cUsage: /fish admin " + sub + " <player> [args]");
                        return true;
                    }
                    Player target = Bukkit.getPlayer((String)args[2]);
                    if (target == null) {
                        TextUtil.sendMessage((CommandSender)player, "&cPlayer not found.");
                        return true;
                    }
                    UUID tUuid = target.getUniqueId();
                    if (sub.equals("clearlog")) {
                        this.plugin.getDatabaseManager().execute("DELETE FROM coresuite_fish_logbook WHERE uuid = ?", tUuid.toString());
                        TextUtil.sendMessage((CommandSender)player, "&aCleared fishing log for " + target.getName() + ".");
                        return true;
                    }
                    if (sub.equals("givebait")) {
                        int giveAmount;
                        if (args.length < 4) {
                            TextUtil.sendMessage((CommandSender)player, "&cUsage: /fish admin givebait <player> <bait_id> [amount]");
                            return true;
                        }
                        String baitId = args[3].toLowerCase();
                        int n = giveAmount = args.length > 4 ? Integer.parseInt(args[4]) : 1;
                        if (this.baitManager != null) {
                            ItemStack baitItem = this.baitManager.createBaitItem(baitId);
                            if (baitItem != null) {
                                baitItem.setAmount(giveAmount);
                                target.getInventory().addItem(new ItemStack[]{baitItem});
                                TextUtil.sendMessage((CommandSender)player, "&aGave " + giveAmount + "x " + baitId + " to " + target.getName() + ".");
                            } else {
                                TextUtil.sendMessage((CommandSender)player, "&cBait ID '" + baitId + "' not found.");
                            }
                        }
                        return true;
                    }
                    if (sub.equals("give")) {
                        if (args.length < 4) {
                            TextUtil.sendMessage((CommandSender)player, "&cUsage: /fish admin give <player> <fish_id> [amount]");
                            return true;
                        }
                        String fishId = args[3];
                        int giveAmount = args.length > 4 ? Integer.parseInt(args[4]) : 1;
                        String spPath = "species." + fishId + ".";
                        if (this.config.getConfigurationSection(spPath) == null) {
                            TextUtil.sendMessage((CommandSender)player, "&cFish '" + fishId + "' not found.");
                            return true;
                        }
                        String name = this.config.getString(spPath + "name");
                        double minWeight = this.config.getDouble(spPath + "min-weight");
                        double maxWeight = this.config.getDouble(spPath + "max-weight");
                        double pricePerKg = this.config.getDouble(spPath + "price-per-kg");
                        String material = this.config.getString(spPath + "material", "COD");
                        String itemModel = this.config.getString(spPath + "item-model", "");
                        double weight = minWeight + (maxWeight - minWeight) * this.random.nextDouble();
                        weight = (double)Math.round(weight * 100.0) / 100.0;
                        ItemStack fishItem = this.createFishItem(name, material, weight, pricePerKg, itemModel);
                        fishItem.setAmount(giveAmount);
                        HashMap leftovers = target.getInventory().addItem(new ItemStack[]{fishItem});
                        for (ItemStack drop : (java.util.Collection<org.bukkit.inventory.ItemStack>)(java.util.Collection<?>)leftovers.values()) {
                            target.getWorld().dropItemNaturally(target.getLocation(), drop);
                        }
                        if (!leftovers.isEmpty()) {
                            TextUtil.sendMessage((CommandSender)target, "&cYour inventory was full, so some fish dropped on the floor!");
                        }
                        TextUtil.sendMessage((CommandSender)player, "&aGave " + giveAmount + "x " + fishId + " to " + target.getName() + ".");
                        return true;
                    }
                    if (args.length < 4) {
                        TextUtil.sendMessage((CommandSender)player, "&cUsage: /fish admin " + sub + " <player> <amount>");
                        return true;
                    }
                    int amount = 0;
                    try {
                        amount = Integer.parseInt(args[3]);
                    }
                    catch (NumberFormatException e) {
                        TextUtil.sendMessage((CommandSender)player, "&cInvalid number.");
                        return true;
                    }
                    FishData tData = this.playerData.getOrDefault(tUuid, new FishData(1, 0, 0, 0));
                    if (sub.equals("setlevel")) {
                        tData.level = amount;
                        this.playerData.put(tUuid, tData);
                        this.saveDataAsync(tUuid);
                        TextUtil.sendMessage((CommandSender)player, "&aSet " + target.getName() + "'s fishing level to " + amount + ".");
                    } else if (sub.equals("addxp")) {
                        tData.xp += amount;
                        this.playerData.put(tUuid, tData);
                        this.saveDataAsync(tUuid);
                        TextUtil.sendMessage((CommandSender)player, "&aAdded " + amount + " XP to " + target.getName() + ".");
                    } else if (sub.equals("fatigue")) {
                        tData.fatigueScore = Math.max(0.0, Math.min(100.0, (double)amount));
                        this.playerData.put(tUuid, tData);
                        this.saveDataAsync(tUuid);
                        TextUtil.sendMessage((CommandSender)player, "&aSet " + target.getName() + "'s fatigue to " + amount + "%.");
                    } else if (sub.equals("setcatches")) {
                        tData.totalCatches = Math.max(0, amount);
                        this.playerData.put(tUuid, tData);
                        this.saveDataAsync(tUuid);
                        TextUtil.sendMessage((CommandSender)player, "&aSet " + target.getName() + "'s total fish catches to " + tData.totalCatches + ".");
                    } else {
                        TextUtil.sendMessage((CommandSender)player, "&cUnknown admin action.");
                    }
                    return true;
                }
                if (args[0].equalsIgnoreCase("debugfish")) {
                    if (!player.hasPermission("coresuite.customfishing.admin")) {
                        TextUtil.sendMessage((CommandSender)player, "&cNo permission.");
                        return true;
                    }
                    if (args.length < 2) {
                        TextUtil.sendMessage((CommandSender)player, "&cUsage: /fish debugfish <material> - e.g. /fish debugfish COD");
                        return true;
                    }
                    String material = args[1];
                    ItemStack fishItem = this.createFishItem("&aDebug Fish", material, 5.0, 10.0);
                    player.getInventory().addItem(new ItemStack[]{fishItem});
                    TextUtil.sendMessage((CommandSender)player, "&aGiven debug fish item (" + material + "). Check your inventory!");
                    return true;
                }
            }
            FishData data = this.playerData.get(player.getUniqueId());
            String prefix = this.config.getString("prefix", "&8[&b&lFishing&8] &r");
            if (data == null || data.totalCatches == 0) {
                TextUtil.sendMessage((CommandSender)player, prefix + "&7You haven't started fishing yet - go catch something!");
                return true;
            }
            this.openMainMenu(player, data);
            return true;
        }
        return false;
    }

    private void sendDetailedStats(Player player, FishData data) {
        double multCap;
        ItemStack rod = player.getInventory().getItemInMainHand();
        if (rod == null || rod.getType() != Material.FISHING_ROD) {
            TextUtil.sendMessage((CommandSender)player, this.config.getString("prefix", "&8[&b&lFishing&8] &r") + "&cYou must be holding a Fishing Rod to view detailed stats!");
            return;
        }
        double fatiguePercent = this.calculateFatigue(player, data);
        double fatigueMult = 1.0 - fatiguePercent;
        int luck = rod.getEnchantmentLevel(Enchantment.LUCK_OF_THE_SEA);
        BaitManager.ActiveBait activeBait = this.baitManager != null ? this.baitManager.getActiveBait(player.getUniqueId()) : null;
        double baitCatchBonus = activeBait != null ? activeBait.catchChanceBonus : 0.0;
        double baitWeightBonus = activeBait != null ? activeBait.weightBonus : 0.0;
        int streak = this.fishStreak.getOrDefault(player.getUniqueId(), 0);
        double streakBonus = Math.pow(streak, 0.6) * 0.045;
        double baseCatchMult = (1.0 + (double)luck * 0.15 + streakBonus + baitCatchBonus) * fatigueMult;
        if (baseCatchMult > (multCap = this.config.getDouble("streak.multiplier-cap", 5.0))) {
            baseCatchMult = multCap;
        }
        double tierMult = 1.0;
        int lureLvl = 0;
        int heavyLvl = 0;
        int doubleHookLvl = 0;
        int treasureLvl = 0;
        if (this.enchantManager != null) {
            tierMult = this.enchantManager.getRodTier(rod).getEnchantMultiplier();
            lureLvl = this.enchantManager.getEnchantLevel(rod, FishingEnchantManager.CustomEnchant.LURE_OF_THE_DEEP);
            heavyLvl = this.enchantManager.getEnchantLevel(rod, FishingEnchantManager.CustomEnchant.HEAVYWEIGHT);
            doubleHookLvl = this.enchantManager.getEnchantLevel(rod, FishingEnchantManager.CustomEnchant.DOUBLE_HOOK);
            treasureLvl = this.enchantManager.getEnchantLevel(rod, FishingEnchantManager.CustomEnchant.TREASURE_HUNTER);
        }
        double fishCatchBonusMult = baseCatchMult * (1.0 + (double)lureLvl * 0.1 * tierMult);
        double weightBonusMult = 1.0 + (double)heavyLvl * 0.05 * tierMult + baitWeightBonus;
        double treasureBonusMult = baseCatchMult * (1.0 + (double)treasureLvl * 0.2 * tierMult);
        double doubleHookChance = (double)doubleHookLvl * 0.05 * tierMult * 100.0;
        TextUtil.sendMessage((CommandSender)player, "&8&m----------------------------------------");
        TextUtil.sendMessage((CommandSender)player, "&b&lDetailed Fishing Stats");
        TextUtil.sendMessage((CommandSender)player, "&7Based on your current rod, bait, and conditions:");
        TextUtil.sendMessage((CommandSender)player, "");
        TextUtil.sendMessage((CommandSender)player, "&7General Catch Multiplier: &a" + String.format("%.2fx", baseCatchMult) + (fatiguePercent > 0.0 ? " &c(Fatigue Penalty Active)" : ""));
        TextUtil.sendMessage((CommandSender)player, "&7Fish Catch Rate Multiplier: &a" + String.format("%.2fx", fishCatchBonusMult));
        TextUtil.sendMessage((CommandSender)player, "&7Fish Weight Multiplier: &e" + String.format("%.2fx", weightBonusMult));
        TextUtil.sendMessage((CommandSender)player, "&7Treasure Find Multiplier: &d" + String.format("%.2fx", treasureBonusMult));
        if (doubleHookChance > 0.0) {
            TextUtil.sendMessage((CommandSender)player, "&7Double Hook Chance: &6" + String.format("%.1f%%", doubleHookChance));
        }
        TextUtil.sendMessage((CommandSender)player, "&8&m----------------------------------------");
    }

    private void openBuyBagGUI(Player player) {
        int reqLevel;
        Gui gui = new Gui("&8Purchase Fish Bag?", 3);
        gui.fillBorder(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        FishData bData = this.playerData.get(player.getUniqueId());
        int currentPages = bData != null ? bData.bagPages : 0;
        double cost = this.config.getDouble("bag-pages." + (currentPages + 1) + ".price", -1.0);
        if (cost == -1.0) {
            cost = 10000.0 + (double)(currentPages + 1) * 15000.0;
        }
        if ((reqLevel = this.config.getInt("bag-pages." + (currentPages + 1) + ".level-required", -1)) == -1) {
            reqLevel = (currentPages + 1) * 5;
        }
        gui.setItem(11, GuiButton.of(Material.LIME_DYE, "&a&lYes, Purchase", Arrays.asList("&7Cost: &a" + this.plugin.getEconomyManager().format(cost), "", "&7Click to purchase!"), e -> player.performCommand("fish buybag")));
        gui.setItem(15, GuiButton.of(Material.RED_DYE, "&c&lCancel", Arrays.asList("&7Nevermind."), e -> player.closeInventory()));
        gui.open(player);
    }

    public void removeLevels(Player player, int amount) {
        int level = player.getLevel() - amount;
        if (level < 0) {
            level = 0;
        }
        player.setLevel(level);
    }

    private void openShopGUI(Player player) {
        Gui gui = new Gui("&8Fishing Shop", 4);
        gui.fillBorder(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        ItemStack rod = player.getInventory().getItemInMainHand();
        if (rod == null || rod.getType() != Material.FISHING_ROD) {
            gui.setItem(13, GuiButton.of(Material.BARRIER, "&c&lNo Fishing Rod", Arrays.asList("&7You must hold a fishing rod in your main hand", "&7to purchase enchantments."), e -> {}));
            gui.open(player);
            return;
        }
        int[] slots = new int[]{10, 12, 14, 16};
        FishingEnchantManager.CustomEnchant[] enchants = FishingEnchantManager.CustomEnchant.values();
        for (int i = 0; i < enchants.length && i < slots.length; ++i) {
            FishingEnchantManager.CustomEnchant ce = enchants[i];
            int level = this.enchantManager.getEnchantLevel(rod, ce);
            String configBase = "enchants." + ce.getConfigKey() + ".";
            String name = this.config.getString(configBase + "name", ce.getDisplayName());
            String desc = this.config.getString(configBase + "description", "");
            double basePrice = this.config.getDouble(configBase + "base-price", 1000.0);
            double priceMult = this.config.getDouble(configBase + "price-multiplier", 1.5);
            int maxLevel = this.config.getInt(configBase + "max-level", ce.getMaxLevel());
            int reqXp = (level + 1) * this.config.getInt(configBase + "xp-required", 0);
            int reqCatches = (level + 1) * this.config.getInt(configBase + "catches-required", 0);
            FishData pData = this.playerData.get(player.getUniqueId());
            int pXp = player.getLevel();
            int pCatches = pData != null ? pData.totalCatches : 0;
            if (level >= maxLevel) {
                gui.setItem(slots[i], GuiButton.of(Material.ENCHANTED_BOOK, name, Arrays.asList("&7" + desc, "", "&c&lMAX LEVEL REACHED"), e -> TextUtil.sendMessage((CommandSender)player, "&cThat enchantment is already at max level.")));
                continue;
            }
            double cost = basePrice * Math.pow(priceMult, level);
            ArrayList<String> lore = new ArrayList<String>(Arrays.asList("&7" + desc, "", "&7Current Level: &b" + level, "&7Next Level: &b" + (level + 1), "&7Cost: &a" + this.plugin.getEconomyManager().format(cost)));
            boolean meetsReq = true;
            if (reqXp > 0) {
                String color = pXp >= reqXp ? "&a" : "&c";
                lore.add(color + "Required XP Levels: " + reqXp + " &8(" + pXp + ")");
                if (pXp < reqXp) {
                    meetsReq = false;
                }
            }
            if (reqCatches > 0) {
                String color = pCatches >= reqCatches ? "&a" : "&c";
                lore.add(color + "Required Catches: " + reqCatches + " &8(" + pCatches + ")");
                if (pCatches < reqCatches) {
                    meetsReq = false;
                }
            }
            lore.add("");
            if (meetsReq) {
                lore.add("&eClick to upgrade!");
            } else {
                lore.add("&cRequirements not met!");
            }
            gui.setItem(slots[i], GuiButton.of(Material.ENCHANTED_BOOK, name, lore, e -> {
                FishData liveData = this.playerData.get(player.getUniqueId());
                int liveCatches = liveData != null ? liveData.totalCatches : 0;
                int liveXp = player.getLevel();
                if (liveXp < reqXp || liveCatches < reqCatches) {
                    TextUtil.sendMessage((CommandSender)player, "&cYou do not meet the requirements to upgrade this.");
                    return;
                }
                double bal = this.plugin.getEconomyManager().getBalance((OfflinePlayer)player);
                if (bal < cost) {
                    TextUtil.sendMessage((CommandSender)player, "&cYou do not have enough money to upgrade this.");
                    return;
                }
                if (!this.plugin.getEconomyManager().withdraw(player, cost)) { TextUtil.sendMessage(player, "&cPayment failed. Nothing was purchased."); return; }
                if (reqXp > 0) {
                    this.removeLevels(player, reqXp);
                }
                this.enchantManager.setEnchantLevel(rod, ce, level + 1);
                TextUtil.sendMessage((CommandSender)player, "&aSuccessfully upgraded " + name + " &ato level " + (level + 1) + "!");
                player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
                this.openShopGUI(player);
            }));
        }
        if (rod != null && rod.getType() == Material.FISHING_ROD) {
            FishingEnchantManager.RodTier currentTier = this.enchantManager.getRodTier(rod);
            FishingEnchantManager.RodTier nextTier = currentTier.getNextTier();
            if (nextTier != null) {
                String tierConfigKey = nextTier.name().toLowerCase();
                double tierCost = this.config.getDouble("rod-tiers." + tierConfigKey + ".price", 50000.0 * (double)nextTier.getOrdinalIndex());
                int tierLevelReq = this.config.getInt("rod-tiers." + tierConfigKey + ".level-required", 5 * nextTier.getOrdinalIndex());
                FishData pData2 = this.playerData.get(player.getUniqueId());
                ItemStack tierIcon = new ItemStack(Material.ANVIL);
                ItemMeta tierMeta = tierIcon.getItemMeta();
                if (tierMeta != null) {
                    boolean hasEnchants;
                    tierMeta.displayName(TextUtil.colorize("&e&lUpgrade Rod Quality"));
                    ArrayList<Object> tierLore = new ArrayList<Object>();
                    tierLore.add(TextUtil.colorize("&7Current: " + currentTier.getDisplayName()));
                    tierLore.add(TextUtil.colorize("&7Next: " + nextTier.getDisplayName()));
                    tierLore.add(Component.empty());
                    tierLore.add(TextUtil.colorize("&7Enchant Power: &b" + String.format("%.0f", currentTier.getEnchantMultiplier() * 100.0) + "% &8\u2192 &a" + String.format("%.0f", nextTier.getEnchantMultiplier() * 100.0) + "%"));
                    tierLore.add(Component.empty());
                    tierLore.add(TextUtil.colorize("&7Cost: &e" + this.plugin.getEconomyManager().format(tierCost)));
                    tierLore.add(TextUtil.colorize("&7Level Required: &b" + tierLevelReq));
                    tierLore.add(Component.empty());
                    boolean canAfford = this.plugin.getEconomyManager().has((OfflinePlayer)player, tierCost);
                    boolean hasLevel = pData2 != null && pData2.level >= tierLevelReq;
                    int totalEnchants = 0;
                    for (FishingEnchantManager.CustomEnchant ce : FishingEnchantManager.CustomEnchant.values()) {
                        totalEnchants += this.enchantManager.getEnchantLevel(rod, ce);
                    }
                    boolean bl = hasEnchants = totalEnchants > 0;
                    if (canAfford && hasLevel && hasEnchants) {
                        tierLore.add(TextUtil.colorize("&a&lClick to upgrade!"));
                    } else {
                        if (!hasEnchants) {
                            tierLore.add(TextUtil.colorize("&c\u2717 Requires at least 1 Custom Enchant"));
                        }
                        if (!hasLevel) {
                            tierLore.add(TextUtil.colorize("&c\u2717 Need Angler Level " + tierLevelReq));
                        }
                        if (!canAfford) {
                            tierLore.add(TextUtil.colorize("&c\u2717 Not enough money"));
                        }
                    }
                    tierMeta.lore((java.util.List<net.kyori.adventure.text.Component>)(java.util.List<?>)tierLore);
                    tierIcon.setItemMeta(tierMeta);
                }
                gui.setItem(22, new GuiButton(tierIcon, e -> {
                    FishData pd = this.playerData.get(player.getUniqueId());
                    if (pd == null || pd.level < tierLevelReq) {
                        TextUtil.sendMessage((CommandSender)player, "&cYou need Angler Level " + tierLevelReq + " for this upgrade.");
                        return;
                    }
                    if (!this.plugin.getEconomyManager().has((OfflinePlayer)player, tierCost)) {
                        TextUtil.sendMessage((CommandSender)player, "&cYou don't have enough money for this upgrade.");
                        return;
                    }
                    ItemStack heldRod = player.getInventory().getItemInMainHand();
                    if (heldRod.getType() != Material.FISHING_ROD) {
                        TextUtil.sendMessage((CommandSender)player, "&cYou must hold a fishing rod in your main hand!");
                        return;
                    }
                    int totalClickEnchants = 0;
                    for (FishingEnchantManager.CustomEnchant ce : FishingEnchantManager.CustomEnchant.values()) {
                        totalClickEnchants += this.enchantManager.getEnchantLevel(heldRod, ce);
                    }
                    if (totalClickEnchants == 0) {
                        TextUtil.sendMessage((CommandSender)player, "&cYou must purchase at least 1 Custom Enchant before upgrading the rod quality!");
                        return;
                    }
                    if (!this.plugin.getEconomyManager().withdraw(player, tierCost)) { TextUtil.sendMessage(player, "&cPayment failed. Nothing was purchased."); return; }
                    this.enchantManager.setRodTier(heldRod, nextTier);
                    TextUtil.sendMessage((CommandSender)player, "&a&lRod upgraded to " + nextTier.getDisplayName() + " Quality&a!");
                    player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
                    this.openShopGUI(player);
                }));
            } else {
                gui.setItem(22, GuiButton.of(Material.NETHERITE_INGOT, "&4&lMax Rod Quality", Arrays.asList("&7Your rod is at the highest quality!", "&7" + currentTier.getDisplayName() + " &8(&b" + String.format("%.0f", currentTier.getEnchantMultiplier() * 100.0) + "% power&8)"), e -> {}));
            }
        }
        gui.setItem(35, GuiButton.of(Material.ARROW, "&c&lBack", Arrays.asList("&7Return to the main menu."), e -> player.performCommand("fish")));
        gui.open(player);
    }

    private double sellFishSilent(Player player, ItemStack fish) {
        if (fish == null || !isFishItem(fish) || fish.getAmount() <= 0) return 0.0;
        ItemMeta meta = fish.getItemMeta();
        if (meta == null) return 0.0;
        double weight = meta.getPersistentDataContainer().getOrDefault(weightKey, PersistentDataType.DOUBLE, 0.0);
        double price = meta.getPersistentDataContainer().getOrDefault(priceKey, PersistentDataType.DOUBLE, 0.0);
        double total = getFishValue(weight, price) * fish.getAmount();
        if (!Double.isFinite(weight) || weight <= 0 || !Double.isFinite(price) || price <= 0
                || !Double.isFinite(total) || total <= 0) return 0.0;
        // Remove one exact stack, then restore its slot if the provider rejects payment.
        for (int slot = 0; slot < player.getInventory().getStorageContents().length; slot++) {
            ItemStack actual = player.getInventory().getItem(slot);
            if (actual == null || !actual.equals(fish)) continue;
            ItemStack original = actual.clone();
            player.getInventory().setItem(slot, null);
            if (!plugin.getEconomyManager().deposit(player, total)) {
                player.getInventory().setItem(slot, original);
                return 0.0;
            }
            return total;
        }
        ItemStack[] bag = fishBagManager.getBag(player.getUniqueId());
        if (bag == null) return 0.0;
        for (int slot = 0; slot < bag.length; slot++) {
            if (bag[slot] == null || !bag[slot].equals(fish)) continue;
            ItemStack original = bag[slot].clone();
            bag[slot] = null;
            if (!plugin.getEconomyManager().deposit(player, total)) {
                bag[slot] = original;
                return 0.0;
            }
            fishBagManager.saveBag(player.getUniqueId());
            return total;
        }
        return 0.0;
    }

    private void sellFish(Player player, ItemStack fish) {
        double total = this.sellFishSilent(player, fish);
        if (total > 0.0) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            player.sendMessage(TextUtil.colorize(this.config.getString("prefix", "&8[&b&lFishing&8] &r") + "&7Sold fish for &a" + this.plugin.getEconomyManager().format(total) + "&7!"));
        }
    }

    public void syncFishItemConfig(ItemStack fish) {
        if (!this.isFishItem(fish)) {
            return;
        }
        ItemMeta meta = fish.getItemMeta();
        if (meta == null) {
            return;
        }
        String speciesId = (String)meta.getPersistentDataContainer().get(this.speciesKey, PersistentDataType.STRING);
        if (speciesId == null) {
            return;
        }
        if (this.config.getConfigurationSection("species") != null) {
            for (String key : this.config.getConfigurationSection("species").getKeys(false)) {
                double currentPrice;
                String spPath = "species." + key + ".";
                String confName = this.config.getString(spPath + "name", "");
                String confSpeciesId = ChatColor.stripColor((String)ChatColor.translateAlternateColorCodes((char)'&', (String)confName));
                if (!speciesId.equals(confSpeciesId)) continue;
                double confPrice = this.config.getDouble(spPath + "price-per-kg", 0.0);
                if (!(Math.abs(confPrice - (currentPrice = ((Double)meta.getPersistentDataContainer().getOrDefault(this.priceKey, PersistentDataType.DOUBLE, 0.0)).doubleValue())) > 0.01)) break;
                double weight = (Double)meta.getPersistentDataContainer().getOrDefault(this.weightKey, PersistentDataType.DOUBLE, 0.0);
                meta.getPersistentDataContainer().set(this.priceKey, PersistentDataType.DOUBLE, confPrice);
                meta.displayName(TextUtil.colorize(confName));
                ArrayList<Component> lore = new ArrayList<Component>();
                List confLore = this.config.getStringList(spPath + "lore");
                if (confLore != null) {
                    for (String line : (java.util.List<String>)(java.util.List<?>)confLore) {
                        lore.add(TextUtil.colorize(line));
                    }
                }
                lore.add(TextUtil.colorize("&7Weight: &b" + weight + " kg"));
                if (confPrice > 0.0) {
                    double val = this.getFishValue(weight, confPrice);
                    lore.add(TextUtil.colorize("&7Value: &a" + this.plugin.getEconomyManager().format(val)));
                }
                meta.lore((java.util.List<net.kyori.adventure.text.Component>)(java.util.List<?>)lore);
                fish.setItemMeta(meta);
                break;
            }
        }
    }

    private void openSellGUI(Player player, int page) {
        int i;
        Gui gui = new Gui("&8Sell Fish", 6);
        ArrayList<ItemStack> fishItems = new ArrayList<ItemStack>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (!this.isFishItem(item)) continue;
            this.syncFishItemConfig(item);
            fishItems.add(item);
        }
        ItemStack[] bag = this.fishBagManager.getBag(player.getUniqueId());
        if (bag != null) {
            for (ItemStack item : bag) {
                if (!this.isFishItem(item)) continue;
                this.syncFishItemConfig(item);
                fishItems.add(item);
            }
        }
        int start = page * 45;
        int end = start + 45;
        int slot = 0;
        for (i = start; i < Math.min(end, fishItems.size()); ++i) {
            ItemStack fish = (ItemStack)fishItems.get(i);
            gui.setItem(slot, new GuiButton(fish.clone(), e -> {
                this.sellFish(player, fish);
                this.fishBagManager.compactBag(player.getUniqueId());
                this.openSellGUI(player, page);
            }));
            ++slot;
        }
        for (i = 45; i < 54; ++i) {
            gui.setItem(i, GuiButton.of(Material.GRAY_STAINED_GLASS_PANE, " ", new ArrayList<String>(), e -> {}));
        }
        if (page > 0) {
            gui.setItem(45, GuiButton.of(Material.ARROW, "&aPrevious Page", Arrays.asList(""), e -> this.openSellGUI(player, page - 1)));
        }
        if (end < fishItems.size()) {
            gui.setItem(53, GuiButton.of(Material.ARROW, "&aNext Page", Arrays.asList(""), e -> this.openSellGUI(player, page + 1)));
        }
        double totalValue = 0.0;
        for (ItemStack fish : fishItems) {
            ItemMeta meta = fish.getItemMeta();
            if (meta == null) continue;
            double weight = (Double)meta.getPersistentDataContainer().getOrDefault(this.weightKey, PersistentDataType.DOUBLE, 0.0);
            double price = (Double)meta.getPersistentDataContainer().getOrDefault(this.priceKey, PersistentDataType.DOUBLE, 0.0);
            totalValue += this.getFishValue(weight, price) * (double)fish.getAmount();
        }
        gui.setItem(49, GuiButton.of(Material.EMERALD, "&a&lSell All", Arrays.asList("&7Click to sell all fish in your inventory.", "&7Total Value: &a" + this.plugin.getEconomyManager().format(totalValue)), e -> {
            double total = 0.0;
            for (ItemStack fish : fishItems) {
                total += this.sellFishSilent(player, fish);
            }
            if (total > 0.0) {
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                player.sendMessage(TextUtil.colorize(this.config.getString("prefix", "&8[&b&lFishing&8] &r") + "&7Sold all fish for &a" + this.plugin.getEconomyManager().format(total) + "&7!"));
            }
            this.fishBagManager.compactBag(player.getUniqueId());
            this.openSellGUI(player, page);
        }));
        gui.setItem(50, GuiButton.of(Material.BARRIER, "&c&lBack", Arrays.asList("&7Return to main menu."), e -> player.performCommand("fish")));
        gui.open(player);
    }

    private void openLogGUI(Player player) {
        Gui gui = new Gui("&8Fishing Logbook", 6);
        gui.fillBorder(Material.GRAY_STAINED_GLASS_PANE);
        this.plugin.getDatabaseManager().executeAsync(() -> {
            HashMap<String, Double> caughtSpecies = new HashMap<String, Double>();
            try (Connection conn = this.plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT species_id, max_weight FROM coresuite_fish_logbook WHERE uuid = ?");){
                stmt.setString(1, player.getUniqueId().toString());
                try (ResultSet rs = stmt.executeQuery();){
                    while (rs.next()) {
                        caughtSpecies.put(rs.getString("species_id"), rs.getDouble("max_weight"));
                    }
                }
            }
            catch (SQLException e) {
                this.plugin.getLogger().log(Level.SEVERE, "Failed to load fish logbook", e);
            }
            this.plugin.getServer().getScheduler().runTask((Plugin)this.plugin, () -> {
                int slot = 10;
                if (this.config.getConfigurationSection("species") != null) {
                    for (String key : this.config.getConfigurationSection("species").getKeys(false)) {
                        String spPath = "species." + key + ".";
                        String name = this.config.getString(spPath + "name", "&8???");
                        String speciesId = ChatColor.stripColor((String)ChatColor.translateAlternateColorCodes((char)'&', (String)name));
                        while (slot % 9 == 0 || slot % 9 == 8) {
                            ++slot;
                        }
                        if (slot >= 53) break;
                        if (caughtSpecies.containsKey(speciesId)) {
                            double maxWeight = (Double)caughtSpecies.get(speciesId);
                            String material = this.config.getString(spPath + "material", "COD");
                            String itemModel = this.config.getString(spPath + "item-model", "");
                            ItemStack fishItem = this.createFishItem(name, material, maxWeight, 0.0, itemModel);
                            ItemMeta meta = fishItem.getItemMeta();
                            ArrayList<Component> lore = new ArrayList<Component>();
                            lore.add(TextUtil.colorize("&7Personal Best: &b" + maxWeight + " kg"));
                            meta.lore((java.util.List<net.kyori.adventure.text.Component>)(java.util.List<?>)lore);
                            fishItem.setItemMeta(meta);
                            gui.setItem(slot, new GuiButton(fishItem, e -> {}));
                        } else {
                            ItemStack unknown = new ItemStack(Material.BARRIER);
                            ItemMeta meta = unknown.getItemMeta();
                            meta.displayName(TextUtil.colorize("&8???"));
                            meta.lore(Arrays.asList(TextUtil.colorize("&7You haven't caught this yet.")));
                            unknown.setItemMeta(meta);
                            gui.setItem(slot, new GuiButton(unknown, e -> {}));
                        }
                        ++slot;
                    }
                }
                gui.open(player);
            });
        });
    }

    private void openMainMenu(Player player, FishData data) {
        Gui gui = new Gui("&8Fishing Menu", 6);
        gui.fillBorder(Material.BLUE_STAINED_GLASS_PANE);
        int needed = data.level * this.config.getInt("leveling.xp-formula-multiplier", 20);
        ItemStack statsHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta statsMeta = (SkullMeta)statsHead.getItemMeta();
        if (statsMeta != null) {
            statsMeta.setOwningPlayer((OfflinePlayer)player);
            statsMeta.displayName(TextUtil.colorize("&b&l" + player.getName() + "'s Stats"));
            this.calculateFatigue(player, data);
            int fatigueDisplay = (int)Math.round(data.fatigueScore);
            String fatigueColor = fatigueDisplay >= 85 ? "&4" : (fatigueDisplay >= 75 ? "&c" : (fatigueDisplay >= 50 ? "&6" : "&e"));
            statsMeta.lore(Arrays.asList(TextUtil.colorize("&7Level: &b" + data.level), TextUtil.colorize("&7XP: &b" + data.xp + "&7/&b" + needed), TextUtil.colorize("&7Total Catches: &b" + data.totalCatches), TextUtil.colorize("&7Fatigue: " + fatigueColor + fatigueDisplay + "%"), TextUtil.colorize(""), TextUtil.colorize("&eHold your fishing rod and click"), TextUtil.colorize("&eto view detailed catch stats!")));
            statsHead.setItemMeta((ItemMeta)statsMeta);
        }
        gui.setItem(13, new GuiButton(statsHead, e -> {
            e.setCancelled(true);
            player.closeInventory();
            this.sendDetailedStats(player, data);
        }));
        List<String> bagLore = new ArrayList<String>();
        bagLore.add("&7Click to open your fish bag.");
        if (data.bagPages <= 0) {
            bagLore.add("");
            bagLore.add("&eClick to purchase your bag!");
        } else if (data.bagPages < 10) {
            bagLore.add("");
            bagLore.add("&aPages: &f" + data.bagPages + "/10");
            bagLore.add("&7(Upgrade inside bag with bottle button)");
        } else {
            bagLore.add("");
            bagLore.add("&aMax Pages Reached! (10/10)");
        }
        gui.setItem(28, GuiButton.of(Material.CHEST, "&a&lFish Bag", bagLore, e -> {
            if (data.bagPages <= 0) {
                player.performCommand("fish buybag");
            } else {
                player.performCommand("fish bag");
            }
        }));
        gui.setItem(29, GuiButton.of(Material.ENCHANTED_BOOK, "&d&lUpgrades Shop", Arrays.asList("&7Click to open the upgrades shop."), e -> player.performCommand("fish shop")));
        gui.setItem(30, GuiButton.of(Material.SLIME_BALL, "&e&lBait Shop", Arrays.asList("&7Click to open the bait shop."), e -> player.performCommand("fish bait")));
        gui.setItem(31, GuiButton.of(Material.GOLD_INGOT, "&e&lSell Fish", Arrays.asList("&7Click to sell your caught fish."), e -> player.performCommand("fish sell")));
        gui.setItem(32, GuiButton.of(Material.BOOK, "&b&lCollection", Arrays.asList("&7Click to view the fish collection."), e -> player.performCommand("fish info")));
        ItemStack leaderboardsHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta lbMeta = (SkullMeta)leaderboardsHead.getItemMeta();
        if (lbMeta != null) {
            lbMeta.setOwningPlayer((OfflinePlayer)player);
            lbMeta.displayName(TextUtil.colorize("&6&lLeaderboards"));
            lbMeta.lore(Arrays.asList(TextUtil.colorize("&7Click to view the server leaderboards.")));
            leaderboardsHead.setItemMeta((ItemMeta)lbMeta);
        }
        gui.setItem(33, new GuiButton(leaderboardsHead, e -> player.performCommand("fish top")));
        gui.setItem(34, GuiButton.of(Material.DIAMOND, "&b&lWeekly Tournament", Arrays.asList("&7Click to view the weekly tournament."), e -> player.performCommand("fish tournament")));
        gui.setItem(40, GuiButton.of(Material.GOLD_BLOCK, "&6&lHall of Fame", Arrays.asList("&7Click to view the Hall of Fame."), e -> player.performCommand("fish hof")));
        gui.open(player);
    }

    private boolean isOpenWater(Location hookLoc) {
        if (hookLoc == null || hookLoc.getWorld() == null) {
            return false;
        }
        int waterCount = 0;
        for (int dy = 0; dy <= 2; ++dy) {
            Block b = hookLoc.clone().subtract(0.0, (double)dy, 0.0).getBlock();
            if (b.getType() == Material.WATER) {
                waterCount++;
            }
        }
        return waterCount >= 2;
    }

    public int getTotalExperience(Player player) {
        int level = player.getLevel();
        int exp = Math.round((float)this.getExpToNext(level) * player.getExp());
        while (level > 0) {
            exp += this.getExpToNext(--level);
        }
        return exp;
    }

    private int getExpToNext(int level) {
        if (level < 16) {
            return 2 * level + 7;
        }
        if (level < 31) {
            return 5 * level - 38;
        }
        return 9 * level - 158;
    }

    public void removeExp(Player player, int amount) {
        int exp = this.getTotalExperience(player) - amount;
        player.setTotalExperience(0);
        player.setLevel(0);
        player.setExp(0.0f);
        if (exp > 0) {
            player.giveExp(exp);
        }
    }

    private void openTopGUI(Player player) {
        String prefix = this.config.getString("prefix", "&8[&b&lFishing&8] &r");
        TextUtil.sendMessage((CommandSender)player, prefix + "&7Loading leaderboard...");
        this.plugin.getDatabaseManager().executeAsync(() -> {
            ArrayList<TopPlayer> topList = new ArrayList<TopPlayer>();
            try (Connection conn = this.plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT uuid, player_name, level, xp, total_catches FROM coresuite_fishing ORDER BY level DESC, xp DESC LIMIT 10");
                 ResultSet rs = stmt.executeQuery();){
                while (rs.next()) {
                    String uStr = rs.getString("uuid");
                    UUID u = uStr != null ? UUID.fromString(uStr) : null;
                    topList.add(new TopPlayer(u, rs.getString("player_name"), rs.getInt("level"), rs.getInt("xp"), rs.getInt("total_catches")));
                }
            }
            catch (SQLException e) {
                this.plugin.getLogger().log(Level.SEVERE, "Failed to load fishing leaderboard", e);
                return;
            }
            this.plugin.getServer().getScheduler().runTask((Plugin)this.plugin, () -> {
                Gui gui = new Gui("&8Fishing Leaderboard", 5);
                gui.fillBorder(Material.CYAN_STAINED_GLASS_PANE);
                int[] slots = new int[]{13, 21, 22, 23, 29, 30, 31, 32, 33, 40};
                for (int i = 0; i < topList.size() && i < slots.length; ++i) {
                    TopPlayer top = (TopPlayer)topList.get(i);
                    String pName = top.name != null ? top.name : "Unknown";
                    ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                    SkullMeta meta = (SkullMeta)head.getItemMeta();
                    if (meta != null) {
                        if (top.uuid != null) {
                            meta.setOwningPlayer(Bukkit.getOfflinePlayer((UUID)top.uuid));
                        } else if (pName != null && !pName.equals("Unknown")) {
                            meta.setOwningPlayer(Bukkit.getOfflinePlayer((String)pName));
                        }
                        meta.displayName(TextUtil.colorize("&b&l#" + (i + 1) + " &f" + pName));
                        meta.lore(Arrays.asList(TextUtil.colorize("&7Level: &b" + top.level), TextUtil.colorize("&7XP: &b" + top.xp), TextUtil.colorize("&7Total Catches: &b" + top.totalCatches)));
                        head.setItemMeta((ItemMeta)meta);
                    }
                    gui.setItem(slots[i], new GuiButton(head, e -> {}));
                }
                gui.setItem(44, GuiButton.of(Material.BARRIER, "&c&lBack", Arrays.asList("&7Return to the main menu."), e -> player.performCommand("fish")));
                gui.open(player);
            });
        });
    }

    private String module_getBaitDisplayName(String baitId) {
        return this.config.getString("baits." + baitId + ".name", "&aBait");
    }

    public void sendFishingActionBar(Player player) {
        BaitManager.ActiveBait activeBait;
        UUID uuid = player.getUniqueId();
        if (this.actionbarOverride.getOrDefault(uuid, 0L) > System.currentTimeMillis()) {
            return;
        }
        FishData data = this.playerData.get(uuid);
        if (data == null) {
            return;
        }
        int streak = this.fishStreak.getOrDefault(uuid, 0);
        int streakCap = this.config.getInt("streak.max-multiplier", 10);
        this.calculateFatigue(player, data);
        String streakMsgFormat = this.config.getString("messages.streak-message", "&7Streak: &b{streak}x&8/&7{cap} &8| &7Angler Lv.&b{level}");
        String streakText = streakMsgFormat.replace("{streak}", String.valueOf(streak)).replace("{cap}", String.valueOf(streakCap)).replace("{level}", String.valueOf(data.level));
        int fatigueDisplayPercent = (int)Math.round(data.fatigueScore);
        StringBuilder actionBar = new StringBuilder(streakText);
        if (fatigueDisplayPercent > 0) {
            String fatigueColor = fatigueDisplayPercent >= 85 ? "&4" : (fatigueDisplayPercent >= 75 ? "&c" : (fatigueDisplayPercent >= 50 ? "&6" : "&e"));
            actionBar.append(" &8| ").append(fatigueColor).append("Fatigue: ").append(fatigueDisplayPercent).append("%");
        }
        if (this.baitManager != null && (activeBait = this.baitManager.getActiveBait(uuid)) != null) {
            String baitName = this.module_getBaitDisplayName(activeBait.baitId);
            if ("casts".equalsIgnoreCase(activeBait.type)) {
                actionBar.append(" &8| &aBait: ").append(activeBait.castsRemaining).append(" casts");
            } else {
                long secsLeft = Math.max(0L, (activeBait.expiresAt - System.currentTimeMillis()) / 1000L);
                actionBar.append(" &8| &aBait: ").append(secsLeft).append("s");
            }
        }
        TextUtil.sendActionBar(player, actionBar.toString());
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("fish")) {
            if (args.length == 1) {
                ArrayList<String> options = new ArrayList<String>(Arrays.asList("top", "sell", "log", "bag", "shop", "pond", "fatigue", "tournament", "bait", "hof", "info", "quest"));
                if (sender.hasPermission("coresuite.customfishing.admin") || sender.hasPermission("coresuite.admin")) {
                    options.add("admin");
                }
                ArrayList<String> result = new ArrayList<String>();
                for (String opt : options) {
                    if (!opt.toLowerCase().startsWith(args[0].toLowerCase())) continue;
                    result.add(opt);
                }
                return result;
            }
            if (args.length > 1 && args[0].equalsIgnoreCase("admin") && (sender.hasPermission("coresuite.customfishing.admin") || sender.hasPermission("coresuite.admin"))) {
                ArrayList<String> result = new ArrayList<String>();
                if (args.length == 2) {
                    StringUtil.copyPartialMatches((String)args[1], Arrays.asList("setlevel", "addxp", "setcatches", "clearlog", "forcepond", "give", "givebait", "legendary", "clearanglers", "spawnangler", "fatigue"), result);
                } else if (args.length == 3) {
                    if (args[1].equalsIgnoreCase("legendary")) {
                        StringUtil.copyPartialMatches((String)args[2], Arrays.asList("givemap"), result);
                    } else if (!args[1].equalsIgnoreCase("forcepond")) {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (!p.getName().toLowerCase().startsWith(args[2].toLowerCase())) continue;
                            result.add(p.getName());
                        }
                    }
                } else if (args.length == 4) {
                    if (args[1].equalsIgnoreCase("give")) {
                        if (this.config.getConfigurationSection("species") != null) {
                            ArrayList species = new ArrayList(this.config.getConfigurationSection("species").getKeys(false));
                            StringUtil.copyPartialMatches((String)args[3], species, result);
                        }
                    } else if (args[1].equalsIgnoreCase("givebait")) {
                        if (this.config.getConfigurationSection("baits") != null) {
                            ArrayList baits = new ArrayList(this.config.getConfigurationSection("baits").getKeys(false));
                            StringUtil.copyPartialMatches((String)args[3], baits, result);
                        }
                    } else if (args[1].equalsIgnoreCase("fatigue")) {
                        StringUtil.copyPartialMatches((String)args[3], Arrays.asList("set", "reset"), result);
                    } else if (args[1].equalsIgnoreCase("setcatches")) {
                        StringUtil.copyPartialMatches((String)args[3], Arrays.asList("10", "50", "100", "250", "500", "1000"), result);
                    } else if (args[1].equalsIgnoreCase("legendary") && args[2].equalsIgnoreCase("givemap")) {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (!p.getName().toLowerCase().startsWith(args[3].toLowerCase())) continue;
                            result.add(p.getName());
                        }
                    }
                }
                return result;
            }
        }
        return Collections.emptyList();
    }

    public LegendaryNPCManager getLegendaryNPCManager() {
        return this.legendaryNPCManager;
    }

    public LegendaryQuestManager getLegendaryQuestManager() {
        return this.legendaryQuestManager;
    }

    public NamespacedKey getSpeciesKey() {
        return this.speciesKey;
    }

    public FishingEnchantManager getEnchantManager() {
        return this.enchantManager;
    }

    public void addAnglerLevels(Player player, int levels) {
        FishData data = this.playerData.get(player.getUniqueId());
        if (data == null) {
            data = new FishData(1, 0, 0, 0);
            this.playerData.put(player.getUniqueId(), data);
        }
        data.level += levels;
        this.playerData.put(player.getUniqueId(), data);
        this.saveDataAsync(player.getUniqueId());
        String levelUpMsg = this.config.getString("messages.level-up", "&a&lLEVEL UP! &7You are now Angler Level &b{level}&7!");
        TextUtil.sendMessage((CommandSender)player, levelUpMsg.replace("{level}", String.valueOf(data.level)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
    }

    public FishData getPlayerData(UUID uuid) {
        return this.playerData.get(uuid);
    }

    public static class FishData {
        public int level;
        public int xp;
        public int totalCatches;
        public int bagPages;
        public double fatigueScore;
        public long fatigueLastUpdate;
        public int legendaryPity;

        public FishData(int level, int xp, int totalCatches, int bagPages) {
            this.level = level;
            this.xp = xp;
            this.totalCatches = totalCatches;
            this.bagPages = bagPages;
            this.fatigueScore = 0.0;
            this.fatigueLastUpdate = System.currentTimeMillis();
            this.legendaryPity = 0;
        }

        public FishData(int level, int xp, int totalCatches, int bagPages, double fatigueScore, long fatigueLastUpdate) {
            this(level, xp, totalCatches, bagPages, fatigueScore, fatigueLastUpdate, 0);
        }

        public FishData(int level, int xp, int totalCatches, int bagPages, double fatigueScore, long fatigueLastUpdate, int legendaryPity) {
            this.level = level;
            this.xp = xp;
            this.totalCatches = totalCatches;
            this.bagPages = bagPages;
            this.fatigueScore = fatigueScore;
            this.fatigueLastUpdate = fatigueLastUpdate;
            this.legendaryPity = legendaryPity;
        }
    }

    private static class TopPlayer {
        UUID uuid;
        String name;
        int level;
        int xp;
        int totalCatches;

        TopPlayer(UUID u, String n, int l, int x, int c) {
            this.uuid = u;
            this.name = n;
            this.level = l;
            this.xp = x;
            this.totalCatches = c;
        }
    }

    public static class CrateReward {
        public final double weight;
        public final String rarity;
        public final String name;
        public final String sound;
        public final String command;

        public CrateReward(double weight, String rarity, String name, String sound, String command) {
            this.weight = weight;
            this.rarity = rarity;
            this.name = name;
            this.sound = sound;
            this.command = command;
        }
    }
}

