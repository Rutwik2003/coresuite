package net.rocky.coresuite.module.archaeologyrush;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.economy.EconomyManager;
import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.gui.GuiButton;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public class CuratorGUI {

    private final CoreSuitePlugin plugin;
    private final ArtifactSetRegistry registry;
    private final PlayerCollectionManager collectionManager;
    private final EconomyManager economy;
    private final FileConfiguration config;
    private final NamespacedKey setKey;

    public CuratorGUI(CoreSuitePlugin plugin, ArtifactSetRegistry registry, PlayerCollectionManager collectionManager, EconomyManager economy, FileConfiguration config, NamespacedKey setKey) {
        this.plugin = plugin;
        this.registry = registry;
        this.collectionManager = collectionManager;
        this.economy = economy;
        this.config = config;
        this.setKey = setKey;
    }

    public void openForSet(Player player, ArtifactSet set) {
        Gui gui = new Gui("&8▶ &6&l🏺 Curator — Turn In &8◀", 4);
        
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        int[] fragmentSlots = {10, 11, 12, 13, 14, 15, 16};
        int i = 0;
        for (ArtifactFragment frag : set.fragments()) {
            if (i >= fragmentSlots.length) break;
            
            ItemStack item = TextUtil.createItem(frag.material(), frag.displayName());
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA, 1, true);
                meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                item.setItemMeta(meta);
            }
            gui.setItem(fragmentSlots[i], new GuiButton(item, e -> {}));
            i++;
        }
        for (; i < fragmentSlots.length; i++) {
            gui.setItem(fragmentSlots[i], GuiButton.filler(Material.GRAY_STAINED_GLASS_PANE));
        }

        List<String> setLore = new ArrayList<>();
        setLore.add("&8&m────────────────────");
        setLore.add("&f" + set.displayName());
        setLore.add("&7" + set.description());
        setLore.add("&8&m────────────────────");
        setLore.add("&a✓ Complete Set (" + set.getPieceCount() + "/" + set.getPieceCount() + ")");
        setLore.add("&8&m────────────────────");
        setLore.add("&7Reward:");
        setLore.add("&a  $ " + TextUtil.formatNumber(set.rewardMoney()));
        for (String cmd : set.rewardCommands()) {
            setLore.add("&7  + Special Reward");
        }
        setLore.add("&8&m────────────────────");

        gui.setItem(22, GuiButton.of(set.displayItem(), set.displayName(), setLore, e -> {}));

        gui.setItem(28, GuiButton.of(Material.GREEN_STAINED_GLASS_PANE, "&a&l✓ Claim Reward", List.of("&7All fragments will be consumed."), e -> {
            collectionManager.completeSet(player.getUniqueId(), player.getName(), set, () -> {
                economy.deposit(player, set.rewardMoney());
                for (String cmd : set.rewardCommands()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", player.getName()));
                }
                
                // Remove physical fragments from inventory
                for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
                    ItemStack invItem = player.getInventory().getItem(slot);
                    if (invItem != null && invItem.hasItemMeta()) {
                        ItemMeta meta = invItem.getItemMeta();
                        if (meta != null) {
                            String itemSetId = meta.getPersistentDataContainer().get(setKey, PersistentDataType.STRING);
                            if (set.id().equals(itemSetId)) {
                                invItem.setAmount(invItem.getAmount() - 1);
                            }
                        }
                    }
                }
                
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                TextUtil.sendTitle(player, "&6&l✨ Set Complete!", "&a" + set.displayName() + " reward claimed!", 10, 40, 10);
                
                String broadcastMsg = config.getString("messages.broadcast", "&6🏺 &e{player}&6 completed the &f{setName}&6 artifact set!")
                        .replace("{player}", player.getName())
                        .replace("{setName}", set.displayName());
                Bukkit.broadcast(TextUtil.colorize(broadcastMsg));
                
                player.closeInventory();
            });
        }));

        gui.setItem(31, GuiButton.filler(Material.GRAY_STAINED_GLASS_PANE));

        gui.setItem(34, GuiButton.of(Material.RED_STAINED_GLASS_PANE, "&c&l✗ Cancel", List.of(), e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            player.closeInventory();
        }));

        gui.open(player);
    }
}
