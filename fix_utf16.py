import codecs

path = "src/main/java/net/rocky/coresuite/module/customfishing/LegendaryQuestManager.java"
try:
    with codecs.open(path, "r", encoding="utf-16") as f:
        content = f.read()
    with codecs.open(path, "w", encoding="utf-8") as f:
        f.write(content)
    print("Converted to UTF-8")
except Exception as e:
    print(e)
