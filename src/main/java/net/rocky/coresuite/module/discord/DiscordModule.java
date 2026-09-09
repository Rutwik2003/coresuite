package net.rocky.coresuite.module.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
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

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * CoreSuite Discord Integration Module.
 * Ultra-lightweight alternative to DiscordSRV with zero lag.
 * Features Hybrid Webhook + Bot REST support, cracked/Bedrock skin resolution,
 * sleek embeds, and 2-way chat gateway.
 */
public class DiscordModule extends Module implements CommandExecutor, TabCompleter {

    private final Instant startTime = Instant.now();
    private DiscordSkinResolver skinResolver;
    private DiscordWebhookClient webhookClient;
    private ServerStatusEmbedManager statusManager;
    private LightweightDiscordGateway gateway;
    private DiscordChatListener chatListener;
    private DiscordCommandHandler commandHandler;
    private DiscordTopicManager topicManager;
    private boolean isReloading = false;

    public DiscordModule(CoreSuitePlugin plugin) {
        super(plugin, "Discord", "discord");
    }

    @Override
    public void onEnable() {
        skinResolver = new DiscordSkinResolver(plugin, config);
        webhookClient = new DiscordWebhookClient(plugin);
        statusManager = new ServerStatusEmbedManager(plugin, this, config, webhookClient, skinResolver);
        commandHandler = new DiscordCommandHandler(plugin, config, webhookClient, skinResolver, startTime);
        gateway = new LightweightDiscordGateway(plugin, config);
        gateway.setCommandHandler(commandHandler);
        chatListener = new DiscordChatListener(plugin, config, webhookClient, skinResolver, statusManager);
        topicManager = new DiscordTopicManager(plugin, config, webhookClient, startTime);

        registerListener(chatListener);
        statusManager.start();
        gateway.start();
        topicManager.start();

        // Send Server Start notification (only on real startup, not on /discord reload)
        if (!isReloading && config.getBoolean("events.server-start.enabled", true)) {
            String destination = getChatDestination();
            if (!destination.isEmpty()) {
                String payload = buildStartEmbedJson();
                webhookClient.sendHybridAsync(destination, getBotToken(), payload);
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
        // Send Server Stop notification (only on real shutdown, not on /discord reload)
        if (!isReloading && config.getBoolean("events.server-stop.enabled", true) && webhookClient != null) {
            String destination = getChatDestination();
            if (!destination.isEmpty()) {
                String payload = buildStopEmbedJson();
                try {
                    webhookClient.patchHybridSync(destination, "nonexistent", getBotToken(), payload); // or send
                    // Send synchronous POST
                    java.net.http.HttpRequest.Builder builder = java.net.http.HttpRequest.newBuilder()
                            .timeout(java.time.Duration.ofSeconds(3))
                            .header("Content-Type", "application/json; charset=utf-8")
                            .header("User-Agent", "CoreSuite-DiscordBridge/1.0 (Minecraft Paper)");

                    if (webhookClient.isWebhookUrl(destination)) {
                        builder.uri(java.net.URI.create(destination));
                    } else {
                        builder.uri(java.net.URI.create("https://discord.com/api/v10/channels/" + destination + "/messages"))
                               .header("Authorization", "Bot " + getBotToken());
                    }

                    java.net.http.HttpClient.newHttpClient().send(
                            builder.POST(java.net.http.HttpRequest.BodyPublishers.ofString(payload)).build(),
                            java.net.http.HttpResponse.BodyHandlers.discarding()
                    );
                } catch (Exception ignored) {}
            }
        }

        if (topicManager != null) {
            topicManager.shutdown();
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

    @Override
    public void onReload() {
        isReloading = true;
        try {
            super.onReload();
        } finally {
            isReloading = false;
        }
    }

    private String getChatDestination() {
        String url = config.getString("webhooks.chat-url", "").trim();
        if (!url.isEmpty()) return url;
        return config.getString("discord-to-minecraft.channel-id", "").trim();
    }

    private String getBotToken() {
        return config.getString("discord-to-minecraft.bot-token", "").trim();
    }

    private String buildStartEmbedJson() {
        String title = config.getString("events.server-start.title", "🟢 Server is Online!");
        String javaIp = config.getString("server-status.embed.java-ip", "meermc.rutwikdev.com:25569");
        String bedrockIp = config.getString("server-status.embed.bedrock-ip", "meermc.rutwikdev.com:25569");
        String desc = config.getString("events.server-start.description",
                "The Minecraft server is online and ready for players!\n\n" +
                "**Java IP:** `" + javaIp + "`\n" +
                "**Bedrock IP:** `" + bedrockIp + "`");
        String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

        JsonObject embed = new JsonObject();
        embed.addProperty("title", title);
        embed.addProperty("color", 3066993);
        embed.addProperty("description", desc);

        JsonObject footer = new JsonObject();
        footer.addProperty("text", "CoreSuite Discord Bridge");
        embed.add("footer", footer);
        embed.addProperty("timestamp", timestamp);

        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        JsonObject payload = new JsonObject();
        payload.add("embeds", embeds);

        return payload.toString();
    }

    private String buildStopEmbedJson() {
        String title = config.getString("events.server-stop.title", "🔴 Server Shutdown");
        String desc = config.getString("events.server-stop.description",
                "The Minecraft server has been shut down or is restarting.\nWe will be back online shortly!");
        String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

        JsonObject embed = new JsonObject();
        embed.addProperty("title", title);
        embed.addProperty("color", 15158332);
        embed.addProperty("description", desc);

        JsonObject footer = new JsonObject();
        footer.addProperty("text", "CoreSuite Discord Bridge");
        embed.add("footer", footer);
        embed.addProperty("timestamp", timestamp);

        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        JsonObject payload = new JsonObject();
        payload.add("embeds", embeds);

        return payload.toString();
    }

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
                boolean botToken = !config.getString("discord-to-minecraft.bot-token", "").trim().isEmpty();

                TextUtil.sendMessage(sender, "&7Chat Delivery: " + (chatWebhook ? "&aWebhook (Skin Avatars)" : (botToken ? "&bBot REST API" : "&cNot Configured")));
                TextUtil.sendMessage(sender, "&7Status Embed: " + (statusWebhook ? "&aDedicated Webhook" : (chatWebhook ? "&aChat Webhook" : (botToken ? "&bBot REST API" : "&cNot Configured"))));
                TextUtil.sendMessage(sender, "&7Two-Way Gateway: " + (gateway != null ? gateway.getStatusFormatted() : "&7Disabled"));

                if (gateway != null) {
                    if (gateway.getState() == LightweightDiscordGateway.GatewayState.DISALLOWED_INTENTS) {
                        TextUtil.sendMessage(sender, "");
                        TextUtil.sendMessage(sender, "&c&lACTION REQUIRED (Why bot is offline):");
                        TextUtil.sendMessage(sender, "&e1. Open &bhttps://discord.com/developers/applications");
                        TextUtil.sendMessage(sender, "&e2. Click your bot -> click &f'Bot'&e tab on the left.");
                        TextUtil.sendMessage(sender, "&e3. Scroll down to &f'Privileged Gateway Intents'&e.");
                        TextUtil.sendMessage(sender, "&e4. Turn ON &a'Message Content Intent'&e!");
                        TextUtil.sendMessage(sender, "&e5. Click &fSave Changes&e, then run &a/discord reload&e.");
                    } else if (gateway.getState() == LightweightDiscordGateway.GatewayState.AUTH_FAILED) {
                        TextUtil.sendMessage(sender, "");
                        TextUtil.sendMessage(sender, "&c&lACTION REQUIRED (Why bot is offline):");
                        TextUtil.sendMessage(sender, "&eYour Discord Bot Token is invalid (Error 4004).");
                        TextUtil.sendMessage(sender, "&eReset your bot token in Developer Portal and paste it in &fmodules/discord.yml&e!");
                    }
                }
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
