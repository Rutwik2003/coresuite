import re

lqm_path = "c:/Users/rutwi/OneDrive - MSFT/Desktop/coresuit/src/main/java/net/rocky/coresuite/module/customfishing/LegendaryQuestManager.java"

with open(lqm_path, "r", encoding="utf-8") as f:
    lqm = f.read()

old_logic = '''ItemStack rod = player.getInventory().getItemInMainHand();
                    RodTier tier = module.getEnchantManager().getRodTier(rod);
                    if (tier.ordinal() < RodTier.BRONZE.ordinal()) {
                        sendLines(player, "&e[Ancient Angler]&f Your rod is too weak to handle this mastery! Hold at least a Bronze Tier rod and talk to me again.");
                        ItemStack fishItem = module.createFishItem(module.getConfig().getString("species.lava_particle.name", "Lava Particle"), "MAGMA_CREAM", 1.0, 50.0, "");
                        player.getInventory().addItem(fishItem);
                        return;
                    }
                    int currentLevel = module.getEnchantManager().getEnchantLevel(rod, CustomEnchant.TIRELESS_CASTER);
                    if (currentLevel >= 3) {'''

new_logic = '''ItemStack rod = player.getInventory().getItemInMainHand();
                    RodTier tier = module.getEnchantManager().getRodTier(rod);
                    int currentLevel = module.getEnchantManager().getEnchantLevel(rod, CustomEnchant.TIRELESS_CASTER);
                    
                    RodTier requiredTier = (currentLevel == 0) ? RodTier.BRONZE : RodTier.SILVER;
                    String tierName = (currentLevel == 0) ? "Bronze" : "Silver";
                    
                    if (tier.ordinal() < requiredTier.ordinal()) {
                        sendLines(player, "&e[Ancient Angler]&f Your rod is too weak to handle this mastery! Hold at least a " + tierName + " Tier rod and talk to me again.");
                        ItemStack fishItem = module.createFishItem(module.getConfig().getString("species.lava_particle.name", "Lava Particle"), "MAGMA_CREAM", 1.0, 50.0, "");
                        player.getInventory().addItem(fishItem);
                        return;
                    }
                    
                    if (currentLevel >= 3) {'''

lqm = lqm.replace(old_logic, new_logic)

with open(lqm_path, "w", encoding="utf-8") as f:
    f.write(lqm)

print("Updated rod tier logic.")
