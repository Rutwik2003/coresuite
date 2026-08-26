package net.rocky.coresuite.module.fishingrpg;

import net.rocky.coresuite.CoreSuitePlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DailyBountyManager {
    protected final CoreSuitePlugin plugin;
    private final SpeciesRegistry speciesRegistry;
    private final FileConfiguration config;
    
    private List<String> todaysBounties = new ArrayList<>();
    private long bountyResetTime = 0;
    private final Set<String> claimedBounties = ConcurrentHashMap.newKeySet();
    private int taskId = -1;

    public DailyBountyManager(CoreSuitePlugin plugin, SpeciesRegistry speciesRegistry, FileConfiguration config) {
        this.plugin = plugin;
        this.speciesRegistry = speciesRegistry;
        this.config = config;
        
        selectNewBounties();
        
        this.taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::checkReset, 20L * 60, 20L * 60);
    }

    public void selectNewBounties() {
        // Dummy logic to populate today's bounties
        todaysBounties.clear();
        claimedBounties.clear();
        int count = config.getInt("daily-bounty.target-count", 3);
        // ... selection logic from registry
        
        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        cal.add(Calendar.DAY_OF_YEAR, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        bountyResetTime = cal.getTimeInMillis();
    }

    public boolean isBountyTarget(String speciesId) {
        return todaysBounties.contains(speciesId);
    }

    public boolean claimBounty(UUID uuid, String speciesId) {
        String key = uuid.toString() + ":" + speciesId;
        if (!claimedBounties.contains(key) && isBountyTarget(speciesId)) {
            claimedBounties.add(key);
            return true;
        }
        return false;
    }

    public double getBountyReward() {
        return config.getDouble("daily-bounty.reward-per-fish", 100.0);
    }

    public int getBountyBonusXP() {
        return config.getInt("daily-bounty.bonus-xp", 50);
    }

    public List<String> getTodaysBounties() {
        return todaysBounties;
    }

    public void checkReset() {
        if (System.currentTimeMillis() > bountyResetTime) {
            selectNewBounties();
        }
    }

    public void shutdown() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
        }
    }
}
