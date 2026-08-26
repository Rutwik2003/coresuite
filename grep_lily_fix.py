import os
import json

path = r"C:\Users\rutwi\.gemini\antigravity\brain\d00b6fc9-7c5d-40ee-a189-7a1412688aa5\.system_generated\logs\transcript_full.jsonl"
with open(path, "r", encoding="utf-8") as f:
    lines = f.readlines()

for i, line in enumerate(lines):
    if "as soon as he spawns the lily pad breaks" in line:
        for j in range(i+1, i+10):
            if j < len(lines):
                data = json.loads(lines[j])
                if data.get("type") == "PLANNER_RESPONSE":
                    print(f"Assistant said: {data.get('content')}")
                    for call in data.get("tool_calls", []):
                        print("Tool call:", call["name"])
                        if "CodeContent" in call.get("args", {}):
                            print(call["args"]["CodeContent"][:200])
                        elif "ReplacementContent" in call.get("args", {}):
                            print(call["args"]["ReplacementContent"][:200])
                        elif "CommandLine" in call.get("args", {}):
                            print(call["args"]["CommandLine"])
        break
