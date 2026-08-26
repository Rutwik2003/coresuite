import os

with open("LegendaryQuestManager_backup.java", "r", encoding="utf-8") as f:
    old = f.read()

with open("LQM.java", "r", encoding="utf-16") as f:
    new = f.read()

for i, line in enumerate(new.splitlines()):
    if "lily" in line.lower() or "lightning" in line.lower() or "sound" in line.lower():
        print(f"NEW LINE {i}: {line.strip()}")
