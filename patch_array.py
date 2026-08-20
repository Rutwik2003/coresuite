with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\FishBagManager.java", "r", encoding="utf-8") as f:
    content = f.read()

import re
content = content.replace("new ItemStack[54]", "new ItemStack[450]")

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\FishBagManager.java", "w", encoding="utf-8") as f:
    f.write(content)
