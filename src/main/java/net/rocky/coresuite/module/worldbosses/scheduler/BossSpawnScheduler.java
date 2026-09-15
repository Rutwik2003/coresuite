package net.rocky.coresuite.module.worldbosses.scheduler;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.worldbosses.WorldBossesModule;
import net.rocky.coresuite.module.worldbosses.boss.BossType;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;

import org.bukkit.block.Biome;
import org.bukkit.util.BiomeSearchResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;

public class BossSpawnScheduler {

    private final CoreSuitePlugin plugin;
    private final WorldBossesModule module;
    private final FileConfiguration config;
    private final Random random = new Random();

    private BukkitTask timerTask;
    private int secondsRemaining;
    private int intervalSeconds;
    private boolean enabled;

    public BossSpawnScheduler(CoreSuitePlugin plugin, WorldBossesModule module, FileConfiguration config) {
        this.plugin = plugin;
        this.module = module;
        this.config = config;
        this.enabled = config.getBoolean("scheduler.enabled", true);
        this.intervalSeconds = config.getInt("scheduler.interval-seconds", 14400);
        this.secondsRemaining = this.intervalSeconds;
    }

    public void start() {
        if (!enabled) return;
        if (timerTask != null) {
            timerTask.cancel();
        }

        this.timerTask = Bukkit.getScheduler().runTaskTimer(plugin, this::onTick, 20L, 20L);
    }

