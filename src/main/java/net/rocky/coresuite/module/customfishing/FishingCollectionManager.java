/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.command.CommandSender
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.player.AsyncPlayerChatEvent
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public class FishingCollectionManager
implements Listener {
    private final CoreSuitePlugin plugin;
    private final CustomFishingModule module;
    private final Map<UUID, String> pendingSearch = new ConcurrentHashMap<UUID, String>();
    private final Map<UUID, String> currentCategory = new ConcurrentHashMap<UUID, String>();

    public FishingCollectionManager(CoreSuitePlugin plugin, CustomFishingModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    public void unloadPlayer(UUID uuid) {
        this.pendingSearch.remove(uuid);
        this.currentCategory.remove(uuid);
    }

    public void openCollectionGUI(Player player) {
        this.openCollectionGUI(player, 0, "All", "");
    }

    public void openCollectionGUI(Player player, int page, String categoryFilter, String searchQuery) {
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
            this.plugin.getServer().getScheduler().runTask((Plugin)this.plugin, () -> this.buildCollectionGUI(player, page, caughtSpecies, categoryFilter, searchQuery));
        });
    }

    private void buildCollectionGUI(Player player, int page, Map<String, Double> caughtSpecies, String categoryFilter, String searchQuery) {
        Gui gui = new Gui("&8Fishing Collection", 6);
        gui.fillBorder(Material.GRAY_STAINED_GLASS_PANE);
        ConfigurationSection speciesSection = this.module.getConfig().getConfigurationSection("species");
        if (speciesSection != null) {
            int itemsPerPage;
            ArrayList allKeys = new ArrayList(speciesSection.getKeys(false));
            ArrayList<String> filteredKeys = new ArrayList<String>();
            for (String key : (java.util.List<String>)(java.util.List<?>)allKeys) {
                String spPath = "species." + key + ".";
                String name = this.module.getConfig().getString(spPath + "name", "");
                String rarity = this.module.getConfig().getString(spPath + "rarity", "Common");
                String strippedName = ChatColor.stripColor((String)ChatColor.translateAlternateColorCodes((char)'&', (String)name)).toLowerCase();
                boolean matchCategory = categoryFilter.equalsIgnoreCase("All") || rarity.equalsIgnoreCase(categoryFilter);
                boolean matchSearch = true;
                if (!searchQuery.isEmpty()) {
                    String[] queryParts;
                    for (String part : queryParts = searchQuery.toLowerCase().trim().split("\\s+")) {
                        if (strippedName.contains(part)) continue;
                        matchSearch = false;
                        break;
                    }
                }
                String speciesId = ChatColor.stripColor((String)ChatColor.translateAlternateColorCodes((char)'&', (String)name));
                if (!searchQuery.isEmpty() && !caughtSpecies.containsKey(speciesId)) {
                    matchSearch = false;
                }
                if (!matchCategory || !matchSearch) continue;
                filteredKeys.add(key);
            }
            int totalItems = filteredKeys.size();
            int totalPages = (int)Math.ceil((double)totalItems / (double)(itemsPerPage = 28));
            if (totalPages == 0) {
                totalPages = 1;
            }
            int startIndex = page * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, totalItems);
            int slot = 10;
            for (int i = startIndex; i < endIndex; ++i) {
                String key = (String)filteredKeys.get(i);
                String spPath = "species." + key + ".";
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
                double pricePerLb = this.module.getConfig().getDouble(spPath + "price-per-kg", 0.0);
                List biomes = this.module.getConfig().getStringList(spPath + "biomes");
                String speciesId = ChatColor.stripColor((String)ChatColor.translateAlternateColorCodes((char)'&', (String)name));
                while (slot % 9 == 0 || slot % 9 == 8) {
                    ++slot;
                }
                if (caughtSpecies.containsKey(speciesId)) {
                    double maxWeight = caughtSpecies.get(speciesId);
                    String material = this.module.getConfig().getString(spPath + "material", "COD");
                    String itemModel = this.module.getConfig().getString(spPath + "item-model", "");
                    int cmd = this.module.getConfig().getInt(spPath + "custom-model-data", -1);
                    Material mat = Material.COD;
                    try {
                        mat = Material.valueOf((String)material.toUpperCase());
                    }
                    catch (Exception exception) {
                        // empty catch block
                    }
                    ItemStack fishItem = new ItemStack(mat);
                    ItemMeta meta = fishItem.getItemMeta();
                    if (meta != null) {
                        meta.displayName(TextUtil.colorize((String)name));
                        ArrayList<Object> lore = new ArrayList<Object>();
                        lore.add(TextUtil.colorize("&7Personal Best: &b" + maxWeight + " kg"));
                        if (biomes != null && !biomes.isEmpty()) {
                            lore.add(Component.empty());
                            lore.add(TextUtil.colorize("&7Required Biomes:"));
                            for (String b : (java.util.List<String>)(java.util.List<?>)(java.util.List<String>)(java.util.List<?>)biomes) {
                                lore.add(TextUtil.colorize("&8- &7" + b));
                            }
                        }
                        meta.lore((java.util.List<net.kyori.adventure.text.Component>)(java.util.List<?>)lore);
                        if (itemModel != null && !itemModel.isEmpty()) {
                            net.rocky.coresuite.common.compat.PaperCompat.setItemModel(meta, NamespacedKey.fromString((String)itemModel));
                        }
                        if (cmd != -1) {
                            meta.setCustomModelData(Integer.valueOf(cmd));
                        }
                        fishItem.setItemMeta(meta);
                    }
                    gui.setItem(slot, new GuiButton(fishItem, e -> {}));
                } else {
                    ItemStack unknown = new ItemStack(Material.PAPER);
                    ItemMeta meta = unknown.getItemMeta();
                    meta.displayName(TextUtil.colorize("&8???"));
                    net.rocky.coresuite.common.compat.PaperCompat.setItemModel(meta, NamespacedKey.fromString((String)"barely_default:fish_unknown"));
                    ArrayList<Object> lore = new ArrayList<Object>();
                    lore.add(TextUtil.colorize("&7You haven't caught this yet."));
                    if (biomes != null && !biomes.isEmpty()) {
                        lore.add(Component.empty());
                        lore.add(TextUtil.colorize("&7Required Biomes:"));
                        for (String b : (java.util.List<String>)(java.util.List<?>)(java.util.List<String>)(java.util.List<?>)biomes) {
                            lore.add(TextUtil.colorize("&8- &7" + b));
                        }
                    }
                    meta.lore((java.util.List<net.kyori.adventure.text.Component>)(java.util.List<?>)lore);
                    unknown.setItemMeta(meta);
                    gui.setItem(slot, new GuiButton(unknown, e -> {}));
                }
                ++slot;
            }
            if (page > 0) {
                gui.setItem(45, GuiButton.of(Material.ARROW, "&aPrevious Page", Arrays.asList("&7Go to page " + page), e -> this.buildCollectionGUI(player, page - 1, caughtSpecies, categoryFilter, searchQuery)));
            }
            if (page < totalPages - 1) {
                gui.setItem(53, GuiButton.of(Material.ARROW, "&aNext Page", Arrays.asList("&7Go to page " + (page + 2)), e -> this.buildCollectionGUI(player, page + 1, caughtSpecies, categoryFilter, searchQuery)));
            }
        }
        gui.setItem(49, GuiButton.of(Material.BARRIER, "&c&lBack", Arrays.asList("&7Return to the main menu."), e -> player.performCommand("fish")));
        List<String> nextCategories = Arrays.asList("All", "Common", "Uncommon", "Rare", "Epic", "Legendary", "Mythical");
        int nextIndex = (nextCategories.indexOf(categoryFilter) + 1) % nextCategories.size();
        String nextCat = nextCategories.get(nextIndex);
        gui.setItem(47, GuiButton.of(Material.HOPPER, "&6Filter Category", Arrays.asList("&7Current: &e" + categoryFilter, "", "&eClick to cycle to &b" + nextCat), e -> {
            this.currentCategory.put(player.getUniqueId(), nextCat);
            this.buildCollectionGUI(player, 0, caughtSpecies, nextCat, searchQuery);
        }));
        gui.setItem(51, GuiButton.of(Material.OAK_SIGN, "&eSearch Fish", Arrays.asList("&7Current: " + (String)(searchQuery.isEmpty() ? "&cNone" : "&a" + searchQuery), "", "&eClick to type a search query in chat."), e -> {
            player.closeInventory();
            this.pendingSearch.put(player.getUniqueId(), categoryFilter);
            TextUtil.sendMessage((CommandSender)player, "&eType the name of the fish you want to search for in chat. Type 'cancel' to cancel.");
        }));
        gui.open(player);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (this.pendingSearch.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            String query = event.getMessage();
            String cat = this.pendingSearch.remove(player.getUniqueId());
            Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
                if (query.equalsIgnoreCase("cancel")) {
                    this.openCollectionGUI(player, 0, cat, "");
                } else {
                    this.openCollectionGUI(player, 0, cat, query);
                }
            });
        }
    }
}

