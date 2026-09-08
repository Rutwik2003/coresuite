package net.rocky.coresuite.module.ordersboard;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.gui.GuiButton;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Handles the multi-step "Post Order" flow.
 *
 * Flow A — Item Order:
 *   Type Select → Category Selection → Item Browser / Search → Set Amount → Set Price → Duration → Confirm
 *
 * Flow B — Service Order:
 *   Type Select → Description (chat) → Set Price → Duration → Confirm
 */
public class PostOrderGUI implements Listener {

    // ── Chat context keys ───────────────────────────────────────────────────
    private static final String CTX_SEARCH       = "search";
    private static final String CTX_AMOUNT       = "amount";
    private static final String CTX_PRICE        = "price";
    private static final String CTX_SERVICE_DESC = "service_desc";

    // ── Item Categories (Aligned with EconomyShopGUI) ───────────────────────
    public enum ItemCategory {
        BUILDING("Blocks & Building", Material.GRASS_BLOCK, "&6&lBlocks &8& &6&lBuilding",
            List.of("&7Stones, woods, planks, terracotta,", "&7concrete, glass, bricks, & slabs.")),

        RESOURCES("Resources & Ores", Material.IRON_INGOT, "&b&lResources &8& &b&lOres",
            List.of("&7Raw ores, ingots, diamonds, coal,", "&7netherite, lapis, nuggets, & gems.")),

        COMBAT("Tools & Combat", Material.DIAMOND_SWORD, "&c&lTools &8& &c&lCombat",
            List.of("&7Weapons, spears, armor, tools, bows,", "&7shields, elytra, & redstone gear.")),

        FARMING("Farming & Food", Material.WHEAT, "&a&lFarming &8& &6&lFood",
            List.of("&7Crops, foods, meats, seeds, stews,", "&7honey, cakes, & harvest produce.")),

        MOBS("Mob Drops & Spawners", Material.GUNPOWDER, "&2&lMob Drops &8& &4&lSpawners",
            List.of("&7Bones, gunpowder, blaze rods, pearls,", "&7slime balls, mob drops, & spawners.")),

        DECORATION("Decoration & Dyes", Material.TUBE_CORAL, "&9&lDecoration &8& &d&lDyes",
            List.of("&7Corals, flowers, lanterns, torches,", "&7candles, paintings, frames, & dyes.")),

        MAGIC("Enchants & Potions", Material.ENCHANTING_TABLE, "&5&lEnchants &8& &d&lPotions",
            List.of("&7Potions, enchanted books, templates,", "&7brewing stands, anvils, & cauldrons.")),

        STORAGE("Storage & Boxes", Material.CHEST, "&6&lStorage &8& &e&lBoxes",
            List.of("&7Chests, barrels, shulker boxes,", "&7ender chests, and bundles.")),

        MISC("Music & Misc", Material.MUSIC_DISC_CAT, "&d&lMusic &8& &e&lMisc",
            List.of("&7Music discs, saddles, leads, buckets,", "&7spyglasses, compasses, & totems.")),

        ALL("Browse All Items", Material.ENDER_CHEST, "&f&lBrowse All Items",
            List.of("&7Browse every survival obtainable item", "&7in alphabetical order."));

        private final String displayName;
        private final Material icon;
        private final String title;
        private final List<String> lore;

        ItemCategory(String displayName, Material icon, String title, List<String> lore) {
            this.displayName = displayName;
            this.icon = icon;
            this.title = title;
            this.lore = lore;
        }

        public String getDisplayName() { return displayName; }
        public Material getIcon() { return icon; }
        public String getTitle() { return title; }
        public List<String> getLore() { return lore; }
    }

    // ── Admin-only / unobtainable materials blocked from the order browser ──
    private static final Set<String> ADMIN_ONLY = Set.of(
        "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK",
        "COMMAND_BLOCK_MINECART", "BARRIER", "BEDROCK", "DEBUG_STICK",
        "KNOWLEDGE_BOOK", "JIGSAW", "STRUCTURE_BLOCK", "STRUCTURE_VOID",
        "LIGHT", "BUNDLE", "PETRIFIED_OAK_SLAB",
        "INFESTED_STONE", "INFESTED_COBBLESTONE", "INFESTED_STONE_BRICKS",
        "INFESTED_CRACKED_STONE_BRICKS", "INFESTED_CHISELED_STONE_BRICKS",
        "INFESTED_DEEPSLATE", "REINFORCED_DEEPSLATE", "BUDDING_AMETHYST",
        "FROGSPAWN", "EGG", "TURTLE_EGG", "SNIFFER_EGG", "DRAGON_EGG",
        "SUSPICIOUS_SAND", "SUSPICIOUS_GRAVEL", "TRIAL_SPAWNER", "VAULT",
        "END_PORTAL_FRAME", "END_PORTAL", "NETHER_PORTAL"
    );

    private static List<Material> cachedAllMats = null;

    private static List<Material> buildAllMats() {
        if (cachedAllMats == null) {
            cachedAllMats = Arrays.stream(Material.values())
                .filter(m -> m.isItem() && !m.isAir() && !m.name().startsWith("LEGACY_"))
                .filter(m -> !ADMIN_ONLY.contains(m.name()))
                .filter(m -> !m.name().contains("SPAWN_EGG"))
                .filter(m -> !m.name().contains("HEAD") && !m.name().contains("SKULL"))
                .sorted(Comparator.comparing(Material::name))
                .collect(Collectors.toList());
        }
        return cachedAllMats;
    }

    public static int getItemCountForCategory(ItemCategory category) {
        List<Material> mats = buildAllMats();
        if (category == ItemCategory.ALL) return mats.size();
        return (int) mats.stream().filter(m -> getCategory(m) == category).count();
    }

    public static ItemCategory getCategory(Material m) {
        String n = m.name();

        // 1. Tools & Combat (including spears, swords, armor, & redstone gear)
        if (isCombatOrTool(n)) {
            return ItemCategory.COMBAT;
        }

        // 2. Storage & Boxes
        if (isStorage(n)) {
            return ItemCategory.STORAGE;
        }

        // 3. Enchants & Potions (Magic)
        if (isMagicOrPotions(n)) {
            return ItemCategory.MAGIC;
        }

        // 4. Music & Misc
        if (isMusicOrMisc(n)) {
            return ItemCategory.MISC;
        }

        // 5. Resources & Ores
        if (isResourcesOrOres(n)) {
            return ItemCategory.RESOURCES;
        }

        // 6. Mob Drops & Spawners
        if (isMobDrops(n)) {
            return ItemCategory.MOBS;
        }

        // 7. Farming & Food
        if (isFarmingOrFood(m, n)) {
            return ItemCategory.FARMING;
        }

        // 8. Decoration & Dyes
        if (isDecorationOrDyes(n)) {
            return ItemCategory.DECORATION;
        }

        // 9. Default to Blocks & Building
        return ItemCategory.BUILDING;
    }

