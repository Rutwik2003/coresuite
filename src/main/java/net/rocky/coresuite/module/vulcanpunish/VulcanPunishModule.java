package net.rocky.coresuite.module.vulcanpunish;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class VulcanPunishModule extends Module implements Listener, CommandExecutor, TabCompleter {

    private final Set<UUID> bypassCache = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public VulcanPunishModule(CoreSuitePlugin plugin) {
        super(plugin, "VulcanPunish", "vulcan-punish");
    }

    @Override
    public void onEnable() {
        registerListener(this);
        initDatabase();
        loadBypassCache();

        String[] commands = {"vulcanpunish", "vulcanwarn", "vulcancheck", "vulcanhistory", "vulcanreset", "vulcansetoffenses", "vulcanbypass"};
        for (String cmd : commands) {
            if (plugin.getCommand(cmd) != null) {
                plugin.getCommand(cmd).setExecutor(this);
                plugin.getCommand(cmd).setTabCompleter(this);
            }
        }
        
        // Dynamically register VulcanFlagEvent listener using reflection
        try {
            Class<? extends org.bukkit.event.Event> eventClass = Class.forName("me.frep.vulcan.api.event.VulcanFlagEvent").asSubclass(org.bukkit.event.Event.class);
            Bukkit.getPluginManager().registerEvent(eventClass, this, org.bukkit.event.EventPriority.NORMAL, (listener, event) -> {
                if (eventClass.isInstance(event)) {
                    try {
                        Player player = (Player) eventClass.getMethod("getPlayer").invoke(event);
                        if (isBypassed(player.getUniqueId())) {
                            if (event instanceof org.bukkit.event.Cancellable) {
                                ((org.bukkit.event.Cancellable) event).setCancelled(true);
                            }
                        }
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.SEVERE, "Failed to handle VulcanFlagEvent via reflection", e);
                    }
                }
            }, plugin);
            plugin.getLogger().info("Successfully hooked into Vulcan API.");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().warning("Vulcan API not found. Vulcan bypasses will not function.");
        }
    }

    @Override
    public void onDisable() {
        String[] commands = {"vulcanpunish", "vulcanwarn", "vulcancheck", "vulcanhistory", "vulcanreset", "vulcansetoffenses", "vulcanbypass"};
        for (String cmd : commands) {
            if (plugin.getCommand(cmd) != null && plugin.getCommand(cmd).getExecutor() == this) {
                plugin.getCommand(cmd).setExecutor(null);
                plugin.getCommand(cmd).setTabCompleter(null);
            }
        }
        bypassCache.clear();
    }

    private void initDatabase() {
        plugin.getDatabaseManager().execute(
            "CREATE TABLE IF NOT EXISTS coresuite_vulcan_records (" +
            "uuid TEXT PRIMARY KEY, " +
            "player_name TEXT, " +
            "offenses INTEGER DEFAULT 0, " +
            "last_punish BIGINT DEFAULT 0, " +
            "cooldown BIGINT DEFAULT 0, " +
            "bypassed BOOLEAN DEFAULT 0)"
        );
        plugin.getDatabaseManager().execute(
            "CREATE TABLE IF NOT EXISTS coresuite_vulcan_history (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "uuid TEXT, " +
            "time BIGINT, " +
            "tier INTEGER, " +
            "action TEXT)"
        );
    }

    private void loadBypassCache() {
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT uuid FROM coresuite_vulcan_records WHERE bypassed = 1");
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    bypassCache.add(UUID.fromString(rs.getString("uuid")));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load bypass cache", e);
            }
        });
    }

    private boolean canUseVulcan(CommandSender sender) {
        if (!(sender instanceof Player)) return true; // console
        Player p = (Player) sender;
        List<String> owners = config.getStringList("owners");
        if (owners != null && owners.contains(p.getUniqueId().toString())) return true;
        return p.hasPermission("vulcan.punish");
    }

    private long currentTime() {
        return System.currentTimeMillis() / 1000L;
    }

    private void alertStaff(String msg) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.hasPermission("vulcan.alerts")) {
                TextUtil.sendMessage(p, msg);
            }
        }
    }

    private boolean isBypassed(UUID uuid) {
        return bypassCache.contains(uuid);
    }

    private int prepareOffense(OfflinePlayer target) {
        UUID uuid = target.getUniqueId();
        int offenses = 0;
        long lastPunish = 0;
        long cooldown = 0;
        
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT offenses, last_punish, cooldown FROM coresuite_vulcan_records WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    offenses = rs.getInt("offenses");
                    lastPunish = rs.getLong("last_punish");
                    cooldown = rs.getLong("cooldown");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get vulcan record", e);
        }

        long now = currentTime();
        if (now - cooldown < 10) {
            return Math.max(1, offenses);
        }

        long expirySeconds = config.getLong("expiry-seconds", 1209600);
        if (lastPunish > 0 && now - lastPunish >= expirySeconds) {
            offenses = 0;
        }

        offenses++;

        int finalOffenses = offenses;
        plugin.getDatabaseManager().executeAsync(() -> {
            plugin.getDatabaseManager().execute(
                "INSERT INTO coresuite_vulcan_records (uuid, player_name, offenses, last_punish, cooldown) VALUES (?, ?, ?, ?, ?) " +
                "ON CONFLICT(uuid) DO UPDATE SET player_name = ?, offenses = ?, last_punish = ?, cooldown = ?",
                uuid.toString(), target.getName(), finalOffenses, now, now,
                target.getName(), finalOffenses, now, now
            );
        });

        return offenses;
    }

    private void logPunishment(OfflinePlayer target, int tier, String action) {
        UUID uuid = target.getUniqueId();
        long now = currentTime();
        plugin.getDatabaseManager().executeAsync(() -> {
            plugin.getDatabaseManager().execute(
                "INSERT INTO coresuite_vulcan_history (uuid, time, tier, action) VALUES (?, ?, ?, ?)",
                uuid.toString(), now, tier, action
            );
            // Limit to 50
            plugin.getDatabaseManager().execute(
                "DELETE FROM coresuite_vulcan_history WHERE uuid = ? AND id NOT IN (SELECT id FROM coresuite_vulcan_history WHERE uuid = ? ORDER BY id DESC LIMIT 50)",
                uuid.toString(), uuid.toString()
            );
        });
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!canUseVulcan(sender)) {
            TextUtil.sendMessage(sender, "&cNo permission.");
            return true;
        }

        String cmd = command.getName().toLowerCase();
        String appealLink = config.getString("appeal-link", "discord.gg/yourserver");

        if (cmd.equals("vulcanpunish")) {
            if (args.length == 0) return false;
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            
            if (isBypassed(target.getUniqueId())) return true;

            plugin.getDatabaseManager().executeAsync(() -> {
                int tier = prepareOffense(target);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    handlePunishment(target, tier, appealLink, false, "");
                });
            });
            return true;
        } 
        else if (cmd.equals("vulcanwarn")) {
            if (args.length < 2) {
                TextUtil.sendMessage(sender, "&cUsage: /vulcanwarn <player> <reason>");
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            StringBuilder reasonBuilder = new StringBuilder();
            for (int i = 1; i < args.length; i++) reasonBuilder.append(args[i]).append(" ");
            String reason = reasonBuilder.toString().trim();
            
            if (isBypassed(target.getUniqueId())) {
                TextUtil.sendMessage(sender, "&e[Vulcan] &f" + target.getName() + " &7is on the bypass list. Remove them first: &c/vulcanbypass remove " + target.getName() + "&7.");
                return true;
            }
            
            plugin.getDatabaseManager().executeAsync(() -> {
                int tier = prepareOffense(target);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    handlePunishment(target, tier, appealLink, true, "[Staff: " + sender.getName() + "] " + reason);
                    TextUtil.sendMessage(sender, "&a[Vulcan] Applied punishment for " + target.getName() + ".");
                });
            });
            return true;
        }
        else if (cmd.equals("vulcancheck")) {
            if (args.length == 0) return false;
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            showCheck(sender, target);
            return true;
        }
        else if (cmd.equals("vulcanhistory")) {
            if (args.length == 0) return false;
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            showHistory(sender, target);
            return true;
        }
        else if (cmd.equals("vulcanreset")) {
            if (args.length == 0) return false;
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            plugin.getDatabaseManager().executeAsync(() -> {
                plugin.getDatabaseManager().execute("DELETE FROM coresuite_vulcan_records WHERE uuid = ?", target.getUniqueId().toString());
                plugin.getDatabaseManager().execute("DELETE FROM coresuite_vulcan_history WHERE uuid = ?", target.getUniqueId().toString());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    TextUtil.sendMessage(sender, "&a[Vulcan] Reset &f" + target.getName() + "&a. Now fully clean.");
                });
            });
            return true;
        }
        else if (cmd.equals("vulcansetoffenses")) {
            if (args.length < 2) return false;
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
            int num;
            try {
                num = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                return false;
            }
            
            if (num < 0) {
                TextUtil.sendMessage(sender, "&c[Vulcan] Cannot set a negative offense count.");
                return true;
            }
            if (num == 0) {
                Bukkit.dispatchCommand(sender, "vulcanreset " + target.getName());
                return true;
            }
            
            long now = currentTime();
            plugin.getDatabaseManager().executeAsync(() -> {
                plugin.getDatabaseManager().execute(
                    "INSERT INTO coresuite_vulcan_records (uuid, player_name, offenses, last_punish) VALUES (?, ?, ?, ?) " +
                    "ON CONFLICT(uuid) DO UPDATE SET player_name = ?, offenses = ?, last_punish = ?",
                    target.getUniqueId().toString(), target.getName(), num, now,
                    target.getName(), num, now
                );
                logPunishment(target, num, "STAFF-SETOFFENSES");
                Bukkit.getScheduler().runTask(plugin, () -> {
                    TextUtil.sendMessage(sender, "&a[Vulcan] Set &f" + target.getName() + " &ato offense #&c" + num + "&a.");
                });
            });
            return true;
        }
        else if (cmd.equals("vulcanbypass")) {
            if (args.length == 0) return false;
            String action = args[0].toLowerCase();
            
            if (action.equals("list")) {
                plugin.getDatabaseManager().executeAsync(() -> {
                    List<String> bypassed = new ArrayList<>();
                    try (Connection conn = plugin.getDatabaseManager().getConnection();
                         PreparedStatement stmt = conn.prepareStatement("SELECT player_name FROM coresuite_vulcan_records WHERE bypassed = 1");
                         ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            bypassed.add(rs.getString("player_name"));
                        }
                    } catch (SQLException e) {
                        plugin.getLogger().log(Level.SEVERE, "Failed to get bypass list", e);
                    }
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        TextUtil.sendMessage(sender, "&7&m---»--*-------------------------------------*--«---");
                        TextUtil.sendMessage(sender, "&cVulcan Bypass List &8(&c" + bypassed.size() + " player(s)&8)");
                        TextUtil.sendMessage(sender, "");
                        if (bypassed.isEmpty()) {
                            TextUtil.sendMessage(sender, "&7No players are currently bypassed.");
                        } else {
                            for (String name : bypassed) {
                                TextUtil.sendMessage(sender, " &8» &f" + name);
                            }
                        }
                        TextUtil.sendMessage(sender, "");
                        TextUtil.sendMessage(sender, "&7&m---»--*-------------------------------------*--«---");
                    });
                });
                return true;
            }
            
            if (args.length < 2) return false;
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            
            if (action.equals("add")) {
                plugin.getDatabaseManager().executeAsync(() -> {
                    plugin.getDatabaseManager().execute(
                        "INSERT INTO coresuite_vulcan_records (uuid, player_name, bypassed) VALUES (?, ?, 1) " +
                        "ON CONFLICT(uuid) DO UPDATE SET player_name = ?, bypassed = 1",
                        target.getUniqueId().toString(), target.getName(), target.getName()
                    );
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        bypassCache.add(target.getUniqueId());
                        TextUtil.sendMessage(sender, "&a[Vulcan] &f" + target.getName() + " &aadded to bypass list.");
                        alertStaff("&e[Vulcan] &7Bypass &aadded &7for &f" + target.getName() + " &7by &f" + sender.getName() + "&7.");
                    });
                });
            } else if (action.equals("remove")) {
                plugin.getDatabaseManager().executeAsync(() -> {
                    plugin.getDatabaseManager().execute(
                        "UPDATE coresuite_vulcan_records SET bypassed = 0 WHERE uuid = ?",
                        target.getUniqueId().toString()
                    );
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        bypassCache.remove(target.getUniqueId());
                        TextUtil.sendMessage(sender, "&a[Vulcan] &f" + target.getName() + " &aremoved from bypass list.");
                        alertStaff("&e[Vulcan] &7Bypass &cremoved &7for &f" + target.getName() + " &7by &f" + sender.getName() + "&7.");
                    });
                });
            }
            return true;
        }

        return false;
    }

    private void dispatchBan(String cmd) {
        if (Bukkit.getPluginManager().isPluginEnabled("LiteBans") || Bukkit.getPluginManager().isPluginEnabled("AdvancedBan") || Bukkit.getPluginManager().isPluginEnabled("LibertyBans")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        } else {
            plugin.getLogger().warning("[VulcanPunish] Skipped ban command because no ban plugin is loaded: " + cmd);
        }
    }

    private void handlePunishment(OfflinePlayer target, int tier, String appealLink, boolean staffWarn, String reason) {
        String name = target.getName();
        if (tier >= 7) {
            if (target.isOnline()) {
                target.getPlayer().kick(TextUtil.colorize("&4&lPermanently Banned&r\n\n&7Reason: " + (staffWarn ? reason : "Cheating (Offense #" + tier + ")") + "\n\n&7Appeal: &b" + appealLink));
            }
            dispatchBan("ban " + name + " " + (staffWarn ? reason : "[Vulcan] Permanently banned — Cheating (Offense #" + tier + ")"));
            logPunishment(target, tier, staffWarn ? "STAFF-PERMBAN" : "PERMBAN");
            
            Bukkit.broadcast(TextUtil.colorize(""));
            Bukkit.broadcast(TextUtil.colorize("&7&m---»--*-------------------------------------*--«---"));
            Bukkit.broadcast(TextUtil.colorize("&4&l" + (staffWarn ? "[Staff]" : "[Vulcan]") + " &f" + name + " &7has been &4&lpermanently banned&7."));
            Bukkit.broadcast(TextUtil.colorize("          &8Offense #" + tier + "  &8|  &7No further chances."));
            Bukkit.broadcast(TextUtil.colorize("&7&m---»--*-------------------------------------*--«---"));
            Bukkit.broadcast(TextUtil.colorize(""));
        } else if (tier >= 6) {
            if (target.isOnline()) {
                target.getPlayer().kick(TextUtil.colorize("&4&lBanned: 7 Days&r\n\n&7Reason: " + (staffWarn ? reason : "Cheating (Offense #" + tier + ")") + "\n\n&7Appeal: &b" + appealLink));
            }
            dispatchBan("tempban " + name + " 7d " + (staffWarn ? reason : "[Vulcan] Cheating - Offense #" + tier));
            logPunishment(target, tier, staffWarn ? "STAFF-TEMPBAN-7D" : "TEMPBAN-7D");
            
            Bukkit.broadcast(TextUtil.colorize(""));
            Bukkit.broadcast(TextUtil.colorize("&7&m---»--*-------------------------------------*--«---"));
            Bukkit.broadcast(TextUtil.colorize("&4" + (staffWarn ? "[Staff]" : "[Vulcan]") + " &f" + name + " &7has been &cbanned &7for &c7 days&7."));
            Bukkit.broadcast(TextUtil.colorize("          &8Offense #" + tier + "  &8|  &7Next offense = &4permanent ban&7."));
            Bukkit.broadcast(TextUtil.colorize("&7&m---»--*-------------------------------------*--«---"));
            Bukkit.broadcast(TextUtil.colorize(""));
        } else if (tier >= 5) {
            if (target.isOnline()) {
                target.getPlayer().kick(TextUtil.colorize("&4&lBanned: 3 Days&r\n\n&7Reason: " + (staffWarn ? reason : "Cheating (Offense #" + tier + ")") + "\n\n&7Appeal: &b" + appealLink));
            }
            dispatchBan("tempban " + name + " 3d " + (staffWarn ? reason : "[Vulcan] Cheating - Offense #" + tier));
            logPunishment(target, tier, staffWarn ? "STAFF-TEMPBAN-3D" : "TEMPBAN-3D");
            
            Bukkit.broadcast(TextUtil.colorize(""));
            Bukkit.broadcast(TextUtil.colorize("&7&m---»--*-------------------------------------*--«---"));
            Bukkit.broadcast(TextUtil.colorize("&6" + (staffWarn ? "[Staff]" : "[Vulcan]") + " &f" + name + " &7has been &6banned &7for &63 days&7."));
            Bukkit.broadcast(TextUtil.colorize("          &8Offense #" + tier + "  &8|  &7Next offense = 7-day ban."));
            Bukkit.broadcast(TextUtil.colorize("&7&m---»--*-------------------------------------*--«---"));
            Bukkit.broadcast(TextUtil.colorize(""));
        } else if (tier >= 4) {
            if (target.isOnline()) {
                target.getPlayer().kick(TextUtil.colorize("&4&lBanned: 1 Day&r\n\n&7Reason: " + (staffWarn ? reason : "Cheating (Offense #" + tier + ")") + "\n\n&7Appeal: &b" + appealLink));
            }
            dispatchBan("tempban " + name + " 1d " + (staffWarn ? reason : "[Vulcan] Cheating - Offense #" + tier));
            logPunishment(target, tier, staffWarn ? "STAFF-TEMPBAN-1D" : "TEMPBAN-1D");
            
            Bukkit.broadcast(TextUtil.colorize(""));
            Bukkit.broadcast(TextUtil.colorize("&7&m---»--*-------------------------------------*--«---"));
            Bukkit.broadcast(TextUtil.colorize("&6" + (staffWarn ? "[Staff]" : "[Vulcan]") + " &f" + name + " &7has been &6banned &7for &61 day&7."));
            Bukkit.broadcast(TextUtil.colorize("          &8Offense #" + tier + "  &8|  &7Next offense = 3-day ban."));
            Bukkit.broadcast(TextUtil.colorize("&7&m---»--*-------------------------------------*--«---"));
            Bukkit.broadcast(TextUtil.colorize(""));
        } else if (tier >= 3) {
            if (target.isOnline()) {
                target.getPlayer().kick(TextUtil.colorize("&4&lCheating Detected&r\n\n&7You have been removed from the server.\n\n&eOffense &c#3 &e— next offense = &c1-day ban&e.\n\n&7Appeal: &b" + appealLink));
            }
            logPunishment(target, tier, staffWarn ? "STAFF-WARN-3" : "KICK-3");
            Bukkit.broadcast(TextUtil.colorize(""));
            Bukkit.broadcast(TextUtil.colorize("&7&m---»--*-------------------------------------*--«---"));
            Bukkit.broadcast(TextUtil.colorize("&c" + (staffWarn ? "[Staff]" : "[Vulcan]") + " &f" + name + " &7was &ckicked &8(Offense #3 — next: 1-day ban)"));
            Bukkit.broadcast(TextUtil.colorize("&7&m---»--*-------------------------------------*--«---"));
            Bukkit.broadcast(TextUtil.colorize(""));
        } else if (tier >= 2) {
            if (target.isOnline()) {
                target.getPlayer().kick(TextUtil.colorize("&4&lCheating Detected&r\n\n&7You have been removed from the server.\n\n&eOffense &6#2 &e— one more kick before bans begin.\n\n&7Appeal: &b" + appealLink));
            }
            logPunishment(target, tier, staffWarn ? "STAFF-WARN-2" : "KICK-2");
            alertStaff("&e" + (staffWarn ? "[Staff]" : "[Vulcan]") + " &f" + name + " &7was &ekicked&7. &8(Offense #2 — next: 3rd kick, then 1d ban)");
        } else {
            if (target.isOnline()) {
                target.getPlayer().kick(TextUtil.colorize("&4&lCheating Detected&r\n\n&7You have been removed from the server.\n\n&eOffense &c#1 &e— further offenses escalate to bans.\n\n&7Appeal: &b" + appealLink));
            }
            logPunishment(target, tier, staffWarn ? "STAFF-WARN-1" : "KICK-1");
            alertStaff("&e" + (staffWarn ? "[Staff]" : "[Vulcan]") + " &f" + name + " &7was &ekicked&7. &8(Offense #1 — 1st warning)");
        }
    }

    private void showCheck(CommandSender sender, OfflinePlayer target) {
        plugin.getDatabaseManager().executeAsync(() -> {
            int offenses = 0;
            long lastPunish = 0;
            boolean bypassed = false;
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT offenses, last_punish, bypassed FROM coresuite_vulcan_records WHERE uuid = ?")) {
                stmt.setString(1, target.getUniqueId().toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        offenses = rs.getInt("offenses");
                        lastPunish = rs.getLong("last_punish");
                        bypassed = rs.getBoolean("bypassed");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to get check record", e);
            }

            final int fOffenses = offenses;
            final long fLastPunish = lastPunish;
            final boolean fBypassed = bypassed;

            Bukkit.getScheduler().runTask(plugin, () -> {
                TextUtil.sendMessage(sender, "&7&m---»--*-------------------------------------*--«---");
                TextUtil.sendMessage(sender, "&cVulcan Record &8| &f" + target.getName());
                TextUtil.sendMessage(sender, "");
                TextUtil.sendMessage(sender, "&7Offenses: &c" + fOffenses);
                if (fBypassed) {
                    TextUtil.sendMessage(sender, "&7Bypass:   &a✔ Active &8— will not be punished");
                }

                if (fLastPunish > 0) {
                    long diff = currentTime() - fLastPunish;
                    long days = diff / 86400;
                    long hours = (diff % 86400) / 3600;
                    long mins = (diff % 3600) / 60;
                    
                    if (days >= 1) TextUtil.sendMessage(sender, "&7Last punishment: &e" + days + "d " + hours + "h ago");
                    else if (hours >= 1) TextUtil.sendMessage(sender, "&7Last punishment: &e" + hours + "h " + mins + "m ago");
                    else TextUtil.sendMessage(sender, "&7Last punishment: &e" + mins + "m ago");

                    long expiry = config.getLong("expiry-seconds", 1209600);
                    long remaining = expiry - diff;
                    if (remaining > 0) {
                        long rd = remaining / 86400;
                        long rh = (remaining % 86400) / 3600;
                        if (rd >= 1) TextUtil.sendMessage(sender, "&7Record expires in: &a" + rd + "d " + rh + "h");
                        else TextUtil.sendMessage(sender, "&7Record expires in: &a" + rh + "h");
                    } else {
                        TextUtil.sendMessage(sender, "&7Record: &aExpired (resets on next offense)");
                    }
                } else {
                    TextUtil.sendMessage(sender, "&7Last punishment: &aNone on record");
                }

                TextUtil.sendMessage(sender, "");
                if (fOffenses >= 7) TextUtil.sendMessage(sender, "&7Status: &4&lPERMANENT BAN TIER");
                else if (fOffenses >= 6) TextUtil.sendMessage(sender, "&7Status: &4Tier 6 &87d ban &8| &7Next: &4permanent ban");
                else if (fOffenses >= 5) TextUtil.sendMessage(sender, "&7Status: &cTier 5 &83d ban &8| &7Next: 7d ban");
                else if (fOffenses >= 4) TextUtil.sendMessage(sender, "&7Status: &6Tier 4 &81d ban &8| &7Next: 3d ban");
                else if (fOffenses >= 3) TextUtil.sendMessage(sender, "&7Status: &eTier 3 &8kick &8| &7Next: 1d ban");
                else if (fOffenses >= 2) TextUtil.sendMessage(sender, "&7Status: &eTier 2 &8kick &8| &7Next: 3rd kick then 1d ban");
                else if (fOffenses >= 1) TextUtil.sendMessage(sender, "&7Status: &eTier 1 &8kick &8| &7Next: 2nd kick");
                else TextUtil.sendMessage(sender, "&7Status: &aClean");
                
                TextUtil.sendMessage(sender, "&7&m---»--*-------------------------------------*--«---");
            });
        });
    }

    private void showHistory(CommandSender sender, OfflinePlayer target) {
        plugin.getDatabaseManager().executeAsync(() -> {
            List<String> history = new ArrayList<>();
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT time, tier, action FROM coresuite_vulcan_history WHERE uuid = ? ORDER BY id ASC LIMIT 15")) {
                stmt.setString(1, target.getUniqueId().toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        long ts = rs.getLong("time");
                        int tier = rs.getInt("tier");
                        String action = rs.getString("action");
                        
                        long age = currentTime() - ts;
                        long ad = age / 86400;
                        long ah = (age % 86400) / 3600;
                        
                        if (ad >= 1) history.add(" &8» &c" + action + " &8| &7Tier " + tier + " &8| &7" + ad + "d " + ah + "h ago");
                        else history.add(" &8» &c" + action + " &8| &7Tier " + tier + " &8| &7" + ah + "h ago");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to get history", e);
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                TextUtil.sendMessage(sender, "&7&m---»--*-------------------------------------*--«---");
                TextUtil.sendMessage(sender, "&cVulcan History &8| &f" + target.getName() + " &8(&c" + history.size() + " entries&8)");
                TextUtil.sendMessage(sender, "");
                
                if (history.isEmpty()) {
                    TextUtil.sendMessage(sender, "&7No history on record.");
                } else {
                    for (String line : history) {
                        TextUtil.sendMessage(sender, line);
                    }
                }
                
                TextUtil.sendMessage(sender, "");
                TextUtil.sendMessage(sender, "&7&m---»--*-------------------------------------*--«---");
            });
        });
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (!canUseVulcan(sender)) return completions;

        String cmd = command.getName().toLowerCase();
        if (cmd.equals("vulcanpunish") || cmd.equals("vulcanwarn") || cmd.equals("vulcancheck") || 
            cmd.equals("vulcanhistory") || cmd.equals("vulcanreset") || cmd.equals("vulcansetoffenses")) {
            if (args.length == 1) {
                List<String> players = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) players.add(p.getName());
                org.bukkit.util.StringUtil.copyPartialMatches(args[0], players, completions);
            }
        } else if (cmd.equals("vulcanbypass")) {
            if (args.length == 1) {
                org.bukkit.util.StringUtil.copyPartialMatches(args[0], java.util.Arrays.asList("list", "add", "remove"), completions);
            } else if (args.length == 2 && (args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("remove"))) {
                List<String> players = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) players.add(p.getName());
                org.bukkit.util.StringUtil.copyPartialMatches(args[1], players, completions);
            }
        }
        return completions;
    }
}
