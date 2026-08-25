import re

lqm_path = "c:/Users/rutwi/OneDrive - MSFT/Desktop/coresuit/src/main/java/net/rocky/coresuite/module/customfishing/LegendaryQuestManager.java"

with open(lqm_path, "r", encoding="utf-8") as f:
    lqm = f.read()

# Fix mythic_chum bug
lqm = lqm.replace('"mythical_chum"', '"mythic_chum"')

with open(lqm_path, "w", encoding="utf-8") as f:
    f.write(lqm)

print("Fixed mythic_chum bug.")
