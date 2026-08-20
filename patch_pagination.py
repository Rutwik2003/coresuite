import re

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\CustomFishingModule.java", "r", encoding="utf-8") as f:
    content = f.read()

# Remove player.closeInventory() for slot 45 and 53 in Fish Bag
old_pagination = """                        if (event.getRawSlot() == 45 && page > 0) {
                            player.closeInventory();
                            fishBagManager.openBag(player, totalPages, page - 1);
                        } else if (event.getRawSlot() == 53 && page < totalPages - 1) {
                            player.closeInventory();
                            fishBagManager.openBag(player, totalPages, page + 1);
                        }"""
                        
new_pagination = """                        if (event.getRawSlot() == 45 && page > 0) {
                            fishBagManager.openBag(player, totalPages, page - 1);
                        } else if (event.getRawSlot() == 53 && page < totalPages - 1) {
                            fishBagManager.openBag(player, totalPages, page + 1);
                        }"""

content = content.replace(old_pagination, new_pagination)

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\CustomFishingModule.java", "w", encoding="utf-8") as f:
    f.write(content)
