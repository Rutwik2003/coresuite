package net.rocky.coresuite.module.website.commands;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class RankCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;

        TextComponent message = new TextComponent(ChatColor.translateAlternateColorCodes('&', 
            "&8[&bCoreSuite&8] &aSupport the server and view our ranks here: &e&nClick here to view Shop"));
        message.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, "https://meermc.com/shop")); // Can be configurable later

        player.spigot().sendMessage(message);
        
        return true;
    }
}
