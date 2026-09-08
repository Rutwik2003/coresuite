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

    public void open(Player player) {
        UUID uuid = player.getUniqueId();
        
        sellManager.loadQuota(uuid, () -> {
            bountyManager.loadOrGenerateBounties(player, bounties -> {
                int seasonId = seasonManager.getCurrentSeason() != null ? seasonManager.getCurrentSeason().getId() : -1;
                lbManager.getTopSellers(seasonId, 3, topSellers -> {
                    
                    Gui gui = new Gui("&8▶ &2&l🌾 Harvest Market &8◀", 6);
                    gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);
                    
                    Season season = seasonManager.getCurrentSeason();
                    String seasonIdStr = season != null ? String.valueOf(season.getId()) : "None";
                    String cropsStr    = season != null ? season.getCropListFormatted() : "&cNone";
                    String timeLeft    = season != null ? season.getTimeRemainingString() : "&cNone";
                    double bonusMult   = config.getDouble("bonus-multiplier", 2.5);
                    double goldenMult  = config.getDouble("golden-multiplier", 25.0);

                    // Slot 0: Guide / How It Works
                    gui.setItem(0, GuiButton.of(Material.BOOK, "&e&l📖 How It Works & Guide",
                        List.of(
                            "&8&m────────────────────",
                            "&7New to the Harvest Market?",
                            "&7Click here to learn about:",
                            "&a  ▸ Crop Seasons (2.5× Bonus)",
                            "&a  ▸ Fair Quota System",
                            "&6  ▸ Rare Golden Crops (25× Bonus)",
                            "&e  ▸ Daily Farming Bounties",
                            "&b  ▸ Seasonal Leaderboards",
                            "&8&m────────────────────",
                            "&e▶ Click to open Farming Guide"
                        ),
                        e -> {
                            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                            openHowItWorks(player);
                        }
                    ));
                    
                    // Slot 4: Main Header
                    gui.setItem(4, GuiButton.of(Material.WHEAT, "&2&l🌾 Harvest Market &8[Season #" + seasonIdStr + "]",
                        List.of(
                            "&8&m────────────────────",
                            "&7Season:     &a#" + seasonIdStr,
                            "&7Featured:   " + cropsStr,
                            "&7Time Left:   &e" + timeLeft,
                            "&8&m────────────────────",
                            "&7In-season crops earn a massive &e" + bonusMult + "×&7 bonus!",
                            "&7Break fully-grown crops for &6Golden Crops&7!",
                            "&8&m────────────────────"
                        ),
                        e -> {}
                    ));

                    // Separator row (27-35)
                    for (int i = 27; i <= 35; i++) {
                        gui.setItem(i, GuiButton.filler(Material.GREEN_STAINED_GLASS_PANE));
                    }
                    gui.setItem(31, GuiButton.of(Material.LIME_STAINED_GLASS_PANE, "&a&l📋 Daily Farming Bounties", List.of(
                        "&7Complete daily orders for cash bonuses!"
                    ), e -> {}));
                    
                    // Bottom navigation bar
                    for (int i = 45; i <= 53; i++) {
                        gui.setItem(i, GuiButton.filler(Material.BLACK_STAINED_GLASS_PANE));
                    }
                    gui.setItem(49, GuiButton.of(Material.BARRIER, "&c&lClose", List.of(), e -> player.closeInventory()));
                    gui.setItem(45, GuiButton.of(Material.PAPER, "&7Season Info", 
                        List.of(
                            "&8&m────────────────────",
                            "&7Season: &f#" + seasonIdStr,
                            "&7Ends:   &e" + timeLeft,
                            "&7Bonus:  &e" + bonusMult + "x",
                            "&8&m────────────────────"
                        ), e -> {}));
                    gui.setItem(53, GuiButton.of(Material.CLOCK, "&eRefresh", List.of(), e -> open(player)));

                    int[] inSeasonSlots = {10, 11, 12, 13, 14, 15, 16};
                    int[] offSeasonSlots = {19, 20, 21, 22, 23, 24, 25};
                    
                    List<Material> inSeason = season != null ? season.getInSeasonCrops() : new ArrayList<>();
                    Map<Material, Double> basePrices = sellManager.getBasePrices();
                    List<Material> allCrops = new ArrayList<>(basePrices.keySet());
                    List<Material> offSeason = allCrops.stream().filter(m -> !inSeason.contains(m)).collect(Collectors.toList());
                    
                    int inIndex = 0;
                    for (Material mat : inSeason) {
                        if (inIndex >= inSeasonSlots.length) break;
                        double basePrice = basePrices.getOrDefault(mat, 0.0);
                        double bonusPrice = basePrice * bonusMult;
                        int used = sellManager.getQuotaUsed(uuid);
                        int max = config.getInt("max-quota-per-season", 5000);
                        int remaining = Math.max(0, max - used);
                        int count = countItems(player, mat, false);
                        
                        List<String> lore = new ArrayList<>();
                        lore.add("&8&m────────────────────");
                        lore.add("&7Base price: &f$" + TextUtil.formatNumber(basePrice) + "/ea");
                        lore.add("&a" + bonusMult + "× Season price: &e&l$" + TextUtil.formatNumber(bonusPrice) + "/ea");
                        lore.add("&8&m────────────────────");
                        lore.add("&7Season quota:    &f" + used + "/" + max + " items");
                        lore.add("&7Remaining quota: &a" + remaining);
                        lore.add("&8&m────────────────────");
                        
                        if (count > 0) {
                            lore.add("&aYou have: &f" + count);
                            if (remaining > 0) lore.add("&e▶ Click to sell ALL at bonus price!");
                            else lore.add("&cQuota reached — selling at base rate ($" + TextUtil.formatNumber(basePrice) + ")");
                        } else {
                            lore.add("&cYou have none of this crop");
                        }
                        
                        GuiButton btn = GuiButton.of(mat, "&a&l" + capitalize(mat.name()) + " &2[IN SEASON]", lore, e -> {
                            if (count > 0) {
                                sellManager.sellCrops(player, mat, count, false, total -> {
                                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
                                    lbManager.recordSell(uuid, player.getName(), seasonId, count);
                                    bountyManager.recordSell(uuid, mat, count);
                                    TextUtil.sendMessage(player, config.getString("messages.sold-bonus", "")
                                            .replace("{amount}", String.valueOf(count))
                                            .replace("{crop}", capitalize(mat.name()))
                                            .replace("{money}", "$" + TextUtil.formatNumber(total))
                                            .replace("{rate}", remaining > 0 ? String.valueOf(bonusMult) : "1.0"));
                                    open(player);
                                }, () -> {});
                            }
                        });
                        
                        gui.setItem(inSeasonSlots[inIndex++], btn);
                    }
                    
                    int offIndex = 0;
                    for (Material mat : offSeason) {
                        if (offIndex >= offSeasonSlots.length) break;
                        double basePrice = basePrices.getOrDefault(mat, 0.0);
                        int count = countItems(player, mat, false);
                        
                        List<String> lore = new ArrayList<>();
                        lore.add("&8&m────────────────────");
                        lore.add("&7Base price: &f$" + TextUtil.formatNumber(basePrice) + "/ea");
                        lore.add("&8(Off-season — normal price)");
                        lore.add("&8&m────────────────────");
                        if (count > 0) {
                            lore.add("&7You have: &f" + count);
                            lore.add("&e▶ Click to sell at standard price");
                        } else {
                            lore.add("&cYou have none of this crop");
                        }
                        
                        GuiButton btn = GuiButton.of(mat, "&7" + capitalize(mat.name()) + " &8[Base Rate]", lore, e -> {
                            if (count > 0) {
                                sellManager.sellCrops(player, mat, count, false, total -> {
                                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
                                    lbManager.recordSell(uuid, player.getName(), seasonId, count);
                                    bountyManager.recordSell(uuid, mat, count);
                                    TextUtil.sendMessage(player, "&a💰 Sold " + count + "x " + capitalize(mat.name()) + " for &e$" + TextUtil.formatNumber(total) + " &a(1.0×)!");
                                    open(player);
                                }, () -> {});
                            }
                        });
                        gui.setItem(offSeasonSlots[offIndex++], btn);
                    }
                    
                    // Daily Bounties
                    int[] bountySlots = {37, 38, 39};
                    for (int i = 0; i < Math.min(bounties.size(), bountySlots.length); i++) {
                        DailyBountyManager.DailyBounty b = bounties.get(i);
                        List<String> lore = new ArrayList<>();
                        lore.add("&8&m────────────────────");
                        lore.add("&7Sell: &f" + b.amountNeeded + "× " + capitalize(b.material.name()));
                        lore.add("&7Reward: &a$" + TextUtil.formatNumber(b.moneyReward));
                        lore.add("&8&m────────────────────");
                        lore.add("&7Progress: &f" + b.amountDone + "/" + b.amountNeeded);
                        lore.add(progressBar(b.amountDone, b.amountNeeded, 10));
                        lore.add("&8&m────────────────────");
                        
                        boolean canClaim = b.amountDone >= b.amountNeeded && !b.claimed;
                        if (b.claimed) lore.add("&2✓ Claimed");
                        else if (canClaim) lore.add("&a▶ Click to CLAIM REWARD!");
                        else lore.add("&7Farm & sell more to complete!");
                        
                        GuiButton btn = GuiButton.of(b.material, "&e📋 Daily Bounty #" + b.slot, lore, e -> {
                            if (canClaim) {
                                bountyManager.claimBounty(uuid, b.slot, () -> {
                                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);
                                    TextUtil.sendMessage(player, config.getString("messages.bounty-claimed", "&a✨ Daily bounty claimed! &e+{money}").replace("{money}", "$" + TextUtil.formatNumber(b.moneyReward)));
                                    open(player);
                                }, () -> open(player));
                            }
                        });
                        gui.setItem(bountySlots[i], btn);
                    }
                    
                    // Golden Crops direct sell button at slot 41
                    int totalGolden = 0;
                    double goldenPayout = 0;
                    for (Material m : allCrops) {
                        int gc = countItems(player, m, true);
                        if (gc > 0) {
                            totalGolden += gc;
                            goldenPayout += gc * basePrices.getOrDefault(m, 10.0) * goldenMult;
                        }
                    }
                    if (totalGolden > 0) {
                        final double finalPayout = goldenPayout;
                        final int finalCount = totalGolden;
                        ItemStack goldItem = new ItemStack(Material.RAW_GOLD_BLOCK);
                        ItemMeta goldMeta = goldItem.getItemMeta();
                        if (goldMeta != null) {
                            goldMeta.addEnchant(Enchantment.LUCK_OF_THE_SEA, 1, true);
                            goldMeta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
                            goldItem.setItemMeta(goldMeta);
                        }
                        gui.setItem(41, new GuiButton(goldItem, e -> {
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
                        gui.setItem(41, GuiButton.of(Material.GOLD_NUGGET, "&6&l✨ Golden Crops",
                            List.of(
                                "&8&m────────────────────",
                                "&7Break mature crops for rare",
                                "&6Golden Crop &7drops!",
                                "&8&m────────────────────",
                                "&7Golden crops sell for &e" + goldenMult + "×&7 value!",
                                "&8(You have 0 in your inventory)"
                            ), e -> {}
                        ));
                    }
                    
                    gui.setItem(42, GuiButton.filler(Material.GRAY_STAINED_GLASS_PANE));
                    
                    // Leaderboard button at slot 43
                    List<String> lbLore = new ArrayList<>();
                    lbLore.add("&8&m────────────────────");
                    for (int i = 0; i < 3; i++) {
                        if (i < topSellers.size()) {
                            HarvestLeaderboardManager.LeaderboardEntry entry = topSellers.get(i);
                            String color = i == 0 ? "&e" : "&7";
                            lbLore.add(color + "#" + (i+1) + " &f" + entry.playerName + ": " + (i == 0 ? "&a" : "&7") + TextUtil.formatNumber(entry.totalSold) + " items");
                        } else {
                            lbLore.add("&7#" + (i+1) + " &8None");
                        }
                    }
                    lbLore.add("&8&m────────────────────");
                    lbLore.add("&7Season ends in: &e" + timeLeft);
                    
                    gui.setItem(43, GuiButton.of(Material.GOLD_BLOCK, "&6&l🏆 Top Farmers", lbLore, e -> {}));
                    
                    gui.open(player);
                });
            });
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Farming Guide / How It Works Screen
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
                "&7Welcome to the official farmer's guide!",
                "&7Everything you need to maximize your profits.",
                "&8&m────────────────────"
            ), e -> {}
        ));

        // 1. Seasons
        gui.setItem(10, GuiButton.of(Material.WHEAT, "&a&l1. Crop Seasons",
            List.of(
                "&8&m────────────────────",
                "&7Every week, 4 random crops become",
                "&ain-season &7crops.",
                "&7In-season crops sell for &e" + bonusMult + "× standard price&7!",
                "&8&m────────────────────"
            ), e -> {}
        ));

        // 2. Quotas
        gui.setItem(12, GuiButton.of(Material.CHEST, "&e&l2. Quota System",
            List.of(
                "&8&m────────────────────",
                "&7To protect the server economy, each player",
                "&7can sell up to &f" + maxQuota + " crops &7at the",
                "&7bonus rate each season.",
                "&7Once full, extra crops sell at standard base price.",
                "&8&m────────────────────"
            ), e -> {}
        ));

        // 3. Golden Crops
        gui.setItem(14, GuiButton.of(Material.RAW_GOLD_BLOCK, "&6&l3. Golden Mutations",
            List.of(
                "&8&m────────────────────",
                "&7When manually harvesting fully-grown crops,",
                "&7there is a chance to discover a &6Golden Crop&7!",
                "&7Golden crops sell for an incredible &a" + goldenMult + "× price&7!",
                "&8&m────────────────────"
            ), e -> {}
        ));

        // 4. Bounties & Leaderboards
        gui.setItem(16, GuiButton.of(Material.EMERALD, "&b&l4. Bounties & Rewards",
            List.of(
                "&8&m────────────────────",
                "&7Daily bounties give extra cash when you",
                "&7meet specific crop harvesting goals.",
                "&7Top 3 sellers each season receive rare",
                "&bcrate keys &7and up to &a$75,000 cash&7!",
                "&8&m────────────────────"
            ), e -> {}
        ));

        // Back button
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
