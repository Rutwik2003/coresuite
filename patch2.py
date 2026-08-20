with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\CustomFishingModule.java", "r", encoding="utf-8") as f:
    content = f.read()

old_lore = """                data.bagPages < 10 ? "&7Cost: &a$" + cost + " &8| &7Req Level: &b" + reqLevel : \"\""""
new_lore = """                data.bagPages < 10 ? "&7Cost: &a$" + (config.getDouble("bag-pages." + (data.bagPages + 1) + ".price", -1.0) == -1.0 ? 10000.0 + ((data.bagPages + 1) * 15000.0) : config.getDouble("bag-pages." + (data.bagPages + 1) + ".price", -1.0)) + " &8| &7Req Level: &b" + (config.getInt("bag-pages." + (data.bagPages + 1) + ".level-required", -1) == -1 ? (data.bagPages + 1) * 5 : config.getInt("bag-pages." + (data.bagPages + 1) + ".level-required", -1)) : \"\""""
content = content.replace(old_lore, new_lore)

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\CustomFishingModule.java", "w", encoding="utf-8") as f:
    f.write(content)
print("Applied inline fix.")
