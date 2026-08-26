import os

path = "src/main/java/net/rocky/coresuite/module/customfishing/LegendaryQuestManager.java"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

target = "player.playSound(finalSpawnLoc, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 0.8f);"
replacement = "player.playSound(finalSpawnLoc, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 0.8f);\n                    player.getWorld().playSound(finalSpawnLoc, Sound.ENTITY_WITHER_SPAWN, 0.5f, 1f);"
content = content.replace(target, replacement)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("Added Wither sound to summoning ritual")
