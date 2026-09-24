package net.rocky.coresuite.module.worldbosses.rewards;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.storage.DatabaseManager;
import net.rocky.coresuite.module.worldbosses.WorldBossesModule;
import net.rocky.coresuite.module.worldbosses.boss.BossType;
import org.bukkit.Bukkit;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BossDiscoveryPersistenceTest {
    @Test void sqliteSchemaAndRepeatedDiscoveryPersistWithoutReplacingFirstUnlock() throws Exception {
        var core = mock(CoreSuitePlugin.class);
        var db = mock(DatabaseManager.class);
        when(core.getDatabaseManager()).thenReturn(db);
        try (var conn = DriverManager.getConnection("jdbc:sqlite::memory:"); var bukkit = mockStatic(Bukkit.class)) {
            doAnswer(call -> {
                String sql = call.getArgument(0);
                Object[] params = (Object[]) call.getRawArguments()[1];
                try (var stmt = conn.prepareStatement(sql)) {
                    for (int i = 0; i < params.length; i++) stmt.setObject(i + 1, params[i]);
                    stmt.executeUpdate();
                }
                return null;
            }).when(db).execute(anyString(), any(Object[].class));
            doAnswer(call -> { ((Runnable) call.getArgument(0)).run(); return null; }).when(db).executeAsync(any());
            var module = new WorldBossesModule(core);
            var init = WorldBossesModule.class.getDeclaredMethod("initDatabase"); init.setAccessible(true); init.invoke(module);
            var discovery = new BossDiscoveryManager(core); discovery.initTable();
            var uuid = UUID.randomUUID();
            discovery.recordParticipation(uuid, BossType.VALERIUS);
            conn.createStatement().executeUpdate("UPDATE coresuite_boss_discoveries SET unlocked_at = 123");
            discovery.recordParticipation(uuid, BossType.VALERIUS);
            discovery.initTable(); // Backfill/reload must not duplicate or overwrite existing discovery.
            try (var result = conn.createStatement().executeQuery("SELECT kill_count, unlocked_at FROM coresuite_boss_discoveries")) {
                assertTrue(result.next()); assertEquals(2, result.getInt(1)); assertEquals(123, result.getLong(2));
                assertFalse(result.next());
            }
        }
    }
}
