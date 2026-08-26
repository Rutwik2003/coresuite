package net.rocky.coresuite.module.customfishing;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class FishingEnchantManager {

    public enum RodTier {
        BASIC("&7Basic", 1.0, 0),
        BRONZE("&6Bronze", 1.15, 1),
        SILVER("&fSilver", 1.30, 2),
        GOLD("&eGold", 1.50, 3),
        DIAMOND("&bDiamond", 1.75, 4),
        NETHERITE("&4Netherite", 2.0, 5);

        private final String displayName;
        private final double enchantMultiplier;
        private final int ordinalIndex;

        RodTier(String displayName, double enchantMultiplier, int ordinalIndex) {
            this.displayName = displayName;
            this.enchantMultiplier = enchantMultiplier;
            this.ordinalIndex = ordinalIndex;
        }

        public String getDisplayName() { return displayName; }
        public double getEnchantMultiplier() { return enchantMultiplier; }
        public int getOrdinalIndex() { return ordinalIndex; }

        public RodTier getNextTier() {
            RodTier[] tiers = values();
            if (ordinalIndex + 1 < tiers.length) return tiers[ordinalIndex + 1];
            return null; // Already max
        }

        public static RodTier fromString(String name) {
            try {
                return valueOf(name.toUpperCase());
            } catch (IllegalArgumentException e) {
                return BASIC;
            }
        }
    }

    public enum CustomEnchant {
        LURE_OF_THE_DEEP("Lure of the Deep", 3, "lure_of_the_deep", "coresuite_enchant_lure_of_the_deep"),
        HEAVYWEIGHT("Heavyweight", 5, "heavyweight", "coresuite_enchant_heavyweight"),
        DOUBLE_HOOK("Double Hook", 3, "double_hook", "coresuite_enchant_double_hook"),
        TREASURE_HUNTER("Treasure Hunter", 3, "treasure_hunter", "coresuite_enchant_treasure_hunter"),
        TIRELESS_CASTER("Tireless Caster", 3, "tireless_caster", "coresuite_enchant_tireless_caster");

        private final String displayName;
        private final int maxLevel;
        private final String configKey;
        private final String keyString;

        CustomEnchant(String displayName, int maxLevel, String configKey, String keyString) {
            this.displayName = displayName;
            this.maxLevel = maxLevel;
            this.configKey = configKey;
            this.keyString = keyString;
        }

        public String getDisplayName() { return displayName; }
        public int getMaxLevel() { return maxLevel; }
        public String getConfigKey() { return configKey; }
        public String getKeyString() { return keyString; }
    }

    private final CoreSuitePlugin plugin;
    private final NamespacedKey rodTierKey;
    private final Map<CustomEnchant, NamespacedKey> enchantKeys = new EnumMap<>(CustomEnchant.class);

    public FishingEnchantManager(CoreSuitePlugin plugin) {
        this.plugin = plugin;
        this.rodTierKey = new NamespacedKey(plugin, "rod_quality_tier");
        for (CustomEnchant ce : CustomEnchant.values()) {
            enchantKeys.put(ce, new NamespacedKey(plugin, ce.getKeyString()));
        }
    }

    public NamespacedKey getKey(CustomEnchant enchant) {
        return enchantKeys.get(enchant);
    }

    public RodTier getRodTier(ItemStack rod) {
        if (rod == null || rod.getType() == Material.AIR || !rod.hasItemMeta()) return RodTier.BASIC;
        String value = rod.getItemMeta().getPersistentDataContainer().get(rodTierKey, PersistentDataType.STRING);
        if (value == null) return RodTier.BASIC;
        return RodTier.fromString(value);
    }

    public void setRodTier(ItemStack rod, RodTier tier) {
        if (rod == null || rod.getType() == Material.AIR) return;
        ItemMeta meta = rod.getItemMeta();
        if (meta == null) return;
        
        meta.getPersistentDataContainer().set(rodTierKey, PersistentDataType.STRING, tier.name());
        rod.setItemMeta(meta);
        updateLore(rod);
    }

    public int getEnchantLevel(ItemStack item, CustomEnchant enchant) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return 0;
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(getKey(enchant), PersistentDataType.INTEGER, 0);
    }

    public void setEnchantLevel(ItemStack item, CustomEnchant enchant, int level) {
        if (item == null || item.getType() == Material.AIR) return;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        
        meta.getPersistentDataContainer().set(getKey(enchant), PersistentDataType.INTEGER, level);
        item.setItemMeta(meta);
        updateLore(item);
    }
    
    public void updateLore(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        List<Component> newLore = new ArrayList<>();
        
        List<Component> oldLore = meta.lore();
        if (oldLore != null) {
            for (Component c : oldLore) {
                String plain = PlainTextComponentSerializer.plainText().serialize(c);
                boolean isCustomEnchant = false;
                for (CustomEnchant ce : CustomEnchant.values()) {
                    if (plain.contains(ce.getDisplayName())) {
                        isCustomEnchant = true;
                        break;
                    }
                }
                boolean isRodTier = plain.contains("Quality Rod");
                if (!isCustomEnchant && !isRodTier) {
                    newLore.add(c);
                }
            }
        }
        
        for (CustomEnchant ce : CustomEnchant.values()) {
            int level = getEnchantLevel(item, ce);
            if (level > 0) {
                newLore.add(0, TextUtil.colorize("&7" + ce.getDisplayName() + " " + toRoman(level)));
            }
        }

        RodTier tier = getRodTier(item);
        if (tier != RodTier.BASIC) {
            newLore.add(0, TextUtil.colorize(tier.getDisplayName() + " Quality Rod"));
        }
        
        meta.lore(newLore);
        item.setItemMeta(meta);
    }
    
    private String toRoman(int number) {
        return switch (number) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            default -> String.valueOf(number);
        };
    }
}
