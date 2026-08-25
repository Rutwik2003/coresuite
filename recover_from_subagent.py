import json
path = r"C:\Users\rutwi\.gemini\antigravity\brain\ab6300c2-b08c-45bd-a7a0-e40da4f5799a\.system_generated\logs\transcript_full.jsonl"
with open(path, "r", encoding="utf-8") as f:
    for line in f:
        data = json.loads(line)
        if data.get("type") == "PLANNER_RESPONSE":
            calls = data.get("tool_calls", [])
            for call in calls:
                if call["name"] == "replace_file_content":
                    args = call["args"]
                    if "ReplacementContent" in args:
                        with open("recovered_paths_4.java", "w", encoding="utf-8") as out:
                            out.write(args["ReplacementContent"])
                        print("Wrote ReplacementContent to recovered_paths_4.java")
                elif call["name"] == "write_to_file":
                    args = call["args"]
                    if "CodeContent" in args:
                        with open("recovered_paths_4.java", "w", encoding="utf-8") as out:
                            out.write(args["CodeContent"])
                        print("Wrote CodeContent to recovered_paths_4.java")
