with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\CustomFishingModule.java", "r", encoding="utf-8") as f:
    content = f.read()

import re

old_give = """                        ItemStack fishItem = createFishItem(name, material, weight, pricePerKg, itemModel);
                        fishItem.setAmount(giveAmount);
                        target.getInventory().addItem(fishItem);
                        TextUtil.sendMessage(player, "&aGave " + giveAmount + "x " + fishId + " to " + target.getName() + ".");"""

new_give = """                        ItemStack fishItem = createFishItem(name, material, weight, pricePerKg, itemModel);
                        fishItem.setAmount(giveAmount);
                        java.util.Map<Integer, org.bukkit.inventory.ItemStack> leftovers = target.getInventory().addItem(fishItem);
                        for (org.bukkit.inventory.ItemStack drop : leftovers.values()) {
                            target.getWorld().dropItemNaturally(target.getLocation(), drop);
                        }
                        if (!leftovers.isEmpty()) TextUtil.sendMessage(target, "&cYour inventory was full, so some fish dropped on the floor!");
                        TextUtil.sendMessage(player, "&aGave " + giveAmount + "x " + fishId + " to " + target.getName() + ".");"""

content = content.replace(old_give, new_give)

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\CustomFishingModule.java", "w", encoding="utf-8") as f:
    f.write(content)
