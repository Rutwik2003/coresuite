with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\CustomFishingModule.java", "r", encoding="utf-8") as f:
    content = f.read()

# Fix 1: Command handler fallback
old1 = """                    double cost = config.getDouble("bag-pages." + nextPage + ".price", 10000.0);
                    int reqLevel = config.getInt("bag-pages." + nextPage + ".level-required", 0);"""
new1 = """                    double cost = config.getDouble("bag-pages." + nextPage + ".price", -1.0);
                    if (cost == -1.0) cost = 10000.0 + (nextPage * 15000.0);
                    int reqLevel = config.getInt("bag-pages." + nextPage + ".level-required", -1);
                    if (reqLevel == -1) reqLevel = nextPage * 5;"""
content = content.replace(old1, new1)

# Fix 2: GUI openMenu fallback
old2 = """        double cost = config.getDouble("bag-pages." + (currentPages + 1) + ".price", 10000.0);"""
new2 = """        double cost = config.getDouble("bag-pages." + (currentPages + 1) + ".price", -1.0);
        if (cost == -1.0) cost = 10000.0 + ((currentPages + 1) * 15000.0);
        int reqLevel = config.getInt("bag-pages." + (currentPages + 1) + ".level-required", -1);
        if (reqLevel == -1) reqLevel = (currentPages + 1) * 5;"""
content = content.replace(old2, new2)

# Fix 3: GUI Lore string replace
old3 = """data.bagPages < 10 ? "&7Cost: &a$" + config.getDouble("bag-pages." + (data.bagPages + 1) + ".price", 10000.0) + " &8| &7Req Level: &b" + config.getInt("bag-pages." + (data.bagPages + 1) + ".level-required", 0) : \"\""""
new3 = """data.bagPages < 10 ? "&7Cost: &a$" + cost + " &8| &7Req Level: &b" + reqLevel : \"\""""
content = content.replace(old3, new3)

# Fix 4: Fatigue threshold from 25 to 100
old4 = """            double score = fatigueScore.getOrDefault(uuid, 0.0);
            if (score > 25) {
                double over = score - 25;
                fatigue = 1.0 / (1.0 + (over * 0.03));
            }"""
new4 = """            double score = fatigueScore.getOrDefault(uuid, 0.0);
            if (score > 100.0) {
                double over = score - 100.0;
                fatigue = 1.0 / (1.0 + (over * 0.015));
            }"""
content = content.replace(old4, new4)

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\CustomFishingModule.java", "w", encoding="utf-8") as f:
    f.write(content)
print("Applied fixes via Python script.")
