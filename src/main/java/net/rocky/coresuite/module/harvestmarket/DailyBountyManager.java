package net.rocky.coresuite.module.harvestmarket;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.logging.Level;

public class DailyBountyManager {
    private final CoreSuitePlugin plugin;
    private final SeasonManager seasonManager;
    private final EconomyManager economy;
    private final FileConfiguration config;

    public DailyBountyManager(CoreSuitePlugin plugin, SeasonManager seasonManager, EconomyManager economy, FileConfiguration config) {
        this.plugin = plugin;
        this.seasonManager = seasonManager;
        this.economy = economy;
        this.config = config;

        plugin.getDatabaseManager().execute(
            "CREATE TABLE IF NOT EXISTS coresuite_harvest_bounties (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "uuid TEXT NOT NULL, " +
            "bounty_date TEXT NOT NULL, " +
            "bounty_slot INTEGER NOT NULL, " +
            "material TEXT NOT NULL, " +
            "amount_needed INTEGER NOT NULL, " +
            "amount_done INTEGER NOT NULL DEFAULT 0, " +
            "money_reward REAL NOT NULL, " +
            "claimed INTEGER NOT NULL DEFAULT 0, " +
            "UNIQUE(uuid, bounty_date, bounty_slot))"
        );
    }

    public String todayKey() {
        return LocalDate.now().toString();
    }

    public void loadOrGenerateBounties(Player player, Consumer<List<DailyBounty>> callback) {
        String today = todayKey();
        UUID uuid = player.getUniqueId();
        
        plugin.getDatabaseManager().executeAsync(() -> {
            List<DailyBounty> bounties = new ArrayList<>();
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT * FROM coresuite_harvest_bounties WHERE uuid = ? AND bounty_date = ? ORDER BY bounty_slot ASC")) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, today);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        bounties.add(new DailyBounty(
                            rs.getInt("id"),
                            rs.getInt("bounty_slot"),
                            Material.valueOf(rs.getString("material")),
                            rs.getInt("amount_needed"),
                            rs.getInt("amount_done"),
                            rs.getDouble("money_reward"),
                            rs.getInt("claimed") == 1
                        ));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "DB error loading bounties", e);
            }
            
            if (bounties.isEmpty() && seasonManager.getCurrentSeason() != null) {
                List<Material> inSeason = seasonManager.getCurrentSeason().getInSeasonCrops();
                if (!inSeason.isEmpty()) {
                    for (int i = 1; i <= 3; i++) {
                        Material mat = inSeason.get(ThreadLocalRandom.current().nextInt(inSeason.size()));
                        int amount = (ThreadLocalRandom.current().nextInt(2, 11)) * 50; // 100 to 500 in steps of 50
                        double basePrice = config.getDouble("crop-prices." + mat.name(), 10);
                        double reward = amount * basePrice * 1.2;
                        
                        try (Connection conn = plugin.getDatabaseManager().getConnection();
                             PreparedStatement stmt = conn.prepareStatement(
                                 "INSERT INTO coresuite_harvest_bounties (uuid, bounty_date, bounty_slot, material, amount_needed, money_reward) " +
                                 "VALUES (?, ?, ?, ?, ?, ?)", PreparedStatement.RETURN_GENERATED_KEYS)) {
                            stmt.setString(1, uuid.toString());
                            stmt.setString(2, today);
                            stmt.setInt(3, i);
                            stmt.setString(4, mat.name());
                            stmt.setInt(5, amount);
                            stmt.setDouble(6, reward);
                            stmt.executeUpdate();
                            
                            try (ResultSet rs = stmt.getGeneratedKeys()) {
                                if (rs.next()) {
                                    bounties.add(new DailyBounty(rs.getInt(1), i, mat, amount, 0, reward, false));
                                }
                            }
                        } catch (SQLException e) {
                            plugin.getLogger().log(Level.SEVERE, "DB error creating bounty", e);
                        }
                    }
                }
            }
            
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(bounties));
        });
    }

    public void recordSell(UUID uuid, Material material, int amount) {
        String today = todayKey();
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE coresuite_harvest_bounties SET amount_done = MIN(amount_needed, amount_done + ?) " +
                     "WHERE uuid = ? AND bounty_date = ? AND material = ? AND claimed = 0")) {
                stmt.setInt(1, amount);
                stmt.setString(2, uuid.toString());
                stmt.setString(3, today);
                stmt.setString(4, material.name());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "DB error updating bounty", e);
            }
        });
    }

    public void claimBounty(UUID uuid, int bountySlot, Runnable onSuccess, Runnable onAlreadyClaimed) {
        String today = todayKey();
        plugin.getDatabaseManager().executeAsync(() -> {
            boolean success = false;
            double reward = 0;
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT * FROM coresuite_harvest_bounties WHERE uuid = ? AND bounty_date = ? AND bounty_slot = ?")) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, today);
                stmt.setInt(3, bountySlot);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        if (rs.getInt("claimed") == 1) {
                            Bukkit.getScheduler().runTask(plugin, onAlreadyClaimed);
                            return;
                        }
                        if (rs.getInt("amount_done") >= rs.getInt("amount_needed")) {
                            success = true;
                            reward = rs.getDouble("money_reward");
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "DB error checking bounty", e);
            }
            
            if (success) {
                try (Connection conn = plugin.getDatabaseManager().getConnection();
                     PreparedStatement stmt = conn.prepareStatement("UPDATE coresuite_harvest_bounties SET claimed = 1 WHERE uuid = ? AND bounty_date = ? AND bounty_slot = ?")) {
                    stmt.setString(1, uuid.toString());
                    stmt.setString(2, today);
                    stmt.setInt(3, bountySlot);
                    stmt.executeUpdate();
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "DB error claiming bounty", e);
                }
                final double fReward = reward;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Player p = Bukkit.getPlayer(uuid);
                    if (p != null) economy.deposit(p, fReward);
                    onSuccess.run();
                });
            }
        });
    }

    public static class DailyBounty {
        public final int id;
        public final int slot;
        public final Material material;
        public final int amountNeeded;
        public final int amountDone;
        public final double moneyReward;
        public final boolean claimed;

        public DailyBounty(int id, int slot, Material material, int amountNeeded, int amountDone, double moneyReward, boolean claimed) {
            this.id = id;
            this.slot = slot;
            this.material = material;
            this.amountNeeded = amountNeeded;
            this.amountDone = amountDone;
            this.moneyReward = moneyReward;
            this.claimed = claimed;
        }
    }
}
