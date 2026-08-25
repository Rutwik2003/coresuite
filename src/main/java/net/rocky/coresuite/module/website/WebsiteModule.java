package net.rocky.coresuite.module.website;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.module.Module;
import net.rocky.coresuite.module.website.commands.LinkCommand;
import net.rocky.coresuite.module.website.commands.RankCommand;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.logging.Level;

public class WebsiteModule extends Module {

    private HttpServer server;
    private File dataFile;
    private FileConfiguration dataConfig;

    // Code -> UUID mappings for pending links
    private final Map<String, UUID> pendingLinks = new HashMap<>();

    public WebsiteModule(CoreSuitePlugin plugin) {
        super(plugin, "Website Integration", "website");
    }

    @Override
    public void onEnable() {
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.exists()) {
            try { dataFile.createNewFile(); } catch (IOException ignored) {}
        }
        dataConfig = YamlConfiguration.loadConfiguration(dataFile);

        int port = getConfig().getInt("port", 25580);
        String apiKey = getConfig().getString("api-key", "default_secret_key");

        try {
            String bindIp = getConfig().getString("bind-ip", "0.0.0.0");
            server = HttpServer.create(new InetSocketAddress(bindIp, port), 0);
            
            server.createContext("/api/players", exchange -> {
                if ("GET".equals(exchange.getRequestMethod())) {
                    if (!checkAuth(exchange, apiKey, "")) return;
                    StringBuilder playersJson = new StringBuilder("{\"players\":[");
                    Player[] players = Bukkit.getOnlinePlayers().toArray(new Player[0]);
                    for (int i = 0; i < players.length; i++) {
                        playersJson.append("\"").append(players[i].getName()).append("\"");
                        if (i < players.length - 1) playersJson.append(",");
                    }
                    playersJson.append("]}");
                    sendResponse(exchange, 200, playersJson.toString());
                } else {
                    sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                }
            });

            server.createContext("/api/execute", exchange -> {
                if ("POST".equals(exchange.getRequestMethod())) {
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    if (!checkAuth(exchange, apiKey, body)) return;
                    String[] commands = body.split("\n");
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        for (String cmd : commands) {
                            String cleanCmd = cmd.replace("\"", "").replace("[", "").replace("]", "").replace(",", "").trim();
                            if (!cleanCmd.isEmpty()) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cleanCmd);
                        }
                    });
                    sendResponse(exchange, 200, "{\"status\": \"success\"}");
                } else {
                    sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                }
            });

            server.createContext("/api/link", exchange -> {
                if ("POST".equals(exchange.getRequestMethod())) {
                    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    if (!checkAuth(exchange, apiKey, body)) return;
                    String[] parts = body.split(":");
                    if (parts.length == 2) {
                        String code = parts[0].trim();
                        String discordId = parts[1].trim();
                        if (pendingLinks.containsKey(code)) {
                            UUID uuid = pendingLinks.remove(code);
                            dataConfig.set("links." + discordId, uuid.toString());
                            try { dataConfig.save(dataFile); } catch (IOException ignored) {}
                            sendResponse(exchange, 200, "{\"status\": \"success\"}");
                        } else {
                            sendResponse(exchange, 400, "{\"error\": \"Invalid code\"}");
                        }
                    } else {
                        sendResponse(exchange, 400, "{\"error\": \"Bad format\"}");
                    }
                } else {
                    sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                }
            });

            server.createContext("/api/donators", exchange -> {
                if ("GET".equals(exchange.getRequestMethod())) {
                    if (!checkAuth(exchange, apiKey, "")) return;
                    // We will just return some top donators based on LuckPerms online players for speed,
                    // or a cached list. To avoid lag, we fetch online players with ranks.
                    LuckPerms lp = LuckPermsProvider.get();
                    StringBuilder json = new StringBuilder("[");
                    boolean first = true;
                    // For simplicity, we loop online players and check their primary group
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        User user = lp.getUserManager().getUser(p.getUniqueId());
                        if (user != null) {
                            String group = user.getPrimaryGroup();
                            if (group.equals("vip") || group.equals("cutie") || group.equals("elite") || group.equals("investor")) {
                                if (!first) json.append(",");
                                json.append("{\"username\":\"").append(p.getName()).append("\",");
                                json.append("\"tier\":\"").append(group).append("\"}");
                                first = false;
                            }
                        }
                    }
                    json.append("]");
                    sendResponse(exchange, 200, json.toString());
                } else {
                    sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}");
                }
            });

            server.createContext("/api/leaderboard", exchange -> {
                if ("GET".equals(exchange.getRequestMethod())) {
                    try {
                        if (!checkAuth(exchange, apiKey, "")) return;
                        
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            try {
                                StringBuilder json = new StringBuilder("[");
                                boolean first = true;
                                
                                for (int i = 1; i <= 10; i++) {
                                    String namePlaceholder = "%ajlb_lb_statistic_player_kills_" + i + "_alltime_name%";
                                    String valuePlaceholder = "%ajlb_lb_statistic_player_kills_" + i + "_alltime_value%";
                                    
                                    // Requires PlaceholderAPI to be installed
                                    String name = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(null, namePlaceholder);
                                    String valueStr = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(null, valuePlaceholder);
                                    
                                    if (name == null || name.isEmpty() || name.equals(namePlaceholder)) {
                                        continue;
                                    }
                                    
                                    int kills = 0;
                                    try { kills = Integer.parseInt(valueStr.replaceAll("[^0-9]", "")); } catch (Exception ignored) {}
                                    
                                    // Fetch additional stats for this specific player
                                    org.bukkit.OfflinePlayer op = Bukkit.getOfflinePlayer(name);
                                    
                                    String deathsStr = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(op, "%statistic_deaths%");
                                    int deaths = 0;
                                    try { deaths = Integer.parseInt(deathsStr.replaceAll("[^0-9]", "")); } catch (Exception ignored) {}
                                    
                                    String netWorthStr = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(op, "%vault_eco_balance%");
                                    long netWorth = 0;
                                    try { 
                                        // Remove commas and parse the whole number part
                                        String cleanWorth = netWorthStr.split("\\.")[0].replaceAll("[^0-9]", "");
                                        if (!cleanWorth.isEmpty()) netWorth = Long.parseLong(cleanWorth);
                                    } catch (Exception ignored) {}
                                    
                                    // play_one_minute is actually ticks in Bukkit (1 second = 20 ticks)
                                    String playtimeStr = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(op, "%statistic_play_one_minute%");
                                    long playtimeSeconds = 0;
                                    try { 
                                        playtimeSeconds = Long.parseLong(playtimeStr.replaceAll("[^0-9]", "")) / 20; 
                                    } catch (Exception ignored) {}
                                    
                                    if (!first) json.append(",");
                                    json.append("{")
                                        .append("\"username\":\"").append(name).append("\",")
                                        .append("\"kills\":").append(kills).append(",")
                                        .append("\"deaths\":").append(deaths).append(",")
                                        .append("\"netWorth\":").append(netWorth).append(",")
                                        .append("\"playtime\":").append(playtimeSeconds)
                                        .append("}");
                                    first = false;
                                }
                                json.append("]");
                                
                                // Send HTTP response back on a background thread to prevent lag
                                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                                    try {
                                        sendResponse(exchange, 200, json.toString());
                                    } catch (Exception ignored) {}
                                });
                            } catch (Exception e) {
                                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                                    try { sendResponse(exchange, 500, "{\"error\":\"PAPI Error\"}"); } catch (Exception ignored) {}
                                });
                            }
                        });
                    } catch (Exception e) {
                        try { sendResponse(exchange, 500, "{\"error\":\"Auth Error\"}"); } catch (Exception ignored) {}
                    }
                } else {
                    try { sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}"); } catch (Exception ignored) {}
                }
            });

            server.createContext("/api/voters", exchange -> {
                if ("GET".equals(exchange.getRequestMethod())) {
                    try {
                        if (!checkAuth(exchange, apiKey, "")) return;
                        
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            try {
                                StringBuilder json = new StringBuilder("[");
                                boolean first = true;
                                
                                for (int i = 1; i <= 3; i++) {
                                    String namePlaceholder = "%VotingPlugin_Top_Month_" + i + "%";
                                    String valuePlaceholder = "%VotingPlugin_Top_MonthVotes_" + i + "%";
                                    
                                    String name = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(null, namePlaceholder);
                                    String valueStr = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(null, valuePlaceholder);
                                    
                                    if (name == null || name.isEmpty() || name.equals(namePlaceholder)) {
                                        continue;
                                    }
                                    
                                    int votes = 0;
                                    try {
                                        votes = Integer.parseInt(valueStr.replaceAll("[^0-9]", ""));
                                    } catch (NumberFormatException ignored) {}
                                    
                                    if (!first) json.append(",");
                                    json.append("{")
                                        .append("\"username\":\"").append(name).append("\",")
                                        .append("\"votes\":").append(votes)
                                        .append("}");
                                    first = false;
                                }
                                json.append("]");
                                
                                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                                    try { sendResponse(exchange, 200, json.toString()); } catch (Exception ignored) {}
                                });
                            } catch (Exception e) {
                                Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
                                    try { sendResponse(exchange, 500, "{\"error\":\"PAPI Error\"}"); } catch (Exception ignored) {}
                                });
                            }
                        });
                    } catch (Exception e) {
                        try { sendResponse(exchange, 500, "{\"error\":\"Auth Error\"}"); } catch (Exception ignored) {}
                    }
                } else {
                    try { sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}"); } catch (Exception ignored) {}
                }
            });

            server.createContext("/api/config", exchange -> {
                if ("GET".equals(exchange.getRequestMethod())) {
                    try {
                        if (!checkAuth(exchange, apiKey, "")) return;
                        
                        StringBuilder json = new StringBuilder("{");
                        json.append("\"site_url\":\"").append(getConfig().getString("site.url", "")).append("\",");
                        json.append("\"server_java_ip\":\"").append(getConfig().getString("server.java-ip", "")).append("\",");
                        json.append("\"server_java_port\":\"").append(getConfig().getString("server.java-port", "")).append("\",");
                        json.append("\"server_bedrock_ip\":\"").append(getConfig().getString("server.bedrock-ip", "")).append("\",");
                        json.append("\"server_bedrock_port\":\"").append(getConfig().getString("server.bedrock-port", "")).append("\",");
                        json.append("\"server_version\":\"").append(getConfig().getString("server.version", "")).append("\",");
                        json.append("\"social_discord\":\"").append(getConfig().getString("social.discord", "")).append("\",");
                        json.append("\"social_instagram\":\"").append(getConfig().getString("social.instagram", "")).append("\",");
                        json.append("\"payment_upi_id\":\"").append(getConfig().getString("payment.upi-id", "")).append("\",");
                        json.append("\"store_rank_vip\":").append(getConfig().getInt("store.ranks.vip", 50)).append(",");
                        json.append("\"store_rank_cutie\":").append(getConfig().getInt("store.ranks.cutie", 69)).append(",");
                        json.append("\"store_rank_elite\":").append(getConfig().getInt("store.ranks.elite", 100)).append(",");
                        json.append("\"store_rank_investor\":").append(getConfig().getInt("store.ranks.investor", 120)).append(",");
                        json.append("\"store_key_rare\":").append(getConfig().getInt("store.keys.rare", 49)).append(",");
                        json.append("\"store_key_elite\":").append(getConfig().getInt("store.keys.elite", 80)).append(",");
                        json.append("\"store_key_insane\":").append(getConfig().getInt("store.keys.insane", 120));
                        json.append("}");
                        
                        sendResponse(exchange, 200, json.toString());
                    } catch (Exception e) {
                        try { sendResponse(exchange, 500, "{\"error\":\"Internal error\"}"); } catch (Exception ignored) {}
                    }
                } else {
                    try { sendResponse(exchange, 405, "{\"error\": \"Method not allowed\"}"); } catch (Exception ignored) {}
                }
            });

            server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
            server.start();
            log("Started internal web server on port " + port);

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to start internal web server", e);
        }

        if (plugin.getCommand("link") != null) plugin.getCommand("link").setExecutor(new LinkCommand(this));
        if (plugin.getCommand("rank") != null) plugin.getCommand("rank").setExecutor(new RankCommand());
    }

    @Override
    public void onDisable() {
        if (server != null) {
            server.stop(0);
            log("Stopped internal web server.");
        }
    }

    private boolean checkAuth(HttpExchange exchange, String apiKey, String body) throws IOException {
        String signature = exchange.getRequestHeaders().getFirst("X-Signature");
        String timestampStr = exchange.getRequestHeaders().getFirst("X-Timestamp");
        
        if (signature == null || timestampStr == null) {
            // Fallback for easy testing, but not secure over HTTP
            String authHeader = exchange.getRequestHeaders().getFirst("Authorization");
            if (authHeader != null && authHeader.equals("Bearer " + apiKey)) {
                return true;
            }
            sendResponse(exchange, 401, "{\"error\": \"Unauthorized - Missing HMAC Signature\"}");
            return false;
        }

        try {
            long timestamp = Long.parseLong(timestampStr);
            long now = System.currentTimeMillis();
            if (Math.abs(now - timestamp) > 300000) { // 5 minutes window
                sendResponse(exchange, 401, "{\"error\": \"Request expired\"}");
                return false;
            }

            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            javax.crypto.spec.SecretKeySpec secretKey = new javax.crypto.spec.SecretKeySpec(apiKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(secretKey);
            
            String payload = timestampStr + (body != null ? body : "");
            byte[] hash = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            
            if (!hexString.toString().equals(signature)) {
                sendResponse(exchange, 401, "{\"error\": \"Invalid signature\"}");
                return false;
            }
            
            return true;
        } catch (Exception e) {
            sendResponse(exchange, 500, "{\"error\": \"Auth error\"}");
            return false;
        }
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

    public void addPendingLink(String code, UUID uuid) {
        pendingLinks.put(code, uuid);
    }
}
