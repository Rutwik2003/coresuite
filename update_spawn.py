import re

lqm_path = "c:/Users/rutwi/OneDrive - MSFT/Desktop/coresuit/src/main/java/net/rocky/coresuite/module/customfishing/LegendaryQuestManager.java"

with open(lqm_path, "r", encoding="utf-8") as f:
    lqm = f.read()

# 1. Update Spawning Logic (no highest block Y, prefer water)
old_spawn = '''Location spawnLoc = player.getLocation().add(player.getLocation().getDirection().multiply(2));
        spawnLoc.setY(player.getWorld().getHighestBlockYAt(spawnLoc) + 1);'''

new_spawn = '''Location spawnLoc = player.getLocation().clone();
        boolean foundWater = false;
        for (int x = -3; x <= 3 && !foundWater; x++) {
            for (int z = -3; z <= 3 && !foundWater; z++) {
                for (int y = -2; y <= 2 && !foundWater; y++) {
                    org.bukkit.block.Block b = player.getLocation().clone().add(x, y, z).getBlock();
                    if (b.getType() == Material.WATER) {
                        spawnLoc = b.getLocation().add(0, 1, 0);
                        foundWater = true;
                    }
                }
            }
        }
        if (!foundWater) {
            spawnLoc = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(2));
            spawnLoc.setY(player.getLocation().getY());
        }'''

lqm = lqm.replace(old_spawn, new_spawn)

# 2. Update delay in runTaskTimer to be longer (80 ticks = 4 seconds instead of 60 ticks = 3 seconds)
lqm = lqm.replace(".runTaskTimer(plugin, 10L, 60L);", ".runTaskTimer(plugin, 10L, 80L);")

with open(lqm_path, "w", encoding="utf-8") as f:
    f.write(lqm)

print("Updated spawning and delay.")
