package net.rocky.coresuite.module.playtimerewards;
import org.junit.jupiter.api.Test;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
class PlaytimeDefaultsTest {
    @Test void extendedRewardsAreConfigurableModestAndOneTime() throws Exception {
        try (var stream = getClass().getResourceAsStream("/modules/playtime-rewards.yml")) {
            assertNotNull(stream);
            var config = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            int count = 0;
            for (String id : config.getConfigurationSection("rewards").getKeys(false)) {
                var reward = config.getConfigurationSection("rewards." + id);
                if (reward.getInt("required-seconds") <= 300 * 3600) continue;
                count++;
                assertEquals(0,reward.getInt("cooldown-hours"));
                assertTrue(reward.getDouble("money") > 0 && reward.getDouble("money") <= 250000);
                assertFalse(reward.getStringList("commands").isEmpty());
                assertTrue(reward.getStringList("commands").stream().anyMatch(cmd -> cmd.startsWith("uc give key ") && cmd.endsWith(" %player_name%")));
            }
            assertEquals(9,count);
        }
    }
    @Test void upgradesStockCommandsOnceAndPreservesCustomRewards() {
        var config = new YamlConfiguration();
        config.set("rewards.reward_350h.commands", java.util.List.of("minecraft:experience add {player} 750 points"));
        config.set("rewards.reward_350h.money",123);
        config.set("rewards.reward_350h.grants","Custom description");
        config.set("rewards.reward_400h.commands",java.util.List.of("say custom"));
        assertTrue(PlaytimeRewardDefaults.upgrade(config));
        assertTrue(config.getStringList("rewards.reward_350h.commands").contains("uc give key 1 %player_name%"));
        assertEquals(123,config.getInt("rewards.reward_350h.money"));
        assertEquals("Custom description",config.getString("rewards.reward_350h.grants"));
        assertEquals(java.util.List.of("say custom"),config.getStringList("rewards.reward_400h.commands"));
        assertFalse(PlaytimeRewardDefaults.upgrade(config));
        assertEquals("uc give key 1 Rocky_rutwik",PlaytimeRewardDefaults.commandFor("uc give key 1 %player_name%","Rocky_rutwik"));
    }

}
