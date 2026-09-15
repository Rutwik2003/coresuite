package net.rocky.coresuite.module;

import net.rocky.coresuite.CoreSuitePlugin;
import org.junit.jupiter.api.Test;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ModuleLifecycleTest {
    static class TestModule extends Module {
        boolean failConfig;
        boolean failEnable;
        int stops;
        TestModule(CoreSuitePlugin plugin) { super(plugin, "test", "test"); }
        @Override protected void loadConfig() { if (failConfig) throw new IllegalStateException("bad config"); }
        @Override public void onEnable() { if (failEnable) throw new IllegalStateException("missing dependency"); }
        @Override public void onDisable() { stops++; }
    }
    private CoreSuitePlugin plugin() {
        CoreSuitePlugin plugin = mock(CoreSuitePlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        return plugin;
    }
    @Test void configurationFailureIsIsolatedToTheModule() {
        TestModule module = new TestModule(plugin()); module.failConfig = true;
        assertDoesNotThrow(module::enable); assertFalse(module.isEnabled());
        assertEquals(0, module.stops);
    }
    @Test void reloadReturnsFalseWhenEnableFailsAndCanRecover() {
        CoreSuitePlugin plugin = plugin();
        TestModule module = new TestModule(plugin);
        ModuleManager manager = new ModuleManager(plugin); manager.registerModule(module);
        assertTrue(manager.enableModule("test"));
        module.failEnable = true;
        assertFalse(manager.reloadModule("test")); assertFalse(module.isEnabled());
        assertEquals(2, module.stops);
        module.failEnable = false;
        assertTrue(manager.enableModule("test"));
        assertTrue(manager.disableModule("test"));
    }

    @Test void customReloadFailureIsReportedAndCleanedUp() {
        CoreSuitePlugin plugin = plugin();
        TestModule module = new TestModule(plugin) {
            @Override public void onReload() { throw new IllegalStateException("reload failed"); }
        };
        ModuleManager manager = new ModuleManager(plugin); manager.registerModule(module);
        assertTrue(manager.enableModule("test"));
        assertFalse(manager.reloadModule("test"));
        assertFalse(module.isEnabled()); assertEquals(1, module.stops);
    }

    @Test void invalidYamlIsNotReplacedWithDefaults(@org.junit.jupiter.api.io.TempDir java.nio.file.Path folder) throws Exception {
        CoreSuitePlugin plugin = plugin();
        when(plugin.getDataFolder()).thenReturn(folder.toFile());
        java.nio.file.Path modules = java.nio.file.Files.createDirectories(folder.resolve("modules"));
        java.nio.file.Path configFile = modules.resolve("broken.yml");
        String original = "settings: [unterminated";
        java.nio.file.Files.writeString(configFile, original);
        Module module = new Module(plugin, "broken", "broken") {
            @Override public void onEnable() { fail("Invalid YAML must not enable a module"); }
            @Override public void onDisable() {}
        };
        assertDoesNotThrow(module::enable);
        assertFalse(module.isEnabled());
        assertEquals(original, java.nio.file.Files.readString(configFile));
    }
}
