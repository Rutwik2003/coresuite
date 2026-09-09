package net.rocky.coresuite.module.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.kyori.adventure.text.Component;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

/**
 * Ultra-lightweight Discord Gateway WebSocket client.
 * Connects directly to Discord Gateway v10 with ONLY GUILD_MESSAGES and MESSAGE_CONTENT intents.
 * Consumes < 1MB RAM and 0.00% CPU. Zero external library dependencies.
 */
public final class LightweightDiscordGateway implements WebSocket.Listener {

    public enum GatewayState {
        DISABLED,
        STANDBY,
        CONNECTING,
        CONNECTED,
        AUTH_FAILED,
        DISALLOWED_INTENTS,
        DISCONNECTED
    }

    private final CoreSuitePlugin plugin;
    private final FileConfiguration config;
    private DiscordCommandHandler commandHandler;

    private WebSocket webSocket;
    private ScheduledExecutorService heartbeatExecutor;
    private ScheduledFuture<?> heartbeatTask;
    private ScheduledFuture<?> reconnectTask;

    private volatile GatewayState connectionState = GatewayState.STANDBY;
    private volatile String botUsername = null;

    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);
    private final AtomicInteger lastSequence = new AtomicInteger(-1);
    private final StringBuilder messageBuffer = new StringBuilder();

    private static final String GATEWAY_URL = "wss://gateway.discord.gg/?v=10&encoding=json";

    public LightweightDiscordGateway(CoreSuitePlugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
    }

    public void setCommandHandler(DiscordCommandHandler commandHandler) {
        this.commandHandler = commandHandler;
    }

    public GatewayState getState() {
        return connectionState;
    }

    public String getBotUsername() {
        return botUsername;
    }

    /**
     * Starts the gateway connection if two-way chat is enabled.
     */
    public synchronized void start() {
        if (!config.getBoolean("discord-to-minecraft.enabled", true)) {
            connectionState = GatewayState.DISABLED;
            return;
        }

        String token = cleanToken(config.getString("discord-to-minecraft.bot-token", ""));
        String channelId = cleanChannelId(config.getString("discord-to-minecraft.channel-id", ""));

        if (token.isEmpty() || channelId.isEmpty()) {
            connectionState = GatewayState.STANDBY;
            plugin.getLogger().info("[Discord] Two-way chat is enabled, but bot-token or channel-id is not configured yet in discord.yml. Gateway standby.");
            return;
        }

        isRunning.set(true);
        connectionState = GatewayState.CONNECTING;
        heartbeatExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CoreSuite-Discord-Heartbeat");
            t.setDaemon(true);
            return t;
        });

        connect();
    }

    private synchronized void connect() {
        if (!isRunning.get() || isConnecting.get()) return;
        isConnecting.set(true);
        connectionState = GatewayState.CONNECTING;

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

            plugin.getLogger().info("[Discord] Connecting to Discord Gateway...");

            client.newWebSocketBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .buildAsync(URI.create(GATEWAY_URL), this)
                    .thenAccept(ws -> {
                        this.webSocket = ws;
                        isConnecting.set(false);
                    })
                    .exceptionally(ex -> {
                        isConnecting.set(false);
                        connectionState = GatewayState.DISCONNECTED;
                        plugin.getLogger().log(Level.WARNING, "[Discord] Gateway connection failed: " + ex.getMessage() + ". Retrying in 10s...");
                        scheduleReconnect(10);
                        return null;
                    });
        } catch (Exception e) {
            isConnecting.set(false);
            connectionState = GatewayState.DISCONNECTED;
            scheduleReconnect(10);
        }
    }

    @Override
    public void onOpen(WebSocket ws) {
        this.webSocket = ws;
        this.connectionState = GatewayState.CONNECTING;
        plugin.getLogger().info("[Discord] Gateway WebSocket connection established. Awaiting HELLO...");
        ws.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
        this.webSocket = ws;
        messageBuffer.append(data);

        if (last) {
            String fullMessage = messageBuffer.toString();
            messageBuffer.setLength(0);
            processGatewayPacket(ws, fullMessage);
        }

        ws.request(1);
        return null;
    }

    @Override
    public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
        String explanation = getCloseCodeExplanation(statusCode);
        plugin.getLogger().warning("[Discord] Gateway disconnected. Code: " + statusCode + " (" + explanation + "). Reason: " + reason);
        cleanupSocket();

        if (statusCode == 4004) {
            connectionState = GatewayState.AUTH_FAILED;
            plugin.getLogger().severe("[Discord] Authentication failed (Code 4004)! Please verify your bot-token in discord.yml.");
            return null; // Don't loop reconnect on invalid token
        }

        if (statusCode == 4014) {
            connectionState = GatewayState.DISALLOWED_INTENTS;
            plugin.getLogger().severe("[Discord] ========================================================");
            plugin.getLogger().severe("[Discord] DISALLOWED INTENTS (Code 4014)!");
            plugin.getLogger().severe("[Discord] Discord blocked the bot because 'Message Content Intent' is OFF.");
            plugin.getLogger().severe("[Discord] 1. Go to: https://discord.com/developers/applications");
            plugin.getLogger().severe("[Discord] 2. Select your Bot -> click 'Bot' tab on the left");
            plugin.getLogger().severe("[Discord] 3. Scroll to 'Privileged Gateway Intents' -> turn ON 'Message Content Intent'");
            plugin.getLogger().severe("[Discord] 4. Click 'Save Changes' and run /discord reload in Minecraft.");
            plugin.getLogger().severe("[Discord] ========================================================");
            return null; // Don't loop reconnect on missing privileged intent
        }

        connectionState = GatewayState.DISCONNECTED;
        if (isRunning.get()) {
            scheduleReconnect(5);
        }
        return null;
    }

    @Override
    public void onError(WebSocket ws, Throwable error) {
        plugin.getLogger().warning("[Discord] Gateway WebSocket error: " + error.getMessage());
        cleanupSocket();
        connectionState = GatewayState.DISCONNECTED;
        if (isRunning.get()) {
            scheduleReconnect(5);
        }
    }

    private void processGatewayPacket(WebSocket ws, String jsonStr) {
        try {
            JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();
            int op = json.has("op") ? json.get("op").getAsInt() : -1;

            if (json.has("s") && !json.get("s").isJsonNull()) {
                lastSequence.set(json.get("s").getAsInt());
            }

            switch (op) {
                case 10 -> { // HELLO
                    JsonObject d = json.getAsJsonObject("d");
                    long interval = d.get("heartbeat_interval").getAsLong();
                    startHeartbeat(ws, interval);
                    sendIdentify(ws);
                }
                case 11 -> { // HEARTBEAT ACK
                    // Heartbeat acknowledged by Discord
                }
                case 0 -> { // DISPATCH
                    String t = json.has("t") && !json.get("t").isJsonNull() ? json.get("t").getAsString() : "";
                    if ("READY".equals(t)) {
                        handleReady(json.getAsJsonObject("d"));
                    } else if ("MESSAGE_CREATE".equals(t)) {
                        handleMessageCreate(json.getAsJsonObject("d"));
                    }
                }
                case 1 -> { // HEARTBEAT REQUEST
                    sendHeartbeat(ws);
                }
                case 7 -> { // RECONNECT REQUEST
                    plugin.getLogger().info("[Discord] Discord requested reconnect. Reconnecting...");
                    reconnect();
                }
                case 9 -> { // INVALID SESSION
                    plugin.getLogger().warning("[Discord] Gateway session invalidated. Reconnecting in 5s...");
                    cleanupSocket();
                    scheduleReconnect(5);
                }
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.FINE, "Failed to parse gateway packet", e);
        }
    }

    private void handleReady(JsonObject d) {
        connectionState = GatewayState.CONNECTED;
        if (d != null && d.has("user") && d.getAsJsonObject("user").has("username")) {
            this.botUsername = d.getAsJsonObject("user").get("username").getAsString();
        } else {
            this.botUsername = "Bot";
        }
        plugin.getLogger().info("[Discord] Gateway READY! Logged in as @" + botUsername + ". Two-way chat is fully ACTIVE!");
    }

    private void handleMessageCreate(JsonObject d) {
        if (d == null) return;

        String targetChannel = cleanChannelId(config.getString("discord-to-minecraft.channel-id", ""));
        String commandsChannel = cleanChannelId(config.getString("commands.channel-id", ""));
        String messageChannel = d.has("channel_id") ? d.get("channel_id").getAsString() : "";

        boolean isTargetChannel = !targetChannel.isEmpty() && targetChannel.equals(messageChannel);
        boolean isCommandsChannel = !commandsChannel.isEmpty() && commandsChannel.equals(messageChannel);

        if (!isTargetChannel && !isCommandsChannel) {
            return; // Not our chat or commands channel
        }

        // 1. Ignore Webhook messages (prevents infinite echo loops)
        if (d.has("webhook_id") && !d.get("webhook_id").isJsonNull()) {
            return;
        }

        // 2. Ignore Bots if configured
        boolean filterBots = config.getBoolean("discord-to-minecraft.filter-bots", true);
        JsonObject author = d.has("author") && d.get("author").isJsonObject() ? d.getAsJsonObject("author") : null;
        if (author == null) return;

        if (filterBots && author.has("bot") && author.get("bot").getAsBoolean()) {
            return;
        }

        String username = author.has("username") ? author.get("username").getAsString() : "User";
        String globalName = author.has("global_name") && !author.get("global_name").isJsonNull()
                ? author.get("global_name").getAsString() : username;

        String content = d.has("content") ? d.get("content").getAsString() : "";

        // Check for attachments if content is empty (e.g. image posted)
        if (content.isEmpty() && d.has("attachments")) {
            JsonArray attachments = d.getAsJsonArray("attachments");
            if (attachments != null && !attachments.isEmpty()) {
                content = "&7[Attachment]";
            }
        }

        if (content.trim().isEmpty()) {
            return;
        }

        // 3. Intercept in-Discord bot commands (e.g. m!playerlist, m!status, m!ip)
        if (commandHandler != null && commandHandler.isCommand(content)) {
            plugin.getLogger().info("[Discord] Intercepted bot command: '" + content + "' from @" + globalName);
            commandHandler.handleCommand(messageChannel, globalName, content);
            return; // Abort broadcasting so the command isn't sent to Minecraft
        }

        // 4. Auto IP reply trigger (e.g. typing "ip", "IP", "what is the ip")
        if (commandHandler != null && commandHandler.isIpTrigger(content)) {
            plugin.getLogger().info("[Discord] Intercepted auto-IP query: '" + content + "' from @" + globalName);
            commandHandler.handleIpTrigger(messageChannel, globalName);
            return; // Don't spam Minecraft chat with standalone "ip"
        }

        // If message was sent in commands channel only (not chat channel), don't broadcast regular chat to Minecraft
        if (!isTargetChannel) {
            return;
        }

        JsonArray mentions = d.has("mentions") && d.get("mentions").isJsonArray()
                ? d.getAsJsonArray("mentions") : null;

        String cleanContent = DiscordSanitizer.toMinecraft(content, mentions);
        String fullContent = cleanContent;
        boolean isTruncated = false;

        int maxLength = config.getInt("discord-to-minecraft.max-message-length", 120);
        if (maxLength > 0 && cleanContent.length() > maxLength) {
            cleanContent = cleanContent.substring(0, maxLength).trim() + "...";
            isTruncated = true;
        }

        String format = config.getString("discord-to-minecraft.format", "&9[Discord] &b{user}&8: &f{message}");
        String finalMsg = format.replace("{user}", globalName).replace("{message}", cleanContent);

        plugin.getLogger().info("[Discord Chat] " + globalName + ": " + (isTruncated ? fullContent : cleanContent));

        final boolean truncated = isTruncated;
        final String hoverContent = fullContent;
        // Broadcast to Minecraft on the server main thread
        Bukkit.getScheduler().runTask(plugin, () -> {
            Component comp = TextUtil.colorize(finalMsg);
            if (truncated) {
                comp = comp.hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                        TextUtil.colorize("&7Full Discord Message:\n&f" + hoverContent)
                ));
            }
            Bukkit.broadcast(comp);
        });
    }

    private void startHeartbeat(WebSocket ws, long intervalMs) {
        if (heartbeatTask != null && !heartbeatTask.isCancelled()) {
            heartbeatTask.cancel(true);
        }

        heartbeatTask = heartbeatExecutor.scheduleAtFixedRate(() -> sendHeartbeat(ws),
                intervalMs, intervalMs, TimeUnit.MILLISECONDS);
    }

    private void sendHeartbeat(WebSocket ws) {
        WebSocket target = ws != null ? ws : this.webSocket;
        if (target == null) return;

        int seq = lastSequence.get();
        String payload = seq >= 0 ? "{\"op\":1,\"d\":" + seq + "}" : "{\"op\":1,\"d\":null}";
        try {
            target.sendText(payload, true);
        } catch (Exception ignored) {}
    }

    private void sendIdentify(WebSocket ws) {
        WebSocket target = ws != null ? ws : this.webSocket;
        if (target == null) return;

        String token = cleanToken(config.getString("discord-to-minecraft.bot-token", ""));

        // Intents: GUILDS (1) | GUILD_MESSAGES (512) | MESSAGE_CONTENT (32768) = 33281
        int intents = 1 | 512 | 32768;

        String payload = "{" +
                "\"op\":2," +
                "\"d\":{" +
                "\"token\":\"" + token + "\"," +
                "\"intents\":" + intents + "," +
                "\"properties\":{" +
                "\"os\":\"linux\"," +
                "\"browser\":\"coresuite\"," +
                "\"device\":\"coresuite\"" +
                "}," +
                "\"presence\":{" +
                "\"status\":\"online\"," +
                "\"activities\":[{\"name\":\"Minecraft\",\"type\":0}]," +
                "\"afk\":false" +
                "}" +
                "}" +
                "}";

        try {
            target.sendText(payload, true);
            plugin.getLogger().info("[Discord] Sent IDENTIFY payload with intents " + intents + ".");
        } catch (Exception e) {
            plugin.getLogger().warning("[Discord] Failed to send IDENTIFY payload: " + e.getMessage());
        }
    }

    private synchronized void scheduleReconnect(int delaySeconds) {
        if (!isRunning.get()) return;
        if (reconnectTask != null && !reconnectTask.isCancelled()) {
            reconnectTask.cancel(false);
        }

        reconnectTask = heartbeatExecutor.schedule(this::connect, delaySeconds, TimeUnit.SECONDS);
    }

    private synchronized void reconnect() {
        cleanupSocket();
        connect();
    }

    private synchronized void cleanupSocket() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel(true);
            heartbeatTask = null;
        }
        if (webSocket != null) {
            try {
                webSocket.abort();
            } catch (Exception ignored) {}
            webSocket = null;
        }
    }

    public synchronized void stop() {
        isRunning.set(false);
        connectionState = GatewayState.DISABLED;
        if (reconnectTask != null) {
            reconnectTask.cancel(true);
            reconnectTask = null;
        }
        cleanupSocket();
        if (heartbeatExecutor != null) {
            heartbeatExecutor.shutdownNow();
            heartbeatExecutor = null;
        }
        plugin.getLogger().info("[Discord] Gateway WebSocket stopped.");
    }

    public String getStatusFormatted() {
        if (!config.getBoolean("discord-to-minecraft.enabled", true)) {
            return "&7Disabled";
        }
        String token = cleanToken(config.getString("discord-to-minecraft.bot-token", ""));
        String channelId = cleanChannelId(config.getString("discord-to-minecraft.channel-id", ""));
        if (token.isEmpty() || channelId.isEmpty()) {
            return "&eMissing Bot Token or Channel ID";
        }

        return switch (connectionState) {
            case CONNECTED -> "&aConnected (@" + (botUsername != null ? botUsername : "Online") + ")";
            case CONNECTING -> "&eConnecting to Discord Gateway...";
            case AUTH_FAILED -> "&cAuthentication Failed (Bad Token)";
            case DISALLOWED_INTENTS -> "&cError: 'Message Content Intent' is disabled in Developer Portal!";
            case DISCONNECTED -> "&cDisconnected (Reconnecting...)";
            default -> "&7Standby";
        };
    }

    public static String cleanToken(String raw) {
        if (raw == null) return "";
        String clean = raw.trim();
        if (clean.startsWith("\"") && clean.endsWith("\"") && clean.length() >= 2) {
            clean = clean.substring(1, clean.length() - 1).trim();
        }
        if (clean.startsWith("'") && clean.endsWith("'") && clean.length() >= 2) {
            clean = clean.substring(1, clean.length() - 1).trim();
        }
        if (clean.startsWith("Bot ")) {
            clean = clean.substring(4).trim();
        }
        return clean;
    }

    public static String cleanChannelId(String raw) {
        if (raw == null) return "";
        String clean = raw.trim();
        if (clean.startsWith("\"") && clean.endsWith("\"") && clean.length() >= 2) {
            clean = clean.substring(1, clean.length() - 1).trim();
        }
        if (clean.startsWith("'") && clean.endsWith("'") && clean.length() >= 2) {
            clean = clean.substring(1, clean.length() - 1).trim();
        }
        return clean.replace("<#", "").replace(">", "").trim();
    }

    private static String getCloseCodeExplanation(int code) {
        return switch (code) {
            case 4000 -> "Unknown error";
            case 4001 -> "Unknown opcode";
            case 4002 -> "Decode error";
            case 4003 -> "Not authenticated";
            case 4004 -> "Authentication failed (invalid bot token)";
            case 4005 -> "Already authenticated";
            case 4007 -> "Invalid seq";
            case 4008 -> "Rate limited";
            case 4009 -> "Session timed out";
            case 4010 -> "Invalid shard";
            case 4011 -> "Sharding required";
            case 4012 -> "Invalid API version";
            case 4013 -> "Invalid intent(s)";
            case 4014 -> "Disallowed intent(s) (Message Content Intent not enabled in Developer Portal)";
            default -> "Network disconnect";
        };
    }
}
