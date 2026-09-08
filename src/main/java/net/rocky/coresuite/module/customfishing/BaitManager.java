/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  org.bukkit.Bukkit
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.OfflinePlayer
 *  org.bukkit.Sound
 *  org.bukkit.command.CommandSender
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.Action
 *  org.bukkit.event.player.PlayerInteractEvent
 *  org.bukkit.event.player.PlayerJoinEvent
 *  org.bukkit.event.player.PlayerQuitEvent
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.persistence.PersistentDataType
 *  org.bukkit.plugin.Plugin
 */
package net.rocky.coresuite.module.customfishing;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.Component;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.gui.GuiButton;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.customfishing.CustomFishingModule;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public class BaitManager
implements Listener {
    private final CoreSuitePlugin plugin;
    private final CustomFishingModule module;
    private final NamespacedKey baitIdKey;
    private final Map<UUID, ActiveBait> activeBaits = new ConcurrentHashMap<UUID, ActiveBait>();

    public BaitManager(CoreSuitePlugin plugin, CustomFishingModule module) {
        this.plugin = plugin;
        this.module = module;
        this.baitIdKey = new NamespacedKey((Plugin)plugin, "bait_id");
        for (Player p : Bukkit.getOnlinePlayers()) {
            this.loadPlayerBait(p.getUniqueId());
        }
    }

    private void loadPlayerBait(UUID uuid) {
        this.plugin.getDatabaseManager().executeAsync(() -> {
            block27: {
                try (Connection conn = this.plugin.getDatabaseManager().getConnection();
                     PreparedStatement stmt = conn.prepareStatement("SELECT * FROM coresuite_active_baits WHERE uuid = ?");){
                    stmt.setString(1, uuid.toString());
                    try (ResultSet rs = stmt.executeQuery();){
                        if (!rs.next()) break block27;
                        ActiveBait bait = new ActiveBait(rs.getString("bait_id"), rs.getString("bait_type"), rs.getInt("casts_remaining"), rs.getLong("expires_at"), rs.getDouble("catch_bonus"), rs.getDouble("weight_bonus"), rs.getString("rarity_bias"));
                        if ("time".equalsIgnoreCase(bait.type) && System.currentTimeMillis() > bait.expiresAt) {
                            try (PreparedStatement deleteStmt = conn.prepareStatement("DELETE FROM coresuite_active_baits WHERE uuid = ?");){
                                deleteStmt.setString(1, uuid.toString());
                                deleteStmt.executeUpdate();
                                break block27;
                            }
                        }
                        this.activeBaits.put(uuid, bait);
                    }
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
        });
    }

    public ItemStack createBaitItem(String baitId) {
        ItemStack item;
        ItemMeta meta;
        ConfigurationSection sec = this.module.getConfig().getConfigurationSection("baits." + baitId);
        if (sec == null) {
            return null;
        }
        Material material = Material.matchMaterial((String)sec.getString("material", "SLIME_BALL"));
        if (material == null) {
            material = Material.SLIME_BALL;
        }
        if ((meta = (item = new ItemStack(material)).getItemMeta()) != null) {
            int customModelData;
            meta.displayName(TextUtil.colorize(sec.getString("name", "&aBait")));
            List loreStrings = sec.getStringList("lore");
            ArrayList<Component> lore = new ArrayList<Component>();
            for (String l : (java.util.List<String>)(java.util.List<?>)loreStrings) {
                lore.add(TextUtil.colorize(l));
            }
            meta.lore((java.util.List<net.kyori.adventure.text.Component>)(java.util.List<?>)lore);
            if (sec.contains("item-model") && (customModelData = sec.getInt("item-model", 0)) > 0) {
                meta.setCustomModelData(Integer.valueOf(customModelData));
            }
            meta.getPersistentDataContainer().set(this.baitIdKey, PersistentDataType.STRING, baitId);
            item.setItemMeta(meta);
        }
        return item;
    }

    public void applyBait(Player player, ItemStack baitItem) {
        int durationSeconds;
        ItemMeta meta = baitItem.getItemMeta();
        if (meta == null) {
            return;
        }
        String baitId = (String)meta.getPersistentDataContainer().get(this.baitIdKey, PersistentDataType.STRING);
        if (baitId == null) {
            return;
        }
        if (this.getActiveBait(player.getUniqueId()) != null) {
            TextUtil.sendMessage((CommandSender)player, "&cYou already have active bait! Wait for it to expire.");
            return;
        }
        ConfigurationSection sec = this.module.getConfig().getConfigurationSection("baits." + baitId);
        if (sec == null) {
            return;
        }
        String type = sec.getString("type", "casts");
        int casts = sec.getInt("casts", 20);
        if (casts <= 0) {
            casts = 20;
        }
        if ((durationSeconds = sec.getInt("duration-seconds", 300)) <= 0) {
            durationSeconds = 300;
        }
        double catchBonus = sec.getDouble("catch-chance-bonus", 0.15);
        double weightBonus = sec.getDouble("weight-bonus", 0.0);
        String rarityBias = sec.getString("rarity-bias", "");
        String name = sec.getString("name", "Bait");
        long expiresAt = 0L;
        if ("time".equalsIgnoreCase(type)) {
            expiresAt = System.currentTimeMillis() + (long)durationSeconds * 1000L;
        }
        ActiveBait activeBait = new ActiveBait(baitId, type, casts, expiresAt, catchBonus, weightBonus, rarityBias);
        this.activeBaits.put(player.getUniqueId(), activeBait);
        baitItem.setAmount(baitItem.getAmount() - 1);
        if ("time".equalsIgnoreCase(type)) {
            TextUtil.sendMessage((CommandSender)player, "&8[Fishing] &aYou applied &e" + name + " &ato your rod! &7(" + durationSeconds + "s remaining)");
        } else {
            TextUtil.sendMessage((CommandSender)player, "&8[Fishing] &aYou applied &e" + name + " &ato your rod! &7(" + casts + " casts remaining)");
        }
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
    }

    public ActiveBait getActiveBait(UUID uuid) {
        ActiveBait bait = this.activeBaits.get(uuid);
        if (bait == null) {
            return null;
        }
        if ("time".equalsIgnoreCase(bait.type) && System.currentTimeMillis() > bait.expiresAt) {
            this.activeBaits.remove(uuid);
            Player p = Bukkit.getPlayer((UUID)uuid);
            if (p != null) {
                ConfigurationSection sec = this.module.getConfig().getConfigurationSection("baits." + bait.baitId);
                String name = sec != null ? sec.getString("name", "Bait") : "Bait";
                TextUtil.sendMessage((CommandSender)p, "&8[Fishing] &cYour bait &e" + name + " &chas expired.");
            }
            return null;
        }
        return bait;
    }

    public void consumeCast(Player player) {
        long remainingMs;
        String name;
        ActiveBait bait = this.getActiveBait(player.getUniqueId());
        if (bait == null) {
            return;
        }
        ConfigurationSection sec = this.module.getConfig().getConfigurationSection("baits." + bait.baitId);
        String string = name = sec != null ? sec.getString("name", "Bait") : "Bait";
        if ("casts".equalsIgnoreCase(bait.type)) {
            --bait.castsRemaining;
            if (bait.castsRemaining <= 0) {
                this.activeBaits.remove(player.getUniqueId());
                TextUtil.sendMessage((CommandSender)player, "&8[Fishing] &cYour bait &e" + name + " &chas run out.");
            } else {
                TextUtil.sendMessage((CommandSender)player, "&8[Fishing] &7Bait: &e" + name + " &7- &b" + bait.castsRemaining + " casts left");
            }
        } else if ("time".equalsIgnoreCase(bait.type) && (remainingMs = bait.expiresAt - System.currentTimeMillis()) > 0L) {
            long remainingSec = remainingMs / 1000L;
            TextUtil.sendMessage((CommandSender)player, "&8[Fishing] &7Bait: &e" + name + " &7- &b" + remainingSec + "s left");
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null || item.getType() == Material.AIR || item.getType() == Material.FISHING_ROD) {
            return;
        }
        if (!item.hasItemMeta()) {
            return;
        }
        if (item.getItemMeta().getPersistentDataContainer().has(this.baitIdKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
            this.applyBait(event.getPlayer(), item);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        ActiveBait bait = this.activeBaits.remove(uuid);
        this.plugin.getDatabaseManager().executeAsync(() -> {
            if (bait != null) {
                try (Connection conn2 = this.plugin.getDatabaseManager().getConnection();
                     PreparedStatement stmt = conn2.prepareStatement("INSERT INTO coresuite_active_baits (uuid, bait_id, bait_type, casts_remaining, expires_at, catch_bonus, weight_bonus, rarity_bias) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET bait_id=excluded.bait_id, bait_type=excluded.bait_type, casts_remaining=excluded.casts_remaining, expires_at=excluded.expires_at, catch_bonus=excluded.catch_bonus, weight_bonus=excluded.weight_bonus, rarity_bias=excluded.rarity_bias");){
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, bait.baitId);
                    stmt.setString(3, bait.type);
                    stmt.setInt(4, bait.castsRemaining);
                    stmt.setLong(5, bait.expiresAt);
                    stmt.setDouble(6, bait.catchChanceBonus);
                    stmt.setDouble(7, bait.weightBonus);
                    stmt.setString(8, bait.rarityBias);
                    stmt.executeUpdate();
                }
                catch (Exception conn2) {}
            } else {
                try (Connection conn = this.plugin.getDatabaseManager().getConnection();
                     PreparedStatement stmt = conn.prepareStatement("DELETE FROM coresuite_active_baits WHERE uuid = ?");){
                    stmt.setString(1, uuid.toString());
                    stmt.executeUpdate();
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
        });
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        this.loadPlayerBait(event.getPlayer().getUniqueId());
    }

    public void openBaitShopGUI(Player player) {
        this.plugin.getDatabaseManager().executeAsync(() -> {
            HashMap<String, BaitStock> stockMap = new HashMap<String, BaitStock>();
            try (Connection conn = this.plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT bait_id, bought_amount, last_restock FROM coresuite_bait_stock WHERE uuid = ?");){
                stmt.setString(1, player.getUniqueId().toString());
                try (ResultSet rs = stmt.executeQuery();){
                    while (rs.next()) {
                        stockMap.put(rs.getString("bait_id"), new BaitStock(rs.getInt("bought_amount"), rs.getLong("last_restock")));
                    }
                }
            }
            catch (Exception exception) {
                // empty catch block
            }
            Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> this.openBaitShopGUIInternal(player, stockMap));
        });
    }

    private void openBaitShopGUIInternal(Player player, Map<String, BaitStock> stockMap) {
        int[] customSlots;
        ConfigurationSection baitsSec = this.module.getConfig().getConfigurationSection("baits");
        if (baitsSec == null) {
            TextUtil.sendMessage((CommandSender)player, "&cNo baits are available in the shop.");
            return;
        }
        int size = baitsSec.getKeys(false).size();
        int rows = size <= 4 ? 4 : (size <= 7 ? 4 : 5);
        Gui gui = new Gui("&8Bait Shop", rows);
        gui.fillBorder(Material.ORANGE_STAINED_GLASS_PANE);
        if (size == 4) {
            customSlots = new int[]{12, 14, 21, 23};
        } else if (size == 3) {
            customSlots = new int[]{11, 13, 15};
        } else if (size == 2) {
            customSlots = new int[]{12, 14};
        } else {
            int startSlot;
            customSlots = new int[size];
            int curSlot = startSlot = 9 + (9 - Math.min(size, 7)) / 2;
            for (int i = 0; i < size; ++i) {
                if (curSlot == 17) {
                    curSlot = 19 + (9 - (size - 7)) / 2;
                }
                customSlots[i] = curSlot++;
            }
        }
        int index = 0;
        for (String baitId : baitsSec.getKeys(false)) {
            ItemMeta meta;
            int cmd;
            Material material;
            int slot;
            if (index >= customSlots.length || (slot = customSlots[index++]) >= rows * 9 - 9) break;
            ConfigurationSection sec = baitsSec.getConfigurationSection(baitId);
            if (sec == null) continue;
            String name = sec.getString("name", "&aBait");
            double rawPrice = sec.getDouble("price", -1.0);
            double price = rawPrice < 0.0 ? 5000.0 : rawPrice;
            int dailyLimit = sec.getInt("daily-limit", -1);
            BaitStock stock = stockMap.getOrDefault(baitId, new BaitStock(0, System.currentTimeMillis()));
            long restockInterval = 86400000L;
            if (System.currentTimeMillis() - stock.lastRestock >= restockInterval) {
                stock.boughtAmount = 0;
                stock.lastRestock = System.currentTimeMillis();
            }
            ArrayList<String> lore = new ArrayList<String>(sec.getStringList("lore"));
            lore.add("");
            lore.add("&7Price: &e$" + this.plugin.getEconomyManager().format(price));
            if (dailyLimit > 0) {
                int left = Math.max(0, dailyLimit - stock.boughtAmount);
                lore.add("&7Daily Stock: " + (left > 0 ? "&a" : "&c") + left + "&8/&7" + dailyLimit);
                if (stock.boughtAmount > 0) {
                    long timeLeft = restockInterval - (System.currentTimeMillis() - stock.lastRestock);
                    if (timeLeft < 0L) {
                        timeLeft = 0L;
                    }
                    long hours = timeLeft / 3600000L;
                    long mins = timeLeft % 3600000L / 60000L;
                    lore.add("&7Restocks in: &e" + hours + "h " + mins + "m");
                }
            }
            if ((material = Material.matchMaterial((String)sec.getString("material", "SLIME_BALL"))) == null) {
                material = Material.SLIME_BALL;
            }
            GuiButton button = GuiButton.of(material, name, lore, e -> {
                e.setCancelled(true);
                if (dailyLimit > 0 && stock.boughtAmount >= dailyLimit) {
                    TextUtil.sendMessage((CommandSender)player, "&cThis item is out of stock today! Check back later or use Force Restock.");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    return;
                }
                if (this.plugin.getEconomyManager().has((OfflinePlayer)player, price)) {
                    ItemStack baitItem;
                    this.plugin.getEconomyManager().withdraw((OfflinePlayer)player, price);
                    if (dailyLimit > 0) {
                        ++stock.boughtAmount;
                        stockMap.put(baitId, stock);
                        this.plugin.getDatabaseManager().executeAsync(() -> this.plugin.getDatabaseManager().execute("INSERT INTO coresuite_bait_stock (uuid, bait_id, bought_amount, last_restock) VALUES (?, ?, ?, ?) ON CONFLICT(uuid, bait_id) DO UPDATE SET bought_amount = excluded.bought_amount, last_restock = excluded.last_restock", player.getUniqueId().toString(), baitId, stock.boughtAmount, stock.lastRestock));
                    }
                    if ((baitItem = this.createBaitItem(baitId)) != null) {
                        HashMap leftover = player.getInventory().addItem(new ItemStack[]{baitItem});
                        if (!leftover.isEmpty()) {
                            player.getWorld().dropItem(player.getLocation(), (ItemStack)leftover.get(0));
                        }
                        TextUtil.sendMessage((CommandSender)player, "&aYou purchased " + name + " &afor $" + this.plugin.getEconomyManager().format(price) + "!");
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                        this.openBaitShopGUIInternal(player, stockMap);
                    }
                } else {
                    TextUtil.sendMessage((CommandSender)player, "&cYou do not have enough money!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
            });
            if (sec.contains("item-model") && (cmd = sec.getInt("item-model", 0)) > 0 && (meta = (button = new GuiButton(button.getItem(), button.getHandler())).getItem().getItemMeta()) != null) {
                meta.setCustomModelData(Integer.valueOf(cmd));
                button.getItem().setItemMeta(meta);
            }
            gui.setItem(slot++, button);
        }
        double forcePrice = this.module.getConfig().getDouble("bait-shop.force-restock-price", 250000.0);
        gui.setItem(rows * 9 - 5, GuiButton.of(Material.GOLD_INGOT, "&6&lForce Restock", Arrays.asList("&7Pay &e$" + this.plugin.getEconomyManager().format(forcePrice) + " &7to instantly", "&7restore half of your daily stock", "&7for all limited baits."), e -> {
            e.setCancelled(true);
            if (this.plugin.getEconomyManager().has((OfflinePlayer)player, forcePrice)) {
                this.plugin.getEconomyManager().withdraw((OfflinePlayer)player, forcePrice);
                for (String baitId : stockMap.keySet()) {
                    BaitStock stock = (BaitStock)stockMap.get(baitId);
                    stock.boughtAmount = Math.max(0, stock.boughtAmount / 2);
                    this.plugin.getDatabaseManager().executeAsync(() -> this.plugin.getDatabaseManager().execute("INSERT INTO coresuite_bait_stock (uuid, bait_id, bought_amount, last_restock) VALUES (?, ?, ?, ?) ON CONFLICT(uuid, bait_id) DO UPDATE SET bought_amount = excluded.bought_amount, last_restock = excluded.last_restock", player.getUniqueId().toString(), baitId, stock.boughtAmount, stock.lastRestock));
                }
                TextUtil.sendMessage((CommandSender)player, "&aYou forcefully restocked the bait shop for $" + this.plugin.getEconomyManager().format(forcePrice) + "!");
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                this.openBaitShopGUIInternal(player, stockMap);
            } else {
                TextUtil.sendMessage((CommandSender)player, "&cYou do not have enough money to force restock! ($" + this.plugin.getEconomyManager().format(forcePrice) + " required)");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            }
        }));
        gui.setItem(rows * 9 - 1, GuiButton.of(Material.BARRIER, "&cBack", List.of("&7Click to go back"), e -> {
            e.setCancelled(true);
            player.closeInventory();
            Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> player.performCommand("fish"));
        }));
        gui.open(player);
    }

    public void shutdown() {
        for (Map.Entry<UUID, ActiveBait> entry : this.activeBaits.entrySet()) {
            UUID uuid = entry.getKey();
            ActiveBait bait = entry.getValue();
            try {
                Connection conn = this.plugin.getDatabaseManager().getConnection();
                try {
                    PreparedStatement stmt = conn.prepareStatement("INSERT INTO coresuite_active_baits (uuid, bait_id, bait_type, casts_remaining, expires_at, catch_bonus, weight_bonus, rarity_bias) VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT(uuid) DO UPDATE SET bait_id=excluded.bait_id, bait_type=excluded.bait_type, casts_remaining=excluded.casts_remaining, expires_at=excluded.expires_at, catch_bonus=excluded.catch_bonus, weight_bonus=excluded.weight_bonus, rarity_bias=excluded.rarity_bias");
                    try {
                        stmt.setString(1, uuid.toString());
                        stmt.setString(2, bait.baitId);
                        stmt.setString(3, bait.type);
                        stmt.setInt(4, bait.castsRemaining);
                        stmt.setLong(5, bait.expiresAt);
                        stmt.setDouble(6, bait.catchChanceBonus);
                        stmt.setDouble(7, bait.weightBonus);
                        stmt.setString(8, bait.rarityBias);
                        stmt.executeUpdate();
                    }
                    finally {
                        if (stmt == null) continue;
                        stmt.close();
                    }
                }
                finally {
                    if (conn == null) continue;
                    conn.close();
                }
            }
            catch (Exception exception) {}
        }
        this.activeBaits.clear();
    }

    public static class ActiveBait {
        public final String baitId;
        public final String type;
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

    public static class BaitStock {
        public int boughtAmount;
        public long lastRestock;

        public BaitStock(int boughtAmount, long lastRestock) {
            this.boughtAmount = boughtAmount;
            this.lastRestock = lastRestock;
        }
    }
}

