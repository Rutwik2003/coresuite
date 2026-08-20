with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\CustomFishingModule.java", "r", encoding="utf-8") as f:
    content = f.read()

import re

old_drop = """                                    for (int i = 0; i < bag.length; i++) {
                                        if (bag[i] == null || bag[i].getType() == org.bukkit.Material.AIR) {
                                            bag[i] = itemToAdd;
                                            fishBagManager.updateBag(player.getUniqueId(), bag);
                                            fishBagManager.saveBag(player.getUniqueId());
                                            addedToBag = true;
                                            leftover.clear();
                                            break;
                                        }
                                    }
                                }
                            }"""

new_drop = """                                    for (int i = 0; i < bag.length; i++) {
                                        if (bag[i] == null || bag[i].getType() == org.bukkit.Material.AIR) {
                                            bag[i] = itemToAdd;
                                            fishBagManager.updateBag(player.getUniqueId(), bag);
                                            addedToBag = true;
                                            leftover.clear();
                                            break;
                                        }
                                    }
                                    if (!addedToBag) {
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

content = content.replace(old_drop, new_drop)

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\CustomFishingModule.java", "w", encoding="utf-8") as f:
    f.write(content)
