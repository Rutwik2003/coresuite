import json
path = r"C:\Users\rutwi\.gemini\antigravity\brain\d00b6fc9-7c5d-40ee-a189-7a1412688aa5\.system_generated\logs\transcript_full.jsonl"
with open(path, "r", encoding="utf-8") as f:
    for line in f:
        if "turnin_601" in line:
            data = json.loads(line)
            if data.get("type") == "PLANNER_RESPONSE":
                calls = data.get("tool_calls", [])
                for call in calls:
                    if call["name"] == "invoke_subagent":
                        args = call["args"]
                        if "Subagents" in args:
                            with open("subagent_prompt.txt", "w", encoding="utf-8") as out:
                                out.write(args["Subagents"][0]["Prompt"])
                            print("Wrote prompt to subagent_prompt.txt")
