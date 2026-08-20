package net.rocky.coresuite.module.customfishing;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.gui.GuiButton;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

public class FishingCollectionManager {

    private final CoreSuitePlugin plugin;
    private final CustomFishingModule module;

    public FishingCollectionManager(CoreSuitePlugin plugin, CustomFishingModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    public void openCollectionGUI(Player player) {
        openCollectionGUI(player, 0);
    }

    public void openCollectionGUI(Player player, int page) {
        Gui gui = new Gui("&8Fishing Collection", 6);
        gui.fillBorder(Material.GRAY_STAINED_GLASS_PANE);
        
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
                ConfigurationSection speciesSection = module.getConfig().getConfigurationSection("species");
                if (speciesSection != null) {
                    List<String> keys = new ArrayList<>(speciesSection.getKeys(false));
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
                        double pricePerLb = module.getConfig().getDouble(spPath + "price-per-kg", 0);
                        List<String> biomes = module.getConfig().getStringList(spPath + "biomes");
                        
                        String speciesId = org.bukkit.ChatColor.stripColor(org.bukkit.ChatColor.translateAlternateColorCodes('&', name));
                        
                        while (slot % 9 == 0 || slot % 9 == 8) slot++;
                        
                        if (caughtSpecies.containsKey(speciesId)) {
                            double maxWeight = caughtSpecies.get(speciesId);
                            String material = module.getConfig().getString(spPath + "material", "COD");
                            String itemModel = module.getConfig().getString(spPath + "item-model", "");
                            int cmd = module.getConfig().getInt(spPath + "custom-model-data", -1);
                            
                            Material mat = Material.COD;
                            try {
                                mat = Material.valueOf(material.toUpperCase());
                            } catch (Exception ignored) {}
                            
                            ItemStack fishItem = new ItemStack(mat);
                            ItemMeta meta = fishItem.getItemMeta();
                            if (meta != null) {
                                meta.displayName(TextUtil.colorize(name));
                                List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                                lore.add(TextUtil.colorize("&7Personal Best: &b" + maxWeight + " kg"));
                                meta.lore(lore);
                                
                                if (itemModel != null && !itemModel.isEmpty()) {
                                    meta.setItemModel(NamespacedKey.fromString(itemModel));
                                }
                                if (cmd != -1) {
                                    meta.setCustomModelData(cmd);
                                }
                                fishItem.setItemMeta(meta);
                            }
                            gui.setItem(slot, new GuiButton(fishItem, e -> {}));
                        } else {
                            ItemStack unknown = new ItemStack(Material.PAPER);
                            ItemMeta meta = unknown.getItemMeta();
                            meta.displayName(TextUtil.colorize("&8???"));
                            meta.setItemModel(org.bukkit.NamespacedKey.fromString("barely_default:fish_unknown"));
                            List<net.kyori.adventure.text.Component> lore = new ArrayList<>();
                            lore.add(TextUtil.colorize("&7You haven't caught this yet."));
                            if (biomes != null && !biomes.isEmpty()) {
                                lore.add(net.kyori.adventure.text.Component.empty());
                                lore.add(TextUtil.colorize("&7Required Biomes:"));
                                for (String b : biomes) {
                                    lore.add(TextUtil.colorize("&8- &7" + b));
                                }
                            }
                            meta.lore(lore);
                            unknown.setItemMeta(meta);
                            gui.setItem(slot, new GuiButton(unknown, e -> {}));
                        }
                        slot++;
                    }
                    
                    if (page > 0) {
                        gui.setItem(45, GuiButton.of(Material.ARROW, "&aPrevious Page", Arrays.asList("&7Go to page " + page), e -> openCollectionGUI(player, page - 1)));
                    }
                    if (page < totalPages - 1) {
                        gui.setItem(53, GuiButton.of(Material.ARROW, "&aNext Page", Arrays.asList("&7Go to page " + (page + 2)), e -> openCollectionGUI(player, page + 1)));
                    }
                }
                
                gui.setItem(49, GuiButton.of(Material.BARRIER, "&c&lBack", Arrays.asList("&7Return to the main menu."), e -> player.performCommand("fish")));
                gui.open(player);
            });
        });
    }
}

