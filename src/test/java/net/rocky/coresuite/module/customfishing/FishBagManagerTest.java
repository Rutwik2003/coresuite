package net.rocky.coresuite.module.customfishing;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.storage.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FishBagManagerTest {
    private final CoreSuitePlugin plugin = mock(CoreSuitePlugin.class);
    private final DatabaseManager database = mock(DatabaseManager.class);
    private final BukkitScheduler scheduler = mock(BukkitScheduler.class);
    private final List<Runnable> jobs = new ArrayList<>();
    private final List<Runnable> callbacks = new ArrayList<>();
    private final UUID uuid = UUID.randomUUID();

    private FishBagManager manager() throws Exception {
        when(plugin.getDatabaseManager()).thenReturn(database);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.isEnabled()).thenReturn(true);
        doAnswer(call -> { jobs.add(call.getArgument(0)); return null; }).when(database).executeAsync(any(Runnable.class));
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> { callbacks.add(call.getArgument(1)); return null; });
        Connection conn = mock(Connection.class);
        PreparedStatement stmt = mock(PreparedStatement.class);
        when(database.getConnection()).thenReturn(conn);
        when(conn.prepareStatement(anyString())).thenReturn(stmt);
        when(stmt.executeQuery()).thenReturn(mock(ResultSet.class));
        return new FishBagManager(plugin);
    }

    @Test void dataIsUnavailableUntilServerThreadAppliesLoad() throws Exception {
        FishBagManager manager = manager();
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            manager.loadBag(uuid);
            assertNull(manager.getBag(uuid));
            jobs.removeFirst().run();
            assertNull(manager.getBag(uuid));
            callbacks.removeFirst().run();
            assertEquals(450, manager.getBag(uuid).length);
        }
    }

    @Test void lateCallbackCannotRestoreLoggedOutBagOrReplaceNewSession() throws Exception {
        FishBagManager manager = manager();
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            manager.loadBag(uuid);
            jobs.removeFirst().run();
            manager.unloadBag(uuid);
            manager.loadBag(uuid);
            callbacks.removeFirst().run();
            assertNull(manager.getBag(uuid));
            jobs.removeFirst().run();
            callbacks.removeFirst().run();
            assertEquals(450, manager.getBag(uuid).length);
        }
    }

    @Test void databaseFailureNeverCreatesOrSavesAnEmptyReplacement() throws Exception {
        FishBagManager manager = manager();
        when(database.getConnection()).thenThrow(new SQLException("offline"));
        manager.loadBag(uuid);
        jobs.removeFirst().run();
        manager.updateBag(uuid, new ItemStack[450]);
        manager.unloadBag(uuid);
        assertNull(manager.getBag(uuid));
        assertTrue(jobs.isEmpty());
        assertTrue(callbacks.isEmpty());
    }

    @Test void corruptStoredSizeDoesNotBecomeASavableEmptyBag() throws Exception {
        FishBagManager manager = manager();
        Connection conn = database.getConnection();
        PreparedStatement stmt = conn.prepareStatement("unused");
        ResultSet rs = stmt.executeQuery();
        when(rs.next()).thenReturn(true);
        java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
        try (var output = new java.io.ObjectOutputStream(bytes)) { output.writeInt(Integer.MAX_VALUE); }
        when(rs.getString("inventory_data")).thenReturn(Base64.getEncoder().encodeToString(bytes.toByteArray()));
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            manager.loadBag(uuid);
            jobs.removeFirst().run();
            callbacks.removeFirst().run();
            manager.unloadBag(uuid);
            assertNull(manager.getBag(uuid));
            assertTrue(jobs.isEmpty());
        }
    }
}
