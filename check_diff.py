import os

with open("LegendaryQuestManager_backup.java", "r", encoding="utf-8") as f:
    old = f.read()

with open("LQM.java", "r", encoding="utf-8") as f:
    new = f.read()

print("lily in old:", "lily" in old.lower())
print("lily in new:", "lily" in new.lower())
print("lightning in old:", "lightning" in old.lower())
print("lightning in new:", "lightning" in new.lower())
