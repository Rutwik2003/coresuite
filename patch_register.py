with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\CustomFishingModule.java", "r", encoding="utf-8") as f:
    content = f.read()

import re

old_fields = """    private FishingEventManager eventManager;
    private FishingLeaderboardManager leaderboardManager;"""

new_fields = """    private FishingEventManager eventManager;
    private FishingLeaderboardManager leaderboardManager;
    private WanderingAnglerManager wanderingAnglerManager;"""

content = content.replace(old_fields, new_fields)

old_init = """        eventManager = new FishingEventManager(plugin, this);
        leaderboardManager = new FishingLeaderboardManager(plugin, this);"""

new_init = """        eventManager = new FishingEventManager(plugin, this);
        leaderboardManager = new FishingLeaderboardManager(plugin, this);
        wanderingAnglerManager = new WanderingAnglerManager(plugin, this);"""

content = content.replace(old_init, new_init)

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\CustomFishingModule.java", "w", encoding="utf-8") as f:
    f.write(content)
print("Applied WanderingAnglerManager registration.")
