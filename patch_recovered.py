import os

path = "recovered_LQM.java"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

content = content.replace("item.getType() == Material.HEART_OF_THE_SEA", "(item.getType() == Material.HEART_OF_THE_SEA || item.getType() == Material.RECOVERY_COMPASS || item.getType() == Material.COMPASS)")
content = content.replace("new ItemStack(Material.HEART_OF_THE_SEA)", "new ItemStack(Material.RECOVERY_COMPASS)")

# Fix access modifier just in case
content = content.replace("private void processChatSync", "public void processChatSync")

with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("Patched recovered_LQM.java")
