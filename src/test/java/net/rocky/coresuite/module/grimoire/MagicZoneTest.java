package net.rocky.coresuite.module.grimoire;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

class MagicZoneTest {
  @Test
  void zonesFailClosedForMissingInvalidOrDifferentWorldBounds() {
    var world = mock(World.class);
    when(world.getName()).thenReturn("duels");
    var zone = new YamlConfiguration();
    zone.set("world", "duels");
    var location = new Location(world, 5, 70, 5);
    assertFalse(MagicZone.contains(zone, location));
    for (String axis : new String[] {"x", "y", "z"}) {
      zone.set("min-" + axis, 0);
      zone.set("max-" + axis, 100);
    }
    assertTrue(MagicZone.contains(zone, location));
    zone.set("world", "world");
    assertFalse(MagicZone.contains(zone, location));
    zone.set("world", "duels");
    zone.set("min-x", 101);
    assertFalse(MagicZone.contains(zone, location));
    zone.set("min-x", Double.NaN);
    assertFalse(MagicZone.contains(zone, location));
  }
}
