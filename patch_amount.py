with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\WanderingAnglerManager.java", "r", encoding="utf-8") as f:
    content = f.read()

old_logic = """                            item.setAmount(item.getAmount() - 1);
                            player.getInventory().setItem(slot, item);"""

new_logic = """                            if (item.getAmount() <= 1) {
                                player.getInventory().setItem(slot, null);
                            } else {
                                item.setAmount(item.getAmount() - 1);
                                player.getInventory().setItem(slot, item);
                            }"""

content = content.replace(old_logic, new_logic)

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\WanderingAnglerManager.java", "w", encoding="utf-8") as f:
    f.write(content)
