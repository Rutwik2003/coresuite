import json
import os

for sub in ["a7e931d4-f935-407c-bc78-899f789c7f6a", "4ddf23b3-b711-4824-a72d-594bc5b4d010"]:
    path = rf"C:\Users\rutwi\.gemini\antigravity\brain\{sub}\.system_generated\logs\transcript_full.jsonl"
    if not os.path.exists(path): continue
    with open(path, "r", encoding="utf-8") as f:
        for line in f:
            data = json.loads(line)
            if data.get("type") == "PLANNER_RESPONSE":
                calls = data.get("tool_calls", [])
                for call in calls:
                    if call["name"] == "replace_file_content" or call["name"] == "write_to_file":
                        args = call["args"]
                        code = args.get("ReplacementContent", args.get("CodeContent", ""))
                        if "turnin_601" in code:
                            with open("recovered_LQM.java", "w", encoding="utf-8") as out:
                                out.write(code)
                            print(f"Wrote to recovered_LQM.java from {sub}")
                        else:
                            print(f"Wrote something else from {sub}")
