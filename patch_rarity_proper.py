with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\resources\modules\custom-fishing.yml", "r", encoding="utf-8") as f:
    content = f.read()

import re

# Find each fish block. We can look for `name: ` and then `lore:` which has the rarity.
# Actually, since we need the rarity, we can do this:
# We read line by line. If we see `name: `, we save the index.
# When we see `- '&7Rarity: `, we extract the rarity, and insert `    rarity: '...'` right after the saved `name:` index.

lines = content.split('\n')
new_lines = []
last_name_idx = -1

for i, line in enumerate(lines):
    new_lines.append(line)
    if line.startswith("    name: "):
        last_name_idx = len(new_lines) - 1
    elif "- '&7Rarity: " in line:
        rarity_match = line.strip().split("&")[-1]
        rarity_word = rarity_match[1:].replace("'", "")
        # Insert at last_name_idx + 1
        new_lines.insert(last_name_idx + 1, f"    rarity: '{rarity_word}'")

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\resources\modules\custom-fishing.yml", "w", encoding="utf-8") as f:
    f.write('\n'.join(new_lines))
