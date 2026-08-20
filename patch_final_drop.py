import re

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\CustomFishingModule.java", "r", encoding="utf-8") as f:
    content = f.read()

# 1. Fix the bag loop limit
content = re.sub(
    r"boolean addedToBag = false;\s*for \(int i = 0; i < bag\.length; i\+\+\) {",
    """boolean addedToBag = false;
                                    FishData bagData = playerData.get(player.getUniqueId());
                                    int unlockedPages = bagData != null ? Math.max(0, bagData.bagPages) : 0;
                                    int maxSlots = Math.min(bag.length, unlockedPages * 45);
                                    for (int i = 0; i < maxSlots; i++) {""",
    content
)

# 2. Delete the extra "else" blocks that drop items when inventory is NOT full
# The block starts right after the `if (!addedToBag)` block finishes.
# Let's find the `} else {` right after `TextUtil.sendMessage(player, "&cYour inventory and bag are full! The fish dropped on the ground!");`
# and delete it and the following `else` block too.

# Actually, the entire block is:
# if (!leftover.isEmpty()) { ... } else { ... }
# Since `leftover` is empty in the `else` block, we don't need to do ANYTHING in the `else` block.

pattern = r"(\}\s*else\s*\{\s*for \(org\.bukkit\.inventory\.ItemStack drop : leftover\.values\(\)\) \{\s*player\.getWorld\(\)\.dropItemNaturally\(player\.getLocation\(\), drop\);\s*\}\s*TextUtil\.sendMessage\(player, \"&cYour inventory is full! The fish dropped on the ground!\"\);\s*\})"
content = re.sub(pattern, "", content)

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\CustomFishingModule.java", "w", encoding="utf-8") as f:
    f.write(content)
