package net.rocky.coresuite.module.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.rocky.coresuite.CoreSuitePlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.stream.Collectors;

/**
 * Handles in-Discord bot commands (e.g. m!playerlist, m!status, m!ip, m!help).
 * Delivers sleek responses directly to the Discord channel without spamming Minecraft chat.
 */
public final class DiscordCommandHandler {

    private final CoreSuitePlugin plugin;
    private final FileConfiguration config;
    private final DiscordWebhookClient webhookClient;
    private final DiscordSkinResolver skinResolver;
    private final Instant startTime;

    public DiscordCommandHandler(CoreSuitePlugin plugin, FileConfiguration config,
                                 DiscordWebhookClient webhookClient, DiscordSkinResolver skinResolver,
                                 Instant startTime) {
        this.plugin = plugin;
        this.config = config;
        this.webhookClient = webhookClient;
        this.skinResolver = skinResolver;
        this.startTime = startTime;
    }

    /**
     * Checks if a message begins with the configured command prefix.
     */
    public boolean isCommand(String message) {
        if (message == null) return false;
        if (!config.getBoolean("commands.enabled", true)) return false;

        String prefix = getPrefix();
        return message.trim().toLowerCase().startsWith(prefix.toLowerCase());
    }

    /**
     * Executes the Discord command asynchronously.
     */
    public void handleCommand(String channelId, String authorName, String message) {
        String prefix = getPrefix();
        String content = message.trim().substring(prefix.length()).trim();
        if (content.isEmpty()) return;

        String[] parts = content.split("\\s+");
        String cmd = parts[0].toLowerCase();

        plugin.getLogger().info("[Discord Command] Received command '" + cmd + "' from @" + authorName + " in channel " + channelId);

        String botToken = config.getString("discord-to-minecraft.bot-token", "").trim();

        switch (cmd) {
            case "playerlist":
            case "players":
            case "online":
            case "list":
                sendPlayerList(channelId, botToken);
                break;
            case "status":
            case "info":
                sendStatus(channelId, botToken);
                break;
            case "ip":
            case "server":
            case "address":
                sendServerIp(channelId, botToken);
                break;
            case "help":
                sendHelp(channelId, botToken);
                break;
            default:
                plugin.getLogger().info("[Discord Command] Unknown command '" + cmd + "'. Ignoring.");
                break;
        }
    }

    private String getPrefix() {
        String prefix = config.getString("commands.prefix", "m!").trim();
        return prefix.isEmpty() ? "m!" : prefix;
    }

    private void sendPlayerList(String channelId, String botToken) {
        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        int onlineCount = players.size();
        int maxPlayers = Bukkit.getMaxPlayers();

        JsonObject embed = new JsonObject();
        embed.addProperty("title", "🎮 MeerMC • Online Players");
        embed.addProperty("color", 3066993); // Green

        if (onlineCount == 0) {
            embed.addProperty("description", "There are currently **no players online**.\n\n*Be the first to join!*");
        } else {
            String roster = players.stream()
                    .limit(50)
                    .map(p -> "`•` " + skinResolver.getFormattedName(p))
                    .collect(Collectors.joining("\n"));

            if (onlineCount > 50) {
                roster += "\n*...and " + (onlineCount - 50) + " more*";
            }

            embed.addProperty("description", "**Online (" + onlineCount + " / " + maxPlayers + "):**\n\n" + roster);
        }

        JsonObject footer = new JsonObject();
        footer.addProperty("text", "Server IP: " + config.getString("server-status.embed.java-ip", "meermc.rutwikdev.com:25569"));
        embed.add("footer", footer);
        embed.addProperty("timestamp", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));

