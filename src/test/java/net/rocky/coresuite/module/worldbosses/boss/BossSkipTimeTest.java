package net.rocky.coresuite.module.worldbosses.boss;

import net.rocky.coresuite.module.worldbosses.scheduler.BossSpawnScheduler;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BossSkipTimeTest {
    @Test void durationsAndCountdownSubtraction() {
        assertEquals(1800, BossSpawnScheduler.parseDuration("30m"));
        assertEquals(3600, BossSpawnScheduler.parseDuration("1hr"));
        assertEquals(5400, BossSpawnScheduler.parseDuration("1h30m"));
        assertEquals(5415, BossSpawnScheduler.parseDuration(" 1 hour 30 minutes 15s "));
        var scheduler = new BossSpawnScheduler(null, null, new YamlConfiguration());
        scheduler.setSecondsRemaining(9000);
        assertEquals(7200, scheduler.skipTime(1800));
        assertEquals(1, scheduler.skipTime(Integer.MAX_VALUE));
    }
    @Test void rejectsInvalidAndOverflowingDurationsWithoutChangingTimer() {
        for (String invalid : new String[]{"", "0m", "-2hr", "30", "1.5h", "1h junk", "999999999999999999999h"})
            assertThrows(RuntimeException.class, () -> BossSpawnScheduler.parseDuration(invalid), invalid);
    }
}
