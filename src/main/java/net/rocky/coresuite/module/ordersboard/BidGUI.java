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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * GUI for placing bids (bidder view) and reviewing bids (poster view).
 *
 * <p>Bidder flow:
 * openPlaceBid(player, order) → types amount in chat → bid placed
 *
 * <p>Poster flow:
 * openMyBids(player) → list of all PENDING bids → per-bid action screen
 */
public class BidGUI {

    private static final String CTX_BID = "bid";
    private static final String CTX_COUNTER = "counter";

    private final CoreSuitePlugin plugin;
    private final BidManager bidManager;
    private final OrderManager orderManager;
    private final FileConfiguration config;

    // chat-input state
    private final Map<UUID, String>  awaitingCtx   = new ConcurrentHashMap<>();
    private final Map<UUID, Order>   awaitingOrder  = new ConcurrentHashMap<>();
    private final Map<UUID, BidManager.Bid> awaitingBid = new ConcurrentHashMap<>();

    public BidGUI(CoreSuitePlugin plugin, BidManager bidManager, OrderManager orderManager, FileConfiguration config) {
        this.plugin       = plugin;
        this.bidManager   = bidManager;
        this.orderManager = orderManager;
        this.config       = config;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BIDDER — Place a bid
    // ─────────────────────────────────────────────────────────────────────────

    /** Opens bid-placement UI for the given order. */
    public void openPlaceBid(Player player, Order order) {
        Gui gui = new Gui("&8\u25b6 &6&lPlace a Bid &8\u25c0", 3);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        String prefix = config.getString("messages.prefix", "&8[&6Orders&8] &r");

        if (order.getStatus() == Order.Status.ACCEPTED) {
            String acceptor = order.getAcceptedByName() != null ? order.getAcceptedByName() : "another player";
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            TextUtil.sendMessage(player, prefix + "&cThis order has already been accepted by &f" + acceptor + "&c.");
            return;
        }
        if (!order.isActive() || order.isExpired()) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
            TextUtil.sendMessage(player, prefix + "&cThis order is no longer active.");
            return;
        }

        // Current order info
        String typeInfo = order.isItemOrder()
            ? "x" + order.getAmountRemaining() + " " + order.getDisplayTitle()
            : order.getDescription();
        double currentPrice = order.getPriceTotal();

        gui.setItem(4, GuiButton.of(order.getDisplayMaterial(), "&f" + order.getDisplayTitle(),
            List.of(
                "&8&m\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500",
                "&7Posted by: &f" + order.getPosterName(),
                order.isItemOrder()
                    ? "&7Needs: &f" + order.getAmountRemaining() + " items"
                    : "&7Service: &f" + order.getDescription(),
                "&7Current price: &a" + plugin.getEconomyManager().format(currentPrice),
                "&8&m\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500",
                "&7Place a bid with a different price.",
                "&7If accepted, the new price applies.",
                "&7For service orders: bid higher to",
                "&7 attract the poster's attention."
            ), e -> {}));

        // Preset bid buttons: +10%, +25%, +50%, -10%, -25%, custom
        double[] presets = {currentPrice * 1.10, currentPrice * 1.25, currentPrice * 1.50,
                            currentPrice * 0.90, currentPrice * 0.75};
        String[] presetLabels = {"&a+10%", "&a+25%", "&a+50%", "&c-10%", "&c-25%"};
        int[] presetSlots = {10, 11, 12, 14, 15};
        Material[] presetMats = {Material.LIME_STAINED_GLASS_PANE, Material.LIME_STAINED_GLASS_PANE,
                                  Material.LIME_STAINED_GLASS_PANE, Material.RED_STAINED_GLASS_PANE,
                                  Material.RED_STAINED_GLASS_PANE};

        for (int i = 0; i < presets.length; i++) {
            final double bidAmt = Math.max(1, Math.round(presets[i]));
            gui.setItem(presetSlots[i], GuiButton.of(presetMats[i],
                presetLabels[i] + " &f" + plugin.getEconomyManager().format(bidAmt),
                List.of(
                    "&7Bid: &a" + plugin.getEconomyManager().format(bidAmt),
                    "&e\u25b6 Click to place this bid"
                ),
                e -> {
                    player.closeInventory();
                    bidManager.placeBid(order, player, bidAmt,
                        () -> TextUtil.sendMessage(player, prefix + "&aBid of &e" + plugin.getEconomyManager().format(bidAmt) + " &aplaced! Poster will be notified."),
                        () -> TextUtil.sendMessage(player, prefix + "&cFailed to place bid.")
                    );
                }));
        }

        // Custom amount
        gui.setItem(13, GuiButton.of(Material.GOLD_INGOT, "&e\u270e Custom Amount",
            List.of(
                "&7Type any amount in chat",
                "&e\u25b6 Click to enter custom bid"
            ),
            e -> {
                player.closeInventory();
                awaitingCtx.put(player.getUniqueId(), CTX_BID);
                awaitingOrder.put(player.getUniqueId(), order);
                TextUtil.sendMessage(player, prefix + "&eEnter your bid amount in chat &8(or type &ccancel&8):");
            }));

        gui.setItem(22, GuiButton.of(Material.ARROW, "&7\u25c0 Cancel", List.of(), e -> player.closeInventory()));

        gui.open(player);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POSTER — View & manage incoming bids
    // ─────────────────────────────────────────────────────────────────────────

    /** Opens a list of all pending bids on the poster's orders. */
    public void openMyBids(Player player) {
        String prefix = config.getString("messages.prefix", "&8[&6Orders&8] &r");
        TextUtil.sendMessage(player, prefix + "&7Loading your bids...");

        bidManager.getBidsForPoster(player.getUniqueId().toString(), bids -> {
            Gui gui = new Gui("&8\u25b6 &6&lIncoming Bids &8\u25c0", bids.isEmpty() ? 3 : Math.min(6, 1 + (int) Math.ceil(bids.size() / 9.0) + 1));
            gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

            gui.setItem(4, GuiButton.of(Material.GOLD_INGOT, "&6&lIncoming Bids",
                List.of(
                    "&8&m\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500",
                    "&7You have &e" + bids.size() + " &7pending bid(s).",
                    "&7Click a bid to accept, counter, or reject."
                ), e -> {}));

            if (bids.isEmpty()) {
                gui.setItem(13, GuiButton.of(Material.BARRIER, "&8No Pending Bids",
                    List.of("&7No one has bid on your orders yet."), e -> {}));
            } else {
                int[] contentSlots = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34, 37,38,39,40,41,42,43};
                for (int i = 0; i < Math.min(bids.size(), contentSlots.length); i++) {
                    final BidManager.Bid bid = bids.get(i);
                    final int slot = contentSlots[i];
                    // Load the order for this bid
                    orderManager.getOrderById(bid.orderId(), order -> {
                        if (order == null) return;
                        String typeLabel = order.isItemOrder() ? order.getDisplayTitle() : "Service";
                        String timeAgo = TextUtil.formatDuration((System.currentTimeMillis() - bid.bidAt()) / 1000) + " ago";
                        List<String> lore = new ArrayList<>();
                        lore.add("&8&m\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");
                        lore.add("&7From: &f" + bid.bidderName());
                        lore.add("&7Order: &f" + typeLabel);
                        lore.add("&7Their bid: &a" + plugin.getEconomyManager().format(bid.bidAmount()));
                        lore.add("&7Current price: &e" + plugin.getEconomyManager().format(order.getPriceTotal()));
                        lore.add("&7Bid placed: &8" + timeAgo);
                        lore.add("&8&m\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500");
                        lore.add("&e\u25b6 Click to manage this bid");
                        gui.setItem(slot, GuiButton.of(Material.GOLD_INGOT,
                            "&f" + bid.bidderName() + " &7\u2192 &a" + plugin.getEconomyManager().format(bid.bidAmount()),
                            lore, e -> openBidAction(player, bid, order)));
                    });
                }
            }

            int lastRow = (gui.getRows() - 1) * 9;
            gui.setItem(lastRow + 4, GuiButton.of(Material.BARRIER, "&c&lClose", List.of(), e -> player.closeInventory()));
            gui.open(player);
        });
    }

