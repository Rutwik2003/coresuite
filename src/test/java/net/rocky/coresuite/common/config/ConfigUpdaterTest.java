package net.rocky.coresuite.common.config;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ConfigUpdaterTest {
    @TempDir Path directory;
    private JavaPlugin plugin(String defaults) {
        var plugin = mock(JavaPlugin.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        when(plugin.getResource(anyString())).thenAnswer(call -> new ByteArrayInputStream(defaults.getBytes(StandardCharsets.UTF_8)));
        return plugin;
    }
    @Test void preservesCustomValuesAndListsAndBacksUpExactlyOnce() throws Exception {
        String original = "enabled: false\nbaits:\n  custom: 99\ncommands: []\n";
        Files.writeString(directory.resolve("config.yml"), original);
        var plugin = plugin("enabled: true\nbaits:\n  default: 2\ncommands: [give]\nnew-setting: 8\n");
        var merged = ConfigUpdater.load(plugin, "config.yml");
        assertFalse(merged.getBoolean("enabled")); assertEquals(99, merged.getInt("baits.custom"));
        assertTrue(merged.getStringList("commands").isEmpty()); assertEquals(2, merged.getInt("baits.default"));
        try (var files = Files.list(directory)) {
            Path backup = files.filter(p -> p.toString().endsWith(".bak")).findFirst().orElseThrow();
            assertEquals(original, Files.readString(backup));
        }
        ConfigUpdater.load(plugin, "config.yml");
        try (var files = Files.list(directory)) { assertEquals(1, files.filter(p -> p.toString().endsWith(".bak")).count()); }
    }
    @Test void invalidYamlOrSectionConflictNeverOverwritesOriginal() throws Exception {
        for (String original : new String[]{"bad: [\n", "nested: false\n"}) {
            Files.writeString(directory.resolve("config.yml"), original);
            assertThrows(IllegalStateException.class, () -> ConfigUpdater.load(plugin("nested:\n  new: 3\n"), "config.yml"));
            assertEquals(original, Files.readString(directory.resolve("config.yml")));
        }
    }
    @Test void allShippedConfigsParseMergeAndAreIdempotent() throws Exception {
        try (var files = Files.walk(Path.of("src/main/resources"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".yml")).toList()) {
                var defaults = new YamlConfiguration(); defaults.load(file.toFile());
                var target = new YamlConfiguration(); ConfigUpdater.merge(target, defaults);
                assertFalse(ConfigUpdater.merge(target, defaults), file.toString());
                for (String key : defaults.getKeys(true)) if (!defaults.isConfigurationSection(key))
                    assertEquals(defaults.get(key), target.get(key), file + ":" + key);
            }
        }
    }
}
