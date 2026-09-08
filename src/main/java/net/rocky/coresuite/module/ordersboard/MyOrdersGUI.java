package net.rocky.coresuite.module.ordersboard;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.gui.GuiButton;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class MyOrdersGUI {

    private static final int[] ITEM_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25
    };
    private static final int ITEMS_PER_PAGE = ITEM_SLOTS.length;

    private final CoreSuitePlugin plugin;
    private final OrderManager orderManager;
    private final FileConfiguration config;
    private BoardGUI boardGUI;
    private BidGUI bidGUI;

    public MyOrdersGUI(CoreSuitePlugin plugin, OrderManager orderManager, FileConfiguration config) {
        this.plugin = plugin;
        this.orderManager = orderManager;
        this.config = config;
    }

    public void setBoardGUI(BoardGUI boardGUI) {
        this.boardGUI = boardGUI;
    }

    public void setBidGUI(BidGUI bidGUI) {
        this.bidGUI = bidGUI;
    }

    public void open(Player player, int page) {
        orderManager.loadPlayerOrders(player.getUniqueId(), orders -> {
            Gui gui = new Gui("&8▶ &6&lMy Orders &8◀", 4);
            gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

            int total = orders.size();
            int totalPages = (int) Math.ceil((double) total / ITEMS_PER_PAGE);
            if (totalPages == 0) totalPages = 1;

            int startIdx = (page - 1) * ITEMS_PER_PAGE;
            int endIdx = Math.min(startIdx + ITEMS_PER_PAGE, total);

            int slotIdx = 0;
            for (int i = startIdx; i < endIdx; i++) {
                Order order = orders.get(i);
                List<String> lore = new ArrayList<>();
                lore.add("&8&m────────────────────");

                if (order.isItemOrder()) {
                    lore.add("&7Type:       &fItem Request");
                    lore.add("&7Progress:   &a" + order.getAmountFilled() + "&7/&f" + order.getAmountNeeded() + " &7items");
                    lore.add("&7Per item:   &a" + plugin.getEconomyManager().format(order.getPricePerItem()));
                    lore.add("&7Total:      &a" + plugin.getEconomyManager().format(order.getPriceTotal()));
                    lore.add("&8&m────────────────────");
                    if (order.getStatus() == Order.Status.ACCEPTED) {
                        String acceptor = order.getAcceptedByName() != null ? order.getAcceptedByName() : "Contractor";
                        lore.add("&7Status:     &a✓ Accepted by &f" + acceptor);
                        lore.add("&7Awaiting delivery of items from &f" + acceptor);
                    } else {
                        lore.add("&7Status:     &eOpen for fulfillment / bids");
                    }
                } else {
                    lore.add("&7Type:       &bService Request");
                    lore.add("&7Details:    &f" + (order.getDescription() != null ? order.getDescription() : "N/A"));
                    lore.add("&7Reward:     &a" + plugin.getEconomyManager().format(order.getPriceTotal()));
                    lore.add("&8&m────────────────────");
                    if (order.getStatus() == Order.Status.ACCEPTED) {
                        String acceptor = order.getAcceptedByName() != null ? order.getAcceptedByName() : "Contractor";
                        lore.add("&7Status:     &a✓ Accepted by &f" + acceptor);
                        lore.add("&a▶ Click to MARK COMPLETED (Pay acceptor)");
                    } else {
                        lore.add("&7Status:     &eWaiting for someone to accept");
                    }
                }

                lore.add("&7Expires:    &e" + order.getTimeLeftString());
                lore.add("&8&m────────────────────");
                lore.add("&e▶ Click to MANAGE order");

                gui.setItem(ITEM_SLOTS[slotIdx++], GuiButton.of(order.getDisplayMaterial(), "&f" + order.getDisplayTitle(), lore, e -> {
                    openManageOrder(player, order, () -> open(player, page));
                }));
            }

            gui.setItem(27, GuiButton.of(Material.ARROW, "&7◀ Back to Board", List.of(), e -> {
                if (boardGUI != null) boardGUI.open(player, 1, BoardGUI.SortMode.HIGHEST_PAYOUT);
            }));

            // Incoming Bids Button
            gui.setItem(30, GuiButton.of(Material.GOLD_INGOT, "&6&lIncoming Bids",
                List.of(
                    "&8&m────────────────────",
                    "&7View players who offered counter-bids",
                    "&7on your posted orders!",
                    "&8&m────────────────────",
                    "&e▶ Click to view incoming bids"
                ),
                e -> {
                    if (bidGUI != null) bidGUI.openMyBids(player);
                }
            ));

            gui.setItem(31, GuiButton.of(Material.PAPER, "&ePage " + page, List.of("&7Showing your active orders"), e -> {}));

            gui.setItem(35, GuiButton.of(Material.COMPASS, "&aRefresh", List.of(), e -> open(player, page)));

            if (page > 1) {
                gui.setItem(28, GuiButton.of(Material.SPECTRAL_ARROW, "&7◀ Previous Page", List.of(), e -> open(player, page - 1)));
            }
            if (page < totalPages) {
                gui.setItem(34, GuiButton.of(Material.SPECTRAL_ARROW, "&7Next Page ▶", List.of(), e -> open(player, page + 1)));
            }

            gui.open(player);
        });
    }

    public void openManageOrder(Player player, Order order, Runnable onReturn) {
        Gui gui = new Gui("&8▶ &6&lManage Order #" + order.getId() + " &8◀", 3);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        List<String> infoLore = new ArrayList<>();
        infoLore.add("&8&m────────────────────");
        infoLore.add("&7Order ID:  &f#" + order.getId());
        if (order.isItemOrder()) {
            infoLore.add("&7Type:      &fItem Request (" + order.getDisplayTitle() + ")");
            infoLore.add("&7Progress:  &a" + order.getAmountFilled() + "&7/&f" + order.getAmountNeeded() + " items");
            infoLore.add("&7Per item:  &a" + plugin.getEconomyManager().format(order.getPricePerItem()));
            infoLore.add("&7Escrow:    &a" + plugin.getEconomyManager().format(order.getPriceTotal()));
        } else {
            infoLore.add("&7Type:      &bService Request");
            infoLore.add("&7Details:   &f" + (order.getDescription() != null ? order.getDescription() : "N/A"));
            infoLore.add("&7Escrow:    &a" + plugin.getEconomyManager().format(order.getPriceTotal()));
        }
        infoLore.add("&7Expires:   &e" + order.getTimeLeftString());
        infoLore.add("&8&m────────────────────");

        boolean isAccepted = (order.getStatus() == Order.Status.ACCEPTED);
        String acceptor = order.getAcceptedByName() != null ? order.getAcceptedByName() : "Contractor";
        if (isAccepted) {
            infoLore.add("&7Status:    &a✓ Accepted by &f" + acceptor);
        } else {
            infoLore.add("&7Status:    &eOpen for bids / fulfillment");
        }
        infoLore.add("&8&m────────────────────");

        gui.setItem(4, GuiButton.of(order.getDisplayMaterial(), "&f" + order.getDisplayTitle(), infoLore, e -> {}));

        // Action Slot 11:
        if (order.isServiceOrder() && isAccepted) {
            // Complete Service & Pay Contractor
            gui.setItem(11, GuiButton.of(Material.GREEN_STAINED_GLASS_PANE, "&a&l✓ Mark Completed & Pay", List.of(
                "&8&m────────────────────",
                "&7Contractor: &f" + acceptor,
                "&7Escrow payout: &a" + plugin.getEconomyManager().format(order.getPriceTotal()),
                "&8&m────────────────────",
                "&7Has &f" + acceptor + " &7finished the service?",
                "&7Clicking this pays out the escrow to them.",
                "&8&m────────────────────",
                "&a▶ Click to MARK COMPLETED & PAY"
            ), e -> {
                player.closeInventory();
                orderManager.completeServiceOrder(order.getId(), player.getUniqueId(), () -> {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                    String prefix = config.getString("messages.prefix", "&8[&6Orders&8] &r");
                    TextUtil.sendMessage(player, prefix + "&aService marked completed! Escrow paid to &e" + acceptor + "&a.");
                    onReturn.run();
                }, () -> {
                    TextUtil.sendMessage(player, "&cCould not complete order. It may have already been completed or cancelled.");
                    onReturn.run();
                });
            }));
        } else if (!isAccepted) {
            // View Incoming Bids for Open Order
            gui.setItem(11, GuiButton.of(Material.GOLD_INGOT, "&6&l📜 View Incoming Bids", List.of(
                "&8&m────────────────────",
                "&7View players offering counter-bids",
                "&7or negotiated prices for this order.",
                "&8&m────────────────────",
                "&e▶ Click to view incoming bids"
            ), e -> {
                if (bidGUI != null) {
                    bidGUI.openMyBids(player);
                } else {
                    TextUtil.sendMessage(player, "&cBidding system is currently unavailable.");
                }
            }));
        } else {
            // Accepted Item Order - Waiting for contractor to deliver
            gui.setItem(11, GuiButton.of(Material.CLOCK, "&e&l⏳ Awaiting Delivery", List.of(
                "&8&m────────────────────",
                "&7Contractor: &f" + acceptor,
                "&7Items: &a" + order.getAmountRemaining() + "x " + order.getDisplayTitle(),
                "&8&m────────────────────",
                "&7Waiting for &f" + acceptor + " &7to deliver the",
                "&7requested items to complete the order.",
                "&8&m────────────────────",
                "&7(If they are taking too long, you may cancel)"
            ), e -> {}));
        }

        // Action Slot 15: Cancel & Refund
        double refundAmount = order.getPriceRemaining();
        gui.setItem(15, GuiButton.of(Material.RED_STAINED_GLASS_PANE, "&c&l✗ Cancel & Remove Order", List.of(
            "&8&m────────────────────",
            "&7Refund to you: &a" + plugin.getEconomyManager().format(refundAmount),
            "&8&m────────────────────",
            "&7Cancel this order, remove it from the",
            "&7board, and refund remaining escrow.",
            "&8&m────────────────────",
            "&c▶ Click to CANCEL order"
        ), e -> openCancelConfirm(player, order, () -> openManageOrder(player, order, onReturn))));

        // Slot 22: Back button
        gui.setItem(22, GuiButton.of(Material.ARROW, "&7◀ Back", List.of(), e -> onReturn.run()));

        gui.open(player);
    }

    public void openCancelConfirm(Player player, Order order, Runnable onReturn) {
        Gui gui = new Gui("&8▶ &c&lCancel Order #" + order.getId() + "? &8◀", 3);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        double refundAmount = order.getPriceRemaining();
        
        gui.setItem(11, GuiButton.of(Material.RED_STAINED_GLASS_PANE, "&c&l✓ Yes, Cancel & Remove", List.of(
            "&8&m────────────────────",
            "&7Order ID: &f#" + order.getId(),
            "&7Refund to you: &a" + plugin.getEconomyManager().format(refundAmount),
            "&8&m────────────────────",
            "&7Permanently removes this order",
            "&7and returns your escrow money.",
            "&8&m────────────────────",
            "&c▶ Click to confirm cancellation"
        ), e -> {
            player.closeInventory();
            orderManager.cancelOrder(order.getId(), player.getUniqueId(), refund -> {
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                String prefix = config.getString("messages.prefix", "&8[&6Orders&8] &r");
                String msg = config.getString("messages.cancelled", "&aOrder cancelled. Refunded: &e{refund}&a.")
                        .replace("{refund}", plugin.getEconomyManager().format(refund));
                TextUtil.sendMessage(player, prefix + msg);
                onReturn.run();
            }, () -> {
                TextUtil.sendMessage(player, "&cCould not cancel order. It might already be cancelled or completed.");
                onReturn.run();
            });
        }));

        gui.setItem(13, GuiButton.of(order.getDisplayMaterial(), "&f" + order.getDisplayTitle(), List.of(
            "&8&m────────────────────",
            "&7Order ID: &f#" + order.getId(),
            "&7Type:     &f" + (order.isItemOrder() ? "Item Request" : "Service"),
            "&7Escrow:   &a" + plugin.getEconomyManager().format(order.getPriceTotal()),
            "&7Expires:  &e" + order.getTimeLeftString(),
            "&8&m────────────────────"
        ), e -> {}));

        gui.setItem(15, GuiButton.of(Material.GREEN_STAINED_GLASS_PANE, "&a&l✗ Keep Order", List.of(
            "&8&m────────────────────",
            "&7Do not cancel this order.",
            "&7Keep it active on the board.",
            "&8&m────────────────────",
            "&e▶ Click to go back"
        ), e -> onReturn.run()));

        gui.setItem(22, GuiButton.of(Material.ARROW, "&7◀ Back", List.of(), e -> onReturn.run()));

        gui.open(player);
    }
}
