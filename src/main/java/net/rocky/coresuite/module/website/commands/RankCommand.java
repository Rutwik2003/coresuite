package net.rocky.coresuite.module.website.commands;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.website.WebsiteModule;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class RankCommand implements CommandExecutor {

    private final WebsiteModule module;

    public RankCommand(WebsiteModule module) {
        this.module = module;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        String baseUrl = module.getConfig().getString("site.url", "https://meermc.com");
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
        String shopUrl = baseUrl + "/shop";

        if (!(sender instanceof Player player)) {
            sender.sendMessage("View server ranks at: " + shopUrl);
            return true;
        }

        Component msg = TextUtil.colorize("&8[&bMeerMC&8] &aSupport the server and view our ranks here: ")
                .append(TextUtil.colorize("&e&nClick here to open Shop&r")
                        .clickEvent(ClickEvent.openUrl(shopUrl))
                        .hoverEvent(HoverEvent.showText(TextUtil.colorize("&aClick to open &f" + shopUrl))));

        player.sendMessage(msg);
        return true;
    }
}
