import os

with open("LQM.java", "r", encoding="utf-16") as f:
    lqm = f.read()

with open("recovered_LQM.java", "r", encoding="utf-8") as f:
    rec = f.read()

# Let's extract all playSound lines from both files
lqm_sounds = [line.strip() for line in lqm.splitlines() if "playSound" in line]
rec_sounds = [line.strip() for line in rec.splitlines() if "playSound" in line]

print("LQM sounds:")
for s in lqm_sounds: print(s)
print("\nREC sounds:")
for s in rec_sounds: print(s)
