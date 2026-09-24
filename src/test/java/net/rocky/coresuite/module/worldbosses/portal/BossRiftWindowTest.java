package net.rocky.coresuite.module.worldbosses.portal;
import net.rocky.coresuite.module.worldbosses.WorldBossesModule;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class BossRiftWindowTest {
    @Test void formingCompletesOnceAfterWallClockDeadlineEvenWhenNoPlayersAreOnline() throws Exception {
        var module = mock(WorldBossesModule.class); when(module.isBossActive()).thenReturn(true);
        var manager = new BossPortalManager(null, module, new YamlConfiguration());
        set(manager, "forming", true); set(manager, "formingEndsAt", 480000L);
        int[] ready = {0}; set(manager, "formingCompletion", (Runnable) () -> ready[0]++);
        var scheduler = mock(org.bukkit.scheduler.BukkitScheduler.class);
        when(scheduler.runTaskTimer(isNull(), any(Runnable.class), anyLong(), anyLong())).thenReturn(mock(org.bukkit.scheduler.BukkitTask.class));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            manager.tickLifecycle(479999); assertEquals(0, ready[0]);
            manager.tickLifecycle(480000); assertEquals(1, ready[0]);
            manager.tickLifecycle(480001); assertEquals(1, ready[0]);
            assertFalse(manager.isForming()); assertTrue(manager.canEnterWindow(UUID.randomUUID(), System.currentTimeMillis()));
        }
    }
    static void set(BossPortalManager manager, String name, Object value) throws Exception {
        var field = BossPortalManager.class.getDeclaredField(name); field.setAccessible(true); field.set(manager, value);
    }
    @Test void deadlineBlocksNewcomersButKeepsSuccessfulEntrantsAndCleanupResetsThem() throws Exception {
        var manager = new BossPortalManager(null, null, new YamlConfiguration());
        UUID entrant = UUID.randomUUID(), newcomer = UUID.randomUUID();
        set(manager, "portalsActive", true); set(manager, "entryClosesAt", 900000L);
        var field = BossPortalManager.class.getDeclaredField("enteredPlayers"); field.setAccessible(true);
        ((Set<UUID>) field.get(manager)).add(entrant);
        var paid = BossPortalManager.class.getDeclaredField("paidPlayers"); paid.setAccessible(true);
        ((Set<UUID>) paid.get(manager)).add(newcomer); // Payment alone, e.g. failed teleport, is insufficient.
        assertTrue(manager.canEnterWindow(newcomer, 899999));
        assertFalse(manager.canEnterWindow(newcomer, 900000));
        assertTrue(manager.canEnterWindow(entrant, 2000000));
        manager.cleanup();
        assertFalse(manager.isActive()); assertFalse(manager.canEnterWindow(entrant, 2000001));
        assertTrue(((Set<?>) field.get(manager)).isEmpty());
    }
    @Test void orphanedActiveRiftsRecoverEvenWithZeroOnlinePlayers() throws Exception {
        var module = mock(WorldBossesModule.class);
        var manager = new BossPortalManager(null, module, new YamlConfiguration());
        set(manager, "portalsActive", true);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            manager.tickLifecycle(System.currentTimeMillis());
            assertFalse(manager.isActive());
        }
    }
    @Test void adminCommandsClearPendingRiftsWithoutNeedingABoss() throws Exception {
        var module = new WorldBossesModule(null);
        var manager = new BossPortalManager(null, module, new YamlConfiguration());
        var field = WorldBossesModule.class.getDeclaredField("portalManager"); field.setAccessible(true); field.set(module, manager);
        var command = mock(org.bukkit.command.Command.class); when(command.getName()).thenReturn("bossadmin");
        var sender = mock(org.bukkit.command.CommandSender.class); when(sender.hasPermission(anyString())).thenReturn(true);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of());
            for (String sub : List.of("kill", "despawn")) {
                set(manager, "forming", true);
                assertTrue(module.onCommand(sender, command, "bossadmin", new String[]{sub}));
                assertFalse(manager.isActive());
            }
        }
    }
}
