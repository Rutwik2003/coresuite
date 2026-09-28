package net.rocky.coresuite.common.storage;

import net.rocky.coresuite.CoreSuitePlugin;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DatabaseManagerTest {
    @Test @Timeout(15) void shutdownDrainsChildTasksWithoutRunningThemInsideTheParent() throws Exception {
        CoreSuitePlugin plugin = mock(CoreSuitePlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        when(plugin.getConfig()).thenReturn(new org.bukkit.configuration.file.YamlConfiguration());
        DatabaseManager database = new DatabaseManager(plugin);
        CountDownLatch parentStarted = new CountDownLatch(1);
        CountDownLatch releaseParent = new CountDownLatch(1);
        AtomicBoolean parentDone = new AtomicBoolean();
        AtomicBoolean childSawParentDone = new AtomicBoolean();
        AtomicBoolean childRan = new AtomicBoolean();
        database.executeAsync(() -> {
            parentStarted.countDown();
            try { releaseParent.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
            database.executeAsync(() -> { childSawParentDone.set(parentDone.get()); childRan.set(true); });
            parentDone.set(true);
        });
        assertTrue(parentStarted.await(2, TimeUnit.SECONDS));
        Thread closer = new Thread(database::close);
        closer.start();
        try {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (closer.getState() != Thread.State.TIMED_WAITING && System.nanoTime() < deadline) Thread.onSpinWait();
            assertEquals(Thread.State.TIMED_WAITING, closer.getState());
        } finally {
            releaseParent.countDown();
            closer.join(12000);
        }
        assertFalse(closer.isAlive());
        assertTrue(childRan.get()); assertTrue(childSawParentDone.get());
        assertThrows(RejectedExecutionException.class, () -> database.executeAsync(() -> {}));
    }
    @Test @Timeout(10) void failedJobDoesNotPreventFollowingSaveOrPoolShutdown() throws Exception {
        CoreSuitePlugin plugin = mock(CoreSuitePlugin.class);
        when(plugin.getLogger()).thenReturn(Logger.getAnonymousLogger());
        var config = new org.bukkit.configuration.file.YamlConfiguration();
        config.set("storage.database", "test.db");
        when(plugin.getConfig()).thenReturn(config);
        java.nio.file.Path folder = java.nio.file.Path.of("build", "db-tests", java.util.UUID.randomUUID().toString());
        java.nio.file.Files.createDirectories(folder);
        when(plugin.getDataFolder()).thenReturn(folder.toFile());
        DatabaseManager database = new DatabaseManager(plugin);
        database.initialize();
        try {
            database.executeAsync(() -> { throw new IllegalStateException("intentional regression test"); });
            database.execute("CREATE TABLE test (value INTEGER)");
            database.execute("INSERT INTO test VALUES (?)", 42);
        } finally {
            database.close();
        }
        assertThrows(java.sql.SQLException.class, database::getConnection);
        // Reopening proves a clean shutdown flushed queued writes to disk.
        try (var c = java.sql.DriverManager.getConnection("jdbc:sqlite:" + folder.resolve("test.db").toAbsolutePath());
             var stmt = c.createStatement(); var rows = stmt.executeQuery("SELECT value FROM test")) {
            assertTrue(rows.next()); assertEquals(42, rows.getInt(1));
        }
    }

}
