package net.rocky.coresuite.module.grimoire;

import java.util.*;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

/** Exact species matching across inventory and unlocked bag pages, no fuzzy name checks. */
final class MagicFishPayment {
  record Result(ItemStack[] inventory, ItemStack[] bag, boolean bagChanged) {}

  static Result plan(
      ItemStack[] inventory,
      ItemStack[] bag,
      int unlockedSlots,
      NamespacedKey speciesKey,
      Map<String, Integer> costs) {
    ItemStack[] inv = copy(inventory), stored = bag == null ? null : copy(bag);
    boolean changed = false;
    for (var cost : costs.entrySet()) {
      int remaining = cost.getValue();
      for (int source = 0; source < 2; source++) {
        ItemStack[] items = source == 0 ? inv : stored;
        if (items == null) continue;
        int limit = source == 0 ? items.length : Math.min(items.length, Math.max(0, unlockedSlots));
        for (int i = 0; i < limit && remaining > 0; i++) {
          ItemStack fish = items[i];
          if (fish == null
              || !fish.hasItemMeta()
              || !cost.getKey()
                  .equals(
                      fish.getItemMeta()
                          .getPersistentDataContainer()
                          .get(speciesKey, PersistentDataType.STRING))) continue;
          int take = Math.min(remaining, fish.getAmount());
          remaining -= take;
          if (source == 1) changed = true;
          fish.setAmount(fish.getAmount() - take);
          if (fish.getAmount() == 0) items[i] = null;
        }
      }
      if (remaining > 0) return null;
    }
    return new Result(inv, stored, changed);
  }

  private static ItemStack[] copy(ItemStack[] items) {
    return Arrays.stream(items).map(i -> i == null ? null : i.clone()).toArray(ItemStack[]::new);
  }
}
