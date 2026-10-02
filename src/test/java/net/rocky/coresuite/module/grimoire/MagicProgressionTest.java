package net.rocky.coresuite.module.grimoire;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MagicProgressionTest {
  @Test
  void fullUpgradeIsPersistableAndConsumesExactlyOnce() throws Exception {
    var p = new MagicProfile();
    p.research = 30;
    p.spells.put("fireball", 1);
    p.shards.put("fire", 7);
    p.duplicates.put("fireball", 2);
    p.pendingUpgrade = "spell:fireball";
    p.pendingResearch = 12;
    p.pendingCash = 100000;
    p.pendingShards.put("fire", 5);
    p.pendingDuplicate = "fireball";
    p.pendingDuplicateCount = 1;
    p.pendingMaterials.put("OAK_LOG", 64);
    p.pendingFish.put("atlantic_cod", 3);
    p.pendingStock = "visit:fireball";
    p.pendingStockLimit = 3;
    var saved = MagicProfile.decode(p.encode(), 4);
    assertEquals(p.pendingFish, saved.pendingFish);
    MagicProgression.complete(saved);
    MagicProgression.complete(saved);
    assertEquals(2, saved.spells.get("fireball"));
    assertEquals(18, saved.research);
    assertEquals(2, saved.shards.get("fire"));
    assertEquals(1, saved.duplicates.get("fireball"));
    assertNull(saved.pendingUpgrade);
    assertTrue(saved.pendingFish.isEmpty());
  }

  @Test
  void insufficientResourcesDoNotPartiallyDeduct() {
    var p = new MagicProfile();
    p.pendingUpgrade = "wand";
    p.pendingResearch = 10;
    p.research = 20;
    p.pendingShards.put("ice", 25);
    p.shards.put("ice", 24);
    assertThrows(IllegalStateException.class, () -> MagicProgression.complete(p));
    assertEquals(20, p.research);
    assertEquals(24, p.shards.get("ice"));
    assertFalse(p.wand);
  }

  @Test
  void contractAndWandStateSurviveSave() throws Exception {
    var p = new MagicProfile();
    p.wand = true;
    p.magicEnabled = false;
    p.activeContract = "broken-winter";
    p.contractProgress.put("BOSS:BOREALIS", 1);
    p.contractClaims.put("first-sparks", 123L);
    var copy = MagicProfile.decode(p.encode(), 1);
    assertTrue(copy.wand);
    assertFalse(copy.magicEnabled);
    assertEquals(p.contractProgress, copy.contractProgress);
    assertEquals(p.contractClaims, copy.contractClaims);
    assertEquals(p.activeContract, copy.activeContract);
  }

  @Test
  void oldProfilesStillLoadAndRecipesDoNotBecomeFree() throws Exception {
    var p = MagicProfile.decode("mana: 40\nstarted: true\n", 0);
    assertTrue(p.magicEnabled);
    assertFalse(p.wand);
    assertTrue(p.shards.isEmpty());
    var balance = new java.util.HashMap<>(Map.of("fire", 4));
    assertThrows(
        IllegalStateException.class, () -> MagicProgression.debit(balance, Map.of("fire", 5)));
    assertEquals(4, balance.get("fire"));
  }

  @Test
  void signatureInscriptionUnlocksThenGrantsDuplicateCredit() {
    var p = new MagicProfile();
    p.pendingUpgrade = "inscribe:zoltraak";
    MagicProgression.complete(p);
    assertEquals(1, p.spells.get("zoltraak"));
    p.pendingUpgrade = "inscribe:zoltraak";
    MagicProgression.complete(p);
    assertEquals(1, p.duplicates.get("zoltraak"));
  }
}
