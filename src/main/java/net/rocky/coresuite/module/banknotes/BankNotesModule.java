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
    private final Map<UUID, String> pendingPrompts = new HashMap<>();

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
        int level = player.getLevel();
        int exp = Math.round(getExpToNext(level) * player.getExp());
        
        while (level > 0) {
            level--;
            exp += getExpToNext(level);
        }
        return exp;
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
                "id TEXT PRIMARY KEY, " +
                "amount REAL NOT NULL, " +
                "owner TEXT NOT NULL, " +
                "redeemed BOOLEAN NOT NULL DEFAULT 0, " +
                "redeemed_by TEXT, " +
                "issue_time INTEGER NOT NULL, " +
                "redeem_time INTEGER)";
        plugin.getDatabaseManager().execute(sql);
        
        plugin.getDatabaseManager().execute("CREATE TABLE IF NOT EXISTS coresuite_banknotes_logs (id INTEGER PRIMARY KEY AUTOINCREMENT, action TEXT, player TEXT, amount REAL, note_id TEXT, time INTEGER)");
        
        int maxEntries = config.getInt("max-log-entries", 10000);
        plugin.getDatabaseManager().execute(
            "DELETE FROM coresuite_banknotes_logs WHERE id NOT IN " +
            "(SELECT id FROM coresuite_banknotes_logs ORDER BY id DESC LIMIT ?)", 
            maxEntries
        );
    }

    private String generateNoteId(boolean isXp) {
        String prefix = isXp ? "XP-" : "BN-";
        return prefix + UUID.randomUUID().toString().substring(0, 8).toUpperCase() + "-" + (100000 + (int)(Math.random() * 899999));
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

    // Synchronous redemption to prevent dupes natively via SQLite locks
    // returns -1 if invalid, -2 if already redeemed, >=0 if successful amount
    private double redeemNote(String id, Player redeemer) {
        if (id == null || id.isEmpty()) return -1;

        try (Connection conn = plugin.getDatabaseManager().getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Check note
                double amount = -1;
                boolean redeemed = false;
                try (PreparedStatement stmt = conn.prepareStatement("SELECT amount, redeemed FROM coresuite_banknotes WHERE id = ?")) {
                    stmt.setString(1, id);
                    try (ResultSet rs = stmt.executeQuery()) {
                        if (rs.next()) {
                            amount = rs.getDouble("amount");
                            redeemed = rs.getBoolean("redeemed");
                        } else {
                            return -1;
                        }
                    }
                }

                if (redeemed) {
                    logTransaction("DUPE-ATTEMPT", redeemer.getName(), amount, id);
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        if (p.hasPermission("coresuite.banknotes.admin")) {
                            TextUtil.sendMessage(p, "&8[&4&lBank Alert&8] &c" + redeemer.getName() + " tried to redeem an ALREADY-REDEEMED note (ID: " + id + ").");
                        }
                    }
                    return -2; // confiscate
                }

                // Mark redeemed
                try (PreparedStatement stmt = conn.prepareStatement("UPDATE coresuite_banknotes SET redeemed = 1, redeemed_by = ?, redeem_time = ? WHERE id = ?")) {
                    stmt.setString(1, redeemer.getName());
                    stmt.setLong(2, System.currentTimeMillis());
                    stmt.setString(3, id);
                    stmt.executeUpdate();
                }

                conn.commit();

                // Give money
                boolean isXp = id.startsWith("XP-");
                if (isXp) {
                    redeemer.giveExp((int) amount);
                } else {
                    plugin.getEconomyManager().deposit(redeemer, amount);
                }
                logTransaction(isXp ? "REDEEM-XP" : "REDEEM", redeemer.getName(), amount, id);

                return amount;

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }

        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to redeem note: " + id, e);
            return -1;
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        String id = getNoteId(item);
        
        if (id != null) {
            String prefix = config.getString("prefix", "&8[&6&lBank&8] &r");
            event.setCancelled(true);
            double result = redeemNote(id, player);
            if (result == -1) {
                // Not in DB? Ignore or say invalid.
                TextUtil.sendMessage(player, prefix + "&cThis note is invalid or corrupted.");
                return;
            } else if (result == -2) {
                TextUtil.sendMessage(player, prefix + "&cThis note was already redeemed! Confiscating.");
                item.setAmount(item.getAmount() - 1);
                return;
            }

            item.setAmount(item.getAmount() - 1);
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            
            if (id.startsWith("XP-")) {
                String depositSuccess = config.getString("messages.deposit-success-xp", "&aRedeemed &f{amount} XP&a!");
                depositSuccess = depositSuccess.replace("{amount}", TextUtil.formatNumber(result));
                TextUtil.sendMessage(player, prefix + depositSuccess);
                TextUtil.sendMessage(player, prefix + "&7New XP: &f" + TextUtil.formatNumber(getTotalExperience(player)));
            } else {
                String depositSuccess = config.getString("messages.deposit-success", "&aRedeemed &f{amount} {currency}&a!");
                depositSuccess = depositSuccess.replace("{amount}", TextUtil.formatNumber(result)).replace("{currency}", config.getString("currency", "Coins"));
                TextUtil.sendMessage(player, prefix + depositSuccess);
                TextUtil.sendMessage(player, prefix + "&7New balance: &f" + plugin.getEconomyManager().format(plugin.getEconomyManager().getBalance(player)));
            }
        }
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
        String promptType = pendingPrompts.get(player.getUniqueId());
        if (promptType != null) {
            event.setCancelled(true);
            pendingPrompts.remove(player.getUniqueId());
            
            String message = event.getMessage();
            if (message.equalsIgnoreCase("cancel")) {
                TextUtil.sendMessage(player, "&8[&6&lBank&8] &r&cCancelled.");
                return;
            }
            
            Bukkit.getScheduler().runTask(plugin, () -> {
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
            
            plugin.getDatabaseManager().executeAsync(() -> {
                try (Connection conn = plugin.getDatabaseManager().getConnection();
                     PreparedStatement stmt = conn.prepareStatement("SELECT * FROM coresuite_banknotes_logs WHERE player = ? ORDER BY id DESC LIMIT 10")) {
                    stmt.setString(1, player.getName());
                    try (ResultSet rs = stmt.executeQuery()) {
                        TextUtil.sendMessage(player, "&8[&6&lBank&8] &r&7Recent Transactions:");
                        boolean found = false;
                        while (rs.next()) {
                            found = true;
                            String action = rs.getString("action");
                            double amount = rs.getDouble("amount");
                            String time = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(new java.util.Date(rs.getLong("time")));
                            TextUtil.sendMessage(player, "&8 - &e" + time + " &7| &a" + action + " &7| &f" + TextUtil.formatNumber(amount));
                        }
                        if (!found) {
                            TextUtil.sendMessage(player, prefix + config.getString("messages.no-notes-found", "&7No recent transactions found."));
                        }
                    }
                } catch (SQLException e) {
                    plugin.getLogger().log(Level.SEVERE, "Failed to get logs", e);
                }
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

            if (isXp) {
                int totalExp = getTotalExperience(player);
                if (totalExp < amount) {
                    String failMsg = config.getString("messages.withdraw-fail-xp", "&cNeed &f{amount} XP&c but only have &f{balance} XP");
                    failMsg = failMsg.replace("{amount}", TextUtil.formatNumber(amount)).replace("{balance}", TextUtil.formatNumber(totalExp));
                    TextUtil.sendMessage(player, prefix + failMsg);
                    return true;
                }
                removeExp(player, (int) amount);
            } else {
                EconomyManager eco = plugin.getEconomyManager();
                if (!eco.has(player, amount)) {
                    String failMsg = config.getString("messages.withdraw-fail", "&cNeed &f{amount}&c but only have &f{balance}");
                    failMsg = failMsg.replace("{amount}", TextUtil.formatNumber(amount)).replace("{balance}", TextUtil.formatNumber(eco.getBalance(player)));
                    TextUtil.sendMessage(player, prefix + failMsg);
                    return true;
                }
                eco.withdraw(player, amount);
            }

            String id = generateNoteId(isXp);

            plugin.getDatabaseManager().execute("INSERT INTO coresuite_banknotes (id, amount, owner, issue_time) VALUES (?, ?, ?, ?)", id, amount, player.getName(), System.currentTimeMillis());
            
            ItemStack note = createNoteItem(id, amount, player.getName());
            
            // Give item
            if (!player.getInventory().addItem(note).isEmpty()) {
                player.getWorld().dropItem(player.getLocation(), note);
            }

            if (isXp) {
                String successMsg = config.getString("messages.withdraw-success-xp", "&aWithdrew &f{amount} XP&a as an XP note!");
                successMsg = successMsg.replace("{amount}", TextUtil.formatNumber(amount));
                TextUtil.sendMessage(player, prefix + successMsg);
                TextUtil.sendMessage(player, prefix + "&7Remaining XP: &f" + TextUtil.formatNumber(getTotalExperience(player)));
                logTransaction("WITHDRAW-XP", player.getName(), amount, id);
            } else {
                EconomyManager eco = plugin.getEconomyManager();
                String successMsg = config.getString("messages.withdraw-success", "&aWithdrew &f{amount} {currency}&a as a bank note!");
                successMsg = successMsg.replace("{amount}", TextUtil.formatNumber(amount)).replace("{currency}", config.getString("currency", "Coins"));
                TextUtil.sendMessage(player, prefix + successMsg);
                TextUtil.sendMessage(player, prefix + "&7Remaining balance: &f" + eco.format(eco.getBalance(player)));
                logTransaction("WITHDRAW", player.getName(), amount, id);
            }
            return true;
        }

        if (cmd.equals("withdrawadmin")) {
            if (!sender.hasPermission("coresuite.banknotes.admin")) {
                TextUtil.sendMessage(sender, "&8[&6&lBank&8] &r&cNo permission.");
                return true;
            }
            if (args.length < 3 || !args[0].equalsIgnoreCase("give")) {
                TextUtil.sendMessage(sender, "&8[&6&lBank&8] &r&eUsage: &f/withdrawadmin give <player> <amount>");
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

            String id = generateNoteId(false);
            plugin.getDatabaseManager().execute("INSERT INTO coresuite_banknotes (id, amount, owner, issue_time) VALUES (?, ?, ?, ?)", id, amount, "ADMIN", System.currentTimeMillis());
            
            ItemStack note = createNoteItem(id, amount, "ADMIN");
            if (!target.getInventory().addItem(note).isEmpty()) {
                target.getWorld().dropItem(target.getLocation(), note);
            }

            TextUtil.sendMessage(sender, "&8[&6&lBank&8] &r&aGave &f" + TextUtil.formatNumber(amount) + " &anote to &f" + target.getName() + "&a.");
            TextUtil.sendMessage(target, "&8[&6&lBank&8] &r&aYou received a &f" + TextUtil.formatNumber(amount) + " " + config.getString("currency", "Coins") + "&a bank note from an admin!");
            logTransaction("ADMIN-GIVE", sender.getName() + " -> " + target.getName(), amount, id);
            return true;
        }

        if (cmd.equals("claimnote")) {
            if (!(sender instanceof Player)) return true;
            Player player = (Player) sender;
            
            if (!player.hasPermission("coresuite.banknotes.withdraw")) {
                TextUtil.sendMessage(player, "&8[&6&lBank&8] &r&cYou don't have permission to claim notes.");
                return true;
            }

            // Collect all note IDs first on main thread
            final java.util.List<java.util.Map.Entry<Integer, String>> noteEntries = new java.util.ArrayList<>();
            for (int i = 0; i < player.getInventory().getSize(); i++) {
                ItemStack noteItem = player.getInventory().getItem(i);
                String noteIdStr = getNoteId(noteItem);
                if (noteIdStr != null) {
                    noteEntries.add(java.util.Map.entry(i, noteIdStr));
                }
            }
            
            if (noteEntries.isEmpty()) {
                TextUtil.sendMessage(player, prefix + config.getString("messages.claim-fail", "&7No claimable bank notes found in your inventory."));
                return true;
            }
            
            // Process redemptions on DB thread
            plugin.getDatabaseManager().executeAsync(() -> {
                int count = 0;
                double total = 0;
                double totalXp = 0;
                java.util.List<Integer> slotsToClean = new java.util.ArrayList<>();
                java.util.List<Integer> slotsToConfiscate = new java.util.ArrayList<>();
                
                for (var entry : noteEntries) {
                    double result = redeemNote(entry.getValue(), player);
                    if (result > 0) {
                        count++;
                        if (entry.getValue().startsWith("XP-")) {
                            totalXp += result;
                        } else {
                            total += result;
                        }
                        slotsToClean.add(entry.getKey());
                    } else if (result == -2) {
                        slotsToConfiscate.add(entry.getKey());
                    }
                }
                
                final int fCount = count;
                final double fTotal = total;
                final double fTotalXp = totalXp;
                Bukkit.getScheduler().runTask(plugin, () -> {
                    // Clear redeemed/confiscated items on main thread
                    for (int slot : slotsToClean) {
                        player.getInventory().setItem(slot, null);
                    }
                    for (int slot : slotsToConfiscate) {
                        player.getInventory().setItem(slot, null);
                    }
                    
                    if (fCount == 0) {
                        TextUtil.sendMessage(player, prefix + config.getString("messages.claim-fail", "&7No claimable bank notes found in your inventory."));
                    } else {
                        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
                        String claimSuccess = config.getString("messages.claim-success", "&aClaimed &f{count}&a note(s)!");
                        claimSuccess = claimSuccess.replace("{count}", String.valueOf(fCount));
                        TextUtil.sendMessage(player, prefix + claimSuccess);
                        if (fTotal > 0) {
                            TextUtil.sendMessage(player, prefix + "&7Deposited: &a" + TextUtil.formatNumber(fTotal) + " " + config.getString("currency", "Coins"));
                            TextUtil.sendMessage(player, prefix + "&7New balance: &f" + plugin.getEconomyManager().format(plugin.getEconomyManager().getBalance(player)));
                        }
                        if (fTotalXp > 0) {
                            TextUtil.sendMessage(player, prefix + "&7Deposited: &a" + TextUtil.formatNumber(fTotalXp) + " XP");
                            TextUtil.sendMessage(player, prefix + "&7New XP: &f" + TextUtil.formatNumber(getTotalExperience(player)));
                        }
                    }
                });
            });
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
                org.bukkit.util.StringUtil.copyPartialMatches(args[0], java.util.Collections.singletonList("give"), completions);
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
