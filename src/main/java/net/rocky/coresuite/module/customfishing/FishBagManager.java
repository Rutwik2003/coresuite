/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.bukkit.Bukkit
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.persistence.PersistentDataType
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.util.io.BukkitObjectInputStream
 *  org.bukkit.util.io.BukkitObjectOutputStream
 */
package net.rocky.coresuite.module.customfishing;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.customfishing.CustomFishingModule;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

public class FishBagManager {
    private final CoreSuitePlugin plugin;
    private CustomFishingModule module;
    private final Map<UUID, ItemStack[]> cache = new ConcurrentHashMap<UUID, ItemStack[]>();

    public FishBagManager(CoreSuitePlugin plugin) {
        this.plugin = plugin;
    }

    public FishBagManager(CoreSuitePlugin plugin, CustomFishingModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    public void setModule(CustomFishingModule module) {
        this.module = module;
    }

    private final Map<UUID, Object> sessions = new ConcurrentHashMap<>();

    public final class BagHolder implements org.bukkit.inventory.InventoryHolder {
        public final UUID owner;
        public final int page;
        private final Object session;
        private Inventory inventory;
        private BagHolder(UUID owner, int page) {
            this.owner = owner;
            this.page = page;
            this.session = sessions.get(owner);
        }
        @Override public Inventory getInventory() { return inventory; }
        public boolean isCurrent(UUID viewer) {
            return owner.equals(viewer) && session != null && sessions.get(owner) == session;
        }
        public boolean belongsTo(FishBagManager manager) { return FishBagManager.this == manager; }
    }

    public void loadBag(UUID uuid) {
        Object session = new Object();
        sessions.put(uuid, session);
        cache.remove(uuid);
        this.plugin.getDatabaseManager().executeAsync(() -> {
            String stored = null;
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT inventory_data FROM coresuite_fish_bag WHERE uuid = ?")) {
                stmt.setString(1, uuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) stored = rs.getString("inventory_data");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Fish bag unavailable; existing data preserved for " + uuid, e);
                return;
            }
            String data = stored;
            if (sessions.get(uuid) != session || !plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (sessions.get(uuid) != session) return;
                try {
                    cache.put(uuid, data == null || data.isEmpty() ? new ItemStack[450] : fromBase64(data));
                } catch (IllegalStateException e) {
                    plugin.getLogger().log(Level.SEVERE, "Invalid fish bag; existing data preserved for " + uuid, e);
                }
            });
        });
    }

    public void saveBag(UUID uuid) {
        ItemStack[] items = cache.get(uuid);
        if (items == null) return;
        // Serialize on the server thread: queued SQL must never observe mutable items.
        String data = toBase64(items);
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("REPLACE INTO coresuite_fish_bag (uuid, inventory_data) VALUES (?, ?)")) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, data);
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save fish bag for " + uuid, e);
            }
        });
    }

    public void unloadBag(UUID uuid) {
        sessions.remove(uuid);
        saveBag(uuid);
        cache.remove(uuid);
    }

    public void shutdown() {
        for (UUID uuid : java.util.List.copyOf(sessions.keySet())) unloadBag(uuid);
    }

    public ItemStack[] getBag(UUID uuid) { return cache.get(uuid); }

    public void updateBag(UUID uuid, ItemStack[] items) {
        if (items.length != 450) throw new IllegalArgumentException("Fish bags require 450 slots");
        cache.replace(uuid, items);
    }

    public void compactBag(UUID uuid) {
        ItemStack[] bag = this.getBag(uuid);
        if (bag == null) {
            return;
        }
        ItemStack[] newBag = new ItemStack[bag.length];
        int index = 0;
        for (ItemStack item : bag) {
            if (item == null || item.getType() == Material.AIR) continue;
            newBag[index++] = item;
        }
        this.updateBag(uuid, newBag);
    }

    public void sortBag(UUID uuid, CustomFishingModule module, boolean byRarity) {
        ItemStack[] bag = this.getBag(uuid);
        if (bag == null) {
            return;
        }
        ArrayList<ItemStack> fishList = new ArrayList<ItemStack>();
        for (ItemStack item : bag) {
            if (item == null || item.getType() == Material.AIR) continue;
            fishList.add(item);
        }
        NamespacedKey speciesKey = new NamespacedKey((Plugin)this.plugin, "fish_species");
        NamespacedKey weightKey = new NamespacedKey((Plugin)this.plugin, "fish_weight");
        List<String> knownRarities = Arrays.asList("mythical", "legendary", "epic", "rare", "uncommon", "common");
        fishList.sort((a, b) -> {
            ItemMeta metaA = a.getItemMeta();
            ItemMeta metaB = b.getItemMeta();
            if (metaA == null || metaB == null) {
                return 0;
            }
            Double weightA = (Double)metaA.getPersistentDataContainer().get(weightKey, PersistentDataType.DOUBLE);
            Double weightB = (Double)metaB.getPersistentDataContainer().get(weightKey, PersistentDataType.DOUBLE);
            if (weightA == null) {
                weightA = 0.0;
            }
            if (weightB == null) {
                weightB = 0.0;
            }
            if (byRarity) {
                int rankB;
                String speciesA = (String)metaA.getPersistentDataContainer().get(speciesKey, PersistentDataType.STRING);
                String speciesB = (String)metaB.getPersistentDataContainer().get(speciesKey, PersistentDataType.STRING);
                String rarityA = speciesA != null ? module.getConfig().getString("species." + speciesA + ".rarity", "Common").toLowerCase() : "common";
                String rarityB = speciesB != null ? module.getConfig().getString("species." + speciesB + ".rarity", "Common").toLowerCase() : "common";
                int rankA = knownRarities.indexOf(rarityA);
                if (rankA == -1) {
                    rankA = 99;
                }
                if ((rankB = knownRarities.indexOf(rarityB)) == -1) {
                    rankB = 99;
                }
                if (rankA != rankB) {
                    return Integer.compare(rankA, rankB);
                }
                if (speciesA != null && speciesB != null && !speciesA.equals(speciesB)) {
                    return speciesA.compareTo(speciesB);
                }
            }
            return Double.compare(weightB, weightA);
        });
        ItemStack[] newBag = new ItemStack[bag.length];
        for (int i = 0; i < fishList.size(); ++i) {
            newBag[i] = (ItemStack)fishList.get(i);
        }
        this.updateBag(uuid, newBag);
    }

    /*
     * Enabled aggressive exception aggregation
     */
    private String toBase64(ItemStack[] items) {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();){
            String string;
            try (BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream((OutputStream)outputStream);){
                dataOutput.writeInt(items.length);
                for (ItemStack item : items) {
                    dataOutput.writeObject((Object)item);
                }
                dataOutput.flush();
                string = Base64.getEncoder().encodeToString(outputStream.toByteArray());
            }
            return string;
        }
        catch (Exception e) {
            throw new IllegalStateException("Unable to save item stacks.", e);
        }
    }

    /*
     * Enabled aggressive exception aggregation
     */
    private ItemStack[] fromBase64(String data) {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));){
            ItemStack[] itemStackArray;
            try (BukkitObjectInputStream dataInput = new BukkitObjectInputStream((InputStream)inputStream);){
                int size = dataInput.readInt();
                if (size < 0 || size > 450) throw new java.io.IOException("Invalid fish bag size: " + size);
                ItemStack[] items = new ItemStack[450];
                for (int i = 0; i < size; ++i) {
                    items[i] = (ItemStack)dataInput.readObject();
                }
                itemStackArray = items;
            }
            return itemStackArray;
        }
        catch (Exception e) {
            throw new IllegalStateException("Unable to load item stacks.", e);
        }
    }

    public void openBag(Player player, int totalPages, int currentPage) {
        ItemMeta meta;
        if (getBag(player.getUniqueId()) == null) {
            TextUtil.sendMessage(player, "&cYour fish bag is not available yet. Please try again shortly.");
            return;
        }
        if (player.getOpenInventory().getTopInventory().getHolder() instanceof BagHolder) player.closeInventory();
        totalPages = Math.max(1, Math.min(10, totalPages));
        currentPage = Math.max(0, Math.min(totalPages - 1, currentPage));
        BagHolder holder = new BagHolder(player.getUniqueId(), currentPage);
        Inventory bagInv = Bukkit.createInventory(holder, 54, "Fish Bag (Page " + (currentPage + 1) + ")");
        holder.inventory = bagInv;
        ItemStack[] items = this.getBag(player.getUniqueId());
        int start = currentPage * 45;
        for (int i = 0; i < 45; ++i) {
            if (start + i >= items.length) continue;
            ItemStack it = items[start + i];
            if (it != null && this.module != null) {
                this.module.syncFishItemConfig(it);
            }
            bagInv.setItem(i, it);
        }
        ItemStack filler = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.displayName(TextUtil.colorize(" "));
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 45; i < 54; ++i) {
            bagInv.setItem(i, filler);
        }
        if (currentPage > 0) {
            ItemStack prev = new ItemStack(Material.ARROW);
            meta = prev.getItemMeta();
            meta.displayName(TextUtil.colorize("&aPrevious Page"));
            prev.setItemMeta(meta);
            bagInv.setItem(45, prev);
        }
        if (currentPage < totalPages - 1) {
            ItemStack next = new ItemStack(Material.ARROW);
            meta = next.getItemMeta();
            meta.displayName(TextUtil.colorize("&aNext Page"));
            next.setItemMeta(meta);
            bagInv.setItem(53, next);
        }
        ItemStack sortRarityBtn = new ItemStack(Material.EMERALD);
        ItemMeta sortRarityMeta = sortRarityBtn.getItemMeta();
        sortRarityMeta.displayName(TextUtil.colorize("&a&lSort by Rarity"));
        sortRarityMeta.lore(Arrays.asList(TextUtil.colorize("&7Click to sort all fish by rarity.")));
        sortRarityBtn.setItemMeta(sortRarityMeta);
        bagInv.setItem(47, sortRarityBtn);
        ItemStack backBtn = new ItemStack(Material.BARRIER);
        ItemMeta backMeta = backBtn.getItemMeta();
        backMeta.displayName(TextUtil.colorize("&c&lBack"));
        backMeta.lore(Arrays.asList(TextUtil.colorize("&7Return to the main menu.")));
        backBtn.setItemMeta(backMeta);
        bagInv.setItem(49, backBtn);
        ItemStack sortWeightBtn = new ItemStack(Material.ANVIL);
        ItemMeta sortWeightMeta = sortWeightBtn.getItemMeta();
        sortWeightMeta.displayName(TextUtil.colorize("&e&lSort by Weight"));
        sortWeightMeta.lore(Arrays.asList(TextUtil.colorize("&7Click to sort all fish by weight.")));
        sortWeightBtn.setItemMeta(sortWeightMeta);
        bagInv.setItem(51, sortWeightBtn);

        if (totalPages < 10) {
            ItemStack upgradeBtn = new ItemStack(Material.EXPERIENCE_BOTTLE);
            ItemMeta upMeta = upgradeBtn.getItemMeta();
            if (upMeta != null) {
                upMeta.displayName(TextUtil.colorize("&e&lUpgrade Bag Pages"));
                upMeta.lore(Arrays.asList(
                    TextUtil.colorize("&8&m────────────────────"),
                    TextUtil.colorize("&7Current Pages: &a" + totalPages + "&7/&f10"),
                    TextUtil.colorize("&8&m────────────────────"),
                    TextUtil.colorize("&e▶ Click to purchase next page")
                ));
                upgradeBtn.setItemMeta(upMeta);
            }
            bagInv.setItem(48, upgradeBtn);
        }

        player.openInventory(bagInv);
    }
}

