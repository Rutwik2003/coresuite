package net.rocky.coresuite.module.archaeologyrush;

import net.rocky.coresuite.CoreSuitePlugin;
import org.bukkit.Bukkit;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

public class PlayerCollectionManager {

    private final CoreSuitePlugin plugin;
    // UUID -> (SetID -> Set<FragmentIndex>)
    private final Map<UUID, Map<String, Set<Integer>>> cache = new ConcurrentHashMap<>();

    public PlayerCollectionManager(CoreSuitePlugin plugin) {
        this.plugin = plugin;
    }

    public void loadPlayer(UUID uuid, Runnable callback) {
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT set_id, fragment_index FROM coresuite_artifact_fragments WHERE uuid = ?")) {
                stmt.setString(1, uuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    Map<String, Set<Integer>> playerSets = new ConcurrentHashMap<>();
                    while (rs.next()) {
                        String setId = rs.getString("set_id");
                        int fragmentIndex = rs.getInt("fragment_index");
                        playerSets.computeIfAbsent(setId, k -> new HashSet<>()).add(fragmentIndex);
                    }
                    cache.put(uuid, playerSets);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "DB error loading player fragments for " + uuid, e);
            }
            if (callback != null) {
                Bukkit.getScheduler().runTask(plugin, callback);
            }
        });
    }

    public Set<Integer> getOwnedFragments(UUID uuid, String setId) {
        return cache.getOrDefault(uuid, Map.of()).getOrDefault(setId, Set.of());
    }

    public boolean hasFragment(UUID uuid, String setId, int fragmentIndex) {
        return getOwnedFragments(uuid, setId).contains(fragmentIndex);
    }

    public boolean hasCompleteSet(UUID uuid, ArtifactSet set) {
        Set<Integer> owned = getOwnedFragments(uuid, set.id());
        for (ArtifactFragment fragment : set.fragments()) {
            if (!owned.contains(fragment.fragmentIndex())) {
                return false;
            }
        }
        return true;
    }

    public void addFragment(UUID uuid, String playerName, String setId, int fragmentIndex) {
        // Update cache
        cache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
             .computeIfAbsent(setId, k -> new HashSet<>())
             .add(fragmentIndex);

        // Update DB
        plugin.getDatabaseManager().execute(
                "INSERT OR IGNORE INTO coresuite_artifact_fragments (uuid, set_id, fragment_index, obtained_at) VALUES (?, ?, ?, ?)",
                uuid.toString(), setId, fragmentIndex, System.currentTimeMillis()
        );
    }

    public void completeSet(UUID uuid, String playerName, ArtifactSet set, Runnable onSuccess) {
        // Clear from cache
        if (cache.containsKey(uuid)) {
            cache.get(uuid).remove(set.id());
        }

        plugin.getDatabaseManager().executeAsync(() -> {
            boolean success = false;
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                conn.setAutoCommit(false);
                try {
                    // Delete fragments
                    try (PreparedStatement delStmt = conn.prepareStatement("DELETE FROM coresuite_artifact_fragments WHERE uuid = ? AND set_id = ?")) {
                        delStmt.setString(1, uuid.toString());
                        delStmt.setString(2, set.id());
                        delStmt.executeUpdate();
                    }
                    
                    // Insert completion
                    try (PreparedStatement insStmt = conn.prepareStatement("INSERT INTO coresuite_artifact_completions (uuid, player_name, set_id, completed_at) VALUES (?, ?, ?, ?)")) {
                        insStmt.setString(1, uuid.toString());
                        insStmt.setString(2, playerName);
                        insStmt.setString(3, set.id());
                        insStmt.setLong(4, System.currentTimeMillis());
                        insStmt.executeUpdate();
                    }
                    
                    conn.commit();
                    success = true;
                } catch (SQLException ex) {
                    conn.rollback();
                    throw ex;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "DB error completing set for " + uuid, e);
            }
            
            if (success && onSuccess != null) {
                Bukkit.getScheduler().runTask(plugin, onSuccess);
            }
        });
    }

    public void getCompletionCount(UUID uuid, String setId, Consumer<Integer> callback) {
        plugin.getDatabaseManager().executeAsync(() -> {
            int count = 0;
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM coresuite_artifact_completions WHERE uuid = ? AND set_id = ?")) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, setId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        count = rs.getInt(1);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "DB error counting completions for " + uuid, e);
            }
            int finalCount = count;
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(finalCount));
        });
    }

    public void getTotalCompletions(UUID uuid, Consumer<Integer> callback) {
        plugin.getDatabaseManager().executeAsync(() -> {
            int count = 0;
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT COUNT(*) FROM coresuite_artifact_completions WHERE uuid = ?")) {
                stmt.setString(1, uuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        count = rs.getInt(1);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "DB error counting total completions for " + uuid, e);
            }
            int finalCount = count;
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(finalCount));
        });
    }
}
