import os

with open("LegendaryQuestManager_backup.java", "r", encoding="utf-8") as f:
    for i, line in enumerate(f):
        if "Ancient Angler" in line and "summoned" in line:
            print(f"{i+1}: {line.strip()}")