    private static boolean isCombatOrTool(String n) {
        if (n.endsWith("_SWORD") || n.endsWith("_PICKAXE") || n.endsWith("_AXE")
            || n.endsWith("_SHOVEL") || n.endsWith("_HOE")
            || n.contains("SPEAR") || n.contains("DAGGER") || n.contains("KATANA")
            || n.contains("HAMMER") || n.contains("SCYTHE") || n.contains("HALBERD")
            || n.contains("RAPIER") || n.contains("LANCE")
            || n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS")
            || n.contains("SHIELD") || n.contains("BOW") || n.contains("CROSSBOW") || n.contains("ARROW")
            || n.contains("TRIDENT") || n.contains("MACE") || n.contains("ELYTRA")
            || n.contains("SHEARS") || n.contains("FISHING_ROD") || n.contains("FLINT_AND_STEEL")
            || n.contains("HORSE_ARMOR") || n.contains("WOLF_ARMOR")) {
            return true;
        }
        // Redstone & mechanics (aligned with EconomyShopGUI Combat/Mechanics section)
        if (n.equals("REDSTONE") || n.equals("REDSTONE_TORCH") || n.equals("REDSTONE_BLOCK")
            || n.contains("REPEATER") || n.contains("COMPARATOR") || n.equals("TARGET")
            || n.equals("LEVER") || n.equals("LIGHTNING_ROD") || n.equals("DAYLIGHT_DETECTOR")
            || n.equals("TRIPWIRE_HOOK") || n.endsWith("_BUTTON") || n.endsWith("_PRESSURE_PLATE")) {
            return true;
        }
        if (n.contains("PISTON") || n.contains("OBSERVER") || n.contains("DISPENSER")
            || n.contains("DROPPER") || n.contains("HOPPER") || n.contains("CRAFTER")) {
            return true;
        }
        if (n.contains("RAIL") || n.contains("MINECART")) {
            return true;
        }
        if (n.equals("TNT") || n.equals("NOTE_BLOCK") || n.equals("JUKEBOX")) {
            return true;
        }
        if (n.contains("SCULK_SENSOR") || n.contains("SCULK_SHRIEKER")) {
            return true;
        }
        if (n.equals("REDSTONE_LAMP") || n.contains("COPPER_BULB")) {
            return true;
        }
        return false;
    }

    private static boolean isStorage(String n) {
        return n.endsWith("SHULKER_BOX") || n.equals("CHEST") || n.equals("TRAPPED_CHEST")
            || n.equals("BARREL") || n.equals("ENDER_CHEST") || n.equals("BUNDLE")
            || n.endsWith("_BUNDLE");
    }

    private static boolean isMagicOrPotions(String n) {
        return n.contains("POTION")
            || n.contains("ENCHANTED_BOOK") || n.equals("BOOK") || n.equals("WRITABLE_BOOK") || n.equals("WRITTEN_BOOK")
            || n.contains("SMITHING_TEMPLATE")
            || n.contains("BANNER_PATTERN")
            || n.contains("POTTERY_SHERD")
            || n.equals("ENCHANTING_TABLE") || n.contains("ANVIL") || n.equals("GRINDSTONE")
            || n.equals("BREWING_STAND") || n.equals("CAULDRON") || n.equals("EXPERIENCE_BOTTLE")
            || n.equals("GLASS_BOTTLE") || n.equals("DRAGON_BREATH");
    }

    private static boolean isMusicOrMisc(String n) {
        if (n.startsWith("MUSIC_DISC_") || n.equals("DISC_FRAGMENT_5")) return true;
        if (n.equals("SADDLE") || n.equals("LEAD") || n.equals("NAME_TAG")
            || n.equals("SPYGLASS") || n.equals("BRUSH") || n.equals("TOTEM_OF_UNDYING")
            || n.equals("COMPASS") || n.equals("CLOCK") || n.equals("RECOVERY_COMPASS")
            || n.equals("WIND_CHARGE")) {
            return true;
        }
        if (n.endsWith("_BUCKET") || n.equals("BUCKET")) return true;
        if (n.equals("BEACON") || n.equals("CONDUIT") || n.equals("LODESTONE") || n.equals("RESPAWN_ANCHOR")
            || n.equals("FIREWORK_ROCKET") || n.equals("FIREWORK_STAR")) {
            return true;
        }
        return false;
    }

    private static boolean isResourcesOrOres(String n) {
        if (n.endsWith("_ORE")) return true;
        if (n.startsWith("RAW_")) return true;
        if (n.endsWith("_INGOT") || n.endsWith("_NUGGET")) return true;
        if (n.equals("DIAMOND") || n.equals("EMERALD") || n.equals("LAPIS_LAZULI")
            || n.equals("COAL") || n.equals("CHARCOAL") || n.equals("NETHERITE_SCRAP")
            || n.equals("ANCIENT_DEBRIS") || n.equals("AMETHYST_SHARD") || n.equals("QUARTZ")) {
            return true;
        }
        if (n.equals("COAL_BLOCK") || n.equals("IRON_BLOCK") || n.equals("GOLD_BLOCK")
            || n.equals("DIAMOND_BLOCK") || n.equals("EMERALD_BLOCK") || n.equals("LAPIS_BLOCK")
            || n.equals("NETHERITE_BLOCK") || n.equals("COPPER_BLOCK")
            || n.startsWith("RESIN_")) {
            return true;
        }
        return false;
    }

    private static boolean isMobDrops(String n) {
        if (n.equals("BONE") || n.equals("STRING") || n.equals("GUNPOWDER")
            || n.equals("ROTTEN_FLESH") || n.equals("SPIDER_EYE") || n.equals("FERMENTED_SPIDER_EYE")
            || n.equals("BLAZE_ROD") || n.equals("BLAZE_POWDER") || n.equals("GHAST_TEAR")
            || n.equals("MAGMA_CREAM") || n.equals("SLIME_BALL")
            || n.equals("ENDER_PEARL") || n.equals("EYE_OF_ENDER") || n.equals("SHULKER_SHELL")
            || n.equals("FEATHER") || n.equals("LEATHER") || n.equals("RABBIT_FOOT") || n.equals("RABBIT_HIDE")
            || n.equals("INK_SAC") || n.equals("GLOW_INK_SAC")
            || n.equals("PRISMARINE_SHARD") || n.equals("PRISMARINE_CRYSTALS")
            || n.equals("NAUTILUS_SHELL") || n.equals("HEART_OF_THE_SEA")
            || n.equals("PHANTOM_MEMBRANE") || n.equals("NETHER_STAR")
            || n.equals("BREEZE_ROD") || n.equals("HEAVY_CORE")
            || n.equals("OMINOUS_BOTTLE") || n.equals("SPAWNER")) {
            return true;
        }
        if (n.contains("SCUTE") || n.contains("TRIAL_KEY")) {
            return true;
        }
        return false;
    }

