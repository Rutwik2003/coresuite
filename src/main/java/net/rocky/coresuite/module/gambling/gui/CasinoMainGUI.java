package net.rocky.coresuite.module.gambling.gui;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.gui.GuiButton;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.gambling.CasinoStatsManager;
import net.rocky.coresuite.module.gambling.PlayerCasinoStats;
import net.rocky.coresuite.module.gambling.crash.CrashGUI;
import net.rocky.coresuite.module.gambling.pvpcoinflip.CoinflipListGUI;
import net.rocky.coresuite.module.gambling.roulette.RouletteGUI;
import net.rocky.coresuite.module.gambling.slots.VegasSlotsGUI;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * The Central Royal Casino Hub GUI.
 */
public class CasinoMainGUI {

    private final CoreSuitePlugin plugin;
    private final FileConfiguration config;
    private final CasinoStatsManager statsManager;
    private final CoinflipListGUI coinflipGUI;
    private final CrashGUI crashGUI;
    private final RouletteGUI rouletteGUI;
    private final VegasSlotsGUI slotsGUI;

    public CasinoMainGUI(CoreSuitePlugin plugin, FileConfiguration config, CasinoStatsManager statsManager,
                         CoinflipListGUI coinflipGUI, CrashGUI crashGUI, RouletteGUI rouletteGUI, VegasSlotsGUI slotsGUI) {
        this.plugin = plugin;
        this.config = config;
        this.statsManager = statsManager;
        this.coinflipGUI = coinflipGUI;
        this.crashGUI = crashGUI;
        this.rouletteGUI = rouletteGUI;
        this.slotsGUI = slotsGUI;

        coinflipGUI.setMainGUI(this);
        crashGUI.setMainGUI(this);
        rouletteGUI.setMainGUI(this);
        slotsGUI.setMainGUI(this);
    }

    public void open(Player player) {
        Gui gui = new Gui("&8▶ &6&l👑 ROYAL CASINO 👑 &8◀", 5);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        double balance = plugin.getEconomyManager().getBalance(player);
        PlayerCasinoStats stats = statsManager.getStats(player.getUniqueId(), player.getName());

        // Header Nether Star at slot 4
        gui.setItem(4, GuiButton.of(Material.NETHER_STAR, "&6&l👑 ROYAL CASINO 👑",
            List.of(
                "&8&m────────────────────",
                "&7Welcome, &f" + player.getName() + "&7!",
                "&7Your Balance: &a" + plugin.getEconomyManager().format(balance),
                "&8&m────────────────────",
                "&7Choose an exciting game below!",
                "&8&m────────────────────"
            ), e -> {}
        ));

        // ── Row 2: Featured Games ───────────────────────────────────────────

        // Slot 11: PVP Coinflip
        gui.setItem(11, GuiButton.of(Material.GOLD_BLOCK, "&e&l⚔ PVP Coinflip Duels",
            List.of(
                "&8&m────────────────────",
                "&7Challenge other online players to",
                "&7intense, high-stakes 50/50 rolls!",
                "&8&m────────────────────",
                "&8  ▸ &fSynchronized live coin roll",
                "&8  ▸ &aEscrow-protected wagering",
                "&8  ▸ &eSolo vs House mode available",
                "&8&m────────────────────",
                "&e▶ Click to enter Coinflip Arena"
            ), e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                coinflipGUI.open(player, 0);
            }
        ));

        // Slot 13: The Crash
        double cTax = config.getDouble("crash.tax-percent", 5.0);
        List<String> crashLore = new ArrayList<>(List.of(
            "&8&m────────────────────",
            "&7Ride the multiplier rocket!",
            "&7Multiplier climbs in real-time.",
            "&7Cash out before it CRASHES!",
            "&8&m────────────────────",
            "&8  ▸ &aUp to 50x Multiplier",
            "&8  ▸ &eLive Cash Out Button",
            "&8  ▸ &cHigh tension, high risk"
        ));
        if (cTax > 0) {
            crashLore.add("&8  ▸ &7House Tax: &c" + cTax + "%");
        }
        crashLore.add("&8&m────────────────────");
        crashLore.add("&e▶ Click to launch The Crash");

