package net.rocky.coresuite.module.wishingwell;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.economy.EconomyManager;
import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.gui.GuiButton;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Dynamic Multi-Screen GUI for the Wishing Well:
 *
 * Screen 1: open(Player)        -> The Mystic Well Chamber (Main Hub)
 * Screen 2: openThrow(Player)   -> Offering Chamber (Pre-calculated odds & presets)
 * Screen 3: openHistory(Player) -> Winners Hall of Fame (Player heads & past jackpots)
 */
public class WellGUI {
    private final CoreSuitePlugin plugin;
    private final WellManager manager;
    private final EconomyManager economy;
    private final FileConfiguration config;
    private final Map<UUID, String> awaitingInput = new ConcurrentHashMap<>();

    public WellGUI(CoreSuitePlugin plugin, WellManager manager, EconomyManager economy, FileConfiguration config) {
        this.plugin  = plugin;
        this.manager = manager;
        this.economy = economy;
        this.config  = config;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SCREEN 1 — The Mystic Well Chamber (Main Hub)
    // ══════════════════════════════════════════════════════════════════════════

    public void open(Player player) {
        Gui gui = new Gui("&8▶ &b&l✨ The Mystic Well &8◀", 5);

        // Border & Glowing Water Well Theme
        for (int i = 0; i <= 8; i++)  gui.setItem(i, GuiButton.filler(Material.BLUE_STAINED_GLASS_PANE));
        for (int i = 36; i <= 44; i++) gui.setItem(i, GuiButton.filler(Material.BLUE_STAINED_GLASS_PANE));
        gui.setItem(9,  GuiButton.filler(Material.BLUE_STAINED_GLASS_PANE));
        gui.setItem(17, GuiButton.filler(Material.BLUE_STAINED_GLASS_PANE));
        gui.setItem(18, GuiButton.filler(Material.BLUE_STAINED_GLASS_PANE));
        gui.setItem(26, GuiButton.filler(Material.BLUE_STAINED_GLASS_PANE));
        gui.setItem(27, GuiButton.filler(Material.BLUE_STAINED_GLASS_PANE));
        gui.setItem(35, GuiButton.filler(Material.BLUE_STAINED_GLASS_PANE));

        // Water ripple effects
        int[] waterSlots = {10, 11, 12, 14, 15, 16, 28, 29, 33, 34};
        for (int slot : waterSlots) {
            gui.setItem(slot, GuiButton.filler(Material.CYAN_STAINED_GLASS_PANE));
        }
        gui.setItem(13, GuiButton.filler(Material.LIGHT_BLUE_STAINED_GLASS_PANE));

        // Well Data
        String potStr        = economy.format(manager.getCurrentPot());
        int    count         = manager.getContributorCount();
        String timeStr       = buildTimeUntilDraw();
        int    intervalHours = config.getInt("draw-interval-hours", 24);
        double pContrib      = manager.getPlayerContribution(player.getUniqueId());
        double pOdds         = manager.getPlayerOddsPercent(player.getUniqueId());

        // Slot 4: Center Core Well Star
        gui.setItem(4, GuiButton.of(Material.NETHER_STAR, "&b&l✨ The Mystic Well",
            List.of(
                "&8&m────────────────────",
                "&7Toss coins into the waters below",
                "&7for a chance to claim the &6Grand Jackpot&7!",
                "&8&m────────────────────",
                "&7Current Jackpot:  &a&l" + potStr,
                "&7Contributors:     &f" + count + " player" + (count == 1 ? "" : "s"),
                "&7Next Draw:        &e" + timeStr,
                "&8&m────────────────────",
                "&7Draws every &e" + intervalHours + " real-world hours&7.",
                "&8(Sleeping does not affect the draw timer)"
            ), e -> {}
        ));

        // Slot 19: Toss Coins Button (Green Block)
        gui.setItem(19, GuiButton.of(Material.EMERALD_BLOCK, "&a&l💰 Toss Coins into the Well",
            List.of(
                "&8&m────────────────────",
                "&7Offer coins to improve your chances.",
                "&7Every coin increases your weight in",
                "&7the grand midnight draw!",
                "&8&m────────────────────",
                "&e▶ Click to open Offering Menu"
            ),
            e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                openThrow(player);
            }
        ));

        // Slot 21: Your Luck & Odds (Ender Eye)
        List<String> oddsLore = new ArrayList<>();
        oddsLore.add("&8&m────────────────────");
        if (pContrib > 0) {
            oddsLore.add("&7Your Contribution: &e" + economy.format(pContrib));
            oddsLore.add("&7Current Win Chance:  &b&l" + TextUtil.formatNumber(pOdds) + "%");
            oddsLore.add("&8&m────────────────────");
            oddsLore.add("&7Odds use fair logarithmic scaling:");
            oddsLore.add("&7Higher offers help, but anyone has a shot!");
        } else {
            oddsLore.add("&cYou haven't tossed any coins yet!");
            oddsLore.add("&8&m────────────────────");
            oddsLore.add("&7Toss coins to enter the current draw.");
        }
        gui.setItem(21, GuiButton.of(Material.ENDER_EYE, "&d&l🎯 Your Luck & Probability", oddsLore, e -> {}));

        // Slot 23: Draw Schedule (Clock)
        gui.setItem(23, GuiButton.of(Material.CLOCK, "&e&l⏱ Draw Schedule",
            List.of(
                "&8&m────────────────────",
                "&7Time Remaining: &e" + timeStr,
                "&7Interval:       &fEvery " + intervalHours + " real hours",
                "&8&m────────────────────",
                "&7When time expires, a winner is chosen",
                "&7and awarded the entire pot server-wide!"
            ), e -> {}
        ));

        // Slot 25: Winners Hall of Fame (Book)
        gui.setItem(25, GuiButton.of(Material.BOOK, "&6&l🏆 Winners Hall of Fame",
            List.of(
                "&8&m────────────────────",
                "&7View recent champions who",
                "&7won the wishing well jackpot!",
                "&8&m────────────────────",
                "&e▶ Click to view past winners"
            ),
            e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                openHistory(player);
            }
        ));

