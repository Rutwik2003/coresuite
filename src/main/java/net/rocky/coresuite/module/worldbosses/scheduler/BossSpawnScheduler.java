package net.rocky.coresuite.module.worldbosses.scheduler;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.worldbosses.WorldBossesModule;
import net.rocky.coresuite.module.worldbosses.boss.BossType;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** Chunk generation is asynchronous; all Bukkit inspection and spawning runs on the server thread. */
public class BossSpawnScheduler {
    private final CoreSuitePlugin plugin;
    private final WorldBossesModule module;
    private final FileConfiguration config;
    private final Random random = new Random();
    private final Set<CompletableFuture<Location>> searches = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private BukkitTask timerTask;
    private int secondsRemaining;
    private final int intervalSeconds;
    private boolean enabled;
    private volatile boolean stopped;
    private boolean spawnPending;

    public BossSpawnScheduler(CoreSuitePlugin plugin, WorldBossesModule module, FileConfiguration config) {
        this.plugin = plugin; this.module = module; this.config = config;
        enabled = config.getBoolean("scheduler.enabled", true);
        intervalSeconds = Math.max(1, config.getInt("scheduler.interval-seconds", 14400));
        secondsRemaining = intervalSeconds;
    }
    public void start() {
        stopped = false;
        if (timerTask != null) timerTask.cancel();
        timerTask = enabled ? Bukkit.getScheduler().runTaskTimer(plugin, this::onTick, 20, 20) : null;
    }
    public void stop() {
        stopped = true;
        if (timerTask != null) timerTask.cancel();
        timerTask = null;
        for (CompletableFuture<Location> search : List.copyOf(searches)) search.complete(null);
        searches.clear();
    }
    private void onTick() {
        if (stopped || !enabled || spawnPending || module.isBossActive()) return;
        if (--secondsRemaining > 0) {
            if (config.getIntegerList("scheduler.broadcast-warnings").contains(secondsRemaining)) broadcastWarning(secondsRemaining);
            return;
        }
        if (Bukkit.getOnlinePlayers().size() < Math.max(0, config.getInt("scheduler.min-players", 1))) {
            secondsRemaining = retrySeconds();
            return;
        }
        triggerSpawnAsync().thenAccept(success -> secondsRemaining = success ? intervalSeconds : retrySeconds());
    }
    private int retrySeconds() { return Math.max(10, config.getInt("scheduler.retry-seconds", 300)); }
    public CompletableFuture<Boolean> triggerSpawnAsync() {
        BossType[] types = BossType.values();
        return triggerSpawnAsync(types[random.nextInt(types.length)], null);
    }
    public CompletableFuture<Boolean> triggerSpawnAsync(BossType type, Location target) {
        CompletableFuture<Boolean> result = new CompletableFuture<>();
        Location requested = target == null ? null : target.clone();
        onMain(() -> {
            if (stopped || spawnPending || !module.isEnabled() || module.isBossActive() || type == null) { result.complete(false); return; }
            spawnPending = true;
            CompletableFuture<Location> search = requested == null ? findSafeSpawnLocationAsync(type) : CompletableFuture.completedFuture(requested);
            search.whenComplete((location, error) -> {
                try {
                    boolean success = error == null && location != null && !stopped && module.isEnabled() && !module.isBossActive() && module.spawnBoss(type, location);
                    if (!success && !stopped) plugin.getLogger().warning("[WorldBosses] Spawn did not complete for " + type + "; no safe location or encounter unavailable.");
                    result.complete(success);
                } catch (Exception | LinkageError failure) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "[WorldBosses] Spawn failed", failure);
                    result.complete(false);
                } finally { spawnPending = false; }
            });
        }, () -> result.complete(false));
        return result;
    }
    /** Legacy return value means the request was accepted; use the future for its outcome. */
    public boolean triggerSpawn() { if (stopped || spawnPending) return false; triggerSpawnAsync(); return true; }
    public boolean triggerSpawn(BossType type, Location target) { if (stopped || spawnPending) return false; triggerSpawnAsync(type, target); return true; }
    public Location findSafeSpawnLocation(BossType type) {
        if (Bukkit.isPrimaryThread()) throw new IllegalStateException("Use findSafeSpawnLocationAsync on the server thread");
        return findSafeSpawnLocationAsync(type).join();
    }
    public CompletableFuture<Location> findSafeSpawnLocationAsync(BossType type) {
        CompletableFuture<Location> result = new CompletableFuture<>();
        onMain(() -> {
            List<World> worlds = config.getStringList("scheduler.allowed-worlds").stream().map(Bukkit::getWorld)
                    .filter(Objects::nonNull).filter(w -> w.getDifficulty() != Difficulty.PEACEFUL).toList();
            if (worlds.isEmpty() || type == null) { result.complete(null); return; }
            World world = worlds.get(random.nextInt(worlds.size()));
            beginSearch(world, config.getInt("scheduler.spawn-radius", 5000), config.getStringList(type.getConfigKey() + ".biomes"), type == BossType.XOLTHOS, result);
        }, () -> result.complete(null));
        return result;
    }
    public CompletableFuture<Location> findSafeSurfaceBiomeSpawnLocationAsync(World world, int radius, List<String> biomes) { return search(world, radius, biomes, false); }
    public CompletableFuture<Location> findSafeCaveSpawnLocationAsync(World world, int radius, List<String> biomes) { return search(world, radius, biomes, true); }
    private CompletableFuture<Location> search(World world, int radius, List<String> biomes, boolean cave) {
        CompletableFuture<Location> result = new CompletableFuture<>();
        onMain(() -> beginSearch(world, radius, biomes, cave, result), () -> result.complete(null));
        return result;
    }
    private void beginSearch(World world, int radius, List<String> biomes, boolean cave, CompletableFuture<Location> result) {
        int minimum = Math.max(0, config.getInt("scheduler.min-spawn-distance", 800));
        if (stopped || world == null || radius <= minimum || world.getDifficulty() == Difficulty.PEACEFUL) { result.complete(null); return; }
        searches.add(result);
        BukkitTask timeout = Bukkit.getScheduler().runTaskLater(plugin, () -> result.complete(null), Math.max(20, Math.min(120, config.getInt("scheduler.search-timeout-seconds", 45))) * 20L);
        result.whenComplete((location, error) -> { searches.remove(result); timeout.cancel(); });
        sample(world, radius, minimum, biomes == null ? List.of() : List.copyOf(biomes), cave, 0, null, result);
    }
    private void sample(World world, int radius, int minimum, List<String> biomes, boolean cave, int attempt, Location fallback, CompletableFuture<Location> result) {
        if (result.isDone() || stopped) { result.complete(null); return; }
        int attempts = Math.max(1, Math.min(128, config.getInt("scheduler.search-attempts", 32)));
        if (attempt >= attempts) {
            result.complete(config.getBoolean("scheduler.allow-biome-fallback", true) ? fallback : null);
            return;
        }
        Location origin = world.getSpawnLocation();
        double angle = random.nextDouble() * Math.PI * 2;
        double distance = minimum + random.nextDouble() * (radius - minimum);
        int x = (int)Math.floor(origin.getX() + Math.cos(angle) * distance);
        int z = (int)Math.floor(origin.getZ() + Math.sin(angle) * distance);
        Location borderPoint = new Location(world, x + .5, origin.getY(), z + .5);
        if (!world.getWorldBorder().isInside(borderPoint)) {
            next(() -> sample(world, radius, minimum, biomes, cave, attempt + 1, fallback, result), result);
            return;
        }
        List<CompletableFuture<Chunk>> chunks = new ArrayList<>();
        for (int cx = (x - 2) >> 4; cx <= (x + 2) >> 4; cx++) {
            for (int cz = (z - 2) >> 4; cz <= (z + 2) >> 4; cz++) chunks.add(world.getChunkAtAsync(cx, cz));
        }
        CompletableFuture.allOf(chunks.toArray(CompletableFuture[]::new)).whenComplete((ignored, error) -> next(() -> {
            if (result.isDone() || stopped) return;
            Location candidate = null;
            if (error == null) {
                if (cave) {
                    int top = Math.min(30, world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) - 10);
                    for (int y = top; y >= Math.max(world.getMinHeight() + 1, -45); y--) {
                        if (safeAt(world, x, y, z) && world.getBlockAt(x, y, z).getLightFromSky() == 0) { candidate = new Location(world, x + .5, y, z + .5); break; }
                    }
                } else {
                    int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1;
                    if (safeAt(world, x, y, z)) candidate = new Location(world, x + .5, y, z + .5);
                }
            }
            if (candidate != null && !net.rocky.coresuite.common.integration.BossProtectionGate.allows(candidate)) candidate = null;
            if (candidate != null) {
                String biome = ((Keyed)world.getBiome(candidate.getBlockX(), candidate.getBlockY(), candidate.getBlockZ())).getKey().getKey().toUpperCase(Locale.ROOT);
                if (biomes.isEmpty() || biomes.stream().anyMatch(b -> biome.equals(b.toUpperCase(Locale.ROOT)) || biome.contains(b.toUpperCase(Locale.ROOT)))) { result.complete(candidate); return; }
                if (fallback == null) { sample(world, radius, minimum, biomes, cave, attempt + 1, candidate, result); return; }
            }
            sample(world, radius, minimum, biomes, cave, attempt + 1, fallback, result);
        }, result));
    }
    public static boolean isSafeLocation(Location location) { return locationProblem(location) == null; }
    public static String locationProblem(Location location) {
        if (location == null || location.getWorld() == null) return "Missing spawn world.";
        if (!Double.isFinite(location.getX()) || !Double.isFinite(location.getY()) || !Double.isFinite(location.getZ())) return "Coordinates must be finite numbers.";
        return terrainProblem(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
    static boolean safeAt(World world, int x, int y, int z) { return terrainProblem(world, x, y, z) == null; }
    private static String terrainProblem(World world, int x, int y, int z) {
        if (y <= world.getMinHeight() || y + 3 >= world.getMaxHeight()) return "Outside the world height limit.";
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (!world.isChunkLoaded((x + dx) >> 4, (z + dz) >> 4)) return "Nearby terrain is not loaded.";
            if (!world.getWorldBorder().isInside(new Location(world, x + dx + .5, y, z + dz + .5))) return "Too close to or outside the world border.";
            for (int dy = 0; dy < 4; dy++) {
                Block block = world.getBlockAt(x + dx, y + dy, z + dz);
                String material = block.getType().name();
                if (block.isLiquid() || material.contains("FIRE") || material.contains("BERRY_BUSH") || material.equals("WITHER_ROSE") || material.equals("POWDER_SNOW")
                        || (!block.getType().isAir() && !block.isPassable()))
                    return "Obstructed by " + material + " at " + (x + dx) + ", " + (y + dy) + ", " + (z + dz) + ". Need 3x3 space and 4 blocks of headroom.";
            }
        }
        Block ground = world.getBlockAt(x, y - 1, z);
        String material = ground.getType().name();
        if (!ground.getType().isSolid() || ground.isLiquid() || material.contains("LEAVES") || material.contains("CACTUS") || material.contains("MAGMA") || material.contains("CAMPFIRE") || material.contains("POWDER_SNOW"))
            return "No safe solid ground beneath spawn (" + material + ").";
        return null;
    }
    /** Bounded local search for player-issued summons; never loads new chunks. */
    public static Location findNearbyLocation(Location origin, java.util.function.Predicate<Location> allowed) {
        if (origin == null || origin.getWorld() == null || !Double.isFinite(origin.getX()) || !Double.isFinite(origin.getY()) || !Double.isFinite(origin.getZ())) return null;
        int protectionChecks = 0;
        for (int radius = 0; radius <= 6; radius++) for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
            if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
            for (int dy : new int[]{0, 1, -1, 2, -2, 3, -3}) {
                Location candidate = new Location(origin.getWorld(), origin.getBlockX() + dx + .5, origin.getBlockY() + dy, origin.getBlockZ() + dz + .5);
                if (!isSafeLocation(candidate)) continue;
                if (++protectionChecks > 32) return null;
                if (allowed.test(candidate)) return candidate;
            }
        }
        return null;
    }
    /**
     * Checks the loaded usable arena footprint without loading terrain. Protection is sampled at
     * the centre, cardinal points and diagonals, which catches claims/regions crossing the arena.
     */
    public static String arenaProblem(Location center, double radius, java.util.function.Function<Location, String> protectionProblem) {
        if (center == null || center.getWorld() == null) return "Missing arena world.";
        if (!Double.isFinite(radius) || radius < 1 || radius > 256) return "Arena radius must be between 1 and 256.";
        World world = center.getWorld();
        for (int angle = 0; angle < 360; angle += 45) {
            double radians = Math.toRadians(angle);
            Location sample = center.clone().add(Math.cos(radians) * radius, 0, Math.sin(radians) * radius);
            if (!world.isChunkLoaded(sample.getBlockX() >> 4, sample.getBlockZ() >> 4)) return "Arena terrain is not loaded near " + sample.getBlockX() + ", " + sample.getBlockZ() + ".";
            if (!world.getWorldBorder().isInside(sample)) return "Arena crosses the world border near " + sample.getBlockX() + ", " + sample.getBlockZ() + ".";
            if (protectionProblem != null) {
                String problem = protectionProblem.apply(sample);
                if (problem != null) return "Arena is unavailable near " + sample.getBlockX() + ", " + sample.getBlockZ() + ": " + problem;
            }
        }
        return null;
    }
    private void next(Runnable action, CompletableFuture<Location> result) {
        if (stopped || result.isDone() || !plugin.isEnabled()) { result.complete(null); return; }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (stopped || result.isDone()) return;
            try { action.run(); } catch (Exception | LinkageError error) { result.completeExceptionally(error); }
        });
    }
    private void onMain(Runnable action, Runnable unavailable) {
        if (stopped || !plugin.isEnabled()) { unavailable.run(); return; }
        Runnable guarded = () -> {
            if (stopped || !plugin.isEnabled()) { unavailable.run(); return; }
            try { action.run(); }
            catch (Exception | LinkageError error) {
                spawnPending = false;
                plugin.getLogger().log(java.util.logging.Level.WARNING, "[WorldBosses] Spawn search failed", error);
                unavailable.run();
            }
        };
        if (Bukkit.isPrimaryThread()) guarded.run();
        else {
            try { Bukkit.getScheduler().runTask(plugin, guarded); }
            catch (org.bukkit.plugin.IllegalPluginAccessException error) { unavailable.run(); }
        }
    }
    private void broadcastWarning(int seconds) {
        String timeFormatted;
        if (seconds >= 3600) {
            timeFormatted = (seconds / 3600) + " hour" + (seconds / 3600 > 1 ? "s" : "");
        } else if (seconds >= 60) {
            timeFormatted = (seconds / 60) + " minute" + (seconds / 60 > 1 ? "s" : "");
        } else {
            timeFormatted = seconds + " seconds";
        }

        Bukkit.broadcast(TextUtil.colorize(""));
        Bukkit.broadcast(TextUtil.colorize("&4&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        Bukkit.broadcast(TextUtil.colorize("  &4&l⚠ WORLD BOSS INVASION WARNING"));
        Bukkit.broadcast(TextUtil.colorize("  &7An ancient catastrophe will awaken in &e" + timeFormatted + "&7!"));
        Bukkit.broadcast(TextUtil.colorize("  &cGather your guild, prepare cleansing potions, and sharpen your weapons!"));
        Bukkit.broadcast(TextUtil.colorize("&4&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
        Bukkit.broadcast(TextUtil.colorize(""));
    }

    public int getSecondsRemaining() { return secondsRemaining; }
    public void setSecondsRemaining(int seconds) { secondsRemaining = Math.max(0, seconds); }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; if (!stopped) start(); }
}
