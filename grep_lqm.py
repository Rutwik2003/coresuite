import os

with open("LQM.java", "r", encoding="utf-16") as f:
    for i, line in enumerate(f):
        if "lily" in line.lower() or "sound" in line.lower() or "lightning" in line.lower():
            print(f"LQM.java Line {i+1}: {line.strip()}")
