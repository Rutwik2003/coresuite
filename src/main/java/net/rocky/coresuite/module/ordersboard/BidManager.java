package net.rocky.coresuite.module.ordersboard;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Level;

/**
 * Manages counter-bids on posted orders.
 *
 * <p>Flow:
 * <ol>
 *   <li>Viewer sees an order → clicks "Place Bid" → types price in chat</li>
 *   <li>Bid saved as PENDING. Poster notified if online.</li>
 *   <li>Poster opens My Orders → Bids tab → sees all PENDING bids</li>
 *   <li>Poster can Accept (bid price replaces order price), Counter (new chat price), or Reject.</li>
 *   <li>On Accept: old escrow refunded, new escrow charged at bid price, bidder notified.</li>
 * </ol>
 */
public class BidManager {

    public enum BidStatus { PENDING, ACCEPTED, REJECTED, COUNTERED }

    public record Bid(
        int id, int orderId, String bidderUuid, String bidderName,
        double bidAmount, long bidAt, BidStatus status
    ) {}

    private final CoreSuitePlugin plugin;
    private OrderManager orderManager;

    public BidManager(CoreSuitePlugin plugin) {
        this.plugin = plugin;
    }

    public void setOrderManager(OrderManager orderManager) {
        this.orderManager = orderManager;
    }

    public void initDatabase() {
        plugin.getDatabaseManager().execute(
            "CREATE TABLE IF NOT EXISTS coresuite_order_bids (" +
            "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
            "  order_id INTEGER NOT NULL," +
            "  bidder_uuid TEXT NOT NULL," +
            "  bidder_name TEXT NOT NULL," +
            "  bid_amount REAL NOT NULL," +
            "  bid_at BIGINT NOT NULL," +
            "  status TEXT NOT NULL DEFAULT 'PENDING'" +
            ")"
        );
    }

