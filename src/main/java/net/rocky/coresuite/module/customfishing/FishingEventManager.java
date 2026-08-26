package net.rocky.coresuite.module.customfishing;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class FishingEventManager {

    private final CoreSuitePlugin plugin;
    private final CustomFishingModule module;
    private final Random random = new Random();

    private Location activePond = null;
    private Location formingPond = null;
    private BukkitTask eventTimerTask;
    private BukkitTask particleTask;
    private BukkitTask endEventTask;
    private BukkitTask pondCheckTask;
    private BukkitTask formingTask;
    private final java.util.Set<java.util.UUID> playersInPond = new java.util.HashSet<>();
    private final java.util.List<org.bukkit.scheduler.BukkitTask> warningTasks = new java.util.ArrayList<>();

    public FishingEventManager(CoreSuitePlugin plugin, CustomFishingModule module) {
        this.plugin = plugin;
        this.module = module;
        startTimer();
    }

    public void shutdown() {
        if (eventTimerTask != null) eventTimerTask.cancel();
        if (particleTask != null) particleTask.cancel();
        if (endEventTask != null) endEventTask.cancel();
        if (pondCheckTask != null) pondCheckTask.cancel();
        if (formingTask != null) formingTask.cancel();
        for (org.bukkit.scheduler.BukkitTask task : warningTasks) task.cancel();
        warningTasks.clear();
        playersInPond.clear();
        activePond = null;
        formingPond = null;
    }

    public Location getActivePond() {
        return activePond;
    }

    public Location getFormingPond() {
        return formingPond;
    }

    private void startTimer() {
        FileConfiguration config = module.getConfig();
        if (!config.getBoolean("pond-event.enabled", true)) return;

        int interval = config.getInt("pond-event.interval-minutes", 30);
        
        eventTimerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (activePond != null || formingTask != null) return; // Already running or forming

                int minPlayers = config.getInt("pond-event.min-players", 2);
                if (Bukkit.getOnlinePlayers().size() < minPlayers) return;

                startEvent(null);
            }
        }.runTaskTimer(plugin, 20L * 60 * interval, 20L * 60 * interval);
    }

    public void forceStartEvent(Location loc) {
        if (activePond != null) endEvent();
        if (formingTask != null) { formingTask.cancel(); formingTask = null; formingPond = null; }
        // Snap to ground/water surface — don't use raw player Y (could be flying)
        Block highest = loc.getWorld().getHighestBlockAt(loc);
        actuallyStartEvent(highest.getLocation(), module.getConfig());
    }

    public void startEvent(Location anchor) {
        if (activePond != null) endEvent(); // Override existing event
        if (formingTask != null) { formingTask.cancel(); formingTask = null; formingPond = null; }
        
        FileConfiguration config = module.getConfig();
        
        if (anchor != null) {
            // Admin forced, search closely around them synchronously
            Location foundWater = findWaterSync(anchor, 50, 30);
            if (foundWater != null) actuallyStartEvent(foundWater, config);
        } else {
            // Automated - search anywhere up to spawn-radius (or world border)
            org.bukkit.World world = Bukkit.getWorlds().get(0);
            double borderSize = world.getWorldBorder().getSize() / 2;
            int maxRadius = config.getInt("pond-event.spawn-radius", (int) borderSize);
            
            searchWaterAsync(world, maxRadius, 50); // try 50 random spots async
        }
    }

    private void startFormingPhase(Location foundWater, FileConfiguration config) {
        String prefix = config.getString("prefix", "&8[&b&lFishing&8] &r");
        int formDelayMinutes = config.getInt("pond-event.forming-minutes", 5);
        Location center = foundWater.clone().add(0.5, 0.9, 0.5);
        formingPond = center;
        String bcast = prefix + "&e&lA Legendary Fish Pond is starting to form at &b" + 
                center.getBlockX() + ", " + center.getBlockZ() + "&e! It will be fully formed in &b" + formDelayMinutes + " minutes&e!";
        
        String soundStr = config.getString("pond-event.forming-sound", "BLOCK_NOTE_BLOCK_CHIME");
        org.bukkit.Sound sound = org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME;
        try {
            sound = org.bukkit.Sound.valueOf(soundStr.toUpperCase());
        } catch (Exception ignored) {}
        
        for (Player p : Bukkit.getOnlinePlayers()) {
            TextUtil.sendMessage(p, bcast);
            p.playSound(p.getLocation(), sound, 1.0f, 1.0f);
        }

        formingTask = new BukkitRunnable() {
            @Override
            public void run() {
                formingTask = null;
                actuallyStartEvent(foundWater, config);
            }
        }.runTaskLater(plugin, 20L * 60 * formDelayMinutes);
    }

    private void actuallyStartEvent(Location foundWater, FileConfiguration config) {
        activePond = foundWater.clone().add(0.5, 0.9, 0.5); // Center on block, slightly above surface
        formingPond = null;
        double radius = config.getDouble("pond-event.radius", 5.0);
        int duration = config.getInt("pond-event.duration-minutes", 10);

        String prefix = config.getString("prefix", "&8[&b&lFishing&8] &r");
        String bcast = prefix + "&b&lThe Legendary Fish Pond has fully formed at &e" + 
                activePond.getBlockX() + ", " + activePond.getBlockZ() + " &bfor &e" + duration + " minutes&b!";
        
        String soundStr = config.getString("pond-event.start-sound", "UI_TOAST_CHALLENGE_COMPLETE");
        org.bukkit.Sound sound = org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE;
        try {
            sound = org.bukkit.Sound.valueOf(soundStr.toUpperCase());
        } catch (Exception ignored) {}
        
        for (Player p : Bukkit.getOnlinePlayers()) {
            TextUtil.sendMessage(p, bcast);
            p.playSound(p.getLocation(), sound, 0.5f, 1.5f);
        }

        // Particle Task
        particleTask = new BukkitRunnable() {
            double angle = 0;
            @Override
            public void run() {
                if (activePond == null) {
                    this.cancel();
                    return;
                }
                
                // Draw 4 segments of the circle per run to maintain speed but reduce scheduler overhead
                for (int i = 0; i < 4; i++) {
                    double x = radius * Math.cos(angle);
                    double z = radius * Math.sin(angle);
                    Location pLoc = activePond.clone().add(x, 0.5, z);
                    
                    pLoc.getWorld().spawnParticle(Particle.END_ROD, pLoc, 2, 0.05, 0.05, 0.05, 0.01);
                    if (random.nextDouble() < 0.3) {
                        pLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, pLoc, 1, 0.0, 0.0, 0.0, 0.02);
                    }
                    
                    angle += 0.2;
                    if (angle > Math.PI * 2) angle = 0;
                }
                
                // Random splashes inside (scaled up chance to compensate for less frequent runs)
                if (random.nextDouble() < 0.8) {
                    double rx = (random.nextDouble() * 2 - 1) * radius;
                    double rz = (random.nextDouble() * 2 - 1) * radius;
                    if ((rx * rx + rz * rz) <= (radius * radius)) {
                        Location inner = activePond.clone().add(rx, 0.5, rz);
                        inner.getWorld().spawnParticle(Particle.PORTAL, inner, 6, 0.1, 0.1, 0.1, 0.05);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 4L);

        // End Task
        endEventTask = new BukkitRunnable() {
            @Override
            public void run() {
                endEvent();
            }
        }.runTaskLater(plugin, 20L * 60 * duration);
        
        // Warning tasks
        if (duration > 5) {
            warningTasks.add(new BukkitRunnable() {
                @Override
                public void run() {
                    if (activePond != null) Bukkit.broadcast(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(prefix + "&eThe Legendary Fish Pond will disappear in 5 minutes!"));
                }
            }.runTaskLater(plugin, 20L * 60 * (duration - 5)));
        }
        if (duration > 2) {
            warningTasks.add(new BukkitRunnable() {
                @Override
                public void run() {
                    if (activePond != null) Bukkit.broadcast(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(prefix + "&cThe Legendary Fish Pond will disappear in 2 minutes!"));
                }
            }.runTaskLater(plugin, 20L * 60 * (duration - 2)));
        }
        if (duration > 1) {
            warningTasks.add(new BukkitRunnable() {
                @Override
                public void run() {
                    if (activePond != null) Bukkit.broadcast(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(prefix + "&4&lThe Legendary Fish Pond will disappear in 1 minute!"));
                }
            }.runTaskLater(plugin, 20L * 60 * (duration - 1)));
        }

        // Pond enter/leave tracking task
        pondCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (activePond == null) {
                    this.cancel();
                    return;
                }
                double checkRadius = config.getDouble("pond-event.radius", 5.0);
                double radSq = checkRadius * checkRadius;
                String pondPrefix = config.getString("prefix", "&8[&b&lFishing&8] &r");
                String enterMsg = config.getString("pond-event.enter-message", "&a✦ You entered the Legendary Fishing Pond! Cast your rod here for bonus rewards!");
                String leaveMsg = config.getString("pond-event.leave-message", "&7✦ You left the Legendary Fishing Pond area.");

                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.getWorld().getName().equals(activePond.getWorld().getName())) {
                        if (playersInPond.remove(p.getUniqueId())) {
                            TextUtil.sendMessage(p, pondPrefix + leaveMsg);
                        }
                        continue;
                    }
                    double dx = p.getLocation().getX() - activePond.getX();
                    double dz = p.getLocation().getZ() - activePond.getZ();
                    double dy = Math.abs(p.getLocation().getY() - activePond.getY());

                    if (dx * dx + dz * dz <= radSq && dy <= 10.0) {
                        if (playersInPond.add(p.getUniqueId())) {
                            TextUtil.sendMessage(p, pondPrefix + enterMsg);
                            p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.5f);
                        }
                    } else {
                        if (playersInPond.remove(p.getUniqueId())) {
                            TextUtil.sendMessage(p, pondPrefix + leaveMsg);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private void endEvent() {
        if (activePond == null) return;
        
        FileConfiguration config = module.getConfig();
        String prefix = config.getString("prefix", "&8[&b&lFishing&8] &r");
        String leaveMsg = config.getString("pond-event.leave-message", "&7✦ You left the Legendary Fishing Pond area.");
        
        // Notify all players in the pond that the event ended
        for (java.util.UUID uuid : playersInPond) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) {
                TextUtil.sendMessage(p, prefix + leaveMsg);
            }
        }
        playersInPond.clear();
        
        for (Player p : Bukkit.getOnlinePlayers()) {
            TextUtil.sendMessage(p, prefix + "&7The Legendary Fish Pond has faded away...");
        }
        
        activePond = null;
        if (particleTask != null) particleTask.cancel();
        endEventTask = null;
        if (pondCheckTask != null) pondCheckTask.cancel();
        for (org.bukkit.scheduler.BukkitTask task : warningTasks) task.cancel();
        warningTasks.clear();
    }

    private void searchWaterAsync(org.bukkit.World world, int radius, int attemptsLeft) {
        if (attemptsLeft <= 0) return; // Gave up
        
        Location center = world.getSpawnLocation();
        int offsetX = (random.nextInt(radius * 2) - radius);
        int offsetZ = (random.nextInt(radius * 2) - radius);
        int targetX = center.getBlockX() + offsetX;
        int targetZ = center.getBlockZ() + offsetZ;
        
        // Asynchronously load the chunk so we don't lag the server at all
        world.getChunkAtAsync(targetX >> 4, targetZ >> 4).thenAccept(chunk -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Location testLoc = new Location(world, targetX, 0, targetZ);
                Block highest = world.getHighestBlockAt(testLoc);
                if (highest.getType() == Material.WATER && isLargeWaterBody(highest)) {
                    startFormingPhase(highest.getLocation(), module.getConfig());
                } else {
                    searchWaterAsync(world, radius, attemptsLeft - 1);
                }
            });
        });
    }

    private Location findWaterSync(Location anchor, int searchRadius, int attempts) {
        for (int i = 0; i < attempts; i++) {
            int offsetX = (random.nextInt(searchRadius * 2) - searchRadius);
            int offsetZ = (random.nextInt(searchRadius * 2) - searchRadius);
            
            Location testLoc = anchor.clone().add(offsetX, 0, offsetZ);
            Block highest = testLoc.getWorld().getHighestBlockAt(testLoc);
            
            if (highest.getType() == Material.WATER && isLargeWaterBody(highest)) {
                return highest.getLocation();
            }
        }
        return null;
    }

    private boolean isLargeWaterBody(Block center) {
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (center.getRelative(x, 0, z).getType() != Material.WATER) {
                    return false;
                }
            }
        }
        return true;
    }
}



