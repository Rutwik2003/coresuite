package net.rocky.coresuite.module.playtimerewards;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.gui.GuiButton;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class PlaytimeRewardsModule extends Module implements CommandExecutor, Listener, TabCompleter {

    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> afkSecondsCache = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> adjustSecondsCache = new ConcurrentHashMap<>();
    
    private int afkTimeout;

    public PlaytimeRewardsModule(CoreSuitePlugin plugin) {
        super(plugin, "PlaytimeRewards", "playtime-rewards");
    }

    @Override
    public void onEnable() {
        afkTimeout = config.getInt("afk-timeout-seconds", 120);
        
        initDatabase();
        registerListener(this);

        PluginCommand command = plugin.getCommand("playtime");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        PluginCommand adminCommand = plugin.getCommand("playtimereward");
        if (adminCommand != null) {
            adminCommand.setExecutor(this);
            adminCommand.setTabCompleter(this);
        }

        startAfkTask();
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            saveData(p.getUniqueId());
        }
        
        PluginCommand command = plugin.getCommand("playtime");
        if (command != null && command.getExecutor() == this) {
            command.setExecutor(null);
            command.setTabCompleter(null);
        }
        PluginCommand adminCommand = plugin.getCommand("playtimereward");
        if (adminCommand != null && adminCommand.getExecutor() == this) {
            adminCommand.setExecutor(null);
            adminCommand.setTabCompleter(null);
        }
    }

    private void initDatabase() {
        plugin.getDatabaseManager().execute(
            "CREATE TABLE IF NOT EXISTS coresuite_playtime (" +
            "uuid TEXT PRIMARY KEY, " +
            "player_name TEXT, " +
            "afk_seconds INTEGER DEFAULT 0, " +
            "adjust_seconds INTEGER DEFAULT 0)"
        );

        plugin.getDatabaseManager().execute(
            "CREATE TABLE IF NOT EXISTS coresuite_playtime_claims (" +
            "uuid TEXT, " +
            "reward_id TEXT, " +
            "claim_time INTEGER, " +
            "PRIMARY KEY (uuid, reward_id))"
        );
    }

    private void startAfkTask() {
        registerTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                long last = lastActivity.getOrDefault(uuid, now);
                if (now - last > afkTimeout * 1000L) {
                    afkSecondsCache.put(uuid, afkSecondsCache.getOrDefault(uuid, 0) + 1);
                }
            }
        }, 20L, 20L)); // 1 second loop
        
        // Save task every 5 minutes
        registerTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            List<UUID> uuids = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                uuids.add(p.getUniqueId());
            }
            plugin.getDatabaseManager().executeAsync(() -> {
                for (UUID uuid : uuids) {
                    saveData(uuid);
                }
            });
        }, 6000L, 6000L));
    }

    private void saveData(UUID uuid) {
        int afk = afkSecondsCache.getOrDefault(uuid, 0);
        int adj = adjustSecondsCache.getOrDefault(uuid, 0);
        plugin.getDatabaseManager().execute("UPDATE coresuite_playtime SET afk_seconds = ?, adjust_seconds = ? WHERE uuid = ?", afk, adj, uuid.toString());
    }

    private void loadData(UUID uuid, String name) {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT afk_seconds, adjust_seconds FROM coresuite_playtime WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    afkSecondsCache.put(uuid, rs.getInt("afk_seconds"));
                    adjustSecondsCache.put(uuid, rs.getInt("adjust_seconds"));
                } else {
                    plugin.getDatabaseManager().execute("INSERT INTO coresuite_playtime (uuid, player_name) VALUES (?, ?)", uuid.toString(), name);
                    afkSecondsCache.put(uuid, 0);
                    adjustSecondsCache.put(uuid, 0);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load playtime data", e);
        }
    }

    public int getPlaytimeSeconds(OfflinePlayer player) {
        UUID uuid = player.getUniqueId();
        int rawTicks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        int rawSeconds = rawTicks / 20;
        int afk = afkSecondsCache.getOrDefault(uuid, 0);
        int adj = adjustSecondsCache.getOrDefault(uuid, 0);
        int total = rawSeconds - afk + adj;
        return Math.max(0, total);
    }

    private Map<String, Long> loadAllClaimTimes(UUID uuid) {
        Map<String, Long> map = new HashMap<>();
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT reward_id, claim_time FROM coresuite_playtime_claims WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    map.put(rs.getString("reward_id"), rs.getLong("claim_time"));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to load claim times", e);
        }
        return map;
    }

    private long getLastClaimTime(OfflinePlayer player, String rewardId) {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT claim_time FROM coresuite_playtime_claims WHERE uuid = ? AND reward_id = ?")) {
            stmt.setString(1, player.getUniqueId().toString());
            stmt.setString(2, rewardId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) return rs.getLong("claim_time");
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to check claim status", e);
        }
        return 0L;
    }

    private void markClaimed(OfflinePlayer player, String rewardId) {
        plugin.getDatabaseManager().execute("INSERT OR REPLACE INTO coresuite_playtime_claims (uuid, reward_id, claim_time) VALUES (?, ?, ?)", player.getUniqueId().toString(), rewardId, System.currentTimeMillis());
    }

    private void updateActivity(Player player) {
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        updateActivity(player);
        plugin.getDatabaseManager().executeAsync(() -> {
            loadData(player.getUniqueId(), player.getName());
        });
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        int afk = afkSecondsCache.getOrDefault(uuid, 0);
        int adj = adjustSecondsCache.getOrDefault(uuid, 0);
        plugin.getDatabaseManager().executeAsync(() -> {
            plugin.getDatabaseManager().execute("UPDATE coresuite_playtime SET afk_seconds = ?, adjust_seconds = ? WHERE uuid = ?", afk, adj, uuid.toString());
        });
        lastActivity.remove(uuid);
        afkSecondsCache.remove(uuid);
        adjustSecondsCache.remove(uuid);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (event.getFrom().getBlockX() != event.getTo().getBlockX() || event.getFrom().getBlockZ() != event.getTo().getBlockZ()) {
            updateActivity(event.getPlayer());
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        updateActivity(event.getPlayer());
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        updateActivity(event.getPlayer());
    }

    private String formatTime(int seconds) {
        int d = seconds / 86400;
        seconds %= 86400;
        int h = seconds / 3600;
        seconds %= 3600;
        int m = seconds / 60;
        int s = seconds % 60;

        StringBuilder out = new StringBuilder();
        if (d > 0) out.append(d).append("d ");
        if (h > 0 || d > 0) out.append(h).append("h ");
        if (m > 0 || h > 0 || d > 0) out.append(m).append("m ");
        out.append(s).append("s");
        return out.toString();
    }

    private String buildProgressBar(int percent) {
        int filled = percent / 5;
        StringBuilder bar = new StringBuilder("&a");
        for (int i = 1; i <= 20; i++) {
            if (i <= filled) {
                bar.append("|");
            } else {
                if (i == filled + 1) bar.append("&7|");
                else bar.append("|");
            }
        }
        return bar.toString();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("playtime")) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            
            if (!player.hasPermission("playtimereward.use")) {
                TextUtil.sendMessage(player, "&cNo permission.");
                return true;
            }

            if (args.length == 0 || args[0].equalsIgnoreCase("rewards") || args[0].equalsIgnoreCase("gui")) {
                openGui(player, 1);
                return true;
            }
            
            if (args[0].equalsIgnoreCase("stats")) {
                int played = getPlaytimeSeconds(player);
                TextUtil.sendMessage(player, "&8[&6&lPlaytime&e&lRewards&8] &r&7Your playtime: &e" + formatTime(played));
                TextUtil.sendMessage(player, "&7Use &e/playtime rewards &7to view and claim rewards.");
                return true;
            }

            if (args[0].equalsIgnoreCase("admin")) {
                if (!player.hasPermission("coresuite.playtimerewards.admin")) {
                    TextUtil.sendMessage(player, "&cNo permission.");
                    return true;
                }
                if (args.length < 3) {
                    TextUtil.sendMessage(player, "&cUsage: /playtime admin <add|remove|set> <player> <seconds> OR /playtime admin reset <player>");
                    return true;
                }
                String sub = args[1].toLowerCase();
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    TextUtil.sendMessage(player, "&cPlayer not found.");
                    return true;
                }
                UUID uuid = target.getUniqueId();
                if (sub.equals("reset")) {
                    afkSecondsCache.put(uuid, 0);
                    adjustSecondsCache.put(uuid, 0);
                    plugin.getDatabaseManager().execute("DELETE FROM coresuite_playtime WHERE uuid = ?", uuid.toString());
                    plugin.getDatabaseManager().execute("DELETE FROM coresuite_playtime_claims WHERE uuid = ?", uuid.toString());
                    TextUtil.sendMessage(player, "&aReset playtime data for " + target.getName() + ".");
                    return true;
                }
                
                if (args.length < 4) {
                    TextUtil.sendMessage(player, "&cUsage: /playtime admin " + sub + " <player> <seconds>");
                    return true;
                }
                int secs = 0;
                try {
                    secs = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    TextUtil.sendMessage(player, "&cInvalid number.");
                    return true;
                }
                
                int currentAdj = adjustSecondsCache.getOrDefault(uuid, 0);
                if (sub.equals("add")) {
                    adjustSecondsCache.put(uuid, currentAdj + secs);
                    TextUtil.sendMessage(player, "&aAdded " + secs + "s to " + target.getName() + ".");
                } else if (sub.equals("remove")) {
                    adjustSecondsCache.put(uuid, currentAdj - secs);
                    TextUtil.sendMessage(player, "&aRemoved " + secs + "s from " + target.getName() + ".");
                } else if (sub.equals("set")) {
                    int rawTicks = target.getStatistic(Statistic.PLAY_ONE_MINUTE);
                    int rawSeconds = rawTicks / 20;
                    int afk = afkSecondsCache.getOrDefault(uuid, 0);
                    adjustSecondsCache.put(uuid, secs - rawSeconds + afk);
                    TextUtil.sendMessage(player, "&aSet playtime for " + target.getName() + " to " + secs + "s.");
                } else {
                    TextUtil.sendMessage(player, "&cUnknown action.");
                }
                saveData(uuid);
                return true;
            }
        }
        
        if (command.getName().equalsIgnoreCase("playtimereward")) {
            if (!sender.hasPermission("playtimereward.admin")) {
                TextUtil.sendMessage(sender, "&cNo permission.");
                return true;
            }
            
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                this.loadConfig();
                afkTimeout = config.getInt("afk-timeout-seconds", 120);
                TextUtil.sendMessage(sender, "&aPlaytimeRewards config reloaded.");
                return true;
            }
            TextUtil.sendMessage(sender, "&cUsage: /playtimereward reload");
            return true;
        }

        return false;
    }

    private void openGui(Player player, int page) {
        plugin.getDatabaseManager().executeAsync(() -> {
            Map<String, Long> claimTimes = loadAllClaimTimes(player.getUniqueId());
            Bukkit.getScheduler().runTask(plugin, () -> {
                buildAndOpenGUI(player, page, claimTimes);
            });
        });
    }

    private void buildAndOpenGUI(Player player, int page, Map<String, Long> lastClaimMap) {
        ConfigurationSection rewardsSec = config.getConfigurationSection("rewards");
        if (rewardsSec == null) {
            TextUtil.sendMessage(player, "&cNo rewards configured.");
            return;
        }

        List<String> keys = new ArrayList<>(rewardsSec.getKeys(false));
        int total = keys.size();
        int perPage = config.getInt("rewards-per-page", 21);
        int maxPage = (int) Math.ceil((double) total / perPage);
        if (maxPage == 0) maxPage = 1;

        String title = config.getString("gui-title", "&8&lPlaytime Rewards");
        Gui gui = new Gui(title + " &8- Page " + page, 6);

        int played = getPlaytimeSeconds(player);
        Map<String, Integer> statusMap = new HashMap<>();

        for (String key : keys) {
            ConfigurationSection sec = rewardsSec.getConfigurationSection(key);
            if (sec == null) {
                statusMap.put(key, 3);
                continue;
            }
            long lastClaim = lastClaimMap.getOrDefault(key, 0L);
            
            int req = sec.getInt("required-seconds");
            if (played < req) {
                statusMap.put(key, 1);
            } else {
                if (lastClaim == 0) {
                    statusMap.put(key, 0);
                } else {
                    int cooldownHours = sec.getInt("cooldown-hours", 0);
                    if (cooldownHours > 0) {
                        if (System.currentTimeMillis() - lastClaim < cooldownHours * 3600000L) {
                            statusMap.put(key, 2);
                        } else {
                            statusMap.put(key, 0);
                        }
                    } else {
                        statusMap.put(key, 2);
                    }
                }
            }
        }

        int unlocked = 0;
        int claimed = 0;
        int claimable = 0;

        for (String key : keys) {
            int status = statusMap.getOrDefault(key, 3);
            if (status == 0) {
                unlocked++;
                claimable++;
            } else if (status == 2) {
                unlocked++;
                claimed++;
            }
        }

        keys.sort((k1, k2) -> {
            int status1 = statusMap.getOrDefault(k1, 3);
            int status2 = statusMap.getOrDefault(k2, 3);
            if (status1 != status2) return Integer.compare(status1, status2);
            ConfigurationSection sec1 = rewardsSec.getConfigurationSection(k1);
            ConfigurationSection sec2 = rewardsSec.getConfigurationSection(k2);
            int req1 = sec1 != null ? sec1.getInt("required-seconds") : 0;
            int req2 = sec2 != null ? sec2.getInt("required-seconds") : 0;
            return Integer.compare(req1, req2);
        });

        gui.setItem(4, new GuiButton(
            TextUtil.createItem(Material.PLAYER_HEAD, "&e&lYour Stats", 
                "&7Playtime: &f" + formatTime(played), 
                "&7Unlocked: &a" + unlocked, 
                "&7Claimed: &b" + claimed, 
                "&7Claimable now: &6" + claimable, 
                "&7Page: &f" + page),
            e -> {}
        ));

        int start = (page - 1) * perPage;
        int end = Math.min(start + perPage, total);

        int slotCursor = 9;
        for (int i = start; i < end; i++) {
            String id = keys.get(i);
            ConfigurationSection sec = rewardsSec.getConfigurationSection(id);
            if (sec == null) continue;

            int required = sec.getInt("required-seconds");
            String matStr = sec.getString("material", "STONE");
            Material mat = Material.matchMaterial(matStr);
            if (mat == null) mat = Material.STONE;
            String name = sec.getString("name", id);
            List<String> lore = sec.getStringList("lore");
            String grants = sec.getString("grants", "");
            
            int status = statusMap.getOrDefault(id, 3);
            
            if (status == 0) {
                List<String> claimLore = new ArrayList<>(lore);
                claimLore.add("&7Grants: " + grants);
                claimLore.add("&7Required: &f" + formatTime(required));
                claimLore.add("&a&l>> CLICK TO CLAIM <<");
                
                gui.setItem(slotCursor, new GuiButton(
                    TextUtil.createItem(mat, "&a&l" + name + " &e[Click to Claim]", claimLore.toArray(new String[0])),
                    e -> {
                        player.closeInventory();
                        if (claimReward(player, id, sec)) {
                            TextUtil.sendMessage(player, "&aSuccessfully claimed!");
                        }
                    }
                ));
            } else if (status == 1) {
                int remaining = required - played;
                int percent = (int) Math.round(((double) played / required) * 100);
                List<String> lockedLore = new ArrayList<>();
                lockedLore.add("&7Grants: " + grants);
                lockedLore.add("&7Required: &f" + formatTime(required));
                lockedLore.add("&7Your time: &f" + formatTime(played));
                lockedLore.add("&7Remaining: &c" + formatTime(remaining));
                lockedLore.add("&7Progress: &e" + percent + "%");
                lockedLore.add(buildProgressBar(percent));
                
                gui.setItem(slotCursor, new GuiButton(
                    TextUtil.createItem(Material.GRAY_DYE, "&7&l" + name + " &8[Locked]", lockedLore.toArray(new String[0])),
                    e -> TextUtil.sendMessage(player, "&cThis reward is still locked.")
                ));
            } else if (status == 2) {
                List<String> claimedLore = new ArrayList<>();
                claimedLore.add("&7Grants: " + grants);
                
                long lastClaim = lastClaimMap.getOrDefault(id, 0L);
                int cooldownHours = sec.getInt("cooldown-hours", 0);
                if (cooldownHours > 0) {
                    long cooldownMillis = cooldownHours * 3600000L;
                    long timeLeftMillis = (lastClaim + cooldownMillis) - System.currentTimeMillis();
                    int timeLeftSecs = (int) (timeLeftMillis / 1000);
                    claimedLore.add("&7Status: &cOn Cooldown");
                    claimedLore.add("&8You must wait &c" + formatTime(timeLeftSecs) + " &8to claim again.");
                } else {
                    claimedLore.add("&7Status: &8Already Claimed");
                    claimedLore.add("&8This reward has already been redeemed.");
                }
                
                gui.setItem(slotCursor, new GuiButton(
                    TextUtil.createItem(Material.NETHER_STAR, "&7&m" + name + "&r &8[Claimed]", claimedLore.toArray(new String[0])),
                    e -> TextUtil.sendMessage(player, "&cYou cannot claim this reward right now.")
                ));
            }
            slotCursor++;
        }

        gui.setItem(49, new GuiButton(TextUtil.createItem(Material.BARRIER, "&c&lClose"), e -> player.closeInventory()));
        
        gui.setItem(48, new GuiButton(
            TextUtil.createItem(Material.BOOK, "&b&lHelp / Info", "&7Earn rewards by staying active", "&7(non-AFK) on the server.", "&7Rewards unlock automatically", "&7as your playtime increases."),
            e -> {}
        ));
        
        final int currentPage = page;
        gui.setItem(50, new GuiButton(TextUtil.createItem(Material.COMPASS, "&a&lRefresh"), e -> openGui(player, currentPage)));

        gui.setItem(51, new GuiButton(
            TextUtil.createItem(Material.EMERALD_BLOCK, "&6&lClaim All Available", "&7Claims every unlocked reward", "&7you haven't claimed yet."),
            e -> {
                int c = 0;
                for (String key : keys) {
                    ConfigurationSection s = rewardsSec.getConfigurationSection(key);
                    if (s != null) {
                        int status = statusMap.getOrDefault(key, 3);
                        if (status == 0) {
                            if (claimReward(player, key, s)) c++;
                        }
                    }
                }
                player.closeInventory();
                if (c > 0) TextUtil.sendMessage(player, "&aClaimed " + c + " reward(s)!");
                else TextUtil.sendMessage(player, "&7No rewards available to claim right now.");
            }
        ));

        if (page > 1) {
            gui.setItem(45, new GuiButton(TextUtil.createItem(Material.ARROW, "&e« Previous Page"), e -> openGui(player, currentPage - 1)));
        }
        if (page < maxPage) {
            gui.setItem(53, new GuiButton(TextUtil.createItem(Material.ARROW, "&eNext Page »"), e -> openGui(player, currentPage + 1)));
        }

        gui.open(player);
    }
    private boolean claimReward(Player player, String id, ConfigurationSection sec) {
        // Validation is already done via statusMap before calling this method.

        // Perform DB write asynchronously to avoid main thread blocking
        plugin.getDatabaseManager().executeAsync(() -> markClaimed(player, id));

        double money = sec.getDouble("money", 0);
        if (money > 0) {
            plugin.getEconomyManager().deposit(player, money);
        }

        List<String> commands = sec.getStringList("commands");
        for (String cmd : commands) {
            String parsed = cmd.replace("{player}", player.getName());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed);
        }

        String claimMsg = sec.getString("claim-message");
        if (claimMsg != null && !claimMsg.isEmpty()) {
            TextUtil.sendMessage(player, "&8[&6&lPlaytime&e&lRewards&8] &r" + claimMsg);
        }

        String broadcastMsg = sec.getString("broadcast-message");
        if (broadcastMsg != null && !broadcastMsg.isEmpty()) {
            Bukkit.broadcast(TextUtil.colorize("&8[&6&lPlaytime&e&lRewards&8] &r" + broadcastMsg.replace("{player}", player.getName())));
        }

        String soundStr = sec.getString("sound");
        if (soundStr != null && !soundStr.isEmpty()) {
            try {
                Sound sound = Sound.valueOf(soundStr);
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException ignored) {}
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("playtime") && args.length == 1) {
            org.bukkit.util.StringUtil.copyPartialMatches(args[0], Arrays.asList("rewards", "gui", "stats", "admin"), completions);
        } else if (command.getName().equalsIgnoreCase("playtime") && args.length > 1 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("coresuite.playtimerewards.admin")) {
            if (args.length == 2) {
                org.bukkit.util.StringUtil.copyPartialMatches(args[1], Arrays.asList("add", "remove", "set", "reset"), completions);
            } else if (args.length == 3) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[2].toLowerCase())) {
                        completions.add(p.getName());
                    }
                }
            }
        } else if (command.getName().equalsIgnoreCase("playtimereward") && args.length == 1 && sender.hasPermission("playtimereward.admin")) {
            org.bukkit.util.StringUtil.copyPartialMatches(args[0], java.util.Collections.singletonList("reload"), completions);
        }
        return completions;
    }
}
