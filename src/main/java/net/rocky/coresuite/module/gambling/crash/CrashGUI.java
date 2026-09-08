package net.rocky.coresuite.module.gambling.crash;

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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The Crash — Real-time multiplier rocket climbing game.
 */
public class CrashGUI {

    private static final double[] BET_PRESETS = {5_000, 10_000, 25_000, 50_000, 100_000, 250_000};

    private final CoreSuitePlugin plugin;
    private final FileConfiguration config;
    private final CasinoStatsManager statsManager;
    private final Random random = new Random();
    private CasinoMainGUI mainGUI;

    private final Set<UUID> awaitingCustomBet = ConcurrentHashMap.newKeySet();

    public CrashGUI(CoreSuitePlugin plugin, FileConfiguration config, CasinoStatsManager statsManager) {
        this.plugin = plugin;
        this.config = config;
        this.statsManager = statsManager;
    }

    public void setMainGUI(CasinoMainGUI mainGUI) {
        this.mainGUI = mainGUI;
    }

    public void openBetSelect(Player player) {
        Gui gui = new Gui("&8▶ &c&l🚀 The Crash — Select Bet &8◀", 4);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        gui.setItem(4, GuiButton.of(Material.FIREWORK_ROCKET, "&c&lThe Crash",
            List.of(
                "&8&m────────────────────",
                "&7Watch the multiplier climb!",
                "&7Click &aCASH OUT &7before it crashes!",
                "&7Greedy players lose everything.",
                "&8&m────────────────────",
                "&e▶ Select your bet below:"
            ), e -> {}
        ));

        int[] slots = {10, 11, 12, 13, 14, 15};
        for (int i = 0; i < Math.min(BET_PRESETS.length, slots.length); i++) {
            double bet = BET_PRESETS[i];
            gui.setItem(slots[i], GuiButton.of(Material.GOLD_INGOT, "&e&l" + plugin.getEconomyManager().format(bet),
                List.of(
                    "&8&m────────────────────",
                    "&7Bet Amount: &e" + plugin.getEconomyManager().format(bet),
                    "&8&m────────────────────",
                    "&e▶ Click to launch rocket"
                ), e -> startCrashGame(player, bet)
            ));
        }

        gui.setItem(22, GuiButton.of(Material.NAME_TAG, "&a&l✎ Custom Bet",
            List.of("&7Type custom bet in chat.", "", "&e▶ Click to type"),
            e -> {
                player.closeInventory();
                awaitingCustomBet.add(player.getUniqueId());
                TextUtil.sendMessage(player, "&8[&6&lCasino&8] &eType your Crash bet in chat &8(or &ccancel&8):");
            }
        ));

        gui.setItem(27, GuiButton.of(Material.ARROW, "&7◀ Back to Casino", List.of(), e -> {
            if (mainGUI != null) mainGUI.open(player);
        }));

        gui.open(player);
    }

    public boolean handleChatBet(Player player, String message) {
        if (!awaitingCustomBet.remove(player.getUniqueId())) return false;

        if (message.equalsIgnoreCase("cancel")) {
            TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cCancelled.");
            Bukkit.getScheduler().runTask(plugin, () -> openBetSelect(player));
            return true;
        }

        try {
            double amount = Double.parseDouble(message.replace(",", "").replace("$", ""));
            Bukkit.getScheduler().runTask(plugin, () -> startCrashGame(player, amount));
        } catch (NumberFormatException ex) {
            TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cInvalid amount. Type a number (e.g. 25000).");
            awaitingCustomBet.add(player.getUniqueId());
        }
        return true;
    }

