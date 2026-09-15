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
    public static boolean allows(Location location) {
        if (location == null || location.getWorld() == null) return false;
        for (String name : new String[]{"GriefPrevention", "WorldGuard"}) {
            Plugin provider = Bukkit.getPluginManager().getPlugin(name);
            if (provider == null) continue;
            if (!provider.isEnabled()) return false;
            try {
                ClassLoader loader = provider.getClass().getClassLoader();
                if (name.equals("GriefPrevention")) {
                    Object store = provider.getClass().getField("dataStore").get(provider);
                    Class<?> claim = Class.forName("me.ryanhamshire.GriefPrevention.Claim", true, loader);
                    if (store.getClass().getMethod("getClaimAt", Location.class, boolean.class, claim)
                            .invoke(store, location, true, null) != null) return false;
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
                    if (!Boolean.TRUE.equals(query.getClass().getMethod("testState", locClass, subject, checks.getClass())
                            .invoke(query, adapted, null, checks))) return false;
                }
            } catch (ReflectiveOperationException | LinkageError error) {
                if (warned.add(name)) java.util.logging.Logger.getLogger("CoreSuite").warning(
                        "Cannot verify boss protection through " + name + "; boss spawning/block changes are paused: " + error);
                return false;
            }
        }
        return true;
    }
}
