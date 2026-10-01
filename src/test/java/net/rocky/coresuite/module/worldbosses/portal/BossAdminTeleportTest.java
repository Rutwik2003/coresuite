package net.rocky.coresuite.module.worldbosses.portal;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.economy.EconomyManager;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BossAdminTeleportTest {
    @TempDir Path directory;

    @Test
    void adminTeleportRejectsWhenInsufficientFunds() throws Exception {
        CoreSuitePlugin plugin = mock(CoreSuitePlugin.class);
        EconomyManager economy = mock(EconomyManager.class);
        CommandSender admin = mock(CommandSender.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getEconomyManager()).thenReturn(economy);

        var config = new YamlConfiguration();
        config.set("arena.portals.entry-fee", 50000.0);
        var manager = new BossPortalManager(plugin, null, config);

        World world = mock(World.class);
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn("TestPlayer");
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(new Location(world, 100, 64, 100));

        when(economy.isAvailable()).thenReturn(true);
        when(economy.getBalance(player)).thenReturn(25000.0); // Less than 50k

        boolean result = manager.adminTeleportPlayer(player, admin);
        assertFalse(result);
        verify(economy, never()).withdraw(player, 50000.0);
        verify(player, never()).teleport(any(Location.class));

        var adminAdmitted = BossPortalManager.class.getDeclaredField("adminAdmittedPlayers");
        adminAdmitted.setAccessible(true);
        assertFalse(((Set<?>) adminAdmitted.get(manager)).contains(id));
    }

    @Test
    void adminTeleportRefundsWhenTeleportFails() throws Exception {
        CoreSuitePlugin plugin = mock(CoreSuitePlugin.class);
        EconomyManager economy = mock(EconomyManager.class);
        CommandSender admin = mock(CommandSender.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getEconomyManager()).thenReturn(economy);

        var config = new YamlConfiguration();
        config.set("arena.portals.entry-fee", 50000.0);
        config.set("arena.fixed-location.enabled", false); // No arena center
        var manager = new BossPortalManager(plugin, null, config);

        World world = mock(World.class);
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn("TestPlayer");
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(new Location(world, 100, 64, 100));

        when(economy.isAvailable()).thenReturn(true);
        when(economy.getBalance(player)).thenReturn(100000.0);
        when(economy.withdraw(player, 50000.0)).thenReturn(true);
        when(economy.deposit(player, 50000.0)).thenReturn(true);

        boolean result = manager.adminTeleportPlayer(player, admin);
        assertFalse(result);
        // Withdraw should have been attempted, followed by refund upon failure
        verify(economy).withdraw(player, 50000.0);
        verify(economy).deposit(player, 50000.0);

        var adminAdmitted = BossPortalManager.class.getDeclaredField("adminAdmittedPlayers");
        adminAdmitted.setAccessible(true);
        assertFalse(((Set<?>) adminAdmitted.get(manager)).contains(id));
    }
    @Test
    void adminTeleportRetainsPaymentWhenRefundFails() throws Exception {
        CoreSuitePlugin plugin = mock(CoreSuitePlugin.class);
        EconomyManager economy = mock(EconomyManager.class);
        CommandSender admin = mock(CommandSender.class);
        when(plugin.getDataFolder()).thenReturn(directory.toFile());
        when(plugin.getEconomyManager()).thenReturn(economy);

        var config = new YamlConfiguration();
        config.set("arena.portals.entry-fee", 50000.0);
        config.set("arena.fixed-location.enabled", false); // No arena center
        var manager = new BossPortalManager(plugin, null, config);

        World world = mock(World.class);
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.getName()).thenReturn("TestPlayer");
        when(player.isOnline()).thenReturn(true);
        when(player.getLocation()).thenReturn(new Location(world, 100, 64, 100));

        when(economy.isAvailable()).thenReturn(true);
        when(economy.getBalance(player)).thenReturn(100000.0);
        when(economy.withdraw(player, 50000.0)).thenReturn(true);
        when(economy.deposit(player, 50000.0)).thenReturn(false);
        when(plugin.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());

        boolean result = manager.adminTeleportPlayer(player, admin);
        assertFalse(result);
        // Withdraw should have been attempted, followed by refund upon failure
        verify(economy).withdraw(player, 50000.0);
        verify(economy).deposit(player, 50000.0);

        var adminAdmitted = BossPortalManager.class.getDeclaredField("adminAdmittedPlayers");
        adminAdmitted.setAccessible(true);
        assertFalse(((Set<?>) adminAdmitted.get(manager)).contains(id));
        var paid = BossPortalManager.class.getDeclaredField("paidPlayers");
        paid.setAccessible(true);
        assertTrue(((Set<?>) paid.get(manager)).contains(id));
        manager.adminTeleportPlayer(player, admin);
        verify(economy, times(1)).withdraw(player, 50000.0);
    }
}
