import re

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\WanderingAnglerManager.java", "r", encoding="utf-8") as f:
    content = f.read()

# Add the caughtSpecies logic right before final String targetStrippedName = uncoloredName;

old_block = """                displayItem.setItemMeta(meta);
            }

            final String targetStrippedName = uncoloredName;"""

new_block = """                displayItem.setItemMeta(meta);
            }
            
            if (!caughtSpecies.contains(uncoloredName)) {
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
            }

            final String targetStrippedName = uncoloredName;"""

content = content.replace(old_block, new_block)

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\WanderingAnglerManager.java", "w", encoding="utf-8") as f:
    f.write(content)
