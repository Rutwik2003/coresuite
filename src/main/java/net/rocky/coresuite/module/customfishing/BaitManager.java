package net.rocky.coresuite.module.customfishing;

import net.kyori.adventure.text.Component;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.gui.GuiButton;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BaitManager implements Listener {
    private final CoreSuitePlugin plugin;
    private final CustomFishingModule module;
    private final NamespacedKey baitIdKey;
    private final Map<UUID, ActiveBait> activeBaits = new ConcurrentHashMap<>();

    public static class ActiveBait {
        public final String baitId;
        public final String type; // "casts" or "time"
        public int castsRemaining;
        public long expiresAt;
        public final double catchChanceBonus;
        public final double weightBonus;
        public final String rarityBias;

        public ActiveBait(String baitId, String type, int castsRemaining, long expiresAt, double catchChanceBonus, double weightBonus, String rarityBias) {
            this.baitId = baitId;
            this.type = type;
            this.castsRemaining = castsRemaining;
            this.expiresAt = expiresAt;
            this.catchChanceBonus = catchChanceBonus;
            this.weightBonus = weightBonus;
            this.rarityBias = rarityBias;
        }
    }

    public BaitManager(CoreSuitePlugin plugin, CustomFishingModule module) {
        this.plugin = plugin;
        this.module = module;
        this.baitIdKey = new NamespacedKey(plugin, "bait_id");
        Bukkit.getPluginManager().registerEvents(this, plugin);

        // Load for already online players (e.g. during /coresuite reload)
        for (Player p : Bukkit.getOnlinePlayers()) {
            loadPlayerBait(p.getUniqueId());
        }
    }

    private void loadPlayerBait(UUID uuid) {
        plugin.getDatabaseManager().executeAsync(() -> {
            try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
                 java.sql.PreparedStatement stmt = conn.prepareStatement("SELECT * FROM coresuite_active_baits WHERE uuid = ?")) {
                stmt.setString(1, uuid.toString());
                try (java.sql.ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        ActiveBait bait = new ActiveBait(
                                rs.getString("bait_id"),
                                rs.getString("bait_type"),
                                rs.getInt("casts_remaining"),
                                rs.getLong("expires_at"),
                                rs.getDouble("catch_bonus"),
                                rs.getDouble("weight_bonus"),
                                rs.getString("rarity_bias")
                        );
                        if ("time".equalsIgnoreCase(bait.type) && System.currentTimeMillis() > bait.expiresAt) {
                            try (java.sql.PreparedStatement deleteStmt = conn.prepareStatement("DELETE FROM coresuite_active_baits WHERE uuid = ?")) {
                                deleteStmt.setString(1, uuid.toString());
                                deleteStmt.executeUpdate();
                            }
                        } else {
                            activeBaits.put(uuid, bait);
                        }
                    }
                }
            } catch (Exception e) {}
        });
    }

    public ItemStack createBaitItem(String baitId) {
        ConfigurationSection sec = module.getConfig().getConfigurationSection("baits." + baitId);
        if (sec == null) return null;

        Material material = Material.matchMaterial(sec.getString("material", "SLIME_BALL"));
        if (material == null) material = Material.SLIME_BALL;

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.colorize(sec.getString("name", "&aBait")));
            List<String> loreStrings = sec.getStringList("lore");
            List<Component> lore = new ArrayList<>();
            for (String l : loreStrings) {
                lore.add(TextUtil.colorize(l));
            }
            meta.lore(lore);
            
            if (sec.contains("item-model")) {
                int customModelData = sec.getInt("item-model", 0);
                if (customModelData > 0) {
                    meta.setCustomModelData(customModelData);
                }
            }

            meta.getPersistentDataContainer().set(baitIdKey, PersistentDataType.STRING, baitId);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void applyBait(Player player, ItemStack baitItem) {
        ItemMeta meta = baitItem.getItemMeta();
        if (meta == null) return;
        
        String baitId = meta.getPersistentDataContainer().get(baitIdKey, PersistentDataType.STRING);
        if (baitId == null) return;

        if (getActiveBait(player.getUniqueId()) != null) {
            TextUtil.sendMessage(player, "&cYou already have active bait! Wait for it to expire.");
            return;
        }

        ConfigurationSection sec = module.getConfig().getConfigurationSection("baits." + baitId);
        if (sec == null) return;

        String type = sec.getString("type", "casts");
        int casts = sec.getInt("casts", 20);
        if (casts <= 0) casts = 20; // Safe fallback
        int durationSeconds = sec.getInt("duration-seconds", 300);
        if (durationSeconds <= 0) durationSeconds = 300; // Safe fallback
        double catchBonus = sec.getDouble("catch-chance-bonus", 0.15);
        double weightBonus = sec.getDouble("weight-bonus", 0.0);
        String rarityBias = sec.getString("rarity-bias", "");
        String name = sec.getString("name", "Bait");

        long expiresAt = 0;
        if ("time".equalsIgnoreCase(type)) {
            expiresAt = System.currentTimeMillis() + (durationSeconds * 1000L);
        }

        ActiveBait activeBait = new ActiveBait(baitId, type, casts, expiresAt, catchBonus, weightBonus, rarityBias);
        activeBaits.put(player.getUniqueId(), activeBait);

        baitItem.setAmount(baitItem.getAmount() - 1);

        if ("time".equalsIgnoreCase(type)) {
            TextUtil.sendMessage(player, "&8[Fishing] &aYou applied &e" + name + " &ato your rod! &7(" + durationSeconds + "s remaining)");
        } else {
            TextUtil.sendMessage(player, "&8[Fishing] &aYou applied &e" + name + " &ato your rod! &7(" + casts + " casts remaining)");
        }
        
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
    }

    public ActiveBait getActiveBait(UUID uuid) {
        ActiveBait bait = activeBaits.get(uuid);
        if (bait == null) return null;

        if ("time".equalsIgnoreCase(bait.type)) {
            if (System.currentTimeMillis() > bait.expiresAt) {
                activeBaits.remove(uuid);
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    ConfigurationSection sec = module.getConfig().getConfigurationSection("baits." + bait.baitId);
                    String name = sec != null ? sec.getString("name", "Bait") : "Bait";
                    TextUtil.sendMessage(p, "&8[Fishing] &cYour bait &e" + name + " &chas expired.");
                }
                return null;
            }
        }
        return bait;
    }

    public void consumeCast(Player player) {
        ActiveBait bait = getActiveBait(player.getUniqueId());
        if (bait == null) return;

        ConfigurationSection sec = module.getConfig().getConfigurationSection("baits." + bait.baitId);
        String name = sec != null ? sec.getString("name", "Bait") : "Bait";

        if ("casts".equalsIgnoreCase(bait.type)) {
            bait.castsRemaining--;
            if (bait.castsRemaining <= 0) {
                activeBaits.remove(player.getUniqueId());
                TextUtil.sendMessage(player, "&8[Fishing] &cYour bait &e" + name + " &chas run out.");
            } else {
                TextUtil.sendMessage(player, "&8[Fishing] &7Bait: &e" + name + " &7- &b" + bait.castsRemaining + " casts left");
            }
        } else if ("time".equalsIgnoreCase(bait.type)) {
            long remainingMs = bait.expiresAt - System.currentTimeMillis();
            if (remainingMs > 0) {
                long remainingSec = remainingMs / 1000;
                TextUtil.sendMessage(player, "&8[Fishing] &7Bait: &e" + name + " &7- &b" + remainingSec + "s left");
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR || item.getType() == Material.FISHING_ROD) return;

        if (!item.hasItemMeta()) return;

        if (item.getItemMeta().getPersistentDataContainer().has(baitIdKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
            applyBait(event.getPlayer(), item);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        ActiveBait bait = activeBaits.remove(uuid);
        
        plugin.getDatabaseManager().executeAsync(() -> {
            if (bait != null) {
                try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
                     java.sql.PreparedStatement stmt = conn.prepareStatement(
                        "INSERT INTO coresuite_active_baits (uuid, bait_id, bait_type, casts_remaining, expires_at, catch_bonus, weight_bonus, rarity_bias) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                        "ON CONFLICT(uuid) DO UPDATE SET " +
                        "bait_id=excluded.bait_id, bait_type=excluded.bait_type, casts_remaining=excluded.casts_remaining, " +
                        "expires_at=excluded.expires_at, catch_bonus=excluded.catch_bonus, weight_bonus=excluded.weight_bonus, rarity_bias=excluded.rarity_bias")) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, bait.baitId);
                    stmt.setString(3, bait.type);
                    stmt.setInt(4, bait.castsRemaining);
                    stmt.setLong(5, bait.expiresAt);
                    stmt.setDouble(6, bait.catchChanceBonus);
                    stmt.setDouble(7, bait.weightBonus);
                    stmt.setString(8, bait.rarityBias);
                    stmt.executeUpdate();
                } catch (Exception e) {}
            } else {
                try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
                     java.sql.PreparedStatement stmt = conn.prepareStatement("DELETE FROM coresuite_active_baits WHERE uuid = ?")) {
                    stmt.setString(1, uuid.toString());
                    stmt.executeUpdate();
                } catch (Exception e) {}
            }
        });
    }

    @EventHandler
    public void onPlayerJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        loadPlayerBait(event.getPlayer().getUniqueId());
    }

    public static class BaitStock {
        public int boughtAmount;
        public long lastRestock;
        public BaitStock(int boughtAmount, long lastRestock) {
            this.boughtAmount = boughtAmount;
            this.lastRestock = lastRestock;
        }
    }

    public void openBaitShopGUI(Player player) {
        plugin.getDatabaseManager().executeAsync(() -> {
            Map<String, BaitStock> stockMap = new HashMap<>();
            try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
                 java.sql.PreparedStatement stmt = conn.prepareStatement("SELECT bait_id, bought_amount, last_restock FROM coresuite_bait_stock WHERE uuid = ?")) {
                stmt.setString(1, player.getUniqueId().toString());
                try (java.sql.ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        stockMap.put(rs.getString("bait_id"), new BaitStock(rs.getInt("bought_amount"), rs.getLong("last_restock")));
                    }
                }
            } catch (Exception e) {}

            Bukkit.getScheduler().runTask(plugin, () -> {
                openBaitShopGUIInternal(player, stockMap);
            });
        });
    }

    private void openBaitShopGUIInternal(Player player, Map<String, BaitStock> stockMap) {
        ConfigurationSection baitsSec = module.getConfig().getConfigurationSection("baits");
        if (baitsSec == null) {
            TextUtil.sendMessage(player, "&cNo baits are available in the shop.");
            return;
        }

        int size = baitsSec.getKeys(false).size();
        int rows = size <= 4 ? 4 : (size <= 7 ? 4 : 5); // Extra row for buttons
        
        Gui gui = new Gui("&8Bait Shop", rows);
        gui.fillBorder(Material.ORANGE_STAINED_GLASS_PANE);

        int[] customSlots;
        if (size == 4) {
            customSlots = new int[]{12, 14, 21, 23};
        } else if (size == 3) {
            customSlots = new int[]{11, 13, 15};
        } else if (size == 2) {
            customSlots = new int[]{12, 14};
        } else {
            customSlots = new int[size];
            int startSlot = 9 + ((9 - Math.min(size, 7)) / 2);
            int curSlot = startSlot;
            for(int i = 0; i < size; i++) {
                if (curSlot == 17) curSlot = 19 + ((9 - (size - 7)) / 2);
                customSlots[i] = curSlot++;
            }
        }

        int index = 0;
        for (String baitId : baitsSec.getKeys(false)) {
            if (index >= customSlots.length) break;
            int slot = customSlots[index++];
            if (slot >= rows * 9 - 9) break; 
            
            ConfigurationSection sec = baitsSec.getConfigurationSection(baitId);
            if (sec == null) continue;

            String name = sec.getString("name", "&aBait");
            double rawPrice = sec.getDouble("price", -1.0);
            final double price = (rawPrice < 0) ? 5000.0 : rawPrice; // Safe fallback
            int dailyLimit = sec.getInt("daily-limit", -1);
            
            BaitStock stock = stockMap.getOrDefault(baitId, new BaitStock(0, System.currentTimeMillis()));
            
            long restockInterval = 24 * 60 * 60 * 1000L;
            if (System.currentTimeMillis() - stock.lastRestock >= restockInterval) {
                stock.boughtAmount = 0;
                stock.lastRestock = System.currentTimeMillis();
            }

            List<String> lore = new ArrayList<>(sec.getStringList("lore"));
            lore.add("");
            lore.add("&7Price: &e$" + plugin.getEconomyManager().format(price));
            
            if (dailyLimit > 0) {
                int left = Math.max(0, dailyLimit - stock.boughtAmount);
                lore.add("&7Daily Stock: " + (left > 0 ? "&a" : "&c") + left + "&8/&7" + dailyLimit);
                if (stock.boughtAmount > 0) {
                    long timeLeft = restockInterval - (System.currentTimeMillis() - stock.lastRestock);
                    if (timeLeft < 0) timeLeft = 0;
                    long hours = timeLeft / (60 * 60 * 1000L);
                    long mins = (timeLeft % (60 * 60 * 1000L)) / (60 * 1000L);
                    lore.add("&7Restocks in: &e" + hours + "h " + mins + "m");
                }
            }

            Material material = Material.matchMaterial(sec.getString("material", "SLIME_BALL"));
            if (material == null) material = Material.SLIME_BALL;

            GuiButton button = GuiButton.of(material, name, lore, e -> {
                e.setCancelled(true);
                if (dailyLimit > 0 && stock.boughtAmount >= dailyLimit) {
                    TextUtil.sendMessage(player, "&cThis item is out of stock today! Check back later or use Force Restock.");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }
                
                if (plugin.getEconomyManager().has(player, price)) {
                    plugin.getEconomyManager().withdraw(player, price);
                    
                    if (dailyLimit > 0) {
                        stock.boughtAmount++;
                        // Always ensure lastRestock is saved if it's the first purchase
                        stockMap.put(baitId, stock); 
                        plugin.getDatabaseManager().executeAsync(() -> {
                            plugin.getDatabaseManager().execute("INSERT INTO coresuite_bait_stock (uuid, bait_id, bought_amount, last_restock) VALUES (?, ?, ?, ?) ON CONFLICT(uuid, bait_id) DO UPDATE SET bought_amount = excluded.bought_amount, last_restock = excluded.last_restock", player.getUniqueId().toString(), baitId, stock.boughtAmount, stock.lastRestock);
                        });
                    }
                    
                    ItemStack baitItem = createBaitItem(baitId);
                    if (baitItem != null) {
                        Map<Integer, ItemStack> leftover = player.getInventory().addItem(baitItem);
                        if (!leftover.isEmpty()) {
                            player.getWorld().dropItem(player.getLocation(), leftover.get(0));
                        }
                        TextUtil.sendMessage(player, "&aYou purchased " + name + " &afor $" + plugin.getEconomyManager().format(price) + "!");
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                        openBaitShopGUIInternal(player, stockMap); // Refresh gui
                    }
                } else {
                    TextUtil.sendMessage(player, "&cYou do not have enough money!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
            });
            
            if (sec.contains("item-model")) {
                int cmd = sec.getInt("item-model", 0);
                if (cmd > 0) {
                    button = new GuiButton(button.getItem(), button.getHandler());
                    org.bukkit.inventory.meta.ItemMeta meta = button.getItem().getItemMeta();
                    if (meta != null) {
                        meta.setCustomModelData(cmd);
                        button.getItem().setItemMeta(meta);
                    }
                }
            }
            
            gui.setItem(slot++, button);
        }
        
        double forcePrice = module.getConfig().getDouble("bait-shop.force-restock-price", 250000.0);

        gui.setItem(rows * 9 - 5, GuiButton.of(
            Material.GOLD_INGOT, 
            "&6&lForce Restock", 
            java.util.Arrays.asList(
                "&7Pay &e$" + plugin.getEconomyManager().format(forcePrice) + " &7to instantly",
                "&7restore half of your daily stock",
                "&7for all limited baits."
            ), 
            e -> {
                e.setCancelled(true);
                if (plugin.getEconomyManager().has(player, forcePrice)) {
                    plugin.getEconomyManager().withdraw(player, forcePrice);
                    for (String baitId : stockMap.keySet()) {
                        BaitStock stock = stockMap.get(baitId);
                        stock.boughtAmount = Math.max(0, stock.boughtAmount / 2);
                        plugin.getDatabaseManager().executeAsync(() -> {
                            plugin.getDatabaseManager().execute("INSERT INTO coresuite_bait_stock (uuid, bait_id, bought_amount, last_restock) VALUES (?, ?, ?, ?) ON CONFLICT(uuid, bait_id) DO UPDATE SET bought_amount = excluded.bought_amount, last_restock = excluded.last_restock", player.getUniqueId().toString(), baitId, stock.boughtAmount, stock.lastRestock);
                        });
                    }
                    TextUtil.sendMessage(player, "&aYou forcefully restocked the bait shop for $" + plugin.getEconomyManager().format(forcePrice) + "!");
                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                    openBaitShopGUIInternal(player, stockMap);
                } else {
                    TextUtil.sendMessage(player, "&cYou do not have enough money to force restock! ($" + plugin.getEconomyManager().format(forcePrice) + " required)");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
            }
        ));

        gui.setItem(rows * 9 - 1, GuiButton.of(Material.BARRIER, "&cBack", List.of("&7Click to go back"), e -> {
            e.setCancelled(true);
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> player.performCommand("fish"));
        }));

        gui.open(player);
    }

    public void shutdown() {
        for (Map.Entry<UUID, ActiveBait> entry : activeBaits.entrySet()) {
            UUID uuid = entry.getKey();
            ActiveBait bait = entry.getValue();
            try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
                 java.sql.PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO coresuite_active_baits (uuid, bait_id, bait_type, casts_remaining, expires_at, catch_bonus, weight_bonus, rarity_bias) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON CONFLICT(uuid) DO UPDATE SET " +
                    "bait_id=excluded.bait_id, bait_type=excluded.bait_type, casts_remaining=excluded.casts_remaining, " +
                    "expires_at=excluded.expires_at, catch_bonus=excluded.catch_bonus, weight_bonus=excluded.weight_bonus, rarity_bias=excluded.rarity_bias")) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, bait.baitId);
                stmt.setString(3, bait.type);
                stmt.setInt(4, bait.castsRemaining);
                stmt.setLong(5, bait.expiresAt);
                stmt.setDouble(6, bait.catchChanceBonus);
                stmt.setDouble(7, bait.weightBonus);
                stmt.setString(8, bait.rarityBias);
                stmt.executeUpdate();
            } catch (Exception e) {}
        }
        activeBaits.clear();
    }
}
