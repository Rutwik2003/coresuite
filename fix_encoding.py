import codecs

path = "src/main/java/net/rocky/coresuite/module/customfishing/LegendaryQuestManager.java"
with open(path, "r", encoding="utf-16") as f:
    content = f.read()

with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("Converted to UTF-8")
