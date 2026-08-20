with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\WanderingAnglerManager.java", "r", encoding="utf-8") as f:
    content = f.read()

import re
content = re.sub(r'Sound\.ENTITY_WANDERING_TRADER_SPAWNED', 'Sound.ENTITY_WANDERING_TRADER_AMBIENT', content)
content = re.sub(r'Sound\.ENTITY_WANDERING_TRADER_DISAPPEARED', 'Sound.ENTITY_WANDERING_TRADER_DISAPPEARED', content) # Does this exist?

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\WanderingAnglerManager.java", "w", encoding="utf-8") as f:
    f.write(content)
