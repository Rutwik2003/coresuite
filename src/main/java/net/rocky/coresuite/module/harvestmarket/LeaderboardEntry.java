package net.rocky.coresuite.module.harvestmarket;

public class LeaderboardEntry {
    public final String playerName;
    public final int totalSold;
    public final int rank;

    public LeaderboardEntry(String playerName, int totalSold, int rank) {
        this.playerName = playerName;
        this.totalSold = totalSold;
        this.rank = rank;
    }
}
