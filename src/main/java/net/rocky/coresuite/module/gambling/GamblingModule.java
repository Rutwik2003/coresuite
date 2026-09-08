package net.rocky.coresuite.module.gambling;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.economy.EconomyManager;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.Module;
import net.rocky.coresuite.module.gambling.crash.CrashGUI;
import net.rocky.coresuite.module.gambling.gui.CasinoLeaderboardGUI;
import net.rocky.coresuite.module.gambling.gui.CasinoMainGUI;
import net.rocky.coresuite.module.gambling.gui.CasinoTOSGUI;
import net.rocky.coresuite.module.gambling.pvpcoinflip.CoinflipDuel;
import net.rocky.coresuite.module.gambling.pvpcoinflip.CoinflipListGUI;
import net.rocky.coresuite.module.gambling.pvpcoinflip.CoinflipManager;
import net.rocky.coresuite.module.gambling.pvpcoinflip.CoinflipRollGUI;
import net.rocky.coresuite.module.gambling.roulette.RouletteGUI;
import net.rocky.coresuite.module.gambling.slots.VegasSlotsGUI;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.*;

/**
 * Royal Casino 2.0 Module — PVP Coinflip, Crash, CS:GO Roulette, and Vegas Slots.
 */
public class GamblingModule extends Module implements CommandExecutor, TabCompleter, Listener {

    private final EconomyManager economy;
    private CasinoStatsManager statsManager;
    private CoinflipManager coinflipManager;
    private CoinflipRollGUI coinflipRollGUI;
    private CoinflipListGUI coinflipListGUI;
    private CrashGUI crashGUI;
    private RouletteGUI rouletteGUI;
    private VegasSlotsGUI slotsGUI;
    private CasinoMainGUI mainGUI;

    private List<String> acceptedTOS;

    public GamblingModule(CoreSuitePlugin plugin) {
        super(plugin, "Gambling", "gambling");
        this.economy = plugin.getEconomyManager();
    }

