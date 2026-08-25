import json
path = r"C:\Users\rutwi\.gemini\antigravity\brain\d00b6fc9-7c5d-40ee-a189-7a1412688aa5\.system_generated\logs\transcript_full.jsonl"
with open(path, "r", encoding="utf-8") as f:
    for line in f:
        if "java-writer" in line and "invoke_subagent" in line:
            print("Found invoke_subagent")
        if "Started subagent" in line:
            print(json.loads(line).get("content")[:150])
