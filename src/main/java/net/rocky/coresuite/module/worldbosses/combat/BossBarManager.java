package net.rocky.coresuite.module.worldbosses.combat;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.worldbosses.boss.BossPhase;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class BossBarManager {

    private final BossBar bossBar;
    private final Set<Player> viewers = ConcurrentHashMap.newKeySet();

    public BossBarManager(String initialTitle, BossBar.Color color) {
        this(initialTitle, color, true, true);
    }

    public BossBarManager(String initialTitle, BossBar.Color color, boolean darkenScreen, boolean createFog) {
        java.util.Set<BossBar.Flag> flags = new java.util.HashSet<>();
        if (darkenScreen) {
            flags.add(BossBar.Flag.DARKEN_SCREEN);
        }
        if (createFog) {
            flags.add(BossBar.Flag.CREATE_WORLD_FOG);
        }
        this.bossBar = BossBar.bossBar(
            TextUtil.colorize(initialTitle),
            1.0f,
            color,
            BossBar.Overlay.PROGRESS,
            flags
        );
    }

    public void update(double currentHp, double maxHp, BossPhase phase, String bossDisplayName) {
        float progress = (float) Math.max(0.0, Math.min(1.0, currentHp / maxHp));
        bossBar.progress(progress);
        if (phase != null && phase.getBarColor() != null) {
            bossBar.color(phase.getBarColor());
        }

        int percent = (int) Math.ceil(progress * 100);
        String titleStr = bossDisplayName + " &8| &ePhase " + (phase != null ? phase.getPhaseNumber() : 1) + " &8(&c" + String.format("%.0f", currentHp) + "&7/&c" + String.format("%.0f", maxHp) + " &7- &a" + percent + "%&8)";
        bossBar.name(TextUtil.colorize(titleStr));
    }

    public void addViewer(Player player) {
        if (player != null && viewers.add(player)) {
            player.showBossBar(bossBar);
        }
    }

    public void removeViewer(Player player) {
        if (player != null && viewers.remove(player)) {
            player.hideBossBar(bossBar);
        }
    }

    public void removeAll() {
        for (Player player : viewers) {
            if (player != null && player.isOnline()) {
                player.hideBossBar(bossBar);
            }
        }
        viewers.clear();
    }

    public Set<Player> getViewers() {
        return Collections.unmodifiableSet(viewers);
    }

    public BossBar getBossBar() {
        return bossBar;
    }
}
