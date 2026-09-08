package net.rocky.coresuite.module.ordersboard;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;

public class OrderManager {
    private final CoreSuitePlugin plugin;
    private final FileConfiguration config;
    private final Map<UUID, Integer> activeOrderCache = new ConcurrentHashMap<>();

    public OrderManager(CoreSuitePlugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        initDatabase();
        loadCache();
    }

    private void initDatabase() {
        plugin.getDatabaseManager().execute(
                "CREATE TABLE IF NOT EXISTS coresuite_orders (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "poster_uuid TEXT NOT NULL, " +
                "poster_name TEXT NOT NULL, " +
                "order_type TEXT NOT NULL DEFAULT 'ITEM', " +
                "material TEXT, " +                         // NULL for SERVICE orders
                "description TEXT, " +                      // NULL for ITEM orders
                "amount_needed INTEGER NOT NULL DEFAULT 1, " +
                "amount_filled INTEGER NOT NULL DEFAULT 0, " +
                "price_per_item REAL NOT NULL, " +
                "price_total REAL NOT NULL, " +
                "posted_at BIGINT NOT NULL, " +
                "expires_at BIGINT NOT NULL, " +
                "active INTEGER NOT NULL DEFAULT 1, " +
                "status TEXT NOT NULL DEFAULT 'OPEN', " +
                "accepted_by_uuid TEXT, " +
                "accepted_by_name TEXT" +
                ")"
        );
        plugin.getDatabaseManager().execute(
                "CREATE TABLE IF NOT EXISTS coresuite_orders_fills (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "order_id INTEGER NOT NULL, " +
                "filler_uuid TEXT NOT NULL, " +
                "filler_name TEXT NOT NULL, " +
                "amount INTEGER NOT NULL, " +
                "money_earned REAL NOT NULL, " +
                "filled_at BIGINT NOT NULL" +
                ")"
        );
        plugin.getDatabaseManager().execute(
                "CREATE TABLE IF NOT EXISTS coresuite_orders_pending_items (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "player_uuid TEXT NOT NULL, " +
                "material TEXT NOT NULL, " +
                "amount INTEGER NOT NULL" +
                ")"
        );

        // Run automatic column migrations for existing databases
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 Statement stmt = conn.createStatement()) {
                String[] migrations = {
                    "ALTER TABLE coresuite_orders ADD COLUMN order_type TEXT NOT NULL DEFAULT 'ITEM'",
                    "ALTER TABLE coresuite_orders ADD COLUMN description TEXT",
                    "ALTER TABLE coresuite_orders ADD COLUMN status TEXT NOT NULL DEFAULT 'OPEN'",
                    "ALTER TABLE coresuite_orders ADD COLUMN accepted_by_uuid TEXT",
                    "ALTER TABLE coresuite_orders ADD COLUMN accepted_by_name TEXT"
                };
                for (String migration : migrations) {
                    try {
                        stmt.execute(migration);
                    } catch (SQLException ignored) {
                        // Column already exists or already migrated
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to run column migrations for coresuite_orders", e);
            }
        });
    }

    private void loadCache() {
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT poster_uuid, COUNT(*) as count FROM coresuite_orders WHERE active = 1 AND amount_filled < amount_needed AND expires_at > ? GROUP BY poster_uuid")) {
                stmt.setLong(1, System.currentTimeMillis());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        activeOrderCache.put(UUID.fromString(rs.getString("poster_uuid")), rs.getInt("count"));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load active orders cache", e);
            }
        });
    }

    public void clearCache() {
        activeOrderCache.clear();
    }

    private void incrementOrderCount(UUID uuid) {
        if (uuid == null) return;
        activeOrderCache.compute(uuid, (k, v) -> (v == null) ? 1 : v + 1);
    }

    private void decrementOrderCount(UUID uuid) {
        if (uuid == null) return;
        activeOrderCache.compute(uuid, (k, v) -> (v == null || v <= 1) ? null : v - 1);
    }

    public void loadActiveOrders(Consumer<List<Order>> callback) {
        plugin.getDatabaseManager().executeAsync(() -> {
            List<Order> orders = new ArrayList<>();
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT * FROM coresuite_orders WHERE active = 1 AND amount_filled < amount_needed AND expires_at > ? AND status != 'COMPLETED'")) {
                stmt.setLong(1, System.currentTimeMillis());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        orders.add(mapResultSetToOrder(rs));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load active orders", e);
            }
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(orders));
        });
    }

    public void loadPlayerOrders(UUID uuid, Consumer<List<Order>> callback) {
        plugin.getDatabaseManager().executeAsync(() -> {
            List<Order> orders = new ArrayList<>();
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT * FROM coresuite_orders WHERE poster_uuid = ? AND active = 1 AND amount_filled < amount_needed AND expires_at > ? AND status != 'COMPLETED'")) {
                stmt.setString(1, uuid.toString());
                stmt.setLong(2, System.currentTimeMillis());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        orders.add(mapResultSetToOrder(rs));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load player orders", e);
            }
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(orders));
        });
    }

    public void postItemOrder(UUID posterUuid, String posterName, Material material, int amount, double priceTotal, int expiryDays, Consumer<Order> onSuccess, Runnable onFail) {
        plugin.getDatabaseManager().executeAsync(() -> {
            double pricePerItem = priceTotal / amount;
            long postedAt  = System.currentTimeMillis();
            long expiresAt = postedAt + (expiryDays * 86400000L);
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO coresuite_orders (poster_uuid, poster_name, order_type, material, description, amount_needed, amount_filled, price_per_item, price_total, posted_at, expires_at, active, status) " +
                     "VALUES (?, ?, 'ITEM', ?, NULL, ?, 0, ?, ?, ?, ?, 1, 'OPEN')",
                     Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, posterUuid.toString());
                stmt.setString(2, posterName);
                stmt.setString(3, material.name());
                stmt.setInt(4, amount);
                stmt.setDouble(5, pricePerItem);
                stmt.setDouble(6, priceTotal);
                stmt.setLong(7, postedAt);
                stmt.setLong(8, expiresAt);
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        int id = rs.getInt(1);
                        Order order = new Order(id, posterUuid.toString(), posterName, Order.OrderType.ITEM,
                            material, amount, 0, null,
                            pricePerItem, priceTotal, postedAt, expiresAt, true,
                            Order.Status.OPEN, null, null);
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            incrementOrderCount(posterUuid);
                            onSuccess.accept(order);
                            // Announce to all players
                            String prefix = config.getString("messages.prefix", "&8[&6Orders&8] &r");
                            String announceMsg = prefix + "&f" + posterName + " &7posted a new "
                                + "&e" + OrderManager.formatMaterialName(material) + " &7order! "
                                + "&e/orders &7to check it out.";
                            Bukkit.broadcast(TextUtil.colorize(announceMsg));
                        });
                        return;
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to post item order", e);
            }
            Bukkit.getScheduler().runTask(plugin, onFail);
        });
    }

    public void postServiceOrder(UUID posterUuid, String posterName, String description, double priceTotal, int expiryDays, Consumer<Order> onSuccess, Runnable onFail) {
        plugin.getDatabaseManager().executeAsync(() -> {
            long postedAt  = System.currentTimeMillis();
            long expiresAt = postedAt + (expiryDays * 86400000L);
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO coresuite_orders (poster_uuid, poster_name, order_type, material, description, amount_needed, amount_filled, price_per_item, price_total, posted_at, expires_at, active, status) " +
                     "VALUES (?, ?, 'SERVICE', NULL, ?, 1, 0, ?, ?, ?, ?, 1, 'OPEN')",
                     Statement.RETURN_GENERATED_KEYS)) {
                stmt.setString(1, posterUuid.toString());
                stmt.setString(2, posterName);
                stmt.setString(3, description);
                stmt.setDouble(4, priceTotal);
                stmt.setDouble(5, priceTotal);
                stmt.setLong(6, postedAt);
                stmt.setLong(7, expiresAt);
                stmt.executeUpdate();
                try (ResultSet rs = stmt.getGeneratedKeys()) {
                    if (rs.next()) {
                        int id = rs.getInt(1);
                        Order order = new Order(id, posterUuid.toString(), posterName, Order.OrderType.SERVICE,
                            null, 1, 0, description,
                            priceTotal, priceTotal, postedAt, expiresAt, true,
                            Order.Status.OPEN, null, null);
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            incrementOrderCount(posterUuid);
                            onSuccess.accept(order);
                            // Announce to all players
                            String prefix = config.getString("messages.prefix", "&8[&6Orders&8] &r");
                            String announceMsg = prefix + "&f" + posterName + " &7posted a new service order! "
                                + "&8'&7" + (description.length() > 30 ? description.substring(0, 30) + "..." : description) + "&8' &e/orders &7to browse.";
                            Bukkit.broadcast(TextUtil.colorize(announceMsg));
                        });
                        return;
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to post service order", e);
            }
            Bukkit.getScheduler().runTask(plugin, onFail);
        });
    }

    /** Accepts a service order — marks it as ACCEPTED and stores who accepted it. */
    public void acceptServiceOrder(int orderId, UUID acceptorUuid, String acceptorName, Runnable onSuccess, Runnable onFail) {
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE coresuite_orders SET status = 'ACCEPTED', accepted_by_uuid = ?, accepted_by_name = ? " +
                     "WHERE id = ? AND active = 1 AND status = 'OPEN'")) {
                stmt.setString(1, acceptorUuid.toString());
                stmt.setString(2, acceptorName);
                stmt.setInt(3, orderId);
                int rows = stmt.executeUpdate();
                if (rows > 0) {
                    Bukkit.getScheduler().runTask(plugin, onSuccess);
                } else {
                    Bukkit.getScheduler().runTask(plugin, onFail); // already accepted or closed
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to accept service order", e);
                Bukkit.getScheduler().runTask(plugin, onFail);
            }
        });
    }

    /** Poster marks a service order as complete, paying the acceptor. */
    public void completeServiceOrder(int orderId, UUID posterUuid, Runnable onSuccess, Runnable onFail) {
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                Order order = null;
                try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM coresuite_orders WHERE id = ? AND active = 1 AND status = 'ACCEPTED'")) {
                    stmt.setInt(1, orderId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) order = mapResultSetToOrder(rs);
                    }
                }
                if (order == null || !order.getPosterUuid().equals(posterUuid.toString())) {
                    Bukkit.getScheduler().runTask(plugin, onFail);
                    return;
                }
                final Order finalOrder = order;
                try (PreparedStatement upd = conn.prepareStatement(
                        "UPDATE coresuite_orders SET active = 0, status = 'COMPLETED', amount_filled = 1 WHERE id = ?")) {
                    upd.setInt(1, orderId);
                    upd.executeUpdate();
                }
                Bukkit.getScheduler().runTask(plugin, () -> {
                    UUID pUuid = UUID.fromString(finalOrder.getPosterUuid());
                    decrementOrderCount(pUuid);
                    // Pay acceptor
                    if (finalOrder.getAcceptedByUuid() != null) {
                        UUID aUuid = UUID.fromString(finalOrder.getAcceptedByUuid());
                        Player acceptor = Bukkit.getPlayer(aUuid);
                        org.bukkit.OfflinePlayer aTarget = (acceptor != null) ? acceptor : Bukkit.getOfflinePlayer(aUuid);
                        plugin.getEconomyManager().deposit(aTarget, finalOrder.getPriceTotal());
                        if (acceptor != null) {
                            String prefix = config.getString("messages.prefix", "&8[&6Orders&8] &r");
                            TextUtil.sendMessage(acceptor, prefix + "&aYour service for &f" + finalOrder.getPosterName()
                                + "&a was marked complete! You earned &e" + plugin.getEconomyManager().format(finalOrder.getPriceTotal()));
                        }
                    }
                    onSuccess.run();
                });
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to complete service order", e);
                Bukkit.getScheduler().runTask(plugin, onFail);
            }
        });
    }

    private Order mapResultSetToOrder(ResultSet rs) throws SQLException {
        String typeStr  = rs.getString("order_type");
        Order.OrderType type = typeStr != null ? Order.OrderType.valueOf(typeStr) : Order.OrderType.ITEM;

        String matStr = rs.getString("material");
        Material material = (matStr != null && !matStr.isEmpty()) ? Material.valueOf(matStr) : null;

        String statusStr = rs.getString("status");
        Order.Status status = statusStr != null ? Order.Status.valueOf(statusStr) : Order.Status.OPEN;

        return new Order(
            rs.getInt("id"),
            rs.getString("poster_uuid"),
            rs.getString("poster_name"),
            type,
            material,
            rs.getInt("amount_needed"),
            rs.getInt("amount_filled"),
            rs.getString("description"),
            rs.getDouble("price_per_item"),
            rs.getDouble("price_total"),
            rs.getLong("posted_at"),
            rs.getLong("expires_at"),
            rs.getInt("active") == 1,
            status,
            rs.getString("accepted_by_uuid"),
            rs.getString("accepted_by_name")
        );
    }

    public void fillOrder(int orderId, Player filler, int amountToFill, Consumer<Double> onSuccess, Runnable onFail) {
        getOrderById(orderId, order -> {
            if (order == null || !order.isActive() || order.isExpired()) {
                onFail.run();
                return;
            }

            if (order.getStatus() == Order.Status.ACCEPTED && order.getAcceptedByUuid() != null) {
                if (!order.getAcceptedByUuid().equals(filler.getUniqueId().toString())) {
                    String prefix = config.getString("messages.prefix", "&8[&6Orders&8] &r");
                    TextUtil.sendMessage(filler, prefix + "&cThis order deal is reserved for &f" + order.getAcceptedByName() + "&c.");
                    onFail.run();
                    return;
                }
            }

            int actualToFill = Math.min(amountToFill, order.getAmountRemaining());
            int playerHas = getAmountInInventory(filler, order.getMaterial());
            if (playerHas < actualToFill || actualToFill <= 0) {
                onFail.run();
                return;
            }

            // Remove items and deposit payout on main thread
            removeItems(filler, order.getMaterial(), actualToFill);
            double moneyEarned = actualToFill * order.getPricePerItem();
            plugin.getEconomyManager().deposit(filler, moneyEarned);

            // Execute DB update in a single self-contained async transaction
            plugin.getDatabaseManager().executeAsync(() -> {
                try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                    conn.setAutoCommit(false);
                    try {
                        boolean fullyFilled = (order.getAmountFilled() + actualToFill >= order.getAmountNeeded());
                        String updateSql = fullyFilled
                            ? "UPDATE coresuite_orders SET amount_filled = amount_filled + ?, active = 0, status = 'COMPLETED' WHERE id = ?"
                            : "UPDATE coresuite_orders SET amount_filled = amount_filled + ? WHERE id = ?";
                        try (PreparedStatement updateStmt = conn.prepareStatement(updateSql)) {
                            updateStmt.setInt(1, actualToFill);
                            updateStmt.setInt(2, orderId);
                            updateStmt.executeUpdate();
                        }

                        try (PreparedStatement insertFill = conn.prepareStatement(
                                "INSERT INTO coresuite_orders_fills (order_id, filler_uuid, filler_name, amount, money_earned, filled_at) VALUES (?, ?, ?, ?, ?, ?)")) {
                            insertFill.setInt(1, orderId);
                            insertFill.setString(2, filler.getUniqueId().toString());
                            insertFill.setString(3, filler.getName());
                            insertFill.setInt(4, actualToFill);
                            insertFill.setDouble(5, moneyEarned);
                            insertFill.setLong(6, System.currentTimeMillis());
                            insertFill.executeUpdate();
                        }

                        // If poster is offline, queue pending items for when they join
                        Player posterCheck = Bukkit.getPlayer(UUID.fromString(order.getPosterUuid()));
                        if (posterCheck == null) {
                            try (PreparedStatement pendingStmt = conn.prepareStatement(
                                    "INSERT INTO coresuite_orders_pending_items (player_uuid, material, amount) VALUES (?, ?, ?)")) {
                                pendingStmt.setString(1, order.getPosterUuid());
                                pendingStmt.setString(2, order.getMaterial().name());
                                pendingStmt.setInt(3, actualToFill);
                                pendingStmt.executeUpdate();
                            }
                        }

                        conn.commit();
                    } catch (SQLException ex) {
                        conn.rollback();
                        throw ex;
                    } finally {
                        conn.setAutoCommit(true);
                    }

                    Bukkit.getScheduler().runTask(plugin, () -> {
                        // Deliver items to poster if online
                        Player poster = Bukkit.getPlayer(UUID.fromString(order.getPosterUuid()));
                        if (poster != null) {
                            Map<Integer, ItemStack> overflow = poster.getInventory().addItem(new ItemStack(order.getMaterial(), actualToFill));
                            if (!overflow.isEmpty()) {
                                overflow.values().forEach(it -> poster.getWorld().dropItemNaturally(poster.getLocation(), it));
                            }
                            String prefix = config.getString("messages.prefix", "&8[&6Orders&8] &r");
                            int newFilled = order.getAmountFilled() + actualToFill;
                            if (newFilled >= order.getAmountNeeded()) {
                                TextUtil.sendMessage(poster, prefix + config.getString("messages.order-complete", "&aYour order is complete!").replace("{item}", formatMaterialName(order.getMaterial())));
                                decrementOrderCount(poster.getUniqueId());
                            } else {
                                TextUtil.sendMessage(poster, prefix + config.getString("messages.filled-notify", "&aOrder partially filled.")
                                        .replace("{item}", formatMaterialName(order.getMaterial()))
                                        .replace("{filled}", String.valueOf(newFilled))
                                        .replace("{total}", String.valueOf(order.getAmountNeeded())));
                            }
                        } else if (order.getAmountFilled() + actualToFill >= order.getAmountNeeded()) {
                            UUID pUuid = UUID.fromString(order.getPosterUuid());
                            decrementOrderCount(pUuid);
                        }
                        onSuccess.accept(moneyEarned);
                    });

                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed fill transaction", e);
                    // Roll back inventory and economy changes on main thread
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        plugin.getEconomyManager().withdraw(filler, moneyEarned);
                        filler.getInventory().addItem(new ItemStack(order.getMaterial(), actualToFill));
                        onFail.run();
                    });
                }
            });
        });
    }

    public void cancelOrder(int orderId, UUID requesterUuid, Consumer<Double> onSuccess, Runnable onFail) {
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM coresuite_orders WHERE id = ? AND active = 1")) {
                    stmt.setInt(1, orderId);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            Order order = mapResultSetToOrder(rs);
                            
                            // Allow poster or admin (admin logic should be handled by caller)
                            if (requesterUuid != null && !order.getPosterUuid().equals(requesterUuid.toString())) {
                                conn.rollback();
                                Bukkit.getScheduler().runTask(plugin, onFail);
                                return;
                            }
                            
                            try (PreparedStatement updateStmt = conn.prepareStatement("UPDATE coresuite_orders SET active = 0 WHERE id = ?")) {
                                updateStmt.setInt(1, orderId);
                                updateStmt.executeUpdate();
                            }
                            try (PreparedStatement rejectBids = conn.prepareStatement("UPDATE coresuite_order_bids SET status = 'REJECTED' WHERE order_id = ? AND status = 'PENDING'")) {
                                rejectBids.setInt(1, orderId);
                                rejectBids.executeUpdate();
                            }
                            conn.commit();
                            
                            Bukkit.getScheduler().runTask(plugin, () -> {
                                UUID pUuid = UUID.fromString(order.getPosterUuid());
                                decrementOrderCount(pUuid);
                                
                                double priceRemaining = order.getPriceRemaining();
                                org.bukkit.entity.Player pOnline = Bukkit.getPlayer(pUuid);
                                org.bukkit.OfflinePlayer pTarget = (pOnline != null) ? pOnline : Bukkit.getOfflinePlayer(pUuid);
                                plugin.getEconomyManager().deposit(pTarget, priceRemaining);
                                onSuccess.accept(priceRemaining);
                            });
                            return;
                        }
                    }
                }
                conn.rollback();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to cancel order", e);
            }
            Bukkit.getScheduler().runTask(plugin, onFail);
        });
    }

    public void expireOldOrders() {
        plugin.getDatabaseManager().executeAsync(() -> {
            List<Order> expiredOrders = new ArrayList<>();
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                conn.setAutoCommit(false);
                long now = System.currentTimeMillis();
                try (PreparedStatement stmt = conn.prepareStatement("SELECT * FROM coresuite_orders WHERE active = 1 AND expires_at <= ? AND amount_filled < amount_needed")) {
                    stmt.setLong(1, now);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            expiredOrders.add(mapResultSetToOrder(rs));
                        }
                    }
                }
                
                if (!expiredOrders.isEmpty()) {
                    try (PreparedStatement updateStmt = conn.prepareStatement("UPDATE coresuite_orders SET active = 0 WHERE id = ?")) {
                        for (Order order : expiredOrders) {
                            updateStmt.setInt(1, order.getId());
                            updateStmt.addBatch();
                        }
                        updateStmt.executeBatch();
                    }
                    conn.commit();
                }
                conn.setAutoCommit(true);
                
                Bukkit.getScheduler().runTask(plugin, () -> {
                    double expiryFeePercent = config.getDouble("expiry-fee-percent", 10.0);
                    for (Order order : expiredOrders) {
                        UUID pUuid = UUID.fromString(order.getPosterUuid());
                        decrementOrderCount(pUuid);
                        
                        double refund = order.getPriceRemaining() * (1.0 - (expiryFeePercent / 100.0));
                        if (refund > 0) {
                            plugin.getEconomyManager().deposit(Bukkit.getOfflinePlayer(pUuid), refund);
                        }
                        
                        Player poster = Bukkit.getPlayer(pUuid);
                        if (poster != null) {
                            String prefix = config.getString("messages.prefix", "&8[&6Orders&8] &r");
                            String msg = config.getString("messages.expired", "&cOrder expired.")
                                    .replace("{amount}", String.valueOf(order.getAmountNeeded()))
                                    .replace("{item}", formatMaterialName(order.getMaterial()))
                                    .replace("{refund}", plugin.getEconomyManager().format(refund));
                            TextUtil.sendMessage(poster, prefix + msg);
                        }
                    }
                });
                
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to expire old orders", e);
            }
        });
    }

    public int getPlayerActiveOrderCount(UUID uuid) {
        return activeOrderCache.getOrDefault(uuid, 0);
    }

    /** Async fetch of a single order by its id. Returns null if not found. */
    public void getOrderById(int orderId, Consumer<Order> callback) {
        plugin.getDatabaseManager().executeAsync(() -> {
            Order order = null;
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT * FROM coresuite_orders WHERE id = ?")) {
                stmt.setInt(1, orderId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) order = mapResultSetToOrder(rs);
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to fetch order by id " + orderId, e);
            }
            final Order finalOrder = order;
            Bukkit.getScheduler().runTask(plugin, () -> callback.accept(finalOrder));
        });
    }

    public static int getAmountInInventory(Player player, Material material) {
        int amount = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                amount += item.getAmount();
            }
        }
        return amount;
    }
    
    public static void removeItems(Player player, Material material, int amount) {
        int remaining = amount;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) {
                if (item.getAmount() <= remaining) {
                    remaining -= item.getAmount();
                    item.setAmount(0); // clear
                } else {
                    item.setAmount(item.getAmount() - remaining);
                    remaining = 0;
                }
                if (remaining <= 0) {
                    break;
                }
            }
        }
    }
    
    public static String formatMaterialName(Material material) {
        String name = material.name().toLowerCase().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String word : name.split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1)).append(" ");
            }
        }
        return sb.toString().trim();
    }

    public void deliverPendingItems(Player player) {
        plugin.getDatabaseManager().executeAsync(() -> {
            List<Map.Entry<Material, Integer>> itemsToGive = new ArrayList<>();
            List<Integer> idsToDelete = new ArrayList<>();
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                     "SELECT id, material, amount FROM coresuite_orders_pending_items WHERE player_uuid = ?")) {
                stmt.setString(1, player.getUniqueId().toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String matStr = rs.getString("material");
                        Material mat = Material.matchMaterial(matStr);
                        int amt = rs.getInt("amount");
                        if (mat != null && amt > 0) {
                            itemsToGive.add(Map.entry(mat, amt));
                            idsToDelete.add(rs.getInt("id"));
                        }
                    }
                }
                if (!idsToDelete.isEmpty()) {
                    try (PreparedStatement del = conn.prepareStatement("DELETE FROM coresuite_orders_pending_items WHERE id = ?")) {
                        for (int id : idsToDelete) {
                            del.setInt(1, id);
                            del.addBatch();
                        }
                        del.executeBatch();
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load pending item deliveries", e);
            }

            if (!itemsToGive.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    String prefix = config.getString("messages.prefix", "&8[&6Orders&8] &r");
                    for (Map.Entry<Material, Integer> entry : itemsToGive) {
                        Map<Integer, ItemStack> overflow = player.getInventory().addItem(new ItemStack(entry.getKey(), entry.getValue()));
                        if (!overflow.isEmpty()) {
                            overflow.values().forEach(it -> player.getWorld().dropItemNaturally(player.getLocation(), it));
                        }
                        TextUtil.sendMessage(player, prefix + "&aDelivered &e" + entry.getValue() + "x " + formatMaterialName(entry.getKey()) + " &afrom fulfilled orders while you were away!");
                    }
                });
            }
        });
    }
}
