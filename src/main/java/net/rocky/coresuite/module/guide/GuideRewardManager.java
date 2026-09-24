package net.rocky.coresuite.module.guide;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Manages the one-time starter reward for new and existing players,
 * backed by atomic SQLite/MySQL database transactions and anti-alt safeguards.
 */
public class GuideRewardManager {

    private final CoreSuitePlugin plugin;
    private final GuideModule module;
    private final Map<UUID, Boolean> claimedCache = new ConcurrentHashMap<>();
    private final Set<UUID> activeClaimLocks = ConcurrentHashMap.newKeySet();

    public GuideRewardManager(CoreSuitePlugin plugin, GuideModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    public void initDatabase() {
        plugin.getDatabaseManager().executeAsync(() -> {
            boolean isMySQL = plugin.getDatabaseManager().isMySQL();
            String uuidType = isMySQL ? "VARCHAR(36)" : "TEXT";
            String ipType = isMySQL ? "VARCHAR(64)" : "TEXT";
            String sql = "CREATE TABLE IF NOT EXISTS coresuite_player_guide (" +
                    "uuid " + uuidType + " PRIMARY KEY, " +
                    "first_seen BIGINT NOT NULL, " +
                    "reward_claimed INTEGER DEFAULT 0, " +
                    "claimed_at BIGINT DEFAULT 0, " +
                    "ip_address " + ipType +
                    ")";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not initialize coresuite_player_guide table", e);
            }
        });
    }

    /**
     * Checks if a player's claim state is currently loaded in cache.
     */
    public boolean isCached(UUID uuid) {
        return claimedCache.containsKey(uuid);
    }

    /**
     * Checks if a player has claimed their starter reward.
     */
    public boolean isClaimed(UUID uuid) {
        Boolean cached = claimedCache.get(uuid);
        return cached != null && cached;
    }

