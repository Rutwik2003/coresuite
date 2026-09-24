package net.rocky.coresuite.module.worldbosses.boss;

import net.rocky.coresuite.module.worldbosses.scheduler.BossSpawnScheduler;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FixedArenaProtectionTest {
    @Test void bypassOnlyAppliesToOptedOutFixedArena() {
        var config = new YamlConfiguration();
        World world = mock(World.class); when(world.getName()).thenReturn("world");
        Location center = new Location(world, 49662, 205, 49528);
        assertFalse(BossSpawnScheduler.bypassFixedArenaProtection(config, center));
        config.set("domain.anti-grief-protection", false);
        assertTrue(BossSpawnScheduler.bypassFixedArenaProtection(config, center));
        assertTrue(BossSpawnScheduler.bypassFixedArenaProtection(config, center.clone().add(.5, 0, .5)));
        assertFalse(BossSpawnScheduler.bypassFixedArenaProtection(config, center.clone().add(2, 0, 0)));
        assertFalse(BossSpawnScheduler.bypassFixedArenaProtection(config, center.clone().add(0, 2, 0)));
        when(world.getName()).thenReturn("another_world");
        assertFalse(BossSpawnScheduler.bypassFixedArenaProtection(config, center));
        when(world.getName()).thenReturn("world");
        config.set("arena.fixed-location.enabled", false);
        assertFalse(BossSpawnScheduler.bypassFixedArenaProtection(config, center));
    }
}
