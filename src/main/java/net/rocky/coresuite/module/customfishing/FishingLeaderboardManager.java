/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.Sound
 *  org.bukkit.entity.Player
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.plugin.Plugin
 */
package net.rocky.coresuite.module.customfishing;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.gui.GuiButton;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.customfishing.CustomFishingModule;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public class FishingLeaderboardManager {
    private final CoreSuitePlugin plugin;
    private final CustomFishingModule module;
    private final Map<String, Double> serverRecords = new ConcurrentHashMap<String, Double>();
    private final Map<String, String> recordHolders = new ConcurrentHashMap<String, String>();

    public FishingLeaderboardManager(CoreSuitePlugin plugin, CustomFishingModule module) {
        this.plugin = plugin;
        this.module = module;
        this.initDatabase();
        this.loadServerRecords();
    }

    private void initDatabase() {
        this.plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = this.plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("CREATE TABLE IF NOT EXISTS coresuite_fish_hall_of_fame (species_id TEXT PRIMARY KEY, record_holder_uuid TEXT NOT NULL, record_holder_name TEXT NOT NULL, max_weight REAL NOT NULL, record_date BIGINT NOT NULL)");){
                stmt.executeUpdate();
            }
            catch (SQLException e) {
                this.plugin.getLogger().log(Level.SEVERE, "Failed to create hall of fame table", e);
            }
        });
    }

    private void loadServerRecords() {
        this.plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = this.plugin.getDatabaseManager().getConnection();){
                String speciesId;
                ResultSet rs;
                boolean hasData = false;
                try (PreparedStatement stmt = conn.prepareStatement("SELECT species_id, record_holder_name, max_weight FROM coresuite_fish_hall_of_fame");){
                    rs = stmt.executeQuery();
                    try {
                        while (rs.next()) {
                            hasData = true;
                            speciesId = rs.getString("species_id");
                            this.serverRecords.put(speciesId, rs.getDouble("max_weight"));
                            this.recordHolders.put(speciesId, rs.getString("record_holder_name"));
                        }
                    }
                    finally {
                        if (rs != null) {
                            rs.close();
                        }
                    }
                }
                if (hasData) return;
                PreparedStatement stmt = null;
                stmt = conn.prepareStatement("SELECT l.species_id, MAX(l.max_weight) as absolute_max, f.player_name, l.uuid FROM coresuite_fish_logbook l LEFT JOIN coresuite_fishing f ON l.uuid = f.uuid GROUP BY l.species_id");
                try {
                    rs = stmt.executeQuery();
                    try {
                        while (rs.next()) {
                            String uuid;
                            speciesId = rs.getString("species_id");
                            double maxWeight = rs.getDouble("absolute_max");
                            String playerName = rs.getString("player_name");
                            if (playerName == null) {
                                playerName = "Unknown";
                            }
                            if ((uuid = rs.getString("uuid")) == null) {
                                uuid = UUID.randomUUID().toString();
                            }
                            this.serverRecords.put(speciesId, maxWeight);
                            this.recordHolders.put(speciesId, playerName);
                            PreparedStatement insertStmt = conn.prepareStatement("INSERT OR REPLACE INTO coresuite_fish_hall_of_fame (species_id, record_holder_uuid, record_holder_name, max_weight, record_date) VALUES (?, ?, ?, ?, ?)");
                            try {
                                insertStmt.setString(1, speciesId);
                                insertStmt.setString(2, uuid);
                                insertStmt.setString(3, playerName);
                                insertStmt.setDouble(4, maxWeight);
                                insertStmt.setLong(5, System.currentTimeMillis());
                                insertStmt.executeUpdate();
                            }
                            finally {
                                if (insertStmt == null) continue;
                                insertStmt.close();
                            }
                        }
                        return;
                    }
                    finally {
                        if (rs != null) {
                            rs.close();
                        }
                    }
                }
                finally {
                    if (stmt != null) {
                        stmt.close();
                    }
                }
            }
            catch (SQLException e) {
                this.plugin.getLogger().log(Level.SEVERE, "Failed to load fishing server records", e);
            }
        });
    }

    public void checkServerRecord(Player player, String speciesId, double weight, String chatName) {
        double currentRecord = this.serverRecords.getOrDefault(speciesId, 0.0);
        if (weight > currentRecord) {
            this.serverRecords.put(speciesId, weight);
            this.recordHolders.put(speciesId, player.getName());
            this.plugin.getDatabaseManager().executeAsync(() -> {
                try (Connection conn = this.plugin.getDatabaseManager().getConnection();
                     PreparedStatement stmt = conn.prepareStatement("INSERT OR REPLACE INTO coresuite_fish_hall_of_fame (species_id, record_holder_uuid, record_holder_name, max_weight, record_date) VALUES (?, ?, ?, ?, ?)");){
                    stmt.setString(1, speciesId);
                    stmt.setString(2, player.getUniqueId().toString());
                    stmt.setString(3, player.getName());
                    stmt.setDouble(4, weight);
                    stmt.setLong(5, System.currentTimeMillis());
                    stmt.executeUpdate();
                }
                catch (SQLException e) {
                    this.plugin.getLogger().log(Level.SEVERE, "Failed to insert hall of fame record", e);
                }
            });
            String prefix = this.plugin.getConfig().getString("modules.custom-fishing.prefix", "&8[&b&lFishing&8] &r");
            Bukkit.broadcast((Component)TextUtil.colorize(prefix + "&6&lSERVER RECORD! &f" + player.getName() + " &7just broke the record for &e" + chatName + "&7: &b" + weight + "kg&7!"));
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.5f, 1.5f);
            }
        }
    }

    public void openHallOfFameGUI(Player player) {
        this.openHallOfFameGUI(player, 0);
    }

    public void openHallOfFameGUI(Player player, int page) {
        this.plugin.getDatabaseManager().executeAsync(() -> {
            HashSet<String> caughtSpecies = new HashSet<String>();
            try (Connection conn = this.plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT species_id FROM coresuite_fish_logbook WHERE uuid = ?");){
                stmt.setString(1, player.getUniqueId().toString());
                try (ResultSet rs = stmt.executeQuery();){
                    while (rs.next()) {
                        caughtSpecies.add(rs.getString("species_id"));
                    }
                }
            }
            catch (SQLException e) {
                this.plugin.getLogger().log(Level.SEVERE, "Failed to load fish logbook for HOF masking", e);
            }
            Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> this.buildHallOfFameGUI(player, page, caughtSpecies));
        });
    }

    private void buildHallOfFameGUI(Player player, int page, Set<String> caughtSpecies) {
        Gui gui = new Gui("&8Hall of Fame", 6);
        gui.fillBorder(Material.YELLOW_STAINED_GLASS_PANE);
        if (this.module.getConfig().getConfigurationSection("species") != null) {
            int itemsPerPage;
            ArrayList keys = new ArrayList(this.module.getConfig().getConfigurationSection("species").getKeys(false));
            int totalItems = keys.size();
            int totalPages = (int)Math.ceil((double)totalItems / (double)(itemsPerPage = 28));
            if (totalPages == 0) {
                totalPages = 1;
            }
            int startIndex = page * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, totalItems);
            int slot = 10;
            for (int i = startIndex; i < endIndex; ++i) {
                String key = (String)keys.get(i);
                String spPath = "species." + key + ".";
                try {
                    Object name = this.module.getConfig().getString(spPath + "name", "");
                    String rarity = this.module.getConfig().getString(spPath + "rarity", "Common");
                    if (!((String)name).contains("&")) {
                        String color = "&7";
                        switch (rarity.toLowerCase()) {
                            case "uncommon": {
                                color = "&a";
                                break;
                            }
                            case "rare": {
                                color = "&9";
                                break;
                            }
                            case "epic": {
                                color = "&5";
                                break;
                            }
                            case "legendary": {
                                color = "&6";
                                break;
                            }
                            case "mythical": {
                                color = "&c&l";
                            }
                        }
                        name = color + (String)name;
                    }
                    String materialName = this.module.getConfig().getString(spPath + "material", "COD");
                    String itemModel = this.module.getConfig().getString(spPath + "item-model", "");
                    int cmd = this.module.getConfig().getInt(spPath + "custom-model-data", -1);
                    String speciesId = ChatColor.stripColor((String)ChatColor.translateAlternateColorCodes((char)'&', (String)name));
                    if (caughtSpecies.contains(speciesId)) {
                        Material mat = Material.COD;
                        try {
                            mat = Material.valueOf((String)materialName.toUpperCase());
                        }
                        catch (Exception exception) {
                            // empty catch block
                        }
                        ItemStack icon = new ItemStack(mat);
                        ItemMeta meta = icon.getItemMeta();
                        if (meta != null) {
                            meta.displayName(TextUtil.colorize((String)name));
                            ArrayList<Object> lore = new ArrayList<Object>();
                            double record = this.serverRecords.getOrDefault(speciesId, 0.0);
                            String holder = this.recordHolders.get(speciesId);
                            if (record > 0.0 && holder != null) {
                                lore.add(TextUtil.colorize("&7Record: &b" + record + " kg"));
                                lore.add(TextUtil.colorize("&7Held by: &e" + holder));
                                lore.add(Component.empty());
                                lore.add(TextUtil.colorize("&8Click to view full leaderboard"));
                            } else {
                                lore.add(TextUtil.colorize("&7Record: &cNone yet!"));
                            }
                            meta.lore((java.util.List<net.kyori.adventure.text.Component>)(java.util.List<?>)lore);
                            if (!itemModel.isEmpty()) {
                                net.rocky.coresuite.common.compat.PaperCompat.setItemModel(meta, NamespacedKey.fromString((String)itemModel));
                            }
                            if (cmd != -1) {
                                meta.setCustomModelData(Integer.valueOf(cmd));
                            }
                            icon.setItemMeta(meta);
                        }
                        Object finalName = name;
                        gui.setItem(slot, new GuiButton(icon, arg_0 -> this.lambda$buildHallOfFameGUI$5(player, speciesId, (String)finalName, icon, arg_0)));
                    } else {
                        ItemStack unknown = new ItemStack(Material.PAPER);
                        ItemMeta meta = unknown.getItemMeta();
                        meta.displayName(TextUtil.colorize("&8???"));
                        net.rocky.coresuite.common.compat.PaperCompat.setItemModel(meta, NamespacedKey.fromString((String)"barely_default:fish_unknown"));
                        meta.lore(Arrays.asList(TextUtil.colorize("&7Catch this fish to reveal"), TextUtil.colorize("&7its Hall of Fame record.")));
                        unknown.setItemMeta(meta);
                        gui.setItem(slot, new GuiButton(unknown, e -> {}));
                    }
                    ++slot;
                    while (slot % 9 == 0 || slot % 9 == 8) {
                        ++slot;
                    }
                    continue;
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
            if (page > 0) {
                gui.setItem(45, GuiButton.of(Material.ARROW, "&aPrevious Page", Arrays.asList("&7Go to page " + page), e -> this.buildHallOfFameGUI(player, page - 1, caughtSpecies)));
            }
            if (page < totalPages - 1) {
                gui.setItem(53, GuiButton.of(Material.ARROW, "&aNext Page", Arrays.asList("&7Go to page " + (page + 2)), e -> this.buildHallOfFameGUI(player, page + 1, caughtSpecies)));
            }
        }
        gui.setItem(49, GuiButton.of(Material.BARRIER, "&c&lBack", Arrays.asList("&7Return to main menu."), e -> player.performCommand("fish")));
        gui.open(player);
    }

    public void openTopGUI(Player player) {
        this.openTopGUI(player, 0);
    }

    public void openTopGUI(Player player, int page) {
        this.plugin.getDatabaseManager().executeAsync(() -> {
            HashSet<String> caughtSpecies = new HashSet<String>();
            try (Connection conn = this.plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT species_id FROM coresuite_fish_logbook WHERE uuid = ?");){
                stmt.setString(1, player.getUniqueId().toString());
                try (ResultSet rs = stmt.executeQuery();){
                    while (rs.next()) {
                        caughtSpecies.add(rs.getString("species_id"));
                    }
                }
            }
            catch (SQLException e) {
                this.plugin.getLogger().log(Level.SEVERE, "Failed to load fish logbook for top masking", e);
            }
            Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> this.buildTopGUI(player, page, caughtSpecies));
        });
    }

    private void buildTopGUI(Player player, int page, Set<String> caughtSpecies) {
        Gui gui = new Gui("&8Fishing Leaderboards", 6);
        gui.fillBorder(Material.GRAY_STAINED_GLASS_PANE);
        if (this.module.getConfig().getConfigurationSection("species") != null) {
            int itemsPerPage;
            ArrayList keys = new ArrayList(this.module.getConfig().getConfigurationSection("species").getKeys(false));
            int totalItems = keys.size();
            int totalPages = (int)Math.ceil((double)totalItems / (double)(itemsPerPage = 28));
            if (totalPages == 0) {
                totalPages = 1;
            }
            int startIndex = page * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, totalItems);
            int slot = 10;
            for (int i = startIndex; i < endIndex; ++i) {
                String key = (String)keys.get(i);
                String spPath = "species." + key + ".";
                try {
                    String name = this.module.getConfig().getString(spPath + "name", "");
                    String materialName = this.module.getConfig().getString(spPath + "material", "COD");
                    String itemModel = this.module.getConfig().getString(spPath + "item-model", "");
                    int cmd = this.module.getConfig().getInt(spPath + "custom-model-data", -1);
                    String speciesId = ChatColor.stripColor((String)ChatColor.translateAlternateColorCodes((char)'&', (String)name));
                    if (caughtSpecies.contains(speciesId)) {
                        Material mat = Material.COD;
                        try {
                            mat = Material.valueOf((String)materialName.toUpperCase());
                        }
                        catch (Exception exception) {
                            // empty catch block
                        }
                        ItemStack icon = new ItemStack(mat);
                        ItemMeta meta = icon.getItemMeta();
                        if (meta != null) {
                            meta.displayName(TextUtil.colorize(name + " &7Leaderboard"));
                            ArrayList<Object> lore = new ArrayList<Object>();
                            lore.add(TextUtil.colorize("&7Click to view the top 10"));
                            lore.add(TextUtil.colorize("&7heaviest catches for this fish."));
                            lore.add(Component.empty());
                            double record = this.serverRecords.getOrDefault(speciesId, 0.0);
                            lore.add(TextUtil.colorize("&eServer Record: &f" + record + " kg"));
                            meta.lore((java.util.List<net.kyori.adventure.text.Component>)(java.util.List<?>)lore);
                            if (!itemModel.isEmpty()) {
                                net.rocky.coresuite.common.compat.PaperCompat.setItemModel(meta, NamespacedKey.fromString((String)itemModel));
                            }
                            if (cmd != -1) {
                                meta.setCustomModelData(Integer.valueOf(cmd));
                            }
                            icon.setItemMeta(meta);
                        }
                        gui.setItem(slot, new GuiButton(icon, e -> this.openSpeciesLeaderboard(player, speciesId, name, icon)));
                    } else {
                        ItemStack unknown = new ItemStack(Material.PAPER);
                        ItemMeta meta = unknown.getItemMeta();
                        meta.displayName(TextUtil.colorize("&8???"));
                        net.rocky.coresuite.common.compat.PaperCompat.setItemModel(meta, NamespacedKey.fromString((String)"barely_default:fish_unknown"));
                        meta.lore(Arrays.asList(TextUtil.colorize("&7Catch this fish to view"), TextUtil.colorize("&7its leaderboard.")));
                        unknown.setItemMeta(meta);
                        gui.setItem(slot, new GuiButton(unknown, e -> {}));
                    }
                    ++slot;
                    while (slot % 9 == 0 || slot % 9 == 8) {
                        ++slot;
                    }
                    continue;
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
            if (page > 0) {
                gui.setItem(45, GuiButton.of(Material.ARROW, "&aPrevious Page", Arrays.asList("&7Go to page " + page), e -> this.buildTopGUI(player, page - 1, caughtSpecies)));
            }
            if (page < totalPages - 1) {
                gui.setItem(53, GuiButton.of(Material.ARROW, "&aNext Page", Arrays.asList("&7Go to page " + (page + 2)), e -> this.buildTopGUI(player, page + 1, caughtSpecies)));
            }
        }
        gui.setItem(49, GuiButton.of(Material.BARRIER, "&c&lBack", Arrays.asList("&7Return to main menu."), e -> player.performCommand("fish")));
        gui.open(player);
    }

    private void openSpeciesLeaderboard(Player player, String speciesId, String chatName, ItemStack icon) {
        player.sendMessage(TextUtil.colorize("&7Loading leaderboard for " + chatName + "&7..."));
        this.plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = this.plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT l.uuid, l.max_weight, f.player_name FROM coresuite_fish_logbook l LEFT JOIN coresuite_fishing f ON l.uuid = f.uuid WHERE l.species_id = ? ORDER BY l.max_weight DESC LIMIT 10");){
                stmt.setString(1, speciesId);
                try (ResultSet rs = stmt.executeQuery();){
                    ArrayList<Object> lines = new ArrayList<Object>();
                    lines.add("&8&m\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550");
                    lines.add("  " + chatName + " &eLeaderboard");
                    lines.add("&8&m\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550");
                    int rank = 1;
                    while (rs.next()) {
                        double weight = rs.getDouble("max_weight");
                        String name = rs.getString("player_name");
                        if (name == null) {
                            name = "Unknown";
                        }
                        Object color = "&7";
                        if (rank == 1) {
                            color = "&e#1 ";
                        }
                        if (rank == 2) {
                            color = "&6#2 ";
                        }
                        if (rank == 3) {
                            color = "&c#3 ";
                        }
                        if (rank > 3) {
                            color = "&8#" + rank + " ";
                        }
                        lines.add((String)color + "&f" + name + " &7- &b" + weight + " kg");
                        ++rank;
                    }
                    if (rank == 1) {
                        lines.add("&7No one has caught this fish yet!");
                    }
                    lines.add("&8&m\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550");
                    Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
                        for (String line : (java.util.List<String>)(java.util.List<?>)lines) {
                            player.sendMessage(TextUtil.colorize(line));
                        }
                    });
                }
            }
            catch (SQLException e) {
                this.plugin.getLogger().log(Level.SEVERE, "Failed to load leaderboard for " + speciesId, e);
                Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> player.sendMessage(TextUtil.colorize("&cFailed to load leaderboard.")));
            }
        });
    }

    private /* synthetic */ void lambda$buildHallOfFameGUI$5(Player player, String speciesId, String finalName, ItemStack icon, InventoryClickEvent e) {
        this.openSpeciesLeaderboard(player, speciesId, finalName, icon);
    }
}

