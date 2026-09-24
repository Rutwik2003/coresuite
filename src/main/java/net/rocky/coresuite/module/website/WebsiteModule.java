package net.rocky.coresuite.module.website;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.Module;
import net.rocky.coresuite.module.website.commands.LinkCommand;
import net.rocky.coresuite.module.website.commands.RankCommand;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;

public class WebsiteModule extends Module implements Listener, CommandExecutor, TabCompleter {

    public static class PendingLink {
        private final UUID uuid;
        private final String username;
        private final long expiresAt;

        public PendingLink(UUID uuid, String username, long expiresAt) {
            this.uuid = uuid;
            this.username = username;
            this.expiresAt = expiresAt;
        }

        public UUID getUuid() { return uuid; }
        public String getUsername() { return username; }
        public boolean isExpired() { return System.currentTimeMillis() > expiresAt; }
    }

    private HttpServer server;
    private ExecutorService httpExecutor;
    private final SecureRandom secureRandom = new SecureRandom();

    // Verification code -> PendingLink (10 minute expiry)
    private final Map<String, PendingLink> pendingLinks = new ConcurrentHashMap<>();

    // Bi-directional link caches (thread-safe)
    private final Map<UUID, String> uuidToDiscordCache = new ConcurrentHashMap<>();
    private final Map<String, UUID> discordToUuidCache = new ConcurrentHashMap<>();

    // Thread-safe online players cache for instant /api/players responses
    private volatile int cachedMaxPlayers;
    private final Set<String> cachedOnlinePlayers = ConcurrentHashMap.newKeySet();

    public WebsiteModule(CoreSuitePlugin plugin) {
        super(plugin, "Website Integration", "website");
    }

    @Override
    public void onEnable() {
        registerListener(this);
        initDatabase();
        loadCachedLinks();

        // Populate online players cache
        cachedMaxPlayers = Bukkit.getMaxPlayers();
        cachedOnlinePlayers.clear();
        for (Player player : Bukkit.getOnlinePlayers()) {
            cachedOnlinePlayers.add(player.getName());
        }

        startServer();

        // Register commands
        PluginCommand linkCmd = plugin.getCommand("link");
        if (linkCmd != null) {
            linkCmd.setExecutor(new LinkCommand(this));
        }

        PluginCommand rankCmd = plugin.getCommand("rank");
        if (rankCmd != null) {
            rankCmd.setExecutor(new RankCommand(this));
        }

        PluginCommand websiteCmd = plugin.getCommand("website");
        if (websiteCmd != null) {
            websiteCmd.setExecutor(this);
            websiteCmd.setTabCompleter(this);
        }
    }

    @Override
    public void onDisable() {
        stopServer();

        pendingLinks.clear();
        uuidToDiscordCache.clear();
        discordToUuidCache.clear();
        cachedOnlinePlayers.clear();

        PluginCommand linkCmd = plugin.getCommand("link");
        if (linkCmd != null && linkCmd.getExecutor() instanceof LinkCommand) {
            linkCmd.setExecutor(null);
        }

        PluginCommand rankCmd = plugin.getCommand("rank");
        if (rankCmd != null && rankCmd.getExecutor() instanceof RankCommand) {
            rankCmd.setExecutor(null);
        }

        PluginCommand websiteCmd = plugin.getCommand("website");
        if (websiteCmd != null && websiteCmd.getExecutor() == this) {
            websiteCmd.setExecutor(null);
            websiteCmd.setTabCompleter(null);
        }
    }

    // ── Online Player Cache Listeners ─────────────────────────────

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        cachedOnlinePlayers.add(event.getPlayer().getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        cachedOnlinePlayers.remove(event.getPlayer().getName());
    }

    // ── HTTP Server Lifecycle ──────────────────────────────────────

