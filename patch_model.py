with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\WanderingAnglerManager.java", "r", encoding="utf-8") as f:
    content = f.read()

import re

old_item = """            ItemStack displayItem = new ItemStack(Material.COD);
            ItemMeta meta = displayItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&6&lBounty: " + coloredName));
                meta.setLore(lore.stream().map(s -> org.bukkit.ChatColor.translateAlternateColorCodes('&', s)).toList());
                if (sec.contains("custom-model-data")) meta.setCustomModelData(sec.getInt("custom-model-data"));
                displayItem.setItemMeta(meta);
            }"""

new_item = """            Material mat;
            try {
                mat = Material.valueOf(sec.getString("material", "COD").toUpperCase());
            } catch (Exception e) {
                mat = Material.COD;
            }
            ItemStack displayItem = new ItemStack(mat);
            ItemMeta meta = displayItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&6&lBounty: " + coloredName));
                meta.setLore(lore.stream().map(s -> org.bukkit.ChatColor.translateAlternateColorCodes('&', s)).toList());
                
                if (sec.contains("custom-model-data")) meta.setCustomModelData(sec.getInt("custom-model-data"));
                
                String itemModelStr = sec.getString("item-model", "");
                if (!itemModelStr.isEmpty()) {
                    meta.setItemModel(NamespacedKey.fromString(itemModelStr));
                }
                
                displayItem.setItemMeta(meta);
            }"""

content = content.replace(old_item, new_item)

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\WanderingAnglerManager.java", "w", encoding="utf-8") as f:
    f.write(content)
