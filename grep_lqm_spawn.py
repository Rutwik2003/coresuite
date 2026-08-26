import os

with open("LQM.java", "r", encoding="utf-16") as f:
    for i, line in enumerate(f):
        if "spawnLoc.getBlock()" in line or "Material.WATER" in line:
            print(f"LQM.java Line {i+1}: {line.strip()}")
