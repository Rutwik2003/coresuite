with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\CustomFishingModule.java", "r", encoding="utf-8") as f:
    content = f.read()

import re

old_logic = """                            if (!leftover.isEmpty()) {
                                ItemStack[] bag = fishBagManager.getBag(player.getUniqueId());
                                if (bag != null) {
                                    ItemStack itemToAdd = leftover.get(0).clone();
                                    boolean addedToBag = false;
                                    FishData bagData = playerData.get(player.getUniqueId());
                                    int unlockedPages = bagData != null ? Math.max(0, bagData.bagPages) : 0;
                                    int maxSlots = Math.min(bag.length, unlockedPages * 45);
                                    for (int i = 0; i < maxSlots; i++) {
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

new_logic = """                            if (!leftover.isEmpty()) {
                                ItemStack[] bag = fishBagManager.getBag(player.getUniqueId());
                                if (bag != null) {
                                    ItemStack itemToAdd = leftover.get(0).clone();
                                    boolean addedToBag = false;
                                    FishData bagData = playerData.get(player.getUniqueId());
                                    int unlockedPages = bagData != null ? Math.max(0, bagData.bagPages) : 0;
                                    int maxSlots = Math.min(bag.length, unlockedPages * 45);
                                    for (int i = 0; i < maxSlots; i++) {
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
                            }"""

content = content.replace(old_logic, new_logic)

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\CustomFishingModule.java", "w", encoding="utf-8") as f:
    f.write(content)
