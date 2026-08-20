import yaml
from collections import defaultdict
import re

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\resources\modules\custom-fishing.yml", "r", encoding="utf-8") as f:
    data = yaml.safe_load(f)

species = data.get("species", {})
biome_map = defaultdict(list)
global_fish = []

def clean_color(text):
    return re.sub(r"&[0-9a-fk-or]", "", text).strip()

for fish_id, fish_data in species.items():
    name = clean_color(fish_data.get("name", fish_id))
    
    lore = fish_data.get("lore", [])
    rarity = "Common"
    for line in lore:
        if "Rarity:" in line:
            rarity_raw = line.split("Rarity:")[1].strip()
            rarity = clean_color(rarity_raw)
    
    biomes = fish_data.get("biomes", [])
    if not biomes:
        global_fish.append(f"{name} ({rarity})")
    else:
        for b in biomes:
            biome_map[b].append(f"{name} ({rarity})")

with open(r"c:\Users\rutwi\.gemini\antigravity\brain\3773223f-72be-44a3-9568-a6fed7b0a6c8\biome_guide.md", "w", encoding="utf-8") as out:
    out.write("# Custom Fishing Biome Guide\n\n")
    out.write("This guide shows which custom fish can be caught in each specific biome. If a biome is not listed here, it only spawns **Global Fish** and Vanilla fish.\n\n")
    
    out.write("### ?? Global Fish (Caught ANYWHERE)\n")
    out.write(", ".join(global_fish) + "\n\n")
    
    out.write("| Biome | Fish Available | Total |\n")
    out.write("|---|---|---|\n")
    
    for b in sorted(biome_map.keys()):
        fish_list = biome_map[b]
        fish_list.sort()
        count = len(fish_list)
        out.write(f"| **{b}** | {', '.join(fish_list)} | {count} |\n")