    /** Opens the accept / counter / reject screen for a single bid. */
    private void openBidAction(Player poster, BidManager.Bid bid, Order order) {
        Gui gui = new Gui("&8\u25b6 &6Bid from &f" + bid.bidderName() + " &8\u25c0", 4);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        String prefix = config.getString("messages.prefix", "&8[&6Orders&8] &r");
        String typeLabel = order.isItemOrder() ? order.getDisplayTitle() : "Service";

        gui.setItem(4, GuiButton.of(order.getDisplayMaterial(), "&f" + typeLabel,
            List.of(
                "&8&m\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500",
                "&7Bidder: &f" + bid.bidderName(),
                "&7Their price: &a" + plugin.getEconomyManager().format(bid.bidAmount()),
                "&7Your price: &e" + plugin.getEconomyManager().format(order.getPriceTotal()),
                "&8&m\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500"
            ), e -> {}));

        // Accept
        gui.setItem(11, GuiButton.of(Material.GREEN_STAINED_GLASS_PANE, "&a&l\u2713 Accept Bid",
            List.of(
                "&7Accept &f" + bid.bidderName() + "&7's price of",
                "&a" + plugin.getEconomyManager().format(bid.bidAmount()),
                "&8&m\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500",
                "&7Order price will update to bid amount.",
                "&7Bidder will be notified.",
                "&e\u25b6 Click to accept"
            ),
            e -> {
                double diff = bid.bidAmount() - order.getPriceTotal();
                if (diff > 0 && !plugin.getEconomyManager().has(poster, diff)) {
                    poster.playSound(poster.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                    TextUtil.sendMessage(poster, prefix + "&cYou do not have enough money to accept this higher bid! You need an extra &e" + plugin.getEconomyManager().format(diff) + "&c.");
                    return;
                }
                poster.playSound(poster.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                bidManager.acceptBid(bid, order,
                    () -> {
                        TextUtil.sendMessage(poster, prefix + "&aBid accepted! Deal locked with &f" + bid.bidderName() + "&a at &e" + plugin.getEconomyManager().format(bid.bidAmount()));
                        poster.closeInventory();
                    },
                    () -> TextUtil.sendMessage(poster, prefix + "&cFailed to accept bid (order may have expired or already been accepted).")
                );
            }));

        // Counter
        gui.setItem(13, GuiButton.of(Material.ORANGE_STAINED_GLASS_PANE, "&6&l\u21c4 Counter Offer",
            List.of(
                "&7Propose a different price",
                "&7to &f" + bid.bidderName(),
                "&8&m\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500",
                "&e\u25b6 Click to type counter amount"
            ),
            e -> {
                poster.closeInventory();
                awaitingCtx.put(poster.getUniqueId(), CTX_COUNTER);
                awaitingBid.put(poster.getUniqueId(), bid);
                awaitingOrder.put(poster.getUniqueId(), order);
                TextUtil.sendMessage(poster, prefix + "&eType your counter-offer amount &8(or &ccancel&8):");
            }));

        // Reject
        gui.setItem(15, GuiButton.of(Material.RED_STAINED_GLASS_PANE, "&c&l\u2717 Reject Bid",
            List.of(
                "&7Decline &f" + bid.bidderName() + "&7's bid.",
                "&7They will be notified.",
                "&e\u25b6 Click to reject"
            ),
            e -> {
                poster.playSound(poster.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                bidManager.rejectBid(bid, order,
                    () -> {
                        TextUtil.sendMessage(poster, prefix + "&cBid rejected.");
                        poster.closeInventory();
                    }
                );
            }));

        gui.setItem(31, GuiButton.of(Material.ARROW, "&7◀ Back", List.of(), e -> openMyBids(poster)));

        gui.open(poster);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Chat handler
    // ─────────────────────────────────────────────────────────────────────────

    /** Returns true if this player's chat was consumed. */
    public boolean handleChat(Player player, String message) {
        String ctx = awaitingCtx.remove(player.getUniqueId());
        if (ctx == null) return false;

        String prefix = config.getString("messages.prefix", "&8[&6Orders&8] &r");

        if (message.equalsIgnoreCase("cancel")) {
            TextUtil.sendMessage(player, prefix + "&cCancelled.");
            awaitingOrder.remove(player.getUniqueId());
            awaitingBid.remove(player.getUniqueId());
            return true;
        }

        double amount;
        try {
            amount = Double.parseDouble(message.replace(",", ""));
        } catch (NumberFormatException e) {
            TextUtil.sendMessage(player, prefix + "&cInvalid amount.");
            awaitingOrder.remove(player.getUniqueId());
            awaitingBid.remove(player.getUniqueId());
            return true;
        }

        if (amount <= 0) {
            TextUtil.sendMessage(player, prefix + "&cAmount must be positive.");
            return true;
        }

        if (CTX_BID.equals(ctx)) {
            Order order = awaitingOrder.remove(player.getUniqueId());
            if (order == null) return true;
            final double finalAmt = amount;
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () ->
                bidManager.placeBid(order, player, finalAmt,
                    () -> TextUtil.sendMessage(player, prefix + "&aBid of &e" + plugin.getEconomyManager().format(finalAmt) + " &aplaced! Poster will be notified."),
                    () -> TextUtil.sendMessage(player, prefix + "&cFailed to place bid.")
                )
            );
        } else if (CTX_COUNTER.equals(ctx)) {
            BidManager.Bid bid = awaitingBid.remove(player.getUniqueId());
            Order order = awaitingOrder.remove(player.getUniqueId());
            if (bid == null || order == null) return true;
            final double finalAmt = amount;
            org.bukkit.Bukkit.getScheduler().runTask(plugin, () ->
                bidManager.counterBid(bid, order, player, finalAmt, () -> {})
            );
        }
        return true;
    }

    public void cleanup(UUID uuid) {
        awaitingCtx.remove(uuid);
        awaitingOrder.remove(uuid);
        awaitingBid.remove(uuid);
    }
}
