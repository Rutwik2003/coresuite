import re

lqm_path = "c:/Users/rutwi/OneDrive - MSFT/Desktop/coresuit/src/main/java/net/rocky/coresuite/module/customfishing/LegendaryQuestManager.java"

with open(lqm_path, "r", encoding="utf-8") as f:
    lqm = f.read()

# 1. Update The Thrill Reward
old_thrill = '''ItemStack chum = module.getBaitManager().createBaitItem("mythic_chum");
                    if (chum != null) {
                        chum.setAmount(3);
                        player.getInventory().addItem(chum);
                    }
                    sendLines(player, true,
                        "&e[Ancient Angler]&f The Queen herself! You are a true hunter.",
                        "&e[Ancient Angler]&f Take this Mythical Chum.",
                        "&e[Ancient Angler]&f Use it to hunt the true legends."
                    );'''

new_thrill = '''String[] availableBaits = {"worm", "golden_lure", "tide_essence", "mythic_chum"};
                    String chosenBait = availableBaits[ThreadLocalRandom.current().nextInt(availableBaits.length)];
                    ItemStack baitReward = module.getBaitManager().createBaitItem(chosenBait);
                    if (baitReward != null) {
                        baitReward.setAmount(3);
                        player.getInventory().addItem(baitReward);
                    }
                    sendLines(player, true,
                        "&e[Ancient Angler]&f The Queen herself! You are a true hunter.",
                        "&e[Ancient Angler]&f Take this bait... use it wisely.",
                        "&e[Ancient Angler]&f Hunt the true legends."
                    );'''

lqm = lqm.replace(old_thrill, new_thrill)

# 2. Update The Quiet Reward to actually give the Anchor
old_quiet = '''player.giveExpLevels(10); 
                    sendLines(player, true,
                        "&e[Ancient Angler]&f You understand the quiet of the waters.",
                        "&e[Ancient Angler]&f Take this wisdom, and an ancient relic.",
                        "&e[Ancient Angler]&f May it remind you to be patient."
                    );'''

new_quiet = '''player.giveExpLevels(10); 
                    ItemStack anchor = new ItemStack(Material.NAUTILUS_SHELL);
                    ItemMeta anchorMeta = anchor.getItemMeta();
                    if (anchorMeta != null) {
                        anchorMeta.setDisplayName(TextUtil.legacyColorize("&8&lFossilized Anchor"));
                        anchorMeta.setLore(Arrays.asList(
                            TextUtil.legacyColorize("&7An ancient, heavy relic..."), 
                            TextUtil.legacyColorize("&7Proof of your ultimate patience.")
                        ));
                        anchor.setItemMeta(anchorMeta);
                        player.getInventory().addItem(anchor);
                    }
                    sendLines(player, true,
                        "&e[Ancient Angler]&f You understand the quiet of the waters.",
                        "&e[Ancient Angler]&f Take this wisdom, and an ancient relic.",
                        "&e[Ancient Angler]&f May it remind you to be patient."
                    );'''

lqm = lqm.replace(old_quiet, new_quiet)

with open(lqm_path, "w", encoding="utf-8") as f:
    f.write(lqm)

print("Updated rewards successfully.")
