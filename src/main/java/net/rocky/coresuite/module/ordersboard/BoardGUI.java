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

    public void open(Player player, int page, SortMode sort) {
        orderManager.loadActiveOrders(orders -> {
            sortOrders(orders, sort);
            
            Gui gui = new Gui("&8▶ &6&l📋 Orders Board &8◀", 6);
            gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);
            
            int activeCount = orders.size();
            int playerActive = orderManager.getPlayerActiveOrderCount(player.getUniqueId());
            int maxOrders = config.getInt("max-orders-per-player", 3);
            
            gui.setItem(4, GuiButton.of(Material.PAPER, "&6&lOrders Board", List.of(
                    "&7Total Active: &a" + activeCount,
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

                    if (isAccepted) {
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
                        } else if (isPoster) {
                            lore.add("&e(Deal locked with &f" + acceptor + "&e)");
                            lore.add("&7Waiting for them to deliver items.");
                        } else {
                            lore.add("&e(Reserved for &f" + acceptor + "&e)");
                            lore.add("&8(Deal locked — awaiting delivery)");
                        }
                    } else {
                        if (playerHas == 0) {
                            lore.add("&c✗ You have none of this item");
                        } else {
                            lore.add("&aYou have: &f" + playerHas + " &7in inventory");
                            lore.add(playerHas >= order.getAmountRemaining()
                                ? "&a▶ Click to fill COMPLETELY (" + plugin.getEconomyManager().format(order.getPriceRemaining()) + ")"
                                : "&e▶ Click to partially fill (+&a" + plugin.getEconomyManager().format(playerHas * order.getPricePerItem()) + "&e)");
                        }
                        lore.add("&6▶ Right-Click to PLACE BID / BARGAIN");
                    }

                    final int finalPlayerHas = playerHas;
                    gui.setItem(ITEM_SLOTS[slotIdx++], GuiButton.of(order.getDisplayMaterial(),
                        "&f" + order.getDisplayTitle(), lore,
                        e -> {
                            if (e.isRightClick()) {
                                if (order.getPosterUuid().equals(player.getUniqueId().toString())) {
                                    TextUtil.sendMessage(player, "&8[&6Orders&8] &cYou cannot bid on your own order.");
                                    return;
                                }
                                if (order.getStatus() == Order.Status.ACCEPTED) {
                                    String acceptor = order.getAcceptedByName() != null ? order.getAcceptedByName() : "another player";
                                    TextUtil.sendMessage(player, "&8[&6Orders&8] &cThis order has already been accepted by &f" + acceptor + "&c.");
                                    return;
                                }
                                if (bidGUI != null) bidGUI.openPlaceBid(player, order);
                                return;
                            }
                            if (isAccepted && !isContractor) {
                                String acceptor = order.getAcceptedByName() != null ? order.getAcceptedByName() : "another player";
                                TextUtil.sendMessage(player, "&8[&6Orders&8] &cThis order deal is reserved for &f" + acceptor + "&c.");
                                return;
                            }
                            if (finalPlayerHas == 0) {
                                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 1f);
                                TextUtil.sendMessage(player, "&cYou don't have any " + order.getDisplayTitle() + "!");
                            } else {
                                openFulfillConfirm(player, order, finalPlayerHas, page, sort);
                            }
                        }
                    ));
                } else {
                    // ── SERVICE order ────────────────────────────────────────
                    String desc = order.getDescription() != null ? order.getDescription() : "Service request";
                    lore.add("&7Request: &f" + desc);
                    lore.add("&7Reward:  &a" + plugin.getEconomyManager().format(order.getPriceTotal()));
                    lore.add("&8&m────────────────────");
                    if (order.getStatus() == Order.Status.ACCEPTED && order.getAcceptedByName() != null) {
                        lore.add("&a✓ Accepted by: &f" + order.getAcceptedByName());
                        lore.add("&8(Deal locked — awaiting completion)");
                    } else if (order.getPosterUuid().equals(player.getUniqueId().toString())) {
                        lore.add("&7This is your own order");
                    } else {
                        lore.add("&b▶ Left-Click to ACCEPT this service");
                        lore.add("&6▶ Right-Click to COUNTER-OFFER / BID");
                        lore.add("&7You'll earn &a" + plugin.getEconomyManager().format(order.getPriceTotal()) + " &7on completion");
                    }
                    gui.setItem(ITEM_SLOTS[slotIdx++], GuiButton.of(order.getDisplayMaterial(),
                        "&b" + order.getDisplayTitle(), lore,
                        e -> {
                            if (e.isRightClick()) {
                                if (order.getPosterUuid().equals(player.getUniqueId().toString())) {
                                    TextUtil.sendMessage(player, "&8[&6Orders&8] &cYou cannot bid on your own order.");
                                    return;
                                }
                                if (order.getStatus() == Order.Status.ACCEPTED) {
                                    String acceptor = order.getAcceptedByName() != null ? order.getAcceptedByName() : "another player";
                                    TextUtil.sendMessage(player, "&8[&6Orders&8] &cThis order has already been accepted by &f" + acceptor + "&c.");
                                    return;
                                }
                                if (bidGUI != null) bidGUI.openPlaceBid(player, order);
                                return;
                            }
                            if (order.getStatus() != Order.Status.OPEN) {
                                TextUtil.sendMessage(player, "&8[&6Orders&8] &cThis service has already been accepted.");
                                return;
                            }
                            if (order.getPosterUuid().equals(player.getUniqueId().toString())) {
                                TextUtil.sendMessage(player, "&8[&6Orders&8] &cYou can't accept your own order.");
                                return;
                            }
                            orderManager.acceptServiceOrder(order.getId(), player.getUniqueId(), player.getName(),
                                () -> {
                                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                                    String prefix = config.getString("messages.prefix", "&8[&6Orders&8] &r");
                                    TextUtil.sendMessage(player, prefix + "&bYou accepted the service order from &f" + order.getPosterName()
                                        + "&b! Complete the task then they will mark it done.");
                                    // Notify poster if online
                                    org.bukkit.entity.Player poster = org.bukkit.Bukkit.getPlayer(java.util.UUID.fromString(order.getPosterUuid()));
                                    if (poster != null) TextUtil.sendMessage(poster, prefix + "&f" + player.getName() + " &aaccepted your service order! They will complete: &7" + desc);
                                    open(player, page, sort);
                                },
                                () -> TextUtil.sendMessage(player, "&8[&6Orders&8] &cFailed — order may have just been taken.")
                            );
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
            
            for (int i = 48; i <= 51; i++) {
                gui.setItem(i, GuiButton.filler(Material.GRAY_STAINED_GLASS_PANE));
            }
            
            if (playerActive >= maxOrders) {
                gui.setItem(52, GuiButton.of(Material.BARRIER, "&c&lOrder Limit Reached", List.of("&7You can't post any more orders."), e -> {}));
            } else {
                int feePct = config.getInt("posting-fee-percent", 5);
                gui.setItem(52, GuiButton.of(Material.EMERALD, "&a&l+ Post New Order", List.of(
                    "&7Fee: &e" + feePct + "%",
                    "&7Max: &e" + maxOrders,
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
}
