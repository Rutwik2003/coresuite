with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\CustomFishingModule.java", "r", encoding="utf-8") as f:
    content = f.read()

import re

# Update dropItem to dropItemNaturally and add a message
old_drop_logic = """                                    if (!addedToBag) {
                                        for (org.bukkit.inventory.ItemStack drop : leftover.values()) {
                                            player.getWorld().dropItem(player.getLocation(), drop);
                                        }
                                    }
                                } else {
                                    for (org.bukkit.inventory.ItemStack drop : leftover.values()) {
                                        player.getWorld().dropItem(player.getLocation(), drop);
                                    }
                                }
                            } else {
                                for (org.bukkit.inventory.ItemStack drop : leftover.values()) {
                                    player.getWorld().dropItem(player.getLocation(), drop);
                                }
                            }"""

new_drop_logic = """                                    if (!addedToBag) {
                                        for (org.bukkit.inventory.ItemStack drop : leftover.values()) {
                                            player.getWorld().dropItemNaturally(player.getLocation(), drop);
                                        }
                                        TextUtil.sendMessage(player, "&cYour inventory and bag are full! The fish dropped on the ground!");
                                    }
                                } else {
                                    for (org.bukkit.inventory.ItemStack drop : leftover.values()) {
                                        player.getWorld().dropItemNaturally(player.getLocation(), drop);
                                    }
                                    TextUtil.sendMessage(player, "&cYour inventory is full! The fish dropped on the ground!");
                                }
                            } else {
                                for (org.bukkit.inventory.ItemStack drop : leftover.values()) {
                                    player.getWorld().dropItemNaturally(player.getLocation(), drop);
                                }
                                TextUtil.sendMessage(player, "&cYour inventory is full! The fish dropped on the ground!");
                            }"""

content = content.replace(old_drop_logic, new_drop_logic)

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\CustomFishingModule.java", "w", encoding="utf-8") as f:
    f.write(content)
