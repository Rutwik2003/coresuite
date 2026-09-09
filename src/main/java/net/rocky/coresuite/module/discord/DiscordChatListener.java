package net.rocky.coresuite.module.discord;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.rocky.coresuite.CoreSuitePlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Listens for Minecraft chat and player lifecycle events,
 * relaying them asynchronously to Discord Webhooks with player avatars.
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

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        if (!config.getBoolean("events.chat.enabled", true)) return;

        String chatUrl = config.getString("webhooks.chat-url", "").trim();
        if (chatUrl.isEmpty()) return;

        Player player = event.getPlayer();
        String rawText = PlainTextComponentSerializer.plainText().serialize(event.message());
        String sanitizedText = DiscordSanitizer.toDiscord(rawText);

        if (sanitizedText.isEmpty()) return;

        String avatarUrl = skinResolver.getAvatarUrl(player);
        String username = skinResolver.getFormattedName(player);

        String payload = "{" +
                "\"username\": \"" + DiscordSanitizer.escapeJson(username) + "\"," +
                "\"avatar_url\": \"" + DiscordSanitizer.escapeJson(avatarUrl) + "\"," +
                "\"content\": \"" + DiscordSanitizer.escapeJson(sanitizedText) + "\"" +
                "}";

        webhookClient.sendAsync(chatUrl, payload);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        // 1. Update live status embed (debounced by 3 seconds)
        if (statusManager != null) {
            statusManager.scheduleDebouncedUpdate();
        }

        // 2. Dispatch join notification to chat webhook
        if (!config.getBoolean("events.join.enabled", true)) return;
        String chatUrl = config.getString("webhooks.chat-url", "").trim();
        if (chatUrl.isEmpty()) return;

        String format = config.getString("events.join.format", "📥 **{player}** joined the server");
        String message = format.replace("{player}", skinResolver.getFormattedName(player));

        String payload = "{\"content\": \"" + DiscordSanitizer.escapeJson(message) + "\"}";
        webhookClient.sendAsync(chatUrl, payload);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        // 1. Update live status embed (debounced by 3 seconds)
        if (statusManager != null) {
            statusManager.scheduleDebouncedUpdate();
        }

        // 2. Dispatch quit notification to chat webhook
        if (config.getBoolean("events.quit.enabled", true)) {
            String chatUrl = config.getString("webhooks.chat-url", "").trim();
            if (!chatUrl.isEmpty()) {
                String format = config.getString("events.quit.format", "📤 **{player}** left the server");
                String message = format.replace("{player}", skinResolver.getFormattedName(player));

                String payload = "{\"content\": \"" + DiscordSanitizer.escapeJson(message) + "\"}";
                webhookClient.sendAsync(chatUrl, payload);
            }
        }

        // 3. Invalidate skin cache
        skinResolver.clearCache(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (!config.getBoolean("events.death.enabled", true)) return;
        String chatUrl = config.getString("webhooks.chat-url", "").trim();
        if (chatUrl.isEmpty()) return;

        Component deathMessageComp = event.deathMessage();
        String deathText = deathMessageComp != null
                ? PlainTextComponentSerializer.plainText().serialize(deathMessageComp)
                : event.getEntity().getName() + " died";

        String sanitizedDeath = DiscordSanitizer.toDiscord(deathText);
        String format = config.getString("events.death.format", "☠ {message}");
        String message = format.replace("{message}", sanitizedDeath);

        String payload = "{\"content\": \"" + DiscordSanitizer.escapeJson(message) + "\"}";
        webhookClient.sendAsync(chatUrl, payload);
    }
}
