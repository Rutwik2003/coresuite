package net.rocky.coresuite.module.grimoire;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

public record SpellSpec(
    String id,
    String name,
    Material icon,
    double mana,
    long cooldown,
    double damage,
    double range,
    boolean arenaAllowed) {
  public static SpellSpec load(String id, ConfigurationSection c) {
    Material icon = Material.matchMaterial(c.getString("icon", "BOOK"));
    return new SpellSpec(
        id,
        c.getString("name", id),
        icon == null ? Material.BOOK : icon,
        bounded(c.getDouble("mana", 20), 20, 1, 100),
        (long) (bounded(c.getDouble("cooldown", 5), 5, .5, 300) * 1000),
        bounded(c.getDouble("damage", 4), 4, 0, 30),
        bounded(c.getDouble("range", 20), 20, 2, 40),
        c.getBoolean("arena", true));
  }

  static double bounded(double n, double fallback, double min, double max) {
    return Double.isFinite(n) ? Math.max(min, Math.min(max, n)) : fallback;
  }

  public double tierMana(int tier, double reduction) {
    double discount = bounded(reduction, .05, 0, .2);
    return Math.ceil(mana * (1 - discount * (Math.max(1, Math.min(3, tier)) - 1)) * 10) / 10;
  }

  public double tierDamage(int tier) {
    return damage * (1 + .15 * (Math.max(1, Math.min(3, tier)) - 1));
  }
}
