import os

path = "src/main/java/net/rocky/coresuite/module/customfishing/LegendaryQuestManager.java"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

# Remove lily pad logic
content = content.replace("feetBlock.setType(Material.LILY_PAD);", "// feetBlock.setType(Material.LILY_PAD); // removed to fix lily pad bug")

# Change default summon sound
content = content.replace('"ENTITY_WITHER_SPAWN"', '"ENTITY_LIGHTNING_BOLT_THUNDER"')
content = content.replace('Sound.ENTITY_WITHER_SPAWN', 'Sound.ENTITY_LIGHTNING_BOLT_THUNDER')

with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("Removed lily pad logic and changed summon sound default to lightning")
