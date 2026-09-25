package net.rocky.coresuite.module.worldbosses.combat;
import org.bukkit.configuration.file.FileConfiguration;

/** Migrate only the shipped soundtrack settings; retain custom music/timing. */
public final class BossMusicDefaults {
    private BossMusicDefaults() {}
    public static boolean upgrade(FileConfiguration config) {
        if (config.getBoolean("balance.soundtrack-v2-upgraded", false)) return false;
        String[] names = {"valerius", "aethelgard", "xolthos"};
        int[][] old = {{375, 200, 375, 165}, {155, 259, 155, 334}, {169, 169, 169, 423}};
        for (int boss = 0; boss < names.length; boss++) for (int phase = 0; phase <= 3; phase++) {
            String key = names[boss] + (phase == 0 ? ".bgm" : ".bgm-p" + phase);
            String expected = "coresuite:boss." + names[boss] + (phase == 0 ? "" : ".p" + phase);
            if (expected.equals(config.getString(key)) && config.getInt(key + "-duration-seconds") == old[boss][phase])
                config.set(key + "-duration-seconds", old[boss][phase] + 1);
        }
        if ("minecraft:music_disc.otherside".equals(config.getString("borealis.bgm"))) {
            config.set("borealis.bgm", "coresuite:boss.borealis");
            config.set("borealis.bgm-duration-seconds", 64);
            for (int phase = 1; phase <= 3; phase++) {
                if (!config.contains("borealis.bgm-p" + phase)) {
                    config.set("borealis.bgm-p" + phase, "coresuite:boss.borealis.p" + phase);
                    config.set("borealis.bgm-p" + phase + "-duration-seconds", new int[]{80,64,48}[phase-1]);
                }
            }
        }
        if ("minecraft:ambient.basalt_deltas.loop".equals(config.getString("borealis.awakening-bgm")))
            config.set("borealis.awakening-bgm", "coresuite:boss.awakening");
        config.set("balance.soundtrack-v2-upgraded", true);
        return true;
    }
}
