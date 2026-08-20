import re

content = '''    private void openQuestGUI(Player player, org.bukkit.entity.Entity npc) {
        int stage = getStage(player.getUniqueId());
        Gui gui = new Gui("&9Ancient Angler", 3);
        gui.fillBorder(Material.BLUE_STAINED_GLASS_PANE);

        if (stage == 0) {
            ItemStack info = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta m = info.getItemMeta();
            m.setDisplayName(TextUtil.legacyColorize("&bThe Awakening"));
            m.setLore(wrapDialogue("&e&lAncient Angler: &7\\"Who awakens me from the slumber of the deep? ...Hm? Let me look at you. My eyes aren't what they used to be... *sniff sniff* ...Ah, yes. I know that smell anywhere. The undeniable stench of sea salt, fish guts, and the open ocean. You're an angler, aren't you? Heh. You remind me of myself, back before my joints turned to coral. In my day, we didn't use fancy lures. We wrestled Leviathans with our bare hands! But I digress... an old man's memory is like the tide, always drifting out. You summoned me for a reason. Tell me, little guppy... what is it you truly seek? Do you seek unimaginable wealth, or to master the endless ocean?\\""));
            info.setItemMeta(m);
            gui.setItem(4, new GuiButton(info, e -> {}));

            ItemStack greed = new ItemStack(Material.GOLD_BLOCK);
            ItemMeta mGreed = greed.getItemMeta();
            mGreed.setDisplayName(TextUtil.legacyColorize("&e&lPath of Greed"));
            List<String> lGreed = new ArrayList<>();
            lGreed.add(TextUtil.legacyColorize("&7\\"I seek unimaginable wealth.\\""));
            lGreed.add(TextUtil.legacyColorize(""));
            lGreed.add(TextUtil.legacyColorize("&cLocks you into the Path of Wealth."));
            mGreed.setLore(lGreed);
            greed.setItemMeta(mGreed);
            gui.setItem(11, new GuiButton(greed, e -> {
                setStage(player.getUniqueId(), 101);
                TextUtil.sendMessage(player, "&e&lAncient Angler: &7\\"Oho! Unimaginable wealth, you say? Your eyes gleam like a freshly gutted cod!\\"");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
                openQuestGUI(player, npc);
            }));

            ItemStack mastery = new ItemStack(Material.ENCHANTED_BOOK);
            ItemMeta mMastery = mastery.getItemMeta();
            mMastery.setDisplayName(TextUtil.legacyColorize("&b&lPath of Mastery"));
            List<String> lMastery = new ArrayList<>();
            lMastery.add(TextUtil.legacyColorize("&7\\"I seek to master the endless ocean.\\""));
            lMastery.add(TextUtil.legacyColorize(""));
            lMastery.add(TextUtil.legacyColorize("&cLocks you into the Path of Mastery."));
            mMastery.setLore(lMastery);
            mastery.setItemMeta(mMastery);
            gui.setItem(15, new GuiButton(mastery, e -> {
                setStage(player.getUniqueId(), 201);
                TextUtil.sendMessage(player, "&e&lAncient Angler: &7\\"Mastery? Ha! A lofty goal for a guppy.\\"");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
                openQuestGUI(player, npc);
            }));
            gui.open(player);
            return;
        }

        if (stage == 999) {
            ItemStack info = new ItemStack(Material.BARRIER);
            ItemMeta m = info.getItemMeta();
            m.setDisplayName(TextUtil.legacyColorize("&cThe Final Departure"));
            m.setLore(wrapDialogue("&e&lAncient Angler: &7\\"Your fate is sealed. I have nothing left to teach you.\\""));
            info.setItemMeta(m);
            gui.setItem(13, new GuiButton(info, e -> player.closeInventory()));
            gui.open(player);
            return;
        }

        // ---------- PATH OF GREED (100s) ----------
        if (stage == 101) {
            ItemStack info = new ItemStack(Material.PAPER);
            ItemMeta m = info.getItemMeta();
            m.setDisplayName(TextUtil.legacyColorize("&eTrial 1: The Shimmering Shoal"));
            List<String> l = wrapDialogue("&e&lAncient Angler: &7\\"Greed is a heavy anchor, little one... let us see if you can carry it. Go fetch me the vibrant colors of the coral reef. Chop chop!\\"");
            l.add("");
            l.add(TextUtil.legacyColorize("&cTask: &fBring 10 Clownfish & 10 Pufferfish"));
            m.setLore(l);
            info.setItemMeta(m);
            gui.setItem(13, new GuiButton(info, e -> {
                if (countFish(player, "clownfish") >= 10 && countFish(player, "pufferfish") >= 10) {
                    takeFishAmount(player, "clownfish", 10);
                    takeFishAmount(player, "pufferfish", 10);
                    setStage(player.getUniqueId(), 102);
                    TextUtil.sendMessage(player, "&aYou completed the trial!");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                    openQuestGUI(player, npc);
                } else {
                    TextUtil.sendMessage(player, "&cYou do not have the required fish! (Need 10 Clownfish, 10 Pufferfish)");
                    player.closeInventory();
                }
            }));
        } else if (stage == 102) {
            ItemStack info = new ItemStack(Material.PAPER);
            ItemMeta m = info.getItemMeta();
            m.setDisplayName(TextUtil.legacyColorize("&eTrial 2: The Sunken Gold"));
            List<String> l = wrapDialogue("&e&lAncient Angler: &7\\"Hah! Pretty colors, but lacking true value. Reminds me of a mermaid I once knew... but that's a tale for another time. Dive into the dark waters and retrieve a creature of pure, untarnished gold.\\"");
            l.add("");
            l.add(TextUtil.legacyColorize("&cTask: &fBring 1 Epic Golden Koi"));
            m.setLore(l);
            info.setItemMeta(m);
            gui.setItem(13, new GuiButton(info, e -> processFishTurnIn(player, "golden_koi", 103, "&aYou completed the trial!", npc)));
        } else if (stage == 103) {
            ItemStack info = new ItemStack(Material.GOLD_BLOCK);
            ItemMeta m = info.getItemMeta();
            m.setDisplayName(TextUtil.legacyColorize("&eFinal Trial: The Exchange"));
            List<String> l = wrapDialogue("&e&lAncient Angler: &7\\"You have proven your avarice. Now, for the final sacrifice. The abyss holds a creature of magma and gold. Bring it to me, and I shall make you rich beyond your wildest dreams!\\"");
            l.add("");
            l.add(TextUtil.legacyColorize("&cTask: &fBring 1 Legendary Lava Particle"));
            m.setLore(l);
            info.setItemMeta(m);
            gui.setItem(13, new GuiButton(info, e -> {
                if (takeFish(player, "lava_particle")) {
                    setStage(player.getUniqueId(), 999);
                    ItemStack mythical = module.createFishItem("&6&lMythical Abyssal Leviathan", "TROPICAL_FISH", 500.0, 1000.0, ""); 
                    ItemMeta meta = mythical.getItemMeta();
                    if (meta != null) {
                        meta.getPersistentDataContainer().set(module.getSpeciesKey(), PersistentDataType.STRING, "mythical_leviathan");
                        mythical.setItemMeta(meta);
                    }
                    player.getInventory().addItem(mythical);
                    TextUtil.sendMessage(player, "&e&oThe Angler crafts the essence into a shimmering golden leviathan. \\"May your pockets be full!\\" he shouts.");
                    completeRitual(player, npc);
                }
            }));
        }

        // ---------- PATH OF MASTERY (200s) ----------
        else if (stage == 201) {
            ItemStack info = new ItemStack(Material.PAPER);
            ItemMeta m = info.getItemMeta();
            m.setDisplayName(TextUtil.legacyColorize("&bTrial 1: The Dedication"));
            List<String> l = wrapDialogue("&e&lAncient Angler: &7\\"Mastery requires patience... something few young ones possess these days. Prove your dedication to the rod, just as I did centuries ago!\\"");
            l.add("");
            l.add(TextUtil.legacyColorize("&cTask: &fBring 20 Custom Fish of any kind"));
            m.setLore(l);
            info.setItemMeta(m);
            gui.setItem(13, new GuiButton(info, e -> {
                int count = 0;
                for (ItemStack i : player.getInventory().getContents()) {
                    if (i != null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) {
                        count += i.getAmount();
                    }
                }
                if (count >= 20) {
                    int removed = 0;
                    for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
                        ItemStack i = player.getInventory().getItem(slot);
                        if (i != null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) {
                            while (removed < 20 && i.getAmount() > 0) {
                                i.setAmount(i.getAmount() - 1);
                                removed++;
                            }
                        }
                    }
                    setStage(player.getUniqueId(), 202);
                    TextUtil.sendMessage(player, "&aYou completed the trial!");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                    openQuestGUI(player, npc);
                } else {
                    TextUtil.sendMessage(player, "&cYou do not have enough custom fish. (" + count + "/20)");
                    player.closeInventory();
                }
            }));
        } else if (stage == 202) {
            ItemStack info = new ItemStack(Material.PAPER);
            ItemMeta m = info.getItemMeta();
            m.setDisplayName(TextUtil.legacyColorize("&bTrial 2: The Frozen Heart"));
            List<String> l = wrapDialogue("&e&lAncient Angler: &7\\"Not bad... you have patience. Now, show me endurance! When I was your age, I traveled to the frozen seas, where the water bites like a blade. Go on, fetch me a catch from the ice!\\"");
            l.add("");
            l.add(TextUtil.legacyColorize("&cTask: &fBring 1 Legendary Char"));
            m.setLore(l);
            info.setItemMeta(m);
            gui.setItem(13, new GuiButton(info, e -> processFishTurnIn(player, "char", 203, "&aYou completed the trial!", npc)));
        } else if (stage == 203) {
            ItemStack info = new ItemStack(Material.PAPER);
            ItemMeta m = info.getItemMeta();
            m.setDisplayName(TextUtil.legacyColorize("&bTrial 3: The Jungle Predator"));
            List<String> l = wrapDialogue("&e&lAncient Angler: &7\\"The cold didn't break you? Color me impressed! But the humid rivers hide fierce predators. Catch the king of the jungle rapids. Try not to lose a finger!\\"");
            l.add("");
            l.add(TextUtil.legacyColorize("&cTask: &fBring 1 Legendary Cherry Salmon"));
            m.setLore(l);
            info.setItemMeta(m);
            gui.setItem(13, new GuiButton(info, e -> processFishTurnIn(player, "cherry_salmon", 204, "&aYou completed the trial!", npc)));
        } else if (stage == 204) {
            ItemStack info = new ItemStack(Material.ENCHANTED_BOOK);
            ItemMeta m = info.getItemMeta();
            m.setDisplayName(TextUtil.legacyColorize("&bFinal Trial: The Ascension"));
            List<String> l = wrapDialogue("&e&lAncient Angler: &7\\"Ice and earth, conquered by a mere mortal! Well, only the fire of the deep remains. Bring me the essence of the abyss, and I shall pass on my greatest secret.\\"");
            l.add("");
            l.add(TextUtil.legacyColorize("&cTask: &fBring 1 Legendary Lava Particle"));
            m.setLore(l);
            info.setItemMeta(m);
            gui.setItem(13, new GuiButton(info, e -> {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType() != Material.FISHING_ROD) {
                    TextUtil.sendMessage(player, "&cYou must be holding the Fishing Rod you want to enchant in your main hand!");
                    player.closeInventory();
                    return;
                }
                if (takeFish(player, "lava_particle")) {
                    setStage(player.getUniqueId(), 999);
                    module.getEnchantManager().setEnchantLevel(hand, CustomEnchant.TIRELESS_CASTER, 3);
                    TextUtil.sendMessage(player, "&b&oThe Angler weaves the essence into your rod, granting you Tireless Caster III!");
                    completeRitual(player, npc);
                }
            }));
        }
        gui.open(player);
    }

    private List<String> wrapDialogue(String text) {
        List<String> wrapped = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if (line.length() + word.length() > 40) {
                wrapped.add(TextUtil.legacyColorize(line.toString().trim()));
                line = new StringBuilder("&7");
            }
            line.append(word).append(" ");
        }
        if (line.length() > 0) wrapped.add(TextUtil.legacyColorize(line.toString().trim()));
        return wrapped;
    }

    private int countFish(Player player, String species) {
        int c = 0;
        for (ItemStack i : player.getInventory().getContents()) {
            if (i != null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) {
                String sp = i.getItemMeta().getPersistentDataContainer().get(module.getSpeciesKey(), PersistentDataType.STRING);
                if (species.equalsIgnoreCase(sp)) {
                    c += i.getAmount();
                }
            }
        }
        return c;
    }

    private void takeFishAmount(Player player, String species, int amount) {
        int removed = 0;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack i = player.getInventory().getItem(slot);
            if (i != null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) {
                String sp = i.getItemMeta().getPersistentDataContainer().get(module.getSpeciesKey(), PersistentDataType.STRING);
                if (species.equalsIgnoreCase(sp)) {
                    while (removed < amount && i.getAmount() > 0) {
                        i.setAmount(i.getAmount() - 1);
                        removed++;
                    }
                }
            }
        }
    }

    private boolean takeFish(Player player, String requiredSpecies) {
        for (ItemStack i : player.getInventory().getContents()) {
            if (i != null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) {
                String sp = i.getItemMeta().getPersistentDataContainer().get(module.getSpeciesKey(), PersistentDataType.STRING);
                if (requiredSpecies.equalsIgnoreCase(sp)) {
                    i.setAmount(i.getAmount() - 1);
                    return true;
                }
            }
        }
        TextUtil.sendMessage(player, "&cYou do not have the required fish (" + requiredSpecies + ")!");
        player.closeInventory();
        return false;
    }

    private void completeRitual(Player player, org.bukkit.entity.Entity npc) {
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        npc.remove();
        player.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, npc.getLocation(), 100, 1.0, 1.0, 1.0, 0.1);
        player.playSound(npc.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
    }

    private void processFishTurnIn(Player player, String requiredSpecies, int nextStage, String successMsg, org.bukkit.entity.Entity npc) {
        boolean hasFish = false;
        ItemStack saved = null;
        for (ItemStack i : player.getInventory().getContents()) {
            if (i != null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) {
                String sp = i.getItemMeta().getPersistentDataContainer().get(module.getSpeciesKey(), PersistentDataType.STRING);
                if (requiredSpecies.equalsIgnoreCase(sp)) {
                    hasFish = true;
                    saved = i.clone();
                    saved.setAmount(1);
                    i.setAmount(i.getAmount() - 1);
                    break;
                }
            }
        }
        if (hasFish) {
            setStage(player.getUniqueId(), nextStage);
            TextUtil.sendMessage(player, successMsg);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            openQuestGUI(player, npc);
        } else {
            TextUtil.sendMessage(player, "&cYou do not have the required fish.");
            player.closeInventory();
        }
    }
}
'''

with open("c:/Users/rutwi/OneDrive - MSFT/Desktop/coresuit/src/main/java/net/rocky/coresuite/module/customfishing/LegendaryQuestManager.java", "r") as f:
    original = f.read()

import re
new_file = re.sub(r"private void openQuestGUI\(Player player, org\.bukkit\.entity\.Entity npc\).*$", content, original, flags=re.DOTALL)

with open("c:/Users/rutwi/OneDrive - MSFT/Desktop/coresuit/src/main/java/net/rocky/coresuite/module/customfishing/LegendaryQuestManager.java", "w") as f:
    f.write(new_file)
