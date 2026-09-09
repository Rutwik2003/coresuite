package net.rocky.coresuite.module.discord;

import java.util.regex.Pattern;

/**
 * Utility for sanitizing messages between Minecraft and Discord.
 * Prevents mass-pinging (@everyone, @here) and cleans formatting.
 */
public final class DiscordSanitizer {

    private static final Pattern STRIP_COLOR_PATTERN = Pattern.compile("(?i)&[0-9A-FK-ORX]|&#[0-9A-F]{6}|§[0-9A-FK-ORX]");
    private static final Pattern DISCORD_MENTION_PATTERN = Pattern.compile("<@!?(\\d+)>|<@&(\\d+)>");
    private static final Pattern DISCORD_CHANNEL_PATTERN = Pattern.compile("<#(\\d+)>");

    private DiscordSanitizer() {}

    /**
     * Sanitizes in-game text before sending to Discord Webhooks.
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
     */
    public static String toMinecraft(String message) {
        if (message == null || message.isEmpty()) return "";

        // Remove newlines or replace with spaces to prevent multiline chat spam
        String clean = message.replace("\r", "").replace("\n", " ").trim();

        // Strip color codes if any user typed &a in Discord
        clean = clean.replace("§", "");

        return clean;
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