        // Slot 31: Custom Offering Shortcut
        gui.setItem(31, GuiButton.of(Material.EXPERIENCE_BOTTLE, "&e&l✎ Custom Offering",
            List.of(
                "&8&m────────────────────",
                "&7Type any custom coin amount in chat.",
                "&8&m────────────────────",
                "&e▶ Click to enter custom amount"
            ),
            e -> openThrowCustom(player)
        ));

        // Slot 40: Close
        gui.setItem(40, GuiButton.of(Material.BARRIER, "&c&lClose", List.of(), e -> player.closeInventory()));

        gui.open(player);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SCREEN 2 — Offering Chamber (Preset amounts & real-time projected odds)
    // ══════════════════════════════════════════════════════════════════════════

    public void openThrow(Player player) {
        Gui gui = new Gui("&8▶ &e&l💰 Offering Chamber &8◀", 5);

        // Water themed frame
        for (int i = 0; i <= 8; i++)  gui.setItem(i, GuiButton.filler(Material.BLUE_STAINED_GLASS_PANE));
        for (int i = 36; i <= 44; i++) gui.setItem(i, GuiButton.filler(Material.BLUE_STAINED_GLASS_PANE));
        gui.setItem(9,  GuiButton.filler(Material.BLUE_STAINED_GLASS_PANE));
        gui.setItem(17, GuiButton.filler(Material.BLUE_STAINED_GLASS_PANE));
        gui.setItem(18, GuiButton.filler(Material.BLUE_STAINED_GLASS_PANE));
        gui.setItem(26, GuiButton.filler(Material.BLUE_STAINED_GLASS_PANE));
        gui.setItem(27, GuiButton.filler(Material.BLUE_STAINED_GLASS_PANE));
        gui.setItem(35, GuiButton.filler(Material.BLUE_STAINED_GLASS_PANE));

        for (int i = 28; i <= 34; i++) {
            gui.setItem(i, GuiButton.filler(Material.CYAN_STAINED_GLASS_PANE));
        }

        // Header
        gui.setItem(4, GuiButton.of(Material.GOLD_BLOCK, "&e&lChoose Your Offering",
            List.of(
                "&8&m────────────────────",
                "&7Select an amount to toss into the well.",
                "&7Hover over each option to see your",
                "&bprojected winning odds&7!",
                "&8&m────────────────────"
            ), e -> {}
        ));

        // Preset Amounts
        double[] amounts = {1_000, 2_500, 5_000, 10_000, 25_000, 50_000, 100_000};
        int[]    slots   = {19, 20, 21, 22, 23, 24, 25};
        Material[] mats  = {Material.GOLD_NUGGET, Material.GOLD_NUGGET, Material.GOLD_INGOT,
                            Material.GOLD_INGOT, Material.GOLD_BLOCK, Material.GOLD_BLOCK, Material.RAW_GOLD_BLOCK};

        for (int i = 0; i < amounts.length; i++) {
            final double amount = amounts[i];
            double projOdds = manager.getProjectedOdds(player.getUniqueId(), amount);

            gui.setItem(slots[i], GuiButton.of(mats[i], "&e&l" + economy.format(amount),
                List.of(
                    "&8&m────────────────────",
                    "&7Toss &e" + economy.format(amount) + " &7into the well",
                    "&8&m────────────────────",
                    "&7New Odds: &b&l" + TextUtil.formatNumber(projOdds) + "%",
                    "&8&m────────────────────",
                    "&e▶ Click to toss coins!"
                ),
                e -> handleThrow(player, amount)
            ));
        }

        // Slot 31: Custom Amount
        gui.setItem(31, GuiButton.of(Material.EMERALD, "&a&l✎ Custom Amount",
            List.of(
                "&8&m────────────────────",
                "&7Type any custom amount into chat.",
                "&8&m────────────────────",
                "&e▶ Click to enter custom amount"
            ),
            e -> openThrowCustom(player)
        ));

        // Slot 36: Back
        gui.setItem(36, GuiButton.of(Material.SPECTRAL_ARROW, "&7◀ Back to Well", List.of(), e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            open(player);
        }));

