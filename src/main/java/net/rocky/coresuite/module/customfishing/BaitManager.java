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
        activeBaits.remove(event.getPlayer().getUniqueId());
    }

    public void openBaitShopGUI(Player player) {
        ConfigurationSection baitsSec = module.getConfig().getConfigurationSection("baits");
        if (baitsSec == null) {
            TextUtil.sendMessage(player, "&cNo baits are available in the shop.");
            return;
        }

        int size = baitsSec.getKeys(false).size();
        int rows = size <= 7 ? 3 : 4;
        
        Gui gui = new Gui("&8Bait Shop", rows);
        gui.fillBorder(Material.ORANGE_STAINED_GLASS_PANE);

        int startSlot = 9 + ((9 - Math.min(size, 7)) / 2);
        int slot = startSlot;
        for (String baitId : baitsSec.getKeys(false)) {
            if (slot == 17) slot = 19 + ((9 - (size - 7)) / 2);
            if (slot >= rows * 9 - 9) break; 
            
            ConfigurationSection sec = baitsSec.getConfigurationSection(baitId);
            if (sec == null) continue;

            String name = sec.getString("name", "&aBait");
            double rawPrice = sec.getDouble("price", -1.0);
            final double price = (rawPrice < 0) ? 5000.0 : rawPrice; // Safe fallback
            List<String> lore = new ArrayList<>(sec.getStringList("lore"));
            lore.add("");
            lore.add("&7Price: &e$" + plugin.getEconomyManager().format(price));

            Material material = Material.matchMaterial(sec.getString("material", "SLIME_BALL"));
            if (material == null) material = Material.SLIME_BALL;

            GuiButton button = GuiButton.of(material, name, lore, e -> {
                e.setCancelled(true);
                if (plugin.getEconomyManager().has(player, price)) {
                    plugin.getEconomyManager().withdraw(player, price);
                    ItemStack baitItem = createBaitItem(baitId);
                    if (baitItem != null) {
                        Map<Integer, ItemStack> leftover = player.getInventory().addItem(baitItem);
                        if (!leftover.isEmpty()) {
                            player.getWorld().dropItem(player.getLocation(), leftover.get(0));
                        }
                        TextUtil.sendMessage(player, "&aYou purchased " + name + " &afor $" + plugin.getEconomyManager().format(price) + "!");
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                    }
                } else {
                    TextUtil.sendMessage(player, "&cYou do not have enough money!");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                }
            });
            
            if (sec.contains("item-model")) {
                int cmd = sec.getInt("item-model", 0);
                if (cmd > 0) {
                    ItemStack icon = button.getItem();
                    ItemMeta meta = icon.getItemMeta();
                    if (meta != null) {
                        meta.setCustomModelData(cmd);
                        icon.setItemMeta(meta);
                    }
                }
            }
            
            gui.setItem(slot++, button);
        }

        gui.setItem(rows * 9 - 1, GuiButton.of(Material.BARRIER, "&cBack", List.of("&7Click to go back"), e -> {
            e.setCancelled(true);
            player.closeInventory();
            Bukkit.getScheduler().runTask(plugin, () -> player.performCommand("fish"));
        }));

        gui.open(player);
    }

    public void shutdown() {
        activeBaits.clear();
    }
}
