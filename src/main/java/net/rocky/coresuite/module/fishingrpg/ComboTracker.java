package net.rocky.coresuite.module.fishingrpg;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ComboTracker {

    public enum CatchQuality {
        PERFECT("&a⚡ Perfect!", "&a"),
        GOOD("&e✓ Good", "&e"),
        NORMAL("&7OK", "&7"),
        SLOW("&c✗ Slow", "&c");

        private final String displayName;
        private final String colorCode;

        CatchQuality(String displayName, String colorCode) {
            this.displayName = displayName;
            this.colorCode = colorCode;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getColorCode() {
            return colorCode;
        }
    }

    private static class ComboState {
        int comboCount = 0;
        long lastCatchTimestamp = 0;
        long bobberSplashTimestamp = 0;
    }

    private final ConcurrentHashMap<UUID, ComboState> combos = new ConcurrentHashMap<>();

    public void onBobberSplash(UUID uuid) {
        ComboState state = combos.computeIfAbsent(uuid, k -> new ComboState());
        state.bobberSplashTimestamp = System.currentTimeMillis();
    }

    public CatchQuality onReel(UUID uuid) {
        ComboState state = combos.get(uuid);
        if (state == null) {
            return CatchQuality.NORMAL;
        }

        long currentTime = System.currentTimeMillis();

        // Auto-reset if more than 15 seconds have passed since the last catch
        if (state.lastCatchTimestamp > 0 && currentTime - state.lastCatchTimestamp > 15000) {
            state.comboCount = 0;
        }

        // Calculate reaction time
        long reactionTime = currentTime - state.bobberSplashTimestamp;
        state.lastCatchTimestamp = currentTime;
        state.bobberSplashTimestamp = 0;

        if (reactionTime <= 300) {
            state.comboCount++;
            return CatchQuality.PERFECT;
        } else if (reactionTime <= 800) {
            // Maintains combo but doesn't increment
            return CatchQuality.GOOD;
        } else if (reactionTime <= 2000) {
            state.comboCount = 0;
            return CatchQuality.NORMAL;
        } else {
            state.comboCount = 0;
            return CatchQuality.SLOW;
        }
    }

    public int getCombo(UUID uuid) {
        ComboState state = combos.get(uuid);
        if (state != null) {
            long currentTime = System.currentTimeMillis();
            if (state.lastCatchTimestamp > 0 && currentTime - state.lastCatchTimestamp > 15000) {
                state.comboCount = 0;
            }
            return state.comboCount;
        }
        return 0;
    }

    public double getComboMultiplier(UUID uuid) {
        int combo = getCombo(uuid);
        return Math.min(5.0, 1.0 + (combo * 0.1));
    }

    public void resetCombo(UUID uuid) {
        ComboState state = combos.get(uuid);
        if (state != null) {
            state.comboCount = 0;
        }
    }

    public void removePlayer(UUID uuid) {
        combos.remove(uuid);
    }
}
