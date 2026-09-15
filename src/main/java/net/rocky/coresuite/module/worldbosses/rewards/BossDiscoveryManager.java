package net.rocky.coresuite.module.worldbosses.rewards;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.worldbosses.boss.BossType;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BossDiscoveryManager {

    private final CoreSuitePlugin plugin;
    private final Map<UUID, Set<BossType>> playerDiscoveries = new ConcurrentHashMap<>();

    public BossDiscoveryManager(CoreSuitePlugin plugin) {
        this.plugin = plugin;
    }

    public void initTable() {
        plugin.getDatabaseManager().execute(
            "CREATE TABLE IF NOT EXISTS coresuite_boss_discoveries (" +
            "uuid TEXT NOT NULL, " +
            "boss_type TEXT NOT NULL, " +
            "unlocked_at BIGINT NOT NULL, " +
            "kill_count INTEGER DEFAULT 1, " +
            "PRIMARY KEY (uuid, boss_type)" +
            ");"
        );

        // Seed / backfill historical MVP kills if available
        plugin.getDatabaseManager().execute(
            "INSERT OR IGNORE INTO coresuite_boss_discoveries (uuid, boss_type, unlocked_at, kill_count) " +
            "SELECT mvp_uuid, boss_type, kill_time, 1 FROM coresuite_boss_kills WHERE mvp_uuid IS NOT NULL;"
        );
    }

    public void loadPlayer(UUID uuid) {
        if (uuid == null) return;
        plugin.getDatabaseManager().executeAsync(() -> {
            Set<BossType> set = ConcurrentHashMap.newKeySet();
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT boss_type FROM coresuite_boss_discoveries WHERE uuid = ?")) {
                stmt.setString(1, uuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        try {
                            set.add(BossType.valueOf(rs.getString("boss_type").toUpperCase()));
                        } catch (IllegalArgumentException ignored) {}
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("[WorldBosses] Failed to load boss discoveries for " + uuid + ": " + e.getMessage());
            }
            playerDiscoveries.compute(uuid, (key, existing) -> {
                if (existing == null) return set;
                existing.addAll(set);
                return existing;
            });
        });
    }

    public void unloadPlayer(UUID uuid) {
        if (uuid != null) {
            playerDiscoveries.remove(uuid);
        }
    }

    public boolean hasDiscovered(UUID uuid, BossType type) {
        if (uuid == null || type == null) return false;
        Set<BossType> set = playerDiscoveries.get(uuid);
        if (set != null) {
            return set.contains(type);
        }

        // Synchronous fallback query if player is not cached yet
        Set<BossType> fallback = ConcurrentHashMap.newKeySet();
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT boss_type FROM coresuite_boss_discoveries WHERE uuid = ?")) {
            stmt.setString(1, uuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    try {
                        fallback.add(BossType.valueOf(rs.getString("boss_type").toUpperCase()));
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("[WorldBosses] Error checking boss discovery for " + uuid + ": " + e.getMessage());
        }
        playerDiscoveries.put(uuid, fallback);
        return fallback.contains(type);
    }

    public void recordParticipation(UUID uuid, BossType type) {
        if (uuid == null || type == null) return;
        Set<BossType> set = playerDiscoveries.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());
        boolean isNewUnlock = set.add(type);

        long now = System.currentTimeMillis();
        plugin.getDatabaseManager().executeAsync(() -> {
            plugin.getDatabaseManager().execute(
                "INSERT INTO coresuite_boss_discoveries (uuid, boss_type, unlocked_at, kill_count) VALUES (?, ?, ?, 1) " +
                "ON CONFLICT(uuid, boss_type) DO UPDATE SET kill_count = kill_count + 1",
                uuid.toString(), type.name(), now
            );
        });

        if (isNewUnlock) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                TextUtil.sendMessage(player, "&6&l✦ CODEX UNLOCKED! &7You have unlocked &e" + type.getDefaultDisplayName() + " &7in your &6/boss &7Hunting Codex!");
            }
        }
    }
}
