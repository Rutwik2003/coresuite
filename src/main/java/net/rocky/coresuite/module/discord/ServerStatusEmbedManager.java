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
 * Periodically and reactively updates via Webhook or Bot REST in-place message patching (PATCH).
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

    public void start() {
        if (!config.getBoolean("server-status.enabled", true)) return;

        int interval = Math.max(15, config.getInt("server-status.update-interval-seconds", 60));

        // Initial update after 3 seconds to let server initialize
        Bukkit.getScheduler().runTaskLater(plugin, this::updateStatus, 60L);

        // Periodic recurring task
        recurringTask = Bukkit.getScheduler().runTaskTimer(plugin, this::updateStatus, interval * 20L, interval * 20L);
    }

    public void scheduleDebouncedUpdate() {
        if (!config.getBoolean("server-status.enabled", true)) return;

        if (debounceTask != null && !debounceTask.isCancelled()) {
            debounceTask.cancel();
        }

        debounceTask = Bukkit.getScheduler().runTaskLater(plugin, this::updateStatus, 60L); // 3-second debounce
    }

    public void updateStatus() {
        if (!config.getBoolean("server-status.enabled", true)) return;
        String destination = getStatusDestination();
        if (destination.isEmpty()) return;

        if (!isUpdating.compareAndSet(false, true)) {
            return;
        }

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

        String botToken = config.getString("discord-to-minecraft.bot-token", "");

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String payload = buildEmbedJson(true, onlineCount, maxPlayers, tps, uptime, playerNames);

                if (messageId == null || messageId.isEmpty()) {
                    webhookClient.sendWaitHybridAsync(destination, botToken, payload).thenAccept(responseBody -> {
                        if (responseBody != null) {
                            Matcher m = ID_PATTERN.matcher(responseBody);
                            if (m.find()) {
                                this.messageId = m.group(1);
                                String idToSave = this.messageId;
                                Bukkit.getScheduler().runTask(plugin, () -> {
                                    config.set("server-status.message-id", idToSave);
                                    module.saveModuleConfig();
                                    plugin.getLogger().info("[Discord] Created status embed message ID: " + idToSave);
                                });
                            }
                        }
                        isUpdating.set(false);
                    });
                } else {
                    webhookClient.patchHybridAsync(destination, messageId, botToken, payload).thenAccept(success -> {
                        if (!success) {
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

    public void shutdown() {
        if (recurringTask != null) recurringTask.cancel();
        if (debounceTask != null) debounceTask.cancel();

        String destination = getStatusDestination();
        if (destination.isEmpty() || messageId == null || messageId.isEmpty()) return;

        String botToken = config.getString("discord-to-minecraft.bot-token", "");

        try {
            int maxPlayers = Bukkit.getMaxPlayers();
            String offlinePayload = buildEmbedJson(false, 0, maxPlayers, 0.0, "", List.of());
            webhookClient.patchHybridSync(destination, messageId, botToken, offlinePayload);
        } catch (Exception ignored) {}
    }

    private String buildEmbedJson(boolean online, int currentPlayers, int maxPlayers, double tps, String uptime, List<String> playerNames) {
        String title = DiscordSanitizer.escapeJson(config.getString("server-status.embed.title", "🎮 MeerMC Network • Live Status"));
        int color = online ? config.getInt("server-status.embed.online-color", 3066993)
                           : config.getInt("server-status.embed.offline-color", 15158332);

        String javaIp = config.getString("server-status.embed.java-ip", "meermc.rutwikdev.com:25569");
        String bedrockIp = config.getString("server-status.embed.bedrock-ip", "meermc.rutwikdev.com:25569");

        // Calculate player fill percentage
        int pct = maxPlayers > 0 ? (int) Math.round(((double) currentPlayers / maxPlayers) * 100) : 0;

        StringBuilder fields = new StringBuilder();
        fields.append("[");

        // Field 1: State
        fields.append("{")
              .append("\"name\": \"📡 Status\",")
              .append("\"value\": \"").append(online ? "🟢 **Online**" : "🔴 **Offline**").append("\",")
              .append("\"inline\": true")
              .append("},");

        // Field 2: Player count
        fields.append("{")
              .append("\"name\": \"👥 Players\",")
              .append("\"value\": \"`").append(currentPlayers).append(" / ").append(maxPlayers).append("` (").append(pct).append("%)\",")
              .append("\"inline\": true")
              .append("},");

        if (online) {
            // Field 3: TPS
            if (config.getBoolean("server-status.embed.show-tps", true)) {
                String tpsColor = tps >= 19.0 ? "🟢" : (tps >= 15.0 ? "🟡" : "🔴");
                fields.append("{")
                      .append("\"name\": \"⚡ Performance\",")
                      .append("\"value\": \"").append(tpsColor).append(" `").append(String.format("%.1f", Math.min(20.0, tps))).append(" TPS`\",")
                      .append("\"inline\": true")
                      .append("},");
            }

            // Field 4: Uptime
            if (config.getBoolean("server-status.embed.show-uptime", true) && !uptime.isEmpty()) {
                fields.append("{")
                      .append("\"name\": \"⏱ Uptime\",")
                      .append("\"value\": \"`").append(uptime).append("`\",")
                      .append("\"inline\": true")
                      .append("},");
            }

            // Field 5: Connection info
            if (!javaIp.isEmpty()) {
                fields.append("{")
                      .append("\"name\": \"🖥 Java IP\",")
                      .append("\"value\": \"`").append(DiscordSanitizer.escapeJson(javaIp)).append("`\",")
                      .append("\"inline\": true")
                      .append("},");
            }
            if (!bedrockIp.isEmpty()) {
                fields.append("{")
                      .append("\"name\": \"📱 Bedrock IP\",")
                      .append("\"value\": \"`").append(DiscordSanitizer.escapeJson(bedrockIp)).append("`\",")
                      .append("\"inline\": true")
                      .append("},");
            }

            // Field 6: Online Roster (Full row)
            if (config.getBoolean("server-status.embed.show-playerlist", true)) {
                fields.append("{")
                      .append("\"name\": \"🎮 Online Players (").append(currentPlayers).append(")\",");
                StringBuilder roster = new StringBuilder();
                if (playerNames.isEmpty()) {
                    roster.append("*No players currently online. Join now to start playing!*");
                } else {
                    for (int i = 0; i < playerNames.size(); i++) {
                        roster.append("• **").append(DiscordSanitizer.escapeJson(playerNames.get(i))).append("**");
                        if (i < playerNames.size() - 1) roster.append("\\n");
                    }
                    if (currentPlayers > playerNames.size()) {
                        roster.append("\\n*...and ").append(currentPlayers - playerNames.size()).append(" more*");
                    }
                }
                fields.append("\"value\": \"").append(roster).append("\",")
                      .append("\"inline\": false")
                      .append("}");
            } else {
                // Remove trailing comma if present
                if (fields.charAt(fields.length() - 1) == ',') {
                    fields.deleteCharAt(fields.length() - 1);
                }
            }
        } else {
            // Offline announcement
            fields.append("{")
                  .append("\"name\": \"📢 Notice\",")
                  .append("\"value\": \"*The server is currently offline or restarting. We will be back online shortly!*\",")
                  .append("\"inline\": false")
                  .append("}");
        }

        fields.append("]");

        String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        String thumbnailUrl = config.getString("server-status.embed.thumbnail-url", "");

        StringBuilder json = new StringBuilder();
        json.append("{\"embeds\": [{");
        json.append("\"title\": \"").append(title).append("\",");
        json.append("\"color\": ").append(color).append(",");
        json.append("\"fields\": ").append(fields).append(",");
        if (!thumbnailUrl.isEmpty()) {
            json.append("\"thumbnail\": {\"url\": \"").append(DiscordSanitizer.escapeJson(thumbnailUrl)).append("\"},");
        }
        json.append("\"footer\": {\"text\": \"CoreSuite Discord Bridge • Auto-updates live\"},");
        json.append("\"timestamp\": \"").append(timestamp).append("\"");
        json.append("}]}");

        return json.toString();
    }

    private String getStatusDestination() {
        String url = config.getString("webhooks.status-url", "").trim();
        if (!url.isEmpty()) return url;

        String chatUrl = config.getString("webhooks.chat-url", "").trim();
        if (!chatUrl.isEmpty()) return chatUrl;

        // Fallback to bot channel ID if using Bot mode
        String statusChannel = config.getString("server-status.channel-id", "").trim();
        if (!statusChannel.isEmpty()) return statusChannel;

        return config.getString("discord-to-minecraft.channel-id", "").trim();
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
