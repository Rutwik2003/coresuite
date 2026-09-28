package net.rocky.coresuite.module.worldbosses.rewards;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.gui.GuiButton;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.worldbosses.boss.BossType;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionType;

import java.util.ArrayList;
import java.util.List;

public class HunterExchangeGUI {

    private final CoreSuitePlugin plugin;
    private final BossItemRegistry itemRegistry;
    private final FileConfiguration config;
    private final java.util.Set<java.util.UUID> pendingPurchases = new java.util.HashSet<>();
    private boolean available = true;
    public void invalidate() { available = false; }

    public HunterExchangeGUI(CoreSuitePlugin plugin, BossItemRegistry itemRegistry, FileConfiguration config) {
        this.plugin = plugin;
        this.itemRegistry = itemRegistry;
        this.config = config;
    }

    public void open(Player player) {
        if (!available) {
            player.closeInventory();
            TextUtil.sendMessage(player, "&eThis shop was reloaded. Reopen /boss shop for current prices.");
            return;
        }
        String title = config.getString("exchange.gui-title", "&8▶ &6&lGarrick's Hunter Exchange &8◀");
        Gui gui = new Gui(title, 6);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        // Header slot 4: Player's current Shards & Balance summary
        int ashenCount = itemRegistry.countShards(player, "ashen");
        int barkCount = itemRegistry.countShards(player, "royal_bark");
        int silkCount = itemRegistry.countShards(player, "phantom_silk");
        int totalShards = itemRegistry.countShards(player, "any");
        double balance = plugin.getEconomyManager().isAvailable() ? plugin.getEconomyManager().getBalance(player) : 0;

        List<String> statsLore = List.of(
            "&8&m────────────────────────",
            "&7Your Hunter Pouch:",
            "  &c• Ashen Shards:    &e" + ashenCount,
            "  &a• Royal Bark:      &e" + barkCount,
            "  &5• Phantom Silk:    &e" + silkCount,
            "  &bFrost Cores: " + itemRegistry.countShards(player, "frost_core"),
            "  &6• Total Shards:    &e" + totalShards,
            "",
            "&7Bank Balance: &a" + plugin.getEconomyManager().format(balance),
            "&8&m────────────────────────"
        );
        gui.setItem(4, GuiButton.of(Material.BUNDLE, "&e&lYour Boss Relics & Funds", statsLore, e -> {}));

        // ─────────────────────────────────────────────────────────────────────
        // Row 2: Primordial Summon Totems
        // ─────────────────────────────────────────────────────────────────────
        int vShards = config.getInt("exchange.trades.totem-valerius.required-shards", 15);
        double vCost = config.getDouble("exchange.trades.totem-valerius.required-currency", 500000.0);
        String vShardType = config.getString("exchange.trades.totem-valerius.required-specific-shard", "ashen");
        gui.setItem(11, createTradeButton("totem-valerius",
            Material.TOTEM_OF_UNDYING,
            "&c&lValerius Summon Totem",
            vShardType, vShards, vCost,
            List.of(
                "&7Awakens &cValerius, The Ashen Vanguard&7.",
                "&7Right-click on ground to trigger the ritual.",
                "&eRequired Territory: &7Badlands / Deserts."
            ),
            player,
            p -> {
                TextUtil.sendMessage(p, "&8[&6&lGarrick&8] &aHere is your Valerius Summon Totem! Awaken the Ashen Vanguard wisely.");
            }
        ));

        int aShards = config.getInt("exchange.trades.totem-aethelgard.required-shards", 20);
        double aCost = config.getDouble("exchange.trades.totem-aethelgard.required-currency", 750000.0);
        String aShardType = config.getString("exchange.trades.totem-aethelgard.required-specific-shard", "royal_bark");
        gui.setItem(13, createTradeButton("totem-aethelgard",
            Material.TOTEM_OF_UNDYING,
            "&a&lAethelgard Summon Totem",
            aShardType, aShards, aCost,
            List.of(
                "&7Awakens &aAethelgard, The Root-Bound King&7.",
                "&7Right-click on ground to summon the Titan.",
                "&eRequired Territory: &7Dark Forest / Old Growth Taiga."
            ),
            player,
            p -> {
                TextUtil.sendMessage(p, "&8[&6&lGarrick&8] &aHere is your Aethelgard Summon Totem! Prepare for the forest's fury.");
            }
        ));

        gui.setItem(38, createTradeButton("totem-borealis", Material.AMETHYST_SHARD,
            "&b&lBorealis Summon Totem",
            config.getString("exchange.trades.totem-borealis.required-specific-shard", "any"),
            config.getInt("exchange.trades.totem-borealis.required-shards", 30),
            config.getDouble("exchange.trades.totem-borealis.required-currency", 1200000),
            List.of("&7Summon the Frostbound Warden.", "&7Snowy plains / ice spikes / frozen peaks."),
            player, p -> TextUtil.sendMessage(p, "&bBorealis Summon Totem delivered.")));
        int xShards = config.getInt("exchange.trades.totem-xolthos.required-shards", 25);
        double xCost = config.getDouble("exchange.trades.totem-xolthos.required-currency", 1000000.0);
        String xShardType = config.getString("exchange.trades.totem-xolthos.required-specific-shard", "phantom_silk");
        gui.setItem(15, createTradeButton("totem-xolthos",
            Material.TOTEM_OF_UNDYING,
            "&5&lXol'thos Summon Totem",
            xShardType, xShards, xCost,
            List.of(
                "&7Awakens &5Xol'thos, The Weeping Widow&7.",
                "&7Right-click on ground to invoke the horror.",
                "&eRequired Territory: &7Deep Subterranean Caverns."
            ),
            player,
            p -> {
                TextUtil.sendMessage(p, "&8[&6&lGarrick&8] &aHere is your Xol'thos Summon Totem! Do not let the venom take you.");
            }
        ));

        // ─────────────────────────────────────────────────────────────────────
        // Row 3: Crate Keys
        // ─────────────────────────────────────────────────────────────────────
        int rareShards = config.getInt("exchange.trades.rare-key.required-shards", 30);
        double rareCost = config.getDouble("exchange.trades.rare-key.required-currency", 250000.0);
        String rareShard = config.getString("exchange.trades.rare-key.required-specific-shard", "any");
        gui.setItem(20, createTradeButton("rare-key",
            Material.TRIPWIRE_HOOK,
            "&b&lRare Crate Key",
            rareShard, rareShards, rareCost,
            List.of(
                "&7Exchange accumulated boss shards for",
                "&7a high-tier Rare Crate Key."
            ),
            player,
            p -> {
                TextUtil.sendMessage(p, "&8[&6&lGarrick&8] &aPleasure doing business! You received 1x Rare Crate Key.");
            }
        ));

        int enchShards = config.getInt("exchange.trades.enchanter-key.required-shards", 40);
        double enchCost = config.getDouble("exchange.trades.enchanter-key.required-currency", 500000.0);
        String enchShard = config.getString("exchange.trades.enchanter-key.required-specific-shard", "any");
        gui.setItem(22, createTradeButton("enchanter-key",
            Material.ENCHANTED_BOOK,
            "&d&lEnchanter Crate Key",
            enchShard, enchShards, enchCost,
            List.of(
                "&7A coveted enchanter crate key packed",
                "&7with mystic enchantment books."
            ),
            player,
            p -> {
                TextUtil.sendMessage(p, "&8[&6&lGarrick&8] &aHere is your Enchanter Crate Key!");
            }
        ));

        int eliteShards = config.getInt("exchange.trades.elite-key.required-shards", 50);
        double eliteCost = config.getDouble("exchange.trades.elite-key.required-currency", 1000000.0);
        String eliteShard = config.getString("exchange.trades.elite-key.required-specific-shard", "any");
        gui.setItem(24, createTradeButton("elite-key",
            Material.NETHER_STAR,
            "&6&lElite Crate Key",
            eliteShard, eliteShards, eliteCost,
            List.of(
                "&7The premier server crate key containing",
                "&7elite armor, ranks, and rare items."
            ),
            player,
            p -> {
                TextUtil.sendMessage(p, "&8[&6&lGarrick&8] &6An Elite Key for an elite hunter. Enjoy!");
            }
        ));

        // ─────────────────────────────────────────────────────────────────────
        // Row 4: Supplies & Mythic Weapons
        // ─────────────────────────────────────────────────────────────────────
        int alchShards = config.getInt("exchange.trades.alchemy-satchel.required-shards", 8);
        double alchCost = config.getDouble("exchange.trades.alchemy-satchel.required-currency", 75000.0);
        String alchShard = config.getString("exchange.trades.alchemy-satchel.required-specific-shard", "any");
        gui.setItem(29, createTradeButton("alchemy-satchel",
            Material.BREWING_STAND,
            "&e&lHunter's Alchemy Satchel",
            alchShard, alchShards, alchCost,
            List.of(
                "&7Essential combat potions to counter",
                "&7all 3 World Boss mechanics:",
                "  &b• 3x Splash Water Potions",
                "  &d• 2x Splash Night Vision",
                "  &c• 2x Fire Resistance (8m)",
                "  &a• 2x Splash Health II"
            ),
            player,
            p -> {
                TextUtil.sendMessage(p, "&8[&6&lGarrick&8] &aStocked up! Your combat satchel has arrived.");
            }
        ));

        int cleaverShards = config.getInt("exchange.trades.vanguard-cleaver.required-shards", 120);
        double cleaverCost = config.getDouble("exchange.trades.vanguard-cleaver.required-currency", 4000000.0);
        String cleaverShard = config.getString("exchange.trades.vanguard-cleaver.required-specific-shard", "ashen");
        gui.setItem(31, createTradeButton("vanguard-cleaver",
            Material.NETHERITE_AXE,
            "&c&lThe Vanguard's Cleaver",
            cleaverShard, cleaverShards, cleaverCost,
            List.of(
                "&7Mythic Weapon from Valerius.",
                "&7Trait: &eIgnited Edge &7(+20% bonus",
                "&7damage against burning foes)."
            ),
            player,
            p -> {
                Bukkit.broadcast(TextUtil.colorize("&6&l✦ WEAPON FORGED! &f" + p.getName() + " &7purchased &cThe Vanguard's Cleaver &7from Garrick!"));
            }
        ));

        int crownShards = config.getInt("exchange.trades.crown-of-forest.required-shards", 140);
        double crownCost = config.getDouble("exchange.trades.crown-of-forest.required-currency", 5500000.0);
        String crownShard = config.getString("exchange.trades.crown-of-forest.required-specific-shard", "royal_bark");
        gui.setItem(33, createTradeButton("crown-of-forest",
            Material.NETHERITE_HELMET,
            "&a&lCrown of the Forest",
            crownShard, crownShards, crownCost,
            List.of(
                "&7Mythic Relic from Aethelgard.",
                "&7Trait: &2Photosynthesis &7(Passive",
                "&7Regeneration in daylight or on grass)."
            ),
            player,
            p -> {
                Bukkit.broadcast(TextUtil.colorize("&6&l✦ RELIC CLAIMED! &f" + p.getName() + " &7purchased the &aCrown of the Forest &7from Garrick!"));
            }
        ));

        // ─────────────────────────────────────────────────────────────────────
        // Row 5: The Widow's Fang
        // ─────────────────────────────────────────────────────────────────────
        int fangShards = config.getInt("exchange.trades.widows-fang.required-shards", 160);
        double fangCost = config.getDouble("exchange.trades.widows-fang.required-currency", 7500000.0);
        String fangShard = config.getString("exchange.trades.widows-fang.required-specific-shard", "phantom_silk");
        gui.setItem(40, createTradeButton("widows-fang",
            Material.NETHERITE_SWORD,
            "&5&lThe Widow's Fang",
            fangShard, fangShards, fangCost,
            List.of(
                "&7Mythic Dagger from Xol'thos.",
                "&7Trait: &dEnvenom &7(Inflicts Poison II",
                "&7and grants Speed I)."
            ),
            player,
            p -> {
                Bukkit.broadcast(TextUtil.colorize("&6&l✦ WEAPON FORGED! &f" + p.getName() + " &7purchased &5The Widow's Fang &7from Garrick!"));
            }
        ));

        // ─────────────────────────────────────────────────────────────────────
        // Row 6: Navigation
        // ─────────────────────────────────────────────────────────────────────
        gui.setItem(42, createTradeButton("frostbound-striders", Material.NETHERITE_BOOTS,
            "&b&lFrostbound Striders",
            config.getString("exchange.trades.frostbound-striders.required-specific-shard", "frost_core"),
            config.getInt("exchange.trades.frostbound-striders.required-shards", 140),
            config.getDouble("exchange.trades.frostbound-striders.required-currency", 6000000),
            List.of("&7Mythic netherite boots from Borealis.", "&bGlacial Guard: &7No freezing damage;",
                "&725% less incoming fall damage.", "&7Protection IV, Feather Falling IV,",
                "&7Depth Strider III, Soul Speed III,",
                "&7Thorns III, Unbreaking IV, Mending."),
            player, p -> TextUtil.sendMessage(p, "&bFrostbound Striders forged and delivered!")));
        gui.setItem(45, GuiButton.of(
            Material.ARROW,
            "&e&l◀ Back to Codex",
            List.of(
                "&8&m────────────────────────",
                "&7Return directly to the",
                "&6Hunting Codex &7overview.",
                "",
                "&e▶ Click to go back.",
                "&8&m────────────────────────"
            ),
            e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                player.closeInventory();
                net.rocky.coresuite.module.worldbosses.WorldBossesModule module =
                    (net.rocky.coresuite.module.worldbosses.WorldBossesModule) plugin.getModuleManager().getModule("world-bosses");
                if (module != null && module.getBossCodexGUI() != null) {
                    module.getBossCodexGUI().open(player);
                }
            }
        ));

