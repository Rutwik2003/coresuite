package net.rocky.coresuite.module.rewardcodes;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

public class RewardCodesModule extends Module implements CommandExecutor, TabCompleter {

    public RewardCodesModule(CoreSuitePlugin plugin) {
        super(plugin, "RewardCodes", "reward-codes");
    }

    @Override
    public void onEnable() {
        initDatabase();

        PluginCommand rewardCommand = plugin.getCommand("reward");
        if (rewardCommand != null) {
            rewardCommand.setExecutor(this);
            rewardCommand.setTabCompleter(this);
        }

        PluginCommand rcCommand = plugin.getCommand("rewardcodes");
        if (rcCommand != null) {
            rcCommand.setExecutor(this);
            rcCommand.setTabCompleter(this);
        }

        PluginCommand adminCommand = plugin.getCommand("rewardadmin");
        if (adminCommand != null) {
            adminCommand.setExecutor(this);
            adminCommand.setTabCompleter(this);
        }
    }

    @Override
    public void onDisable() {
        PluginCommand rcCommand = plugin.getCommand("rewardcodes");
        if (rcCommand != null && rcCommand.getExecutor() == this) {
            rcCommand.setExecutor(null);
            rcCommand.setTabCompleter(null);
        }

        PluginCommand rewardCommand = plugin.getCommand("reward");
        if (rewardCommand != null && rewardCommand.getExecutor() == this) {
            rewardCommand.setExecutor(null);
            rewardCommand.setTabCompleter(null);
        }

        PluginCommand adminCommand = plugin.getCommand("rewardadmin");
        if (adminCommand != null && adminCommand.getExecutor() == this) {
            adminCommand.setExecutor(null);
            adminCommand.setTabCompleter(null);
        }
    }

    private void initDatabase() {
        String sql = "CREATE TABLE IF NOT EXISTS coresuite_reward_codes (" +
                "uuid TEXT, " +
                "player_name TEXT, " +
                "code TEXT, " +
                "time INTEGER, " +
                "PRIMARY KEY(uuid, code))";
        plugin.getDatabaseManager().execute(sql);
    }

    private boolean hasRedeemed(OfflinePlayer player, String code) {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM coresuite_reward_codes WHERE uuid = ? AND code = ?")) {
            stmt.setString(1, player.getUniqueId().toString());
            stmt.setString(2, code.toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to check reward status", e);
            return false;
        }
    }

    private void markRedeemed(OfflinePlayer player, String code) {
        String sql = "INSERT OR IGNORE INTO coresuite_reward_codes (uuid, player_name, code, time) VALUES (?, ?, ?, ?)";
        plugin.getDatabaseManager().execute(sql, player.getUniqueId().toString(), player.getName(), code.toLowerCase(), System.currentTimeMillis());
    }

    private void removeRedeemed(OfflinePlayer player, String code) {
        String sql = "DELETE FROM coresuite_reward_codes WHERE uuid = ? AND code = ?";
        plugin.getDatabaseManager().execute(sql, player.getUniqueId().toString(), code.toLowerCase());
    }

    private void giveReward(OfflinePlayer player) {
        double money = config.getDouble("money-reward", 0);
        if (money > 0) {
            plugin.getEconomyManager().deposit(player, money);
        }

        List<String> commands = config.getStringList("commands");
        for (String cmd : commands) {
            String parsed = cmd.replace("%player%", player.getName() != null ? player.getName() : "");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String expectedCode = config.getString("code", "M7X2Q9");

        if (command.getName().equalsIgnoreCase("reward")) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            
            if (!player.hasPermission("coresuite.reward.use")) {
                TextUtil.sendMessage(player, "&cYou don't have permission to use this command.");
                return true;
            }

            if (args.length == 0 || !args[0].equalsIgnoreCase(expectedCode)) {
                TextUtil.sendMessage(player, "&cInvalid redeem code!");
                return true;
            }

            plugin.getDatabaseManager().executeAsync(() -> {
                if (hasRedeemed(player, expectedCode)) {
                    Bukkit.getScheduler().runTask(plugin, () -> 
                        TextUtil.sendMessage(player, "&cYou have already redeemed this code!"));
                    return;
                }

                markRedeemed(player, expectedCode);
                
                Bukkit.getScheduler().runTask(plugin, () -> {
                    giveReward(player);
                    for (String msg : config.getStringList("success-messages")) {
                        TextUtil.sendMessage(player, msg);
                    }
                });
            });
            return true;
        }

        if (command.getName().equalsIgnoreCase("rewardcodes") || command.getName().equalsIgnoreCase("rewardadmin")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
                if (!sender.hasPermission("coresuite.rewardcodes.admin")) {
                    TextUtil.sendMessage(sender, "&cYou don't have permission to use this command.");
                    return true;
                }
                if (args.length < 2) {
                    TextUtil.sendMessage(sender, "&cUsage: /rewardcodes admin <create|unclaim>");
                    return true;
                }
                String sub = args[1].toLowerCase();
                if (sub.equals("create")) {
                    if (args.length < 5) {
                        TextUtil.sendMessage(sender, "&cUsage: /rewardcodes admin create <code> <max_uses> <commands...>");
                        return true;
                    }
                    String code = args[2];
                    int maxUses;
                    try {
                        maxUses = Integer.parseInt(args[3]);
                    } catch (NumberFormatException e) {
                        TextUtil.sendMessage(sender, "&cInvalid max_uses number.");
                        return true;
                    }
                    StringBuilder cmdBuilder = new StringBuilder();
                    for (int i = 4; i < args.length; i++) {
                        cmdBuilder.append(args[i]).append(" ");
                    }
                    String cmdStr = cmdBuilder.toString().trim();
                    
                    config.set("codes." + code + ".max-uses", maxUses);
                    config.set("codes." + code + ".commands", java.util.Collections.singletonList(cmdStr));
                    saveConfig();
                    
                    TextUtil.sendMessage(sender, "&aCreated reward code " + code + " with max uses " + maxUses + ".");
                    return true;
                } else if (sub.equals("unclaim")) {
                    if (args.length < 4) {
                        TextUtil.sendMessage(sender, "&cUsage: /rewardcodes admin unclaim <player> <code>");
                        return true;
                    }
                    @SuppressWarnings("deprecation")
                    OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
                    String code = args[3];
                    removeRedeemed(target, code);
                    TextUtil.sendMessage(sender, "&aRemoved redemption record for " + target.getName() + " on code " + code + ".");
                    return true;
                }
            }
        }
        
