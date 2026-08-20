with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\resources\modules\custom-fishing.yml", "r", encoding="utf-8") as f:
    content = f.read()

import re

# Update lore and rarity for bonefish
content = re.sub(r"name: '&9Bonefish'[\s\S]*?- '&7Rarity: &9Rare'[\s\S]*?rarity: 'Rare'",
                 "name: '&d&lBonefish'\n    lore:\n    - '&7Rarity: &dMythical'\n    rarity: 'Mythical'", content)
# Ensure chance is low
content = re.sub(r"name: '&d&lBonefish'[\s\S]*?chance: \d+\.\d+",
                 "name: '&d&lBonefish'\n    lore:\n    - '&7Rarity: &dMythical'\n    rarity: 'Mythical'\n    - '&7A beautiful fish found in the wild.'\n    chance: 0.55", content)

# Update demonic_netherfish
content = re.sub(r"name: '&9Demonic Netherfish'[\s\S]*?- '&7Rarity: &9Rare'[\s\S]*?rarity: 'Rare'",
                 "name: '&d&lDemonic Netherfish'\n    lore:\n    - '&7Rarity: &dMythical'\n    rarity: 'Mythical'", content)
content = re.sub(r"name: '&d&lDemonic Netherfish'[\s\S]*?chance: \d+\.\d+",
                 "name: '&d&lDemonic Netherfish'\n    lore:\n    - '&7Rarity: &dMythical'\n    rarity: 'Mythical'\n    - '&7A beautiful fish found in the wild.'\n    chance: 0.35", content)

# Update wither_bonefish
content = re.sub(r"name: '&7Wither Bonefish'[\s\S]*?- '&7Rarity: &7Common'[\s\S]*?rarity: 'Common'",
                 "name: '&d&lWither Bonefish'\n    lore:\n    - '&7Rarity: &dMythical'\n    rarity: 'Mythical'", content)
content = re.sub(r"name: '&d&lWither Bonefish'[\s\S]*?chance: \d+\.\d+",
                 "name: '&d&lWither Bonefish'\n    lore:\n    - '&7Rarity: &dMythical'\n    rarity: 'Mythical'\n    - '&7A beautiful fish found in the wild.'\n    chance: 0.25", content)

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\resources\modules\custom-fishing.yml", "w", encoding="utf-8") as f:
    f.write(content)
