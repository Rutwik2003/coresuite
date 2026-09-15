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
import org.bukkit.persistence.PersistentDataType;

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
        
        String autoInc = plugin.getDatabaseManager().isMySQL() ? "AUTO_INCREMENT" : "AUTOINCREMENT";
        plugin.getDatabaseManager().execute("CREATE TABLE IF NOT EXISTS coresuite_banknotes_logs (id INTEGER PRIMARY KEY " + autoInc + ", action TEXT, player TEXT, amount REAL, note_id TEXT, time INTEGER)");
        
        int maxEntries = config.getInt("max-log-entries", 10000);
        plugin.getDatabaseManager().execute(
            "DELETE FROM coresuite_banknotes_logs WHERE id NOT IN " +
            "(SELECT id FROM coresuite_banknotes_logs ORDER BY id DESC LIMIT ?)", 
            maxEntries
        );
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
                            TextUtil.sendMessage(player, "&aNote issued for " + TextUtil.formatNumber(amount) + (xp ? " XP." : " " + config.getString("currency", "Coins") + "."));
                            deliverPending(player);
                        }
                    });
                });
            });
        });
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
        
        if (cmd.equals("bank")) {
            if (!(sender instanceof Player)) return true;
            if (!sender.hasPermission("coresuite.banknotes.withdraw")) {
                TextUtil.sendMessage(sender, prefix + "&cNo permission.");
                return true;
            }
            openBankGui((Player) sender);
            return true;
        }

        if (cmd.equals("withdrawlog")) {
            if (!(sender instanceof Player player)) return true;
            if (!player.hasPermission("coresuite.banknotes.withdrawlog")) {
                TextUtil.sendMessage(player, "&8[&6&lBank&8] &r&cNo permission.");
                return true;
            }
            
            String playerName = player.getName();
            plugin.getDatabaseManager().executeAsync(() -> {
                java.util.List<String> messages = new java.util.ArrayList<>();
                messages.add("&8[&6&lBank&8] &r&7Recent Transactions:");
                try (Connection conn = plugin.getDatabaseManager().getConnection();
                     PreparedStatement stmt = conn.prepareStatement("SELECT action, amount, time FROM coresuite_banknotes_logs WHERE player = ? ORDER BY id DESC LIMIT 10")) {
                    stmt.setString(1, playerName);
                    try (ResultSet rs = stmt.executeQuery()) {
                        while (rs.next()) {
                            String time = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(rs.getLong("time")));
                            messages.add("&8 - &e" + time + " &7| &a" + rs.getString("action") + " &7| &f" + TextUtil.formatNumber(rs.getDouble("amount")));
                        }
                    }
                    if (messages.size() == 1) messages.add("&7No recent transactions found.");
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to get bank logs", e);
                    messages.add("&cTransaction history is temporarily unavailable.");
                }
                if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> {
                    if (isEnabled() && player.isOnline()) messages.forEach(message -> TextUtil.sendMessage(player, message));
                });
            });
            return true;
        }

        if (cmd.equals("withdraw")) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            
            if (!player.hasPermission("coresuite.banknotes.withdraw")) {
                TextUtil.sendMessage(player, "&8[&6&lBank&8] &r&cYou don't have permission to withdraw.");
                return true;
            }

            if (args.length == 0) {
                TextUtil.sendMessage(player, "&8[&6&lBank&8] &r&eUsage: &f/withdraw <amount> &eor &f/withdraw xp <amount>");
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
                TextUtil.sendMessage(player, "&8[&6&lBank&8] &r&cInvalid amount.");
                return true;
            }

            amount = Math.floor(amount);
            double min = config.getDouble(isXp ? "min-withdraw-xp" : "min-withdraw", 1);
            double max = config.getDouble(isXp ? "max-withdraw-xp" : "max-withdraw", 1000000000000.0);

            if (amount < min) {
                TextUtil.sendMessage(player, "&8[&6&lBank&8] &r&cMinimum withdrawal: &f" + TextUtil.formatNumber(min));
                return true;
            }
            if (amount > max) {
                TextUtil.sendMessage(player, "&8[&6&lBank&8] &r&cMaximum per note: &f" + TextUtil.formatNumber(max));
                return true;
            }

            if (amount <= 0 || (isXp && amount > Integer.MAX_VALUE)) {
                TextUtil.sendMessage(player, "&cInvalid note amount.");
                return true;
            }
            issueNote(player, amount, isXp, false);
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

    private void openBankGui(Player player) {
        Gui gui = new Gui(config.getString("gui-title", "&6&lBank Menu"), 4);
        
        gui.fillBorder(Material.GRAY_STAINED_GLASS_PANE);

        java.util.List<java.util.Map<?, ?>> denominations = config.getMapList("gui.denominations");
        int slot = 10;
        for (java.util.Map<?, ?> denomMap : denominations) {
            if (slot == 13) slot = 14;
            if (slot > 16) break;
            
            String amountStr = String.valueOf(denomMap.get("amount"));
            String materialStr = (String) denomMap.get("material");
            String displayName = (String) denomMap.get("display-name");
            
            Material mat = Material.matchMaterial(materialStr != null ? materialStr : "SUNFLOWER");
            if (mat == null) mat = Material.SUNFLOWER;
            
            gui.setItem(slot, new GuiButton(
                TextUtil.createItem(mat, displayName, "&7Click to withdraw " + amountStr),
                e -> player.performCommand("withdraw " + amountStr)
            ));
            slot++;
        }
        
        gui.setItem(16, new GuiButton(
            TextUtil.createItem(Material.WRITABLE_BOOK, "&e&lCustom Amount", "&7Click to enter a custom", "&7amount in chat."),
            e -> {
                player.closeInventory();
                pendingPrompts.put(player.getUniqueId(), "money");
                TextUtil.sendMessage(player, "&6&lBank &8» &7Enter the amount of money to withdraw in chat (or type 'cancel').");
            }
        ));
        
        java.util.List<java.util.Map<?, ?>> xpDenoms = config.getMapList("gui.xp-denominations");
        if (xpDenoms.isEmpty()) {
            xpDenoms = java.util.List.of(
                java.util.Map.of("amount", 100, "material", "EXPERIENCE_BOTTLE", "display-name", "&a100 XP"),
                java.util.Map.of("amount", 1000, "material", "EXPERIENCE_BOTTLE", "display-name", "&a1000 XP"),
                java.util.Map.of("amount", 5000, "material", "EXPERIENCE_BOTTLE", "display-name", "&a5000 XP")
            );
        }
        int xpSlot = 19;
        for (java.util.Map<?, ?> denomMap : xpDenoms) {
            if (xpSlot == 22) xpSlot = 23;
            if (xpSlot > 25) break;
            
            String amountStr = String.valueOf(denomMap.get("amount"));
            String materialStr = (String) denomMap.get("material");
            String displayName = (String) denomMap.get("display-name");
            
            Material mat = Material.matchMaterial(materialStr != null ? materialStr : "EXPERIENCE_BOTTLE");
            if (mat == null) mat = Material.EXPERIENCE_BOTTLE;
            
            gui.setItem(xpSlot, new GuiButton(
                TextUtil.createItem(mat, displayName, "&7Click to withdraw " + amountStr + " XP"),
                e -> player.performCommand("withdraw xp " + amountStr)
            ));
            xpSlot++;
        }

        gui.setItem(25, new GuiButton(
            TextUtil.createItem(Material.WRITABLE_BOOK, "&a&lCustom XP Amount", "&7Click to enter a custom", "&7XP amount in chat."),
            e -> {
                player.closeInventory();
                pendingPrompts.put(player.getUniqueId(), "xp");
                TextUtil.sendMessage(player, "&6&lBank &8» &7Enter the amount of XP to withdraw in chat (or type 'cancel').");
            }
        ));
        
        gui.setItem(31, new GuiButton(
            TextUtil.createItem(Material.NAME_TAG, "&d&lClaim Notes", "&7Redeem all Bank Notes", "&7in your inventory", "", "&a✔ Works even if a note", "&awas renamed"),
            e -> player.performCommand("claimnote")
        ));

        gui.open(player);
    }

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
