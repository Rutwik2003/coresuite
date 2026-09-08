package net.rocky.coresuite.module.gambling.pvpcoinflip;

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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 6-row GUI displaying all active open PVP Coinflip duels and creation flow.
 */
public class CoinflipListGUI {

    private static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    private static final double[] BET_PRESETS = {5_000, 10_000, 25_000, 50_000, 100_000, 250_000, 500_000};

    private final CoreSuitePlugin plugin;
    private final FileConfiguration config;
    private final CoinflipManager manager;
    private final CoinflipRollGUI rollGUI;
    private final CasinoStatsManager statsManager;
    private CasinoMainGUI mainGUI;

    // Chat custom bet map
    private final Map<UUID, Boolean> awaitingCustomBetSide = new ConcurrentHashMap<>();

    public CoinflipListGUI(CoreSuitePlugin plugin, FileConfiguration config, CoinflipManager manager,
                           CoinflipRollGUI rollGUI, CasinoStatsManager statsManager) {
        this.plugin = plugin;
        this.config = config;
        this.manager = manager;
        this.rollGUI = rollGUI;
        this.statsManager = statsManager;
    }

    public void setMainGUI(CasinoMainGUI mainGUI) {
        this.mainGUI = mainGUI;
    }

    public void open(Player player, int page) {
        List<CoinflipDuel> duels = manager.getOpenDuels();
        int totalPages = Math.max(1, (int) Math.ceil((double) duels.size() / CONTENT_SLOTS.length));
        int currentPage = Math.max(0, Math.min(page, totalPages - 1));

        Gui gui = new Gui("&8▶ &6&l⚔ PVP Coinflip Board &8◀", 6);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        double taxPercent = config.getDouble("pvp-coinflip.tax-percent", 5.0);

        // Header info at slot 4
        gui.setItem(4, GuiButton.of(Material.GOLD_INGOT, "&6&lPVP Coinflip Duels",
            List.of(
                "&8&m────────────────────",
                "&7Duel other online players in real-time!",
                "&7Winner takes the entire pot.",
                "&8&m────────────────────",
                "&7Active Duels:   &a" + duels.size(),
                "&7House Tax:      &c" + taxPercent + "% &8(burned)",
                "&7Expiry Time:    &e10 minutes &8(auto-refund)",
                "&8&m────────────────────"
            ), e -> {}
        ));

        // Display Duels in content slots
        int start = currentPage * CONTENT_SLOTS.length;
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            int idx = start + i;
            if (idx >= duels.size()) break;
            CoinflipDuel duel = duels.get(idx);

            boolean isCreator = duel.getCreatorUuid().equals(player.getUniqueId());
            double pot = duel.getTotalPot();
            double payout = pot * (1.0 - (taxPercent / 100.0));

            List<String> lore = new ArrayList<>(List.of(
                "&8&m────────────────────",
                "&7Bet Amount:   &a" + plugin.getEconomyManager().format(duel.getBetAmount()),
                "&7Creator Side: &6" + duel.getCreatorSideName(),
                "&7Your Side:    &f" + (isCreator ? duel.getCreatorSideName() : duel.getOpponentSideName()),
                "&7Total Pot:    &e" + plugin.getEconomyManager().format(pot),
                "&7Winner Takes: &a" + plugin.getEconomyManager().format(payout) + " &8(" + taxPercent + "% tax)",
                "&8&m────────────────────"
            ));

            if (isCreator) {
                lore.add("&c▶ Click to CANCEL & refund escrow");
            } else {
                lore.add("&a▶ Click to ACCEPT & DUEL!");
            }

            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            SkullMeta meta = (SkullMeta) head.getItemMeta();
            if (meta != null) {
                meta.setOwningPlayer(Bukkit.getOfflinePlayer(duel.getCreatorUuid()));
                meta.setDisplayName(TextUtil.legacyColorize((isCreator ? "&a★ &e&l" : "&e&l") + duel.getCreatorName() + "'s Coinflip"));
                meta.setLore(lore.stream().map(TextUtil::legacyColorize).toList());
                head.setItemMeta(meta);
            }

            gui.setItem(CONTENT_SLOTS[i], new GuiButton(head, e -> {
                if (isCreator) {
                    manager.cancelDuel(player, duel.getId(), () -> {
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                        TextUtil.sendMessage(player, "&8[&6&lCasino&8] &aDuel cancelled. Escrow refunded!");
                        open(player, currentPage);
                    }, error -> TextUtil.sendMessage(player, "&8[&6&lCasino&8] " + error));
                } else {
                    player.closeInventory();
                    manager.acceptDuel(player, duel.getId(), activeDuel -> {
                        Player creator = Bukkit.getPlayer(activeDuel.getCreatorUuid());
                        if (creator != null && creator.isOnline()) {
                            rollGUI.startPvpRoll(activeDuel, creator, player);
                        }
                    }, error -> {
                        TextUtil.sendMessage(player, "&8[&6&lCasino&8] " + error);
                        open(player, currentPage);
                    });
                }
            }));
        }

