package net.rocky.coresuite.module.grimoire;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.sql.*;
import java.util.UUID;
import net.rocky.coresuite.common.storage.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MagicStockTest {
  @TempDir Path directory;

  MagicStore store() throws Exception {
    var db = mock(DatabaseManager.class);
    when(db.getConnection())
        .thenAnswer(
            c -> DriverManager.getConnection("jdbc:sqlite:" + directory.resolve("magic.db")));
    var store = new MagicStore(db);
    store.initialize();
    return store;
  }

  @Test
  void stockIsSharedAndSurvivesAStoreRestart() throws Exception {
    var store = store();
    UUID a = UUID.randomUUID(), b = UUID.randomUUID();
    var first = store.load(a);
    var second = store.load(b);
    first.pendingUpgrade = "learn:zoltraak";
    store.saveWithStock(a, 0, first.encode(), "same-visit", 1);
    var reopened = store();
    assertThrows(
        SQLException.class, () -> reopened.saveWithStock(b, 0, second.encode(), "same-visit", 1));
    assertEquals(0, reopened.load(b).revision);
    reopened.saveWithStock(b, 0, second.encode(), "new-visit", 1);
    assertEquals(1, reopened.load(b).revision);
  }

  @Test
  void failedProfileSaveRollsBackStockReservation() throws Exception {
    var store = store();
    UUID id = UUID.randomUUID();
    var p = store.load(id);
    assertThrows(SQLException.class, () -> store.saveWithStock(id, 99, p.encode(), "stock", 1));
    store.saveWithStock(id, 0, p.encode(), "stock", 1);
    assertEquals(1, store.load(id).revision);
  }

  @Test
  void offlineShardReceiptsCannotBeClaimedTwice() throws Exception {
    var store = store();
    UUID id = UUID.randomUUID();
    store.load(id);
    store.awardShard("boss-player-element", id, "ice", 3);
    store.awardShard("boss-player-element", id, "ice", 3);
    assertEquals(3, store.collect(id).shards.get("ice"));
    assertEquals(3, store.collect(id).shards.get("ice"));
    assertEquals(3, store().load(id).shards.get("ice"));
  }

  @Test
  void bossBundleAndLiveManaAreCollectedAtomicallyOnce() throws Exception {
    var store = store();
    UUID id = UUID.randomUUID();
    var state = store.load(id);
    assertTrue(store.awardBossBundle("kill:player", id, 3, "arcane", 3, "zoltraak-fragment"));
    assertFalse(store.awardBossBundle("kill:player", id, 3, "arcane", 3, "zoltraak-fragment"));
    state.mana = 27;
    var result = store.collect(id, state.encode(), state.revision);
    assertEquals(27, result.mana);
    assertEquals(3, result.research);
    assertEquals(3, result.shards.get("arcane"));
    assertEquals(1, result.shards.get("zoltraak-fragment"));
    assertEquals(1, store.collect(id).shards.get("zoltraak-fragment"));
  }
}
