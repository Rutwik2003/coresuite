package net.rocky.coresuite.module.archaeologyrush;

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
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class ArchaeologyRushModule extends Module implements CommandExecutor, Listener, TabCompleter {

    private NamespacedKey setKey;
    private NamespacedKey fragmentKey;
    private ArtifactSetRegistry registry;
    private PlayerCollectionManager collectionManager;
    private CollectionGUI collectionGUI;
    private CuratorGUI curatorGUI;
    private FragmentDropHandler dropHandler;

    public ArchaeologyRushModule(CoreSuitePlugin plugin) {
        super(plugin, "Archaeology Rush", "archaeology-rush");
        this.setKey = new NamespacedKey(plugin, "artifact_set");
        this.fragmentKey = new NamespacedKey(plugin, "artifact_fragment");
    }

    @Override
    public void onEnable() {
        plugin.getDatabaseManager().execute(
            "CREATE TABLE IF NOT EXISTS coresuite_artifact_fragments (" +
            "uuid TEXT NOT NULL, set_id TEXT NOT NULL, fragment_index INTEGER NOT NULL, " +
            "obtained_at BIGINT NOT NULL, PRIMARY KEY (uuid, set_id, fragment_index))"
        );
        plugin.getDatabaseManager().execute(
            "CREATE TABLE IF NOT EXISTS coresuite_artifact_completions (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, uuid TEXT NOT NULL, player_name TEXT NOT NULL, " +
            "set_id TEXT NOT NULL, completed_at BIGINT NOT NULL)"
        );

        registry = new ArtifactSetRegistry(plugin, config);
        registry.load();

        collectionManager = new PlayerCollectionManager(plugin);
        
        curatorGUI = new CuratorGUI(plugin, registry, collectionManager, plugin.getEconomyManager(), config, setKey);
        collectionGUI = new CollectionGUI(plugin, registry, collectionManager, plugin.getEconomyManager(), curatorGUI);
        
        dropHandler = new FragmentDropHandler(plugin, registry, collectionManager, config, setKey, fragmentKey);

        registerListener(this);
        registerListener(dropHandler);

        PluginCommand cmd = plugin.getCommand("artifacts");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }

        if (Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            registerCitizensHook();
        }
    }

    private void registerCitizensHook() {
        registerListener(new Listener() {
            @EventHandler
            public void onNPCClick(net.citizensnpcs.api.event.NPCRightClickEvent e) {
                int configNpcId = config.getInt("npc-id", -1);
                if (configNpcId == -1 || e.getNPC().getId() != configNpcId) return;
                Player player = e.getClicker();
                e.setCancelled(true);
                collectionGUI.open(player);
            }
        });
    }

    @Override
    public void onDisable() {
        if (collectionManager != null) {
            collectionManager.clearCache();
        }
        PluginCommand cmd = plugin.getCommand("artifacts");
        if (cmd != null && cmd.getExecutor() == this) {
            cmd.setExecutor(null);
            cmd.setTabCompleter(null);
        }
    }

    @EventHandler
    public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent e) {
        if (collectionManager != null) {
            collectionManager.unloadPlayer(e.getPlayer().getUniqueId());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (args.length == 0) {
            collectionGUI.open(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("sets")) {
            TextUtil.sendMessage(player, "&8▶ &6&lArtifact Sets &8◀");
            for (ArtifactSet set : registry.getAllSets()) {
                TextUtil.sendMessage(player, "&8 - &f" + set.displayName() + " &7(" + set.getPieceCount() + " pieces)");
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("curator")) {
            boolean found = false;
            for (ArtifactSet set : registry.getAllSets()) {
                if (collectionManager.hasCompleteSet(player.getUniqueId(), set)) {
                    curatorGUI.openForSet(player, set);
                    found = true;
                    break;
                }
            }
            if (!found) {
                TextUtil.sendMessage(player, config.getString("messages.no-complete-sets", "&7You have no complete sets to turn in yet."));
            }
            return true;
        }

        if (args.length >= 1 && args[0].equalsIgnoreCase("admin") && player.hasPermission("coresuite.admin")) {
            if (args.length >= 2 && args[1].equalsIgnoreCase("reload")) {
                loadConfig();
                registry.load();
                TextUtil.sendMessage(player, "&aArchaeology Rush config reloaded.");
                return true;
            }
            if (args.length >= 2 && args[1].equalsIgnoreCase("setnpc")) {
                TextUtil.sendMessage(player, "&cPlease use the config to set npc-id.");
                return true;
            }
            if (args.length >= 5 && args[1].equalsIgnoreCase("give")) {
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    TextUtil.sendMessage(player, "&cPlayer not found.");
                    return true;
                }
                String setId = args[3];
                ArtifactSet set = registry.getSet(setId);
                if (set == null) {
                    TextUtil.sendMessage(player, "&cSet not found.");
                    return true;
                }
                try {
                    int index = Integer.parseInt(args[4]);
                    ArtifactFragment frag = set.getFragment(index);
                    if (frag == null) {
                        TextUtil.sendMessage(player, "&cFragment index out of bounds.");
                        return true;
                    }
                    ItemStack item = frag.toItem(set.displayName(), set.getPieceCount(), setKey, fragmentKey);
                    if (target.getInventory().firstEmpty() != -1) {
                        target.getInventory().addItem(item);
                    } else {
                        target.getWorld().dropItem(target.getLocation(), item);
                    }
                    collectionManager.addFragment(target.getUniqueId(), target.getName(), setId, index);
                    TextUtil.sendMessage(player, "&aGiven fragment to " + target.getName() + ".");
                } catch (NumberFormatException e) {
                    TextUtil.sendMessage(player, "&cInvalid fragment index.");
                }
                return true;
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            completions.add("sets");
            completions.add("curator");
            if (sender.hasPermission("coresuite.admin")) {
                completions.add("admin");
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("admin") && sender.hasPermission("coresuite.admin")) {
            completions.add("give");
            completions.add("reload");
            completions.add("setnpc");
        } else if (args.length == 3 && args[1].equalsIgnoreCase("give") && sender.hasPermission("coresuite.admin")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                completions.add(p.getName());
            }
        } else if (args.length == 4 && args[1].equalsIgnoreCase("give") && sender.hasPermission("coresuite.admin")) {
            for (ArtifactSet set : registry.getAllSets()) {
                completions.add(set.id());
            }
        } else if (args.length == 5 && args[1].equalsIgnoreCase("give") && sender.hasPermission("coresuite.admin")) {
            ArtifactSet set = registry.getSet(args[3]);
            if (set != null) {
                for (ArtifactFragment frag : set.fragments()) {
                    completions.add(String.valueOf(frag.fragmentIndex()));
                }
            }
        }
        return completions;
    }
}
