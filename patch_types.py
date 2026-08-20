with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\WanderingAnglerManager.java", "r", encoding="utf-8") as f:
    content = f.read()

import re

# Replace TextUtil.colorize with ChatColor.translateAlternateColorCodes for String returns
content = re.sub(r'TextUtil\.colorize\((.*?)\)', r'org.bukkit.ChatColor.translateAlternateColorCodes(\'&\', \1)', content)

# But wait, Bukkit.broadcastMessage takes Component if using Paper, but if using Bukkit it takes String.
# The error said Component cannot be converted to String, so broadcastMessage wanted a String or I just use Bukkit.broadcast(Component)

# Also fix lore mapping
old_lore = "meta.setLore(lore.stream().map(org.bukkit.ChatColor.translateAlternateColorCodes('&', ::colorize)).toList());"
content = content.replace(old_lore, "meta.setLore(lore.stream().map(s -> org.bukkit.ChatColor.translateAlternateColorCodes('&', s)).toList());")
old_lore2 = "meta.setLore(lore.stream().map(TextUtil::colorize).toList());"
content = content.replace(old_lore2, "meta.setLore(lore.stream().map(s -> org.bukkit.ChatColor.translateAlternateColorCodes('&', s)).toList());")

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\WanderingAnglerManager.java", "w", encoding="utf-8") as f:
    f.write(content)
