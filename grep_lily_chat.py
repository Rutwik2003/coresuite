import os
import json

for root, dirs, files in os.walk(r"C:\Users\rutwi\.gemini\antigravity\brain"):
    if "transcript_full.jsonl" in files:
        path = os.path.join(root, "transcript_full.jsonl")
        with open(path, "r", encoding="utf-8") as f:
            for line in f:
                if "lily pad" in line.lower() or "lily_pad" in line.lower():
                    data = json.loads(line)
                    if data.get("type") == "USER_INPUT":
                        print(f"USER said in {path}: {data.get('content')}")