    private static boolean isFarmingOrFood(Material m, String n) {
        if (m.isEdible()) return true;
        if (n.contains("WHEAT") || n.contains("CARROT") || n.contains("POTATO")
            || n.contains("BEETROOT") || n.contains("MELON") || n.contains("PUMPKIN")
            || n.contains("SUGAR_CANE") || n.contains("BAMBOO") || n.contains("CACTUS")
            || n.contains("COCOA_BEANS") || n.contains("NETHER_WART") || n.contains("CHORUS")
            || n.contains("KELP") || n.contains("BERRIES") || n.contains("SEEDS")
            || n.contains("TORCHFLOWER") || n.contains("PITCHER")) {
            return true;
        }
        if (n.equals("BREAD") || n.equals("COOKIE") || n.equals("CAKE")
            || n.equals("HONEY_BOTTLE") || n.equals("MILK_BUCKET") || n.equals("EGG")
            || n.equals("SUGAR") || n.equals("BOWL") || n.equals("APPLE")
            || n.equals("GOLDEN_APPLE") || n.equals("ENCHANTED_GOLDEN_APPLE")
            || n.equals("BEEF") || n.equals("COOKED_BEEF") || n.equals("PORKCHOP") || n.equals("COOKED_PORKCHOP")
            || n.equals("MUTTON") || n.equals("COOKED_MUTTON") || n.equals("CHICKEN") || n.equals("COOKED_CHICKEN")
            || n.equals("RABBIT") || n.equals("COOKED_RABBIT") || n.equals("COD") || n.equals("COOKED_COD")
            || n.equals("SALMON") || n.equals("COOKED_SALMON") || n.equals("PUFFERFISH") || n.equals("TROPICAL_FISH")
            || n.contains("STEW") || n.contains("SOUP") || n.contains("MUSHROOM")) {
            return true;
        }
        if (n.equals("HAY_BLOCK") || n.equals("BONE_MEAL") || n.equals("HONEY_BLOCK")
            || n.equals("HONEYCOMB") || n.equals("HONEYCOMB_BLOCK")
            || n.equals("BEE_NEST") || n.equals("BEEHIVE")) {
            return true;
        }
        return false;
    }

    private static boolean isDecorationOrDyes(String n) {
        if (n.endsWith("_DYE")) return true;
        if (n.contains("FLOWER") || n.contains("TULIP") || n.contains("ORCHID")
            || n.contains("ROSE") || n.contains("DANDELION") || n.contains("POPPY")
            || n.contains("LILY") || n.contains("SUNFLOWER") || n.contains("LILAC")
            || n.contains("PEONY") || n.contains("ALLIUM") || n.contains("AZURE_BLUET")
            || n.contains("CORNFLOWER") || n.contains("SAPLING") || n.contains("PROPAGULE")
            || n.contains("LEAVES") || n.contains("MOSS") || n.contains("AZALEA")
            || n.contains("SPORE_BLOSSOM") || n.contains("VINE") || n.contains("LICHEN")
            || n.contains("ROOTS") || n.contains("DRIPLEAF")) {
            return true;
        }
        if (n.contains("LANTERN") || n.contains("TORCH") || n.contains("CANDLE")
            || n.contains("FROGLIGHT") || n.contains("CAMPFIRE")
            || n.equals("SEA_LANTERN") || n.equals("GLOWSTONE") || n.equals("SHROOMLIGHT") || n.equals("END_ROD")) {
            return true;
        }
        if (n.equals("FLOWER_POT") || n.equals("PAINTING") || n.contains("ITEM_FRAME")
            || n.equals("ARMOR_STAND") || n.equals("BELL") || n.equals("CHAIN")) {
            return true;
        }
        if (n.contains("CORAL") || n.equals("SEA_PICKLE") || n.contains("SPONGE") || n.contains("AMETHYST_CLUSTER") || n.contains("AMETHYST_BUD")) {
            return true;
        }
        if (n.endsWith("_BANNER") || n.endsWith("_CARPET")) {
            return true;
        }
        return false;
    }

    // ── Preset amounts and prices ───────────────────────────────────────────
    private static final int[]    AMOUNT_PRESETS = {16, 32, 64, 128, 256, 512, 1000};
    private static final double[] PRICE_PRESETS  = {5_000, 10_000, 25_000, 50_000, 100_000, 250_000, 500_000};

