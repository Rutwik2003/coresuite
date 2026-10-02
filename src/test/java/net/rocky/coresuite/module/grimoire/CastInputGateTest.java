package net.rocky.coresuite.module.grimoire;

import static org.junit.jupiter.api.Assertions.*;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CastInputGateTest {
  @Test void holdingBeyondSpellCooldownDoesNotRecast() {
    var gate = new CastInputGate();
    UUID id = UUID.randomUUID();
    assertTrue(gate.accept(id, 0, 350));
    for (long time = 200; time <= 10000; time += 200)
      assertFalse(gate.accept(id, time, 350));
    assertTrue(gate.accept(id, 10350, 350));
  }

  @Test void playersAndSessionsAreIndependent() {
    var gate = new CastInputGate();
    UUID id = UUID.randomUUID();
    assertTrue(gate.accept(id, 0, 350));
    assertTrue(gate.accept(UUID.randomUUID(), 1, 350));
    assertFalse(gate.accept(id, 1, 350));
    gate.remove(id);
    assertTrue(gate.accept(id, 2, 350));
    gate.clear();
    assertTrue(gate.accept(id, 3, 350));
  }
}
