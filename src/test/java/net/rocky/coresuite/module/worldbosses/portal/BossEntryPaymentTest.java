package net.rocky.coresuite.module.worldbosses.portal;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.economy.EconomyManager;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BossEntryPaymentTest {
    @TempDir Path directory;
    @Test void declinedPaymentDoesNotGrantAdmissionOrTeleport() throws Exception {
        CoreSuitePlugin plugin = mock(CoreSuitePlugin.class); EconomyManager economy = mock(EconomyManager.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile()); when(plugin.getEconomyManager()).thenReturn(economy);
        var manager = new BossPortalManager(plugin, null, new YamlConfiguration());
        World world = mock(World.class); Player player = mock(Player.class);
        UUID id = UUID.randomUUID(); when(player.getUniqueId()).thenReturn(id);
        when(player.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(economy.isAvailable()).thenReturn(true); when(economy.getBalance(player)).thenReturn(100000d);
        when(economy.withdraw(player, 50000)).thenReturn(false);
        var active = BossPortalManager.class.getDeclaredField("portalsActive"); active.setAccessible(true); active.set(manager, true);
        var entrances = BossPortalManager.class.getDeclaredField("activePortals"); entrances.setAccessible(true);
        var portal = new BossPortalManager.PortalInstance(new Location(world, 0, 64, 0)); portal.active = true;
        ((List<BossPortalManager.PortalInstance>) entrances.get(manager)).add(portal);
        manager.handlePlayerEnterCommand(player);
        verify(economy).withdraw(player, 50000);
        verify(player, never()).teleport(any(Location.class));
        var paid = BossPortalManager.class.getDeclaredField("paidPlayers"); paid.setAccessible(true);
        assertTrue(((Set<?>) paid.get(manager)).isEmpty());
    }
}
