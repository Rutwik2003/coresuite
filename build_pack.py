import os
import json
import shutil
import yaml

pack_name = "CoreSuite-UltimateFishPack"
base_dir = rf"C:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\{pack_name}"
if os.path.exists(base_dir):
    shutil.rmtree(base_dir)

# Create directories
os.makedirs(os.path.join(base_dir, "assets", "barely_default", "textures", "item", "fish"))
os.makedirs(os.path.join(base_dir, "assets", "barely_default", "models", "item"))
os.makedirs(os.path.join(base_dir, "assets", "minecraft", "items"))

# Write pack.mcmeta
with open(os.path.join(base_dir, "pack.mcmeta"), "w") as f:
    json.dump({
        "pack": {
            "pack_format": 34,
            "description": "CoreSuite - Ultimate Fish Pack (165 Fish!)"
        }
    }, f, indent=2)

with open(r'c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\resources\modules\custom-fishing.yml', 'r', encoding='utf-8') as f:
    data = yaml.safe_load(f)

entries = []

species = data.get('species', {})
for k, v in species.items():
    cmd = v.get("custom-model-data", -1)
    if cmd == -1: continue
    
    # 1. Copy Texture
    src_tex = rf"C:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\scratch_pack\assets\barely_default\textures\item\fish\{k}.png"
    dst_tex = os.path.join(base_dir, "assets", "barely_default", "textures", "item", "fish", f"{k}.png")
    if os.path.exists(src_tex):
        shutil.copy2(src_tex, dst_tex)
    
    # 2. Create Model JSON
    model_json = {
        "parent": "minecraft:item/generated",
        "textures": {
            "layer0": f"barely_default:item/fish/{k}"
        }
    }
    model_path = os.path.join(base_dir, "assets", "barely_default", "models", "item", f"fish_{k}.json")
    with open(model_path, "w") as f:
        json.dump(model_json, f, indent=2)
        
    # 3. Add to items/cod.json dispatch entries
    entries.append({
        "threshold": cmd,
        "model": {
            "type": "minecraft:model",
            "model": f"barely_default:item/fish_{k}"
        }
    })

# 4. Generate assets/minecraft/items/cod.json
entries.sort(key=lambda x: x["threshold"])

cod_json = {
  "model": {
    "type": "minecraft:range_dispatch",
    "property": "minecraft:custom_model_data",
    "index": 0,
    "entries": entries,
    "fallback": {
      "type": "minecraft:model",
      "model": "minecraft:item/cod"
    }
  }
}

with open(os.path.join(base_dir, "assets", "minecraft", "items", "cod.json"), "w") as f:
    json.dump(cod_json, f, indent=2)

# Zip it up
shutil.make_archive(base_dir, 'zip', base_dir)
print(f"Successfully generated {pack_name}.zip")
