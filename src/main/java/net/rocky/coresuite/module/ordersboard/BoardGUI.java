package net.rocky.coresuite.module.ordersboard;

import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.gui.GuiButton;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.CoreSuitePlugin;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class BoardGUI {

    private static final int[] ITEM_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };
    private static final int ITEMS_PER_PAGE = ITEM_SLOTS.length;

    public enum SortMode {
        HIGHEST_PAYOUT, LOWEST_FILLED, NEWEST;
        
        public SortMode next() {
            int nextOrdinal = (this.ordinal() + 1) % values().length;
            return values()[nextOrdinal];
        }
    }

    public enum CategoryFilter {
        ALL("All Orders", Material.CHEST, "&f&lAll Orders"),
        BUILDING("Blocks & Building", Material.GRASS_BLOCK, "&6&lBlocks &8& &6&lBuilding"),
        RESOURCES("Resources & Ores", Material.IRON_INGOT, "&b&lResources &8& &b&lOres"),
        COMBAT("Tools & Combat", Material.DIAMOND_SWORD, "&c&lTools &8& &c&lCombat"),
        FARMING("Farming & Food", Material.WHEAT, "&a&lFarming &8& &6&lFood"),
        MOBS("Mob Drops & Spawners", Material.GUNPOWDER, "&2&lMob Drops &8& &4&lSpawners"),
        DECORATION("Decoration & Dyes", Material.TUBE_CORAL, "&9&lDecoration &8& &d&lDyes"),
        MAGIC("Enchants & Potions", Material.ENCHANTING_TABLE, "&5&lEnchants &8& &d&lPotions"),
        STORAGE("Storage & Boxes", Material.CHEST, "&6&lStorage &8& &e&lBoxes"),
        MISC("Music & Misc", Material.MUSIC_DISC_CAT, "&d&lMusic &8& &e&lMisc"),
        SERVICES("Service Orders", Material.WRITABLE_BOOK, "&b&lService Orders");

        private final String displayName;
        private final Material icon;
        private final String title;

        CategoryFilter(String displayName, Material icon, String title) {
            this.displayName = displayName;
            this.icon = icon;
            this.title = title;
        }

        public String getDisplayName() { return displayName; }
        public Material getIcon() { return icon; }
        public String getTitle() { return title; }

        public CategoryFilter next() {
            int nextOrdinal = (this.ordinal() + 1) % values().length;
            return values()[nextOrdinal];
        }
    }

    private final Map<UUID, CategoryFilter> playerFilter = new ConcurrentHashMap<>();

    private final CoreSuitePlugin plugin;
    private final OrderManager orderManager;
    private final FileConfiguration config;
    private final PostOrderGUI postOrderGUI;
    private final MyOrdersGUI myOrdersGUI;
    private BidGUI bidGUI;

    public BoardGUI(CoreSuitePlugin plugin, OrderManager orderManager, FileConfiguration config, PostOrderGUI postOrderGUI, MyOrdersGUI myOrdersGUI) {
        this.plugin = plugin;
        this.orderManager = orderManager;
        this.config = config;
        this.postOrderGUI = postOrderGUI;
        this.myOrdersGUI = myOrdersGUI;
    }

    public void setBidGUI(BidGUI bidGUI) { this.bidGUI = bidGUI; }

    public static boolean matchesFilter(Order order, CategoryFilter filter) {
        if (filter == CategoryFilter.ALL) return true;
        if (filter == CategoryFilter.SERVICES) return order.isServiceOrder();
        if (order.isServiceOrder()) return false;
        PostOrderGUI.ItemCategory cat = PostOrderGUI.getCategory(order.getMaterial());
        return switch (filter) {
            case BUILDING -> cat == PostOrderGUI.ItemCategory.BUILDING;
            case RESOURCES -> cat == PostOrderGUI.ItemCategory.RESOURCES;
            case COMBAT -> cat == PostOrderGUI.ItemCategory.COMBAT;
            case FARMING -> cat == PostOrderGUI.ItemCategory.FARMING;
            case MOBS -> cat == PostOrderGUI.ItemCategory.MOBS;
            case DECORATION -> cat == PostOrderGUI.ItemCategory.DECORATION;
            case MAGIC -> cat == PostOrderGUI.ItemCategory.MAGIC;
            case STORAGE -> cat == PostOrderGUI.ItemCategory.STORAGE;
            case MISC -> cat == PostOrderGUI.ItemCategory.MISC;
            default -> true;
        };
    }

    public void open(Player player, int page, SortMode sort) {
        orderManager.loadActiveOrders(allOrders -> {
            sortOrders(allOrders, sort);
            
            CategoryFilter activeFilter = playerFilter.getOrDefault(player.getUniqueId(), CategoryFilter.ALL);
            int totalUnfiltered = allOrders.size();
            List<Order> orders = allOrders.stream()
                .filter(o -> matchesFilter(o, activeFilter))
                .collect(Collectors.toList());

            Gui gui = new Gui("&8▶ &6&l📋 Orders Board &8◀", 6);
            gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);
            
            int activeCount = orders.size();
            int playerActive = orderManager.getPlayerActiveOrderCount(player.getUniqueId());
            int maxOrders = config.getInt("max-orders-per-player", 3);
            
            gui.setItem(4, GuiButton.of(Material.PAPER, "&6&lOrders Board", List.of(
                    "&7Total Active: &a" + totalUnfiltered,
                    "&7Filter: &e" + activeFilter.getDisplayName() + " &7(&a" + activeCount + "&7)",
                    "&7Your Orders: &a" + playerActive + "&7/&f" + maxOrders,
                    " ",
                    "&eClick to view your orders"
            ), e -> myOrdersGUI.open(player, 1)));
            
            int totalPages = (int) Math.ceil((double) activeCount / ITEMS_PER_PAGE);
            if (totalPages == 0) totalPages = 1;
            
            int startIdx = (page - 1) * ITEMS_PER_PAGE;
            int endIdx = Math.min(startIdx + ITEMS_PER_PAGE, activeCount);
            
            int slotIdx = 0;
            for (int i = startIdx; i < endIdx; i++) {
                Order order = orders.get(i);

                List<String> lore = new ArrayList<>();
                lore.add("&8&m────────────────────");
                lore.add("&7Posted by: &f" + order.getPosterName());
                lore.add("&7Expires:   &e" + order.getTimeLeftString());
                lore.add("&8&m────────────────────");

                if (order.isItemOrder()) {
                    // ── ITEM order ──────────────────────────────────────────
                    int playerHas = OrderManager.getAmountInInventory(player, order.getMaterial());
                    lore.add("&7Progress:  &a" + order.getAmountFilled() + "&7/&f" + order.getAmountNeeded() + " items");
                    lore.add("&7Per item:  &a" + plugin.getEconomyManager().format(order.getPricePerItem()));
                    lore.add("&7Remaining: &a" + plugin.getEconomyManager().format(order.getPriceRemaining()));
                    lore.add("&8&m────────────────────");

                    boolean isAccepted = (order.getStatus() == Order.Status.ACCEPTED);
                    boolean isContractor = isAccepted && player.getUniqueId().toString().equals(order.getAcceptedByUuid());
                    boolean isPoster = player.getUniqueId().toString().equals(order.getPosterUuid());

                    if (isPoster) {
                        lore.add("&e★ THIS IS YOUR ORDER");
                        if (isAccepted) {
                            String acceptor = order.getAcceptedByName() != null ? order.getAcceptedByName() : "Contractor";
                            lore.add("&a✓ Deal Accepted by: &f" + acceptor);
                            lore.add("&7Waiting for delivery of items.");
                        }
                        lore.add("&e▶ Click to MANAGE order");
                    } else if (isAccepted) {
                        String acceptor = order.getAcceptedByName() != null ? order.getAcceptedByName() : "Contractor";
                        lore.add("&a✓ Deal Accepted by: &f" + acceptor);
                        lore.add("&7Agreed Payout:      &a" + plugin.getEconomyManager().format(order.getPriceTotal()));
                        lore.add("&8&m────────────────────");

                        if (isContractor) {
                            if (playerHas == 0) {
                                lore.add("&c✗ You have none of this item");
                                lore.add("&7Deliver " + order.getAmountRemaining() + "x to claim &a" + plugin.getEconomyManager().format(order.getPriceRemaining()));
                            } else {
                                lore.add("&aYou have: &f" + playerHas + " &7in inventory");
                                lore.add(playerHas >= order.getAmountRemaining()
                                    ? "&a▶ Click to fulfill your accepted order! (" + plugin.getEconomyManager().format(order.getPriceRemaining()) + ")"
                                    : "&e▶ Click to partially fulfill (+&a" + plugin.getEconomyManager().format(playerHas * order.getPricePerItem()) + "&e)");
                            }
                        } else {
                            lore.add("&e(Reserved for &f" + acceptor + "&e)");
                            lore.add("&8(Deal locked — awaiting delivery)");
                        }
                    } else {
                        if (playerHas == 0) {
                            lore.add("&c✗ You have none of this item");
                        } else {
                            lore.add("&aYou have: &f" + playerHas + " &7in inventory");
                        }
                        lore.add("&e▶ Click to DELIVER or BARGAIN");
                    }

                    final int finalPlayerHas = playerHas;
                    gui.setItem(ITEM_SLOTS[slotIdx++], GuiButton.of(order.getDisplayMaterial(),
                        "&f" + order.getDisplayTitle(), lore,
                        e -> {
                            if (isPoster) {
                                if (myOrdersGUI != null) {
                                    myOrdersGUI.openManageOrder(player, order, () -> open(player, page, sort));
                                }
                                return;
                            }
                            if (isAccepted) {
                                if (!isContractor) {
                                    String acceptor = order.getAcceptedByName() != null ? order.getAcceptedByName() : "another player";
                                    TextUtil.sendMessage(player, "&8[&6Orders&8] &cThis order deal is reserved for &f" + acceptor + "&c.");
                                    return;
                                }
                                if (finalPlayerHas == 0) {
                                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
                                    TextUtil.sendMessage(player, "&cYou don't have any " + order.getDisplayTitle() + " to deliver!");
                                } else {
                                    openFulfillConfirm(player, order, finalPlayerHas, page, sort);
                                }
                                return;
                            }
                            // Single-click opens item action menu with touch-friendly deliver or bargain buttons
                            openItemAction(player, order, finalPlayerHas, page, sort);
                        }
                    ));
                } else {
                    // ── SERVICE order ────────────────────────────────────────
                    String desc = order.getDescription() != null ? order.getDescription() : "Service request";
                    lore.add("&7Request: &f" + desc);
                    lore.add("&7Reward:  &a" + plugin.getEconomyManager().format(order.getPriceTotal()));
                    lore.add("&8&m────────────────────");

                    boolean isAccepted = (order.getStatus() == Order.Status.ACCEPTED);
                    boolean isPoster = player.getUniqueId().toString().equals(order.getPosterUuid());

                    if (isPoster) {
                        lore.add("&e★ THIS IS YOUR ORDER");
                        if (isAccepted) {
                            String acceptor = order.getAcceptedByName() != null ? order.getAcceptedByName() : "Contractor";
                            lore.add("&a✓ Accepted by: &f" + acceptor);
                        }
                        lore.add("&e▶ Click to MANAGE order");
                    } else if (isAccepted && order.getAcceptedByName() != null) {
                        lore.add("&a✓ Accepted by: &f" + order.getAcceptedByName());
                        lore.add("&8(Deal locked — awaiting completion)");
                    } else {
                        lore.add("&e▶ Click to ACCEPT or BARGAIN");
                        lore.add("&7Reward: &a" + plugin.getEconomyManager().format(order.getPriceTotal()) + " &7on completion");
                    }

                    gui.setItem(ITEM_SLOTS[slotIdx++], GuiButton.of(order.getDisplayMaterial(),
                        "&b" + order.getDisplayTitle(), lore,
                        e -> {
                            if (isPoster) {
                                if (myOrdersGUI != null) {
                                    myOrdersGUI.openManageOrder(player, order, () -> open(player, page, sort));
                                }
                                return;
                            }

                            if (order.getStatus() != Order.Status.OPEN) {
                                String acceptor = order.getAcceptedByName() != null ? order.getAcceptedByName() : "another player";
                                TextUtil.sendMessage(player, "&8[&6Orders&8] &cThis service has already been accepted by &f" + acceptor + "&c.");
                                return;
                            }

                            // Single click opens the Service Action menu (Accept at listed price OR Bargain)
                            openServiceAction(player, order, page, sort);
                        }
                    ));
                }
            }
            
            if (page > 1) {
                gui.setItem(45, GuiButton.of(Material.SPECTRAL_ARROW, "&7◀ Previous Page", List.of("&7Page " + (page - 1)), e -> open(player, page - 1, sort)));
            } else {
                gui.setItem(45, GuiButton.filler(Material.GRAY_STAINED_GLASS_PANE));
            }
            
            gui.setItem(46, GuiButton.of(Material.HOPPER, "&6&lSort Mode", List.of(
                "&7Current: &e" + sort.name(),
                " ",
                "&eClick to cycle"
            ), e -> {
                open(player, 1, sort.next());
            }));

            // Slot 47: My Bids button
            gui.setItem(47, GuiButton.of(Material.GOLD_INGOT, "&6&lMy Bids", List.of(
                "&8&m────────────────────",
                "&7View incoming bids on",
                "&7your posted orders!",
                "&8&m────────────────────",
                "&e▶ Click to view bids"
            ), e -> {
                if (bidGUI != null) bidGUI.openMyBids(player);
            }));

            // Slot 49: My Orders button
            gui.setItem(49, GuiButton.of(Material.BOOK, "&e&lMy Orders &8(&f" + playerActive + "&8/&f" + maxOrders + "&8)", List.of(
                "&8&m────────────────────",
                "&7View and manage your orders,",
                "&7cancel active orders, or",
                "&7collect refunds & payouts.",
                "&8&m────────────────────",
                "&e▶ Click to open My Orders"
            ), e -> {
                if (myOrdersGUI != null) myOrdersGUI.open(player, 1);
            }));

            // Slot 48: Dynamic Category Filter button (100% single-click touch friendly)
            gui.setItem(48, GuiButton.of(activeFilter.getIcon(), "&6&lCategory: &f" + activeFilter.getDisplayName(), List.of(
                "&8&m────────────────────",
                "&7Filter: &e" + activeFilter.getDisplayName(),
                "&7Showing: &a" + activeCount + "&7/&f" + totalUnfiltered + " orders",
                "&8&m────────────────────",
                "&e▶ Click to filter by category"
            ), e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                openCategoryFilterPicker(player, page, sort, allOrders);
            }));
            
            gui.setItem(50, GuiButton.filler(Material.GRAY_STAINED_GLASS_PANE));
            gui.setItem(51, GuiButton.filler(Material.GRAY_STAINED_GLASS_PANE));
            
            if (playerActive >= maxOrders) {
                gui.setItem(52, GuiButton.of(Material.BARRIER, "&c&lOrder Limit Reached (" + playerActive + "/" + maxOrders + ")", List.of(
                    "&8&m────────────────────",
                    "&7You have reached your limit",
                    "&7of &e" + maxOrders + " &7active orders.",
                    "&8&m────────────────────",
                    "&c▶ Click to CANCEL an order to free a slot!"
                ), e -> {
                    if (myOrdersGUI != null) myOrdersGUI.open(player, 1);
                }));
            } else {
                int feePct = config.getInt("posting-fee-percent", 5);
                gui.setItem(52, GuiButton.of(Material.EMERALD, "&a&l+ Post New Order", List.of(
                    "&7Fee: &e" + feePct + "%",
                    "&7Active: &e" + playerActive + "&7/&e" + maxOrders,
                    " ",
                    "&aClick to post"
                ), e -> postOrderGUI.openTypeSelect(player)));
            }
            
            if (page < totalPages) {
                gui.setItem(53, GuiButton.of(Material.SPECTRAL_ARROW, "&7Next Page ▶", List.of("&7Page " + (page + 1)), e -> open(player, page + 1, sort)));
            } else {
                gui.setItem(53, GuiButton.filler(Material.GRAY_STAINED_GLASS_PANE));
            }
            
            gui.open(player);
        });
    }

    private void sortOrders(List<Order> orders, SortMode sort) {
        orders.sort((o1, o2) -> {
            switch (sort) {
                case HIGHEST_PAYOUT:
                    return Double.compare(o2.getPriceRemaining(), o1.getPriceRemaining());
                case LOWEST_FILLED:
                    double p1 = (double) o1.getAmountFilled() / o1.getAmountNeeded();
                    double p2 = (double) o2.getAmountFilled() / o2.getAmountNeeded();
                    return Double.compare(p1, p2);
                case NEWEST:
                    return Long.compare(o2.getPostedAt(), o1.getPostedAt());
                default:
                    return 0;
            }
        });
    }

    private void openFulfillConfirm(Player player, Order order, int playerHas, int previousPage, SortMode sort) {
        Gui gui = new Gui("&8▶ &e&lConfirm Order Fill &8◀", 3);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);
        
        int fillAmount = Math.min(playerHas, order.getAmountRemaining());
        double earnAmount = fillAmount * order.getPricePerItem();
        
        gui.setItem(4, GuiButton.of(order.getMaterial(), "&f" + OrderManager.formatMaterialName(order.getMaterial()), List.of(
            "&7Order ID: &f" + order.getId(),
            "&7Poster: &f" + order.getPosterName(),
            "&7Need: &a" + order.getAmountRemaining()
        ), e -> {}));
        
        gui.setItem(11, GuiButton.of(Material.GREEN_STAINED_GLASS_PANE, "&a&l✓ Confirm", List.of(
            "&7You will give: &f" + fillAmount + "x " + OrderManager.formatMaterialName(order.getMaterial()),
            "&7You will earn: &a" + plugin.getEconomyManager().format(earnAmount),
            "&8(items removed from inventory)"
        ), e -> {
            player.closeInventory();
            orderManager.fillOrder(order.getId(), player, fillAmount, money -> {
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                String prefix = config.getString("messages.prefix", "&8[&6Orders&8] &r");
                String msg = config.getString("messages.filled", "&aYou fulfilled {amount}x {item} and earned &e{money}&a!")
                        .replace("{amount}", String.valueOf(fillAmount))
                        .replace("{item}", OrderManager.formatMaterialName(order.getMaterial()))
                        .replace("{money}", plugin.getEconomyManager().format(money));
                TextUtil.sendMessage(player, prefix + msg);
                open(player, previousPage, sort);
            }, () -> {
                TextUtil.sendMessage(player, "&cOrder fill failed. It may have expired or been cancelled.");
                open(player, previousPage, sort);
            });
        }));
        
        gui.setItem(13, GuiButton.filler(Material.GRAY_STAINED_GLASS_PANE));
        gui.setItem(15, GuiButton.of(Material.RED_STAINED_GLASS_PANE, "&c&l✗ Cancel", List.of(), e -> open(player, previousPage, sort)));
        
        gui.open(player);
    }

    private void openItemAction(Player player, Order order, int playerHas, int previousPage, SortMode sort) {
        Gui gui = new Gui("&8▶ &e&lItem Order #" + order.getId() + " &8◀", 3);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        int amountRemaining = order.getAmountRemaining();
        gui.setItem(4, GuiButton.of(order.getMaterial(), "&f" + order.getDisplayTitle(), List.of(
            "&8&m────────────────────",
            "&7Order ID:   &f#" + order.getId(),
            "&7Posted by:  &f" + order.getPosterName(),
            "&7Needs:      &a" + amountRemaining + "x &7" + order.getDisplayTitle(),
            "&7Pay / item: &a" + plugin.getEconomyManager().format(order.getPricePerItem()),
            "&7Remaining:  &a" + plugin.getEconomyManager().format(order.getPriceRemaining()),
            "&7Expires:    &e" + order.getTimeLeftString(),
            "&8&m────────────────────",
            "&aYou have:   &f" + playerHas + " &7in inventory"
        ), e -> {}));

        // Deliver Button (Slot 11)
        if (playerHas > 0) {
            int deliverAmt = Math.min(playerHas, amountRemaining);
            double deliverEarn = deliverAmt * order.getPricePerItem();
            gui.setItem(11, GuiButton.of(Material.GREEN_STAINED_GLASS_PANE, "&a&l✓ Deliver Items", List.of(
                "&8&m────────────────────",
                "&7You will deliver: &f" + deliverAmt + "x &7" + order.getDisplayTitle(),
                "&7You will earn:    &a" + plugin.getEconomyManager().format(deliverEarn),
                "&8&m────────────────────",
                deliverAmt >= amountRemaining
                    ? "&a▶ Click to fulfill COMPLETELY"
                    : "&e▶ Click to deliver partial amount"
            ), e -> openFulfillConfirm(player, order, playerHas, previousPage, sort)));
        } else {
            gui.setItem(11, GuiButton.of(Material.BARRIER, "&c&l✗ No Items in Inventory", List.of(
                "&8&m────────────────────",
                "&7You have &c0x &7" + order.getDisplayTitle() + " in your inventory.",
                "&7You cannot deliver right now.",
                "&8&m────────────────────",
                "&e(You can still Bargain / Place a Bid below!)"
            ), e -> {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
                TextUtil.sendMessage(player, "&cYou do not have any " + order.getDisplayTitle() + " to deliver! Use Bargain to propose a price.");
            }));
        }

        // Bargain / Bid Button (Slot 15)
        gui.setItem(15, GuiButton.of(Material.GOLD_BLOCK, "&6&l✎ Bargain / Place Bid", List.of(
            "&8&m────────────────────",
            "&7Current Price / item: &a" + plugin.getEconomyManager().format(order.getPricePerItem()),
            "&7Total Needed:        &f" + amountRemaining + " items",
            "&8&m────────────────────",
            "&7Propose your own price or total",
            "&7payout to &e" + order.getPosterName() + "&7.",
            "&8&m────────────────────",
            "&6▶ Click to place a BID / COUNTER-OFFER"
        ), e -> {
            if (bidGUI != null) {
                bidGUI.openPlaceBid(player, order);
            } else {
                TextUtil.sendMessage(player, "&cBidding system is currently unavailable.");
            }
        }));

        gui.setItem(22, GuiButton.of(Material.ARROW, "&7◀ Back to Board", List.of(), e -> open(player, previousPage, sort)));

        gui.open(player);
    }

    private void openServiceAction(Player player, Order order, int previousPage, SortMode sort) {
        Gui gui = new Gui("&8▶ &b&lService Order #" + order.getId() + " &8◀", 3);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        String desc = order.getDescription() != null ? order.getDescription() : "Service request";
        gui.setItem(4, GuiButton.of(order.getDisplayMaterial(), "&b" + order.getDisplayTitle(), List.of(
            "&8&m────────────────────",
            "&7Order ID:  &f#" + order.getId(),
            "&7Posted by: &f" + order.getPosterName(),
            "&7Service:   &f" + desc,
            "&7Reward:    &a" + plugin.getEconomyManager().format(order.getPriceTotal()),
            "&7Expires:   &e" + order.getTimeLeftString(),
            "&8&m────────────────────"
        ), e -> {}));

        // Accept button (instant lock at listed price)
        gui.setItem(11, GuiButton.of(Material.GREEN_STAINED_GLASS_PANE, "&a&l✓ Accept at Listed Price", List.of(
            "&8&m────────────────────",
            "&7Reward: &a" + plugin.getEconomyManager().format(order.getPriceTotal()),
            "&8&m────────────────────",
            "&7Locks this service deal immediately.",
            "&7You become the designated contractor!",
            "&8&m────────────────────",
            "&a▶ Click to ACCEPT contract"
        ), e -> {
            player.closeInventory();
            orderManager.acceptServiceOrder(order.getId(), player.getUniqueId(), player.getName(), () -> {
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                String prefix = config.getString("messages.prefix", "&8[&6Orders&8] &r");
                TextUtil.sendMessage(player, prefix + "&aYou accepted service order &f#" + order.getId() + "&a! Complete the task and contact &e" + order.getPosterName() + "&a.");
                open(player, previousPage, sort);
            }, () -> {
                TextUtil.sendMessage(player, "&cCould not accept service order. It may have already been taken or cancelled.");
                open(player, previousPage, sort);
            });
        }));

        // Bargain / Counter-offer button
        gui.setItem(15, GuiButton.of(Material.GOLD_BLOCK, "&6&l✎ Bargain / Counter-Offer", List.of(
            "&8&m────────────────────",
            "&7Current Listed Reward: &a" + plugin.getEconomyManager().format(order.getPriceTotal()),
            "&8&m────────────────────",
            "&7Propose your own price or",
            "&7negotiate terms with &e" + order.getPosterName() + "&7.",
            "&8&m────────────────────",
            "&6▶ Click to place a BID / COUNTER-OFFER"
        ), e -> {
            if (bidGUI != null) {
                bidGUI.openPlaceBid(player, order);
            } else {
                TextUtil.sendMessage(player, "&cBidding system is currently unavailable.");
            }
        }));

        gui.setItem(22, GuiButton.of(Material.ARROW, "&7◀ Back to Board", List.of(), e -> open(player, previousPage, sort)));

        gui.open(player);
    }

    public void openCategoryFilterPicker(Player player, int previousPage, SortMode sort, List<Order> allOrders) {
        Gui gui = new Gui("&8▶ &6&lFilter by Category &8◀", 5);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        CategoryFilter currentFilter = playerFilter.getOrDefault(player.getUniqueId(), CategoryFilter.ALL);

        // Header info item at slot 4
        gui.setItem(4, GuiButton.of(Material.PAPER, "&6&lFilter Orders by Category", List.of(
            "&8&m────────────────────",
            "&7Select a category to view only",
            "&7orders requesting that item type.",
            "&8&m────────────────────",
            "&7Active Filter: &e" + currentFilter.getDisplayName(),
            "&7Total Active:  &a" + allOrders.size() + " orders"
        ), e -> {}));

        // Row 2 (slots 11 to 15): 5 primary categories
        CategoryFilter[] row1 = {
            CategoryFilter.ALL,
            CategoryFilter.BUILDING,
            CategoryFilter.RESOURCES,
            CategoryFilter.COMBAT,
            CategoryFilter.FARMING
        };
        int[] row1Slots = {11, 12, 13, 14, 15};

        for (int i = 0; i < row1.length; i++) {
            CategoryFilter f = row1[i];
            long matchCount = allOrders.stream().filter(o -> matchesFilter(o, f)).count();
            boolean isSelected = (f == currentFilter);
            List<String> lore = new ArrayList<>();
            lore.add("&8&m────────────────────");
            lore.add("&7Matching Orders: &a" + matchCount);
            if (isSelected) {
                lore.add("&a✔ CURRENTLY ACTIVE");
            }
            lore.add("&8&m────────────────────");
            lore.add("&e▶ Click to filter board");

            gui.setItem(row1Slots[i], GuiButton.of(f.getIcon(), f.getTitle(), lore, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                playerFilter.put(player.getUniqueId(), f);
                open(player, 1, sort);
            }));
        }

        // Row 3 (slots 20 to 24): 5 secondary categories
        CategoryFilter[] row2 = {
            CategoryFilter.MOBS,
            CategoryFilter.DECORATION,
            CategoryFilter.MAGIC,
            CategoryFilter.STORAGE,
            CategoryFilter.MISC
        };
        int[] row2Slots = {20, 21, 22, 23, 24};

        for (int i = 0; i < row2.length; i++) {
            CategoryFilter f = row2[i];
            long matchCount = allOrders.stream().filter(o -> matchesFilter(o, f)).count();
            boolean isSelected = (f == currentFilter);
            List<String> lore = new ArrayList<>();
            lore.add("&8&m────────────────────");
            lore.add("&7Matching Orders: &a" + matchCount);
            if (isSelected) {
                lore.add("&a✔ CURRENTLY ACTIVE");
            }
            lore.add("&8&m────────────────────");
            lore.add("&e▶ Click to filter board");

            gui.setItem(row2Slots[i], GuiButton.of(f.getIcon(), f.getTitle(), lore, e -> {
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
                playerFilter.put(player.getUniqueId(), f);
                open(player, 1, sort);
            }));
        }

        // Row 4: Service Orders at slot 31
        CategoryFilter servFilter = CategoryFilter.SERVICES;
        long servCount = allOrders.stream().filter(o -> matchesFilter(o, servFilter)).count();
        boolean isServSelected = (servFilter == currentFilter);
        List<String> servLore = new ArrayList<>();
        servLore.add("&8&m────────────────────");
        servLore.add("&7Matching Orders: &a" + servCount);
        if (isServSelected) {
            servLore.add("&a✔ CURRENTLY ACTIVE");
        }
        servLore.add("&8&m────────────────────");
        servLore.add("&e▶ Click to filter board");

        gui.setItem(31, GuiButton.of(servFilter.getIcon(), servFilter.getTitle(), servLore, e -> {
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1f);
            playerFilter.put(player.getUniqueId(), servFilter);
            open(player, 1, sort);
        }));

        // Row 5: Back to Board at slot 36, Close at slot 40
        gui.setItem(36, GuiButton.of(Material.ARROW, "&7◀ Back to Board", List.of(), e -> open(player, previousPage, sort)));
        gui.setItem(40, GuiButton.of(Material.BARRIER, "&c&lClose", List.of("&7Close the menu"), e -> player.closeInventory()));

        gui.open(player);
    }
}
