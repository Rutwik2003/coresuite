package net.rocky.coresuite.module.gambling.gui;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.gui.GuiButton;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.gambling.CasinoStatsManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Top 10 Casino High Rollers and Biggest Winners Leaderboard GUI.
 */
public class CasinoLeaderboardGUI {

    private static final int[] LEADERBOARD_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        20, 22, 24
    };

    public static void open(CoreSuitePlugin plugin, CasinoStatsManager statsManager, Player player, Runnable onBack) {
        statsManager.getTopWinners(10, topList -> {
            Gui gui = new Gui("&8▶ &6&l🏆 Casino High Rollers &8◀", 4);
            gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

            gui.setItem(4, GuiButton.of(Material.EMERALD, "&6&lCasino Leaderboard",
                List.of(
                    "&8&m────────────────────",
                    "&7Top 10 highest-earning casino players!",
                    "&8&m────────────────────"
                ), e -> {}
            ));

            for (int i = 0; i < Math.min(topList.size(), LEADERBOARD_SLOTS.length); i++) {
                CasinoStatsManager.PlayerCasinoStats stat = topList.get(i);
                int rank = i + 1;
                String medal = rank == 1 ? "&6&l🥇 " : (rank == 2 ? "&f&l🥈 " : (rank == 3 ? "&c&l🥉 " : "&7#" + rank + " "));

                List<String> lore = new ArrayList<>(List.of(
                    "&8&m────────────────────",
                    "&7Total Won:     &a" + plugin.getEconomyManager().format(stat.totalWon()),
                    "&7Total Wagered: &e" + plugin.getEconomyManager().format(stat.totalWagered()),
                    "&7Net Profit:    " + (stat.getNetProfit() >= 0 ? "&a+" : "&c") + plugin.getEconomyManager().format(stat.getNetProfit()),
                    "&7Biggest Win:   &6" + plugin.getEconomyManager().format(stat.biggestWin()),
                    "&7Games Played:  &f" + stat.gamesPlayed(),
                    "&8&m────────────────────"
                ));

                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                if (meta != null) {
                    meta.setOwningPlayer(Bukkit.getOfflinePlayer(stat.uuid()));
                    meta.setDisplayName(TextUtil.legacyColorize(medal + "&e" + stat.playerName()));
                    meta.setLore(lore.stream().map(TextUtil::legacyColorize).toList());
                    head.setItemMeta(meta);
                }

                gui.setItem(LEADERBOARD_SLOTS[i], new GuiButton(head, e -> {}));
            }

            if (topList.isEmpty()) {
                gui.setItem(22, GuiButton.of(Material.BARRIER, "&cNo Casino Data Yet",
                    List.of("&7Be the first to play and top the leaderboard!"), e -> {}));
            }

            // Back button at slot 27
            gui.setItem(27, GuiButton.of(Material.ARROW, "&7◀ Back to Casino", List.of(), e -> {
                if (onBack != null) onBack.run();
            }));

            gui.open(player);
        });
    }
}
