package net.rocky.coresuite.module.gambling.pvpcoinflip;

import java.util.UUID;

/**
 * Represents a PVP Coinflip challenge.
 */
public class CoinflipDuel {

    public enum Status {
        OPEN, ACTIVE, COMPLETED, CANCELLED, EXPIRED
    }

    private final int id;
    private final UUID creatorUuid;
    private final String creatorName;
    private final double betAmount;
    private final boolean creatorChoseHeads;
    private final long createdAt;
    private Status status;
    private UUID opponentUuid;
    private String opponentName;
    private UUID winnerUuid;

    public CoinflipDuel(int id, UUID creatorUuid, String creatorName, double betAmount, boolean creatorChoseHeads, long createdAt) {
        this.id = id;
        this.creatorUuid = creatorUuid;
        this.creatorName = creatorName;
        this.betAmount = betAmount;
        this.creatorChoseHeads = creatorChoseHeads;
        this.createdAt = createdAt;
        this.status = Status.OPEN;
    }

    public int getId() { return id; }
    public UUID getCreatorUuid() { return creatorUuid; }
    public String getCreatorName() { return creatorName; }
    public double getBetAmount() { return betAmount; }
    public boolean isCreatorChoseHeads() { return creatorChoseHeads; }
    public long getCreatedAt() { return createdAt; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public UUID getOpponentUuid() { return opponentUuid; }
    public void setOpponentUuid(UUID opponentUuid) { this.opponentUuid = opponentUuid; }
    public String getOpponentName() { return opponentName; }
    public void setOpponentName(String opponentName) { this.opponentName = opponentName; }

    public UUID getWinnerUuid() { return winnerUuid; }
    public void setWinnerUuid(UUID winnerUuid) { this.winnerUuid = winnerUuid; }

    public double getTotalPot() { return betAmount * 2.0; }

    public String getCreatorSideName() { return creatorChoseHeads ? "Heads" : "Tails"; }
    public String getOpponentSideName() { return creatorChoseHeads ? "Tails" : "Heads"; }
}