    public void stop() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }
    }

    private void onTick() {
        if (!enabled) return;

        // If a boss is currently alive, do not countdown to spawn another
        if (module.isBossActive()) {
            return;
        }

        secondsRemaining--;

        // Check broadcast warnings
        List<Integer> warnings = config.getIntegerList("scheduler.broadcast-warnings");
        if (warnings.contains(secondsRemaining)) {
            broadcastWarning(secondsRemaining);
        }

        // Spawn time reached
        if (secondsRemaining <= 0) {
            int minPlayers = config.getInt("scheduler.min-players", 3);
            int onlineCount = Bukkit.getOnlinePlayers().size();

            if (onlineCount < minPlayers) {
                // Not enough players: postpone by 5 minutes (300 seconds)
                secondsRemaining = 300;
                Bukkit.broadcast(TextUtil.colorize("&8[&4&lBoss&8] &7The world boss slumber continues: Not enough online hunters (&e" + onlineCount + "&7/&e" + minPlayers + "&7). Retrying in 5m."));
                return;
            }

            // Spawn boss asynchronously so the main thread never freezes or drops TPS
            triggerSpawnAsync();
            secondsRemaining = intervalSeconds;
        }
    }

    public CompletableFuture<Boolean> triggerSpawnAsync() {
        BossType[] types = BossType.values();
        BossType chosen = types[random.nextInt(types.length)];
        return triggerSpawnAsync(chosen, null);
    }

    public CompletableFuture<Boolean> triggerSpawnAsync(BossType type, Location targetLoc) {
        if (targetLoc != null) {
            return CompletableFuture.completedFuture(module.spawnBoss(type, targetLoc));
        }

        CompletableFuture<Boolean> future = new CompletableFuture<>();
        findSafeSpawnLocationAsync(type).thenAccept(spawnLoc -> {
            if (spawnLoc == null) {
                plugin.getLogger().warning("[WorldBosses] Could not find a safe location to spawn scheduled boss " + type.name());
                future.complete(false);
                return;
            }

            boolean success = module.spawnBoss(type, spawnLoc);
            future.complete(success);
        }).exceptionally(t -> {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "[WorldBosses] Error during async boss spawn", t);
            future.complete(false);
            return null;
        });

        return future;
    }

    public boolean triggerSpawn() {
        triggerSpawnAsync();
        return true;
    }

    public boolean triggerSpawn(BossType type, Location targetLoc) {
        triggerSpawnAsync(type, targetLoc);
        return true;
    }

    public Location findSafeSpawnLocation(BossType type) {
        try {
            return findSafeSpawnLocationAsync(type).join();
        } catch (Throwable t) {
            plugin.getLogger().warning("[WorldBosses] Error in findSafeSpawnLocation: " + t.getMessage());
            return null;
        }
    }

    public CompletableFuture<Location> findSafeSpawnLocationAsync(BossType type) {
        List<String> allowedWorlds = config.getStringList("scheduler.allowed-worlds");
        if (allowedWorlds.isEmpty()) {
            allowedWorlds = List.of("world");
        }

        String worldName = allowedWorlds.get(random.nextInt(allowedWorlds.size()));
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            world = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        }
        if (world == null) {
            return CompletableFuture.completedFuture(null);
        }

        int radius = config.getInt("scheduler.spawn-radius", 5000);
        List<String> configuredBiomes = config.getStringList(type.getConfigKey() + ".biomes");

        if (type == BossType.XOLTHOS) {
            return findSafeCaveSpawnLocationAsync(world, radius, configuredBiomes);
        } else {
            return findSafeSurfaceBiomeSpawnLocationAsync(world, radius, configuredBiomes);
        }
    }

    public CompletableFuture<Location> findSafeSurfaceBiomeSpawnLocationAsync(World world, int radius, List<String> targetBiomes) {
        final Location worldSpawn = world.getSpawnLocation();
        final int minDistance = config.getInt("scheduler.min-spawn-distance", 800);
        final int effectiveRadius = (radius <= minDistance) ? (minDistance + 1000) : radius;

        CompletableFuture<Location> future = new CompletableFuture<>();

        // 1. Precise Biome Search using Bukkit locateNearestBiome (instant mathematical noise query)
        if (targetBiomes != null && !targetBiomes.isEmpty()) {
            List<Biome> matchingBiomes = new ArrayList<>();
            for (String tb : targetBiomes) {
                for (Biome b : Biome.values()) {
                    if (b.name().equalsIgnoreCase(tb) || b.name().contains(tb.toUpperCase())) {
                        if (!matchingBiomes.contains(b)) {
                            matchingBiomes.add(b);
                        }
                    }
                }
            }

            if (!matchingBiomes.isEmpty()) {
                try {
                    double initAngle = random.nextDouble() * 2 * Math.PI;
                    Location searchOrigin = new Location(
                            world,
                            worldSpawn.getBlockX() + Math.cos(initAngle) * minDistance,
                            64,
                            worldSpawn.getBlockZ() + Math.sin(initAngle) * minDistance
                    );

                    BiomeSearchResult result = world.locateNearestBiome(
                            searchOrigin,
                            effectiveRadius,
                            matchingBiomes.toArray(new Biome[0])
                    );

                    if (result != null && result.getLocation() != null) {
                        Location found = result.getLocation();
                        double d2 = Math.pow(found.getBlockX() - worldSpawn.getBlockX(), 2)
                                + Math.pow(found.getBlockZ() - worldSpawn.getBlockZ(), 2);
                        if (d2 >= (minDistance * minDistance)) {
                            // Asynchronously load the candidate chunk on Paper worker threads
                            world.getChunkAtAsync(found.getBlockX() >> 4, found.getBlockZ() >> 4).thenAccept(chunk -> {
                                Location safeSpot = findSafeGroundNear(world, found.getBlockX(), found.getBlockZ(), 20);
                                if (safeSpot != null) {
                                    future.complete(safeSpot);
                                } else {
                                    searchRingAsync(world, effectiveRadius, minDistance, targetBiomes, 0, null, future);
                                }
                            }).exceptionally(t -> {
                                searchRingAsync(world, effectiveRadius, minDistance, targetBiomes, 0, null, future);
                                return null;
                            });
                            return future;
                        }
                    }
                } catch (Throwable t) {
                    plugin.getLogger().warning("[WorldBosses] locateNearestBiome skipped: " + t.getMessage());
                }
            }
        }

        // 2. Start Async Ring Search
        searchRingAsync(world, effectiveRadius, minDistance, targetBiomes, 0, null, future);
        return future;
    }

    private void searchRingAsync(World world, int radius, int minDistance, List<String> targetBiomes,
                                 int attempt, Location fallback, CompletableFuture<Location> future) {
        if (attempt >= 30) {
            if (fallback != null) {
                future.complete(fallback);
                return;
            }

            // Guaranteed safe wilderness spot at minDistance
            double angle = random.nextDouble() * 2 * Math.PI;
            double dist = minDistance + random.nextDouble() * 500;
            Location worldSpawn = world.getSpawnLocation();
            int fx = (int) (worldSpawn.getBlockX() + dist * Math.cos(angle));
            int fz = (int) (worldSpawn.getBlockZ() + dist * Math.sin(angle));

            world.getChunkAtAsync(fx >> 4, fz >> 4).thenAccept(chunk -> {
                int fy = world.getHighestBlockYAt(fx, fz, HeightMap.MOTION_BLOCKING);
                future.complete(new Location(world, fx + 0.5, Math.max(64, fy), fz + 0.5));
            }).exceptionally(t -> {
                int defY = world.getHighestBlockYAt(fx, fz, HeightMap.MOTION_BLOCKING);
                future.complete(new Location(world, fx + 0.5, Math.max(64, defY), fz + 0.5));
                return null;
            });
            return;
        }

        Location worldSpawn = world.getSpawnLocation();
        double angle = random.nextDouble() * 2 * Math.PI;
        double dist = minDistance + random.nextDouble() * (radius - minDistance);
        int x = (int) (worldSpawn.getBlockX() + dist * Math.cos(angle));
        int z = (int) (worldSpawn.getBlockZ() + dist * Math.sin(angle));

        // Load chunk asynchronously on Paper background worker thread
        world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> {
            int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
            Location newFallback = fallback;

            if (y > world.getMinHeight() + 5) {
                Block ground = world.getBlockAt(x, y - 1, z);
                Block feet = world.getBlockAt(x, y, z);
                Block head = world.getBlockAt(x, y + 1, z);

                if (ground.getType().isSolid() && !ground.isLiquid() && feet.getType().isAir() && head.getType().isAir()) {
                    String gName = ground.getType().name();
                    if (!gName.contains("LAVA") && !gName.contains("FIRE") && !gName.contains("CACTUS")) {
                        Location candidate = new Location(world, x + 0.5, y, z + 0.5);
                        if (newFallback == null) {
                            newFallback = candidate;
                        }

                        if (targetBiomes != null && !targetBiomes.isEmpty()) {
                            String currentBiome = world.getBiome(x, y, z).name();
                            boolean biomeMatch = false;
                            for (String tb : targetBiomes) {
                                if (currentBiome.equalsIgnoreCase(tb) || currentBiome.contains(tb.toUpperCase())) {
                                    biomeMatch = true;
                                    break;
                                }
                            }
                            if (biomeMatch) {
                                future.complete(candidate);
                                return;
                            }
                        } else {
                            future.complete(candidate);
                            return;
                        }
                    }
                }
            }

            searchRingAsync(world, radius, minDistance, targetBiomes, attempt + 1, newFallback, future);
        }).exceptionally(t -> {
            searchRingAsync(world, radius, minDistance, targetBiomes, attempt + 1, fallback, future);
            return null;
        });
    }

    private Location findSafeGroundNear(World world, int centerX, int centerZ, int searchRadius) {
        for (int r = 0; r <= searchRadius; r += 2) {
            for (int dx = -r; dx <= r; dx += (r == 0 ? 1 : r * 2)) {
                for (int dz = -r; dz <= r; dz += (r == 0 ? 1 : r * 2)) {
                    int x = centerX + dx;
                    int z = centerZ + dz;
                    if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;

                    int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
                    if (y <= world.getMinHeight() + 5) continue;

                    Block ground = world.getBlockAt(x, y - 1, z);
                    Block feet = world.getBlockAt(x, y, z);
                    Block head = world.getBlockAt(x, y + 1, z);

                    if (ground.getType().isSolid() && !ground.isLiquid() && feet.getType().isAir() && head.getType().isAir()) {
                        String gName = ground.getType().name();
                        if (!gName.contains("LAVA") && !gName.contains("FIRE") && !gName.contains("CACTUS")) {
                            return new Location(world, x + 0.5, y, z + 0.5);
                        }
                    }
                }
            }
        }
        return null;
    }

    public CompletableFuture<Location> findSafeCaveSpawnLocationAsync(World world, int radius, List<String> targetBiomes) {
        final Location worldSpawn = world.getSpawnLocation();
        final int minDistance = config.getInt("scheduler.min-spawn-distance", 800);
        final int effectiveRadius = (radius <= minDistance) ? (minDistance + 1000) : radius;

        CompletableFuture<Location> future = new CompletableFuture<>();
        searchCaveAsync(world, effectiveRadius, minDistance, targetBiomes, 0, null, future);
        return future;
    }

    private void searchCaveAsync(World world, int radius, int minDistance, List<String> targetBiomes,
                                 int attempt, Location fallback, CompletableFuture<Location> future) {
        if (attempt >= 30) {
            if (fallback != null) {
                future.complete(fallback);
                return;
            }
            findSafeSurfaceBiomeSpawnLocationAsync(world, radius, targetBiomes).thenAccept(future::complete);
            return;
        }

        Location worldSpawn = world.getSpawnLocation();
        double angle = random.nextDouble() * 2 * Math.PI;
        double dist = minDistance + random.nextDouble() * (radius - minDistance);
        int x = (int) (worldSpawn.getBlockX() + dist * Math.cos(angle));
        int z = (int) (worldSpawn.getBlockZ() + dist * Math.sin(angle));

        world.getChunkAtAsync(x >> 4, z >> 4).thenAccept(chunk -> {
            int surfaceY = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING);
            int startY = Math.min(30, surfaceY - 15);
            int minY = Math.max(world.getMinHeight() + 5, -45);
            Location newFallback = fallback;

            for (int y = startY; y >= minY; y--) {
                Block ground = world.getBlockAt(x, y - 1, z);
                Block feet = world.getBlockAt(x, y, z);

                if (ground.getType().isSolid() && !ground.isLiquid() && feet.getType().isAir()) {
                    String gName = ground.getType().name();
                    if (gName.contains("LAVA") || gName.contains("FIRE") || gName.contains("WATER")) {
                        continue;
                    }

                    if (feet.getLightFromSky() > 0) {
                        continue;
                    }

                    boolean openVertical = true;
                    for (int dy = 1; dy <= 4; dy++) {
                        if (!world.getBlockAt(x, y + dy, z).getType().isAir()) {
                            openVertical = false;
                            break;
                        }
                    }
                    if (!openVertical) continue;

                    boolean openHorizontal = true;
                    int[][] offsets = {{2, 0}, {-2, 0}, {0, 2}, {0, -2}, {3, 0}, {-3, 0}, {0, 3}, {0, -3}};
                    for (int[] off : offsets) {
                        Block checkFeet = world.getBlockAt(x + off[0], y, z + off[1]);
                        Block checkHead = world.getBlockAt(x + off[0], y + 1, z + off[1]);
                        if (!checkFeet.getType().isAir() || !checkHead.getType().isAir()) {
                            openHorizontal = false;
                            break;
                        }
                    }
                    if (!openHorizontal) continue;

                    Location candidate = new Location(world, x + 0.5, y, z + 0.5);
                    if (newFallback == null) {
                        newFallback = candidate;
                    }

                    if (targetBiomes != null && !targetBiomes.isEmpty()) {
                        String currentBiome = world.getBiome(x, y, z).name();
                        boolean biomeMatch = false;
                        for (String tb : targetBiomes) {
                            if (currentBiome.equalsIgnoreCase(tb) || currentBiome.contains(tb.toUpperCase())) {
                                biomeMatch = true;
                                break;
                            }
                        }
                        if (biomeMatch) {
                            future.complete(candidate);
                            return;
                        }
                    } else {
                        future.complete(candidate);
                        return;
                    }
                }
            }

            searchCaveAsync(world, radius, minDistance, targetBiomes, attempt + 1, newFallback, future);
        }).exceptionally(t -> {
            searchCaveAsync(world, radius, minDistance, targetBiomes, attempt + 1, fallback, future);
            return null;
        });
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

    public int getSecondsRemaining() {
        return secondsRemaining;
    }

    public void setSecondsRemaining(int secondsRemaining) {
        this.secondsRemaining = secondsRemaining;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
