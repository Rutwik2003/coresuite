package net.rocky.coresuite.module.worldbosses.rewards;

import com.google.gson.Gson;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.sql.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Durable independent reward components. State 2 is an uncertain delivery, never auto-replayed. */
public final class BossRewardMailbox {
    private static final Gson JSON = new Gson();
    public record Reward(String id, String kind, String payload) {
        public static Reward items(List<ItemStack> items) {
            String[] data = items.stream().map(item -> Base64.getEncoder().encodeToString(item.serializeAsBytes())).toArray(String[]::new);
            return new Reward(UUID.randomUUID().toString(), "ITEMS", JSON.toJson(data));
        }
        public static Reward currency(double amount) { return new Reward(UUID.randomUUID().toString(), "CURRENCY", Double.toString(amount)); }
        public static Reward command(String command) { return new Reward(UUID.randomUUID().toString(), "COMMAND", command); }
    }
    private final CoreSuitePlugin plugin;
    private final Set<UUID> claiming = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> claimAfter = new ConcurrentHashMap<>();
    public void deferForWorldChange(Player player) {
        claimAfter.put(player.getUniqueId(), System.currentTimeMillis() + 2000L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) claim(player);
            claimAfter.entrySet().removeIf(entry -> entry.getValue() <= System.currentTimeMillis());
        }, 45L);
    }
    public BossRewardMailbox(CoreSuitePlugin plugin) { this.plugin = plugin; }
    public void initialize() {
        plugin.getDatabaseManager().execute("CREATE TABLE IF NOT EXISTS coresuite_boss_rewards (reward_id VARCHAR(36) PRIMARY KEY, recipient VARCHAR(36) NOT NULL, kind VARCHAR(16) NOT NULL, payload TEXT NOT NULL, state INTEGER NOT NULL DEFAULT 0, created_at BIGINT NOT NULL)");
    }
    public void enqueue(UUID recipient, List<Reward> rewards) {
        List<Reward> snapshot = List.copyOf(rewards);
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                conn.setAutoCommit(false);
                try (PreparedStatement stmt = conn.prepareStatement("INSERT INTO coresuite_boss_rewards (reward_id, recipient, kind, payload, created_at) VALUES (?, ?, ?, ?, ?)")) {
                    for (Reward reward : snapshot) {
                        stmt.setString(1, reward.id()); stmt.setString(2, recipient.toString()); stmt.setString(3, reward.kind()); stmt.setString(4, reward.payload()); stmt.setLong(5, System.currentTimeMillis()); stmt.addBatch();
                    }
                    stmt.executeBatch(); conn.commit();
                } catch (SQLException error) { conn.rollback(); throw error; }
                finally { conn.setAutoCommit(true); }
            } catch (SQLException error) {
                plugin.getLogger().log(Level.SEVERE, "Boss rewards could not be queued for " + recipient + "; recovery payload=" + JSON.toJson(snapshot), error);
                return;
            }
            if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> {
                Player player = Bukkit.getPlayer(recipient);
                if (player != null && player.isOnline()) claim(player);
            });
        });
    }
    public void inspectPending(org.bukkit.command.CommandSender sender) {
        plugin.getDatabaseManager().executeAsync(() -> {
            List<String> lines = new ArrayList<>();
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT reward_id, recipient, kind, state, payload FROM coresuite_boss_rewards WHERE state <> 1 ORDER BY created_at LIMIT 25");
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) lines.add("&e" + rs.getString(1) + " &7recipient=" + rs.getString(2)
                        + " type=" + rs.getString(3) + (rs.getInt(4) == 2 ? " &cRESERVED / REVIEW" : " &aAWAITING CLAIM")
                        + ("COMMAND".equals(rs.getString(3)) ? " &7command=" + rs.getString(5) : ""));
            } catch (SQLException error) { lines.add("&cCannot read reward receipts; check the server log."); plugin.getLogger().log(Level.WARNING, "Cannot inspect boss rewards", error); }
            if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> {
                TextUtil.sendMessage(sender, "&6Pending boss rewards (up to 25). Verify delivery before reconciling reserved receipts.");
                if (lines.isEmpty()) TextUtil.sendMessage(sender, "&7No pending rewards.");
                else lines.forEach(line -> TextUtil.sendMessage(sender, line));
            });
        });
    }
    static boolean reserve(Connection conn, String id) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("UPDATE coresuite_boss_rewards SET state = 2 WHERE reward_id = ? AND state = 0")) {
            stmt.setString(1, id); return stmt.executeUpdate() == 1;
        }
    }

    public void openHistory(Player player) {
        UUID recipient = player.getUniqueId();
        plugin.getDatabaseManager().executeAsync(() -> {
            List<String[]> receipts = new ArrayList<>();
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement query = conn.prepareStatement("SELECT reward_id, kind, state FROM coresuite_boss_rewards WHERE recipient = ? ORDER BY created_at DESC LIMIT 45")) {
                query.setString(1, recipient.toString());
                try (ResultSet rs = query.executeQuery()) {
                    while (rs.next()) receipts.add(new String[]{rs.getString(1), rs.getString(2), rs.getString(3)});
                }
            } catch (SQLException error) {
                plugin.getLogger().log(Level.WARNING, "Cannot load boss reward history", error);
                return;
            }
            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;
                net.rocky.coresuite.common.gui.Gui gui = new net.rocky.coresuite.common.gui.Gui("&8Boss Reward History", 6);
                int slot = 0;
                for (String[] receipt : receipts) {
                    int state = Integer.parseInt(receipt[2]);
                    String status = state == 1 ? "&aDelivered" : state == 2 ? "&cAdmin review needed" : "&eAwaiting claim";
                    gui.setItem(slot++, net.rocky.coresuite.common.gui.GuiButton.of(
                            state == 1 ? org.bukkit.Material.LIME_DYE : org.bukkit.Material.CHEST,
                            status + " &7" + receipt[1], List.of("&7Receipt: " + receipt[0],
                                    state == 2 ? "&7Ask an admin to review this receipt." : state == 1 ? "&7Already delivered." : "&7Make space, then claim below.",
                                    "&7Missing providers or key IDs keep rewards pending."), event -> {}));
                }
                gui.setItem(49, net.rocky.coresuite.common.gui.GuiButton.of(org.bukkit.Material.HOPPER,
                        "&aClaim pending rewards", List.of("&7Click once. Reopen to refresh status."), event -> claim(player)));
                gui.open(player);
            });
        });
    }
    static boolean reconcile(Connection conn, String id, boolean delivered) throws SQLException {
        try (PreparedStatement stmt = conn.prepareStatement("UPDATE coresuite_boss_rewards SET state = ? WHERE reward_id = ? AND state = 2")) {
            stmt.setInt(1, delivered ? 1 : 0); stmt.setString(2, id);
            return stmt.executeUpdate() == 1;
        }
    }
    public void reconcile(org.bukkit.command.CommandSender sender, String id, boolean delivered) {
        try { UUID.fromString(id); }
        catch (IllegalArgumentException error) { TextUtil.sendMessage(sender, "&cInvalid reward receipt UUID."); return; }
        String administrator = sender.getName();
        plugin.getDatabaseManager().executeAsync(() -> {
            String message;
            UUID recipient = null;
            boolean locked = false;
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT recipient FROM coresuite_boss_rewards WHERE reward_id = ? AND state = 2")) {
                stmt.setString(1, id);
                try (ResultSet rs = stmt.executeQuery()) { if (rs.next()) recipient = UUID.fromString(rs.getString(1)); }
                if (recipient == null) message = "&eNo reserved receipt found; nothing changed.";
                else if (!(locked = claiming.add(recipient))) message = "&eThis player's rewards are being processed. Try again shortly.";
                else if (!reconcile(conn, id, delivered)) message = "&eReceipt state changed; nothing changed.";
                else {
                    message = delivered ? "&aReceipt marked delivered; it will not pay again." : "&aReceipt reopened. The player can use /boss claim after making inventory space.";
                    plugin.getLogger().warning("[WorldBosses] Administrator " + administrator + " reconciled receipt=" + id + " recipient=" + recipient + " action=" + (delivered ? "delivered" : "retry"));
                }
            } catch (SQLException | IllegalArgumentException error) {
                message = "&cCannot reconcile reward; check the server log.";
                plugin.getLogger().log(Level.SEVERE, "Cannot reconcile boss reward " + id, error);
            } finally { if (locked) claiming.remove(recipient); }
            String result = message;
            if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> TextUtil.sendMessage(sender, result));
        });
    }
    public void claim(Player player) {
        if (System.currentTimeMillis() < claimAfter.getOrDefault(player.getUniqueId(), 0L)) return;
        UUID claimWorld = player.getWorld().getUID();
        if (!net.rocky.coresuite.common.integration.AuthenticationGate.isAuthenticated(player)) return;
        UUID uuid = player.getUniqueId();
        if (!claiming.add(uuid)) return;
        plugin.getDatabaseManager().executeAsync(() -> {
            List<Reward> acquired = new ArrayList<>();
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                List<Reward> pending = new ArrayList<>();
                try (PreparedStatement stmt = conn.prepareStatement("SELECT reward_id, kind, payload FROM coresuite_boss_rewards WHERE recipient = ? AND state = 0 ORDER BY created_at, reward_id LIMIT 50")) {
                    stmt.setString(1, uuid.toString());
                    try (ResultSet rs = stmt.executeQuery()) { while (rs.next()) pending.add(new Reward(rs.getString(1), rs.getString(2), rs.getString(3))); }
                }
                for (Reward reward : pending) if (reserve(conn, reward.id())) acquired.add(reward);
            } catch (SQLException error) {
                for (Reward reward : acquired) finish(reward, false);
                claiming.remove(uuid);
                plugin.getLogger().log(Level.SEVERE, "Cannot load boss rewards for " + uuid, error);
                return;
            }
            if (!plugin.isEnabled()) {
                acquired.forEach(reward -> finish(reward, false)); claiming.remove(uuid); return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                try {
                    if (!player.isOnline() || !claimWorld.equals(player.getWorld().getUID())
                            || System.currentTimeMillis() < claimAfter.getOrDefault(uuid, 0L)
                            || !net.rocky.coresuite.common.integration.AuthenticationGate.isAuthenticated(player)) { acquired.forEach(reward -> finish(reward, false)); return; }
                    for (Reward reward : acquired) {
                        try {
                            boolean delivered = deliver(player, reward);
                            finish(reward, delivered);
                        } catch (Exception | LinkageError error) {
                            plugin.getLogger().log(Level.SEVERE, "Uncertain boss reward; retained for review: " + reward.id() + " recipient=" + uuid
                                    + " kind=" + reward.kind() + ("COMMAND".equals(reward.kind()) ? " command=" + reward.payload() : ""), error);
                            TextUtil.sendMessage(player, "&cA boss reward needs administrator review. Receipt: " + reward.id());
                        }
                    }
                } finally { claiming.remove(uuid); }
            });
        });
    }
    private void finish(Reward reward, boolean delivered) {
        plugin.getDatabaseManager().execute("UPDATE coresuite_boss_rewards SET state = ? WHERE reward_id = ? AND state = 2", delivered ? 1 : 0, reward.id());
    }
    private boolean deliver(Player player, Reward reward) throws ReflectiveOperationException {
        switch (reward.kind()) {
            case "ITEMS" -> {
                String[] data = JSON.fromJson(reward.payload(), String[].class);
                List<ItemStack> items = Arrays.stream(data).map(value -> ItemStack.deserializeBytes(Base64.getDecoder().decode(value))).toList();
                ItemStack[] plan = fit(player.getInventory().getStorageContents(), items, player.getInventory().getMaxStackSize());
                if (plan == null) {
                    TextUtil.sendMessage(player, "&eInventory full — these boss rewards are stored. Make space, then use &6/boss claim&e:");
                    sendItemDetails(player, items);
                    return false;
                }
                player.getInventory().setStorageContents(plan);
                player.saveData();
                if (!items.isEmpty()) {
                    TextUtil.sendMessage(player, "&aBoss items received:");
                    sendItemDetails(player, items);
                }
                return true;
            }
            case "CURRENCY" -> {
                double amount = Double.parseDouble(reward.payload());
                if (!Double.isFinite(amount) || amount <= 0) throw new IllegalArgumentException("Invalid currency reward");
                if (!plugin.getEconomyManager().isAvailable() || !plugin.getEconomyManager().deposit(player, amount)) {
                    TextUtil.sendMessage(player, "&eBoss money pending: &6$" + String.format("%,.0f", amount) + "&e. The economy is unavailable; your reward remains saved.");
                    return false;
                }
                TextUtil.sendMessage(player, "&a+ " + plugin.getEconomyManager().format(amount) + " &7boss reward!");
                return true;
            }
            case "COMMAND" -> {
                String command = reward.payload().replace("%player%", player.getName()).replace("{player}", player.getName()).strip().replaceFirst("^/", "");
                ExcellentCratesRewardBridge.Delivery keyDelivery;
                try {
                    var grant = ExcellentCratesRewardBridge.parse(command, player.getName());
                    var provider = Bukkit.getPluginManager().getPlugin("ExcellentCrates");
                    if (grant != null && provider != null) {
                        if (!provider.isEnabled()) return deferCommand(player, reward, "ExcellentCrates is disabled");
                        keyDelivery = ExcellentCratesRewardBridge.prepare(provider, player, grant);
                    } else keyDelivery = null;
                } catch (ReflectiveOperationException | RuntimeException | LinkageError error) {
                    return deferCommand(player, reward, "ExcellentCrates preflight failed: " + error);
                }
                if (keyDelivery != null) {
                    // No catch/retry after a mutation: a delivery exception keeps the reserved receipt for review.
                    String description = keyDescription(command, player.getName());
                    if (!keyDelivery.deliver()) {
                        TextUtil.sendMessage(player, "&eInventory full — stored &f" + description + "&e. Make space, then use &6/boss claim&e.");
                        return false;
                    }
                    TextUtil.sendMessage(player, "&aBoss reward received: &f" + description);
                    return true;
                }
                String root = command.split("\\s+")[0].toLowerCase(Locale.ROOT);
                if ((root.equals("crate") || root.equals("crates") || root.equals("crazycrates"))
                        && !Bukkit.getPluginManager().isPluginEnabled("ExcellentCrates") && !Bukkit.getPluginManager().isPluginEnabled("CrazyCrates") && !Bukkit.getPluginManager().isPluginEnabled("GoldenCrates")) return deferCommand(player, reward, "No enabled crate provider");
                if (Bukkit.getCommandMap().getCommand(root) == null) return deferCommand(player, reward, "Command is not registered: " + root);
                if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) throw new IllegalStateException("Reward command returned false: " + command);
                TextUtil.sendMessage(player, "&aBoss bonus applied: &f" + keyDescription(command, player.getName()));
                return true;
            }
            default -> throw new IllegalArgumentException("Unknown reward type");
        }
    }
    private boolean deferCommand(Player player, Reward reward, String reason) {
        plugin.getLogger().warning("[WorldBosses] Reward kept pending: " + reward.id() + " recipient=" + player.getUniqueId() + " command=" + reward.payload() + " reason=" + reason);
        TextUtil.sendMessage(player, "&eA boss bonus is pending because its reward provider is unavailable or misconfigured. Your reward is saved; please contact an admin.");
        return false;
    }
    static String keyDescription(String command, String playerName) {
        var grant = ExcellentCratesRewardBridge.parse(command, playerName);
        return grant == null ? "Custom reward (" + command.split("\\s+")[0] + ")"
                : grant.amount() + " × " + grant.key().replace('_', ' ') + " Crate Key" + (grant.amount() == 1 ? "" : "s");
    }
    private static void sendItemDetails(Player player, List<ItemStack> items) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ItemStack item : items) {
            var meta = item.getItemMeta();
            String name = meta != null && meta.hasDisplayName()
                    ? net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().serialize(meta.displayName())
                    : item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
            counts.merge(name, item.getAmount(), Integer::sum);
        }
        counts.forEach((name, amount) -> TextUtil.sendMessage(player, "&7 • &f" + amount + " × " + name));
    }
    static ItemStack[] fit(ItemStack[] current, List<ItemStack> incoming, int inventoryLimit) {
        ItemStack[] plan = Arrays.stream(current).map(item -> item == null ? null : item.clone()).toArray(ItemStack[]::new);
        for (ItemStack source : incoming) {
            int left = source.getAmount();
            int maximum = Math.max(1, Math.min(inventoryLimit, source.getMaxStackSize()));
            for (int i = 0; i < plan.length && left > 0; i++) {
                if (plan[i] == null || !plan[i].isSimilar(source)) continue;
                int added = Math.max(0, Math.min(left, maximum - plan[i].getAmount()));
                plan[i].setAmount(plan[i].getAmount() + added); left -= added;
            }
            for (int i = 0; i < plan.length && left > 0; i++) {
                if (plan[i] != null && !plan[i].getType().isAir()) continue;
                plan[i] = source.clone(); int added = Math.min(left, maximum); plan[i].setAmount(added); left -= added;
            }
            if (left > 0) return null;
        }
        return plan;
    }
}
