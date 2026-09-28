package net.rocky.coresuite.module.playtimerewards;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

/** Upgrade only the exact cash/XP defaults shipped previously; keep custom rewards and claim IDs. */
final class PlaytimeRewardDefaults {
    static boolean upgrade(FileConfiguration config) {
        try (var stream = PlaytimeRewardDefaults.class.getResourceAsStream("/modules/playtime-rewards.yml")) {
            if (stream == null) throw new IllegalStateException("Missing playtime defaults");
            var defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(stream, StandardCharsets.UTF_8));
            int[][] milestones = {{350,40000,750},{400,50000,1000},{500,75000,1500},{600,100000,2000},
                    {750,125000,2500},{1000,150000,3000},{1250,175000,4000},{1500,200000,5000},{2000,250000,6000}};
            boolean changed = false;
            for (int[] milestone : milestones) {
                String path = "rewards.reward_" + milestone[0] + "h.";
                if (!config.getStringList(path + "commands").equals(List.of("minecraft:experience add {player} " + milestone[2] + " points"))) continue;
                config.set(path + "commands", defaults.getStringList(path + "commands"));
                String money = String.format(Locale.US, "%,d", milestone[1]);
                String xp = String.format(Locale.US, "%,d", milestone[2]);
                if (("&f$" + money + " &7+ &f" + xp + " XP points").equals(config.getString(path + "grants")))
                    config.set(path + "grants", defaults.getString(path + "grants"));
                if (("&aClaimed: $" + money + " and " + xp + " XP points!").equals(config.getString(path + "claim-message")))
                    config.set(path + "claim-message", defaults.getString(path + "claim-message"));
                changed = true;
            }
            return changed;
        } catch (java.io.IOException error) {
            throw new IllegalStateException("Cannot read playtime defaults", error);
        }
    }
    static String commandFor(String command, String playerName) {
        return command.replace("{player}", playerName).replace("%player_name%", playerName);
    }
    private PlaytimeRewardDefaults() {}
}
