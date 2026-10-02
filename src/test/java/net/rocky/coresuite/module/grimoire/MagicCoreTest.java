package net.rocky.coresuite.module.grimoire;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Path;
import java.sql.*;
import java.util.UUID;
import net.rocky.coresuite.common.storage.DatabaseManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MagicCoreTest {
  @TempDir Path directory;

  private MagicStore store() throws Exception {
    var db = mock(DatabaseManager.class);
    String url = "jdbc:sqlite:" + directory.resolve("magic.db");
    when(db.getConnection()).thenAnswer(call -> DriverManager.getConnection(url));
    var store = new MagicStore(db);
    store.initialize();
    return store;
  }

  @Test
  void invalidCostNeverCreatesOrPoisonsMana() {
    var p = new MagicProfile();
    assertFalse(p.reserve("x", -1, 100, 1000, 500));
    assertFalse(p.reserve("x", Double.NaN, 100, 1000, 500));
    assertEquals(100, p.mana);
  }

  @Test
  void offlineBossResearchIsDurableAndNotDuplicated() throws Exception {
    var store = store();
    UUID id = UUID.randomUUID();
    store.award("kill-one", id, 3);
    store.award("kill-one", id, 3);
    store.load(id);
    assertEquals(3, store.collect(id).research);
    assertEquals(3, store.collect(id).research);
    store.award("kill-two", id, 2);
    assertEquals(5, store.collect(id).research);
  }

  @Test
  void pendingUpgradeSurvivesRestartAndIsAppliedOnce() throws Exception {
    var p = new MagicProfile();
    p.spells.put("mana-dart", 1);
    p.research = 20;
    p.pendingUpgrade = "spell:mana-dart";
    p.pendingResearch = 12;
    p.pendingCash = 100000;
    var saved = MagicProfile.decode(p.encode(), 0);
    GrimoireModule.completeUpgrade(saved);
    assertEquals(8, saved.research);
    assertEquals(2, saved.spells.get("mana-dart"));
    assertNull(saved.pendingUpgrade);
    GrimoireModule.completeUpgrade(saved);
    assertEquals(8, saved.research);
    assertEquals(2, saved.spells.get("mana-dart"));
  }

  @Test
  void duplicateTomeCannotGrantTwiceEvenToAnotherPlayer() throws Exception {
    var store = store();
    UUID first = UUID.randomUUID(), second = UUID.randomUUID();
    var a = store.load(first);
    var b = store.load(second);
    store.mint("seal", "mana-dart");
    a.spells.put("mana-dart", 1);
    store.redeem("seal", "mana-dart", first, a.revision, a.encode());
    assertThrows(
        SQLException.class,
        () -> store.redeem("seal", "mana-dart", second, b.revision, b.encode()));
    assertEquals(1, store.load(first).spells.get("mana-dart"));
    assertFalse(store.load(second).spells.containsKey("mana-dart"));
  }

  @Test
  void staleWriteRollsBackTomeConsumption() throws Exception {
    var store = store();
    UUID id = UUID.randomUUID();
    var p = store.load(id);
    store.save(id, 0, p.encode());
    store.mint("seal", "zoltraak");
    assertThrows(SQLException.class, () -> store.redeem("seal", "zoltraak", id, 0, p.encode()));
    var updated = store.load(id);
    updated.spells.put("zoltraak", 1);
    store.redeem("seal", "zoltraak", id, 1, updated.encode());
    assertTrue(store.load(id).spells.containsKey("zoltraak"));
  }

  @Test
  void snapshotPreservesManaCooldownsAndLoadout() throws Exception {
    var p = new MagicProfile();
    p.started = true;
    p.slots = 3;
    p.research = 9;
    p.spells.put("mana-dart", 2);
    p.prepared.add("mana-dart");
    assertTrue(p.reserve("mana-dart", 8, 1000, 1000, 500));
    assertFalse(p.reserve("mana-dart", 8, 1001, 1000, 500));
    var saved = MagicProfile.decode(p.encode(), 4);
    assertEquals(92, saved.mana);
    assertEquals(2000, saved.cooldowns.get("mana-dart"));
    assertEquals("mana-dart", saved.selectedSpell());
    assertEquals(9, saved.research);
  }

  @Test
  void regenerationWaitsAndCaps() {
    var p = new MagicProfile();
    p.mana = 98;
    p.lastCast = 1000;
    p.regenerate(2000, 100, 2);
    assertEquals(98, p.mana);
    p.regenerate(4000, 100, 20);
    assertEquals(100, p.mana);
  }

  @Test
  void tierScalingIsBoundedAndFinite() {
    var s = new SpellSpec("x", "x", org.bukkit.Material.BOOK, 10, 1000, 10, 20, true);
    assertEquals(13, s.tierDamage(3));
    assertEquals(13, s.tierDamage(999));
    assertEquals(10, SpellSpec.bounded(Double.NaN, 10, 1, 20));
  }
}
