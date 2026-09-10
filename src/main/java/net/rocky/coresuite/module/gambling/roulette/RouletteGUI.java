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
    private static final double[] GOLD_BET_PRESETS = {1_000, 2_500, 5_000, 10_000, 15_000, 25_000};

    private final CoreSuitePlugin plugin;
    private final FileConfiguration config;
    private final CasinoStatsManager statsManager;
    private final Random random = new Random();
    private CasinoMainGUI mainGUI;

    private final Map<UUID, RouletteColor> awaitingCustomBetColor = new ConcurrentHashMap<>();
    private final Set<UUID> activeSpinners = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastSpinTimes = new ConcurrentHashMap<>();

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

        double cMult = config.getDouble("roulette.color-multiplier", 1.90);
        double gMult = config.getDouble("roulette.gold-multiplier", 9.0);
        double taxPercent = config.getDouble("roulette.tax-percent", 7.5);

        String cMultStr = (cMult % 1 == 0) ? String.format("%.0fx", cMult) : String.format("%.2fx", cMult);
        String gMultStr = (gMult % 1 == 0) ? String.format("%.0fx", gMult) : String.format("%.2fx", gMult);

        List<String> wheelLore = new ArrayList<>(List.of(
            "&8&m────────────────────",
            "&7Classic CS:GO-style 15-slot wheel!",
            "&7  ▸ &c7 Red &7(" + cMultStr + " Payout)",
            "&7  ▸ &87 Black &7(" + cMultStr + " Payout)",
            "&7  ▸ &61 Gold &7(" + gMultStr + " JACKPOT!)"
        ));
        if (taxPercent > 0) {
            wheelLore.add("&7  ▸ &cHouse Tax: &f" + taxPercent + "% &7(on wins)");
        }
        wheelLore.add("&8&m────────────────────");
        wheelLore.add("&e▶ Click a color below to select bet:");

        gui.setItem(4, GuiButton.of(Material.COMPASS, "&6&lRoulette Wheel", wheelLore, e -> {}));

        List<String> redLore = new ArrayList<>(List.of("&7Pays &e" + cMultStr + " &7your bet."));
        if (taxPercent > 0) redLore.add("&8(House Tax: &c" + taxPercent + "%&8)");
        redLore.add("");
        redLore.add("&e▶ Click to set bet amount");

        gui.setItem(11, GuiButton.of(Material.RED_CONCRETE, "&c&lBet on RED (" + cMultStr + ")", redLore,
            e -> openBetAmount(player, RouletteColor.RED)
        ));

        List<String> goldLore = new ArrayList<>(List.of(
            "&7The Jackpot slot! Pays &e" + gMultStr + "&7.",
            "&7High volatility jackpot!"
        ));
        if (taxPercent > 0) goldLore.add("&8(House Tax: &c" + taxPercent + "%&8)");
        goldLore.add("");
        goldLore.add("&e▶ Click to set bet amount");

        gui.setItem(13, GuiButton.of(Material.GOLD_BLOCK, "&6&lBet on GOLD (" + gMultStr + ")", goldLore,
            e -> openBetAmount(player, RouletteColor.GOLD)
        ));

        List<String> blackLore = new ArrayList<>(List.of("&7Pays &e" + cMultStr + " &7your bet."));
        if (taxPercent > 0) blackLore.add("&8(House Tax: &c" + taxPercent + "%&8)");
        blackLore.add("");
        blackLore.add("&e▶ Click to set bet amount");

        gui.setItem(15, GuiButton.of(Material.BLACK_CONCRETE, "&8&lBet on BLACK (" + cMultStr + ")", blackLore,
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

        boolean isGold = (chosenColor == RouletteColor.GOLD);
        double mult = isGold
            ? config.getDouble("roulette.gold-multiplier", 9.0)
            : config.getDouble("roulette.color-multiplier", 1.90);
        double taxPercent = config.getDouble("roulette.tax-percent", 7.5);
        double minBet = config.getDouble("min-bet", 1000);
        double maxBet = isGold
            ? config.getDouble("roulette.max-bet-gold", 25000)
            : config.getDouble("max-bet", 250000);

        String multStr = (mult % 1 == 0) ? String.format("%.0fx", mult) : String.format("%.2fx", mult);

        List<String> headerLore = new ArrayList<>(List.of(
            "&8&m────────────────────",
            "&7Payout Multiplier: &e" + multStr,
            "&7Bet Limits: &f" + plugin.getEconomyManager().format(minBet) + " &7- &e" + plugin.getEconomyManager().format(maxBet)
        ));
        if (taxPercent > 0) {
            headerLore.add("&7House Tax: &c" + taxPercent + "% &8(deducted on wins)");
        }
        headerLore.add("&8&m────────────────────");
        headerLore.add("&7Select bet amount below:");

        gui.setItem(4, GuiButton.of(chosenColor.getMaterial(), chosenColor.getName(), headerLore, e -> {}));

        double[] presets = isGold ? GOLD_BET_PRESETS : BET_PRESETS;
        int[] slots = {10, 11, 12, 13, 14, 15};
        for (int i = 0; i < Math.min(presets.length, slots.length); i++) {
            double bet = presets[i];
            if (bet > maxBet) continue;

            double gross = bet * mult;
            double tax = (taxPercent > 0) ? (gross * (taxPercent / 100.0)) : 0.0;
            double net = gross - tax;

            List<String> betLore = new ArrayList<>();
            betLore.add("&8&m────────────────────");
            betLore.add("&7Potential Win: &a" + plugin.getEconomyManager().format(net));
            if (taxPercent > 0) {
                betLore.add("&8(Gross: " + plugin.getEconomyManager().format(gross) + " | Tax: -" + plugin.getEconomyManager().format(tax) + ")");
            }
            betLore.add("&8&m────────────────────");
            betLore.add("&e▶ Click to Spin Roulette");

            gui.setItem(slots[i], GuiButton.of(Material.GOLD_INGOT, "&e&l" + plugin.getEconomyManager().format(bet),
                betLore, e -> startSpin(player, chosenColor, bet)
            ));
        }

        gui.setItem(22, GuiButton.of(Material.NAME_TAG, "&a&l✎ Custom Bet",
            List.of(
                "&7Type custom amount in chat.",
                "&7Max allowed: &e" + plugin.getEconomyManager().format(maxBet),
                taxPercent > 0 ? "&8(House Tax: &c" + taxPercent + "%&8)" : "",
                "",
                "&e▶ Click to type"
            ),
            e -> {
                player.closeInventory();
                awaitingCustomBetColor.put(player.getUniqueId(), chosenColor);
                TextUtil.sendMessage(player, "&8[&6&lCasino&8] &eType your " + chosenColor.getName() + " &ebet amount in chat &8(max &6"
                        + plugin.getEconomyManager().format(maxBet) + "&8, or &ccancel&8):");
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
            TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cInvalid amount. Type a number (e.g. 25000).");
            awaitingCustomBetColor.put(player.getUniqueId(), color);
        }
        return true;
    }

    public void cleanup(UUID uuid) {
        awaitingCustomBetColor.remove(uuid);
        activeSpinners.remove(uuid);
    }

    private void startSpin(Player player, RouletteColor chosenColor, double bet) {
        if (activeSpinners.contains(player.getUniqueId())) {
            TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cYou already have an active spin in progress!");
            return;
        }

        long now = System.currentTimeMillis();
        long lastSpin = lastSpinTimes.getOrDefault(player.getUniqueId(), 0L);
        int cooldownSeconds = config.getInt("roulette.spin-cooldown-seconds", 4);
        long cooldownMs = cooldownSeconds * 1000L;
        if (now - lastSpin < cooldownMs) {
            long waitSec = (cooldownMs - (now - lastSpin)) / 1000L + 1;
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
            TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cPlease wait &e" + waitSec + "s &cbefore spinning again!");
            return;
        }

        double minBet = config.getDouble("min-bet", 1000);
        boolean isGold = (chosenColor == RouletteColor.GOLD);
        double maxBet = isGold
            ? config.getDouble("roulette.max-bet-gold", 25000)
            : config.getDouble("max-bet", 250000);

        if (bet < minBet) {
            TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cMinimum bet is &e" + plugin.getEconomyManager().format(minBet) + "&c.");
            return;
        }

        if (bet > maxBet) {
            if (isGold) {
                TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cMaximum bet on the &6&lGOLD Jackpot &cis &e"
                        + plugin.getEconomyManager().format(maxBet) + "&c!");
            } else {
                TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cMaximum bet is &e" + plugin.getEconomyManager().format(maxBet) + "&c.");
            }
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

        activeSpinners.add(player.getUniqueId());
        lastSpinTimes.put(player.getUniqueId(), now);

        // Winning index on the 15-slot wheel
        int winningIndex = random.nextInt(15);
        RouletteColor winningColor = WHEEL[winningIndex];

        double mult = isGold
            ? config.getDouble("roulette.gold-multiplier", 9.0)
            : config.getDouble("roulette.color-multiplier", 1.90);

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

            @Override
            public void run() {
                if (!player.isOnline()) {
                    activeSpinners.remove(player.getUniqueId());
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

                // Stop after ~45 ticks when offset aligns winningIndex at slot 13 (col 4)
                if (step >= 45 && ((offset + 4) % 15) == winningIndex) {
                    activeSpinners.remove(player.getUniqueId());
                    cancel();

                    if (won) {
                        double grossPayout = bet * mult;
                        double taxPercent = config.getDouble("roulette.tax-percent", 7.5);
                        double taxAmount = (taxPercent > 0) ? (grossPayout * (taxPercent / 100.0)) : 0.0;
                        double netPayout = grossPayout - taxAmount;

                        plugin.getEconomyManager().deposit(player, netPayout);
                        statsManager.recordGame(player.getUniqueId(), player.getName(), bet, netPayout);

                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);

                        String taxNotice = (taxPercent > 0)
                            ? " &8(&c" + taxPercent + "% house tax: -" + plugin.getEconomyManager().format(taxAmount) + "&8)"
                            : "";
                        TextUtil.sendMessage(player, "&8[&6&lCasino&8] &a&lROULETTE WIN! &7Landed on " + winningColor.getName() +
                                "&7! You won &a" + plugin.getEconomyManager().format(netPayout) + taxNotice + "&a!");

                        if (winningColor == RouletteColor.GOLD) {
                            String prefix = config.getString("messages.prefix", "&8[&6&lCasino&8] &r");
                            String gMultFormatted = (mult % 1 == 0) ? String.format("%.0fx", mult) : String.format("%.2fx", mult);
                            Bukkit.broadcastMessage(TextUtil.legacyColorize(
                                prefix + "&6★ &e" + player.getName() + " &ahit the &6&lGOLD ROULETTE JACKPOT (" + gMultFormatted + ") &afor &e" +
                                plugin.getEconomyManager().format(netPayout) + "&a! &6★"
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
