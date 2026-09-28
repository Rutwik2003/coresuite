package net.rocky.coresuite.module.rewardcodes;

import org.bukkit.configuration.file.FileConfiguration;
import java.util.*;

/** Validates edits before mutating configuration; never executes reward commands. */
public final class RewardCodeEditor {
    public static final List<String> ACTIONS = List.of("create", "codes", "info", "addcommand", "setcommand", "removecommand", "enable", "disable", "setlimit");
    public record Result(boolean changed, List<String> messages) {}
    public static List<String> codes(FileConfiguration config) {
        var codes = new ArrayList<String>();
        codes.add(config.getString("code", "M7X2Q9"));
        var section = config.getConfigurationSection("codes");
        if (section != null) codes.addAll(section.getKeys(false));
        return codes;
    }
    public static String resolve(FileConfiguration config, String input) {
        return codes(config).stream().filter(code -> code.equalsIgnoreCase(input)).findFirst().orElse(null);
    }
    public static String prefix(FileConfiguration config, String code) {
        return code.equalsIgnoreCase(config.getString("code", "M7X2Q9")) ? "" : "codes." + code + ".";
    }
    private static int limit(String value) {
        int number;
        try { number = Integer.parseInt(value); } catch (NumberFormatException e) { throw new IllegalArgumentException("Use a whole-number limit: 0 means unlimited."); }
        if (number < 0) throw new IllegalArgumentException("Limit must be 0 (unlimited) or greater.");
        return number;
    }
    private static String command(String[] args, int start) {
        String command = String.join(" ", Arrays.copyOfRange(args, start, args.length)).trim();
        if (command.startsWith("/")) command = command.substring(1).trim();
        if (command.isBlank() || command.contains("\n") || command.contains("\r")) throw new IllegalArgumentException("Enter one console command, with %player% for the recipient.");
        return command;
    }
    public static Result edit(FileConfiguration config, String[] args) {
        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("codes")) return new Result(false, List.of("&eReward codes: &f" + String.join(", ", codes(config)), "&7Use /rewardadmin info <code> to inspect rewards."));
        if (args.length < 2) throw new IllegalArgumentException("Usage: /rewardadmin " + action + " <code>");
        String code = resolve(config, args[1]);
        if (action.equals("create")) {
            if (!args[1].matches("[A-Za-z0-9_-]{1,48}")) throw new IllegalArgumentException("Codes may contain 1–48 letters, numbers, hyphens or underscores.");
            if (code != null) throw new IllegalArgumentException("That code already exists. Use addcommand to append a reward.");
            int max = args.length >= 3 ? limit(args[2]) : 0;
            List<String> commands = args.length >= 4 ? List.of(command(args, 3)) : List.of();
            String path = "codes." + args[1] + ".";
            config.set(path + "max-uses", max);
            config.set(path + "commands", commands);
            config.set(path + "enabled", !commands.isEmpty());
            return new Result(true, List.of("&aCreated " + args[1] + (commands.isEmpty() ? " as a disabled draft." : "."),
                    "&7/rewardadmin addcommand " + args[1] + " <console command>", "&7/rewardadmin enable " + args[1]));
        }
        if (code == null) throw new IllegalArgumentException("Unknown code. Use /rewardadmin codes.");
        String path = prefix(config, code);
        var commands = new ArrayList<>(config.getStringList(path + "commands"));
        if (action.equals("info")) {
            var lines = new ArrayList<String>();
            lines.add("&e" + code + " &7— " + (config.getBoolean(path + "enabled", true) ? "enabled" : "disabled")
                    + ", limit: " + config.getInt(path + "max-uses", 0) + " (0 = unlimited)");
            for (int i = 0; i < commands.size(); i++) lines.add("&e" + (i + 1) + ". &f" + commands.get(i));
            if (path.isEmpty()) lines.add("&7Money: " + config.getDouble("money-reward", 0));
            if (commands.isEmpty()) lines.add("&7No commands configured.");
            return new Result(false, lines);
        }
        switch (action) {
            case "addcommand" -> { if (args.length < 3) throw new IllegalArgumentException("Usage: /rewardadmin addcommand <code> <command...>"); commands.add(command(args, 2)); }
            case "removecommand", "setcommand" -> {
                if (args.length < 3) throw new IllegalArgumentException("Specify the command number shown by /rewardadmin info " + code);
                int index = limit(args[2]) - 1;
                if (index < 0 || index >= commands.size()) throw new IllegalArgumentException("Command number is outside the list. Use /rewardadmin info " + code);
                if (action.equals("removecommand")) commands.remove(index);
                else { if (args.length < 4) throw new IllegalArgumentException("Usage: /rewardadmin setcommand <code> <number> <command...>"); commands.set(index, command(args, 3)); }
            }
            case "enable", "disable" -> {
                if (action.equals("enable") && commands.isEmpty() && !(path.isEmpty() && config.getDouble("money-reward", 0) > 0))
                    throw new IllegalArgumentException("Add a reward before enabling this code.");
                config.set(path + "enabled", action.equals("enable"));
            }
            case "setlimit" -> {
                if (args.length != 3) throw new IllegalArgumentException("Usage: /rewardadmin setlimit <code> <max-uses; 0 = unlimited>");
                config.set(path + "max-uses", limit(args[2]));
            }
            default -> throw new IllegalArgumentException("Unknown editor action.");
        }
        if (action.endsWith("command")) config.set(path + "commands", commands);
        return new Result(true, List.of("&aUpdated " + code + ". &7Use /rewardadmin info " + code + " to review."));
    }
}
