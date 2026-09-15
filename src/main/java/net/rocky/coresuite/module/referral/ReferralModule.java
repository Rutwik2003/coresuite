package net.rocky.coresuite.module.referral;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;

public class ReferralModule extends Module implements CommandExecutor, Listener, TabCompleter {

    private final Random random = new Random();
    private final char[] charset = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private final Set<UUID> pendingRedeem = java.util.concurrent.ConcurrentHashMap.newKeySet();

    public ReferralModule(CoreSuitePlugin plugin) {
        super(plugin, "Referral", "referral");
    }

    @Override
    public void onEnable() {
        registerListener(this);
        initDatabase();

        PluginCommand command = plugin.getCommand("referral");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    @Override
    public void onDisable() {
        PluginCommand command = plugin.getCommand("referral");
        if (command != null && command.getExecutor() == this) {
            command.setExecutor(null);
            command.setTabCompleter(null);
        }
    }

    private void initDatabase() {
        plugin.getDatabaseManager().execute(
            "CREATE TABLE IF NOT EXISTS coresuite_referrals (" +
            "uuid TEXT PRIMARY KEY, " +
            "player_name TEXT, " +
            "code TEXT, " +
            "ip TEXT, " +
            "banned BOOLEAN DEFAULT 0, " +
            "used_code TEXT, " +
            "referred_by TEXT)"
        );

        plugin.getDatabaseManager().execute(
            "CREATE TABLE IF NOT EXISTS coresuite_referral_rewards (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "owner TEXT, " +
            "referred_name TEXT, " +
            "owner_reward TEXT, " +
            "redeemer_reward TEXT)"
        );

        plugin.getDatabaseManager().execute(
            "CREATE TABLE IF NOT EXISTS coresuite_referral_flags (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "message TEXT)"
        );
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            sb.append(charset[random.nextInt(charset.length)]);
        }
        return sb.toString();
    }

    private String getUniqueCode(Connection conn) {
        try (PreparedStatement stmt = conn.prepareStatement("SELECT 1 FROM coresuite_referrals WHERE code = ?")) {
            while (true) {
                String code = generateCode();
                stmt.setString(1, code);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (!rs.next()) return code;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to generate referral code", e);
            return generateCode();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "";
        plugin.getDatabaseManager().execute("INSERT OR IGNORE INTO coresuite_referrals (uuid, player_name, ip) VALUES (?, ?, ?)", 
            player.getUniqueId().toString(), player.getName(), ip);
        
        plugin.getDatabaseManager().execute("UPDATE coresuite_referrals SET ip = ? WHERE uuid = ?", ip, player.getUniqueId().toString());
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (pendingRedeem.contains(player.getUniqueId())) {
            event.setCancelled(true);
            String message = event.getMessage().trim();
            pendingRedeem.remove(player.getUniqueId());
            if (message.equalsIgnoreCase("cancel")) {
                TextUtil.sendMessage(player, config.getString("prefix", "&8[&6Referral&8] &r") + "&cRedemption cancelled.");
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                Bukkit.dispatchCommand(player, "referral use " + message);
            });
        }
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        pendingRedeem.remove(event.getPlayer().getUniqueId());
    }

    private int getPlaytimeMinutes(Player player) {
        return player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20 / 60;
    }

    private void dispatchEco(String cmd) {
        if (Bukkit.getPluginManager().isPluginEnabled("Essentials") || Bukkit.getPluginManager().isPluginEnabled("Vault")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        } else {
            plugin.getLogger().warning("[Referral] Skipped economy command because Vault/Essentials is not loaded: " + cmd);
        }
    }

    private void dispatchCrate(String cmd) {
        if (Bukkit.getPluginManager().isPluginEnabled("ExcellentCrates")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        } else {
            plugin.getLogger().warning("[Referral] Skipped crate command because ExcellentCrates is not loaded: " + cmd);
        }
    }

    private void giveReward(String owner, int count, String referredName) {
        String ownerReward = "";
        if (count == 1) {
            double amt = config.getDouble("reward-1-eco", 2000);
            dispatchEco("eco give " + owner + " " + amt);
            ownerReward = "eco:" + amt;
        } else if (count == 2) {
            double amt = config.getDouble("reward-2-eco", 3000);
            dispatchEco("eco give " + owner + " " + amt);
            ownerReward = "eco:" + amt;
        } else if (count == config.getInt("tier-common-at", 3)) {
            double amt = config.getDouble("reward-3-eco", 3000);
            String key = config.getString("key-common", "common");
            dispatchEco("eco give " + owner + " " + amt);
            dispatchCrate("crate key give " + owner + " " + key + " 1");
            ownerReward = "eco:" + amt + "+key:" + key;
        } else if (count >= config.getInt("tier-uncommon-start", 4) && count <= config.getInt("tier-uncommon-end", 13)) {
            String key = config.getString("key-uncommon", "uncommon");
            dispatchCrate("crate key give " + owner + " " + key + " 1");
            ownerReward = "key:" + key;
        } else if (count >= config.getInt("tier-rare-start", 14) && count <= config.getInt("tier-rare-end", 29)) {
            String key = config.getString("key-rare", "rare");
            dispatchCrate("crate key give " + owner + " " + key + " 1");
            ownerReward = "key:" + key;
        } else if (count >= config.getInt("tier-insane-start", 30)) {
            String key = config.getString("key-insane", "insane");
            dispatchCrate("crate key give " + owner + " " + key + " 1");
            ownerReward = "key:" + key;
        }

        double redeemerEco = config.getDouble("redeemer-eco", 1000);
        String redeemerKey = config.getString("redeemer-key", "common");
        dispatchEco("eco give " + referredName + " " + redeemerEco);
        dispatchCrate("crate key give " + referredName + " " + redeemerKey + " 1");
        String redeemerReward = "eco:" + redeemerEco + "+key:" + redeemerKey;

        plugin.getDatabaseManager().execute("INSERT INTO coresuite_referral_rewards (owner, referred_name, owner_reward, redeemer_reward) VALUES (?, ?, ?, ?)",
            owner, referredName, ownerReward, redeemerReward);

        Player ownerPlayer = Bukkit.getPlayer(owner);
        if (ownerPlayer != null) {
            String prefix = config.getString("prefix", "&8[&6Referral&8] &r");
            TextUtil.sendMessage(ownerPlayer, prefix + "&6New referral! (#" + count + ") Reward has been sent to your account.");
        }

        Player referredPlayer = Bukkit.getPlayer(referredName);
        if (referredPlayer != null) {
            String prefix = config.getString("prefix", "&8[&6Referral&8] &r");
            TextUtil.sendMessage(referredPlayer, prefix + "&aWelcome bonus: &e" + redeemerEco + " &aeco + &e1 " + redeemerKey + " key&a!");
        }
    }

    private void takeReward(String owner, String rewardString) {
        if (rewardString.isEmpty()) return;
        String[] parts = rewardString.split("\\+");
        for (String part : parts) {
            if (part.startsWith("eco:")) {
                double amt = Double.parseDouble(part.split(":")[1]);
                dispatchEco("eco take " + owner + " " + amt);
            } else if (part.startsWith("key:")) {
                String key = part.split(":")[1];
                dispatchCrate("crate key take " + owner + " " + key + " 1");
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String prefix = config.getString("prefix", "&8[&6Referral&8] &r");

        if (args.length == 0) {
            if (sender instanceof Player) {
                Bukkit.dispatchCommand(sender, "referral gui");
            } else {
                sendHelp(sender, prefix);
            }
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("gui")) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            
            plugin.getDatabaseManager().executeAsync(() -> {
                try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                    String code = null;
                    try (PreparedStatement stmt = conn.prepareStatement("SELECT code FROM coresuite_referrals WHERE uuid = ?")) {
                        stmt.setString(1, player.getUniqueId().toString());
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                code = rs.getString("code");
                            }
                        }
                    }

                    int count = 0;
                    try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM coresuite_referrals WHERE referred_by = ?")) {
                        stmt.setString(1, player.getName());
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                count = rs.getInt(1);
                            }
                        }
                    }

                    final String fCode = code;
                    final int fCount = count;
                    
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        net.rocky.coresuite.common.gui.Gui gui = new net.rocky.coresuite.common.gui.Gui("&8Referral Menu", 3);
                        gui.fillBorder(org.bukkit.Material.BLACK_STAINED_GLASS_PANE);
                        
                        if (fCode != null) {
                            org.bukkit.inventory.ItemStack codeItem = TextUtil.createItem(
                                org.bukkit.Material.PAPER, 
                                "&e&lYour Code", 
                                "&7Code: &f" + fCode
                            );
                            gui.setItem(11, codeItem, e -> {});
                        } else {
                            org.bukkit.inventory.ItemStack createItem = TextUtil.createItem(
                                org.bukkit.Material.NAME_TAG,
                                "&e&lCreate Code",
                                "&7Click to generate your referral code!"
                            );
                            gui.setItem(11, createItem, e -> {
                                player.closeInventory();
                                Bukkit.dispatchCommand(player, "referral create -gui");
                            });
                        }
                        
                        org.bukkit.inventory.ItemStack statsItem = TextUtil.createItem(
                            org.bukkit.Material.PLAYER_HEAD,
                            "&b&lStats",
                            "&7Total Referrals: &a" + fCount
                        );
                        gui.setItem(13, statsItem, e -> {});
                        
                        org.bukkit.inventory.ItemStack redeemItem = TextUtil.createItem(
                            org.bukkit.Material.EMERALD,
                            "&a&lRedeem Code",
                            "&7Click to redeem a friend's code"
                        );
                        gui.setItem(15, redeemItem, e -> {
                            player.closeInventory();
                            pendingRedeem.add(player.getUniqueId());
                            TextUtil.sendMessage(player, config.getString("prefix", "&8[&6Referral&8] &r") + "&eType the referral code in chat now (or type 'cancel').");
                        });
                        
                        gui.open(player);
                    });
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to load GUI data", e);
                }
            });
            return true;
        }

        if (sub.equals("create")) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            
            plugin.getDatabaseManager().executeAsync(() -> {
                try (Connection conn = plugin.getDatabaseManager().getConnection();
                     PreparedStatement stmt = conn.prepareStatement("SELECT code, banned FROM coresuite_referrals WHERE uuid = ?")) {
                    stmt.setString(1, player.getUniqueId().toString());
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            if (rs.getBoolean("banned")) {
                                Bukkit.getScheduler().runTask(plugin, () -> TextUtil.sendMessage(player, prefix + "&cYou have been banned from the referral system due to abuse."));
                                return;
                            }
                            String existing = rs.getString("code");
                            if (existing != null) {
                                Bukkit.getScheduler().runTask(plugin, () -> TextUtil.sendMessage(player, prefix + "&cYou already have a code: &e" + existing));
                                return;
                            }
                        }
                    }

                    boolean fromGui = args.length > 1 && args[1].equalsIgnoreCase("-gui");
                    String newCode = getUniqueCode(conn);
                    plugin.getDatabaseManager().execute("UPDATE coresuite_referrals SET code = ? WHERE uuid = ?", newCode, player.getUniqueId().toString());
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        TextUtil.sendMessage(player, prefix + "&aYour referral code is: &e" + newCode);
                        TextUtil.sendMessage(player, prefix + "&7Share it! Friends redeem it with &e/referral use " + newCode);
                        if (fromGui) {
                            Bukkit.dispatchCommand(player, "referral gui");
                        }
                    });
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to create referral code", e);
                }
            });
            return true;
        }

        if (sub.equals("use")) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;

            if (args.length < 2) {
                TextUtil.sendMessage(player, prefix + "&cUsage: /referral use <code>");
                return true;
            }
            String code = args[1];

            int minPlaytime = config.getInt("min-playtime-minutes", 120);
            int playtime = getPlaytimeMinutes(player);
            if (playtime < minPlaytime) {
                TextUtil.sendMessage(player, prefix + "&cYou need to play a bit longer before redeeming a code. (&e" + (minPlaytime - playtime) + "&c minutes left)");
                return true;
            }

            if (!pendingRedeem.add(player.getUniqueId())) {
                TextUtil.sendMessage(player, prefix + "&cYour referral request is currently processing...");
                return true;
            }

            plugin.getDatabaseManager().executeAsync(() -> {
                try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                    
                    // Check if banned or already used
                    try (PreparedStatement stmt = conn.prepareStatement("SELECT banned, used_code FROM coresuite_referrals WHERE uuid = ?")) {
                        stmt.setString(1, player.getUniqueId().toString());
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                if (rs.getBoolean("banned")) {
                                    Bukkit.getScheduler().runTask(plugin, () -> TextUtil.sendMessage(player, prefix + "&cYou have been banned from the referral system."));
                                    return;
                                }
                                if (rs.getString("used_code") != null) {
                                    Bukkit.getScheduler().runTask(plugin, () -> TextUtil.sendMessage(player, prefix + "&cYou've already redeemed a referral code before."));
                                    return;
                                }
                            }
                        }
                    }

                    // Check code owner
                    String ownerName = null;
                    String ownerIp = null;
                    try (PreparedStatement stmt = conn.prepareStatement("SELECT player_name, ip FROM coresuite_referrals WHERE code = ?")) {
                        stmt.setString(1, code);
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                ownerName = rs.getString("player_name");
                                ownerIp = rs.getString("ip");
                            } else {
                                Bukkit.getScheduler().runTask(plugin, () -> TextUtil.sendMessage(player, prefix + "&cThat referral code doesn't exist."));
                                return;
                            }
                        }
                    }

                    if (ownerName.equalsIgnoreCase(player.getName())) {
                        Bukkit.getScheduler().runTask(plugin, () -> TextUtil.sendMessage(player, prefix + "&cYou can't use your own referral code!"));
                        return;
                    }

                    String myIp = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : "";
                    boolean sameSubnet = false;
                    if (ownerIp != null && !ownerIp.isEmpty() && !myIp.isEmpty()) {
                        if (ownerIp.equals(myIp)) {
                            sameSubnet = true;
                        } else {
                            int lastDotOwner = ownerIp.lastIndexOf('.');
                            int lastDotMy = myIp.lastIndexOf('.');
                            if (lastDotOwner != -1 && lastDotMy != -1 && 
                                ownerIp.substring(0, lastDotOwner).equals(myIp.substring(0, lastDotMy))) {
                                sameSubnet = true;
                            }
                        }
                    }

                    int ipReferralCount = 0;
                    if (!myIp.isEmpty()) {
                        try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM coresuite_referrals WHERE ip = ? AND referred_by = ?")) {
                            stmt.setString(1, myIp);
                            stmt.setString(2, ownerName);
                            try (ResultSet rs = stmt.executeQuery()) {
                                if (rs.next()) ipReferralCount = rs.getInt(1);
                            }
                        }
                    }

                    if (sameSubnet || ipReferralCount > 0) {
                        String flag = "ALT SUSPECTED: " + player.getName() + " and " + ownerName + " share network " + myIp;
                        plugin.getDatabaseManager().execute("INSERT INTO coresuite_referral_flags (message) VALUES (?)", flag);
                        final String finalOwnerName = ownerName;
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            TextUtil.sendMessage(player, prefix + "&cThis referral could not be completed. Contact staff if this is a mistake.");
                            for (Player p : Bukkit.getOnlinePlayers()) {
                                if (p.hasPermission("coresuite.referral.admin")) {
                                    TextUtil.sendMessage(p, prefix + "&c[Alert] Blocked referral: " + player.getName() + " -> " + finalOwnerName + " (alt account detected)");
                                }
                            }
                        });
                        return;
                    }

                    plugin.getDatabaseManager().execute("UPDATE coresuite_referrals SET used_code = ?, referred_by = ? WHERE uuid = ?", code, ownerName, player.getUniqueId().toString());
                    
                    // Get count
                    int count = 0;
                    try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM coresuite_referrals WHERE referred_by = ?")) {
                        stmt.setString(1, ownerName);
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                count = rs.getInt(1);
                            }
                        }
                    }

                    final String finalOwner = ownerName;
                    final int finalCount = count;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        giveReward(finalOwner, finalCount, player.getName());
                        TextUtil.sendMessage(player, prefix + "&aCode redeemed! You were referred by &e" + finalOwner);
                    });

                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to use referral code", e);
                } finally {
                    pendingRedeem.remove(player.getUniqueId());
                }
            });
            return true;
        }

        if (sub.equals("info")) {
            String targetName = sender.getName();
            if (args.length > 1) {
                targetName = args[1];
            }
            final String finalTarget = targetName;
            
            if (sender instanceof Player && args.length == 1) {
                int playtime = getPlaytimeMinutes((Player) sender);
                int minPlaytime = config.getInt("min-playtime-minutes", 120);
                int hours = playtime / 60;
                int mins = playtime % 60;
                
                if (playtime >= minPlaytime) {
                    TextUtil.sendMessage(sender, "&7Playtime: &a" + hours + "h " + mins + "m &a(requirement met)");
                } else {
                    TextUtil.sendMessage(sender, "&7Playtime: &c" + hours + "h " + mins + "m &7(" + (minPlaytime - playtime) + " minutes until you can redeem a code)");
                }
            }

            plugin.getDatabaseManager().executeAsync(() -> {
                try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                    String code = null;
                    String referredBy = null;
                    try (PreparedStatement stmt = conn.prepareStatement("SELECT code, referred_by FROM coresuite_referrals WHERE player_name = ?")) {
                        stmt.setString(1, finalTarget);
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                code = rs.getString("code");
                                referredBy = rs.getString("referred_by");
                            }
                        }
                    }

                    int count = 0;
                    try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM coresuite_referrals WHERE referred_by = ?")) {
                        stmt.setString(1, finalTarget);
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                count = rs.getInt(1);
                            }
                        }
                    }

                    final String fCode = code;
                    final String fRefBy = referredBy;
                    final int fCount = count;
                    
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        TextUtil.sendMessage(sender, prefix + "&6Stats for " + finalTarget + ":");
                        if (fCode != null) {
                            TextUtil.sendMessage(sender, "&7Code: &e" + fCode);
                        } else {
                            TextUtil.sendMessage(sender, "&7Code: &8(none yet - /referral create)");
                        }
                        TextUtil.sendMessage(sender, "&7Total referrals: &a" + fCount);
                        if (fRefBy != null) {
                            TextUtil.sendMessage(sender, "&7Referred by: &e" + fRefBy);
                        }
                    });

                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to get referral info", e);
                }
            });
            return true;
        }

        if (sub.equals("leaderboard") || sub.equals("top")) {
            plugin.getDatabaseManager().executeAsync(() -> {
                try (Connection conn = plugin.getDatabaseManager().getConnection();
                     PreparedStatement stmt = conn.prepareStatement("SELECT referred_by, COUNT(*) as refs FROM coresuite_referrals WHERE referred_by IS NOT NULL GROUP BY referred_by ORDER BY refs DESC LIMIT 10")) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        List<String> lb = new ArrayList<>();
                        int rank = 1;
                        while (rs.next()) {
                            lb.add("&e#" + rank + " &f" + rs.getString("referred_by") + " &7- &a" + rs.getInt("refs") + " referrals");
                            rank++;
                        }
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            TextUtil.sendMessage(sender, prefix + "&6Top Referrers:");
                            for (String s : lb) {
                                TextUtil.sendMessage(sender, s);
                            }
                        });
                    }
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to get leaderboard", e);
                }
            });
            return true;
        }

        if (sub.equals("flags")) {
            if (!sender.hasPermission("coresuite.referral.admin")) {
                TextUtil.sendMessage(sender, prefix + "&cNo permission.");
                return true;
            }
            plugin.getDatabaseManager().executeAsync(() -> {
                try (Connection conn = plugin.getDatabaseManager().getConnection();
                     PreparedStatement stmt = conn.prepareStatement("SELECT message FROM coresuite_referral_flags ORDER BY id DESC LIMIT 20")) {
                    try (ResultSet rs = stmt.executeQuery()) {
                        List<String> flags = new ArrayList<>();
                        while (rs.next()) {
                            flags.add(rs.getString("message"));
                        }
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            if (flags.isEmpty()) {
                                TextUtil.sendMessage(sender, prefix + "&7No flagged attempts.");
                            } else {
                                TextUtil.sendMessage(sender, prefix + "&6Flagged alt attempts (last 20):");
                                for (String f : flags) {
                                    TextUtil.sendMessage(sender, "&7- " + f);
                                }
                            }
                        });
                    }
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to get flags", e);
                }
            });
            return true;
        }

        if (sub.equals("revoke")) {
            if (!sender.hasPermission("coresuite.referral.admin")) {
                TextUtil.sendMessage(sender, prefix + "&cNo permission.");
                return true;
            }
            if (args.length < 2) {
                TextUtil.sendMessage(sender, prefix + "&cUsage: /referral revoke <referred player name>");
                return true;
            }
            String target = args[1];

            plugin.getDatabaseManager().executeAsync(() -> {
                try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                    String owner = null;
                    try (PreparedStatement stmt = conn.prepareStatement("SELECT referred_by FROM coresuite_referrals WHERE player_name = ?")) {
                        stmt.setString(1, target);
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) owner = rs.getString("referred_by");
                        }
                    }

                    if (owner == null) {
                        Bukkit.getScheduler().runTask(plugin, () -> TextUtil.sendMessage(sender, prefix + "&cNo referral record found for " + target + "."));
                        return;
                    }

                    String ownerReward = "";
                    String redeemerReward = "";
                    int logId = -1;
                    try (PreparedStatement stmt = conn.prepareStatement("SELECT id, owner_reward, redeemer_reward FROM coresuite_referral_rewards WHERE owner = ? AND referred_name = ?")) {
                        stmt.setString(1, owner);
                        stmt.setString(2, target);
                        try (ResultSet rs = stmt.executeQuery()) {
                            if (rs.next()) {
                                logId = rs.getInt("id");
                                ownerReward = rs.getString("owner_reward");
                                redeemerReward = rs.getString("redeemer_reward");
                            }
                        }
                    }

                    if (logId != -1) {
                        plugin.getDatabaseManager().execute("DELETE FROM coresuite_referral_rewards WHERE id = ?", logId);
                        
                        final String finalOwner = owner;
                        final String finalOwnerReward = ownerReward;
                        final String finalRedeemerReward = redeemerReward;
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            takeReward(finalOwner, finalOwnerReward);
                            takeReward(target, finalRedeemerReward);
                        });
                    } else {
                        Bukkit.getScheduler().runTask(plugin, () -> TextUtil.sendMessage(sender, prefix + "&cCouldn't find a matching reward log entry - may need manual correction."));
                    }

                    plugin.getDatabaseManager().execute("UPDATE coresuite_referrals SET banned = 1, used_code = NULL, referred_by = NULL WHERE player_name = ?", target);

                    final String finalOwner2 = owner;
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        TextUtil.sendMessage(sender, prefix + "&aRevoked: " + finalOwner2 + "'s referral reward from " + target + " has been undone. " + target + " is now banned from the referral system.");
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            if (p.hasPermission("coresuite.referral.admin")) {
                                TextUtil.sendMessage(p, prefix + "&e" + sender.getName() + " revoked referral: " + finalOwner2 + " <- " + target);
                            }
                        }
                    });

                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to revoke referral", e);
                }
            });
            return true;
        }

        if (sub.equals("admin")) {
            if (!sender.hasPermission("coresuite.referral.admin")) {
                TextUtil.sendMessage(sender, prefix + "&cNo permission.");
                return true;
            }
            if (args.length < 2) {
                TextUtil.sendMessage(sender, prefix + "&c/referral admin forceadd <player> <referrer>");
                TextUtil.sendMessage(sender, prefix + "&c/referral admin setcount <player> <amount>");
                return true;
            }
            String action = args[1].toLowerCase();
            if (action.equals("forceadd")) {
                if (args.length < 4) {
                    TextUtil.sendMessage(sender, prefix + "&cUsage: /referral admin forceadd <player> <referrer>");
                    return true;
                }
                String targetPlayer = args[2];
                String referrerPlayer = args[3];
                
                plugin.getDatabaseManager().executeAsync(() -> {
                    try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                        String rCode = null;
                        try (PreparedStatement stmt = conn.prepareStatement("SELECT code FROM coresuite_referrals WHERE player_name = ?")) {
                            stmt.setString(1, referrerPlayer);
                            try (ResultSet rs = stmt.executeQuery()) {
                                if (rs.next()) rCode = rs.getString("code");
                            }
                        }
                        if (rCode == null) {
                            Bukkit.getScheduler().runTask(plugin, () -> TextUtil.sendMessage(sender, prefix + "&cReferrer doesn't have a code."));
                            return;
                        }
                        
                        plugin.getDatabaseManager().execute("UPDATE coresuite_referrals SET used_code = ?, referred_by = ? WHERE player_name = ?", rCode, referrerPlayer, targetPlayer);
                        
                        int count = 0;
                        try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM coresuite_referrals WHERE referred_by = ?")) {
                            stmt.setString(1, referrerPlayer);
                            try (ResultSet rs = stmt.executeQuery()) {
                                if (rs.next()) count = rs.getInt(1);
                            }
                        }
                        final int fCount = count;
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            giveReward(referrerPlayer, fCount, targetPlayer);
                            TextUtil.sendMessage(sender, prefix + "&aForce added referral: " + targetPlayer + " -> " + referrerPlayer);
                        });
                    } catch (SQLException e) {
                        plugin.getLogger().log(Level.SEVERE, "Failed forceadd", e);
                    }
                });
                return true;
            } else if (action.equals("setcount")) {
                if (args.length < 4) {
                    TextUtil.sendMessage(sender, prefix + "&cUsage: /referral admin setcount <player> <amount>");
                    return true;
                }
                String targetPlayer = args[2];
                int amount;
                try {
                    amount = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    TextUtil.sendMessage(sender, prefix + "&cInvalid amount.");
                    return true;
                }
                plugin.getDatabaseManager().executeAsync(() -> {
                    try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                        int current = 0;
                        try (PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM coresuite_referrals WHERE referred_by = ?")) {
                            stmt.setString(1, targetPlayer);
                            try (ResultSet rs = stmt.executeQuery()) {
                                if (rs.next()) current = rs.getInt(1);
                            }
                        }
                        if (current < amount) {
                            for (int i = 0; i < amount - current; i++) {
                                String dummyUuid = UUID.randomUUID().toString();
                                plugin.getDatabaseManager().execute("INSERT INTO coresuite_referrals (uuid, player_name, referred_by) VALUES (?, ?, ?)", dummyUuid, "dummy_" + dummyUuid.substring(0, 8), targetPlayer);
                            }
                        } else if (current > amount) {
                            plugin.getDatabaseManager().execute("DELETE FROM coresuite_referrals WHERE uuid IN (SELECT uuid FROM coresuite_referrals WHERE referred_by = ? LIMIT ?)", targetPlayer, current - amount);
                        }
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            TextUtil.sendMessage(sender, prefix + "&aSet " + targetPlayer + "'s referrals to " + amount + ".");
                        });
                    } catch (SQLException e) {
                        plugin.getLogger().log(Level.SEVERE, "Failed setcount", e);
                    }
                });
                return true;
            }
        }

        sendHelp(sender, prefix);
        return true;
    }

    private void sendHelp(CommandSender sender, String prefix) {
        TextUtil.sendMessage(sender, prefix + "&7-------------------------");
        TextUtil.sendMessage(sender, "&e/referral create &7- get your code");
        TextUtil.sendMessage(sender, "&e/referral gui &7- open the referral menu");
        TextUtil.sendMessage(sender, "&e/referral use <code> &7- redeem a code");
        TextUtil.sendMessage(sender, "&e/referral info [player] &7- view stats");
        TextUtil.sendMessage(sender, "&e/referral leaderboard &7- top referrers");
        TextUtil.sendMessage(sender, prefix + "&7-------------------------");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> subs = Arrays.asList("gui", "create", "use", "info", "leaderboard", "top");
            if (sender.hasPermission("coresuite.referral.admin")) {
                subs = new ArrayList<>(subs);
                subs.addAll(Arrays.asList("flags", "revoke", "admin"));
            }
            org.bukkit.util.StringUtil.copyPartialMatches(args[0], subs, completions);
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("info") || (args[0].equalsIgnoreCase("revoke") && sender.hasPermission("coresuite.referral.admin"))) {
                List<String> players = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) players.add(p.getName());
                org.bukkit.util.StringUtil.copyPartialMatches(args[1], players, completions);
            } else if (args[0].equalsIgnoreCase("admin") && sender.hasPermission("coresuite.referral.admin")) {
                org.bukkit.util.StringUtil.copyPartialMatches(args[1], Arrays.asList("forceadd", "setcount"), completions);
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("admin") && sender.hasPermission("coresuite.referral.admin")) {
                List<String> players = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) players.add(p.getName());
                org.bukkit.util.StringUtil.copyPartialMatches(args[2], players, completions);
            }
        } else if (args.length == 4) {
            if (args[0].equalsIgnoreCase("admin") && args[1].equalsIgnoreCase("forceadd") && sender.hasPermission("coresuite.referral.admin")) {
                List<String> players = new ArrayList<>();
                for (Player p : Bukkit.getOnlinePlayers()) players.add(p.getName());
                org.bukkit.util.StringUtil.copyPartialMatches(args[3], players, completions);
            }
        }
        return completions;
    }
}
