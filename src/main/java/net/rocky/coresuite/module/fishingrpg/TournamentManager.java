package net.rocky.coresuite.module.fishingrpg;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import java.util.UUID;
import java.util.Calendar;
import java.util.TimeZone;
import org.bukkit.scheduler.BukkitRunnable;

public class TournamentManager {
    protected final CoreSuitePlugin plugin;
    private final FileConfiguration config;
    private int taskId = -1;

    public TournamentManager(CoreSuitePlugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        initDatabase();
        
        this.taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::checkPayout, 20L * 60 * 5, 20L * 60 * 5);
    }

    public void initDatabase() {
        plugin.getDatabaseManager().execute("CREATE TABLE IF NOT EXISTS fishingrpg_tournament (" +
                "uuid TEXT NOT NULL, " +
                "player_name TEXT NOT NULL, " +
                "week_start BIGINT NOT NULL, " +
                "total_weight REAL DEFAULT 0, " +
                "total_catches INTEGER DEFAULT 0, " +
                "biggest_catch REAL DEFAULT 0, " +
                "PRIMARY KEY (uuid, week_start))");
        plugin.getDatabaseManager().execute("CREATE TABLE IF NOT EXISTS fishingrpg_tournament_meta (" +
                "meta_key TEXT PRIMARY KEY, " +
                "meta_value TEXT NOT NULL)");
    }

    public void recordCatch(UUID uuid, String playerName, double weight) {
        long weekStart = getCurrentWeekStart();
        plugin.getDatabaseManager().execute("INSERT INTO fishingrpg_tournament (uuid, player_name, week_start, total_weight, total_catches, biggest_catch) " +
                "VALUES (?, ?, ?, ?, 1, ?) " +
                "ON CONFLICT(uuid, week_start) DO UPDATE SET " +
                "total_weight = total_weight + ?, " +
                "total_catches = total_catches + 1, " +
                "biggest_catch = MAX(biggest_catch, ?)",
                uuid.toString(), playerName, weekStart, weight, weight, weight, weight);
    }

    public long getCurrentWeekStart() {
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    public void checkPayout() {
        // Dummy logic for payout calculation
        // Check current week against last paid week in meta DB
    }

    public void openTournamentGUI(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, TextUtil.colorize("&8Fishing Tournament"));
        player.openInventory(gui);
    }

    public void shutdown() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }
}
