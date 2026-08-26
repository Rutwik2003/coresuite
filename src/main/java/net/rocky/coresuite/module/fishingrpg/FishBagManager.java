package net.rocky.coresuite.module.fishingrpg;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class FishBagManager implements Listener {
    protected final CoreSuitePlugin plugin;
    private final Map<UUID, ItemStack[]> bagCache = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> openPages = new HashMap<>();
    private static final int MAX_PAGES = 10;
    private static final int SLOTS_PER_PAGE = 45;
    private static final int TOTAL_SLOTS = MAX_PAGES * SLOTS_PER_PAGE;

    public FishBagManager(CoreSuitePlugin plugin) {
        this.plugin = plugin;
        initDatabase();
    }

    public void initDatabase() {
        plugin.getDatabaseManager().execute("CREATE TABLE IF NOT EXISTS fishingrpg_fish_bag (" +
                "uuid VARCHAR(36) PRIMARY KEY, " +
                "inventory_data TEXT)");
    }

    public void loadPlayer(UUID uuid) {
        plugin.getDatabaseManager().executeAsync(() -> {
            try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
                 java.sql.PreparedStatement ps = conn.prepareStatement("SELECT inventory_data FROM fishingrpg_fish_bag WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        String data = rs.getString("inventory_data");
                        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
                        BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
                        ItemStack[] items = new ItemStack[dataInput.readInt()];
                        for (int i = 0; i < items.length; i++) {
                            items[i] = (ItemStack) dataInput.readObject();
                        }
                        dataInput.close();
                        
                        ItemStack[] padded = new ItemStack[TOTAL_SLOTS];
                        System.arraycopy(items, 0, padded, 0, Math.min(items.length, TOTAL_SLOTS));
                        bagCache.put(uuid, padded);
                    } else {
                        bagCache.put(uuid, new ItemStack[TOTAL_SLOTS]);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load fish bag for " + uuid);
                bagCache.put(uuid, new ItemStack[TOTAL_SLOTS]);
            }
        });
    }

    public void unloadPlayer(UUID uuid) {
        savePlayer(uuid);
        bagCache.remove(uuid);
        openPages.remove(uuid);
    }

    public void openBagGUI(Player player, int page) {
        if (page < 0) page = 0;
        if (page >= MAX_PAGES) page = MAX_PAGES - 1;
        
        ItemStack[] items = bagCache.get(player.getUniqueId());
        if (items == null) {
            TextUtil.sendMessage(player, "&cYour fish bag is still loading...");
            return;
        }
        
        openPages.put(player.getUniqueId(), page);
        Inventory gui = Bukkit.createInventory(null, 54, TextUtil.legacyColorize("&8Fish Bag - Page " + (page + 1)));
        
        int startIndex = page * SLOTS_PER_PAGE;
        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            gui.setItem(i, items[startIndex + i]);
        }
        
        ItemStack glass = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta glassMeta = glass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.setDisplayName(" ");
            glass.setItemMeta(glassMeta);
        }
        for (int i = 45; i < 54; i++) {
            gui.setItem(i, glass);
        }
        
        if (page > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            ItemMeta meta = prev.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(TextUtil.legacyColorize("&e<- Previous Page"));
                prev.setItemMeta(meta);
            }
            gui.setItem(45, prev);
        }
        
        if (page < MAX_PAGES - 1) {
            ItemStack next = new ItemStack(Material.ARROW);
            ItemMeta meta = next.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(TextUtil.legacyColorize("&eNext Page ->"));
                next.setItemMeta(meta);
            }
            gui.setItem(53, next);
        }
        
        ItemStack sellAll = new ItemStack(Material.GOLD_INGOT);
        ItemMeta sellMeta = sellAll.getItemMeta();
        if (sellMeta != null) {
            sellMeta.setDisplayName(TextUtil.legacyColorize("&6Sell All Fish"));
            sellAll.setItemMeta(sellMeta);
        }
        gui.setItem(49, sellAll);
        
        player.openInventory(gui);
    }

    public void savePlayer(UUID uuid) {
        ItemStack[] items = bagCache.get(uuid);
        if (items == null) return;
        plugin.getDatabaseManager().executeAsync(() -> {
            try {
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
                dataOutput.writeInt(items.length);
                for (ItemStack item : items) {
                    dataOutput.writeObject(item);
                }
                dataOutput.close();
                String data = Base64.getEncoder().encodeToString(outputStream.toByteArray());
                plugin.getDatabaseManager().execute("INSERT INTO fishingrpg_fish_bag (uuid, inventory_data) VALUES (?, ?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET inventory_data = ?", uuid.toString(), data, data);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void saveAll() {
        for (UUID uuid : bagCache.keySet()) {
            savePlayer(uuid);
        }
    }

    public void shutdown() {
        saveAll();
        bagCache.clear();
        openPages.clear();
    }

    private void syncPageToCache(Player player, Inventory gui, int page) {
        ItemStack[] items = bagCache.get(player.getUniqueId());
        if (items == null) return;
        int startIndex = page * SLOTS_PER_PAGE;
        for (int i = 0; i < SLOTS_PER_PAGE; i++) {
            items[startIndex + i] = gui.getItem(i);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        
        if (event.getView().getTitle().contains("Fish Bag")) {
            event.setCancelled(true);
            
            Integer currentPage = openPages.get(player.getUniqueId());
            if (currentPage == null) return;
            
            Inventory clicked = event.getClickedInventory();
            if (clicked == null) return;
            
            if (clicked.equals(event.getView().getTopInventory())) {
                int slot = event.getSlot();
                if (slot < 45) {
                    ItemStack item = clicked.getItem(slot);
                    if (item != null && item.getType() != Material.AIR) {
                        if (player.getInventory().firstEmpty() != -1) {
                            player.getInventory().addItem(item);
                            clicked.setItem(slot, null);
                            syncPageToCache(player, clicked, currentPage);
                        } else {
                            TextUtil.sendMessage(player, "&cYour inventory is full!");
                        }
                    }
                } else {
                    if (slot == 45 && currentPage > 0) {
                        syncPageToCache(player, clicked, currentPage);
                        openBagGUI(player, currentPage - 1);
                    } else if (slot == 53 && currentPage < MAX_PAGES - 1) {
                        syncPageToCache(player, clicked, currentPage);
                        openBagGUI(player, currentPage + 1);
                    } else if (slot == 49) {
                        syncPageToCache(player, clicked, currentPage);
                        sellAll(player);
                    }
                }
            } else if (clicked.equals(player.getInventory())) {
                ItemStack item = clicked.getItem(event.getSlot());
                if (item != null && item.getType() != Material.AIR) {
                    if (item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(new org.bukkit.NamespacedKey(plugin, "fish_species"), PersistentDataType.STRING)) {
                        Inventory top = event.getView().getTopInventory();
                        boolean placed = false;
                        for (int i = 0; i < 45; i++) {
                            if (top.getItem(i) == null || top.getItem(i).getType() == Material.AIR) {
                                top.setItem(i, item);
                                clicked.setItem(event.getSlot(), null);
                                syncPageToCache(player, top, currentPage);
                                placed = true;
                                break;
                            }
                        }
                        if (!placed) {
                            TextUtil.sendMessage(player, "&cThis page is full! Switch pages to deposit more.");
                        }
                    } else {
                        TextUtil.sendMessage(player, "&cYou can only put caught fish in this bag.");
                    }
                }
            }
        }
    }
    
    private void sellAll(Player player) {
        ItemStack[] items = bagCache.get(player.getUniqueId());
        if (items == null) return;
        
        double totalMoney = 0;
        int count = 0;
        org.bukkit.NamespacedKey priceKey = new org.bukkit.NamespacedKey(plugin, "fish_price");
        
        for (int i = 0; i < items.length; i++) {
            ItemStack item = items[i];
            if (item != null && item.hasItemMeta()) {
                Double price = item.getItemMeta().getPersistentDataContainer().get(priceKey, PersistentDataType.DOUBLE);
                if (price != null) {
                    totalMoney += price;
                    count++;
                    items[i] = null;
                }
            }
        }
        
        if (count > 0) {
            plugin.getEconomyManager().deposit(player, totalMoney);
            TextUtil.sendMessage(player, "&aSold " + count + " fish for &e$" + TextUtil.formatNumber(totalMoney) + "&a!");
            Integer page = openPages.get(player.getUniqueId());
            if (page != null) {
                openBagGUI(player, page);
            }
        } else {
            TextUtil.sendMessage(player, "&cNo sellable fish found in your bag.");
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getView().getTitle().contains("Fish Bag")) {
            Player player = (Player) event.getPlayer();
            Integer page = openPages.remove(player.getUniqueId());
            if (page != null) {
                syncPageToCache(player, event.getInventory(), page);
            }
        }
    }
}
