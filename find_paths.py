import json
path = r"C:\Users\rutwi\.gemini\antigravity\brain\d00b6fc9-7c5d-40ee-a189-7a1412688aa5\.system_generated\logs\transcript_full.jsonl"
with open(path, "r", encoding="utf-8") as f:
    for line in f:
        if "Path 6" in line or "turnin_601" in line:
            data = json.loads(line)
            content = data.get("content", "")
            if "turnin_601" in content:
                print("FOUND A LINE WITH Path 6")
                print(content[:500])
                print("...")
                print(content[-500:])
