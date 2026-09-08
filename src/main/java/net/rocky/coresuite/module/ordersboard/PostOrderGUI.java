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
 *   Type Select → Search/Browse Material → Set Amount → Set Price → Confirm
 *
 * Flow B — Service Order:
 *   Type Select → Type Description (chat) → Set Price → Confirm
 */
public class PostOrderGUI implements Listener {

    // ── Chat context keys ───────────────────────────────────────────────────
    private static final String CTX_SEARCH      = "search";
    private static final String CTX_AMOUNT      = "amount";
    private static final String CTX_PRICE       = "price";
    private static final String CTX_SERVICE_DESC = "service_desc";

    // ── Admin-only / unobtainable materials blocked from the search browser ─
    private static final Set<String> ADMIN_ONLY = Set.of(
        "COMMAND_BLOCK", "CHAIN_COMMAND_BLOCK", "REPEATING_COMMAND_BLOCK",
        "COMMAND_BLOCK_MINECART", "BARRIER", "BEDROCK", "DEBUG_STICK",
        "KNOWLEDGE_BOOK", "JIGSAW", "STRUCTURE_BLOCK", "STRUCTURE_VOID",
        "LIGHT", "BUNDLE", "PETRIFIED_OAK_SLAB", "SPAWNER",
        "INFESTED_STONE", "INFESTED_COBBLESTONE", "INFESTED_STONE_BRICKS",
        "INFESTED_CRACKED_STONE_BRICKS", "INFESTED_CHISELED_STONE_BRICKS",
        "INFESTED_DEEPSLATE", "REINFORCED_DEEPSLATE", "BUDDING_AMETHYST",
        "FROGSPAWN", "EGG", "TURTLE_EGG", "SNIFFER_EGG", "DRAGON_EGG",
        "SUSPICIOUS_SAND", "SUSPICIOUS_GRAVEL"
    );

    private static List<Material> buildAllMats() {
        return Arrays.stream(Material.values())
            .filter(m -> m.isItem() && !m.isAir() && !m.name().startsWith("LEGACY_"))
            .filter(m -> !ADMIN_ONLY.contains(m.name()))
            .filter(m -> !m.name().contains("SPAWN_EGG")) // too many, separate category
            .sorted(Comparator.comparing(Material::name))
            .collect(Collectors.toList());
    }

    // ── Preset amounts and prices ───────────────────────────────────────────
    private static final int[]    AMOUNT_PRESETS = {16, 32, 64, 128, 256, 512, 1000, 2000, 5000};
    private static final double[] PRICE_PRESETS  = {5_000, 10_000, 25_000, 50_000, 100_000, 250_000, 500_000};

    // ── Content slot layout (6-row GUI, no column 0/8 borders) ─────────────
    private static final int[] CONTENT_SLOTS = {
        10,11,12,13,14,15,16,
        19,20,21,22,23,24,25,
        28,29,30,31,32,33,34,
        37,38,39,40,41,42,43
    }; // 28 slots per page

    // ── State maps (keyed by player UUID) ──────────────────────────────────
    private final Map<UUID, Material>      selectedMaterial    = new ConcurrentHashMap<>();
    private final Map<UUID, Integer>       selectedAmount      = new ConcurrentHashMap<>();
    private final Map<UUID, String>        selectedDesc        = new ConcurrentHashMap<>();  // service description
    private final Map<UUID, String>        searchQuery         = new ConcurrentHashMap<>();
    private final Map<UUID, String>        awaitingInput       = new ConcurrentHashMap<>();
    private final Map<UUID, Order.OrderType> orderTypeState    = new ConcurrentHashMap<>();
    private final Map<UUID, Integer>       selectedExpiry      = new ConcurrentHashMap<>();  // expiry in days

    private final CoreSuitePlugin plugin;
    private final OrderManager    orderManager;
    private final FileConfiguration config;
    private BoardGUI boardGUI;

