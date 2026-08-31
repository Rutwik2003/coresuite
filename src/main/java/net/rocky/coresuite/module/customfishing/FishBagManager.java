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
    private final Map<UUID, ItemStack[]> cache = new ConcurrentHashMap<UUID, ItemStack[]>();

    public FishBagManager(CoreSuitePlugin plugin) {
        this.plugin = plugin;
    }

    public void loadBag(UUID uuid) {
        this.plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = this.plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT inventory_data FROM coresuite_fish_bag WHERE uuid = ?");){
                stmt.setString(1, uuid.toString());
                try (ResultSet rs = stmt.executeQuery();){
                    if (rs.next()) {
                        String data = rs.getString("inventory_data");
                        if (data != null && !data.isEmpty()) {
                            ItemStack[] items = this.fromBase64(data);
                            this.cache.put(uuid, items);
                        } else {
                            this.cache.put(uuid, new ItemStack[450]);
                        }
                    } else {
                        this.cache.put(uuid, new ItemStack[450]);
                    }
                }
            }
            catch (SQLException e) {
                this.plugin.getLogger().log(Level.SEVERE, "Failed to load fish bag for " + String.valueOf(uuid), e);
                this.cache.put(uuid, new ItemStack[450]);
            }
        });
    }

    public void saveBag(UUID uuid) {
        ItemStack[] items = this.cache.get(uuid);
        if (items != null) {
            ItemStack[] cloned = (ItemStack[])items.clone();
            this.plugin.getDatabaseManager().executeAsync(() -> {
                String data = this.toBase64(cloned);
                try (Connection conn = this.plugin.getDatabaseManager().getConnection();
                     PreparedStatement stmt = conn.prepareStatement("REPLACE INTO coresuite_fish_bag (uuid, inventory_data) VALUES (?, ?)");){
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, data);
                    stmt.executeUpdate();
                }
                catch (SQLException e) {
                    this.plugin.getLogger().log(Level.SEVERE, "Failed to save fish bag for " + String.valueOf(uuid), e);
                }
            });
        }
    }

    public void saveBagSync(UUID uuid) {
        ItemStack[] items = this.cache.get(uuid);
        if (items == null) {
            return;
        }
        String data = this.toBase64(items);
        try (Connection conn = this.plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement("REPLACE INTO coresuite_fish_bag (uuid, inventory_data) VALUES (?, ?)");){
            stmt.setString(1, uuid.toString());
            stmt.setString(2, data);
            stmt.executeUpdate();
        }
        catch (SQLException e) {
            this.plugin.getLogger().log(Level.SEVERE, "Failed to save fish bag (sync) for " + String.valueOf(uuid), e);
        }
    }

    public void unloadBag(UUID uuid) {
        ItemStack[] items = this.cache.remove(uuid);
        if (items != null) {
            this.plugin.getDatabaseManager().executeAsync(() -> {
                String data = this.toBase64(items);
                try (Connection conn = this.plugin.getDatabaseManager().getConnection();
                     PreparedStatement stmt = conn.prepareStatement("REPLACE INTO coresuite_fish_bag (uuid, inventory_data) VALUES (?, ?)");){
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, data);
                    stmt.executeUpdate();
                }
                catch (SQLException e) {
                    this.plugin.getLogger().log(Level.SEVERE, "Failed to save fish bag for " + String.valueOf(uuid), e);
                }
            });
        }
    }

    public ItemStack[] getBag(UUID uuid) {
        return this.cache.getOrDefault(uuid, new ItemStack[450]);
    }

    public void updateBag(UUID uuid, ItemStack[] items) {
        this.cache.put(uuid, items);
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
                ItemStack[] items = new ItemStack[dataInput.readInt()];
                for (int i = 0; i < items.length; ++i) {
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
        Inventory bagInv = Bukkit.createInventory(null, (int)54, (String)("Fish Bag (Page " + (currentPage + 1) + ")"));
        ItemStack[] items = this.getBag(player.getUniqueId());
        int start = currentPage * 45;
        for (int i = 0; i < 45; ++i) {
            if (start + i >= items.length) continue;
            bagInv.setItem(i, items[start + i]);
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
        player.openInventory(bagInv);
    }
}

