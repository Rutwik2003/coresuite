with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\CustomFishingModule.java", "r", encoding="utf-8") as f:
    content = f.read()

import re

old_loop = """                                      ItemStack itemToAdd = leftover.get(0).clone();
                                      boolean addedToBag = false;
                                      for (int i = 0; i < bag.length; i++) {"""

new_loop = """                                      ItemStack itemToAdd = leftover.get(0).clone();
                                      boolean addedToBag = false;
                                      FishData bagData = playerData.get(player.getUniqueId());
                                      int unlockedPages = bagData != null ? Math.max(0, bagData.bagPages) : 0;
                                      int maxSlots = Math.min(bag.length, unlockedPages * 45);
                                      for (int i = 0; i < maxSlots; i++) {"""

content = content.replace(old_loop, new_loop)

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\CustomFishingModule.java", "w", encoding="utf-8") as f:
    f.write(content)
