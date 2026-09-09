package net.rocky.coresuite.module.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility for sanitizing messages between Minecraft and Discord.
 * Prevents mass-pinging (@everyone, @here) and cleans/formats mentions and emojis.
 */
public final class DiscordSanitizer {

    private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)&[0-9A-FK-ORX]|&#[0-9A-F]{6}|§[0-9A-FK-ORX]");
    private static final Pattern EMOJI_PATTERN = Pattern.compile("<a?:([a-zA-Z0-9_]+):\\d+>");
    private static final Pattern USER_MENTION_PATTERN = Pattern.compile("<@!?(\\d+)>");
    private static final Pattern ROLE_MENTION_PATTERN = Pattern.compile("<@&\\d+>");
    private static final Pattern CHANNEL_MENTION_PATTERN = Pattern.compile("<#\\d+>");
    private static final Pattern BOLD_PATTERN = Pattern.compile("\\*\\*([^*]+)\\*\\*");
    private static final Pattern ITALIC_PATTERN = Pattern.compile("(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)");
    private static final Pattern UNDERLINE_PATTERN = Pattern.compile("__([^_]+)__");
    private static final Pattern STRIKE_PATTERN = Pattern.compile("~~([^~]+)~~");
    private static final Pattern CODE_PATTERN = Pattern.compile("`([^`\\n]+)`");

    private DiscordSanitizer() {}

    /**
     * Sanitizes in-game text before sending to Discord Webhooks or Bot REST API.
     * Prevents @everyone, @here, and unwanted pings.
     */
    public static String toDiscord(String message) {
        if (message == null || message.isEmpty()) return "";

        // Strip legacy and hex Minecraft color codes
        String clean = STRIP_COLOR_PATTERN.matcher(message).replaceAll("");

        // Prevent @everyone and @here pings using zero-width spaces
        clean = clean.replace("@everyone", "@\u200Beveryone")
                     .replace("@here", "@\u200Bhere");

        // Neutralize role/user raw mention syntax if entered in-game
        clean = clean.replace("<@&", "<@&\u200B")
                     .replace("<@", "<@\u200B");

        return clean.trim();
    }

    /**
     * Sanitizes incoming Discord messages before displaying in Minecraft chat.
     * Resolves custom emojis to :name:, user mentions to @Username, role/channel mentions, and markdown.
     */
    public static String toMinecraft(String message, JsonArray mentions) {
        if (message == null || message.isEmpty()) return "";

        // Remove newlines or replace with spaces to prevent multiline chat spam
        String clean = message.replace("\r", "").replace("\n", " ").trim();

        // Strip raw section signs to prevent unauthorized formatting injection
        clean = clean.replace("§", "");

        // 1. Resolve custom and animated Discord emojis: <:pepe:12345> -> :pepe:
        clean = EMOJI_PATTERN.matcher(clean).replaceAll(":$1:");

        // 2. Resolve user mentions: <@123456> or <@!123456> -> @DisplayName / @Username
        Matcher userMatcher = USER_MENTION_PATTERN.matcher(clean);
        StringBuilder userSb = new StringBuilder();
        while (userMatcher.find()) {
            String userId = userMatcher.group(1);
            String replacement = resolveMentionName(userId, mentions);
            userMatcher.appendReplacement(userSb, Matcher.quoteReplacement(replacement));
        }
        userMatcher.appendTail(userSb);
        clean = userSb.toString();

        // 3. Resolve role and channel mentions
        clean = ROLE_MENTION_PATTERN.matcher(clean).replaceAll("@role");
        clean = CHANNEL_MENTION_PATTERN.matcher(clean).replaceAll("#channel");

        // 4. Translate basic Discord markdown into Minecraft color codes
        clean = BOLD_PATTERN.matcher(clean).replaceAll("&l$1&r");
        clean = ITALIC_PATTERN.matcher(clean).replaceAll("&o$1&r");
        clean = UNDERLINE_PATTERN.matcher(clean).replaceAll("&n$1&r");
        clean = STRIKE_PATTERN.matcher(clean).replaceAll("&m$1&r");
        clean = CODE_PATTERN.matcher(clean).replaceAll("&7$1&r");

        return clean.trim();
    }

    /**
     * Backwards-compatible overload for toMinecraft without mentions array.
     */
    public static String toMinecraft(String message) {
        return toMinecraft(message, null);
    }

    private static String resolveMentionName(String userId, JsonArray mentions) {
        if (mentions != null) {
            for (JsonElement el : mentions) {
                if (el.isJsonObject()) {
                    JsonObject userObj = el.getAsJsonObject();
                    if (userObj.has("id") && userId.equals(userObj.get("id").getAsString())) {
                        if (userObj.has("global_name") && !userObj.get("global_name").isJsonNull()) {
                            return "@" + userObj.get("global_name").getAsString();
                        }
                        if (userObj.has("username") && !userObj.get("username").isJsonNull()) {
                            return "@" + userObj.get("username").getAsString();
                        }
                    }
                }
            }
        }
        return "@User";
    }

    /**
     * Escapes raw JSON string characters for safe inclusion in payload templates.
     */
    public static String escapeJson(String text) {
        if (text == null) return "";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c <= '\u001F') {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }
}
