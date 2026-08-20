with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\CustomFishingModule.java", "r", encoding="utf-8") as f:
    content = f.read()

import re

# We are going to replace exactly from `if (!addedToBag) {` block down to `player.sendMessage(`
pattern = r"if \(!addedToBag\) \{\s*for \(org\.bukkit\.inventory\.ItemStack drop : leftover\.values\(\)\) \{\s*player\.getWorld\(\)\.dropItemNaturally\(player\.getLocation\(\), drop\);\s*\}\s*TextUtil\.sendMessage\(player, \"&cYour inventory and bag are full! The fish dropped on the ground!\"\);\s*\}.*?(player\.sendMessage\(TextUtil\.colorize\(config\.getString\(\"prefix\", \"&8\[&b&lFishing&8\] &r\"\))"

new_block = """if (!addedToBag) {
                                          for (org.bukkit.inventory.ItemStack drop : leftover.values()) {
                                              player.getWorld().dropItemNaturally(player.getLocation(), drop);
                                          }
                                          TextUtil.sendMessage(player, "&cYour inventory and bag are full! The fish dropped on the ground!");
                                      }
                                  }
                              }
                          
                              \\1"""

content = re.sub(pattern, new_block, content, flags=re.DOTALL)

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\CustomFishingModule.java", "w", encoding="utf-8") as f:
    f.write(content)
