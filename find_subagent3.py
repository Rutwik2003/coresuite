import json
path = r"C:\Users\rutwi\.gemini\antigravity\brain\d00b6fc9-7c5d-40ee-a189-7a1412688aa5\.system_generated\logs\transcript_full.jsonl"
with open(path, "r", encoding="utf-8") as f:
    found = False
    for line in f:
        if found:
            print("NEXT LINE:")
            print(line[:500])
            found = False
        if "invoke_subagent" in line and "java-writer" in line:
            found = True
