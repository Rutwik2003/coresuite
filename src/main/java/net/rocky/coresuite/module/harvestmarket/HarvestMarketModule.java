package net.rocky.coresuite.module.harvestmarket;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class HarvestMarketModule extends Module implements CommandExecutor, Listener, TabCompleter {
    private SeasonManager seasonManager;
    private SellManager sellManager;
    private DailyBountyManager bountyManager;
    private HarvestLeaderboardManager lbManager;
    private MarketGUI marketGui;
    private MutationHandler mutationHandler;
    private BukkitTask timerTask;

    public HarvestMarketModule(CoreSuitePlugin plugin) {
        super(plugin, "Harvest Market", "harvest-market");
    }

    @Override
    public void onEnable() {
        if (!config.getBoolean("enabled", true)) return;
        
        seasonManager = new SeasonManager(plugin, config);
        sellManager = new SellManager(plugin, seasonManager, plugin.getEconomyManager(), config);
        bountyManager = new DailyBountyManager(plugin, seasonManager, plugin.getEconomyManager(), config);
        lbManager = new HarvestLeaderboardManager(plugin, seasonManager, plugin.getEconomyManager(), config);
        marketGui = new MarketGUI(plugin, seasonManager, sellManager, bountyManager, lbManager, plugin.getEconomyManager(), config);
        
        NamespacedKey goldenKey = new NamespacedKey(plugin, "golden_crop");
        mutationHandler = new MutationHandler(plugin, seasonManager, config, goldenKey);
        
        registerListener(this);
        registerListener(mutationHandler);
        
        plugin.getCommand("harvest").setExecutor(this);
        plugin.getCommand("harvest").setTabCompleter(this);
        
        seasonManager.initialize(() -> {
            Season s = seasonManager.getCurrentSeason();
            if (s != null) {
                plugin.getLogger().info("Loaded Harvest Market season #" + s.getId());
            }
        });
        
        timerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            seasonManager.checkAndAdvance();
        }, 12000L, 12000L); // every 10 minutes
        registerTask(timerTask);

        // Periodic non-spammable rotating broadcast every 20 minutes (24000 ticks)
        int[] broadcastIndex = {0};
        broadcastTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (Bukkit.getOnlinePlayers().isEmpty()) return;
            Season s = seasonManager.getCurrentSeason();
            if (s == null) return;
            
            int idx = broadcastIndex[0] % 3;
            broadcastIndex[0]++;
            
            switch (idx) {
                case 0 -> {
                    double mult = config.getDouble("bonus-multiplier", 2.5);
                    Bukkit.broadcast(TextUtil.colorize(
                        "&8[&2🌾 Market&8] &aHarvest Season &f#" + s.getId() + " &ais active! Featured crops: " 
                        + s.getCropListFormatted() + " &8(Sell at &e/harvest &8for &e" + mult + "× &8price!)"
                    ));
                }
                case 1 -> {
                    double goldenMult = config.getDouble("golden-multiplier", 25.0);
                    Bukkit.broadcast(TextUtil.colorize(
                        "&8[&2🌾 Market&8] &6✨ Farming Tip: &7Harvesting mature crops can yield rare &e&lGolden Crops &7worth &a"
                        + goldenMult + "× &7cash! Check &e/harvest&7."
                    ));
                }
                case 2 -> {
                    Bukkit.broadcast(TextUtil.colorize(
                        "&8[&2🌾 Market&8] &aDaily Harvest Bounties &7are available! Sell target crops to earn extra cash & bonuses at &e/harvest&7!"
                    ));
                }
            }
        }, 6000L, 24000L); // first broadcast after 5m, then every 20m
        registerTask(broadcastTask);

        if (Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            registerListener(new CitizensNpcListener());
        }
    }

    private BukkitTask broadcastTask;

    @Override
    public void onDisable() {
        if (timerTask != null) timerTask.cancel();
        if (broadcastTask != null) broadcastTask.cancel();
        if (sellManager != null) sellManager.clearCache();
        PluginCommand cmd = plugin.getCommand("harvest");
        if (cmd != null && cmd.getExecutor() == this) {
            cmd.setExecutor(null);
            cmd.setTabCompleter(null);
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        if (sellManager != null) {
            sellManager.unloadPlayer(e.getPlayer().getUniqueId());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                marketGui.open((Player) sender);
            }
            return true;
        }
        
        String sub = args[0].toLowerCase();
        if (sub.equals("season")) {
            Season s = seasonManager.getCurrentSeason();
            if (s != null) {
                TextUtil.sendMessage(sender, "&2\uD83C\uDF3E &aCurrent Season &f#" + s.getId());
                TextUtil.sendMessage(sender, "&7Featured: " + s.getCropListFormatted());
                TextUtil.sendMessage(sender, "&7Ends in: &e" + s.getTimeRemainingString());
            } else {
                TextUtil.sendMessage(sender, "&cNo active season.");
            }
        } else if (sub.equals("top")) {
            Season s = seasonManager.getCurrentSeason();
            if (s != null) {
                lbManager.getTopSellers(s.getId(), 10, list -> {
                    TextUtil.sendMessage(sender, "&6&l🏆 Season #" + s.getId() + " Top Sellers");
                    if (list.isEmpty()) {
                        TextUtil.sendMessage(sender, "&7No sellers yet.");
                    } else {
                        for (LeaderboardEntry entry : list) {
                            TextUtil.sendMessage(sender, "&e#" + entry.rank + " &f" + entry.playerName + " &7- &a" + TextUtil.formatNumber(entry.totalSold) + " items");
                        }
                    }
                });
            }
        } else if (sub.equals("admin") && sender.hasPermission("coresuite.harvest.admin")) {
            if (args.length > 1) {
                String adminSub = args[1].toLowerCase();
                if (adminSub.equals("newseason")) {
                    seasonManager.advanceSeason();
                    TextUtil.sendMessage(sender, "&aForced new harvest season!");
                } else if (adminSub.equals("reload")) {
                    plugin.reloadConfig();
                    TextUtil.sendMessage(sender, "&aModule reloaded.");
                } else if (adminSub.equals("quota") && args.length > 3 && args[2].equalsIgnoreCase("reset")) {
                    Player p = Bukkit.getPlayer(args[3]);
                    if (p != null) {
                        UUID uuid = p.getUniqueId();
                        Season s = seasonManager.getCurrentSeason();
                        if (s != null) {
                            plugin.getDatabaseManager().executeAsync(() -> {
                                plugin.getDatabaseManager().execute("DELETE FROM coresuite_harvest_quota WHERE uuid = ? AND season_id = ?", uuid.toString(), s.getId());
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    sellManager.loadQuota(uuid, null);
                                    TextUtil.sendMessage(sender, "&aReset quota for " + p.getName());
                                });
                            });
                        }
                    }
                }
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> tabs = new ArrayList<>();
        if (args.length == 1) {
            tabs.add("season");
            tabs.add("top");
            if (sender.hasPermission("coresuite.harvest.admin")) {
                tabs.add("admin");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("coresuite.harvest.admin")) {
            tabs.add("newseason");
            tabs.add("reload");
            tabs.add("quota");
        } else if (args.length == 3 && args[1].equalsIgnoreCase("quota") && sender.hasPermission("coresuite.harvest.admin")) {
            tabs.add("reset");
        }
        return tabs;
    }
    
    private class CitizensNpcListener implements Listener {
        @EventHandler
        public void onNpcClick(net.citizensnpcs.api.event.NPCRightClickEvent e) {
            int configNpcId = config.getInt("npc-id", -1);
            if (configNpcId == -1 || e.getNPC().getId() != configNpcId) return;
            e.setCancelled(true);
            marketGui.open(e.getClicker());
        }
    }
}
