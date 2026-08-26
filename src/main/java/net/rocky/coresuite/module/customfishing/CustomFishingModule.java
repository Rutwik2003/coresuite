package net.rocky.coresuite.module.customfishing;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class CustomFishingModule extends Module implements Listener, CommandExecutor, TabCompleter {

    private final Map<UUID, FishData> playerData = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastFishTime = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> fishStreak = new ConcurrentHashMap<>();
    private final Map<UUID, Long> actionbarOverride = new ConcurrentHashMap<>();
    private final Map<UUID, Map<Long, java.util.concurrent.atomic.AtomicInteger>> chunkCatches = new ConcurrentHashMap<>();
    
    private final Random random = new Random();
    private final FishingEnchantManager enchantManager;
    private FishBagManager fishBagManager;
    public FishBagManager getFishBagManager() { return fishBagManager; }
    private FishingEventManager eventManager;
    private FishingLeaderboardManager leaderboardManager;
    private WanderingAnglerManager wanderingAnglerManager;
    private FishingTournamentManager tournamentManager;
    private BaitManager baitManager;
    public BaitManager getBaitManager() { return baitManager; }
    private LegendaryNPCManager legendaryNPCManager;
    private LegendaryQuestManager legendaryQuestManager;

    private NamespacedKey speciesKey;
    private NamespacedKey weightKey;
    private NamespacedKey priceKey;

    public CustomFishingModule(CoreSuitePlugin plugin) {
        super(plugin, "CustomFishing", "custom-fishing");
        this.enchantManager = new FishingEnchantManager(plugin);
    }

    @Override
    public void onEnable() {
        int configVersion = config.getInt("config-version", 1);
        if (configVersion < 2) {
            plugin.getLogger().info("CoreSuite CustomFishing: Migrating config to version 2 (updating baits)...");
            java.io.InputStream defStream = plugin.getResource("modules/custom-fishing.yml");
            if (defStream != null) {
                org.bukkit.configuration.file.YamlConfiguration defConfig = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(defStream, java.nio.charset.StandardCharsets.UTF_8)
                );
                config.set("baits", defConfig.getConfigurationSection("baits"));
                config.set("config-version", 2);
                saveConfig();
            }
        }
        
        boolean saved = false;
        if (!config.contains("fatigue.recovery-time-minutes")) {
            config.set("fatigue.recovery-time-minutes", 60);
            saved = true;
        }
        if (!config.contains("pond-event.fatigue-heal-amount")) {
            config.set("pond-event.fatigue-heal-amount", 5.0);
            saved = true;
        }
        if (saved) saveConfig();
        
        speciesKey = new NamespacedKey(plugin, "fish_species");
        weightKey = new NamespacedKey(plugin, "fish_weight");
        priceKey = new NamespacedKey(plugin, "fish_price");
        initDatabase();
        cleanupRemovedFish();
        fishBagManager = new FishBagManager(plugin);
        eventManager = new FishingEventManager(plugin, this);
        leaderboardManager = new FishingLeaderboardManager(plugin, this);
        wanderingAnglerManager = new WanderingAnglerManager(plugin, this);
        tournamentManager = new FishingTournamentManager(plugin, this);
        baitManager = new BaitManager(plugin, this);
        legendaryNPCManager = new LegendaryNPCManager(plugin, this);
        legendaryQuestManager = new LegendaryQuestManager(plugin, this);
        registerListener(this);

        PluginCommand command = plugin.getCommand("fish");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }

        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (Map<Long, java.util.concurrent.atomic.AtomicInteger> worldMap : chunkCatches.values()) {
                for (Map.Entry<Long, java.util.concurrent.atomic.AtomicInteger> entry : worldMap.entrySet()) {
                    if (entry.getValue().decrementAndGet() <= 0) {
                        worldMap.remove(entry.getKey());
                    }
                }
            }
        }, 1200L, 1200L);
        
        // Action bar continuous update for players holding a fishing rod
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                ItemStack mainHand = player.getInventory().getItemInMainHand();
                ItemStack offHand = player.getInventory().getItemInOffHand();
                if ((mainHand != null && mainHand.getType() == org.bukkit.Material.FISHING_ROD) || 
                    (offHand != null && offHand.getType() == org.bukkit.Material.FISHING_ROD)) {
                    sendFishingActionBar(player);
                }
            }
        }, 20L, 20L);
        // load online players
        for (Player p : Bukkit.getOnlinePlayers()) {
            loadData(p);
            fishBagManager.loadBag(p.getUniqueId());
        }
    }
    
    private void cleanupRemovedFish() {
        String[] removed = {"drowned_king_fish", "fish_barrel", "fin_shady", "fishlin_brute", 
            "malfisha", "moaf", "nameless_fish", "necromancer_fish", "pain_eel", "zombie_fish"};
        
        boolean configChanged = false;
        for (String f : removed) {
            if (config.contains("species." + f)) {
                config.set("species." + f, null);
                configChanged = true;
            }
        }
        if (configChanged) {
            saveConfig();
        }
        
        // Remove from database logbook
        plugin.getDatabaseManager().executeAsync(() -> {
            for (String f : removed) {
                plugin.getDatabaseManager().execute("DELETE FROM coresuite_fish_logbook WHERE species_id = ?", f);
            }
        });
    }

    @Override
    public void onDisable() {
        // Shutdown event manager to cancel particle/timer tasks
        if (eventManager != null) {
            eventManager.shutdown();
        }
        if (wanderingAnglerManager != null) {
            wanderingAnglerManager.shutdown();
        }
        if (tournamentManager != null) {
            tournamentManager.shutdown();
        }
        if (legendaryNPCManager != null) {
            legendaryNPCManager.shutdown();
        }
        if (baitManager != null) {
            baitManager.shutdown();
        }
        PluginCommand command = plugin.getCommand("fish");
        if (command != null && command.getExecutor() == this) {
            command.setExecutor(null);
            command.setTabCompleter(null);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            saveData(p.getUniqueId());
            fishBagManager.saveBagSync(p.getUniqueId());
        }
        playerData.clear();
    }

    private void initDatabase() {
        plugin.getDatabaseManager().execute(
            "CREATE TABLE IF NOT EXISTS coresuite_fishing (" +
            "uuid TEXT PRIMARY KEY, " +
            "player_name TEXT, " +
            "level INTEGER DEFAULT 1, " +
            "xp INTEGER DEFAULT 0, " +
            "total_catches INTEGER DEFAULT 0, " +
            "bag_pages INTEGER DEFAULT 0, " +
            "fatigue_score REAL DEFAULT 0, " +
            "fatigue_last_update BIGINT DEFAULT 0, " +
            "legendary_pity INTEGER DEFAULT 0)"
        );
        plugin.getDatabaseManager().execute(
            "CREATE TABLE IF NOT EXISTS coresuite_active_baits (" +
            "uuid TEXT PRIMARY KEY, " +
            "bait_id TEXT, " +
            "bait_type TEXT, " +
            "casts_remaining INTEGER, " +
            "expires_at BIGINT, " +
            "catch_bonus REAL, " +
            "weight_bonus REAL, " +
            "rarity_bias TEXT)"
        );
        plugin.getDatabaseManager().execute(
            "CREATE TABLE IF NOT EXISTS coresuite_bait_stock (" +
            "uuid TEXT, " +
            "bait_id TEXT, " +
            "bought_amount INTEGER DEFAULT 0, " +
            "last_restock BIGINT DEFAULT 0, " +
            "PRIMARY KEY (uuid, bait_id))"
        );
        plugin.getDatabaseManager().executeAsync(() -> {
            try (java.sql.Connection c = plugin.getDatabaseManager().getConnection();
                 java.sql.Statement s = c.createStatement()) {
                s.execute("ALTER TABLE coresuite_fishing ADD COLUMN bag_pages INTEGER DEFAULT 0");
            } catch (Exception ignored) {}
            try (java.sql.Connection c = plugin.getDatabaseManager().getConnection();
                 java.sql.Statement s = c.createStatement()) {
                s.execute("ALTER TABLE coresuite_fishing ADD COLUMN fatigue_score REAL DEFAULT 0");
            } catch (Exception ignored) {}
            try (java.sql.Connection c = plugin.getDatabaseManager().getConnection();
                 java.sql.Statement s = c.createStatement()) {
                s.execute("ALTER TABLE coresuite_fishing ADD COLUMN fatigue_last_update BIGINT DEFAULT 0");
            } catch (Exception ignored) {}
            try (java.sql.Connection c = plugin.getDatabaseManager().getConnection();
                 java.sql.Statement s = c.createStatement()) {
                s.execute("ALTER TABLE coresuite_fishing ADD COLUMN legendary_pity INTEGER DEFAULT 0");
            } catch (Exception ignored) {}
        });
        plugin.getDatabaseManager().execute(
            "CREATE TABLE IF NOT EXISTS coresuite_fish_logbook (" +
            "uuid TEXT, " +
            "species_id TEXT, " +
            "max_weight REAL, " +
            "PRIMARY KEY(uuid, species_id))"
        );
        plugin.getDatabaseManager().execute(
            "CREATE TABLE IF NOT EXISTS coresuite_fish_bag (" +
            "uuid VARCHAR(36) PRIMARY KEY, " +
            "inventory_data TEXT)"
        );
    }

    private void loadData(Player player) {
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT level, xp, total_catches, bag_pages, fatigue_score, fatigue_last_update, legendary_pity FROM coresuite_fishing WHERE uuid = ?")) {
                stmt.setString(1, player.getUniqueId().toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        FishData data = new FishData(rs.getInt("level"), rs.getInt("xp"), rs.getInt("total_catches"), rs.getInt("bag_pages"), rs.getDouble("fatigue_score"), rs.getLong("fatigue_last_update"), rs.getInt("legendary_pity"));
                        playerData.put(player.getUniqueId(), data);
                    } else {
                        plugin.getDatabaseManager().execute("INSERT INTO coresuite_fishing (uuid, player_name) VALUES (?, ?)", player.getUniqueId().toString(), player.getName());
                        playerData.put(player.getUniqueId(), new FishData(1, 0, 0, 0));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load fishing data", e);
            }
        });
    }

    private void saveData(UUID uuid) {
        FishData data = playerData.get(uuid);
        if (data != null) {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE coresuite_fishing SET level = ?, xp = ?, total_catches = ?, bag_pages = ?, fatigue_score = ?, fatigue_last_update = ?, legendary_pity = ? WHERE uuid = ?"
                 )) {
                stmt.setInt(1, data.level);
                stmt.setInt(2, data.xp);
                stmt.setInt(3, data.totalCatches);
                stmt.setInt(4, data.bagPages);
                stmt.setDouble(5, data.fatigueScore);
                stmt.setLong(6, data.fatigueLastUpdate);
                stmt.setInt(7, data.legendaryPity);
                stmt.setString(8, uuid.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save data for " + uuid, e);
            }
        }
    }

    private void saveDataAsync(UUID uuid) {
        plugin.getDatabaseManager().executeAsync(() -> saveData(uuid));
    }

    /**
     * Calculates the current effective fatigue percentage (0.0 to 1.0) for a player,
     * applying time-based decay. Fatigue decays linearly over 1 hour of real time.
     * Updates the stored fatigue score with the decayed value.
     */
    private double calculateFatigue(Player player, FishData data) {
        long now = System.currentTimeMillis();
        long elapsed = now - data.fatigueLastUpdate;
        
        if (elapsed > 0) {
            if (data.fatigueScore > 0) {
                // Decay: full fatigue (100 points) decays to 0 over X minutes (default 60m)
                int decayMinutes = config.getInt("fatigue.recovery-time-minutes", 60);
                double decayMs = decayMinutes * 60000.0;
                double decayRate = 100.0 / decayMs; // points per millisecond
                double decay = elapsed * decayRate;
                double oldScore = data.fatigueScore;
                data.fatigueScore = Math.max(0.0, data.fatigueScore - decay);
                
                // Only notify if they actually took a break (e.g. > 3 minutes / 180000ms elapsed)
                if (decay >= 1.0 && player != null && elapsed >= 180000) {
                    double recovered = Math.min(oldScore, decay);
                    if (data.fatigueScore == 0.0) {
                        TextUtil.sendMessage(player, "&a&lYou feel fully rested! &aYour fatigue has completely faded.");
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 2.0f);
                    } else {
                        TextUtil.sendMessage(player, "&aYou recovered &2" + String.format("%.1f", recovered) + "% &afatigue while resting.");
                    }
                }
            }
            data.fatigueLastUpdate = now; // Always sync the timer
        }
        
        int threshold = config.getInt("fatigue.threshold", 75);
        if (data.fatigueScore <= threshold) {
            return 0.0; // No fatigue below threshold
        }
        // Scale: threshold -> 0%, 100 -> 100%
        double fatiguePercent = (data.fatigueScore - threshold) / (100.0 - threshold);
        return Math.min(1.0, Math.max(0.0, fatiguePercent));
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        loadData(event.getPlayer());
        fishBagManager.loadBag(event.getPlayer().getUniqueId());
        if (legendaryQuestManager != null) legendaryQuestManager.loadPlayer(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (legendaryQuestManager != null) legendaryQuestManager.unloadPlayer(event.getPlayer().getUniqueId());
        
        // Grab data and save synchronously to avoid data loss on restart
        FishData data = playerData.get(event.getPlayer().getUniqueId());
        if (data != null) {
            UUID quitUuid = event.getPlayer().getUniqueId();
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                    "UPDATE coresuite_fishing SET level = ?, xp = ?, total_catches = ?, bag_pages = ?, fatigue_score = ?, fatigue_last_update = ?, legendary_pity = ? WHERE uuid = ?"
                 )) {
                stmt.setInt(1, data.level);
                stmt.setInt(2, data.xp);
                stmt.setInt(3, data.totalCatches);
                stmt.setInt(4, data.bagPages);
                stmt.setDouble(5, data.fatigueScore);
                stmt.setLong(6, data.fatigueLastUpdate);
                stmt.setInt(7, data.legendaryPity);
                stmt.setString(8, quitUuid.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save data on quit for " + quitUuid, e);
            }
        }
        fishBagManager.unloadBag(event.getPlayer().getUniqueId());
        playerData.remove(event.getPlayer().getUniqueId());
        lastFishTime.remove(event.getPlayer().getUniqueId());
        fishStreak.remove(event.getPlayer().getUniqueId());
        chunkCatches.remove(event.getPlayer().getUniqueId());
    }

    private void saveCurrentBagPage(Player player, org.bukkit.inventory.Inventory inv, int page) {
        ItemStack[] contents = inv.getContents();
        ItemStack[] bag = fishBagManager.getBag(player.getUniqueId());
        if (bag.length < (page + 1) * 45) {
            bag = Arrays.copyOf(bag, (page + 1) * 45);
        }
        int start = page * 45;
        for (int i = 0; i < 45; i++) {
            bag[start + i] = contents[i];
        }
        fishBagManager.updateBag(player.getUniqueId(), bag);
    }

    @EventHandler
    public void onInventoryClose(org.bukkit.event.inventory.InventoryCloseEvent event) {
        String title = event.getView().getTitle();
        if (title.startsWith("Fish Bag (Page ")) {
            Player player = (Player) event.getPlayer();
            int page = 0;
            try {
                page = Integer.parseInt(title.replace("Fish Bag (Page ", "").replace(")", "")) - 1;
            } catch (Exception ignored) {}
            
            ItemStack[] contents = event.getInventory().getContents();
            ItemStack[] bag = fishBagManager.getBag(player.getUniqueId());
            if (bag.length < (page + 1) * 45) {
                bag = Arrays.copyOf(bag, (page + 1) * 45);
            }
            int start = page * 45;
            for (int i = 0; i < 45; i++) {
                bag[start + i] = contents[i];
            }
            
            fishBagManager.updateBag(player.getUniqueId(), bag);
            fishBagManager.saveBag(player.getUniqueId());
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        String title = event.getView().getTitle();
        if (title.startsWith("Fish Bag (Page ")) {
            Player player = (Player) event.getWhoClicked();
            if (event.getClick() == org.bukkit.event.inventory.ClickType.NUMBER_KEY) {
                org.bukkit.inventory.ItemStack hotbarItem = player.getInventory().getItem(event.getHotbarButton());
                if (hotbarItem != null && !isFishItem(hotbarItem)) {
                    event.setCancelled(true);
                    return;
                }
            }
            if (event.getRawSlot() >= 45 && event.getRawSlot() < 54) {
                event.setCancelled(true);
                if (event.getCurrentItem() != null) {
                    if (event.getCurrentItem().getType() == org.bukkit.Material.ARROW) {
                        FishData bData = playerData.get(player.getUniqueId());
                        int totalPages = bData != null ? bData.bagPages : 1;
                        int page = 0;
                        try {
                            page = Integer.parseInt(title.replace("Fish Bag (Page ", "").replace(")", "")) - 1;
                        } catch (Exception ignored) {}
                        
                        if (event.getRawSlot() == 45 && page > 0) {
                            saveCurrentBagPage(player, event.getView().getTopInventory(), page);
                            fishBagManager.openBag(player, totalPages, page - 1);
                        } else if (event.getRawSlot() == 53 && page < totalPages - 1) {
                            saveCurrentBagPage(player, event.getView().getTopInventory(), page);
                            fishBagManager.openBag(player, totalPages, page + 1);
                        }
                    } else if (event.getRawSlot() == 47 && event.getCurrentItem().getType() == org.bukkit.Material.EMERALD) {
                        FishData bData = playerData.get(player.getUniqueId());
                        int totalPages = bData != null ? bData.bagPages : 1;
                        int page = 0;
                        try {
                            page = Integer.parseInt(title.replace("Fish Bag (Page ", "").replace(")", "")) - 1;
                        } catch (Exception ignored) {}
                        saveCurrentBagPage(player, event.getView().getTopInventory(), page);
                        fishBagManager.sortBag(player.getUniqueId(), this, true);
                        fishBagManager.openBag(player, totalPages, page);
                    } else if (event.getRawSlot() == 51 && event.getCurrentItem().getType() == org.bukkit.Material.ANVIL) {
                        FishData bData = playerData.get(player.getUniqueId());
                        int totalPages = bData != null ? bData.bagPages : 1;
                        int page = 0;
                        try {
                            page = Integer.parseInt(title.replace("Fish Bag (Page ", "").replace(")", "")) - 1;
                        } catch (Exception ignored) {}
                        saveCurrentBagPage(player, event.getView().getTopInventory(), page);
                        fishBagManager.sortBag(player.getUniqueId(), this, false);
                        fishBagManager.openBag(player, totalPages, page);
                    } else if (event.getRawSlot() == 48 && event.getCurrentItem().getType() == org.bukkit.Material.GOLD_INGOT) {
                        player.performCommand("fish buybag");
                    } else if (event.getRawSlot() == 49 && event.getCurrentItem().getType() == org.bukkit.Material.BARRIER) {
                        // player.closeInventory();
                        player.performCommand("fish");
                    }
                }
                return;
            }
            
            if (event.getAction() == org.bukkit.event.inventory.InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                if (event.getRawSlot() >= event.getView().getTopInventory().getSize()) {
                    if (!isFishItem(event.getCurrentItem())) {
                        event.setCancelled(true);
                    }
                }
            } else if (event.getClickedInventory() != null && event.getClickedInventory().equals(event.getView().getTopInventory())) {
                if (event.getCursor() != null && event.getCursor().getType() != org.bukkit.Material.AIR) {
                    if (!isFishItem(event.getCursor())) {
                        event.setCancelled(true);
                    }
                }
            }
        }
    }

    public ItemStack createFishItem(String name, String materialName, double weight, double pricePerKg) {
        return createFishItem(name, materialName, weight, pricePerKg, "");
    }

    public ItemStack createFishItem(String name, String materialName, double weight, double pricePerKg, String itemModel) {
        org.bukkit.Material mat;
        try {
            mat = org.bukkit.Material.valueOf(materialName.toUpperCase());
        } catch (IllegalArgumentException e) {
            mat = org.bukkit.Material.COD; // fallback
        }
        ItemStack fish = new ItemStack(mat);
        org.bukkit.inventory.meta.ItemMeta meta = fish.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.colorize(name));
            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
            lore.add(TextUtil.colorize("&7Weight: &b" + weight + " kg"));
            if (pricePerKg > 0) {
                lore.add(TextUtil.colorize("&7Value: &a$" + String.format("%.2f", weight * pricePerKg)));
            }
            meta.lore(lore);

            if (itemModel != null && !itemModel.isEmpty()) {
                meta.setItemModel(NamespacedKey.fromString(itemModel));
            }

            meta.getPersistentDataContainer().set(speciesKey, PersistentDataType.STRING, org.bukkit.ChatColor.stripColor(org.bukkit.ChatColor.translateAlternateColorCodes('&', name)));
            meta.getPersistentDataContainer().set(weightKey, PersistentDataType.DOUBLE, weight);
            meta.getPersistentDataContainer().set(priceKey, PersistentDataType.DOUBLE, pricePerKg);

            fish.setItemMeta(meta);
        }
        return fish;
    }

    private boolean isFishItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(speciesKey, PersistentDataType.STRING);
    }

    private void checkAndUpdatePersonalBest(Player player, String speciesId, double weight) {
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT max_weight FROM coresuite_fish_logbook WHERE uuid = ? AND species_id = ?")) {
                stmt.setString(1, player.getUniqueId().toString());
                stmt.setString(2, speciesId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        double max = rs.getDouble("max_weight");
                        if (weight > max) {
                            try (PreparedStatement update = conn.prepareStatement("UPDATE coresuite_fish_logbook SET max_weight = ? WHERE uuid = ? AND species_id = ?")) {
                                update.setDouble(1, weight);
                                update.setString(2, player.getUniqueId().toString());
                                update.setString(3, speciesId);
                                update.executeUpdate();
                            }
                            player.sendMessage(TextUtil.colorize(config.getString("prefix", "&8[&b&lFishing&8] &r") + "&aNew Personal Best for " + speciesId + "! &8(&b" + weight + " kg&8)"));
                        }
                    } else {
                        try (PreparedStatement insert = conn.prepareStatement("INSERT INTO coresuite_fish_logbook (uuid, species_id, max_weight) VALUES (?, ?, ?)")) {
                            insert.setString(1, player.getUniqueId().toString());
                            insert.setString(2, speciesId);
                            insert.setDouble(3, weight);
                            insert.executeUpdate();
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to update personal best", e);
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            Player player = event.getPlayer();
            UUID uuid = player.getUniqueId();
            
            org.bukkit.Chunk pChunk = player.getLocation().getChunk();
            Map<Long, java.util.concurrent.atomic.AtomicInteger> worldMap = chunkCatches.computeIfAbsent(
                player.getWorld().getUID(), k -> new ConcurrentHashMap<>()
            );
            long pKey = ((long) pChunk.getX() & 0xFFFFFFFFL) | (((long) pChunk.getZ() & 0xFFFFFFFFL) << 32);
            worldMap.computeIfAbsent(pKey, k -> new java.util.concurrent.atomic.AtomicInteger(0)).incrementAndGet();
            
            int radiusSum = 0;
            for (int dx = -5; dx <= 5; dx++) {
                for (int dz = -5; dz <= 5; dz++) {
                    long ck = ((long) (pChunk.getX() + dx) & 0xFFFFFFFFL) | (((long) (pChunk.getZ() + dz) & 0xFFFFFFFFL) << 32);
                    java.util.concurrent.atomic.AtomicInteger count = worldMap.get(ck);
                    if (count != null) {
                        radiusSum += count.get();
                    }
                }
            }
            
            if (radiusSum > 50) {
                actionbarOverride.put(player.getUniqueId(), System.currentTimeMillis() + 3000L);
                TextUtil.sendActionBar(player, "&c[!] These waters are overfished! Move away!");
                return;
            }
            
            FishData data = playerData.get(uuid);
            if (data == null) return; // Data not loaded yet, skip this catch

            int streakCap = 6;
            if (data.level >= 10) streakCap = 15;
            else if (data.level >= 5) streakCap = 10;

            long now = System.currentTimeMillis();
            long last = lastFishTime.getOrDefault(uuid, 0L);
            int streak = fishStreak.getOrDefault(uuid, 0);
            int timeout = config.getInt("streak-timeout", 45);

            if (now - last < timeout * 1000L) {
                streak++;
            } else {
                streak = 1;
            }
            lastFishTime.put(uuid, now);

            if (streak > streakCap) streak = streakCap;
            fishStreak.put(uuid, streak);

            // XP and leveling logic has been moved to trigger only on successful custom catches.

            double eventRewardMult = 1.0;
            double eventWeightMult = 1.0;
            if (eventManager != null && eventManager.getActivePond() != null) {
                org.bukkit.Location hookLoc = event.getHook().getLocation();
                org.bukkit.Location pondLoc = eventManager.getActivePond();
                if (hookLoc.getWorld().getName().equals(pondLoc.getWorld().getName())) {
                    // Use horizontal (XZ) distance only — the hook sinks underwater so 3D distance breaks detection
                    double dx = hookLoc.getX() - pondLoc.getX();
                    double dz = hookLoc.getZ() - pondLoc.getZ();
                    double dy = Math.abs(hookLoc.getY() - pondLoc.getY());
                    double radius = config.getDouble("pond-event.radius", 5.0);
                    if (dx * dx + dz * dz <= radius * radius && dy <= 10.0) {
                        eventRewardMult = config.getDouble("pond-event.reward-multiplier", 2.0);
                        eventWeightMult = config.getDouble("pond-event.weight-multiplier", 2.0);
                    }
                }
            }

            // Calculate fatigue with time-based decay
            double fatiguePercent = calculateFatigue(player, data);
            double fatigueMult = 1.0 - fatiguePercent; // 1.0 = no penalty, 0.0 = max penalty

            // Apply fatigue potion effects
            if (fatiguePercent >= 0.85) {
                // 85%+ fatigue: Darkness (blackout) — random chance each catch
                if (random.nextDouble() < 0.4) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 100, 0, false, false, true));
                }
                // Also always apply nausea at this level
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 200, 0, false, false, true));
            } else if (fatiguePercent >= 0.75) {
                // 75%+ fatigue: Nausea (sea sickness)
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 160, 0, false, false, true));
            }

            // Show fatigue message when first crossing threshold
            if (fatiguePercent > 0 && fatiguePercent < 0.1) {
                String fatigueMsg = config.getString("fatigue.message", "&c&oGetting tired...");
                TextUtil.sendActionBar(player, fatigueMsg);
            }

            double streakBonus = Math.pow(streak, 0.6) * 0.045;

            ItemStack rod = player.getInventory().getItemInMainHand();
            if (rod == null || rod.getType() != org.bukkit.Material.FISHING_ROD) {
                rod = player.getInventory().getItemInOffHand();
            }

            int lureLvl = enchantManager.getEnchantLevel(rod, FishingEnchantManager.CustomEnchant.LURE_OF_THE_DEEP);
            int heavyLvl = enchantManager.getEnchantLevel(rod, FishingEnchantManager.CustomEnchant.HEAVYWEIGHT);
            int doubleHookLvl = enchantManager.getEnchantLevel(rod, FishingEnchantManager.CustomEnchant.DOUBLE_HOOK);
            int treasureLvl = enchantManager.getEnchantLevel(rod, FishingEnchantManager.CustomEnchant.TREASURE_HUNTER);
            double tierMult = enchantManager.getRodTier(rod).getEnchantMultiplier();

            // Apply bait bonuses
            BaitManager.ActiveBait activeBait = baitManager != null ? baitManager.getActiveBait(uuid) : null;
            double baitCatchBonus = activeBait != null ? activeBait.catchChanceBonus : 0.0;
            double baitWeightBonus = activeBait != null ? activeBait.weightBonus : 0.0;

            int luck = rod != null ? rod.getEnchantmentLevel(Enchantment.LUCK_OF_THE_SEA) : 0;
            double mult = (1.0 + (luck * 0.15) + streakBonus + baitCatchBonus) * fatigueMult;
            double multCap = config.getDouble("streak.multiplier-cap", 5.0);
            if (mult > multCap) mult = multCap;

            sendFishingActionBar(player);
            
            // Check for Legendary Map drop (base 1 in 10000 chance)
            // First check if player already has a compass/tracker or active quest
            boolean hasCompassOrTracker = false;
            if (legendaryQuestManager != null && legendaryQuestManager.getStage(player.getUniqueId()) > 0) {
                hasCompassOrTracker = true;
            }
            if (!hasCompassOrTracker) {
                NamespacedKey cKey = new NamespacedKey(plugin, "legendary_compass");
                NamespacedKey tKey = new NamespacedKey(plugin, "legendary_tracker");
                for (ItemStack invItem : player.getInventory().getContents()) {
                    if (invItem != null && invItem.hasItemMeta()) {
                        org.bukkit.persistence.PersistentDataContainer pdc = invItem.getItemMeta().getPersistentDataContainer();
                        if (pdc.has(cKey, org.bukkit.persistence.PersistentDataType.STRING) ||
                            pdc.has(tKey, org.bukkit.persistence.PersistentDataType.STRING)) {
                            hasCompassOrTracker = true;
                            break;
                        }
                    }
                }
            }
            
            if (!hasCompassOrTracker) {
                double baseDropChance = config.getDouble("legendary-quest.map-drop-chance", 0.0001);
                double bonusChance = 0.0;
                
                // Only Treasure Hunter affects compass drop, not bait
                if (enchantManager != null) {
                    int thLevel = enchantManager.getEnchantLevel(player.getInventory().getItemInMainHand(), net.rocky.coresuite.module.customfishing.FishingEnchantManager.CustomEnchant.TREASURE_HUNTER);
                    bonusChance += (thLevel * 0.0001); // 0.01% per level
                }
                double finalChance = baseDropChance + bonusChance;
                finalChance = Math.min(finalChance, 0.001); // Absolute max cap at 0.1% (1 in 1000)
                
                boolean gotMap = false;
                int pityThreshold = config.getInt("legendary-quest.pity-threshold", 2500);
                
                if (random.nextDouble() <= finalChance) {
                    gotMap = true;
                } else if (data.legendaryPity >= pityThreshold) {
                    gotMap = true;
                }
                
                if (gotMap && legendaryQuestManager != null) {
                    data.legendaryPity = 0; // Reset pity on drop
                    legendaryQuestManager.giveMap(player);
                    if (event.getCaught() != null) event.getCaught().remove();
                    return; // Stole the catch!
                } else {
                    // If fatigue is >= 75%, do NOT increment pity.
                    if (fatiguePercent < 0.75) {
                        data.legendaryPity++;
                    }
                }
            } else {
                // Player already has compass/tracker or active quest — still increment pity
                if (fatiguePercent < 0.75) {
                    data.legendaryPity++;
                }
            }

            List<String> rewards = config.getStringList("rewards");
            for (String rewardStr : rewards) {
                String[] parts = rewardStr.split("\\|", -1);
                if (parts.length < 4) continue;
                
                try {
                    double chance = Double.parseDouble(parts[0]);
                    String rarity = parts[1];
                    String name = parts[2];
                    String soundStr = parts[3];
                    String cmd = parts.length > 4 ? parts[4] : "";

                    double actualChance = chance * mult;
                    actualChance *= (1.0 + (treasureLvl * 0.2 * tierMult)) * eventRewardMult; // Treasure Hunter * rod tier
                    if (random.nextDouble() * 100.0 <= actualChance) {
                        
                        if (rarity.equalsIgnoreCase("junk")) {
                            String catchMsg = config.getString("messages.junk-catch-message", "&7You caught {name}&7... not exactly treasure.");
                            TextUtil.sendActionBar(player, catchMsg.replace("{name}", name));
                            return; // stop checking rewards
                        } else {
                            if (event.getHook() != null && !isOpenWater(event.getHook().getLocation())) {
                                continue; // Skip treasure if it's not open water
                            }
                        }

                        if (!cmd.isEmpty()) {
                            String parsedCmd = cmd.replace("{player}", player.getName());
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsedCmd);
                        }
                        
                        try {
                            Sound sound = Sound.valueOf(soundStr.toUpperCase().replace(".", "_"));
                            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
                        } catch (IllegalArgumentException ignored) {}

                        String prefix = config.getString("prefix", "&8[&b&lFishing&8] &r");
                        if (rarity.equalsIgnoreCase("rare") || rarity.equalsIgnoreCase("legendary")) {
                            TextUtil.sendTitle(player, "&d&l✦ Legendary Catch ✦", name + "&7!", 10, 100, 20);
                            Bukkit.broadcast(TextUtil.colorize(prefix + "&f" + player.getName() + " &7just reeled in " + name + "&7! &8(&b" + rarity + "&8)"));
                        } else {
                            TextUtil.sendTitle(player, "&a&l✦ Reward Found ✦", name + "&7!", 10, 80, 20);
                        }
                        
                        if (event.getCaught() != null) {
                            event.getCaught().remove();
                        }
                        return;
                    }
                } catch (NumberFormatException ignored) {}
            }

            if (config.getConfigurationSection("species") != null) {
                List<String> validSpecies = new ArrayList<>();
                for (String key : config.getConfigurationSection("species").getKeys(false)) {
                    String spPath = "species." + key + ".";
                    List<String> biomes = config.getStringList(spPath + "biomes");
                    if (biomes != null && !biomes.isEmpty()) {
                        boolean inPond = (eventRewardMult > 1.0);
                        if (!inPond) {
                            org.bukkit.block.Biome hookBiome = event.getHook().getLocation().getBlock().getBiome();
                            boolean match = false;
                            for (String b : biomes) {
                                if (hookBiome.getKey().getKey().equalsIgnoreCase(b)) {
                                    match = true;
                                    break;
                                }
                            }
                            if (!match) continue; // Skip this fish if the biome doesn't match and not in pond
                        }
                    }
                    validSpecies.add(key);
                }

                // Sort species by chance (rarest first) to accurately process rare pulls
                validSpecies.sort((a, b) -> {
                    double chanceA = config.getDouble("species." + a + ".chance", 100.0);
                    double chanceB = config.getDouble("species." + b + ".chance", 100.0);
                    return Double.compare(chanceA, chanceB);
                });

                for (String key : validSpecies) {
                    String spPath = "species." + key + ".";
                    try {
                        double chance = config.getDouble(spPath + "chance");
                        String rarity = config.getString(spPath + "rarity", "Common");
                        String name = config.getString(spPath + "name");
                        
                        if (name != null && !name.contains("&")) {
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
                        double minWeight = config.getDouble(spPath + "min-weight");
                        double maxWeight = config.getDouble(spPath + "max-weight");
                        double pricePerKg = config.getDouble(spPath + "price-per-kg");
                        String material = config.getString(spPath + "material", "COD");
                        String itemModel = config.getString(spPath + "item-model", "");
                        int cmd = config.getInt(spPath + "custom-model-data", -1);

                        double actualChance = chance * mult;
                        actualChance *= (1.0 + (lureLvl * 0.1 * tierMult)); // Lure of the deep * rod tier
                        
                        if (activeBait != null && activeBait.rarityBias != null && !activeBait.rarityBias.isEmpty()) {
                            if (activeBait.rarityBias.equalsIgnoreCase(rarity)) {
                                actualChance *= 1.5; // 50% boost to chance if rarity bias matches
                            }
                        }
                        if (random.nextDouble() * 100.0 <= actualChance) {
                            double weight = minWeight + (maxWeight - minWeight) * random.nextDouble();
                            weight *= (1.0 + (heavyLvl * 0.05 * tierMult) + baitWeightBonus) * eventWeightMult; // Heavyweight * rod tier + bait
                            weight = Math.round(weight * 100.0) / 100.0;
                            
                            double addFatigue = 0.0;
                            boolean inPond = (eventRewardMult > 1.0);
                            
                            if (!inPond) {
                                switch (rarity.toLowerCase()) {
                                    case "uncommon": addFatigue = 1.0; break;
                                    case "rare": addFatigue = 2.5; break;
                                    case "epic": addFatigue = 5.0; break;
                                    case "legendary": addFatigue = 15.0; break;
                                    case "mythical": addFatigue = 30.0; break;
                                    default: addFatigue = 0.5; break;
                                }
                                addFatigue += (weight * 0.02);
                                
                                int tirelessLvl = enchantManager.getEnchantLevel(rod, FishingEnchantManager.CustomEnchant.TIRELESS_CASTER);
                                if (tirelessLvl > 0) {
                                    // Reduces fatigue generated by 25% per level
                                    double reduction = tirelessLvl * 0.25;
                                    addFatigue = addFatigue * (1.0 - reduction);
                                }
                                
                                double oldFatigue = data.fatigueScore;
                                data.fatigueScore = Math.min(100.0, data.fatigueScore + addFatigue);
                                addFatigue = data.fatigueScore - oldFatigue;
                            } else {
                                // Legendary Pond heals fatigue!
                                double healAmount = config.getDouble("pond-event.fatigue-heal-amount", 5.0);
                                double oldScore = data.fatigueScore;
                                data.fatigueScore = Math.max(0.0, data.fatigueScore - healAmount);
                                addFatigue = -(oldScore - data.fatigueScore); // Store actual healed amount as negative
                            }
                            data.fatigueLastUpdate = System.currentTimeMillis();
                        
                            ItemStack fishItem = createFishItem(name, material, weight, pricePerKg, itemModel);
                        
                            int amount = 1;
                            if (doubleHookLvl > 0 && random.nextDouble() * 100.0 <= doubleHookLvl * 5.0 * tierMult) {
                                amount = 2;
                            }
                            fishItem.setAmount(amount);
                        
                            Map<Integer, ItemStack> leftover = player.getInventory().addItem(fishItem);
                        
                            if (!leftover.isEmpty()) {
                                ItemStack[] bag = fishBagManager.getBag(player.getUniqueId());
                                if (bag != null) {
                                    ItemStack itemToAdd = leftover.get(0).clone();
                                    boolean addedToBag = false;
                                    FishData bagData = playerData.get(player.getUniqueId());
                                    int unlockedPages = bagData != null ? Math.max(0, bagData.bagPages) : 0;
                                    int maxSlots = Math.min(bag.length, unlockedPages * 45);
                                    for (int i = 0; i < maxSlots; i++) {
                                        if (bag[i] == null || bag[i].getType() == org.bukkit.Material.AIR) {
                                            bag[i] = itemToAdd;
                                            fishBagManager.updateBag(player.getUniqueId(), bag);
                                            addedToBag = true;
                                            leftover.clear();
                                            break;
                                        }
                                    }
                                    if (!addedToBag) {
                                          for (org.bukkit.inventory.ItemStack drop : leftover.values()) {
                                              player.getWorld().dropItemNaturally(player.getLocation(), drop);
                                          }
                                          TextUtil.sendMessage(player, "&cYour inventory and bag are full! The fish dropped on the ground!");
                                      }
                                  }
                              }
                          
                              String fatigueMsg = "";
                              int thresholdCheck = config.getInt("fatigue.threshold", 75);
                              if (inPond && addFatigue < 0) {
                                  fatigueMsg = " &a(" + String.format("%.1f", addFatigue) + "% Fatigue)";
                              } else if (!inPond) {
                                  fatigueMsg = " &8(&c+" + String.format("%.1f", addFatigue) + "% Fatigue &8| &cTotal: " + String.format("%.1f", data.fatigueScore) + "%&8)";
                              }
                              player.sendMessage(TextUtil.colorize(config.getString("prefix", "&8[&b&lFishing&8] &r") + "&7You caught a &e" + name + " &8(&b" + weight + " kg&8)" + fatigueMsg));
                              
                              data.totalCatches++;
                              if (!inPond && data.fatigueScore > thresholdCheck) {
                                  actionbarOverride.put(player.getUniqueId(), System.currentTimeMillis() + 3000L);
                                  TextUtil.sendActionBar(player, "&cYou are too fatigued to gain XP! Rest or find a Legendary Pond.");
                              } else {
                                  int baseXp = config.getInt("leveling.xp-per-catch", 1);
                                  int earnedXp = baseXp;
                                  
                                  if (streak > 1 && random.nextDouble() * 100.0 <= (streak * 5.0)) {
                                      earnedXp += random.nextInt(3) + 1;
                                  }
                                  
                                  if (amount == 2) {
                                      earnedXp *= 2;
                                  }
                                  
                                  data.xp += earnedXp;
                                  int needed = data.level * config.getInt("leveling.xp-formula-multiplier", 20);
                                  if (data.xp >= needed) {
                                      data.xp -= needed;
                                      data.level++;
                                      String lvlMsg = config.getString("messages.level-up", "&e&lLevel Up! &7You are now Angler Level &b{level}");
                                      player.sendMessage(TextUtil.colorize(lvlMsg.replace("{level}", String.valueOf(data.level))));
                                      player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                                  }
                              }
                        
                            if (event.getCaught() != null) {
                                event.getCaught().remove();
                            }
                        
                        if (weight > maxWeight * 0.75) {
                            TextUtil.sendTitle(player, "&b&lHeavy Catch!", name + " &7- &b" + weight + "kg", 10, 60, 20);
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                        } else {
                            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                        }
                        
                        String speciesId = org.bukkit.ChatColor.stripColor(org.bukkit.ChatColor.translateAlternateColorCodes('&', name));
                        checkAndUpdatePersonalBest(player, speciesId, weight);
                        leaderboardManager.checkServerRecord(player, speciesId, weight, name);
                        
                        // Record tournament catch
                        if (tournamentManager != null) {
                            tournamentManager.recordCatch(player, weight);
                        }
                        // Consume bait cast
                        if (baitManager != null && activeBait != null) {
                            baitManager.consumeCast(player);
                        }
                        
                        return; // Stop checking
                    }
                } catch (NumberFormatException ignored) {}
            }
            } // end if config != null
            saveDataAsync(uuid);
            playerData.put(uuid, data);
        } // end if CAUGHT_FISH
    } // end onFish

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("fish")) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            
            if (args.length > 0) {
                // /fish quest <1|2|3|cancel> — for clickable chat buttons in quest dialogue
                if (args[0].equalsIgnoreCase("quest")) {
                    if (args.length < 2) {
                        TextUtil.sendMessage(player, "&cUsage: /fish quest <1|2|3|cancel>");
                        return true;
                    }
                    if (legendaryQuestManager != null && legendaryQuestManager.isInChatState(player.getUniqueId())) {
                        legendaryQuestManager.processChatSync(player, player.getUniqueId(), args[1]);
                    } else {
                        TextUtil.sendMessage(player, "&cYou are not in an active dialogue with the Ancient Angler.");
                    }
                    return true;
                } else if (args[0].equalsIgnoreCase("top")) {
                    openTopGUI(player);
                    return true;
                } else if (args[0].equalsIgnoreCase("sell")) {
                    openSellGUI(player, 0);
                    return true;
                } else if (args[0].equalsIgnoreCase("pond")) {
                    if (eventManager != null) {
                        if (eventManager.getActivePond() != null) {
                            org.bukkit.Location loc = eventManager.getActivePond();
                            TextUtil.sendMessage(player, config.getString("prefix", "&8[&b&lFishing&8] &r") + "&bThe Legendary Fish Pond is currently active at &e" + loc.getBlockX() + ", " + loc.getBlockZ() + "&b!");
                        } else if (eventManager.getFormingPond() != null) {
                            org.bukkit.Location loc = eventManager.getFormingPond();
                            TextUtil.sendMessage(player, config.getString("prefix", "&8[&b&lFishing&8] &r") + "&eA Legendary Fish Pond is currently &6forming &eat &b" + loc.getBlockX() + ", " + loc.getBlockZ() + "&e!");
                        } else {
                            TextUtil.sendMessage(player, config.getString("prefix", "&8[&b&lFishing&8] &r") + "&cThere is no Legendary Fish Pond active right now.");
                        }
                    } else {
                        TextUtil.sendMessage(player, config.getString("prefix", "&8[&b&lFishing&8] &r") + "&cThe Legendary Fish Pond event is currently disabled.");
                    }
                    return true;
                } else if (args[0].equalsIgnoreCase("fatigue")) {
                    FishData data = playerData.get(player.getUniqueId());
                    if (data == null) return true;
                    
                    calculateFatigue(player, data); // Process decay
                    int display = (int) Math.round(data.fatigueScore);
                    
                    if (display <= 0) {
                        TextUtil.sendMessage(player, config.getString("prefix", "&8[&b&lFishing&8] &r") + "&aYou are fully rested and have no fishing fatigue.");
                    } else {
                        String color = display >= 85 ? "&4" : display >= 75 ? "&c" : display >= 50 ? "&6" : "&e";
                        TextUtil.sendMessage(player, config.getString("prefix", "&8[&b&lFishing&8] &r") + "&7Current Fatigue: " + color + display + "%");
                        if (display >= 85) TextUtil.sendMessage(player, config.getString("prefix", "&8[&b&lFishing&8] &r") + "&7&o(You are experiencing severe blackouts)");
                        else if (display >= 75) TextUtil.sendMessage(player, config.getString("prefix", "&8[&b&lFishing&8] &r") + "&7&o(You are experiencing sea sickness)");
                    }
                    return true;
                } else if (args[0].equalsIgnoreCase("tournament") || args[0].equalsIgnoreCase("tourney")) {
                    if (tournamentManager != null) {
                        tournamentManager.openTournamentGUI(player);
                    }
                    return true;
                } else if (args[0].equalsIgnoreCase("bait")) {
                    if (baitManager != null) {
                        baitManager.openBaitShopGUI(player);
                    }
                    return true;
                } else if (args[0].equalsIgnoreCase("hof") || args[0].equalsIgnoreCase("halloffame")) {
                    if (leaderboardManager != null) {
                        leaderboardManager.openHallOfFameGUI(player);
                    }
                    return true;
                } else if (args[0].equalsIgnoreCase("buybag")) {
                    FishData bData = playerData.get(player.getUniqueId());
                    int currentPages = bData != null ? bData.bagPages : 0;
                    if (currentPages >= 10) {
                        TextUtil.sendMessage(player, "&cYou have already reached the maximum amount of bag pages.");
                        return true;
                    }
                    int nextPage = currentPages + 1;
                    double cost = config.getDouble("bag-pages." + nextPage + ".price", -1.0);
                    if (cost == -1.0) cost = 10000.0 + (nextPage * 15000.0);
                    int reqLevel = config.getInt("bag-pages." + nextPage + ".level-required", -1);
                    if (reqLevel == -1) reqLevel = nextPage * 5;
                    if (nextPage > 1 && bData != null && bData.level < reqLevel) {
                        TextUtil.sendMessage(player, "&cYou need Fishing Level &a" + reqLevel + " &cto purchase this page.");
                        return true;
                    }
                    
                    double bal = plugin.getEconomyManager().getBalance(player);
                    if (bal < cost) {
                        TextUtil.sendMessage(player, "&cYou do not have enough money. Cost: &a$" + String.format("%.2f", cost));
                        return true;
                    }
                    plugin.getEconomyManager().withdraw(player, cost);
                    
                    if (bData == null) bData = new FishData(1, 0, 0, 0);
                    bData.bagPages = nextPage;
                    playerData.put(player.getUniqueId(), bData);
                    
                    TextUtil.sendMessage(player, "&aSuccessfully purchased Fish Bag Page " + nextPage + " for $" + String.format("%.2f", cost) + "!");
                    player.closeInventory();
                    return true;
                } else if (args[0].equalsIgnoreCase("bag")) {
                    FishData bData = playerData.get(player.getUniqueId());
                    if (bData == null || bData.bagPages <= 0) {
                        openBuyBagGUI(player);
                        return true;
                    }
                    fishBagManager.openBag(player, bData.bagPages, 0);
                    return true;
                } else if (args[0].equalsIgnoreCase("info")) {
                    new FishingCollectionManager(plugin, this).openCollectionGUI(player);
                    return true;

                } else if (args[0].equalsIgnoreCase("log")) {
                    // Deprecated or removed, using info now
                    new FishingCollectionManager(plugin, this).openCollectionGUI(player);
                    return true;
                } else if (args[0].equalsIgnoreCase("shop")) {
                    openShopGUI(player);
                    return true;
                } else if (args[0].equalsIgnoreCase("admin")) {
                    if (!player.hasPermission("coresuite.customfishing.admin")) {
                        TextUtil.sendMessage(player, "&cNo permission.");
                        return true;
                    }
                    if (args.length < 2) {
                        TextUtil.sendMessage(player, "&cUsage: /fish admin <setlevel|addxp|fatigue|clearlog|forcepond|give> [player] [args]");
                        return true;
                    }
                    String sub = args[1].toLowerCase();
                    
                                        if (sub.equals("legendary")) {
                        if (args.length > 2 && args[2].equalsIgnoreCase("givemap")) {
                            Player target = args.length > 3 ? Bukkit.getPlayer(args[3]) : player;
                            if (target != null && legendaryQuestManager != null) {
                                legendaryQuestManager.giveMap(target);
                                TextUtil.sendMessage(player, "&aGave Legendary Fisherman's Compass to " + target.getName());
                            } else {
                                TextUtil.sendMessage(player, "&cPlayer not found.");
                            }
                        } else if (args.length > 3 && args[2].equalsIgnoreCase("setstage")) {
                            Player target = Bukkit.getPlayer(args[3]);
                            if (target != null && legendaryQuestManager != null) {
                                try {
                                    int stage = Integer.parseInt(args[4]);
                                    legendaryQuestManager.setStage(target.getUniqueId(), stage);
                                    TextUtil.sendMessage(player, "&aSet " + target.getName() + "'s legendary quest stage to " + stage);
                                } catch (Exception e) {
                                    TextUtil.sendMessage(player, "&cUsage: /fish admin legendary setstage <player> <stage>");
                                }
                            } else {
                                TextUtil.sendMessage(player, "&cPlayer not found.");
                            }
                        } else if (args.length > 2 && args[2].equalsIgnoreCase("reset")) {
                            Player target = args.length > 3 ? Bukkit.getPlayer(args[3]) : player;
                            if (target != null && legendaryQuestManager != null) {
                                legendaryQuestManager.setStage(target.getUniqueId(), 0);
                                legendaryNPCManager.removeNPC(target.getUniqueId());
                                TextUtil.sendMessage(player, "&aReset " + target.getName() + "'s legendary quest progress.");
                            } else {
                                TextUtil.sendMessage(player, "&cPlayer not found.");
                            }
                        } else {
                            TextUtil.sendMessage(player, "&cUsage: /fish admin legendary <givemap|setstage|reset>");
                        }
                        return true;
                    }
                    if (sub.equals("clearanglers")) {
                        if (wanderingAnglerManager != null) {
                            int removed = wanderingAnglerManager.clearAllAnglers();
                            TextUtil.sendMessage(player, "&aSuccessfully removed &e" + removed + " &aWandering Angler(s) from all loaded chunks.");
                        } else {
                            TextUtil.sendMessage(player, "&cWandering Angler feature is disabled.");
                        }
                        return true;
                    }
                    if (sub.equals("spawnangler")) {
                        if (wanderingAnglerManager != null) {
                            wanderingAnglerManager.spawnAngler(player.getLocation());
                            TextUtil.sendMessage(player, "&aForce-spawned a Wandering Angler!");
                        } else {
                            TextUtil.sendMessage(player, "&cWandering Angler feature is disabled.");
                        }
                        return true;
                    }
                    if (sub.equals("forcepond")) {
                        if (eventManager != null) {
                            eventManager.forceStartEvent(player.getLocation());
                            TextUtil.sendMessage(player, "&aForce-started a Legendary Fish Pond near your location!");
                        }
                        return true;
                    }
                    
                    if (args.length < 3) {
                        TextUtil.sendMessage(player, "&cUsage: /fish admin " + sub + " <player> [args]");
                        return true;
                    }
                    
                    Player target = Bukkit.getPlayer(args[2]);
                    if (target == null) {
                        TextUtil.sendMessage(player, "&cPlayer not found.");
                        return true;
                    }
                    UUID tUuid = target.getUniqueId();
                    
                    if (sub.equals("clearlog")) {
                        plugin.getDatabaseManager().execute("DELETE FROM coresuite_fish_logbook WHERE uuid = ?", tUuid.toString());
                        TextUtil.sendMessage(player, "&aCleared fishing log for " + target.getName() + ".");
                        return true;
                    }
                    if (sub.equals("givebait")) {
                        if (args.length < 4) {
                            TextUtil.sendMessage(player, "&cUsage: /fish admin givebait <player> <bait_id> [amount]");
                            return true;
                        }
                        String baitId = args[3].toLowerCase();
                        int giveAmount = args.length > 4 ? Integer.parseInt(args[4]) : 1;
                        if (baitManager != null) {
                            ItemStack baitItem = baitManager.createBaitItem(baitId);
                            if (baitItem != null) {
                                baitItem.setAmount(giveAmount);
                                target.getInventory().addItem(baitItem);
                                TextUtil.sendMessage(player, "&aGave " + giveAmount + "x " + baitId + " to " + target.getName() + ".");
                            } else {
                                TextUtil.sendMessage(player, "&cBait ID '" + baitId + "' not found.");
                            }
                        }
                        return true;
                    }
                    if (sub.equals("give")) {
                        if (args.length < 4) {
                            TextUtil.sendMessage(player, "&cUsage: /fish admin give <player> <fish_id> [amount]");
                            return true;
                        }
                        String fishId = args[3];
                        int giveAmount = args.length > 4 ? Integer.parseInt(args[4]) : 1;
                        String spPath = "species." + fishId + ".";
                        if (config.getConfigurationSection(spPath) == null) {
                            TextUtil.sendMessage(player, "&cFish '" + fishId + "' not found.");
                            return true;
                        }
                        String name = config.getString(spPath + "name");
                        double minWeight = config.getDouble(spPath + "min-weight");
                        double maxWeight = config.getDouble(spPath + "max-weight");
                        double pricePerKg = config.getDouble(spPath + "price-per-kg");
                        String material = config.getString(spPath + "material", "COD");
                        String itemModel = config.getString(spPath + "item-model", "");
                        double weight = minWeight + (maxWeight - minWeight) * random.nextDouble();
                        weight = Math.round(weight * 100.0) / 100.0;
                        ItemStack fishItem = createFishItem(name, material, weight, pricePerKg, itemModel);
                        fishItem.setAmount(giveAmount);
                        java.util.Map<Integer, org.bukkit.inventory.ItemStack> leftovers = target.getInventory().addItem(fishItem);
                        for (org.bukkit.inventory.ItemStack drop : leftovers.values()) {
                            target.getWorld().dropItemNaturally(target.getLocation(), drop);
                        }
                        if (!leftovers.isEmpty()) TextUtil.sendMessage(target, "&cYour inventory was full, so some fish dropped on the floor!");
                        TextUtil.sendMessage(player, "&aGave " + giveAmount + "x " + fishId + " to " + target.getName() + ".");
                        return true;
                    }
                    if (args.length < 4) {
                        TextUtil.sendMessage(player, "&cUsage: /fish admin " + sub + " <player> <amount>");
                        return true;
                    }
                    int amount = 0;
                    try {
                        amount = Integer.parseInt(args[3]);
                    } catch (NumberFormatException e) {
                        TextUtil.sendMessage(player, "&cInvalid number.");
                        return true;
                    }
                    
                    FishData tData = playerData.getOrDefault(tUuid, new FishData(1, 0, 0, 0));
                    if (sub.equals("setlevel")) {
                        tData.level = amount;
                        playerData.put(tUuid, tData);
                        TextUtil.sendMessage(player, "&aSet " + target.getName() + "'s fishing level to " + amount + ".");
                    } else if (sub.equals("addxp")) {
                        tData.xp += amount;
                        playerData.put(tUuid, tData);
                        TextUtil.sendMessage(player, "&aAdded " + amount + " XP to " + target.getName() + ".");
                    } else if (sub.equals("fatigue")) {
                        tData.fatigueScore = Math.max(0.0, Math.min(100.0, amount));
                        playerData.put(tUuid, tData);
                        TextUtil.sendMessage(player, "&aSet " + target.getName() + "'s fatigue to " + amount + "%.");
                    } else {
                        TextUtil.sendMessage(player, "&cUnknown admin action.");
                    }
                    return true;
                } else if (args[0].equalsIgnoreCase("debugfish")) {
                    if (!player.hasPermission("coresuite.customfishing.admin")) {
                        TextUtil.sendMessage(player, "&cNo permission.");
                        return true;
                    }
                    if (args.length < 2) {
                        TextUtil.sendMessage(player, "&cUsage: /fish debugfish <material> - e.g. /fish debugfish COD");
                        return true;
                    }
                    String material = args[1];
                    ItemStack fishItem = createFishItem("&aDebug Fish", material, 5.0, 10.0);
                    player.getInventory().addItem(fishItem);
                    TextUtil.sendMessage(player, "&aGiven debug fish item (" + material + "). Check your inventory!");
                    return true;

                }
            }
            
            FishData data = playerData.get(player.getUniqueId());
            String prefix = config.getString("prefix", "&8[&b&lFishing&8] &r");
            if (data == null || data.totalCatches == 0) {
                TextUtil.sendMessage(player, prefix + "&7You haven't started fishing yet - go catch something!");
                return true;
            }
            
            openMainMenu(player, data);
            return true;
        }
        return false;
    }

    

    private void sendDetailedStats(Player player, FishData data) {
        ItemStack rod = player.getInventory().getItemInMainHand();
        if (rod == null || rod.getType() != org.bukkit.Material.FISHING_ROD) {
            TextUtil.sendMessage(player, config.getString("prefix", "&8[&b&lFishing&8] &r") + "&cYou must be holding a Fishing Rod to view detailed stats!");
            return;
        }

        double fatiguePercent = calculateFatigue(player, data);
        double fatigueMult = 1.0 - fatiguePercent;
        
        int luck = rod.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA);
        
        BaitManager.ActiveBait activeBait = baitManager != null ? baitManager.getActiveBait(player.getUniqueId()) : null;
        double baitCatchBonus = activeBait != null ? activeBait.catchChanceBonus : 0.0;
        double baitWeightBonus = activeBait != null ? activeBait.weightBonus : 0.0;
        
        int streak = fishStreak.getOrDefault(player.getUniqueId(), 0);
        double streakBonus = Math.pow(streak, 0.6) * 0.045;

        double baseCatchMult = (1.0 + (luck * 0.15) + streakBonus + baitCatchBonus) * fatigueMult;
        double multCap = config.getDouble("streak.multiplier-cap", 5.0);
        if (baseCatchMult > multCap) baseCatchMult = multCap;
        
        double tierMult = 1.0;
        int lureLvl = 0;
        int heavyLvl = 0;
        int doubleHookLvl = 0;
        int treasureLvl = 0;
        
        if (enchantManager != null) {
            tierMult = enchantManager.getRodTier(rod).getEnchantMultiplier();
            lureLvl = enchantManager.getEnchantLevel(rod, net.rocky.coresuite.module.customfishing.FishingEnchantManager.CustomEnchant.LURE_OF_THE_DEEP);
            heavyLvl = enchantManager.getEnchantLevel(rod, net.rocky.coresuite.module.customfishing.FishingEnchantManager.CustomEnchant.HEAVYWEIGHT);
            doubleHookLvl = enchantManager.getEnchantLevel(rod, net.rocky.coresuite.module.customfishing.FishingEnchantManager.CustomEnchant.DOUBLE_HOOK);
            treasureLvl = enchantManager.getEnchantLevel(rod, net.rocky.coresuite.module.customfishing.FishingEnchantManager.CustomEnchant.TREASURE_HUNTER);
        }

        double fishCatchBonusMult = baseCatchMult * (1.0 + (lureLvl * 0.1 * tierMult));
        double weightBonusMult = (1.0 + (heavyLvl * 0.05 * tierMult) + baitWeightBonus);
        double treasureBonusMult = baseCatchMult * (1.0 + (treasureLvl * 0.2 * tierMult));
        double doubleHookChance = (doubleHookLvl * 0.05 * tierMult) * 100.0;

        TextUtil.sendMessage(player, "&8&m----------------------------------------");
        TextUtil.sendMessage(player, "&b&lDetailed Fishing Stats");
        TextUtil.sendMessage(player, "&7Based on your current rod, bait, and conditions:");
        TextUtil.sendMessage(player, "");
        TextUtil.sendMessage(player, "&7General Catch Multiplier: &a" + String.format("%.2fx", baseCatchMult) + (fatiguePercent > 0 ? " &c(Fatigue Penalty Active)" : ""));
        TextUtil.sendMessage(player, "&7Fish Catch Rate Multiplier: &a" + String.format("%.2fx", fishCatchBonusMult));
        TextUtil.sendMessage(player, "&7Fish Weight Multiplier: &e" + String.format("%.2fx", weightBonusMult));
        TextUtil.sendMessage(player, "&7Treasure Find Multiplier: &d" + String.format("%.2fx", treasureBonusMult));
        if (doubleHookChance > 0) {
            TextUtil.sendMessage(player, "&7Double Hook Chance: &6" + String.format("%.1f%%", doubleHookChance));
        }
        TextUtil.sendMessage(player, "&8&m----------------------------------------");
    }

    private void openBuyBagGUI(Player player) {
        net.rocky.coresuite.common.gui.Gui gui = new net.rocky.coresuite.common.gui.Gui("&8Purchase Fish Bag?", 3);
        gui.fillBorder(org.bukkit.Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        
        FishData bData = playerData.get(player.getUniqueId());
        int currentPages = bData != null ? bData.bagPages : 0;
        double cost = config.getDouble("bag-pages." + (currentPages + 1) + ".price", -1.0);
        if (cost == -1.0) cost = 10000.0 + ((currentPages + 1) * 15000.0);
        int reqLevel = config.getInt("bag-pages." + (currentPages + 1) + ".level-required", -1);
        if (reqLevel == -1) reqLevel = (currentPages + 1) * 5;
        
        gui.setItem(11, net.rocky.coresuite.common.gui.GuiButton.of(
            org.bukkit.Material.LIME_DYE,
            "&a&lYes, Purchase",
            java.util.Arrays.asList(
                "&7Cost: &a$" + String.format("%.2f", cost),
                "",
                "&7Click to purchase!"
            ),
            e -> {
                // player.closeInventory();
                player.performCommand("fish buybag");
            }
        ));
        
        gui.setItem(15, net.rocky.coresuite.common.gui.GuiButton.of(
            org.bukkit.Material.RED_DYE,
            "&c&lCancel",
            java.util.Arrays.asList("&7Nevermind."),
            e -> player.closeInventory()
        ));
        
        gui.open(player);
    }
    public void removeLevels(Player player, int amount) {
        int level = player.getLevel() - amount;
        if (level < 0) level = 0;
        player.setLevel(level);
    }

    private void openShopGUI(Player player) {
        net.rocky.coresuite.common.gui.Gui gui = new net.rocky.coresuite.common.gui.Gui("&8Fishing Shop", 4);
        gui.fillBorder(org.bukkit.Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        
        ItemStack rod = player.getInventory().getItemInMainHand();
        if (rod == null || rod.getType() != org.bukkit.Material.FISHING_ROD) {
            gui.setItem(13, net.rocky.coresuite.common.gui.GuiButton.of(
                org.bukkit.Material.BARRIER,
                "&c&lNo Fishing Rod",
                Arrays.asList("&7You must hold a fishing rod in your main hand", "&7to purchase enchantments."),
                e -> {}
            ));
            gui.open(player);
            return;
        }

        int[] slots = {10, 12, 14, 16};
        FishingEnchantManager.CustomEnchant[] enchants = FishingEnchantManager.CustomEnchant.values();

        for (int i = 0; i < enchants.length && i < slots.length; i++) {
            FishingEnchantManager.CustomEnchant ce = enchants[i];
            int level = enchantManager.getEnchantLevel(rod, ce);
            
            String configBase = "enchants." + ce.getConfigKey() + ".";
            String name = config.getString(configBase + "name", ce.getDisplayName());
            String desc = config.getString(configBase + "description", "");
            double basePrice = config.getDouble(configBase + "base-price", 1000.0);
            double priceMult = config.getDouble(configBase + "price-multiplier", 1.5);
            int maxLevel = config.getInt(configBase + "max-level", ce.getMaxLevel());
            
            int reqXp = (level + 1) * config.getInt(configBase + "xp-required", 0);
            int reqCatches = (level + 1) * config.getInt(configBase + "catches-required", 0);
            
            FishData pData = playerData.get(player.getUniqueId());
            int pXp = player.getLevel(); // Use XP Levels instead of total experience points
            int pCatches = pData != null ? pData.totalCatches : 0;
            
            if (level >= maxLevel) {
                gui.setItem(slots[i], net.rocky.coresuite.common.gui.GuiButton.of(
                    org.bukkit.Material.ENCHANTED_BOOK,
                    name,
                    Arrays.asList(
                        "&7" + desc,
                        "",
                        "&c&lMAX LEVEL REACHED"
                    ),
                    e -> {
                        TextUtil.sendMessage(player, "&cThat enchantment is already at max level.");
                    }
                ));
            } else {
                double cost = basePrice * Math.pow(priceMult, level);
                List<String> lore = new ArrayList<>(Arrays.asList(
                    "&7" + desc,
                    "",
                    "&7Current Level: &b" + level,
                    "&7Next Level: &b" + (level + 1),
                    "&7Cost: &a$" + String.format("%.2f", cost)
                ));
                
                boolean meetsReq = true;
                if (reqXp > 0) {
                    String color = pXp >= reqXp ? "&a" : "&c";
                    lore.add(color + "Required XP Levels: " + reqXp + " &8(" + pXp + ")");
                    if (pXp < reqXp) meetsReq = false;
                }
                if (reqCatches > 0) {
                    String color = pCatches >= reqCatches ? "&a" : "&c";
                    lore.add(color + "Required Catches: " + reqCatches + " &8(" + pCatches + ")");
                    if (pCatches < reqCatches) meetsReq = false;
                }
                
                lore.add("");
                if (meetsReq) lore.add("&eClick to upgrade!");
                else lore.add("&cRequirements not met!");

                gui.setItem(slots[i], net.rocky.coresuite.common.gui.GuiButton.of(
                    org.bukkit.Material.ENCHANTED_BOOK,
                    name,
                    lore,
                    e -> {
                        if (pXp < reqXp || pCatches < reqCatches) {
                            TextUtil.sendMessage(player, "&cYou do not meet the requirements to upgrade this.");
                            return;
                        }
                        double bal = plugin.getEconomyManager().getBalance(player);
                        if (bal < cost) {
                            TextUtil.sendMessage(player, "&cYou do not have enough money to upgrade this.");
                            return;
                        }
                        plugin.getEconomyManager().withdraw(player, cost);
                        if (reqXp > 0) removeLevels(player, reqXp); // Remove XP Levels
                        enchantManager.setEnchantLevel(rod, ce, level + 1);
                        TextUtil.sendMessage(player, "&aSuccessfully upgraded " + name + " &ato level " + (level + 1) + "!");
                        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
                        openShopGUI(player); // Refresh GUI
                    }
                ));
            }
        }
            // Rod Tier Upgrade Button
            if (rod != null && rod.getType() == org.bukkit.Material.FISHING_ROD) {
                FishingEnchantManager.RodTier currentTier = enchantManager.getRodTier(rod);
                FishingEnchantManager.RodTier nextTier = currentTier.getNextTier();
                
                if (nextTier != null) {
                    String tierConfigKey = nextTier.name().toLowerCase();
                    double tierCost = config.getDouble("rod-tiers." + tierConfigKey + ".price", 50000.0 * nextTier.getOrdinalIndex());
                    int tierLevelReq = config.getInt("rod-tiers." + tierConfigKey + ".level-required", 5 * nextTier.getOrdinalIndex());
                    FishData pData2 = playerData.get(player.getUniqueId());
                    
                    ItemStack tierIcon = new ItemStack(org.bukkit.Material.ANVIL);
                    org.bukkit.inventory.meta.ItemMeta tierMeta = tierIcon.getItemMeta();
                    if (tierMeta != null) {
                        tierMeta.displayName(TextUtil.colorize("&e&lUpgrade Rod Quality"));
                        List<net.kyori.adventure.text.Component> tierLore = new ArrayList<>();
                        tierLore.add(TextUtil.colorize("&7Current: " + currentTier.getDisplayName()));
                        tierLore.add(TextUtil.colorize("&7Next: " + nextTier.getDisplayName()));
                        tierLore.add(net.kyori.adventure.text.Component.empty());
                        tierLore.add(TextUtil.colorize("&7Enchant Power: &b" + String.format("%.0f", currentTier.getEnchantMultiplier() * 100) + "% &8→ &a" + String.format("%.0f", nextTier.getEnchantMultiplier() * 100) + "%"));
                        tierLore.add(net.kyori.adventure.text.Component.empty());
                        tierLore.add(TextUtil.colorize("&7Cost: &e$" + plugin.getEconomyManager().format(tierCost)));
                        tierLore.add(TextUtil.colorize("&7Level Required: &b" + tierLevelReq));
                        tierLore.add(net.kyori.adventure.text.Component.empty());
                        boolean canAfford = plugin.getEconomyManager().has(player, tierCost);
                        boolean hasLevel = pData2 != null && pData2.level >= tierLevelReq;
                        
                        int totalEnchants = 0;
                        for (FishingEnchantManager.CustomEnchant ce : FishingEnchantManager.CustomEnchant.values()) {
                            totalEnchants += enchantManager.getEnchantLevel(rod, ce);
                        }
                        boolean hasEnchants = totalEnchants > 0;
                        
                        if (canAfford && hasLevel && hasEnchants) {
                            tierLore.add(TextUtil.colorize("&a&lClick to upgrade!"));
                        } else {
                            if (!hasEnchants) tierLore.add(TextUtil.colorize("&c✗ Requires at least 1 Custom Enchant"));
                            if (!hasLevel) tierLore.add(TextUtil.colorize("&c✗ Need Angler Level " + tierLevelReq));
                            if (!canAfford) tierLore.add(TextUtil.colorize("&c✗ Not enough money"));
                        }
                        tierMeta.lore(tierLore);
                        tierIcon.setItemMeta(tierMeta);
                    }
                    
                    gui.setItem(22, new net.rocky.coresuite.common.gui.GuiButton(tierIcon, e -> {
                        FishData pd = playerData.get(player.getUniqueId());
                        if (pd == null || pd.level < tierLevelReq) {
                            TextUtil.sendMessage(player, "&cYou need Angler Level " + tierLevelReq + " for this upgrade.");
                            return;
                        }
                        if (!plugin.getEconomyManager().has(player, tierCost)) {
                            TextUtil.sendMessage(player, "&cYou don't have enough money for this upgrade.");
                            return;
                        }
                        ItemStack heldRod = player.getInventory().getItemInMainHand();
                        if (heldRod.getType() != org.bukkit.Material.FISHING_ROD) {
                            TextUtil.sendMessage(player, "&cYou must hold a fishing rod in your main hand!");
                            return;
                        }
                        int totalClickEnchants = 0;
                        for (FishingEnchantManager.CustomEnchant ce : FishingEnchantManager.CustomEnchant.values()) {
                            totalClickEnchants += enchantManager.getEnchantLevel(heldRod, ce);
                        }
                        if (totalClickEnchants == 0) {
                            TextUtil.sendMessage(player, "&cYou must purchase at least 1 Custom Enchant before upgrading the rod quality!");
                            return;
                        }
                        plugin.getEconomyManager().withdraw(player, tierCost);
                        enchantManager.setRodTier(heldRod, nextTier);
                        TextUtil.sendMessage(player, "&a&lRod upgraded to " + nextTier.getDisplayName() + " Quality&a!");
                        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.0f);
                        // player.closeInventory();
                        openShopGUI(player);
                    }));
                } else {
                    // Max tier reached
                    gui.setItem(22, net.rocky.coresuite.common.gui.GuiButton.of(org.bukkit.Material.NETHERITE_INGOT, "&4&lMax Rod Quality", java.util.Arrays.asList("&7Your rod is at the highest quality!", "&7" + currentTier.getDisplayName() + " &8(&b" + String.format("%.0f", currentTier.getEnchantMultiplier() * 100) + "% power&8)"), e -> {}));
                }
            }

        gui.setItem(35, net.rocky.coresuite.common.gui.GuiButton.of(org.bukkit.Material.ARROW, "&c&lBack", java.util.Arrays.asList("&7Return to the main menu."), e -> player.performCommand("fish")));
        gui.open(player);
    }

    private double sellFishSilent(Player player, ItemStack fish) {
        boolean removed = false;
        if (player.getInventory().contains(fish)) {
            player.getInventory().removeItem(fish);
            removed = true;
        } else {
            ItemStack[] bag = fishBagManager.getBag(player.getUniqueId());
            if (bag != null) {
                for (int i = 0; i < bag.length; i++) {
                    if (bag[i] != null && bag[i].equals(fish)) {
                        bag[i] = null;
                        fishBagManager.updateBag(player.getUniqueId(), bag);
                        fishBagManager.saveBag(player.getUniqueId());
                        removed = true;
                        break;
                    }
                }
            }
        }
        
        if (!removed) return 0;
        
        org.bukkit.inventory.meta.ItemMeta meta = fish.getItemMeta();
        if (meta == null) return 0;
        double weight = meta.getPersistentDataContainer().getOrDefault(weightKey, PersistentDataType.DOUBLE, 0.0);
        double price = meta.getPersistentDataContainer().getOrDefault(priceKey, PersistentDataType.DOUBLE, 0.0);
        double total = weight * price * fish.getAmount();
        
        plugin.getEconomyManager().deposit(player, total);
        return total;
    }
    
    private void sellFish(Player player, ItemStack fish) {
        double total = sellFishSilent(player, fish);
        if (total > 0) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            player.sendMessage(TextUtil.colorize(config.getString("prefix", "&8[&b&lFishing&8] &r") + "&7Sold fish for &a$" + String.format("%.2f", total) + "&7!"));
        }
    }

    public void syncFishItemConfig(ItemStack fish) {
        if (!isFishItem(fish)) return;
        org.bukkit.inventory.meta.ItemMeta meta = fish.getItemMeta();
        if (meta == null) return;
        
        String speciesId = meta.getPersistentDataContainer().get(speciesKey, org.bukkit.persistence.PersistentDataType.STRING);
        if (speciesId == null) return;
        
        if (config.getConfigurationSection("species") != null) {
            for (String key : config.getConfigurationSection("species").getKeys(false)) {
                String spPath = "species." + key + ".";
                String confName = config.getString(spPath + "name", "");
                String confSpeciesId = org.bukkit.ChatColor.stripColor(org.bukkit.ChatColor.translateAlternateColorCodes('&', confName));
                
                if (speciesId.equals(confSpeciesId)) {
                    double confPrice = config.getDouble(spPath + "price-per-kg", 0.0);
                    double currentPrice = meta.getPersistentDataContainer().getOrDefault(priceKey, org.bukkit.persistence.PersistentDataType.DOUBLE, 0.0);
                    
                    if (Math.abs(confPrice - currentPrice) > 0.01) {
                        double weight = meta.getPersistentDataContainer().getOrDefault(weightKey, org.bukkit.persistence.PersistentDataType.DOUBLE, 0.0);
                        
                        meta.getPersistentDataContainer().set(priceKey, org.bukkit.persistence.PersistentDataType.DOUBLE, confPrice);
                        
                        meta.displayName(TextUtil.colorize(confName));
                        List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                        List<String> confLore = config.getStringList(spPath + "lore");
                        if (confLore != null) {
                            for (String line : confLore) {
                                lore.add(TextUtil.colorize(line));
                            }
                        }
                        lore.add(TextUtil.colorize("&7Weight: &b" + weight + " kg"));
                        if (confPrice > 0) {
                            lore.add(TextUtil.colorize("&7Value: &a$" + String.format("%.2f", weight * confPrice)));
                        }
                        
                        meta.lore(lore);
                        fish.setItemMeta(meta);
                    }
                    break;
                }
            }
        }
    }

    private void openSellGUI(Player player, int page) {
        net.rocky.coresuite.common.gui.Gui gui = new net.rocky.coresuite.common.gui.Gui("&8Sell Fish", 6);
        
        List<ItemStack> fishItems = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) {
            if (isFishItem(item)) {
                syncFishItemConfig(item);
                fishItems.add(item);
            }
        }
        
        ItemStack[] bag = fishBagManager.getBag(player.getUniqueId());
        if (bag != null) {
            for (ItemStack item : bag) {
                if (isFishItem(item)) {
                    syncFishItemConfig(item);
                    fishItems.add(item);
                }
            }
        }
        
        int start = page * 45;
        int end = start + 45;
        int slot = 0;
        for (int i = start; i < Math.min(end, fishItems.size()); i++) {
            ItemStack fish = fishItems.get(i);
            gui.setItem(slot, new net.rocky.coresuite.common.gui.GuiButton(fish.clone(), e -> {
                sellFish(player, fish);
                fishBagManager.compactBag(player.getUniqueId());
                openSellGUI(player, page); // refresh
            }));
            slot++;
        }
        
        for (int i = 45; i < 54; i++) {
            gui.setItem(i, net.rocky.coresuite.common.gui.GuiButton.of(org.bukkit.Material.GRAY_STAINED_GLASS_PANE, " ", new ArrayList<>(), e -> {}));
        }
        
        if (page > 0) {
            gui.setItem(45, net.rocky.coresuite.common.gui.GuiButton.of(org.bukkit.Material.ARROW, "&aPrevious Page", java.util.Arrays.asList(""), e -> openSellGUI(player, page - 1)));
        }
        if (end < fishItems.size()) {
            gui.setItem(53, net.rocky.coresuite.common.gui.GuiButton.of(org.bukkit.Material.ARROW, "&aNext Page", java.util.Arrays.asList(""), e -> openSellGUI(player, page + 1)));
        }
        
        double totalValue = 0;
        for (ItemStack fish : fishItems) {
            org.bukkit.inventory.meta.ItemMeta meta = fish.getItemMeta();
            if (meta != null) {
                double weight = meta.getPersistentDataContainer().getOrDefault(weightKey, org.bukkit.persistence.PersistentDataType.DOUBLE, 0.0);
                double price = meta.getPersistentDataContainer().getOrDefault(priceKey, org.bukkit.persistence.PersistentDataType.DOUBLE, 0.0);
                totalValue += weight * price * fish.getAmount();
            }
        }
        
        gui.setItem(49, net.rocky.coresuite.common.gui.GuiButton.of(
            org.bukkit.Material.EMERALD,
            "&a&lSell All",
            Arrays.asList(
                "&7Click to sell all fish in your inventory.",
                "&7Total Value: &a$" + String.format("%.2f", totalValue)
            ),
            e -> {
                double total = 0;
                for (ItemStack fish : fishItems) {
                    total += sellFishSilent(player, fish);
                }
                if (total > 0) {
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    player.sendMessage(TextUtil.colorize(config.getString("prefix", "&8[&b&lFishing&8] &r") + "&7Sold all fish for &a$" + String.format("%.2f", total) + "&7!"));
                }
                fishBagManager.compactBag(player.getUniqueId());
                openSellGUI(player, page);
            }
        ));
        
        gui.setItem(50, net.rocky.coresuite.common.gui.GuiButton.of(org.bukkit.Material.BARRIER, "&c&lBack", Arrays.asList("&7Return to main menu."), e -> player.performCommand("fish")));
        
        gui.open(player);
    }

    private void openLogGUI(Player player) {
        net.rocky.coresuite.common.gui.Gui gui = new net.rocky.coresuite.common.gui.Gui("&8Fishing Logbook", 6);
        gui.fillBorder(org.bukkit.Material.GRAY_STAINED_GLASS_PANE);
        
        plugin.getDatabaseManager().executeAsync(() -> {
            Map<String, Double> caughtSpecies = new HashMap<>();
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT species_id, max_weight FROM coresuite_fish_logbook WHERE uuid = ?")) {
                stmt.setString(1, player.getUniqueId().toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        caughtSpecies.put(rs.getString("species_id"), rs.getDouble("max_weight"));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load fish logbook", e);
            }
            
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                int slot = 10;
                if (config.getConfigurationSection("species") != null) {
                    for (String key : config.getConfigurationSection("species").getKeys(false)) {
                        String spPath = "species." + key + ".";
                        String name = config.getString(spPath + "name", "&8???");
                        String speciesId = org.bukkit.ChatColor.stripColor(org.bukkit.ChatColor.translateAlternateColorCodes('&', name));
                        
                        while (slot % 9 == 0 || slot % 9 == 8) slot++;
                        if (slot >= 53) break;
                        
                        if (caughtSpecies.containsKey(speciesId)) {
                            double maxWeight = caughtSpecies.get(speciesId);
                            String material = config.getString(spPath + "material", "COD");
                            String itemModel = config.getString(spPath + "item-model", "");
                            ItemStack fishItem = createFishItem(name, material, maxWeight, 0, itemModel);
                            org.bukkit.inventory.meta.ItemMeta meta = fishItem.getItemMeta();
                            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                            lore.add(TextUtil.colorize("&7Personal Best: &b" + maxWeight + " kg"));
                            meta.lore(lore);
                            fishItem.setItemMeta(meta);
                            gui.setItem(slot, new net.rocky.coresuite.common.gui.GuiButton(fishItem, e -> {}));
                        } else {
                            ItemStack unknown = new ItemStack(org.bukkit.Material.BARRIER);
                            org.bukkit.inventory.meta.ItemMeta meta = unknown.getItemMeta();
                            meta.displayName(TextUtil.colorize("&8???"));
                            meta.lore(Arrays.asList(TextUtil.colorize("&7You haven't caught this yet.")));
                            unknown.setItemMeta(meta);
                            gui.setItem(slot, new net.rocky.coresuite.common.gui.GuiButton(unknown, e -> {}));
                        }
                        slot++;
                    }
                }
                gui.open(player);
            });
        });
    }

    private void openMainMenu(Player player, FishData data) {
        net.rocky.coresuite.common.gui.Gui gui = new net.rocky.coresuite.common.gui.Gui("&8Fishing Menu", 6);
        gui.fillBorder(org.bukkit.Material.BLUE_STAINED_GLASS_PANE);
        
        int needed = data.level * config.getInt("leveling.xp-formula-multiplier", 20);
        
        ItemStack statsHead = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta statsMeta = (org.bukkit.inventory.meta.SkullMeta) statsHead.getItemMeta();
        if (statsMeta != null) {
            statsMeta.setOwningPlayer(player);
            statsMeta.displayName(TextUtil.colorize("&b&l" + player.getName() + "'s Stats"));
            
            calculateFatigue(player, data); // Process decay
            int fatigueDisplay = (int) Math.round(data.fatigueScore);
            String fatigueColor = fatigueDisplay >= 85 ? "&4" : fatigueDisplay >= 75 ? "&c" : fatigueDisplay >= 50 ? "&6" : "&e";

            statsMeta.lore(Arrays.asList(
                TextUtil.colorize("&7Level: &b" + data.level),
                TextUtil.colorize("&7XP: &b" + data.xp + "&7/&b" + needed),
                TextUtil.colorize("&7Total Catches: &b" + data.totalCatches),
                TextUtil.colorize("&7Fatigue: " + fatigueColor + fatigueDisplay + "%"),
                TextUtil.colorize(""),
                TextUtil.colorize("&eHold your fishing rod and click"),
                TextUtil.colorize("&eto view detailed catch stats!")
            ));
            statsHead.setItemMeta(statsMeta);
        }
        gui.setItem(13, new net.rocky.coresuite.common.gui.GuiButton(statsHead, e -> {
            e.setCancelled(true);
            player.closeInventory();
            sendDetailedStats(player, data);
        }));
        
        gui.setItem(28, net.rocky.coresuite.common.gui.GuiButton.of(
            org.bukkit.Material.CHEST,
            "&a&lFish Bag",
            Arrays.asList(
                "&7Click to open your fish bag.",
                "",
                "&e&o(Upgrade available inside!)"
            ),
            e -> {
                player.performCommand("fish bag");
            }
        ));
        
        gui.setItem(29, net.rocky.coresuite.common.gui.GuiButton.of(
            org.bukkit.Material.ENCHANTED_BOOK,
            "&d&lUpgrades Shop",
            Arrays.asList("&7Click to open the upgrades shop."),
            e -> player.performCommand("fish shop")
        ));

        gui.setItem(30, net.rocky.coresuite.common.gui.GuiButton.of(
            org.bukkit.Material.SLIME_BALL,
            "&e&lBait Shop",
            Arrays.asList("&7Click to open the bait shop."),
            e -> player.performCommand("fish bait")
        ));
        
        gui.setItem(31, net.rocky.coresuite.common.gui.GuiButton.of(
            org.bukkit.Material.GOLD_INGOT,
            "&e&lSell Fish",
            Arrays.asList("&7Click to sell your caught fish."),
            e -> player.performCommand("fish sell")
        ));
        
        gui.setItem(32, net.rocky.coresuite.common.gui.GuiButton.of(
            org.bukkit.Material.BOOK,
            "&b&lCollection",
            Arrays.asList("&7Click to view the fish collection."),
            e -> player.performCommand("fish info")
        ));
        
        ItemStack leaderboardsHead = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
        org.bukkit.inventory.meta.SkullMeta lbMeta = (org.bukkit.inventory.meta.SkullMeta) leaderboardsHead.getItemMeta();
        if (lbMeta != null) {
            lbMeta.setOwningPlayer(player);
            lbMeta.displayName(TextUtil.colorize("&6&lLeaderboards"));
            lbMeta.lore(Arrays.asList(TextUtil.colorize("&7Click to view the server leaderboards.")));
            leaderboardsHead.setItemMeta(lbMeta);
        }
        gui.setItem(33, new net.rocky.coresuite.common.gui.GuiButton(leaderboardsHead, e -> {
            // player.closeInventory();
            player.performCommand("fish top");
        }));
        
        gui.setItem(34, net.rocky.coresuite.common.gui.GuiButton.of(
            org.bukkit.Material.DIAMOND,
            "&b&lWeekly Tournament",
            Arrays.asList("&7Click to view the weekly tournament."),
            e -> player.performCommand("fish tournament")
        ));

        gui.setItem(40, net.rocky.coresuite.common.gui.GuiButton.of(
            org.bukkit.Material.GOLD_BLOCK,
            "&6&lHall of Fame",
            Arrays.asList("&7Click to view the Hall of Fame."),
            e -> player.performCommand("fish hof")
        ));
        
        gui.open(player);
    }

    private boolean isOpenWater(org.bukkit.Location hookLoc) {
        if (hookLoc.getWorld().getHighestBlockYAt(hookLoc) > hookLoc.getBlockY()) {
            return false;
        }
        for (int i = 0; i <= 4; i++) {
            org.bukkit.block.Block b = hookLoc.clone().subtract(0, i, 0).getBlock();
            if (b.getType() != org.bukkit.Material.WATER) {
                return false;
            }
        }
        return true;
    }

    public int getTotalExperience(Player player) {
        int level = player.getLevel();
        int exp = Math.round(getExpToNext(level) * player.getExp());
        while (level > 0) {
            level--;
            exp += getExpToNext(level);
        }
        return exp;
    }

    private int getExpToNext(int level) {
        if (level < 16) return 2 * level + 7;
        if (level < 31) return 5 * level - 38;
        return 9 * level - 158;
    }

    public void removeExp(Player player, int amount) {
        int exp = getTotalExperience(player) - amount;
        player.setTotalExperience(0);
        player.setLevel(0);
        player.setExp(0);
        if (exp > 0) player.giveExp(exp);
    }

    private void openTopGUI(Player player) {
        String prefix = config.getString("prefix", "&8[&b&lFishing&8] &r");
        TextUtil.sendMessage(player, prefix + "&7Loading leaderboard...");
        
        plugin.getDatabaseManager().executeAsync(() -> {
            List<TopPlayer> topList = new ArrayList<>();
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "SELECT uuid, player_name, level, xp, total_catches FROM coresuite_fishing ORDER BY level DESC, xp DESC LIMIT 10"
                 );
                 ResultSet rs = stmt.executeQuery()) {
                
                while (rs.next()) {
                    String uStr = rs.getString("uuid");
                    UUID u = uStr != null ? UUID.fromString(uStr) : null;
                    topList.add(new TopPlayer(
                        u,
                        rs.getString("player_name"),
                        rs.getInt("level"),
                        rs.getInt("xp"),
                        rs.getInt("total_catches")
                    ));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load fishing leaderboard", e);
                return;
            }
            
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                net.rocky.coresuite.common.gui.Gui gui = new net.rocky.coresuite.common.gui.Gui("&8Fishing Leaderboard", 5);
                gui.fillBorder(org.bukkit.Material.CYAN_STAINED_GLASS_PANE);
                
                int[] slots = {13, 21, 22, 23, 29, 30, 31, 32, 33, 40};
                for (int i = 0; i < topList.size() && i < slots.length; i++) {
                    TopPlayer top = topList.get(i);
                    String pName = top.name != null ? top.name : "Unknown";
                    
                    ItemStack head = new ItemStack(org.bukkit.Material.PLAYER_HEAD);
                    org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) head.getItemMeta();
                    if (meta != null) {
                        if (top.uuid != null) {
                            meta.setOwningPlayer(Bukkit.getOfflinePlayer(top.uuid));
                        } else if (pName != null && !pName.equals("Unknown")) {
                            meta.setOwningPlayer(Bukkit.getOfflinePlayer(pName));
                        }
                        meta.displayName(TextUtil.colorize("&b&l#" + (i + 1) + " &f" + pName));
                        meta.lore(Arrays.asList(
                            TextUtil.colorize("&7Level: &b" + top.level),
                            TextUtil.colorize("&7XP: &b" + top.xp),
                            TextUtil.colorize("&7Total Catches: &b" + top.totalCatches)
                        ));
                        head.setItemMeta(meta);
                    }
                    
                    gui.setItem(slots[i], new net.rocky.coresuite.common.gui.GuiButton(head, e -> {}));
                }
                
                gui.setItem(44, net.rocky.coresuite.common.gui.GuiButton.of(
                    org.bukkit.Material.BARRIER,
                    "&c&lBack",
                    Arrays.asList("&7Return to the main menu."),
                    e -> {
                        // player.closeInventory();
                        player.performCommand("fish");
                    }
                ));
                
                gui.open(player);
            });
        });
    }

    private static class TopPlayer {
        UUID uuid;
        String name;
        int level;
        int xp;
        int totalCatches;
        TopPlayer(UUID u, String n, int l, int x, int c) {
            uuid = u; name = n; level = l; xp = x; totalCatches = c;
        }
    }

    private String module_getBaitDisplayName(String baitId) {
        return config.getString("baits." + baitId + ".name", "&aBait");
    }

    public void sendFishingActionBar(Player player) {
        UUID uuid = player.getUniqueId();
        if (actionbarOverride.getOrDefault(uuid, 0L) > System.currentTimeMillis()) return;
        
        FishData data = playerData.get(uuid);
        if (data == null) return;
        
        int streak = fishStreak.getOrDefault(uuid, 0);
        int streakCap = config.getInt("streak.max-multiplier", 10);
        calculateFatigue(player, data); // To process decay
        
        String streakMsgFormat = config.getString("messages.streak-message", "&7Streak: &b{streak}x&8/&7{cap} &8| &7Angler Lv.&b{level}");
        String streakText = streakMsgFormat.replace("{streak}", String.valueOf(streak))
                                           .replace("{cap}", String.valueOf(streakCap))
                                           .replace("{level}", String.valueOf(data.level));
        int fatigueDisplayPercent = (int) Math.round(data.fatigueScore);
        StringBuilder actionBar = new StringBuilder(streakText);
        if (fatigueDisplayPercent > 0) {
            String fatigueColor = fatigueDisplayPercent >= 85 ? "&4" : fatigueDisplayPercent >= 75 ? "&c" : fatigueDisplayPercent >= 50 ? "&6" : "&e";
            actionBar.append(" &8| ").append(fatigueColor).append("Fatigue: ").append(fatigueDisplayPercent).append("%");
        }
        
        if (baitManager != null) {
            BaitManager.ActiveBait activeBait = baitManager.getActiveBait(uuid);
            if (activeBait != null) {
                String baitName = module_getBaitDisplayName(activeBait.baitId);
                if ("casts".equalsIgnoreCase(activeBait.type)) {
                    actionBar.append(" &8| &aBait: ").append(activeBait.castsRemaining).append(" casts");
                } else {
                    long secsLeft = Math.max(0, (activeBait.expiresAt - System.currentTimeMillis()) / 1000);
                    actionBar.append(" &8| &aBait: ").append(secsLeft).append("s");
                }
            }
        }
        TextUtil.sendActionBar(player, actionBar.toString());
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

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("fish")) {
            if (args.length == 1) {
                List<String> options = new ArrayList<>(Arrays.asList("top", "sell", "log", "bag", "shop", "pond", "fatigue", "tournament", "bait", "hof", "info"));
                if (sender.hasPermission("coresuite.customfishing.admin") || sender.hasPermission("coresuite.admin")) {
                    options.add("admin");
                }
                List<String> result = new ArrayList<>();
                for (String opt : options) {
                    if (opt.toLowerCase().startsWith(args[0].toLowerCase())) {
                        result.add(opt);
                    }
                }
                return result;
            } else if (args.length > 1 && args[0].equalsIgnoreCase("admin") && (sender.hasPermission("coresuite.customfishing.admin") || sender.hasPermission("coresuite.admin"))) {
                List<String> result = new ArrayList<>();
                if (args.length == 2) {
                    org.bukkit.util.StringUtil.copyPartialMatches(args[1], Arrays.asList("setlevel", "addxp", "clearlog", "forcepond", "give", "givebait", "legendary", "clearanglers", "spawnangler", "fatigue"), result);
                } else if (args.length == 3) {
                    if (args[1].equalsIgnoreCase("legendary")) {
                        org.bukkit.util.StringUtil.copyPartialMatches(args[2], Arrays.asList("givemap"), result);
                    } else if (args[1].equalsIgnoreCase("forcepond")) {
                        // forcepond takes no more arguments
                    } else {
                        // All others (setlevel, addxp, clearlog, give, givebait) take a player name as the 3rd argument
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (p.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                                result.add(p.getName());
                            }
                        }
                    }
                } else if (args.length == 4) {
                    if (args[1].equalsIgnoreCase("give")) {
                        if (config.getConfigurationSection("species") != null) {
                            List<String> species = new ArrayList<>(config.getConfigurationSection("species").getKeys(false));
                            org.bukkit.util.StringUtil.copyPartialMatches(args[3], species, result);
                        }
                    } else if (args[1].equalsIgnoreCase("givebait")) {
                        if (config.getConfigurationSection("baits") != null) {
                            List<String> baits = new ArrayList<>(config.getConfigurationSection("baits").getKeys(false));
                            org.bukkit.util.StringUtil.copyPartialMatches(args[3], baits, result);
                        }
                    } else if (args[1].equalsIgnoreCase("fatigue")) {
                        org.bukkit.util.StringUtil.copyPartialMatches(args[3], Arrays.asList("set", "reset"), result);
                    } else if (args[1].equalsIgnoreCase("legendary") && args[2].equalsIgnoreCase("givemap")) {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (p.getName().toLowerCase().startsWith(args[3].toLowerCase())) {
                                result.add(p.getName());
                            }
                        }
                    }
                }
                return result;
            }
        }
        return java.util.Collections.emptyList();
    }

    // Fish items using vanilla materials don't need block-place or head-wear prevention
    // since COD/SALMON/PUFFERFISH/TROPICAL_FISH cannot be placed or worn as armor.
    public LegendaryNPCManager getLegendaryNPCManager() { return legendaryNPCManager; }
    public LegendaryQuestManager getLegendaryQuestManager() { return legendaryQuestManager; }
    public NamespacedKey getSpeciesKey() { return speciesKey; }
    public FishingEnchantManager getEnchantManager() { return enchantManager; }

    /**
     * Adds Angler Levels to a player. Used by the quest system for level rewards.
     * This directly modifies the player's FishData level field and persists it.
     */
    public void addAnglerLevels(Player player, int levels) {
        FishData data = playerData.get(player.getUniqueId());
        if (data == null) {
            data = new FishData(1, 0, 0, 0);
            playerData.put(player.getUniqueId(), data);
        }
        data.level += levels;
        playerData.put(player.getUniqueId(), data);
        saveDataAsync(player.getUniqueId());
        String levelUpMsg = config.getString("messages.level-up", "&a&lLEVEL UP! &7You are now Angler Level &b{level}&7!");
        TextUtil.sendMessage(player, levelUpMsg.replace("{level}", String.valueOf(data.level)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
    }

    /**
     * Returns the FishData for a player, or null if not loaded.
     */
    public FishData getPlayerData(UUID uuid) {
        return playerData.get(uuid);
    }
}



