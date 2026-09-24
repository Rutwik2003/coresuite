package net.rocky.coresuite.module.worldbosses.boss;

import org.bukkit.entity.EntityType;

public enum BossType {
    VALERIUS("valerius", "Valerius, The Ashen Vanguard", EntityType.WITHER_SKELETON, "ashen"),
    AETHELGARD("aethelgard", "Aethelgard, The Root-Bound King", EntityType.HUSK, "royal_bark"),
    XOLTHOS("xolthos", "Xol'thos, The Weeping Widow", EntityType.CAVE_SPIDER, "phantom_silk"),
    BOREALIS("borealis", "Borealis, The Permafrost Tyrant", EntityType.STRAY, "frost_core");

    private final String configKey;
    private final String defaultDisplayName;
    private final EntityType entityType;
    private final String shardId;

    BossType(String configKey, String defaultDisplayName, EntityType entityType, String shardId) {
        this.configKey = configKey;
        this.defaultDisplayName = defaultDisplayName;
        this.entityType = entityType;
        this.shardId = shardId;
    }

    public String getConfigKey() {
        return configKey;
    }

    public String getDefaultDisplayName() {
        return defaultDisplayName;
    }

    public EntityType getEntityType() {
        return entityType;
    }

    public String getShardId() {
        return shardId;
    }

    public static BossType fromString(String str) {
        if (str == null) return null;
        for (BossType type : values()) {
            if (type.name().equalsIgnoreCase(str) || type.configKey.equalsIgnoreCase(str)) {
                return type;
            }
        }
        return null;
    }
}
