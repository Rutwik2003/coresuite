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
 * by running on a safe configurable interval (default: 5 minutes / 300s).
 */
public final class DiscordTopicManager {

    private final CoreSuitePlugin plugin;
    private final FileConfiguration config;
    private final DiscordWebhookClient webhookClient;
    private final Instant startTime;

    private ScheduledExecutorService executor;
    private ScheduledFuture<?> updateTask;

    public DiscordTopicManager(CoreSuitePlugin plugin, FileConfiguration config,
                               DiscordWebhookClient webhookClient, Instant startTime) {
        this.plugin = plugin;
        this.config = config;
        this.webhookClient = webhookClient;
        this.startTime = startTime;
    }

    public synchronized void start() {
        if (!config.getBoolean("channel-topic.enabled", true)) return;

        String channelId = config.getString("discord-to-minecraft.channel-id", "").trim();
        String botToken = config.getString("discord-to-minecraft.bot-token", "").trim();

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

        // Delay first update slightly (10 seconds) so server fully initializes
        updateTask = executor.scheduleAtFixedRate(this::updateTopic, 10, (long) intervalMinutes * 60, TimeUnit.SECONDS);
    }

    public void updateTopic() {
        if (!config.getBoolean("channel-topic.enabled", true)) return;

        String channelId = config.getString("discord-to-minecraft.channel-id", "").trim();
        String botToken = config.getString("discord-to-minecraft.bot-token", "").trim();
        if (channelId.isEmpty() || botToken.isEmpty()) return;

        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        double tps = Math.min(20.0, Bukkit.getTPS()[0]);
        String tpsFormatted = String.format("%.1f", tps);
        String ip = config.getString("server-status.embed.java-ip", "meermc.rutwikdev.com:25569");
        String uptime = formatUptime();

        String format = config.getString("channel-topic.format-online", "🟢 Online: {online}/{max} | TPS: {tps} | IP: {ip}");
        String topic = format.replace("{online}", String.valueOf(online))
                .replace("{max}", String.valueOf(max))
                .replace("{tps}", tpsFormatted)
                .replace("{uptime}", uptime)
                .replace("{ip}", ip);

        webhookClient.updateChannelTopicAsync(channelId, botToken, topic);
    }

    public synchronized void shutdown() {
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel(false);
        }
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }

        if (config.getBoolean("channel-topic.enabled", true)) {
            String channelId = config.getString("discord-to-minecraft.channel-id", "").trim();
            String botToken = config.getString("discord-to-minecraft.bot-token", "").trim();
            if (!channelId.isEmpty() && !botToken.isEmpty()) {
                String ip = config.getString("server-status.embed.java-ip", "meermc.rutwikdev.com:25569");
                String format = config.getString("channel-topic.format-offline", "🔴 Server Offline | IP: {ip}");
                String offlineTopic = format.replace("{ip}", ip);
                webhookClient.updateChannelTopicSync(channelId, botToken, offlineTopic);
            }
        }
    }

    private String formatUptime() {
        Duration d = Duration.between(startTime, Instant.now());
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        return hours > 0 ? hours + "h " + minutes + "m" : minutes + "m";
    }
}
