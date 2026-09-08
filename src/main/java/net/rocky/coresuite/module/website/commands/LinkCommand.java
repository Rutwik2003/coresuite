package net.rocky.coresuite.module.website.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.website.WebsiteModule;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class LinkCommand implements CommandExecutor {

    private final WebsiteModule module;

    public LinkCommand(WebsiteModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        String existingDiscordId = module.getLinkedDiscordId(player.getUniqueId());
        if (existingDiscordId != null && (args.length == 0 || !args[0].equalsIgnoreCase("new"))) {
            TextUtil.sendMessage(player, "&8[&bMeerMC&8] &aYour account is currently linked to Discord ID: &e" + existingDiscordId);
            TextUtil.sendMessage(player, "&7To generate a new code to re-link, type &e/link new&7.");
            return true;
        }

        // Generate a 4-digit code (valid for 10 minutes)
        String code = module.createPendingLink(player);

        TextUtil.sendMessage(player, "&8&m--------------------------------------------------");
        TextUtil.sendMessage(player, " &b&l🔗 Discord Account Verification");
        TextUtil.sendMessage(player, " &7Your one-time verification code is:");

        Component codeComponent = TextUtil.colorize("   &e&l" + code + " &8(&aClick to copy&8)")
                .clickEvent(ClickEvent.copyToClipboard(code))
                .hoverEvent(HoverEvent.showText(TextUtil.colorize("&aClick to copy &e" + code + " &ato your clipboard")));
        player.sendMessage(codeComponent);

        TextUtil.sendMessage(player, " &7Go to our Discord server and type: &b/verify " + code);
        TextUtil.sendMessage(player, " &8(This code will expire in 10 minutes)");
        TextUtil.sendMessage(player, "&8&m--------------------------------------------------");

        try {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.2f);
        } catch (Exception ignored) {}

        return true;
    }
}
