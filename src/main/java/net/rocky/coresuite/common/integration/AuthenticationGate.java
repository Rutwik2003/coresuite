package net.rocky.coresuite.common.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.util.concurrent.atomic.AtomicBoolean;

/** Optional AuthMe API bridge. An installed but unavailable authenticator fails closed. */
public final class AuthenticationGate {
    private static final AtomicBoolean warned = new AtomicBoolean();
    private AuthenticationGate() {}
    public static boolean isAuthenticated(Player player) {
        Plugin auth = Bukkit.getPluginManager().getPlugin("AuthMe");
        if (auth == null) return true;
        if (!auth.isEnabled()) return false;
        try {
            Class<?> api = Class.forName("fr.xephi.authme.api.v3.AuthMeApi", true, auth.getClass().getClassLoader());
            Object instance = api.getMethod("getInstance").invoke(null);
            return Boolean.TRUE.equals(api.getMethod("isAuthenticated", Player.class).invoke(instance, player));
        } catch (ReflectiveOperationException | LinkageError error) {
            if (warned.compareAndSet(false, true)) java.util.logging.Logger.getLogger("CoreSuite").warning(
                    "AuthMe authentication could not be verified; automatic rewards and join teleports will wait. " + error.getClass().getSimpleName());
            return false;
        }
    }
    /** Join delivery waits for authentication and a short inventory/teleport settling interval. */
    public static void afterLogin(Plugin plugin, Player player, Runnable action) {
        new BukkitRunnable() {
            int attempts;
            @Override public void run() {
                if (!player.isOnline() || ++attempts > 180) { cancel(); return; }
                if (!isAuthenticated(player)) return;
                cancel();
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (player.isOnline() && isAuthenticated(player)) action.run();
                }, 2L);
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }
}
