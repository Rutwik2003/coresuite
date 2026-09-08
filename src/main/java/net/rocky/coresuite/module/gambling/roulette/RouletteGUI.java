package net.rocky.coresuite.module.gambling.roulette;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.gui.GuiButton;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.gambling.CasinoStatsManager;
import net.rocky.coresuite.module.gambling.gui.CasinoMainGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * CS:GO-Style 15-Slot Wheel Roulette (Red 2x, Black 2x, Gold 14x).
 */
public class RouletteGUI {

    public enum RouletteColor {
        RED("&c&lRED", Material.RED_CONCRETE, 2.0),
        BLACK("&8&lBLACK", Material.BLACK_CONCRETE, 2.0),
        GOLD("&6&lGOLD", Material.GOLD_BLOCK, 14.0);

        private final String name;
        private final Material material;
        private final double defaultMultiplier;

        RouletteColor(String name, Material material, double defaultMultiplier) {
            this.name = name;
            this.material = material;
            this.defaultMultiplier = defaultMultiplier;
        }

        public String getName() { return name; }
        public Material getMaterial() { return material; }
        public double getDefaultMultiplier() { return defaultMultiplier; }
    }

    // 15-slot wheel: index 0 is GOLD, odd are RED, even are BLACK
    private static final RouletteColor[] WHEEL = new RouletteColor[15];
    static {
        WHEEL[0] = RouletteColor.GOLD;
        for (int i = 1; i < 15; i++) {
            WHEEL[i] = (i % 2 == 1) ? RouletteColor.RED : RouletteColor.BLACK;
        }
    }

    private static final double[] BET_PRESETS = {5_000, 10_000, 25_000, 50_000, 100_000, 250_000};

    private final CoreSuitePlugin plugin;
    private final FileConfiguration config;
    private final CasinoStatsManager statsManager;
    private final Random random = new Random();
    private CasinoMainGUI mainGUI;

    private final Map<UUID, RouletteColor> awaitingCustomBetColor = new ConcurrentHashMap<>();

    public RouletteGUI(CoreSuitePlugin plugin, FileConfiguration config, CasinoStatsManager statsManager) {
        this.plugin = plugin;
        this.config = config;
        this.statsManager = statsManager;
    }

    public void setMainGUI(CasinoMainGUI mainGUI) {
        this.mainGUI = mainGUI;
    }

    public void openColorSelect(Player player) {
        Gui gui = new Gui("&8▶ &e&lRoulette — Choose Bet &8◀", 4);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        gui.setItem(4, GuiButton.of(Material.COMPASS, "&6&lRoulette Wheel",
            List.of(
                "&8&m────────────────────",
                "&7Classic CS:GO-style 15-slot wheel!",
                "&7  ▸ &c7 Red &7(2x Payout)",
                "&7  ▸ &87 Black &7(2x Payout)",
                "&7  ▸ &61 Gold &7(14x JACKPOT!)",
                "&8&m────────────────────",
                "&e▶ Click a color below to select bet:"
            ), e -> {}
        ));

        // Color buttons at slots 11, 13, 15
        gui.setItem(11, GuiButton.of(Material.RED_CONCRETE, "&c&lBet on RED (2x)",
            List.of("&7Pays &e2x &7your bet.", "", "&e▶ Click to set bet amount"),
            e -> openBetAmount(player, RouletteColor.RED)
        ));

        gui.setItem(13, GuiButton.of(Material.GOLD_BLOCK, "&6&lBet on GOLD (14x)",
            List.of("&7The Jackpot slot! Pays &e14x&7.", "", "&e▶ Click to set bet amount"),
            e -> openBetAmount(player, RouletteColor.GOLD)
        ));

        gui.setItem(15, GuiButton.of(Material.BLACK_CONCRETE, "&8&lBet on BLACK (2x)",
            List.of("&7Pays &e2x &7your bet.", "", "&e▶ Click to set bet amount"),
            e -> openBetAmount(player, RouletteColor.BLACK)
        ));

        gui.setItem(27, GuiButton.of(Material.ARROW, "&7◀ Back to Casino", List.of(), e -> {
            if (mainGUI != null) mainGUI.open(player);
        }));

        gui.open(player);
    }

    public void openBetAmount(Player player, RouletteColor chosenColor) {
        Gui gui = new Gui("&8▶ &e&lRoulette — Bet Amount &8◀", 4);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        double mult = (chosenColor == RouletteColor.GOLD)
            ? config.getDouble("roulette.gold-multiplier", 14.0)
            : config.getDouble("roulette.color-multiplier", 2.0);

        gui.setItem(4, GuiButton.of(chosenColor.getMaterial(), chosenColor.getName(),
            List.of(
                "&8&m────────────────────",
                "&7Payout Multiplier: &e" + (int) mult + "x",
                "&8&m────────────────────",
                "&7Select bet amount below:"
            ), e -> {}
        ));

        int[] slots = {10, 11, 12, 13, 14, 15};
        for (int i = 0; i < Math.min(BET_PRESETS.length, slots.length); i++) {
            double bet = BET_PRESETS[i];
            gui.setItem(slots[i], GuiButton.of(Material.GOLD_INGOT, "&e&l" + plugin.getEconomyManager().format(bet),
                List.of(
                    "&8&m────────────────────",
                    "&7Potential Win: &a" + plugin.getEconomyManager().format(bet * mult),
                    "&8&m────────────────────",
                    "&e▶ Click to Spin Roulette"
                ), e -> startSpin(player, chosenColor, bet)
            ));
        }

        gui.setItem(22, GuiButton.of(Material.NAME_TAG, "&a&l✎ Custom Bet",
            List.of("&7Type custom amount in chat.", "", "&e▶ Click to type"),
            e -> {
                player.closeInventory();
                awaitingCustomBetColor.put(player.getUniqueId(), chosenColor);
                TextUtil.sendMessage(player, "&8[&6&lCasino&8] &eType your Roulette bet amount in chat &8(or &ccancel&8):");
            }
        ));

        gui.setItem(27, GuiButton.of(Material.ARROW, "&7◀ Back", List.of(), e -> openColorSelect(player)));

        gui.open(player);
    }

