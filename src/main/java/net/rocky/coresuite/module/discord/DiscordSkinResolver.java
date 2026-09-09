package net.rocky.coresuite.module.discord;

import net.rocky.coresuite.CoreSuitePlugin;
import net.skinsrestorer.api.SkinsRestorer;
import net.skinsrestorer.api.SkinsRestorerProvider;
import net.skinsrestorer.api.property.SkinProperty;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance skin and avatar resolver tailored for:
 * 1. Java Premium players
 * 2. Cracked players with custom skins set via SkinsRestorer
 * 3. Bedrock players connected via Geyser / Floodgate
 */
public final class DiscordSkinResolver {

    private final CoreSuitePlugin plugin;
    private final FileConfiguration config;
    private final Map<UUID, String> avatarCache = new ConcurrentHashMap<>();

    public DiscordSkinResolver(CoreSuitePlugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
    }

    /**
     * Resolves the Discord avatar URL for a player.
     * Checks in-memory cache first, then SkinsRestorer texture hash, then fallback.
     */
    public String getAvatarUrl(Player player) {
        if (player == null) return getDefaultAvatar();

        return avatarCache.computeIfAbsent(player.getUniqueId(), uuid -> resolveDirect(player));
    }

    /**
     * Resolves the avatar URL directly without using cache.
     */
    private String resolveDirect(Player player) {
        String template = config.getString("avatars.avatar-url-template", "https://mc-heads.net/avatar/{identifier}/100.png");
        boolean useSr = config.getBoolean("avatars.use-skinsrestorer", true);

        // 1. Check SkinsRestorer for custom skin texture hash (works for cracked players!)
        if (useSr && Bukkit.getPluginManager().isPluginEnabled("SkinsRestorer")) {
            try {
                SkinsRestorer sr = SkinsRestorerProvider.get();
                Optional<SkinProperty> opt = sr.getPlayerStorage().getSkinForPlayer(player.getUniqueId(), player.getName());
                if (opt.isPresent()) {
                    String base64Value = opt.get().getValue();
                    if (base64Value != null && !base64Value.isEmpty()) {
                        String textureHash = extractTextureHash(base64Value);
                        if (textureHash != null && !textureHash.isEmpty()) {
                            return template.replace("{identifier}", textureHash);
                        }
                    }
                }
            } catch (Throwable ignored) {
                // SkinsRestorer API not loaded or incompatible version
            }
        }

        // 2. Bedrock (Geyser/Floodgate) check
        String bedrockPrefix = config.getString("avatars.bedrock-prefix", ".");
        String name = player.getName();
        if (!bedrockPrefix.isEmpty() && name.startsWith(bedrockPrefix)) {
            String cleanName = name.substring(bedrockPrefix.length());
            if (!cleanName.isEmpty()) {
                return template.replace("{identifier}", cleanName);
            }
        }

        // 3. Fallback to username
        if (name != null && !name.isEmpty()) {
            return template.replace("{identifier}", name);
        }

        return getDefaultAvatar();
    }

    /**
     * Extracts the Mojang skin texture hash from SkinsRestorer's Base64 JSON payload.
     * E.g. {"textures":{"SKIN":{"url":"http://textures.minecraft.net/texture/<hash>"}}}
     */
    private String extractTextureHash(String base64Value) {
        try {
            String json = new String(Base64.getDecoder().decode(base64Value), StandardCharsets.UTF_8);
            int idx = json.indexOf("/texture/");
            if (idx != -1) {
                int start = idx + 9;
                int end = json.indexOf('"', start);
                if (end != -1) {
                    return json.substring(start, end);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Checks whether a player joined from Bedrock (e.g. Geyser/Floodgate).
     */
    public boolean isBedrock(Player player) {
        if (player == null) return false;
        String prefix = config.getString("avatars.bedrock-prefix", ".");
        return !prefix.isEmpty() && player.getName().startsWith(prefix);
    }

    /**
     * Gets the formatted player display name for Discord, adding a Bedrock tag if configured.
     */
    public String getFormattedName(Player player) {
        if (player == null) return "Unknown";
        String name = player.getName();
        if (isBedrock(player)) {
            String tag = config.getString("avatars.bedrock-tag", " 📱");
            return name + tag;
        }
        return name;
    }

    public String getDefaultAvatar() {
        return config.getString("avatars.default-avatar-url", "https://mc-heads.net/avatar/MHF_Steve/100.png");
    }

    public void clearCache(UUID uuid) {
        avatarCache.remove(uuid);
    }

    public void clearAll() {
        avatarCache.clear();
    }
}
