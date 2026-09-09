package net.rocky.coresuite.module.gambling;

import java.util.UUID;

public record PlayerCasinoStats(
    UUID uuid,
    String playerName,
    double totalWagered,
    double totalWon,
    int gamesPlayed,
    double biggestWin,
    long lastPlayed
) {
    public double getNetProfit() {
        return totalWon - totalWagered;
    }
}
