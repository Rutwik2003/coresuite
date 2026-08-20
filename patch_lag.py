with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\FishBagManager.java", "r", encoding="utf-8") as f:
    content = f.read()

import re

# Remove saveBag from updateBag to fix lag
old_update = """    public void updateBag(UUID uuid, ItemStack[] items) {
        cache.put(uuid, items);
        saveBag(uuid);
    }"""
new_update = """    public void updateBag(UUID uuid, ItemStack[] items) {
        cache.put(uuid, items);
    }"""
content = content.replace(old_update, new_update)

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\FishBagManager.java", "w", encoding="utf-8") as f:
    f.write(content)
