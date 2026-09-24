package net.rocky.coresuite.module.guide;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.gui.GuiButton;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 54-slot interactive Guide GUI showcasing server commands, features,
 * and the one-time claimable starter gift.
 */
public final class GuideGui {

    private GuideGui() {}

    public static void open(Player player, GuideModule module) {
        if (!module.getRewardManager().isCached(player.getUniqueId())) {
            module.getRewardManager().loadPlayer(player.getUniqueId(), () -> {
                if (player.isOnline()) {
                    openDirect(player, module);
                }
            });
            return;
        }
        openDirect(player, module);
    }

    private static void openDirect(Player player, GuideModule module) {
        String title = module.getConfig().getString("gui.title", "&8&lServer Guide &7&l» &eManual & Perks");
        int rows = module.getConfig().getInt("gui.rows", 6);
        Gui gui = new Gui(title, rows);

        // Fill border
        String borderMatName = module.getConfig().getString("gui.border-material", "GRAY_STAINED_GLASS_PANE");
        Material borderMat = Material.matchMaterial(borderMatName);
        if (borderMat == null) borderMat = Material.GRAY_STAINED_GLASS_PANE;
        gui.fillBorder(borderMat);

        // Populate guide entries
        for (GuideEntry entry : module.getEntries().values()) {
            if (entry.slot() < 0 || entry.slot() >= rows * 9) continue;

            List<String> lore = new ArrayList<>(entry.description());
            ItemStack item = TextUtil.createItem(entry.material(), entry.name(), lore.toArray(String[]::new));

            gui.setItem(entry.slot(), new GuiButton(item, event -> {
                player.closeInventory();

                if (event.isRightClick()) {
                    // Right Click: send detailed guide manual to chat with clickable run action
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.2f);
                    for (String line : entry.chatExplanation()) {
                        if (line.contains("▶ Click to run:")) {
                            Component interactive = TextUtil.colorize(line)
                                    .clickEvent(ClickEvent.runCommand("/" + entry.command()))
                                    .hoverEvent(HoverEvent.showText(TextUtil.colorize("&aClick to run &b/" + entry.command())));
                            TextUtil.sendMessage(player, interactive);
                        } else {
                            TextUtil.sendMessage(player, line);
                        }
                    }
                } else {
                    // Left Click: execute command directly
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                    player.performCommand(entry.command());
                }
            }));
        }

        // Starter Gift Claim Button (Slot 49 by default)
        ConfigurationSection rewardSec = module.getConfig().getConfigurationSection("starter-reward");
        if (rewardSec != null && rewardSec.getBoolean("enabled", true)) {
            int claimSlot = rewardSec.getInt("claim-button.slot", 49);
            boolean claimed = module.getRewardManager().isClaimed(player.getUniqueId());

            if (claimed) {
                String claimedMatName = rewardSec.getString("claim-button.claimed-material", "MINECART");
                Material mat = Material.matchMaterial(claimedMatName);
                if (mat == null) mat = Material.MINECART;

                String name = rewardSec.getString("claim-button.claimed-name", "&c&lStarter Gift Claimed");
                List<String> lore = rewardSec.getStringList("claim-button.claimed-lore");
                ItemStack item = TextUtil.createItem(mat, name, lore.toArray(String[]::new));

                gui.setItem(claimSlot, new GuiButton(item, event -> {
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    TextUtil.sendMessage(player, module.getPrefix() + "&cYou have already claimed your one-time starter gift.");
                }));
            } else {
                String unclaimedMatName = rewardSec.getString("claim-button.unclaimed-material", "CHEST");
                Material mat = Material.matchMaterial(unclaimedMatName);
                if (mat == null) mat = Material.CHEST;

                String name = rewardSec.getString("claim-button.unclaimed-name", "&a&lClaim Starter Gift &7(One-Time)");
                List<String> lore = rewardSec.getStringList("claim-button.unclaimed-lore");
                ItemStack item = TextUtil.createItem(mat, name, lore.toArray(String[]::new));

                gui.setItem(claimSlot, new GuiButton(item, event -> {
                    module.getRewardManager().claimStarterReward(player, () -> {
                        if (player.isOnline()) {
                            GuideGui.open(player, module);
                        }
                    });
                }));
            }
        }

        // Info & Instructions (Slot 4)
        ItemStack infoBook = TextUtil.createItem(Material.KNOWLEDGE_BOOK, "&6&lServer Manual & Guide",
                "&7Welcome to the official server guide!",
                "&7Click any icon below to quickly run commands,",
                "&7or right-click to view the full chat breakdown.",
                "",
                "&e• &fLeft-Click &7» Execute command immediately",
                "&e• &fRight-Click &7» Print detailed instructions",
                "&e• &aClaim Button &7» Claim your one-time welcome gift"
        );
        gui.setItem(4, new GuiButton(infoBook, event -> {
            player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
        }));

        // Close button (Slot 53)
        ItemStack closeItem = TextUtil.createItem(Material.BARRIER, "&c&lClose Guide", "&7Click to exit menu.");
        gui.setItem(53, new GuiButton(closeItem, event -> {
            player.closeInventory();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
        }));

        gui.open(player);
    }
}
