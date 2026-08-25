import sys, os
import zipfile

jar_path = None
for root, dirs, files in os.walk(r"C:\Users\rutwi\.gradle\caches"):
    for file in files:
        if file.startswith("paper-api") and file.endswith(".jar"):
            jar_path = os.path.join(root, file)
            break
    if jar_path: break

if jar_path:
    with zipfile.ZipFile(jar_path, "r") as z:
        if "org/bukkit/attribute/Attribute.class" in z.namelist():
            print("Found Attribute.class")
            os.system(f"javap -cp \"{jar_path}\" org.bukkit.attribute.Attribute | findstr ATTACK")
