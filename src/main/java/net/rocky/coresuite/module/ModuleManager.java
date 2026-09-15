package net.rocky.coresuite.module;

import net.rocky.coresuite.CoreSuitePlugin;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Manages the registration, lifecycle, and retrieval of modules.
 */
public final class ModuleManager {
    
    private final CoreSuitePlugin plugin;
    private final Map<String, Module> modules = new LinkedHashMap<>();

    public ModuleManager(CoreSuitePlugin plugin) { 
        this.plugin = plugin; 
    }

    public void registerModule(Module module) {
        modules.put(module.getConfigKey().toLowerCase(), module);
    }

    /**
     * Reads config.yml "modules" section, enables each that's set to true.
     */
    public void enableModules() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("modules");
        if (section == null) { 
            plugin.getLogger().warning("Could not find 'modules' section in config.yml! No modules will be enabled.");
            return; 
        }
        
        int count = 0;
        for (Map.Entry<String, Module> entry : modules.entrySet()) {
            Module module = entry.getValue();
            if (section.getBoolean(module.getConfigKey(), false)) {
                module.enable();
                if (module.isEnabled()) {
                    count++;
                }
            }
        }
        
        // This count is now logged in the CoreSuitePlugin banner end
    }

    public void disableAllModules() {
        // Disable in reverse order
        List<Module> moduleList = new ArrayList<>(modules.values());
        Collections.reverse(moduleList);
        
        for (Module module : moduleList) {
            if (module.isEnabled()) {
                module.disable();
            }
        }
    }

    public boolean enableModule(String configKey) {
        Module module = modules.get(configKey.toLowerCase());
        if (module != null && !module.isEnabled()) {
            module.enable();
            return module.isEnabled();
        }
        return false;
    }

    public boolean disableModule(String configKey) {
        Module module = modules.get(configKey.toLowerCase());
        if (module != null && module.isEnabled()) {
            module.disable();
            return !module.isEnabled();
        }
        return false;
    }

    public boolean reloadModule(String configKey) {
        Module module = modules.get(configKey.toLowerCase());
        if (module != null) {
            try {
                if (module.isEnabled()) {
                    module.onReload();
                } else {
                    module.enable();
                }
                return module.isEnabled();
            } catch (Exception | LinkageError e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to reload module: " + module.getName(), e);
                module.disable();
                return false;
            }
        }
        return false;
    }

    public Module getModule(String configKey) {
        return modules.get(configKey.toLowerCase());
    }

    public Collection<Module> getAllModules() { 
        return Collections.unmodifiableCollection(modules.values()); 
    }

    public int getEnabledCount() { 
        return (int) modules.values().stream().filter(Module::isEnabled).count(); 
    }
}
