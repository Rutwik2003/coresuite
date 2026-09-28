package net.rocky.coresuite.module.discord;

import net.rocky.coresuite.CoreSuitePlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Manages periodic updates of the Discord channel topic.
 * Strictly respects Discord's channel edit rate limit (2 edits per 10 minutes)
 * by running on a safe configurable interval (default: 5 minutes / 300s)
 * and avoiding false offline updates during plugin reloads.
 */
public final class DiscordTopicManager {

    private final CoreSuitePlugin plugin;
    private final FileConfiguration config;
    private final DiscordWebhookClient webhookClient;
    private final Instant startTime;

    private ScheduledExecutorService executor;
    private ScheduledFuture<?> updateTask;
    private ScheduledFuture<?> retryTask;
    private volatile String lastUpdatedTopic = null;

    public DiscordTopicManager(CoreSuitePlugin plugin, FileConfiguration config,
                               DiscordWebhookClient webhookClient, Instant startTime) {
        this.plugin = plugin;
        this.config = config;
        this.webhookClient = webhookClient;
        this.startTime = startTime;
    }

    public synchronized void start() {
        if (!config.getBoolean("channel-topic.enabled", true)) return;

        String channelId = getTopicChannelId();
        String botToken = getBotToken();

        if (channelId.isEmpty() || botToken.isEmpty()) {
            return;
        }

        // Rate limit safe: minimum 5 minutes (300 seconds)
        int intervalMinutes = Math.max(5, config.getInt("channel-topic.update-interval-minutes", 5));

        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "CoreSuite-Discord-TopicManager");
            t.setDaemon(true);
            return t;
        });

        // Run initial update 3 seconds after server startup, then periodically
        updateTask = executor.scheduleAtFixedRate(this::updateTopic, 3, (long) intervalMinutes * 60, TimeUnit.SECONDS);
    }

    public void updateTopic() {
        if (!config.getBoolean("channel-topic.enabled", true)) return;

        String channelId = getTopicChannelId();
        String botToken = getBotToken();
        if (channelId.isEmpty() || botToken.isEmpty()) return;

        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        double tps = Math.min(20.0, Bukkit.getTPS()[0]);
        String tpsFormatted = String.format("%.1f", tps);
        String ip = config.getString("server-status.embed.java-ip", "meermc.rutwikdev.com:25569");
        String uptime = formatUptime();

        String format = config.getString("channel-topic.format-online", "🟢 Online: {online}/{max} | TPS: {tps} | IP: {ip}");
        var maintenance = net.rocky.coresuite.module.maintenance.MaintenanceModule.state(plugin);
        if (maintenance != net.rocky.coresuite.module.maintenance.MaintenanceModule.State.OPEN)
            format = net.rocky.coresuite.module.maintenance.MaintenanceModule.label(maintenance) + " | Players: {online}/{max} | IP: {ip}";
        String topic = format.replace("{online}", String.valueOf(online))
                .replace("{max}", String.valueOf(max))
                .replace("{tps}", tpsFormatted)
                .replace("{uptime}", uptime)
                .replace("{ip}", ip);

        // Only update if the topic has actually changed (prevents spamming Discord API and logs when server is empty or static)
        boolean onlyOnChange = config.getBoolean("channel-topic.update-only-on-change", true);
        if (onlyOnChange && topic.equals(lastUpdatedTopic)) {
            return;
        }

        webhookClient.updateChannelTopicAsync(channelId, botToken, topic)
                .thenAccept(result -> {
                    if (result != null && result.statusCode() >= 200 && result.statusCode() < 300) {
                        lastUpdatedTopic = topic;
                        if (config.getBoolean("channel-topic.log-updates", false)) {
                            plugin.getLogger().info("[Discord Topic] Successfully updated channel topic: " + topic);
                        }
                    } else if (result != null && result.isRateLimited() && result.retryAfterSeconds() > 0) {
                        scheduleRetry((long) Math.ceil(result.retryAfterSeconds()) + 1);
                    }
                });
    }

    private synchronized void scheduleRetry(long delaySeconds) {
        if (executor == null || executor.isShutdown()) return;
        if (retryTask != null && !retryTask.isDone()) {
            retryTask.cancel(false);
        }
        plugin.getLogger().info("[Discord Topic] Scheduling automatic retry in " + delaySeconds + " seconds.");
        retryTask = executor.schedule(this::updateTopic, delaySeconds, TimeUnit.SECONDS);
    }

    public synchronized void shutdown(boolean isReloading) {
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel(false);
        }
        if (retryTask != null && !retryTask.isCancelled()) {
            retryTask.cancel(false);
        }
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }

        // Never set offline topic during /discord reload or plugin reloads!
        // Discord allows only 2 edits per 10 mins. Setting offline on reload burns an edit.
        if (!isReloading && config.getBoolean("channel-topic.enabled", true)) {
            String channelId = getTopicChannelId();
            String botToken = getBotToken();
            if (!channelId.isEmpty() && !botToken.isEmpty()) {
                String ip = config.getString("server-status.embed.java-ip", "meermc.rutwikdev.com:25569");
                String format = config.getString("channel-topic.format-offline", "🔴 Server Offline | IP: {ip}");
                String offlineTopic = format.replace("{ip}", ip);
                webhookClient.updateChannelTopicSync(channelId, botToken, offlineTopic);
            }
        }
    }

    public String getTopicChannelId() {
        String channelId = config.getString("channel-topic.channel-id", "").trim();
        if (channelId.isEmpty()) {
            channelId = config.getString("discord-to-minecraft.channel-id", "").trim();
        }
        if (channelId.isEmpty()) {
            channelId = config.getString("server-status.channel-id", "").trim();
        }
        return channelId;
    }

    public String getBotToken() {
        return config.getString("discord-to-minecraft.bot-token", "").trim();
    }

    private String formatUptime() {
        Duration d = Duration.between(startTime, Instant.now());
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        return hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
    }
}
