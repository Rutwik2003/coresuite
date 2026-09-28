package net.rocky.coresuite.module.maintenance;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.module.Module;
import net.rocky.coresuite.module.discord.DiscordModule;
import org.bukkit.Bukkit;

/** Observes Maintenance; admission and whitelist rules remain owned by that plugin. */
public final class MaintenanceModule extends Module {
    public enum State { OPEN, MAINTENANCE, UNKNOWN }
    private volatile State state = State.OPEN;
    private boolean warned;
    public MaintenanceModule(CoreSuitePlugin plugin) { super(plugin, "Maintenance Bridge", "maintenance"); }
    @Override public void onEnable() {
        refresh(false);
        long interval = Math.max(1, Math.min(60, config.getInt("poll-seconds", 2))) * 20L;
        registerTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> refresh(true), interval, interval));
    }
    @Override public void onDisable() { state = State.OPEN; }
    private void refresh(boolean notify) {
        State next;
        var maintenance = Bukkit.getPluginManager().getPlugin("Maintenance");
        if (maintenance == null || !maintenance.isEnabled()) next = State.OPEN;
        else {
            try {
                ClassLoader loader = maintenance.getClass().getClassLoader();
                Class<?> provider = Class.forName("eu.kennytv.maintenance.api.MaintenanceProvider", true, loader);
                Object api = provider.getMethod("get").invoke(null);
                Class<?> apiType = Class.forName("eu.kennytv.maintenance.api.Maintenance", true, loader);
                next = Boolean.TRUE.equals(apiType.getMethod("isMaintenance").invoke(api)) ? State.MAINTENANCE : State.OPEN;
                warned = false;
            } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
                next = State.UNKNOWN;
                if (!warned) plugin.getLogger().warning("[Maintenance Bridge] Unable to read Maintenance status: " + error);
                warned = true;
            }
        }
        State previous = state;
        state = next;
        if (notify && previous != next) {
            var discord = plugin.getModuleManager().getModule("discord");
            if (discord instanceof DiscordModule bridge && bridge.isEnabled())
                bridge.maintenanceChanged(next, config.getBoolean("announce-changes", true));
        }
    }
    public static State state(CoreSuitePlugin plugin) {
        var module = plugin.getModuleManager().getModule("maintenance");
        return module instanceof MaintenanceModule bridge && bridge.isEnabled() ? bridge.state : State.OPEN;
    }
    public static String label(State state) {
        return switch (state) {
            case OPEN -> "🟢 Online";
            case MAINTENANCE -> "🟠 Under Maintenance — whitelisted players only";
            case UNKNOWN -> "🟡 Maintenance status unavailable";
        };
    }
}
