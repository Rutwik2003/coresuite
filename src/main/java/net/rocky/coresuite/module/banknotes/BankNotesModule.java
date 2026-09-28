package net.rocky.coresuite.module.banknotes;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.economy.EconomyManager;
import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.gui.GuiButton;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import net.kyori.adventure.text.Component;
import java.util.List;
import java.util.ArrayList;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.command.TabCompleter;

public class BankNotesModule extends Module implements Listener, CommandExecutor, TabCompleter {

    private final NamespacedKey noteIdKey;
    private final Map<UUID, String> pendingPrompts = new ConcurrentHashMap<>();
    private final Set<String> activeRedeemingNotes = ConcurrentHashMap.newKeySet();
    private final Set<UUID> activeWithdrawingPlayers = ConcurrentHashMap.newKeySet();

    public BankNotesModule(CoreSuitePlugin plugin) {
        super(plugin, "BankNotes", "bank-notes");
        this.noteIdKey = new NamespacedKey(plugin, "bn_id");
    }

    @Override
    public void onEnable() {
        registerListener(this);
        initDatabase();

        String[] commands = {"withdraw", "withdrawadmin", "withdrawlog", "claimnote", "bank"};
        for (String cmd : commands) {
            PluginCommand command = plugin.getCommand(cmd);
            if (command != null) {
                command.setExecutor(this);
                command.setTabCompleter(this);
            }
        }
    }

    @Override
    public void onDisable() {
        String[] commands = {"withdraw", "withdrawadmin", "withdrawlog", "claimnote", "bank"};
        for (String cmd : commands) {
            PluginCommand command = plugin.getCommand(cmd);
            if (command != null && command.getExecutor() == this) {
                command.setExecutor(null);
                command.setTabCompleter(null);
            }
        }
    }

    public int getTotalExperience(Player player) {
        int level = Math.max(0, player.getLevel());
        double base = level <= 16 ? (double)level * level + 6.0 * level
                : level <= 31 ? 2.5 * level * level - 40.5 * level + 360
                : 4.5 * level * level - 162.5 * level + 2220;
        double next = level < 16 ? 2.0 * level + 7 : level < 31 ? 5.0 * level - 38 : 9.0 * level - 158;
        return (int)Math.min(Integer.MAX_VALUE, Math.max(0, base + Math.round(next * player.getExp())));
    }

    private int getExpToNext(int level) {
        if (level < 16) return 2 * level + 7;
        if (level < 31) return 5 * level - 38;
        return 9 * level - 158;
    }
    
    public void removeExp(Player player, int amount) {
        int exp = getTotalExperience(player) - amount;
        player.setTotalExperience(0);
        player.setLevel(0);
        player.setExp(0);
        if (exp > 0) player.giveExp(exp);
    }

    private void initDatabase() {
        String sql = "CREATE TABLE IF NOT EXISTS coresuite_banknotes (" +
                "id VARCHAR(64) PRIMARY KEY, " +
                "amount REAL NOT NULL, " +
                "owner TEXT NOT NULL, " +
                "redeemed BOOLEAN NOT NULL DEFAULT 0, " +
                "redeemed_by TEXT, " +
                "issue_time BIGINT NOT NULL, " +
                "redeem_time BIGINT)";
        plugin.getDatabaseManager().execute(sql);
        plugin.getDatabaseManager().execute("CREATE TABLE IF NOT EXISTS coresuite_banknote_deliveries (note_id VARCHAR(64) PRIMARY KEY, recipient VARCHAR(36) NOT NULL)");
        for (Player player : Bukkit.getOnlinePlayers()) net.rocky.coresuite.common.integration.AuthenticationGate.afterLogin(plugin, player, () -> { if (isEnabled()) deliverPending(player); });
        
        boolean isMySQL = plugin.getDatabaseManager().isMySQL();
        String idCol = isMySQL ? "id INT NOT NULL AUTO_INCREMENT PRIMARY KEY" : "id INTEGER PRIMARY KEY AUTOINCREMENT";
        plugin.getDatabaseManager().execute("CREATE TABLE IF NOT EXISTS coresuite_banknotes_logs (" + idCol + ", action VARCHAR(32), player VARCHAR(64), amount REAL, note_id VARCHAR(64), time BIGINT)");
        
        if (!isMySQL) {
            plugin.getDatabaseManager().execute("CREATE INDEX IF NOT EXISTS idx_bn_logs_player ON coresuite_banknotes_logs (player, id DESC)");
        } else {
            plugin.getDatabaseManager().executeAsync(() -> {
                try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                    boolean hasIndex = false;
                    try (ResultSet rs = conn.getMetaData().getIndexInfo(null, null, "coresuite_banknotes_logs", false, false)) {
                        while (rs.next()) {
                            if ("idx_bn_logs_player".equalsIgnoreCase(rs.getString("INDEX_NAME"))) {
                                hasIndex = true;
                                break;
                            }
                        }
                    }
                    if (!hasIndex) {
                        try (PreparedStatement ps = conn.prepareStatement("CREATE INDEX idx_bn_logs_player ON coresuite_banknotes_logs (player, id DESC)")) {
                            ps.executeUpdate();
                        }
                    }
                } catch (SQLException ignored) {}
            });
        }
        
