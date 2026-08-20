import re

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\resources\modules\custom-fishing.yml", "r", encoding="utf-8") as f:
    content = f.read()

# Revert bonefish to Rare
content = re.sub(r"name: '&d&lBonefish'\n\s*lore:\n\s*- '&7Rarity: &dMythical'\n\s*- '&7A beautiful fish found in the wild.'\n\s*rarity: 'Mythical'\n\s*chance: 0\.55",
                 "name: '&9Bonefish'\n    lore:\n    - '&7Rarity: &9Rare'\n    rarity: 'Rare'\n    - '&7A beautiful fish found in the wild.'\n    chance: 6.98", content)

# Revert demonic_netherfish to Rare
content = re.sub(r"name: '&d&lDemonic Netherfish'\n\s*lore:\n\s*- '&7Rarity: &dMythical'\n\s*- '&7A beautiful fish found in the wild.'\n\s*rarity: 'Mythical'\n\s*chance: 0\.35",
                 "name: '&9Demonic Netherfish'\n    lore:\n    - '&7Rarity: &9Rare'\n    rarity: 'Rare'\n    - '&7A beautiful fish found in the wild.'\n    chance: 7.23", content)

# Revert wither_bonefish to Common
content = re.sub(r"name: '&d&lWither Bonefish'\n\s*lore:\n\s*- '&7Rarity: &dMythical'\n\s*- '&7A beautiful fish found in the wild.'\n\s*rarity: 'Mythical'\n\s*chance: 0\.25",
                 "name: '&7Wither Bonefish'\n    lore:\n    - '&7Rarity: &7Common'\n    rarity: 'Common'\n    - '&7A beautiful fish found in the wild.'\n    chance: 25.75", content)

# Update enchanted_bookfish to Mythical
content = re.sub(r"name: '&7Enchanted Bookfish'\n\s*lore:\n\s*- '&7Rarity: &7Common'\n\s*rarity: 'Common'\n\s*- '&7A beautiful fish found in the wild.'\n\s*chance: 26\.83",
                 "name: '&d&lEnchanted Bookfish'\n    lore:\n    - '&7Rarity: &dMythical'\n    - '&7A legendary artifact from the depths...'\n    rarity: 'Mythical'\n    chance: 0.25", content)

# Update enchanted_bookfish_christmas to Mythical
content = re.sub(r"name: '&7Enchanted Bookfish Christmas'\n\s*lore:\n\s*- '&7Rarity: &7Common'\n\s*rarity: 'Common'\n\s*- '&7A beautiful fish found in the wild.'\n\s*chance: 26\.34",
                 "name: '&d&lEnchanted Bookfish Christmas'\n    lore:\n    - '&7Rarity: &dMythical'\n    - '&7A festive artifact from the depths...'\n    rarity: 'Mythical'\n    chance: 0.25", content)

# Update enchanted_bookfish_halloween to Mythical
content = re.sub(r"name: '&aEnchanted Bookfish Halloween'\n\s*lore:\n\s*- '&7Rarity: &aUncommon'\n\s*rarity: 'Uncommon'\n\s*- '&7A beautiful fish found in the wild.'\n\s*chance: 16\.14",
                 "name: '&d&lEnchanted Bookfish Halloween'\n    lore:\n    - '&7Rarity: &dMythical'\n    - '&7A spooky artifact from the depths...'\n    rarity: 'Mythical'\n    chance: 0.25", content)

# Update the_uncatchable to Mythical
content = re.sub(r"name: '&7The Uncatchable'\n\s*lore:\n\s*- '&7Rarity: &7Common'\n\s*rarity: 'Common'\n\s*- '&7A beautiful fish found in the wild.'\n\s*chance: 26\.78",
                 "name: '&d&lThe Uncatchable'\n    lore:\n    - '&7Rarity: &dMythical'\n    - '&7They said it couldn''t be done...'\n    rarity: 'Mythical'\n    chance: 0.15", content)

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\resources\modules\custom-fishing.yml", "w", encoding="utf-8") as f:
    f.write(content)
