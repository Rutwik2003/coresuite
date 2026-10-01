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
    @Test void bossMigrationBacksUpOriginalAndPersistsOnce() throws Exception {
        Path file = directory.resolve("modules/world-bosses.yml"); Files.createDirectories(file.getParent());
        String original = "valerius:\n  loot:\n    participation:\n      currency: 20000\n";
        Files.writeString(file, original);
        String defaults = Files.readString(Path.of("src/main/resources/modules/world-bosses.yml"));
        var config = ConfigUpdater.load(plugin(defaults), "modules/world-bosses.yml");
        assertEquals(55000, config.getInt("valerius.loot.participation.currency"));
        ConfigUpdater.load(plugin(defaults), "modules/world-bosses.yml");
        try (var files = Files.list(file.getParent())) {
            var backups = files.filter(p -> p.toString().endsWith(".bak")).toList();
            assertEquals(1, backups.size()); assertEquals(original, Files.readString(backups.getFirst()));
        }
    }

    @Test void bossLootMigrationUpdatesStockPreservesCustomAndOnlyRunsOnce() throws Exception {
        var defaults = new YamlConfiguration();
        defaults.load(Path.of("src/main/resources/modules/world-bosses.yml").toFile());
        var target = new YamlConfiguration();
        target.set("valerius.loot.participation.currency", 20000);
        target.set("valerius.loot.rank-2-3.currency", 123456);
        target.set("valerius.loot.rank-2-3.commands", java.util.List.of("custom reward"));
        assertTrue(net.rocky.coresuite.module.worldbosses.rewards.BossLootMigration.upgrade(target, defaults));
        mergeForTest(target, defaults);
        assertEquals(55000, target.getInt("valerius.loot.participation.currency"));
        assertEquals(123456, target.getInt("valerius.loot.rank-2.currency"));
        assertEquals(java.util.List.of("custom reward"), target.getStringList("valerius.loot.rank-3.commands"));
        target.set("valerius.loot.participation.currency", 20000);
        assertFalse(net.rocky.coresuite.module.worldbosses.rewards.BossLootMigration.upgrade(target, defaults));
        assertEquals(20000, target.getInt("valerius.loot.participation.currency"));
    }
    private void mergeForTest(YamlConfiguration target, YamlConfiguration defaults) { ConfigUpdater.merge(target, defaults); }

    @Test void resourcePackPolicyMigratesOnceAndPreservesOtherSettings() throws Exception {
        Path file=directory.resolve("modules/resource-pack.yml");Files.createDirectories(file.getParent());
        Files.writeString(file,"required: false\nrepository: owner/custom\nupdate-online-players: false\n");
        var plugin=plugin("delivery-policy-version: 1\nrequired: true\nload-timeout-seconds: 180\nrepository: default/repo\n");
        var config=ConfigUpdater.load(plugin,"modules/resource-pack.yml");
        assertTrue(config.getBoolean("required"));assertEquals("owner/custom",config.getString("repository"));
        assertEquals(180,config.getInt("load-timeout-seconds"));assertFalse(config.getBoolean("update-online-players"));
        config.set("required",false);config.save(file.toFile());
        assertFalse(ConfigUpdater.load(plugin,"modules/resource-pack.yml").getBoolean("required"));
        try(var files=Files.list(file.getParent())) { assertEquals(1,files.filter(p->p.toString().endsWith(".bak")).count()); }
    }
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
    @Test void extendedPlaytimeMilestonesMergeWithoutChangingCustomRewards() throws Exception {
        var defaults = new YamlConfiguration();
        defaults.load(Path.of("src/main/resources/modules/playtime-rewards.yml").toFile());
        var current = new YamlConfiguration();
        current.set("rewards.reward_300h.money", 123);
        current.set("rewards.reward_350h.commands", java.util.List.of("say custom"));
        assertTrue(ConfigUpdater.merge(current, defaults));
        assertEquals(123,current.getInt("rewards.reward_300h.money"));
        assertEquals(java.util.List.of("say custom"),current.getStringList("rewards.reward_350h.commands"));
        assertEquals(7200000,current.getInt("rewards.reward_2000h.required-seconds"));
        assertFalse(ConfigUpdater.merge(current,defaults));
    }


    @Test void mvpExtraKeysMergeForEveryBossWithoutReplacingConfiguredCommands() throws Exception {
        var defaults = new YamlConfiguration(); defaults.load(Path.of("src/main/resources/modules/world-bosses.yml").toFile());
        var config = new YamlConfiguration();
        config.set("valerius.loot.rank-1.commands", java.util.List.of("custom command"));
        assertTrue(ConfigUpdater.merge(config, defaults));
        for (String boss : java.util.List.of("valerius", "aethelgard", "xolthos", "borealis"))
            assertEquals(java.util.List.of("crates key give %player% Common 1", "crates key give %player% Uncommon 1", "crates key give %player% Rare 1"),
                    config.getStringList(boss + ".loot.rank-1.extra-key-commands"));
        assertEquals(java.util.List.of("custom command"), config.getStringList("valerius.loot.rank-1.commands"));
        config.set("valerius.loot.rank-1.extra-key-commands", java.util.List.of());
        ConfigUpdater.merge(config, defaults);
        assertTrue(config.getStringList("valerius.loot.rank-1.extra-key-commands").isEmpty());
    }
}
