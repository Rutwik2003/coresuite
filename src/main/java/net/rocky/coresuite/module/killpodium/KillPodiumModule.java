package net.rocky.coresuite.module.killpodium;

import me.clip.placeholderapi.PlaceholderAPI;
import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.SkinTrait;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.Module;
import net.skinsrestorer.api.SkinsRestorerProvider;
import net.skinsrestorer.api.SkinsRestorer;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;

import java.util.Optional;
import org.bukkit.command.TabCompleter;

public class KillPodiumModule extends Module implements CommandExecutor, TabCompleter {

    public KillPodiumModule(CoreSuitePlugin plugin) {
        super(plugin, "KillPodium", "kill-podium");
    }

    @Override
    public void onEnable() {
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") == null ||
            Bukkit.getPluginManager().getPlugin("Citizens") == null ||
            Bukkit.getPluginManager().getPlugin("SkinsRestorer") == null) {
            plugin.getLogger().warning("KillPodium module requires PlaceholderAPI, Citizens, and SkinsRestorer. Features disabled.");
            return;
        }

        PluginCommand command = plugin.getCommand("podiumupdate");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }

        int interval = config.getInt("update-interval", 300);
        registerTask(Bukkit.getScheduler().runTaskTimer(plugin, this::runPodiumUpdate, interval * 20L, interval * 20L));
    }

    @Override
    public void onDisable() {
        PluginCommand command = plugin.getCommand("podiumupdate");
        if (command != null && command.getExecutor() == this) {
            command.setExecutor(null);
            command.setTabCompleter(null);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("podiumupdate")) {
            if (!sender.hasPermission("podiumupdater.admin")) {
                TextUtil.sendMessage(sender, config.getString("prefix", "&b[PodiumUpdater]&r ") + "&cYou don't have permission to do that.");
                return true;
            }
            TextUtil.sendMessage(sender, config.getString("prefix", "&b[PodiumUpdater]&r ") + "&7Forcing podium skin update...");
            runPodiumUpdate();
            TextUtil.sendMessage(sender, config.getString("prefix", "&b[PodiumUpdater]&r ") + "&aDone. Check console for details on any NPCs that were skipped.");
            return true;
        }
        return false;
    }

    private void runPodiumUpdate() {
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            plugin.getLogger().info("Skipping podium update - no players online.");
            return;
        }

        Player ctx = Bukkit.getOnlinePlayers().iterator().next();
        String board = config.getString("board", "statistic_player_kills");
        String type = config.getString("type", "alltime");

        // Resolve placeholders on main thread, then fetch skins async
        java.util.Map<Integer, String> npcPlayerMap = new java.util.LinkedHashMap<>();
        for (int i = 1; i <= 5; i++) {
            int npcId = config.getInt("npcs." + i, -1);
            if (npcId != -1) {
                String placeholder = "%ajlb_lb_" + board + "_" + i + "_" + type + "_name%";
                String playerName = PlaceholderAPI.setPlaceholders(ctx, placeholder);
                npcPlayerMap.put(npcId, playerName);
            }
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            for (var entry : npcPlayerMap.entrySet()) {
                syncPodiumSkin(entry.getKey(), entry.getValue());
            }
        });
    }

    private void syncPodiumSkin(int npcId, String playerName) {
        if (playerName == null || playerName.isEmpty() || playerName.equalsIgnoreCase("N/A") || playerName.equalsIgnoreCase("none")) {
            return;
        }
        if (playerName.contains("%")) {
            plugin.getLogger().warning("Placeholder for NPC " + npcId + " didn't resolve - got: " + playerName);
            return;
        }

        SkinsRestorer sr = SkinsRestorerProvider.get();
        java.util.UUID uuid = Bukkit.getOfflinePlayer(playerName).getUniqueId();
        
        String value = null;
        String signature = null;

        try {
            Optional<net.skinsrestorer.api.property.SkinProperty> opt = sr.getPlayerStorage().getSkinForPlayer(uuid, playerName);
            if (opt.isPresent()) {
                value = opt.get().getValue();
                signature = opt.get().getSignature();
            }
        } catch (Exception e) {
            // Ignore
        }

        if (value == null) {
            try {
                // Fetch from Mojang if not stored in SkinsRestorer
                Object resultOpt = sr.getMojangAPI().getSkin(playerName);
                if (resultOpt instanceof Optional) {
                    Optional<?> opt = (Optional<?>) resultOpt;
                    if (opt.isPresent()) {
                        Object result = opt.get();
                        if (result instanceof net.skinsrestorer.api.property.SkinProperty) {
                            value = ((net.skinsrestorer.api.property.SkinProperty) result).getValue();
                            signature = ((net.skinsrestorer.api.property.SkinProperty) result).getSignature();
                        } else {
                            value = (String) result.getClass().getMethod("getValue").invoke(result);
                            signature = (String) result.getClass().getMethod("getSignature").invoke(result);
                        }
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning(playerName + " has no skin available, skipping NPC " + npcId);
                return;
            }
        }

        if (value == null || value.isEmpty()) {
            plugin.getLogger().warning(playerName + " has no skin available, skipping NPC " + npcId);
            return;
        }

        NPC npc = CitizensAPI.getNPCRegistry().getById(npcId);
        if (npc == null) {
            plugin.getLogger().warning("NPC id " + npcId + " doesn't exist");
            return;
        }

        SkinTrait trait = npc.getOrAddTrait(SkinTrait.class);
        trait.setSkinPersistent(playerName, signature, value);
        trait.setShouldUpdateSkins(false);
        trait.setFetchDefaultSkin(false);
        plugin.getLogger().info("Updated NPC " + npcId + " skin to match " + playerName + " (texture length: " + value.length() + ")");
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return java.util.Collections.emptyList();
    }
}
