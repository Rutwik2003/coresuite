/*
 * Copyright (c) 2024. CoreSuite. All rights reserved.
 */
package net.rocky.coresuite.common.integration;

import net.rocky.coresuite.CoreSuitePlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;

import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Soft-dependency wrapper. Checks and caches which optional plugins are available.
 */
public final class IntegrationManager {
    private final CoreSuitePlugin plugin;
    private final Map<String, Boolean> cache = new ConcurrentHashMap<>();

    private static final String[] WATCHED_PLUGINS = {
        "Vault", "Citizens", "SkinsRestorer", "PlaceholderAPI",
        "ExcellentCrates", "Vulcan", "ajLeaderboards", "UltraCosmetics",
        "Essentials"
    };

    /**
     * Constructs a new IntegrationManager.
     *
     * @param plugin the plugin instance
     */
    public IntegrationManager(CoreSuitePlugin plugin) {
        this.plugin = plugin;
        refresh();
    }

    /**
     * Refreshes the cache of available plugins.
     */
    public void refresh() {
        cache.clear();
        StringBuilder found = new StringBuilder();
        for (String name : WATCHED_PLUGINS) {
            org.bukkit.plugin.Plugin p = Bukkit.getPluginManager().getPlugin(name);
            boolean available = p != null && p.isEnabled();
            cache.put(name.toLowerCase(), available);
            if (available) {
                if (!found.isEmpty()) found.append(", ");
                found.append(name);
            }
        }
        if (!found.isEmpty()) {
            plugin.getLogger().info("Integrations detected: " + found);
        }
    }

    /**
     * Checks if a plugin is available.
     *
     * @param name the plugin name
     * @return true if the plugin is available, false otherwise
     */
    public boolean isPluginAvailable(String name) {
        return cache.getOrDefault(name.toLowerCase(), false);
    }

    /**
     * Gets a plugin by name.
     *
     * @param name the plugin name
     * @return the plugin instance, or null if not found
     */
    public Plugin getPlugin(String name) {
        return Bukkit.getPluginManager().getPlugin(name);
    }

    /**
     * Checks if Vault is available.
     * @return true if Vault is available
     */
    public boolean hasVault() { return isPluginAvailable("vault"); }

    /**
     * Checks if Citizens is available.
     * @return true if Citizens is available
     */
    public boolean hasCitizens() { return isPluginAvailable("citizens"); }

    /**
     * Checks if SkinsRestorer is available.
     * @return true if SkinsRestorer is available
     */
    public boolean hasSkinsRestorer() { return isPluginAvailable("skinsrestorer"); }

    /**
     * Checks if PlaceholderAPI is available.
     * @return true if PlaceholderAPI is available
     */
    public boolean hasPlaceholderAPI() { return isPluginAvailable("placeholderapi"); }

    /**
     * Checks if ExcellentCrates is available.
     * @return true if ExcellentCrates is available
     */
    public boolean hasExcellentCrates() { return isPluginAvailable("excellentcrates"); }

    /**
     * Checks if Vulcan is available.
     * @return true if Vulcan is available
     */
    public boolean hasVulcan() { return isPluginAvailable("vulcan"); }

    /**
     * Checks if UltraCosmetics is available.
     * @return true if UltraCosmetics is available
     */
    public boolean hasUltraCosmetics() { return isPluginAvailable("ultracosmetics"); }

    /**
     * Checks if Essentials is available.
     * @return true if Essentials is available
     */
    public boolean hasEssentials() { return isPluginAvailable("essentials"); }
}
