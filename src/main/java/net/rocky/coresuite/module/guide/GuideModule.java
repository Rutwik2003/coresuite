package net.rocky.coresuite.module.guide;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.title.Title;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.integration.AuthenticationGate;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Player Guide & Onboarding Module.
 * Provides interactive /guide GUI, first-time player walkthrough,
 * and exploit-safe starter rewards.
 */
public class GuideModule extends Module implements Listener, CommandExecutor, TabCompleter {

    private GuideRewardManager rewardManager;
    private final Map<String, GuideEntry> entries = new LinkedHashMap<>();

    public GuideModule(CoreSuitePlugin plugin) {
        super(plugin, "Guide", "guide");
    }

    @Override
    public void onEnable() {
        rewardManager = new GuideRewardManager(plugin, this);
        rewardManager.initDatabase();

        registerListener(this);
        loadEntries();

        String[] commands = {"guide", "guideadmin"};
        for (String cmdName : commands) {
            PluginCommand cmd = plugin.getCommand(cmdName);
            if (cmd != null) {
                cmd.setExecutor(this);
                cmd.setTabCompleter(this);
            }
        }
    }

    @Override
    public void onDisable() {
        String[] commands = {"guide", "guideadmin"};
        for (String cmdName : commands) {
            PluginCommand cmd = plugin.getCommand(cmdName);
            if (cmd != null && cmd.getExecutor() == this) {
                cmd.setExecutor(null);
                cmd.setTabCompleter(null);
            }
        }
        entries.clear();
    }

    @Override
    public void onReload() {
        loadConfig();
        loadEntries();
        log("Player guide configuration and entries reloaded.");
    }

    public void loadEntries() {
        entries.clear();
        ConfigurationSection sec = config.getConfigurationSection("entries");
        if (sec == null) return;

        for (String key : sec.getKeys(false)) {
            ConfigurationSection itemSec = sec.getConfigurationSection(key);
            if (itemSec == null) continue;

            String name = itemSec.getString("name", key);
            String command = itemSec.getString("command", key);
            String matName = itemSec.getString("material", "BOOK");
            Material material = Material.matchMaterial(matName);
            if (material == null) material = Material.BOOK;

            int slot = itemSec.getInt("slot", 0);
            List<String> description = itemSec.getStringList("description");
            List<String> chatExplanation = itemSec.getStringList("chat-explanation");

            entries.put(key.toLowerCase(), new GuideEntry(key, name, command, material, slot, description, chatExplanation));
        }
    }

    public Map<String, GuideEntry> getEntries() {
        return Collections.unmodifiableMap(entries);
    }

    public GuideRewardManager getRewardManager() {
        return rewardManager;
    }

    public String getPrefix() {
        return config.getString("prefix", "&8[&6&lGuide&8] &r");
    }

    // ── First-Time Join Walkthrough ────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String ip = player.getAddress() != null && player.getAddress().getAddress() != null
                ? player.getAddress().getAddress().getHostAddress()
                : "unknown";

        // Load claim data asynchronously
        rewardManager.loadPlayer(player.getUniqueId(), null);
        rewardManager.recordFirstSeen(player.getUniqueId(), ip);

        // Check first join
        boolean firstJoinEnabled = config.getBoolean("first-join.enabled", true);
        if (!firstJoinEnabled) return;

        boolean isNewPlayer = !player.hasPlayedBefore();
        boolean onlyFirstJoin = config.getBoolean("first-join.only-first-join", true);
        if (onlyFirstJoin && !isNewPlayer) {
            return;
        }

        // Wait until AuthMe login is completed before welcoming
        AuthenticationGate.afterLogin(plugin, player, () -> {
            if (!player.isOnline()) return;

            int delayTicks = config.getInt("first-join.authme-delay-ticks", 40);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!player.isOnline()) return;

                // Welcome sound
                String soundName = config.getString("first-join.sound", "UI_TOAST_CHALLENGE_COMPLETE");
                if (soundName != null && !soundName.isBlank()) {
                    try {
                        Sound sound = Sound.valueOf(soundName);
                        player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
                    } catch (IllegalArgumentException ignored) {}
                }

                // Welcome screen title (can be disabled via title-enabled: false)
                boolean titleEnabled = config.getBoolean("first-join.title-enabled", true);
                String title = config.getString("first-join.title", "&6&lWELCOME TO THE SERVER!");
                if (titleEnabled && title != null && !title.isBlank()) {
                    String subtitle = config.getString("first-join.subtitle", "&eType &b/guide &eto view the player manual");
                    player.showTitle(Title.title(
                            TextUtil.colorize(title),
                            TextUtil.colorize(subtitle.replace("%player%", player.getName())),
                            Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofMillis(500))
                    ));
                }

                // Welcome chat banner
                List<String> messages = config.getStringList("first-join.welcome-message");
                for (String msg : messages) {
                    if (msg.contains("CLICK HERE TO OPEN SERVER GUIDE")) {
                        Component openBtn = TextUtil.colorize(msg)
                                .clickEvent(ClickEvent.runCommand("/guide"))
                                .hoverEvent(HoverEvent.showText(TextUtil.colorize("&aClick to open the interactive &b/guide &amenu!")));
                        TextUtil.sendMessage(player, openBtn);
                    } else {
                        TextUtil.sendMessage(player, msg.replace("%player%", player.getName()));
                    }
                }

