with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\CustomFishingModule.java", "r", encoding="utf-8") as f:
    content = f.read()

import re

old_text = """                                      if (!addedToBag) {
                                          for (org.bukkit.inventory.ItemStack drop : leftover.values()) {
                                              player.getWorld().dropItemNaturally(player.getLocation(), drop);
                                          }
                                          TextUtil.sendMessage(player, "&cYour inventory and bag are full! The fish dropped on the ground!");
                                      }
                                  
                              
                          

                          
                              player.sendMessage(TextUtil.colorize(config.getString("prefix", "&8[&b&lFishing&8] &r") + "&7You caught a &e" + name + " &8(&b" + weight + " kg&8)"));"""

new_text = """                                      if (!addedToBag) {
                                          for (org.bukkit.inventory.ItemStack drop : leftover.values()) {
                                              player.getWorld().dropItemNaturally(player.getLocation(), drop);
                                          }
                                          TextUtil.sendMessage(player, "&cYour inventory and bag are full! The fish dropped on the ground!");
                                      }
                                  }
                              }
                          
                              player.sendMessage(TextUtil.colorize(config.getString("prefix", "&8[&b&lFishing&8] &r") + "&7You caught a &e" + name + " &8(&b" + weight + " kg&8)"));"""

content = content.replace(old_text, new_text)

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\CustomFishingModule.java", "w", encoding="utf-8") as f:
    f.write(content)
