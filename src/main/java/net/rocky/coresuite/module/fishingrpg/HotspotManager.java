package net.rocky.coresuite.module.fishingrpg;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Random;

public class HotspotManager {
    private final CoreSuitePlugin plugin;
    private final FileConfiguration config;
    
    private Location activeHotspot = null;
    private BukkitTask spawnTimerTask;
    private BukkitTask endTask;
    private BukkitTask particleTask;
    
    private final double radius;
    private final double catchMultiplier;
    private final double weightMultiplier;
    
    private final Random random = new Random();

    public HotspotManager(CoreSuitePlugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        
        this.radius = config.getDouble("hotspot.radius", 15.0);
        this.catchMultiplier = config.getDouble("hotspot.catch-multiplier", 2.0);
        this.weightMultiplier = config.getDouble("hotspot.weight-multiplier", 1.5);
    }
    
    public void startTimer() {
        int intervalMinutes = config.getInt("hotspot.interval-minutes", 60);
        long intervalTicks = intervalMinutes * 60L * 20L;
        
        spawnTimerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int minPlayers = config.getInt("hotspot.min-players", 5);
            if (Bukkit.getOnlinePlayers().size() >= minPlayers && activeHotspot == null) {
                spawnHotspot();
            }
        }, intervalTicks, intervalTicks);
    }
    
    public void spawnHotspot() {
        World world = Bukkit.getWorlds().get(0);
        Location spawn = world.getSpawnLocation();
        int spawnRadius = config.getInt("hotspot.spawn-radius", 2000);
        
        int x = spawn.getBlockX() + random.nextInt(spawnRadius * 2) - spawnRadius;
        int z = spawn.getBlockZ() + random.nextInt(spawnRadius * 2) - spawnRadius;
        
        world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                Block highest = world.getHighestBlockAt(x, z);
                // Simple search for water
                Location loc = highest.getLocation();
                while (loc.getBlockY() > 0 && !loc.getBlock().getType().name().contains("WATER")) {
                    loc.subtract(0, 1, 0);
                }
                
                if (loc.getBlock().getType().name().contains("WATER")) {
                    startHotspot(loc.add(0.5, 1, 0.5));
                } else {
                    // Fallback to random spot if not water, in a real impl might retry
                    startHotspot(highest.getLocation().add(0.5, 1, 0.5));
                }
            });
        });
    }
    
    private void startHotspot(Location loc) {
        this.activeHotspot = loc;
        
        int durationMinutes = config.getInt("hotspot.duration-minutes", 15);
        String announce = config.getString("messages.hotspot-announce", "&bA fishing hotspot has appeared at %x%, %z% for %duration% minutes!");
        announce = announce.replace("%x%", String.valueOf(loc.getBlockX()))
                           .replace("%z%", String.valueOf(loc.getBlockZ()))
                           .replace("%duration%", String.valueOf(durationMinutes));
                           
        for (Player p : Bukkit.getOnlinePlayers()) {
            TextUtil.sendMessage(p, announce);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }
        
        particleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeHotspot == null) return;
            for (double angle = 0; angle < 2 * Math.PI; angle += Math.PI / 16) {
                double px = activeHotspot.getX() + radius * Math.cos(angle);
                double pz = activeHotspot.getZ() + radius * Math.sin(angle);
                activeHotspot.getWorld().spawnParticle(Particle.END_ROD, px, activeHotspot.getY(), pz, 1, 0, 0, 0, 0);
            }
        }, 0L, 4L);
        
        endTask = Bukkit.getScheduler().runTaskLater(plugin, this::endHotspot, durationMinutes * 60L * 20L);
    }
    
    public void endHotspot() {
        if (activeHotspot == null) return;
        
        activeHotspot = null;
        if (particleTask != null) particleTask.cancel();
        if (endTask != null) endTask.cancel();
        
        String fade = config.getString("messages.hotspot-fade", "&7The fishing hotspot has faded away.");
        for (Player p : Bukkit.getOnlinePlayers()) {
            TextUtil.sendMessage(p, fade);
        }
    }
    
    public boolean isInHotspot(Location loc) {
        if (activeHotspot == null || loc.getWorld() != activeHotspot.getWorld()) return false;
        return loc.distanceSquared(activeHotspot) <= radius * radius;
    }
    
    public double getCatchMultiplier() {
        return activeHotspot != null ? catchMultiplier : 1.0;
    }
    
    public double getWeightMultiplier() {
        return activeHotspot != null ? weightMultiplier : 1.0;
    }
    
    public void forceStart(Location loc) {
        if (activeHotspot != null) endHotspot();
        startHotspot(loc);
    }
    
    public void shutdown() {
        if (spawnTimerTask != null) spawnTimerTask.cancel();
        if (particleTask != null) particleTask.cancel();
        if (endTask != null) endTask.cancel();
        activeHotspot = null;
    }
}
