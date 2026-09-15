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
                 PreparedStatement stmt = conn.prepareStatement("SELECT reward_id, recipient, kind, state FROM coresuite_boss_rewards WHERE state <> 1 ORDER BY created_at LIMIT 25");
                 ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) lines.add("&e" + rs.getString(1) + " &7recipient=" + rs.getString(2)
                        + " type=" + rs.getString(3) + (rs.getInt(4) == 2 ? " &cRESERVED / REVIEW" : " &aAWAITING CLAIM"));
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
    public void claim(Player player) {
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
                    if (!player.isOnline() || !net.rocky.coresuite.common.integration.AuthenticationGate.isAuthenticated(player)) { acquired.forEach(reward -> finish(reward, false)); return; }
                    for (Reward reward : acquired) {
                        try {
                            boolean delivered = deliver(player, reward);
                            finish(reward, delivered);
                        } catch (Exception | LinkageError error) {
                            plugin.getLogger().log(Level.SEVERE, "Uncertain boss reward; retained for review: " + reward.id() + " recipient=" + uuid, error);
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
    private boolean deliver(Player player, Reward reward) {
        switch (reward.kind()) {
            case "ITEMS" -> {
                String[] data = JSON.fromJson(reward.payload(), String[].class);
                List<ItemStack> items = Arrays.stream(data).map(value -> ItemStack.deserializeBytes(Base64.getDecoder().decode(value))).toList();
                ItemStack[] plan = fit(player.getInventory().getStorageContents(), items, player.getInventory().getMaxStackSize());
                if (plan == null) {
                    TextUtil.sendMessage(player, "&eYour boss loot is saved. Make inventory space and use /boss claim.");
                    return false;
                }
                player.getInventory().setStorageContents(plan);
                player.saveData();
                TextUtil.sendMessage(player, "&aYour boss participation loot has been delivered!");
                return true;
            }
            case "CURRENCY" -> {
                double amount = Double.parseDouble(reward.payload());
                if (!Double.isFinite(amount) || amount <= 0) throw new IllegalArgumentException("Invalid currency reward");
                if (!plugin.getEconomyManager().isAvailable() || !plugin.getEconomyManager().deposit(player, amount)) {
                    TextUtil.sendMessage(player, "&eYour boss currency reward is pending. Retry with /boss claim when the economy is available.");
                    return false;
                }
                TextUtil.sendMessage(player, "&a+ " + plugin.getEconomyManager().format(amount) + " &7boss reward!");
                return true;
            }
            case "COMMAND" -> {
                String command = reward.payload().replace("%player%", player.getName()).replaceFirst("^/", "");
                String root = command.split(" ")[0].toLowerCase(Locale.ROOT);
                if ((root.equals("crate") || root.equals("crates") || root.equals("crazycrates"))
                        && !Bukkit.getPluginManager().isPluginEnabled("ExcellentCrates") && !Bukkit.getPluginManager().isPluginEnabled("CrazyCrates") && !Bukkit.getPluginManager().isPluginEnabled("GoldenCrates")) return false;
                if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) throw new IllegalStateException("Reward command did not report success");
                return true;
            }
            default -> throw new IllegalArgumentException("Unknown reward type");
        }
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
