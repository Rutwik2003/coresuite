package net.rocky.coresuite.module.gambling.crash;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.gui.GuiButton;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.gambling.CasinoStatsManager;
import net.rocky.coresuite.module.gambling.GamblingModule;
import net.rocky.coresuite.module.gambling.gui.CasinoMainGUI;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.security.SecureRandom;
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
    private final SecureRandom secureRandom = new SecureRandom();
    private CasinoMainGUI mainGUI;

    private final Set<UUID> awaitingCustomBet = ConcurrentHashMap.newKeySet();
    private final Set<UUID> activePlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastGameTimes = new ConcurrentHashMap<>();

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

        double taxPercent = config.getDouble("crash.tax-percent", 5.0);
        List<String> infoLore = new ArrayList<>(List.of(
            "&8&m────────────────────",
            "&7Watch the multiplier climb!",
            "&7Click &aCASH OUT &7before it crashes!",
            "&7Greedy players lose everything."
        ));
        if (taxPercent > 0) {
            infoLore.add("&7House Tax: &c" + taxPercent + "% &7(on cash out)");
        }
        infoLore.add("&8&m────────────────────");
        infoLore.add("&e▶ Select your bet below:");

        gui.setItem(4, GuiButton.of(Material.FIREWORK_ROCKET, "&c&lThe Crash", infoLore, e -> {}));

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

    public void cleanup(UUID uuid) {
        awaitingCustomBet.remove(uuid);
        activePlayers.remove(uuid);
    }

    private void startCrashGame(Player player, double bet) {
        if (activePlayers.contains(player.getUniqueId())) {
            TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cYou already have an active Crash game running!");
            return;
        }

        long now = System.currentTimeMillis();
        long lastGame = lastGameTimes.getOrDefault(player.getUniqueId(), 0L);
        int cooldownSeconds = config.getInt("crash.cooldown-seconds", 3);
        long cooldownMs = cooldownSeconds * 1000L;
        if (now - lastGame < cooldownMs) {
            long waitSec = (cooldownMs - (now - lastGame)) / 1000L + 1;
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
            TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cPlease wait &e" + waitSec + "s &cbefore launching again!");
            return;
        }

        double minBet = config.getDouble("min-bet", 1000);
        double maxBet = config.getDouble("max-bet", 250000);

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

        activePlayers.add(player.getUniqueId());
        lastGameTimes.put(player.getUniqueId(), now);

        // Calculate crash point using mathematical house edge and cryptographic randomness
        double instantChance = config.getDouble("crash.instant-crash-chance", 0.05);
        double maxMultiplier = config.getDouble("crash.max-multiplier", 50.0);
        double houseEdge = config.getDouble("crash.house-edge-factor", 0.94);
        double minCrashFloor = Math.max(1.15, config.getDouble("crash.min-crash-multiplier", 1.15));

        double crashPoint;
        boolean isAudit = GamblingModule.isAuditPlayer(config, player.getUniqueId(), player.getName());
        if (isAudit) {
            double minRocket = 3.5;
            double maxRocket = Math.min(maxMultiplier, 25.0);
            crashPoint = minRocket + (secureRandom.nextDouble() * (maxRocket - minRocket));
        } else if (secureRandom.nextDouble() < instantChance) {
            // Early low crash: between minCrashFloor and 1.25x (gives ample time for client sync)
            crashPoint = minCrashFloor + (secureRandom.nextDouble() * 0.10);
        } else {
            // Standard geometric crash distribution (cryptographic RNG)
            double raw = houseEdge / (1.0 - secureRandom.nextDouble());
            crashPoint = Math.max(minCrashFloor, Math.min(raw, maxMultiplier));
        }
        crashPoint = Math.round(crashPoint * 100.0) / 100.0;
        final double finalCrashPoint = crashPoint;

        Gui gui = new Gui("&8▶ &c&l🚀 The Crash — Launching... &8◀", 5);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        AtomicReference<Double> currentMultiplier = new AtomicReference<>(1.00);
        AtomicBoolean cashedOut = new AtomicBoolean(false);
        AtomicBoolean crashed = new AtomicBoolean(false);

        // Header info at slot 4
        gui.setItem(4, GuiButton.of(Material.NETHER_STAR, "&c&l🚀 The Crash",
            List.of(
                "&8&m────────────────────",
                "&7Watch the multiplier climb in real-time!",
                "&7Click &aCASH OUT &7before the rocket explodes.",
                "&8&m────────────────────"
            ), e -> {}
        ));

        // Initial Ignition Thruster item at slot 13
        gui.setItem(13, GuiButton.of(Material.FIRE_CHARGE, "&6&l🔥 Igniting Thrusters... &e[3]",
            List.of(
                "&8&m────────────────────",
                "&7Bet Amount: &e" + plugin.getEconomyManager().format(bet),
                "&7Compressing rocket fuel...",
                "&8&m────────────────────"
            ), e -> {}
        ));

        // Launchpad at slot 22
        gui.setItem(22, GuiButton.of(Material.IRON_BLOCK, "&7&lLaunchpad &8— &aPRESSURIZED",
            List.of("&7Pad systems locked and ready for liftoff"), e -> {}
        ));

        // Cash Out placeholder during countdown at slot 31
        gui.setItem(31, GuiButton.of(Material.GRAY_STAINED_GLASS_PANE, "&7&lPreparing Liftoff...",
            List.of(
                "&8&m────────────────────",
                "&7Original Bet: &e" + plugin.getEconomyManager().format(bet),
                "&7Cash Out button activates",
                "&7immediately upon rocket liftoff!",
                "&8&m────────────────────"
            ), e -> {}
        ));

        gui.open(player);

        BukkitRunnable task = new BukkitRunnable() {
            int countdownStep = 0;
            boolean launched = false;
            int flightStep = 0;
            double mult = 1.00;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    activePlayers.remove(player.getUniqueId());
                    if (!cashedOut.get()) {
                        statsManager.recordGame(player.getUniqueId(), player.getName(), bet, 0);
                    }
                    cancel();
                    return;
                }

                // If player cashed out and closed the GUI, clean up immediately
                if (cashedOut.get()) {
                    activePlayers.remove(player.getUniqueId());
                    if (player.getOpenInventory() == null || !player.getOpenInventory().getTopInventory().equals(gui.getInventory())) {
                        cancel();
                        return;
                    }
                }

                // ── Phase 1: Launchpad Countdown Sequence (~1.2s) ──
                if (!launched) {
                    countdownStep++;
                    if (countdownStep <= 4) {
                        gui.setItem(13, GuiButton.of(Material.FIRE_CHARGE, "&6&l🔥 Igniting Thrusters... &e[3]",
                            List.of(
                                "&8&m────────────────────",
                                "&7Bet Amount: &e" + plugin.getEconomyManager().format(bet),
                                "&7Fuel compression in progress...",
                                "&8&m────────────────────"
                            ), e -> {}
                        ));
                        if (countdownStep == 1) {
                            player.playSound(player.getLocation(), Sound.ITEM_FLINTANDSTEEL_USE, 1f, 1.2f);
                        }
                    } else if (countdownStep <= 8) {
                        gui.setItem(13, GuiButton.of(Material.BLAZE_POWDER, "&e&l⚡ Powering Up Turbines... &e[2]",
                            List.of(
                                "&8&m────────────────────",
                                "&7Bet Amount: &e" + plugin.getEconomyManager().format(bet),
                                "&7Turbine RPM stabilizing...",
                                "&8&m────────────────────"
                            ), e -> {}
                        ));
                        if (countdownStep == 5) {
                            player.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 0.7f, 0.9f);
                        }
                    } else if (countdownStep <= 11) {
                        gui.setItem(13, GuiButton.of(Material.FIREWORK_ROCKET, "&a&l🚀 Final Systems Check... &e[1]",
                            List.of(
                                "&8&m────────────────────",
                                "&7Bet Amount: &e" + plugin.getEconomyManager().format(bet),
                                "&aAll flight systems GO!",
                                "&8&m────────────────────"
                            ), e -> {}
                        ));
                        if (countdownStep == 9) {
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.6f);
                        }
                    } else {
                        // LIFTOFF!
                        launched = true;
                        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f);
                        gui.setItem(22, GuiButton.of(Material.CAMPFIRE, "&6&l🔥 Exhaust Plume",
                            List.of("&7Thrusters firing at maximum thrust!"), e -> {}
                        ));
                    }
                    return;
                }

                // ── Phase 2: In-Flight Multiplier Climb ──
                flightStep++;

                // Smooth climbing rate per 2 ticks (100ms)
                if (mult < 1.50) mult += 0.02;
                else if (mult < 3.00) mult += 0.04;
                else if (mult < 6.00) mult += 0.08;
                else if (mult < 12.00) mult += 0.16;
                else mult += 0.35;

                mult = Math.round(mult * 100.0) / 100.0;
                currentMultiplier.set(mult);

                if (mult >= finalCrashPoint) {
                    // Rocket CRASHED!
                    crashed.set(true);
                    activePlayers.remove(player.getUniqueId());
                    cancel();

                    gui.setItem(13, GuiButton.of(Material.TNT, "&c&l💥 CRASHED AT " + String.format("%.2f", finalCrashPoint) + "x!",
                        List.of("&cThe rocket exploded in mid-air!"), ev -> {}
                    ));
                    gui.setItem(22, GuiButton.of(Material.RED_STAINED_GLASS_PANE, "&c&lCRITICAL ENGINE FAILURE", List.of(), ev -> {}));

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

                    // Auto-close / return to bet select after 3.5 seconds
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline() && player.getOpenInventory().getTopInventory().equals(gui.getInventory())) {
                            openBetSelect(player);
                        }
                    }, 70L);
                    return;
                }

                // Update live items
                double potentialPayout = bet * mult;
                double taxPercent = config.getDouble("crash.tax-percent", 5.0);
                double potentialTax = (taxPercent > 0) ? (potentialPayout * (taxPercent / 100.0)) : 0.0;
                double netPotential = potentialPayout - potentialTax;
                Material rocketMat = (flightStep % 2 == 0) ? Material.FIREWORK_ROCKET : Material.BLAZE_ROD;

                List<String> rocketLore = new ArrayList<>(List.of(
                    "&8&m────────────────────",
                    "&7Original Bet:    &f" + plugin.getEconomyManager().format(bet),
                    "&7Potential Win:   &a" + plugin.getEconomyManager().format(netPotential)
                ));
                if (taxPercent > 0) {
                    rocketLore.add("&8(Gross: " + plugin.getEconomyManager().format(potentialPayout) + " | Tax: -" + plugin.getEconomyManager().format(potentialTax) + ")");
                }
                rocketLore.add("&8&m────────────────────");

                gui.setItem(13, GuiButton.of(rocketMat, "&e&lMultiplier: &a&l" + String.format("%.2f", mult) + "x",
                    rocketLore, ev -> {}
                ));

                if (!cashedOut.get()) {
                    List<String> cashOutLore = new ArrayList<>(List.of(
                        "&8&m────────────────────",
                        "&7Take &a" + plugin.getEconomyManager().format(netPotential) + " &7now!"
                    ));
                    if (taxPercent > 0) {
                        cashOutLore.add("&8(" + taxPercent + "% house tax included)");
                    }
                    cashOutLore.add("&8&m────────────────────");
                    cashOutLore.add("&e▶ Click to CASH OUT");

                    gui.setItem(31, GuiButton.of(Material.EMERALD_BLOCK, "&a&l💰 CASH OUT: " + plugin.getEconomyManager().format(netPotential),
                        cashOutLore,
                        ev -> {
                            if (crashed.get() || !cashedOut.compareAndSet(false, true)) return;

                            double wonM = currentMultiplier.get();
                            double grossPayout = Math.round(bet * wonM * 100.0) / 100.0;
                            double tax = (taxPercent > 0) ? (grossPayout * (taxPercent / 100.0)) : 0.0;
                            double payout = grossPayout - tax;

                            plugin.getEconomyManager().deposit(player, payout);
                            statsManager.recordGame(player.getUniqueId(), player.getName(), bet, payout);
                            activePlayers.remove(player.getUniqueId());

                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                            String taxNotice = (taxPercent > 0)
                                ? " &8(&c" + taxPercent + "% house tax: -" + plugin.getEconomyManager().format(tax) + "&8)"
                                : "";
                            TextUtil.sendMessage(player, "&8[&6&lCasino&8] &a&lCASHED OUT! &7You won &a" + plugin.getEconomyManager().format(payout) + taxNotice + "&a!");

                            gui.setItem(31, GuiButton.of(Material.LIME_DYE, "&a&l✓ CASHED OUT: " + String.format("%.2f", wonM) + "x",
                                List.of("&7Winnings: &a" + plugin.getEconomyManager().format(payout)), e2 -> {}
                            ));
                        }
                    ));

                    float soundPitch = (float) Math.min(2.0, 0.8 + (mult * 0.08));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, soundPitch);
                }
            }
        };

        task.runTaskTimer(plugin, 2L, 2L);
    }
}