    /**
     * Loads the player's claim state from the database into the cache.
     */
    public void loadPlayer(UUID uuid, Runnable onDone) {
        plugin.getDatabaseManager().executeAsync(() -> {
            String sql = "SELECT reward_claimed FROM coresuite_player_guide WHERE uuid = ?";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        claimedCache.put(uuid, rs.getInt("reward_claimed") == 1);
                    } else {
                        claimedCache.put(uuid, false);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load player guide data for " + uuid, e);
                claimedCache.putIfAbsent(uuid, false);
            }
            if (onDone != null) {
                Bukkit.getScheduler().runTask(plugin, onDone);
            }
        });
    }

    /**
     * Records a player's first appearance in the database if not already present.
     */
    public void recordFirstSeen(UUID uuid, String ip) {
        plugin.getDatabaseManager().executeAsync(() -> {
            boolean isMySQL = plugin.getDatabaseManager().isMySQL();
            String sql = (isMySQL ? "INSERT IGNORE INTO" : "INSERT OR IGNORE INTO") +
                    " coresuite_player_guide (uuid, first_seen, reward_claimed, claimed_at, ip_address) " +
                    "VALUES (?, ?, 0, 0, ?)";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.setLong(2, System.currentTimeMillis());
                ps.setString(3, ip != null ? ip : "unknown");
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to record first seen for " + uuid, e);
            }
        });
    }

    /**
     * Attempts to claim the starter reward for the player.
     * Uses atomic database update to prevent race conditions or double claims.
     */
    public void claimStarterReward(Player player, Runnable onComplete) {
        if (!module.getConfig().getBoolean("starter-reward.enabled", true)) {
            TextUtil.sendMessage(player, module.getPrefix() + "&cStarter rewards are currently disabled.");
            return;
        }

        UUID uuid = player.getUniqueId();
        if (isClaimed(uuid)) {
            TextUtil.sendMessage(player, module.getPrefix() + "&cYou have already claimed your one-time starter gift.");
            return;
        }

        if (!activeClaimLocks.add(uuid)) {
            TextUtil.sendMessage(player, module.getPrefix() + "&eYour claim is currently processing. Please wait...");
            return;
        }

        // Inventory capacity check (ensure player has at least required-free-slots for crate keys)
        int requiredSlots = module.getConfig().getInt("starter-reward.required-free-slots", 2);
        int freeSlots = countFreeSlots(player);
        if (freeSlots < requiredSlots) {
            activeClaimLocks.remove(uuid);
            TextUtil.sendMessage(player, module.getPrefix() + "&cYour inventory is full! Please clear at least &e" + requiredSlots + " &cslots to receive your crate keys.");
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        String ip = player.getAddress() != null && player.getAddress().getAddress() != null
                ? player.getAddress().getAddress().getHostAddress()
                : "unknown";

        int maxClaimsPerIp = module.getConfig().getInt("starter-reward.max-claims-per-ip", 1);

        plugin.getDatabaseManager().executeAsync(() -> {
            try {
                boolean isMySQL = plugin.getDatabaseManager().isMySQL();
                // Ensure row exists before updating
                String insertSql = (isMySQL ? "INSERT IGNORE INTO" : "INSERT OR IGNORE INTO") +
                        " coresuite_player_guide (uuid, first_seen, reward_claimed, claimed_at, ip_address) " +
                        "VALUES (?, ?, 0, 0, ?)";
                try (Connection conn = plugin.getDatabaseManager().getConnection();
                     PreparedStatement insertPs = conn.prepareStatement(insertSql)) {
                    insertPs.setString(1, uuid.toString());
                    insertPs.setLong(2, System.currentTimeMillis());
                    insertPs.setString(3, ip);
                    insertPs.executeUpdate();
                }

                // Check IP address claim count if restricted (exempt localhost/loopback addresses)
                if (maxClaimsPerIp > 0 && !"unknown".equals(ip) && !isLocalhost(ip)) {
                    String ipCheckSql = "SELECT COUNT(*) FROM coresuite_player_guide WHERE ip_address = ? AND reward_claimed = 1 AND uuid != ?";
                    try (Connection conn = plugin.getDatabaseManager().getConnection();
                         PreparedStatement ipPs = conn.prepareStatement(ipCheckSql)) {
                        ipPs.setString(1, ip);
                        ipPs.setString(2, uuid.toString());
                        try (ResultSet rs = ipPs.executeQuery()) {
                            if (rs.next() && rs.getInt(1) >= maxClaimsPerIp) {
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    activeClaimLocks.remove(uuid);
                                    TextUtil.sendMessage(player, module.getPrefix() + "&cThe starter reward has already been claimed from your network (IP limit reached).");
                                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                                });
                                return;
                            }
                        }
                    }
                }

                // Atomic conditional update: only succeeds if reward_claimed is currently 0
                String updateSql = "UPDATE coresuite_player_guide SET reward_claimed = 1, claimed_at = ?, ip_address = ? " +
                        "WHERE uuid = ? AND reward_claimed = 0";
                int rowsUpdated;
                try (Connection conn = plugin.getDatabaseManager().getConnection();
                     PreparedStatement updatePs = conn.prepareStatement(updateSql)) {
                    updatePs.setLong(1, System.currentTimeMillis());
                    updatePs.setString(2, ip);
                    updatePs.setString(3, uuid.toString());
                    rowsUpdated = updatePs.executeUpdate();
                }

                if (rowsUpdated == 1) {
                    // Update cache
                    claimedCache.put(uuid, true);

                    // Payout rewards safely on server main thread
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        try {
                            if (player.isOnline()) {
                                double ecoAmount = module.getConfig().getDouble("starter-reward.economy-amount", 5000.0);
                                if (ecoAmount > 0 && plugin.getEconomyManager() != null) {
                                    plugin.getEconomyManager().deposit(player, ecoAmount);
                                }

                                List<String> commands = module.getConfig().getStringList("starter-reward.reward-commands");
                                for (String cmd : commands) {
                                    if (cmd != null && !cmd.isBlank()) {
                                        String dispatch = cmd.replace("{player}", player.getName())
                                                             .replace("%player%", player.getName())
                                                             .replace("{name}", player.getName())
                                                             .replace("%name%", player.getName())
                                                             .trim();
                                        if (dispatch.startsWith("/")) {
                                            dispatch = dispatch.substring(1);
                                        }
                                        plugin.getLogger().info("[Guide] Executing starter reward command for " + player.getName() + ": " + dispatch);
                                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), dispatch);
                                    }
                                }

                                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
                                TextUtil.sendMessage(player, "");
                                TextUtil.sendMessage(player, "&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                                TextUtil.sendMessage(player, "&a&l  ✦ STARTER WELCOME GIFT CLAIMED! ✦");
                                TextUtil.sendMessage(player, "  &e• &6+$" + String.format("%,.0f", ecoAmount) + " &7deposited to your bank");
                                TextUtil.sendMessage(player, "  &e• &f1x Common Crate Key &7delivered to your inventory");
                                TextUtil.sendMessage(player, "  &e• &a1x Uncommon Crate Key &7delivered to your inventory");
                                TextUtil.sendMessage(player, "&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                                TextUtil.sendMessage(player, "");

                                if (onComplete != null) {
                                    onComplete.run();
                                }
                            }
                        } finally {
                            activeClaimLocks.remove(uuid);
                        }
                    });
                } else {
                    // Already claimed
                    claimedCache.put(uuid, true);
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        activeClaimLocks.remove(uuid);
                        TextUtil.sendMessage(player, module.getPrefix() + "&cYou have already claimed your one-time starter gift.");
                        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        if (onComplete != null) {
                            onComplete.run();
                        }
                    });
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "SQL error claiming starter reward for " + uuid, e);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    activeClaimLocks.remove(uuid);
                    TextUtil.sendMessage(player, module.getPrefix() + "&cAn error occurred processing your claim. Please try again later.");
                });
            }
        });
    }

    /**
     * Resets a player's claim state (admin tool).
     */
    public void resetPlayer(UUID uuid, Runnable onDone) {
        claimedCache.put(uuid, false);
        plugin.getDatabaseManager().executeAsync(() -> {
            String sql = "UPDATE coresuite_player_guide SET reward_claimed = 0, claimed_at = 0 WHERE uuid = ?";
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, uuid.toString());
                ps.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to reset player guide for " + uuid, e);
            }
            if (onDone != null) {
                Bukkit.getScheduler().runTask(plugin, onDone);
            }
        });
    }

    public void removePlayerFromCache(UUID uuid) {
        claimedCache.remove(uuid);
        activeClaimLocks.remove(uuid);
    }

    private int countFreeSlots(Player player) {
        int free = 0;
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (ItemStack item : contents) {
            if (item == null || item.getType().isAir()) {
                free++;
            }
        }
        return free;
    }

    private boolean isLocalhost(String ip) {
        if (ip == null) return false;
        return ip.equals("127.0.0.1") || ip.equals("0:0:0:0:0:0:0:1") || ip.equalsIgnoreCase("localhost");
    }
}
