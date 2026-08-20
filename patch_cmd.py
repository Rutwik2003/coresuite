with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\CustomFishingModule.java", "r", encoding="utf-8") as f:
    content = f.read()

import re

admin_cmd = """                } else if (args[0].equalsIgnoreCase("spawnangler")) {
                    if (!player.hasPermission("coresuite.admin")) {
                        TextUtil.sendMessage(player, "&cYou do not have permission to do this.");
                        return true;
                    }
                    if (wanderingAnglerManager != null) {
                        wanderingAnglerManager.spawnAngler(player.getLocation());
                        TextUtil.sendMessage(player, "&aForce-spawned a Wandering Angler!");
                    }
                    return true;
                } else if (args[0].equalsIgnoreCase("log")) {"""

content = content.replace('                } else if (args[0].equalsIgnoreCase("log")) {', admin_cmd)

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\CustomFishingModule.java", "w", encoding="utf-8") as f:
    f.write(content)