        gui.setItem(13, GuiButton.of(Material.FIREWORK_ROCKET, "&c&l🚀 The Crash",
            crashLore, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                crashGUI.openBetSelect(player);
            }
        ));

        // Slot 15: Roulette
        double rColorMult = config.getDouble("roulette.color-multiplier", 1.90);
        double rGoldMult = config.getDouble("roulette.gold-multiplier", 9.0);
        double rTax = config.getDouble("roulette.tax-percent", 7.5);
        String rColorStr = (rColorMult % 1 == 0) ? String.format("%.0fx", rColorMult) : String.format("%.2fx", rColorMult);
        String rGoldStr = (rGoldMult % 1 == 0) ? String.format("%.0fx", rGoldMult) : String.format("%.2fx", rGoldMult);

        List<String> rouletteLore = new ArrayList<>(List.of(
            "&8&m────────────────────",
            "&7The classic 15-slot conveyor wheel!",
            "&8&m────────────────────",
            "&8  ▸ &cRed   &8— &a" + rColorStr + " Payout",
            "&8  ▸ &8Black &8— &a" + rColorStr + " Payout",
            "&8  ▸ &6Gold  &8— &e" + rGoldStr + " JACKPOT!"
        ));
        if (rTax > 0) {
            rouletteLore.add("&8  ▸ &7House Tax: &c" + rTax + "%");
        }
        rouletteLore.add("&8&m────────────────────");
        rouletteLore.add("&e▶ Click to play Roulette");

        gui.setItem(15, GuiButton.of(Material.COMPASS, "&6&l🎰 CS:GO Roulette",
            rouletteLore, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                rouletteGUI.openColorSelect(player);
            }
        ));

        // ── Row 4: Secondary Games & Stats ──────────────────────────────────

        // Slot 29: Vegas Slots
        double dMult = config.getDouble("slots.multipliers.diamond", 20.0);
        double eMult = config.getDouble("slots.multipliers.emerald", 8.0);
        double gMult = config.getDouble("slots.multipliers.gold", 4.0);
        double iMult = config.getDouble("slots.multipliers.iron", 2.0);
        double sTax = config.getDouble("slots.tax-percent", 5.0);

        List<String> slotsLore = new ArrayList<>(List.of(
            "&8&m────────────────────",
            "&7Match 3 symbols across the reels!",
            "&8&m────────────────────",
            "&8  ▸ &bDiamonds &8— &a" + (int)dMult + "x JACKPOT",
            "&8  ▸ &aEmeralds &8— &a" + (int)eMult + "x",
            "&8  ▸ &eGold     &8— &a" + (int)gMult + "x",
            "&8  ▸ &fIron     &8— &a" + (int)iMult + "x"
        ));
        if (sTax > 0) {
            slotsLore.add("&8  ▸ &7House Tax: &c" + sTax + "%");
        }
        slotsLore.add("&8&m────────────────────");
        slotsLore.add("&e▶ Click to spin Slots");

        gui.setItem(29, GuiButton.of(Material.JUKEBOX, "&d&l🍒 Vegas 3-Reel Slots",
            slotsLore, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                slotsGUI.openBetSelect(player);
            }
        ));

        // Slot 31: High Rollers Leaderboard
        gui.setItem(31, GuiButton.of(Material.EMERALD, "&6&l🏆 High Rollers Leaderboard",
            List.of(
                "&8&m────────────────────",
                "&7View top-earning casino players,",
                "&7biggest payouts, and server whales.",
                "&8&m────────────────────",
                "&e▶ Click to view Leaderboard"
            ), e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                CasinoLeaderboardGUI.open(plugin, statsManager, player, () -> open(player));
            }
        ));

        // Slot 33: Player Profile Stats
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.setDisplayName(TextUtil.legacyColorize("&b&l" + player.getName() + "'s Casino Stats"));
            List<String> statsLore = new ArrayList<>(stats == null ? List.of("&eStatistics are loading or temporarily unavailable.", "&7Click to refresh. Your saved totals are preserved.") : List.of(
                "&8&m────────────────────",
                "&7Balance:       &a" + plugin.getEconomyManager().format(balance),
                "&7Total Won:     &a" + plugin.getEconomyManager().format(stats.totalWon()),
                "&7Total Wagered: &e" + plugin.getEconomyManager().format(stats.totalWagered()),
                "&7Net Profit:    " + (stats.getNetProfit() >= 0 ? "&a+" : "&c") + plugin.getEconomyManager().format(stats.getNetProfit()),
                "&7Biggest Win:   &6" + plugin.getEconomyManager().format(stats.biggestWin()),
                "&7Games Played:  &f" + stats.gamesPlayed(),
                "&8&m────────────────────"
            ));
            meta.setLore(statsLore.stream().map(TextUtil::legacyColorize).toList());
            head.setItemMeta(meta);
        }
        gui.setItem(33, new GuiButton(head, e -> open(player)));

        // Exit button at slot 40
        gui.setItem(40, GuiButton.of(Material.DARK_OAK_DOOR, "&c&lExit Casino",
            List.of("&7Close the casino menu."),
            e -> {
                player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_DOOR_CLOSE, 1f, 1f);
                player.closeInventory();
            }
        ));

        gui.open(player);
    }
}
