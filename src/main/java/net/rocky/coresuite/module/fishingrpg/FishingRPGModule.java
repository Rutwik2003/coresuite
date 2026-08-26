package net.rocky.coresuite.module.fishingrpg;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class FishingRPGModule extends Module {

    private AnglerProfileManager profileManager;
    private SpeciesRegistry speciesRegistry;
    private LootEngine lootEngine;
    private ComboTracker comboTracker;
    
    // Future managers
    private BaitManager baitManager;
    private HotspotManager hotspotManager;
    private SeaMonsterManager seaMonsterManager;
    private AncientAnglerManager ancientAnglerManager;
    private FishBagManager fishBagManager;
    private CollectionManager collectionManager;
    private TournamentManager tournamentManager;
    private DailyBountyManager dailyBountyManager;
    private GuideBookManager guideBookManager;
    private UpdateKitManager updateKitManager;

    public final NamespacedKey speciesKey;
    public final NamespacedKey weightKey;
    public final NamespacedKey priceKey;
    public final NamespacedKey rarityKey;
    public final NamespacedKey corruptedKey;
    public final NamespacedKey baitIdKey;
    public final NamespacedKey compassKey;
    public final NamespacedKey trackerKey;

    public FishingRPGModule(CoreSuitePlugin plugin) {
        super(plugin, "FishingRPG", "fishing-rpg");
        this.speciesKey = new NamespacedKey(plugin, "fish_species");
        this.weightKey = new NamespacedKey(plugin, "fish_weight");
        this.priceKey = new NamespacedKey(plugin, "fish_price");
        this.rarityKey = new NamespacedKey(plugin, "fish_rarity");
        this.corruptedKey = new NamespacedKey(plugin, "fish_corrupted");
        this.baitIdKey = new NamespacedKey(plugin, "bait_id");
        this.compassKey = new NamespacedKey(plugin, "legendary_compass");
        this.trackerKey = new NamespacedKey(plugin, "legendary_tracker");
    }

    @Override
    public void onEnable() {
        getPlugin().getLogger().info("Initializing FishingRPG module...");

        // Note: Using placeholders for missing managers in standard configuration
        this.speciesRegistry = new SpeciesRegistry(getConfig());
        getPlugin().getLogger().info("Loaded " + speciesRegistry.getSpeciesCount() + " fish species.");
        
        this.profileManager = new AnglerProfileManager(getPlugin());
        this.comboTracker = new ComboTracker();
        this.lootEngine = new LootEngine(speciesRegistry, getConfig());

        this.baitManager = new BaitManager(getPlugin());
        this.hotspotManager = new HotspotManager(getPlugin(), getConfig());
        this.seaMonsterManager = new SeaMonsterManager(getPlugin(), getConfig());
        this.ancientAnglerManager = new AncientAnglerManager(getPlugin(), this, getConfig());
        this.fishBagManager = new FishBagManager(getPlugin());
        this.collectionManager = new CollectionManager(getPlugin(), speciesRegistry);
        this.tournamentManager = new TournamentManager(getPlugin(), getConfig());
        this.dailyBountyManager = new DailyBountyManager(getPlugin(), speciesRegistry, getConfig());
        this.guideBookManager = new GuideBookManager(getConfig());
        this.updateKitManager = new UpdateKitManager(getPlugin(), getConfig());

        registerListener(new FishingListener(this));
        registerListener(this.baitManager);
        registerListener(this.seaMonsterManager);
        registerListener(this.ancientAnglerManager);
        registerListener(this.fishBagManager);
        registerListener(this.collectionManager);

        registerListener(new Listener() {
            @EventHandler
            public void onPlayerJoin(PlayerJoinEvent event) {
                profileManager.loadPlayer(event.getPlayer().getUniqueId());
            }

            @EventHandler
            public void onPlayerQuit(PlayerQuitEvent event) {
                profileManager.unloadPlayer(event.getPlayer().getUniqueId());
                comboTracker.removePlayer(event.getPlayer().getUniqueId());
            }
        });

        // Load profiles for currently online players (reload support)
        for (Player player : Bukkit.getOnlinePlayers()) {
            profileManager.loadPlayer(player.getUniqueId());
        }

        FishCommandExecutor commandExecutor = new FishCommandExecutor();
        if (getPlugin().getCommand("fish") != null) {
            getPlugin().getCommand("fish").setExecutor(commandExecutor);
            getPlugin().getCommand("fish").setTabCompleter(commandExecutor);
        }

        getPlugin().getLogger().info("FishingRPG module enabled.");
    }

    @Override
    public void onDisable() {
        if (profileManager != null) {
            profileManager.shutdown();
        }
        getPlugin().getLogger().info("FishingRPG module disabled.");
    }

    public AnglerProfileManager getProfileManager() {
        return profileManager;
    }

    public SpeciesRegistry getSpeciesRegistry() {
        return speciesRegistry;
    }

    public LootEngine getLootEngine() {
        return lootEngine;
    }

    public ComboTracker getComboTracker() {
        return comboTracker;
    }

    public BaitManager getBaitManager() {
        return baitManager;
    }

    public HotspotManager getHotspotManager() {
        return hotspotManager;
    }

    public SeaMonsterManager getSeaMonsterManager() {
        return seaMonsterManager;
    }

    public AncientAnglerManager getAncientAnglerManager() {
        return ancientAnglerManager;
    }

    public FishBagManager getFishBagManager() {
        return fishBagManager;
    }

    public CollectionManager getCollectionManager() {
        return collectionManager;
    }

    public TournamentManager getTournamentManager() {
        return tournamentManager;
    }

    public DailyBountyManager getDailyBountyManager() {
        return dailyBountyManager;
    }

    public GuideBookManager getGuideBookManager() {
        return guideBookManager;
    }

    public UpdateKitManager getUpdateKitManager() {
        return updateKitManager;
    }

    private class FishCommandExecutor implements CommandExecutor, TabCompleter {
        private final List<String> subcommands = Arrays.asList("guide", "skills", "bag", "sell", "top", "tournament", "claimkit", "admin");

        @Override
        public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
            if (!(sender instanceof Player player)) {
                TextUtil.sendMessage(sender, "<red>Only players can use this command.");
                return true;
            }

            if (args.length == 0) {
                // Summary message
                AnglerProfile profile = profileManager.getProfile(player.getUniqueId());
                if (profile != null) {
                    TextUtil.sendMessage(player, "<gold><bold>Fishing RPG Stats:");
                    TextUtil.sendMessage(player, "<yellow>Level: <white>" + profile.getLevel());
                    TextUtil.sendMessage(player, "<yellow>XP: <white>" + profile.getXp() + " / " + profile.getRequiredXP());
                    TextUtil.sendMessage(player, "<yellow>Total Catches: <white>" + profile.getTotalCatches());
                } else {
                    TextUtil.sendMessage(player, "<red>Could not load your fishing profile.");
                }
                return true;
            }

            String subCommand = args[0].toLowerCase();
            switch (subCommand) {
                case "guide":
                    guideBookManager.openBook(player);
                    break;
                case "skills":
                    new SkillTreeGUI(FishingRPGModule.this).open(player);
                    break;
                case "bag":
                    fishBagManager.openBagGUI(player, 0);
                    break;
                case "sell":
                    TextUtil.sendMessage(player, "<gray>Sell feature is available inside your Fish Bag! Type /fish bag to sell.");
                    break;
                case "top":
                    collectionManager.openCollectionGUI(player, 0, null);
                    break;
                case "tournament":
                    tournamentManager.openTournamentGUI(player);
                    break;
                case "claimkit":
                    updateKitManager.claimKit(player);
                    break;
                                case "admin":
                    if (!player.hasPermission("coresuite.fishingrpg.admin")) {
                        TextUtil.sendMessage(player, "<red>You do not have permission to use this command.");
                        return true;
                    }
                    if (args.length > 1 && args[1].equalsIgnoreCase("resetkit")) {
                        Player target = args.length > 2 ? org.bukkit.Bukkit.getPlayer(args[2]) : player;
                        if (target == null) {
                            TextUtil.sendMessage(player, "<red>Player not found.");
                            return true;
                        }
                        getPlugin().getDatabaseManager().execute("DELETE FROM fishingrpg_update_kit WHERE uuid = ?", target.getUniqueId().toString());
                        TextUtil.sendMessage(player, "<green>Update kit claim has been reset for " + target.getName() + ".");
                        return true;
                    }
                    if (args.length > 1 && args[1].equalsIgnoreCase("givecompass")) {
                        Player target = args.length > 2 ? org.bukkit.Bukkit.getPlayer(args[2]) : player;
                        if (target == null) {
                            TextUtil.sendMessage(player, "<red>Player not found.");
                            return true;
                        }
                        org.bukkit.Location compassTarget = new org.bukkit.Location(target.getWorld(), target.getLocation().getX() + 1000, 64, target.getLocation().getZ() + 1000);
                        org.bukkit.inventory.ItemStack compass = FishItemBuilder.buildCompassItem(target, compassTarget, getPlugin());
                        if (target.getInventory().firstEmpty() != -1) {
                            target.getInventory().addItem(compass);
                        } else {
                            target.getWorld().dropItem(target.getLocation(), compass);
                        }
                        TextUtil.sendMessage(player, "<green>Gave Legendary Compass to " + target.getName() + ".");
                        return true;
                    }
                    if (args.length > 1 && args[1].equalsIgnoreCase("givebait")) {
                        if (args.length < 3) {
                            TextUtil.sendMessage(player, "<red>Usage: /fish admin givebait <bait_id> [player]");
                            return true;
                        }
                        String baitId = args[2];
                        Player target = args.length > 3 ? org.bukkit.Bukkit.getPlayer(args[3]) : player;
                        if (target == null) {
                            TextUtil.sendMessage(player, "<red>Player not found.");
                            return true;
                        }
                        org.bukkit.inventory.ItemStack baitItem = baitManager.createBaitItem(baitId, getConfig());
                        if (baitItem != null) {
                            if (target.getInventory().firstEmpty() != -1) {
                                target.getInventory().addItem(baitItem);
                            } else {
                                target.getWorld().dropItem(target.getLocation(), baitItem);
                            }
                            TextUtil.sendMessage(player, "<green>Gave bait " + baitId + " to " + target.getName() + ".");
                        } else {
                            TextUtil.sendMessage(player, "<red>Bait ID not found in config.");
                        }
                        return true;
                    }
                    TextUtil.sendMessage(player, "<gold>Admin Commands:");
                    TextUtil.sendMessage(player, "<yellow>/fish admin givecompass [player]");
                    TextUtil.sendMessage(player, "<yellow>/fish admin givebait <bait_id> [player]");
                    TextUtil.sendMessage(player, "<yellow>/fish admin resetkit [player]");
                    break;
                default:
                    TextUtil.sendMessage(player, "<red>Unknown subcommand. Available: " + String.join(", ", subcommands));
                    break;
            }

            return true;
        }

        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
            if (args.length == 1) {
                String partial = args[0].toLowerCase();
                List<String> available = new ArrayList<>(subcommands);
                if (!sender.hasPermission("coresuite.fishingrpg.admin")) {
                    available.remove("admin");
                }
                return available.stream().filter(s -> s.startsWith(partial)).collect(Collectors.toList());
            }
            return new ArrayList<>();
        }
    }
}
