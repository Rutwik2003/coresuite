/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  org.bukkit.Bukkit
 *  org.bukkit.Material
 *  org.bukkit.OfflinePlayer
 *  org.bukkit.command.CommandSender
 *  org.bukkit.entity.Player
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.inventory.meta.SkullMeta
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.scheduler.BukkitTask
 */
package net.rocky.coresuite.module.customfishing;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.gui.GuiButton;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.customfishing.CustomFishingModule;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class FishingTournamentManager {
    private final CoreSuitePlugin plugin;
    private final CustomFishingModule module;
    private BukkitTask checkTask;
    private long lastPaidWeekStart = 0L;
    private long cachedWeekStart = 0L;

    public FishingTournamentManager(CoreSuitePlugin plugin, CustomFishingModule module) {
        this.plugin = plugin;
        this.module = module;
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();){
                try (java.sql.PreparedStatement ps = conn.prepareStatement("CREATE TABLE IF NOT EXISTS coresuite_fish_tournament (uuid TEXT NOT NULL, player_name TEXT NOT NULL, week_start BIGINT NOT NULL, total_weight REAL DEFAULT 0, total_catches INTEGER DEFAULT 0, biggest_catch REAL DEFAULT 0, PRIMARY KEY (uuid, week_start))");){
                    ps.executeUpdate();
                }
                PreparedStatement ps = conn.prepareStatement("CREATE TABLE IF NOT EXISTS coresuite_fish_tournament_meta (meta_key TEXT PRIMARY KEY, meta_value TEXT NOT NULL)");
                try {
                    ps.executeUpdate();
                }
                finally {
                    if (ps != null) {
                        ps.close();
                    }
                }
                ps = conn.prepareStatement("SELECT meta_value FROM coresuite_fish_tournament_meta WHERE meta_key = 'last_paid_week'");
                try {
                    ResultSet rs = ps.executeQuery();
                    if (rs.next()) {
                        this.lastPaidWeekStart = Long.parseLong(rs.getString("meta_value"));
                    }
                }
                finally {
                    if (ps != null) {
                        ps.close();
                    }
                }
            }
            catch (Exception e) {
                plugin.getLogger().severe("Failed to initialize Fishing Tournament tables");
                e.printStackTrace();
            }
        });
        this.cachedWeekStart = this.calculateWeekStart();
        this.checkTask = Bukkit.getScheduler().runTaskTimer((Plugin)plugin, () -> {
            this.cachedWeekStart = this.calculateWeekStart();
            plugin.getDatabaseManager().executeAsync(this::checkAndPayoutPreviousWeek);
        }, 6000L, 6000L);
    }

    public long getWeekStart() {
        return this.cachedWeekStart;
    }

    private long calculateWeekStart() {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return monday.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    public void recordCatch(Player player, double weight) {
        long weekStart = this.getWeekStart();
        this.plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = this.plugin.getDatabaseManager().getConnection();
                 java.sql.PreparedStatement ps = conn.prepareStatement("INSERT INTO coresuite_fish_tournament (uuid, player_name, week_start, total_weight, total_catches, biggest_catch) VALUES (?, ?, ?, ?, 1, ?) ON CONFLICT(uuid, week_start) DO UPDATE SET total_weight = total_weight + excluded.total_weight, total_catches = total_catches + 1, biggest_catch = MAX(biggest_catch, excluded.biggest_catch), player_name = excluded.player_name");){
                ps.setString(1, player.getUniqueId().toString());
                ps.setString(2, player.getName());
                ps.setLong(3, weekStart);
                ps.setDouble(4, weight);
                ps.setDouble(5, weight);
                ps.executeUpdate();
            }
            catch (Exception e) {
                this.plugin.getLogger().severe("Failed to record catch for tournament");
                e.printStackTrace();
            }
        });
    }

    public void checkAndPayoutPreviousWeek() {
        long previousWeekStart = this.getWeekStart() - 604800000L;
        if (previousWeekStart <= this.lastPaidWeekStart) {
            return;
        }
        try (Connection conn = this.plugin.getDatabaseManager().getConnection();){
            int totalParticipants = 0;
            try (PreparedStatement psCount = conn.prepareStatement("SELECT COUNT(DISTINCT uuid) AS count FROM coresuite_fish_tournament WHERE week_start = ?");){
                psCount.setLong(1, previousWeekStart);
                ResultSet rs = psCount.executeQuery();
                if (rs.next()) {
                    totalParticipants = rs.getInt("count");
                }
            }
            int minParticipants = this.module.getConfig().getInt("tournament.min-participants", 3);
            if (totalParticipants < minParticipants) {
                Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> Bukkit.broadcast((Component)TextUtil.colorize("&c&lFishing Tournament: &7Not enough participants last week for payouts.")));
            } else {
                ArrayList<Winner> winners = new ArrayList<Winner>();
                try (java.sql.PreparedStatement ps = conn.prepareStatement("SELECT uuid, player_name, total_weight, total_catches, biggest_catch FROM coresuite_fish_tournament WHERE week_start = ? ORDER BY total_weight DESC LIMIT 3");){
                    ps.setLong(1, previousWeekStart);
                    ResultSet rs = ps.executeQuery();
                    int rank = 1;
                    while (rs.next()) {
                        winners.add(new Winner(rs.getString("uuid"), rs.getString("player_name"), rs.getDouble("total_weight"), rank++));
                    }
                }
                Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
                    Bukkit.broadcast((Component)TextUtil.colorize("&b&lWeekly Fishing Tournament Results!"));
                    for (Winner w : winners) {
                        String placementStr = w.rank == 1 ? "1st" : (w.rank == 2 ? "2nd" : "3rd");
                        double prize = this.module.getConfig().getDouble("tournament.prizes." + placementStr, w.rank == 1 ? 500000.0 : (w.rank == 2 ? 250000.0 : 100000.0));
                        String command = this.module.getConfig().getString("tournament.prize-commands." + placementStr, "");
                        OfflinePlayer op = Bukkit.getOfflinePlayer((UUID)UUID.fromString(w.uuid));
                        this.plugin.getEconomyManager().deposit(op, prize);
                        if (command != null && !command.isEmpty()) {
                            Bukkit.dispatchCommand((CommandSender)Bukkit.getConsoleSender(), (String)command.replace("{player}", w.playerName));
                        }
                        String formattedPrize = this.plugin.getEconomyManager().format(prize);
                        Bukkit.broadcast((Component)TextUtil.colorize("&e" + placementStr + " Place: &f" + w.playerName + " &7- &b" + String.format("%.2f", w.totalWeight) + " kg &7(&a" + formattedPrize + "&7)"));
                    }
                });
            }
            this.lastPaidWeekStart = previousWeekStart;
            try (PreparedStatement psMeta = conn.prepareStatement("INSERT INTO coresuite_fish_tournament_meta (meta_key, meta_value) VALUES ('last_paid_week', ?) ON CONFLICT(meta_key) DO UPDATE SET meta_value = excluded.meta_value");){
                psMeta.setString(1, String.valueOf(this.lastPaidWeekStart));
                psMeta.executeUpdate();
            }
        }
        catch (Exception e) {
            this.plugin.getLogger().severe("Failed to process tournament payouts");
            e.printStackTrace();
        }
    }

    public void openTournamentGUI(Player player) {
        long currentWeekStart = this.getWeekStart();
        this.plugin.getDatabaseManager().executeAsync(() -> {
            ArrayList<TournamentEntry> top10 = new ArrayList<TournamentEntry>();
            TournamentEntry ownEntry = null;
            try (Connection conn = this.plugin.getDatabaseManager().getConnection();){
                ResultSet rs;
                try (java.sql.PreparedStatement ps = conn.prepareStatement("SELECT uuid, player_name, total_weight, total_catches, biggest_catch FROM coresuite_fish_tournament WHERE week_start = ? ORDER BY total_weight DESC LIMIT 10");){
                    ps.setLong(1, currentWeekStart);
                    rs = ps.executeQuery();
                    int rank = 1;
                    while (rs.next()) {
                        top10.add(new TournamentEntry(rs.getString("uuid"), rs.getString("player_name"), rs.getDouble("total_weight"), rs.getInt("total_catches"), rs.getDouble("biggest_catch"), rank++));
                    }
                }
                PreparedStatement ps = conn.prepareStatement("WITH ranked AS (SELECT uuid, total_weight, total_catches, biggest_catch, RANK() OVER (ORDER BY total_weight DESC) as rank FROM coresuite_fish_tournament WHERE week_start = ?) SELECT * FROM ranked WHERE uuid = ?");
                try {
                    ps.setLong(1, currentWeekStart);
                    ps.setString(2, player.getUniqueId().toString());
                    rs = ps.executeQuery();
                    if (rs.next()) {
                        ownEntry = new TournamentEntry(rs.getString("uuid"), player.getName(), rs.getDouble("total_weight"), rs.getInt("total_catches"), rs.getDouble("biggest_catch"), rs.getInt("rank"));
                    }
                }
                finally {
                    if (ps != null) {
                        ps.close();
                    }
                }
            }
            catch (Exception e) {
                this.plugin.getLogger().severe("Failed to load tournament data for GUI");
                e.printStackTrace();
                Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> TextUtil.sendMessage((CommandSender)player, "&cError loading tournament data."));
                return;
            }
            TournamentEntry finalOwnEntry = ownEntry;
            Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
                Gui gui = new Gui("&8Weekly Tournament", 5);
                gui.fillBorder(Material.CYAN_STAINED_GLASS_PANE);
                int[] slots = new int[]{13, 21, 22, 23, 29, 30, 31, 32, 33, 40};
                for (int i = 0; i < top10.size() && i < slots.length; ++i) {
                    TournamentEntry entry = (TournamentEntry)top10.get(i);
                    ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                    SkullMeta meta = (SkullMeta)head.getItemMeta();
                    if (meta != null) {
                        meta.setOwningPlayer(Bukkit.getOfflinePlayer((UUID)UUID.fromString(entry.uuid)));
                        meta.displayName(TextUtil.colorize("&b&l#" + entry.rank + " &f" + entry.playerName));
                        meta.lore(Arrays.asList(TextUtil.colorize("&7Total Weight: &b" + String.format("%.2f", entry.totalWeight) + " kg"), TextUtil.colorize("&7Catches: &b" + entry.totalCatches), TextUtil.colorize("&7Biggest: &b" + String.format("%.2f", entry.biggestCatch) + " kg")));
                        head.setItemMeta((ItemMeta)meta);
                    }
                    gui.setItem(slots[i], new GuiButton(head, event -> {}));
                }
                long nextReset = currentWeekStart + 604800000L;
                long timeLeft = nextReset - System.currentTimeMillis();
                long days = timeLeft / 86400000L;
                long hours = timeLeft % 86400000L / 3600000L;
                gui.setItem(4, GuiButton.of(Material.CLOCK, "&e&lWeekly Tournament", Arrays.asList("&7Resets: &bMonday at 00:00", "&7Time Left: &b" + days + "d " + hours + "h", "", "&7Prize Pool:", "&e1st: &f" + this.plugin.getEconomyManager().format(this.module.getConfig().getDouble("tournament.prizes.1st", 500000.0)), "&62nd: &f" + this.plugin.getEconomyManager().format(this.module.getConfig().getDouble("tournament.prizes.2nd", 250000.0)), "&c3rd: &f" + this.plugin.getEconomyManager().format(this.module.getConfig().getDouble("tournament.prizes.3rd", 100000.0))), event -> {}));
                if (finalOwnEntry != null) {
                    gui.setItem(40, GuiButton.of(Material.PAPER, "&b&lYour Stats", Arrays.asList("&7Rank: &e#" + finalOwnEntry.rank, "&7Total Weight: &b" + String.format("%.2f", finalOwnEntry.totalWeight) + " kg", "&7Catches: &b" + finalOwnEntry.totalCatches, "&7Biggest: &b" + String.format("%.2f", finalOwnEntry.biggestCatch) + " kg"), event -> {}));
                } else {
                    gui.setItem(40, GuiButton.of(Material.PAPER, "&b&lYour Stats", Arrays.asList("&7You haven't caught anything this week!"), event -> {}));
                }
                gui.setItem(44, GuiButton.of(Material.BARRIER, "&cBack", Arrays.asList(new String[0]), event -> player.performCommand("fish")));
                gui.open(player);
            });
        });
    }

    public void shutdown() {
        if (this.checkTask != null) {
            this.checkTask.cancel();
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

