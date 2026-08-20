import re

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\WanderingAnglerManager.java", "r", encoding="utf-8") as f:
    content = f.read()

# 1. Update imports
content = content.replace("import org.bukkit.entity.WanderingTrader;", "import org.bukkit.entity.Villager;")

# 2. Update spawnAngler to clear old anglers and use Villager
old_spawn = """        WanderingTrader trader = (WanderingTrader) loc.getWorld().spawnEntity(loc, EntityType.WANDERING_TRADER);
        trader.setCustomName(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&b&lWandering Angler"));
        trader.setCustomNameVisible(true);
        trader.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 999999, 0, false, false));
        trader.setDespawnDelay(0); // We will manually despawn him"""

new_spawn = """        for (org.bukkit.World w : org.bukkit.Bukkit.getWorlds()) {
            for (org.bukkit.entity.Entity e : w.getEntities()) {
                if (e.getPersistentDataContainer().has(anglerKey, org.bukkit.persistence.PersistentDataType.BYTE)) {
                    e.remove();
                }
            }
        }
        
        Villager trader = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
        trader.setProfession(Villager.Profession.FISHERMAN);
        trader.setVillagerType(Villager.Type.JUNGLE);
        trader.setCustomName(org.bukkit.ChatColor.translateAlternateColorCodes('&', "&b&lWandering Angler"));
        trader.setCustomNameVisible(true);
        trader.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 999999, 0, false, false));
        trader.setInvulnerable(true);"""

content = content.replace(old_spawn, new_spawn)

# 3. Update interaction
old_interact = """    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof WanderingTrader trader) {
            PersistentDataContainer pdc = trader.getPersistentDataContainer();
            if (pdc.has(anglerKey, PersistentDataType.BYTE)) {
                event.setCancelled(true);
                openBlackMarket(event.getPlayer(), trader);
            }
        }
    }

    private void openBlackMarket(Player player, WanderingTrader trader) {"""

new_interact = """    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Villager trader) {
            PersistentDataContainer pdc = trader.getPersistentDataContainer();
            if (pdc.has(anglerKey, PersistentDataType.BYTE)) {
                event.setCancelled(true);
                openBlackMarket(event.getPlayer(), trader);
            }
        }
    }

    private void openBlackMarket(Player player, Villager trader) {"""

content = content.replace(old_interact, new_interact)

# 4. Update barrier lore to show biomes
old_barrier = """                    List<String> unkLore = new ArrayList<>();
                    unkLore.add("&7I am looking for an undiscovered fish...");
                    unkLore.add("&cYou have not caught this species yet!");
                    m.setLore(unkLore.stream().map(s -> org.bukkit.ChatColor.translateAlternateColorCodes('&', s)).toList());"""

new_barrier = """                    List<String> unkLore = new ArrayList<>();
                    unkLore.add("&7I am looking for an undiscovered fish...");
                    unkLore.add("&cYou have not caught this species yet!");
                    List<String> biomes = sec.getStringList("biomes");
                    if (biomes != null && !biomes.isEmpty()) {
                        unkLore.add("");
                        unkLore.add("&7Required Biomes:");
                        for (String b : biomes) {
                            unkLore.add("&8- &7" + b);
                        }
                    }
                    m.setLore(unkLore.stream().map(s -> org.bukkit.ChatColor.translateAlternateColorCodes('&', s)).toList());"""

content = content.replace(old_barrier, new_barrier)

with open(r"c:\Users\rutwi\OneDrive - MSFT\Desktop\coresuit\src\main\java\net\rocky\coresuite\module\customfishing\WanderingAnglerManager.java", "w", encoding="utf-8") as f:
    f.write(content)
