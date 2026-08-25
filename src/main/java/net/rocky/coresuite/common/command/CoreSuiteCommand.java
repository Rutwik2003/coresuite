/*
 * CoreSuite — Professional PaperMC Plugin
 * by Rocky_Rutwik
 */
package net.rocky.coresuite.common.command;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.Module;
import net.rocky.coresuite.module.ModuleManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.PluginDescriptionFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Handles the main {@code /coresuite} (alias {@code /cs}) administration command.
 * <p>
 * Subcommands: help, modules, reload, version, enable, disable.
 */
public final class CoreSuiteCommand implements CommandExecutor, TabCompleter {

    private final CoreSuitePlugin plugin;
    private static final String PREFIX = "&8[&6&lCoreSuite&8] &r";

    /**
     * Constructs a new CoreSuiteCommand.
     *
     * @param plugin The main plugin instance
     */
    public CoreSuiteCommand(CoreSuitePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("coresuite.admin")) {
            TextUtil.sendMessage(sender, PREFIX + "&cYou do not have permission to use this command.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendHelp(sender, args);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "commands"-> sendCommands(sender);
            case "modules" -> sendModules(sender);
            case "reload"  -> handleReload(sender, args);
            case "version" -> sendVersion(sender);
            case "enable"  -> handleModuleStateChange(sender, args, true);
            case "disable" -> handleModuleStateChange(sender, args, false);
            default -> TextUtil.sendMessage(sender,
                PREFIX + "&cUnknown subcommand. Type /coresuite help for a list of commands.");
        }

        return true;
    }

