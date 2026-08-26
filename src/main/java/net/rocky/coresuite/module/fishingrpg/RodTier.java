package net.rocky.coresuite.module.fishingrpg;

public enum RodTier {
    BASIC("&7Basic", 1.0),
    BRONZE("&6Bronze", 1.15),
    SILVER("&fSilver", 1.30),
    GOLD("&eGold", 1.50),
    DIAMOND("&bDiamond", 1.75),
    NETHERITE("&4Netherite", 2.0);

    private final String displayName;
    private final double enchantMultiplier;

    RodTier(String displayName, double enchantMultiplier) {
        this.displayName = displayName;
        this.enchantMultiplier = enchantMultiplier;
    }

    public String getDisplayName() {
        return displayName;
    }

    public double getEnchantMultiplier() {
        return enchantMultiplier;
    }

    public RodTier getNextTier() {
        int nextIndex = this.ordinal() + 1;
        if (nextIndex < values().length) {
            return values()[nextIndex];
        }
        return null;
    }

    public static RodTier fromString(String str) {
        if (str == null) return null;
        try {
            return valueOf(str.toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
