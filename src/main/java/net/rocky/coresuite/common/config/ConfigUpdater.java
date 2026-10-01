package net.rocky.coresuite.common.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

/** Additive config updates: validate first, preserve values, back up, replace atomically. */
public final class ConfigUpdater {
    private ConfigUpdater() {}

    public static YamlConfiguration load(JavaPlugin plugin, String resource) {
        Path file = plugin.getDataFolder().toPath().resolve(resource);
        try (InputStream stream = plugin.getResource(resource)) {
            YamlConfiguration defaults = new YamlConfiguration();
            if (stream != null) defaults.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            YamlConfiguration existing = new YamlConfiguration();
            boolean present = Files.exists(file);
            if (present) {
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) { existing.load(reader); }
            }
            boolean changed = false;
            // The original delivery policy made the pack optional and treated an offer as success.
            // Upgrade once; subsequent explicit administrator choices remain intact.
            if (resource.equals("modules/resource-pack.yml") && existing.getInt("delivery-policy-version", 0) < 1) {
                existing.set("required", true);
                existing.set("delivery-policy-version", 1);
                changed = true;
            }
            if (resource.equals("modules/world-bosses.yml"))
                changed |= net.rocky.coresuite.module.worldbosses.rewards.BossLootMigration.upgrade(existing, defaults);
            changed |= merge(existing, defaults);
            if (!present || changed) {
                Files.createDirectories(file.getParent());
                Path backup = null;
                if (present) {
                    backup = file.resolveSibling(file.getFileName() + ".before-merge-" + java.util.UUID.randomUUID() + ".bak");
                    Files.copy(file, backup);
                }
                Path temporary = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
                try {
                    Files.writeString(temporary, existing.saveToString(), StandardCharsets.UTF_8);
                    try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                    catch (AtomicMoveNotSupportedException ignored) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
                } finally { Files.deleteIfExists(temporary); }
                plugin.getLogger().info("Updated " + resource + " with missing defaults"
                        + (backup == null ? "." : "; original saved as " + backup.getFileName()));
            }
            return existing;
        } catch (Exception error) {
            throw new IllegalStateException("Cannot safely update " + resource + "; existing configuration was not replaced with defaults", error);
        }
    }

    static boolean merge(ConfigurationSection target, ConfigurationSection defaults) {
        boolean changed = false;
        for (String key : defaults.getKeys(false)) {
            if (defaults.isConfigurationSection(key)) {
                if (!target.contains(key, true)) { target.createSection(key); changed = true; }
                if (!target.isConfigurationSection(key)) {
                    throw new IllegalArgumentException("Expected a config section at " + target.getCurrentPath() + "." + key);
                }
                changed |= merge(target.getConfigurationSection(key), defaults.getConfigurationSection(key));
            } else if (!target.contains(key, true)) {
                target.set(key, defaults.get(key));
                target.setComments(key, defaults.getComments(key));
                target.setInlineComments(key, defaults.getInlineComments(key));
                changed = true;
            }
        }
        return changed;
    }
}
