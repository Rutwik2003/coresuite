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
}
