package net.rocky.coresuite.module.automessage;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import org.bukkit.command.TabCompleter;

public class AutoMessengerModule extends Module implements CommandExecutor, TabCompleter {

    private BukkitTask broadcastTask;
    private int currentIndex = 0;
    private boolean broadcastingEnabled = true;

    public AutoMessengerModule(CoreSuitePlugin plugin) {
        super(plugin, "AutoMessenger", "auto-messenger");
    }

    @Override
    public void onEnable() {
        PluginCommand command = plugin.getCommand("automsg");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
        startBroadcastTask();
    }

    @Override
    public void onDisable() {
        stopBroadcastTask();
        PluginCommand command = plugin.getCommand("automsg");
        if (command != null && command.getExecutor() == this) {
            command.setExecutor(null);
            command.setTabCompleter(null);
        }
    }

    private void startBroadcastTask() {
        stopBroadcastTask();
        int intervalSeconds = config.getInt("interval", 600);
        if (intervalSeconds <= 0) intervalSeconds = 600;

        broadcastTask = registerTask(Bukkit.getScheduler().runTaskTimer(plugin, this::broadcastNext, 200L, intervalSeconds * 20L));
    }

    private void stopBroadcastTask() {
        if (broadcastTask != null) {
            broadcastTask.cancel();
            broadcastTask = null;
        }
    }

    private void broadcastNext() {
        if (!broadcastingEnabled) return;

        int minPlayers = config.getInt("min-players", 1);
        if (Bukkit.getOnlinePlayers().size() < minPlayers) return;

        List<String> messages = config.getStringList("messages");
        if (messages.isEmpty()) return;

        if (currentIndex >= messages.size()) {
            currentIndex = 0;
        }

        String msg = messages.get(currentIndex);
        String prefix = config.getString("prefix", "&8[&6&lMeerMC&8]&r");
        String line = config.getString("line", "");

        if (!line.isEmpty()) {
            Bukkit.broadcast(TextUtil.colorize(line));
        }
        
        Bukkit.broadcast(TextUtil.colorize("  " + prefix + " &r" + msg));
        
        if (!line.isEmpty()) {
            Bukkit.broadcast(TextUtil.colorize(line));
        }

        currentIndex++;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("coresuite.automessage.admin")) {
            TextUtil.sendMessage(sender, "&cYou don't have permission to use this command.");
            return true;
        }

        String prefix = config.getString("prefix", "&8[&6&lMeerMC&8]&r");

        if (args.length == 0) {
            TextUtil.sendMessage(sender, prefix + " &cUsage: &f/automsg <reload/toggle/list/add/remove>");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                onReload();
                currentIndex = 0;
                TextUtil.sendMessage(sender, prefix + " &aAuto messages have been reloaded!");
                break;
            case "toggle":
                broadcastingEnabled = !broadcastingEnabled;
                if (broadcastingEnabled) {
                    TextUtil.sendMessage(sender, prefix + " &aAuto messages have been enabled.");
                } else {
                    TextUtil.sendMessage(sender, prefix + " &cAuto messages have been disabled.");
                }
                break;
            case "list":
                TextUtil.sendMessage(sender, "&6&lCurrent Auto Messages:");
                List<String> messages = config.getStringList("messages");
                for (int i = 0; i < messages.size(); i++) {
                    TextUtil.sendMessage(sender, "&7#" + (i + 1) + " &f" + messages.get(i));
                }
                break;
            case "add":
                if (args.length > 1) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 1; i < args.length; i++) {
                        sb.append(args[i]).append(" ");
                    }
                    String newMsg = sb.toString().trim();
                    List<String> msgs = config.getStringList("messages");
                    msgs.add(newMsg);
                    config.set("messages", msgs);
                    saveConfig();
                    TextUtil.sendMessage(sender, prefix + " &aMessage added! There are now &e" + msgs.size() + " &amessages.");
                } else {
                    TextUtil.sendMessage(sender, prefix + " &cUsage: /automsg add <message>");
                }
                break;
            case "remove":
                if (args.length > 1) {
                    try {
                        int num = Integer.parseInt(args[1]);
                        List<String> msgs = config.getStringList("messages");
                        if (num > 0 && num <= msgs.size()) {
                            msgs.remove(num - 1);
                            config.set("messages", msgs);
                            saveConfig();
                            TextUtil.sendMessage(sender, prefix + " &aMessage #" + num + " removed.");
                        } else {
                            TextUtil.sendMessage(sender, prefix + " &cNo message found with that number.");
                        }
                    } catch (NumberFormatException e) {
                        TextUtil.sendMessage(sender, prefix + " &cPlease provide a valid message number.");
                    }
                } else {
                    TextUtil.sendMessage(sender, prefix + " &cUsage: /automsg remove <number>");
                }
                break;
            case "force":
                broadcastNext();
                TextUtil.sendMessage(sender, prefix + " &aForced the next auto message to broadcast.");
                break;
            default:
                TextUtil.sendMessage(sender, prefix + " &cUsage: &f/automsg <reload/toggle/list/add/remove/force>");
                break;
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new java.util.ArrayList<>();
        if (args.length == 1) {
            org.bukkit.util.StringUtil.copyPartialMatches(args[0], java.util.Arrays.asList("reload", "toggle", "list", "add", "remove", "force"), completions);
            java.util.Collections.sort(completions);
        }
        return completions;
    }
}
