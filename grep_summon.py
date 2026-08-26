import os

with open("LQM.java", "r", encoding="utf-16") as f:
    for i, line in enumerate(f):
        if "Ancient Angler" in line and "summoned" in line:
            print(f"{i+1}: {line.strip()}")
