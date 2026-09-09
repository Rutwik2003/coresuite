package net.rocky.coresuite.module.discord;

import net.rocky.coresuite.CoreSuitePlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Manages the live Discord server status & player list embed.
 * Periodically and reactively updates via Webhook in-place message patching (PATCH).
 */
public final class ServerStatusEmbedManager {

    private final CoreSuitePlugin plugin;
    private final DiscordModule module;
    private final FileConfiguration config;
    private final DiscordWebhookClient webhookClient;
    private final DiscordSkinResolver skinResolver;

    private String messageId;
    private BukkitTask recurringTask;
    private BukkitTask debounceTask;
    private final AtomicBoolean isUpdating = new AtomicBoolean(false);
    private static final Pattern ID_PATTERN = Pattern.compile("\"id\"\\s*:\\s*\"(\\d+)\"");

    public ServerStatusEmbedManager(CoreSuitePlugin plugin, DiscordModule module, FileConfiguration config,
                                    DiscordWebhookClient webhookClient, DiscordSkinResolver skinResolver) {
        this.plugin = plugin;
        this.module = module;
        this.config = config;
        this.webhookClient = webhookClient;
        this.skinResolver = skinResolver;
        this.messageId = config.getString("server-status.message-id", "").trim();
    }

    /**
     * Starts the periodic update task and triggers an initial status refresh.
     */
    public void start() {
        if (!config.getBoolean("server-status.enabled", true)) return;

        int interval = Math.max(15, config.getInt("server-status.update-interval-seconds", 60));

        // Initial update after 3 seconds to let server initialize
        Bukkit.getScheduler().runTaskLater(plugin, this::updateStatus, 60L);

        // Periodic recurring task
        recurringTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateStatus, interval * 20L, interval * 20L);
    }

    /**
     * Debounced update triggered on player join/quit.
     * Coalesces rapid joins/quits into a single update after 3 seconds.
     */
    public void scheduleDebouncedUpdate() {
        if (!config.getBoolean("server-status.enabled", true)) return;

        if (debounceTask != null && !debounceTask.isCancelled()) {
            debounceTask.cancel();
        }

        debounceTask = Bukkit.getScheduler().runTaskLater(plugin, this::updateStatus, 60L); // 3-second debounce
    }

    /**
     * Performs the asynchronous embed update.
     */
    public void updateStatus() {
        if (!config.getBoolean("server-status.enabled", true)) return;
        String webhookUrl = getStatusWebhookUrl();
        if (webhookUrl.isEmpty()) return;

        if (!isUpdating.compareAndSet(false, true)) {
            return; // Previous update still processing
        }

        // Collect server stats on main thread safely
        int onlineCount = Bukkit.getOnlinePlayers().size();
        int maxPlayers = Bukkit.getMaxPlayers();
        double tps = getRecentTps();
        String uptime = getFormattedUptime();

        List<String> playerNames = new ArrayList<>();
        if (config.getBoolean("server-status.embed.show-playerlist", true)) {
            int maxShow = config.getInt("server-status.embed.max-displayed-players", 50);
            Collection<? extends Player> online = Bukkit.getOnlinePlayers();
            int count = 0;
            for (Player p : online) {
                if (count >= maxShow) break;
                playerNames.add(skinResolver.getFormattedName(p));
                count++;
            }
        }

        // Generate JSON asynchronously and dispatch HTTP request
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String payload = buildEmbedJson(true, onlineCount, maxPlayers, tps, uptime, playerNames);

                if (messageId == null || messageId.isEmpty()) {
                    // Send initial message and capture ID
                    webhookClient.sendWaitAsync(webhookUrl, payload).thenAccept(responseBody -> {
                        if (responseBody != null) {
                            Matcher m = ID_PATTERN.matcher(responseBody);
                            if (m.find()) {
                                this.messageId = m.group(1);
                                config.set("server-status.message-id", this.messageId);
                                module.saveModuleConfig();
                                plugin.getLogger().info("[Discord] Created status embed message ID: " + this.messageId);
                            }
                        }
                        isUpdating.set(false);
                    });
                } else {
                    // Patch existing message
                    webhookClient.patchAsync(webhookUrl, messageId, payload).thenAccept(success -> {
                        if (!success) {
                            // Message was deleted or invalid; clear so next tick creates a new one
                            this.messageId = "";
                        }
                        isUpdating.set(false);
                    });
                }
            } catch (Exception e) {
                isUpdating.set(false);
            }
        });
    }

    /**
     * Dispatched synchronously on server shutdown to mark the embed as Offline.
     */
    public void shutdown() {
        if (recurringTask != null) recurringTask.cancel();
        if (debounceTask != null) debounceTask.cancel();

        String webhookUrl = getStatusWebhookUrl();
        if (webhookUrl.isEmpty() || messageId == null || messageId.isEmpty()) return;

        try {
            int maxPlayers = Bukkit.getMaxPlayers();
            String offlinePayload = buildEmbedJson(false, 0, maxPlayers, 0.0, "", List.of());
            webhookClient.patchSync(webhookUrl, messageId, offlinePayload);
        } catch (Exception ignored) {}
    }

    private String buildEmbedJson(boolean online, int currentPlayers, int maxPlayers, double tps, String uptime, List<String> playerNames) {
        String title = DiscordSanitizer.escapeJson(config.getString("server-status.embed.title", "🎮 Server Status & Player List"));
        int color = online ? config.getInt("server-status.embed.online-color", 3066993)
                           : config.getInt("server-status.embed.offline-color", 15158332);

        String statusText = online ? "🟢 **Online**" : "🔴 **Offline**";
        StringBuilder desc = new StringBuilder();
        desc.append("**Status:** ").append(statusText).append("\\n");
        desc.append("**Players:** `").append(currentPlayers).append(" / ").append(maxPlayers).append("`");

        if (online) {
            if (config.getBoolean("server-status.embed.show-tps", true)) {
                desc.append("  •  **TPS:** `").append(String.format("%.1f", Math.min(20.0, tps))).append("`");
            }
            if (config.getBoolean("server-status.embed.show-uptime", true) && !uptime.isEmpty()) {
                desc.append("\\n**Uptime:** `").append(uptime).append("`");
            }
        } else {
            desc.append("\\n*Server is currently offline or restarting.*");
        }

        StringBuilder fields = new StringBuilder();
        if (online && config.getBoolean("server-status.embed.show-playerlist", true)) {
            fields.append("[{");
            fields.append("\"name\": \"👥 Online Players (").append(currentPlayers).append(")\",");
            StringBuilder roster = new StringBuilder();
            if (playerNames.isEmpty()) {
                roster.append("*No players currently online.*");
            } else {
                for (int i = 0; i < playerNames.size(); i++) {
                    roster.append("• ").append(DiscordSanitizer.escapeJson(playerNames.get(i)));
                    if (i < playerNames.size() - 1) roster.append("\\n");
                }
                if (currentPlayers > playerNames.size()) {
                    roster.append("\\n*...and ").append(currentPlayers - playerNames.size()).append(" more*");
                }
            }
            fields.append("\"value\": \"").append(roster).append("\",");
            fields.append("\"inline\": false");
            fields.append("}]");
        } else {
            fields.append("[]");
        }

        String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        String thumbnailUrl = config.getString("server-status.embed.thumbnail-url", "");

        StringBuilder json = new StringBuilder();
        json.append("{\"embeds\": [{");
        json.append("\"title\": \"").append(title).append("\",");
        json.append("\"color\": ").append(color).append(",");
        json.append("\"description\": \"").append(desc).append("\",");
        json.append("\"fields\": ").append(fields).append(",");
        if (!thumbnailUrl.isEmpty()) {
            json.append("\"thumbnail\": {\"url\": \"").append(DiscordSanitizer.escapeJson(thumbnailUrl)).append("\"},");
        }
        json.append("\"footer\": {\"text\": \"CoreSuite Discord Bridge • ").append(online ? "Live Status" : "Offline").append("\"},");
        json.append("\"timestamp\": \"").append(timestamp).append("\"");
        json.append("}]}");

        return json.toString();
    }

    private String getStatusWebhookUrl() {
        String url = config.getString("webhooks.status-url", "").trim();
        if (url.isEmpty()) {
            url = config.getString("webhooks.chat-url", "").trim();
        }
        return url;
    }

    private double getRecentTps() {
        try {
            double[] tps = Bukkit.getTPS();
            if (tps != null && tps.length > 0) return tps[0];
        } catch (Throwable ignored) {}
        return 20.0;
    }

    private String getFormattedUptime() {
        long uptimeMs = ManagementFactory.getRuntimeMXBean().getUptime();
        long seconds = uptimeMs / 1000;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }
}
