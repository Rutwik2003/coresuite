package net.rocky.coresuite.module.creator;

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
import java.security.SecureRandom;

public class CreatorModule extends Module implements CommandExecutor, TabCompleter {

    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private final SecureRandom random = new SecureRandom();

    public CreatorModule(CoreSuitePlugin plugin) {
        super(plugin, "Creator", "creator");
    }

    @Override
    public void onEnable() {
        initDatabase();

        PluginCommand command = plugin.getCommand("creator");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    @Override
    public void onDisable() {
        PluginCommand command = plugin.getCommand("creator");
        if (command != null && command.getExecutor() == this) {
            command.setExecutor(null);
            command.setTabCompleter(null);
        }
    }

    private void initDatabase() {
        plugin.getDatabaseManager().execute(
            "CREATE TABLE IF NOT EXISTS coresuite_creators (" +
            "uuid TEXT PRIMARY KEY, " +
            "code TEXT UNIQUE)"
        );
        plugin.getDatabaseManager().execute(
            "CREATE TABLE IF NOT EXISTS coresuite_creator_redeems (" +
            "viewer_uuid TEXT, " +
            "code TEXT, " +
            "PRIMARY KEY(viewer_uuid, code))"
        );
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
        }
        return sb.toString();
    }

    private String getCreatorCode(UUID uuid) {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT code FROM coresuite_creators WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("code");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to fetch creator code", e);
        }
        return null;
    }
    
    private UUID getCreatorByCode(String code) {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT uuid FROM coresuite_creators WHERE code = ? COLLATE NOCASE")) {
            stmt.setString(1, code);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return UUID.fromString(rs.getString("uuid"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to check creator by code", e);
        }
        return null;
    }

    private void setCreatorCode(UUID uuid, String code) {
        plugin.getDatabaseManager().execute("REPLACE INTO coresuite_creators (uuid, code) VALUES (?, ?)", uuid.toString(), code.toUpperCase());
    }

    private void removeCreatorCode(UUID uuid) {
        plugin.getDatabaseManager().execute("DELETE FROM coresuite_creators WHERE uuid = ?", uuid.toString());
    }

    private boolean hasRedeemed(UUID viewerId, String code) {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM coresuite_creator_redeems WHERE viewer_uuid = ? AND code = ? COLLATE NOCASE")) {
            stmt.setString(1, viewerId.toString());
            stmt.setString(2, code);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to check redeem status", e);
            return false;
        }
    }

    private void markRedeemed(UUID viewerId, String code) {
        plugin.getDatabaseManager().execute("INSERT OR IGNORE INTO coresuite_creator_redeems (viewer_uuid, code) VALUES (?, ?)", viewerId.toString(), code.toUpperCase());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("grant")) {
            if (!sender.hasPermission("coresuite.creator.admin")) {
                TextUtil.sendMessage(sender, "&cYou don't have permission to use this command.");
                return true;
            }

            if (args.length < 2) {
                TextUtil.sendMessage(sender, "&cUsage: /creator grant <player> [time e.g. 1d, 1w, 30d]");
                return true;
            }

            String targetName = args[1];
            String time = args.length > 2 ? args[2] : "30d"; // Default to 30d

            Player target = Bukkit.getPlayer(targetName);
            @SuppressWarnings("deprecation")
            OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
            
            // Run commands
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eco give " + targetName + " 100000");
            
            // Always run addtemp as per request
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + targetName + " parent addtemp creator " + time);
            
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "crate key give " + targetName + " common 5");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "crate key give " + targetName + " uncommon 3");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "crate key give " + targetName + " rare 2");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "crate key give " + targetName + " enchanter 2");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "crate key give " + targetName + " potion 2");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "crate key give " + targetName + " spawner 1");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "crate key give " + targetName + " elite 1");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "uc give key 10 " + targetName);

            TextUtil.sendMessage(sender, "&aSuccessfully granted Creator rank and perks to &f" + targetName + "&a for " + time + "!");
            
            if (target != null) {
                TextUtil.sendMessage(target, "&aYou have been granted the &dCreator &arank and perks!");
            }

            plugin.getDatabaseManager().executeAsync(() -> {
                if (getCreatorCode(offlineTarget.getUniqueId()) == null) {
                    String newCode = targetName.toUpperCase();
                    // Just in case the name is taken as code (unlikely but check)
                    if (getCreatorByCode(newCode) != null) {
                        newCode = generateCode();
                    }
                    setCreatorCode(offlineTarget.getUniqueId(), newCode);
                    if (target != null) {
                        final String finalCode = newCode;
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            TextUtil.sendMessage(target, "&aYour unique creator code is: &e" + finalCode);
                            TextUtil.sendMessage(target, "&aShare this with your viewers! They can use &e/creator redeem " + finalCode);
                        });
                    }
                }
            });

            return true;
        }

        if (sub.equals("setcode")) {
            if (!sender.hasPermission("coresuite.creator.admin")) {
                TextUtil.sendMessage(sender, "&cYou don't have permission to use this command.");
                return true;
            }

            if (args.length < 3) {
                TextUtil.sendMessage(sender, "&cUsage: /creator setcode <player> <code>");
                return true;
            }

            String targetName = args[1];
            String newCode = args[2].toUpperCase();
            
            @SuppressWarnings("deprecation")
            OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
            
            plugin.getDatabaseManager().executeAsync(() -> {
                if (getCreatorByCode(newCode) != null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        TextUtil.sendMessage(sender, "&cThat code is already in use by another creator.");
                    });
                    return;
                }
                
                setCreatorCode(offlineTarget.getUniqueId(), newCode);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    TextUtil.sendMessage(sender, "&aSuccessfully set " + targetName + "'s creator code to &e" + newCode);
                    Player target = Bukkit.getPlayer(targetName);
                    if (target != null) {
                        TextUtil.sendMessage(target, "&aYour creator code has been changed to: &e" + newCode);
                    }
                });
            });
            return true;
        }

        if (sub.equals("remove")) {
            if (!sender.hasPermission("coresuite.creator.admin")) {
                TextUtil.sendMessage(sender, "&cYou don't have permission to use this command.");
                return true;
            }

            if (args.length < 2) {
                TextUtil.sendMessage(sender, "&cUsage: /creator remove <player>");
                return true;
            }

            String targetName = args[1];
            @SuppressWarnings("deprecation")
            OfflinePlayer offlineTarget = Bukkit.getOfflinePlayer(targetName);
            
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + targetName + " parent removetemp creator");
            removeCreatorCode(offlineTarget.getUniqueId());
            
            TextUtil.sendMessage(sender, "&aSuccessfully revoked Creator rank and deleted the creator code for &f" + targetName + "&a.");
            
            Player target = Bukkit.getPlayer(targetName);
            if (target != null) {
                TextUtil.sendMessage(target, "&cYour Creator rank and code have been removed.");
            }
            return true;
        }

        if (sub.equals("code")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Players only.");
                return true;
            }
            Player player = (Player) sender;
            
            plugin.getDatabaseManager().executeAsync(() -> {
                String code = getCreatorCode(player.getUniqueId());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (code == null) {
                        TextUtil.sendMessage(player, "&cYou do not have a creator code. You must be granted Creator rank first.");
                    } else {
                        TextUtil.sendMessage(player, "&aYour creator code is: &e" + code);
                        TextUtil.sendMessage(player, "&aViewers can use &e/creator redeem " + code + " &ato get rewards!");
                    }
                });
            });
            return true;
        }

        if (sub.equals("redeem")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Players only.");
                return true;
            }
            Player player = (Player) sender;
            
            if (args.length < 2) {
                TextUtil.sendMessage(player, "&cUsage: /creator redeem <code>");
                return true;
            }
            
            String code = args[1].toUpperCase();
            
            plugin.getDatabaseManager().executeAsync(() -> {
                UUID creatorId = getCreatorByCode(code);
                if (creatorId == null) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        TextUtil.sendMessage(player, "&cInvalid creator code!");
                    });
                    return;
                }
                
                if (creatorId.equals(player.getUniqueId())) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        TextUtil.sendMessage(player, "&cYou cannot redeem your own creator code!");
                    });
                    return;
                }
                
                if (hasRedeemed(player.getUniqueId(), code)) {
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        TextUtil.sendMessage(player, "&cYou have already redeemed this creator's code!");
                    });
                    return;
                }
                
                markRedeemed(player.getUniqueId(), code);
                
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Give viewer perks
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "eco give " + player.getName() + " 15000");
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "crate key give " + player.getName() + " uncommon 2");
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "crate key give " + player.getName() + " common 2");
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "uc give key 2 " + player.getName());
                    
                    TextUtil.sendMessage(player, "&aSuccessfully redeemed creator code &e" + code + "&a!");
                    TextUtil.sendMessage(player, "&7Received: &f$15,000&7, &f2x Uncommon Key&7, &f2x Common Key&7, &f2x UltraCosmetic Key");
                    
                    OfflinePlayer creator = Bukkit.getOfflinePlayer(creatorId);
                    if (creator.isOnline() && creator.getPlayer() != null) {
                        TextUtil.sendMessage(creator.getPlayer(), "&d" + player.getName() + " &ajust redeemed your creator code!");
                    }
                });
            });
            return true;
        }

        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        TextUtil.sendMessage(sender, "&e--- Creator Commands ---");
        if (sender.hasPermission("coresuite.creator.admin")) {
            TextUtil.sendMessage(sender, "&7/creator grant <player> [time] &f- Grant creator rank & perks");
            TextUtil.sendMessage(sender, "&7/creator setcode <player> <code> &f- Set a specific code for a creator");
            TextUtil.sendMessage(sender, "&7/creator remove <player> &f- Revoke creator rank & code");
        }
        TextUtil.sendMessage(sender, "&7/creator code &f- View your creator code");
        TextUtil.sendMessage(sender, "&7/creator redeem <code> &f- Redeem a creator's code");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (sender.hasPermission("coresuite.creator.admin")) {
                options.add("grant");
                options.add("setcode");
                options.add("remove");
            }
            options.add("code");
            options.add("redeem");
            org.bukkit.util.StringUtil.copyPartialMatches(args[0], options, completions);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("grant") || args[0].equalsIgnoreCase("setcode") || args[0].equalsIgnoreCase("remove")) && sender.hasPermission("coresuite.creator.admin")) {
            List<String> players = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) players.add(p.getName());
            org.bukkit.util.StringUtil.copyPartialMatches(args[1], players, completions);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("grant") && sender.hasPermission("coresuite.creator.admin")) {
            List<String> times = java.util.Arrays.asList("30d", "1w", "1m", "1d");
            org.bukkit.util.StringUtil.copyPartialMatches(args[2], times, completions);
        }
        return completions;
    }
}
