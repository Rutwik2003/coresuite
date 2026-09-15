package net.rocky.coresuite.module.announce;

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

import java.util.ArrayList;
import java.util.List;

public class AnnounceModule extends Module implements CommandExecutor, TabCompleter {

    public AnnounceModule(CoreSuitePlugin plugin) {
        super(plugin, "Announce", "announce");
    }

    @Override
    public void onEnable() {
        PluginCommand command = plugin.getCommand("announce");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    @Override
    public void onDisable() {
        PluginCommand command = plugin.getCommand("announce");
        if (command != null && command.getExecutor() == this) {
            command.setExecutor(null);
            command.setTabCompleter(null);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("coresuite.announce.use")) {
            TextUtil.sendMessage(sender, "&cNo permission.");
            return true;
        }

        if (args.length == 0) {
            TextUtil.sendMessage(sender, "&cUsage: /announce <message>");
            return true;
        }

        String message = String.join(" ", args);
        String title = config.getString("title", "&4&lAnnouncement!");
        
        // Minecraft titles cannot word-wrap. If it's too long, it overflows off screen.
        // So we smartly truncate the subtitle if it's longer than 40 characters.
        String subtitleText = message;
        if (subtitleText.length() > 40) {
            subtitleText = subtitleText.substring(0, 37) + "...";
        }
        String subtitle = config.getString("subtitle-color", "&f") + subtitleText;
        
        int fadeIn = config.getInt("fade-in", 10);
        int stay = config.getInt("stay", 70);
        int fadeOut = config.getInt("fade-out", 20);
        
        String soundStr = config.getString("sound", "ENTITY_ENDER_DRAGON_GROWL");
        Sound sound = null;
        try {
            sound = net.rocky.coresuite.common.compat.PaperCompat.sound(soundStr);
        } catch (IllegalArgumentException ignored) {}

        for (Player player : Bukkit.getOnlinePlayers()) {
            TextUtil.sendTitle(player, title, subtitle, fadeIn, stay, fadeOut);
            if (sound != null) {
                player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
            }
            
            // Format the long message nicely in chat where it CAN word-wrap natively
            TextUtil.sendMessage(player, "");
            TextUtil.sendMessage(player, "  &4&lAnnouncement");
            TextUtil.sendMessage(player, "  &7" + message);
            TextUtil.sendMessage(player, "");
        }
        
        if (!(sender instanceof Player)) {
            TextUtil.sendMessage(sender, "&aAnnouncement sent!");
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return new ArrayList<>();
    }
}
