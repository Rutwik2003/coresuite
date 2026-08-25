import yaml
import sys

with open('src/main/resources/modules/custom-fishing.yml', 'r') as f:
    data = yaml.safe_load(f)

# Add bait-shop section
data['bait-shop'] = {
    'force-restock-price': 250000.0
}

# Update baits to match user request and lore
b = data['baits']
b['worm']['price'] = 65000.0
b['worm']['casts'] = 15
b['worm']['catch-chance-bonus'] = 0.15

b['golden_lure']['price'] = 95000.0
b['golden_lure']['casts'] = 10
b['golden_lure']['weight-bonus'] = 0.25
# Update lore for golden lure to 10 casts
b['golden_lure']['lore'] = [line if 'Durability' not in line else ' &8- &7Durability: &e10 Casts' for line in b['golden_lure']['lore']]

b['tide_essence']['price'] = 250000.0
b['tide_essence']['duration-seconds'] = 180
b['tide_essence']['catch-chance-bonus'] = 0.40
b['tide_essence']['lore'] = [line if 'Duration' not in line else ' &8- &7Duration: &e3 Minutes' for line in b['tide_essence']['lore']]

b['mythic_chum']['price'] = 500000.0
b['mythic_chum']['catch-chance-bonus'] = 0.65
b['mythic_chum']['weight-bonus'] = 0.50

with open('src/main/resources/modules/custom-fishing.yml', 'w') as f:
    yaml.dump(data, f, sort_keys=False)

print('Success')
