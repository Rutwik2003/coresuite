package net.rocky.coresuite.module.ordersboard;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public class OrdersBoardModule extends Module implements CommandExecutor, Listener, TabCompleter {

    private OrderManager orderManager;
    private BidManager bidManager;
    private BoardGUI boardGUI;
    private PostOrderGUI postOrderGUI;
    private MyOrdersGUI myOrdersGUI;
    private BidGUI bidGUI;

    public OrdersBoardModule(CoreSuitePlugin plugin) {
        super(plugin, "OrdersBoard", "orders-board");
    }

    @Override
    public void onEnable() {
        orderManager = new OrderManager(plugin, config);
        bidManager   = new BidManager(plugin);
        bidManager.initDatabase();
        bidManager.setOrderManager(orderManager);

        OrderDiscordNotifier discordNotifier = new OrderDiscordNotifier(plugin);
        orderManager.setDiscordNotifier(discordNotifier);

        myOrdersGUI  = new MyOrdersGUI(plugin, orderManager, config);
        postOrderGUI = new PostOrderGUI(plugin, orderManager, config);
        boardGUI     = new BoardGUI(plugin, orderManager, config, postOrderGUI, myOrdersGUI);
        bidGUI       = new BidGUI(plugin, bidManager, orderManager, config);

        myOrdersGUI.setBoardGUI(boardGUI);
        myOrdersGUI.setBidGUI(bidGUI);
        postOrderGUI.setBoardGUI(boardGUI);
        boardGUI.setBidGUI(bidGUI);

        registerListener(this);
        registerListener(postOrderGUI);

        PluginCommand command = plugin.getCommand("orders");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }

        registerTask(Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            orderManager.expireOldOrders();
        }, 0L, 6000L)); // Every 5 minutes

        if (Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            registerListener(new CitizensListener());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (orderManager != null) {
            Player player = event.getPlayer();
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) {
                    orderManager.deliverPendingItems(player);
                    orderManager.deliverPendingNotifications(player);
                }
            }, 25L);
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
        if (bidGUI != null) {
            bidGUI.cleanup(event.getPlayer().getUniqueId());
        }
    }

    @Override
    public void onDisable() {
        PluginCommand command = plugin.getCommand("orders");
        if (command != null && command.getExecutor() == this) {
            command.setExecutor(null);
            command.setTabCompleter(null);
        }
        if (orderManager != null) {
            orderManager.clearCache();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!command.getName().equalsIgnoreCase("orders")) return false;
        
        String prefix = config.getString("messages.prefix", "&8[&6Orders&8] &r");
        
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }
        
        Player player = (Player) sender;
        if (orderManager != null) {
            orderManager.deliverPendingItems(player);
            orderManager.deliverPendingNotifications(player);
        }
        
        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            boardGUI.open(player, 1, BoardGUI.SortMode.HIGHEST_PAYOUT);
            return true;
        }
        
        if (args[0].equalsIgnoreCase("mine")) {
            myOrdersGUI.open(player, 1);
            return true;
        }

        if (args[0].equalsIgnoreCase("bids")) {
            bidGUI.openMyBids(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("cancel") || args[0].equalsIgnoreCase("remove")) {
            if (args.length == 1) {
                myOrdersGUI.open(player, 1);
                TextUtil.sendMessage(player, prefix + "&eClick any order to cancel it and reclaim your escrow money.");
                return true;
            }
            try {
                int orderId = Integer.parseInt(args[1].replace("#", ""));
                boolean isAdmin = player.hasPermission("coresuite.ordersboard.admin");
                UUID requesterUuid = isAdmin ? null : player.getUniqueId();
                orderManager.cancelOrder(orderId, requesterUuid, refund -> {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1f);
                    String msg = config.getString("messages.cancelled", "&aOrder cancelled. Refunded: &e{refund}&a.")
                            .replace("{refund}", plugin.getEconomyManager().format(refund));
                    TextUtil.sendMessage(player, prefix + msg);
                }, () -> {
                    TextUtil.sendMessage(player, prefix + "&cCould not cancel order #" + orderId + ". Make sure you are the owner and it is active.");
                });
            } catch (NumberFormatException ex) {
                TextUtil.sendMessage(player, prefix + "&cInvalid order ID: &e" + args[1]);
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("help")) {
            TextUtil.sendMessage(player, prefix + "&eOrders Board Help:");
            TextUtil.sendMessage(player, "&7/orders &8- &fOpen the main board");
            TextUtil.sendMessage(player, "&7/orders mine &8- &fView your active orders");
            TextUtil.sendMessage(player, "&7/orders cancel [id] &8- &fCancel an order & reclaim refund");
            TextUtil.sendMessage(player, "&7/orders bids &8- &fView incoming bids & offers");
            if (player.hasPermission("coresuite.ordersboard.admin")) {
                TextUtil.sendMessage(player, "&7/orders admin setnpc &8- &fSet Citizens NPC ID (look at NPC)");
                TextUtil.sendMessage(player, "&7/orders admin reload &8- &fReload config");
                TextUtil.sendMessage(player, "&7/orders admin clear &8- &fClear expired orders");
            }
            return true;
        }
        
        if (args[0].equalsIgnoreCase("admin")) {
            if (!player.hasPermission("coresuite.ordersboard.admin")) {
                TextUtil.sendMessage(player, prefix + config.getString("messages.no-permission", "&cNo permission."));
                return true;
            }
            if (args.length > 1) {
                if (args[1].equalsIgnoreCase("setnpc")) {
                    if (Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
                        net.citizensnpcs.api.npc.NPC npc = net.citizensnpcs.api.CitizensAPI.getDefaultNPCSelector().getSelected(sender);
                        if (npc != null) {
                            config.set("npc-id", npc.getId());
                            saveConfig();
                            TextUtil.sendMessage(player, prefix + "&aNPC ID set to " + npc.getId());
                        } else {
                            TextUtil.sendMessage(player, prefix + "&cYou must have an NPC selected (use /npc select).");
                        }
                    } else {
                        TextUtil.sendMessage(player, prefix + "&cCitizens plugin not found.");
                    }
                    return true;
                } else if (args[1].equalsIgnoreCase("reload")) {
                    loadConfig();
                    TextUtil.sendMessage(player, prefix + "&aOrdersBoard config reloaded.");
                    return true;
                } else if (args[1].equalsIgnoreCase("clear")) {
                    orderManager.expireOldOrders();
                    TextUtil.sendMessage(player, prefix + "&aForced expiry check.");
                    return true;
                }
            }
            TextUtil.sendMessage(player, prefix + "&cUnknown admin command. Use /orders help.");
            return true;
        }
        
        return false;
    }

    @EventHandler
    public void onChat(org.bukkit.event.player.AsyncPlayerChatEvent e) {
        if (bidGUI != null && bidGUI.handleChat(e.getPlayer(), e.getMessage())) {
            e.setCancelled(true);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            List<String> options = new ArrayList<>(Arrays.asList("gui", "mine", "bids", "cancel", "help"));
            if (sender.hasPermission("coresuite.ordersboard.admin")) {
                options.add("admin");
            }
            StringUtil.copyPartialMatches(args[0], options, completions);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("coresuite.ordersboard.admin")) {
            StringUtil.copyPartialMatches(args[1], Arrays.asList("setnpc", "reload", "clear"), completions);
        }
        return completions;
    }
    
    private class CitizensListener implements Listener {
        @EventHandler
        public void onNPCClick(net.citizensnpcs.api.event.NPCRightClickEvent e) {
            int configNpcId = config.getInt("npc-id", -1);
            if (configNpcId == -1 || e.getNPC().getId() != configNpcId) return;
            Player player = e.getClicker();
            e.setCancelled(true);
            boardGUI.open(player, 1, BoardGUI.SortMode.HIGHEST_PAYOUT);
        }
    }
}
