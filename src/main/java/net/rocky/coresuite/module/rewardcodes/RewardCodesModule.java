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
import java.util.UUID;
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
                "ip TEXT, " +
                "PRIMARY KEY(uuid, code))";
        plugin.getDatabaseManager().execute(sql);

        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                boolean hasIp = false;
                if (plugin.getDatabaseManager().isMySQL()) {
                    try (PreparedStatement ps = conn.prepareStatement("SHOW COLUMNS FROM coresuite_reward_codes LIKE 'ip'");
                         ResultSet rs = ps.executeQuery()) {
                        hasIp = rs.next();
                    }
                } else {
                    try (PreparedStatement ps = conn.prepareStatement("PRAGMA table_info(coresuite_reward_codes)");
                         ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            if ("ip".equalsIgnoreCase(rs.getString("name"))) {
                                hasIp = true;
                                break;
                            }
                        }
                    }
                }
                if (!hasIp) {
                    try (PreparedStatement alter = conn.prepareStatement("ALTER TABLE coresuite_reward_codes ADD COLUMN ip TEXT")) {
                        alter.executeUpdate();
                    }
                }
            } catch (SQLException ignored) {
            }
        });
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

    private boolean hasIpRedeemed(String ip, String code) {
        if (ip == null || ip.isEmpty()) return false;
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM coresuite_reward_codes WHERE ip = ? AND code = ?")) {
            stmt.setString(1, ip);
            stmt.setString(2, code.toLowerCase());
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to check reward IP status", e);
            return false;
        }
    }

    private void markRedeemed(OfflinePlayer player, String code, String ip) {
        String sql = "REPLACE INTO coresuite_reward_codes (uuid, player_name, code, time, ip) VALUES (?, ?, ?, ?, ?)";
        plugin.getDatabaseManager().execute(sql, player.getUniqueId().toString(), player.getName(), code.toLowerCase(), System.currentTimeMillis(), ip);
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

    private final java.util.Set<UUID> pendingRedemptions = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private void giveCustomCodeReward(OfflinePlayer player, String codeKey) {
        List<String> commands = config.getStringList("codes." + codeKey + ".commands");
        for (String cmd : commands) {
            String parsed = cmd.replace("%player%", player.getName() != null ? player.getName() : "");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String expectedCode = config.getString("code", "M7X2Q9");
        if (command.getName().equalsIgnoreCase("rewardadmin") || command.getName().equalsIgnoreCase("rewardcodes")) {
            String[] editArgs = args.length > 0 && args[0].equalsIgnoreCase("admin")
                    ? java.util.Arrays.copyOfRange(args, 1, args.length) : args;
            if (editArgs.length > 0 && RewardCodeEditor.ACTIONS.contains(editArgs[0].toLowerCase(java.util.Locale.ROOT))) {
                if (!sender.hasPermission("coresuite.rewardcodes.admin") && !sender.hasPermission("coresuite.reward.admin")) {
                    TextUtil.sendMessage(sender, "&cYou don't have permission to manage reward codes."); return true;
                }
                String previous = config.saveToString();
                try {
                    var result = RewardCodeEditor.edit(config, editArgs);
                    if (result.changed()) config.save(new java.io.File(plugin.getDataFolder(), "modules/reward-codes.yml"));
                    result.messages().forEach(message -> TextUtil.sendMessage(sender, message));
                } catch (IllegalArgumentException error) {
                    TextUtil.sendMessage(sender, "&c" + error.getMessage());
                } catch (java.io.IOException error) {
                    try { config.loadFromString(previous); } catch (org.bukkit.configuration.InvalidConfigurationException impossible) { throw new IllegalStateException(impossible); }
                    plugin.getLogger().log(Level.SEVERE, "Could not save reward code edit", error);
                    TextUtil.sendMessage(sender, "&cCould not save this edit; changes were rolled back.");
                }
                return true;
            }
        }


        if (command.getName().equalsIgnoreCase("reward")) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            
            if (!player.hasPermission("coresuite.reward.use")) {
                TextUtil.sendMessage(player, "&cYou don't have permission to use this command.");
                return true;
            }

            if (args.length == 0) {
                TextUtil.sendMessage(player, "&cInvalid redeem code! Usage: /reward <code>");
                return true;
            }

            String inputCode = args[0];
            String matchedCode = null;
            boolean isCustom = false;

            if (inputCode.equalsIgnoreCase(expectedCode)) {
                matchedCode = expectedCode;
            } else if (config.isConfigurationSection("codes")) {
                for (String codeKey : config.getConfigurationSection("codes").getKeys(false)) {
                    if (codeKey.equalsIgnoreCase(inputCode)) {
                        matchedCode = codeKey;
                        isCustom = true;
                        break;
                    }
                }
            }

            if (matchedCode == null) {
                TextUtil.sendMessage(player, "&cInvalid redeem code!");
                return true;
            }

            final String finalCode = matchedCode;
            String rewardPath = RewardCodeEditor.prefix(config, matchedCode);
            if (!config.getBoolean(rewardPath + "enabled", true)) {
                TextUtil.sendMessage(player, "&cThis reward code is currently disabled."); return true;
            }
            final List<String> rewardCommands = List.copyOf(config.getStringList(rewardPath + "commands"));
            final double money = isCustom ? 0 : config.getDouble("money-reward", 0);
            final List<String> successMessages = isCustom ? List.of() : List.copyOf(config.getStringList("success-messages"));
            if (rewardCommands.stream().allMatch(String::isBlank) && !(Double.isFinite(money) && money > 0)) {
                TextUtil.sendMessage(player, "&cThis code has no rewards configured yet."); return true;
            }
            final int maxUses = Math.max(0, config.getInt(rewardPath + "max-uses", 0));
            if (!pendingRedemptions.add(player.getUniqueId())) {
                TextUtil.sendMessage(player, "&eYour reward redemption is currently processing..."); return true;
            }
            String playerIp = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "";
            boolean bypassIp = player.hasPermission("coresuite.reward.bypassip");
            UUID recipient = player.getUniqueId();
            String playerName = player.getName();
            plugin.getDatabaseManager().executeAsync(() -> {
                try {
                    String rejection;
                    try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                        rejection = RewardCodeRedemption.reserve(conn, recipient, playerName, finalCode, playerIp, bypassIp, maxUses);
                    }
                    if (rejection != null) {
                        Bukkit.getScheduler().runTask(plugin, () -> TextUtil.sendMessage(player, "&c" + rejection));
                        pendingRedemptions.remove(recipient);
                        return;
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            boolean delivered = true;
                            if (Double.isFinite(money) && money > 0) delivered = plugin.getEconomyManager().deposit(player, money);
                            for (String rewardCommand : rewardCommands) {
                                if (rewardCommand.isBlank()) continue;
                                String parsed = rewardCommand.replace("%player_name%", playerName).replace("%player%", playerName);
                                if (parsed.startsWith("/")) parsed = parsed.substring(1);
                                try { if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed)) delivered = false; }
                                catch (RuntimeException error) { delivered = false; plugin.getLogger().log(Level.SEVERE, "Reward command failed for " + finalCode + " / " + recipient, error); }
                            }
                            if (delivered) {
                                TextUtil.sendMessage(player, "&aSuccessfully redeemed code &e" + finalCode + "&a!");
                                successMessages.forEach(message -> TextUtil.sendMessage(player, message));
                            }
                            else {
                                plugin.getLogger().severe("Reward delivery needs review: code=" + finalCode + " player=" + recipient + ". Some rewards may have delivered; do not blindly retry.");
                                TextUtil.sendMessage(player, "&eYour code was accepted, but part of its reward failed. Please contact an admin.");
                            }
                        } finally { pendingRedemptions.remove(recipient); }
                    });
                } catch (SQLException | RuntimeException error) {
                    pendingRedemptions.remove(recipient);
                    plugin.getLogger().log(Level.SEVERE, "Reward redemption could not complete for " + recipient, error);
                    if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> TextUtil.sendMessage(player, "&cReward processing failed. Please contact an admin if it persists."));
                }
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
                if (sub.equals("unclaim")) {
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
        
        if (command.getName().equalsIgnoreCase("rewardadmin") || command.getName().equalsIgnoreCase("rewardcodes")) {
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
        TextUtil.sendMessage(sender, "&6Reward code management &7(commands run as console; use %player%)");
        TextUtil.sendMessage(sender, "&e/rewardadmin create <code> [max-uses] [first command...]");
        TextUtil.sendMessage(sender, "&e/rewardadmin codes &7| &e/rewardadmin info <code>");
        TextUtil.sendMessage(sender, "&e/rewardadmin addcommand <code> <command...>");
        TextUtil.sendMessage(sender, "&e/rewardadmin setcommand <code> <number> <command...>");
        TextUtil.sendMessage(sender, "&e/rewardadmin removecommand <code> <number>");
        TextUtil.sendMessage(sender, "&e/rewardadmin enable|disable <code>");
        TextUtil.sendMessage(sender, "&e/rewardadmin setlimit <code> <max-uses> &7(0 = unlimited)");
        TextUtil.sendMessage(sender, "&7/rewardadmin reset <player>");
        TextUtil.sendMessage(sender, "&7/rewardadmin check <player>");
        TextUtil.sendMessage(sender, "&7/rewardadmin give <player>");
        TextUtil.sendMessage(sender, "&7/rewardadmin list");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if ((command.getName().equalsIgnoreCase("rewardadmin") || command.getName().equalsIgnoreCase("rewardcodes"))
                && (sender.hasPermission("coresuite.reward.admin") || sender.hasPermission("coresuite.rewardcodes.admin"))) {
            String[] editArgs = args.length > 0 && args[0].equalsIgnoreCase("admin") ? java.util.Arrays.copyOfRange(args, 1, args.length) : args;
            if (editArgs.length == 1) org.bukkit.util.StringUtil.copyPartialMatches(editArgs[0], RewardCodeEditor.ACTIONS, completions);
            if (editArgs.length == 2 && RewardCodeEditor.ACTIONS.contains(editArgs[0].toLowerCase(java.util.Locale.ROOT)) && !editArgs[0].equalsIgnoreCase("create")) {
                org.bukkit.util.StringUtil.copyPartialMatches(editArgs[1], RewardCodeEditor.codes(config), completions);
                return completions;
            }
            if (editArgs.length == 3 && (editArgs[0].equalsIgnoreCase("removecommand") || editArgs[0].equalsIgnoreCase("setcommand"))) {
                String code = RewardCodeEditor.resolve(config, editArgs[1]);
                if (code != null) for (int i = 1; i <= config.getStringList(RewardCodeEditor.prefix(config, code) + "commands").size(); i++)
                    if (String.valueOf(i).startsWith(editArgs[2])) completions.add(String.valueOf(i));
                return completions;
            }
        }
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
        return completions.stream().distinct().toList();
    }
}
