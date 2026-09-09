package net.rocky.coresuite.module.discord;

import net.rocky.coresuite.CoreSuitePlugin;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

/**
 * Asynchronous, non-blocking HTTP client for Discord Webhooks using Java 21 native HttpClient.
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
     * Sends a fire-and-forget webhook message asynchronously.
     */
    public CompletableFuture<Void> sendAsync(String webhookUrl, String jsonPayload) {
        if (webhookUrl == null || webhookUrl.trim().isEmpty() || !webhookUrl.startsWith("http")) {
            return CompletableFuture.completedFuture(null);
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(webhookUrl))
                    .timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("User-Agent", "CoreSuite-DiscordBridge/1.0 (Minecraft Paper)")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            return httpClient.sendAsync(request, HttpResponse.BodyHandlers.discarding())
                    .thenAccept(response -> {
                        if (response.statusCode() == 429) {
                            plugin.getLogger().warning("[Discord] Webhook rate-limited (HTTP 429). Throttling output.");
                        } else if (response.statusCode() >= 400) {
                            plugin.getLogger().warning("[Discord] Webhook returned status code: " + response.statusCode());
                        }
                    })
                    .exceptionally(ex -> {
                        plugin.getLogger().log(Level.WARNING, "[Discord] Failed to dispatch webhook message: " + ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Discord] Error creating webhook request: " + e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Sends a webhook message with ?wait=true and returns the response body containing the message ID.
     */
    public CompletableFuture<String> sendWaitAsync(String webhookUrl, String jsonPayload) {
        if (webhookUrl == null || webhookUrl.trim().isEmpty() || !webhookUrl.startsWith("http")) {
            return CompletableFuture.completedFuture(null);
        }

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
                        } else {
                            plugin.getLogger().warning("[Discord] Wait webhook returned HTTP " + response.statusCode() + ": " + response.body());
                            return null;
                        }
                    })
                    .exceptionally(ex -> {
                        plugin.getLogger().log(Level.WARNING, "[Discord] Failed to send wait webhook: " + ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Discord] Error building wait webhook: " + e.getMessage());
            return CompletableFuture.completedFuture(null);
        }
    }

    /**
     * Edits an existing Discord webhook message using HTTP PATCH.
     */
    public CompletableFuture<Boolean> patchAsync(String webhookUrl, String messageId, String jsonPayload) {
        if (webhookUrl == null || webhookUrl.trim().isEmpty() || messageId == null || messageId.trim().isEmpty()) {
            return CompletableFuture.completedFuture(false);
        }

        // Clean any query params from base webhook URL
        String baseUrl = webhookUrl.split("\\?")[0];
        if (baseUrl.endsWith("/")) {
            baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        }
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
                    .thenApply(response -> {
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            return true;
                        } else if (response.statusCode() == 404) {
                            plugin.getLogger().warning("[Discord] Status message ID " + messageId + " was not found (deleted?). Re-posting on next tick.");
                            return false;
                        } else {
                            plugin.getLogger().warning("[Discord] PATCH webhook returned HTTP " + response.statusCode() + ": " + response.body());
                            return false;
                        }
                    })
                    .exceptionally(ex -> {
                        plugin.getLogger().log(Level.WARNING, "[Discord] Failed to patch webhook message: " + ex.getMessage());
                        return false;
                    });
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[Discord] Error creating PATCH request: " + e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Blocking PATCH used during plugin shutdown onDisable() to ensure the offline status is sent.
     */
    public void patchSync(String webhookUrl, String messageId, String jsonPayload) {
        if (webhookUrl == null || webhookUrl.trim().isEmpty() || messageId == null || messageId.trim().isEmpty()) {
            return;
        }

        String baseUrl = webhookUrl.split("\\?")[0];
        if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
        String patchUrl = baseUrl + "/messages/" + messageId;

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(patchUrl))
                    .timeout(Duration.ofSeconds(4))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("User-Agent", "CoreSuite-DiscordBridge/1.0 (Minecraft Paper)")
                    .method("PATCH", HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            httpClient.send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception ignored) {}
    }
}
