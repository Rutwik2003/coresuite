/**
 * CoreSuite Plugin
 * Package: net.rocky.coresuite.common.text
 * 
 * Utility for manipulating and formatting text in Minecraft.
 */
package net.rocky.coresuite.common.text;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.text.DecimalFormat;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Static utility class for text formatting using Adventure API.
 */
public final class TextUtil {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.builder().strict(false).build();
    private static final LegacyComponentSerializer LEGACY_SERIALIZER = 
        LegacyComponentSerializer.builder().character('&').hexColors().build();
    private static final ThreadLocal<DecimalFormat> NUMBER_FORMAT = ThreadLocal.withInitial(() -> new DecimalFormat("#,##0.##"));
    
    // Regex for hex color codes like <#RRGGBB> or <##RRGGBB>
    private static final Pattern HEX_PATTERN = Pattern.compile("<#([0-9a-fA-F]{6})>");
    private static final Pattern DOUBLE_HEX_PATTERN = Pattern.compile("<##([0-9a-fA-F]{6})>");

    private TextUtil() {} // No instantiation

    /**
     * Parses legacy ampersand codes and hex codes into an Adventure Component.
     *
     * @param text The raw text string.
     * @return The parsed Component.
     */
    public static Component colorize(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        
        // Translate legacy hex format &#RRGGBB to <#RRGGBB>
        text = text.replaceAll("&#([0-9a-fA-F]{6})", "<#$1>");
        // Normalize <##RRGGBB> to <#RRGGBB>
        text = DOUBLE_HEX_PATTERN.matcher(text).replaceAll("<#$1>");
        
        // Translate standard legacy codes
        text = text.replace("&0", "<black>")
                   .replace("&1", "<dark_blue>")
                   .replace("&2", "<dark_green>")
                   .replace("&3", "<dark_aqua>")
                   .replace("&4", "<dark_red>")
                   .replace("&5", "<dark_purple>")
                   .replace("&6", "<gold>")
                   .replace("&7", "<gray>")
                   .replace("&8", "<dark_gray>")
                   .replace("&9", "<blue>")
                   .replace("&a", "<green>")
                   .replace("&b", "<aqua>")
                   .replace("&c", "<red>")
                   .replace("&d", "<light_purple>")
                   .replace("&e", "<yellow>")
                   .replace("&f", "<white>")
                   .replace("&k", "<obf>")
                   .replace("&l", "<b>")
                   .replace("&m", "<st>")
                   .replace("&n", "<u>")
                   .replace("&o", "<i>")
                   .replace("&r", "<reset>");

        return MINI_MESSAGE.deserialize(text)
            .decoration(TextDecoration.ITALIC, false); // Don't inherit italic by default
    }

    /**
     * Parses MiniMessage formatted text into an Adventure Component.
     *
     * @param text The MiniMessage string.
     * @return The parsed Component.
     */
    public static Component parseMiniMessage(String text) {
        if (text == null || text.isEmpty()) return Component.empty();
        return MINI_MESSAGE.deserialize(text);
    }

    /**
     * Colorizes a string using Bukkit's legacy chat color translate.
     * Intended only for APIs that absolutely require strings.
     *
     * @param text The string to format.
     * @return The legacy-formatted string.
     */
    public static String legacyColorize(String text) {
        if (text == null) return "";
        return org.bukkit.ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * Sends a colorized message to a CommandSender.
     *
     * @param sender  The recipient of the message.
     * @param message The message to format and send.
     */
    public static void sendMessage(CommandSender sender, String message) {
        sender.sendMessage(colorize(message));
    }

    /**
     * Sends a colorized, prefixed message to a CommandSender.
     *
     * @param sender  The recipient of the message.
     * @param prefix  The prefix string.
     * @param message The message string.
     */
    public static void sendPrefixed(CommandSender sender, String prefix, String message) {
        sender.sendMessage(colorize(prefix + message));
    }

    /**
     * Sends an action bar message to a player.
     *
     * @param player  The recipient player.
     * @param message The action bar text.
     */
    public static void sendActionBar(Player player, String message) {
        player.sendActionBar(colorize(message));
    }

    /**
     * Sends a title and subtitle to a player.
     *
     * @param player       The recipient player.
     * @param title        The main title text.
     * @param subtitle     The subtitle text.
     * @param fadeInTicks  Ticks to fade in.
     * @param stayTicks    Ticks to stay visible.
     * @param fadeOutTicks Ticks to fade out.
     */
    public static void sendTitle(Player player, String title, String subtitle, 
                                  int fadeInTicks, int stayTicks, int fadeOutTicks) {
        Title.Times times = Title.Times.times(
            Duration.ofMillis(fadeInTicks * 50L),
            Duration.ofMillis(stayTicks * 50L),
            Duration.ofMillis(fadeOutTicks * 50L)
        );
        player.showTitle(Title.title(colorize(title), colorize(subtitle), times));
    }

    /**
     * Formats a double into a comma-separated decimal string.
     *
     * @param number The number to format.
     * @return The formatted string.
     */
    public static String formatNumber(double number) {
        return NUMBER_FORMAT.get().format(number);
    }

    /**
     * Formats a duration in seconds into "Xh Ym Zs".
     *
     * @param totalSeconds Total seconds.
     * @return The formatted duration string.
     */
    public static String formatDuration(long totalSeconds) {
        if (totalSeconds < 0) totalSeconds = 0;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        StringBuilder sb = new StringBuilder();
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0 || hours > 0) sb.append(minutes).append("m ");
        sb.append(seconds).append("s");
        return sb.toString().trim();
    }

    /**
     * Creates a simple ItemStack with a formatted name and lore.
     *
     * @param material The item material.
     * @param name     The display name.
     * @param lore     The lore lines.
     * @return The constructed ItemStack.
     */
    public static ItemStack createItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(colorize(name));
            if (lore != null && lore.length > 0) {
                meta.lore(Arrays.stream(lore).map(TextUtil::colorize).toList());
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    /**
     * Creates an ItemStack with amount, a formatted name, and lore.
     *
     * @param material The item material.
     * @param amount   The item amount.
     * @param name     The display name.
     * @param lore     The lore lines.
     * @return The constructed ItemStack.
     */
    public static ItemStack createItem(Material material, int amount, String name, String... lore) {
        ItemStack item = createItem(material, name, lore);
        item.setAmount(amount);
        return item;
    }
}
