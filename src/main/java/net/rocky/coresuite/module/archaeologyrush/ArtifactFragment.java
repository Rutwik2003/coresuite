package net.rocky.coresuite.module.archaeologyrush;

import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public record ArtifactFragment(String setId, int fragmentIndex, String displayName, Material material) {

    public ItemStack toItem(String setName, int total, NamespacedKey setKey, NamespacedKey fragmentKey) {
        ItemStack item = TextUtil.createItem(
                material,
                displayName,
                "&8&m────────────────────",
                "&7This ancient fragment belongs to",
                "&7the &6" + setName + " &7collection.",
                " ",
                "&7Collect all &f" + total + " &7pieces and",
                "&7bring them to &6The Curator&7 to",
                "&7claim your reward!",
                "&8&m────────────────────",
                "&7Fragment: &f" + fragmentIndex + "&8/&f" + total,
                "&8(Brush suspicious blocks to find more)",
                "&8&m────────────────────"
        );
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
            meta.getPersistentDataContainer().set(setKey, PersistentDataType.STRING, setId);
            meta.getPersistentDataContainer().set(fragmentKey, PersistentDataType.INTEGER, fragmentIndex);

            if (meta instanceof org.bukkit.inventory.meta.BookMeta bookMeta) {
                bookMeta.title(TextUtil.colorize(displayName));
                bookMeta.author(net.kyori.adventure.text.Component.text("Ancient Chronicler"));
                bookMeta.pages(List.of(
                    TextUtil.colorize(
                        "&8&l=== " + displayName + " &8&l===\n\n" +
                        "&7Part &f" + fragmentIndex + "&7 of the &6" + setName + "&7 artifact set.\n\n" +
                        "&8The pages are worn by centuries of time, carrying forgotten whispers of the ancient realm.\n\n" +
                        "&6Collect all " + total + " pieces &7and bring them to &eThe Curator &7(/artifacts) to claim your great reward!"
                    )
                ));
            }

            item.setItemMeta(meta);
        }
        return item;
    }
}