        gui.open(player);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SCREEN 3 — Winners Hall of Fame (Past winners & stats)
    // ══════════════════════════════════════════════════════════════════════════

    public void openHistory(Player player) {
        Gui gui = new Gui("&8▶ &6&l🏆 Winners Hall of Fame &8◀", 4);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        gui.setItem(4, GuiButton.of(Material.BEACON, "&6&l🏆 Winners Hall of Fame",
            List.of(
                "&8&m────────────────────",
                "&7Honoring the lucky adventurers",
                "&7who conquered the Mystic Well!",
                "&8&m────────────────────"
            ), e -> {}
        ));

        List<WellManager.WellHistoryEntry> history = manager.getRecentHistory();
        int[] headSlots = {11, 12, 13, 14, 15};

        for (int i = 0; i < 5; i++) {
            int slot = headSlots[i];
            if (i < history.size()) {
                WellManager.WellHistoryEntry entry = history.get(i);
                String timeAgo = TextUtil.formatDuration((System.currentTimeMillis() - entry.drawnAt()) / 1000) + " ago";
                ItemStack head = TextUtil.createItem(Material.PLAYER_HEAD,
                    "&6&l" + entry.name(),
                    "&8&m────────────────────",
                    "&7Jackpot Won: &a&l" + economy.format(entry.potWon()),
                    "&7Won:         &8" + timeAgo,
                    "&8&m────────────────────"
                );
                try {
                    SkullMeta meta = (SkullMeta) head.getItemMeta();
                    if (meta != null) {
                        meta.setOwningPlayer(Bukkit.getOfflinePlayer(entry.name()));
                        head.setItemMeta(meta);
                    }
                } catch (Exception ignored) {}
                gui.setItem(slot, new GuiButton(head, ev -> {}));
            } else {
                gui.setItem(slot, GuiButton.of(Material.GRAY_STAINED_GLASS_PANE,
                    "&8Round #" + (i + 1),
                    List.of("&7No winner recorded yet."),
                    ev -> {}
                ));
            }
        }

        // Slot 27: Back
        gui.setItem(27, GuiButton.of(Material.SPECTRAL_ARROW, "&7◀ Back to Well", List.of(), e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            open(player);
        }));

        // Slot 35: Close
        gui.setItem(35, GuiButton.of(Material.BARRIER, "&c&lClose", List.of(), e -> player.closeInventory()));

        gui.open(player);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Helper & Chat Handling
    // ══════════════════════════════════════════════════════════════════════════

    private String buildTimeUntilDraw() {
        long nextDrawAt = manager.getNextDrawAt();
        if (nextDrawAt <= 0) return "Soon!";
        long msLeft = nextDrawAt - System.currentTimeMillis();
        if (msLeft <= 0) return "Any moment now!";
        return TextUtil.formatDuration(msLeft / 1000);
    }

    private void handleThrow(Player player, double amount) {
        String prefix = config.getString("messages.prefix", "&8[&b✨ Well&8] &r");
        double min = config.getDouble("min-throw", 100);
        if (amount < min) {
            TextUtil.sendMessage(player, prefix + config.getString("messages.min-throw",
                "&cMinimum throw is &e{min}&c.").replace("{min}", economy.format(min)));
            return;
        }
        manager.throwMoney(player, amount,
            () -> {
                player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_THROW, 1.0f, 1.0f);
                String msg = config.getString("messages.threw",
                    "&aTossed &e{amount}&a into the well! Your odds: &b{odds}%")
                    .replace("{amount}", economy.format(amount))
                    .replace("{odds}", TextUtil.formatNumber(manager.getPlayerOddsPercent(player.getUniqueId())));
                TextUtil.sendMessage(player, prefix + msg);
                open(player); // refresh GUI
            },
            () -> TextUtil.sendMessage(player, prefix + config.getString("messages.no-balance",
                "&cYou don't have enough money!"))
        );
    }

    public void openThrowCustom(Player player) {
        player.closeInventory();
        awaitingInput.put(player.getUniqueId(), "throw");
        String prefix = config.getString("messages.prefix", "&8[&b✨ Well&8] &r");
        TextUtil.sendMessage(player, prefix + "&eHow much do you want to throw? &8(type a number or &ccancel&8):");
    }

    public boolean handleChat(Player player, String message) {
        if (!awaitingInput.containsKey(player.getUniqueId())) return false;
        awaitingInput.remove(player.getUniqueId());
        String prefix = config.getString("messages.prefix", "&8[&b✨ Well&8] &r");
        if (message.equalsIgnoreCase("cancel")) {
            TextUtil.sendMessage(player, prefix + "&cCancelled.");
            return true;
        }
        try {
            double amount = Double.parseDouble(message.replace(",", ""));
            Bukkit.getScheduler().runTask(plugin, () -> handleThrow(player, amount));
        } catch (NumberFormatException e) {
            TextUtil.sendMessage(player, prefix + "&cInvalid number.");
        }
        return true;
    }
}
