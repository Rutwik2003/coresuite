package net.rocky.coresuite.module.website.commands;

import net.rocky.coresuite.module.website.WebsiteModule;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Random;

public class LinkCommand implements CommandExecutor {

    private final WebsiteModule module;
    private final Random random = new Random();

    public LinkCommand(WebsiteModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        Player player = (Player) sender;
        
        // Generate a 4-digit code
        int codeInt = 1000 + random.nextInt(9000);
        String code = String.valueOf(codeInt);

        // Save it in the module
        module.addPendingLink(code, player.getUniqueId());

        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&8[&bCoreSuite&8] &aYour Discord linking code is &e" + code));
        player.sendMessage(ChatColor.translateAlternateColorCodes('&', "&aGo to our Discord and type &e/verify " + code + " &ato link your account!"));
        
        return true;
    }
}
