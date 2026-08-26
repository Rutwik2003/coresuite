package net.rocky.coresuite.module.customfishing;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.gui.GuiButton;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class FishingTournamentManager {
    private final CoreSuitePlugin plugin;
    private final CustomFishingModule module;
    private BukkitTask checkTask;
    private long lastPaidWeekStart = 0;
    private long cachedWeekStart = 0;

    public FishingTournamentManager(CoreSuitePlugin plugin, CustomFishingModule module) {
        this.plugin = plugin;
        this.module = module;

        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "CREATE TABLE IF NOT EXISTS coresuite_fish_tournament (" +
                                "uuid TEXT NOT NULL, " +
                                "player_name TEXT NOT NULL, " +
                                "week_start BIGINT NOT NULL, " +
                                "total_weight REAL DEFAULT 0, " +
                                "total_catches INTEGER DEFAULT 0, " +
                                "biggest_catch REAL DEFAULT 0, " +
                                "PRIMARY KEY (uuid, week_start)" +
                                ")"
                )) {
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "CREATE TABLE IF NOT EXISTS coresuite_fish_tournament_meta (" +
                                "meta_key TEXT PRIMARY KEY, " +
                                "meta_value TEXT NOT NULL" +
                                ")"
                )) {
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT meta_value FROM coresuite_fish_tournament_meta WHERE meta_key = 'last_paid_week'"
                )) {
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            lastPaidWeekStart = Long.parseLong(rs.getString("meta_value"));
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to initialize Fishing Tournament tables");
                e.printStackTrace();
            }
        });

        this.cachedWeekStart = calculateWeekStart();

        this.checkTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            this.cachedWeekStart = calculateWeekStart();
            plugin.getDatabaseManager().executeAsync(this::checkAndPayoutPreviousWeek);
        }, 20L * 60 * 5, 20L * 60 * 5);
    }

    public long getWeekStart() {
        return cachedWeekStart;
    }

    private long calculateWeekStart() {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return monday.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public void recordCatch(Player player, double weight) {
        long weekStart = getWeekStart();
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = conn.prepareStatement(
                         "INSERT INTO coresuite_fish_tournament (uuid, player_name, week_start, total_weight, total_catches, biggest_catch) " +
                                 "VALUES (?, ?, ?, ?, 1, ?) " +
                                 "ON CONFLICT(uuid, week_start) DO UPDATE SET " +
                                 "total_weight = total_weight + excluded.total_weight, " +
                                 "total_catches = total_catches + 1, " +
                                 "biggest_catch = MAX(biggest_catch, excluded.biggest_catch), " +
                                 "player_name = excluded.player_name"
                 )) {
                ps.setString(1, player.getUniqueId().toString());
                ps.setString(2, player.getName());
                ps.setLong(3, weekStart);
                ps.setDouble(4, weight);
                ps.setDouble(5, weight);
                ps.executeUpdate();
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to record catch for tournament");
                e.printStackTrace();
            }
        });
    }

    public void checkAndPayoutPreviousWeek() {
        long previousWeekStart = getWeekStart() - (7 * 24 * 60 * 60 * 1000L);
        if (previousWeekStart <= lastPaidWeekStart) {
            return;
        }

        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            int totalParticipants = 0;
            try (PreparedStatement psCount = conn.prepareStatement(
                    "SELECT COUNT(DISTINCT uuid) AS count FROM coresuite_fish_tournament WHERE week_start = ?"
            )) {
                psCount.setLong(1, previousWeekStart);
                try (ResultSet rs = psCount.executeQuery()) {
                    if (rs.next()) {
                        totalParticipants = rs.getInt("count");
                    }
                }
            }

            int minParticipants = module.getConfig().getInt("tournament.min-participants", 3);
            if (totalParticipants < minParticipants) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    Bukkit.broadcast(TextUtil.colorize("&c&lFishing Tournament: &7Not enough participants last week for payouts."));
                });
            } else {
                List<Winner> winners = new ArrayList<>();
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT uuid, player_name, total_weight, total_catches, biggest_catch " +
                                "FROM coresuite_fish_tournament WHERE week_start = ? ORDER BY total_weight DESC LIMIT 3"
                )) {
                    ps.setLong(1, previousWeekStart);
                    try (ResultSet rs = ps.executeQuery()) {
                        int rank = 1;
                        while (rs.next()) {
                            winners.add(new Winner(
                                    rs.getString("uuid"),
                                    rs.getString("player_name"),
                                    rs.getDouble("total_weight"),
                                    rank++
                            ));
                        }
                    }
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    Bukkit.broadcast(TextUtil.colorize("&b&lWeekly Fishing Tournament Results!"));
                    
                    for (Winner w : winners) {
                        String placementStr = w.rank == 1 ? "1st" : w.rank == 2 ? "2nd" : "3rd";
                        double prize = module.getConfig().getDouble("tournament.prizes." + placementStr, w.rank == 1 ? 500000.0 : w.rank == 2 ? 250000.0 : 100000.0);
                        String command = module.getConfig().getString("tournament.prize-commands." + placementStr, "");
                        
                        OfflinePlayer op = Bukkit.getOfflinePlayer(UUID.fromString(w.uuid));
                        plugin.getEconomyManager().deposit(op, prize);
                        
                        if (command != null && !command.isEmpty()) {
                            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command.replace("{player}", w.playerName));
                        }
                        
                        String formattedPrize = plugin.getEconomyManager().format(prize);
                        Bukkit.broadcast(TextUtil.colorize("&e" + placementStr + " Place: &f" + w.playerName + " &7- &b" + String.format("%.2f", w.totalWeight) + " kg &7(&a" + formattedPrize + "&7)"));
                    }
                });
            }

            lastPaidWeekStart = previousWeekStart;
            try (PreparedStatement psMeta = conn.prepareStatement(
                    "INSERT INTO coresuite_fish_tournament_meta (meta_key, meta_value) VALUES ('last_paid_week', ?) " +
                            "ON CONFLICT(meta_key) DO UPDATE SET meta_value = excluded.meta_value"
            )) {
                psMeta.setString(1, String.valueOf(lastPaidWeekStart));
                psMeta.executeUpdate();
            }

        } catch (Exception e) {
            plugin.getLogger().severe("Failed to process tournament payouts");
            e.printStackTrace();
        }
    }

    public void openTournamentGUI(Player player) {
        long currentWeekStart = getWeekStart();
        plugin.getDatabaseManager().executeAsync(() -> {
            List<TournamentEntry> top10 = new ArrayList<>();
            TournamentEntry ownEntry = null;

            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT uuid, player_name, total_weight, total_catches, biggest_catch " +
                                "FROM coresuite_fish_tournament WHERE week_start = ? ORDER BY total_weight DESC LIMIT 10"
                )) {
                    ps.setLong(1, currentWeekStart);
                    try (ResultSet rs = ps.executeQuery()) {
                        int rank = 1;
                        while (rs.next()) {
                            top10.add(new TournamentEntry(
                                    rs.getString("uuid"),
                                    rs.getString("player_name"),
                                    rs.getDouble("total_weight"),
                                    rs.getInt("total_catches"),
                                    rs.getDouble("biggest_catch"),
                                    rank++
                            ));
                        }
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "WITH ranked AS (SELECT uuid, total_weight, total_catches, biggest_catch, " +
                                "RANK() OVER (ORDER BY total_weight DESC) as rank " +
                                "FROM coresuite_fish_tournament WHERE week_start = ?) " +
                                "SELECT * FROM ranked WHERE uuid = ?"
                )) {
                    ps.setLong(1, currentWeekStart);
                    ps.setString(2, player.getUniqueId().toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            ownEntry = new TournamentEntry(
                                    rs.getString("uuid"),
                                    player.getName(),
                                    rs.getDouble("total_weight"),
                                    rs.getInt("total_catches"),
                                    rs.getDouble("biggest_catch"),
                                    rs.getInt("rank")
                            );
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to load tournament data for GUI");
                e.printStackTrace();
                Bukkit.getScheduler().runTask(plugin, () -> TextUtil.sendMessage(player, "&cError loading tournament data."));
                return;
            }

            final TournamentEntry finalOwnEntry = ownEntry;

            Bukkit.getScheduler().runTask(plugin, () -> {
                Gui gui = new Gui("&8Weekly Tournament", 5);
                gui.fillBorder(Material.CYAN_STAINED_GLASS_PANE);

                int[] slots = {13, 21, 22, 23, 29, 30, 31, 32, 33, 40};
                for (int i = 0; i < top10.size() && i < slots.length; i++) {
                    TournamentEntry entry = top10.get(i);
                    ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                    SkullMeta meta = (SkullMeta) head.getItemMeta();
                    if (meta != null) {
                        meta.setOwningPlayer(Bukkit.getOfflinePlayer(UUID.fromString(entry.uuid)));
                        meta.displayName(TextUtil.colorize("&b&l#" + entry.rank + " &f" + entry.playerName));
                        meta.lore(Arrays.asList(
                                TextUtil.colorize("&7Total Weight: &b" + String.format("%.2f", entry.totalWeight) + " kg"),
                                TextUtil.colorize("&7Catches: &b" + entry.totalCatches),
                                TextUtil.colorize("&7Biggest: &b" + String.format("%.2f", entry.biggestCatch) + " kg")
                        ));
                        head.setItemMeta(meta);
                    }
                    gui.setItem(slots[i], new GuiButton(head, event -> {}));
                }

                long nextReset = currentWeekStart + (7 * 24 * 60 * 60 * 1000L);
                long timeLeft = nextReset - System.currentTimeMillis();
                long days = timeLeft / (24 * 60 * 60 * 1000L);
                long hours = (timeLeft % (24 * 60 * 60 * 1000L)) / (60 * 60 * 1000L);

                gui.setItem(4, GuiButton.of(
                        Material.CLOCK,
                        "&e&lWeekly Tournament",
                        Arrays.asList(
                                "&7Resets: &bMonday at 00:00",
                                "&7Time Left: &b" + days + "d " + hours + "h",
                                "",
                                "&7Prize Pool:",
                                "&e1st: &f" + plugin.getEconomyManager().format(module.getConfig().getDouble("tournament.prizes.1st", 500000.0)),
                                "&62nd: &f" + plugin.getEconomyManager().format(module.getConfig().getDouble("tournament.prizes.2nd", 250000.0)),
                                "&c3rd: &f" + plugin.getEconomyManager().format(module.getConfig().getDouble("tournament.prizes.3rd", 100000.0))
                        ),
                        event -> {}
                ));

                if (finalOwnEntry != null) {
                    gui.setItem(40, GuiButton.of(
                            Material.PAPER,
                            "&b&lYour Stats",
                            Arrays.asList(
                                    "&7Rank: &e#" + finalOwnEntry.rank,
                                    "&7Total Weight: &b" + String.format("%.2f", finalOwnEntry.totalWeight) + " kg",
                                    "&7Catches: &b" + finalOwnEntry.totalCatches,
                                    "&7Biggest: &b" + String.format("%.2f", finalOwnEntry.biggestCatch) + " kg"
                            ),
                            event -> {}
                    ));
                } else {
                    gui.setItem(40, GuiButton.of(
                            Material.PAPER,
                            "&b&lYour Stats",
                            Arrays.asList("&7You haven't caught anything this week!"),
                            event -> {}
                    ));
                }

                gui.setItem(44, GuiButton.of(
                        Material.BARRIER,
                        "&cBack",
                        Arrays.asList(),
                        event -> {
                            // player.closeInventory();
                            player.performCommand("fish");
                        }
                ));

                gui.open(player);
            });
        });
    }

    public void shutdown() {
        if (checkTask != null) {
            checkTask.cancel();
        }
    }

    private static class Winner {
        String uuid;
        String playerName;
        double totalWeight;
        int rank;

        Winner(String uuid, String playerName, double totalWeight, int rank) {
            this.uuid = uuid;
            this.playerName = playerName;
            this.totalWeight = totalWeight;
            this.rank = rank;
        }
    }

    private static class TournamentEntry {
        String uuid;
        String playerName;
        double totalWeight;
        int totalCatches;
        double biggestCatch;
        int rank;

        TournamentEntry(String uuid, String playerName, double totalWeight, int totalCatches, double biggestCatch, int rank) {
            this.uuid = uuid;
            this.playerName = playerName;
            this.totalWeight = totalWeight;
            this.totalCatches = totalCatches;
            this.biggestCatch = biggestCatch;
            this.rank = rank;
        }
    }
}
