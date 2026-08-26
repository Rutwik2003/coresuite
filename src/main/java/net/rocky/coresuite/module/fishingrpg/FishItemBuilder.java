package net.rocky.coresuite.module.fishingrpg;

import net.kyori.adventure.text.Component;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class FishItemBuilder {

    public static ItemStack buildFishItem(FishSpecies species, double weight, double price, boolean isCorrupted, CoreSuitePlugin plugin) {
        Material material;
        try {
            material = Material.valueOf(species.getMaterial().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            material = Material.COD;
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        String displayName = species.getRarity().getColorCode() + species.getName();
        if (isCorrupted) {
            displayName = "&4☠ Corrupted " + displayName;
        }
        meta.displayName(TextUtil.colorize(displayName));

        List<Component> lore = new ArrayList<>();
        lore.add(TextUtil.colorize("&7Weight: &f" + String.format("%.2f", weight) + " kg"));
        lore.add(TextUtil.colorize("&7Rarity: " + species.getRarity().getColorCode() + species.getRarity().name()));
        lore.add(TextUtil.colorize("&7Value: &e$" + String.format("%.2f", price)));
        
        if (species.getLore() != null) {
            for (String line : species.getLore()) {
                lore.add(TextUtil.colorize(line));
            }
        }
        
        if (isCorrupted) {
            lore.add(TextUtil.colorize("&4This fish pulses with dark energy..."));
        }
        
        lore.add(Component.empty());
        lore.add(TextUtil.colorize("&8Caught by Unknown")); // Placeholder since we don't have the player here

        meta.lore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(new NamespacedKey(plugin, "fish_species"), PersistentDataType.STRING, species.getId());
        pdc.set(new NamespacedKey(plugin, "fish_weight"), PersistentDataType.DOUBLE, weight);
        pdc.set(new NamespacedKey(plugin, "fish_price"), PersistentDataType.DOUBLE, price);
        pdc.set(new NamespacedKey(plugin, "fish_rarity"), PersistentDataType.STRING, species.getRarity().name());
        pdc.set(new NamespacedKey(plugin, "fish_corrupted"), PersistentDataType.BYTE, (byte) (isCorrupted ? 1 : 0));

        if (species.getItemModel() != null && !species.getItemModel().isEmpty()) {
            meta.setItemModel(NamespacedKey.fromString(species.getItemModel()));
        }

        item.setItemMeta(meta);
        return item;
    }

    public static ItemStack buildCompassItem(Player player, Location target, CoreSuitePlugin plugin) {
        ItemStack item = new ItemStack(Material.RECOVERY_COMPASS);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.displayName(TextUtil.colorize("&6&lLegendary Fisherman's Compass"));
        
        List<Component> lore = new ArrayList<>();
        lore.add(TextUtil.colorize("&7Follow this compass to find"));
        lore.add(TextUtil.colorize("&7a legendary fishing spot."));
        meta.lore(lore);

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(new NamespacedKey(plugin, "legendary_compass"), PersistentDataType.STRING, player.getUniqueId().toString());
        
        if (target != null && target.getWorld() != null) {
            pdc.set(new NamespacedKey(plugin, "lode_w"), PersistentDataType.STRING, target.getWorld().getName());
            pdc.set(new NamespacedKey(plugin, "lode_x"), PersistentDataType.DOUBLE, target.getX());
            pdc.set(new NamespacedKey(plugin, "lode_y"), PersistentDataType.DOUBLE, target.getY());
            pdc.set(new NamespacedKey(plugin, "lode_z"), PersistentDataType.DOUBLE, target.getZ());
        }

        item.setItemMeta(meta);
        return item;
    }
}
