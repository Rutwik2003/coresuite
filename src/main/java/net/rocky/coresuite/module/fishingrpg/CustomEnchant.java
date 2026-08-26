package net.rocky.coresuite.module.fishingrpg;

public enum CustomEnchant {
    LURE_OF_THE_DEEP("Lure of the Deep", 3, "lure_of_the_deep"),
    HEAVYWEIGHT("Heavyweight", 5, "heavyweight"),
    DOUBLE_HOOK("Double Hook", 3, "double_hook"),
    TREASURE_HUNTER("Treasure Hunter", 3, "treasure_hunter"),
    TIRELESS_CASTER("Tireless Caster", 3, "tireless_caster");

    private final String displayName;
    private final int maxLevel;
    private final String configKey;
    private final String namespacedKeyString;

    CustomEnchant(String displayName, int maxLevel, String configKey) {
        this.displayName = displayName;
        this.maxLevel = maxLevel;
        this.configKey = configKey;
        this.namespacedKeyString = "fishingrpg_enchant_" + configKey;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMaxLevel() {
        return maxLevel;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getKeyString() {
        return namespacedKeyString;
    }

    public static String toRoman(int num) {
        String[] roman = {"O", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};
        if (num > 0 && num < roman.length) {
            return roman[num];
        }
        return String.valueOf(num);
    }
}
