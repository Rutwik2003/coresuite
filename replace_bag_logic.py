import re

content = '''    private int countFish(Player player, String species) {
        int c = 0;
        // Count in inventory
        for (ItemStack i : player.getInventory().getContents()) {
            if (i != null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) {
                String sp = i.getItemMeta().getPersistentDataContainer().get(module.getSpeciesKey(), PersistentDataType.STRING);
                if (species.equalsIgnoreCase(sp)) c += i.getAmount();
            }
        }
        // Count in bag
        ItemStack[] bag = module.getFishBagManager().getBag(player.getUniqueId());
        if (bag != null) {
            for (ItemStack i : bag) {
                if (i != null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) {
                    String sp = i.getItemMeta().getPersistentDataContainer().get(module.getSpeciesKey(), PersistentDataType.STRING);
                    if (species.equalsIgnoreCase(sp)) c += i.getAmount();
                }
            }
        }
        return c;
    }

    private void takeFishAmount(Player player, String species, int amount) {
        int removed = 0;
        // Remove from inventory
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack i = player.getInventory().getItem(slot);
            if (i != null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) {
                String sp = i.getItemMeta().getPersistentDataContainer().get(module.getSpeciesKey(), PersistentDataType.STRING);
                if (species.equalsIgnoreCase(sp)) {
                    while (removed < amount && i.getAmount() > 0) {
                        i.setAmount(i.getAmount() - 1);
                        removed++;
                    }
                    if (i.getAmount() <= 0) player.getInventory().setItem(slot, null);
                }
            }
        }
        // Remove from bag
        if (removed < amount) {
            ItemStack[] bag = module.getFishBagManager().getBag(player.getUniqueId());
            if (bag != null) {
                boolean bagChanged = false;
                for (int slot = 0; slot < bag.length; slot++) {
                    ItemStack i = bag[slot];
                    if (i != null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) {
                        String sp = i.getItemMeta().getPersistentDataContainer().get(module.getSpeciesKey(), PersistentDataType.STRING);
                        if (species.equalsIgnoreCase(sp)) {
                            while (removed < amount && i.getAmount() > 0) {
                                i.setAmount(i.getAmount() - 1);
                                removed++;
                                bagChanged = true;
                            }
                            if (i.getAmount() <= 0) bag[slot] = null;
                        }
                    }
                }
                if (bagChanged) {
                    module.getFishBagManager().updateBag(player.getUniqueId(), bag);
                    module.getFishBagManager().saveBag(player.getUniqueId());
                }
            }
        }
    }

    private boolean takeFish(Player player, String requiredSpecies) {
        boolean removed = false;
        // Try inventory
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack i = player.getInventory().getItem(slot);
            if (i != null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) {
                String sp = i.getItemMeta().getPersistentDataContainer().get(module.getSpeciesKey(), PersistentDataType.STRING);
                if (requiredSpecies.equalsIgnoreCase(sp)) {
                    i.setAmount(i.getAmount() - 1);
                    if (i.getAmount() <= 0) player.getInventory().setItem(slot, null);
                    removed = true;
                    break;
                }
            }
        }
        // Try bag
        if (!removed) {
            ItemStack[] bag = module.getFishBagManager().getBag(player.getUniqueId());
            if (bag != null) {
                for (int slot = 0; slot < bag.length; slot++) {
                    ItemStack i = bag[slot];
                    if (i != null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) {
                        String sp = i.getItemMeta().getPersistentDataContainer().get(module.getSpeciesKey(), PersistentDataType.STRING);
                        if (requiredSpecies.equalsIgnoreCase(sp)) {
                            i.setAmount(i.getAmount() - 1);
                            if (i.getAmount() <= 0) bag[slot] = null;
                            removed = true;
                            module.getFishBagManager().updateBag(player.getUniqueId(), bag);
                            module.getFishBagManager().saveBag(player.getUniqueId());
                            break;
                        }
                    }
                }
            }
        }
        if (!removed) {
            TextUtil.sendMessage(player, "&cYou do not have the required fish (" + requiredSpecies + ")!");
            player.closeInventory();
            return false;
        }
        return true;
    }

    private void completeRitual(Player player, org.bukkit.entity.Entity npc) {
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        npc.remove();
        player.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, npc.getLocation(), 100, 1.0, 1.0, 1.0, 0.1);
        player.playSound(npc.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
    }

    private void processFishTurnIn(Player player, String requiredSpecies, int nextStage, String successMsg, org.bukkit.entity.Entity npc) {
        if (takeFish(player, requiredSpecies)) {
            setStage(player.getUniqueId(), nextStage);
            TextUtil.sendMessage(player, successMsg);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            openQuestGUI(player, npc);
        }
    }
}'''

with open("c:/Users/rutwi/OneDrive - MSFT/Desktop/coresuit/src/main/java/net/rocky/coresuite/module/customfishing/LegendaryQuestManager.java", "r") as f:
    original = f.read()

new_file = re.sub(r"    private int countFish\(Player player, String species\).*$", content, original, flags=re.DOTALL)

with open("c:/Users/rutwi/OneDrive - MSFT/Desktop/coresuit/src/main/java/net/rocky/coresuite/module/customfishing/LegendaryQuestManager.java", "w") as f:
    f.write(new_file)
