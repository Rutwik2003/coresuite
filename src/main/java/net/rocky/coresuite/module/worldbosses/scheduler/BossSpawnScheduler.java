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
    public static boolean isSafeLocation(Location location) {
        return location != null && location.getWorld() != null
                && Double.isFinite(location.getX()) && Double.isFinite(location.getY()) && Double.isFinite(location.getZ())
                && safeAt(location.getWorld(), location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }
    static boolean safeAt(World world, int x, int y, int z) {
        if (y <= world.getMinHeight() || y + 5 >= world.getMaxHeight()) return false;
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            if (!world.isChunkLoaded((x + dx) >> 4, (z + dz) >> 4)) return false;
            if (!world.getWorldBorder().isInside(new Location(world, x + dx + .5, y, z + dz + .5))) return false;
            for (int dy = 0; dy < 5; dy++) if (!world.getBlockAt(x + dx, y + dy, z + dz).getType().isAir()) return false;
        }
        Block ground = world.getBlockAt(x, y - 1, z);
        String material = ground.getType().name();
        return ground.getType().isSolid() && !ground.isLiquid() && !material.contains("LEAVES") && !material.contains("CACTUS") && !material.contains("MAGMA") && !material.contains("CAMPFIRE") && !material.contains("POWDER_SNOW");
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
