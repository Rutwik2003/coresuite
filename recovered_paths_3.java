$script = @"
import os
import json

found = False
for root, dirs, files in os.walk(r"C:\Users\rutwi\.gemini\antigravity\brain"):
    if "transcript_full.jsonl" in files:
        path = os.path.join(root, "transcript_full.jsonl")
        with open(path, "r", encoding="utf-8") as f:
            for line in f:
                if "turnin_601" in line:
                    data = json.loads(line)
                    if data.get("type") == "PLANNER_RESPONSE":
                        calls = data.get("tool_calls", [])
                        for call in calls:
                            if call["name"] == "replace_file_content":
                                args = call["args"]
                                if "ReplacementContent" in args:
                                    with open("recovered_paths_2.java", "w", encoding="utf-8") as out:
                                        out.write(args["ReplacementContent"])
                                    print("Wrote ReplacementContent to recovered_paths_2.java from " + path)
                                    found = True
                            elif call["name"] == "run_command":
                                args = call["args"]
                                if "CommandLine" in args and "turnin_601" in args["CommandLine"]:
                                    with open("recovered_paths_3.java", "w", encoding="utf-8") as out:
                                        out.write(args["CommandLine"])
                                    print("Wrote CommandLine to recovered_paths_3.java from " + path)
                                    found = True
"@
$script | Out-File -FilePath recover_all.py -Encoding utf8
python recover_all.py