    private void startCrashGame(Player player, double bet) {
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

        // Calculate crash point using mathematical house edge
        double instantChance = config.getDouble("crash.instant-crash-chance", 0.08);
        double maxMultiplier = config.getDouble("crash.max-multiplier", 50.0);
        double houseEdge = config.getDouble("crash.house-edge-factor", 0.94);

        double crashPoint;
        if (random.nextDouble() < instantChance) {
            crashPoint = 1.00;
        } else {
            // Standard geometric crash distribution
            double raw = houseEdge / (1.0 - random.nextDouble());
            crashPoint = Math.min(raw, maxMultiplier);
            crashPoint = Math.round(crashPoint * 100.0) / 100.0;
        }
        final double finalCrashPoint = crashPoint;

        Gui gui = new Gui("&8▶ &c&l🚀 The Crash &8◀", 5);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        AtomicReference<Double> currentMultiplier = new AtomicReference<>(1.00);
        AtomicBoolean cashedOut = new AtomicBoolean(false);
        AtomicBoolean crashed = new AtomicBoolean(false);

        // Rocket item at slot 13
        gui.setItem(13, GuiButton.of(Material.FIREWORK_ROCKET, "&e&lMultiplier: &a&l1.00x",
            List.of("&7Bet: &a" + plugin.getEconomyManager().format(bet)), e -> {}
        ));

        // Cash out button at slot 31
        gui.setItem(31, GuiButton.of(Material.EMERALD_BLOCK, "&a&l💰 CASH OUT NOW",
            List.of(
                "&8&m────────────────────",
                "&7Current Payout: &a" + plugin.getEconomyManager().format(bet),
                "&8&m────────────────────",
                "&e▶ Click to take your winnings!"
            ),
            e -> {
                if (crashed.get() || !cashedOut.compareAndSet(false, true)) return;

                double wonMult = currentMultiplier.get();
                double payout = Math.round(bet * wonMult * 100.0) / 100.0;
                plugin.getEconomyManager().deposit(player, payout);
                statsManager.recordGame(player.getUniqueId(), player.getName(), bet, payout);

                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                TextUtil.sendMessage(player, "&8[&6&lCasino&8] &a&lCASHED OUT! &7You cashed out at &e" +
                        String.format("%.2f", wonMult) + "x &7and won &a" + plugin.getEconomyManager().format(payout) + "&a!");

                gui.setItem(31, GuiButton.of(Material.LIME_DYE, "&a&l✓ CASHED OUT: " + String.format("%.2f", wonMult) + "x",
                    List.of("&7Winnings: &a" + plugin.getEconomyManager().format(payout)), ev -> {}
                ));
            }
        ));

        gui.open(player);

        BukkitRunnable task = new BukkitRunnable() {
            int step = 0;
            double mult = 1.00;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    if (!cashedOut.get()) {
                        statsManager.recordGame(player.getUniqueId(), player.getName(), bet, 0);
                    }
                    cancel();
                    return;
                }

                step++;

                // Step growth curve
                if (mult < 2.0) mult += 0.04;
                else if (mult < 5.0) mult += 0.08;
                else if (mult < 10.0) mult += 0.15;
                else mult += 0.30;

                mult = Math.round(mult * 100.0) / 100.0;
                currentMultiplier.set(mult);

                if (mult >= finalCrashPoint) {
                    // Rocket CRASHED!
                    crashed.set(true);
                    cancel();

                    gui.setItem(13, GuiButton.of(Material.TNT, "&c&l💥 CRASHED AT " + String.format("%.2f", finalCrashPoint) + "x!",
                        List.of("&cThe rocket crashed!"), ev -> {}
                    ));

                    if (!cashedOut.get()) {
                        statsManager.recordGame(player.getUniqueId(), player.getName(), bet, 0);
                        player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.2f);
                        TextUtil.sendMessage(player, "&8[&6&lCasino&8] &c&lCRASH! &7The rocket crashed at &c" +
                                String.format("%.2f", finalCrashPoint) + "x&7. You lost your bet!");

                        gui.setItem(31, GuiButton.of(Material.BARRIER, "&c&lBUSTED! (" + String.format("%.2f", finalCrashPoint) + "x)",
                            List.of("&cYou didn't cash out in time."), ev -> {}
                        ));
                    } else {
                        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 0.6f, 1f);
                    }

                    // Auto-close after 3 seconds
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline() && player.getOpenInventory().getTopInventory().equals(gui.getInventory())) {
                            openBetSelect(player);
                        }
                    }, 60L);
                    return;
                }

                // Update live items
                double potentialPayout = bet * mult;
                Material rocketMat = (step % 2 == 0) ? Material.FIREWORK_ROCKET : Material.BLAZE_ROD;

                gui.setItem(13, GuiButton.of(rocketMat, "&e&lMultiplier: &a&l" + String.format("%.2f", mult) + "x",
                    List.of(
                        "&8&m────────────────────",
                        "&7Original Bet:    &f" + plugin.getEconomyManager().format(bet),
                        "&7Potential Win:   &a" + plugin.getEconomyManager().format(potentialPayout),
                        "&8&m────────────────────"
                    ), ev -> {}
                ));

                if (!cashedOut.get()) {
                    gui.setItem(31, GuiButton.of(Material.EMERALD_BLOCK, "&a&l💰 CASH OUT: " + plugin.getEconomyManager().format(potentialPayout),
                        List.of(
                            "&8&m────────────────────",
                            "&7Take &a" + plugin.getEconomyManager().format(potentialPayout) + " &7now!",
                            "&8&m────────────────────",
                            "&e▶ Click to CASH OUT"
                        ),
                        ev -> {
                            if (crashed.get() || !cashedOut.compareAndSet(false, true)) return;

                            double wonM = currentMultiplier.get();
                            double payout = Math.round(bet * wonM * 100.0) / 100.0;
                            plugin.getEconomyManager().deposit(player, payout);
                            statsManager.recordGame(player.getUniqueId(), player.getName(), bet, payout);

                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                            TextUtil.sendMessage(player, "&8[&6&lCasino&8] &a&lCASHED OUT! &7You won &a" + plugin.getEconomyManager().format(payout) + "&a!");

                            gui.setItem(31, GuiButton.of(Material.LIME_DYE, "&a&l✓ CASHED OUT: " + String.format("%.2f", wonM) + "x",
                                List.of("&7Winnings: &a" + plugin.getEconomyManager().format(payout)), e2 -> {}
                            ));
                        }
                    ));

                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, (float) Math.min(2.0, 0.8 + (mult * 0.1)));
                }
            }
        };

        task.runTaskTimer(plugin, 3L, 3L);
    }
}