        gui.open(player);
    }

    private GuiButton createTradeButton(String tradeId, Material mat, String name, String shardType, int requiredShards, double requiredCost,
                                        List<String> desc, Player player, java.util.function.Consumer<Player> onSuccess) {
        int playerShards = itemRegistry.countShards(player, shardType);
        double balance = plugin.getEconomyManager().isAvailable() ? plugin.getEconomyManager().getBalance(player) : 0;

        boolean hasShards = requiredShards <= 0 || playerShards >= requiredShards;
        boolean hasCash = requiredCost <= 0 || balance >= requiredCost;
        boolean canAfford = hasShards && hasCash;

        List<String> lore = new ArrayList<>();
        lore.add("&8&m────────────────────────");
        lore.addAll(desc);
        lore.add("");
        lore.add("&7Price:");
        if (requiredShards > 0) {
            String shardLabel = switch (shardType) {
                case "ashen" -> "Ashen Shard(s)";
                case "royal_bark" -> "Royal Bark";
                case "phantom_silk" -> "Phantom Silk";
                case "frost_core" -> "Frost Core(s)";
                default -> "Any Boss Shard(s)";
            };
            lore.add("  " + (hasShards ? "&a✔ " : "&c✘ ") + "&7" + requiredShards + "x " + shardLabel + " &8(" + playerShards + "/" + requiredShards + ")");
        }
        if (requiredCost > 0) {
            lore.add("  " + (hasCash ? "&a✔ " : "&c✘ ") + "&7" + plugin.getEconomyManager().format(requiredCost) + " Cash");
        }
        if (requiredShards <= 0 && requiredCost <= 0) {
            lore.add("  &a✔ Free Trade");
        }
        lore.add("");
        if (canAfford) {
            lore.add("&a▶ Click to purchase trade!");
        } else {
            lore.add("&c▶ Insufficient materials or funds.");
        }
        lore.add("&8&m────────────────────────");

        return GuiButton.of(mat, name, lore, e -> {
            // Inventory transactions/opening a new menu must happen outside InventoryClickEvent.
            if (!pendingPurchases.add(player.getUniqueId())) return;
            var view = e.getView().getTopInventory();
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    if (!player.isOnline() || player.getOpenInventory().getTopInventory() != view) return;
                    String path = "exchange.trades." + tradeId + ".";
                    if (purchase(player, config.getString(path + "required-specific-shard", shardType),
                            config.getInt(path + "required-shards", requiredShards),
                            config.getDouble(path + "required-currency", requiredCost),
                            () -> prepareDelivery(player, tradeId))) {
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
                        onSuccess.accept(player);
                    }
                    open(player);
                } finally { pendingPurchases.remove(player.getUniqueId()); }
            });
        });
    }

    @FunctionalInterface interface DeliveryFactory {
        ExcellentCratesRewardBridge.Delivery prepare() throws ReflectiveOperationException;
    }

    boolean purchase(Player player, String shardType, int shards, double cost, DeliveryFactory factory) {
        if (!available) return false;
        if (!Double.isFinite(cost) || cost < 0 || shards < 0) {
            TextUtil.sendMessage(player, "&cThis trade has an invalid price. Please contact an admin.");
            return false;
        }
        var economy = plugin.getEconomyManager();
        if (itemRegistry.countShards(player, shardType) < shards
                || (cost > 0 && (!economy.isAvailable() || !(economy.getBalance(player) >= cost)))) {
            TextUtil.sendMessage(player, "&cYou do not have enough shards or cash for this trade.");
            return false;
        }
        ItemStack[] before = java.util.Arrays.stream(player.getInventory().getContents())
                .map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new);
        if (shards > 0 && !itemRegistry.takeShards(player, shardType, shards)) return false;
        ExcellentCratesRewardBridge.Delivery delivery;
        try {
            // Plan against inventory AFTER removing shards, so delivery cannot restore spent shards.
            delivery = factory.prepare();
            if (delivery == null || !delivery.canDeliver()) {
                player.getInventory().setContents(before);
                TextUtil.sendMessage(player, "&eMake room for the entire purchase first. You were not charged.");
                return false;
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            player.getInventory().setContents(before);
            plugin.getLogger().log(java.util.logging.Level.WARNING, "Boss shop reward preflight failed", error);
            TextUtil.sendMessage(player, "&cThis reward is unavailable or misconfigured. You were not charged; contact an admin.");
            return false;
        }
        try {
            if (cost > 0 && !economy.withdraw(player, cost)) {
                player.getInventory().setContents(before);
                TextUtil.sendMessage(player, "&cPayment was declined. Your shards were kept and nothing was purchased.");
                return false;
            }
        } catch (RuntimeException | LinkageError error) {
            player.getInventory().setContents(before);
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Boss shop payment uncertain: player=" + player.getUniqueId() + " cash=" + cost, error);
            TextUtil.sendMessage(player, "&cPayment could not be confirmed. Shards were kept; contact an admin before retrying.");
            return false;
        }
        try {
            if (delivery.deliver()) return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
            // The provider may already have delivered: do not refund/retry an uncertain mutation.
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Boss shop delivery uncertain: player=" + player.getUniqueId()
                    + " cash=" + cost + " shards=" + shards + " type=" + shardType, error);
            TextUtil.sendMessage(player, "&cReward delivery could not be confirmed. Contact an admin before buying again.");
            return false;
        }
        player.getInventory().setContents(before);
        boolean refunded = cost <= 0;
        try { if (cost > 0) refunded = economy.deposit(player, cost); }
        catch (RuntimeException | LinkageError error) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Boss shop refund uncertain for " + player.getUniqueId(), error);
        }
        if (!refunded) plugin.getLogger().severe("Boss shop refund requires review: player=" + player.getUniqueId() + " cash=" + cost);
        TextUtil.sendMessage(player, refunded ? "&ePurchase cancelled; cash and shards returned."
                : "&cPurchase failed. Shards returned; cash refund needs admin review.");
        return false;
    }

    private ExcellentCratesRewardBridge.Delivery prepareDelivery(Player player, String tradeId) throws ReflectiveOperationException {
        List<ItemStack> items = switch (tradeId) {
            case "totem-valerius" -> List.of(itemRegistry.createSummonTotem(BossType.VALERIUS));
            case "totem-aethelgard" -> List.of(itemRegistry.createSummonTotem(BossType.AETHELGARD));
            case "totem-xolthos" -> List.of(itemRegistry.createSummonTotem(BossType.XOLTHOS));
            case "totem-borealis" -> List.of(itemRegistry.createSummonTotem(BossType.BOREALIS));
            case "vanguard-cleaver", "crown-of-forest", "widows-fang", "frostbound-striders" -> List.of(itemRegistry.createSignatureWeapon(tradeId));
            case "alchemy-satchel" -> alchemySatchel();
            default -> null;
        };
        if (items != null) {
            ItemStack[] plan = BossRewardMailbox.fit(player.getInventory().getStorageContents(), items, player.getInventory().getMaxStackSize());
            if (plan == null) return null;
            return () -> { player.getInventory().setStorageContents(plan); return true; };
        }
        String defaultKey = switch (tradeId) {
            case "rare-key" -> "Rare";
            case "enchanter-key" -> "enchanter";
            case "elite-key" -> "Elite";
            default -> throw new IllegalArgumentException("Unknown boss shop trade: " + tradeId);
        };
        String command = config.getString("exchange.trades." + tradeId + ".command", "crates key give %player% " + defaultKey + " 1")
                .replace("%player%", player.getName()).replace("{player}", player.getName()).strip().replaceFirst("^/", "");
        var grant = ExcellentCratesRewardBridge.parse(command, player.getName());
        var provider = Bukkit.getPluginManager().getPlugin("ExcellentCrates");
        if (grant == null || provider == null || !provider.isEnabled())
            throw new IllegalArgumentException("A valid ExcellentCrates key-give command and enabled provider are required");
        return ExcellentCratesRewardBridge.prepare(provider, player, grant);
    }

    private List<ItemStack> alchemySatchel() {
        // 3x Splash Water Bottle
        ItemStack splashWater = new ItemStack(Material.SPLASH_POTION, 3);
        PotionMeta pmWater = (PotionMeta) splashWater.getItemMeta();
        if (pmWater != null) {
            pmWater.setBasePotionType(PotionType.WATER);
            pmWater.displayName(TextUtil.colorize("&bSplash Potion of Water"));
            splashWater.setItemMeta(pmWater);
        }


        // 2x Splash Night Vision (Long)
        ItemStack splashNv = new ItemStack(Material.SPLASH_POTION, 2);
        PotionMeta pmNv = (PotionMeta) splashNv.getItemMeta();
        if (pmNv != null) {
            pmNv.setBasePotionType(PotionType.LONG_NIGHT_VISION);
            pmNv.displayName(TextUtil.colorize("&dSplash Potion of Night Vision"));
            splashNv.setItemMeta(pmNv);
        }


        // 2x Fire Resistance (Long)
        ItemStack fireRes = new ItemStack(Material.POTION, 2);
        PotionMeta pmFire = (PotionMeta) fireRes.getItemMeta();
        if (pmFire != null) {
            pmFire.setBasePotionType(PotionType.LONG_FIRE_RESISTANCE);
            fireRes.setItemMeta(pmFire);
        }


        // 2x Splash Health II
        ItemStack heal = new ItemStack(Material.SPLASH_POTION, 2);
        PotionMeta pmHeal = (PotionMeta) heal.getItemMeta();
        if (pmHeal != null) {
            pmHeal.setBasePotionType(PotionType.STRONG_HEALING);
            heal.setItemMeta(pmHeal);
        }

        return List.of(splashWater, splashNv, fireRes, heal);
    }
}
