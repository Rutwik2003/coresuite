import os

path = "src/main/java/net/rocky/coresuite/module/customfishing/LegendaryQuestManager.java"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

content = content.replace(
    "org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE,",
    "org.bukkit.attribute.Attribute.ATTACK_DAMAGE,"
)

# Replace the AttributeModifier constructor with the modern NamespacedKey one
import re
pattern = r"new org\.bukkit\.attribute\.AttributeModifier\(\s*UUID\.randomUUID\(\),\s*\"stormcaller_damage\",\s*12\.0,\s*org\.bukkit\.attribute\.AttributeModifier\.Operation\.ADD_NUMBER,\s*org\.bukkit\.inventory\.EquipmentSlot\.HAND\s*\)"
replacement = "new org.bukkit.attribute.AttributeModifier(org.bukkit.NamespacedKey.minecraft(\"stormcaller_damage\"), 12.0, org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER, org.bukkit.inventory.EquipmentSlotGroup.MAINHAND)"
content = re.sub(pattern, replacement, content)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("Patched compilation errors in LegendaryQuestManager.java")
