package net.rocky.coresuite.module.harvestmarket;

import org.bukkit.Material;

public class DailyBounty {
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
