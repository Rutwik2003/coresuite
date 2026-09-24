package net.rocky.coresuite.common.integration;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;
import java.lang.reflect.Array;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Non-player boss actions have no claim-owner or administrator bypass. */
public final class BossProtectionGate {
    private static final Set<String> warned = ConcurrentHashMap.newKeySet();
    private BossProtectionGate() {}
    public static boolean allows(Location location) { return denialReason(location) == null; }
    /** Snapshot local arena regions once, then cheaply test occupants without changing any flags. */
    public static java.util.function.Predicate<Location> arenaOccupancy(Location center, double fallbackRadius) {
        java.util.function.Predicate<Location> fallback = location -> location != null
                && center.getWorld().equals(location.getWorld())
                && Math.hypot(location.getX() - center.getX(), location.getZ() - center.getZ()) <= fallbackRadius;
        Plugin provider = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (provider == null || !provider.isEnabled()) return fallback;
        try {
            ClassLoader loader = provider.getClass().getClassLoader();
            Class<?> guard = Class.forName("com.sk89q.worldguard.WorldGuard", true, loader);
            Object platform = guard.getMethod("getPlatform").invoke(guard.getMethod("getInstance").invoke(null));
            Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter", true, loader);
            Object world = adapter.getMethod("adapt", org.bukkit.World.class).invoke(null, center.getWorld());
            Object manager = container.getClass().getMethod("get", Class.forName("com.sk89q.worldedit.world.World", true, loader)).invoke(container, world);
            if (manager == null) return fallback;
            Class<?> vector = Class.forName("com.sk89q.worldedit.math.BlockVector3", true, loader);
            var at = vector.getMethod("at", int.class, int.class, int.class);
            Object point = at.invoke(null, center.getBlockX(), center.getBlockY(), center.getBlockZ());
            Class<?> region = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion", true, loader);
            var contains = region.getMethod("contains", vector);
            var id = region.getMethod("getId");
            java.util.List<Object> regions = new java.util.ArrayList<>();
            for (Object candidate : (Iterable<?>) manager.getClass().getMethod("getApplicableRegions", vector).invoke(manager, point))
                if (!"__global__".equals(id.invoke(candidate))) regions.add(candidate);
            if (regions.isEmpty()) return fallback;
            return location -> {
                if (location == null || !center.getWorld().equals(location.getWorld())) return false;
                if (fallback.test(location)) return true;
                try {
                    Object position = at.invoke(null, location.getBlockX(), location.getBlockY(), location.getBlockZ());
                    for (Object candidate : regions) if (Boolean.TRUE.equals(contains.invoke(candidate, position))) return true;
                    return false;
                } catch (ReflectiveOperationException error) {
                    return true; // Keep the exit available if a region query fails.
                }
            };
        } catch (ReflectiveOperationException | LinkageError error) {
            if (warned.add("exit occupancy")) java.util.logging.Logger.getLogger("CoreSuite").log(java.util.logging.Level.WARNING,
                    "Cannot read arena regions for exit cleanup; retaining the exit while players remain in its world", error);
            return location -> location != null && center.getWorld().equals(location.getWorld());
        }
    }
    /** Conservatively checks the entire arena's bounding volume, including interior claims. */
    public static String arenaDenialReason(Location center, double radius) {
        if (center == null || center.getWorld() == null || !Double.isFinite(radius) || radius < 1 || radius > 64)
            return "Arena radius must be finite and between 1 and 64.";
        for (String name : new String[]{"GriefPrevention", "WorldGuard"}) {
            Plugin provider = Bukkit.getPluginManager().getPlugin(name);
            if (provider == null) continue;
            if (!provider.isEnabled()) return name + " is installed but disabled.";
            try {
                ClassLoader loader = provider.getClass().getClassLoader();
                if (name.equals("GriefPrevention")) {
                    Object store = provider.getClass().getField("dataStore").get(provider);
                    Class<?> claimType = Class.forName("me.ryanhamshire.GriefPrevention.Claim", true, loader);
                    for (Object claim : (Iterable<?>) store.getClass().getMethod("getClaims").invoke(store)) {
                        Location low = (Location) claimType.getMethod("getLesserBoundaryCorner").invoke(claim);
                        Location high = (Location) claimType.getMethod("getGreaterBoundaryCorner").invoke(claim);
                        if (!center.getWorld().equals(low.getWorld())) continue;
                        if (low.getBlockX() <= center.getX() + radius && high.getBlockX() + 1 >= center.getX() - radius
                                && low.getBlockZ() <= center.getZ() + radius && high.getBlockZ() + 1 >= center.getZ() - radius)
                            return "GriefPrevention: a claim intersects the arena near " + low.getBlockX() + ", " + low.getBlockZ() + ".";
                    }
                } else {
                    Class<?> guard = Class.forName("com.sk89q.worldguard.WorldGuard", true, loader);
                    Object platform = guard.getMethod("getPlatform").invoke(guard.getMethod("getInstance").invoke(null));
                    Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
                    Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter", true, loader);
                    Object world = adapter.getMethod("adapt", org.bukkit.World.class).invoke(null, center.getWorld());
                    Object manager = container.getClass().getMethod("get", Class.forName("com.sk89q.worldedit.world.World", true, loader)).invoke(container, world);
                    if (manager == null) return "WorldGuard region data is unavailable for the arena.";
                    Class<?> vector = Class.forName("com.sk89q.worldedit.math.BlockVector3", true, loader);
                    java.lang.reflect.Method at = vector.getMethod("at", int.class, int.class, int.class);
                    Object low = at.invoke(null, (int) Math.floor(center.getX() - radius), Math.max(center.getWorld().getMinHeight(), (int) Math.floor(center.getY() - radius)), (int) Math.floor(center.getZ() - radius));
                    Object high = at.invoke(null, (int) Math.ceil(center.getX() + radius), Math.min(center.getWorld().getMaxHeight() - 1, (int) Math.ceil(center.getY() + radius)), (int) Math.ceil(center.getZ() + radius));
                    Class<?> region = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion", true, loader);
                    Object volume = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion", true, loader)
                            .getConstructor(String.class, vector, vector).newInstance("coresuite_arena_query", low, high);
                    Iterable<?> overlaps = (Iterable<?>) manager.getClass().getMethod("getApplicableRegions", region).invoke(manager, volume);
                    Object global = manager.getClass().getMethod("getRegion", String.class).invoke(manager, "__global__");
                    Class<?> stateFlag = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag", true, loader);
                    Class<?> flags = Class.forName("com.sk89q.worldguard.protection.flags.Flags", true, loader);
                    Object checks = Array.newInstance(stateFlag, 3);
                    int i = 0;
                    for (String flag : new String[]{"BUILD", "MOB_SPAWNING", "MOB_DAMAGE"}) Array.set(checks, i++, flags.getField(flag).get(null));
                    Class<?> subject = Class.forName("com.sk89q.worldguard.protection.association.RegionAssociable", true, loader);
                    Object actor = nonMemberActor(subject);
                    Class<?> resultSet = Class.forName("com.sk89q.worldguard.protection.RegionResultSet", true, loader);
                    java.lang.reflect.Method test = Class.forName("com.sk89q.worldguard.protection.ApplicableRegionSet", true, loader)
                            .getMethod("testState", subject, checks.getClass());
                    // Separate tests prevent a permissive, disjoint high-priority region from masking a denied region.
                    for (Object overlapping : overlaps) {
                        Object set = resultSet.getConstructor(java.util.List.class, region)
                                .newInstance(new java.util.ArrayList<>(java.util.List.of(overlapping)), global);
                        for (int flagIndex = 0; flagIndex < Array.getLength(checks); flagIndex++) {
                            Object single = Array.newInstance(stateFlag, 1);
                            Array.set(single, 0, Array.get(checks, flagIndex));
                            if (!Boolean.TRUE.equals(test.invoke(set, actor, single)))
                                return "WorldGuard: protected region '" + region.getMethod("getId").invoke(overlapping) + "' intersects the arena.";
                        }
                    }
                }
            } catch (ReflectiveOperationException | LinkageError error) {
                Throwable cause = invocationCause(error);
                if (warned.add(name + " arena")) java.util.logging.Logger.getLogger("CoreSuite").log(java.util.logging.Level.WARNING,
                        "Cannot verify whole arena through " + name, cause);
                return name + " arena query failed: " + cause.getClass().getSimpleName() + ": " + cause.getMessage();
            }
        }
        return null;
    }
    public static String denialReason(Location location) {
        if (location == null || location.getWorld() == null) return "Missing protection-query world.";
        for (String name : new String[]{"GriefPrevention", "WorldGuard"}) {
            Plugin provider = Bukkit.getPluginManager().getPlugin(name);
            if (provider == null) continue;
            if (!provider.isEnabled()) return name + " is installed but disabled.";
            try {
                ClassLoader loader = provider.getClass().getClassLoader();
                if (name.equals("GriefPrevention")) {
                    Object store = provider.getClass().getField("dataStore").get(provider);
                    Class<?> claim = Class.forName("me.ryanhamshire.GriefPrevention.Claim", true, loader);
                    if (store.getClass().getMethod("getClaimAt", Location.class, boolean.class, claim)
                            .invoke(store, location, true, null) != null) return "GriefPrevention: this location is inside a claim.";
                } else {
                    Class<?> worldGuard = Class.forName("com.sk89q.worldguard.WorldGuard", true, loader);
                    Object instance = worldGuard.getMethod("getInstance").invoke(null);
                    Object platform = worldGuard.getMethod("getPlatform").invoke(instance);
                    Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
                    Object query = container.getClass().getMethod("createQuery").invoke(container);
                    Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter", true, loader);
                    Object adapted = adapter.getMethod("adapt", Location.class).invoke(null, location);
                    Class<?> stateFlag = Class.forName("com.sk89q.worldguard.protection.flags.StateFlag", true, loader);
                    Class<?> flags = Class.forName("com.sk89q.worldguard.protection.flags.Flags", true, loader);
                    Object checks = Array.newInstance(stateFlag, 3);
                    Array.set(checks, 0, flags.getField("BUILD").get(null));
                    Array.set(checks, 1, flags.getField("MOB_SPAWNING").get(null));
                    Array.set(checks, 2, flags.getField("MOB_DAMAGE").get(null));
                    Class<?> locClass = Class.forName("com.sk89q.worldedit.util.Location", true, loader);
                    Class<?> subject = Class.forName("com.sk89q.worldguard.protection.association.RegionAssociable", true, loader);
                    // Test independently: an ALLOW on mob-spawning must never mask an unset,
                    // implicitly denied BUILD flag for a non-member.
                    {
                        for (String flag : new String[]{"BUILD", "MOB_SPAWNING", "MOB_DAMAGE"}) {
                            Object single = Array.newInstance(stateFlag, 1);
                            Array.set(single, 0, flags.getField(flag).get(null));
                            if (!testWorldGuardState(query, adapted, locClass, subject, single))
                                return "WorldGuard blocks " + flag.toLowerCase(java.util.Locale.ROOT).replace('_', '-') + " for a non-member boss here.";
                        }
                    }
                }
            } catch (ReflectiveOperationException | LinkageError error) {
                Throwable cause = invocationCause(error);
                if (warned.add(name)) java.util.logging.Logger.getLogger("CoreSuite").log(java.util.logging.Level.WARNING,
                        "Cannot verify boss protection through " + name + "; boss spawning/block changes are paused: " + cause, cause);
                return name + " query failed: " + cause.getClass().getSimpleName() + ": " + cause.getMessage();
            }
        }
        return null;
    }
    /** BUILD requires an actor even for non-player actions; bosses never inherit player bypass or membership. */
    static boolean testWorldGuardState(Object query, Object location, Class<?> locationType,
                                      Class<?> subjectType, Object flags) throws ReflectiveOperationException {
        Object actor = nonMemberActor(subjectType);
        java.lang.reflect.Method test = query.getClass().getMethod("testState", locationType, subjectType, flags.getClass());
        for (int index = 0; index < Array.getLength(flags); index++) {
            Object single = Array.newInstance(flags.getClass().getComponentType(), 1);
            Array.set(single, 0, Array.get(flags, index));
            if (!Boolean.TRUE.equals(test.invoke(query, location, actor, single))) return false;
        }
        return true;
    }

    private static Object nonMemberActor(Class<?> subjectType) throws ReflectiveOperationException {
        Class<?> associationType = subjectType.getMethod("getAssociation", java.util.List.class).getReturnType();
        Object nonMember = associationType.getField("NON_MEMBER").get(null);
        return java.lang.reflect.Proxy.newProxyInstance(subjectType.getClassLoader(), new Class<?>[]{subjectType},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAssociation" -> nonMember;
                    case "toString" -> "CoreSuite boss (non-member)";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.toString());
                });
    }

    static Throwable invocationCause(Throwable error) {
        while (error instanceof java.lang.reflect.InvocationTargetException && error.getCause() != null)
            error = error.getCause();
        return error;
    }

}