        sendEmbed(channelId, botToken, embed);
    }

    private void sendStatus(String channelId, String botToken) {
        int onlineCount = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();
        double tps = Math.min(20.0, Bukkit.getTPS()[0]);
        String tpsFormatted = String.format("%.2f", tps);
        String uptime = formatUptime();
        String javaIp = config.getString("server-status.embed.java-ip", "meermc.rutwikdev.com:25569");
        String bedrockIp = config.getString("server-status.embed.bedrock-ip", "meermc.rutwikdev.com:25569");

        JsonObject embed = new JsonObject();
        embed.addProperty("title", "📊 MeerMC • Server Status");
        embed.addProperty("color", 3066993);

        JsonArray fields = new JsonArray();

        // Status field
        JsonObject f1 = new JsonObject();
        f1.addProperty("name", "📡 Status");
        f1.addProperty("value", "🟢 Online");
        f1.addProperty("inline", true);
        fields.add(f1);

        // Players field
        JsonObject f2 = new JsonObject();
        f2.addProperty("name", "👥 Players");
        f2.addProperty("value", "`" + onlineCount + " / " + maxPlayers + "`");
        f2.addProperty("inline", true);
        fields.add(f2);

        // TPS field
        JsonObject f3 = new JsonObject();
        f3.addProperty("name", "⚡ TPS");
        f3.addProperty("value", "`" + tpsFormatted + "`");
        f3.addProperty("inline", true);
        fields.add(f3);

        // Uptime field
        JsonObject f4 = new JsonObject();
        f4.addProperty("name", "⏱ Uptime");
        f4.addProperty("value", "`" + uptime + "`");
        f4.addProperty("inline", true);
        fields.add(f4);

        // Connection IPs
        JsonObject f5 = new JsonObject();
        f5.addProperty("name", "🌐 Connection Addresses");
        f5.addProperty("value", "**Java Edition:** `" + javaIp + "`\n**Bedrock Edition:** `" + bedrockIp + "`");
        f5.addProperty("inline", false);
        fields.add(f5);

        embed.add("fields", fields);
        embed.addProperty("timestamp", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));

        sendEmbed(channelId, botToken, embed);
    }

    private void sendServerIp(String channelId, String botToken) {
        String javaIp = config.getString("server-status.embed.java-ip", "meermc.rutwikdev.com:25569");
        String bedrockIp = config.getString("server-status.embed.bedrock-ip", "meermc.rutwikdev.com:25569");

        JsonObject embed = new JsonObject();
        embed.addProperty("title", "🌐 MeerMC Server Connection Details");
        embed.addProperty("color", 3447003); // Blue

        embed.addProperty("description",
                "**Java Edition:**\n`" + javaIp + "`\n\n" +
                "**Bedrock Edition:**\nIP: `" + bedrockIp.split(":")[0] + "`\nPort: `" + (bedrockIp.contains(":") ? bedrockIp.split(":")[1] : "25569") + "`\n\n" +
                "*Join from PC, Mobile, or Consoles!*");

        embed.addProperty("timestamp", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));

        sendEmbed(channelId, botToken, embed);
    }

    private void sendHelp(String channelId, String botToken) {
        String p = getPrefix();

        JsonObject embed = new JsonObject();
        embed.addProperty("title", "🤖 MeerMC Discord Bot Commands");
        embed.addProperty("color", 10181046); // Purple

        embed.addProperty("description",
                "`" + p + "playerlist` — View who is playing on the server right now\n" +
                "`" + p + "status` — Live server status, TPS, uptime, and player count\n" +
                "`" + p + "ip` — Server IP and port for Java and Bedrock\n" +
                "`" + p + "help` — Show this command list\n\n" +
                "*Note: In-game chat is synced with this Discord channel!*");

        embed.addProperty("timestamp", DateTimeFormatter.ISO_INSTANT.format(Instant.now()));

        sendEmbed(channelId, botToken, embed);
    }

    private String getDestination(String channelId) {
        String webhook = config.getString("webhooks.chat-url", "").trim();
        if (!webhook.isEmpty()) return webhook;
        return channelId;
    }

    private void sendEmbed(String channelId, String botToken, JsonObject embed) {
        JsonArray embeds = new JsonArray();
        embeds.add(embed);
        JsonObject payload = new JsonObject();
        payload.add("embeds", embeds);

        String destination = getDestination(channelId);
        if (webhookClient.isWebhookUrl(destination)) {
            payload.addProperty("username", "MeerMC Bot");
            String avatarUrl = config.getString("avatars.default-avatar-url", "https://mc-heads.net/avatar/MHF_Steve/100.png");
            payload.addProperty("avatar_url", avatarUrl);
        }

        plugin.getLogger().info("[Discord Command] Sending response embed via " + (webhookClient.isWebhookUrl(destination) ? "Webhook" : "Bot REST API"));

        webhookClient.sendHybridAsync(destination, botToken, payload.toString());
    }

    private String formatUptime() {
        Duration d = Duration.between(startTime, Instant.now());
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        return hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
    }
}
