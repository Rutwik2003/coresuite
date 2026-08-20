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
    private final Random random = new Random();

    private enum GameType { COIN_FLIP, DICE_ROLL }

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
            saveConfig();
        }
    }

    private void recordGamePlayed(UUID uuid) {
        int played = gamesPlayed.getOrDefault(uuid, 0) + 1;
        gamesPlayed.put(uuid, played);
        config.set("games-played." + uuid.toString(), played);
        saveConfig();
    }

    private double getWinProbability(Player player, GameType type, double bet) {
        int played = gamesPlayed.getOrDefault(player.getUniqueId(), 0);
        
        // If they get greedy and bet the maximum amount, the casino trap kicks in IMMEDIATELY!
        boolean isGreedy = (type == GameType.COIN_FLIP && bet >= 10000) || (type == GameType.DICE_ROLL && bet >= 5000);
        
        if (type == GameType.COIN_FLIP) {
            if (isGreedy) return 0.05; // 5% chance if they bet $10k!
            
            if (played < 2) return 1.0; // 100% win first 2 games (only on small/med bets)
            if (played < 5) return 0.45; // 45% win next 3 games
            if (played < 10) return 0.35; // 35% win
            return 0.25; // 25% win eventually (the trap)
            
        } else if (type == GameType.DICE_ROLL) {
            if (isGreedy) return 0.01; // 1% chance if they bet $5k!
            
            if (played < 2) return 0.75; // 75% win first 2 games (only on small/med bets)
            if (played < 5) return 0.15; // 15% win
            if (played < 10) return 0.10; // 10% win
            return 0.05; // 5% win eventually
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
        Gui gui = new Gui("&6&lCasino Menu", 3);
        gui.fillBorder(Material.YELLOW_STAINED_GLASS_PANE);

        // Coin Flip Game
        List<String> coinFlipLore = new ArrayList<>();
        coinFlipLore.add("&8&m--------------------------------");
        coinFlipLore.add("&7A classic game of heads or tails!");
        coinFlipLore.add("&7You have a &f50% chance &7to win.");
        coinFlipLore.add("");
        coinFlipLore.add("&a&lHow to Play:");
        coinFlipLore.add("&8- &7Select a bet amount.");
        coinFlipLore.add("&8- &7The dealer flips a virtual coin.");
        coinFlipLore.add("&8- &7If you win, you &aDOUBLE &7your bet!");
        coinFlipLore.add("&8- &7If you lose, the house takes the bet.");
        coinFlipLore.add("&8&m--------------------------------");
        coinFlipLore.add("&eClick to play Coin Flip!");
        
        gui.setItem(11, GuiButton.of(Material.SUNFLOWER, "&e&lCoin Flip", coinFlipLore, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            openCoinFlipMenu(player);
        }));

        // Dice Roll Game
        List<String> diceLore = new ArrayList<>();
        diceLore.add("&8&m--------------------------------");
        diceLore.add("&7A high-risk, high-reward game.");
        diceLore.add("&7You have a &f1-in-6 chance &7(~16.6%) to win.");
        diceLore.add("");
        diceLore.add("&a&lHow to Play:");
        diceLore.add("&8- &7Select a bet amount.");
        diceLore.add("&8- &7The dealer rolls a 6-sided die.");
        diceLore.add("&8- &7If the die lands on a &f6&7, you win!");
        diceLore.add("&8- &7Winning pays out &a5x &7your original bet!");
        diceLore.add("&8&m--------------------------------");
        diceLore.add("&eClick to play Dice Roll!");

        gui.setItem(15, GuiButton.of(Material.BONE_BLOCK, "&f&lDice Roll", diceLore, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            openDiceRollMenu(player);
        }));

        gui.open(player);
    }

    private void openCoinFlipMenu(Player player) {
        Gui gui = new Gui("&e&lCoin Flip - Select Bet", 3);
        gui.fillBorder(Material.ORANGE_STAINED_GLASS_PANE);

        int[] bets = {100, 1000, 10000};
        int[] slots = {11, 13, 15};

        for (int i = 0; i < bets.length; i++) {
            final double betAmount = bets[i];
            List<String> lore = new ArrayList<>();
            lore.add("&8&m------------------------");
            lore.add("&7Bet Amount: &c$" + betAmount);
            lore.add("&7Potential Win: &a$" + (betAmount * 2));
            lore.add("&8&m------------------------");
            lore.add("&eClick to bet!");

            gui.setItem(slots[i], GuiButton.of(Material.GOLD_INGOT, "&6&lBet $" + betAmount, lore, e -> {
                playCoinFlip(player, betAmount);
                player.closeInventory();
            }));
        }

        // Back Button
        gui.setItem(22, GuiButton.of(Material.BARRIER, "&c&lBack", List.of("&7Return to Casino Menu"), e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            openMainMenu(player);
        }));

        gui.open(player);
    }

    private void playCoinFlip(Player player, double bet) {
        if (!economy.has(player, bet)) {
            TextUtil.sendMessage(player, "&cYou do not have enough money for this bet!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        economy.withdraw(player, bet);
        
        double winProb = getWinProbability(player, GameType.COIN_FLIP, bet);
        boolean win = random.nextDouble() < winProb;
        recordGamePlayed(player.getUniqueId());

        if (win) {
            double reward = bet * 2;
            economy.deposit(player, reward);
            TextUtil.sendMessage(player, "&6[Casino] &aYou won the coin flip and received &e$" + reward + "&a!");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        } else {
            TextUtil.sendMessage(player, "&6[Casino] &cYou lost the coin flip. Better luck next time!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }

    private void openDiceRollMenu(Player player) {
        Gui gui = new Gui("&f&lDice Roll - Select Bet", 3);
        gui.fillBorder(Material.GRAY_STAINED_GLASS_PANE);

        int[] bets = {500, 2500, 5000};
        int[] slots = {11, 13, 15};

        for (int i = 0; i < bets.length; i++) {
            final double betAmount = bets[i];
            List<String> lore = new ArrayList<>();
            lore.add("&8&m------------------------");
            lore.add("&7Bet Amount: &c$" + betAmount);
            lore.add("&7Potential Win: &a$" + (betAmount * 5));
            lore.add("&7Win Chance: &f16.6% &7(Roll a 6)");
            lore.add("&8&m------------------------");
            lore.add("&eClick to roll!");

            gui.setItem(slots[i], GuiButton.of(Material.IRON_NUGGET, "&f&lBet $" + betAmount, lore, e -> {
                playDiceRoll(player, betAmount);
                player.closeInventory();
            }));
        }

        // Back Button
        gui.setItem(22, GuiButton.of(Material.BARRIER, "&c&lBack", List.of("&7Return to Casino Menu"), e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            openMainMenu(player);
        }));

        gui.open(player);
    }

    private void playDiceRoll(Player player, double bet) {
        if (!economy.has(player, bet)) {
            TextUtil.sendMessage(player, "&cYou do not have enough money for this bet!");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        economy.withdraw(player, bet);
        
        double winProb = getWinProbability(player, GameType.DICE_ROLL, bet);
        boolean win = random.nextDouble() < winProb;
        recordGamePlayed(player.getUniqueId());
        
        int roll;
        if (win) {
            roll = 6;
        } else {
            roll = random.nextInt(5) + 1; // 1 to 5
        }

        if (roll == 6) {
            double reward = bet * 5;
            economy.deposit(player, reward);
            TextUtil.sendMessage(player, "&6[Casino] &aYou rolled a &l6&a and won &e$" + reward + "&a!");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        } else {
            TextUtil.sendMessage(player, "&6[Casino] &cYou rolled a &e" + roll + "&c and lost your bet. You needed a 6.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
        }
    }
}
