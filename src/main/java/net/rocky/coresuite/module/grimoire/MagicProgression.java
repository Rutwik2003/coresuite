package net.rocky.coresuite.module.grimoire;

import java.util.*;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

/** Pure progression rules. External payments are journaled before debiting anything. */
final class MagicProgression {
  static final Set<String> ELEMENTS = Set.of("fire", "ice", "arcane", "nature");

  static String element(String spell) {
    return switch (spell) {
      case "fireball" -> "fire";
      case "frost-lance", "water-cannon" -> "ice";
      case "harvesters-touch", "aura-of-the-hearth" -> "nature";
      default -> "arcane";
    };
  }

  static Map<String, Integer> costs(ConfigurationSection c, String path) {
    Map<String, Integer> values = new HashMap<>();
    var section = c.getConfigurationSection(path);
    if (section != null)
      for (String key : section.getKeys(false)) {
        int n = section.getInt(key);
        if (n < 0 || n > 100000)
          throw new IllegalArgumentException("Invalid cost: " + path + "." + key);
        if (n > 0) values.put(key, n);
      }
    return values;
  }

  static boolean has(Map<String, Integer> balance, Map<String, Integer> cost) {
    return cost.entrySet().stream()
        .allMatch(e -> balance.getOrDefault(e.getKey(), 0) >= e.getValue());
  }

  static void debit(Map<String, Integer> balance, Map<String, Integer> cost) {
    if (!has(balance, cost)) throw new IllegalStateException("Insufficient saved resources");
    cost.forEach((k, v) -> balance.put(k, balance.getOrDefault(k, 0) - v));
  }

  static boolean hasMaterials(ItemStack[] inventory, Map<String, Integer> cost) {
    Map<String, Integer> counts = new HashMap<>();
    for (ItemStack item : inventory)
      if (item != null && !item.hasItemMeta())
        counts.merge(item.getType().name(), item.getAmount(), Integer::sum);
    return has(counts, cost);
  }

  static ItemStack[] debitMaterials(ItemStack[] inventory, Map<String, Integer> cost) {
    if (!hasMaterials(inventory, cost))
      throw new IllegalStateException("Materials changed before payment");
    ItemStack[] result =
        Arrays.stream(inventory).map(i -> i == null ? null : i.clone()).toArray(ItemStack[]::new);
    for (var entry : cost.entrySet()) {
      Material material = Material.matchMaterial(entry.getKey());
      if (material == null)
        throw new IllegalArgumentException("Unknown material: " + entry.getKey());
      int remaining = entry.getValue();
      for (int i = 0; i < result.length && remaining > 0; i++) {
        var item = result[i];
        if (item == null || item.getType() != material || item.hasItemMeta()) continue;
        int take = Math.min(remaining, item.getAmount());
        remaining -= take;
        item.setAmount(item.getAmount() - take);
        if (item.getAmount() == 0) result[i] = null;
      }
    }
    return result;
  }

  static void complete(MagicProfile p) {
    if (p.pendingUpgrade == null) return;
    if (p.research < p.pendingResearch
        || !has(p.shards, p.pendingShards)
        || p.duplicates.getOrDefault(p.pendingDuplicate, 0) < p.pendingDuplicateCount)
      throw new IllegalStateException("Reserved progression resources changed");
    String action = p.pendingUpgrade;
    if (!(action.equals("page")
        || action.equals("wand")
        || action.startsWith("spell:")
        || action.startsWith("learn:")
        || action.startsWith("inscribe:")))
      throw new IllegalStateException("Unknown purchase action");
    p.research -= p.pendingResearch;
    debit(p.shards, p.pendingShards);
    if (p.pendingDuplicateCount > 0)
      p.duplicates.merge(p.pendingDuplicate, -p.pendingDuplicateCount, Integer::sum);
    if (action.equals("page")) p.slots = Math.min(5, p.slots + 1);
    else if (action.equals("wand")) p.wand = true;
    else if (action.startsWith("learn:")) p.spells.putIfAbsent(action.substring(6), 1);
    else if (action.startsWith("inscribe:")) {
      String id = action.substring(9);
      if (p.spells.containsKey(id)) p.duplicates.merge(id, 1, (a, b) -> Math.min(100000, a + b));
      else p.spells.put(id, 1);
    } else p.spells.computeIfPresent(action.substring(6), (id, tier) -> Math.min(3, tier + 1));
    clearPending(p);
  }

  static void clearPending(MagicProfile p) {
    p.pendingUpgrade = null;
    p.pendingResearch = 0;
    p.pendingCash = 0;
    p.pendingMaterials.clear();
    p.pendingShards.clear();
    p.pendingFish.clear();
    p.pendingStock = "";
    p.pendingStockLimit = 0;
    p.pendingDuplicate = "";
    p.pendingDuplicateCount = 0;
  }
}
