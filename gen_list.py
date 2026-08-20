import yaml
with open('c:/Users/rutwi/OneDrive - MSFT/Desktop/coresuit/src/main/resources/modules/custom-fishing.yml', 'r', encoding='utf-8') as f:
    data = yaml.safe_load(f)

with open(r'C:\Users\rutwi\.gemini\antigravity\brain\3773223f-72be-44a3-9568-a6fed7b0a6c8\fish_list.md', 'w', encoding='utf-8') as out:
    out.write('# Custom Fish Collection\n\n')
    out.write('Here is the complete list of all 165 fish generated from your resource pack, including their rarity, weight ranges, and the specific biomes you must travel to in order to catch them.\n\n')
    out.write('| Fish Name | Rarity | Weight Range | Required Biomes |\n')
    out.write('|---|---|---|---|\n')
    
    species = data.get('species', {})
    for k, v in species.items():
        # clean color codes
        name = v.get('name', k).replace('&7', '').replace('&a', '').replace('&9', '').replace('&5', '').replace('&6', '').replace('&b', '').replace('&c', '').replace('&e', '').replace('&f', '').replace('&d', '')
        
        lore = v.get('lore', [])
        rarity = 'Unknown'
        for l in lore:
            if 'Rarity:' in l:
                rarity = l.split('Rarity: ')[1].replace('&7', '').replace('&a', '').replace('&9', '').replace('&5', '').replace('&6', '').replace('"', '')
        
        min_w = v.get('min-weight', 0)
        max_w = v.get('max-weight', 0)
        weight = f"{min_w}kg - {max_w}kg"
        
        biomes = v.get('biomes', [])
        biomes_str = ', '.join(biomes) if biomes else 'Anywhere'
        
        out.write(f'| {name} | {rarity} | {weight} | {biomes_str} |\n')
