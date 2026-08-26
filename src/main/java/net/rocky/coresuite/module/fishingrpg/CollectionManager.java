package net.rocky.coresuite.module.fishingrpg;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CollectionManager implements Listener {
    protected final CoreSuitePlugin plugin;
    private final SpeciesRegistry speciesRegistry;
    private final Map<UUID, Map<String, Double>> discoveredSpecies = new ConcurrentHashMap<>();

    public CollectionManager(CoreSuitePlugin plugin, SpeciesRegistry speciesRegistry) {
        this.plugin = plugin;
        this.speciesRegistry = speciesRegistry;
        initDatabase();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void initDatabase() {
        plugin.getDatabaseManager().execute("CREATE TABLE IF NOT EXISTS fishingrpg_collection (" +
                "uuid TEXT NOT NULL, " +
                "species_id TEXT NOT NULL, " +
                "max_weight REAL DEFAULT 0, " +
                "PRIMARY KEY (uuid, species_id))");
    }

    public void loadPlayer(UUID uuid) {
        plugin.getDatabaseManager().executeAsync(() -> {
            Map<String, Double> speciesMap = new ConcurrentHashMap<>();
            try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
                 java.sql.PreparedStatement ps = conn.prepareStatement("SELECT species_id, max_weight FROM fishingrpg_collection WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        speciesMap.put(rs.getString("species_id"), rs.getDouble("max_weight"));
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            discoveredSpecies.put(uuid, speciesMap);
        });
    }

    public void unloadPlayer(UUID uuid) {
        discoveredSpecies.remove(uuid);
    }

    public boolean hasDiscovered(UUID uuid, String speciesId) {
        return discoveredSpecies.containsKey(uuid) && discoveredSpecies.get(uuid).containsKey(speciesId);
    }

    public boolean recordCatch(UUID uuid, String speciesId, double weight) {
        Map<String, Double> map = discoveredSpecies.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
        boolean newDiscovery = !map.containsKey(speciesId);
        double currentBest = map.getOrDefault(speciesId, 0.0);
        
        if (newDiscovery || weight > currentBest) {
            map.put(speciesId, weight);
            plugin.getDatabaseManager().execute("INSERT INTO fishingrpg_collection (uuid, species_id, max_weight) VALUES (?, ?, ?) " +
                    "ON CONFLICT(uuid, species_id) DO UPDATE SET max_weight = ?", 
                    uuid.toString(), speciesId, weight, weight);
        }
        
        return newDiscovery;
    }

    public double getPersonalBest(UUID uuid, String speciesId) {
        if (!discoveredSpecies.containsKey(uuid)) return 0.0;
        return discoveredSpecies.get(uuid).getOrDefault(speciesId, 0.0);
    }

    public int getDiscoveredCount(UUID uuid) {
        return discoveredSpecies.containsKey(uuid) ? discoveredSpecies.get(uuid).size() : 0;
    }

    public int getTotalSpeciesCount() {
        return speciesRegistry.getSpeciesCount();
    }

        private static final int SLOTS_PER_PAGE = 45;
    private final java.util.Map<UUID, Integer> openPages = new java.util.concurrent.ConcurrentHashMap<>();

    public void openCollectionGUI(Player player, int page, String rarityFilter) {
        java.util.List<FishSpecies> allSpecies = new java.util.ArrayList<>(speciesRegistry.getAllSpecies());
        allSpecies.sort(java.util.Comparator.comparing(FishSpecies::getId)); // Sort alphabetically

        int totalSpecies = allSpecies.size();
        int maxPages = (int) Math.ceil((double) totalSpecies / SLOTS_PER_PAGE);
        if (page < 0) page = 0;
        if (page >= maxPages && maxPages > 0) page = maxPages - 1;

        openPages.put(player.getUniqueId(), page);
        Inventory gui = Bukkit.createInventory(null, 54, net.rocky.coresuite.common.text.TextUtil.legacyColorize("&8Fish Collection - Page " + (page + 1)));

        // Background
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        org.bukkit.inventory.meta.ItemMeta paneMeta = pane.getItemMeta();
        if (paneMeta != null) {
            paneMeta.setDisplayName(" ");
            pane.setItemMeta(paneMeta);
        }
        for (int i = 0; i < 54; i++) {
            gui.setItem(i, pane);
        }

        int startIndex = page * SLOTS_PER_PAGE;
        int endIndex = Math.min(startIndex + SLOTS_PER_PAGE, totalSpecies);
        
        for (int i = startIndex; i < endIndex; i++) {
            FishSpecies species = allSpecies.get(i);
            boolean discovered = hasDiscovered(player.getUniqueId(), species.getId());
            
            ItemStack item;
            if (discovered) {
                Material mat = Material.matchMaterial(species.getMaterial());
                if (mat == null) mat = Material.COD;
                item = new ItemStack(mat);
                org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(net.rocky.coresuite.common.text.TextUtil.legacyColorize(species.getRarity().getColorCode() + species.getName()));
                    java.util.List<String> lore = new java.util.ArrayList<>();
                    lore.add(net.rocky.coresuite.common.text.TextUtil.legacyColorize("&7Rarity: " + species.getRarity().getColorCode() + species.getRarity().getDisplayName()));
                    lore.add(net.rocky.coresuite.common.text.TextUtil.legacyColorize("&7Weight: &e" + species.getMinWeight() + " - " + species.getMaxWeight() + "kg"));
                    double pb = getPersonalBest(player.getUniqueId(), species.getId());
                    if (pb > 0) {
                    
                        lore.add(net.rocky.coresuite.common.text.TextUtil.legacyColorize("&7Personal Best: &a" + String.format("%.2f", pb) + "kg"));
                    }
                    lore.add("");
                    for (String l : species.getLore()) {
                        lore.add(net.rocky.coresuite.common.text.TextUtil.legacyColorize(l));
                    }
                    meta.setLore(lore);
                    
                    if (species.getItemModel() != null && !species.getItemModel().isEmpty()) {
                        org.bukkit.NamespacedKey nsk = org.bukkit.NamespacedKey.fromString(species.getItemModel());
                        if (nsk != null) {
                            try { meta.setItemModel(nsk); } catch (Throwable ignored) {} // For backwards compat, skip if method doesn't exist
                        }
                    }
                    item.setItemMeta(meta);
                }
            } else {
                item = new ItemStack(Material.PAPER);
                org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName(net.rocky.coresuite.common.text.TextUtil.legacyColorize("&8??? Unknown Species"));
                    java.util.List<String> lore = new java.util.ArrayList<>();
                    lore.add(net.rocky.coresuite.common.text.TextUtil.legacyColorize("&7Rarity: " + species.getRarity().getColorCode() + species.getRarity().getDisplayName()));
                    lore.add(net.rocky.coresuite.common.text.TextUtil.legacyColorize("&cCatch this fish to reveal it!"));
                    meta.setLore(lore);
                    item.setItemMeta(meta);
                }
            }
            gui.setItem(i - startIndex, item);
        }

        // Navigation
        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            org.bukkit.inventory.meta.ItemMeta meta = prev.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(net.rocky.coresuite.common.text.TextUtil.legacyColorize("&e<- Previous Page"));
                prev.setItemMeta(meta);
            }
            gui.setItem(45, prev);
        }
        
        if (page < maxPages - 1) {
            ItemStack next = new ItemStack(Material.ARROW);
            org.bukkit.inventory.meta.ItemMeta meta = next.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(net.rocky.coresuite.common.text.TextUtil.legacyColorize("&eNext Page ->"));
                next.setItemMeta(meta);
            }
            gui.setItem(53, next);
        }

        // Stats
        ItemStack stats = new ItemStack(Material.BOOK);
        org.bukkit.inventory.meta.ItemMeta statsMeta = stats.getItemMeta();
        if (statsMeta != null) {
            statsMeta.setDisplayName(net.rocky.coresuite.common.text.TextUtil.legacyColorize("&aCollection Stats"));
            java.util.List<String> lore = new java.util.ArrayList<>();
            lore.add(net.rocky.coresuite.common.text.TextUtil.legacyColorize("&7Discovered: &e" + getDiscoveredCount(player.getUniqueId()) + " / " + totalSpecies));
            double completion = totalSpecies == 0 ? 0 : (double) getDiscoveredCount(player.getUniqueId()) / totalSpecies * 100;
            lore.add(net.rocky.coresuite.common.text.TextUtil.legacyColorize("&7Completion: &a" + String.format("%.1f%%", completion)));
            statsMeta.setLore(lore);
            stats.setItemMeta(statsMeta);
        }
        gui.setItem(49, stats);

        player.openInventory(gui);
    }

    public void shutdown() {
        discoveredSpecies.clear();
        
        openPages.clear();
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getView().getTitle().contains("Collection")) {
            event.setCancelled(true);
            Integer currentPage = openPages.get(player.getUniqueId());
            if (currentPage == null) return;
            
            Inventory clicked = event.getClickedInventory();
            if (clicked != null && clicked.equals(event.getView().getTopInventory())) {
                int slot = event.getSlot();
                if (slot == 45 && currentPage > 0) {
                    openCollectionGUI(player, currentPage - 1, null);
                } else if (slot == 53) {
                    openCollectionGUI(player, currentPage + 1, null);
                }
            }
        }
    }
    
    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().contains("Collection")) {
            openPages.remove(event.getPlayer().getUniqueId());
        }
    }
}
