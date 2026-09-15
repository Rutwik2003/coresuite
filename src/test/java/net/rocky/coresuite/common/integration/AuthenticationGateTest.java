package net.rocky.coresuite.common.integration;
import org.junit.jupiter.api.Test;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class AuthenticationGateTest {
    @Test void optionalProviderAndUnauthenticatedSessionsAreDistinguished() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            PluginManager manager = mock(PluginManager.class); Player player = mock(Player.class);
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            assertTrue(AuthenticationGate.isAuthenticated(player));
            Plugin auth = mock(Plugin.class); when(manager.getPlugin("AuthMe")).thenReturn(auth);
            assertFalse(AuthenticationGate.isAuthenticated(player));
            when(auth.isEnabled()).thenReturn(true);
            fr.xephi.authme.api.v3.AuthMeApi.broken = false;
            fr.xephi.authme.api.v3.AuthMeApi.authenticated = false;
            assertFalse(AuthenticationGate.isAuthenticated(player));
            fr.xephi.authme.api.v3.AuthMeApi.authenticated = true;
            assertTrue(AuthenticationGate.isAuthenticated(player));
            fr.xephi.authme.api.v3.AuthMeApi.broken = true;
            assertFalse(AuthenticationGate.isAuthenticated(player));
            fr.xephi.authme.api.v3.AuthMeApi.broken = false;
        }
    }
    @Test void protectionProviderFailureCannotPermitBossPlacement() {
        try (var bukkit = mockStatic(Bukkit.class)) {
            PluginManager manager = mock(PluginManager.class);
            bukkit.when(Bukkit::getPluginManager).thenReturn(manager);
            var location = new org.bukkit.Location(mock(org.bukkit.World.class), 0, 65, 0);
            assertTrue(BossProtectionGate.allows(location));
            Plugin guard = mock(Plugin.class); when(manager.getPlugin("WorldGuard")).thenReturn(guard);
            assertFalse(BossProtectionGate.allows(location));
            when(guard.isEnabled()).thenReturn(true);
            assertFalse(BossProtectionGate.allows(location));
        }
    }
}
