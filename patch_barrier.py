with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\WanderingAnglerManager.java", "r", encoding="utf-8") as f:
    content = f.read()

import re

old_barrier = """            if (!caughtSpecies.contains(uncoloredName)) {
                displayItem = new ItemStack(Material.BARRIER);
                ItemMeta m = displayItem.getItemMeta();
                if (m != null) {
                    m.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&8???"));
                    List<String> unkLore = new ArrayList<>();
                    unkLore.add("&7I am looking for an undiscovered fish...");
                    unkLore.add("&cYou have not caught this species yet!");
                    m.setLore(unkLore.stream().map(s -> org.bukkit.ChatColor.translateAlternateColorCodes('&', s)).toList());
                    displayItem.setItemMeta(m);
                }
            }"""

new_barrier = """            if (!caughtSpecies.contains(uncoloredName)) {
                displayItem = new ItemStack(Material.PAPER);
                ItemMeta m = displayItem.getItemMeta();
                if (m != null) {
                    m.setDisplayName(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&8???"));
                    m.setItemModel(NamespacedKey.fromString("barely_default:fish_unknown"));
                    List<String> unkLore = new ArrayList<>();
                    unkLore.add("&7I am looking for an undiscovered fish...");
                    unkLore.add("&cYou have not caught this species yet!");
                    m.setLore(unkLore.stream().map(s -> org.bukkit.ChatColor.translateAlternateColorCodes('&', s)).toList());
                    displayItem.setItemMeta(m);
                }
            }"""

content = content.replace(old_barrier, new_barrier)

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\WanderingAnglerManager.java", "w", encoding="utf-8") as f:
    f.write(content)
