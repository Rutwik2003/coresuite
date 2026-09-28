package net.rocky.coresuite.module;
import net.rocky.coresuite.CoreSuitePlugin;
import org.bukkit.Server;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ModuleListenerLifecycleTest {
    @Test void replacingListenersDoesNotRetainEveryOldManager() throws Exception {
        var core = mock(CoreSuitePlugin.class);
        var server = mock(Server.class);
        when(core.getServer()).thenReturn(server);
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        class TestModule extends Module {
            TestModule() { super(core,"test","test"); }
            public void onEnable() {}
            public void onDisable() {}
            void add(Listener listener) { registerListener(listener); }
            void remove(Listener listener) { unregisterListener(listener); }
        }
        var module = new TestModule();
        Listener current = new Listener() {};
        module.add(current);
        for (int i=0; i<100; i++) {
            module.remove(current);
            current = new Listener() {};
            module.add(current);
        }
        var field = Module.class.getDeclaredField("registeredListeners"); field.setAccessible(true);
        assertEquals(java.util.List.of(current),field.get(module));
    }
}