    public PostOrderGUI(CoreSuitePlugin plugin, OrderManager orderManager, FileConfiguration config) {
        this.plugin       = plugin;
        this.orderManager = orderManager;
        this.config       = config;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
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
                "&7Examples:",
                "&8  ▸ &f500x Iron Ingots",
                "&8  ▸ &f1000x Wheat",
                "&8  ▸ &f64x Diamonds",
                "&8&m────────────────────",
                "&e▶ Click to post an Item Order"
            ),
            e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                orderTypeState.put(player.getUniqueId(), Order.OrderType.ITEM);
                openItemSearch(player, "");
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

        // Info item
        gui.setItem(13, GuiButton.of(Material.GOLD_INGOT, "&6&lHow Orders Work",
            List.of(
                "&8&m────────────────────",
                "&7When you post an order:",
                "&8  ▸ &fFull payout is &cHeld in escrow",
                "&8  ▸ &fA &e" + config.getInt("posting-fee-percent", 5) + "% posting fee &fis charged",
                "&8  ▸ &fFulfillers earn money &ainstantly",
                "&8  ▸ &fOrders expire in &e"
                        + config.getInt("expiry-hours", 72) + "h &f(&e10% &ffee if expired)",
                "&8&m────────────────────",
                "&7Max active orders: &f" + config.getInt("max-orders-per-player", 3)
            ),
            e -> {}
        ));

        gui.setItem(45, GuiButton.of(Material.ARROW, "&7◀ Back to Board", List.of(), e -> {
            if (boardGUI != null) boardGUI.open(player, 1, BoardGUI.SortMode.HIGHEST_PAYOUT);
        }));

        gui.open(player);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ITEM FLOW — Step 1: Search / Browse Materials
    // ══════════════════════════════════════════════════════════════════════════

    /** Opens the item search/browse GUI, optionally filtered by `query`. */
    public void openItemSearch(Player player, String query) {
        openItemSearchPage(player, query, 0);
    }

    private void openItemSearchPage(Player player, String query, int page) {
        // Build the filtered material list from all valid, obtainable materials
        List<Material> allMats = buildAllMats();

        List<Material> filtered;
        if (query == null || query.isBlank()) {
            filtered = allMats;
        } else {
            String q = query.toLowerCase().replace(" ", "_");
            filtered = allMats.stream()
                .filter(m -> m.name().toLowerCase().contains(q)
                          || OrderManager.formatMaterialName(m).toLowerCase().contains(query.toLowerCase()))
                .collect(Collectors.toList());
        }

        int totalPages = Math.max(1, (int) Math.ceil((double) filtered.size() / CONTENT_SLOTS.length));
        final int currentPage = Math.max(0, Math.min(page, totalPages - 1));

        String displayQuery = (query == null || query.isBlank()) ? "All Items" : "\"" + query + "\"";
        Gui gui = new Gui("&8▶ &a&lItem Order — Search &8◀", 6);

        // Full black border
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);
        for (int col = 1; col <= 7; col++) {
            gui.setItem(col, GuiButton.filler(Material.BLACK_STAINED_GLASS_PANE)); // top row all black
        }

        // Slot 4: search bar display
        String searchLoreHint = query == null || query.isBlank()
            ? "&7Click to search for a specific item"
            : "&7Current search: &f" + query + "\n&7Click to change search";
        gui.setItem(4, GuiButton.of(Material.SPYGLASS, "&e🔍 Search: &f" + displayQuery,
            List.of(
                "&8&m────────────────────",
                "&7Searching: &f" + filtered.size() + " results",
                searchLoreHint,
                "&8&m────────────────────",
                "&e▶ Click to type a new search"
            ),
            e -> {
                player.closeInventory();
                awaitingInput.put(player.getUniqueId(), CTX_SEARCH);
                TextUtil.sendMessage(player, "&8[&6Orders&8] &eType an item name to search for &8(or &ccancel&8):");
                TextUtil.sendMessage(player, "&8 &7Tip: type &fwheat&7, &firon&7, &fdiamond&7, etc.");
            }
        ));

        // Slot 2: sort/category hint
        gui.setItem(2, GuiButton.of(Material.PAPER, "&7Browse All Items",
            List.of("&7Showing &f" + filtered.size() + "&7 items", "&8Page " + (page + 1) + "/" + totalPages),
            e -> {}
        ));

