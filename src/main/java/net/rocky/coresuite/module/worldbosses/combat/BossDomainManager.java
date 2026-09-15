package net.rocky.coresuite.module.worldbosses.combat;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.worldbosses.boss.WorldBoss;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BossDomainManager implements Listener {

    private final CoreSuitePlugin plugin;
    private final WorldBoss boss;
    private final Location center;
    private final double domainRadius;
    private final double leashDistance;
    private final boolean antiGrief;
    private final Particle boundaryParticle;
    private final boolean lockdownEnabled;
    private final int prepGraceSeconds;
    private final Random random = new Random();
    private BukkitTask visualTask;

    private final Set<UUID> lockedPlayers = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> graceTimestamps = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastWarningTime = new ConcurrentHashMap<>();
    private final Set<Location> playerPlacedBlocks = ConcurrentHashMap.newKeySet();
    private final List<Location> cachedBoundaryLocations = new ArrayList<>();

    public BossDomainManager(CoreSuitePlugin plugin, WorldBoss boss, Location center,
                             double domainRadius, double leashDistance, boolean antiGrief,
                             String particleName, boolean lockdownEnabled, int prepGraceSeconds) {
        this.plugin = plugin;
        this.boss = boss;
        this.center = center.clone();
        this.domainRadius = domainRadius;
        this.leashDistance = leashDistance;
        this.antiGrief = antiGrief;
        this.lockdownEnabled = lockdownEnabled;
        this.prepGraceSeconds = prepGraceSeconds;

        Particle p = Particle.FLAME;
        if (particleName != null) {
            try {
                p = Particle.valueOf(particleName.toUpperCase());
            } catch (IllegalArgumentException ignored) {}
        }
        this.boundaryParticle = p;
        precomputeBoundaryLocations();
    }

    private void precomputeBoundaryLocations() {
        cachedBoundaryLocations.clear();
        World world = center.getWorld();
        if (world == null) return;
        int points = 72;
        double[] verticalOffsets = {0.0, 1.2, 2.5, 4.4};
        for (int i = 0; i < points; i++) {
            double angle = 2 * Math.PI * i / points;
            double x = center.getX() + (domainRadius * Math.cos(angle));
            double z = center.getZ() + (domainRadius * Math.sin(angle));
            int blockX = (int) Math.floor(x), blockZ = (int) Math.floor(z);
            int highestY = world.isChunkLoaded(blockX >> 4, blockZ >> 4)
                    ? world.getHighestBlockYAt(blockX, blockZ) + 1 : center.getBlockY();
            double groundY = (Math.abs(highestY - center.getY()) <= 10) ? highestY : center.getY();
            for (double offY : verticalOffsets) {
                cachedBoundaryLocations.add(new Location(world, x, groundY + offY, z));
            }
        }
    }

    public BossDomainManager(CoreSuitePlugin plugin, WorldBoss boss, Location center,
                             double domainRadius, double leashDistance, boolean antiGrief, String particleName) {
        this(plugin, boss, center, domainRadius, leashDistance, antiGrief, particleName, true, 45);
    }

    public boolean isInDomain(Location loc) {
        if (loc == null || loc.getWorld() == null || center.getWorld() == null) return false;
        if (!loc.getWorld().equals(center.getWorld())) return false;
        return loc.distanceSquared(center) <= (domainRadius * domainRadius);
    }

    public boolean isOutsideLeash(Location loc) {
        if (loc == null || loc.getWorld() == null || center.getWorld() == null) return true;
        if (!loc.getWorld().equals(center.getWorld())) return true;
        return loc.distanceSquared(center) > (leashDistance * leashDistance);
    }

    public void lockPlayer(UUID uuid) {
        if (!lockdownEnabled || uuid == null) return;
        if (boss != null && !boss.isAwakened()) return;
        graceTimestamps.remove(uuid);
        if (lockedPlayers.add(uuid)) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.playSound(p.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.2f, 0.7f);
            }
        }
    }

    public boolean isLockedIn(UUID uuid) {
        return lockdownEnabled && lockedPlayers.contains(uuid);
    }

    public void unlockPlayer(UUID uuid) {
        if (uuid == null) return;
        lockedPlayers.remove(uuid);
        graceTimestamps.remove(uuid);
        lastWarningTime.remove(uuid);
    }

    public void startVisualTask() {
        stopVisualTask();
        precomputeBoundaryLocations();
        this.visualTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (boss == null || !boss.isActive()) {
                stopVisualTask();
                return;
            }
            renderBoundary();
            handleLockdownCycle();
        }, 15L, 15L);
    }

    public void stopVisualTask() {
        if (visualTask != null) {
            visualTask.cancel();
            visualTask = null;
        }
        lockedPlayers.clear();
        graceTimestamps.clear();
        lastWarningTime.clear();
        playerPlacedBlocks.clear();
        cachedBoundaryLocations.clear();
    }

    private void handleLockdownCycle() {
        if (!lockdownEnabled) return;
        if (boss != null && !boss.isAwakened()) return;

        // 1. Enforce physical bounce-back for locked players who attempt to cross boundary
        java.util.List<UUID> toUnlock = new java.util.ArrayList<>();
        for (UUID uuid : lockedPlayers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p == null || !p.isOnline() || p.isDead()) {
                toUnlock.add(uuid);
                continue;
            }
            if (p.getWorld() == null || !p.getWorld().equals(center.getWorld())) {
                toUnlock.add(uuid);
                continue;
            }

            double distSq = p.getLocation().distanceSquared(center);
            // If player is far outside (e.g. respawned elsewhere, or beyond arena leash), unlock them immediately
            double boundaryLimit = domainRadius + 12.0;
            if (distSq > (boundaryLimit * boundaryLimit)) {
                toUnlock.add(uuid);
                continue;
            }

            if (distSq > (domainRadius * domainRadius)) {
                // Bounce player back inward towards domain center
                org.bukkit.util.Vector push = center.toVector().subtract(p.getLocation().toVector()).normalize().multiply(1.35);
                push.setY(0.42);
                p.setVelocity(push);

                p.playSound(p.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.2f, 0.8f);
                p.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, p.getLocation().add(0, 1.0, 0), 20, 0.4, 0.4, 0.4, 0.1);
                boss.sendCombatAlert(p, net.rocky.coresuite.module.worldbosses.combat.StatusBadge.DEFLECTED, "&cArena Sealed! Retreat is blocked!", 2);
            }
        }
        for (UUID uid : toUnlock) {
            unlockPlayer(uid);
        }

        // 2. Constrain boss entity within arena perimeter
        LivingEntity bossEntity = boss.getEntity();
        if (bossEntity != null && bossEntity.isValid() && center.getWorld() != null && center.getWorld().equals(bossEntity.getWorld())) {
            double bossDistSq = bossEntity.getLocation().distanceSquared(center);
            double boundaryLimit = domainRadius - 2.0;
            if (bossDistSq > (boundaryLimit * boundaryLimit)) {
                if (bossDistSq > (domainRadius * domainRadius)) {
                    // Boss pushed outside barrier -> snap inward
                    org.bukkit.util.Vector push = center.toVector().subtract(bossEntity.getLocation().toVector()).normalize().multiply(1.5);
                    push.setY(0.35);
                    bossEntity.setVelocity(push);
                    if (bossDistSq > ((domainRadius + 4.0) * (domainRadius + 4.0))) {
                        bossEntity.teleport(center.clone().add(0, 0.5, 0));
                    }
                } else {
                    // Boss is edging perimeter -> deflect back inward
                    org.bukkit.util.Vector nudge = center.toVector().subtract(bossEntity.getLocation().toVector()).normalize().multiply(0.85);
                    nudge.setY(0.15);
                    bossEntity.setVelocity(nudge);
                }
            }
        }

        // 3. Constrain minions within arena perimeter
        for (LivingEntity minion : boss.getMinions()) {
            if (minion != null && minion.isValid() && center.getWorld() != null && center.getWorld().equals(minion.getWorld())) {
                double mDistSq = minion.getLocation().distanceSquared(center);
                if (mDistSq > (domainRadius * domainRadius)) {
                    org.bukkit.util.Vector push = center.toVector().subtract(minion.getLocation().toVector()).normalize().multiply(1.25);
                    push.setY(0.2);
                    minion.setVelocity(push);
                    if (mDistSq > ((domainRadius + 5.0) * (domainRadius + 5.0))) {
                        minion.teleport(center.clone().add(0, 0.5, 0));
                    }
                }
            }
        }

        // 4. Automatically lock any player who enters the domain during active combat
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p == null || !p.isOnline() || p.isDead()) continue;
            UUID uuid = p.getUniqueId();
            if (isInDomain(p.getLocation())) {
                if (!lockedPlayers.contains(uuid)) {
                    lockPlayer(uuid);
                }
            }
        }
    }

    public void renderBoundary() {
        try {
            World world = center.getWorld();
            if (world == null || boss == null || !boss.isActive()) return;
            if (cachedBoundaryLocations.isEmpty()) {
                precomputeBoundaryLocations();
            }

            for (Location pLoc : cachedBoundaryLocations) {
                world.spawnParticle(boundaryParticle, pLoc, 1, 0.05, 0.05, 0.05, 0.0, null, true);

                double offY = pLoc.getY() - center.getY();
                switch (boss.getType()) {
                    case VALERIUS -> {
                        if (offY <= 0.5) {
                            world.spawnParticle(Particle.LAVA, pLoc, 1, 0, 0, 0, 0, null, true);
                        } else if (offY >= 4.0) {
                            world.spawnParticle(Particle.SOUL_FIRE_FLAME, pLoc, 1, 0.05, 0.05, 0.05, 0.01, null, true);
                        }
                    }
                    case AETHELGARD -> {
                        if (offY >= 3.0) {
                            world.spawnParticle(Particle.TOTEM_OF_UNDYING, pLoc, 1, 0.05, 0.05, 0.05, 0.05, null, true);
                        } else {
                            world.spawnParticle(Particle.HAPPY_VILLAGER, pLoc, 1, 0.05, 0.05, 0.05, 0.0, null, true);
                        }
                    }
                    case XOLTHOS -> {
                        if (offY >= 3.0) {
                            world.spawnParticle(Particle.DRAGON_BREATH, pLoc, 1, 0.05, 0.05, 0.05, 0.01, 1.0f, true);
                        } else {
                            world.spawnParticle(Particle.PORTAL, pLoc, 2, 0.05, 0.05, 0.05, 0.1, null, true);
                        }
                    }
                }
            }

            renderThematicAtmosphere();
        } catch (Throwable ignored) {}
    }

    public void renderThematicAtmosphere() {
        try {
            World world = center.getWorld();
            if (world == null || boss == null || !boss.isActive()) return;
            if (lockedPlayers.isEmpty()) return;

            // Highly optimized: Spawn subtle atmospheric embers directly around active fighters (zero heightmap queries)
            for (UUID uuid : lockedPlayers) {
                Player p = Bukkit.getPlayer(uuid);
                if (p == null || !p.isOnline() || p.getWorld() == null || !p.getWorld().equals(world)) continue;
                Location pLoc = p.getLocation();

                switch (boss.getType()) {
                    case VALERIUS -> {
                        world.spawnParticle(Particle.ASH, pLoc.clone().add((random.nextDouble() - 0.5) * 8, 1.5 + random.nextDouble() * 2, (random.nextDouble() - 0.5) * 8), 2, 0.1, 0.1, 0.1, 0.02);
                        if (random.nextDouble() < 0.25) {
                            world.spawnParticle(Particle.FLAME, pLoc.clone().add((random.nextDouble() - 0.5) * 6, 0.5 + random.nextDouble(), (random.nextDouble() - 0.5) * 6), 1, 0.05, 0.05, 0.05, 0.01);
                        }
                    }
                    case AETHELGARD -> {
                        world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, pLoc.clone().add((random.nextDouble() - 0.5) * 8, 1.5 + random.nextDouble() * 2, (random.nextDouble() - 0.5) * 8), 2, 0.1, 0.1, 0.1, 0.01);
                        if (random.nextDouble() < 0.25) {
                            world.spawnParticle(Particle.CHERRY_LEAVES, pLoc.clone().add((random.nextDouble() - 0.5) * 6, 2.0 + random.nextDouble(), (random.nextDouble() - 0.5) * 6), 1, 0.05, 0.05, 0.05, 0.01);
                        }
                    }
                    case XOLTHOS -> {
                        world.spawnParticle(Particle.PORTAL, pLoc.clone().add((random.nextDouble() - 0.5) * 8, 1.5 + random.nextDouble() * 2, (random.nextDouble() - 0.5) * 8), 2, 0.05, 0.05, 0.05, 0.05);
                        if (random.nextDouble() < 0.25) {
                            world.spawnParticle(Particle.DRAGON_BREATH, pLoc.clone().add((random.nextDouble() - 0.5) * 6, 0.5 + random.nextDouble(), (random.nextDouble() - 0.5) * 6), 1, 0.05, 0.05, 0.05, 0.01);
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
    }

    public void checkLeash() {
        LivingEntity entity = boss.getEntity();
        if (entity == null || !entity.isValid() || center.getWorld() == null) return;

        if (entity.getLocation().distanceSquared(center) > (domainRadius * domainRadius)) {
            entity.teleport(center.clone().add(0, 0.5, 0));
            entity.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            center.getWorld().playSound(center, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.8f);
            boss.broadcastToDomain("&c&l[!] " + boss.getDisplayName() + " &cwas dragged beyond the domain and rebounded to the center!");
        }
    }

    private boolean isUtilityBlock(org.bukkit.Material mat) {
        if (mat == null) return false;
        String name = mat.name();
        return name.contains("CHEST") || name.contains("SHULKER_BOX") || name.contains("BARREL")
                || name.contains("ANVIL") || name.contains("CRAFTING_TABLE") || name.contains("ENCHANTING_TABLE")
                || name.contains("BEACON") || name.contains("FURNACE") || name.contains("SMOKER") || name.contains("BLAST_FURNACE");
    }

    public Set<Location> getPlayerPlacedBlocks() {
        return playerPlacedBlocks;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!antiGrief || !boss.isActive()) return;
        Location loc = event.getBlock().getLocation();
        if (isInDomain(loc)) {
            org.bukkit.Material mat = event.getBlock().getType();
            if (playerPlacedBlocks.remove(loc) || isUtilityBlock(mat)) {
                return;
            }
            event.setCancelled(true);
            TextUtil.sendActionBar(event.getPlayer(), "&c[!] Natural arena terrain is protected while the World Boss is alive!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!antiGrief || !boss.isActive()) return;
        Location loc = event.getBlock().getLocation();
        if (isInDomain(loc)) {
            // Anti-Cheesing: Strictly forbid boxing in or placing blocks near dormant boss during awakening
            LivingEntity bossEnt = boss.getEntity();
            if (bossEnt != null && bossEnt.isValid()) {
                if (!boss.isAwakened() && loc.distanceSquared(bossEnt.getLocation()) <= 25.0) { // 5m radius
                    event.setCancelled(true);
                    TextUtil.sendActionBar(event.getPlayer(), "&c&l[!] PRIMORDIAL BARRIER: &7You cannot place blocks near the dormant Titan!");
                    event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.2f, 0.8f);
                    event.getPlayer().getWorld().spawnParticle(Particle.DRAGON_BREATH, loc.clone().add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3, 0.05);
                    return;
                }
            }

            boolean allowUtility = (boss != null && boss.getConfig() != null)
                    ? boss.getConfig().getBoolean("domain.allow-utility-blocks", true)
                    : true;
            org.bukkit.Material mat = event.getBlock().getType();
            if (allowUtility && isUtilityBlock(mat)) {
                playerPlacedBlocks.add(loc);
                return;
            }
            event.setCancelled(true);
            TextUtil.sendActionBar(event.getPlayer(), "&c[!] Placing non-utility blocks inside the Boss Domain is prohibited!");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerTeleport(PlayerTeleportEvent event) {
        if (!lockdownEnabled || !boss.isActive()) return;
        Player player = event.getPlayer();
        if (player.hasPermission("coresuite.boss.admin") || player.isOp()) return;

        if (isLockedIn(player.getUniqueId())) {
            Location to = event.getTo();
            if (to == null || !isInDomain(to)) {
                event.setCancelled(true);
                TextUtil.sendActionBar(player, "&c&l[!] DOMAIN SEAL: &7Teleportation beyond the arena barrier is blocked!");
                player.playSound(player.getLocation(), Sound.BLOCK_BEACON_DEACTIVATE, 1.2f, 0.8f);
            }
        }
    }

    public Location getCenter() {
        return center;
    }

    public double getDomainRadius() {
        return domainRadius;
    }

    public Set<UUID> getLockedPlayers() {
        return java.util.Collections.unmodifiableSet(lockedPlayers);
    }
}
