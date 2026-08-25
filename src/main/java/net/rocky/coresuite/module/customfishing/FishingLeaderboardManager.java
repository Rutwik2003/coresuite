package net.rocky.coresuite.module.customfishing;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.gui.GuiButton;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class FishingLeaderboardManager {

    private final CoreSuitePlugin plugin;
    private final CustomFishingModule module;
    private final Map<String, Double> serverRecords = new ConcurrentHashMap<>();
    private final Map<String, String> recordHolders = new ConcurrentHashMap<>();

    public FishingLeaderboardManager(CoreSuitePlugin plugin, CustomFishingModule module) {
        this.plugin = plugin;
        this.module = module;
        initDatabase();
        loadServerRecords();
    }

    private void initDatabase() {
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "CREATE TABLE IF NOT EXISTS coresuite_fish_hall_of_fame (" +
                     "species_id TEXT PRIMARY KEY, " +
                     "record_holder_uuid TEXT NOT NULL, " +
                     "record_holder_name TEXT NOT NULL, " +
                     "max_weight REAL NOT NULL, " +
                     "record_date BIGINT NOT NULL" +
                     ")")) {
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to create hall of fame table", e);
            }
        });
    }

    private void loadServerRecords() {
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                boolean hasData = false;
                try (PreparedStatement stmt = conn.prepareStatement("SELECT species_id, record_holder_name, max_weight FROM coresuite_fish_hall_of_fame");
                     ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        hasData = true;
                        String speciesId = rs.getString("species_id");
                        serverRecords.put(speciesId, rs.getDouble("max_weight"));
                        recordHolders.put(speciesId, rs.getString("record_holder_name"));
                    }
                }
                
                if (!hasData) {
                    try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT l.species_id, MAX(l.max_weight) as absolute_max, f.player_name, l.uuid " +
                        "FROM coresuite_fish_logbook l " +
                        "LEFT JOIN coresuite_fishing f ON l.uuid = f.uuid " +
                        "GROUP BY l.species_id")) {
                        try (ResultSet rs = stmt.executeQuery()) {
                            while (rs.next()) {
                                String speciesId = rs.getString("species_id");
                                double maxWeight = rs.getDouble("absolute_max");
                                String playerName = rs.getString("player_name");
                                if (playerName == null) playerName = "Unknown";
                                String uuid = rs.getString("uuid");
                                if (uuid == null) uuid = UUID.randomUUID().toString();

                                serverRecords.put(speciesId, maxWeight);
                                recordHolders.put(speciesId, playerName);
                                
                                try (PreparedStatement insertStmt = conn.prepareStatement(
                                    "INSERT OR REPLACE INTO coresuite_fish_hall_of_fame (species_id, record_holder_uuid, record_holder_name, max_weight, record_date) VALUES (?, ?, ?, ?, ?)")) {
                                    insertStmt.setString(1, speciesId);
                                    insertStmt.setString(2, uuid);
                                    insertStmt.setString(3, playerName);
                                    insertStmt.setDouble(4, maxWeight);
                                    insertStmt.setLong(5, System.currentTimeMillis());
                                    insertStmt.executeUpdate();
                                }
                            }
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load fishing server records", e);
            }
        });
    }

    public void checkServerRecord(Player player, String speciesId, double weight, String chatName) {
        double currentRecord = serverRecords.getOrDefault(speciesId, 0.0);
        if (weight > currentRecord) {
            serverRecords.put(speciesId, weight);
            recordHolders.put(speciesId, player.getName());

            plugin.getDatabaseManager().executeAsync(() -> {
                try (Connection conn = plugin.getDatabaseManager().getConnection();
                     PreparedStatement stmt = conn.prepareStatement(
                         "INSERT OR REPLACE INTO coresuite_fish_hall_of_fame (species_id, record_holder_uuid, record_holder_name, max_weight, record_date) VALUES (?, ?, ?, ?, ?)")) {
                    stmt.setString(1, speciesId);
                    stmt.setString(2, player.getUniqueId().toString());
                    stmt.setString(3, player.getName());
                    stmt.setDouble(4, weight);
                    stmt.setLong(5, System.currentTimeMillis());
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to insert hall of fame record", e);
                }
            });

            String prefix = plugin.getConfig().getString("modules.custom-fishing.prefix", "&8[&b&lFishing&8] &r");
            Bukkit.broadcast(TextUtil.colorize(prefix + "&6&lSERVER RECORD! &f" + player.getName() + " &7just broke the record for &e" + chatName + "&7: &b" + weight + "kg&7!"));
            
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.5f);
            }
        }
    }

    public void openHallOfFameGUI(Player player) {
        openHallOfFameGUI(player, 0);
    }

    public void openHallOfFameGUI(Player player, int page) {
        plugin.getDatabaseManager().executeAsync(() -> {
            Set<String> caughtSpecies = new HashSet<>();
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT species_id FROM coresuite_fish_logbook WHERE uuid = ?")) {
                stmt.setString(1, player.getUniqueId().toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        caughtSpecies.add(rs.getString("species_id"));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load fish logbook for HOF masking", e);
            }
            Bukkit.getScheduler().runTask(plugin, () -> buildHallOfFameGUI(player, page, caughtSpecies));
        });
    }

    private void buildHallOfFameGUI(Player player, int page, Set<String> caughtSpecies) {
        Gui gui = new Gui("&8Hall of Fame", 6);
        gui.fillBorder(Material.YELLOW_STAINED_GLASS_PANE);
        
        if (module.getConfig().getConfigurationSection("species") != null) {
            List<String> keys = new ArrayList<>(module.getConfig().getConfigurationSection("species").getKeys(false));
            int totalItems = keys.size();
            int itemsPerPage = 28;
            int totalPages = (int) Math.ceil((double) totalItems / itemsPerPage);
            if (totalPages == 0) totalPages = 1;
            
            int startIndex = page * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, totalItems);
            
            int slot = 10;
            for (int i = startIndex; i < endIndex; i++) {
                String key = keys.get(i);
                String spPath = "species." + key + ".";
                try {
                    String name = module.getConfig().getString(spPath + "name", "");
                    String rarity = module.getConfig().getString(spPath + "rarity", "Common");
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
                    String materialName = module.getConfig().getString(spPath + "material", "COD");
                    String itemModel = module.getConfig().getString(spPath + "item-model", "");
                    int cmd = module.getConfig().getInt(spPath + "custom-model-data", -1);
                
                    String speciesId = org.bukkit.ChatColor.stripColor(org.bukkit.ChatColor.translateAlternateColorCodes('&', name));
                
                    if (caughtSpecies.contains(speciesId)) {
                        Material mat = Material.COD;
                        try {
                            mat = Material.valueOf(materialName.toUpperCase());
                        } catch (Exception ignored) {}
                    
                        ItemStack icon = new ItemStack(mat);
                        ItemMeta meta = icon.getItemMeta();
                        if (meta != null) {
                            meta.displayName(TextUtil.colorize(name));
                            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                            
                            double record = serverRecords.getOrDefault(speciesId, 0.0);
                            String holder = recordHolders.get(speciesId);
                            
                            if (record > 0 && holder != null) {
                                lore.add(TextUtil.colorize("&7Record: &b" + record + " kg"));
                                lore.add(TextUtil.colorize("&7Held by: &e" + holder));
                                lore.add(net.kyori.adventure.text.Component.empty());
                                lore.add(TextUtil.colorize("&8Click to view full leaderboard"));
                            } else {
                                lore.add(TextUtil.colorize("&7Record: &cNone yet!"));
                            }
                            meta.lore(lore);
                        
                            if (!itemModel.isEmpty()) {
                                meta.setItemModel(NamespacedKey.fromString(itemModel));
                            }
                            if (cmd != -1) {
                                meta.setCustomModelData(cmd);
                            }
                            icon.setItemMeta(meta);
                        }
                    
                        final String finalName = name;
                        gui.setItem(slot, new GuiButton(icon, e -> openSpeciesLeaderboard(player, speciesId, finalName, icon)));
                    } else {
                        ItemStack unknown = new ItemStack(Material.PAPER);
                        ItemMeta meta = unknown.getItemMeta();
                        meta.displayName(TextUtil.colorize("&8???"));
                        meta.setItemModel(org.bukkit.NamespacedKey.fromString("barely_default:fish_unknown"));
                        meta.lore(java.util.Arrays.asList(TextUtil.colorize("&7Catch this fish to reveal"), TextUtil.colorize("&7its Hall of Fame record.")));
                        unknown.setItemMeta(meta);
                        gui.setItem(slot, new GuiButton(unknown, e -> {}));
                    }
                
                    slot++;
                    while (slot % 9 == 0 || slot % 9 == 8) slot++;
                } catch (Exception ignored) {}
            }
            
            if (page > 0) {
                gui.setItem(45, GuiButton.of(Material.ARROW, "&aPrevious Page", java.util.Arrays.asList("&7Go to page " + page), e -> buildHallOfFameGUI(player, page - 1, caughtSpecies)));
            }
            if (page < totalPages - 1) {
                gui.setItem(53, GuiButton.of(Material.ARROW, "&aNext Page", java.util.Arrays.asList("&7Go to page " + (page + 2)), e -> buildHallOfFameGUI(player, page + 1, caughtSpecies)));
            }
        }
        gui.setItem(49, GuiButton.of(Material.BARRIER, "&c&lBack", java.util.Arrays.asList("&7Return to main menu."), e -> player.performCommand("fish")));
        gui.open(player);
    }

    public void openTopGUI(Player player) {
        openTopGUI(player, 0);
    }

    public void openTopGUI(Player player, int page) {
        plugin.getDatabaseManager().executeAsync(() -> {
            Set<String> caughtSpecies = new HashSet<>();
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT species_id FROM coresuite_fish_logbook WHERE uuid = ?")) {
                stmt.setString(1, player.getUniqueId().toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        caughtSpecies.add(rs.getString("species_id"));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load fish logbook for top masking", e);
            }
            Bukkit.getScheduler().runTask(plugin, () -> buildTopGUI(player, page, caughtSpecies));
        });
    }

    private void buildTopGUI(Player player, int page, Set<String> caughtSpecies) {
        Gui gui = new Gui("&8Fishing Leaderboards", 6);
        gui.fillBorder(Material.GRAY_STAINED_GLASS_PANE);
        
        if (module.getConfig().getConfigurationSection("species") != null) {
            List<String> keys = new ArrayList<>(module.getConfig().getConfigurationSection("species").getKeys(false));
            int totalItems = keys.size();
            int itemsPerPage = 28;
            int totalPages = (int) Math.ceil((double) totalItems / itemsPerPage);
            if (totalPages == 0) totalPages = 1;
            
            int startIndex = page * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, totalItems);
            
            int slot = 10;
            for (int i = startIndex; i < endIndex; i++) {
                String key = keys.get(i);
                String spPath = "species." + key + ".";
                try {
                    String name = module.getConfig().getString(spPath + "name", "");
                    String materialName = module.getConfig().getString(spPath + "material", "COD");
                    String itemModel = module.getConfig().getString(spPath + "item-model", "");
                    int cmd = module.getConfig().getInt(spPath + "custom-model-data", -1);
                
                    String speciesId = org.bukkit.ChatColor.stripColor(org.bukkit.ChatColor.translateAlternateColorCodes('&', name));
                
                    if (caughtSpecies.contains(speciesId)) {
                        Material mat = Material.COD;
                        try {
                            mat = Material.valueOf(materialName.toUpperCase());
                        } catch (Exception ignored) {}
                    
                        ItemStack icon = new ItemStack(mat);
                        ItemMeta meta = icon.getItemMeta();
                        if (meta != null) {
                            meta.displayName(TextUtil.colorize(name + " &7Leaderboard"));
                            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                            lore.add(TextUtil.colorize("&7Click to view the top 10"));
                            lore.add(TextUtil.colorize("&7heaviest catches for this fish."));
                            lore.add(net.kyori.adventure.text.Component.empty());
                            double record = serverRecords.getOrDefault(speciesId, 0.0);
                            lore.add(TextUtil.colorize("&eServer Record: &f" + record + " kg"));
                            meta.lore(lore);
                        
                            if (!itemModel.isEmpty()) {
                                meta.setItemModel(NamespacedKey.fromString(itemModel));
                            }
                            if (cmd != -1) {
                                meta.setCustomModelData(cmd);
                            }
                            icon.setItemMeta(meta);
                        }
                    
                        gui.setItem(slot, new GuiButton(icon, e -> openSpeciesLeaderboard(player, speciesId, name, icon)));
                    } else {
                        ItemStack unknown = new ItemStack(Material.PAPER);
                        ItemMeta meta = unknown.getItemMeta();
                        meta.displayName(TextUtil.colorize("&8???"));
                        meta.setItemModel(org.bukkit.NamespacedKey.fromString("barely_default:fish_unknown"));
                        meta.lore(java.util.Arrays.asList(TextUtil.colorize("&7Catch this fish to view"), TextUtil.colorize("&7its leaderboard.")));
                        unknown.setItemMeta(meta);
                        gui.setItem(slot, new GuiButton(unknown, e -> {}));
                    }
                
                    slot++;
                    while (slot % 9 == 0 || slot % 9 == 8) slot++;
                } catch (Exception ignored) {}
            }
            
            if (page > 0) {
                gui.setItem(45, GuiButton.of(Material.ARROW, "&aPrevious Page", java.util.Arrays.asList("&7Go to page " + page), e -> buildTopGUI(player, page - 1, caughtSpecies)));
            }
            if (page < totalPages - 1) {
                gui.setItem(53, GuiButton.of(Material.ARROW, "&aNext Page", java.util.Arrays.asList("&7Go to page " + (page + 2)), e -> buildTopGUI(player, page + 1, caughtSpecies)));
            }
        }
        gui.setItem(49, GuiButton.of(Material.BARRIER, "&c&lBack", java.util.Arrays.asList("&7Return to main menu."), e -> player.performCommand("fish")));
        gui.open(player);
    }

    private void openSpeciesLeaderboard(Player player, String speciesId, String chatName, ItemStack icon) {
        player.sendMessage(TextUtil.colorize("&7Loading leaderboard for " + chatName + "&7..."));
        
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "SELECT l.uuid, l.max_weight, f.player_name FROM coresuite_fish_logbook l " +
                     "LEFT JOIN coresuite_fishing f ON l.uuid = f.uuid " +
                     "WHERE l.species_id = ? ORDER BY l.max_weight DESC LIMIT 10")) {
                stmt.setString(1, speciesId);
                try (ResultSet rs = stmt.executeQuery()) {
                    
                    List<String> lines = new ArrayList<>();
                    lines.add("\u00268\u0026m══════════════════════════════");
                    lines.add("  " + chatName + " \u0026eLeaderboard");
                    lines.add("\u00268\u0026m══════════════════════════════");
                    
                    int rank = 1;
                    while (rs.next()) {
                        double weight = rs.getDouble("max_weight");
                        String name = rs.getString("player_name");
                        if (name == null) name = "Unknown";
                        
                        String color = "\u00267";
                        if (rank == 1) color = "\u0026e#1 ";
                        if (rank == 2) color = "\u00266#2 ";
                        if (rank == 3) color = "\u0026c#3 ";
                        if (rank > 3) color = "\u00268#" + rank + " ";
                        
                        lines.add(color + "\u0026f" + name + " \u00267- \u0026b" + weight + " kg");
                        rank++;
                    }
                    
                    if (rank == 1) {
                        lines.add("\u00267No one has caught this fish yet!");
                    }
                    lines.add("\u00268\u0026m══════════════════════════════");
                    
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        for (String line : lines) {
                            player.sendMessage(TextUtil.colorize(line));
                        }
                    });
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load leaderboard for " + speciesId, e);
                Bukkit.getScheduler().runTask(plugin, () -> player.sendMessage(TextUtil.colorize("\u0026cFailed to load leaderboard.")));
            }
        });
    }
}
