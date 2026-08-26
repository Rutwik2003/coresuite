package net.rocky.coresuite.module.fishingrpg;

import net.rocky.coresuite.CoreSuitePlugin;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class AnglerProfileManager {
    private final CoreSuitePlugin plugin;
    private final ConcurrentHashMap<UUID, AnglerProfile> cache = new ConcurrentHashMap<>();

    public AnglerProfileManager(CoreSuitePlugin plugin) {
        this.plugin = plugin;
        initDatabase();
    }

    private void initDatabase() {
        String sql = """
            CREATE TABLE IF NOT EXISTS fishingrpg_profiles (
                uuid TEXT PRIMARY KEY,
                level INTEGER DEFAULT 1,
                xp INTEGER DEFAULT 0,
                total_catches INTEGER DEFAULT 0,
                bag_pages INTEGER DEFAULT 0,
                fatigue_score REAL DEFAULT 0,
                fatigue_last_update BIGINT DEFAULT 0,
                legendary_pity INTEGER DEFAULT 0,
                skill_points INTEGER DEFAULT 0,
                hunter_points INTEGER DEFAULT 0,
                scavenger_points INTEGER DEFAULT 0,
                mystic_points INTEGER DEFAULT 0
            )
        """;
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize fishingrpg_profiles table", e);
        }
    }

    public void loadPlayer(UUID uuid) {
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement("SELECT * FROM fishingrpg_profiles WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        AnglerProfile profile = new AnglerProfile(uuid);
                        profile.setLevel(rs.getInt("level"));
                        profile.setXp(rs.getInt("xp"));
                        profile.setTotalCatches(rs.getInt("total_catches"));
                        profile.setBagPages(rs.getInt("bag_pages"));
                        profile.setFatigueScore(rs.getDouble("fatigue_score"));
                        profile.setFatigueLastUpdate(rs.getLong("fatigue_last_update"));
                        profile.setLegendaryPity(rs.getInt("legendary_pity"));
                        profile.setSkillPointsAvailable(rs.getInt("skill_points"));
                        profile.setHunterPoints(rs.getInt("hunter_points"));
                        profile.setScavengerPoints(rs.getInt("scavenger_points"));
                        profile.setMysticPoints(rs.getInt("mystic_points"));
                        profile.setDirty(false);
                        cache.put(uuid, profile);
                    } else {
                        insertDefaultProfile(conn, uuid);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load AnglerProfile for " + uuid, e);
            }
        });
    }

    private void insertDefaultProfile(Connection conn, UUID uuid) {
        String sql = "INSERT INTO fishingrpg_profiles (uuid) VALUES (?)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.executeUpdate();
            AnglerProfile profile = new AnglerProfile(uuid);
            cache.put(uuid, profile);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to insert default AnglerProfile for " + uuid, e);
        }
    }

    public void unloadPlayer(UUID uuid) {
        AnglerProfile profile = cache.remove(uuid);
        if (profile != null && profile.isDirty()) {
            saveSync(profile);
        }
    }

    public AnglerProfile getProfile(UUID uuid) {
        return cache.computeIfAbsent(uuid, AnglerProfile::new);
    }

    public void saveProfile(UUID uuid) {
        AnglerProfile profile = cache.get(uuid);
        if (profile != null && profile.isDirty()) {
            plugin.getDatabaseManager().executeAsync(() -> saveSync(profile));
        }
    }

    public void saveAll() {
        for (AnglerProfile profile : cache.values()) {
            if (profile.isDirty()) {
                saveSync(profile);
            }
        }
    }

    public void shutdown() {
        saveAll();
        cache.clear();
    }

    private void saveSync(AnglerProfile profile) {
        String sql = """
            INSERT INTO fishingrpg_profiles (
                uuid, level, xp, total_catches, bag_pages, fatigue_score, fatigue_last_update,
                legendary_pity, skill_points, hunter_points, scavenger_points, mystic_points
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT(uuid) DO UPDATE SET
                level = excluded.level,
                xp = excluded.xp,
                total_catches = excluded.total_catches,
                bag_pages = excluded.bag_pages,
                fatigue_score = excluded.fatigue_score,
                fatigue_last_update = excluded.fatigue_last_update,
                legendary_pity = excluded.legendary_pity,
                skill_points = excluded.skill_points,
                hunter_points = excluded.hunter_points,
                scavenger_points = excluded.scavenger_points,
                mystic_points = excluded.mystic_points
        """;
        try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
             java.sql.PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, profile.getUuid().toString());
            ps.setInt(2, profile.getLevel());
            ps.setInt(3, profile.getXp());
            ps.setInt(4, profile.getTotalCatches());
            ps.setInt(5, profile.getBagPages());
            ps.setDouble(6, profile.getFatigueScore());
            ps.setLong(7, profile.getFatigueLastUpdate());
            ps.setInt(8, profile.getLegendaryPity());
            ps.setInt(9, profile.getSkillPointsAvailable());
            ps.setInt(10, profile.getHunterPoints());
            ps.setInt(11, profile.getScavengerPoints());
            ps.setInt(12, profile.getMysticPoints());
            ps.executeUpdate();
            profile.setDirty(false);
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save AnglerProfile for " + profile.getUuid(), e);
        }
    }
}
