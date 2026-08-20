with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\WanderingAnglerManager.java", "r", encoding="utf-8") as f:
    content = f.read()

import re

old_open = """    private void openBlackMarket(Player player, WanderingTrader trader) {
        Gui gui = new Gui("&0Wandering Angler", 3);"""

new_open = """    private void openBlackMarket(Player player, WanderingTrader trader) {
        plugin.getDatabaseManager().executeAsync(() -> {
            java.util.Set<String> caughtSpecies = new java.util.HashSet<>();
            try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
                 java.sql.PreparedStatement stmt = conn.prepareStatement("SELECT species_id FROM coresuite_fish_logbook WHERE uuid = ?")) {
                stmt.setString(1, player.getUniqueId().toString());
                try (java.sql.ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        caughtSpecies.add(rs.getString("species_id"));
                    }
                }
            } catch (java.sql.SQLException e) {
                plugin.getLogger().log(java.util.logging.Level.SEVERE, "Failed to load fish logbook for NPC", e);
            }
            
            Bukkit.getScheduler().runTask(plugin, () -> {
                Gui gui = new Gui("&0Wandering Angler", 3);"""

content = content.replace(old_open, new_open)

old_gui_build = """            if (sec.contains("custom-model-data")) meta.setCustomModelData(sec.getInt("custom-model-data"));
            
            String itemModelStr = sec.getString("item-model", "");
            if (!itemModelStr.isEmpty()) {
                meta.setItemModel(NamespacedKey.fromString(itemModelStr));
            }
            
            displayItem.setItemMeta(meta);
        }

        final String targetStrippedName = uncoloredName;"""

new_gui_build = """            if (sec.contains("custom-model-data")) meta.setCustomModelData(sec.getInt("custom-model-data"));
            
            String itemModelStr = sec.getString("item-model", "");
            if (!itemModelStr.isEmpty()) {
                meta.setItemModel(NamespacedKey.fromString(itemModelStr));
            }
            
            displayItem.setItemMeta(meta);
        }
        
        if (!caughtSpecies.contains(speciesKey)) {
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

content = content.replace(old_gui_build, new_gui_build)

old_end = """        gui.open(player);
        player.playSound(player.getLocation(), Sound.ENTITY_WANDERING_TRADER_TRADE, 1f, 1f);
    }
}"""

new_end = """        gui.open(player);
        player.playSound(player.getLocation(), Sound.ENTITY_WANDERING_TRADER_TRADE, 1f, 1f);
            });
        });
    }
}"""

content = content.replace(old_end, new_end)

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\WanderingAnglerManager.java", "w", encoding="utf-8") as f:
    f.write(content)
