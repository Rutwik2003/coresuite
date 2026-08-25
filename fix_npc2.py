import re

with open("c:/Users/rutwi/OneDrive - MSFT/Desktop/coresuit/src/main/java/net/rocky/coresuite/module/customfishing/LegendaryNPCManager.java", "r") as f:
    content = f.read()

content = content.replace("npc.data().set(net.citizensnpcs.api.npc.NPC.Metadata.SAVE_TO_DISK, false);", "")

with open("c:/Users/rutwi/OneDrive - MSFT/Desktop/coresuit/src/main/java/net/rocky/coresuite/module/customfishing/LegendaryNPCManager.java", "w") as f:
    f.write(content)
