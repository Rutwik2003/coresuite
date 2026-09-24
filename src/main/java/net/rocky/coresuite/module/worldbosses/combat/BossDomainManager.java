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
    private final Map<UUID, Location> safePositions = new ConcurrentHashMap<>();

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
        int points = Math.max(12, Math.min(72, boss.getConfig().getInt("domain.boundary-points", 36)));
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
        if (!WorldBoss.eligibleFighter(Bukkit.getPlayer(uuid))) return;
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
        int[] cycles = {0};
        this.visualTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (boss == null || !boss.isActive()) {
                stopVisualTask();
                return;
            }
            recoverEmbeddedPlayers();
            if (++cycles[0] % 3 == 0) {
                renderBoundary();
                handleLockdownCycle();
            }
        }, 5L, 5L);
    }

    public void stopVisualTask() {
        if (visualTask != null) {
            visualTask.cancel();
            visualTask = null;
        }
        lockedPlayers.clear();
        safePositions.clear();
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
            if (p == null || !p.isOnline() || !WorldBoss.eligibleFighter(p)) {
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
                BossMovement.push(p, push);
                // A wall can block the swept push. Recover to verified footing instead
                // of repeatedly pinning the player against protected terrain.
                if (p.getVelocity().lengthSquared() < .01 || distSq > Math.pow(domainRadius + 3, 2)) {
                    Location target = safePositions.get(uuid);
                    if (target == null || !isInDomain(target) || !BossMovement.standing(target)) {
                        org.bukkit.util.Vector inward = p.getLocation().toVector().subtract(center.toVector());
                        if (inward.lengthSquared() > .01) inward.normalize().multiply(Math.max(1, domainRadius - 3));
                        target = BossMovement.findLanding(center.clone().add(inward), this::isInDomain);
                    }
                    if (target != null) BossMovement.teleport(p, target, this::isInDomain);
                }

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
                        returnEntityToArena(bossEntity);
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
                        returnEntityToArena(minion);
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

            Particle effect = boundaryParticle.getDataType() == Void.class ? boundaryParticle : Particle.END_ROD;
            for (Player viewer : world.getPlayers()) {
                if (viewer.getLocation().distanceSquared(center) > (domainRadius + 20) * (domainRadius + 20)) continue;
                int stride = BossPreferences.reduced(viewer) ? 6 : 1;
                for (int point = 0; point < cachedBoundaryLocations.size(); point += stride) {
                    viewer.spawnParticle(effect, cachedBoundaryLocations.get(point), 1, .05, .05, .05, 0);
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
                if (BossPreferences.reduced(p) || !boss.getConfig().getBoolean("domain.ambient-particles", true)) continue;
                Location pLoc = p.getLocation();

                switch (boss.getType()) {
                    case BOREALIS -> p.spawnParticle(Particle.SNOWFLAKE, pLoc.clone().add(0, 2, 0), 4, 3, 1, 3, .01);
                    case VALERIUS -> {
                        p.spawnParticle(Particle.ASH, pLoc.clone().add((random.nextDouble() - 0.5) * 8, 1.5 + random.nextDouble() * 2, (random.nextDouble() - 0.5) * 8), 2, 0.1, 0.1, 0.1, 0.02);
                        if (random.nextDouble() < 0.25) {
                            p.spawnParticle(Particle.FLAME, pLoc.clone().add((random.nextDouble() - 0.5) * 6, 0.5 + random.nextDouble(), (random.nextDouble() - 0.5) * 6), 1, 0.05, 0.05, 0.05, 0.01);
                        }
                    }
                    case AETHELGARD -> {
                        p.spawnParticle(Particle.SPORE_BLOSSOM_AIR, pLoc.clone().add((random.nextDouble() - 0.5) * 8, 1.5 + random.nextDouble() * 2, (random.nextDouble() - 0.5) * 8), 2, 0.1, 0.1, 0.1, 0.01);
                        if (random.nextDouble() < 0.25) {
                            p.spawnParticle(Particle.CHERRY_LEAVES, pLoc.clone().add((random.nextDouble() - 0.5) * 6, 2.0 + random.nextDouble(), (random.nextDouble() - 0.5) * 6), 1, 0.05, 0.05, 0.05, 0.01);
                        }
                    }
                    case XOLTHOS -> {
                        p.spawnParticle(Particle.PORTAL, pLoc.clone().add((random.nextDouble() - 0.5) * 8, 1.5 + random.nextDouble() * 2, (random.nextDouble() - 0.5) * 8), 2, 0.05, 0.05, 0.05, 0.05);
                        if (random.nextDouble() < 0.25) {
                            p.spawnParticle(Particle.SMOKE, pLoc.clone().add((random.nextDouble() - 0.5) * 6, 0.5 + random.nextDouble(), (random.nextDouble() - 0.5) * 6), 1, 0.05, 0.05, 0.05, 0.01);
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
            returnEntityToArena(entity);
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
            if (boss.isEncounterWeb(loc)) {
                event.setDropItems(false);
                return;
            }
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
                    event.getPlayer().getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3, 0.05);
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
        return center.clone();
    }

    private void returnEntityToArena(LivingEntity entity) {
        Location landing = net.rocky.coresuite.module.worldbosses.scheduler.BossSpawnScheduler.findNearbyLocation(
                center, this::isInDomain);
        if (landing != null && entity.teleport(landing)) {
            entity.setVelocity(new org.bukkit.util.Vector());
            entity.setFallDistance(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArenaBurn(org.bukkit.event.block.BlockBurnEvent event) {
        if (antiGrief && boss.isActive() && isInDomain(event.getBlock().getLocation())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onArenaFireSpread(org.bukkit.event.block.BlockSpreadEvent event) {
        if (boss.isActive() && event.getSource().getType() == org.bukkit.Material.FIRE
                && boss.isTemporaryBlock(event.getSource().getLocation())) event.setCancelled(true);
    }

    private void recoverEmbeddedPlayers() {
        if (center.getWorld() == null) return;
        safePositions.keySet().removeIf(id -> {
            Player p = Bukkit.getPlayer(id);
            return p == null || p.isDead() || !isInDomain(p.getLocation());
        });
        for (Player player : center.getWorld().getPlayers()) {
            if (!WorldBoss.eligibleFighter(player)
                    || !isInDomain(player.getLocation())) continue;
            Location current = player.getLocation();
            if (BossMovement.standing(current)) {
                safePositions.put(player.getUniqueId(), current.clone());
            } else if (BossMovement.embedded(player)) {
                Location previous = safePositions.get(player.getUniqueId());
                Location target = previous != null && isInDomain(previous) && BossMovement.standing(previous)
                        ? previous : BossMovement.findLanding(current, this::isInDomain);
                if (target == null) target = BossMovement.findLanding(center, this::isInDomain);
                if (target != null && BossMovement.teleport(player, target, this::isInDomain)) {
                    safePositions.put(player.getUniqueId(), player.getLocation());
                    TextUtil.sendActionBar(player, "&aMoved you out of blocked arena terrain.");
                }
            }
        }
    }

    public double getDomainRadius() {
        return domainRadius;
    }

    public Set<UUID> getLockedPlayers() {
        return java.util.Collections.unmodifiableSet(lockedPlayers);
    }
}
