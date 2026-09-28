package net.rocky.coresuite.module;

import net.rocky.coresuite.CoreSuitePlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Abstract base class representing a feature module in CoreSuite.
 */
public abstract class Module {
    
    protected final CoreSuitePlugin plugin;
    protected final String name;
    protected final String configKey;
    protected FileConfiguration config;
    private boolean enabled = false;
    private final List<Listener> registeredListeners = new ArrayList<>();
    private final List<org.bukkit.scheduler.BukkitTask> registeredTasks = new ArrayList<>();

    public Module(CoreSuitePlugin plugin, String name, String configKey) {
        this.plugin = plugin;
        this.name = name;
        this.configKey = configKey;
    }

    // Abstract lifecycle methods to be implemented by child classes
    public abstract void onEnable();
    public abstract void onDisable();
    
    // Virtual method - default implementation reloads the configuration and restarts the module
    public void onReload() {
        disable();
        enable();
    }

    // Called by ModuleManager only
    final void enable() {
        if (enabled) return;
        boolean started = false;
        try {
            loadConfig();
            started = true;
            onEnable();
            enabled = true;
            log("Enabled successfully.");
        } catch (Exception | LinkageError e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to enable module: " + name, e);
            if (started) {
                try { onDisable(); }
                catch (Exception | LinkageError cleanupError) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to clean up partially enabled module: " + name, cleanupError);
                }
            }
            // Clean up any listeners registered during the failed enable attempt
            for (Listener listener : registeredListeners) {
                HandlerList.unregisterAll(listener);
            }
            registeredListeners.clear();
            for (org.bukkit.scheduler.BukkitTask task : registeredTasks) {
                if (!task.isCancelled()) {
                    task.cancel();
                }
            }
            registeredTasks.clear();
        }
    }

    final void disable() {
        if (!enabled) return;
        try {
            onDisable();
        } catch (Exception | LinkageError e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to disable module: " + name, e);
        }
        
        // Unregister all listeners
        for (Listener listener : registeredListeners) {
            HandlerList.unregisterAll(listener);
        }
        registeredListeners.clear();
        
        // Cancel all registered tasks
        for (org.bukkit.scheduler.BukkitTask task : registeredTasks) {
            if (!task.isCancelled()) {
                task.cancel();
            }
        }
        registeredTasks.clear();
        enabled = false;
        log("Disabled.");
    }

    /**
     * Loads the module configuration from plugins/CoreSuite/modules/<configKey>.yml.
     * If the file doesn't exist, it copies the default from jar resources modules/<configKey>.yml.
     */
    protected void loadConfig() {
        config = net.rocky.coresuite.common.config.ConfigUpdater.load(plugin, "modules/" + configKey + ".yml");
    }

    /**
     * Saves the current config memory state to the file.
     */
    protected void saveConfig() {
        if (config == null) {
            return;
        }
        File modulesFolder = new File(plugin.getDataFolder(), "modules");
        File configFile = new File(modulesFolder, configKey + ".yml");
        try {
            config.save(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save config to " + configFile, e);
        }
    }

    // Helper methods
    protected final void registerListener(Listener listener) {
        plugin.getServer().getPluginManager().registerEvents(listener, plugin);
        registeredListeners.add(listener);
    }

    /** Releases both Bukkit registration and our lifecycle reference when replacing a listener. */
    protected final void unregisterListener(Listener listener) {
        HandlerList.unregisterAll(listener);
        registeredListeners.remove(listener);
    }

    protected final org.bukkit.scheduler.BukkitTask registerTask(org.bukkit.scheduler.BukkitTask task) {
        registeredTasks.add(task);
        return task;
    }

    protected final void log(String message) {
        plugin.getLogger().info("[" + name + "] " + message);
    }

    protected final void warn(String message) {
        plugin.getLogger().warning("[" + name + "] " + message);
    }

    protected final void debug(String message) {
        if (plugin.getConfig().getBoolean("debug", false)) {
            plugin.getLogger().info("[" + name + "/DEBUG] " + message);
        }
    }

    // Getters
    public final String getName() { return name; }
    
    public final String getConfigKey() { return configKey; }
    
    public final boolean isEnabled() { return enabled; }
    
    public final CoreSuitePlugin getPlugin() { return plugin; }
    
    public final FileConfiguration getConfig() { return config; }
    
    public final File getDataFolder() {
        File folder = new File(plugin.getDataFolder(), "modules" + File.separator + configKey);
        if (!folder.exists()) {
            folder.mkdirs();
        }
        return folder;
    }
}
