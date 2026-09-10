package net.rocky.coresuite.module.gambling.slots;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.gui.GuiButton;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.gambling.CasinoStatsManager;
import net.rocky.coresuite.module.gambling.gui.CasinoMainGUI;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Vegas 3-Reel Slots with progressive multipliers, near-miss mechanics, and celebration effects.
 */
public class VegasSlotsGUI {

    private static final double[] BET_PRESETS = {5_000, 10_000, 25_000, 50_000, 100_000, 250_000};

    private final CoreSuitePlugin plugin;
    private final FileConfiguration config;
    private final CasinoStatsManager statsManager;
    private final Random random = new Random();
    private CasinoMainGUI mainGUI;

    private final Set<UUID> awaitingCustomBet = ConcurrentHashMap.newKeySet();
    private final Set<UUID> activeSpinners = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> lastSpinTimes = new ConcurrentHashMap<>();

    public VegasSlotsGUI(CoreSuitePlugin plugin, FileConfiguration config, CasinoStatsManager statsManager) {
        this.plugin = plugin;
        this.config = config;
        this.statsManager = statsManager;
    }

    public void setMainGUI(CasinoMainGUI mainGUI) {
        this.mainGUI = mainGUI;
    }

    public void openBetSelect(Player player) {
        Gui gui = new Gui("&8▶ &d&l🍒 Vegas Slots — Set Bet &8◀", 4);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        double dMult = config.getDouble("slots.multipliers.diamond", 20.0);
        double eMult = config.getDouble("slots.multipliers.emerald", 8.0);
        double gMult = config.getDouble("slots.multipliers.gold", 4.0);
        double iMult = config.getDouble("slots.multipliers.iron", 2.0);
        double taxPercent = config.getDouble("slots.tax-percent", 5.0);

        List<String> machineLore = new ArrayList<>(List.of(
            "&8&m────────────────────",
            "&7Match 3 symbols on the reels to win!",
            "&8  ▸ &b3x Diamonds  &8— &a" + (int)dMult + "x JACKPOT",
            "&8  ▸ &a3x Emeralds  &8— &a" + (int)eMult + "x",
            "&8  ▸ &e3x Gold      &8— &a" + (int)gMult + "x",
            "&8  ▸ &f3x Iron      &8— &a" + (int)iMult + "x"
        ));
        if (taxPercent > 0) {
            machineLore.add("&8  ▸ &cHouse Tax:   &f" + taxPercent + "% &7(on wins)");
        }
        machineLore.add("&8&m────────────────────");
        machineLore.add("&e▶ Select your spin bet below:");

        gui.setItem(4, GuiButton.of(Material.JUKEBOX, "&d&lVegas Slot Machine", machineLore, e -> {}));

        int[] slots = {10, 11, 12, 13, 14, 15};
        for (int i = 0; i < Math.min(BET_PRESETS.length, slots.length); i++) {
            double bet = BET_PRESETS[i];
            double grossJackpot = bet * dMult;
            double netJackpot = (taxPercent > 0) ? (grossJackpot * (1.0 - taxPercent / 100.0)) : grossJackpot;

            List<String> spinLore = new ArrayList<>(List.of(
                "&8&m────────────────────",
                "&7Bet Amount: &e" + plugin.getEconomyManager().format(bet),
                "&7Jackpot:    &a" + plugin.getEconomyManager().format(netJackpot)
            ));
            if (taxPercent > 0) {
                spinLore.add("&8(House Tax: " + taxPercent + "% deducted on wins)");
            }
            spinLore.add("&8&m────────────────────");
            spinLore.add("&e▶ Click to Spin!");

            gui.setItem(slots[i], GuiButton.of(Material.EMERALD, "&a&lSpin " + plugin.getEconomyManager().format(bet),
                spinLore, e -> startSpin(player, bet)
            ));
        }

        gui.setItem(22, GuiButton.of(Material.NAME_TAG, "&a&l✎ Custom Spin Bet",
            List.of(
                "&7Type custom amount in chat.",
                taxPercent > 0 ? "&8(House Tax: &c" + taxPercent + "%&8)" : "",
                "",
                "&e▶ Click to type"
            ),
            e -> {
                player.closeInventory();
                awaitingCustomBet.add(player.getUniqueId());
                TextUtil.sendMessage(player, "&8[&6&lCasino&8] &eType your Slots bet in chat &8(or &ccancel&8):");
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
            Bukkit.getScheduler().runTask(plugin, () -> startSpin(player, amount));
        } catch (NumberFormatException ex) {
            TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cInvalid amount. Type a number (e.g. 50000).");
            awaitingCustomBet.add(player.getUniqueId());
        }
        return true;
    }

    public void cleanup(UUID uuid) {
        awaitingCustomBet.remove(uuid);
        activeSpinners.remove(uuid);
    }

    private void startSpin(Player player, double bet) {
        if (activeSpinners.contains(player.getUniqueId())) {
            TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cYou already have an active spin in progress!");
            return;
        }

        long now = System.currentTimeMillis();
        long lastSpin = lastSpinTimes.getOrDefault(player.getUniqueId(), 0L);
        int cooldownSeconds = config.getInt("slots.cooldown-seconds", 3);
        long cooldownMs = cooldownSeconds * 1000L;
        if (now - lastSpin < cooldownMs) {
            long waitSec = (cooldownMs - (now - lastSpin)) / 1000L + 1;
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
            TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cPlease wait &e" + waitSec + "s &cbefore spinning again!");
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

        activeSpinners.add(player.getUniqueId());
        lastSpinTimes.put(player.getUniqueId(), now);

        // Determine outcome based on configured odds
        double dChance = config.getDouble("slots.odds.diamond-percent", 0.5) / 100.0;
        double eChance = config.getDouble("slots.odds.emerald-percent", 2.5) / 100.0;
        double gChance = config.getDouble("slots.odds.gold-percent", 7.0) / 100.0;
        double iChance = config.getDouble("slots.odds.iron-percent", 15.0) / 100.0;
        double nearMissChance = config.getDouble("slots.odds.near-miss-percent", 40.0) / 100.0;

        double roll = random.nextDouble();
        Material[] finalReels = new Material[3];
        double multiplier = 0;

        if (roll < dChance) {
            finalReels[0] = finalReels[1] = finalReels[2] = Material.DIAMOND;
            multiplier = config.getDouble("slots.multipliers.diamond", 20.0);
        } else if (roll < (dChance + eChance)) {
            finalReels[0] = finalReels[1] = finalReels[2] = Material.EMERALD;
            multiplier = config.getDouble("slots.multipliers.emerald", 8.0);
        } else if (roll < (dChance + eChance + gChance)) {
            finalReels[0] = finalReels[1] = finalReels[2] = Material.GOLD_INGOT;
            multiplier = config.getDouble("slots.multipliers.gold", 4.0);
        } else if (roll < (dChance + eChance + gChance + iChance)) {
            finalReels[0] = finalReels[1] = finalReels[2] = Material.IRON_INGOT;
            multiplier = config.getDouble("slots.multipliers.iron", 2.0);
        } else if (roll < (dChance + eChance + gChance + iChance + nearMissChance)) {
            // Near Miss (2 matching and 1 off)
            Material symbol = random.nextBoolean() ? Material.DIAMOND : Material.EMERALD;
            finalReels[0] = symbol;
            finalReels[1] = symbol;
            finalReels[2] = Material.COAL;
        } else {
            // Total loss
            finalReels[0] = Material.IRON_INGOT;
            finalReels[1] = Material.COAL;
            finalReels[2] = Material.DIRT;
        }

        final double finalMult = multiplier;
        final boolean isWin = multiplier > 0;

        Gui spinGui = new Gui("&8▶ &d&lSpinning Reels... &8◀", 3);
        spinGui.fillBorder(Material.MAGENTA_STAINED_GLASS_PANE);

        Material[] cycle = {Material.DIAMOND, Material.EMERALD, Material.GOLD_INGOT, Material.IRON_INGOT, Material.COAL};

        spinGui.open(player);

        BukkitRunnable task = new BukkitRunnable() {
            int ticks = 0;
            boolean rewarded = false;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    activeSpinners.remove(player.getUniqueId());
                    if (!rewarded) {
                        statsManager.recordGame(player.getUniqueId(), player.getName(), bet, 0);
                    }
                    cancel();
                    return;
                }

                ticks++;

                // Reel 1 spin
                if (ticks <= 15) {
                    spinGui.setItem(11, GuiButton.of(cycle[random.nextInt(cycle.length)], "&eSpinning...", List.of(), e -> {}));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.4f, 1.8f);
                } else if (ticks == 16) {
                    spinGui.setItem(11, GuiButton.of(finalReels[0], "&f&l" + formatMaterial(finalReels[0]), List.of(), e -> {}));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.4f);
                }

                // Reel 2 spin
                if (ticks <= 25) {
                    spinGui.setItem(13, GuiButton.of(cycle[random.nextInt(cycle.length)], "&eSpinning...", List.of(), e -> {}));
                } else if (ticks == 26) {
                    spinGui.setItem(13, GuiButton.of(finalReels[1], "&f&l" + formatMaterial(finalReels[1]), List.of(), e -> {}));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.8f, 1.6f);
                }

                // Reel 3 spin
                if (ticks <= 38) {
                    spinGui.setItem(15, GuiButton.of(cycle[random.nextInt(cycle.length)], "&eSpinning...", List.of(), e -> {}));
                } else if (ticks == 39) {
                    spinGui.setItem(15, GuiButton.of(finalReels[2], "&f&l" + formatMaterial(finalReels[2]), List.of(), e -> {}));
                    activeSpinners.remove(player.getUniqueId());

                    if (isWin) {
                        double grossPayout = bet * finalMult;
                        double taxPercent = config.getDouble("slots.tax-percent", 5.0);
                        double tax = (taxPercent > 0) ? (grossPayout * (taxPercent / 100.0)) : 0.0;
                        double payout = grossPayout - tax;

                        plugin.getEconomyManager().deposit(player, payout);
                        statsManager.recordGame(player.getUniqueId(), player.getName(), bet, payout);
                        rewarded = true;

                        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                        String taxNotice = (taxPercent > 0)
                            ? " &8(&c" + taxPercent + "% house tax: -" + plugin.getEconomyManager().format(tax) + "&8)"
                            : "";
                        TextUtil.sendMessage(player, "&8[&6&lCasino&8] &a&lSLOT WIN! &7Matched 3x &f" +
                                formatMaterial(finalReels[0]) + "! &aWon &e" + plugin.getEconomyManager().format(payout) + taxNotice + "&a!");

                        if (finalReels[0] == Material.DIAMOND) {
                            spawnCelebrationFirework(player);
                            String prefix = config.getString("messages.prefix", "&8[&6&lCasino&8] &r");
                            Bukkit.broadcastMessage(TextUtil.legacyColorize(
                                prefix + "&b★ &e" + player.getName() + " &ahit the &b&lDIAMOND SLOTS JACKPOT (" + (int)finalMult + "x) &afor &e" +
                                plugin.getEconomyManager().format(payout) + "&a! &b★"
                            ));
                        }
                    } else if (finalReels[0] == finalReels[1]) {
                        statsManager.recordGame(player.getUniqueId(), player.getName(), bet, 0);
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                        TextUtil.sendMessage(player, "&8[&6&lCasino&8] &eSo close! &7Two matching symbols, but missed the jackpot.");
                    } else {
                        statsManager.recordGame(player.getUniqueId(), player.getName(), bet, 0);
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                        TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cNo match. Better luck next spin!");
                    }

                    cancel();

                    // Re-open bet selector after 3 seconds
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline() && player.getOpenInventory().getTopInventory().equals(spinGui.getInventory())) {
                            openBetSelect(player);
                        }
                    }, 60L);
                }
            }
        };

        task.runTaskTimer(plugin, 2L, 2L);
    }

    private void spawnCelebrationFirework(Player player) {
        if (!config.getBoolean("enable-fireworks", true)) return;
        try {
            Firework fw = player.getWorld().spawn(player.getLocation(), Firework.class);
            FireworkMeta meta = fw.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder()
                .withColor(Color.AQUA, Color.TEAL, Color.WHITE)
                .withFade(Color.YELLOW)
                .with(FireworkEffect.Type.BALL_LARGE)
                .withFlicker()
                .build());
            meta.setPower(1);
            fw.setFireworkMeta(meta);
        } catch (Exception ignored) {}
    }

    private String formatMaterial(Material m) {
        String name = m.name().toLowerCase().replace('_', ' ');
        String[] parts = name.split(" ");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (!part.isEmpty()) {
                sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }
}
