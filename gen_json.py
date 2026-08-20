import os
import json

model_path = r"C:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\resourcepack\assets\barely_default\models\item\fish_unknown.json"
with open(model_path, "w") as f:
    json.dump({
        "parent": "minecraft:item/generated",
        "textures": {
            "layer0": "barely_default:item/fish_unknown"
        }
    }, f, indent=2)

item_path = r"C:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\resourcepack\assets\barely_default\items\fish_unknown.json"
with open(item_path, "w") as f:
    json.dump({
        "model": {
            "type": "model",
            "model": "barely_default:item/fish_unknown"
        }
    }, f, indent=2)

print("Created JSON models.")
