package net.rocky.coresuite.module.grimoire;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Main-thread input debounce. Every use packet extends the held-input window. */
final class CastInputGate {
  private final Map<UUID, Long> lastInput = new HashMap<>();

  boolean accept(UUID player, long now, long gap) {
    Long previous = lastInput.put(player, now);
    return previous == null || now - previous >= gap;
  }

  void remove(UUID player) {
    lastInput.remove(player);
  }

  void clear() {
    lastInput.clear();
  }
}
