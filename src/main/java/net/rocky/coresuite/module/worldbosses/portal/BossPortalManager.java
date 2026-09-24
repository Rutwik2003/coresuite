package net.rocky.coresuite.module.worldbosses.portal;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.worldbosses.WorldBossesModule;
import net.rocky.coresuite.module.worldbosses.combat.BossMovement;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Manages the lifecycle of arena entrance portals and exit portals for the World Boss system.
 * Includes interactive confirmation prompt for entry fee, dual randomized arena spawn points,
 * 15-second spawn protection against TP killing, and rich, lag-free particle effects.
 */
public class BossPortalManager implements Listener {

    private final CoreSuitePlugin plugin;
    private final WorldBossesModule module;
    private final FileConfiguration config;
    private final Random random = new Random();

    // ── Portal State ─────────────────────────────────────────────────────────
    private final List<PortalInstance> activePortals = new CopyOnWriteArrayList<>();
    private PortalInstance exitPortal;
    /** Default rift cue works without a pack; namespaced values support custom pack audio. */
    static void playRiftSound(Player player, String key, float volume, float pitch) {
        if (key == null || key.isBlank()) return;
        if (key.equals("RIFT_AMBIENT")) {
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_AMBIENT, volume * .45f, .65f);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, volume * .7f, .75f);
        } else if (key.equals("RIFT_FORMING") || key.equals("BLOCK_PORTAL_AMBIENT")) {
            player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, volume * .7f, .6f);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, volume * .8f, .5f);
        } else if (key.contains(":")) {
            player.playSound(player.getLocation(), key, org.bukkit.SoundCategory.BLOCKS, volume, pitch);
        } else {
            player.playSound(player.getLocation(), Sound.valueOf(key), volume, pitch);
        }
    }

    private BukkitTask portalParticleTask;
    private BukkitTask proximityCheckTask;
    private BukkitTask formingTimerTask;
    private BukkitTask exitCleanupTask;
    private BukkitTask exitSpawnTask;
    private boolean searching;
    private long generation;
    private java.util.function.Predicate<Location> arenaOccupancy;
    private long evacuationAt;
    private final Set<UUID> internalTransfers = new HashSet<>();
    private final Map<UUID, Location> outsideLocations = new HashMap<>();
    private boolean forming = false;
    private long searchExpiresAt;
    private long formingEndsAt;
    private long entryClosesAt;
    private Runnable formingCompletion;
    private boolean entryClosureAnnounced;
    private final Set<UUID> enteredPlayers = ConcurrentHashMap.newKeySet();

    public boolean canEnterWindow(UUID player, long now) {
        return portalsActive && (entryClosesAt == 0 || now < entryClosesAt || enteredPlayers.contains(player));
    }

    /** Wall-clock deadlines also recover correctly after an empty server resumes ticking. */
    public void tickLifecycle(long now) {
        if (searching && now >= searchExpiresAt) {
            plugin.getLogger().warning("[WorldBosses] Portal search timed out; clearing the pending encounter.");
            cleanup();
            return;
        }
        if (forming && now >= formingEndsAt) finishForming();
        if (!portalsActive) return;
        if (!module.isBossActive()) {
            endWithoutVictory();
            return;
        }
        if (!entryClosureAnnounced && entryClosesAt > 0 && now >= entryClosesAt) {
            entryClosureAnnounced = true;
            Bukkit.broadcast(TextUtil.colorize("&5Arena entry has closed. Only players who entered during the first 15 minutes may re-enter."));
            if (enteredPlayers.isEmpty()) activePortals.clear();
        }
    }

    private void finishForming() {
        if (!forming) return;
        forming = false;
        if (formingTimerTask != null) { formingTimerTask.cancel(); formingTimerTask = null; }
        Runnable ready = formingCompletion;
        formingCompletion = null;
        try {
            activatePortals();
            if (ready != null) ready.run();
        } catch (RuntimeException | LinkageError error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "[WorldBosses] Rift activation failed; clearing encounter", error);
            cleanup();
        }
    }

    public void endWithoutVictory() {
        if (arenaCenter != null && Bukkit.getOnlinePlayers().stream().anyMatch(p -> inArena(p.getLocation())))
            spawnExitPortal(arenaCenter.clone());
        else cleanup();
    }
    private boolean portalsActive = false;
    private Location arenaCenter;

    // ── Player Tracking ──────────────────────────────────────────────────────
    /** Maps player UUID → the entrance portal location they used to enter */
    private final Map<UUID, Location> playerEntranceLocations = new ConcurrentHashMap<>();
    /** Players who have already paid the entry fee for this encounter */
    private final Set<UUID> paidPlayers = ConcurrentHashMap.newKeySet();
    /** Cooldown for sending chat payment prompts (UUID -> timestamp) */
    private final Map<UUID, Long> promptCooldowns = new ConcurrentHashMap<>();
    /** 15-second spawn protection immunity (UUID -> expiry timestamp) */
    private final Map<UUID, Long> spawnProtection = new ConcurrentHashMap<>();

    // ── Particle Animation State ─────────────────────────────────────────────
    private double particleAngle = 0;
    private int tickCounter = 0;

    public BossPortalManager(CoreSuitePlugin plugin, WorldBossesModule module, FileConfiguration config) {
        this.plugin = plugin;
        this.module = module;
        this.config = config;
        loadReturnLocations();
    }

    private boolean inArena(Location location) {
        if (location == null || location.getWorld() == null) return false;
        if (arenaOccupancy == null && config.getBoolean("arena.fixed-location.enabled", true)) {
            World world = Bukkit.getWorld(config.getString("arena.fixed-location.world", "world"));
            if (world != null) {
                Location center = new Location(world, config.getDouble("arena.fixed-location.x", 49662),
                        config.getDouble("arena.fixed-location.y", 205), config.getDouble("arena.fixed-location.z", 49528));
                arenaOccupancy = net.rocky.coresuite.common.integration.BossProtectionGate.arenaOccupancy(center,
                        Math.max(64, config.getDouble("domain.radius", 35) + config.getDouble("domain.spectator-distance", 16)));
            }
        }
        return arenaOccupancy != null && arenaOccupancy.test(location);
    }

    private boolean admissionAllowed(Player player) {
        return internalTransfers.contains(player.getUniqueId()) || player.hasPermission("coresuite.boss.admin")
                || (paidPlayers.contains(player.getUniqueId()) && (canEnterWindow(player.getUniqueId(), System.currentTimeMillis()) || exitPortal != null || exitSpawnTask != null));
    }

    public boolean canParticipate(Player player) {
        return !config.getBoolean("arena.portals.enabled", true) || !inArena(player.getLocation()) || admissionAllowed(player);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaTeleport(org.bukkit.event.player.PlayerTeleportEvent event) {
        if (!config.getBoolean("arena.portals.enabled", true) || internalTransfers.contains(event.getPlayer().getUniqueId())) return;
        if (inArena(event.getTo()) && !admissionAllowed(event.getPlayer())) {
            event.setCancelled(true);
            TextUtil.sendMessage(event.getPlayer(), "&cEnter through an active arena rift using /boss enter. Direct teleports do not pay the entry fee.");
        } else if (!inArena(event.getFrom()) && inArena(event.getTo())) {
            outsideLocations.put(event.getPlayer().getUniqueId(), event.getFrom().clone());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArenaMove(org.bukkit.event.player.PlayerMoveEvent event) {
        if (event instanceof org.bukkit.event.player.PlayerTeleportEvent || event.getTo() == null
                || !config.getBoolean("arena.portals.enabled", true)) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX() && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        if (inArena(event.getTo()) && !admissionAllowed(event.getPlayer())) {
            if (!inArena(event.getFrom())) event.setCancelled(true);
            else handleExitPortal(event.getPlayer());
        }
    }

    @EventHandler public void onArenaJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        if (!config.getBoolean("arena.portals.enabled", true)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Player player = event.getPlayer();
            if (player.isOnline() && inArena(player.getLocation())
                    && (!admissionAllowed(player) || (evacuationAt > 0 && System.currentTimeMillis() >= evacuationAt))) handleExitPortal(player);
        }, 20L);
    }

    @EventHandler public void onArenaRespawn(org.bukkit.event.player.PlayerRespawnEvent event) {
        if (!config.getBoolean("arena.portals.enabled", true)) return;
        if (inArena(event.getRespawnLocation()) && !admissionAllowed(event.getPlayer())) {
            Location safe = returnLocation(event.getPlayer());
            if (safe != null) event.setRespawnLocation(safe);
        }
    }

    private void loadReturnLocations() {
        if (plugin == null) return;
        java.io.File file = new java.io.File(plugin.getDataFolder(), "boss-arena-returns.yml");
        if (!file.exists()) return;
        try {
            var yaml = new org.bukkit.configuration.file.YamlConfiguration(); yaml.load(file);
            for (String key : yaml.getKeys(false)) {
                Location location = yaml.getLocation(key);
                if (location != null) playerEntranceLocations.put(UUID.fromString(key), location);
            }
        } catch (Exception error) { plugin.getLogger().log(java.util.logging.Level.WARNING, "Cannot load boss arena return locations", error); }
    }

    private void saveReturnLocations() {
        if (plugin == null) return;
        try {
            var yaml = new org.bukkit.configuration.file.YamlConfiguration();
            playerEntranceLocations.forEach((uuid, location) -> yaml.set(uuid.toString(), location));
            java.nio.file.Path target = new java.io.File(plugin.getDataFolder(), "boss-arena-returns.yml").toPath();
            java.nio.file.Path temp = java.nio.file.Files.createTempFile(target.getParent(), "boss-returns-", ".tmp");
            try {
                yaml.save(temp.toFile());
                try { java.nio.file.Files.move(temp, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
                catch (java.nio.file.AtomicMoveNotSupportedException ignored) { java.nio.file.Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
            } finally { java.nio.file.Files.deleteIfExists(temp); }
        } catch (Exception error) { plugin.getLogger().log(java.util.logging.Level.WARNING, "Cannot save boss arena return locations", error); }
    }

    private Location returnLocation(Player player) {
        List<Location> candidates = new ArrayList<>();
        candidates.add(playerEntranceLocations.get(player.getUniqueId()));
        candidates.add(outsideLocations.get(player.getUniqueId()));
        for (World world : Bukkit.getWorlds()) candidates.add(world.getSpawnLocation());
        for (Location candidate : candidates) {
            if (candidate == null || candidate.getWorld() == null || inArena(candidate)) continue;
            candidate.getWorld().getChunkAt(candidate);
            Location landing = BossMovement.findLanding(candidate, at -> !inArena(at) && at.getWorld().getWorldBorder().isInside(at));
            if (landing != null) return landing;
        }
        return null;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Spawn Protection Event Handler (15s Invulnerability)
    // ═══════════════════════════════════════════════════════════════════════════

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            Long expiry = spawnProtection.get(player.getUniqueId());
            if (expiry != null) {
                long now = System.currentTimeMillis();
                if (now < expiry) {
                    event.setCancelled(true);
                    int remaining = (int) Math.ceil((expiry - now) / 1000.0);
                    TextUtil.sendActionBar(player, "&a🛡 &lSPAWN PROTECTION: &e" + remaining + "s &8(&fImmune to all damage&8)");
                } else {
                    spawnProtection.remove(player.getUniqueId());
                    TextUtil.sendActionBar(player, "&c🛡 &lSpawn Protection Expired! &fPrepare for battle!");
                }
            }
        }
    }

    public boolean isProtected(UUID uuid) {
        Long expiry = spawnProtection.get(uuid);
        if (expiry == null) return false;
        if (System.currentTimeMillis() < expiry) return true;
        spawnProtection.remove(uuid);
        return false;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Portal Spawning
    // ═══════════════════════════════════════════════════════════════════════════

    public void spawnPortals(Location arenaCenter, Runnable onReady) {
        if (!config.getBoolean("arena.portals.enabled", true)) {
            onReady.run();
            return;
        }

        this.arenaCenter = arenaCenter.clone();
        arenaOccupancy = net.rocky.coresuite.common.integration.BossProtectionGate.arenaOccupancy(arenaCenter,
                Math.max(64, config.getDouble("domain.radius", 35) + config.getDouble("domain.spectator-distance", 16)));
        evacuationAt = 0;
        searching = true;
        searchExpiresAt = System.currentTimeMillis() + 120_000L;
        long searchGeneration = ++generation;
        int portalCount = Math.max(1, Math.min(10, config.getInt("arena.portals.count", 3)));
        int searchRadius = config.getInt("arena.portals.search-radius", 8000);
        int maxAttempts = config.getInt("arena.portals.search-attempts", 50);

        AtomicInteger found = new AtomicInteger(0);
        AtomicInteger pending = new AtomicInteger(portalCount);

        for (int i = 0; i < portalCount; i++) {
            searchPortalLocationAsync(arenaCenter.getWorld(), searchRadius, maxAttempts, location -> {
                if (searchGeneration != generation) return;
                if (location != null) {
                    PortalInstance portal = new PortalInstance(location.clone());
                    activePortals.add(portal);
                    found.incrementAndGet();
                }
                if (pending.decrementAndGet() <= 0) {
                    searching = false;
                    if (activePortals.isEmpty()) {
                        plugin.getLogger().warning("[WorldBosses] Failed to find portal locations! Spawning boss directly.");
                        onReady.run();
                        return;
                    }
                    startFormingPhase(onReady);
                }
            });
        }
    }

    private void searchPortalLocationAsync(World world, int radius, int attemptsLeft, Consumer<Location> callback) {
        if (attemptsLeft <= 0 || world == null) {
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
            return;
        }

        Location worldSpawn = world.getSpawnLocation();
        int offsetX = random.nextInt(radius * 2) - radius;
        int offsetZ = random.nextInt(radius * 2) - radius;
        int targetX = worldSpawn.getBlockX() + offsetX;
        int targetZ = worldSpawn.getBlockZ() + offsetZ;

        world.getChunkAtAsync(targetX >> 4, targetZ >> 4).thenAccept(chunk ->
            Bukkit.getScheduler().runTask(plugin, () -> {
                Location testLoc = new Location(world, targetX, 0, targetZ);
                Block highest = world.getHighestBlockAt(testLoc, HeightMap.MOTION_BLOCKING_NO_LEAVES);

                if (isValidPortalLocation(highest)) {
                    Location portalLoc = highest.getLocation().add(0.5, 1.0, 0.5);

                    int minDist = config.getInt("arena.portals.min-portal-distance", 250);
                    boolean tooClose = false;
                    for (PortalInstance existing : activePortals) {
                        if (existing.location.distanceSquared(portalLoc) < (double) minDist * minDist) {
                            tooClose = true;
                            break;
                        }
                    }

                    if (!tooClose) {
                        callback.accept(portalLoc);
                        return;
                    }
                }
                searchPortalLocationAsync(world, radius, attemptsLeft - 1, callback);
            })
        ).exceptionally(ex -> {
            Bukkit.getScheduler().runTask(plugin, () ->
                searchPortalLocationAsync(world, radius, attemptsLeft - 1, callback));
            return null;
        });
    }

    private boolean isValidPortalLocation(Block highest) {
        if (highest == null) return false;
        Material mat = highest.getType();
        if (!mat.isSolid()) return false;

        String name = mat.name();
        if (mat == Material.LAVA || name.contains("MAGMA") || name.contains("CACTUS")
                || name.contains("CAMPFIRE") || name.contains("LEAVES") || name.contains("POWDER_SNOW")) {
            return false;
        }

        World world = highest.getWorld();
        int x = highest.getX(), y = highest.getY() + 1, z = highest.getZ();
        for (int dy = 0; dy < 3; dy++) {
            Block above = world.getBlockAt(x, y + dy, z);
            if (!above.getType().isAir() && !above.isPassable()) return false;
        }

        if (!world.getWorldBorder().isInside(highest.getLocation().add(0.5, 0, 0.5))) return false;

        String denial = net.rocky.coresuite.common.integration.BossProtectionGate.denialReason(
                highest.getLocation().add(0.5, 1, 0.5));
        return denial == null;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Forming Phase
    // ═══════════════════════════════════════════════════════════════════════════

    private void startFormingPhase(Runnable onReady) {
        forming = true;
        portalsActive = false;
        int formingMinutes = config.getInt("arena.portals.forming-minutes", 8);
        formingMinutes = Math.max(0, Math.min(60, formingMinutes));
        formingEndsAt = System.currentTimeMillis() + formingMinutes * 60_000L;
        formingCompletion = onReady;

        String formingMsg = config.getString("arena.portals.forming-broadcast",
                "&5&l⚔ &d Dark rifts are tearing open across the world... Boss portals forming!");
        Bukkit.broadcast(TextUtil.colorize(""));
        Bukkit.broadcast(TextUtil.colorize("&4&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        Bukkit.broadcast(TextUtil.colorize(formingMsg));
        Bukkit.broadcast(TextUtil.colorize("  &7The rifts will fully open in &e" + formingMinutes + " minutes&7!"));
        Bukkit.broadcast(TextUtil.colorize("  &7Travel to your nearest rift to prepare for battle:"));
        Bukkit.broadcast(TextUtil.colorize(""));

        String locFormat = config.getString("arena.portals.portal-location-format",
                "&8 → &7Portal at &e{x}&7, &e{z} &8(&7{distance}m away&8)");
        for (PortalInstance portal : activePortals) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.getWorld().equals(portal.location.getWorld())) continue;
                int dist = (int) p.getLocation().distance(portal.location);
                String msg = locFormat
                        .replace("{x}", String.valueOf(portal.location.getBlockX()))
                        .replace("{z}", String.valueOf(portal.location.getBlockZ()))
                        .replace("{distance}", String.valueOf(dist));
                p.sendMessage(TextUtil.colorize(msg));
            }
        }

        Bukkit.broadcast(TextUtil.colorize("&4&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        Bukkit.broadcast(TextUtil.colorize(""));

        String formingSound = config.getString("arena.portals.forming-sound", "BLOCK_PORTAL_AMBIENT");
        try {
            String sound = formingSound;
            for (Player p : Bukkit.getOnlinePlayers()) {
                playRiftSound(p, sound, 1.0f, 0.8f);
            }
        } catch (IllegalArgumentException ignored) {}

        startParticleTask();

        formingTimerTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            finishForming();
        }, formingMinutes * 60L * 20L);
    }

    private void activatePortals() {
        portalsActive = true;
        entryClosesAt = System.currentTimeMillis() + 15 * 60_000L;
        entryClosureAnnounced = false;
        Bukkit.broadcast(TextUtil.colorize("&dRifts admit new fighters for 15 minutes. Enter now to secure re-entry for this encounter."));
        for (PortalInstance portal : activePortals) {
            portal.active = true;
        }

        String activeMsg = config.getString("arena.portals.active-broadcast",
                "&5&l⚔ &d The Arena Rifts have opened! Find a portal to enter the battle!");
        Bukkit.broadcast(TextUtil.colorize(""));
        Bukkit.broadcast(TextUtil.colorize("&5&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        Bukkit.broadcast(TextUtil.colorize(activeMsg));

        String locFormat = config.getString("arena.portals.portal-location-format",
                "&8 → &7Portal at &e{x}&7, &e{z} &8(&7{distance}m away&8)");
        for (PortalInstance portal : activePortals) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (!p.getWorld().equals(portal.location.getWorld())) continue;
                int dist = (int) p.getLocation().distance(portal.location);
                String msg = locFormat
                        .replace("{x}", String.valueOf(portal.location.getBlockX()))
                        .replace("{z}", String.valueOf(portal.location.getBlockZ()))
                        .replace("{distance}", String.valueOf(dist));
                p.sendMessage(TextUtil.colorize(msg));
            }
        }

        Bukkit.broadcast(TextUtil.colorize("&5&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        Bukkit.broadcast(TextUtil.colorize(""));

        try {
            String startSound = config.getString("arena.portals.start-sound", "UI_TOAST_CHALLENGE_COMPLETE");
            String sound = startSound;
            for (Player p : Bukkit.getOnlinePlayers()) {
                playRiftSound(p, sound, 1.0f, 1.0f);
            }
        } catch (IllegalArgumentException ignored) {}

        startProximityTask();
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Particle Effects (Dense, Tall & Highly Visible)
    // ═══════════════════════════════════════════════════════════════════════════

    private void startParticleTask() {
        if (portalParticleTask != null) portalParticleTask.cancel();

        // Run every 2 ticks (0.1s) for ultra-smooth fluid rotation
        portalParticleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            try {
                particleAngle += 0.25;
                tickCounter++;

                // 1. Entrance portals
                for (PortalInstance portal : activePortals) {
                    Location loc = portal.location;
                    World world = loc.getWorld();
                    if (world == null) continue;

                    // Render for all players within 96 blocks
                    boolean hasNearbyPlayer = false;
                    for (Player p : world.getPlayers()) {
                        if (p.getLocation().distanceSquared(loc) < 9216) { // 96^2
                            hasNearbyPlayer = true;
                            break;
                        }
                    }
                    if (!hasNearbyPlayer) continue;

                    if (portal.active) {
                        // Active Portal: Upright Dimensional Rift Tear
                        double riftCenterY = loc.getY() + 1.75;
                        double riftFacingAngle = particleAngle * 0.15;
                        double cosFace = Math.cos(riftFacingAngle);
                        double sinFace = Math.sin(riftFacingAngle);

                        // 1. Upright Elliptical Tear Contour (20 perimeter nodes)
                        for (int i = 0; i < 20; i++) {
                            double theta = (2 * Math.PI * i / 20) + (particleAngle * 0.4);
                            double u = Math.cos(theta) * 0.95;
                            double v = Math.sin(theta) * 1.40;
                            double px = loc.getX() + u * cosFace;
                            double py = riftCenterY + v;
                            double pz = loc.getZ() + u * sinFace;

                            spawnParticleSafe(world, Particle.PORTAL, px, py, pz, 1, 0, 0, 0, 0.05);
                            if (i % 2 == 0) {
                                spawnParticleSafe(world, Particle.REVERSE_PORTAL, px, py, pz, 1, 0.02, 0.02, 0.02, 0.01);
                            }
                            if (i % 5 == 0) {
                                spawnParticleSafe(world, Particle.SOUL_FIRE_FLAME, px, py, pz, 1, 0.01, 0.01, 0.01, 0.01);
                            }
                        }

                        // 2. Singularity Core (Interior Void Drift)
                        double innerU = (Math.random() - 0.5) * 1.2;
                        double innerV = (Math.random() - 0.5) * 2.2;
                        if ((innerU * innerU) / 0.75 + (innerV * innerV) / 1.75 <= 1.0) {
                            double coreX = loc.getX() + innerU * cosFace;
                            double coreY = riftCenterY + innerV;
                            double coreZ = loc.getZ() + innerU * sinFace;
                            spawnParticleSafe(world, Particle.PORTAL, coreX, coreY, coreZ, 2, 0.05, 0.05, 0.05, 0.02);
                            spawnParticleSafe(world, Particle.SOUL_FIRE_FLAME, coreX, coreY, coreZ, 1, 0.02, 0.02, 0.02, 0.01);
                            if (tickCounter % 3 == 0) {
                                spawnParticleSafe(world, Particle.SOUL, coreX, coreY, coreZ, 1, 0.01, 0.02, 0.01, 0.02);
                            }
                        }

                        // 3. Gravitational Inward Pull (Enchant runes rushing into rift)
                        for (int i = 0; i < 3; i++) {
                            double r = 1.4 + (Math.random() * 1.4);
                            double a = particleAngle * 2.0 + (i * Math.PI * 2 / 3);
                            double gx = loc.getX() + Math.cos(a) * r;
                            double gz = loc.getZ() + Math.sin(a) * r;
                            double gy = riftCenterY + ((Math.random() - 0.5) * 1.5);
                            spawnParticleSafe(world, Particle.ENCHANT, gx, gy, gz, 1, 0, 0, 0, 0.5);
                        }

                        // 4. Ground Rupture Anchor (Ground-level rune aura)
                        for (int i = 0; i < 8; i++) {
                            double a = (Math.PI * 2 / 8) * i - particleAngle * 0.5;
                            double gx = loc.getX() + Math.cos(a) * 1.3;
                            double gz = loc.getZ() + Math.sin(a) * 1.3;
                            spawnParticleSafe(world, Particle.WITCH, gx, loc.getY() + 0.1, gz, 1, 0, 0, 0, 0);
                        }

                        // 5. Sky Rift Pillar (ethereal beacon up 14m)
                        if (tickCounter % 3 == 0) {
                            for (double yOff = 1.0; yOff <= 14.0; yOff += 2.0) {
                                spawnParticleSafe(world, Particle.END_ROD, loc.getX(), loc.getY() + yOff, loc.getZ(),
                                        1, 0.1, 0.15, 0.1, 0.01);
                            }
                        }
                    } else {
                        // Forming Phase: Concentrated Swirling Void Anomaly (No smoke or squid ink)
                        double formCenterY = loc.getY() + 1.75;

                        // Spinning dual-ring vortex
                        for (int i = 0; i < 10; i++) {
                            double a1 = particleAngle * 2.5 + (Math.PI * 2 / 10) * i;
                            double r1 = 1.2 + 0.3 * Math.sin(particleAngle + i);
                            double vx = loc.getX() + Math.cos(a1) * r1;
                            double vz = loc.getZ() + Math.sin(a1) * r1;
                            double vy = formCenterY + 0.5 * Math.sin(a1 * 1.5);
                            spawnParticleSafe(world, Particle.PORTAL, vx, vy, vz, 1, 0, 0.05, 0, 0.04);
                            if (i % 2 == 0) {
                                spawnParticleSafe(world, Particle.REVERSE_PORTAL, vx, vy, vz, 1, 0.02, 0.02, 0.02, 0.02);
                            }
                        }

                        // Core gathering cloud
                        spawnParticleSafe(world, Particle.SOUL_FIRE_FLAME, loc.getX(), formCenterY, loc.getZ(), 2, 0.2, 0.2, 0.2, 0.01);
                        if (tickCounter % 4 == 0) {
                            spawnParticleSafe(world, Particle.WITCH, loc.getX(), formCenterY, loc.getZ(), 3, 0.3, 0.3, 0.3, 0.02);
                        }

                        // Inward rushing runes
                        for (int i = 0; i < 4; i++) {
                            double a = (Math.PI * 2 / 4) * i + particleAngle;
                            double rx = loc.getX() + Math.cos(a) * 2.5;
                            double rz = loc.getZ() + Math.sin(a) * 2.5;
                            spawnParticleSafe(world, Particle.ENCHANT, rx, formCenterY + (Math.random() - 0.5), rz, 1, 0, 0, 0, 0.6);
                        }

                        // Sky beacon beam to locate from distance
                        if (tickCounter % 3 == 0) {
                            for (double yOff = 1.5; yOff <= 16.0; yOff += 2.5) {
                                spawnParticleSafe(world, Particle.END_ROD, loc.getX(), loc.getY() + yOff, loc.getZ(),
                                        1, 0.12, 0.2, 0.12, 0.01);
                            }
                        }
                    }
                }

                // 2. Exit Portal (Golden / Emerald Light Gate)
                if (exitPortal != null) {
                    Location loc = exitPortal.location;
                    World world = loc.getWorld();
                    if (world != null) {
                        double radius = config.getDouble("arena.exit-portal.radius", 2.5);
                        for (int i = 0; i < 12; i++) {
                            double angle = particleAngle + (Math.PI * 2 / 12) * i;
                            double px = loc.getX() + Math.cos(angle) * radius;
                            double pz = loc.getZ() + Math.sin(angle) * radius;
                            spawnParticleSafe(world, Particle.HAPPY_VILLAGER, px, loc.getY() + 0.4, pz, 1, 0, 0.1, 0, 0);
                        }
                        if (tickCounter % 2 == 0) {
                            for (double y = 0.5; y <= 6.0; y += 1.5) {
                                spawnParticleSafe(world, Particle.END_ROD, loc.getX(), loc.getY() + y, loc.getZ(),
                                        1, 0.2, 0.2, 0.2, 0.01);
                            }
                        }
                    }
                }

                // 3. Spawn Protection Aura on protected players
                if (!spawnProtection.isEmpty()) {
                    long now = System.currentTimeMillis();
                    for (Map.Entry<UUID, Long> entry : spawnProtection.entrySet()) {
                        if (now >= entry.getValue()) {
                            spawnProtection.remove(entry.getKey());
                            continue;
                        }
                        Player p = Bukkit.getPlayer(entry.getKey());
                        if (p != null && p.isOnline() && !p.isDead()) {
                            Location pLoc = p.getLocation().add(0, 1.0, 0);
                            spawnParticleSafe(p.getWorld(), Particle.WAX_ON, pLoc.getX(), pLoc.getY(), pLoc.getZ(), 3, 0.4, 0.5, 0.4, 0.01);
                        }
                    }
                }
            } catch (Throwable ignored) {
                // Safeguard against version-specific particle errors
            }
        }, 2L, 2L);
    }

    /**
     * Spawns a particle safely, supplying required data (e.g. Float scale) if required by the server's Paper build,
     * and silently catching any version discrepancies so scheduler tasks never fail.
     */
    private void spawnParticleSafe(World world, Particle particle, double x, double y, double z, int count,
                                   double offsetX, double offsetY, double offsetZ, double extra) {
        if (world == null || particle == null) return;
        try {
            Class<?> dataType = particle.getDataType();
            if (dataType == Void.class) {
                world.spawnParticle(particle, x, y, z, count, offsetX, offsetY, offsetZ, extra);
            } else if (dataType == Float.class) {
                world.spawnParticle(particle, x, y, z, count, offsetX, offsetY, offsetZ, extra, 1.0f);
            } else {
                world.spawnParticle(particle, x, y, z, count, offsetX, offsetY, offsetZ, extra);
            }
        } catch (Throwable ignored) {}
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Proximity Detection & Interactive Payment Prompt
    // ═══════════════════════════════════════════════════════════════════════════

    private void startProximityTask() {
        if (proximityCheckTask != null) proximityCheckTask.cancel();

        double detectRadius = config.getDouble("arena.portals.detect-radius", 6.0);
        double exitRadius = config.getDouble("arena.exit-portal.radius", 2.5);

        proximityCheckTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.isDead() || !player.isValid()) continue;
                Location playerLoc = player.getLocation();
                UUID uuid = player.getUniqueId();
                if (config.getBoolean("arena.portals.enabled", true) && inArena(playerLoc) && !admissionAllowed(player)) {
                    handleExitPortal(player);
                    continue;
                }

                // Check entrance portals
                for (PortalInstance portal : activePortals) {
                    if (!portal.active) continue;
                    if (portal.location.getWorld() != playerLoc.getWorld()) continue;

                    double distSq = horizontalDistanceSquared(playerLoc, portal.location);
                    double vertDist = Math.abs(playerLoc.getY() - portal.location.getY());
                    if (vertDist > 5.0) continue;

                    if (distSq <= detectRadius * detectRadius) {
                        handlePortalProximity(player, portal, distSq);
                        break;
                    }
                }

                // Check exit portal
                if (exitPortal != null && exitPortal.location.getWorld() == playerLoc.getWorld()) {
                    double distSq = horizontalDistanceSquared(playerLoc, exitPortal.location);
                    double vertDist = Math.abs(playerLoc.getY() - exitPortal.location.getY());
                    if (distSq <= exitRadius * exitRadius && vertDist <= 3.0) {
                        handleExitPortal(player);
                    } else if (distSq <= (exitRadius + 3.0) * (exitRadius + 3.0) && vertDist <= 5.0) {
                        String exitPrompt = config.getString("arena.portals.exit-prompt",
                                "&a&l✦ &fStep into the light to return to where you came from!");
                        TextUtil.sendActionBar(player, exitPrompt);
                    }
                }
            }
        }, 10L, 10L); // Run every 0.5s
    }

    /**
     * Handles approaching an entrance rift:
     * - If already paid: free instant step-in or actionbar prompt.
     * - If not paid: displays actionbar and debounced interactive chat prompt with clickable "/boss enter" button.
     *   NO accidental or automatic 50k deduction!
     */
    private void handlePortalProximity(Player player, PortalInstance portal, double distSq) {
        UUID uuid = player.getUniqueId();

        // If player is already inside the arena, skip
        if (arenaCenter != null && player.getWorld() == arenaCenter.getWorld()) {
            double distToArena = horizontalDistanceSquared(player.getLocation(), arenaCenter);
            if (distToArena <= 100 * 100) return;
        }

        double fee = config.getDouble("arena.portals.entry-fee", 50000.0);
        boolean hasPaid = paidPlayers.contains(uuid);
        if (!canEnterWindow(uuid, System.currentTimeMillis())) {
            TextUtil.sendActionBar(player, "&cEntry closed: this rift is reserved for fighters who entered in time.");
            return;
        }

        if (hasPaid && config.getBoolean("arena.portals.allow-free-reentry", true)) {
            // Player already paid: allow free re-entry either on step-in (3.0m) or via /boss enter
            if (distSq <= 3.0 * 3.0) {
                if (!teleportToArena(player, portal.location)) return;
                String reentryMsg = config.getString("arena.portals.reentry-message",
                        "&a&l✦ &fWelcome back to the arena! &8(No charge — already paid)");
                TextUtil.sendMessage(player, reentryMsg);
            } else {
                TextUtil.sendActionBar(player, "&a&l✦ &fStep into the rift to re-enter the Boss Arena &8(&aFREE RE-ENTRY&8)");
            }
            return;
        }

        // Player has NOT paid yet: SHOW PROMPT, DO NOT CHARGE OR TELEPORT DIRECTLY!
        TextUtil.sendActionBar(player, "&5&l⚡ &fAncient Rift: Type &e/boss enter &for click chat to pay &e$" + String.format("%,.0f", fee) + " &f& enter!");

        // Send clickable chat prompt once every 15 seconds to avoid spamming
        long now = System.currentTimeMillis();
        Long lastPrompt = promptCooldowns.get(uuid);
        if (lastPrompt == null || (now - lastPrompt) > 15000L) {
            promptCooldowns.put(uuid, now);

            player.sendMessage(TextUtil.colorize(""));
            player.sendMessage(TextUtil.colorize("&5&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            player.sendMessage(TextUtil.colorize("  &5&l⚡ ANCIENT ARENA RIFT DETECTED ⚡"));
            player.sendMessage(TextUtil.colorize("  &7An unstable dimensional rift to the World Boss Arena lies before you."));
            player.sendMessage(TextUtil.colorize("  &7Entry Fee: &e$" + String.format("%,.0f", fee) + " &8(One-time fee; free re-entry on death)"));
            player.sendMessage(TextUtil.colorize(""));

            Component button = TextUtil.colorize("  &a&l[ ✦ CLICK HERE TO PAY $" + String.format("%,.0f", fee) + " & ENTER ARENA ✦ ]")
                    .clickEvent(ClickEvent.runCommand("/boss enter"))
                    .hoverEvent(HoverEvent.showText(TextUtil.colorize("&eClick to confirm payment of &6$" + String.format("%,.0f", fee) + " &eand enter!")));
            player.sendMessage(button);

            player.sendMessage(TextUtil.colorize("  &7Or type &e/boss enter &7in chat to confirm."));
            player.sendMessage(TextUtil.colorize("&5&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            player.sendMessage(TextUtil.colorize(""));

            try {
                playRiftSound(player, config.getString("arena.portals.ambient-sound", "RIFT_AMBIENT"), 0.7f, 1.0f);
            } catch (Throwable ignored) {}
        }
    }

    /**
     * Called when a player types /boss enter (or clicks the interactive chat prompt).
     * Validates that the player is near a portal, verifies balance, charges 50k,
     * and teleports to one of the two random spawn points with 15s protection.
     */
    public void handlePlayerEnterCommand(Player player) {
        UUID uuid = player.getUniqueId();
        Location playerLoc = player.getLocation();

        // Check if boss encounter is active
        if (!portalsActive) {
            if (forming) {
                TextUtil.sendMessage(player, "&cThe Arena Rifts are still forming! Wait for them to fully open.");
            } else {
                TextUtil.sendMessage(player, "&cNo World Boss Arena Rifts are currently active.");
            }
            return;
        }

        // Five-block sphere: being far above/below a rift must not grant entry.
        if (!canEnterWindow(uuid, System.currentTimeMillis())) {
            TextUtil.sendMessage(player, "&cThe 15-minute entry window has closed. Only existing fighters may re-enter.");
            return;
        }
        PortalInstance nearbyPortal = null;
        double closestDistSq = Double.MAX_VALUE;
        for (PortalInstance portal : activePortals) {
            if (!portal.active || portal.location.getWorld() != playerLoc.getWorld()) continue;
            double distSq = playerLoc.distanceSquared(portal.location);
            if (withinEntranceRange(playerLoc, portal.location) && distSq < closestDistSq) {
                closestDistSq = distSq;
                nearbyPortal = portal;
            }
        }

        if (nearbyPortal == null) {
            TextUtil.sendMessage(player, "&cYou must be within 5 blocks of an active entrance rift to enter! Use &e/boss portals &cto locate one.");
            return;
        }

        double fee = config.getDouble("arena.portals.entry-fee", 50000.0);
        boolean allowFreeReentry = config.getBoolean("arena.portals.allow-free-reentry", true);

        // Check if already paid
        if (paidPlayers.contains(uuid) && allowFreeReentry) {
            if (teleportToArena(player, nearbyPortal.location))
                TextUtil.sendMessage(player, "&a&l✦ &fWelcome back to the Arena! &8(No charge — already paid)");
            return;
        }

        // Check economy
        net.rocky.coresuite.common.economy.EconomyManager eco = plugin.getEconomyManager();
        if (!Double.isFinite(fee) || fee < 0) {
            TextUtil.sendMessage(player, "&cThe arena entry fee is invalid. Please contact an admin."); return;
        }
        if (fee == 0) {
            paidPlayers.add(uuid);
            if (!teleportToArena(player, nearbyPortal.location)) paidPlayers.remove(uuid);
            return;
        }
        if (eco == null || !eco.isAvailable()) {
            TextUtil.sendMessage(player, "&cArena entry is unavailable because the economy is offline. You were not charged."); return;
        }

        double balance = eco.getBalance(player);
        if (balance < fee) {
            String insufficientMsg = config.getString("arena.portals.insufficient-funds",
                    "&c&l✘ &fYou need &e${fee} &fto enter the Boss Arena! &8(Balance: &e${balance}&8)");
            insufficientMsg = insufficientMsg
                    .replace("{fee}", String.format("%,.0f", fee))
                    .replace("{balance}", String.format("%,.0f", balance));
            TextUtil.sendMessage(player, insufficientMsg);
            try {
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
            } catch (Throwable ignored) {}
            return;
        }

        // Deduct fee
        if (!eco.withdraw(player, fee)) {
            TextUtil.sendMessage(player, "&cPayment was not confirmed. Entry was cancelled; contact an admin if money was deducted."); return;
        }
        paidPlayers.add(uuid);

        // Teleport to arena
        if (!teleportToArena(player, nearbyPortal.location)) {
            if (eco.deposit(player, fee)) {
                paidPlayers.remove(uuid);
                TextUtil.sendMessage(player, "&cThe teleport was blocked or unsafe. Your entry fee was refunded.");
            } else {
                TextUtil.sendMessage(player, "&cThe teleport failed and the refund was not confirmed. Your admission is retained; contact an admin before paying again.");
                plugin.getLogger().warning("[WorldBosses] Entry refund requires review for " + uuid + ": " + fee);
            }
            return;
        }

        String enterMsg = config.getString("arena.portals.enter-success",
                "&a&l✦ &fYou have entered the arena! &8$50,000 charged.");
        enterMsg = enterMsg.replace("{fee}", String.format("%,.0f", fee));
        TextUtil.sendMessage(player, enterMsg);
    }

    /**
     * Uses configured arena landings and grants spawn protection only after a successful teleport.
     */
    static boolean withinEntranceRange(Location player, Location entrance) {
        return player.getWorld() != null && player.getWorld().equals(entrance.getWorld())
                && player.distanceSquared(entrance) <= 25.0;
    }

    private boolean teleportToArena(Player player, Location entranceLoc) {
        if (arenaCenter == null) return false;
        World world = arenaCenter.getWorld();
        if (world == null) return false;

        // Departure effects at entrance rift
        Location from = player.getLocation();
        World fWorld = from.getWorld();
        if (fWorld != null) {
            spawnParticleSafe(fWorld, Particle.REVERSE_PORTAL, from.getX(), from.getY() + 1, from.getZ(), 35, 0.4, 0.8, 0.4, 0.1);
            spawnParticleSafe(fWorld, Particle.SOUL_FIRE_FLAME, from.getX(), from.getY() + 1, from.getZ(), 20, 0.3, 0.6, 0.3, 0.05);
            spawnParticleSafe(fWorld, Particle.SOUL, from.getX(), from.getY() + 1, from.getZ(), 10, 0.3, 0.6, 0.3, 0.05);
            try {
                fWorld.playSound(from, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.2f, 0.8f);
                fWorld.playSound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.1f);
            } catch (Throwable ignored) {}
        }

        // Try configured entry positions in random order; reject obstructed landings.
        List<Location> destinations = new ArrayList<>();
        for (Map<?, ?> spawn : config.getMapList("arena.player-spawns")) {
            if (spawn.get("x") instanceof Number x && spawn.get("y") instanceof Number y && spawn.get("z") instanceof Number z)
                destinations.add(new Location(world, x.doubleValue(), y.doubleValue(), z.doubleValue()));
        }
        if (destinations.isEmpty()) destinations.add(arenaCenter.clone());
        Collections.shuffle(destinations, random);
        Location destination = destinations.getFirst();

        // Ensure chunk at destination is loaded
        int destChunkX = destination.getBlockX() >> 4;
        int destChunkZ = destination.getBlockZ() >> 4;
        if (!world.isChunkLoaded(destChunkX, destChunkZ)) {
            world.getChunkAt(destChunkX, destChunkZ);
        }

        // Teleport
        boolean arrived = false;
        internalTransfers.add(player.getUniqueId());
        try {
            for (Location candidate : destinations) {
                candidate.getWorld().getChunkAt(candidate);
                if (BossMovement.teleport(player, candidate, this::inArena)) { destination = player.getLocation(); arrived = true; break; }
            }
        } finally { internalTransfers.remove(player.getUniqueId()); }
        if (!arrived) return false;
        enteredPlayers.add(player.getUniqueId());
        playerEntranceLocations.put(player.getUniqueId(), entranceLoc.clone());
        saveReturnLocations();

        // Grant 15 seconds of spawn protection
        int protectionSec = config.getInt("arena.spawn-protection-seconds", 15);
        spawnProtection.put(player.getUniqueId(), System.currentTimeMillis() + (protectionSec * 1000L));

        // Visual and audio arrival fanfare
        try {
            String entrySound = config.getString("arena.portals.entry-sound", "ENTITY_ENDERMAN_TELEPORT");
            playRiftSound(player, entrySound, 1.2f, 0.8f);
            player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 0.6f, 1.2f);
        } catch (Throwable ignored) {}

        world.spawnParticle(Particle.PORTAL, destination.getX(), destination.getY() + 1, destination.getZ(), 30, 0.5, 1.0, 0.5, 0.3);
        world.spawnParticle(Particle.TOTEM_OF_UNDYING, destination.getX(), destination.getY() + 1, destination.getZ(), 20, 0.5, 0.5, 0.5, 0.1);

        // Title and alert informing player of protection
        TextUtil.sendTitle(player, "&a&l🛡 SPAWN PROTECTION", "&fInvulnerable for &e" + protectionSec + " seconds&f!", 10, 50, 15);
        TextUtil.sendMessage(player, "&a&l🛡 &aSpawn Protection active for &e" + protectionSec + "s&a! You are immune to all damage.");
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, protectionSec * 20, 0, false, false, false));
        return true;
    }

    private void handleExitPortal(Player player) {
        UUID uuid = player.getUniqueId();
        Location entranceLoc = returnLocation(player);
        if (entranceLoc == null) {
            TextUtil.sendMessage(player, "&cNo safe return destination is available. Please contact an admin; the exit remains open.");
            return;
        }

        World arenaWorld = player.getLocation().getWorld();
        if (arenaWorld != null) {
            arenaWorld.spawnParticle(Particle.HAPPY_VILLAGER, player.getLocation().getX(), player.getLocation().getY() + 1,
                    player.getLocation().getZ(), 20, 0.5, 0.5, 0.5, 0);
            arenaWorld.spawnParticle(Particle.END_ROD, player.getLocation().getX(), player.getLocation().getY() + 1,
                    player.getLocation().getZ(), 10, 0.3, 0.5, 0.3, 0.02);
        }

        boolean returned;
        internalTransfers.add(uuid);
        try { returned = BossMovement.teleport(player, entranceLoc, at -> !inArena(at)); }
        finally { internalTransfers.remove(uuid); }
        if (!returned) {
            TextUtil.sendMessage(player, "&cYour return teleport was blocked. The exit remains available; please try again.");
            return;
        }

        try {
            String exitSound = config.getString("arena.portals.exit-sound", "ENTITY_ENDERMAN_TELEPORT");
            playRiftSound(player, exitSound, 1.2f, 1.0f);
        } catch (Throwable ignored) {}

        String exitMsg = config.getString("arena.portals.exit-success",
                "&a&l✦ &fYou have been returned to your entrance location.");
        TextUtil.sendMessage(player, exitMsg);

        playerEntranceLocations.remove(uuid);
        outsideLocations.remove(uuid);
        saveReturnLocations();
        paidPlayers.remove(uuid);
        spawnProtection.remove(uuid);
        promptCooldowns.remove(uuid);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Exit Portal (Post-Defeat)
    // ═══════════════════════════════════════════════════════════════════════════

    public void spawnExitPortal(Location location) {
        if (location == null || location.getWorld() == null) return;
        if (exitSpawnTask != null) exitSpawnTask.cancel();
        arenaCenter = location.clone();
        evacuationAt = System.currentTimeMillis() + 300_000L;
        searching = false;
        forming = false;
        portalsActive = false;
        activePortals.clear();
        if (formingTimerTask != null) { formingTimerTask.cancel(); formingTimerTask = null; }
        long exitGeneration = ++generation;
        arenaOccupancy = net.rocky.coresuite.common.integration.BossProtectionGate.arenaOccupancy(arenaCenter,
                Math.max(64, config.getDouble("domain.radius", 35) + config.getDouble("domain.spectator-distance", 16)));
        int delaySeconds = Math.max(0, Math.min(60, config.getInt("arena.exit-portal.spawn-delay-seconds", 5)));

        exitSpawnTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            exitSpawnTask = null;
            if (exitGeneration != generation) return;
            exitPortal = new PortalInstance(location.clone());
            exitPortal.active = true;

            for (PortalInstance portal : activePortals) {
                portal.active = false;
            }

            if (location.getWorld() != null) {
                location.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,
                        location.getX(), location.getY() + 2, location.getZ(),
                        50, 1.0, 1.5, 1.0, 0);
                location.getWorld().spawnParticle(Particle.END_ROD,
                        location.getX(), location.getY() + 1, location.getZ(),
                        30, 0.5, 1.0, 0.5, 0.02);
            }

            Bukkit.broadcast(TextUtil.colorize(""));
            Bukkit.broadcast(TextUtil.colorize("&a&l  ✦ A portal of light has opened at the arena center!"));
            Bukkit.broadcast(TextUtil.colorize("&7  Step into it to return home. Everyone still in the arena will be returned automatically five minutes after the fight."));
            Bukkit.broadcast(TextUtil.colorize(""));

            if (portalParticleTask == null || portalParticleTask.isCancelled()) startParticleTask();
            if (proximityCheckTask == null || proximityCheckTask.isCancelled()) startProximityTask();

            scheduleExitCleanup();
        }, delaySeconds * 20L);
    }

    private void scheduleExitCleanup() {
        if (exitCleanupTask != null) exitCleanupTask.cancel();

        exitCleanupTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (System.currentTimeMillis() >= evacuationAt) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (arenaOccupancy != null && arenaOccupancy.test(player.getLocation())) handleExitPortal(player);
                }
            }
            if (Bukkit.getOnlinePlayers().stream().noneMatch(player -> arenaOccupancy != null && arenaOccupancy.test(player.getLocation()))) {
                cleanup();
            }
        }, 20L, 20L);
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Cleanup
    // ═══════════════════════════════════════════════════════════════════════════

    public void cleanup() {
        formingCompletion = null;
        searchExpiresAt = formingEndsAt = entryClosesAt = 0;
        entryClosureAnnounced = false;
        enteredPlayers.clear();
        generation++;
        searching = false;
        arenaOccupancy = null;
        if (exitSpawnTask != null) { exitSpawnTask.cancel(); exitSpawnTask = null; }
        if (portalParticleTask != null) {
            portalParticleTask.cancel();
            portalParticleTask = null;
        }
        if (proximityCheckTask != null) {
            proximityCheckTask.cancel();
            proximityCheckTask = null;
        }
        if (formingTimerTask != null) {
            formingTimerTask.cancel();
            formingTimerTask = null;
        }
        if (exitCleanupTask != null) {
            exitCleanupTask.cancel();
            exitCleanupTask = null;
        }

        activePortals.clear();
        exitPortal = null;
        forming = false;
        portalsActive = false;
        arenaCenter = null;
        // Keep offline players' saved returns for evacuation when they reconnect.
        paidPlayers.clear();
        spawnProtection.clear();
        promptCooldowns.clear();
        particleAngle = 0;
    }

    // ═══════════════════════════════════════════════════════════════════════════
    //  Public Accessors & Info
    // ═══════════════════════════════════════════════════════════════════════════

    public boolean isActive() {
        return searching || forming || portalsActive || exitSpawnTask != null || exitPortal != null;
    }

    public boolean isForming() {
        return forming;
    }

    public boolean arePortalsActive() {
        return portalsActive;
    }

    public List<PortalInstance> getActivePortals() {
        return Collections.unmodifiableList(activePortals);
    }

    public void sendPortalInfo(Player player) {
        if (portalsActive && !canEnterWindow(player.getUniqueId(), System.currentTimeMillis())) {
            TextUtil.sendMessage(player, "&cThe 15-minute entry window has closed. Rifts are reserved for existing fighters.");
            return;
        }
        if (activePortals.isEmpty() && exitPortal == null) {
            TextUtil.sendMessage(player, "&8[&4&lBoss&8] &7No active arena portals at this time.");
            return;
        }

        TextUtil.sendMessage(player, "&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        TextUtil.sendMessage(player, "  &5&l⚡ ARENA PORTALS " + (forming ? "&e(FORMING)" : portalsActive ? "&a(ACTIVE)" : "&7(CLOSING)"));

        for (int i = 0; i < activePortals.size(); i++) {
            PortalInstance portal = activePortals.get(i);
            String distance = player.getWorld().equals(portal.location.getWorld())
                    ? ((int) player.getLocation().distance(portal.location)) + "m away"
                    : portal.location.getWorld().getName();
            String status = portal.active ? "&a●" : "&e○";
            TextUtil.sendMessage(player, "  " + status + " &7Portal " + (i + 1) + ": &e"
                    + portal.location.getBlockX() + "&7, &e" + portal.location.getBlockZ()
                    + " &8(&7" + distance + "&8)");
        }

        if (exitPortal != null) {
            TextUtil.sendMessage(player, "  &a✦ &7Exit Portal at arena center");
        }

        TextUtil.sendMessage(player, "&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private double horizontalDistanceSquared(Location a, Location b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return dx * dx + dz * dz;
    }

    public static class PortalInstance {
        final Location location;
        final long spawnedAt;
        boolean active;

        PortalInstance(Location location) {
            this.location = location;
            this.spawnedAt = System.currentTimeMillis();
            this.active = false;
        }

        public Location getLocation() { return location; }
        public boolean isActive() { return active; }
    }
}
