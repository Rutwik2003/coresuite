package net.rocky.coresuite.module.jumppad;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.command.TabCompleter;

public class JumpPadModule extends Module implements Listener, CommandExecutor, TabCompleter {

    private static class JumpPad {
        String name;
        Location pad;
        Location dest;
    }

    private final Map<String, JumpPad> padsByLocation = new HashMap<>();
    private final Map<String, JumpPad> padsByName = new HashMap<>();
    private final Map<UUID, Long> cooldowns = new HashMap<>();
    private final Map<UUID, String> editingPad = new HashMap<>();

    public JumpPadModule(CoreSuitePlugin plugin) {
        super(plugin, "JumpPad", "jump-pad");
    }

    @Override
    public void onEnable() {
        registerListener(this);

        PluginCommand command = plugin.getCommand("launchpad");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }

        loadPads();
    }

    @Override
    public void onDisable() {
        PluginCommand command = plugin.getCommand("launchpad");
        if (command != null && command.getExecutor() == this) {
            command.setExecutor(null);
            command.setTabCompleter(null);
        }
        padsByLocation.clear();
        padsByName.clear();
        cooldowns.clear();
        editingPad.clear();
    }

    private void loadPads() {
        padsByLocation.clear();
        padsByName.clear();
        
        ConfigurationSection section = config.getConfigurationSection("pads");
        if (section == null) return;

        for (String key : section.getKeys(false)) {
            ConfigurationSection padSec = section.getConfigurationSection(key);
            if (padSec != null) {
                JumpPad pad = new JumpPad();
                pad.name = key;
                pad.pad = locFromString(padSec.getString("pad"));
                pad.dest = locFromString(padSec.getString("dest"));
                
                padsByName.put(key.toLowerCase(), pad);
                if (pad.pad != null) {
                    padsByLocation.put(blockLocToString(pad.pad), pad);
                }
            }
        }
    }

    private void savePads() {
        config.set("pads", null);
        for (JumpPad pad : padsByName.values()) {
            config.set("pads." + pad.name + ".pad", locToString(pad.pad));
            config.set("pads." + pad.name + ".dest", locToString(pad.dest));
        }
        saveConfig();
    }

    private String locToString(Location loc) {
        if (loc == null) return null;
        return loc.getWorld().getName() + ";" + loc.getX() + ";" + loc.getY() + ";" + loc.getZ() + ";" + loc.getYaw() + ";" + loc.getPitch();
    }

    private String blockLocToString(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        return loc.getWorld().getName() + ";" + loc.getBlockX() + ";" + loc.getBlockY() + ";" + loc.getBlockZ();
    }

    private Location locFromString(String str) {
        if (str == null || str.isEmpty()) return null;
        String[] parts = str.split(";");
        if (parts.length == 6) {
            World w = Bukkit.getWorld(parts[0]);
            if (w != null) {
                try {
                    return new Location(w, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]), Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) return;

        Player player = event.getPlayer();
        Location blockBelow = player.getLocation().getBlock().getRelative(0, -1, 0).getLocation();

        JumpPad pad = padsByLocation.get(blockLocToString(blockBelow));
        if (pad != null && pad.dest != null) {
            // Check cooldown
            long now = System.currentTimeMillis();
            long cd = cooldowns.getOrDefault(player.getUniqueId(), 0L);
            int cooldownSeconds = config.getInt("cooldown", 2);
            
            if (now < cd + (cooldownSeconds * 1000L)) {
                return;
            }
            cooldowns.put(player.getUniqueId(), now);

            launchPlayer(player, pad.dest);
        }
    }

    @EventHandler
    public void onQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        editingPad.remove(uuid);
        cooldowns.remove(uuid);
    }

    private void launchPlayer(Player player, Location dest) {
        TextUtil.sendActionBar(player, config.getString("launch-message", "&b&lLaunching..."));
        
        try {
            Sound sound = Sound.valueOf(config.getString("launch-sound", "ENTITY_FIREWORK_ROCKET_LAUNCH"));
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (IllegalArgumentException e) {
            // Invalid sound in config
        }

        Location from = player.getLocation();
        double dx = dest.getX() - from.getX();
        double dy = dest.getY() - from.getY();
        double dz = dest.getZ() - from.getZ();

        double distanceXZ = Math.sqrt(dx * dx + dz * dz);
        if (distanceXZ == 0 && dy == 0) {
            return;
        }

        // Exact physics math for standard Minecraft player in air
        double g = 0.08;
        double dragV = 0.98;
        double dragH = 0.91; // vanilla player horizontal drag

        // Heuristic for time of flight
        int t = (int) (Math.max(20.0, distanceXZ * 0.5 + 10.0));
        
        // Find a flight time 't' that provides a positive upward arc if possible
        double powV, term1V, term2V, vy = 0;
        while (t < 200) {
            powV = Math.pow(dragV, t);
            term1V = dragV * (1 - powV) / (1 - dragV);
            term2V = g * dragV / (1 - dragV) * (t - term1V);
            vy = (dy + term2V) / term1V;
            
            if (vy > 0.5) break;
            t += 5;
        }

        // Final recalculation with determined t
        powV = Math.pow(dragV, t);
        term1V = dragV * (1 - powV) / (1 - dragV);
        term2V = g * dragV / (1 - dragV) * (t - term1V);
        
        double powH = Math.pow(dragH, t);
        double term1H = dragH * (1 - powH) / (1 - dragH);

        double vx = dx / term1H;
        double vz = dz / term1H;
        vy = (dy + term2V) / term1V;

        Vector vec = new Vector(vx, vy, vz);
        
        // Cap the velocity at Minecraft's typical maximum to avoid server anti-cheat clamping
        // although exact landing won't be possible beyond this limit.
        if (vec.lengthSquared() > 16.0) {
            vec.normalize().multiply(4.0);
        }

        player.setVelocity(vec);

        // Prevent fall damage for a few seconds
        Bukkit.getScheduler().runTaskLater(plugin, () -> player.setFallDistance(0), config.getLong("fall-damage-grace-ticks", 60L));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String prefix = config.getString("prefix", "&8[&b&lJumpPad&8]&r");

        if (!sender.hasPermission("coresuite.jumppad.admin")) {
            TextUtil.sendMessage(sender, prefix + " &cYou don't have permission to manage launchpads.");
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can manage jump pads.");
            return true;
        }

        Player player = (Player) sender;

        if (args.length == 0) {
            sendHelp(player, prefix);
            return true;
        }

        String sub = args[0].toLowerCase();
        
        if (sub.equals("create")) {
            if (args.length < 2) {
                TextUtil.sendMessage(player, prefix + " &c/launchpad create <name>");
                return true;
            }
            String name = args[1];
            editingPad.put(player.getUniqueId(), name);
            TextUtil.sendMessage(player, prefix + " &aNow editing launchpad: &e" + name);
            TextUtil.sendMessage(player, prefix + " &7Run &f/launchpad setpad &7and &f/launchpad setdest");
            return true;
        }
        
        if (sub.equals("setpad")) {
            String name = editingPad.get(player.getUniqueId());
            if (name == null) {
                TextUtil.sendMessage(player, prefix + " &cYou must create/edit a pad first: /launchpad create <name>");
                return true;
            }
            JumpPad pad = padsByName.computeIfAbsent(name.toLowerCase(), k -> {
                JumpPad p = new JumpPad();
                p.name = name;
                return p;
            });

            // Remove old pad location
            if (pad.pad != null) {
                padsByLocation.remove(blockLocToString(pad.pad));
            }

            pad.pad = player.getLocation().getBlock().getRelative(0, -1, 0).getLocation();
            padsByLocation.put(blockLocToString(pad.pad), pad);
            savePads();

            TextUtil.sendMessage(player, prefix + " &aPad set for &e" + name + "&a at your feet.");
            TextUtil.sendMessage(player, prefix + " &7Go to the landing spot and run &f/launchpad setdest");
            return true;
        }
        
        if (sub.equals("setdest")) {
            String name = editingPad.get(player.getUniqueId());
            if (name == null) {
                TextUtil.sendMessage(player, prefix + " &cYou must create/edit a pad first: /launchpad create <name>");
                return true;
            }
            JumpPad pad = padsByName.get(name.toLowerCase());
            if (pad == null || pad.pad == null) {
                TextUtil.sendMessage(player, prefix + " &cNo pad found or set for &e" + name + "&c. Run /launchpad setpad first.");
                return true;
            }

            pad.dest = player.getLocation();
            savePads();

            TextUtil.sendMessage(player, prefix + " &aDestination set! Launchpad &e" + name + " &ais now &2active&a.");
            return true;
        }
        
        if (sub.equals("delete")) {
            if (args.length < 2) {
                TextUtil.sendMessage(player, prefix + " &c/launchpad delete <name>");
                return true;
            }
            String name = args[1];
            JumpPad pad = padsByName.remove(name.toLowerCase());
            if (pad != null) {
                if (pad.pad != null) {
                    padsByLocation.remove(blockLocToString(pad.pad));
                }
                savePads();
                TextUtil.sendMessage(player, prefix + " &cLaunchpad &e" + name + " &cdeleted.");
            } else {
                TextUtil.sendMessage(player, prefix + " &cNo launchpad named &e" + name + "&c found.");
            }
            return true;
        }

        if (sub.equals("list")) {
            if (padsByName.isEmpty()) {
                TextUtil.sendMessage(player, prefix + " &7No launchpads configured yet.");
                return true;
            }
            TextUtil.sendMessage(player, prefix + " &7Launchpads &8(&f" + padsByName.size() + "&8):");
            for (JumpPad pad : padsByName.values()) {
                if (pad.dest != null) {
                    TextUtil.sendMessage(player, "  &8> &e" + pad.name + " &a[Ready]");
                } else {
                    TextUtil.sendMessage(player, "  &8> &e" + pad.name + " &c[No destination set]");
                }
            }
            return true;
        }

        sendHelp(player, prefix);
        return true;
    }

    private void sendHelp(Player player, String prefix) {
        TextUtil.sendMessage(player, prefix + " &7Commands:");
        TextUtil.sendMessage(player, "  &f/launchpad create <name> &8- &7Start editing a pad");
        TextUtil.sendMessage(player, "  &f/launchpad setpad      &8- &7Set the pad to the block below you");
        TextUtil.sendMessage(player, "  &f/launchpad setdest     &8- &7Set the destination to your location");
        TextUtil.sendMessage(player, "  &f/launchpad delete <name>");
        TextUtil.sendMessage(player, "  &f/launchpad list");
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        java.util.List<String> completions = new java.util.ArrayList<>();
        if (command.getName().equalsIgnoreCase("launchpad")) {
            if (args.length == 1) {
                org.bukkit.util.StringUtil.copyPartialMatches(args[0], java.util.Arrays.asList("create", "setpad", "setdest", "delete", "list"), completions);
                java.util.Collections.sort(completions);
            } else if (args.length == 2 && args[0].equalsIgnoreCase("delete")) {
                org.bukkit.util.StringUtil.copyPartialMatches(args[1], padsByName.keySet(), completions);
                java.util.Collections.sort(completions);
            }
        }
        return completions;
    }
}