    /** Place a new bid on an order. Notifies poster if online. */
    public void placeBid(Order order, Player bidder, double amount, Runnable onSuccess, Runnable onFail) {
        if (order.getStatus() == Order.Status.ACCEPTED) {
            Bukkit.getScheduler().runTask(plugin, onFail);
            return;
        }
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                // Verify order is still OPEN, active, and not expired in DB
                try (PreparedStatement checkOrder = conn.prepareStatement(
                        "SELECT status, active, expires_at FROM coresuite_orders WHERE id = ?")) {
                    checkOrder.setInt(1, order.getId());
                    try (ResultSet rs = checkOrder.executeQuery()) {
                        if (!rs.next() || rs.getInt("active") != 1 || rs.getLong("expires_at") <= System.currentTimeMillis() || !"OPEN".equalsIgnoreCase(rs.getString("status"))) {
                            Bukkit.getScheduler().runTask(plugin, onFail);
                            return;
                        }
                    }
                }

                // Check if bidder already has a PENDING bid on this order
                int existingBidId = -1;
                try (PreparedStatement checkBid = conn.prepareStatement(
                        "SELECT id FROM coresuite_order_bids WHERE order_id = ? AND bidder_uuid = ? AND status = 'PENDING'")) {
                    checkBid.setInt(1, order.getId());
                    checkBid.setString(2, bidder.getUniqueId().toString());
                    try (ResultSet rs = checkBid.executeQuery()) {
                        if (rs.next()) existingBidId = rs.getInt("id");
                    }
                }

                if (existingBidId > 0) {
                    try (PreparedStatement updateBid = conn.prepareStatement(
                            "UPDATE coresuite_order_bids SET bid_amount = ?, bid_at = ? WHERE id = ?")) {
                        updateBid.setDouble(1, amount);
                        updateBid.setLong(2, System.currentTimeMillis());
                        updateBid.setInt(3, existingBidId);
                        updateBid.executeUpdate();
                    }
                } else {
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "INSERT INTO coresuite_order_bids (order_id, bidder_uuid, bidder_name, bid_amount, bid_at, status) " +
                            "VALUES (?, ?, ?, ?, ?, 'PENDING')",
                            Statement.RETURN_GENERATED_KEYS)) {
                        stmt.setInt(1, order.getId());
                        stmt.setString(2, bidder.getUniqueId().toString());
                        stmt.setString(3, bidder.getName());
                        stmt.setDouble(4, amount);
                        stmt.setLong(5, System.currentTimeMillis());
                        stmt.executeUpdate();
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to place bid", e);
                Bukkit.getScheduler().runTask(plugin, onFail);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                onSuccess.run();
                // Notify poster if online
                Player poster = Bukkit.getPlayer(UUID.fromString(order.getPosterUuid()));
                if (poster != null) {
                    String type = order.isItemOrder() ? order.getDisplayTitle() : "service";
                    TextUtil.sendMessage(poster,
                        "&8[&6Orders&8] &f" + bidder.getName() + " &7placed a bid of &a" +
                        plugin.getEconomyManager().format(amount) + " &7on your &e" + type +
                        " &7order! &8(Open &e/orders \u2192 My Orders \u2192 Bids&8)");
                }
            });
        });
    }

    /** Load all PENDING bids for a specific order (async). */
    public void getBidsForOrder(int orderId, Consumer<List<Bid>> callback) {
        plugin.getDatabaseManager().executeAsync(() -> {
            List<Bid> bids = new ArrayList<>();
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "SELECT * FROM coresuite_order_bids WHERE order_id = ? AND status = 'PENDING' ORDER BY bid_at DESC")) {
                stmt.setInt(1, orderId);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) bids.add(mapBid(rs));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load bids for order " + orderId, e);
            }
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(bids));
        });
    }

    /** Load all PENDING bids across orders posted by a specific player (for My Orders view). */
    public void getBidsForPoster(String posterUuid, Consumer<List<Bid>> callback) {
        plugin.getDatabaseManager().executeAsync(() -> {
            List<Bid> bids = new ArrayList<>();
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "SELECT b.* FROM coresuite_order_bids b " +
                     "JOIN coresuite_orders o ON b.order_id = o.id " +
                     "WHERE o.poster_uuid = ? AND b.status = 'PENDING' " +
                     "ORDER BY b.bid_at DESC LIMIT 27")) {
                stmt.setString(1, posterUuid);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) bids.add(mapBid(rs));
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load bids for poster", e);
            }
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(bids));
        });
    }

    /**
     * Accept a bid: updates bid status, updates order price, persists contractor & ACCEPTED status, notifies bidder.
     */
    public void acceptBid(Bid bid, Order order, Runnable onSuccess, Runnable onFail) {
        UUID posterUuid = UUID.fromString(order.getPosterUuid());
        Player onlinePoster = Bukkit.getPlayer(posterUuid);
        OfflinePlayer poster = (onlinePoster != null) ? onlinePoster : Bukkit.getOfflinePlayer(posterUuid);
        double diff = bid.bidAmount() - order.getPriceTotal();

        // 1. If negotiated price is HIGHER, withdraw difference upfront synchronously on main thread
        if (diff > 0) {
            if (!plugin.getEconomyManager().has(poster, diff)) {
                if (onlinePoster != null) {
                    onlinePoster.playSound(onlinePoster.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                    TextUtil.sendMessage(onlinePoster, "&8[&6Orders&8] &cYou do not have enough money to accept this higher bid! You need an extra &e" + plugin.getEconomyManager().format(diff) + "&c.");
                }
                Bukkit.getScheduler().runTask(plugin, onFail);
                return;
            }
            boolean withdrawn = plugin.getEconomyManager().withdraw(poster, diff);
            if (!withdrawn) {
                if (onlinePoster != null) {
                    onlinePoster.playSound(onlinePoster.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.5f);
                    TextUtil.sendMessage(onlinePoster, "&8[&6Orders&8] &cTransaction failed: could not withdraw &e" + plugin.getEconomyManager().format(diff) + " &cfor escrow adjustment.");
                }
                Bukkit.getScheduler().runTask(plugin, onFail);
                return;
            }
            if (onlinePoster != null) {
                TextUtil.sendMessage(onlinePoster, "&8[&6Orders&8] &c-" + plugin.getEconomyManager().format(diff) + " &7withdrawn from your balance and added to order escrow.");
            }
        }

        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                conn.setAutoCommit(false);
                try {
                    // Double check order is still active and OPEN in DB
                    try (PreparedStatement checkStmt = conn.prepareStatement(
                            "SELECT status, active FROM coresuite_orders WHERE id = ?")) {
                        checkStmt.setInt(1, order.getId());
                        try (ResultSet rs = checkStmt.executeQuery()) {
                            if (!rs.next() || rs.getInt("active") != 1 || !"OPEN".equalsIgnoreCase(rs.getString("status"))) {
                                conn.rollback();
                                if (diff > 0) {
                                    Bukkit.getScheduler().runTask(plugin, () -> plugin.getEconomyManager().deposit(poster, diff));
                                }
                                Bukkit.getScheduler().runTask(plugin, onFail);
                                return;
                            }
                        }
                    }

                    // Mark bid as ACCEPTED
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "UPDATE coresuite_order_bids SET status = 'ACCEPTED' WHERE id = ?")) {
                        stmt.setInt(1, bid.id());
                        stmt.executeUpdate();
                    }
                    // Reject all other PENDING bids on this order
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "UPDATE coresuite_order_bids SET status = 'REJECTED' WHERE order_id = ? AND id != ? AND status = 'PENDING'")) {
                        stmt.setInt(1, bid.orderId());
                        stmt.setInt(2, bid.id());
                        stmt.executeUpdate();
                    }
                    // Update order price_total, price_per_item, status = 'ACCEPTED', accepted_by_uuid, accepted_by_name
                    double newPricePerItem = order.getAmountNeeded() > 0
                        ? bid.bidAmount() / order.getAmountNeeded()
                        : bid.bidAmount();
                    try (PreparedStatement stmt = conn.prepareStatement(
                            "UPDATE coresuite_orders SET price_total = ?, price_per_item = ?, status = 'ACCEPTED', accepted_by_uuid = ?, accepted_by_name = ? WHERE id = ?")) {
                        stmt.setDouble(1, bid.bidAmount());
                        stmt.setDouble(2, newPricePerItem);
                        stmt.setString(3, bid.bidderUuid());
                        stmt.setString(4, bid.bidderName());
                        stmt.setInt(5, bid.orderId());
                        stmt.executeUpdate();
                    }
                    conn.commit();
                } catch (SQLException ex) {
                    conn.rollback();
                    if (diff > 0) {
                        Bukkit.getScheduler().runTask(plugin, () -> plugin.getEconomyManager().deposit(poster, diff));
                    }
                    throw ex;
                } finally {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to accept bid", e);
                if (diff > 0) {
                    Bukkit.getScheduler().runTask(plugin, () -> plugin.getEconomyManager().deposit(poster, diff));
                }
                Bukkit.getScheduler().runTask(plugin, onFail);
                return;
            }

            Bukkit.getScheduler().runTask(plugin, () -> {
                // If negotiated price was LOWER, refund excess escrow to poster
                if (diff < 0) {
                    plugin.getEconomyManager().deposit(poster, -diff);
                    if (onlinePoster != null) {
                        TextUtil.sendMessage(onlinePoster, "&8[&6Orders&8] &a+" + plugin.getEconomyManager().format(-diff) + " &7excess escrow refunded to your balance!");
                    }
                }

                // Update in-memory order object
                order.setStatus(Order.Status.ACCEPTED);
                order.setAcceptedBy(bid.bidderUuid(), bid.bidderName());
                double newPricePerItem = order.getAmountNeeded() > 0
                    ? bid.bidAmount() / order.getAmountNeeded()
                    : bid.bidAmount();
                order.setPriceTotal(bid.bidAmount());
                order.setPricePerItem(newPricePerItem);

                onSuccess.run();

                // Notify bidder
                String type = order.isItemOrder() ? order.getDisplayTitle() : "service";
                String msg = "&8[&6Orders&8] &a\u2713 Your bid of &e" + plugin.getEconomyManager().format(bid.bidAmount()) +
                    " &awas accepted by &f" + order.getPosterName() + "&a on the &f" + type + " &aorder! Check &e/orders &ato fill it.";
                if (orderManager != null) {
                    orderManager.notifyPlayerOrQueue(UUID.fromString(bid.bidderUuid()), msg);
                } else {
                    Player bidder = Bukkit.getPlayer(UUID.fromString(bid.bidderUuid()));
                    if (bidder != null) {
                        TextUtil.sendMessage(bidder, msg);
                    }
                }
            });
        });
    }

    /** Reject a bid and notify the bidder. */
    public void rejectBid(Bid bid, Order order, Runnable onSuccess) {
        plugin.getDatabaseManager().execute(
            "UPDATE coresuite_order_bids SET status = 'REJECTED' WHERE id = ?",
            bid.id()
        );
        Bukkit.getScheduler().runTask(plugin, () -> {
            onSuccess.run();
            Player bidder = Bukkit.getPlayer(UUID.fromString(bid.bidderUuid()));
            if (bidder != null) {
                String type = order.isItemOrder() ? order.getDisplayTitle() : "service";
                TextUtil.sendMessage(bidder,
                    "&8[&6Orders&8] &cYour bid of &e" + plugin.getEconomyManager().format(bid.bidAmount()) +
                    " &cwas declined on the &f" + type + " &corder.");
            }
        });
    }

    /** Counter a bid: marks it COUNTERED, and sends a message to bidder with the new offered price. */
    public void counterBid(Bid bid, Order order, Player poster, double counterAmount, Runnable onSuccess) {
        plugin.getDatabaseManager().execute(
            "UPDATE coresuite_order_bids SET status = 'COUNTERED' WHERE id = ?",
            bid.id()
        );
        Bukkit.getScheduler().runTask(plugin, () -> {
            onSuccess.run();
            Player bidder = Bukkit.getPlayer(UUID.fromString(bid.bidderUuid()));
            if (bidder != null) {
                String type = order.isItemOrder() ? order.getDisplayTitle() : "service";
                TextUtil.sendMessage(bidder,
                    "&8[&6Orders&8] &e" + poster.getName() + " &7counter-offered &a" +
                    plugin.getEconomyManager().format(counterAmount) + " &7for their &f" + type +
                    " &7order (was &c" + plugin.getEconomyManager().format(bid.bidAmount()) +
                    "&7). Open &e/orders &7to view & respond.");
                // Place a new PENDING bid from the bidder's perspective with the counter amount
                // so they can accept/reject by viewing it
            }
            TextUtil.sendMessage(poster,
                "&8[&6Orders&8] &aCounter-offer sent to &f" + bid.bidderName() + "&a.");
        });
    }

    private Bid mapBid(ResultSet rs) throws SQLException {
        return new Bid(
            rs.getInt("id"),
            rs.getInt("order_id"),
            rs.getString("bidder_uuid"),
            rs.getString("bidder_name"),
            rs.getDouble("bid_amount"),
            rs.getLong("bid_at"),
            BidStatus.valueOf(rs.getString("status"))
        );
    }
}
