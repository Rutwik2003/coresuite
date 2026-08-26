package net.rocky.coresuite.module.fishingrpg;

public enum FishRarity {
    COMMON("Common", "&7", 0.45, 1),
    UNCOMMON("Uncommon", "&a", 0.30, 2),
    RARE("Rare", "&9", 0.15, 3),
    EPIC("Epic", "&5", 0.08, 4),
    LEGENDARY("Legendary", "&6", 0.019, 5),
    MYTHICAL("Mythical", "&d", 0.001, 6);

    private final String displayName;
    private final String colorCode;
    private final double baseChance;
    private final int weight;

    FishRarity(String displayName, String colorCode, double baseChance, int weight) {
        this.displayName = displayName;
        this.colorCode = colorCode;
        this.baseChance = baseChance;
        this.weight = weight;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColorCode() {
        return colorCode;
    }

    public double getBaseChance() {
        return baseChance;
    }

    public int getWeight() {
        return weight;
    }

    public String getColoredName() {
        return colorCode + displayName;
    }

    public static FishRarity fromString(String name) {
        for (FishRarity rarity : values()) {
            if (rarity.name().equalsIgnoreCase(name) || rarity.displayName.equalsIgnoreCase(name)) {
                return rarity;
            }
        }
        return COMMON;
    }
}
