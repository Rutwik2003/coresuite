package net.rocky.coresuite.module.harvestmarket;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;

public class MutationHandler implements Listener {
    private final CoreSuitePlugin plugin;
    private final SeasonManager seasonManager;
    private final FileConfiguration config;
    private final NamespacedKey goldenKey;

    public MutationHandler(CoreSuitePlugin plugin, SeasonManager seasonManager, FileConfiguration config, NamespacedKey goldenKey) {
        this.plugin = plugin;
        this.seasonManager = seasonManager;
        this.config = config;
        this.goldenKey = goldenKey;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        
        Material dropMaterial = getDropMaterial(block.getType());
        if (dropMaterial == null) return;
        
        if (block.getBlockData() instanceof Ageable ageable) {
            if (ageable.getAge() != ageable.getMaximumAge()) {
                return;
            }
        }
        
        boolean inSeason = seasonManager.isInSeason(dropMaterial);
        double configChance = config.getDouble("golden-mutation-chance", 0.04);
        // If in-season: full chance (e.g. 4-5%). If not in-season: baseline 1.5% chance so players can always find them!
        double chance = inSeason ? Math.max(configChance, 0.04) : 0.015;
        
        if (Math.random() <= chance) {
            double basePrice = config.getDouble("crop-prices." + dropMaterial.name(), 10.0);
            double goldenMultiplier = config.getDouble("golden-multiplier", 25.0);
            double goldenPrice = basePrice * goldenMultiplier;
            
            ItemStack goldenCrop = new ItemStack(dropMaterial);
            ItemMeta meta = goldenCrop.getItemMeta();
            if (meta != null) {
                String name = dropMaterial.name().replace('_', ' ').toLowerCase();
                meta.displayName(TextUtil.colorize("&6✨ Golden " + capitalize(name)));
                meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);

                meta.lore(Arrays.asList(
                    TextUtil.colorize("&8&m────────────────────"),
                    TextUtil.colorize("&7A magnificent golden mutation!"),
                    TextUtil.colorize("&7Sell at the &2Harvest Market &8(/harvest)"),
                    TextUtil.colorize("&7for &a$" + TextUtil.formatNumber(goldenPrice) + " each&7! &8(" + goldenMultiplier + "× base)"),
                    TextUtil.colorize("&8&m────────────────────"),
                    TextUtil.colorize(inSeason ? "&6✦ In-Season Golden Mutation ✦" : "&e✦ Rare Golden Mutation ✦")
                ));
                
                meta.getPersistentDataContainer().set(goldenKey, PersistentDataType.STRING, dropMaterial.name());
                goldenCrop.setItemMeta(meta);
            }
            
            block.getWorld().dropItemNaturally(block.getLocation(), goldenCrop);
            
            String name = dropMaterial.name().replace('_', ' ').toLowerCase();
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
            TextUtil.sendTitle(
                player,
                "&6&l✨ GOLDEN CROP!",
                "&eFound a Golden " + capitalize(name) + "! (&a$" + TextUtil.formatNumber(goldenPrice) + "&e)",
                10, 40, 10
            );
            TextUtil.sendMessage(player, "&8[&2🌾 Market&8] &6✨ Rare Mutation! &7You unearthed a &e&lGolden " + capitalize(name) + "&7! Sell at &e/harvest &7for &a$" + TextUtil.formatNumber(goldenPrice) + "&7!");
        }
    }
    
    private Material getDropMaterial(Material blockType) {
        return switch (blockType) {
            case WHEAT -> Material.WHEAT;
            case CARROTS -> Material.CARROT;
            case POTATOES -> Material.POTATO;
            case BEETROOTS -> Material.BEETROOT;
            case MELON -> Material.MELON;
            case PUMPKIN -> Material.PUMPKIN;
            case SUGAR_CANE -> Material.SUGAR_CANE;
            case BAMBOO -> Material.BAMBOO;
            case COCOA -> Material.COCOA_BEANS;
            case SWEET_BERRY_BUSH -> Material.SWEET_BERRIES;
            default -> null;
        };
    }
    
    private String capitalize(String str) {
        StringBuilder sb = new StringBuilder();
        for (String word : str.replace('_', ' ').toLowerCase().split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }
}
