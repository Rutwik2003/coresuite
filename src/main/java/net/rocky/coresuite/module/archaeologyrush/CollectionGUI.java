package net.rocky.coresuite.module.archaeologyrush;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.economy.EconomyManager;
import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.gui.GuiButton;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class CollectionGUI {
    
    private final CoreSuitePlugin plugin;
    private final ArtifactSetRegistry registry;
    private final PlayerCollectionManager collectionManager;
    private final EconomyManager economy;
    private final CuratorGUI curatorGUI;



    public CollectionGUI(CoreSuitePlugin plugin, ArtifactSetRegistry registry, PlayerCollectionManager collectionManager, EconomyManager economy, CuratorGUI curatorGUI) {
        this.plugin = plugin;
        this.registry = registry;
        this.collectionManager = collectionManager;
        this.economy = economy;
        this.curatorGUI = curatorGUI;
    }

    public void open(Player player) {
        openPage(player, 1);
    }

    private void openPage(Player player, int page) {
        collectionManager.loadPlayer(player.getUniqueId(), () -> {
            Gui gui = new Gui("&8▶ &6&l🏺 Artifact Collection &8◀", 4);
            gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

            List<ArtifactSet> sets = registry.getAllSets();
            int maxPages = Math.max(1, (int) Math.ceil((double) sets.size() / 9));
            int startIndex = (page - 1) * 9;
            int endIndex = Math.min(startIndex + 9, sets.size());
            int totalItems = endIndex - startIndex;

            // Center slots dynamically based on count
            int[] slots;
            if (totalItems == 1) slots = new int[]{13};
            else if (totalItems == 2) slots = new int[]{12, 14};
            else if (totalItems == 3) slots = new int[]{11, 13, 15};
            else if (totalItems == 4) slots = new int[]{10, 12, 14, 16};
            else if (totalItems == 5) slots = new int[]{11, 12, 13, 14, 15};
            else slots = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20};

            gui.setItem(4, GuiButton.of(Material.BRUSH, "&6&l🏺 Artifact Collection",
                List.of(
                    "&8&m────────────────────",
                    "&7Brush &eSuspicious Sand &7& &eGravel",
                    "&7to discover hidden artifact fragments!",
                    "&8&m────────────────────",
                    "&7Collect all pieces of a set to claim",
                    "&7massive cash rewards and rare crate keys.",
                    "&8&m────────────────────",
                    "&eClick a completed set below to turn in!"
                ), e -> {}));

            // Track how many async callbacks have completed
            int[] completedCount = {0};

            if (totalItems == 0) {
                gui.setItem(13, GuiButton.of(Material.BARRIER, "&cNo Artifact Sets Configured",
                    List.of("&7Ask an admin to configure sets."), e -> {}));
                setupBottomRow(gui, player, page, maxPages);
                gui.open(player);
                return;
            }

            for (int i = startIndex; i < endIndex; i++) {
                final ArtifactSet set = sets.get(i);
                final int slotIdx = i - startIndex;
                // isComplete evaluated INSIDE loadPlayer callback so cache IS populated
                final boolean isComplete = collectionManager.hasCompleteSet(player.getUniqueId(), set);

                collectionManager.getCompletionCount(player.getUniqueId(), set.id(), count -> {
                    int ownedCount = 0;
                    for (ArtifactFragment frag : set.fragments()) {
                        if (collectionManager.hasFragment(player.getUniqueId(), set.id(), frag.fragmentIndex())) ownedCount++;
                    }

                    ItemStack item = new ItemStack(set.displayItem());
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        meta.displayName(TextUtil.colorize(set.displayName()));
                        List<net.kyori.adventure.text.Component> lore = new java.util.ArrayList<>();
                        lore.add(TextUtil.colorize("&8&m────────────────────"));
                        lore.add(TextUtil.colorize("&7" + set.description()));
                        lore.add(TextUtil.colorize("&8&m────────────────────"));
                        lore.add(TextUtil.colorize("&7Pieces: &f" + ownedCount + "&8/&7" + set.getPieceCount()));
                        for (ArtifactFragment frag : set.fragments()) {
                            boolean has = collectionManager.hasFragment(player.getUniqueId(), set.id(), frag.fragmentIndex());
                            lore.add(TextUtil.colorize("&8  ▸ " + (has ? "&a✓ " : "&c✗ ") + frag.displayName()));
                        }
                        lore.add(TextUtil.colorize("&8&m────────────────────"));
                        lore.add(TextUtil.colorize("&7Reward: &a$" + TextUtil.formatNumber(set.rewardMoney())));
                        lore.add(TextUtil.colorize("&7Completions: &b" + count + "×"));
                        lore.add(TextUtil.colorize("&8&m────────────────────"));
                        if (isComplete) {
                            lore.add(TextUtil.colorize("&a▶ Click to claim reward!"));
                            meta.addEnchant(org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA, 1, true);
                            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                        } else {
                            lore.add(TextUtil.colorize("&8Brush suspicious blocks to find more!"));
                        }
                        meta.lore(lore);
                        item.setItemMeta(meta);
                    }

                    final int finalOwnedCount = ownedCount;
                    gui.setItem(slots[slotIdx], new GuiButton(item, e -> {
                        if (isComplete) {
                            curatorGUI.openForSet(player, set);
                        } else {
                            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 1.0f);
                            TextUtil.sendMessage(player, "&cYou don't have all pieces for this set yet! (&f" + finalOwnedCount + "&c/&f" + set.getPieceCount() + "&c)");
                        }
                    }));

                    completedCount[0]++;
                    if (completedCount[0] == totalItems) {
                        // All items built — now open the GUI
                        setupBottomRow(gui, player, page, maxPages);
                        gui.open(player);
                    }
                });
            }
        });
    }

    private void setupBottomRow(Gui gui, Player player, int page, int maxPages) {
        gui.setItem(27, GuiButton.of(Material.BARRIER, "&c&lClose", List.of(), e -> {
            player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            player.closeInventory();
        }));
        if (page > 1) {
            gui.setItem(31, GuiButton.of(Material.SPECTRAL_ARROW, "&e◀ Previous", List.of("&7Page " + (page - 1) + "/" + maxPages), e -> openPage(player, page - 1)));
        } else {
            gui.setItem(31, GuiButton.filler(Material.GRAY_STAINED_GLASS_PANE));
        }
        if (page < maxPages) {
            gui.setItem(35, GuiButton.of(Material.SPECTRAL_ARROW, "&eNext ▶", List.of("&7Page " + (page + 1) + "/" + maxPages), e -> openPage(player, page + 1)));
        } else {
            gui.setItem(35, GuiButton.filler(Material.GRAY_STAINED_GLASS_PANE));
        }
    }
}
