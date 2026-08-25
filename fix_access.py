import re

path = "src/main/java/net/rocky/coresuite/module/customfishing/LegendaryQuestManager.java"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

content = content.replace("private void processChatSync(", "public void processChatSync(")
content = content.replace("private final Map<UUID, String> chatState", "private final Map<UUID, String> chatState")

if "public boolean isInChatState" not in content:
    content = content.replace("public void processChatSync(", "public boolean isInChatState(UUID uuid) {\n        return chatState.containsKey(uuid);\n    }\n\n    public void processChatSync(")

with open(path, "w", encoding="utf-8") as f:
    f.write(content)
print("Fixed access")
