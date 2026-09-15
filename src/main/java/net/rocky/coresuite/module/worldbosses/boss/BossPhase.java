package net.rocky.coresuite.module.worldbosses.boss;

import net.kyori.adventure.bossbar.BossBar;

public class BossPhase {
    private final int phaseNumber;
    private final double healthPercentMin;
    private final String name;
    private final BossBar.Color barColor;
    private final String domainEffect;

    public BossPhase(int phaseNumber, double healthPercentMin, String name, BossBar.Color barColor, String domainEffect) {
        this.phaseNumber = phaseNumber;
        this.healthPercentMin = healthPercentMin;
        this.name = name;
        this.barColor = barColor;
        this.domainEffect = domainEffect;
    }

    public int getPhaseNumber() {
        return phaseNumber;
    }

    public double getHealthPercentMin() {
        return healthPercentMin;
    }

    public String getName() {
        return name;
    }

    public BossBar.Color getBarColor() {
        return barColor;
    }

    public String getDomainEffect() {
        return domainEffect;
    }
}
