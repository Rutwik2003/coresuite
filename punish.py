import os

with open("src/main/java/net/rocky/coresuite/module/customfishing/LegendaryQuestManager.java", "r", encoding="utf-8") as f:
    lines = f.readlines()

in_punish = False
for line in lines:
    if "private void sendLinesAndPunish(Player player, String... lines)" in line:
        in_punish = True
    if in_punish:
        print(line.rstrip())
        if "cancel();" in line:
            break