    // ── Content slot layout (6-row GUI, slots 10-16, 19-25, 28-34, 37-43) ──
    private static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    }; // 28 slots per page

    // ── State maps (keyed by player UUID) ──────────────────────────────────
    private final Map<UUID, Material>        selectedMaterial  = new ConcurrentHashMap<>();
    private final Map<UUID, ItemCategory>    selectedCategory  = new ConcurrentHashMap<>();
    private final Map<UUID, Integer>         categoryPage      = new ConcurrentHashMap<>();
    private final Map<UUID, Integer>         selectedAmount    = new ConcurrentHashMap<>();
    private final Map<UUID, String>          selectedDesc      = new ConcurrentHashMap<>();
    private final Map<UUID, String>          searchQuery       = new ConcurrentHashMap<>();
    private final Map<UUID, String>          awaitingInput     = new ConcurrentHashMap<>();
    private final Map<UUID, Order.OrderType> orderTypeState    = new ConcurrentHashMap<>();
    private final Map<UUID, Integer>         selectedExpiry    = new ConcurrentHashMap<>();

    private final CoreSuitePlugin plugin;
    private final OrderManager    orderManager;
    private final FileConfiguration config;
    private BoardGUI boardGUI;

    public PostOrderGUI(CoreSuitePlugin plugin, OrderManager orderManager, FileConfiguration config) {
        this.plugin       = plugin;
        this.orderManager = orderManager;
        this.config       = config;
    }

    public void setBoardGUI(BoardGUI boardGUI) { this.boardGUI = boardGUI; }

    // ══════════════════════════════════════════════════════════════════════════
    //  Step 0 — Choose Order Type
    // ══════════════════════════════════════════════════════════════════════════

    public void openTypeSelect(Player player) {
        Gui gui = new Gui("&8▶ &a&lCreate Order &8◀", 3);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        // ── Item Order button (slot 11) ─────────────────────────────────────
        gui.setItem(11, GuiButton.of(Material.CHEST, "&a&lItem Order",
            List.of(
                "&8&m────────────────────",
                "&7Request a specific &fMinecraft item&7.",
                "&7Other players bring you the",
                "&7items and earn money instantly.",
                "&8&m────────────────────",
                "&7Browse by &ecategories &7or &esearch&7.",
                "&8  ▸ &fBuilding, Ores, Food, Combat...",
                "&8&m────────────────────",
                "&e▶ Click to browse item categories"
            ),
            e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                orderTypeState.put(player.getUniqueId(), Order.OrderType.ITEM);
                openCategories(player);
            }
        ));

        // ── Service Order button (slot 15) ──────────────────────────────────
        gui.setItem(15, GuiButton.of(Material.WRITABLE_BOOK, "&b&lService Order",
            List.of(
                "&8&m────────────────────",
                "&7Request a &fservice or task&7 from",
                "&7another player on the server.",
                "&7You pay when they complete it.",
                "&8&m────────────────────",
                "&7Examples:",
                "&8  ▸ &fBuild me a wheat farm",
                "&8  ▸ &fHelp me decorate my house",
                "&8  ▸ &fRaid event escort",
                "&8&m────────────────────",
                "&e▶ Click to post a Service Order"
            ),
            e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                orderTypeState.put(player.getUniqueId(), Order.OrderType.SERVICE);
                promptServiceDescription(player);
            }
        ));

        // Info item (slot 13)
        gui.setItem(13, GuiButton.of(Material.GOLD_INGOT, "&6&lHow Orders Work",
            List.of(
                "&8&m────────────────────",
                "&7When you post an order:",
                "&8  ▸ &fFull payout is &cHeld in escrow",
                "&8  ▸ &fA &e" + config.getInt("posting-fee-percent", 5) + "% posting fee &fis charged",
                "&8  ▸ &fFulfillers earn money &ainstantly",
                "&8  ▸ &fOrders expire in &e1–7 days &f(customizable)",
                "&8&m────────────────────",
                "&7Max active orders: &f" + config.getInt("max-orders-per-player", 3)
            ),
            e -> {}
        ));

        // Back button at slot 18 (bottom-left of 3-row GUI)
        gui.setItem(18, GuiButton.of(Material.ARROW, "&7◀ Back to Board",
            List.of("&7Return to the main orders board"),
            e -> {
                if (boardGUI != null) boardGUI.open(player, 1, BoardGUI.SortMode.HIGHEST_PAYOUT);
            }
        ));

        // Close button at slot 22 (bottom-center)
        gui.setItem(22, GuiButton.of(Material.BARRIER, "&c&lClose",
            List.of("&7Exit the order menu"),
            e -> player.closeInventory()
        ));

        gui.open(player);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ITEM FLOW — Step 1: Category Selection
    // ══════════════════════════════════════════════════════════════════════════

    public void openCategories(Player player) {
        Gui gui = new Gui("&8▶ &6&lItem Categories &8◀", 5);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        int totalMats = buildAllMats().size();

        // Header info item at slot 4
        gui.setItem(4, GuiButton.of(Material.PAPER, "&6&lSelect a Category",
            List.of(
                "&8&m────────────────────",
                "&7Choose an item category below to",
                "&7browse survival obtainable items,",
                "&7or use search to find an item.",
                "&8&m────────────────────",
                "&7Total Obtainable Items: &a" + totalMats
            ),
            e -> {}
        ));

        // Row 2: 4 Primary Categories (slots 10, 12, 14, 16)
        ItemCategory[] row1Cats = {
            ItemCategory.BUILDING,
            ItemCategory.RESOURCES,
            ItemCategory.COMBAT,
            ItemCategory.FARMING
        };
        int[] row1Slots = {10, 12, 14, 16};

        for (int i = 0; i < row1Cats.length; i++) {
            ItemCategory cat = row1Cats[i];
            int count = getItemCountForCategory(cat);
            List<String> lore = new ArrayList<>();
            lore.add("&8&m────────────────────");
            lore.add("&7Available: &a" + count + " items");
            lore.add("&8&m────────────────────");
            lore.addAll(cat.getLore());
            lore.add("");
            lore.add("&e▶ Click to browse");

            gui.setItem(row1Slots[i], GuiButton.of(cat.getIcon(), cat.getTitle(), lore, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                selectedCategory.put(player.getUniqueId(), cat);
                openCategoryBrowser(player, cat, 0, "");
            }));
        }

        // Row 3: 4 Secondary Categories (slots 19, 21, 23, 25)
        ItemCategory[] row2Cats = {
            ItemCategory.MOBS,
            ItemCategory.DECORATION,
            ItemCategory.MAGIC,
            ItemCategory.STORAGE
        };
        int[] row2Slots = {19, 21, 23, 25};

        for (int i = 0; i < row2Cats.length; i++) {
            ItemCategory cat = row2Cats[i];
            int count = getItemCountForCategory(cat);
            List<String> lore = new ArrayList<>();
            lore.add("&8&m────────────────────");
            lore.add("&7Available: &a" + count + " items");
            lore.add("&8&m────────────────────");
            lore.addAll(cat.getLore());
            lore.add("");
            lore.add("&e▶ Click to browse");

            gui.setItem(row2Slots[i], GuiButton.of(cat.getIcon(), cat.getTitle(), lore, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                selectedCategory.put(player.getUniqueId(), cat);
                openCategoryBrowser(player, cat, 0, "");
            }));
        }

        // Row 4: Misc (slot 29), Browse All Items (slot 31), Search by Name (slot 33)
        ItemCategory miscCat = ItemCategory.MISC;
        int miscCount = getItemCountForCategory(miscCat);
        List<String> miscLore = new ArrayList<>();
        miscLore.add("&8&m────────────────────");
        miscLore.add("&7Available: &a" + miscCount + " items");
        miscLore.add("&8&m────────────────────");
        miscLore.addAll(miscCat.getLore());
        miscLore.add("");
        miscLore.add("&e▶ Click to browse");

        gui.setItem(29, GuiButton.of(miscCat.getIcon(), miscCat.getTitle(), miscLore, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            selectedCategory.put(player.getUniqueId(), miscCat);
            openCategoryBrowser(player, miscCat, 0, "");
        }));

        gui.setItem(31, GuiButton.of(Material.ENDER_CHEST, "&f&lBrowse All Items",
            List.of(
                "&8&m────────────────────",
                "&7Available: &a" + totalMats + " items",
                "&8&m────────────────────",
                "&7Browse every obtainable survival item",
                "&7in alphabetical order.",
                "",
                "&e▶ Click to browse all items"
            ),
            e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                selectedCategory.put(player.getUniqueId(), ItemCategory.ALL);
                openCategoryBrowser(player, ItemCategory.ALL, 0, "");
            }
        ));

        gui.setItem(33, GuiButton.of(Material.SPYGLASS, "&e&l🔍 Search by Name",
            List.of(
                "&8&m────────────────────",
                "&7Know what you are looking for?",
                "&7Type the item name directly in chat.",
                "&8&m────────────────────",
                "&e▶ Click to search"
            ),
            e -> {
                player.closeInventory();
                awaitingInput.put(player.getUniqueId(), CTX_SEARCH);
                TextUtil.sendMessage(player, "&8[&6Orders&8] &eType an item name to search for &8(or &ccancel&8):");
                TextUtil.sendMessage(player, "&8  &7Tip: type &firon&7, &fdiamond&7, &fspawner&7, &flog&7, etc.");
            }
        ));

        // Row 5: Navigation (slot 36 = Back, slot 40 = Close)
        gui.setItem(36, GuiButton.of(Material.ARROW, "&7◀ Back",
            List.of("&7Return to order type selection"),
            e -> openTypeSelect(player)
        ));

        gui.setItem(40, GuiButton.of(Material.BARRIER, "&c&lClose",
            List.of("&7Close the menu"),
            e -> player.closeInventory()
        ));

        gui.open(player);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ITEM FLOW — Step 1.5: Item Browser for Category
    // ══════════════════════════════════════════════════════════════════════════

    public void openCategoryBrowser(Player player, ItemCategory category, int page, String query) {
        List<Material> allMats = buildAllMats();

        // 1. Filter by category
        List<Material> inCategory;
        if (category == ItemCategory.ALL) {
            inCategory = allMats;
        } else {
            inCategory = allMats.stream()
                .filter(m -> getCategory(m) == category)
                .collect(Collectors.toList());
        }

        // 2. Filter by query if present
        List<Material> filtered;
        if (query == null || query.isBlank()) {
            filtered = inCategory;
        } else {
            String q = query.toLowerCase().replace(" ", "_");
            filtered = inCategory.stream()
                .filter(m -> m.name().toLowerCase().contains(q)
                          || OrderManager.formatMaterialName(m).toLowerCase().contains(query.toLowerCase()))
                .collect(Collectors.toList());
        }

        int totalPages = Math.max(1, (int) Math.ceil((double) filtered.size() / CONTENT_SLOTS.length));
        final int currentPage = Math.max(0, Math.min(page, totalPages - 1));
        categoryPage.put(player.getUniqueId(), currentPage);
        searchQuery.put(player.getUniqueId(), query == null ? "" : query);

        String title = (query != null && !query.isBlank())
            ? "&8▶ &6&lSearch: " + (query.length() > 14 ? query.substring(0, 11) + "..." : query) + " &8◀"
            : "&8▶ &6&l" + category.getDisplayName() + " &8◀";

        Gui gui = new Gui(title, 6);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        // Slot 0: Quick Back to Categories button in top-left
        gui.setItem(0, GuiButton.of(Material.ARROW, "&7◀ Categories",
            List.of("&7Return to categories menu"),
            e -> openCategories(player)
        ));

        // Slot 4: Search button / info
        String displayQuery = (query == null || query.isBlank()) ? "None" : "\"" + query + "\"";
        gui.setItem(4, GuiButton.of(Material.SPYGLASS, "&e🔍 Filter: &f" + displayQuery,
            List.of(
                "&8&m────────────────────",
                "&7Category: &f" + category.getDisplayName(),
                "&7Matches:  &a" + filtered.size() + " items",
                "&8&m────────────────────",
                "&e▶ Click to type a new search",
                "&7(Type &ccancel &7in chat to keep current)"
            ),
            e -> {
                player.closeInventory();
                awaitingInput.put(player.getUniqueId(), CTX_SEARCH);
                TextUtil.sendMessage(player, "&8[&6Orders&8] &eType an item name to search for &8(or &ccancel&8):");
            }
        ));

        // Slot 8: Category badge
        gui.setItem(8, GuiButton.of(category.getIcon(), category.getTitle(),
            List.of("&7Showing &f" + filtered.size() + " &7items", "&8Page " + (currentPage + 1) + "/" + totalPages),
            e -> {}
        ));

        // Fill 28 content slots
        int start = currentPage * CONTENT_SLOTS.length;
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            int idx = start + i;
            if (idx >= filtered.size()) break;
            Material mat = filtered.get(idx);
            gui.setItem(CONTENT_SLOTS[i], GuiButton.of(mat,
                "&f" + OrderManager.formatMaterialName(mat),
                List.of(
                    "&8&m────────────────────",
                    "&7Category: &e" + getCategory(mat).getDisplayName(),
                    "&8&m────────────────────",
                    "&e▶ Click to request this item"
                ),
                e -> {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                    selectedMaterial.put(player.getUniqueId(), mat);
                    selectedCategory.put(player.getUniqueId(), category);
                    openAmountStep(player);
                }
            ));
        }

        // Row 6: Navigation
        // Slot 45: Previous Page (or Back to Categories if page 0)
        if (currentPage > 0) {
            gui.setItem(45, GuiButton.of(Material.SPECTRAL_ARROW, "&7◀ Previous Page",
                List.of("&7Page " + currentPage + "/" + totalPages),
                e -> openCategoryBrowser(player, category, currentPage - 1, query)));
        } else {
            gui.setItem(45, GuiButton.of(Material.ARROW, "&7◀ Back to Categories",
                List.of("&7Return to categories menu"),
                e -> openCategories(player)));
        }

        for (int s = 46; s <= 52; s++) {
            gui.setItem(s, GuiButton.filler(Material.GRAY_STAINED_GLASS_PANE));
        }

        // Slot 48: Dedicated Back Button
        gui.setItem(48, GuiButton.of(Material.ARROW, "&7◀ Categories",
            List.of("&7Return to categories menu"),
            e -> openCategories(player)));

        // Slot 49: Page indicator
        gui.setItem(49, GuiButton.of(Material.PAPER, "&7Page &f" + (currentPage + 1) + "&7/&f" + totalPages,
            List.of("&7Total items: &f" + filtered.size()), e -> {}));

        // Slot 53: Next Page
        if (currentPage < totalPages - 1) {
            gui.setItem(53, GuiButton.of(Material.SPECTRAL_ARROW, "&7Next Page ▶",
                List.of("&7Page " + (currentPage + 2) + "/" + totalPages),
                e -> openCategoryBrowser(player, category, currentPage + 1, query)));
        } else {
            gui.setItem(53, GuiButton.filler(Material.GRAY_STAINED_GLASS_PANE));
        }

        gui.open(player);
    }

    // Legacy method redirection for search
    public void openItemSearch(Player player, String query) {
        ItemCategory cat = selectedCategory.getOrDefault(player.getUniqueId(), ItemCategory.ALL);
        openCategoryBrowser(player, cat, 0, query);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ITEM FLOW — Step 2: Set Amount
    // ══════════════════════════════════════════════════════════════════════════

    private void openAmountStep(Player player) {
        Material mat = selectedMaterial.get(player.getUniqueId());
        if (mat == null) { openTypeSelect(player); return; }

        Gui gui = new Gui("&8▶ &a&lItem Order — How Many? &8◀", 4);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        // Selected item display (slot 4 top center)
        gui.setItem(4, GuiButton.of(mat, "&f" + OrderManager.formatMaterialName(mat),
            List.of("&7Selected item to request"),
            e -> {}
        ));

        // Preset amount buttons (row 2: slots 10–16)
        int[] presetSlots = {10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < Math.min(AMOUNT_PRESETS.length, presetSlots.length); i++) {
            int amount = AMOUNT_PRESETS[i];
            gui.setItem(presetSlots[i], GuiButton.of(Material.GOLD_NUGGET, "&e&l" + amount + "x",
                List.of("&7Request &f" + amount + "x " + OrderManager.formatMaterialName(mat), "", "&e▶ Click to select"),
                e -> {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                    selectedAmount.put(player.getUniqueId(), amount);
                    openPriceStep(player, Order.OrderType.ITEM);
                }
            ));
        }

        // Custom amount (slot 22)
        gui.setItem(22, GuiButton.of(Material.EMERALD, "&a&l✎ Custom Amount",
            List.of(
                "&8&m────────────────────",
                "&7Need a specific amount?",
                "&7Type any number in chat.",
                "&8&m────────────────────",
                "&e▶ Click to type custom amount"
            ),
            e -> {
                player.closeInventory();
                awaitingInput.put(player.getUniqueId(), CTX_AMOUNT);
                TextUtil.sendMessage(player, "&8[&6Orders&8] &eHow many &f" + OrderManager.formatMaterialName(mat) + "&e do you need? &8(type a number or &ccancel&8)");
            }
        ));

        // Back button at slot 27 (row 4, col 1)
        gui.setItem(27, GuiButton.of(Material.ARROW, "&7◀ Back",
            List.of("&7Return to item browser"),
            e -> {
                ItemCategory cat = selectedCategory.getOrDefault(player.getUniqueId(), ItemCategory.ALL);
                int p = categoryPage.getOrDefault(player.getUniqueId(), 0);
                String q = searchQuery.getOrDefault(player.getUniqueId(), "");
                openCategoryBrowser(player, cat, p, q);
            }
        ));

        gui.open(player);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ITEM + SERVICE FLOW — Step 3: Set Price
    // ══════════════════════════════════════════════════════════════════════════

    private void openPriceStep(Player player, Order.OrderType type) {
        Material mat    = selectedMaterial.get(player.getUniqueId());
        int      amount = selectedAmount.getOrDefault(player.getUniqueId(), 1);
        String   desc   = selectedDesc.get(player.getUniqueId());

        double feePct = config.getInt("posting-fee-percent", 5);

        String title = type == Order.OrderType.ITEM
            ? "&8▶ &a&lItem Order — Set Price &8◀"
            : "&8▶ &b&lService Order — Set Price &8◀";
        Gui gui = new Gui(title, 4);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        // Summary item (slot 4)
        if (type == Order.OrderType.ITEM && mat != null) {
            gui.setItem(4, GuiButton.of(mat, "&f" + amount + "x " + OrderManager.formatMaterialName(mat),
                List.of("&7You are requesting &f" + amount + "x &7of this item"),
                e -> {}
            ));
        } else {
            String shortDesc = desc != null && desc.length() > 30 ? desc.substring(0, 27) + "..." : (desc != null ? desc : "Service");
            gui.setItem(4, GuiButton.of(Material.WRITABLE_BOOK, "&b" + shortDesc,
                List.of("&7Service order request"),
                e -> {}
            ));
        }

        // Price preset buttons (row 2: slots 10–16)
        int[] priceSlots = {10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < Math.min(PRICE_PRESETS.length, priceSlots.length); i++) {
            double price   = PRICE_PRESETS[i];
            double fee     = price * (feePct / 100.0);
            double perItem = type == Order.OrderType.ITEM ? price / amount : price;
            List<String> lore = new ArrayList<>(List.of(
                "&8&m────────────────────",
                "&7Total Payout: &a" + plugin.getEconomyManager().format(price)
            ));
            if (type == Order.OrderType.ITEM) {
                lore.add("&7Per Item:     &a" + plugin.getEconomyManager().format(perItem));
            }
            lore.addAll(List.of(
                "&7Posting Fee (&e" + feePct + "%&7): &c" + plugin.getEconomyManager().format(fee),
                "&7You pay now:  &c" + plugin.getEconomyManager().format(price + fee),
                "&8&m────────────────────",
                "&e▶ Click to select this price"
            ));
            gui.setItem(priceSlots[i], GuiButton.of(Material.GOLD_INGOT,
                "&e&l" + plugin.getEconomyManager().format(price),
                lore,
                e -> {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                    openExpiryStep(player, type, price);
                }
            ));
        }

        // Custom price at slot 22
        gui.setItem(22, GuiButton.of(Material.EMERALD, "&a&l✎ Custom Price",
            List.of(
                "&8&m────────────────────",
                "&7Type any price in chat.",
                "&7Min: &f" + plugin.getEconomyManager().format(config.getDouble("min-order-value", 1000)),
                "&7Max: &f" + plugin.getEconomyManager().format(config.getDouble("max-order-value", 500000)),
                "&8&m────────────────────",
                "&e▶ Click to type custom price"
            ),
            e -> {
                player.closeInventory();
                awaitingInput.put(player.getUniqueId(), CTX_PRICE);
                TextUtil.sendMessage(player, "&8[&6Orders&8] &eHow much will you pay in total? &8(type a number or &ccancel&8)");
                TextUtil.sendMessage(player, "&8  &7Min: &f" + plugin.getEconomyManager().format(config.getDouble("min-order-value", 1000))
                        + "  &7Max: &f" + plugin.getEconomyManager().format(config.getDouble("max-order-value", 500000)));
            }
        ));

        // Back button at slot 27
        gui.setItem(27, GuiButton.of(Material.ARROW, "&7◀ Back", List.of("&7Change amount / details"),
            e -> {
                if (type == Order.OrderType.ITEM) openAmountStep(player);
                else promptServiceDescription(player);
            }
        ));

        gui.open(player);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ITEM + SERVICE FLOW — Step 3.5: Expiry Selection (NO GAPS, Contiguous)
    // ══════════════════════════════════════════════════════════════════════════

    public void openExpiryStep(Player player, Order.OrderType type, double priceTotal) {
        Gui gui = new Gui("&8▶ &6&lOrder Duration &8◀", 3);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        // Header info at slot 4
        gui.setItem(4, GuiButton.of(Material.CLOCK, "&e&lSelect Expiry Duration",
            List.of(
                "&8&m────────────────────",
                "&7How long should this order last?",
                "&7Orders expire if not filled.",
                "&8&m────────────────────",
                "&7Your escrowed money is returned",
                "&7if the order expires unfilled.",
                "&7(Max 7 days)",
                "&8&m────────────────────"
            ), e -> {}));

        // Contiguous, centered slots: 11, 12, 13, 14, 15 (Zero gap in the middle)
        int[] optionsSlots = {11, 12, 13, 14, 15};
        int[] daysList     = {1, 2, 3, 5, 7};
        Material[] mats    = {
            Material.COMPARATOR,
            Material.REPEATER,
            Material.REDSTONE_TORCH,
            Material.DAYLIGHT_DETECTOR,
            Material.CLOCK
        };
        String[] labels    = {"1 Day", "2 Days", "3 Days", "5 Days", "7 Days (Max)"};

        for (int i = 0; i < optionsSlots.length; i++) {
            final int days = daysList[i];
            int slot = optionsSlots[i];
            gui.setItem(slot, GuiButton.of(mats[i], "&e&l" + labels[i],
                List.of(
                    "&8&m────────────────────",
                    "&7Order will remain active for:",
                    "&a  ▶ " + days + " day" + (days > 1 ? "s" : "") + " (" + (days * 24) + " hours)",
                    "&8&m────────────────────",
                    "&e▶ Click to select this duration"
                ),
                e -> {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                    selectedExpiry.put(player.getUniqueId(), days);
                    openConfirmStep(player, type, priceTotal);
                }));
        }

        // Back button at slot 18 (bottom-left of 3-row GUI)
        gui.setItem(18, GuiButton.of(Material.ARROW, "&7◀ Back to Price",
            List.of("&7Change the order payout"),
            e -> openPriceStep(player, type)
        ));

        // Cancel / Close at slot 22
        gui.setItem(22, GuiButton.of(Material.BARRIER, "&c&lCancel",
            List.of("&7Cancel order creation"),
            e -> openTypeSelect(player)
        ));

        gui.open(player);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ITEM + SERVICE FLOW — Step 4: Confirm
    // ══════════════════════════════════════════════════════════════════════════

    private void openConfirmStep(Player player, Order.OrderType type, double priceTotal) {
        Material mat        = selectedMaterial.get(player.getUniqueId());
        int      amount     = selectedAmount.getOrDefault(player.getUniqueId(), 1);
        String   desc       = selectedDesc.get(player.getUniqueId());
        int      expiryDays = selectedExpiry.getOrDefault(player.getUniqueId(), 3);

        double feePct    = config.getInt("posting-fee-percent", 5);
        double fee       = priceTotal * (feePct / 100.0);
        double totalCost = priceTotal + fee;
        double perItem   = type == Order.OrderType.ITEM ? priceTotal / amount : priceTotal;

        Gui gui = new Gui("&8▶ &a&lConfirm Order &8◀", 3);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        // Summary item in slot 13
        Material displayMat = (type == Order.OrderType.ITEM && mat != null) ? mat : Material.WRITABLE_BOOK;
        String displayName  = (type == Order.OrderType.ITEM && mat != null)
            ? "&f" + amount + "x " + OrderManager.formatMaterialName(mat)
            : "&b" + (desc != null ? (desc.length() > 25 ? desc.substring(0, 22) + "..." : desc) : "Service");

        List<String> summaryLore = new ArrayList<>();
        summaryLore.add("&8&m────────────────────");
        if (type == Order.OrderType.ITEM) {
            summaryLore.add("&7Type:       &fItem Order");
            summaryLore.add("&7Item:       &f" + OrderManager.formatMaterialName(mat));
            summaryLore.add("&7Amount:     &f" + amount);
            summaryLore.add("&7Per Item:   &a" + plugin.getEconomyManager().format(perItem));
        } else {
            summaryLore.add("&7Type:       &bService Order");
            summaryLore.add("&7Request:    &f" + (desc != null ? desc : "N/A"));
        }
        summaryLore.addAll(List.of(
            "&8&m────────────────────",
            "&7Total Payout: &a" + plugin.getEconomyManager().format(priceTotal),
            "&7Expires in:   &e" + expiryDays + " day" + (expiryDays > 1 ? "s" : ""),
            "&8&m────────────────────",
            "&cPosting fee (" + feePct + "%) : &c" + plugin.getEconomyManager().format(fee),
            "&cTotal cost now:  &c" + plugin.getEconomyManager().format(totalCost),
            "&8(Refunded if cancelled, minus fee)",
            "&8&m────────────────────"
        ));

        gui.setItem(13, GuiButton.of(displayMat, displayName, summaryLore, e -> {}));

        // Confirm button at slot 11
        gui.setItem(11, GuiButton.of(Material.GREEN_STAINED_GLASS_PANE, "&a&l✓ Post Order",
            List.of(
                "&7You will be charged: &c" + plugin.getEconomyManager().format(totalCost),
                "&7This includes the &e" + feePct + "% &7posting fee.",
                "&8&m────────────────────",
                "&a▶ Click to confirm and post!"
            ),
            e -> {
                player.closeInventory();
                if (!plugin.getEconomyManager().has(player, totalCost)) {
                    TextUtil.sendMessage(player, "&8[&6Orders&8] &cYou need &e"
                            + plugin.getEconomyManager().format(totalCost) + "&c to post this order.");
                    return;
                }
                if (!plugin.getEconomyManager().withdraw(player, totalCost)) {
                    TextUtil.sendMessage(player, "&8[&6Orders&8] &cTransaction failed.");
                    return;
                }

                if (type == Order.OrderType.ITEM) {
                    orderManager.postItemOrder(player.getUniqueId(), player.getName(), mat, amount, priceTotal, expiryDays,
                        order -> {
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                            String prefix = config.getString("messages.prefix", "&8[&6Orders&8] &r");
                            TextUtil.sendMessage(player, prefix + "&aItem order posted! Payout held in escrow. &8(" + feePct + "% fee charged)");
                            cleanup(player.getUniqueId());
                        },
                        () -> {
                            plugin.getEconomyManager().deposit(player, totalCost);
                            TextUtil.sendMessage(player, "&8[&6Orders&8] &cFailed to post. Database error — money refunded.");
                        }
                    );
                } else {
                    orderManager.postServiceOrder(player.getUniqueId(), player.getName(), desc, priceTotal, expiryDays,
                        order -> {
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                            String prefix = config.getString("messages.prefix", "&8[&6Orders&8] &r");
                            TextUtil.sendMessage(player, prefix + "&bService order posted! Someone will accept and complete it.");
                            cleanup(player.getUniqueId());
                        },
                        () -> {
                            plugin.getEconomyManager().deposit(player, totalCost);
                            TextUtil.sendMessage(player, "&8[&6Orders&8] &cFailed to post. Database error — money refunded.");
                        }
                    );
                }
            }
        ));

        // Back button at slot 18
        gui.setItem(18, GuiButton.of(Material.ARROW, "&7◀ Back to Duration",
            List.of("&7Change the order duration"),
            e -> openExpiryStep(player, type, priceTotal)
        ));

        // Cancel button at slot 15
        gui.setItem(15, GuiButton.of(Material.RED_STAINED_GLASS_PANE, "&c&l✗ Cancel",
            List.of("&7Cancel and return to order type"),
            e -> openTypeSelect(player)
        ));

        gui.open(player);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  SERVICE FLOW — Description prompt
    // ══════════════════════════════════════════════════════════════════════════

    private void promptServiceDescription(Player player) {
        player.closeInventory();
        awaitingInput.put(player.getUniqueId(), CTX_SERVICE_DESC);
        TextUtil.sendMessage(player, "&8[&6Orders&8] &bDescribe your service request in chat:");
        TextUtil.sendMessage(player, "&8  &7Examples: &f\"Build me a wheat farm\"&7, &f\"Help decorate my base\"&7, &f\"PvP escort to outpost\"");
        TextUtil.sendMessage(player, "&8  &7Type &ccancel &7to go back.");
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Chat handler (search / amount / price / service description)
    // ══════════════════════════════════════════════════════════════════════════

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!awaitingInput.containsKey(uuid)) return;

        event.setCancelled(true);
        String ctx   = awaitingInput.remove(uuid);
        String input = event.getMessage().trim();
        Player p     = event.getPlayer();

        if (input.equalsIgnoreCase("cancel")) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                TextUtil.sendMessage(p, "&8[&6Orders&8] &cCancelled.");
                switch (ctx) {
                    case CTX_SEARCH       -> openCategories(p);
                    case CTX_AMOUNT       -> openAmountStep(p);
                    case CTX_PRICE        -> openPriceStep(p, orderTypeState.getOrDefault(uuid, Order.OrderType.ITEM));
                    case CTX_SERVICE_DESC -> openTypeSelect(p);
                }
            });
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            switch (ctx) {
                case CTX_SEARCH -> {
                    ItemCategory cat = selectedCategory.getOrDefault(uuid, ItemCategory.ALL);
                    openCategoryBrowser(p, cat, 0, input);
                }
                case CTX_AMOUNT -> {
                    try {
                        int amt = Integer.parseInt(input);
                        if (amt <= 0) throw new NumberFormatException();
                        selectedAmount.put(uuid, amt);
                        openPriceStep(p, Order.OrderType.ITEM);
                    } catch (NumberFormatException ex) {
                        TextUtil.sendMessage(p, "&8[&6Orders&8] &cInvalid number. Please type a whole number.");
                        awaitingInput.put(uuid, CTX_AMOUNT);
                        TextUtil.sendMessage(p, "&8[&6Orders&8] &eHow many do you need? &8(or &ccancel&8)");
                    }
                }
                case CTX_PRICE -> {
                    try {
                        double price = Double.parseDouble(input.replace(",", ""));
                        double min   = config.getDouble("min-order-value", 1000);
                        double max   = config.getDouble("max-order-value", 500000);
                        if (price < min || price > max) {
                            TextUtil.sendMessage(p, "&8[&6Orders&8] &cPrice must be between &e"
                                    + plugin.getEconomyManager().format(min) + "&c and &e"
                                    + plugin.getEconomyManager().format(max) + "&c.");
                            awaitingInput.put(uuid, CTX_PRICE);
                            return;
                        }
                        Order.OrderType type = orderTypeState.getOrDefault(uuid, Order.OrderType.ITEM);
                        openExpiryStep(p, type, price);
                    } catch (NumberFormatException ex) {
                        TextUtil.sendMessage(p, "&8[&6Orders&8] &cInvalid price. Please type a number (e.g. 50000).");
                        awaitingInput.put(uuid, CTX_PRICE);
                    }
                }
                case CTX_SERVICE_DESC -> {
                    if (input.length() < 5) {
                        TextUtil.sendMessage(p, "&8[&6Orders&8] &cDescription too short. Please describe your request.");
                        awaitingInput.put(uuid, CTX_SERVICE_DESC);
                        return;
                    }
                    if (input.length() > 100) {
                        TextUtil.sendMessage(p, "&8[&6Orders&8] &cDescription too long (max 100 chars, you typed " + input.length() + ").");
                        awaitingInput.put(uuid, CTX_SERVICE_DESC);
                        return;
                    }
                    selectedDesc.put(uuid, input);
                    openPriceStep(p, Order.OrderType.SERVICE);
                }
            }
        });
    }

    // ── Cleanup on order posted / cancelled ───────────────────────────────

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        cleanup(event.getPlayer().getUniqueId());
    }

    private void cleanup(UUID uuid) {
        selectedMaterial.remove(uuid);
        selectedCategory.remove(uuid);
        categoryPage.remove(uuid);
        selectedAmount.remove(uuid);
        selectedDesc.remove(uuid);
        searchQuery.remove(uuid);
        orderTypeState.remove(uuid);
        selectedExpiry.remove(uuid);
        awaitingInput.remove(uuid);
    }
}
