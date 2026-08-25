import re

path = "LQM.java"
with open(path, "r", encoding="utf-16") as f:
    try:
        content = f.read()
    except:
        with open(path, "r", encoding="utf-8-sig") as f2:
            content = f2.read()

# Fix Action Bar Task
old_actionbar = '''        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                ItemStack item = p.getInventory().getItemInMainHand();
                if (item != null && item.getType() == Material.COMPASS && item.hasItemMeta()) {
                    if (item.getItemMeta().getPersistentDataContainer().has(compassKey, PersistentDataType.STRING)) {
                        CompassMeta meta = (CompassMeta) item.getItemMeta();'''

new_actionbar = '''        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                ItemStack item = p.getInventory().getItemInMainHand();
                if (item != null && (item.getType() == Material.COMPASS || item.getType() == Material.RECOVERY_COMPASS) && item.hasItemMeta()) {
                    if (item.getItemMeta().getPersistentDataContainer().has(compassKey, PersistentDataType.STRING) ||
                        item.getItemMeta().getPersistentDataContainer().has(trackerKey, PersistentDataType.STRING)) {
                        CompassMeta meta = (CompassMeta) item.getItemMeta();'''

content = content.replace(old_actionbar, new_actionbar)

# Fix Action Bar Tracker handling
old_actionbar2 = '''                            if (p.getWorld().equals(lode.getWorld())) {
                                double dist = p.getLocation().distance(lode);
                                if (dist < 15.0) {
                                    p.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("&e&lYou have reached the spot! Press 'Q' (Drop) to summon him!"));
                                } else {
                                    p.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("&bDistance to Ancient Angler: &e" + (int)dist + "m"));
                                }
                            } else {
                                p.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("&cThe compass needle spins randomly... (Wrong World)"));
                            }'''

new_actionbar2 = '''                            if (p.getWorld().equals(lode.getWorld())) {
                                double dist = p.getLocation().distance(lode);
                                if (dist < 15.0) {
                                    p.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("&e&lYou have reached the spot! Press 'Q' (Drop) to summon him!"));
                                } else {
                                    p.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("&bDistance to Ancient Angler: &e" + (int)dist + "m"));
                                }
                            } else {
                                p.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("&cThe compass needle spins randomly... (Wrong World)"));
                            }
                        } else {
                            // Tracker without lodestone metadata
                            if (item.getItemMeta().getPersistentDataContainer().has(new org.bukkit.NamespacedKey(plugin, "lode_w"), org.bukkit.persistence.PersistentDataType.STRING)) {
                                String wName = item.getItemMeta().getPersistentDataContainer().get(new org.bukkit.NamespacedKey(plugin, "lode_w"), org.bukkit.persistence.PersistentDataType.STRING);
                                org.bukkit.World w = org.bukkit.Bukkit.getWorld(wName);
                                if (w != null && p.getWorld().equals(w)) {
                                    double tx = item.getItemMeta().getPersistentDataContainer().get(new org.bukkit.NamespacedKey(plugin, "lode_x"), org.bukkit.persistence.PersistentDataType.DOUBLE);
                                    double ty = item.getItemMeta().getPersistentDataContainer().get(new org.bukkit.NamespacedKey(plugin, "lode_y"), org.bukkit.persistence.PersistentDataType.DOUBLE);
                                    double tz = item.getItemMeta().getPersistentDataContainer().get(new org.bukkit.NamespacedKey(plugin, "lode_z"), org.bukkit.persistence.PersistentDataType.DOUBLE);
                                    double dist = p.getLocation().distance(new org.bukkit.Location(w, tx, ty, tz));
                                    if (dist < 15.0) {
                                        p.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("&e&lYou have reached the spot! Press 'Q' (Drop) to summon him!"));
                                    } else {
                                        p.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("&bDistance to Ancient Angler: &e" + (int)dist + "m"));
                                    }
                                } else {
                                    p.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("&cThe tracker hums randomly... (Wrong World)"));
                                }
                            }'''

content = content.replace(old_actionbar2, new_actionbar2)

with open(path, "w", encoding="utf-16") as f:
    f.write(content)

print("Patched LQM.java")
