with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\resources\modules\custom-fishing.yml", "r", encoding="utf-8") as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    new_lines.append(line)
    if "- '&7Rarity: " in line:
        # Extract rarity word
        rarity_match = line.strip().split("&")[-1] # e.g. "7Common'", "9Rare'"
        rarity_word = rarity_match[1:].replace("'", "")
        # Add rarity field right after lore
        # We'll just insert it
        indent = len(line) - len(line.lstrip()) - 2
        new_lines.append(" " * indent + f"rarity: '{rarity_word}'\n")

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\resources\modules\custom-fishing.yml", "w", encoding="utf-8") as f:
    f.writelines(new_lines)
