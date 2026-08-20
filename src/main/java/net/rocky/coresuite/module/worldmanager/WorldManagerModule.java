package net.rocky.coresuite.module.worldmanager;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class WorldManagerModule extends Module implements Listener, CommandExecutor, TabCompleter {

    private Location lobbySpawn;
    private final NamespacedKey lastLocKey;
    private final Map<UUID, BukkitTask> hubTasks = new HashMap<>();

    public WorldManagerModule(CoreSuitePlugin plugin) {
        super(plugin, "WorldManager", "world-manager");
        this.lastLocKey = new NamespacedKey(plugin, "last_survival_loc");
    }

    @Override
    public void onEnable() {
        registerListener(this);

        String[] commands = {"setlobbyspawn", "hub", "joinsurvival", "joinpvp"};
        for (String cmd : commands) {
            PluginCommand command = plugin.getCommand(cmd);
            if (command != null) {
                command.setExecutor(this);
                command.setTabCompleter(this);
            }
        }

        loadLobbySpawn();
    }

    @Override
    public void onDisable() {
        for (BukkitTask task : hubTasks.values()) {
            task.cancel();
        }
        hubTasks.clear();
        
        String[] commands = {"setlobbyspawn", "hub", "joinsurvival", "joinpvp"};
        for (String cmd : commands) {
            PluginCommand command = plugin.getCommand(cmd);
            if (command != null && command.getExecutor() == this) {
                command.setExecutor(null);
                command.setTabCompleter(null);
            }
        }
    }

    private void loadLobbySpawn() {
        if (config.contains("lobby-spawn.world")) {
            World w = Bukkit.getWorld(config.getString("lobby-spawn.world"));
            if (w != null) {
                double x = config.getDouble("lobby-spawn.x");
                double y = config.getDouble("lobby-spawn.y");
                double z = config.getDouble("lobby-spawn.z");
                float yaw = (float) config.getDouble("lobby-spawn.yaw");
                float pitch = (float) config.getDouble("lobby-spawn.pitch");
                lobbySpawn = new Location(w, x, y, z, yaw, pitch);
            }
        }
    }

    private void saveLobbySpawn(Location loc) {
        this.lobbySpawn = loc;
        config.set("lobby-spawn.world", loc.getWorld().getName());
        config.set("lobby-spawn.x", loc.getX());
        config.set("lobby-spawn.y", loc.getY());
        config.set("lobby-spawn.z", loc.getZ());
        config.set("lobby-spawn.yaw", loc.getYaw());
        config.set("lobby-spawn.pitch", loc.getPitch());
        saveConfig();
    }

    private void savePlayerLocation(Player player) {
        String survivalWorld = config.getString("survival-world", "world");
        if (player.getWorld().getName().equals(survivalWorld)) {
            Location loc = player.getLocation();
            String serialized = loc.getWorld().getName() + ";" + loc.getX() + ";" + loc.getY() + ";" + loc.getZ() + ";" + loc.getYaw() + ";" + loc.getPitch();
            player.getPersistentDataContainer().set(lastLocKey, PersistentDataType.STRING, serialized);
        }
    }

    private Location getSavedLocation(Player player) {
        String serialized = player.getPersistentDataContainer().get(lastLocKey, PersistentDataType.STRING);
        if (serialized != null) {
            String[] parts = serialized.split(";");
            if (parts.length == 6) {
                World w = Bukkit.getWorld(parts[0]);
                if (w != null) {
                    try {
                        return new Location(w, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]), Float.parseFloat(parts[4]), Float.parseFloat(parts[5]));
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return null;
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (lobbySpawn != null) {
                event.getPlayer().teleport(lobbySpawn);
            } else {
                World spawnWorld = Bukkit.getWorld(config.getString("fallback-spawn-world", "spawn"));
                if (spawnWorld != null) {
                    event.getPlayer().teleport(spawnWorld.getSpawnLocation());
                }
            }
        }, 10L); // Delay by 10 ticks to guarantee override of EssentialsSpawn
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        savePlayerLocation(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        savePlayerLocation(event.getPlayer());
        hubTasks.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (!event.hasChangedBlock()) return;
        
        Player player = event.getPlayer();
        if (hubTasks.containsKey(player.getUniqueId())) {
            hubTasks.get(player.getUniqueId()).cancel();
            hubTasks.remove(player.getUniqueId());
            String prefix = config.getString("prefix", "&8[&a&lWorld&8] &r");
            TextUtil.sendMessage(player, prefix + config.getString("messages.teleport-cancelled", "&cTeleport cancelled because you moved!"));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        String cmd = command.getName().toLowerCase();

        String prefix = config.getString("prefix", "&8[&a&lWorld&8] &r");

        if (cmd.equals("setlobbyspawn")) {
            if (!player.hasPermission("coresuite.worldmanager.admin")) {
                TextUtil.sendMessage(player, prefix + config.getString("messages.no-permission", "&cYou don't have permission."));
                return true;
            }
            saveLobbySpawn(player.getLocation());
            TextUtil.sendMessage(player, prefix + config.getString("messages.lobby-spawn-set", "&aLobby spawn set to your location!"));
            return true;
        }

        if (cmd.equals("joinsurvival")) {
            Location loc = getSavedLocation(player);
            if (loc != null) {
                player.teleport(loc);
            } else {
                String survivalWorld = config.getString("survival-world", "world");
                World w = Bukkit.getWorld(survivalWorld);
                if (w != null) {
                    player.teleport(new Location(w, 0.5, config.getDouble("fallback-y", 64.0), 0.5));
                } else {
                    TextUtil.sendMessage(player, prefix + config.getString("messages.survival-not-found", "&cSurvival world not found."));
                }
            }
            return true;
        }

        if (cmd.equals("joinpvp")) {
            String pvpWorld = config.getString("pvp-world", "Duels");
            World w = Bukkit.getWorld(pvpWorld);
            if (w != null) {
                player.teleport(w.getSpawnLocation());
            } else {
                TextUtil.sendMessage(player, prefix + config.getString("messages.pvp-not-found", "&cPvP world not found."));
            }
            return true;
        }

        if (cmd.equals("hub")) {
            if (hubTasks.containsKey(player.getUniqueId())) {
                TextUtil.sendMessage(player, prefix + config.getString("messages.already-teleporting", "&cYou are already teleporting!"));
                return true;
            }

            int delay = config.getInt("hub-teleport-delay", 3);
            String teleportMsg = config.getString("messages.teleporting", "&aTeleporting to the hub in &e{time} seconds&a. Don't move!");
            TextUtil.sendMessage(player, prefix + teleportMsg.replace("{time}", String.valueOf(delay)));

            org.bukkit.scheduler.BukkitRunnable task = new org.bukkit.scheduler.BukkitRunnable() {
                int timeLeft = delay;

                @Override
                public void run() {
                    if (timeLeft <= 0) {
                        hubTasks.remove(player.getUniqueId());
                        if (lobbySpawn != null) {
                            player.teleport(lobbySpawn);
                        } else {
                            World spawnWorld = Bukkit.getWorld(config.getString("fallback-spawn-world", "world"));
                            if (spawnWorld != null) {
                                player.teleport(spawnWorld.getSpawnLocation());
                            } else {
                                player.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
                            }
                        }
                        this.cancel();
                        return;
                    }
                    String actionBar = config.getString("teleport-action-bar", "&eTeleporting in &6{time}s...");
                    TextUtil.sendActionBar(player, actionBar.replace("{time}", String.valueOf(timeLeft)));
                    timeLeft--;
                }
            };
            
            hubTasks.put(player.getUniqueId(), task.runTaskTimer(plugin, 20L, 20L));
            return true;
        }

        return false;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return java.util.Collections.emptyList();
    }
}
