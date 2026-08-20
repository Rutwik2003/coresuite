with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\resources\modules\custom-fishing.yml", "r", encoding="utf-8") as f:
    content = f.read()

import re

# Fix bonefish
content = re.sub(r"name: '&d&lBonefish'[\s\S]*?chance: 0.55",
                 "name: '&d&lBonefish'\n    lore:\n    - '&7Rarity: &dMythical'\n    - '&7A beautiful fish found in the wild.'\n    rarity: 'Mythical'\n    chance: 0.55", content)

# Fix demonic_netherfish
content = re.sub(r"name: '&d&lDemonic Netherfish'[\s\S]*?chance: 0.35",
                 "name: '&d&lDemonic Netherfish'\n    lore:\n    - '&7Rarity: &dMythical'\n    - '&7A beautiful fish found in the wild.'\n    rarity: 'Mythical'\n    chance: 0.35", content)

# Fix wither_bonefish
content = re.sub(r"name: '&d&lWither Bonefish'[\s\S]*?chance: 0.25",
                 "name: '&d&lWither Bonefish'\n    lore:\n    - '&7Rarity: &dMythical'\n    - '&7A beautiful fish found in the wild.'\n    rarity: 'Mythical'\n    chance: 0.25", content)

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\resources\modules\custom-fishing.yml", "w", encoding="utf-8") as f:
    f.write(content)
