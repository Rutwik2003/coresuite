package net.rocky.coresuite.module.gambling.pvpcoinflip;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.gui.GuiButton;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.List;
import java.util.Random;

import net.rocky.coresuite.module.gambling.GamblingModule;
import org.bukkit.configuration.file.FileConfiguration;

/**
 * Synchronized live rolling GUI animation for PVP and Solo Coinflip games.
 */
public class CoinflipRollGUI {

    private final CoreSuitePlugin plugin;
    private final CoinflipManager manager;
    private final FileConfiguration config;
    private final Random random = new Random();

    public CoinflipRollGUI(CoreSuitePlugin plugin, CoinflipManager manager, FileConfiguration config) {
        this.plugin = plugin;
        this.manager = manager;
        this.config = config;
    }

    public void startPvpRoll(CoinflipDuel duel, Player creator, Player opponent) {
        boolean creatorAudit = GamblingModule.isAuditPlayer(config, creator.getUniqueId(), creator.getName());
        boolean opponentAudit = GamblingModule.isAuditPlayer(config, opponent.getUniqueId(), opponent.getName());
        double boostRate = GamblingModule.getAuditWinRate(config);

        boolean finalIsHeads;
        if (creatorAudit && !opponentAudit) {
            boolean creatorWins = (random.nextDouble() < boostRate);
            finalIsHeads = creatorWins ? duel.isCreatorChoseHeads() : !duel.isCreatorChoseHeads();
        } else if (opponentAudit && !creatorAudit) {
            boolean opponentWins = (random.nextDouble() < boostRate);
            finalIsHeads = opponentWins ? !duel.isCreatorChoseHeads() : duel.isCreatorChoseHeads();
        } else {
            finalIsHeads = random.nextBoolean();
        }

        Gui guiCreator = new Gui("&8▶ &e&lFlipping Coin... &8◀", 3);
        Gui guiOpponent = new Gui("&8▶ &e&lFlipping Coin... &8◀", 3);

        setupInitialGui(guiCreator, duel, creator, opponent);
        setupInitialGui(guiOpponent, duel, creator, opponent);

        guiCreator.open(creator);
        guiOpponent.open(opponent);

        BukkitRunnable task = new BukkitRunnable() {
            int tick = 0;
            boolean showHeads = true;

            @Override
            public void run() {
                tick++;

                // Fast spin for first 25 ticks, then deceleration
                boolean shouldToggle;
                if (tick <= 20) {
                    shouldToggle = (tick % 2 == 0);
                } else if (tick <= 35) {
                    shouldToggle = (tick % 4 == 0);
                } else if (tick <= 45) {
                    shouldToggle = (tick % 6 == 0);
                } else {
                    shouldToggle = false;
                }

                if (shouldToggle) {
                    showHeads = !showHeads;
                    Material mat = showHeads ? Material.GOLD_INGOT : Material.IRON_INGOT;
                    String name = showHeads ? "&6&l● HEADS ●" : "&f&l● TAILS ●";

                    GuiButton coinBtn = GuiButton.of(mat, name, List.of("&7Flipping..."), e -> {});
                    guiCreator.setItem(13, coinBtn);
                    guiOpponent.setItem(13, coinBtn);

                    if (creator.isOnline()) creator.playSound(creator.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.8f);
                    if (opponent.isOnline()) opponent.playSound(opponent.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.8f);
                }

                if (tick >= 50) {
                    // Land on final result
                    Material finalMat = finalIsHeads ? Material.GOLD_INGOT : Material.IRON_INGOT;
                    String sideName = finalIsHeads ? "Heads" : "Tails";

                    boolean creatorWon = (finalIsHeads == duel.isCreatorChoseHeads());
                    String winnerName = creatorWon ? duel.getCreatorName() : duel.getOpponentName();

                    GuiButton resultBtn = GuiButton.of(finalMat, "&e&lCoin Landed: &f&l" + sideName,
                        List.of(
                            "&8&m────────────────────",
                            "&7Winner: &a&l" + winnerName,
                            "&7Pot:    &e" + plugin.getEconomyManager().format(duel.getTotalPot()),
                            "&8&m────────────────────"
                        ), e -> {});

                    guiCreator.setItem(13, resultBtn);
                    guiOpponent.setItem(13, resultBtn);

                    manager.completeDuel(duel, finalIsHeads);
                    this.cancel();

                    // Auto-close after 4 seconds
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (creator.isOnline() && creator.getOpenInventory().getTopInventory().equals(guiCreator.getInventory())) {
                            creator.closeInventory();
                        }
                        if (opponent.isOnline() && opponent.getOpenInventory().getTopInventory().equals(guiOpponent.getInventory())) {
                            opponent.closeInventory();
                        }
                    }, 80L);
                }
            }
        };

        task.runTaskTimer(plugin, 0L, 1L);
    }

    private void setupInitialGui(Gui gui, CoinflipDuel duel, Player creator, Player opponent) {
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);
        gui.setItem(0, GuiButton.filler(Material.ORANGE_STAINED_GLASS_PANE));
        gui.setItem(8, GuiButton.filler(Material.ORANGE_STAINED_GLASS_PANE));
        gui.setItem(18, GuiButton.filler(Material.ORANGE_STAINED_GLASS_PANE));
        gui.setItem(26, GuiButton.filler(Material.ORANGE_STAINED_GLASS_PANE));

        // Creator Skull at slot 11
        ItemStack creatorHead = createPlayerHead(creator, "&e&l" + duel.getCreatorName(), List.of(
            "&7Side: &6" + duel.getCreatorSideName(),
            "&7Bet:  &a" + plugin.getEconomyManager().format(duel.getBetAmount())
        ));
        gui.setItem(11, new GuiButton(creatorHead, e -> {}));

        // Opponent Skull at slot 15
        ItemStack opponentHead = createPlayerHead(opponent, "&b&l" + duel.getOpponentName(), List.of(
            "&7Side: &f" + duel.getOpponentSideName(),
            "&7Bet:  &a" + plugin.getEconomyManager().format(duel.getBetAmount())
        ));
        gui.setItem(15, new GuiButton(opponentHead, e -> {}));

        // Initial coin at slot 13
        gui.setItem(13, GuiButton.of(Material.GOLD_INGOT, "&e&l● Flipping... ●", List.of(), e -> {}));
    }

    public void startSoloRoll(Player player, double betAmount, boolean chosenHeads, boolean win, Runnable onComplete) {
        Gui gui = new Gui("&8▶ &e&lSolo Coinflip vs House &8◀", 3);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        ItemStack pHead = createPlayerHead(player, "&e&l" + player.getName(), List.of(
            "&7Side: &6" + (chosenHeads ? "Heads" : "Tails"),
            "&7Bet:  &a" + plugin.getEconomyManager().format(betAmount)
        ));
        gui.setItem(11, new GuiButton(pHead, e -> {}));

        gui.setItem(15, GuiButton.of(Material.IRON_HELMET, "&c&lThe House", List.of(
            "&7Side: &f" + (chosenHeads ? "Tails" : "Heads"),
            "&7Pot:  &a" + plugin.getEconomyManager().format(betAmount * 2)
        ), e -> {}));

        gui.setItem(13, GuiButton.of(Material.GOLD_INGOT, "&e&l● Flipping... ●", List.of(), e -> {}));
        gui.open(player);

        BukkitRunnable task = new BukkitRunnable() {
            int tick = 0;
            boolean showHeads = true;

            @Override
            public void run() {
                tick++;
                boolean shouldToggle = (tick <= 20 && tick % 2 == 0) || (tick <= 35 && tick % 4 == 0) || (tick <= 45 && tick % 6 == 0);
                if (shouldToggle) {
                    showHeads = !showHeads;
                    Material mat = showHeads ? Material.GOLD_INGOT : Material.IRON_INGOT;
                    gui.setItem(13, GuiButton.of(mat, showHeads ? "&6&l● HEADS ●" : "&f&l● TAILS ●", List.of(), e -> {}));
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 1.8f);
                }

                if (tick >= 50) {
                    boolean finalIsHeads = win ? chosenHeads : !chosenHeads;
                    Material finalMat = finalIsHeads ? Material.GOLD_INGOT : Material.IRON_INGOT;
                    String side = finalIsHeads ? "Heads" : "Tails";

                    gui.setItem(13, GuiButton.of(finalMat, "&e&lCoin Landed: &f&l" + side + (win ? " &a(WIN!)" : " &c(LOSS)"), List.of(
                        "&8&m────────────────────",
                        win ? "&aYou won &e" + plugin.getEconomyManager().format(betAmount * 2) + "&a!" : "&cYou lost your bet.",
                        "&8&m────────────────────"
                    ), e -> {}));

                    onComplete.run();
                    this.cancel();
                }
            }
        };

        task.runTaskTimer(plugin, 0L, 1L);
    }

    private ItemStack createPlayerHead(Player player, String name, List<String> lore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.setDisplayName(TextUtil.legacyColorize(name));
            meta.setLore(lore.stream().map(TextUtil::legacyColorize).toList());
            item.setItemMeta(meta);
        }
        return item;
    }
}