        // Fill content slots
        int start = currentPage * CONTENT_SLOTS.length;
        for (int i = 0; i < CONTENT_SLOTS.length; i++) {
            int idx = start + i;
            if (idx >= filtered.size()) break;
            Material mat = filtered.get(idx);
            gui.setItem(CONTENT_SLOTS[i], GuiButton.of(mat,
                "&f" + OrderManager.formatMaterialName(mat),
                List.of("&7Click to request this item"),
                e -> {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                    selectedMaterial.put(player.getUniqueId(), mat);
                    searchQuery.put(player.getUniqueId(), query == null ? "" : query);
                    openAmountStep(player);
                }
            ));
        }

        // Navigation row (row 6)
        gui.setItem(45, currentPage > 0
            ? GuiButton.of(Material.SPECTRAL_ARROW, "&7◀ Previous",
                List.of("&7Page " + currentPage + "/" + totalPages),
                e -> openItemSearchPage(player, query, currentPage - 1))
            : GuiButton.of(Material.ARROW, "&7◀ Back",
                List.of("&7Return to order type selection"),
                e -> openTypeSelect(player))
        );

        for (int s = 46; s <= 52; s++) gui.setItem(s, GuiButton.filler(Material.GRAY_STAINED_GLASS_PANE));

        gui.setItem(49, GuiButton.of(Material.PAPER, "&7Page &f" + (currentPage + 1) + "&7/&f" + totalPages, List.of(), e -> {}));

        if (currentPage < totalPages - 1) {
            gui.setItem(53, GuiButton.of(Material.SPECTRAL_ARROW, "&7Next ▶",
                List.of("&7Page " + (currentPage + 2) + "/" + totalPages),
                e -> openItemSearchPage(player, query, currentPage + 1)));
        } else {
            gui.setItem(53, GuiButton.filler(Material.GRAY_STAINED_GLASS_PANE));
        }

