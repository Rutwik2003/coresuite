import os
path = "src/main/java/net/rocky/coresuite/module/customfishing/LegendaryQuestManager.java"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

content = content.replace(
    "if (item != null && item.getType() == Material.COMPASS && item.hasItemMeta()) {",
    "if (item != null && (item.getType() == Material.RECOVERY_COMPASS || item.getType() == Material.COMPASS) && item.hasItemMeta()) {"
)
content = content.replace(
    "if (item.getType() == Material.COMPASS) {",
    "if (item.getType() == Material.RECOVERY_COMPASS || item.getType() == Material.COMPASS) {"
)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)
