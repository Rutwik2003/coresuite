package net.rocky.coresuite.module.harvestmarket;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.economy.EconomyManager;
import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.gui.GuiButton;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

public class MarketGUI {
    private final CoreSuitePlugin plugin;
    private final SeasonManager seasonManager;
    private final SellManager sellManager;
    private final DailyBountyManager bountyManager;
    private final HarvestLeaderboardManager lbManager;
    private final EconomyManager economy;
    private final FileConfiguration config;

    public MarketGUI(CoreSuitePlugin plugin, SeasonManager seasonManager, SellManager sellManager, DailyBountyManager bountyManager, HarvestLeaderboardManager lbManager, EconomyManager economy, FileConfiguration config) {
        this.plugin        = plugin;
        this.seasonManager = seasonManager;
        this.sellManager   = sellManager;
        this.bountyManager = bountyManager;
        this.lbManager     = lbManager;
        this.economy       = economy;
        this.config        = config;
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  MAIN HARVEST MARKET SCREEN (5 Rows)
    // ══════════════════════════════════════════════════════════════════════════

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        
        sellManager.loadQuota(uuid, () -> {
            bountyManager.loadOrGenerateBounties(player, bounties -> {
                int seasonId = seasonManager.getCurrentSeason() != null ? seasonManager.getCurrentSeason().getId() : -1;
                lbManager.getTopSellers(seasonId, 3, topSellers -> {
                    
                    Gui gui = new Gui("&8▶ &2&l🌾 Harvest Market &8◀", 5);
                    gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);
                    
                    Season season = seasonManager.getCurrentSeason();
                    String seasonIdStr = season != null ? String.valueOf(season.getId()) : "None";
                    String cropsStr    = season != null ? season.getCropListFormatted() : "&cNone";
                    String timeLeft    = season != null ? season.getTimeRemainingString() : "&cNone";
                    double bonusMult   = config.getDouble("bonus-multiplier", 2.5);
                    double goldenMult  = config.getDouble("golden-multiplier", 25.0);

                    // ── Row 0: Top Header & Navigation ────────────────────────
                    gui.setItem(0, GuiButton.of(Material.BOOK, "&e&l📖 Farmer's Guide & Rules",
                        List.of(
                            "&8&m────────────────────",
                            "&7Learn how to earn maximum profit:",
                            "&a  ▸ In-Season Bonus (2.5×)",
                            "&a  ▸ Season Quota Limits",
                            "&6  ▸ Golden Crop Mutations (25×)",
                            "&e  ▸ Daily Farming Bounties",
                            "&8&m────────────────────",
                            "&e▶ Click to read guide"
                        ),
                        e -> {
                            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                            openHowItWorks(player);
                        }
                    ));

                    gui.setItem(4, GuiButton.of(Material.WHEAT, "&2&l🌾 Harvest Market &8[Season #" + seasonIdStr + "]",
                        List.of(
                            "&8&m────────────────────",
                            "&7Season:     &a#" + seasonIdStr,
                            "&7Featured:   " + cropsStr,
                            "&7Time Left:  &e" + timeLeft,
                            "&8&m────────────────────",
                            "&7Sell featured crops below for a &e" + bonusMult + "× cash bonus&7!",
                            "&7Break mature crops for &6Golden Crop &7drops."
                        ), e -> {}
                    ));

                    // Leaderboard item (slot 8)
                    List<String> lbLore = new ArrayList<>();
                    lbLore.add("&8&m────────────────────");
                    for (int i = 0; i < 3; i++) {
                        if (i < topSellers.size()) {
                            LeaderboardEntry entry = topSellers.get(i);
                            String color = i == 0 ? "&e" : (i == 1 ? "&f" : "&6");
                            lbLore.add(color + "#" + (i + 1) + " &f" + entry.playerName + "&7: &a" + TextUtil.formatNumber(entry.totalSold) + " crops");
                        } else {
                            lbLore.add("&7#" + (i + 1) + " &8None");
                        }
                    }
                    lbLore.add("&8&m────────────────────");
                    lbLore.add("&7Top 3 receive crate keys & cash at season end!");
                    gui.setItem(8, GuiButton.of(Material.GOLD_BLOCK, "&6&l🏆 Top Farmers", lbLore, e -> {}));

                    // ── Row 1: Featured In-Season Crops (slots 10, 12, 14, 16) ─
                    List<Material> inSeason = season != null ? season.getInSeasonCrops() : new ArrayList<>();
                    Map<Material, Double> basePrices = sellManager.getBasePrices();
                    int used = sellManager.getQuotaUsed(uuid);
                    int max = config.getInt("max-quota-per-season", 5000);
                    int remainingQuota = Math.max(0, max - used);

                    int[] featuredSlots = {10, 12, 14, 16};
                    for (int i = 0; i < Math.min(inSeason.size(), featuredSlots.length); i++) {
                        Material mat = inSeason.get(i);
                        double basePrice  = basePrices.getOrDefault(mat, 10.0);
                        double bonusPrice = basePrice * bonusMult;
                        int count = countItems(player, mat, false);

                        List<String> cropLore = new ArrayList<>();
                        cropLore.add("&8&m────────────────────");
                        cropLore.add("&7Standard Price: &f$" + TextUtil.formatNumber(basePrice));
                        cropLore.add("&aSeason Price:   &e&l$" + TextUtil.formatNumber(bonusPrice) + " &a(" + bonusMult + "×)");
                        cropLore.add("&8&m────────────────────");
                        cropLore.add("&7Your Inventory: &f" + count + " items");
                        cropLore.add("&7Remaining Quota: &a" + remainingQuota + " items");
                        cropLore.add("&8&m────────────────────");

                        if (count > 0) {
                            if (remainingQuota > 0) {
                                int atBonus = Math.min(count, remainingQuota);
                                int atBase = count - atBonus;
                                double earnings = (atBonus * bonusPrice) + (atBase * basePrice);
                                cropLore.add("&e▶ Click to SELL ALL for &a$" + TextUtil.formatNumber(earnings) + "&e!");
                            } else {
                                cropLore.add("&cQuota reached — selling at standard base price");
                                cropLore.add("&e▶ Click to sell for &f$" + TextUtil.formatNumber(count * basePrice));
                            }
                        } else {
                            cropLore.add("&c✗ You have none in your inventory");
                        }

                        gui.setItem(featuredSlots[i], GuiButton.of(mat, "&a&l" + capitalize(mat.name()) + " &2[IN-SEASON]", cropLore, e -> {
                            if (count > 0) {
                                sellManager.sellCrops(player, mat, count, false, total -> {
                                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                                    lbManager.recordSell(uuid, player.getName(), seasonId, count);
                                    bountyManager.recordSell(uuid, mat, count);
                                    TextUtil.sendMessage(player, config.getString("messages.sold-bonus", "")
                                            .replace("{amount}", String.valueOf(count))
                                            .replace("{crop}", capitalize(mat.name()))
                                            .replace("{money}", "$" + TextUtil.formatNumber(total))
                                            .replace("{rate}", remainingQuota > 0 ? String.valueOf(bonusMult) : "1.0"));
                                    open(player);
                                }, () -> {});
                            } else {
                                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
                                TextUtil.sendMessage(player, "&cYou don't have any " + capitalize(mat.name()) + " to sell!");
                            }
                        }));
                    }

                    // ── Row 2: Separator Bar (slots 18-26) ───────────────────
                    for (int i = 18; i <= 26; i++) {
                        gui.setItem(i, GuiButton.filler(Material.GREEN_STAINED_GLASS_PANE));
                    }
                    gui.setItem(22, GuiButton.of(Material.LIME_STAINED_GLASS_PANE, "&a&l✦ VAULT & DAILY BOUNTIES ✦", List.of(), e -> {}));

                    // ── Row 3: Golden Crops Vault, Bounties, Off-Season Button ─
                    // Slot 28: Golden Crops Vault (ALWAYS GOLD_BLOCK, never transforms)
                    List<Material> allCrops = new ArrayList<>(basePrices.keySet());
                    int totalGolden = 0;
                    double goldenPayout = 0;
                    for (Material m : allCrops) {
                        int gc = countItems(player, m, true);
                        if (gc > 0) {
                            totalGolden += gc;
                            goldenPayout += gc * basePrices.getOrDefault(m, 10.0) * goldenMult;
                        }
                    }

                    List<String> goldLore = new ArrayList<>();
                    goldLore.add("&8&m────────────────────");
                    goldLore.add("&7Rare crop mutations discovered while");
                    goldLore.add("&7harvesting fully-grown crops.");
                    goldLore.add("&8&m────────────────────");
                    goldLore.add("&7Value Multiplier: &6&l" + goldenMult + "× Base Price");
                    goldLore.add("&7In Your Inventory: &e" + totalGolden + " Golden Crop" + (totalGolden == 1 ? "" : "s"));
                    goldLore.add("&8&m────────────────────");

                    if (totalGolden > 0) {
                        goldLore.add("&a▶ Click to SELL ALL for &6&l$" + TextUtil.formatNumber(goldenPayout) + "&a!");
                        final double finalPayout = goldenPayout;
                        final int finalCount = totalGolden;
                        ItemStack goldItem = new ItemStack(Material.GOLD_BLOCK);
                        ItemMeta goldMeta = goldItem.getItemMeta();
                        if (goldMeta != null) {
                            goldMeta.displayName(TextUtil.colorize("&6&l✨ Cash In Golden Crops"));
                            goldMeta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
                            goldMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                            goldMeta.lore(goldLore.stream().map(TextUtil::colorize).collect(Collectors.toList()));
                            goldItem.setItemMeta(goldMeta);
                        }
                        gui.setItem(28, new GuiButton(goldItem, e -> {
                            for (Material m : allCrops) {
                                int gc = countItems(player, m, true);
                                if (gc > 0) {
                                    sellManager.sellCrops(player, m, gc, true, earned -> {}, () -> {});
                                }
                            }
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                            TextUtil.sendMessage(player, "&8[&2🌾 Market&8] &6✨ Sold &e" + finalCount + "x Golden Crops &6for &a$" + TextUtil.formatNumber(finalPayout) + "&6!");
                            open(player);
                        }));
                    } else {
                        goldLore.add("&8(Harvest mature crops to find golden mutations)");
                        gui.setItem(28, GuiButton.of(Material.GOLD_BLOCK, "&6&l✨ Golden Crop Vault", goldLore, e -> {
                            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
                            TextUtil.sendMessage(player, "&8[&2🌾 Market&8] &cYou don't have any Golden Crops in your inventory yet!");
                        }));
                    }

                    // Slots 30, 31, 32: Daily Bounties
                    int[] bountySlots = {30, 31, 32};
                    for (int i = 0; i < Math.min(bounties.size(), bountySlots.length); i++) {
                        DailyBounty b = bounties.get(i);
                        List<String> bLore = new ArrayList<>();
                        bLore.add("&8&m────────────────────");
                        bLore.add("&7Objective: &fSell " + b.amountNeeded + "× " + capitalize(b.material.name()));
                        bLore.add("&7Reward:    &a$" + TextUtil.formatNumber(b.moneyReward));
                        bLore.add("&8&m────────────────────");
                        bLore.add("&7Progress:  &f" + b.amountDone + "/" + b.amountNeeded);
                        bLore.add(progressBar(b.amountDone, b.amountNeeded, 10));
                        bLore.add("&8&m────────────────────");

                        boolean canClaim = b.amountDone >= b.amountNeeded && !b.claimed;
                        if (b.claimed) {
                            bLore.add("&2✓ Claimed today");
                        } else if (canClaim) {
                            bLore.add("&a▶ Click to CLAIM REWARD!");
                        } else {
                            bLore.add("&7Sell more " + capitalize(b.material.name()) + " to complete");
                        }

                        gui.setItem(bountySlots[i], GuiButton.of(b.material, "&e📋 Daily Bounty #" + b.slot, bLore, e -> {
                            if (canClaim) {
                                bountyManager.claimBounty(uuid, b.slot, () -> {
                                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                                    TextUtil.sendMessage(player, config.getString("messages.bounty-claimed", "&a✨ Daily bounty claimed! &e+{money}").replace("{money}", "$" + TextUtil.formatNumber(b.moneyReward)));
                                    open(player);
                                }, () -> open(player));
                            }
                        }));
                    }

                    // Slot 34: Off-Season Crops Button
                    List<Material> offSeason = allCrops.stream().filter(m -> !inSeason.contains(m)).collect(Collectors.toList());
                    gui.setItem(34, GuiButton.of(Material.CHEST, "&7📦 Off-Season Crops",
                        List.of(
                            "&8&m────────────────────",
                            "&7View and sell all standard crops",
                            "&7at their normal base prices.",
                            "&8&m────────────────────",
                            "&7Available: &f" + offSeason.size() + " other crops",
                            "&e▶ Click to browse off-season crops"
                        ),
                        e -> {
                            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                            openOffSeason(player);
                        }
                    ));

                    // ── Row 4: Bottom Navigation Bar (slots 36-44) ────────────
                    gui.setItem(36, GuiButton.of(Material.PAPER, "&7Season Quota",
                        List.of(
                            "&8&m────────────────────",
                            "&7Bonus Quota: &f" + used + " / " + max,
                            "&7Remaining:   &a" + remainingQuota + " items",
                            "&8&m────────────────────",
                            "&7Crops sold beyond quota",
                            "&7receive standard base price."
                        ), e -> {}
                    ));

                    gui.setItem(40, GuiButton.of(Material.BARRIER, "&c&lClose", List.of(), e -> player.closeInventory()));

                    gui.setItem(44, GuiButton.of(Material.CLOCK, "&e⟳ Refresh", List.of("&7Update counts and prices"), e -> {
                        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                        open(player);
                    }));

                    gui.open(player);
                });
            });
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  OFF-SEASON CROPS SCREEN (4 Rows)
    // ══════════════════════════════════════════════════════════════════════════

    public void openOffSeason(Player player) {
        Gui gui = new Gui("&8▶ &7📦 Off-Season Crops &8◀", 4);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        Season season = seasonManager.getCurrentSeason();
        List<Material> inSeason = season != null ? season.getInSeasonCrops() : new ArrayList<>();
        Map<Material, Double> basePrices = sellManager.getBasePrices();
        List<Material> offSeason = basePrices.keySet().stream().filter(m -> !inSeason.contains(m)).collect(Collectors.toList());

        gui.setItem(4, GuiButton.of(Material.CHEST, "&7📦 Off-Season Crop Market",
            List.of(
                "&8&m────────────────────",
                "&7These crops are currently off-season.",
                "&7They sell at standard base prices.",
                "&8&m────────────────────"
            ), e -> {}
        ));

        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25};
        for (int i = 0; i < Math.min(offSeason.size(), slots.length); i++) {
            Material mat = offSeason.get(i);
            double price = basePrices.getOrDefault(mat, 10.0);
            int count = countItems(player, mat, false);

            List<String> lore = new ArrayList<>();
            lore.add("&8&m────────────────────");
            lore.add("&7Base Price: &f$" + TextUtil.formatNumber(price) + "/ea");
            lore.add("&8&m────────────────────");
            lore.add("&7Your Inventory: &f" + count + " items");
            lore.add("&8&m────────────────────");
            if (count > 0) {
                lore.add("&e▶ Click to sell all for &a$" + TextUtil.formatNumber(count * price) + "&e!");
            } else {
                lore.add("&c✗ None in inventory");
            }

            gui.setItem(slots[i], GuiButton.of(mat, "&7" + capitalize(mat.name()) + " &8[Base Rate]", lore, e -> {
                if (count > 0) {
                    sellManager.sellCrops(player, mat, count, false, total -> {
                        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                        int seasonId = season != null ? season.getId() : -1;
                        lbManager.recordSell(player.getUniqueId(), player.getName(), seasonId, count);
                        bountyManager.recordSell(player.getUniqueId(), mat, count);
                        TextUtil.sendMessage(player, "&a💰 Sold " + count + "x " + capitalize(mat.name()) + " for &e$" + TextUtil.formatNumber(total) + "&a!");
                        openOffSeason(player);
                    }, () -> {});
                } else {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
                    TextUtil.sendMessage(player, "&cYou don't have any " + capitalize(mat.name()) + " to sell!");
                }
            }));
        }

        // Slot 27: Back to Main Market
        gui.setItem(27, GuiButton.of(Material.SPECTRAL_ARROW, "&7◀ Back to Main Market", List.of(), e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            open(player);
        }));

        // Slot 35: Close
        gui.setItem(35, GuiButton.of(Material.BARRIER, "&c&lClose", List.of(), e -> player.closeInventory()));

        gui.open(player);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  FARMING GUIDE / HOW IT WORKS SCREEN (4 Rows)
    // ══════════════════════════════════════════════════════════════════════════

    public void openHowItWorks(Player player) {
        Gui gui = new Gui("&8▶ &e&l📖 Farmer's Guide &8◀", 4);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        double bonusMult = config.getDouble("bonus-multiplier", 2.5);
        double goldenMult = config.getDouble("golden-multiplier", 25.0);
        int maxQuota = config.getInt("max-quota-per-season", 5000);

        gui.setItem(4, GuiButton.of(Material.WRITTEN_BOOK, "&e&l📖 Harvest Market Guide",
            List.of(
                "&8&m────────────────────",
                "&7Everything you need to know to",
                "&7maximize your farming income!",
                "&8&m────────────────────"
            ), e -> {}
        ));

        // 1. Seasons
        gui.setItem(10, GuiButton.of(Material.WHEAT, "&a&l1. Crop Seasons",
            List.of(
                "&8&m────────────────────",
                "&7Every season, 4 random crops become",
                "&afeatured in-season &7crops.",
                "&7Sell them for a huge &e" + bonusMult + "× price multiplier&7!",
                "&8&m────────────────────"
            ), e -> {}
        ));

        // 2. Quotas
        gui.setItem(12, GuiButton.of(Material.CHEST, "&e&l2. Quota System",
            List.of(
                "&8&m────────────────────",
                "&7Each player can sell up to &f" + maxQuota + " crops",
                "&7at the bonus rate each season.",
                "&7Crops beyond that sell at base price.",
                "&8&m────────────────────"
            ), e -> {}
        ));

        // 3. Golden Crops
        gui.setItem(14, GuiButton.of(Material.GOLD_BLOCK, "&6&l3. Golden Mutations",
            List.of(
                "&8&m────────────────────",
                "&7Harvesting fully-grown crops has a chance",
                "&7to yield rare &6Golden Crops&7!",
                "&7Golden crops sell for &a" + goldenMult + "× base price&7!",
                "&8&m────────────────────"
            ), e -> {}
        ));

        // 4. Bounties & Leaderboards
        gui.setItem(16, GuiButton.of(Material.EMERALD, "&b&l4. Bounties & Rewards",
            List.of(
                "&8&m────────────────────",
                "&7Complete daily harvest bounties for extra cash.",
                "&7Top 3 sellers each season win rare crate keys",
                "&7and up to &a$75,000 cash&7!",
                "&8&m────────────────────"
            ), e -> {}
        ));

        // Slot 27: Back button
        gui.setItem(27, GuiButton.of(Material.SPECTRAL_ARROW, "&7◀ Back to Market", List.of(), e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            open(player);
        }));

        gui.open(player);
    }

    private String progressBar(int done, int needed, int length) {
        int progress = (int) ((double) done / needed * length);
        StringBuilder sb = new StringBuilder("&a");
        for (int i = 0; i < progress; i++) sb.append("█");
        sb.append("&8");
        for (int i = progress; i < length; i++) sb.append("█");
        return sb.toString();
    }

    private int countItems(Player player, Material material, boolean isGolden) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                if (isGolden == sellManager.isGoldenCrop(item)) {
                    count += item.getAmount();
                }
            }
        }
        return count;
    }

    private String capitalize(String str) {
        StringBuilder sb = new StringBuilder();
        for (String word : str.replace('_', ' ').toLowerCase().split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }
}
