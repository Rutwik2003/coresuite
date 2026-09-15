package net.rocky.coresuite.module.worldbosses.combat;

import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BossDamageTracker {

    public static record DamageEntry(UUID uuid, String name, double damage, double percentage) {}

    private final double maxHealth;
    private final Map<UUID, Long> lastActivity = new ConcurrentHashMap<>();
    private final Map<UUID, Double> playerDamage = new ConcurrentHashMap<>();
    private final Map<UUID, String> playerNames = new ConcurrentHashMap<>();

    public BossDamageTracker(double maxHealth) {
        this.maxHealth = maxHealth;
    }

    public void addDamage(Player player, double amount) {
        if (player == null || !Double.isFinite(amount) || amount <= 0) return;
        recordParticipation(player);
        UUID uuid = player.getUniqueId();
        playerDamage.merge(uuid, amount, Double::sum);
        playerNames.put(uuid, player.getName());
    }

    public void recordParticipation(Player player) {
        if (player == null) return;
        playerDamage.putIfAbsent(player.getUniqueId(), 0.0);
        playerNames.put(player.getUniqueId(), player.getName());
        lastActivity.put(player.getUniqueId(), System.currentTimeMillis());
    }
    public boolean wasRecentlyActive(UUID uuid, long since) { return lastActivity.getOrDefault(uuid, 0L) >= since; }

    public double getDamage(UUID uuid) {
        return playerDamage.getOrDefault(uuid, 0.0);
    }

    public double getTotalDamage() {
        return playerDamage.values().stream().mapToDouble(Double::doubleValue).sum();
    }

    public int getRank(UUID uuid) {
        List<DamageEntry> sorted = getSortedDamagers();
        for (int i = 0; i < sorted.size(); i++) {
            if (sorted.get(i).uuid().equals(uuid)) {
                return i + 1;
            }
        }
        return -1;
    }

    public List<DamageEntry> getSortedDamagers() {
        double total = getTotalDamage();
        if (total <= 0) total = maxHealth;

        final double finalTotal = total;
        List<DamageEntry> list = new ArrayList<>();
        for (Map.Entry<UUID, Double> entry : playerDamage.entrySet()) {
            UUID uuid = entry.getKey();
            double dmg = entry.getValue();
            double pct = (dmg / finalTotal) * 100.0;
            String name = playerNames.getOrDefault(uuid, "Unknown");
            list.add(new DamageEntry(uuid, name, dmg, pct));
        }

        list.sort((a, b) -> Double.compare(b.damage(), a.damage()));
        return list;
    }

    public DamageEntry getMvp() {
        List<DamageEntry> sorted = getSortedDamagers();
        return sorted.isEmpty() ? null : sorted.get(0);
    }

    public List<DamageEntry> getQualifiedDamagers(double minPercentOfMaxHealth) {
        double threshold = maxHealth * (minPercentOfMaxHealth / 100.0);
        List<DamageEntry> result = new ArrayList<>();
        for (DamageEntry entry : getSortedDamagers()) {
            if (entry.damage() >= threshold) {
                result.add(entry);
            }
        }
        return result;
    }

    public void clear() {
        lastActivity.clear();
        playerDamage.clear();
        playerNames.clear();
    }
}
