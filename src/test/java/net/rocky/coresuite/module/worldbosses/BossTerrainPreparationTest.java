package net.rocky.coresuite.module.worldbosses;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.module.Module;
import net.rocky.coresuite.module.worldbosses.boss.BossType;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.scheduler.*;
import org.junit.jupiter.api.Test;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BossTerrainPreparationTest {
    private static WorldBossesModule enabledModule(CoreSuitePlugin core) throws Exception {
        var module = spy(new WorldBossesModule(core));
        var enabled = Module.class.getDeclaredField("enabled"); enabled.setAccessible(true); enabled.set(module,true);
        var configuration = Module.class.getDeclaredField("config"); configuration.setAccessible(true);
        var config = new YamlConfiguration(); config.set("domain.radius", 8);
        configuration.set(module, config);
        return module;
    }

    @Test void eachChunkIsPinnedBeforeOtherLoadsFinishAndTicketsTransferToBoss() throws Exception {
        var core = mock(CoreSuitePlugin.class);
        when(core.isEnabled()).thenReturn(true);
        var module = enabledModule(core);
        var world = mock(World.class);
        var loads = new java.util.ArrayList<CompletableFuture<Chunk>>();
        when(world.getChunkAtAsync(anyInt(),anyInt())).thenAnswer(call -> {
            var future = new CompletableFuture<Chunk>(); loads.add(future); return future;
        });
        var scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTaskLater(eq(core),any(Runnable.class),anyLong())).thenReturn(mock(BukkitTask.class));
        var scheduled = new java.util.ArrayList<Runnable>();
        when(scheduler.runTask(eq(core),any(Runnable.class))).thenAnswer(call -> {
            scheduled.add(call.getArgument(1)); return mock(BukkitTask.class);
        });
        var chunks = new java.util.ArrayList<Chunk>();
        var boss = mock(net.rocky.coresuite.module.worldbosses.boss.WorldBoss.class);
        var active = WorldBossesModule.class.getDeclaredField("activeBoss"); active.setAccessible(true);
        doAnswer(call -> {
            // Simulate the final validation after several ticks of loading. Without immediate
            // tickets an unoccupied arena could have unloaded before this point.
            for (var chunk : chunks) verify(chunk).addPluginChunkTicket(core);
            active.set(module,boss);
            return true;
        }).when(module).spawnBoss(eq(BossType.VALERIUS),any(Location.class));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            var result = module.spawnBossAsync(BossType.VALERIUS,new Location(world,49662,205,49528));
            assertTrue(loads.size()>1);
            for (var load : loads) {
                var chunk = mock(Chunk.class); chunks.add(chunk);
                when(chunk.addPluginChunkTicket(core)).thenReturn(true);
                load.complete(chunk);
                verify(chunk).addPluginChunkTicket(core); // pinned immediately, before allOf
                verify(chunk,never()).removePluginChunkTicket(core);
            }
            assertFalse(result.isDone());
            assertEquals(1,scheduled.size()); scheduled.getFirst().run();
            assertTrue(result.join()); assertFalse(module.isPreparingTerrain());
            verify(boss).retainPreparedChunks(argThat(held -> held.containsAll(chunks)));
            for (var chunk:chunks) verify(chunk,never()).removePluginChunkTicket(core);
            module.despawnActiveBoss();
            verify(boss).despawn();
            // The module no longer owns the transferred tickets.
            for (var chunk:chunks) verify(chunk,never()).removePluginChunkTicket(core);
            verify(world,never()).getChunkAt(anyInt(),anyInt());
        }
    }

    @Test void partialLoadsAreReleasedOnCancelAndLateCompletionsCannotPinAgain() throws Exception {
        var core=mock(CoreSuitePlugin.class); when(core.isEnabled()).thenReturn(true);
        var module=enabledModule(core); var world=mock(World.class);
        var loads=new java.util.ArrayList<CompletableFuture<Chunk>>();
        when(world.getChunkAtAsync(anyInt(),anyInt())).thenAnswer(call->{var f=new CompletableFuture<Chunk>();loads.add(f);return f;});
        var scheduler=mock(BukkitScheduler.class);
        when(scheduler.runTaskLater(eq(core),any(Runnable.class),anyLong())).thenReturn(mock(BukkitTask.class));
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            var result=module.spawnBossAsync(BossType.VALERIUS,new Location(world,0,80,0));
            var first=mock(Chunk.class);when(first.addPluginChunkTicket(core)).thenReturn(true);
            loads.getFirst().complete(first);verify(first).addPluginChunkTicket(core);
            module.despawnActiveBoss();assertFalse(result.join());verify(first).removePluginChunkTicket(core);
            var late=mock(Chunk.class);for(var f:loads)f.complete(late);
            verify(late,never()).addPluginChunkTicket(core);
            verify(module,never()).spawnBoss(any(),any());
        }
    }

    @Test void spawnRejectionReleasesPreparedChunksIncludingSharedTicketSafety() throws Exception {
        var core=mock(CoreSuitePlugin.class); when(core.isEnabled()).thenReturn(true);
        var module=enabledModule(core); var world=mock(World.class);
        var owned=mock(Chunk.class);when(owned.addPluginChunkTicket(core)).thenReturn(true);
        var shared=mock(Chunk.class); // false means another CoreSuite subsystem already owns it
        var count=new java.util.concurrent.atomic.AtomicInteger();
        when(world.getChunkAtAsync(anyInt(),anyInt())).thenAnswer(call->CompletableFuture.completedFuture(count.getAndIncrement()==0?shared:owned));
        var scheduler=mock(BukkitScheduler.class);
        when(scheduler.runTaskLater(eq(core),any(Runnable.class),anyLong())).thenReturn(mock(BukkitTask.class));
        when(scheduler.runTask(eq(core),any(Runnable.class))).thenAnswer(call->{((Runnable)call.getArgument(1)).run();return mock(BukkitTask.class);});
        doReturn(false).when(module).spawnBoss(any(),any());
        try(var bukkit=mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            assertFalse(module.spawnBossAsync(BossType.VALERIUS,new Location(world,0,80,0)).join());
            verify(owned).removePluginChunkTicket(core);
            verify(shared,never()).removePluginChunkTicket(core);
        }
    }

    @Test void asyncPreparationIsBoundedAndDespawnCancelsItWithoutLoadingChunksSynchronously() throws Exception {
        CoreSuitePlugin core = mock(CoreSuitePlugin.class);
        when(core.getLogger()).thenReturn(java.util.logging.Logger.getAnonymousLogger());
        when(core.isEnabled()).thenReturn(true);
        var module = new WorldBossesModule(core);
        var enabled = Module.class.getDeclaredField("enabled"); enabled.setAccessible(true); enabled.set(module,true);
        var configuration = Module.class.getDeclaredField("config"); configuration.setAccessible(true); configuration.set(module,new YamlConfiguration());
        World world = mock(World.class);
        var chunk = new CompletableFuture<Chunk>();
        when(world.getChunkAtAsync(anyInt(),anyInt())).thenReturn(chunk);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        BukkitTask timeout = mock(BukkitTask.class);
        when(scheduler.runTaskLater(eq(core),any(Runnable.class),anyLong())).thenReturn(timeout);
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::isPrimaryThread).thenReturn(true);
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            var result = module.spawnBossAsync(BossType.BOREALIS, new Location(world,0,80,0));
            assertTrue(module.isPreparingTerrain()); assertFalse(result.isDone());
            assertFalse(module.spawnBossAsync(BossType.VALERIUS,new Location(world,0,80,0)).join());
            verify(world,times(36)).getChunkAtAsync(anyInt(),anyInt());
            verify(world,never()).getChunkAt(anyInt(),anyInt());
            module.despawnActiveBoss();
            assertFalse(result.join()); assertFalse(module.isPreparingTerrain());
            verify(timeout).cancel();
            chunk.complete(mock(Chunk.class));
            verify(scheduler,never()).runTask(eq(core),any(Runnable.class));
        }
    }
}