        // Navigation row (Row 6)
        gui.setItem(45, GuiButton.of(Material.ARROW, "&7◀ Back to Casino", List.of("&7Return to main hub"), e -> {
            if (mainGUI != null) mainGUI.open(player);
        }));

        gui.setItem(48, GuiButton.of(Material.EMERALD, "&a&l+ Create Coinflip Duel",
            List.of(
                "&8&m────────────────────",
                "&7Post your own coinflip for",
                "&7other players to challenge!",
                "&8&m────────────────────",
                "&e▶ Click to create a duel"
            ), e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                openCreateDuelSide(player);
            }
        ));

        gui.setItem(50, GuiButton.of(Material.GOLD_BLOCK, "&6&lPlay Solo vs House",
            List.of(
                "&8&m────────────────────",
                "&7No players online? Play a fast",
                "&7coinflip against The House!",
                "&8&m────────────────────",
                "&e▶ Click to play Solo"
            ), e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                openSoloCoinflip(player);
            }
        ));

        gui.setItem(53, GuiButton.of(Material.HOPPER, "&b&lRefresh Board", List.of("&7Click to refresh active duels"), e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            open(player, currentPage);
        }));

        gui.open(player);
    }

    // ── Creation flow: Step 1 — Select Side ─────────────────────────────────

    public void openCreateDuelSide(Player player) {
        Gui gui = new Gui("&8▶ &e&lCreate Coinflip — Choose Side &8◀", 3);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        gui.setItem(11, GuiButton.of(Material.GOLD_BLOCK, "&6&lHeads",
            List.of("&7You will represent Heads.", "", "&e▶ Click to select Heads"),
            e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                openCreateDuelBet(player, true);
            }
        ));

        gui.setItem(15, GuiButton.of(Material.IRON_BLOCK, "&f&lTails",
            List.of("&7You will represent Tails.", "", "&e▶ Click to select Tails"),
            e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                openCreateDuelBet(player, false);
            }
        ));

        gui.setItem(18, GuiButton.of(Material.ARROW, "&7◀ Back", List.of(), e -> open(player, 0)));

        gui.open(player);
    }

    // ── Creation flow: Step 2 — Select Bet Amount ───────────────────────────

    public void openCreateDuelBet(Player player, boolean isHeads) {
        Gui gui = new Gui("&8▶ &e&lCreate Coinflip — Set Bet &8◀", 4);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        gui.setItem(4, GuiButton.of(isHeads ? Material.GOLD_INGOT : Material.IRON_INGOT,
            "&e&lYour Side: &f" + (isHeads ? "Heads" : "Tails"),
            List.of("&7Select your bet amount below:"), e -> {}
        ));

        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < Math.min(BET_PRESETS.length, slots.length); i++) {
            double bet = BET_PRESETS[i];
            gui.setItem(slots[i], GuiButton.of(Material.GOLD_NUGGET, "&e&l" + plugin.getEconomyManager().format(bet),
                List.of(
                    "&8&m────────────────────",
                    "&7Total Pot:   &a" + plugin.getEconomyManager().format(bet * 2),
                    "&7House Escrow: Held until filled",
                    "&8&m────────────────────",
                    "&e▶ Click to create duel"
                ), e -> {
                    player.closeInventory();
                    manager.createDuel(player, bet, isHeads, duel -> {
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                        TextUtil.sendMessage(player, "&8[&6&lCasino&8] &aCoinflip duel posted! Other players can now accept it.");
                    }, err -> TextUtil.sendMessage(player, "&8[&6&lCasino&8] " + err));
                }
            ));
        }

        // Custom Bet button at slot 22
        gui.setItem(22, GuiButton.of(Material.NAME_TAG, "&a&l✎ Custom Amount",
            List.of(
                "&8&m────────────────────",
                "&7Want to bet a custom amount?",
                "&7Click here and type in chat.",
                "&8&m────────────────────",
                "&e▶ Click to type custom bet"
            ), e -> {
                player.closeInventory();
                awaitingCustomBetSide.put(player.getUniqueId(), isHeads);
                TextUtil.sendMessage(player, "&8[&6&lCasino&8] &eType your bet amount in chat &8(or &ccancel&8):");
            }
        ));

        gui.setItem(27, GuiButton.of(Material.ARROW, "&7◀ Back", List.of(), e -> openCreateDuelSide(player)));

        gui.open(player);
    }

    public boolean handleChatBet(Player player, String message) {
        if (!awaitingCustomBetSide.containsKey(player.getUniqueId())) return false;
        boolean isHeads = awaitingCustomBetSide.remove(player.getUniqueId());

        if (message.equalsIgnoreCase("cancel")) {
            TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cCancelled.");
            Bukkit.getScheduler().runTask(plugin, () -> openCreateDuelBet(player, isHeads));
            return true;
        }

        try {
            double amount = Double.parseDouble(message.replace(",", "").replace("$", ""));
            Bukkit.getScheduler().runTask(plugin, () -> {
                manager.createDuel(player, amount, isHeads, duel -> {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                    TextUtil.sendMessage(player, "&8[&6&lCasino&8] &aCoinflip duel posted! Other players can now accept it.");
                }, err -> {
                    TextUtil.sendMessage(player, "&8[&6&lCasino&8] " + err);
                    openCreateDuelBet(player, isHeads);
                });
            });
        } catch (NumberFormatException ex) {
            TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cInvalid number. Please type a valid amount (e.g. 50000).");
            awaitingCustomBetSide.put(player.getUniqueId(), isHeads);
        }
        return true;
    }

    public void cleanup(UUID uuid) {
        awaitingCustomBetSide.remove(uuid);
    }

    // ── Solo vs House Coinflip ──────────────────────────────────────────────

    public void openSoloCoinflip(Player player) {
        Gui gui = new Gui("&8▶ &6&lSolo Coinflip vs House &8◀", 3);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        double winChance = config.getDouble("pvp-coinflip.solo-win-chance", 0.44);

        gui.setItem(4, GuiButton.of(Material.GOLD_BLOCK, "&6&lSolo Coinflip",
            List.of(
                "&8&m────────────────────",
                "&7Instant coinflip against The House!",
                "&7Double your money on a win.",
                "&7Win Chance: &e" + (int)(winChance * 100) + "%",
                "&8&m────────────────────"
            ), e -> {}
        ));

        int[] slots = {10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < Math.min(BET_PRESETS.length, slots.length); i++) {
            double bet = BET_PRESETS[i];
            gui.setItem(slots[i], GuiButton.of(Material.GOLD_INGOT, "&e&l" + plugin.getEconomyManager().format(bet),
                List.of(
                    "&8&m────────────────────",
                    "&7Payout on Win: &a" + plugin.getEconomyManager().format(bet * 2),
                    "&8&m────────────────────",
                    "&e▶ Click to flip (Heads)"
                ), e -> {
                    playSoloBet(player, bet);
                }
            ));
        }

        gui.setItem(18, GuiButton.of(Material.ARROW, "&7◀ Back to Duels", List.of(), e -> open(player, 0)));

        gui.open(player);
    }

    private void playSoloBet(Player player, double bet) {
        if (!plugin.getEconomyManager().has(player, bet)) {
            TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cYou do not have &e" + plugin.getEconomyManager().format(bet) + "&c.");
            return;
        }

        if (!plugin.getEconomyManager().withdraw(player, bet)) {
            TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cTransaction failed.");
            return;
        }

        player.closeInventory();
        double winChance = config.getDouble("pvp-coinflip.solo-win-chance", 0.44);
        boolean won = new Random().nextDouble() < winChance;

        rollGUI.startSoloRoll(player, bet, true, won, () -> {
            if (won) {
                double payout = bet * 2.0;
                plugin.getEconomyManager().deposit(player, payout);
                statsManager.recordGame(player.getUniqueId(), player.getName(), bet, payout);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                TextUtil.sendMessage(player, "&8[&6&lCasino&8] &aYou won &e" + plugin.getEconomyManager().format(payout) + "&a against the House!");
            } else {
                statsManager.recordGame(player.getUniqueId(), player.getName(), bet, 0);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cYou lost your bet to the House!");
            }
        });
    }
}
