package net.rocky.coresuite.module.customfishing;

import java.util.*;

/** A repeatable offer derived from persisted quest history, never from click count. */
record AnglerIntroOffer(int scenario, List<Integer> paths) {
    static AnglerIntroOffer create(UUID player, int completed, int disrespected, int scenarios) {
        long seed = player.getMostSignificantBits() ^ Long.rotateLeft(player.getLeastSignificantBits(), 17)
                ^ (0x9E3779B97F4A7C15L * completed) ^ (0xD1B54A32D192ED03L * disrespected);
        var random = new Random(seed);
        int scenario = random.nextInt(scenarios);
        var paths = new ArrayList<>(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        Collections.shuffle(paths, random);
        return new AnglerIntroOffer(scenario, List.copyOf(paths.subList(0, 3)));
    }
}
