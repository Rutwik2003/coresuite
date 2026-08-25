import sys
import re

lqm_path = "c:/Users/rutwi/OneDrive - MSFT/Desktop/coresuit/src/main/java/net/rocky/coresuite/module/customfishing/LegendaryQuestManager.java"
cfm_path = "c:/Users/rutwi/OneDrive - MSFT/Desktop/coresuit/src/main/java/net/rocky/coresuite/module/customfishing/CustomFishingModule.java"
fem_path = "c:/Users/rutwi/OneDrive - MSFT/Desktop/coresuit/src/main/java/net/rocky/coresuite/module/customfishing/FishingEnchantManager.java"

# 1. Fix CustomFishingModule.java (compass drop and getBaitManager)
with open(cfm_path, "r", encoding="utf-8") as f:
    cfm = f.read()

cfm = cfm.replace("private BaitManager baitManager;", "private BaitManager baitManager;\n    public BaitManager getBaitManager() { return baitManager; }")

drop_chance_old = '''double dropChance = config.getDouble("legendary-quest.map-drop-chance", 0.0002);
            if (random.nextDouble() <= dropChance) {'''

drop_chance_new = '''double baseDropChance = config.getDouble("legendary-quest.map-drop-chance", 0.0002);
            double bonusChance = 0.0;
            if (activeBait != null && config.contains("baits." + activeBait.baitId + ".catch-chance-bonus")) {
                bonusChance += config.getDouble("baits." + activeBait.baitId + ".catch-chance-bonus", 0.0) / 100.0;
            }
            if (enchantManager != null) {
                int thLevel = enchantManager.getEnchantLevel(player.getInventory().getItemInMainHand(), net.rocky.coresuite.module.customfishing.FishingEnchantManager.CustomEnchant.TREASURE_HUNTER);
                bonusChance += (thLevel * 0.0005);
            }
            double finalChance = baseDropChance + bonusChance;
            finalChance = Math.min(finalChance, 0.005); // Cap at 0.5%
            
            if (random.nextDouble() <= finalChance) {'''

cfm = cfm.replace(drop_chance_old, drop_chance_new)

with open(cfm_path, "w", encoding="utf-8") as f:
    f.write(cfm)


# 2. Fix LegendaryQuestManager.java (particle, enchants, potions)
with open(lqm_path, "r", encoding="utf-8") as f:
    lqm = f.read()

lqm = lqm.replace("org.bukkit.Particle.WATER_SPLASH", "org.bukkit.Particle.SPLASH")
lqm = lqm.replace("module.getEnchantManager().applyEnchant(rod, CustomEnchant.TIRELESS_CASTER, newLevel);", "module.getEnchantManager().setEnchantLevel(rod, CustomEnchant.TIRELESS_CASTER, newLevel);")
lqm = lqm.replace("PotionEffectType.SLOW_DIGGING", "PotionEffectType.MINING_FATIGUE")
lqm = lqm.replace("PotionEffectType.CONFUSION", "PotionEffectType.NAUSEA")

with open(lqm_path, "w", encoding="utf-8") as f:
    f.write(lqm)

print("Fixes applied.")