    private void sendHelp(CommandSender sender, String[] args) {
        int page = 1;
        if (args.length > 1) {
            try {
                page = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {}
        }

        List<String> helpLines = Arrays.asList(
            " &e/coresuite help [page] &8- &7Show this help menu",
            " &e/coresuite modules &8- &7List all modules",
            " &e/coresuite commands &8- &7List all plugin commands",
            " &e/coresuite reload [module] &8- &7Reload configuration",
            " &e/coresuite enable <module> &8- &7Enable a module",
            " &e/coresuite disable <module> &8- &7Disable a module",
            " &e/coresuite version &8- &7Show plugin info"
        );

        int perPage = 5;
        int maxPages = (int) Math.ceil((double) helpLines.size() / perPage);
        if (page < 1) page = 1;
        if (page > maxPages) page = maxPages;

        TextUtil.sendMessage(sender, "&8&m--------------------------------------------------");
        TextUtil.sendMessage(sender, "&6&lCoreSuite Help &7(Page " + page + "/" + maxPages + ")");

        int start = (page - 1) * perPage;
        int end = Math.min(start + perPage, helpLines.size());

        for (int i = start; i < end; i++) {
            TextUtil.sendMessage(sender, helpLines.get(i));
        }
        TextUtil.sendMessage(sender, "&8&m--------------------------------------------------");
    }

    private void sendCommands(CommandSender sender) {
        TextUtil.sendMessage(sender, PREFIX + "&7All Plugin Commands:");
        var commands = plugin.getDescription().getCommands();
        if (commands != null) {
            for (String cmdName : commands.keySet()) {
                if (cmdName.equalsIgnoreCase("coresuite")) continue; // Skip main command
                
                var cmdData = commands.get(cmdName);
                String desc = (cmdData != null && cmdData.get("description") != null) 
                    ? cmdData.get("description").toString() 
                    : "No description";
                
                TextUtil.sendMessage(sender, " &e/" + cmdName + " &8- &7" + desc);
            }
        }
    }

    private void sendModules(CommandSender sender) {
        ModuleManager mm = plugin.getModuleManager();
        Collection<Module> allModules = mm.getAllModules();
        long enabledCount = allModules.stream().filter(Module::isEnabled).count();

        TextUtil.sendMessage(sender, "&8&m--------------------------------------------------");
        TextUtil.sendMessage(sender, "&6&lCoreSuite Modules &7(&f" + enabledCount + "&8/&f" + allModules.size() + " &7enabled)");

        for (Module module : allModules) {
            String indicator = module.isEnabled() ? "&a[✔]" : "&c[✘]";
            TextUtil.sendMessage(sender, " " + indicator + " &f" + module.getName() + " &8- &7Key: " + module.getConfigKey());
        }
        TextUtil.sendMessage(sender, "&8&m--------------------------------------------------");
    }

    private void handleReload(CommandSender sender, String[] args) {
        ModuleManager mm = plugin.getModuleManager();

        if (args.length > 1) {
            String configKey = args[1].toLowerCase();
            Module module = mm.getModule(configKey);

            if (module == null) {
                TextUtil.sendMessage(sender, PREFIX + "&cModule '" + configKey + "' not found.");
                return;
            }

            if (!module.isEnabled()) {
                TextUtil.sendMessage(sender, PREFIX + "&cCannot reload disabled module '" + configKey + "'.");
                return;
            }

            org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    mm.reloadModule(configKey);
                    TextUtil.sendMessage(sender, PREFIX + "&aSuccessfully reloaded module '&f" + module.getName() + "&a'.");
                });
            });
        } else {
            org.bukkit.Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                plugin.reloadConfig();
                plugin.getConfig().options().copyDefaults(true);
                plugin.saveConfig();
                
                org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
                    org.bukkit.configuration.ConfigurationSection section = plugin.getConfig().getConfigurationSection("modules");
                    if (section != null) {
                        for (Module module : mm.getAllModules()) {
                            boolean shouldBeEnabled = section.getBoolean(module.getConfigKey(), false);
                            if (shouldBeEnabled && !module.isEnabled()) {
                                mm.enableModule(module.getConfigKey());
                            } else if (!shouldBeEnabled && module.isEnabled()) {
                                mm.disableModule(module.getConfigKey());
                            } else if (shouldBeEnabled && module.isEnabled()) {
                                mm.reloadModule(module.getConfigKey());
                            }
                        }
                    }
                    TextUtil.sendMessage(sender, PREFIX + "&aSuccessfully reloaded master configuration and all enabled modules.");
                });
            });
        }
    }

    private void sendVersion(CommandSender sender) {
        PluginDescriptionFile desc = plugin.getDescription();
        String version = desc.getVersion();
        String author = desc.getAuthors().isEmpty() ? "Unknown" : String.join(", ", desc.getAuthors());
        String serverVersion = plugin.getServer().getVersion();
        String javaVersion = System.getProperty("java.version");

        ModuleManager mm = plugin.getModuleManager();
        long enabledCount = mm.getAllModules().stream().filter(Module::isEnabled).count();
        int totalModules = mm.getAllModules().size();

        TextUtil.sendMessage(sender, PREFIX + "&7CoreSuite v&f" + version);
        TextUtil.sendMessage(sender, " &7Author: &f" + author);
        TextUtil.sendMessage(sender, " &7Server: &f" + serverVersion);
        TextUtil.sendMessage(sender, " &7Java: &f" + javaVersion);
        TextUtil.sendMessage(sender, " &7Modules: &f" + enabledCount + "&8/&f" + totalModules + " &7enabled");
    }

    private void handleModuleStateChange(CommandSender sender, String[] args, boolean enable) {
        if (args.length < 2) {
            TextUtil.sendMessage(sender,
                PREFIX + "&cUsage: /coresuite " + (enable ? "enable" : "disable") + " <module>");
            return;
        }

        String configKey = args[1].toLowerCase();
        ModuleManager mm = plugin.getModuleManager();
        Module module = mm.getModule(configKey);

        if (module == null) {
            TextUtil.sendMessage(sender, PREFIX + "&cModule '" + configKey + "' not found.");
            return;
        }

        if (enable) {
            if (module.isEnabled()) {
                TextUtil.sendMessage(sender,
                    PREFIX + "&cModule '" + configKey + "' is already enabled.");
                return;
            }
            boolean success = mm.enableModule(configKey);
            if (success) {
                plugin.getConfig().set("modules." + configKey, true);
                plugin.saveConfig();
                TextUtil.sendMessage(sender,
                    PREFIX + "&aSuccessfully enabled module '&f" + module.getName() + "&a'.");
            } else {
                TextUtil.sendMessage(sender,
                    PREFIX + "&cFailed to enable module '" + configKey + "'. Check console for errors.");
            }
        } else {
            if (!module.isEnabled()) {
                TextUtil.sendMessage(sender,
                    PREFIX + "&cModule '" + configKey + "' is already disabled.");
                return;
            }
            boolean success = mm.disableModule(configKey);
            if (success) {
                plugin.getConfig().set("modules." + configKey, false);
                plugin.saveConfig();
                TextUtil.sendMessage(sender,
                    PREFIX + "&aSuccessfully disabled module '&f" + module.getName() + "&a'.");
            } else {
                TextUtil.sendMessage(sender,
                    PREFIX + "&cFailed to disable module '" + configKey + "'.");
            }
        }
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("coresuite.admin")) {
            return List.of();
        }

        if (args.length == 1) {
            return Arrays.asList("help", "commands", "modules", "reload", "version", "enable", "disable")
                .stream()
                .filter(cmd -> cmd.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2) {
            ModuleManager mm = plugin.getModuleManager();
            String sub = args[0].toLowerCase();

            return switch (sub) {
                case "reload" -> mm.getAllModules().stream()
                    .filter(Module::isEnabled)
                    .map(Module::getConfigKey)
                    .filter(key -> key.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
                case "enable" -> mm.getAllModules().stream()
                    .filter(m -> !m.isEnabled())
                    .map(Module::getConfigKey)
                    .filter(key -> key.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
                case "disable" -> mm.getAllModules().stream()
                    .filter(Module::isEnabled)
                    .map(Module::getConfigKey)
                    .filter(key -> key.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
                default -> List.of();
            };
        }

        return List.of();
    }
}
