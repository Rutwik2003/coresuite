import os
path = "src/main/java/net/rocky/coresuite/module/customfishing/LegendaryQuestManager.java"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

content = content.replace("private void processChatSync", "public void processChatSync")
if "public boolean isInChatState" not in content:
    content = content.replace("public void removeNPC", "public boolean isInChatState(java.util.UUID uuid) { return chatState.containsKey(uuid); }\n\n    public void removeNPC")

with open(path, "w", encoding="utf-8") as f:
    f.write(content)
