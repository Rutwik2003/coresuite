import re

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\CustomFishingModule.java", "r", encoding="utf-8") as f:
    content = f.read()

old_formula = """            if (score > 100.0) {
                double over = score - 100.0;
                fatigue = 1.0 / (1.0 + (over * 0.015));
            }"""
            
new_formula = """            if (score > 100.0) {
                double over = score - 100.0;
                fatigue = Math.max(0.01, 1.0 - (over * 0.003));
            }"""

content = content.replace(old_formula, new_formula)

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\CustomFishingModule.java", "w", encoding="utf-8") as f:
    f.write(content)
