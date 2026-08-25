import sys, os
import zipfile

jar_path = None
for root, dirs, files in os.walk(r"C:\Users\rutwi\.gradle\caches"):
    for file in files:
        if file.startswith("paper-api") and "1.21.4" in file and file.endswith(".jar"):
            jar_path = os.path.join(root, file)
            break
    if jar_path: break

if jar_path:
    os.system(f"javap -cp \"{jar_path}\" org.bukkit.inventory.EquipmentSlotGroup")
