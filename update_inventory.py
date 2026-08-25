import re

lqm_path = "c:/Users/rutwi/OneDrive - MSFT/Desktop/coresuit/src/main/java/net/rocky/coresuite/module/customfishing/LegendaryQuestManager.java"

with open(lqm_path, "r", encoding="utf-8") as f:
    lqm = f.read()

# Update The Thrill reward adding logic
old_thrill = '''baitReward.setAmount(3);
                        player.getInventory().addItem(baitReward);'''

new_thrill = '''baitReward.setAmount(3);
                        java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(baitReward);
                        for (ItemStack left : leftover.values()) {
                            player.getWorld().dropItem(player.getLocation(), left);
                        }'''

lqm = lqm.replace(old_thrill, new_thrill)

# Update The Quiet reward adding logic
old_quiet = '''anchor.setItemMeta(anchorMeta);
                        player.getInventory().addItem(anchor);'''

new_quiet = '''anchor.setItemMeta(anchorMeta);
                        java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(anchor);
                        for (ItemStack left : leftover.values()) {
                            player.getWorld().dropItem(player.getLocation(), left);
                        }'''

lqm = lqm.replace(old_quiet, new_quiet)

with open(lqm_path, "w", encoding="utf-8") as f:
    f.write(lqm)

print("Updated inventory additions.")
