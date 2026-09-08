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
                lore.add("&c▶ Shift+Click to CANCEL order (refund)");

                gui.setItem(ITEM_SLOTS[slotIdx++], GuiButton.of(order.getDisplayMaterial(), "&f" + order.getDisplayTitle(), lore, e -> {
                    if (e.isShiftClick()) {
                        openCancelConfirm(player, order, page);
                    } else if (order.isServiceOrder() && order.getStatus() == Order.Status.ACCEPTED) {
                        // Complete service order
                        orderManager.completeServiceOrder(order.getId(), player.getUniqueId(), () -> {
                            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                            String prefix = config.getString("messages.prefix", "&8[&6Orders&8] &r");
                            TextUtil.sendMessage(player, prefix + "&aService marked completed! Escrow paid to &e" + order.getAcceptedByName() + "&a.");
                            open(player, page);
                        }, () -> {
                            TextUtil.sendMessage(player, "&cCould not complete order.");
                        });
                    }
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

    private void openCancelConfirm(Player player, Order order, int page) {
        Gui gui = new Gui("&8▶ &c&lCancel Order? &8◀", 3);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        double refundAmount = order.getPriceRemaining();
        
        gui.setItem(11, GuiButton.of(Material.RED_STAINED_GLASS_PANE, "&c&lYes, Cancel", List.of(
            "&7Refund: &a" + plugin.getEconomyManager().format(refundAmount)
        ), e -> {
            player.closeInventory();
            orderManager.cancelOrder(order.getId(), player.getUniqueId(), refund -> {
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                String prefix = config.getString("messages.prefix", "&8[&6Orders&8] &r");
                String msg = config.getString("messages.cancelled", "&aOrder cancelled. Refunded: &e{refund}&a.")
                        .replace("{refund}", plugin.getEconomyManager().format(refund));
                TextUtil.sendMessage(player, prefix + msg);
                open(player, page);
            }, () -> {
                TextUtil.sendMessage(player, "&cCould not cancel order. It might already be cancelled or completed.");
                open(player, page);
            });
        }));

        gui.setItem(13, GuiButton.of(order.getDisplayMaterial(), "&f" + order.getDisplayTitle(), List.of("&7Order ID: " + order.getId()), e -> {}));

        gui.setItem(15, GuiButton.of(Material.GREEN_STAINED_GLASS_PANE, "&a&lKeep Order", List.of(), e -> open(player, page)));

        gui.open(player);
    }
}
