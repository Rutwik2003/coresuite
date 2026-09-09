package net.rocky.coresuite.module.discord;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CoreSuite Discord Integration Module.
 * Ultra-lightweight alternative to DiscordSRV with zero lag.
 * Features Webhook chat, cracked/Bedrock skin support, live status embed, and 2-way chat gateway.
 */
public class DiscordModule extends Module implements CommandExecutor, TabCompleter {

    private DiscordSkinResolver skinResolver;
    private DiscordWebhookClient webhookClient;
    private ServerStatusEmbedManager statusManager;
    private LightweightDiscordGateway gateway;
    private DiscordChatListener chatListener;

    public DiscordModule(CoreSuitePlugin plugin) {
        super(plugin, "Discord", "discord");
    }

    @Override
    public void onEnable() {
        skinResolver = new DiscordSkinResolver(plugin, config);
        webhookClient = new DiscordWebhookClient(plugin);
        statusManager = new ServerStatusEmbedManager(plugin, this, config, webhookClient, skinResolver);
        gateway = new LightweightDiscordGateway(plugin, config);
        chatListener = new DiscordChatListener(plugin, config, webhookClient, skinResolver, statusManager);

        registerListener(chatListener);
        statusManager.start();
        gateway.start();

        // Send Server Start notification
        if (config.getBoolean("events.server-start.enabled", true)) {
            String chatUrl = config.getString("webhooks.chat-url", "").trim();
            if (!chatUrl.isEmpty()) {
                String message = config.getString("events.server-start.message", "🟢 **Server is online and ready for players!**");
                String payload = "{\"content\": \"" + DiscordSanitizer.escapeJson(message) + "\"}";
                webhookClient.sendAsync(chatUrl, payload);
            }
        }

        PluginCommand command = plugin.getCommand("discord");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }
    }

    @Override
    public void onDisable() {
        // Send Server Stop notification
        if (config.getBoolean("events.server-stop.enabled", true) && webhookClient != null) {
            String chatUrl = config.getString("webhooks.chat-url", "").trim();
            if (!chatUrl.isEmpty()) {
                String message = config.getString("events.server-stop.message", "🔴 **Server has been shut down.**");
                String payload = "{\"content\": \"" + DiscordSanitizer.escapeJson(message) + "\"}";
                try {
                    // Quick synchronous post during shutdown
                    java.net.http.HttpClient.newHttpClient().send(
                            java.net.http.HttpRequest.newBuilder()
                                    .uri(java.net.URI.create(chatUrl))
                                    .header("Content-Type", "application/json; charset=utf-8")
                                    .timeout(java.time.Duration.ofSeconds(3))
                                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload))
                                    .build(),
                            java.net.http.HttpResponse.BodyHandlers.discarding()
                    );
                } catch (Exception ignored) {}
            }
        }

        if (statusManager != null) {
            statusManager.shutdown();
        }

        if (gateway != null) {
            gateway.stop();
        }

        PluginCommand command = plugin.getCommand("discord");
        if (command != null && command.getExecutor() == this) {
            command.setExecutor(null);
            command.setTabCompleter(null);
        }
    }

    /**
     * Saves the module configuration asynchronously.
     */
    public void saveModuleConfig() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::saveConfig);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            String invite = config.getString("invite-link", "https://discord.gg/yourserver");
            TextUtil.sendMessage(sender, "");
            TextUtil.sendMessage(sender, "&9&lDiscord Community");
            TextUtil.sendMessage(sender, "&7Join our official Discord server: &b" + invite);
            
            if (sender instanceof Player player) {
                Component clickComponent = Component.text("  ")
                        .append(Component.text("➡ [Click here to join Discord]")
                                .color(net.kyori.adventure.text.format.NamedTextColor.AQUA)
                                .clickEvent(ClickEvent.openUrl(invite))
                                .hoverEvent(HoverEvent.showText(Component.text("Click to open Discord invite"))));
                player.sendMessage(clickComponent);
            }
            TextUtil.sendMessage(sender, "");
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {
            case "status" -> {
                TextUtil.sendMessage(sender, "&8&m----------------------------------------");
                TextUtil.sendMessage(sender, "&9&lCoreSuite Discord Bridge Status");
                boolean chatWebhook = !config.getString("webhooks.chat-url", "").trim().isEmpty();
                boolean statusWebhook = !config.getString("webhooks.status-url", "").trim().isEmpty();
                boolean twoWay = config.getBoolean("discord-to-minecraft.enabled", false);
                boolean botToken = !config.getString("discord-to-minecraft.bot-token", "").trim().isEmpty();
                boolean channelId = !config.getString("discord-to-minecraft.channel-id", "").trim().isEmpty();

                TextUtil.sendMessage(sender, "&7Chat Webhook: " + (chatWebhook ? "&aConfigured" : "&cNot Configured"));
                TextUtil.sendMessage(sender, "&7Status Webhook: " + (statusWebhook ? "&aConfigured" : "&eUsing Chat Webhook"));
                TextUtil.sendMessage(sender, "&7Two-Way Gateway: " + (twoWay ? (botToken && channelId ? "&aActive" : "&eMissing Token/Channel") : "&7Disabled"));
                TextUtil.sendMessage(sender, "&8&m----------------------------------------");
                return true;
            }

            case "update" -> {
                if (!sender.hasPermission("coresuite.discord.admin")) {
                    TextUtil.sendMessage(sender, "&cYou don't have permission to use this command.");
                    return true;
                }
                if (statusManager != null) {
                    statusManager.updateStatus();
                    TextUtil.sendMessage(sender, "&aTriggered Discord status embed update!");
                }
                return true;
            }

            case "reload" -> {
                if (!sender.hasPermission("coresuite.discord.admin")) {
                    TextUtil.sendMessage(sender, "&cYou don't have permission to use this command.");
                    return true;
                }
                onReload();
                TextUtil.sendMessage(sender, "&aDiscord module reloaded successfully.");
                return true;
            }

            default -> {
                TextUtil.sendMessage(sender, "&cUsage: /discord [status|update|reload]");
                return true;
            }
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("status"));
            if (sender.hasPermission("coresuite.discord.admin")) {
                subs.addAll(List.of("update", "reload"));
            }
            return subs.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
