import os

path = "src/main/java/net/rocky/coresuite/module/customfishing/LegendaryQuestManager.java"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

ritual_target = """if (tick == 0) {
                      player.getWorld().playSound(finalSpawnLoc, Sound.BLOCK_WATER_AMBIENT, 1f, 0.5f);
                      TextUtil.sendMessage(player, "&3&oThe waters begin to churn violently...");"""

ritual_replacement = """if (tick == 0) {
                      player.getWorld().strikeLightningEffect(finalSpawnLoc);
                      player.getWorld().playSound(finalSpawnLoc, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 0.5f);
                      TextUtil.sendMessage(player, "&3&oThe waters begin to churn violently...");"""

content = content.replace(ritual_target, ritual_replacement)

with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("Added strikeLightningEffect to ritual")
