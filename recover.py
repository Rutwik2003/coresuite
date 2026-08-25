import json
path = r"C:\Users\rutwi\.gemini\antigravity\brain\d00b6fc9-7c5d-40ee-a189-7a1412688aa5\.system_generated\logs\transcript_full.jsonl"
with open(path, "r", encoding="utf-8") as f:
    for line in f:
        if "turnin_601" in line:
            data = json.loads(line)
            if data.get("type") == "PLANNER_RESPONSE":
                print("FOUND A PLANNER RESPONSE WITH turnin_601")
                calls = data.get("tool_calls", [])
                for call in calls:
                    print(call["name"])
                    args = call["args"]
                    if "CodeContent" in args:
                        with open("recovered_paths.java", "w", encoding="utf-8") as out:
                            out.write(args["CodeContent"])
                        print("Wrote CodeContent to recovered_paths.java")
                    elif "ReplacementContent" in args:
                        with open("recovered_paths.java", "a", encoding="utf-8") as out:
                            out.write(args["ReplacementContent"])
                        print("Wrote ReplacementContent to recovered_paths.java")
