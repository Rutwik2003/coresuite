package net.rocky.coresuite.module.harvestmarket;

import org.bukkit.Material;
import java.util.List;
import java.util.stream.Collectors;

public class Season {
    private final int id;
    private final List<Material> inSeasonCrops;
    private final long startedAt;
    private final long endsAt;
    private final boolean active;

    public Season(int id, List<Material> inSeasonCrops, long startedAt, long endsAt, boolean active) {
        this.id = id;
        this.inSeasonCrops = inSeasonCrops;
        this.startedAt = startedAt;
        this.endsAt = endsAt;
        this.active = active;
    }

    public int getId() { return id; }
    public List<Material> getInSeasonCrops() { return inSeasonCrops; }
    public long getStartedAt() { return startedAt; }
    public long getEndsAt() { return endsAt; }
    public boolean isActive() { return active; }

    public boolean isExpired() {
        return System.currentTimeMillis() > endsAt;
    }

    public long getTimeRemainingMs() {
        return Math.max(0, endsAt - System.currentTimeMillis());
    }

    public String getTimeRemainingString() {
        long ms = getTimeRemainingMs();
        long totalSecs = ms / 1000;
        long days = totalSecs / 86400;
        long hours = (totalSecs % 86400) / 3600;
        long mins = ((totalSecs % 86400) % 3600) / 60;
        return days + "d " + hours + "h " + mins + "m";
    }

    public String getCropListFormatted() {
        if (inSeasonCrops == null || inSeasonCrops.isEmpty()) return "&cNone";
        return inSeasonCrops.stream()
                .map(m -> {
                    String name = m.name().replace('_', ' ').toLowerCase();
                    StringBuilder sb = new StringBuilder();
                    for (String word : name.split(" ")) {
                        if (word.length() > 0) {
                            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
                        }
                    }
                    return "&f" + sb.toString().trim();
                })
                .collect(Collectors.joining("&7, "));
    }
}
