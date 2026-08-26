import os

with open("LQM.java", "r", encoding="utf-16") as f:
    lines = f.readlines()

in_ritual = False
for line in lines:
    if "private void performRitual" in line:
        in_ritual = True
    if in_ritual:
        print(line.rstrip())
        if "spawnNPC" in line:
            break
