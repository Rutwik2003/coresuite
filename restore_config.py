import json
import re

transcript_path = r"C:\Users\rutwi\.gemini\antigravity\brain\3773223f-72be-44a3-9568-a6fed7b0a6c8\.system_generated\logs\transcript_full.jsonl"
config_content = None

with open(transcript_path, "r", encoding="utf-8") as f:
    lines = f.readlines()
    for line in reversed(lines):
        data = json.loads(line)
        if data.get("type") == "USER_INPUT":
            content = data.get("content", "")
            if "prefix: '&8[&b&lFishing&8] &r'" in content:
                # Extract the config block
                idx = content.find("prefix: ")
                config_content = content[idx:]
                break

if config_content:
    # Add bag pages 5 to 10
    pages_to_add = """  '5':
    price: 75000.0
    level-required: 20
  '6':
    price: 100000.0
    level-required: 30
  '7':
    price: 150000.0
    level-required: 40
  '8':
    price: 200000.0
    level-required: 50
  '9':
    price: 300000.0
    level-required: 65
  '10':
    price: 500000.0
    level-required: 80
rewards:"""
    config_content = config_content.replace("rewards:", pages_to_add)
    
    with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\resources\modules\custom-fishing.yml", "w", encoding="utf-8") as f:
        f.write(config_content)
    print("Successfully restored custom-fishing.yml from transcript.")
else:
    print("Could not find the config in transcript.")
