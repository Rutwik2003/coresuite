package net.rocky.coresuite.module.grimoire;

import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;

final class MagicZone {
  static boolean contains(ConfigurationSection c, Location l) {
    if (l.getWorld() == null || !l.getWorld().getName().equals(c.getString("world"))) return false;
    double[] coordinates = {l.getX(), l.getY(), l.getZ()};
    String[] axes = {"x", "y", "z"};
    for (int i = 0; i < 3; i++) {
      if (!c.contains("min-" + axes[i]) || !c.contains("max-" + axes[i])) return false;
      double min = c.getDouble("min-" + axes[i]), max = c.getDouble("max-" + axes[i]);
      if (!Double.isFinite(min)
          || !Double.isFinite(max)
          || min > max
          || !Double.isFinite(coordinates[i])
          || coordinates[i] < min
          || coordinates[i] > max) return false;
    }
    return true;
  }
}