    public synchronized void startServer() {
        stopServer();

        int port = getConfig().getInt("port", 25580);
        String bindIp = getConfig().getString("bind-ip", "0.0.0.0");
        String apiKey = getConfig().getString("api-key", "default_secret_key");

        if ("your_secret_api_key_here".equals(apiKey) || "default_secret_key".equals(apiKey)) {
            plugin.getLogger().warning("[Website Integration] WARNING: You are using a default/example API key in website.yml! Please change it to a strong secret key for production security.");
        }

        try {
            server = HttpServer.create(new InetSocketAddress(bindIp, port), 0);
            httpExecutor = new ThreadPoolExecutor(2, 8, 60L, TimeUnit.SECONDS,
                    new ArrayBlockingQueue<>(64), runnable -> {
                        Thread thread = new Thread(runnable, "CoreSuite-Website");
                        thread.setDaemon(true);
                        return thread;
                    }, new ThreadPoolExecutor.AbortPolicy());
            server.setExecutor(httpExecutor);

            setupContexts(apiKey);

            server.start();
            log("Started internal web server on " + bindIp + ":" + port);
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to start internal web server on " + bindIp + ":" + port +
                    ". If using shared hosting, verify that this port is allocated to your server in your hosting panel.", e);
            stopServer();
            throw new IllegalStateException("Website server failed to start", e);
        }
    }

    public synchronized void stopServer() {
        if (server != null) {
            try {
                server.stop(0);
            } catch (Exception ignored) {}
            server = null;
            log("Stopped internal web server.");
        }
        if (httpExecutor != null) {
            try {
                httpExecutor.shutdownNow();
            } catch (Exception ignored) {}
            httpExecutor = null;
        }
    }

    // ── HTTP Contexts ─────────────────────────────────────────────

    private void setupContexts(String apiKey) {
        // GET /api/config — Used by sync-env.js to synchronize site prices & connection info
        server.createContext("/api/config", exchange -> {
            if (handleOptions(exchange)) return;
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                if (!checkAuth(exchange, apiKey, "")) return;

                StringBuilder json = new StringBuilder("{");
                json.append("\"site_url\":\"").append(escapeJson(getConfig().getString("site.url", "https://meermc.com"))).append("\",");
                json.append("\"server_java_ip\":\"").append(escapeJson(getConfig().getString("server.java-ip", "play.meermc.com"))).append("\",");
                json.append("\"server_java_port\":\"").append(escapeJson(getConfig().getString("server.java-port", "25565"))).append("\",");
                json.append("\"server_bedrock_ip\":\"").append(escapeJson(getConfig().getString("server.bedrock-ip", "bedrock.meermc.com"))).append("\",");
                json.append("\"server_bedrock_port\":\"").append(escapeJson(getConfig().getString("server.bedrock-port", "19132"))).append("\",");
                json.append("\"server_version\":\"").append(escapeJson(getConfig().getString("server.version", "1.21+"))).append("\",");
                json.append("\"social_discord\":\"").append(escapeJson(getConfig().getString("social.discord", "https://discord.gg/meermc"))).append("\",");
                json.append("\"social_instagram\":\"").append(escapeJson(getConfig().getString("social.instagram", "https://instagram.com/meermc"))).append("\",");
                json.append("\"payment_upi_id\":\"").append(escapeJson(getConfig().getString("payment.upi-id", ""))).append("\",");
                json.append("\"store_rank_vip\":").append(getConfig().getInt("store.ranks.vip", 50)).append(",");
                json.append("\"store_rank_cutie\":").append(getConfig().getInt("store.ranks.cutie", 69)).append(",");
                json.append("\"store_rank_elite\":").append(getConfig().getInt("store.ranks.elite", 100)).append(",");
                json.append("\"store_rank_investor\":").append(getConfig().getInt("store.ranks.investor", 120)).append(",");
                json.append("\"store_key_rare\":").append(getConfig().getInt("store.keys.rare", 49)).append(",");
                json.append("\"store_key_elite\":").append(getConfig().getInt("store.keys.elite", 80)).append(",");
                json.append("\"store_key_insane\":").append(getConfig().getInt("store.keys.insane", 120));
                json.append("}");

                sendResponse(exchange, 200, json.toString());
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Error serving /api/config", t);
                sendResponse(exchange, 500, "{\"error\":\"Internal error\"}");
            }
        });

        // GET /api/players — Returns array of active online players
        server.createContext("/api/players", exchange -> {
            if (handleOptions(exchange)) return;
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                if (!checkAuth(exchange, apiKey, "")) return;

                StringBuilder playersJson = new StringBuilder("{\"players\":[");
                List<String> list = new ArrayList<>(cachedOnlinePlayers);
                for (int i = 0; i < list.size(); i++) {
                    playersJson.append("\"").append(escapeJson(list.get(i))).append("\"");
                    if (i < list.size() - 1) playersJson.append(",");
                }
                playersJson.append("],\"online\":true,\"maxPlayers\":").append(cachedMaxPlayers).append(",\"updatedAt\":").append(System.currentTimeMillis()).append("}");
                sendResponse(exchange, 200, playersJson.toString());
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Error serving /api/players", t);
                sendResponse(exchange, 500, "{\"error\":\"Internal error\"}");
            }
        });

        // POST /api/execute — Dispatches console commands securely with logging & donator auto-tracking
        server.createContext("/api/execute", exchange -> {
            if (handleOptions(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                String body = readRequestBody(exchange);
                if (body == null) return;
                if (!checkAuth(exchange, apiKey, body)) return;

                String[] commands = body.split("\r?\n");
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (String rawCmd : commands) {
                        String cleanCmd = rawCmd.replace("\"", "").replace("[", "").replace("]", "").replace(",", "").trim();
                        if (cleanCmd.isEmpty()) continue;

                        plugin.getLogger().info("[Website API] Executing console command: " + cleanCmd);
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cleanCmd);

                        // Auto-record rank donation if applicable
                        // Format: lp user <username> parent addtemp <tier> ...
                        if (cleanCmd.toLowerCase().startsWith("lp user ") && cleanCmd.toLowerCase().contains("parent add")) {
                            try {
                                String[] parts = cleanCmd.split("\\s+");
                                if (parts.length >= 6) {
                                    String targetUser = parts[2];
                                    String rankTier = parts[5];
                                    recordDonator(targetUser, rankTier, 0.0);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                });

                sendResponse(exchange, 200, "{\"status\":\"success\"}");
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "Error serving /api/execute", t);
                sendResponse(exchange, 500, "{\"error\":\"Internal error\"}");
            }
        });

        // POST /api/link — Verifies 4-digit code and links Discord ID with Minecraft UUID
        server.createContext("/api/link", exchange -> {
            if (handleOptions(exchange)) return;
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                String body = readRequestBody(exchange);
                if (body == null) return;
                if (!checkAuth(exchange, apiKey, body)) return;

                String[] parts = body.split(":", 2);
                if (parts.length == 2) {
                    String code = parts[0].trim();
                    String discordId = parts[1].trim();

                    PendingLink link = pendingLinks.remove(code);
                    if (link != null && !link.isExpired()) {
                        UUID uuid = link.getUuid();
                        saveLinkToDb(uuid, discordId, link.getUsername());

                        // Send in-game confirmation message & sound if player is online
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            Player player = Bukkit.getPlayer(uuid);
                            if (player != null && player.isOnline()) {
                                TextUtil.sendMessage(player, "&8[&bMeerMC&8] &a&l✔ &fYour Minecraft account has been successfully linked with Discord!");
                                try {
                                    player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
                                } catch (Exception ignored) {}
                            }
                        });

                        plugin.getLogger().info("[Website API] Successfully linked " + link.getUsername() + " (" + uuid + ") with Discord ID: " + discordId);
                        sendResponse(exchange, 200, "{\"status\":\"success\"}");
                    } else {
                        sendResponse(exchange, 400, "{\"error\":\"Invalid or expired code\"}");
                    }
                } else {
                    sendResponse(exchange, 400, "{\"error\":\"Bad format. Expected code:discordId\"}");
                }
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Error serving /api/link", t);
                sendResponse(exchange, 500, "{\"error\":\"Internal error\"}");
            }
        });

        // GET /api/donators — Returns list of donators (persisted + online ranked players)
        server.createContext("/api/donators", exchange -> {
            if (handleOptions(exchange)) return;
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                if (!checkAuth(exchange, apiKey, "")) return;

                Map<String, String> donatorsMap = new LinkedHashMap<>();

                // 1. Fetch persisted donators from SQLite
                try (Connection conn = plugin.getDatabaseManager().getConnection();
                     PreparedStatement stmt = conn.prepareStatement("SELECT username, tier FROM coresuite_website_donators ORDER BY updated_at DESC LIMIT 50");
                     ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        String name = rs.getString("username");
                        String tier = rs.getString("tier");
                        if (name != null && tier != null) {
                            donatorsMap.put(name.toLowerCase(), tier);
                        }
                    }
                } catch (Exception ignored) {}

                // 2. Add any currently online players who have rank tiers in LuckPerms
                if (Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
                    try {
                        LuckPerms lp = LuckPermsProvider.get();
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            User user = lp.getUserManager().getUser(p.getUniqueId());
                            if (user != null) {
                                String group = user.getPrimaryGroup().toLowerCase();
                                if (group.equals("vip") || group.equals("cutie") || group.equals("elite") || group.equals("investor")) {
                                    donatorsMap.put(p.getName().toLowerCase(), group);
                                }
                            }
                        }
                    } catch (Throwable ignored) {}
                }

                // Build response JSON array
                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                for (Map.Entry<String, String> entry : donatorsMap.entrySet()) {
                    if (!first) json.append(",");
                    json.append("{\"username\":\"").append(escapeJson(entry.getKey())).append("\",")
                            .append("\"tier\":\"").append(escapeJson(entry.getValue())).append("\"}");
                    first = false;
                }
                json.append("]");

                sendResponse(exchange, 200, json.toString());
            } catch (Throwable t) {
                plugin.getLogger().log(Level.WARNING, "Error serving /api/donators", t);
                sendResponse(exchange, 500, "{\"error\":\"Internal error\"}");
            }
        });

        // GET /api/leaderboard — Resolves leaderboard stats safely without crashing if PAPI is absent
        server.createContext("/api/leaderboard", exchange -> {
            if (handleOptions(exchange)) return;
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                if (!checkAuth(exchange, apiKey, "")) return;

                String query = exchange.getRequestURI().getQuery();
                String metric = "kills";
                if ("sort=netWorth".equals(query)) metric = "netWorth";
                else if ("sort=playtime".equals(query)) metric = "playtime";
                final String board = config.getString("leaderboards." + metric,
                        metric.equals("netWorth") ? "vault_eco_balance" : metric.equals("playtime") ? "statistic_time_played" : "statistic_player_kills");
                boolean hasPapi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;

                if (!hasPapi) {
                    sendResponse(exchange, 503, "{\"error\":\"Leaderboard data unavailable; check PlaceholderAPI and ajLeaderboards setup\"}");
                    return;
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        StringBuilder json = new StringBuilder("[");
                        boolean first = true;

                        for (int i = 1; i <= 10; i++) {
                            String namePlaceholder = "%ajlb_lb_" + board + "_" + i + "_alltime_name%";
                            String valuePlaceholder = "%ajlb_lb_" + board + "_" + i + "_alltime_rawvalue%";

                            String name = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(null, namePlaceholder);
                            String valueStr = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(null, valuePlaceholder);

                            if (namePlaceholder.equals(name)) throw new IllegalStateException("Unresolved leaderboard placeholder: " + board);
                            if (name == null || name.isEmpty() || name.equalsIgnoreCase("N/A")) {
                                continue;
                            }

                            OfflinePlayer op = Bukkit.getOfflinePlayerIfCached(name);
                            int kills = (int) parseWebsiteNumber(me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(op, "%statistic_player_kills%"));
                            String deathsStr = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(op, "%statistic_deaths%");
                            int deaths = 0;
                            try { deaths = (int) parseWebsiteNumber(deathsStr); } catch (Exception ignored) {}

                            String netWorthStr = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(op, "%vault_eco_balance%");
                            long netWorth = (long) parseWebsiteNumber(netWorthStr);

                            String playtimeStr = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(op, "%statistic_time_played%");
                            long playtimeSeconds = 0;
                            try {
                                playtimeSeconds = (long) parseWebsiteNumber(playtimeStr) / 20;
                            } catch (Exception ignored) {}

                            double score = parseWebsiteNumber(valueStr);
                            if (board.equals(config.getString("leaderboards.kills", "statistic_player_kills"))) kills = (int) score;
                            else if (board.equals(config.getString("leaderboards.netWorth", "vault_eco_balance"))) netWorth = (long) score;
                            else if (board.equals(config.getString("leaderboards.playtime", "statistic_time_played"))) playtimeSeconds = (long) score / 20;
                            if (!first) json.append(",");
                            json.append("{")
                                    .append("\"username\":\"").append(escapeJson(name)).append("\",")
                                    .append("\"kills\":").append(kills).append(",")
                                    .append("\"deaths\":").append(deaths).append(",")
                                    .append("\"netWorth\":").append(netWorth).append(",")
                                    .append("\"playtime\":").append(playtimeSeconds)
                                    .append("}");
                            first = false;
                        }
                        json.append("]");

                        httpExecutor.submit(() -> {
                            try {
                                sendResponse(exchange, 200, json.toString());
                            } catch (Exception ignored) {}
                        });
                    } catch (Throwable t) {
                        httpExecutor.submit(() -> {
                            try {
                                sendResponse(exchange, 503, "{\"error\":\"Leaderboard data unavailable; check PlaceholderAPI and ajLeaderboards setup\"}");
                            } catch (Exception ignored) {}
                        });
                    }
                });
            } catch (Throwable t) {
                sendResponse(exchange, 500, "{\"error\":\"Internal error\"}");
            }
        });

        // GET /api/voters — Fallback for vote leaderboard
        server.createContext("/api/voters", exchange -> {
            if (handleOptions(exchange)) return;
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
                return;
            }
            try {
                if (!checkAuth(exchange, apiKey, "")) return;

                boolean hasPapi = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
                if (!hasPapi) {
                    sendResponse(exchange, 503, "{\"error\":\"Vote data unavailable\"}");
                    return;
                }

                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        StringBuilder json = new StringBuilder("[");
                        boolean first = true;

                        for (int i = 1; i <= 3; i++) {
                            String namePlaceholder = "%votingplugin_top_month_" + i + "%";
                            String valuePlaceholder = "%votingplugin_top_monthvotes_" + i + "%";

                            String name = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(null, namePlaceholder);
                            String valueStr = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(null, valuePlaceholder);

                            if (namePlaceholder.equals(name)) throw new IllegalStateException("Unresolved vote placeholder");
                            if (name == null || name.isEmpty() || name.equalsIgnoreCase("N/A")) continue;

                            int votes = 0;
                            try { votes = Integer.parseInt(valueStr.replaceAll("[^0-9]", "")); } catch (Exception ignored) {}

                            if (!first) json.append(",");
                            json.append("{\"username\":\"").append(escapeJson(name)).append("\",\"votes\":").append(votes).append("}");
                            first = false;
                        }
                        json.append("]");

                        httpExecutor.submit(() -> {
                            try { sendResponse(exchange, 200, json.toString()); } catch (Exception ignored) {}
                        });
                    } catch (Throwable t) {
                        httpExecutor.submit(() -> {
                            try { sendResponse(exchange, 503, "{\"error\":\"Vote data unavailable\"}"); } catch (Exception ignored) {}
                        });
                    }
                });
            } catch (Throwable t) {
                sendResponse(exchange, 500, "{\"error\":\"Internal error\"}");
            }
        });
    }

    // ── Authentication & Security ─────────────────────────────────

    static String readRequestBody(HttpExchange exchange) throws IOException {
        int limit = 64 * 1024;
        byte[] body = exchange.getRequestBody().readNBytes(limit + 1);
        if (body.length > limit) {
            exchange.sendResponseHeaders(413, -1);
            exchange.close();
            return null;
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    static double parseWebsiteNumber(String value) {
        if (value == null) return 0;
        try {
            double number = Double.parseDouble(value.replace(",", "").trim());
            return Double.isFinite(number) ? Math.max(0, number) : 0;
        } catch (NumberFormatException ignored) { return 0; }
    }

    private boolean checkAuth(HttpExchange exchange, String apiKey, String body) throws IOException {
        String signature = exchange.getRequestHeaders().getFirst("X-Signature");
        String timestampStr = exchange.getRequestHeaders().getFirst("X-Timestamp");

        // Fallback for simple Authorization header
        if (signature == null || timestampStr == null) {
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            if (authHeader != null && authHeader.equals("Bearer " + apiKey)) {
                return true;
            }
            sendResponse(exchange, 401, "{\"error\":\"Unauthorized - Missing HMAC Signature\"}");
            return false;
        }

        try {
            long timestamp = Long.parseLong(timestampStr);
            long now = System.currentTimeMillis();
            if (Math.abs(now - timestamp) > 300_000L) { // 5 minutes window
                sendResponse(exchange, 401, "{\"error\":\"Request expired\"}");
                return false;
            }

            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(apiKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);

            String payload = timestampStr + (body != null ? body : "");
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }

            // Constant-time comparison to prevent timing attacks
            if (!MessageDigest.isEqual(hexString.toString().getBytes(StandardCharsets.UTF_8), signature.toLowerCase().getBytes(StandardCharsets.UTF_8))) {
                sendResponse(exchange, 401, "{\"error\":\"Invalid signature\"}");
                return false;
            }

            return true;
        } catch (Exception e) {
            sendResponse(exchange, 401, "{\"error\":\"Auth error\"}");
            return false;
        }
    }

    private boolean handleOptions(HttpExchange exchange) throws IOException {
        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Authorization, Content-Type, X-Timestamp, X-Signature");
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return true;
        }
        return false;
    }

    private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < ' ') {
                        String hex = Integer.toHexString(c);
                        sb.append("\\u").append("0".repeat(4 - hex.length())).append(hex);
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    // ── Database & Link Management ────────────────────────────────

    private void initDatabase() {
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             java.sql.Statement s = conn.createStatement()) {
            s.execute(
                    "CREATE TABLE IF NOT EXISTS coresuite_discord_links (" +
                            "uuid TEXT PRIMARY KEY, " +
                            "discord_id TEXT UNIQUE, " +
                            "username TEXT, " +
                            "linked_at BIGINT);"
            );
            s.execute(
                    "CREATE TABLE IF NOT EXISTS coresuite_website_donators (" +
                            "username TEXT PRIMARY KEY, " +
                            "tier TEXT, " +
                            "amount REAL DEFAULT 0, " +
                            "updated_at BIGINT);"
            );
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to initialize website integration database tables", e);
        }
    }

    private void loadCachedLinks() {
        uuidToDiscordCache.clear();
        discordToUuidCache.clear();
        try (Connection conn = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT uuid, discord_id FROM coresuite_discord_links");
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                try {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    String discordId = rs.getString("discord_id");
                    if (discordId != null && !discordId.isEmpty()) {
                        uuidToDiscordCache.put(uuid, discordId);
                        discordToUuidCache.put(discordId, uuid);
                    }
                } catch (Exception ignored) {}
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to load discord links cache from database", e);
        }

        // Migrate legacy data.yml if present
        File legacyFile = new File(getDataFolder(), "data.yml");
        if (legacyFile.exists()) {
            try {
                YamlConfiguration yaml = YamlConfiguration.loadConfiguration(legacyFile);
                ConfigurationSection linksSec = yaml.getConfigurationSection("links");
                if (linksSec != null) {
                    for (String discordId : linksSec.getKeys(false)) {
                        String uuidStr = linksSec.getString(discordId);
                        if (uuidStr != null) {
                            try {
                                UUID uuid = UUID.fromString(uuidStr);
                                if (!uuidToDiscordCache.containsKey(uuid)) {
                                    saveLinkToDb(uuid, discordId, Bukkit.getOfflinePlayer(uuid).getName());
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }
            } catch (Exception ignored) {}
        }
    }

    public void saveLinkToDb(UUID uuid, String discordId, String username) {
        uuidToDiscordCache.put(uuid, discordId);
        discordToUuidCache.put(discordId, uuid);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT OR REPLACE INTO coresuite_discord_links (uuid, discord_id, username, linked_at) VALUES (?, ?, ?, ?)")) {
                stmt.setString(1, uuid.toString());
                stmt.setString(2, discordId);
                stmt.setString(3, username != null ? username : "");
                stmt.setLong(4, System.currentTimeMillis());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to save discord link to database", e);
            }
        });
    }

    public void recordDonator(String username, String tier, double amount) {
        if (username == null || username.isEmpty() || tier == null || tier.isEmpty()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "INSERT OR REPLACE INTO coresuite_website_donators (username, tier, amount, updated_at) VALUES (?, ?, ?, ?)")) {
                stmt.setString(1, username);
                stmt.setString(2, tier.toLowerCase());
                stmt.setDouble(3, amount);
                stmt.setLong(4, System.currentTimeMillis());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to record donator in database", e);
            }
        });
    }

    public String createPendingLink(Player player) {
        // Purge expired codes
        pendingLinks.entrySet().removeIf(e -> e.getValue().isExpired());
        // Remove previous code for this player
        pendingLinks.values().removeIf(link -> link.getUuid().equals(player.getUniqueId()));

        String code;
        do {
            int num = 1000 + secureRandom.nextInt(9000);
            code = String.valueOf(num);
        } while (pendingLinks.containsKey(code));

        long expiresAt = System.currentTimeMillis() + 600_000L; // 10 minutes
        pendingLinks.put(code, new PendingLink(player.getUniqueId(), player.getName(), expiresAt));
        return code;
    }

    public String getLinkedDiscordId(UUID uuid) {
        return uuidToDiscordCache.get(uuid);
    }

    public UUID getLinkedUuid(String discordId) {
        return discordToUuidCache.get(discordId);
    }

    public void addPendingLink(String code, UUID uuid) {
        pendingLinks.put(code, new PendingLink(uuid, Bukkit.getOfflinePlayer(uuid).getName(), System.currentTimeMillis() + 600_000L));
    }

    // ── Admin Command & Diagnostics ───────────────────────────────

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("coresuite.admin")) {
            TextUtil.sendMessage(sender, "&cYou don't have permission to use this command.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            boolean isRunning = server != null;
            int port = getConfig().getInt("port", 25580);
            String bindIp = getConfig().getString("bind-ip", "0.0.0.0");
            TextUtil.sendMessage(sender, "&8&m--------------------------------------------------");
            TextUtil.sendMessage(sender, " &b&lWebsite Integration Module Status");
            TextUtil.sendMessage(sender, " &7HTTP Server: " + (isRunning ? "&a● Running" : "&c● Stopped"));
            TextUtil.sendMessage(sender, " &7Bound Address: &e" + bindIp + ":" + port);
            TextUtil.sendMessage(sender, " &7Total Linked Discord Accounts: &a" + uuidToDiscordCache.size());
            TextUtil.sendMessage(sender, " &7Active Pending Verification Codes: &e" + pendingLinks.size());
            TextUtil.sendMessage(sender, " &7Subcommands: &e/website reload &7| &e/website links");
            TextUtil.sendMessage(sender, "&8&m--------------------------------------------------");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            loadConfig();
            startServer();
            TextUtil.sendMessage(sender, "&8[&bWebsite&8] &aWebsite integration config reloaded and HTTP server restarted!");
            return true;
        }

        if (args[0].equalsIgnoreCase("links")) {
            TextUtil.sendMessage(sender, "&8[&bWebsite&8] &6Linked Accounts (&e" + uuidToDiscordCache.size() + "&6):");
            int count = 0;
            for (Map.Entry<UUID, String> entry : uuidToDiscordCache.entrySet()) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
                String name = op.getName() != null ? op.getName() : entry.getKey().toString();
                TextUtil.sendMessage(sender, " &7- &f" + name + " &8-> &bDiscord ID: &e" + entry.getValue());
                if (++count >= 15) {
                    TextUtil.sendMessage(sender, " &7... and " + (uuidToDiscordCache.size() - 15) + " more.");
                    break;
                }
            }
            return true;
        }

        TextUtil.sendMessage(sender, "&cUsage: /website [status|reload|links]");
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("coresuite.admin")) return Collections.emptyList();
        if (args.length == 1) {
            return Arrays.asList("status", "reload", "links");
        }
        return Collections.emptyList();
    }
}
