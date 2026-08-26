import os
path = "src/main/java/net/rocky/coresuite/module/customfishing/LegendaryQuestManager.java"
with open(path, "r", encoding="utf-8") as f:
    content = f.read()

content = content.replace("public void loadPlayer(UUID uuid)", "public boolean isInChatState(java.util.UUID uuid) { return chatState.containsKey(uuid); }\n\n    public void loadPlayer(UUID uuid)")

with open(path, "w", encoding="utf-8") as f:
    f.write(content)