                // Optional auto-open GUI
                if (isNewPlayer && config.getBoolean("first-join.auto-open-gui", false)) {
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (player.isOnline()) {
                            GuideGui.open(player, this);
                        }
                    }, 40L);
                }
            }, delayTicks);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        rewardManager.removePlayerFromCache(event.getPlayer().getUniqueId());
    }

    // ── Command Handling ───────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("guide")) {
            return handleGuideCommand(sender, args);
        } else if (command.getName().equalsIgnoreCase("guideadmin")) {
            return handleGuideAdminCommand(sender, args);
        }
        return false;
    }

    private boolean handleGuideCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            TextUtil.sendMessage(sender, getPrefix() + "&cThe /guide command must be executed by a player in-game.");
            return true;
        }

        if (!player.hasPermission("coresuite.guide.use")) {
            TextUtil.sendMessage(player, getPrefix() + "&cYou do not have permission to view the guide.");
            return true;
        }

        if (args.length == 0) {
            GuideGui.open(player, this);
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("claim")) {
            rewardManager.claimStarterReward(player, null);
            return true;
        }

        if (sub.equals("help")) {
            TextUtil.sendMessage(player, getPrefix() + "&eUsage: &b/guide &7(opens GUI) or &b/guide <command>");
            TextUtil.sendMessage(player, "&7Available topics: &f" + String.join(", ", entries.keySet()));
            return true;
        }

        GuideEntry entry = entries.get(sub);
        if (entry != null) {
            for (String line : entry.chatExplanation()) {
                if (line.contains("▶ Click to run:")) {
                    Component interactive = TextUtil.colorize(line)
                            .clickEvent(ClickEvent.runCommand("/" + entry.command()))
                            .hoverEvent(HoverEvent.showText(TextUtil.colorize("&aClick to run &b/" + entry.command())));
                    TextUtil.sendMessage(player, interactive);
                } else {
                    TextUtil.sendMessage(player, line);
                }
            }
            return true;
        }

        TextUtil.sendMessage(player, getPrefix() + "&cUnknown guide topic: &e" + args[0] + "&c. Type &b/guide &cto browse all.");
        return true;
    }

    private boolean handleGuideAdminCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("coresuite.guide.admin") && !sender.isOp()) {
            TextUtil.sendMessage(sender, getPrefix() + "&cYou do not have permission to use /guideadmin.");
            return true;
        }

        if (args.length == 0) {
            TextUtil.sendMessage(sender, getPrefix() + "&eGuide Admin Commands:");
            TextUtil.sendMessage(sender, "  &b/guideadmin reload &7- Reload configuration and guide cards");
            TextUtil.sendMessage(sender, "  &b/guideadmin reset <player> &7- Reset a player's starter reward claim");
            TextUtil.sendMessage(sender, "  &b/guideadmin claim <player> &7- Claim starter reward on behalf of player");
            TextUtil.sendMessage(sender, "  &b/guideadmin info <player> &7- Check a player's claim status");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "reload" -> {
                onReload();
                TextUtil.sendMessage(sender, getPrefix() + "&aGuide module configuration and entries reloaded.");
                return true;
            }
            case "reset" -> {
                if (args.length < 2) {
                    TextUtil.sendMessage(sender, getPrefix() + "&cUsage: /guideadmin reset <player>");
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                rewardManager.resetPlayer(target.getUniqueId(), () -> {
                    TextUtil.sendMessage(sender, getPrefix() + "&aStarter reward claim state reset for &e" + target.getName() + "&a.");
                });
                return true;
            }
            case "claim" -> {
                if (args.length < 2) {
                    TextUtil.sendMessage(sender, getPrefix() + "&cUsage: /guideadmin claim <player>");
                    return true;
                }
                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    TextUtil.sendMessage(sender, getPrefix() + "&cPlayer &e" + args[1] + " &cis not online.");
                    return true;
                }
                rewardManager.claimStarterReward(target, () -> {
                    TextUtil.sendMessage(sender, getPrefix() + "&aStarter reward granted to &e" + target.getName() + "&a.");
                });
                return true;
            }
            case "info" -> {
                if (args.length < 2) {
                    TextUtil.sendMessage(sender, getPrefix() + "&cUsage: /guideadmin info <player>");
                    return true;
                }
                OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
                boolean claimed = rewardManager.isClaimed(target.getUniqueId());
                TextUtil.sendMessage(sender, getPrefix() + "&7Player: &e" + target.getName());
                TextUtil.sendMessage(sender, "  &7Starter Gift Claimed: " + (claimed ? "&aYes" : "&cNo"));
                return true;
            }
            default -> {
                TextUtil.sendMessage(sender, getPrefix() + "&cUnknown subcommand. Type &b/guideadmin &cfor help.");
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("guide")) {
            if (args.length == 1) {
                List<String> options = new ArrayList<>(entries.keySet());
                options.add("claim");
                options.add("help");
                for (String opt : options) {
                    if (opt.toLowerCase().startsWith(args[0].toLowerCase())) {
                        completions.add(opt);
                    }
                }
            }
        } else if (command.getName().equalsIgnoreCase("guideadmin")) {
            if (!sender.hasPermission("coresuite.guide.admin") && !sender.isOp()) {
                return Collections.emptyList();
            }
            if (args.length == 1) {
                for (String s : Arrays.asList("reload", "reset", "claim", "info")) {
                    if (s.startsWith(args[0].toLowerCase())) {
                        completions.add(s);
                    }
                }
            } else if (args.length == 2 && Arrays.asList("reset", "claim", "info").contains(args[0].toLowerCase())) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(p.getName());
                    }
                }
            }
        }
        return completions;
    }
}
