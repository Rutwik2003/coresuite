package net.rocky.coresuite.module.grimoire;

import static org.junit.jupiter.api.Assertions.*;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;

class TomeModelTest {
  @Test void everySpellHasADistinctStableModel() {
    var ids = new HashSet<Integer>();
    for (String spell : List.of("mana-dart", "fireball", "water-cannon", "frost-lance",
        "zoltraak", "dimension-door", "aura-of-the-hearth", "harvesters-touch")) {
      int id = GrimoireModule.tomeModel(spell);
      assertTrue(id >= 71010 && id <= 71017);
      assertTrue(ids.add(id));
    }
    assertEquals(71002, GrimoireModule.tomeModel("unknown"));
  }
}