        gui.open(player);
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
            List.of("&7Selected item"),
            e -> {}
        ));

        // Preset amount buttons (row 2, slots 9–17 → use 10–16)
        int[] presetSlots = {10, 11, 12, 13, 14, 15, 16};
        for (int i = 0; i < Math.min(AMOUNT_PRESETS.length, presetSlots.length); i++) {
            int amount = AMOUNT_PRESETS[i];
            gui.setItem(presetSlots[i], GuiButton.of(Material.GOLD_NUGGET, "&e&l" + amount + "x",
                List.of("&7Request &f" + amount + "x " + OrderManager.formatMaterialName(mat), "&e▶ Click to select"),
                e -> {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                    selectedAmount.put(player.getUniqueId(), amount);
                    openPriceStep(player, Order.OrderType.ITEM);
                }
            ));
        }

        // Custom amount
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

        // Back
        gui.setItem(27, GuiButton.of(Material.ARROW, "&7◀ Back", List.of("&7Change item"), e -> {
            String q = searchQuery.getOrDefault(player.getUniqueId(), "");
            openItemSearch(player, q);
        }));

        gui.open(player);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ITEM + SERVICE FLOW — Step 3: Set Price
    // ══════════════════════════════════════════════════════════════════════════

    private void openPriceStep(Player player, Order.OrderType type) {
        Material mat      = selectedMaterial.get(player.getUniqueId());  // null for service
        int      amount   = selectedAmount.getOrDefault(player.getUniqueId(), 1);
        String   desc     = selectedDesc.get(player.getUniqueId());

        double feePct = config.getInt("posting-fee-percent", 5);

        String title = type == Order.OrderType.ITEM
            ? "&8▶ &a&lItem Order — Set Price &8◀"
            : "&8▶ &b&lService Order — Set Price &8◀";
        Gui gui = new Gui(title, 4);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        // Summary item (center top)
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
                lore.add("&7Per Item: &a" + plugin.getEconomyManager().format(perItem));
            }
            lore.addAll(List.of(
                "&7Posting Fee (&e" + feePct + "%&7): &c" + plugin.getEconomyManager().format(fee),
                "&7You pay now: &c" + plugin.getEconomyManager().format(price + fee),
                "&8&m────────────────────",
                "&e▶ Click to select this price"
            ));
            gui.setItem(priceSlots[i], GuiButton.of(Material.GOLD_INGOT,
                "&e&l" + plugin.getEconomyManager().format(price),
                lore,
                e -> {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                    // Store price temporarily in selectedAmount context isn't needed — store in state
                    // We pass price through to expiry step by storing it in a temporary field
                    openExpiryStep(player, type, price);
                }
            ));
        }

        // Custom price
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

        // Back
        gui.setItem(27, GuiButton.of(Material.ARROW, "&7◀ Back", List.of(),
            e -> {
                if (type == Order.OrderType.ITEM) openAmountStep(player);
                else promptServiceDescription(player);
            }
        ));

        gui.open(player);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ITEM + SERVICE FLOW — Step 3.5: Expiry Selection
    // ══════════════════════════════════════════════════════════════════════════

    public void openExpiryStep(Player player, Order.OrderType type, double priceTotal) {
        Gui gui = new Gui("&8▶ &6&lOrder Duration &8◀", 3);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

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

        int[][] options = {{10, 1}, {11, 2}, {12, 3}, {14, 5}, {15, 7}};
        Material[] mats = {Material.COMPARATOR, Material.REPEATER, Material.REDSTONE_TORCH,
                           Material.DAYLIGHT_DETECTOR, Material.CLOCK};
        String[] labels = {"1 Day", "2 Days", "3 Days", "5 Days", "7 Days (Max)"};

        for (int i = 0; i < options.length; i++) {
            final int days = options[i][1];
            int slot = options[i][0];
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

        gui.setItem(22, GuiButton.of(Material.ARROW, "&7◀ Back to Price", List.of(), e -> {
            openPriceStep(player, type);
        }));

        gui.open(player);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  ITEM + SERVICE FLOW — Step 4: Confirm
    // ══════════════════════════════════════════════════════════════════════════

    private void openConfirmStep(Player player, Order.OrderType type, double priceTotal) {
        Material mat     = selectedMaterial.get(player.getUniqueId());
        int      amount  = selectedAmount.getOrDefault(player.getUniqueId(), 1);
        String   desc    = selectedDesc.get(player.getUniqueId());
        int      expiryDays = selectedExpiry.getOrDefault(player.getUniqueId(), 3);

        double feePct    = config.getInt("posting-fee-percent", 5);
        double fee       = priceTotal * (feePct / 100.0);
        double totalCost = priceTotal + fee;
        double perItem   = type == Order.OrderType.ITEM ? priceTotal / amount : priceTotal;

        Gui gui = new Gui("&8▶ &a&lConfirm Order &8◀", 3);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        // Summary item
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

        // Confirm
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

        // Cancel — go back to expiry picker
        gui.setItem(15, GuiButton.of(Material.RED_STAINED_GLASS_PANE, "&c&l✗ Cancel",
            List.of("&7Go back and change your order."),
            e -> openExpiryStep(player, type, priceTotal)
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
                    case CTX_SEARCH  -> openItemSearch(p, "");
                    case CTX_AMOUNT  -> openAmountStep(p);
                    case CTX_PRICE   -> openPriceStep(p, orderTypeState.getOrDefault(uuid, Order.OrderType.ITEM));
                    case CTX_SERVICE_DESC -> openTypeSelect(p);
                }
            });
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            switch (ctx) {
                case CTX_SEARCH -> {
                    // Apply search and reopen
                    openItemSearch(p, input);
                }
                case CTX_AMOUNT -> {
                    try {
                        int amt = Integer.parseInt(input);
                        if (amt <= 0) throw new NumberFormatException();
                        selectedAmount.put(uuid, amt);
                        openPriceStep(p, Order.OrderType.ITEM);
                    } catch (NumberFormatException ex) {
                        TextUtil.sendMessage(p, "&8[&6Orders&8] &cInvalid number. Please type a whole number.");
                        awaitingInput.put(uuid, CTX_AMOUNT); // re-prompt
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

    private void cleanup(UUID uuid) {
        selectedMaterial.remove(uuid);
        selectedAmount.remove(uuid);
        selectedDesc.remove(uuid);
        searchQuery.remove(uuid);
        orderTypeState.remove(uuid);
        selectedExpiry.remove(uuid);
        awaitingInput.remove(uuid);
    }
}
