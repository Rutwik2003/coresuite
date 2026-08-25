import re

with open('src/main/java/net/rocky/coresuite/module/customfishing/CustomFishingModule.java', 'r') as f:
    text = f.read()

stats_block = '''            statsMeta.displayName(TextUtil.colorize("&b&l" + player.getName() + "'s Stats"));
            
            double fatiguePercent = calculateFatigue(player, data);
            int fatigueDisplay = (int) Math.round(fatiguePercent * 100);
            String fatigueColor = fatigueDisplay >= 85 ? "&4" : fatigueDisplay >= 75 ? "&c" : fatigueDisplay >= 50 ? "&6" : "&e";

            statsMeta.lore(Arrays.asList(
                TextUtil.colorize("&7Level: &b" + data.level),
                TextUtil.colorize("&7XP: &b" + data.xp + "&7/&b" + needed),
                TextUtil.colorize("&7Total Catches: &b" + data.totalCatches),
                TextUtil.colorize("&7Fatigue: " + fatigueColor + fatigueDisplay + "%"),
                "",
                TextUtil.colorize("&eHold your fishing rod and click"),
                TextUtil.colorize("&eto view detailed catch stats!")
            ));
            statsHead.setItemMeta(statsMeta);
        }
        gui.setItem(13, new net.rocky.coresuite.common.gui.GuiButton(statsHead, e -> {
            e.setCancelled(true);
            player.closeInventory();
            sendDetailedStats(player, data);
        }));'''

old_stats = '''            statsMeta.displayName(TextUtil.colorize("&b&l" + player.getName() + "'s Stats"));
            statsMeta.lore(Arrays.asList(
                TextUtil.colorize("&7Level: &b" + data.level),
                TextUtil.colorize("&7XP: &b" + data.xp + "&7/&b" + needed),
                TextUtil.colorize("&7Total Catches: &b" + data.totalCatches)
            ));
            statsHead.setItemMeta(statsMeta);
        }
        gui.setItem(13, new net.rocky.coresuite.common.gui.GuiButton(statsHead, e -> {}));'''

text = text.replace(old_stats, stats_block)

new_method = '''
    private void sendDetailedStats(Player player, FishData data) {
        ItemStack rod = player.getInventory().getItemInMainHand();
        if (rod == null || rod.getType() != org.bukkit.Material.FISHING_ROD) {
            TextUtil.sendMessage(player, config.getString("prefix", "&8[&b&lFishing&8] &r") + "&cYou must be holding a Fishing Rod to view detailed stats!");
            return;
        }

        double fatiguePercent = calculateFatigue(player, data);
        double fatigueMult = 1.0 - fatiguePercent;
        
        int luck = rod.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA);
        
        BaitManager.ActiveBait activeBait = baitManager != null ? baitManager.getActiveBait(player.getUniqueId()) : null;
        double baitCatchBonus = activeBait != null ? activeBait.catchChanceBonus : 0.0;
        double baitWeightBonus = activeBait != null ? activeBait.weightBonus : 0.0;
        
        int streak = fishStreak.getOrDefault(player.getUniqueId(), 0);
        double streakBonus = Math.pow(streak, 0.6) * 0.045;

        double baseCatchMult = (1.0 + (luck * 0.15) + streakBonus + baitCatchBonus) * fatigueMult;
        double multCap = config.getDouble("streak.multiplier-cap", 5.0);
        if (baseCatchMult > multCap) baseCatchMult = multCap;
        
        double tierMult = 1.0;
        int lureLvl = 0;
        int heavyLvl = 0;
        int doubleHookLvl = 0;
        int treasureLvl = 0;
        
        if (enchantManager != null) {
            tierMult = enchantManager.getRodTier(rod).getEnchantMultiplier();
            lureLvl = enchantManager.getEnchantLevel(rod, net.rocky.coresuite.module.customfishing.FishingEnchantManager.CustomEnchant.LURE_OF_THE_DEEP);
            heavyLvl = enchantManager.getEnchantLevel(rod, net.rocky.coresuite.module.customfishing.FishingEnchantManager.CustomEnchant.HEAVYWEIGHT);
            doubleHookLvl = enchantManager.getEnchantLevel(rod, net.rocky.coresuite.module.customfishing.FishingEnchantManager.CustomEnchant.DOUBLE_HOOK);
            treasureLvl = enchantManager.getEnchantLevel(rod, net.rocky.coresuite.module.customfishing.FishingEnchantManager.CustomEnchant.TREASURE_HUNTER);
        }

        double fishCatchBonusMult = baseCatchMult * (1.0 + (lureLvl * 0.1 * tierMult));
        double weightBonusMult = (1.0 + (heavyLvl * 0.05 * tierMult) + baitWeightBonus);
        double treasureBonusMult = baseCatchMult * (1.0 + (treasureLvl * 0.2 * tierMult));
        double doubleHookChance = (doubleHookLvl * 0.05 * tierMult) * 100.0;

        TextUtil.sendMessage(player, "&8&m----------------------------------------");
        TextUtil.sendMessage(player, "&b&lDetailed Fishing Stats");
        TextUtil.sendMessage(player, "&7Based on your current rod, bait, and conditions:");
        TextUtil.sendMessage(player, "");
        TextUtil.sendMessage(player, "&7General Catch Multiplier: &a" + String.format("%.2fx", baseCatchMult) + (fatiguePercent > 0 ? " &c(Fatigue Penalty Active)" : ""));
        TextUtil.sendMessage(player, "&7Fish Catch Rate Multiplier: &a" + String.format("%.2fx", fishCatchBonusMult));
        TextUtil.sendMessage(player, "&7Fish Weight Multiplier: &e" + String.format("%.2fx", weightBonusMult));
        TextUtil.sendMessage(player, "&7Treasure Find Multiplier: &d" + String.format("%.2fx", treasureBonusMult));
        if (doubleHookChance > 0) {
            TextUtil.sendMessage(player, "&7Double Hook Chance: &6" + String.format("%.1f%%", doubleHookChance));
        }
        TextUtil.sendMessage(player, "&8&m----------------------------------------");
    }

    private void openBuyBagGUI'''

text = text.replace("    private void openBuyBagGUI", new_method)

with open('src/main/java/net/rocky/coresuite/module/customfishing/CustomFishingModule.java', 'w') as f:
    f.write(text)

print('Success')
