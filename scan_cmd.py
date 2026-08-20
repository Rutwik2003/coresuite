import os, json

packs = ["scratch_pack", "scratch_pack_2", "scratch_pack_3"]
for pack in packs:
    pack_dir = os.path.join(r"C:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit", pack)
    if not os.path.exists(pack_dir): continue
    
    print(f"--- PACK: {pack} ---")
    
    for root, dirs, files in os.walk(pack_dir):
        for file in files:
            if file.endswith(".json"):
                path = os.path.join(root, file)
                try:
                    with open(path, "r", encoding="utf-8") as f:
                        data = json.load(f)
                except:
                    continue
                
                # Check for 1.20 format overrides
                if "overrides" in data:
                    for override in data["overrides"]:
                        if "predicate" in override and "custom_model_data" in override["predicate"]:
                            print(f"{file} (1.20 CMD): {override['predicate']['custom_model_data']} -> {override.get('model')}")
                            
                # Check for 1.21 format property: minecraft:custom_model_data
                if "model" in data and isinstance(data["model"], dict) and data["model"].get("property") == "minecraft:custom_model_data":
                    for entry in data["model"].get("entries", []):
                        cmd = entry.get("threshold", "unknown")
                        model = entry.get("model", {}).get("model", "unknown")
                        print(f"{file} (1.21 CMD): {cmd} -> {model}")
