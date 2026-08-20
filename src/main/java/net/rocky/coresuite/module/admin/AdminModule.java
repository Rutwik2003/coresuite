package net.rocky.coresuite.module.admin;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.luckperms.api.node.types.InheritanceNode;
import net.luckperms.api.node.types.PermissionNode;
import net.luckperms.api.node.types.PrefixNode;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.*;
import java.util.stream.Collectors;

public class AdminModule extends Module implements CommandExecutor, TabCompleter {

    private boolean luckPermsAvailable = false;
    private LuckPerms luckPerms;

    public AdminModule(CoreSuitePlugin plugin) {
        super(plugin, "Admin", "admin");
    }

    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            luckPermsAvailable = true;
            luckPerms = LuckPermsProvider.get();
            log("LuckPerms integration enabled.");
        } else {
            warn("LuckPerms not found! Some /csadmin features will have limited functionality.");
        }

        PluginCommand cmd = plugin.getCommand("csadmin");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
    }

    @Override
    public void onDisable() {
        luckPermsAvailable = false;
        luckPerms = null;
    }

    private String getPrefix() {
        return getConfig().getString("prefix", "&8[&6&lAdmin&8] &r");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("coresuite.admin")) {
            TextUtil.sendMessage(sender, getPrefix() + getConfig().getString("messages.no-permission", "&cYou don't have permission to use this command."));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        
        switch (subCommand) {
            case "checkperms":
                if (args.length < 2) {
                    TextUtil.sendMessage(sender, getPrefix() + "&cUsage: /csadmin checkperms <player> [page]");
                    return true;
                }
                int page = 1;
                if (args.length >= 3) {
                    try {
                        page = Integer.parseInt(args[2]);
                    } catch (NumberFormatException ignored) {}
                }
                handleCheckPerms(sender, args[1], page);
                break;
            case "groupperms":
                if (args.length < 2) {
                    TextUtil.sendMessage(sender, getPrefix() + "&cUsage: /csadmin groupperms <group>");
                    return true;
                }
                handleGroupPerms(sender, args[1]);
                break;
            case "info":
                if (args.length < 2) {
                    TextUtil.sendMessage(sender, getPrefix() + "&cUsage: /csadmin info <player>");
                    return true;
                }
                handleInfo(sender, args[1]);
                break;
            case "groups":
                handleGroups(sender);
                break;
            case "modules":
                handleModules(sender);
                break;
            case "reload":
                handleReload(sender);
                break;
            case "luckperms":
                handleLuckPerms(sender);
                break;
            default:
                sendHelp(sender);
                break;
        }
        
        return true;
    }

    private void sendHelp(CommandSender sender) {
        List<String> helpLines = getConfig().getStringList("help");
        if (helpLines == null || helpLines.isEmpty()) {
            helpLines = Arrays.asList(
                "&8&m--------------------------------------------------",
                "&6&lCoreSuite Admin Commands",
                "&e/csadmin checkperms <player> [page] &7- View player perms",
                "&e/csadmin groupperms <group> &7- View group perms",
                "&e/csadmin info <player> &7- View player info",
                "&e/csadmin groups &7- List all groups",
                "&e/csadmin modules &7- List modules",
                "&e/csadmin reload &7- Reload config",
                "&e/csadmin luckperms &7- View default group perms",
                "&8&m--------------------------------------------------"
            );
        }
        for (String line : helpLines) {
            TextUtil.sendMessage(sender, line);
        }
    }

    private void handleCheckPerms(CommandSender sender, String playerName, int page) {
        int perPage = getConfig().getInt("permissions-per-page", 15);
        
        if (!luckPermsAvailable) {
            Player target = Bukkit.getPlayerExact(playerName);
            if (target == null) target = Bukkit.getPlayer(playerName);
            
            if (target == null) {
                TextUtil.sendMessage(sender, getPrefix() + "&cPlayer must be online when LuckPerms is not available.");
                return;
            }
            
            List<String> perms = new ArrayList<>();
            for (PermissionAttachmentInfo pai : target.getEffectivePermissions()) {
                String color = pai.getValue() ? "&a" : "&c";
                perms.add(color + pai.getPermission() + " &7(Bukkit)");
            }
            
            showPaginatedList(sender, perms, page, perPage, "Permissions for " + target.getName());
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            UUID targetId = Bukkit.getOfflinePlayer(playerName).getUniqueId();
            luckPerms.getUserManager().loadUser(targetId, playerName).thenAcceptAsync(user -> {
                if (user == null) {
                    TextUtil.sendMessage(sender, getPrefix() + getConfig().getString("messages.player-not-found", "&cPlayer not found."));
                    return;
                }

                List<String> perms = new ArrayList<>();
                Map<String, Boolean> resolved = user.getCachedData().getPermissionData().getPermissionMap();
                
                for (Map.Entry<String, Boolean> entry : resolved.entrySet()) {
                    String color = entry.getValue() ? "&a" : "&c";
                    perms.add(color + entry.getKey());
                }
                
                Collections.sort(perms);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    showPaginatedList(sender, perms, page, perPage, "Permissions for " + user.getUsername());
                });
            });
        });
    }

    private void handleGroupPerms(CommandSender sender, String groupName) {
        if (!luckPermsAvailable) {
            TextUtil.sendMessage(sender, getPrefix() + getConfig().getString("messages.luckperms-not-available", "&cLuckPerms is not available."));
            return;
        }

        Group group = luckPerms.getGroupManager().getGroup(groupName);
        if (group == null) {
            TextUtil.sendMessage(sender, getPrefix() + getConfig().getString("messages.group-not-found", "&cGroup not found."));
            return;
        }

        List<String> perms = new ArrayList<>();
        List<String> inherited = new ArrayList<>();

        for (Node node : group.getNodes()) {
            if (node instanceof PermissionNode) {
                String color = node.getValue() ? "&a" : "&c";
                perms.add(color + node.getKey());
            } else if (node instanceof InheritanceNode) {
                inherited.add("&e" + ((InheritanceNode) node).getGroupName());
            }
        }

        String header = getConfig().getString("header-line", "&8&m--------------------------------------------------");
        TextUtil.sendMessage(sender, header);
        TextUtil.sendMessage(sender, "&6&lGroup Permissions: &e" + group.getName());
        TextUtil.sendMessage(sender, "&7Inherits from: " + (inherited.isEmpty() ? "&cNone" : String.join("&7, ", inherited)));
        TextUtil.sendMessage(sender, "");
        for (String perm : perms) {
            TextUtil.sendMessage(sender, perm);
        }
        TextUtil.sendMessage(sender, header);
    }

    private void handleInfo(CommandSender sender, String playerName) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(playerName);
            String header = getConfig().getString("header-line", "&8&m--------------------------------------------------");
            
            TextUtil.sendMessage(sender, header);
            TextUtil.sendMessage(sender, "&6&lPlayer Info: &e" + offlinePlayer.getName());
            TextUtil.sendMessage(sender, "&7UUID: &f" + offlinePlayer.getUniqueId());
            
            Player onlinePlayer = offlinePlayer.getPlayer();
            if (onlinePlayer != null) {
                TextUtil.sendMessage(sender, "&7Status: &aOnline");
                TextUtil.sendMessage(sender, "&7IP: &f" + onlinePlayer.getAddress().getAddress().getHostAddress());
            } else {
                TextUtil.sendMessage(sender, "&7Status: &cOffline");
            }

            if (luckPermsAvailable) {
                luckPerms.getUserManager().loadUser(offlinePlayer.getUniqueId(), playerName).thenAcceptAsync(user -> {
                    if (user != null) {
                        String primaryGroup = user.getPrimaryGroup();
                        String prefix = user.getCachedData().getMetaData().getPrefix();
                        
                        List<String> groups = user.getNodes().stream()
                                .filter(n -> n instanceof InheritanceNode)
                                .map(n -> ((InheritanceNode) n).getGroupName())
                                .collect(Collectors.toList());

                        Bukkit.getScheduler().runTask(plugin, () -> {
                            TextUtil.sendMessage(sender, "&7Primary Group: &e" + primaryGroup);
                            TextUtil.sendMessage(sender, "&7Prefix: &f" + (prefix != null ? prefix : "None"));
                            TextUtil.sendMessage(sender, "&7All Groups: &e" + String.join("&7, &e", groups));
                            TextUtil.sendMessage(sender, header);
                        });
                    }
                });
            } else {
                TextUtil.sendMessage(sender, header);
            }
        });
    }

    private void handleGroups(CommandSender sender) {
        if (!luckPermsAvailable) {
            TextUtil.sendMessage(sender, getPrefix() + getConfig().getString("messages.luckperms-not-available", "&cLuckPerms is not available."));
            return;
        }

        Set<Group> groups = luckPerms.getGroupManager().getLoadedGroups();
        String header = getConfig().getString("header-line", "&8&m--------------------------------------------------");
        
        TextUtil.sendMessage(sender, header);
        TextUtil.sendMessage(sender, "&6&lLoaded Groups (" + groups.size() + ")");
        
        for (Group group : groups) {
            OptionalInt weight = group.getWeight();
            String weightStr = weight.isPresent() ? String.valueOf(weight.getAsInt()) : "None";
            // Getting accurate member count usually requires a DB query in LP, we will just show weight.
            TextUtil.sendMessage(sender, "&e" + group.getName() + " &7(Weight: " + weightStr + ")");
        }
        TextUtil.sendMessage(sender, header);
    }

    private void handleModules(CommandSender sender) {
        net.rocky.coresuite.module.ModuleManager mm = plugin.getModuleManager();
        Collection<Module> allModules = mm.getAllModules();
        long enabledCount = allModules.stream().filter(Module::isEnabled).count();

        String header = getConfig().getString("header-line", "&8&m--------------------------------------------------");
        TextUtil.sendMessage(sender, header);
        TextUtil.sendMessage(sender, "&6&lCoreSuite Modules &7(&f" + enabledCount + "&8/&f" + allModules.size() + " &7enabled)");

        for (Module module : allModules) {
            String indicator = module.isEnabled() ? "&a[✔]" : "&c[✘]";
            TextUtil.sendMessage(sender, " " + indicator + " &f" + module.getName() + " &8- &7" + module.getConfigKey());
        }
        TextUtil.sendMessage(sender, header);
    }

    private void handleReload(CommandSender sender) {
        plugin.reloadConfig();
        net.rocky.coresuite.module.ModuleManager mm = plugin.getModuleManager();
        for (Module module : mm.getAllModules()) {
            if (module.isEnabled()) {
                mm.reloadModule(module.getConfigKey());
            }
        }
        TextUtil.sendMessage(sender, getPrefix() + "&aSuccessfully reloaded master configuration and all enabled modules.");
    }

    private void handleLuckPerms(CommandSender sender) {
        if (!luckPermsAvailable) {
            TextUtil.sendMessage(sender, getPrefix() + "&cLuckPerms is not available.");
            return;
        }

        Group group = luckPerms.getGroupManager().getGroup("default");
        if (group == null) {
            TextUtil.sendMessage(sender, getPrefix() + "&cDefault group not found.");
            return;
        }

        List<String> perms = new ArrayList<>();
        for (Node node : group.getNodes()) {
            if (node instanceof PermissionNode) {
                String color = node.getValue() ? "&a" : "&c";
                perms.add(color + node.getKey());
            }
        }

        String header = getConfig().getString("header-line", "&8&m--------------------------------------------------");
        TextUtil.sendMessage(sender, header);
        TextUtil.sendMessage(sender, "&6&lDefault Group Permissions");
        TextUtil.sendMessage(sender, "");
        for (String perm : perms) {
            TextUtil.sendMessage(sender, perm);
        }
        TextUtil.sendMessage(sender, header);
    }

    private void showPaginatedList(CommandSender sender, List<String> list, int page, int perPage, String title) {
        int maxPages = (int) Math.ceil((double) list.size() / perPage);
        if (maxPages == 0) maxPages = 1;
        
        if (page < 1) page = 1;
        if (page > maxPages) page = maxPages;

        int startIndex = (page - 1) * perPage;
        int endIndex = Math.min(startIndex + perPage, list.size());

        String header = getConfig().getString("header-line", "&8&m--------------------------------------------------");
        TextUtil.sendMessage(sender, header);
        TextUtil.sendMessage(sender, "&6&l" + title + " &7(Page " + page + "/" + maxPages + ")");
        
        if (list.isEmpty()) {
            TextUtil.sendMessage(sender, "&cNo entries found.");
        } else {
            for (int i = startIndex; i < endIndex; i++) {
                TextUtil.sendMessage(sender, list.get(i));
            }
        }
        TextUtil.sendMessage(sender, header);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (!sender.hasPermission("coresuite.admin")) return completions;

        if (args.length == 1) {
            List<String> subCommands = Arrays.asList("checkperms", "groupperms", "info", "groups", "modules", "reload", "luckperms");
            for (String sub : subCommands) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
        } else if (args.length == 2) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("checkperms") || subCommand.equals("info")) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (p.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(p.getName());
                    }
                }
            } else if (subCommand.equals("groupperms") && luckPermsAvailable) {
                for (Group group : luckPerms.getGroupManager().getLoadedGroups()) {
                    if (group.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        completions.add(group.getName());
                    }
                }
            }
        }

        return completions;
    }
}
