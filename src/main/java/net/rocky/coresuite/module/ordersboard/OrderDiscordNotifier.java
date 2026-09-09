package net.rocky.coresuite.module.ordersboard;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.module.discord.DiscordModule;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Builds and dispatches rich Discord embeds when players post new item or service orders.
 */
public final class OrderDiscordNotifier {

    private final CoreSuitePlugin plugin;

    public OrderDiscordNotifier(CoreSuitePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Broadcasts a newly posted order to Discord.
     */
    public void broadcastNewOrder(Order order) {
        if (order == null) return;

        DiscordModule discord = (DiscordModule) plugin.getModuleManager().getModule("discord");
        if (discord == null || !discord.isEnabled()) {
            return;
        }

        try {
            boolean isService = !order.isItemOrder();
            String payload = isService ? buildServiceOrderPayload(discord, order) : buildItemOrderPayload(discord, order);
            discord.sendOrderEmbed(payload, isService);
        } catch (Exception e) {
            plugin.getLogger().warning("[Orders Discord] Failed to dispatch order announcement: " + e.getMessage());
        }
    }

    private String buildItemOrderPayload(DiscordModule discord, Order order) {
        int color = discord.getOrdersEmbedColor();
        String posterName = order.getPosterName();
        String avatarUrl = resolveAvatarUrl(discord, order.getPosterUuid(), posterName);
        String itemName = OrderManager.formatMaterialName(order.getMaterial());
        int amount = order.getAmountNeeded();
        String totalPayout = plugin.getEconomyManager().format(order.getPriceTotal());
        String unitPayout = plugin.getEconomyManager().format(order.getPricePerItem());
        long expiryEpochSec = order.getExpiresAt() / 1000;
        String isoTimestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(order.getPostedAt()));

        JsonObject embed = new JsonObject();
        embed.addProperty("title", "📦 New Item Order Posted!");
        embed.addProperty("description", "**" + posterName + "** posted a buy order for **" + amount + "x " + itemName + "**!\nFulfill this order in-game using `/orders` to earn money.");
        embed.addProperty("color", color);

        JsonObject author = new JsonObject();
        author.addProperty("name", posterName);
        author.addProperty("icon_url", avatarUrl);
        embed.add("author", author);

        JsonArray fields = new JsonArray();

        JsonObject fItem = new JsonObject();
        fItem.addProperty("name", "📋 Item");
        fItem.addProperty("value", "`" + itemName + "`");
        fItem.addProperty("inline", true);
        fields.add(fItem);

        JsonObject fQty = new JsonObject();
        fQty.addProperty("name", "🔢 Quantity");
        fQty.addProperty("value", "`" + amount + "`");
        fQty.addProperty("inline", true);
        fields.add(fQty);

        JsonObject fTotal = new JsonObject();
        fTotal.addProperty("name", "💰 Total Payout");
        fTotal.addProperty("value", "**" + totalPayout + "**");
        fTotal.addProperty("inline", true);
        fields.add(fTotal);

        JsonObject fUnit = new JsonObject();
        fUnit.addProperty("name", "💵 Price / Unit");
        fUnit.addProperty("value", unitPayout);
        fUnit.addProperty("inline", true);
        fields.add(fUnit);

        JsonObject fExpiry = new JsonObject();
        fExpiry.addProperty("name", "⏳ Expires");
        fExpiry.addProperty("value", "<t:" + expiryEpochSec + ":R>");
        fExpiry.addProperty("inline", true);
        fields.add(fExpiry);

        JsonObject fId = new JsonObject();
        fId.addProperty("name", "🆔 Order ID");
        fId.addProperty("value", "`#" + order.getId() + "`");
        fId.addProperty("inline", true);
        fields.add(fId);

        embed.add("fields", fields);

        JsonObject footer = new JsonObject();
        footer.addProperty("text", "MeerMC Orders Board • Run /orders in-game");
        embed.add("footer", footer);
        embed.addProperty("timestamp", isoTimestamp);

        JsonArray embeds = new JsonArray();
        embeds.add(embed);

        JsonObject root = new JsonObject();
        root.addProperty("username", "MeerMC Orders");
        root.addProperty("avatar_url", "https://mc-heads.net/avatar/MHF_Chest/100.png");
        root.add("embeds", embeds);

        return root.toString();
    }

    private String buildServiceOrderPayload(DiscordModule discord, Order order) {
        int color = discord.getOrdersEmbedColor();
        String posterName = order.getPosterName();
        String avatarUrl = resolveAvatarUrl(discord, order.getPosterUuid(), posterName);
        String desc = order.getDescription() != null ? order.getDescription() : "Custom Service";
        String totalPayout = plugin.getEconomyManager().format(order.getPriceTotal());
        long expiryEpochSec = order.getExpiresAt() / 1000;
        String isoTimestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.ofEpochMilli(order.getPostedAt()));

        JsonObject embed = new JsonObject();
        embed.addProperty("title", "🛠️ New Service Order Posted!");
        embed.addProperty("description", "**" + posterName + "** has posted a task / service request!\nBrowse and accept contracts in-game using `/orders`.\n\n📝 **Task Description:**\n> *" + desc + "*");
        embed.addProperty("color", color);

        JsonObject author = new JsonObject();
        author.addProperty("name", posterName);
        author.addProperty("icon_url", avatarUrl);
        embed.add("author", author);

        JsonArray fields = new JsonArray();

        JsonObject fTotal = new JsonObject();
        fTotal.addProperty("name", "💰 Bounty Payout");
        fTotal.addProperty("value", "**" + totalPayout + "**");
        fTotal.addProperty("inline", true);
        fields.add(fTotal);

        JsonObject fExpiry = new JsonObject();
        fExpiry.addProperty("name", "⏳ Expires");
        fExpiry.addProperty("value", "<t:" + expiryEpochSec + ":R>");
        fExpiry.addProperty("inline", true);
        fields.add(fExpiry);

        JsonObject fId = new JsonObject();
        fId.addProperty("name", "🆔 Order ID");
        fId.addProperty("value", "`#" + order.getId() + "`");
        fId.addProperty("inline", true);
        fields.add(fId);

        embed.add("fields", fields);

        JsonObject footer = new JsonObject();
        footer.addProperty("text", "MeerMC Orders Board • Run /orders in-game to accept");
        embed.add("footer", footer);
        embed.addProperty("timestamp", isoTimestamp);

        JsonArray embeds = new JsonArray();
        embeds.add(embed);

        JsonObject root = new JsonObject();
        root.addProperty("username", "MeerMC Orders");
        root.addProperty("avatar_url", "https://mc-heads.net/avatar/MHF_Villager/100.png");
        root.add("embeds", embeds);

        return root.toString();
    }

    private String resolveAvatarUrl(DiscordModule discord, String uuidStr, String playerName) {
        if (discord != null && discord.getSkinResolver() != null) {
            try {
                UUID uuid = UUID.fromString(uuidStr);
                return discord.getSkinResolver().getAvatarUrl(uuid, playerName);
            } catch (Exception ignored) {}
        }
        return "https://mc-heads.net/avatar/" + playerName + "/100.png";
    }
}
