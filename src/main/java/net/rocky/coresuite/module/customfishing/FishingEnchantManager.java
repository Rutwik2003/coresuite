/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.persistence.PersistentDataType
 *  org.bukkit.plugin.Plugin
 */
package net.rocky.coresuite.module.customfishing;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public class FishingEnchantManager {
    private final CoreSuitePlugin plugin;
    private final NamespacedKey rodTierKey;
    private final Map<CustomEnchant, NamespacedKey> enchantKeys = new EnumMap<CustomEnchant, NamespacedKey>(CustomEnchant.class);

    public FishingEnchantManager(CoreSuitePlugin plugin) {
        this.plugin = plugin;
        this.rodTierKey = new NamespacedKey((Plugin)plugin, "rod_quality_tier");
        for (CustomEnchant ce : CustomEnchant.values()) {
            this.enchantKeys.put(ce, new NamespacedKey((Plugin)plugin, ce.getKeyString()));
        }
    }

    public NamespacedKey getKey(CustomEnchant enchant) {
        return this.enchantKeys.get((Object)enchant);
    }

    public RodTier getRodTier(ItemStack rod) {
        if (rod == null || rod.getType() == Material.AIR || !rod.hasItemMeta()) {
            return RodTier.BASIC;
        }
        String value = (String)rod.getItemMeta().getPersistentDataContainer().get(this.rodTierKey, PersistentDataType.STRING);
        if (value == null) {
            return RodTier.BASIC;
        }
        return RodTier.fromString(value);
    }

    public void setRodTier(ItemStack rod, RodTier tier) {
        if (rod == null || rod.getType() == Material.AIR) {
            return;
        }
        ItemMeta meta = rod.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(this.rodTierKey, PersistentDataType.STRING, tier.name());
        rod.setItemMeta(meta);
        this.updateLore(rod);
    }

    public int getEnchantLevel(ItemStack item, CustomEnchant enchant) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return 0;
        }
        return (Integer)item.getItemMeta().getPersistentDataContainer().getOrDefault(this.getKey(enchant), PersistentDataType.INTEGER, 0);
    }

    public void setEnchantLevel(ItemStack item, CustomEnchant enchant, int level) {
        if (item == null || item.getType() == Material.AIR) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        meta.getPersistentDataContainer().set(this.getKey(enchant), PersistentDataType.INTEGER, level);
        item.setItemMeta(meta);
        this.updateLore(item);
    }

    public void updateLore(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        ArrayList<Component> newLore = new ArrayList<Component>();
        List oldLore = meta.lore();
        if (oldLore != null) {
            for (Component c : (java.util.List<net.kyori.adventure.text.Component>)(java.util.List<?>)oldLore) {
                String plain = PlainTextComponentSerializer.plainText().serialize(c);
                boolean isCustomEnchant = false;
                for (CustomEnchant ce : CustomEnchant.values()) {
                    if (!plain.contains(ce.getDisplayName())) continue;
                    isCustomEnchant = true;
                    break;
                }
                boolean isRodTier = plain.contains("Quality Rod");
                if (isCustomEnchant || isRodTier) continue;
                newLore.add(c);
            }
        }
        for (CustomEnchant ce : CustomEnchant.values()) {
            int level = this.getEnchantLevel(item, ce);
            if (level <= 0) continue;
            newLore.add(0, TextUtil.colorize("&7" + ce.getDisplayName() + " " + this.toRoman(level)));
        }
        RodTier tier = this.getRodTier(item);
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

    public static enum CustomEnchant {
        LURE_OF_THE_DEEP("Lure of the Deep", 3, "lure_of_the_deep", "coresuite_enchant_lure_of_the_deep"),
        HEAVYWEIGHT("Heavyweight", 5, "heavyweight", "coresuite_enchant_heavyweight"),
        DOUBLE_HOOK("Double Hook", 3, "double_hook", "coresuite_enchant_double_hook"),
        TREASURE_HUNTER("Treasure Hunter", 3, "treasure_hunter", "coresuite_enchant_treasure_hunter"),
        TIRELESS_CASTER("Tireless Caster", 3, "tireless_caster", "coresuite_enchant_tireless_caster");

        private final String displayName;
        private final int maxLevel;
        private final String configKey;
        private final String keyString;

        private CustomEnchant(String displayName, int maxLevel, String configKey, String keyString) {
            this.displayName = displayName;
            this.maxLevel = maxLevel;
            this.configKey = configKey;
            this.keyString = keyString;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public int getMaxLevel() {
            return this.maxLevel;
        }

        public String getConfigKey() {
            return this.configKey;
        }

        public String getKeyString() {
            return this.keyString;
        }
    }

    public static enum RodTier {
        BASIC("&7Basic", 1.0, 0),
        BRONZE("&6Bronze", 1.15, 1),
        SILVER("&fSilver", 1.3, 2),
        GOLD("&eGold", 1.5, 3),
        DIAMOND("&bDiamond", 1.75, 4),
        NETHERITE("&4Netherite", 2.0, 5);

        private final String displayName;
        private final double enchantMultiplier;
        private final int ordinalIndex;

        private RodTier(String displayName, double enchantMultiplier, int ordinalIndex) {
            this.displayName = displayName;
            this.enchantMultiplier = enchantMultiplier;
            this.ordinalIndex = ordinalIndex;
        }

        public String getDisplayName() {
            return this.displayName;
        }

        public double getEnchantMultiplier() {
            return this.enchantMultiplier;
        }

        public int getOrdinalIndex() {
            return this.ordinalIndex;
        }

        public RodTier getNextTier() {
            RodTier[] tiers = RodTier.values();
            if (this.ordinalIndex + 1 < tiers.length) {
                return tiers[this.ordinalIndex + 1];
            }
            return null;
        }

        public static RodTier fromString(String name) {
            try {
                return RodTier.valueOf(name.toUpperCase());
            }
            catch (IllegalArgumentException e) {
                return BASIC;
            }
        }
    }
}