        if (command.getName().equalsIgnoreCase("rewardadmin")) {
            if (!sender.hasPermission("coresuite.rewardcodes.admin") && !sender.hasPermission("coresuite.reward.admin")) {
                TextUtil.sendMessage(sender, "&cYou don't have permission to use this command.");
                return true;
            }

            if (args.length == 0) {
                sendAdminHelp(sender);
                return true;
            }

            String sub = args[0].toLowerCase();

            if (sub.equals("reset")) {
                if (args.length < 2) {
                    TextUtil.sendMessage(sender, "&cUsage: /rewardadmin reset <player>");
                    return true;
                }
                @SuppressWarnings("deprecation")
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                removeRedeemed(target, expectedCode);
                TextUtil.sendMessage(sender, "&a" + target.getName() + "'s redeem status has been reset. They can use /reward again.");
                return true;
            }

            if (sub.equals("check")) {
                if (args.length < 2) {
                    TextUtil.sendMessage(sender, "&cUsage: /rewardadmin check <player>");
                    return true;
                }
                @SuppressWarnings("deprecation")
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                if (hasRedeemed(target, expectedCode)) {
                    TextUtil.sendMessage(sender, "&e" + target.getName() + " &fhas &calready redeemed &fthe reward.");
                } else {
                    TextUtil.sendMessage(sender, "&e" + target.getName() + " &fhas &anot yet redeemed &fthe reward.");
                }
                return true;
            }

            if (sub.equals("give")) {
                if (args.length < 2) {
                    TextUtil.sendMessage(sender, "&cUsage: /rewardadmin give <player>");
                    return true;
                }
                @SuppressWarnings("deprecation")
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                giveReward(target);
                TextUtil.sendMessage(sender, "&aForce-gave the reward to " + target.getName() + " (redeem status unaffected).");
                return true;
            }

            if (sub.equals("list")) {
                TextUtil.sendMessage(sender, "&e--- Redeemed Players ---");
                // Fetch list of players who redeemed the current code
                plugin.getDatabaseManager().executeAsync(() -> {
                    try (Connection conn = plugin.getDatabaseManager().getConnection();
                         PreparedStatement stmt = conn.prepareStatement("SELECT player_name FROM coresuite_reward_codes WHERE code = ?")) {
                        stmt.setString(1, expectedCode.toLowerCase());
                        try (ResultSet rs = stmt.executeQuery()) {
                            List<String> names = new ArrayList<>();
                            while (rs.next()) {
                                names.add(rs.getString("player_name"));
                            }
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                for (String n : names) {
                                    TextUtil.sendMessage(sender, "&7- &f" + n);
                                }
                            });
                        }
                    } catch (SQLException e) {
                        plugin.getLogger().log(Level.SEVERE, "Failed to list rewards", e);
                    }
                });
                return true;
            }

            sendAdminHelp(sender);
            return true;
        }

        return false;
    }

    private void sendAdminHelp(CommandSender sender) {
        TextUtil.sendMessage(sender, "&cUnknown subcommand. Usage:");
        TextUtil.sendMessage(sender, "&7/rewardadmin reset <player>");
        TextUtil.sendMessage(sender, "&7/rewardadmin check <player>");
        TextUtil.sendMessage(sender, "&7/rewardadmin give <player>");
        TextUtil.sendMessage(sender, "&7/rewardadmin list");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if ((command.getName().equalsIgnoreCase("rewardadmin") || command.getName().equalsIgnoreCase("rewardcodes")) && (sender.hasPermission("coresuite.reward.admin") || sender.hasPermission("coresuite.rewardcodes.admin"))) {
            if (args.length == 1) {
                org.bukkit.util.StringUtil.copyPartialMatches(args[0], java.util.Arrays.asList("reset", "check", "give", "list", "admin"), completions);
            } else if (args.length == 2 && args[0].equalsIgnoreCase("admin")) {
                org.bukkit.util.StringUtil.copyPartialMatches(args[1], java.util.Arrays.asList("create", "unclaim"), completions);
            } else if (args.length == 2 && !args[0].equalsIgnoreCase("list") && !args[0].equalsIgnoreCase("admin")) {
                List<String> players = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) players.add(p.getName());
                org.bukkit.util.StringUtil.copyPartialMatches(args[1], players, completions);
            } else if (args.length == 3 && args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("unclaim")) {
                List<String> players = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) players.add(p.getName());
                org.bukkit.util.StringUtil.copyPartialMatches(args[2], players, completions);
            }
        }
        return completions;
    }
}
