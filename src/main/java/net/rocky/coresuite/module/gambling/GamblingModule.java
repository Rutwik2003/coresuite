package net.rocky.coresuite.module.gambling;

import net.kyori.adventure.text.Component;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.economy.EconomyManager;
import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.gui.GuiButton;
import net.rocky.coresuite.common.gui.GuiManager;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.Module;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class GamblingModule extends Module implements CommandExecutor, Listener {

    private final EconomyManager economy;
    private final GuiManager guiManager;
    private List<String> acceptedTOS;
    private final Map<UUID, Integer> gamesPlayed = new HashMap<>();
    private final Map<UUID, String> awaitingCustomBet = new java.util.concurrent.ConcurrentHashMap<>();
    private final Random random = new Random();

    private enum GameType { COIN_FLIP, DICE_ROLL, SLOT_MACHINE }

    public GamblingModule(CoreSuitePlugin plugin) {
        super(plugin, "Gambling", "gambling");
        this.economy = plugin.getEconomyManager();
        this.guiManager = plugin.getGuiManager();
    }

    @Override
    public void onEnable() {
        acceptedTOS = config.getStringList("tos-accepted");
        if (acceptedTOS == null) {
            acceptedTOS = new ArrayList<>();
        }

        ConfigurationSection playedSection = config.getConfigurationSection("games-played");
        if (playedSection != null) {
            for (String key : playedSection.getKeys(false)) {
                gamesPlayed.put(UUID.fromString(key), playedSection.getInt(key));
            }
        }

        PluginCommand cmd = plugin.getCommand("gamble");
        if (cmd != null) {
            cmd.setExecutor(this);
        }
        registerListener(this);
    }

    @Override
    public void onDisable() {
        config.set("tos-accepted", acceptedTOS);
        
        config.set("games-played", null);
        for (Map.Entry<UUID, Integer> entry : gamesPlayed.entrySet()) {
            config.set("games-played." + entry.getKey().toString(), entry.getValue());
        }
        
        saveConfig();

        PluginCommand cmd = plugin.getCommand("gamble");
        if (cmd != null && cmd.getExecutor() == this) {
            cmd.setExecutor(null);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (hasAcceptedTOS(player.getUniqueId())) {
            openMainMenu(player);
        } else {
            openTOSMenu1(player);
        }

        return true;
    }

    private boolean hasAcceptedTOS(UUID uuid) {
        return acceptedTOS.contains(uuid.toString());
    }

    private void acceptTOS(UUID uuid) {
        if (!hasAcceptedTOS(uuid)) {
            acceptedTOS.add(uuid.toString());
            config.set("tos-accepted", acceptedTOS);
            org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, this::saveConfig);
        }
    }

    private void recordGamePlayed(UUID uuid) {
        int played = gamesPlayed.getOrDefault(uuid, 0) + 1;
        gamesPlayed.put(uuid, played);
        config.set("games-played." + uuid.toString(), played);
        org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, this::saveConfig);
    }

    private double getWinProbability(Player player, GameType type, double bet) {
        int played = gamesPlayed.getOrDefault(player.getUniqueId(), 0);
        
        // If they get greedy and bet the maximum amount, the casino trap kicks in IMMEDIATELY!
        boolean isGreedy = (type == GameType.COIN_FLIP && bet >= 10000) || (type == GameType.DICE_ROLL && bet >= 5000);
        
        if (type == GameType.COIN_FLIP) {
            if (isGreedy) return 0.35; // 35% chance on huge bets
            
            if (played < 2) return 0.60; // 60% win first 2 games (beginner's luck)
            if (played < 5) return 0.50; // 50% fair chance
            if (played < 10) return 0.45; // 45% win chance
            return 0.42; // 42% win eventually (the long-term house edge)
            
        } else if (type == GameType.DICE_ROLL) {
            if (isGreedy) return 0.08; // 8% chance on huge bets
            
            if (played < 2) return 0.30; // 30% win first 2 games (beginner's luck)
            if (played < 5) return 0.20; // 20% win chance (fair EV for 5x payout)
            if (played < 10) return 0.16; // 16% win chance
            return 0.14; // 14% win eventually
        }
        return 0.0;
    }

    private void openTOSMenu1(Player player) {
        Gui gui = new Gui("&c&lGambling ToS (1/2)", 3);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        List<String> lore = new ArrayList<>();
        lore.add("&7Please read carefully:");
        lore.add("&cAny in-game money you lose here");
        lore.add("&cwill &lNOT&c be our responsibility.");
        lore.add("");
        lore.add("&eClick to confirm you understand.");

        gui.setItem(13, GuiButton.of(Material.PAPER, "&a&lI Understand", lore, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            openTOSMenu2(player);
        }));

        gui.open(player);
    }

    private void openTOSMenu2(Player player) {
        Gui gui = new Gui("&4&lAre you REALLY sure? (2/2)", 3);
        gui.fillBorder(Material.RED_STAINED_GLASS_PANE);

        List<String> lore = new ArrayList<>();
        lore.add("&7Final Confirmation:");
        lore.add("&cBy clicking confirm, you accept all");
        lore.add("&crisks associated with gambling.");
        lore.add("&cRefunds will &lNEVER&c be issued.");
        lore.add("");
        lore.add("&aClick to AGREE and open the menu.");

        gui.setItem(11, GuiButton.of(Material.BARRIER, "&c&lI Decline", List.of("&7Cancel gambling"), e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            player.closeInventory();
        }));

        gui.setItem(15, GuiButton.of(Material.WRITTEN_BOOK, "&a&lI Agree to the ToS", lore, e -> {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
            acceptTOS(player.getUniqueId());
            openMainMenu(player);
        }));

        gui.open(player);
    }

    private void openMainMenu(Player player) {
        Gui gui = new Gui("&8\u25b6 &6&lROYAL CASINO &8\u25c0", 5);
        
        for (int i = 0; i < 45; i++) {
            if (i < 9 || i >= 36 || i % 9 == 0 || i % 9 == 8) {
                Material pane = (i % 2 == 0) ? Material.GRAY_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE;
                gui.setItem(i, GuiButton.filler(pane));
            }
        }
        
        List<String> statsLore = new ArrayList<>();
        statsLore.add("&8&m--------------------------------");
        statsLore.add("&7Balance: &a$" + TextUtil.formatNumber(economy.getBalance(player)));
        statsLore.add("&7Games Played: &e" + gamesPlayed.getOrDefault(player.getUniqueId(), 0));
        statsLore.add("&8&m--------------------------------");
        ItemStack statsHead = TextUtil.createItem(Material.PLAYER_HEAD, "&b&lYour Stats", statsLore.toArray(new String[0]));
        org.bukkit.inventory.meta.SkullMeta meta = (org.bukkit.inventory.meta.SkullMeta) statsHead.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            statsHead.setItemMeta(meta);
        }
        gui.setItem(36, new GuiButton(statsHead, e -> {}));

        gui.setItem(40, GuiButton.of(Material.DARK_OAK_DOOR, "&c&lLeave Casino", List.of("&7Click to exit."), e -> {
            player.playSound(player.getLocation(), Sound.BLOCK_WOODEN_DOOR_CLOSE, 1.0f, 1.0f);
            player.closeInventory();
        }));
        
        gui.setItem(44, new GuiButton(TextUtil.createItem(Material.BOOK, "&e&lCasino Rules", 
            "&8&m--------------------------------",
            "&71. Play responsibly.",
            "&72. The house always has an edge.",
            "&73. All bets are final.",
            "&8&m--------------------------------"
        ), e -> {}));

        List<String> coinFlipLore = new ArrayList<>();
        coinFlipLore.add("&8&m--------------------------------");
        coinFlipLore.add("&7A classic game of heads or tails!");
        coinFlipLore.add("&7You have a &f50% chance &7to win.");
        coinFlipLore.add("");
        coinFlipLore.add("&a&lHow to Play:");
        coinFlipLore.add("&8\u25b8 &7Select a bet amount.");
        coinFlipLore.add("&8\u25b8 &7The dealer flips a virtual coin.");
        coinFlipLore.add("&8\u25b8 &7If you win, you &aDOUBLE &7your bet!");
        coinFlipLore.add("&8\u25b8 &7Win again? Play &cDouble or Nothing&7!");
        coinFlipLore.add("&8&m--------------------------------");
        coinFlipLore.add("&e\u25b6 Click to play Coin Flip \u25c0");
        gui.setItem(20, GuiButton.of(Material.GOLD_BLOCK, "&e&lCoin Flip", coinFlipLore, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            openCoinFlipMenu(player);
        }));

        List<String> slotLore = new ArrayList<>();
        slotLore.add("&8&m--------------------------------");
        slotLore.add("&7Spin the reels to win the Jackpot!");
        slotLore.add("&7Highly addictive. Spin at your own risk.");
        slotLore.add("");
        slotLore.add("&a&lHow to Play:");
        slotLore.add("&8\u25b8 &7Select a bet amount.");
        slotLore.add("&8\u25b8 &7Watch the reels spin.");
        slotLore.add("&8\u25b8 &7Match 3 items to win big!");
        slotLore.add("&8&m--------------------------------");
        slotLore.add("&d\u25b6 Click to play Slot Machine \u25c0");
        gui.setItem(22, GuiButton.of(Material.JUKEBOX, "&d&lSlot Machine", slotLore, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            openSlotMachineMenu(player);
        }));

        List<String> diceLore = new ArrayList<>();
        diceLore.add("&8&m--------------------------------");
        diceLore.add("&7A high-risk, high-reward game.");
        diceLore.add("&7You have a &f1-in-6 chance &7(~16.6%) to win.");
        diceLore.add("");
        diceLore.add("&a&lHow to Play:");
        diceLore.add("&8\u25b8 &7Select a bet amount.");
        diceLore.add("&8\u25b8 &7The dealer rolls a 6-sided die.");
        diceLore.add("&8\u25b8 &7If the die lands on a &f6&7, you win!");
        diceLore.add("&8\u25b8 &7Winning pays out &a5x &7your original bet!");
        diceLore.add("&8&m--------------------------------");
        diceLore.add("&f\u25b6 Click to play Dice Roll \u25c0");
        gui.setItem(24, GuiButton.of(Material.BONE_BLOCK, "&f&lDice Roll", diceLore, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            openDiceRollMenu(player);
        }));

        gui.open(player);
    }

    private void openCoinFlipMenu(Player player) {
        Gui gui = new Gui("&8\u25b6 &e&lCOIN FLIP &8\u25c0", 4);
        
        for (int i = 0; i < 36; i++) {
            if (i < 9 || i >= 27 || i % 9 == 0 || i % 9 == 8) {
                Material pane = (i % 2 == 0) ? Material.GRAY_STAINED_GLASS_PANE : Material.ORANGE_STAINED_GLASS_PANE;
                gui.setItem(i, GuiButton.filler(pane));
            }
        }

        int[] bets = {100, 1000, 10000, 100000};
        int[] slots = {10, 12, 14, 16};

        for (int i = 0; i < bets.length; i++) {
            final double betAmount = bets[i];
            List<String> lore = new ArrayList<>();
            lore.add("&8&m------------------------");
            lore.add("&7Bet Amount: &c$" + TextUtil.formatNumber(betAmount));
            lore.add("&7Potential Win: &a$" + TextUtil.formatNumber(betAmount * 2));
            lore.add("&8&m------------------------");
            lore.add("&e\u25b6 Click to bet \u25c0");

            gui.setItem(slots[i], GuiButton.of(Material.GOLD_INGOT, "&6&lBet $" + TextUtil.formatNumber(betAmount), lore, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
                openCoinFlipSideSelectMenu(player, betAmount);
            }));
        }

        gui.setItem(22, GuiButton.of(Material.NAME_TAG, "&a&lCustom Bet", List.of("&7Click to enter a custom", "&7amount in chat."), e -> {
            player.closeInventory();
            awaitingCustomBet.put(player.getUniqueId(), "COIN_FLIP");
            TextUtil.sendMessage(player, "&8[&6&lCasino&8] &ePlease type your custom bet amount in chat, or type '&ccancel&e' to abort.");
        }));

        gui.setItem(31, GuiButton.of(Material.BARRIER, "&c&lBack", List.of("&7Return to Casino Menu"), e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            openMainMenu(player);
        }));

        gui.open(player);
    }

    private void openCoinFlipSideSelectMenu(Player player, double bet) {
        Gui gui = new Gui("&8\u25b6 &e&lChoose a Side &8\u25c0", 3);
        
        for (int i = 0; i < 27; i++) {
            if (i < 9 || i > 17 || i % 9 == 0 || i % 9 == 8) {
                gui.setItem(i, GuiButton.filler(i % 2 == 0 ? Material.ORANGE_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE));
            }
        }
        
        gui.setItem(11, GuiButton.of(Material.GOLD_BLOCK, "&6&lHeads", List.of("&7Bet $" + TextUtil.formatNumber(bet) + " on Heads"), e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            playCoinFlip(player, bet, true);
        }));
        
        gui.setItem(15, GuiButton.of(Material.IRON_BLOCK, "&f&lTails", List.of("&7Bet $" + TextUtil.formatNumber(bet) + " on Tails"), e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            playCoinFlip(player, bet, false);
        }));
        
        gui.setItem(22, GuiButton.of(Material.BARRIER, "&c&lCancel", List.of("&7Return to menu"), e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            openCoinFlipMenu(player);
        }));
        
        gui.open(player);
    }

    private void playCoinFlip(Player player, double bet, boolean chosenHeads) {
        if (!economy.has(player, bet)) {
            TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cYou do not have enough money for this bet!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        economy.withdraw(player, bet);
        double winProb = getWinProbability(player, GameType.COIN_FLIP, bet);
        boolean win = random.nextDouble() < winProb;
        recordGamePlayed(player.getUniqueId());
        
        Gui flipGui = new Gui("&e&lFlipping Coin...", 3);
        for (int i = 0; i < 27; i++) {
            if (i < 9 || i > 17 || i % 9 == 0 || i % 9 == 8) {
                flipGui.setItem(i, GuiButton.filler(i % 2 == 0 ? Material.ORANGE_STAINED_GLASS_PANE : Material.WHITE_STAINED_GLASS_PANE));
            }
        }
        flipGui.setAllowPlayerInventoryClicks(false);
        flipGui.open(player);

        org.bukkit.scheduler.BukkitRunnable task = new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                ticks++;
                if (ticks <= 30) {
                    if (ticks % 4 == 0) {
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 2.0f);
                        Material coinMat = (ticks % 8 == 0) ? Material.GOLD_INGOT : Material.IRON_INGOT;
                        String coinName = (ticks % 8 == 0) ? "&6&lHeads" : "&f&lTails";
                        flipGui.setItem(13, new GuiButton(TextUtil.createItem(coinMat, coinName), e -> {}));
                    }
                } else if (ticks == 31) {
                    // Result: if win, the coin lands on what they chose. If lose, it lands on the opposite.
                    boolean coinLandedHeads = win ? chosenHeads : !chosenHeads;
                    
                    Material finalMat = coinLandedHeads ? Material.GOLD_INGOT : Material.IRON_INGOT;
                    String finalSideName = coinLandedHeads ? "Heads" : "Tails";
                    String finalName = (coinLandedHeads ? "&6&l" : "&f&l") + finalSideName + (win ? " &a(WIN!)" : " &c(LOSS!)");
                    flipGui.setItem(13, new GuiButton(TextUtil.createItem(finalMat, finalName), e -> {}));
                    
                    String sideColor = chosenHeads ? "&6Heads" : "&fTails";
                    
                    if (win) {
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                        TextUtil.sendMessage(player, "&8[&6&lCasino&8] &7You bet &c$" + TextUtil.formatNumber(bet) + "&7 on " + sideColor + "&7.");
                        double reward = bet * 2;
                        economy.deposit(player, reward);
                        TextUtil.sendMessage(player, "&8[&6&lCasino&8] &aCoin landed on " + finalSideName + "! You won &e$" + TextUtil.formatNumber(reward) + "&a!");
                    } else {
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        TextUtil.sendMessage(player, "&8[&6&lCasino&8] &7You bet &c$" + TextUtil.formatNumber(bet) + "&7 on " + sideColor + "&7.");
                        TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cCoin landed on " + finalSideName + ". You lost!");
                    }
                } else if (ticks == 70) { // Approx 2 seconds after result
                    if (win) {
                        openDoubleOrNothingMenu(player, bet * 2, 1);
                    } else {
                        if (player.getOpenInventory().getTopInventory().equals(flipGui.getInventory())) {
                            player.closeInventory();
                        }
                    }
                    this.cancel();
                }
            }
        };
        task.runTaskTimer(plugin, 0L, 1L);
        
        flipGui.onClose(p -> {
            try { task.cancel(); } catch(Exception ignored) {}
        });
    }

    private void openDoubleOrNothingMenu(Player player, double currentPot, int streak) {
        Gui gui = new Gui("&8\u25b6 &c&lDOUBLE OR NOTHING &8\u25c0", 4);
        
        for (int i = 0; i < 36; i++) {
            if (i < 9 || i >= 27 || i % 9 == 0 || i % 9 == 8) {
                gui.setItem(i, GuiButton.filler(i % 2 == 0 ? Material.RED_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE));
            }
        }

        List<String> winLore = new ArrayList<>();
        winLore.add("&7You are currently holding:");
        winLore.add("&a&l$" + TextUtil.formatNumber(currentPot));
        winLore.add("");
        winLore.add("&7Current Win Streak: &c" + streak);
        gui.setItem(13, new GuiButton(TextUtil.createItem(Material.GOLD_BLOCK, "&e&lCURRENT POT", winLore.toArray(new String[0])), e -> {}));

        List<String> takeLore = new ArrayList<>();
        takeLore.add("&7Don't be greedy. Walk away with your cash.");
        takeLore.add("");
        takeLore.add("&a\u25b6 Click to keep your money \u25c0");

        gui.setItem(20, GuiButton.of(Material.EMERALD_BLOCK, "&a&lTake Money", takeLore, e -> {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            TextUtil.sendMessage(player, "&8[&6&lCasino&8] &aYou smartly walked away with &e$" + TextUtil.formatNumber(currentPot) + "&a!");
            player.closeInventory();
        }));

        List<String> riskLore = new ArrayList<>();
        riskLore.add("&7Risk it all to double it to: &a$" + TextUtil.formatNumber(currentPot * 2));
        riskLore.add("");
        riskLore.add("&cWARNING: If you lose, you get NOTHING.");
        riskLore.add("&e\u25b6 Click to RISK IT ALL \u25c0");

        gui.setItem(24, GuiButton.of(Material.FIRE_CHARGE, "&c&lDOUBLE IT!", riskLore, e -> {
            if (!economy.has(player, currentPot)) {
                player.closeInventory();
                return;
            }
            economy.withdraw(player, currentPot);
            
            // Rigged probabilities based on streak
            double chance = 0.48; // Base slightly rigged
            if (streak == 1) chance = 0.45;
            else if (streak == 2) chance = 0.38;
            else if (streak == 3) chance = 0.25;
            else chance = 0.15;

            boolean won = random.nextDouble() < chance;
            
            Gui riskGui = new Gui("&c&lRolling...", 3);
            for (int i = 0; i < 27; i++) {
                riskGui.setItem(i, GuiButton.filler(Material.BLACK_STAINED_GLASS_PANE));
            }
            riskGui.setAllowPlayerInventoryClicks(false);
            riskGui.open(player);

            org.bukkit.scheduler.BukkitRunnable task = new org.bukkit.scheduler.BukkitRunnable() {
                int ticks = 0;
                @Override
                public void run() {
                    ticks++;
                    if (ticks % 2 == 0 && ticks <= 20) {
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.5f, 2.0f);
                        Material mat = (ticks % 4 == 0) ? Material.RED_STAINED_GLASS_PANE : Material.GREEN_STAINED_GLASS_PANE;
                        riskGui.setItem(13, GuiButton.filler(mat));
                    } else if (ticks == 30) {
                        if (won) {
                            double newPot = currentPot * 2;
                            economy.deposit(player, newPot);
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                            TextUtil.sendMessage(player, "&8[&6&lCasino&8] &a&lLUCKY! &aYou doubled your money to &e$" + TextUtil.formatNumber(newPot) + "&a!");
                            openDoubleOrNothingMenu(player, newPot, streak + 1);
                        } else {
                            player.playSound(player.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
                            TextUtil.sendMessage(player, "&8[&6&lCasino&8] &c&lOOF! &cYou lost it all! The house wins.");
                            player.closeInventory();
                        }
                        this.cancel();
                    }
                }
            };
            task.runTaskTimer(plugin, 0L, 1L);
            riskGui.onClose(p -> { try { task.cancel(); } catch(Exception ignored) {} });
        }));

        gui.open(player);
    }

    private void openDiceRollMenu(Player player) {
        Gui gui = new Gui("&8\u25b6 &f&lDICE ROLL &8\u25c0", 4);
        
        for (int i = 0; i < 36; i++) {
            if (i < 9 || i >= 27 || i % 9 == 0 || i % 9 == 8) {
                Material pane = (i % 2 == 0) ? Material.GRAY_STAINED_GLASS_PANE : Material.WHITE_STAINED_GLASS_PANE;
                gui.setItem(i, GuiButton.filler(pane));
            }
        }

        int[] bets = {100, 1000, 10000, 100000};
        int[] slots = {10, 12, 14, 16};

        for (int i = 0; i < bets.length; i++) {
            final double betAmount = bets[i];
            List<String> lore = new ArrayList<>();
            lore.add("&8&m------------------------");
            lore.add("&7Bet Amount: &c$" + TextUtil.formatNumber(betAmount));
            lore.add("&7Potential Win: &a$" + TextUtil.formatNumber(betAmount * 5));
            lore.add("&7Win Chance: &f16.6% &7(Roll a 6)");
            lore.add("&8&m------------------------");
            lore.add("&e\u25b6 Click to roll \u25c0");

            gui.setItem(slots[i], GuiButton.of(Material.IRON_NUGGET, "&f&lBet $" + TextUtil.formatNumber(betAmount), lore, e -> {
                playDiceRoll(player, betAmount);
            }));
        }

        gui.setItem(22, GuiButton.of(Material.NAME_TAG, "&a&lCustom Bet", List.of("&7Click to enter a custom", "&7amount in chat."), e -> {
            player.closeInventory();
            awaitingCustomBet.put(player.getUniqueId(), "DICE_ROLL");
            TextUtil.sendMessage(player, "&8[&6&lCasino&8] &ePlease type your custom bet amount in chat, or type '&ccancel&e' to abort.");
        }));

        gui.setItem(31, GuiButton.of(Material.BARRIER, "&c&lBack", List.of("&7Return to Casino Menu"), e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            openMainMenu(player);
        }));

        gui.open(player);
    }

    private void playDiceRoll(Player player, double bet) {
        if (!economy.has(player, bet)) {
            TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cYou do not have enough money for this bet!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        economy.withdraw(player, bet);
        
        double winProb = getWinProbability(player, GameType.DICE_ROLL, bet);
        boolean win = random.nextDouble() < winProb;
        recordGamePlayed(player.getUniqueId());
        
        int finalRoll = win ? 6 : (random.nextInt(5) + 1);

        Gui rollGui = new Gui("&f&lRolling Dice...", 3);
        // Professional border
        for (int i = 0; i < 27; i++) {
            if (i < 9 || i > 17 || i % 9 == 0 || i % 9 == 8) {
                rollGui.setItem(i, GuiButton.filler(i % 2 == 0 ? Material.GRAY_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE));
            }
        }
        rollGui.setAllowPlayerInventoryClicks(false);
        rollGui.open(player);

        org.bukkit.scheduler.BukkitRunnable task = new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                ticks++;
                if (ticks <= 30) {
                    // Flashing animation
                    if (ticks % 3 == 0) {
                        player.playSound(player.getLocation(), Sound.BLOCK_WOOD_PLACE, 0.8f, 1.5f);
                        int tempRoll = random.nextInt(6) + 1;
                        rollGui.setItem(13, new GuiButton(TextUtil.createItem(Material.BONE_BLOCK, tempRoll, "&f&lRolling... &e(" + tempRoll + ")"), e -> {}));
                    }
                } else if (ticks == 31) {
                    // Result
                    String finalName = win ? "&a&lWinner! &e(Rolled a 6)" : "&c&lLoss! &e(Rolled a " + finalRoll + ")";
                    rollGui.setItem(13, new GuiButton(TextUtil.createItem(Material.BONE_BLOCK, finalRoll, finalName), e -> {}));
                    
                    if (win) {
                        double reward = bet * 5;
                        economy.deposit(player, reward);
                        TextUtil.sendMessage(player, "&8[&6&lCasino&8] &7You bet &c$" + TextUtil.formatNumber(bet) + "&7 on &fDice Roll&7.");
                        TextUtil.sendMessage(player, "&8[&6&lCasino&8] &aYou rolled a &l6&a and won &e$" + TextUtil.formatNumber(reward) + "&a!");
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                    } else {
                        TextUtil.sendMessage(player, "&8[&6&lCasino&8] &7You bet &c$" + TextUtil.formatNumber(bet) + "&7 on &fDice Roll&7.");
                        TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cYou rolled a &e" + finalRoll + "&c and lost. You needed a 6.");
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                    }
                } else if (ticks == 70) {
                    if (win) {
                        openDoubleOrNothingMenu(player, bet * 5, 1);
                    } else {
                        if (player.getOpenInventory().getTopInventory().equals(rollGui.getInventory())) {
                            player.closeInventory();
                        }
                    }
                    this.cancel();
                }
            }
        };
        task.runTaskTimer(plugin, 0L, 1L);
        
        rollGui.onClose(p -> {
            try { task.cancel(); } catch(Exception ignored) {}
        });
    }

    private void openSlotMachineMenu(Player player) {
        Gui gui = new Gui("&8\u25b6 &d&lSLOT MACHINE &8\u25c0", 4);
        
        for (int i = 0; i < 36; i++) {
            if (i < 9 || i >= 27 || i % 9 == 0 || i % 9 == 8) {
                Material pane = (i % 2 == 0) ? Material.GRAY_STAINED_GLASS_PANE : Material.MAGENTA_STAINED_GLASS_PANE;
                gui.setItem(i, GuiButton.filler(pane));
            }
        }

        int[] bets = {100, 1000, 10000, 100000};
        int[] slots = {10, 12, 14, 16};

        for (int i = 0; i < bets.length; i++) {
            final double betAmount = bets[i];
            List<String> lore = new ArrayList<>();
            lore.add("&8&m------------------------");
            lore.add("&7Bet Amount: &c$" + TextUtil.formatNumber(betAmount));
            lore.add("&7Match 3 Items to win!");
            lore.add("&7Jackpot: &a$" + TextUtil.formatNumber(betAmount * 10) + " &7(Diamonds)");
            lore.add("&8&m------------------------");
            lore.add("&e\u25b6 Click to spin \u25c0");

            gui.setItem(slots[i], GuiButton.of(Material.EMERALD, "&a&lSpin $" + TextUtil.formatNumber(betAmount), lore, e -> {
                playSlotMachine(player, betAmount);
            }));
        }

        gui.setItem(22, GuiButton.of(Material.NAME_TAG, "&a&lCustom Bet", List.of("&7Click to enter a custom", "&7amount in chat."), e -> {
            player.closeInventory();
            awaitingCustomBet.put(player.getUniqueId(), "SLOT_MACHINE");
            TextUtil.sendMessage(player, "&8[&6&lCasino&8] &ePlease type your custom bet amount in chat, or type '&ccancel&e' to abort.");
        }));

        gui.setItem(31, GuiButton.of(Material.BARRIER, "&c&lBack", List.of("&7Return to Casino Menu"), e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            openMainMenu(player);
        }));

        gui.open(player);
    }

    private void playSlotMachine(Player player, double bet) {
        if (!economy.has(player, bet)) {
            TextUtil.sendMessage(player, "&cYou do not have enough money to spin!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        economy.withdraw(player, bet);

        // Prepare the rigged outcome
        int outcomeRoll = random.nextInt(100);
        final Material[] finalItems = new Material[3];
        final double[] reward = {0};
        
        Material[] possibleItems = {Material.DIAMOND, Material.EMERALD, Material.GOLD_INGOT, Material.IRON_INGOT, Material.COAL};

        if (outcomeRoll < 1) {
            // Jackpot (1%)
            finalItems[0] = finalItems[1] = finalItems[2] = Material.DIAMOND;
            reward[0] = bet * 10;
        } else if (outcomeRoll < 6) {
            // Big win (5%)
            finalItems[0] = finalItems[1] = finalItems[2] = Material.EMERALD;
            reward[0] = bet * 5;
        } else if (outcomeRoll < 20) {
            // Small win (14%)
            finalItems[0] = finalItems[1] = finalItems[2] = Material.GOLD_INGOT;
            reward[0] = bet * 2;
        } else if (outcomeRoll < 60) {
            // Near miss (40%) - The hook
            Material missItem = possibleItems[random.nextInt(possibleItems.length)];
            finalItems[0] = finalItems[1] = missItem;
            do {
                finalItems[2] = possibleItems[random.nextInt(possibleItems.length)];
            } while (finalItems[2] == missItem);
        } else {
            // Complete loss
            finalItems[0] = possibleItems[random.nextInt(possibleItems.length)];
            finalItems[1] = possibleItems[random.nextInt(possibleItems.length)];
            finalItems[2] = possibleItems[random.nextInt(possibleItems.length)];
            // Ensure they don't accidentally match all 3
            if (finalItems[0] == finalItems[1] && finalItems[1] == finalItems[2]) {
                finalItems[2] = Material.DIRT; 
            }
        }

        Gui spinGui = new Gui("&d&lSpinning...", 3);
        spinGui.fillBorder(Material.MAGENTA_STAINED_GLASS_PANE);
        spinGui.setAllowPlayerInventoryClicks(false);
        spinGui.open(player);

        // Animation task
        org.bukkit.scheduler.BukkitRunnable task = new org.bukkit.scheduler.BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                ticks++;
                if (ticks <= 20) {
                    spinGui.setItem(11, GuiButton.filler(possibleItems[random.nextInt(possibleItems.length)]));
                } else if (ticks == 21) {
                    spinGui.setItem(11, GuiButton.filler(finalItems[0]));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.5f);
                }

                if (ticks <= 30) {
                    spinGui.setItem(13, GuiButton.filler(possibleItems[random.nextInt(possibleItems.length)]));
                } else if (ticks == 31) {
                    spinGui.setItem(13, GuiButton.filler(finalItems[1]));
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 1.5f);
                }

                if (ticks <= 45) {
                    spinGui.setItem(15, GuiButton.filler(possibleItems[random.nextInt(possibleItems.length)]));
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 2.0f);
                } else if (ticks == 46) {
                    spinGui.setItem(15, GuiButton.filler(finalItems[2]));
                    
                    if (reward[0] > 0) {
                        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                        economy.deposit(player, reward[0]);
                        TextUtil.sendMessage(player, "&8[&6&lCasino&8] &7You bet &c$" + TextUtil.formatNumber(bet) + "&7 on &dSlot Machine&7!");
                        TextUtil.sendMessage(player, "&8[&6&lCasino&8] &a&lJACKPOT! &7You won &a$" + TextUtil.formatNumber(reward[0]) + "&7! Risk it all?");
                        
                        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
                            openDoubleOrNothingMenu(player, reward[0], 1);
                        }, 20L); // Wait 1 second before showing Double or Nothing
                    } else if (finalItems[0] == finalItems[1]) {
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        TextUtil.sendMessage(player, "&8[&6&lCasino&8] &7You bet &c$" + TextUtil.formatNumber(bet) + "&7 on &dSlot Machine&7.");
                        TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cSo close! You were one off!");
                    } else {
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        TextUtil.sendMessage(player, "&8[&6&lCasino&8] &7You bet &c$" + TextUtil.formatNumber(bet) + "&7 on &dSlot Machine&7.");
                        TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cYou lost! Better luck next time.");
                    }
                } else if (ticks == 100) {
                    // Close GUI after 3 seconds of showing the result
                    if (player.getOpenInventory().getTopInventory().equals(spinGui.getInventory())) {
                        player.closeInventory();
                    }
                    this.cancel();
                }
            }
        };
        task.runTaskTimer(plugin, 0L, 2L);

        // Ensure task is cancelled when GUI is closed early
        spinGui.onClose(p -> {
            try { task.cancel(); } catch(Exception ignored) {}
        });
    }

    @org.bukkit.event.EventHandler
    public void onPlayerChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (awaitingCustomBet.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
            String game = awaitingCustomBet.remove(player.getUniqueId());
            String message = event.getMessage();
            
            if (message.equalsIgnoreCase("cancel")) {
                TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cCustom bet cancelled.");
                return;
            }
            
            try {
                double bet = Double.parseDouble(message);
                if (bet <= 0) {
                    TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cBet amount must be greater than 0.");
                    return;
                }
                
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    if (game.equals("COIN_FLIP")) {
                        openCoinFlipSideSelectMenu(player, bet);
                    } else if (game.equals("DICE_ROLL")) {
                        playDiceRoll(player, bet);
                    } else if (game.equals("SLOT_MACHINE")) {
                        playSlotMachine(player, bet);
                    }
                });
            } catch (NumberFormatException e) {
                TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cInvalid amount. Bet cancelled.");
            }
        }
    }

    @org.bukkit.event.EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        awaitingCustomBet.remove(event.getPlayer().getUniqueId());
    }
}
