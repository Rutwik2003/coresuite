package net.rocky.coresuite.module.autorestarter;

import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AutoRestarterCommand implements CommandExecutor, TabCompleter {

    private final AutoRestarterModule module;

    public AutoRestarterCommand(AutoRestarterModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("coresuite.admin")) {
            TextUtil.sendMessage(sender, "&cYou do not have permission to use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String prefix = module.getConfig().getString("prefix", "&8[&c&lAutoRestart&8] &r");
        String sub = args[0].toLowerCase();
        RestartTask task = module.getRestartTask();

        switch (sub) {
            case "now":
                if (task != null) {
                    int seconds = 5;
                    if (args.length > 1) {
                        int parsed = parseDuration(args[1]);
                        if (parsed > 0) {
                            seconds = parsed;
                        } else {
                            TextUtil.sendMessage(sender, prefix + "&cInvalid duration format. Use 1h, 30m, 5s etc.");
                            return true;
                        }
                    }
                    task.forceRestart(seconds);
                    org.bukkit.Bukkit.broadcast(TextUtil.colorize(prefix + "&cAn admin forced a restart! Restarting in " + formatSeconds(seconds) + "."));
                } else {
                    TextUtil.sendMessage(sender, prefix + "&cRestart task is not running.");
                }
                break;
            case "cancel":
                if (task != null) {
                    task.cancelRestart();
                    org.bukkit.Bukkit.broadcast(TextUtil.colorize(prefix + "&aAn admin cancelled the autorestart, reverting back to original schedule."));
                } else {
                    TextUtil.sendMessage(sender, prefix + "&cRestart task is not running.");
                }
                break;
            case "set":
                if (args.length < 2) {
                    TextUtil.sendMessage(sender, prefix + "&cUsage: /autorestart set <duration> (e.g. 1h, 30m, 5s)");
                    return true;
                }
                int seconds = parseDuration(args[1]);
                if (seconds > 0) {
                    if (task != null) {
                        task.forceRestart(seconds);
                        TextUtil.sendMessage(sender, prefix + "&aRestart scheduled in " + args[1] + ".");
                    } else {
                        TextUtil.sendMessage(sender, prefix + "&cRestart task is not running.");
                    }
                } else {
                    TextUtil.sendMessage(sender, prefix + "&cInvalid duration format. Use 1h, 30m, etc.");
                }
                break;
            case "time":
                if (task != null) {
                    long left = task.getSecondsUntilNextRestart();
                    if (left > 0) {
                        TextUtil.sendMessage(sender, prefix + "&aNext restart is in: &e" + formatSeconds(left));
                    } else {
                        TextUtil.sendMessage(sender, prefix + "&aRestart is happening right now!");
                    }
                } else {
                    TextUtil.sendMessage(sender, prefix + "&cRestart task is not running.");
                }
                break;
            default:
                sendHelp(sender);
                break;
        }

        return true;
    }

    private void sendHelp(CommandSender sender) {
        String header = module.getConfig().getString("header-line", "&8&m--------------------------------------------------");
        TextUtil.sendMessage(sender, header);
        TextUtil.sendMessage(sender, "&6&lAutoRestarter Commands:");
        TextUtil.sendMessage(sender, "&e/autorestart now &7- Restart in 5 seconds (failsafe)");
        TextUtil.sendMessage(sender, "&e/autorestart cancel &7- Cancel an impending restart");
        TextUtil.sendMessage(sender, "&e/autorestart set <time> &7- Schedule a restart (e.g., 30m)");
        TextUtil.sendMessage(sender, "&e/autorestart time &7- Check time until next restart");
        TextUtil.sendMessage(sender, header);
    }

    private int parseDuration(String input) {
        Pattern pattern = Pattern.compile("(\\d+)([hms])");
        Matcher matcher = pattern.matcher(input.toLowerCase());
        int totalSeconds = 0;
        boolean found = false;
        while (matcher.find()) {
            found = true;
            int amount = Integer.parseInt(matcher.group(1));
            String unit = matcher.group(2);
            switch (unit) {
                case "h": totalSeconds += amount * 3600; break;
                case "m": totalSeconds += amount * 60; break;
                case "s": totalSeconds += amount; break;
            }
        }
        return found ? totalSeconds : -1;
    }

    private String formatSeconds(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) return hours + "h " + minutes + "m " + secs + "s";
        if (minutes > 0) return minutes + "m " + secs + "s";
        return secs + "s";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("coresuite.admin")) return new ArrayList<>();

        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            for (String s : Arrays.asList("now", "cancel", "set", "time")) {
                if (s.startsWith(args[0].toLowerCase())) completions.add(s);
            }
            return completions;
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("now"))) {
            return Arrays.asList("1h", "30m", "10m", "5m", "1m", "15s", "5s");
        }
        return new ArrayList<>();
    }
}
