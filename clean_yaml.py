with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\resources\modules\custom-fishing.yml", "r", encoding="utf-8") as f:
    lines = f.readlines()

new_lines = []
for line in lines:
    if line.strip().startswith("rarity: '"):
        continue
    new_lines.append(line)

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\resources\modules\custom-fishing.yml", "w", encoding="utf-8") as f:
    f.writelines(new_lines)