    @Override
    public void onEnable() {
        acceptedTOS = config.getStringList("tos-accepted");
        if (acceptedTOS == null) {
            acceptedTOS = new ArrayList<>();
        }

        // Initialize Managers & Sub-systems
        this.statsManager = new CasinoStatsManager(plugin);
        this.coinflipManager = new CoinflipManager(plugin, config, statsManager);
        this.coinflipRollGUI = new CoinflipRollGUI(plugin, coinflipManager);
        this.coinflipListGUI = new CoinflipListGUI(plugin, config, coinflipManager, coinflipRollGUI, statsManager);
        this.crashGUI = new CrashGUI(plugin, config, statsManager);
        this.rouletteGUI = new RouletteGUI(plugin, config, statsManager);
        this.slotsGUI = new VegasSlotsGUI(plugin, config, statsManager);

        this.mainGUI = new CasinoMainGUI(
            plugin, config, statsManager,
            coinflipListGUI, crashGUI, rouletteGUI, slotsGUI
        );

        // Register Commands
        PluginCommand gambleCmd = plugin.getCommand("gamble");
        if (gambleCmd != null) {
            gambleCmd.setExecutor(this);
            gambleCmd.setTabCompleter(this);
        }

        PluginCommand cfCmd = plugin.getCommand("cf");
        if (cfCmd != null) {
            cfCmd.setExecutor(this);
            cfCmd.setTabCompleter(this);
        }

        registerListener(this);

        // Schedule 60-second duel expiration task
        registerTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (coinflipManager != null) {
                coinflipManager.checkExpiredDuels();
            }
        }, 1200L, 1200L));
    }

    @Override
    public void onDisable() {
        if (acceptedTOS != null) {
            config.set("tos-accepted", acceptedTOS);
            saveConfig();
        }

        PluginCommand gambleCmd = plugin.getCommand("gamble");
        if (gambleCmd != null && gambleCmd.getExecutor() == this) {
            gambleCmd.setExecutor(null);
        }

        PluginCommand cfCmd = plugin.getCommand("cf");
        if (cfCmd != null && cfCmd.getExecutor() == this) {
            cfCmd.setExecutor(null);
        }

        if (statsManager != null) {
            statsManager.clearCache();
        }
    }

    public boolean hasAcceptedTOS(UUID uuid) {
        return acceptedTOS.contains(uuid.toString());
    }

    public void acceptTOS(UUID uuid) {
        if (!hasAcceptedTOS(uuid)) {
            acceptedTOS.add(uuid.toString());
            config.set("tos-accepted", acceptedTOS);
            Bukkit.getScheduler().runTaskAsynchronously(plugin, this::saveConfig);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        // 1-Click Disclaimer check
        if (!hasAcceptedTOS(player.getUniqueId())) {
            CasinoTOSGUI.open(plugin, player, p -> {
                acceptTOS(p.getUniqueId());
                routeCommand(p, label, args);
            });
            return true;
        }

        routeCommand(player, label, args);
        return true;
    }

    private void routeCommand(Player player, String label, String[] args) {
        String lower = label.toLowerCase();

        // Direct shortcut /cf or /coinflip
        if (lower.equals("cf") || lower.equals("coinflip")) {
            handleCoinflipCommand(player, args);
            return;
        }

        // /gamble or /casino
        if (args.length == 0) {
            mainGUI.open(player);
            return;
        }

        switch (args[0].toLowerCase()) {
            case "cf", "coinflip" -> handleCoinflipCommand(player, Arrays.copyOfRange(args, 1, args.length));
            case "crash" -> crashGUI.openBetSelect(player);
            case "roulette" -> rouletteGUI.openColorSelect(player);
            case "slots" -> slotsGUI.openBetSelect(player);
            case "top", "leaderboard" -> CasinoLeaderboardGUI.open(plugin, statsManager, player, () -> mainGUI.open(player));
            case "help" -> sendHelp(player);
            default -> mainGUI.open(player);
        }
    }

    private void handleCoinflipCommand(Player player, String[] args) {
        if (args.length == 0) {
            coinflipListGUI.open(player, 0);
            return;
        }

        if (args[0].equalsIgnoreCase("cancel")) {
            Optional<CoinflipDuel> open = coinflipManager.getPlayerOpenDuel(player.getUniqueId());
            if (open.isEmpty()) {
                TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cYou have no active coinflip duel to cancel.");
                return;
            }
            coinflipManager.cancelDuel(player, open.get().getId(), () -> {
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                TextUtil.sendMessage(player, "&8[&6&lCasino&8] &aYour coinflip duel was cancelled and escrow refunded.");
            }, err -> TextUtil.sendMessage(player, "&8[&6&lCasino&8] " + err));
            return;
        }

        if (args[0].equalsIgnoreCase("create")) {
            if (args.length < 3) {
                TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cUsage: &e/cf create <amount> <heads|tails>");
                return;
            }

            double amount;
            try {
                amount = Double.parseDouble(args[1].replace(",", "").replace("$", ""));
            } catch (NumberFormatException e) {
                TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cInvalid amount number.");
                return;
            }

            String sideStr = args[2].toLowerCase();
            boolean isHeads;
            if (sideStr.startsWith("h")) isHeads = true;
            else if (sideStr.startsWith("t")) isHeads = false;
            else {
                TextUtil.sendMessage(player, "&8[&6&lCasino&8] &cSide must be &eheads &cor &etails&c.");
                return;
            }

            coinflipManager.createDuel(player, amount, isHeads, duel -> {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                TextUtil.sendMessage(player, "&8[&6&lCasino&8] &aCoinflip duel posted! Other players can now accept it.");
            }, err -> TextUtil.sendMessage(player, "&8[&6&lCasino&8] " + err));
            return;
        }

        coinflipListGUI.open(player, 0);
    }

    private void sendHelp(Player player) {
        TextUtil.sendMessage(player, "&8&m────────────────────────────────────────");
        TextUtil.sendMessage(player, "&6&l👑 ROYAL CASINO — COMMANDS");
        TextUtil.sendMessage(player, "&8  ▸ &e/gamble &7or &e/casino &8— &fOpen the Casino hub");
        TextUtil.sendMessage(player, "&8  ▸ &e/cf &8— &fOpen PVP Coinflip board");
        TextUtil.sendMessage(player, "&8  ▸ &e/cf create <amount> <heads|tails> &8— &fQuick create duel");
        TextUtil.sendMessage(player, "&8  ▸ &e/cf cancel &8— &fCancel your active duel");
        TextUtil.sendMessage(player, "&8  ▸ &e/gamble crash &8— &fPlay The Crash");
        TextUtil.sendMessage(player, "&8  ▸ &e/gamble roulette &8— &fPlay CS:GO Roulette");
        TextUtil.sendMessage(player, "&8  ▸ &e/gamble slots &8— &fPlay Vegas Slots");
        TextUtil.sendMessage(player, "&8  ▸ &e/gamble top &8— &fView High Rollers Leaderboard");
        TextUtil.sendMessage(player, "&8&m────────────────────────────────────────");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase();
        if (cmd.equals("cf") || cmd.equals("coinflip")) {
            if (args.length == 1) {
                return filterPrefix(List.of("create", "cancel"), args[0]);
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
                return filterPrefix(List.of("5000", "10000", "25000", "50000", "100000"), args[1]);
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
                return filterPrefix(List.of("heads", "tails"), args[2]);
            }
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return filterPrefix(List.of("cf", "crash", "roulette", "slots", "top", "help"), args[0]);
        }
        return Collections.emptyList();
    }

    private List<String> filterPrefix(List<String> options, String prefix) {
        String lower = prefix.toLowerCase();
        return options.stream().filter(s -> s.toLowerCase().startsWith(lower)).toList();
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String msg = event.getMessage().trim();

        if (coinflipListGUI.handleChatBet(player, msg)
            || crashGUI.handleChatBet(player, msg)
            || rouletteGUI.handleChatBet(player, msg)
            || slotsGUI.handleChatBet(player, msg)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (statsManager != null) statsManager.unloadPlayer(uuid);
        if (coinflipListGUI != null) coinflipListGUI.cleanup(uuid);
        if (crashGUI != null) crashGUI.cleanup(uuid);
        if (rouletteGUI != null) rouletteGUI.cleanup(uuid);
        if (slotsGUI != null) slotsGUI.cleanup(uuid);
    }
}
