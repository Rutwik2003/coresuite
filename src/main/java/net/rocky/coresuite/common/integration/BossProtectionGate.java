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
                    if (!testWorldGuardState(query, adapted, locClass, subject, checks)) {
                        for (String flag : new String[]{"BUILD", "MOB_SPAWNING", "MOB_DAMAGE"}) {
                            Object single = Array.newInstance(stateFlag, 1);
                            Array.set(single, 0, flags.getField(flag).get(null));
                            if (!testWorldGuardState(query, adapted, locClass, subject, single))
                                return "WorldGuard blocks " + flag.toLowerCase(java.util.Locale.ROOT).replace('_', '-') + " for a non-member boss here.";
                        }
                        return "WorldGuard denies boss actions at this location.";
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
        Class<?> associationType = subjectType.getMethod("getAssociation", java.util.List.class).getReturnType();
        Object nonMember = associationType.getField("NON_MEMBER").get(null);
        Object actor = java.lang.reflect.Proxy.newProxyInstance(subjectType.getClassLoader(), new Class<?>[]{subjectType},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getAssociation" -> nonMember;
                    case "toString" -> "CoreSuite boss (non-member)";
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> throw new UnsupportedOperationException(method.toString());
                });
        return Boolean.TRUE.equals(query.getClass().getMethod("testState", locationType, subjectType, flags.getClass())
                .invoke(query, location, actor, flags));
    }

    static Throwable invocationCause(Throwable error) {
        while (error instanceof java.lang.reflect.InvocationTargetException && error.getCause() != null)
            error = error.getCause();
        return error;
    }

}
