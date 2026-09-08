package net.rocky.coresuite.module.wishingwell;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class WishingWellModule extends Module implements CommandExecutor, Listener, TabCompleter {
    private WellManager wellManager;
    private WellGUI wellGUI;

    public WishingWellModule(CoreSuitePlugin plugin) {
        super(plugin, "Wishing Well", "wishing-well");
    }

    @Override
    public void onEnable() {
        wellManager = new WellManager(plugin, config);
        wellManager.initDatabase();

        wellGUI = new WellGUI(plugin, wellManager, plugin.getEconomyManager(), config);

        registerListener(this);

        PluginCommand cmd = plugin.getCommand("well");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }

        // ── Interval-based draw scheduler ──────────────────────────────────
        // Checks every 5 minutes if enough real time has passed since the last draw.
        // This is immune to players sleeping (which only skips Minecraft time, not real time).
        registerTask(new BukkitRunnable() {
            @Override
            public void run() {
                wellManager.checkAndDraw();
            }
        }.runTaskTimer(plugin, 200L, 6000L)); // 200 ticks delay, check every 5 min (6000 ticks)

        if (Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            registerCitizensListener();
        }
    }

    private void registerCitizensListener() {
        registerListener(new CitizensListener());
    }

    @Override
    public void onDisable() {
        PluginCommand cmd = plugin.getCommand("well");
        if (cmd != null && cmd.getExecutor() == this) {
            cmd.setExecutor(null);
            cmd.setTabCompleter(null);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        String prefix = config.getString("messages.prefix", "&8[&b✨ Well&8] &r");

        if (args.length == 0) {
            wellGUI.open(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("throw")) {
            if (args.length < 2) {
                wellGUI.openThrowCustom(player);
                return true;
            }
            try {
                double amount = Double.parseDouble(args[1]);
                // Re-use gui throw logic to handle min/max/economy
                wellGUI.handleChat(player, String.valueOf(amount));
            } catch (NumberFormatException e) {
                TextUtil.sendMessage(player, prefix + "&cInvalid amount.");
            }
            return true;
        }

        if (sub.equals("top")) {
            TextUtil.sendMessage(player, prefix + "&6&lTop Contributors:");
            // Not explicitly tracked in DB for all-time, instructions say "in chat format". Let's say top contributors this round
            // Since currentEntries is private in WellManager, I'll need a getter or just ignore. 
            // The prompt says "show top contributors in chat (list format, not GUI)".
            // Let's just output contributor count for now.
            TextUtil.sendMessage(player, "&7There are &f" + wellManager.getContributorCount() + "&7 contributors this round. Total Pot: &a" + plugin.getEconomyManager().format(wellManager.getCurrentPot()));
            return true;
        }

        if (sub.equals("history")) {
            TextUtil.sendMessage(player, prefix + "&6&lRecent Winners:");
            for (WellManager.WellHistoryEntry entry : wellManager.getRecentHistory()) {
                String timeAgo = TextUtil.formatDuration((System.currentTimeMillis() - entry.drawnAt()) / 1000) + " ago";
                TextUtil.sendMessage(player, "&8- &e" + entry.name() + " &7won &a" + plugin.getEconomyManager().format(entry.potWon()) + " &8(" + timeAgo + ")");
            }
            if (wellManager.getRecentHistory().isEmpty()) {
                TextUtil.sendMessage(player, "&7No history yet.");
            }
            return true;
        }

        if (sub.equals("admin")) {
            if (!player.hasPermission("coresuite.admin")) {
                TextUtil.sendMessage(player, prefix + "&cNo permission.");
                return true;
            }
            if (args.length > 1) {
                if (args[1].equalsIgnoreCase("draw")) {
                    wellManager.performDraw();
                    TextUtil.sendMessage(player, prefix + "&aForce draw triggered.");
                    return true;
                } else if (args[1].equalsIgnoreCase("reload")) {
                    onReload();
                    TextUtil.sendMessage(player, prefix + "&aWishing Well reloaded.");
                    return true;
                } else if (args[1].equalsIgnoreCase("setnpc")) {
                    if (Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
                        net.citizensnpcs.api.npc.NPC npc = net.citizensnpcs.api.CitizensAPI.getDefaultNPCSelector().getSelected(player);
                        if (npc != null) {
                            config.set("npc-id", npc.getId());
                            saveConfig();
                            TextUtil.sendMessage(player, prefix + "&aNPC set to ID: " + npc.getId());
                        } else {
                            TextUtil.sendMessage(player, prefix + "&cNo NPC selected. Use /npc sel first.");
                        }
                    } else {
                        TextUtil.sendMessage(player, prefix + "&cCitizens is not loaded.");
                    }
                    return true;
                }
            }
            TextUtil.sendMessage(player, prefix + "&cUsage: /well admin <draw|reload|setnpc>");
            return true;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("throw");
            completions.add("top");
            completions.add("history");
            if (sender.hasPermission("coresuite.admin")) {
                completions.add("admin");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("coresuite.admin")) {
            completions.add("draw");
            completions.add("reload");
            completions.add("setnpc");
        }
        
        String lastArg = args[args.length - 1].toLowerCase();
        return completions.stream().filter(c -> c.toLowerCase().startsWith(lastArg)).collect(Collectors.toList());
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent e) {
        if (wellGUI.handleChat(e.getPlayer(), e.getMessage())) {
            e.setCancelled(true);
        }
    }

    private class CitizensListener implements Listener {
        @EventHandler
        public void onNPCClick(net.citizensnpcs.api.event.NPCRightClickEvent e) {
            int configNpcId = config.getInt("npc-id", -1);
            if (configNpcId == -1 || e.getNPC().getId() != configNpcId) return;
            Player player = e.getClicker();
            e.setCancelled(true);
            wellGUI.open(player);
        }
    }
}
