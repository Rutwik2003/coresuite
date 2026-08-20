with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\CustomFishingModule.java", "r", encoding="utf-8") as f:
    content = f.read()

import re

old_fatigue = """                                case "legendary": addFatigue = 20.0; break;
                                default: addFatigue = 1.0; break;"""

new_fatigue = """                                case "legendary": addFatigue = 20.0; break;
                                case "mythical": addFatigue = 50.0; break;
                                default: addFatigue = 1.0; break;"""

content = content.replace(old_fatigue, new_fatigue)

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\CustomFishingModule.java", "w", encoding="utf-8") as f:
    f.write(content)
