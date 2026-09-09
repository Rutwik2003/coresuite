package net.rocky.coresuite.module.discord;

import com.google.gson.JsonObject;
import net.rocky.coresuite.CoreSuitePlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Asynchronous, non-blocking HTTP client for Discord.
 * Supports both Discord Webhooks AND direct Discord Bot REST API (Hybrid mode).
 * Never blocks the Minecraft server tick loop.
 */
public final class DiscordWebhookClient {

    private final CoreSuitePlugin plugin;
    private final HttpClient httpClient;

    public DiscordWebhookClient(CoreSuitePlugin plugin) {
        this.plugin = plugin;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Determines whether the destination is a full Webhook URL or a Discord Channel ID.
     */
    public boolean isWebhookUrl(String target) {
        return target != null && (target.startsWith("http://") || target.startsWith("https://"));
    }

    /**
     * Sends a message to a Webhook URL OR directly to a Discord channel via Bot REST API.
     */
    public CompletableFuture<Void> sendHybridAsync(String target, String botToken, String jsonPayload) {
        if (target == null || target.trim().isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        if (isWebhookUrl(target)) {
            return sendWebhookAsync(target, jsonPayload);
        } else {
            // Target is a Discord Channel ID; use Bot REST API
            String channelId = LightweightDiscordGateway.cleanChannelId(target);
            String token = LightweightDiscordGateway.cleanToken(botToken);
            if (channelId.isEmpty() || token.isEmpty()) return CompletableFuture.completedFuture(null);

            String url = "https://discord.com/api/v10/channels/" + channelId + "/messages";
            return sendBotRestAsync(url, token, "POST", jsonPayload).thenAccept(res -> {});
        }
    }

    /**
     * Sends a message and waits for the response body (containing message ID).
     * Works with both Webhook (?wait=true) and Bot REST API.
     */
    public CompletableFuture<String> sendWaitHybridAsync(String target, String botToken, String jsonPayload) {
        if (target == null || target.trim().isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        if (isWebhookUrl(target)) {
            return sendWebhookWaitAsync(target, jsonPayload);
        } else {
            String channelId = LightweightDiscordGateway.cleanChannelId(target);
            String token = LightweightDiscordGateway.cleanToken(botToken);
            if (channelId.isEmpty() || token.isEmpty()) return CompletableFuture.completedFuture(null);

            String url = "https://discord.com/api/v10/channels/" + channelId + "/messages";
            return sendBotRestAsync(url, token, "POST", jsonPayload);
        }
    }

    /**
     * Edits an existing message (PATCH) via Webhook OR Bot REST API.
     */
    public CompletableFuture<Boolean> patchHybridAsync(String target, String messageId, String botToken, String jsonPayload) {
        if (target == null || target.trim().isEmpty() || messageId == null || messageId.trim().isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        if (isWebhookUrl(target)) {
            return patchWebhookAsync(target, messageId, jsonPayload);
        } else {
            String channelId = LightweightDiscordGateway.cleanChannelId(target);
            String token = LightweightDiscordGateway.cleanToken(botToken);
            if (channelId.isEmpty() || token.isEmpty()) return CompletableFuture.completedFuture(false);

            String url = "https://discord.com/api/v10/channels/" + channelId + "/messages/" + messageId;
            return sendBotRestAsync(url, token, "PATCH", jsonPayload).thenApply(body -> body != null);
        }
    }

    /**
     * Synchronous PATCH used during plugin shutdown onDisable().
     */
    public void patchHybridSync(String target, String messageId, String botToken, String jsonPayload) {
        if (target == null || target.trim().isEmpty() || messageId == null || messageId.trim().isEmpty()) {
            return;
        }

        try {
            HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .timeout(Duration.ofSeconds(4))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("User-Agent", "CoreSuite-DiscordBridge/1.0 (Minecraft Paper)");

            if (isWebhookUrl(target)) {
                String baseUrl = target.split("\\?")[0];
                if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
                reqBuilder.uri(URI.create(baseUrl + "/messages/" + messageId))
                          .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonPayload));
            } else {
                String channelId = LightweightDiscordGateway.cleanChannelId(target);
                String token = LightweightDiscordGateway.cleanToken(botToken);
                if (channelId.isEmpty() || token.isEmpty()) return;

                reqBuilder.uri(URI.create("https://discord.com/api/v10/channels/" + channelId + "/messages/" + messageId))
                          .header("Authorization", "Bot " + token)
                          .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonPayload));
            }

            httpClient.send(reqBuilder.build(), HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {}
    }

    /**
     * Updates the Discord channel topic asynchronously.
     */
    public CompletableFuture<Void> updateChannelTopicAsync(String channelId, String botToken, String topic) {
        String cleanChannel = LightweightDiscordGateway.cleanChannelId(channelId);
        String token = LightweightDiscordGateway.cleanToken(botToken);
        if (cleanChannel.isEmpty() || token.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }

        JsonObject body = new JsonObject();
        body.addProperty("topic", topic);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://discord.com/api/v10/channels/" + cleanChannel))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bot " + token)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("User-Agent", "CoreSuite-DiscordBridge/1.0 (Minecraft Paper)")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 403) {
                            plugin.getLogger().warning("[Discord] Cannot update channel topic (403 Forbidden). Ensure the bot has 'Manage Channels' permission in Discord.");
                        } else if (response.statusCode() == 429) {
                            plugin.getLogger().warning("[Discord] Channel topic update rate-limited (429).");
                        } else if (response.statusCode() >= 400) {
                            plugin.getLogger().warning("[Discord] Channel topic update failed (HTTP " + response.statusCode() + "): " + response.body());
                        }
                    })
                    .exceptionally(ex -> {
                        plugin.getLogger().log(Level.FINE, "[Discord] Topic update network exception", ex);
                        return null;
                    });
        } catch (Exception e) {
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Updates the Discord channel topic synchronously during plugin shutdown.
     */
    public void updateChannelTopicSync(String channelId, String botToken, String topic) {
        String cleanChannel = LightweightDiscordGateway.cleanChannelId(channelId);
        String token = LightweightDiscordGateway.cleanToken(botToken);
        if (cleanChannel.isEmpty() || token.isEmpty()) return;

        JsonObject body = new JsonObject();
        body.addProperty("topic", topic);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://discord.com/api/v10/channels/" + cleanChannel))
                    .timeout(Duration.ofSeconds(3))
                    .header("Authorization", "Bot " + token)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("User-Agent", "CoreSuite-DiscordBridge/1.0 (Minecraft Paper)")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(body.toString()))
                    .build();

            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {}
    }

    // ── Internal Webhook Methods ──

    private CompletableFuture<Void> sendWebhookAsync(String webhookUrl, String jsonPayload) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("User-Agent", "CoreSuite-DiscordBridge/1.0 (Minecraft Paper)")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 429) {
                            plugin.getLogger().warning("[Discord] Webhook rate-limited (HTTP 429).");
                        } else if (response.statusCode() >= 400) {
                            plugin.getLogger().warning("[Discord] Webhook returned status code: " + response.statusCode() + " | Body: " + response.body());
                        }
                    })
                    .exceptionally(ex -> {
                        plugin.getLogger().log(Level.WARNING, "[Discord] Webhook dispatch error: " + ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private CompletableFuture<String> sendWebhookWaitAsync(String webhookUrl, String jsonPayload) {
        String targetUrl = webhookUrl.contains("?") ? webhookUrl + "&wait=true" : webhookUrl + "?wait=true";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(targetUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("User-Agent", "CoreSuite-DiscordBridge/1.0 (Minecraft Paper)")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            return response.body();
                        }
                        plugin.getLogger().warning("[Discord Webhook Wait] HTTP " + response.statusCode() + ": " + response.body());
                        return null;
                    })
                    .exceptionally(ex -> null);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(null);
        }
    }

    private CompletableFuture<Boolean> patchWebhookAsync(String webhookUrl, String messageId, String jsonPayload) {
        String baseUrl = webhookUrl.split("\\?")[0];
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        String patchUrl = baseUrl + "/messages/" + messageId;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(patchUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("User-Agent", "CoreSuite-DiscordBridge/1.0 (Minecraft Paper)")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> response.statusCode() >= 200 && response.statusCode() < 300)
                    .exceptionally(ex -> false);
        } catch (Exception e) {
            return CompletableFuture.completedFuture(false);
        }
    }

    // ── Internal Bot REST API Methods ──

    private CompletableFuture<String> sendBotRestAsync(String url, String botToken, String method, String jsonPayload) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bot " + botToken)
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("User-Agent", "CoreSuite-DiscordBridge/1.0 (Minecraft Paper)")
                    .method(method, HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(response -> {
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            return response.body();
                        } else {
                            plugin.getLogger().warning("[Discord Bot REST] HTTP " + response.statusCode() + ": " + response.body());
                            return null;
                        }
                    })
                    .exceptionally(ex -> {
                        plugin.getLogger().log(Level.WARNING, "[Discord Bot REST] Error: " + ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            return CompletableFuture.completedFuture(null);
        }
    }

    // Convenience delegates for backwards compatibility
    public CompletableFuture<Void> sendAsync(String webhookUrl, String jsonPayload) {
        return sendWebhookAsync(webhookUrl, jsonPayload);
    }
}
