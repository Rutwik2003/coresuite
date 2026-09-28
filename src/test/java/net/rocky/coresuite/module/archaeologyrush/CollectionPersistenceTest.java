package net.rocky.coresuite.module.archaeologyrush;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.storage.DatabaseManager;
import org.bukkit.*;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class CollectionPersistenceTest {
    @Test void staleMenuCannotRedeemTheSamePersistedFragmentsTwice() throws Exception {
        var path = java.nio.file.Path.of("build","db-tests",UUID.randomUUID()+".db");
        java.nio.file.Files.createDirectories(path.getParent());
        String url = "jdbc:sqlite:"+path.toAbsolutePath();
        UUID uuid = UUID.randomUUID();
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE coresuite_artifact_fragments (uuid TEXT,set_id TEXT,fragment_index INTEGER)");
            s.execute("CREATE TABLE coresuite_artifact_completions (uuid TEXT,player_name TEXT,set_id TEXT,completed_at BIGINT)");
            s.execute("INSERT INTO coresuite_artifact_fragments VALUES ('"+uuid+"','test',1)");
        }
        CoreSuitePlugin core = mock(CoreSuitePlugin.class);
        DatabaseManager db = mock(DatabaseManager.class);
        when(core.getDatabaseManager()).thenReturn(db);
        when(db.getConnection()).thenAnswer(call -> DriverManager.getConnection(url));
        doAnswer(call -> { ((Runnable)call.getArgument(0)).run(); return null; }).when(db).executeAsync(any());
        var manager = new PlayerCollectionManager(core);
        var set = new ArtifactSet("test","Test","",Material.BRUSH,
                List.of(new ArtifactFragment("test",1,"Piece",Material.PAPER)),100,List.of());
        List<Runnable> payouts = new ArrayList<>();
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        when(scheduler.runTask(eq(core),any(Runnable.class))).thenAnswer(call -> { payouts.add(call.getArgument(1)); return null; });
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            manager.completeSet(uuid,"Alice",set,() -> {});
            manager.completeSet(uuid,"Alice",set,() -> {});
            assertEquals(1,payouts.size());
        }
        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement(); ResultSet r = s.executeQuery("SELECT COUNT(*) FROM coresuite_artifact_completions")) {
            assertTrue(r.next()); assertEquals(1,r.getInt(1));
        }
    }
}
