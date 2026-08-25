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
        loadConfig();
        try {
            onEnable();
            enabled = true;
            log("Enabled successfully.");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to enable module: " + name, e);
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
        } catch (Exception e) {
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
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        File modulesFolder = new File(plugin.getDataFolder(), "modules");
        if (!modulesFolder.exists()) {
            modulesFolder.mkdirs();
        }
        
        File configFile = new File(modulesFolder, configKey + ".yml");
        
        if (!configFile.exists()) {
            // Try saving default from jar
            String resourcePath = "modules/" + configKey + ".yml";
            if (plugin.getResource(resourcePath) != null) {
                plugin.saveResource(resourcePath, false);
            } else {
                // If there's no default config in the jar, just create an empty one
                try {
                    configFile.createNewFile();
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Could not create empty config file for module: " + name, e);
                }
            }
        }

        try {
            config = YamlConfiguration.loadConfiguration(new java.io.InputStreamReader(new java.io.FileInputStream(configFile), java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.io.FileNotFoundException e) {
            config = new YamlConfiguration();
        }

        // Load defaults if present in JAR
        try (InputStream defaultStream = plugin.getResource("modules/" + configKey + ".yml")) {
            if (defaultStream != null) {
                YamlConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream, StandardCharsets.UTF_8));
                boolean changed = false;
                for (String key : defaultConfig.getKeys(true)) {
                    if (!config.contains(key)) {
                        config.set(key, defaultConfig.get(key));
                        changed = true;
                    }
                }
                if (changed) {
                    saveConfig();
                }
            }
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Could not close default config stream for module: " + name, e);
        }
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
