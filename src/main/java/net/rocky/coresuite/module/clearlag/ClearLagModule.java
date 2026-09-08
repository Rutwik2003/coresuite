package net.rocky.coresuite.module.clearlag;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.scheduler.BukkitTask;

import java.util.Arrays;
import java.util.List;

public class ClearLagModule extends Module implements CommandExecutor, TabCompleter {

    private BukkitTask timerTask;
    private int countdownSeconds = 0;
    private int intervalSeconds = 480;
    private List<Integer> warnings;

    public ClearLagModule(CoreSuitePlugin plugin) {
        super(plugin, "ClearLag", "clear-lag");
    }

    @Override
    public void onEnable() {
        intervalSeconds = config.getInt("interval", 480);
        warnings = config.getIntegerList("warnings");

        PluginCommand command = plugin.getCommand("clearlag");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }

        startTimer();
    }

    @Override
    public void onDisable() {
        if (timerTask != null) {
            timerTask.cancel();
            timerTask = null;
        }

        PluginCommand command = plugin.getCommand("clearlag");
        if (command != null && command.getExecutor() == this) {
            command.setExecutor(null);
            command.setTabCompleter(null);
        }
    }

    private void startTimer() {
        if (timerTask != null) {
            timerTask.cancel();
        }

        countdownSeconds = intervalSeconds;
        timerTask = registerTask(Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L));
    }

    private void tick() {
        if (Bukkit.getOnlinePlayers().isEmpty()) {
            return; // Don't run countdown if server is empty
        }

        countdownSeconds--;

        if (warnings.contains(countdownSeconds)) {
            String prefix = config.getString("prefix", "&#66EF8B&lMeerMC » &r");
            
            String timeString;
            if (countdownSeconds >= 60) {
                int mins = countdownSeconds / 60;
                timeString = mins + " minute" + (mins > 1 ? "s" : "");
            } else {
                timeString = countdownSeconds + " second" + (countdownSeconds > 1 ? "s" : "");
            }
            
            String warning = config.getString("messages.warning-message", "&eGround items will be cleared in {time}.");
            Bukkit.broadcast(TextUtil.colorize(prefix + " " + warning.replace("{time}", timeString)));
        }

        if (countdownSeconds <= 0) {
            clearItems();
            countdownSeconds = intervalSeconds;
        }
    }

    private void clearItems() {
        int itemsRemoved = 0;
        int arrowsRemoved = 0;

        java.util.List<String> types = config.getStringList("clear-entities");
        if (types.isEmpty()) types = java.util.Arrays.asList("ITEM", "DROPPED_ITEM", "ARROW");

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                boolean isItem = entity instanceof org.bukkit.entity.Item;
                boolean isArrow = entity instanceof org.bukkit.entity.Arrow;
                
                if (isItem || types.contains(entity.getType().name())) {
                    entity.remove();
                    itemsRemoved++;
                }
            }
        }

        String prefix = config.getString("prefix", "&#66EF8B&lMeerMC » &r");
        String clearedMsg = config.getString("messages.cleared-message", "&aCleared {count} entities.");
        Bukkit.broadcast(TextUtil.colorize(prefix + " " + clearedMsg.replace("{count}", String.valueOf(itemsRemoved))));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("coresuite.clearlag.use")) {
            String prefix = config.getString("prefix", "&#66EF8B&lMeerMC » &r");
            TextUtil.sendMessage(sender, prefix + " &cSorry, but you can't do that.");
            return true;
        }

        String prefix = config.getString("prefix", "&#66EF8B&lMeerMC » &r");
        
        if (args.length == 0 || args[0].equalsIgnoreCase("timer")) {
            TextUtil.sendMessage(sender, prefix + " &aExecuting forced clearlag (timer)...");
            countdownSeconds = 3;
            if (!warnings.contains(2)) warnings.add(2);
            if (!warnings.contains(1)) warnings.add(1);
        } else if (args[0].equalsIgnoreCase("now")) {
            TextUtil.sendMessage(sender, prefix + " &aExecuting forced clearlag...");
            clearItems();
            countdownSeconds = intervalSeconds;
        } else {
            TextUtil.sendMessage(sender, prefix + " &cUnknown argument.");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("now", "timer");
        }
        return List.of();
    }
}