    public boolean handleChatBet(Player player, String message) {
        if (!awaitingCustomBetColor.containsKey(player.getUniqueId())) return false;
        RouletteColor color = awaitingCustomBetColor.remove(player.getUniqueId());

        if (message.equalsIgnoreCase("cancel")) {
            TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cCancelled.");
            Bukkit.getScheduler().runTask(plugin, () -> openBetAmount(player, color));
            return true;
        }

        try {
            double amount = Double.parseDouble(message.replace(",", "").replace("$", ""));
            Bukkit.getScheduler().runTask(plugin, () -> startSpin(player, color, amount));
        } catch (NumberFormatException ex) {
            TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cInvalid amount. Type a number (e.g. 50000).");
            awaitingCustomBetColor.put(player.getUniqueId(), color);
        }
        return true;
    }

    private void startSpin(Player player, RouletteColor chosenColor, double bet) {
        double minBet = config.getDouble("min-bet", 1000);
        double maxBet = config.getDouble("max-bet", 5000000);

        if (bet < minBet || bet > maxBet) {
            TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cBet must be between &e" + plugin.getEconomyManager().format(minBet)
                    + " &cand &e" + plugin.getEconomyManager().format(maxBet) + "&c.");
            return;
        }

        if (!plugin.getEconomyManager().has(player, bet)) {
            TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cYou do not have &e" + plugin.getEconomyManager().format(bet) + "&c.");
            return;
        }

        if (!plugin.getEconomyManager().withdraw(player, bet)) {
            TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cTransaction failed.");
            return;
        }

        // Winning index on the 15-slot wheel
        int winningIndex = random.nextInt(15);
        RouletteColor winningColor = WHEEL[winningIndex];

        double mult = (chosenColor == RouletteColor.GOLD)
            ? config.getDouble("roulette.gold-multiplier", 14.0)
            : config.getDouble("roulette.color-multiplier", 2.0);

        boolean won = (chosenColor == winningColor);

        Gui gui = new Gui("&8▶ &e&lSpinning Roulette... &8◀", 5);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        // Indicator needles at top slot 4 and bottom slot 22
        gui.setItem(4, GuiButton.of(Material.HOPPER, "&e&l▼ SELECTOR ▼", List.of("&7The winning slot lands here!"), e -> {}));
        gui.setItem(22, GuiButton.of(Material.ARROW, "&e&l▲ SELECTOR ▲", List.of("&7The winning slot lands here!"), e -> {}));

        // Summary item at slot 31
        gui.setItem(31, GuiButton.of(chosenColor.getMaterial(), "&7Your Bet: " + chosenColor.getName(),
            List.of("&7Amount: &a" + plugin.getEconomyManager().format(bet)), e -> {}
        ));

        gui.open(player);

        // We want the wheel to spin multiple revolutions and halt with winningIndex at center slot (slot 13, which is 4th item in row 2: slots 9..17)
        // Center of row 2 (slots 9 to 17) is slot 13 (offset 4 from slot 9).
        // So at the final stop, slot 13 must show WHEEL[winningIndex].
        // This means slot 9 shows WHEEL[(winningIndex - 4 + 15) % 15].

        BukkitRunnable task = new BukkitRunnable() {
            int step = 0;
            int offset = random.nextInt(15);
            int speed = 1;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }

                step++;
                offset = (offset + 1) % 15;

                // Render the 9 slots in row 2 (slots 9 to 17)
                for (int col = 0; col < 9; col++) {
                    int slotIndex = (offset + col) % 15;
                    RouletteColor c = WHEEL[slotIndex];
                    int guiSlot = 9 + col;
                    gui.setItem(guiSlot, GuiButton.of(c.getMaterial(), c.getName(), List.of(), e -> {}));
                }

                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, 1.6f);

                // Stop after ~50 ticks when offset aligns winningIndex at slot 13 (col 4)
                if (step >= 45 && ((offset + 4) % 15) == winningIndex) {
                    cancel();

                    if (won) {
                        double payout = bet * mult;
                        plugin.getEconomyManager().deposit(player, payout);
                        statsManager.recordGame(player.getUniqueId(), player.getName(), bet, payout);

                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                        TextUtil.sendMessage(player, "&8[&6&lCasino&8] &a&lROULETTE WIN! &7Landed on " + winningColor.getName() +
                                "&7! You won &a" + plugin.getEconomyManager().format(payout) + "&a!");

                        if (winningColor == RouletteColor.GOLD) {
                            String prefix = config.getString("messages.prefix", "&8[&6&lCasino&8] &r");
                            Bukkit.broadcastMessage(TextUtil.legacyColorize(
                                prefix + "&6★ &e" + player.getName() + " &ahit the &6&lGOLD ROULETTE JACKPOT (14x) &afor &e" +
                                plugin.getEconomyManager().format(payout) + "&a! &6★"
                            ));
                        }
                    } else {
                        statsManager.recordGame(player.getUniqueId(), player.getName(), bet, 0);
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                        TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cRoulette landed on " + winningColor.getName() +
                                "&c. You lost your bet!");
                    }

                    // Auto-reopen color select after 3 seconds
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline() && player.getOpenInventory().getTopInventory().equals(gui.getInventory())) {
                            openColorSelect(player);
                        }
                    }, 60L);
                }
            }
        };

        task.runTaskTimer(plugin, 1L, 1L);
    }
}