        int maxEntries = config.getInt("max-log-entries", 10000);
        String deleteSql = isMySQL
                ? "DELETE FROM coresuite_banknotes_logs WHERE id NOT IN (SELECT id FROM (SELECT id FROM coresuite_banknotes_logs ORDER BY id DESC LIMIT ?) AS tmp_logs)"
                : "DELETE FROM coresuite_banknotes_logs WHERE id NOT IN (SELECT id FROM coresuite_banknotes_logs ORDER BY id DESC LIMIT ?)";
        plugin.getDatabaseManager().execute(deleteSql, maxEntries);
    }

    private String generateNoteId(boolean isXp) {
        String prefix = isXp ? "XP-" : "BN-";
        return prefix + UUID.randomUUID().toString();
    }

    private double parseAmount(String raw) {
        String s = raw.toLowerCase().replace(",", "");
        double mult = 1.0;
        if (s.endsWith("k")) {
            s = s.substring(0, s.length() - 1);
            mult = 1000.0;
        } else if (s.endsWith("m")) {
            s = s.substring(0, s.length() - 1);
            mult = 1000000.0;
        } else if (s.endsWith("b")) {
            s = s.substring(0, s.length() - 1);
            mult = 1000000000.0;
        } else if (s.endsWith("t")) {
            s = s.substring(0, s.length() - 1);
            mult = 1000000000000.0;
        }

        try {
            double val = Double.parseDouble(s) * mult;
            if (Double.isNaN(val) || Double.isInfinite(val)) {
                return -1;
            }
            return val;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private ItemStack createNoteItem(String id, double amount, String owner) {
        boolean isXp = id != null && id.startsWith("XP-");
        java.util.List<String> lore = config.getStringList(isXp ? "messages.xp-note-lore" : "messages.note-lore");
        if (lore.isEmpty()) {
            lore = java.util.List.of(
                "&7Issued to: &f{player}",
                isXp ? "&7Amount: &a{amount} XP" : "&7Amount: &a{amount} {currency}",
                "",
                "&8Right-click to redeem"
            );
        }
        
        String currency = config.getString("currency", "Coins");
        String formattedAmount = TextUtil.formatNumber(amount);
        java.util.List<String> formattedLore = new java.util.ArrayList<>();
        for (String line : lore) {
            formattedLore.add(line.replace("{player}", owner)
                                  .replace("{amount}", formattedAmount)
                                  .replace("{currency}", currency));
        }

        Material material = isXp ? Material.EXPERIENCE_BOTTLE : Material.PAPER;
        String title = isXp ? config.getString("xp-note-title", "&a&lXP Note &8| &e" + formattedAmount + " XP")
                            : config.getString("note-title", "&6&lBank Note &8| &e" + formattedAmount + " " + currency);
        
        ItemStack item = TextUtil.createItem(material, title, formattedLore.toArray(new String[0]));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(noteIdKey, PersistentDataType.STRING, id);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String getNoteId(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(noteIdKey, PersistentDataType.STRING);
    }

    private void logTransaction(String action, String player, double amount, String id) {
        plugin.getLogger().info(String.format("[BankNotes] %s | Player: %s | Amount: %s | ID: %s", action, player, TextUtil.formatNumber(amount), id));
        plugin.getDatabaseManager().execute("INSERT INTO coresuite_banknotes_logs (action, player, amount, note_id, time) VALUES (?, ?, ?, ?, ?)", action, player, amount, id, System.currentTimeMillis());
    }

    private int noteSlot(Player player, String id) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            if (id.equals(getNoteId(player.getInventory().getItem(slot)))) return slot;
        }
        return -1;
    }

    private void finishReservation(String id, boolean paid) {
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                BankNoteStore.finish(conn, id, paid);
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Bank note requires reconciliation: " + id + ", paid=" + paid, e);
            } finally { activeRedeemingNotes.remove(id); }
        });
    }

    private void redeemNote(String id, Player player) {
        if (!activeRedeemingNotes.add(id)) return;
        String name = player.getName();
        plugin.getDatabaseManager().executeAsync(() -> {
            BankNoteStore.Reservation result;
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                result = BankNoteStore.reserve(conn, id, name);
            } catch (SQLException e) {
                activeRedeemingNotes.remove(id);
                plugin.getLogger().log(Level.SEVERE, "Failed to reserve note; item retained: " + id, e);
                return;
            }
            if (!plugin.isEnabled() || !isEnabled()) {
                if (result.state() == BankNoteStore.AVAILABLE) finishReservation(id, false);
                else activeRedeemingNotes.remove(id);
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (result.state() != BankNoteStore.AVAILABLE) {
                    activeRedeemingNotes.remove(id);
                    if (player.isOnline()) TextUtil.sendMessage(player, "&cThis note is invalid, already redeemed, or awaiting settlement review. ID: " + id);
                    return;
                }
                int slot = player.isOnline() && isEnabled() && net.rocky.coresuite.common.integration.AuthenticationGate.isAuthenticated(player) ? noteSlot(player, id) : -1;
                if (slot < 0) { finishReservation(id, false); return; }
                boolean xp = id.startsWith("XP-");
                double amount = result.amount();
                if (xp && (long)getTotalExperience(player) + (long)amount > Integer.MAX_VALUE) {
                    finishReservation(id, false);
                    TextUtil.sendMessage(player, "&cRedeeming this note would exceed the XP limit.");
                    return;
                }
                try {
                    boolean paid;
                    if (xp) { player.giveExp((int)amount); paid = true; }
                    else paid = plugin.getEconomyManager().deposit(player, amount);
                    if (!paid) {
                        finishReservation(id, false);
                        TextUtil.sendMessage(player, "&cPayment failed. Your note has been retained.");
                        return;
                    }
                    ItemStack item = player.getInventory().getItem(slot);
                    item.setAmount(item.getAmount() - 1);
                    player.getInventory().setItem(slot, item.getAmount() == 0 ? null : item);
                    if (xp) player.saveData();
                    finishReservation(id, true);
                    logTransaction(xp ? "REDEEM-XP" : "REDEEM", name, amount, id);
                    TextUtil.sendMessage(player, "&aRedeemed " + TextUtil.formatNumber(amount) + (xp ? " XP." : " " + config.getString("currency", "Coins") + "."));
                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1);
                } catch (RuntimeException e) {
                    activeRedeemingNotes.remove(id);
                    plugin.getLogger().log(Level.SEVERE, "Uncertain bank-note payout; reservation retained for review: " + id, e);
                    TextUtil.sendMessage(player, "&cPayment needs administrator review. Keep this note. ID: " + id);
                }
            });
        });
    }

    private final Set<UUID> activeDeliveries = ConcurrentHashMap.newKeySet();

    @EventHandler public void onJoin(org.bukkit.event.player.PlayerJoinEvent event) {
        net.rocky.coresuite.common.integration.AuthenticationGate.afterLogin(plugin, event.getPlayer(), () -> { if (isEnabled()) deliverPending(event.getPlayer()); });
    }

    private void deliverPending(Player player) {
        if (!net.rocky.coresuite.common.integration.AuthenticationGate.isAuthenticated(player)) return;
        UUID uuid = player.getUniqueId();
        if (!activeDeliveries.add(uuid)) return;
        plugin.getDatabaseManager().executeAsync(() -> {
            record Delivery(String id, double amount, String owner) {}
            java.util.List<Delivery> deliveries = new java.util.ArrayList<>();
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT n.id, n.amount, n.owner FROM coresuite_banknote_deliveries d JOIN coresuite_banknotes n ON n.id = d.note_id WHERE d.recipient = ? AND n.redeemed = 0")) {
                stmt.setString(1, uuid.toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) deliveries.add(new Delivery(rs.getString(1), rs.getDouble(2), rs.getString(3)));
                }
            } catch (SQLException e) {
                activeDeliveries.remove(uuid);
                plugin.getLogger().log(Level.SEVERE, "Failed to load bank-note deliveries for " + uuid, e);
                return;
            }
            if (!plugin.isEnabled()) { activeDeliveries.remove(uuid); return; }
            Bukkit.getScheduler().runTask(plugin, () -> {
                activeDeliveries.remove(uuid);
                if (!isEnabled() || !player.isOnline() || !net.rocky.coresuite.common.integration.AuthenticationGate.isAuthenticated(player)) return;
                java.util.List<String> deliveredIds = new java.util.ArrayList<>();
                for (Delivery delivery : deliveries) {
                    // A crash before acknowledgement can replay the item; its unique ID can only be redeemed once.
                    if (noteSlot(player, delivery.id()) < 0) {
                        if (player.getInventory().firstEmpty() < 0) {
                            TextUtil.sendMessage(player, "&eMake inventory space, then use /claimnote to collect pending notes.");
                            break;
                        }
                        player.getInventory().addItem(createNoteItem(delivery.id(), delivery.amount(), delivery.owner()));
                    }
                    deliveredIds.add(delivery.id());
                }
                if (!deliveredIds.isEmpty()) {
                    // Bukkit player persistence is a server-thread operation. Save once per batch
                    // before acknowledging delivery so a restart cannot erase issued notes.
                    player.saveData();
                    for (String id : deliveredIds) plugin.getDatabaseManager().execute("DELETE FROM coresuite_banknote_deliveries WHERE note_id = ?", id);
                }
            });
        });
    }

    private void issueNote(Player player, double amount, boolean xp, boolean admin) {
        UUID uuid = player.getUniqueId();
        if (!activeWithdrawingPlayers.add(uuid)) {
            TextUtil.sendMessage(player, "&cYour previous withdrawal is still processing.");
            return;
        }
        String id = generateNoteId(xp);
        String owner = admin ? "ADMIN" : player.getName();
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                BankNoteStore.createPending(conn, id, amount, owner, uuid.toString());
            } catch (SQLException e) {
                activeWithdrawingPlayers.remove(uuid);
                plugin.getLogger().log(Level.SEVERE, "Could not create bank note; no funds withdrawn: " + id, e);
                return;
            }
            if (!plugin.isEnabled()) { activeWithdrawingPlayers.remove(uuid); return; }
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || !isEnabled() || !net.rocky.coresuite.common.integration.AuthenticationGate.isAuthenticated(player)) {
                    plugin.getDatabaseManager().execute("DELETE FROM coresuite_banknotes WHERE id = ? AND redeemed = 3", id);
                    activeWithdrawingPlayers.remove(uuid);
                    return;
                }
                boolean paid = admin;
                try {
                    if (!admin && xp && amount <= getTotalExperience(player)) { removeExp(player, (int)amount); player.saveData(); paid = true; }
                    else if (!admin && !xp) paid = plugin.getEconomyManager().withdraw(player, amount);
                } catch (RuntimeException e) {
                    activeWithdrawingPlayers.remove(uuid);
                    plugin.getLogger().log(Level.SEVERE, "Uncertain withdrawal; pending note retained for review: " + id, e);
                    TextUtil.sendMessage(player, "&cThe payment provider returned an uncertain result. Contact an administrator with note ID " + id);
                    return;
                }
                if (!paid) {
                    plugin.getDatabaseManager().execute("DELETE FROM coresuite_banknotes WHERE id = ? AND redeemed = 3", id);
                    activeWithdrawingPlayers.remove(uuid);
                    TextUtil.sendMessage(player, "&cWithdrawal failed. No note was issued.");
                    return;
                }
                plugin.getDatabaseManager().executeAsync(() -> {
                    try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                        BankNoteStore.activate(conn, id, uuid.toString());
                    } catch (SQLException e) {
                        plugin.getLogger().log(Level.SEVERE, "Withdrawal completed but note activation requires reconciliation: " + id + ", player=" + uuid + ", amount=" + amount, e);
                        activeWithdrawingPlayers.remove(uuid);
                        return;
                    }
                    activeWithdrawingPlayers.remove(uuid);
                    logTransaction(admin ? "ADMIN-GIVE" : xp ? "WITHDRAW-XP" : "WITHDRAW", owner, amount, id);
                    if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> {
                        if (isEnabled() && player.isOnline()) {
                            if (admin) TextUtil.sendMessage(player, "&aNote issued for " + TextUtil.formatNumber(amount) + (xp ? " XP." : " " + config.getString("currency", "Coins") + "."));
                            else sendWithdrawalConfirmation(player, amount, xp);
                            deliverPending(player);
                        }
                    });
                });
            });
        });
    }

    private void sendWithdrawalConfirmation(Player player, double amount, boolean xp) {
        String currency = xp ? "XP points" : config.getString("currency", "Coins");
        String remaining;
        try {
            remaining = TextUtil.formatNumber(xp ? getTotalExperience(player) : plugin.getEconomyManager().getBalance(player));
        } catch (RuntimeException error) {
            // A balance-display failure must never interfere with delivery of an activated note.
            remaining = "unavailable";
            plugin.getLogger().log(Level.WARNING, "Could not display remaining balance for " + player.getUniqueId(), error);
        }
        java.util.List<String> lines = config.getStringList(xp ? "messages.withdraw-xp" : "messages.withdraw-money");
        if (lines.isEmpty()) lines = java.util.List.of(
                "&8[&6&lBank&8] &aWithdrawal successful!",
                "&7Withdrawn: &f{amount} {currency} &8| &7Remaining: &e{remaining} {currency}");
        for (String line : lines) TextUtil.sendMessage(player, line.replace("{amount}", TextUtil.formatNumber(amount))
                .replace("{remaining}", remaining).replace("{currency}", currency));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (!net.rocky.coresuite.common.integration.AuthenticationGate.isAuthenticated(event.getPlayer())) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        String id = getNoteId(event.getItem());
        if (id == null) return;
        event.setCancelled(true);
        redeemNote(id, event.getPlayer());
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent event) {
        ItemStack item = event.getInventory().getItem(0);
        if (item != null && getNoteId(item) != null) {
            event.setResult(new ItemStack(Material.AIR));
        }
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String promptType = pendingPrompts.remove(player.getUniqueId());
        if (promptType != null) {
            event.setCancelled(true);
            String message = event.getMessage();
            if (message.equalsIgnoreCase("cancel")) {
                Bukkit.getScheduler().runTask(plugin, () -> { if (player.isOnline()) TextUtil.sendMessage(player, "&8[&6&lBank&8] &r&cCancelled."); });
                return;
            }
            
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!isEnabled() || !player.isOnline() || !net.rocky.coresuite.common.integration.AuthenticationGate.isAuthenticated(player)) return;
                if (promptType.equals("xp")) {
                    player.performCommand("withdraw xp " + message);
                } else {
                    player.performCommand("withdraw " + message);
                }
            });
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pendingPrompts.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player && !net.rocky.coresuite.common.integration.AuthenticationGate.isAuthenticated(player)) { TextUtil.sendMessage(player, "&cPlease log in before using bank notes."); return true; }
        String cmd = command.getName().toLowerCase();
        
        String prefix = config.getString("prefix", "&8[&6&lBank&8] &r");
        
        if (cmd.equals("bank") || cmd.equals("withdraw")) {
            if (!(sender instanceof Player player)) return true;
            if (!player.hasPermission("coresuite.banknotes.withdraw")) {
                TextUtil.sendMessage(player, prefix + "&cYou don't have permission to use this command.");
                return true;
            }
            return handleWithdrawOrBank(player, args, prefix);
        }

        if (cmd.equals("withdrawlog")) {
            if (!(sender instanceof Player player)) {
                TextUtil.sendMessage(sender, prefix + "&cThis command can only be run by players in-game.");
                return true;
            }
            if (!player.hasPermission("coresuite.banknotes.withdraw") && !player.hasPermission("coresuite.banknotes.withdrawlog") && !player.hasPermission("coresuite.banknotes.admin")) {
                TextUtil.sendMessage(player, prefix + "&cNo permission.");
                return true;
            }
            
            int page = 1;
            if (args.length > 0) {
                try {
                    page = Math.max(1, Integer.parseInt(args[0]));
                } catch (NumberFormatException ignored) {}
            }
            openTransactionLogGui(player, page);
            return true;
        }

        if (cmd.equals("withdrawadmin")) {
            if (!sender.hasPermission("coresuite.banknotes.admin")) {
                TextUtil.sendMessage(sender, "&8[&6&lBank&8] &r&cNo permission.");
                return true;
            }
            if (args.length == 1 && args[0].equalsIgnoreCase("pending")) {
                plugin.getDatabaseManager().executeAsync(() -> {
                    java.util.List<String> messages = new java.util.ArrayList<>();
                    try (Connection conn = plugin.getDatabaseManager().getConnection();
                         PreparedStatement stmt = conn.prepareStatement("SELECT id, amount, owner, redeemed, redeemed_by FROM coresuite_banknotes WHERE redeemed IN (2, 3) ORDER BY issue_time DESC LIMIT 25");
                         ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) messages.add("&e" + rs.getString("id") + " &7| " + (rs.getInt("redeemed") == 2 ? "redemption" : "issuance") + " | " + rs.getDouble("amount") + " | owner=" + rs.getString("owner") + " | redeemer=" + rs.getString("redeemed_by"));
                    } catch (SQLException e) {
                        plugin.getLogger().log(Level.SEVERE, "Failed to read pending bank settlements", e);
                        messages.add("&cCould not read pending settlements.");
                    }
                    if (messages.isEmpty()) messages.add("&aNo pending settlements.");
                    if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> messages.forEach(message -> TextUtil.sendMessage(sender, message)));
                });
                return true;
            }
            if (args.length < 3 || !args[0].equalsIgnoreCase("give")) {
                TextUtil.sendMessage(sender, "&8[&6&lBank&8] &r&eUsage: &f/withdrawadmin give <player> <amount> &7or &f/withdrawadmin pending");
                return true;
            }

            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                TextUtil.sendMessage(sender, "&8[&6&lBank&8] &r&cPlayer not found.");
                return true;
            }

            double amount = Math.floor(parseAmount(args[2]));
            if (amount <= 0) {
                TextUtil.sendMessage(sender, "&8[&6&lBank&8] &r&cInvalid amount.");
                return true;
            }

            issueNote(target, amount, false, true);
            TextUtil.sendMessage(sender, "&eNote issuance queued for " + target.getName() + ".");
            return true;
        }

        if (cmd.equals("claimnote")) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            
            if (!player.hasPermission("coresuite.banknotes.withdraw")) {
                TextUtil.sendMessage(player, "&8[&6&lBank&8] &r&cYou don't have permission to claim notes.");
                return true;
            }

            deliverPending(player);
            java.util.Set<String> ids = new java.util.LinkedHashSet<>();
            for (ItemStack item : player.getInventory().getContents()) {
                String id = getNoteId(item);
                if (id != null) ids.add(id);
            }
            for (String id : ids) redeemNote(id, player);
            if (ids.isEmpty()) TextUtil.sendMessage(player, "&7Checking for pending notes. Right-click a delivered note to redeem it.");
            return true;
        }

        return false;
    }

    private boolean handleWithdrawOrBank(Player player, String[] args, String prefix) {
        if (args.length == 0 || args[0].equalsIgnoreCase("gui") || args[0].equalsIgnoreCase("menu")) {
            openBankGui(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("help")) {
            TextUtil.sendMessage(player, prefix + "&eBank & Withdrawal Commands:");
            TextUtil.sendMessage(player, "  &b/withdraw &7or &b/bank &7- Open the Bank & Withdrawal Menu");
            TextUtil.sendMessage(player, "  &b/withdraw <amount> &7- Withdraw money as a physical note");
            TextUtil.sendMessage(player, "  &b/withdraw xp <amount> &7- Withdraw experience into a bottle");
            TextUtil.sendMessage(player, "  &b/claimnote &7- Redeem all notes in your inventory");
            TextUtil.sendMessage(player, "  &b/withdrawlog &7- View transaction history ledger");
            return true;
        }

        boolean isXp = false;
        String amountStr = args[0];
        if (args[0].equalsIgnoreCase("xp") && args.length > 1) {
            isXp = true;
            amountStr = args[1];
        }

        double amount = parseAmount(amountStr);
        if (amount <= 0) {
            TextUtil.sendMessage(player, prefix + "&cInvalid amount.");
            return true;
        }

        amount = Math.floor(amount);
        double min = config.getDouble(isXp ? "min-withdraw-xp" : "min-withdraw", 1);
        double max = config.getDouble(isXp ? "max-withdraw-xp" : "max-withdraw", 1000000000000.0);

        if (amount < min) {
            TextUtil.sendMessage(player, prefix + "&cMinimum withdrawal: &f" + TextUtil.formatNumber(min));
            return true;
        }
        if (amount > max) {
            TextUtil.sendMessage(player, prefix + "&cMaximum per note: &f" + TextUtil.formatNumber(max));
            return true;
        }

        if (amount <= 0 || (isXp && amount > Integer.MAX_VALUE)) {
            TextUtil.sendMessage(player, prefix + "&cInvalid note amount.");
            return true;
        }
        issueNote(player, amount, isXp, false);
        return true;
    }

    public void openBankGui(Player player) {
        String title = config.getString("gui-title", "&6&lBank &7» &eWithdraw & Manage");
        Gui gui = new Gui(title, 5);
        gui.fillBorder(Material.GRAY_STAINED_GLASS_PANE);

        double balance = plugin.getEconomyManager() != null ? plugin.getEconomyManager().getBalance(player) : 0.0;
        int totalXp = getTotalExperience(player);
        int level = player.getLevel();

        // Slot 4: Player Profile & Financial Summary Head
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.displayName(TextUtil.colorize("&6&lAccount Summary &8| &e" + player.getName()));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.colorize("&8─────────────────────────"));
            lore.add(TextUtil.colorize("&7Economy Balance: &6$" + TextUtil.formatNumber(balance)));
            lore.add(TextUtil.colorize("&7Experience Level: &aLevel " + level));
            lore.add(TextUtil.colorize("&7Total XP Points: &a" + TextUtil.formatNumber(totalXp) + " XP"));
            lore.add(TextUtil.colorize("&8─────────────────────────"));
            lore.add(TextUtil.colorize("&e▶ Click to view transaction ledger"));
            meta.lore(lore);
            head.setItemMeta(meta);
        }
        gui.setItem(4, new GuiButton(head, e -> {
            player.closeInventory();
            openTransactionLogGui(player, 1);
        }));

        // Slot 9: Currency Category Label
        gui.setItem(9, new GuiButton(
            TextUtil.createItem(Material.GOLD_INGOT, "&6&lCurrency Withdrawal",
                "&7Withdraw money from your balance",
                "&7into signed physical banknotes.",
                "",
                "&7Your Balance: &6$" + TextUtil.formatNumber(balance)
            ),
            e -> {}
        ));

        // Slots 10-15: Currency Denominations
        List<Map<?, ?>> denominations = config.getMapList("gui.denominations");
        int slot = 10;
        for (Map<?, ?> denomMap : denominations) {
            if (slot > 15) break;
            
            String amountStr = String.valueOf(denomMap.get("amount"));
            double amountVal = parseAmount(amountStr);
            String materialStr = (String) denomMap.get("material");
            String displayName = (String) denomMap.get("display-name");
            
            Material mat = Material.matchMaterial(materialStr != null ? materialStr : "SUNFLOWER");
            if (mat == null) mat = Material.SUNFLOWER;

            boolean canAfford = balance >= amountVal;
            List<String> lore = new ArrayList<>();
            lore.add("&8─────────────────────────");
            lore.add("&7Withdrawal: &6$" + TextUtil.formatNumber(amountVal));
            lore.add("&7Your Balance: &f$" + TextUtil.formatNumber(balance));
            lore.add("&8─────────────────────────");
            lore.add(canAfford ? "&a✔ Click to withdraw note" : "&c✖ Insufficient balance");
            
            gui.setItem(slot, new GuiButton(
                TextUtil.createItem(mat, displayName, lore.toArray(String[]::new)),
                e -> player.performCommand("withdraw " + amountStr)
            ));
            slot++;
        }
        
        // Slot 16: Custom Amount
        gui.setItem(16, new GuiButton(
            TextUtil.createItem(Material.WRITABLE_BOOK, "&e&lCustom Currency Amount",
                "&8─────────────────────────",
                "&7Click to type a custom amount",
                "&7in chat to withdraw.",
                "",
                "&7Current Balance: &6$" + TextUtil.formatNumber(balance),
                "&8─────────────────────────",
                "&a▶ Click to enter amount"
            ),
            e -> {
                player.closeInventory();
                pendingPrompts.put(player.getUniqueId(), "money");
                TextUtil.sendMessage(player, "&6&lBank &8» &7Enter the amount of money to withdraw in chat (or type 'cancel').");
            }
        ));

        // Slot 18: Experience Category Label
        gui.setItem(18, new GuiButton(
            TextUtil.createItem(Material.EXPERIENCE_BOTTLE, "&a&lExperience Withdrawal",
                "&7Convert your XP points into",
                "&7redeemable experience bottles.",
                "",
                "&7Current XP: &a" + TextUtil.formatNumber(totalXp) + " XP",
                "&7Current Level: &aLevel " + level
            ),
            e -> {}
        ));
        
        // Slots 19-24: XP Denominations
        List<Map<?, ?>> xpDenoms = config.getMapList("gui.xp-denominations");
        if (xpDenoms.isEmpty()) {
            xpDenoms = List.of(
                Map.of("amount", 100, "material", "EXPERIENCE_BOTTLE", "display-name", "&a100 XP"),
                Map.of("amount", 500, "material", "EXPERIENCE_BOTTLE", "display-name", "&a500 XP"),
                Map.of("amount", 1000, "material", "EXPERIENCE_BOTTLE", "display-name", "&a1,000 XP"),
                Map.of("amount", 5000, "material", "EXPERIENCE_BOTTLE", "display-name", "&a5,000 XP"),
                Map.of("amount", 10000, "material", "EXPERIENCE_BOTTLE", "display-name", "&a10,000 XP")
            );
        }
        int xpSlot = 19;
        for (Map<?, ?> denomMap : xpDenoms) {
            if (xpSlot > 24) break;
            
            String amountStr = String.valueOf(denomMap.get("amount"));
            double amountVal = parseAmount(amountStr);
            String materialStr = (String) denomMap.get("material");
            String displayName = (String) denomMap.get("display-name");
            
            Material mat = Material.matchMaterial(materialStr != null ? materialStr : "EXPERIENCE_BOTTLE");
            if (mat == null) mat = Material.EXPERIENCE_BOTTLE;

            boolean canAfford = totalXp >= amountVal;
            List<String> lore = new ArrayList<>();
            lore.add("&8─────────────────────────");
            lore.add("&7Withdrawal: &a" + TextUtil.formatNumber(amountVal) + " XP");
            lore.add("&7Your XP: &f" + TextUtil.formatNumber(totalXp) + " XP");
            lore.add("&8─────────────────────────");
            lore.add(canAfford ? "&a✔ Click to withdraw XP bottle" : "&c✖ Insufficient XP");
            
            gui.setItem(xpSlot, new GuiButton(
                TextUtil.createItem(mat, displayName, lore.toArray(String[]::new)),
                e -> player.performCommand("withdraw xp " + amountStr)
            ));
            xpSlot++;
        }

        // Slot 25: Custom XP Amount
        gui.setItem(25, new GuiButton(
            TextUtil.createItem(Material.WRITABLE_BOOK, "&a&lCustom XP Amount",
                "&8─────────────────────────",
                "&7Click to type a custom XP amount",
                "&7in chat to withdraw.",
                "",
                "&7Current XP: &a" + TextUtil.formatNumber(totalXp) + " XP",
                "&8─────────────────────────",
                "&a▶ Click to enter XP"
            ),
            e -> {
                player.closeInventory();
                pendingPrompts.put(player.getUniqueId(), "xp");
                TextUtil.sendMessage(player, "&6&lBank &8» &7Enter the amount of XP to withdraw in chat (or type 'cancel').");
            }
        ));

        // Bottom Controls (Row 5)
        // Slot 38: Transaction History / Ledger
        gui.setItem(38, new GuiButton(
            TextUtil.createItem(Material.WRITTEN_BOOK, "&e&lTransaction History",
                "&8─────────────────────────",
                "&7View your full history of",
                "&7deposits, withdrawals, and",
                "&7note redemptions.",
                "&8─────────────────────────",
                "&e▶ Click to view ledger"
            ),
            e -> {
                player.closeInventory();
                openTransactionLogGui(player, 1);
            }
        ));

        // Slot 40: Claim Notes
        gui.setItem(40, new GuiButton(
            TextUtil.createItem(Material.NAME_TAG, "&d&lClaim Notes",
                "&8─────────────────────────",
                "&7Redeem all Bank Notes and",
                "&7XP Bottles in your inventory.",
                "",
                "&a✔ Works even if a note",
                "&awas renamed in an anvil",
                "&8─────────────────────────",
                "&d▶ Click to claim all"
            ),
            e -> player.performCommand("claimnote")
        ));

        // Slot 42: Close Menu
        gui.setItem(42, new GuiButton(
            TextUtil.createItem(Material.BARRIER, "&c&lClose Menu", "&7Click to exit."),
            e -> {
                player.closeInventory();
                player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.0f);
            }
        ));

        gui.open(player);
    }

    public void openTransactionLogGui(Player player, int page) {
        int pageSize = 21;
        int offset = Math.max(0, page - 1) * pageSize;
        String playerName = player.getName();

        plugin.getDatabaseManager().executeAsync(() -> {
            List<TransactionLogEntry> entries = new ArrayList<>();
            int totalCount = 0;
            try (Connection conn = plugin.getDatabaseManager().getConnection()) {
                try (PreparedStatement countStmt = conn.prepareStatement(
                        "SELECT COUNT(*) FROM coresuite_banknotes_logs WHERE player = ?")) {
                    countStmt.setString(1, playerName);
                    try (ResultSet rs = countStmt.executeQuery()) {
                        if (rs.next()) totalCount = rs.getInt(1);
                    }
                }

                try (PreparedStatement stmt = conn.prepareStatement(
                        "SELECT action, amount, note_id, time FROM coresuite_banknotes_logs WHERE player = ? ORDER BY id DESC LIMIT ? OFFSET ?")) {
                    stmt.setString(1, playerName);
                    stmt.setInt(2, pageSize);
                    stmt.setInt(3, offset);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            entries.add(new TransactionLogEntry(
                                    rs.getString("action"),
                                    rs.getDouble("amount"),
                                    rs.getString("note_id"),
                                    rs.getLong("time")
                            ));
                        }
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load transaction logs for " + playerName, e);
            }

            final int totalTransactions = totalCount;
            final int totalPages = Math.max(1, (int) Math.ceil((double) totalCount / pageSize));
            final int currentPage = Math.min(page, totalPages);

            if (!plugin.isEnabled()) return;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || !isEnabled()) return;
                Gui gui = new Gui("&8Bank Ledger &7» &ePage " + currentPage + "/" + totalPages, 6);
                gui.fillBorder(Material.GRAY_STAINED_GLASS_PANE);

                double balance = plugin.getEconomyManager() != null ? plugin.getEconomyManager().getBalance(player) : 0.0;
                int totalXp = getTotalExperience(player);
                int level = player.getLevel();

                // Slot 4: Player summary head
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta meta = (SkullMeta) head.getItemMeta();
                if (meta != null) {
                    meta.setOwningPlayer(player);
                    meta.displayName(TextUtil.colorize("&6&l" + player.getName() + "'s Financial Records"));
                    List<Component> lore = new ArrayList<>();
                    lore.add(TextUtil.colorize("&8─────────────────────────"));
                    lore.add(TextUtil.colorize("&7Current Balance: &6$" + TextUtil.formatNumber(balance)));
                    lore.add(TextUtil.colorize("&7Current Level: &aLevel " + level));
                    lore.add(TextUtil.colorize("&7Current XP: &a" + TextUtil.formatNumber(totalXp) + " XP"));
                    lore.add(TextUtil.colorize("&7Recorded Transactions: &f" + totalTransactions));
                    lore.add(TextUtil.colorize("&8─────────────────────────"));
                    meta.lore(lore);
                    head.setItemMeta(meta);
                }
                gui.setItem(4, new GuiButton(head, e -> {}));

                int[] slots = {
                        10, 11, 12, 13, 14, 15, 16,
                        19, 20, 21, 22, 23, 24, 25,
                        28, 29, 30, 31, 32, 33, 34
                };

                if (entries.isEmpty()) {
                    ItemStack emptyItem = TextUtil.createItem(Material.MAP, "&7&lNo Transactions Recorded",
                            "&7You have no recorded bank transactions yet.",
                            "&7Withdraw or redeem notes to build your ledger!");
                    gui.setItem(22, new GuiButton(emptyItem, e -> {}));
                } else {
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm");
                    for (int i = 0; i < entries.size() && i < slots.length; i++) {
                        TransactionLogEntry entry = entries.get(i);
                        Material mat;
                        String actionTitle;
                        String actionColor;
                        boolean isXp = entry.action().contains("XP");

                        if (entry.action().contains("XP")) {
                            if (entry.action().startsWith("WITHDRAW")) {
                                mat = Material.EXPERIENCE_BOTTLE;
                                actionTitle = "&e&lXP Withdrawal";
                                actionColor = "&e";
                            } else {
                                mat = Material.ENCHANTED_BOOK;
                                actionTitle = "&b&lXP Redeemed";
                                actionColor = "&b";
                            }
                        } else if (entry.action().startsWith("ADMIN")) {
                            mat = Material.NETHER_STAR;
                            actionTitle = "&d&lAdmin Grant";
                            actionColor = "&d";
                        } else if (entry.action().startsWith("WITHDRAW")) {
                            mat = Material.PAPER;
                            actionTitle = "&c&lCash Withdrawal";
                            actionColor = "&c";
                        } else {
                            mat = Material.EMERALD;
                            actionTitle = "&a&lNote Redeemed";
                            actionColor = "&a";
                        }

                        String amountStr = isXp ? TextUtil.formatNumber(entry.amount()) + " XP" : "$" + TextUtil.formatNumber(entry.amount());

                        ItemStack item = TextUtil.createItem(mat, actionTitle,
                                "&8─────────────────────────",
                                "&7Type: " + actionColor + entry.action(),
                                "&7Amount: &f" + amountStr,
                                "&7Date: &f" + sdf.format(new java.util.Date(entry.time())),
                                "&7Note ID: &8" + (entry.noteId() != null ? entry.noteId() : "N/A"),
                                "&8─────────────────────────"
                        );
                        gui.setItem(slots[i], new GuiButton(item, e -> {}));
                    }
                }

                // Page indicator (Slot 49)
                ItemStack pageIndicator = TextUtil.createItem(Material.COMPASS, "&e&lPage " + currentPage + " / " + totalPages,
                        "&7Total records: &f" + totalTransactions);
                gui.setItem(49, new GuiButton(pageIndicator, e -> {}));

                // Previous page
                if (currentPage > 1) {
                    ItemStack prev = TextUtil.createItem(Material.ARROW, "&a◀ Previous Page", "&7Go to page " + (currentPage - 1));
                    gui.setItem(48, new GuiButton(prev, e -> openTransactionLogGui(player, currentPage - 1)));
                }

                // Next page
                if (currentPage < totalPages) {
                    ItemStack next = TextUtil.createItem(Material.ARROW, "&aNext Page ▶", "&7Go to page " + (currentPage + 1));
                    gui.setItem(50, new GuiButton(next, e -> openTransactionLogGui(player, currentPage + 1)));
                }

                // Back to Bank button
                ItemStack back = TextUtil.createItem(Material.BARRIER, "&c◀ Back to Bank", "&7Return to Bank Menu");
                gui.setItem(45, new GuiButton(back, e -> openBankGui(player)));

                gui.open(player);
            });
        });
    }

    private record TransactionLogEntry(String action, double amount, String noteId, long time) {}

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        java.util.List<String> completions = new java.util.ArrayList<>();
        if (command.getName().equalsIgnoreCase("withdrawadmin")) {
            if (args.length == 1) {
                org.bukkit.util.StringUtil.copyPartialMatches(args[0], java.util.List.of("give", "pending"), completions);
                java.util.Collections.sort(completions);
                return completions;
            } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
                return null; // Suggests online players natively
            } else if (args.length == 3) {
                return java.util.Collections.emptyList();
            }
        } else if (command.getName().equalsIgnoreCase("withdraw")) {
            if (args.length == 1) {
                java.util.List<String> list = new java.util.ArrayList<>();
                list.add("xp");
                org.bukkit.util.StringUtil.copyPartialMatches(args[0], list, completions);
                return completions;
            }
        }
        return java.util.Collections.emptyList();
    }
}
