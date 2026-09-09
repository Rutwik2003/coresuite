package net.rocky.coresuite.module.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.rocky.coresuite.CoreSuitePlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Listens for Minecraft chat and player lifecycle events,
 * relaying them asynchronously to Discord with styled embeds or webhooks.
 */
public final class DiscordChatListener implements Listener {

    private final CoreSuitePlugin plugin;
    private final FileConfiguration config;
    private final DiscordWebhookClient webhookClient;
    private final DiscordSkinResolver skinResolver;
    private final ServerStatusEmbedManager statusManager;

    public DiscordChatListener(CoreSuitePlugin plugin, FileConfiguration config,
                               DiscordWebhookClient webhookClient, DiscordSkinResolver skinResolver,
                               ServerStatusEmbedManager statusManager) {
        this.plugin = plugin;
        this.config = config;
        this.webhookClient = webhookClient;
        this.skinResolver = skinResolver;
        this.statusManager = statusManager;
    }

    private String getChatDestination() {
        String url = config.getString("webhooks.chat-url", "").trim();
        if (!url.isEmpty()) return url;
        return config.getString("discord-to-minecraft.channel-id", "").trim();
    }

    private String getBotToken() {
        return config.getString("discord-to-minecraft.bot-token", "").trim();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!config.getBoolean("events.chat.enabled", true)) return;

        String destination = getChatDestination();
        if (destination.isEmpty()) return;

        Player player = event.getPlayer();
        String rawText = PlainTextComponentSerializer.plainText().serialize(event.message());
        String sanitizedText = DiscordSanitizer.toDiscord(rawText);

        if (sanitizedText.isEmpty()) return;

        String avatarUrl = skinResolver.getAvatarUrl(player);
        String username = skinResolver.getFormattedName(player);

        JsonObject payload = new JsonObject();
        if (webhookClient.isWebhookUrl(destination)) {
            // Webhook mode: dynamic username + skin avatar
            payload.addProperty("username", username);
            payload.addProperty("avatar_url", avatarUrl);
            payload.addProperty("content", sanitizedText);
        } else {
            // Bot REST API mode: prefix with bold player name
            payload.addProperty("content", "**" + username + ":** " + sanitizedText);
        }

        webhookClient.sendHybridAsync(destination, getBotToken(), payload.toString());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (statusManager != null) {
            statusManager.scheduleDebouncedUpdate();
        }

        if (!config.getBoolean("events.join.enabled", true)) return;
        String destination = getChatDestination();
        if (destination.isEmpty()) return;

        String style = config.getString("events.style", "EMBED").toUpperCase();
        String avatarUrl = skinResolver.getAvatarUrl(player);
        String playerName = skinResolver.getFormattedName(player);
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();

        JsonObject payload = new JsonObject();
        if ("EMBED".equals(style)) {
            int color = config.getInt("events.join.color", config.getInt("events.join-color", 3066993)); // Emerald Green
            String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

            JsonObject embed = new JsonObject();
            embed.addProperty("color", color);

            JsonObject author = new JsonObject();
            author.addProperty("name", playerName + " joined the server");
            author.addProperty("icon_url", avatarUrl);
            embed.add("author", author);

            embed.addProperty("description", "Online: `" + online + " / " + max + "`");
            embed.addProperty("timestamp", timestamp);

            JsonArray embeds = new JsonArray();
            embeds.add(embed);
            payload.add("embeds", embeds);
        } else {
            String format = config.getString("events.join.format", "📥 **{player}** joined the server");
            String message = format.replace("{player}", playerName);
            payload.addProperty("content", message);
        }

        webhookClient.sendHybridAsync(destination, getBotToken(), payload.toString());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (statusManager != null) {
            statusManager.scheduleDebouncedUpdate();
        }

        if (config.getBoolean("events.quit.enabled", true)) {
            String destination = getChatDestination();
            if (!destination.isEmpty()) {
                String style = config.getString("events.style", "EMBED").toUpperCase();
                String avatarUrl = skinResolver.getAvatarUrl(player);
                String playerName = skinResolver.getFormattedName(player);
                int online = Math.max(0, Bukkit.getOnlinePlayers().size() - 1);
                int max = Bukkit.getMaxPlayers();

                JsonObject payload = new JsonObject();
                if ("EMBED".equals(style)) {
                    int color = config.getInt("events.quit.color", config.getInt("events.quit-color", 15158332)); // Coral Red
                    String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

                    JsonObject embed = new JsonObject();
                    embed.addProperty("color", color);

                    JsonObject author = new JsonObject();
                    author.addProperty("name", playerName + " left the server");
                    author.addProperty("icon_url", avatarUrl);
                    embed.add("author", author);

                    embed.addProperty("description", "Online: `" + online + " / " + max + "`");
                    embed.addProperty("timestamp", timestamp);

                    JsonArray embeds = new JsonArray();
                    embeds.add(embed);
                    payload.add("embeds", embeds);
                } else {
                    String format = config.getString("events.quit.format", "📤 **{player}** left the server");
                    String message = format.replace("{player}", playerName);
                    payload.addProperty("content", message);
                }

                webhookClient.sendHybridAsync(destination, getBotToken(), payload.toString());
            }
        }

        skinResolver.clearCache(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (!config.getBoolean("events.death.enabled", true)) return;
        String destination = getChatDestination();
        if (destination.isEmpty()) return;

        Player player = event.getEntity();
        Component deathMessageComp = event.deathMessage();
        String deathText = deathMessageComp != null
                ? PlainTextComponentSerializer.plainText().serialize(deathMessageComp)
                : player.getName() + " died";

        String sanitizedDeath = DiscordSanitizer.toDiscord(deathText);
        String style = config.getString("events.style", "EMBED").toUpperCase();
        String avatarUrl = skinResolver.getAvatarUrl(player);

        JsonObject payload = new JsonObject();
        if ("EMBED".equals(style)) {
            int color = config.getInt("events.death.color", config.getInt("events.death-color", 10038562)); // Dark Red
            String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());

            JsonObject embed = new JsonObject();
            embed.addProperty("color", color);

            JsonObject author = new JsonObject();
            author.addProperty("name", sanitizedDeath);
            author.addProperty("icon_url", avatarUrl);
            embed.add("author", author);

            embed.addProperty("timestamp", timestamp);

            JsonArray embeds = new JsonArray();
            embeds.add(embed);
            payload.add("embeds", embeds);
        } else {
            String format = config.getString("events.death.format", "☠ {message}");
            String message = format.replace("{message}", sanitizedDeath);
            payload.addProperty("content", message);
        }

        webhookClient.sendHybridAsync(destination, getBotToken(), payload.toString());
    }
}
