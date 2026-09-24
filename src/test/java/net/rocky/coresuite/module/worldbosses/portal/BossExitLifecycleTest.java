package net.rocky.coresuite.module.worldbosses.portal;

import net.rocky.coresuite.common.integration.BossProtectionGate;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BossExitLifecycleTest {
    @Test void unpaidTeleportsAreRejectedAndPaidEntryIsAllowed() throws Exception {
        var manager = new BossPortalManager(null, null, new YamlConfiguration());
        World world = mock(World.class); Player player = mock(Player.class);
        UUID id = UUID.randomUUID(); when(player.getUniqueId()).thenReturn(id);
        var field = BossPortalManager.class.getDeclaredField("arenaOccupancy"); field.setAccessible(true);
        field.set(manager, (java.util.function.Predicate<Location>) location -> location.getX() < 200);
        var blocked = new org.bukkit.event.player.PlayerTeleportEvent(player, new Location(world, 300, 64, 0), new Location(world, 0, 64, 0));
        manager.onArenaTeleport(blocked); assertTrue(blocked.isCancelled());
        var paid = BossPortalManager.class.getDeclaredField("paidPlayers"); paid.setAccessible(true);
        ((Set<UUID>) paid.get(manager)).add(id);
        var active = BossPortalManager.class.getDeclaredField("portalsActive"); active.setAccessible(true); active.set(manager, true);
        var admitted = new org.bukkit.event.player.PlayerTeleportEvent(player, new Location(world, 300, 64, 0), new Location(world, 0, 64, 0));
        manager.onArenaTeleport(admitted); assertFalse(admitted.isCancelled());
    }

    @Test void deadlineEvacuatesToRecordedRiftOrSafeWorldSpawn() throws Exception {
        for (boolean recorded : new boolean[]{true, false}) {
            var manager = new BossPortalManager(null, null, new YamlConfiguration());
            World world = mock(World.class); var border = mock(WorldBorder.class);
            when(world.getWorldBorder()).thenReturn(border); when(border.isInside(any())).thenReturn(true);
            Location center = new Location(world, 0, 64, 0), rift = new Location(world, 300, 64, 0), spawn = new Location(world, 400, 64, 0);
            when(world.getSpawnLocation()).thenReturn(spawn);
            Player player = mock(Player.class); UUID id = UUID.randomUUID();
            when(player.getUniqueId()).thenReturn(id); when(player.getLocation()).thenReturn(center);
            if (recorded) {
                var field = BossPortalManager.class.getDeclaredField("playerEntranceLocations"); field.setAccessible(true);
                ((Map<UUID, Location>) field.get(manager)).put(id, rift);
            }
            List<Runnable> delayed = new ArrayList<>(), repeated = new ArrayList<>();
            BukkitScheduler scheduler = mock(BukkitScheduler.class);
            when(scheduler.runTaskLater(isNull(), any(Runnable.class), anyLong())).thenAnswer(c -> { delayed.add(c.getArgument(1)); return mock(BukkitTask.class); });
            when(scheduler.runTaskTimer(isNull(), any(Runnable.class), anyLong(), anyLong())).thenAnswer(c -> { repeated.add(c.getArgument(1)); return mock(BukkitTask.class); });
            when(scheduler.runTaskTimer(isNull(), any(BukkitRunnable.class), anyLong(), anyLong())).thenReturn(mock(BukkitTask.class));
            try (var bukkit = mockStatic(Bukkit.class); var gate = mockStatic(BossProtectionGate.class);
                 var movement = mockStatic(net.rocky.coresuite.module.worldbosses.combat.BossMovement.class)) {
                bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
                bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
                bukkit.when(Bukkit::getWorlds).thenReturn(List.of(world));
                gate.when(() -> BossProtectionGate.arenaOccupancy(any(), anyDouble())).thenReturn((java.util.function.Predicate<Location>) loc -> loc.getX() < 200);
                movement.when(() -> net.rocky.coresuite.module.worldbosses.combat.BossMovement.findLanding(any(), any())).thenAnswer(c -> c.getArgument(0));
                movement.when(() -> net.rocky.coresuite.module.worldbosses.combat.BossMovement.teleport(eq(player), any(), any())).thenAnswer(c -> {
                    Location destination = c.getArgument(1); assertEquals(recorded ? rift : spawn, destination);
                    when(player.getLocation()).thenReturn(destination); return true;
                });
                manager.spawnExitPortal(center); delayed.getFirst().run();
                var deadline = BossPortalManager.class.getDeclaredField("evacuationAt"); deadline.setAccessible(true); deadline.set(manager, 1L);
                repeated.getLast().run();
                assertEquals(recorded ? rift : spawn, player.getLocation());
                assertFalse(manager.isActive());
            }
        }
    }

    @Test void exitStaysForUntrackedOccupantsThenClosesWhenRegionEmpty() {
        var config = new YamlConfiguration();
        var manager = new BossPortalManager(null, null, config);
        World world = mock(World.class);
        Location center = new Location(world, 0, 64, 0);
        Player occupant = mock(Player.class);
        when(occupant.getLocation()).thenReturn(new Location(world, 150, 64, 0));
        List<Runnable> delayed = new ArrayList<>(), repeated = new ArrayList<>();
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskLater(isNull(), any(Runnable.class), anyLong())).thenAnswer(c -> {
            delayed.add(c.getArgument(1)); return mock(BukkitTask.class);
        });
        when(scheduler.runTaskTimer(isNull(), any(Runnable.class), anyLong(), anyLong())).thenAnswer(c -> {
            repeated.add(c.getArgument(1)); return mock(BukkitTask.class);
        });
        // Particle task uses BukkitRunnable; the supplied scheduler must accept it too.
        when(scheduler.runTaskTimer(isNull(), any(org.bukkit.scheduler.BukkitRunnable.class), anyLong(), anyLong())).thenReturn(mock(BukkitTask.class));
        try (var bukkit = mockStatic(Bukkit.class); var gate = mockStatic(BossProtectionGate.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(occupant));
            gate.when(() -> BossProtectionGate.arenaOccupancy(any(), anyDouble())).thenReturn((java.util.function.Predicate<Location>) loc -> loc.getX() <= 200);
            manager.spawnExitPortal(center);
            assertTrue(manager.isActive(), "Delay must reserve the encounter");
            delayed.getFirst().run();
            Runnable cleanup = repeated.getLast();
            cleanup.run();
            assertTrue(manager.isActive(), "Admin arrivals and spectators must keep the exit open");
            when(occupant.getLocation()).thenReturn(new Location(world, 250, 64, 0));
            cleanup.run();
            assertFalse(manager.isActive());
        }
    }

    @Test void cleanupCancelsPendingExitWithoutResurrectingIt() {
        var manager = new BossPortalManager(null, null, new YamlConfiguration());
        World world = mock(World.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        List<Runnable> delayed = new ArrayList<>();
        BukkitTask task = mock(BukkitTask.class);
        when(scheduler.runTaskLater(isNull(), any(Runnable.class), anyLong())).thenAnswer(c -> {
            delayed.add(c.getArgument(1)); return task;
        });
        try (var bukkit = mockStatic(Bukkit.class); var gate = mockStatic(BossProtectionGate.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            manager.spawnExitPortal(new Location(world, 0, 64, 0));
            manager.cleanup(); verify(task).cancel();
            delayed.getFirst().run();
            assertFalse(manager.isActive());
        }
    }
}
