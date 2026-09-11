/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
 *  org.bukkit.Bukkit
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.Particle
 *  org.bukkit.Sound
 *  org.bukkit.World
 *  org.bukkit.block.Block
 *  org.bukkit.command.CommandSender
 *  org.bukkit.configuration.file.FileConfiguration
 *  org.bukkit.entity.Player
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.scheduler.BukkitRunnable
 *  org.bukkit.scheduler.BukkitTask
 */
package net.rocky.coresuite.module.customfishing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.customfishing.CustomFishingModule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

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
    private final Set<UUID> playersInPond = new HashSet<UUID>();
    private final List<BukkitTask> warningTasks = new ArrayList<BukkitTask>();

    public FishingEventManager(CoreSuitePlugin plugin, CustomFishingModule module) {
        this.plugin = plugin;
        this.module = module;
        this.startTimer();
    }

    public void shutdown() {
        if (this.eventTimerTask != null) {
            this.eventTimerTask.cancel();
        }
        if (this.particleTask != null) {
            this.particleTask.cancel();
        }
        if (this.endEventTask != null) {
            this.endEventTask.cancel();
        }
        if (this.pondCheckTask != null) {
            this.pondCheckTask.cancel();
        }
        if (this.formingTask != null) {
            this.formingTask.cancel();
        }
        for (BukkitTask task : this.warningTasks) {
            task.cancel();
        }
        this.warningTasks.clear();
        this.playersInPond.clear();
        this.activePond = null;
        this.formingPond = null;
    }

    public Location getActivePond() {
        return this.activePond;
    }

    public Location getFormingPond() {
        return this.formingPond;
    }

    private void startTimer() {
        final FileConfiguration config = this.module.getConfig();
        if (!config.getBoolean("pond-event.enabled", true)) {
            return;
        }
        int interval = config.getInt("pond-event.interval-minutes", 30);
        this.eventTimerTask = new BukkitRunnable(){

            public void run() {
                if (FishingEventManager.this.activePond != null || FishingEventManager.this.formingTask != null) {
                    return;
                }
                int minPlayers = config.getInt("pond-event.min-players", 2);
                if (Bukkit.getOnlinePlayers().size() < minPlayers) {
                    return;
                }
                FishingEventManager.this.startEvent(null);
            }
        }.runTaskTimer((Plugin)this.plugin, 1200L * (long)interval, 1200L * (long)interval);
    }

    public void forceStartEvent(Location loc) {
        if (this.activePond != null) {
            this.endEvent();
        }
        if (this.formingTask != null) {
            this.formingTask.cancel();
            this.formingTask = null;
            this.formingPond = null;
        }
        Block highest = loc.getWorld().getHighestBlockAt(loc);
        this.actuallyStartEvent(highest.getLocation(), this.module.getConfig());
    }

    public void startEvent(Location anchor) {
        if (this.activePond != null) {
            this.endEvent();
        }
        if (this.formingTask != null) {
            this.formingTask.cancel();
            this.formingTask = null;
            this.formingPond = null;
        }
        FileConfiguration config = this.module.getConfig();
        if (anchor != null) {
            Location foundWater = this.findWaterSync(anchor, 50, 30);
            if (foundWater != null) {
                this.actuallyStartEvent(foundWater, config);
            }
        } else {
            World world = (World)Bukkit.getWorlds().get(0);
            double borderSize = world.getWorldBorder().getSize() / 2.0;
            int maxRadius = config.getInt("pond-event.spawn-radius", (int)borderSize);
            this.searchWaterAsync(world, maxRadius, 50);
        }
    }

    private void startFormingPhase(final Location foundWater, final FileConfiguration config) {
        Location center;
        String prefix = config.getString("prefix", "&8[&b&lFishing&8] &r");
        int formDelayMinutes = config.getInt("pond-event.forming-minutes", 5);
        this.formingPond = center = foundWater.clone().add(0.5, 0.9, 0.5);
        String bcast = prefix + "&e&lA Legendary Fish Pond is starting to form at &b" + center.getBlockX() + ", " + center.getBlockZ() + "&e! It will be fully formed in &b" + formDelayMinutes + " minutes&e!";
        String soundStr = config.getString("pond-event.forming-sound", "BLOCK_NOTE_BLOCK_CHIME");
        Sound sound = Sound.BLOCK_NOTE_BLOCK_CHIME;
        try {
            sound = Sound.valueOf((String)soundStr.toUpperCase());
        }
        catch (Exception exception) {
            // empty catch block
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            TextUtil.sendMessage((CommandSender)p, bcast);
            p.playSound(p.getLocation(), sound, 1.0f, 1.0f);
        }
        this.formingTask = new BukkitRunnable(){

            public void run() {
                FishingEventManager.this.formingTask = null;
                FishingEventManager.this.actuallyStartEvent(foundWater, config);
            }
        }.runTaskLater((Plugin)this.plugin, 1200L * (long)formDelayMinutes);
    }

    private void actuallyStartEvent(Location foundWater, final FileConfiguration config) {
        this.activePond = foundWater.clone().add(0.5, 0.9, 0.5);
        this.formingPond = null;
        final double radius = config.getDouble("pond-event.radius", 5.0);
        int duration = config.getInt("pond-event.duration-minutes", 10);
        final String prefix = config.getString("prefix", "&8[&b&lFishing&8] &r");
        String bcast = prefix + "&b&lThe Legendary Fish Pond has fully formed at &e" + this.activePond.getBlockX() + ", " + this.activePond.getBlockZ() + " &bfor &e" + duration + " minutes&b!";
        String soundStr = config.getString("pond-event.start-sound", "UI_TOAST_CHALLENGE_COMPLETE");
        Sound sound = Sound.UI_TOAST_CHALLENGE_COMPLETE;
        try {
            sound = Sound.valueOf((String)soundStr.toUpperCase());
        }
        catch (Exception exception) {
            // empty catch block
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            TextUtil.sendMessage((CommandSender)p, bcast);
            p.playSound(p.getLocation(), sound, 0.5f, 1.5f);
        }
        this.particleTask = new BukkitRunnable(){
            double angle = 0.0;

            public void run() {
                double rz;
                double rx;
                if (FishingEventManager.this.activePond == null) {
                    this.cancel();
                    return;
                }
                for (int i = 0; i < 4; ++i) {
                    double x = radius * Math.cos(this.angle);
                    double z = radius * Math.sin(this.angle);
                    Location pLoc = FishingEventManager.this.activePond.clone().add(x, 0.5, z);
                    pLoc.getWorld().spawnParticle(Particle.END_ROD, pLoc, 2, 0.05, 0.05, 0.05, 0.01);
                    if (FishingEventManager.this.random.nextDouble() < 0.3) {
                        pLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, pLoc, 1, 0.0, 0.0, 0.0, 0.02);
                    }
                    this.angle += 0.2;
                    if (!(this.angle > Math.PI * 2)) continue;
                    this.angle = 0.0;
                }
                if (FishingEventManager.this.random.nextDouble() < 0.8 && (rx = (FishingEventManager.this.random.nextDouble() * 2.0 - 1.0) * radius) * rx + (rz = (FishingEventManager.this.random.nextDouble() * 2.0 - 1.0) * radius) * rz <= radius * radius) {
                    Location inner = FishingEventManager.this.activePond.clone().add(rx, 0.5, rz);
                    inner.getWorld().spawnParticle(Particle.PORTAL, inner, 6, 0.1, 0.1, 0.1, 0.05);
                }
            }
        }.runTaskTimer((Plugin)this.plugin, 0L, 4L);
        this.endEventTask = new BukkitRunnable(){

            public void run() {
                FishingEventManager.this.endEvent();
            }
        }.runTaskLater((Plugin)this.plugin, 1200L * (long)duration);
        if (duration > 5) {
            this.warningTasks.add(new BukkitRunnable(){

                public void run() {
                    if (FishingEventManager.this.activePond != null) {
                        Bukkit.broadcast((Component)LegacyComponentSerializer.legacyAmpersand().deserialize(prefix + "&eThe Legendary Fish Pond will disappear in 5 minutes!"));
                    }
                }
            }.runTaskLater((Plugin)this.plugin, 1200L * (long)(duration - 5)));
        }
        if (duration > 2) {
            this.warningTasks.add(new BukkitRunnable(){

                public void run() {
                    if (FishingEventManager.this.activePond != null) {
                        Bukkit.broadcast((Component)LegacyComponentSerializer.legacyAmpersand().deserialize(prefix + "&cThe Legendary Fish Pond will disappear in 2 minutes!"));
                    }
                }
            }.runTaskLater((Plugin)this.plugin, 1200L * (long)(duration - 2)));
        }
        if (duration > 1) {
            this.warningTasks.add(new BukkitRunnable(){

                public void run() {
                    if (FishingEventManager.this.activePond != null) {
                        Bukkit.broadcast((Component)LegacyComponentSerializer.legacyAmpersand().deserialize(prefix + "&4&lThe Legendary Fish Pond will disappear in 1 minute!"));
                    }
                }
            }.runTaskLater((Plugin)this.plugin, 1200L * (long)(duration - 1)));
        }
        this.pondCheckTask = new BukkitRunnable(){

            public void run() {
                if (FishingEventManager.this.activePond == null) {
                    this.cancel();
                    return;
                }
                double pondRadius = config.getDouble("pond-event.radius", 5.0);
                double enterRadius = pondRadius + 3.0;
                double leaveRadius = enterRadius + 2.5;
                double enterRadSq = enterRadius * enterRadius;
                double leaveRadSq = leaveRadius * leaveRadius;
                String pondPrefix = config.getString("prefix", "&8[&b&lFishing&8] &r");
                String enterMsg = config.getString("pond-event.enter-message", "&a\u2726 Legendary Fishing Pond Nearby! Cast your bobber into the glowing ring for bonus rewards!")
                        .replace("âœ¦", "\u2726").replace("\u00e2\u0153\u00a6", "\u2726").replace("â", "").replace("œ", "").replace("¦", "")
                        .replace("\r", "").replace("\n", " ").replaceAll("\\s+", " ").trim();
                String leaveMsg = config.getString("pond-event.leave-message", "&7\u2726 You moved away from the Legendary Fishing Pond.")
                        .replace("âœ¦", "\u2726").replace("\u00e2\u0153\u00a6", "\u2726").replace("â", "").replace("œ", "").replace("¦", "")
                        .replace("\r", "").replace("\n", " ").replaceAll("\\s+", " ").trim();
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (!p.getWorld().getName().equals(FishingEventManager.this.activePond.getWorld().getName())) {
                        if (FishingEventManager.this.playersInPond.remove(p.getUniqueId())) {
                            TextUtil.sendMessage((CommandSender)p, pondPrefix + leaveMsg);
                        }
                        continue;
                    }
                    double dx = p.getLocation().getX() - FishingEventManager.this.activePond.getX();
                    double dz = p.getLocation().getZ() - FishingEventManager.this.activePond.getZ();
                    double dy = Math.abs(p.getLocation().getY() - FishingEventManager.this.activePond.getY());
                    double distSq = dx * dx + dz * dz;

                    if (FishingEventManager.this.playersInPond.contains(p.getUniqueId())) {
                        if (distSq > leaveRadSq || dy > 10.0) {
                            FishingEventManager.this.playersInPond.remove(p.getUniqueId());
                            TextUtil.sendMessage((CommandSender)p, pondPrefix + leaveMsg);
                        }
                    } else {
                        if (distSq <= enterRadSq && dy <= 10.0) {
                            FishingEventManager.this.playersInPond.add(p.getUniqueId());
                            TextUtil.sendMessage((CommandSender)p, pondPrefix + enterMsg);
                            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 1.5f);
                        }
                    }
                }
            }
        }.runTaskTimer((Plugin)this.plugin, 0L, 20L);
    }

    private void endEvent() {
        if (this.activePond == null) {
            return;
        }
        FileConfiguration config = this.module.getConfig();
        String prefix = config.getString("prefix", "&8[&b&lFishing&8] &r");
        String leaveMsg = config.getString("pond-event.leave-message", "&7\u2726 You moved away from the Legendary Fishing Pond.")
                .replace("âœ¦", "\u2726").replace("\u00e2\u0153\u00a6", "\u2726").replace("â", "").replace("œ", "").replace("¦", "")
                .replace("\r", "").replace("\n", " ").replaceAll("\\s+", " ").trim();
        for (UUID uuid : this.playersInPond) {
            Player p = Bukkit.getPlayer((UUID)uuid);
            if (p == null) continue;
            TextUtil.sendMessage((CommandSender)p, prefix + leaveMsg);
        }
        this.playersInPond.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            TextUtil.sendMessage((CommandSender)p, prefix + "&7The Legendary Fish Pond has faded away...");
        }
        this.activePond = null;
        if (this.particleTask != null) {
            this.particleTask.cancel();
        }
        this.endEventTask = null;
        if (this.pondCheckTask != null) {
            this.pondCheckTask.cancel();
        }
        for (BukkitTask task : this.warningTasks) {
            task.cancel();
        }
        this.warningTasks.clear();
    }

    public void unloadPlayer(UUID uuid) {
        this.playersInPond.remove(uuid);
    }

    private void searchWaterAsync(World world, int radius, int attemptsLeft) {
        if (attemptsLeft <= 0) {
            return;
        }
        Location center = world.getSpawnLocation();
        int offsetX = this.random.nextInt(radius * 2) - radius;
        int offsetZ = this.random.nextInt(radius * 2) - radius;
        int targetX = center.getBlockX() + offsetX;
        int targetZ = center.getBlockZ() + offsetZ;
        world.getChunkAtAsync(targetX >> 4, targetZ >> 4).thenAccept(chunk -> Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
            Location testLoc = new Location(world, (double)targetX, 0.0, (double)targetZ);
            Block highest = world.getHighestBlockAt(testLoc);
            if (highest.getType() == Material.WATER && this.isLargeWaterBody(highest)) {
                this.startFormingPhase(highest.getLocation(), this.module.getConfig());
            } else {
                this.searchWaterAsync(world, radius, attemptsLeft - 1);
            }
        }));
    }

    private Location findWaterSync(Location anchor, int searchRadius, int attempts) {
        for (int i = 0; i < attempts; ++i) {
            int offsetX = this.random.nextInt(searchRadius * 2) - searchRadius;
            int offsetZ = this.random.nextInt(searchRadius * 2) - searchRadius;
            Location testLoc = anchor.clone().add((double)offsetX, 0.0, (double)offsetZ);
            Block highest = testLoc.getWorld().getHighestBlockAt(testLoc);
            if (highest.getType() != Material.WATER || !this.isLargeWaterBody(highest)) continue;
            return highest.getLocation();
        }
        return null;
    }

    private boolean isLargeWaterBody(Block center) {
        for (int x = -1; x <= 1; ++x) {
            for (int z = -1; z <= 1; ++z) {
                if (center.getRelative(x, 0, z).getType() == Material.WATER) continue;
                return false;
            }
        }
        return true;
    }
}

