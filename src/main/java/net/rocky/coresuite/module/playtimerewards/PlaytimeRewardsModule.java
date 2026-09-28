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
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class PlaytimeRewardsModule extends Module implements CommandExecutor, Listener, TabCompleter {

    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> afkSecondsCache = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> adjustSecondsCache = new ConcurrentHashMap<>();
    
    private int afkTimeout;
    private volatile long generation;
    private final Map<UUID, Object> sessions = new ConcurrentHashMap<>();
    private final Set<UUID> loaded = ConcurrentHashMap.newKeySet();
    private final Set<UUID> openingMenus = ConcurrentHashMap.newKeySet();
    private final Set<String> pendingClaims = ConcurrentHashMap.newKeySet();

    public PlaytimeRewardsModule(CoreSuitePlugin plugin) {
        super(plugin, "PlaytimeRewards", "playtime-rewards");
    }

    @Override
    public void onEnable() {
        afkTimeout = Math.max(10, config.getInt("afk-timeout-seconds", 120));
        
        String oldFinalMessage = "&6&l[!] &e{player} has played for 300 hours and claimed the final reward!";
        if (oldFinalMessage.equals(config.getString("rewards.reward_300h.broadcast-message"))) {
            config.set("rewards.reward_300h.broadcast-message", "&a{player} has claimed their 300 Hour Reward!");
            saveConfig();
        }
        if (PlaytimeRewardDefaults.upgrade(config)) saveConfig();
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

        generation++;
        for (Player player : Bukkit.getOnlinePlayers()) startSession(player);
        startAfkTask();
    }

    @Override
    public void onDisable() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            saveData(p.getUniqueId());
        }
        
        generation++;
        sessions.clear();
        loaded.clear();
        lastActivity.clear();
        afkSecondsCache.clear();
        adjustSecondsCache.clear();
        openingMenus.clear();
        pendingClaims.clear();
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
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection c = plugin.getDatabaseManager().getConnection()) {
                PlaytimeStore.initialize(c);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Cannot initialize playtime storage", e);
            }
        });
    }

    private void startAfkTask() {
        registerTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                if (loaded.contains(uuid) && now - lastActivity.getOrDefault(uuid, now) > afkTimeout * 1000L) {
                    afkSecondsCache.compute(uuid, (key, value) -> value == null ? 1 : (int) Math.min(Integer.MAX_VALUE, (long) value + 1));
                }
            }
        }, 20L, 20L));
        registerTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Snapshot on the server thread, before logout can clear the caches.
            for (Player player : Bukkit.getOnlinePlayers()) saveData(player.getUniqueId());
        }, 6000L, 6000L));
    }

    private void saveData(UUID uuid) {
        if (!loaded.contains(uuid)) return;
        int afk = afkSecondsCache.getOrDefault(uuid, 0);
        int adj = adjustSecondsCache.getOrDefault(uuid, 0);
        plugin.getDatabaseManager().execute("UPDATE coresuite_playtime SET afk_seconds = ?, adjust_seconds = ? WHERE uuid = ?", afk, adj, uuid.toString());
    }

    private void startSession(Player player) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        Object session = new Object();
        sessions.put(uuid, session);
        long epoch = generation;
        lastActivity.put(uuid, System.currentTimeMillis());
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection c = plugin.getDatabaseManager().getConnection()) {
                PlaytimeStore.Data data = PlaytimeStore.load(c, uuid, name, plugin.getDatabaseManager().isMySQL());
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (generation != epoch || sessions.get(uuid) != session || !player.isOnline()) return;
                    afkSecondsCache.put(uuid, data.afk());
                    adjustSecondsCache.put(uuid, data.adjustment());
                    loaded.add(uuid);
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load playtime for " + uuid + "; claims disabled until reconnect", e);
            }
        });
    }

    public int getPlaytimeSeconds(OfflinePlayer player) {
        UUID uuid = player.getUniqueId();
        int rawTicks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        int rawSeconds = rawTicks / 20;
        int afk = afkSecondsCache.getOrDefault(uuid, 0);
        int adj = adjustSecondsCache.getOrDefault(uuid, 0);
        long total = (long) rawSeconds - afk + adj;
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, total));
    }

    private void updateActivity(Player player) {
        lastActivity.computeIfPresent(player.getUniqueId(), (uuid, previous) -> System.currentTimeMillis());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        startSession(event.getPlayer());
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        saveData(uuid);
        sessions.remove(uuid);
        loaded.remove(uuid);
        openingMenus.remove(uuid);
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

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        updateActivity(event.getPlayer());
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
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
                if (!loaded.contains(uuid) || pendingClaims.stream().anyMatch(key -> key.startsWith(uuid + ":"))) {
                    TextUtil.sendMessage(player, "&cPlayer data is loading or a reward is being processed. Try again shortly.");
                    return true;
                }
                if (sub.equals("reset")) {
                    afkSecondsCache.put(uuid, 0);
                    adjustSecondsCache.put(uuid, -target.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20);
                    saveData(uuid);
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
                
                if (secs < 0) {
                    TextUtil.sendMessage(player, "&cSeconds must be non-negative.");
                    return true;
                }
                int currentAdj = adjustSecondsCache.getOrDefault(uuid, 0);
                if (sub.equals("add")) {
                    adjustSecondsCache.put(uuid, (int) Math.min(Integer.MAX_VALUE, (long) currentAdj + secs));
                    TextUtil.sendMessage(player, "&aAdded " + secs + "s to " + target.getName() + ".");
                } else if (sub.equals("remove")) {
                    adjustSecondsCache.put(uuid, (int) Math.max(Integer.MIN_VALUE, (long) currentAdj - secs));
                    TextUtil.sendMessage(player, "&aRemoved " + secs + "s from " + target.getName() + ".");
                } else if (sub.equals("set")) {
                    int rawTicks = target.getStatistic(Statistic.PLAY_ONE_MINUTE);
                    int rawSeconds = rawTicks / 20;
                    int afk = afkSecondsCache.getOrDefault(uuid, 0);
                    adjustSecondsCache.put(uuid, (int) Math.min(Integer.MAX_VALUE, (long) secs - rawSeconds + afk));
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
            
            if (args.length > 0 && (args[0].equalsIgnoreCase("review") || args[0].equalsIgnoreCase("resolve"))) {
                reviewClaims(sender, args);
                return true;
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                onReload();
                TextUtil.sendMessage(sender, "&aPlaytimeRewards config reloaded.");
                return true;
            }
            TextUtil.sendMessage(sender, "&cUsage: /playtimereward reload | review | resolve <uuid> <reward-id> <delivered|retry>");
            return true;
        }

        return false;
    }

    private void reviewClaims(CommandSender sender, String[] args) {
        boolean resolve = args[0].equalsIgnoreCase("resolve");
        UUID recipient = null;
        if (resolve) {
            if (args.length != 4 || !(args[3].equalsIgnoreCase("delivered") || args[3].equalsIgnoreCase("retry"))) {
                TextUtil.sendMessage(sender, "&cUsage: /playtimereward resolve <uuid> <reward-id> <delivered|retry>");
                return;
            }
            try { recipient = UUID.fromString(args[1]); }
            catch (IllegalArgumentException error) { TextUtil.sendMessage(sender, "&cInvalid UUID."); return; }
            if (pendingClaims.contains(recipient + ":" + args[2])) {
                TextUtil.sendMessage(sender, "&cThat claim is still processing. Wait before reviewing it.");
                return;
            }
        }
        UUID uuid = recipient;
        plugin.getDatabaseManager().executeAsync(() -> {
            List<String> lines = new ArrayList<>();
            try (Connection c = plugin.getDatabaseManager().getConnection()) {
                if (resolve) {
                    try (java.sql.PreparedStatement q = c.prepareStatement("UPDATE coresuite_playtime_claims SET claim_time=? WHERE uuid=? AND reward_id=? AND claim_time<0")) {
                        q.setLong(1, args[3].equalsIgnoreCase("retry") ? 0 : System.currentTimeMillis());
                        q.setString(2, uuid.toString()); q.setString(3, args[2]);
                        lines.add(q.executeUpdate() == 1 ? "&aClaim reconciled. Retry unlocks the reward; it does not deliver automatically." : "&eNo reserved claim matched.");
                    }
                } else {
                    lines.add("&6Reserved playtime claims: verify what was received before resolving. Retry may duplicate a partial payout.");
                    try (java.sql.PreparedStatement q = c.prepareStatement("SELECT uuid,reward_id FROM coresuite_playtime_claims WHERE claim_time<0 LIMIT 25");
                         java.sql.ResultSet rows = q.executeQuery()) {
                        while (rows.next()) lines.add("&e" + rows.getString(1) + " &7" + rows.getString(2));
                    }
                    if (lines.size() == 1) lines.add("&7No reserved claims.");
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Cannot review playtime receipts", e);
                lines.add("&cCannot read or reconcile claims. Check the console.");
            }
            if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> lines.forEach(line -> TextUtil.sendMessage(sender, line)));
        });
    }

    private void openGui(Player player, int page) {
        UUID uuid = player.getUniqueId();
        if (!loaded.contains(uuid)) {
            TextUtil.sendMessage(player, "&eYour playtime data is still loading. Please try again shortly.");
            return;
        }
        if (!openingMenus.add(uuid)) return;
        Object session = sessions.get(uuid);
        long epoch = generation;
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection c = plugin.getDatabaseManager().getConnection()) {
                Map<String, Long> claims = PlaytimeStore.claims(c, uuid);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (generation != epoch || sessions.get(uuid) != session) return;
                    openingMenus.remove(uuid);
                    if (player.isOnline()) buildAndOpenGUI(player, page, claims);
                });
            } catch (SQLException e) {
                openingMenus.remove(uuid);
                plugin.getLogger().log(Level.SEVERE, "Could not read playtime claims for " + uuid, e);
            }
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
        int perPage = Math.max(1, Math.min(36, config.getInt("rewards-per-page", 21)));
        int maxPage = (int) Math.ceil((double) total / perPage);
        if (maxPage == 0) maxPage = 1;

        page = Math.max(1, Math.min(page, maxPage));
        long menuGeneration = generation;
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
                    if (cooldownHours > 0 && lastClaim > 0) {
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
                        if (menuGeneration == generation) claimReward(player, id, sec);
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
                if (lastClaim < 0) {
                    claimedLore.add("&eDelivery pending review. Contact an administrator.");
                } else if (cooldownHours > 0) {
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
                if (menuGeneration != generation) return;
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
                if (c > 0) TextUtil.sendMessage(player, "&eProcessing " + c + " reward(s)...");
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
        UUID uuid = player.getUniqueId();
        if (!isEnabled() || !loaded.contains(uuid) || getPlaytimeSeconds(player) < sec.getInt("required-seconds")) return false;
        String key = uuid + ":" + id;
        if (!pendingClaims.add(key)) return false;
        long epoch = generation;
        Object session = sessions.get(uuid);
        long now = System.currentTimeMillis();
        long cooldown = Math.max(0, sec.getLong("cooldown-hours", 0));
        cooldown = Math.min(cooldown, Integer.MAX_VALUE) * 3600000L;
        final long delay = cooldown;
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection c = plugin.getDatabaseManager().getConnection()) {
                if (!PlaytimeStore.reserve(c, uuid, id, now, delay, plugin.getDatabaseManager().isMySQL())) {
                    pendingClaims.remove(key);
                    return;
                }
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (generation != epoch || sessions.get(uuid) != session || !player.isOnline()) {
                        finishClaim(uuid, id, now, 0, key); // No payout started: safe to retry.
                        return;
                    }
                    try {
                        deliverReward(player, sec);
                        finishClaim(uuid, id, now, now, key);
                    } catch (RuntimeException e) {
                        pendingClaims.remove(key);
                        plugin.getLogger().log(Level.SEVERE, "Playtime delivery needs manual review: " + key + ". Reservation retained to prevent duplicate rewards.", e);
                        TextUtil.sendMessage(player, "&cReward delivery needs administrator review. Your claim has been recorded.");
                    }
                });
            } catch (SQLException | RuntimeException e) {
                pendingClaims.remove(key);
                plugin.getLogger().log(Level.SEVERE, "Playtime claim failed or needs review: " + key, e);
            }
        });
        return true;
    }

    private void finishClaim(UUID uuid, String id, long now, long result, String key) {
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection c = plugin.getDatabaseManager().getConnection()) {
                PlaytimeStore.finish(c, uuid, id, now, result);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Playtime receipt needs manual review: " + key, e);
            } finally {
                pendingClaims.remove(key);
            }
        });
    }

    private void deliverReward(Player player, ConfigurationSection sec) {
        double money = sec.getDouble("money", 0);
        if (money > 0) {
            if (!plugin.getEconomyManager().deposit(player, money)) throw new IllegalStateException("Economy rejected playtime payout");
        }

        List<String> commands = sec.getStringList("commands");
        for (String cmd : commands) {
            String parsed = PlaytimeRewardDefaults.commandFor(cmd, player.getName());
            if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), parsed)) throw new IllegalStateException("Reward command rejected: " + parsed);
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
                Sound sound = net.rocky.coresuite.common.compat.PaperCompat.sound(soundStr);
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            } catch (IllegalArgumentException ignored) {}
        }

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
            org.bukkit.util.StringUtil.copyPartialMatches(args[0], java.util.List.of("reload", "review", "resolve"), completions);
        }
        return completions;
    }
}
