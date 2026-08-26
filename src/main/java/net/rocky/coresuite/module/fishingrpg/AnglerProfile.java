package net.rocky.coresuite.module.fishingrpg;

import java.util.UUID;

public class AnglerProfile {
    private final UUID uuid;
    private int level = 1;
    private int xp = 0;
    private int totalCatches = 0;
    private int bagPages = 0;
    private double fatigueScore = 0.0;
    private long fatigueLastUpdate = 0;
    private int legendaryPity = 0;
    private int skillPointsAvailable = 0;
    private int hunterPoints = 0;
    private int scavengerPoints = 0;
    private int mysticPoints = 0;

    private transient int comboCount = 0;
    private transient long lastCatchTime = 0;
    private transient boolean dirty = false;

    public AnglerProfile(UUID uuid) {
        this.uuid = uuid;
    }

    public AnglerProfile(UUID uuid, int level, int xp, int totalCatches, int bagPages, double fatigueScore, long fatigueLastUpdate, int legendaryPity, int skillPointsAvailable, int hunterPoints, int scavengerPoints, int mysticPoints) {
        this.uuid = uuid;
        this.level = level;
        this.xp = xp;
        this.totalCatches = totalCatches;
        this.bagPages = bagPages;
        this.fatigueScore = fatigueScore;
        this.fatigueLastUpdate = fatigueLastUpdate;
        this.legendaryPity = legendaryPity;
        this.skillPointsAvailable = skillPointsAvailable;
        this.hunterPoints = hunterPoints;
        this.scavengerPoints = scavengerPoints;
        this.mysticPoints = mysticPoints;
    }

    public UUID getUuid() { return uuid; }
    
    public int getLevel() { return level; }
    public void setLevel(int level) { this.level = level; this.dirty = true; }
    
    public int getXp() { return xp; }
    public void setXp(int xp) { this.xp = xp; this.dirty = true; }
    
    public int getTotalCatches() { return totalCatches; }
    public void setTotalCatches(int totalCatches) { this.totalCatches = totalCatches; this.dirty = true; }
    
    public int getBagPages() { return bagPages; }
    public void setBagPages(int bagPages) { this.bagPages = bagPages; this.dirty = true; }
    
    public double getFatigueScore() { return fatigueScore; }
    public void setFatigueScore(double fatigueScore) { this.fatigueScore = fatigueScore; this.dirty = true; }
    
    public long getFatigueLastUpdate() { return fatigueLastUpdate; }
    public void setFatigueLastUpdate(long fatigueLastUpdate) { this.fatigueLastUpdate = fatigueLastUpdate; this.dirty = true; }
    
    public int getLegendaryPity() { return legendaryPity; }
    public void setLegendaryPity(int legendaryPity) { this.legendaryPity = legendaryPity; this.dirty = true; }
    
    public int getSkillPointsAvailable() { return skillPointsAvailable; }
    public void setSkillPointsAvailable(int skillPointsAvailable) { this.skillPointsAvailable = skillPointsAvailable; this.dirty = true; }
    
    public int getHunterPoints() { return hunterPoints; }
    public void setHunterPoints(int hunterPoints) { this.hunterPoints = hunterPoints; this.dirty = true; }
    
    public int getScavengerPoints() { return scavengerPoints; }
    public void setScavengerPoints(int scavengerPoints) { this.scavengerPoints = scavengerPoints; this.dirty = true; }
    
    public int getMysticPoints() { return mysticPoints; }
    public void setMysticPoints(int mysticPoints) { this.mysticPoints = mysticPoints; this.dirty = true; }
    
    public int getComboCount() { return comboCount; }
    public void setComboCount(int comboCount) { this.comboCount = comboCount; }
    
    public long getLastCatchTime() { return lastCatchTime; }
    public void setLastCatchTime(long lastCatchTime) { this.lastCatchTime = lastCatchTime; }
    
    public boolean isDirty() { return dirty; }
    public void setDirty(boolean dirty) { this.dirty = dirty; }

    public int getRequiredXP() {
        return level * level * 100;
    }

    public boolean addXP(int amount) {
        this.xp += amount;
        this.dirty = true;
        boolean leveledUp = false;
        
        while (this.xp >= getRequiredXP()) {
            this.xp -= getRequiredXP();
            this.level++;
            this.skillPointsAvailable++;
            leveledUp = true;
        }
        return leveledUp;
    }

    public double getFishingPower() {
        return 1.0 + (hunterPoints * 0.05);
    }

    public double getFishingLuck() {
        return 1.0 + (scavengerPoints * 0.05);
    }

    public double getWeightBonus() {
        return 1.0 + (hunterPoints * 0.03);
    }

    public double getBaitSaver() {
        return mysticPoints * 0.05;
    }

    public double getComboMultiplier() {
        return Math.min(5.0, 1.0 + (comboCount * 0.1));
    }
}
