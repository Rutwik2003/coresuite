import os

path = "src/main/java/net/rocky/coresuite/module/customfishing/LegendaryQuestManager.java"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

# 1. Lily pad logic before spawnLegendaryNPC
lily_logic = """
                    org.bukkit.block.Block feetBlock = finalSpawnLoc.getBlock();
                    if (feetBlock.getType().isAir()) {
                        org.bukkit.block.Block below = feetBlock.getRelative(org.bukkit.block.BlockFace.DOWN);
                        if (below.getType() == Material.WATER || below.getType() == Material.SEAGRASS || below.getType() == Material.KELP_PLANT || below.getType() == Material.KELP) {
                            feetBlock.setType(Material.LILY_PAD);
                            module.getLegendaryNPCManager().registerLilyPad(u, feetBlock.getLocation());
                        }
                    } else if (feetBlock.getType() == Material.WATER || feetBlock.getType() == Material.SEAGRASS || feetBlock.getType() == Material.KELP_PLANT || feetBlock.getType() == Material.KELP) {
                        feetBlock.setType(Material.LILY_PAD);
                        module.getLegendaryNPCManager().registerLilyPad(u, feetBlock.getLocation());
                        finalSpawnLoc.add(0, 0.5, 0);
                    }
                    module.getLegendaryNPCManager().spawnLegendaryNPC(u, finalSpawnLoc);"""
                    
content = content.replace("module.getLegendaryNPCManager().spawnLegendaryNPC(u, finalSpawnLoc);", lily_logic.strip())

# 2. Punish sound
punish_target = "player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);"
punish_replace = "player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1f);\n        player.getWorld().strikeLightningEffect(player.getLocation());"
content = content.replace(punish_target, punish_replace)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("Changes applied successfully!")
