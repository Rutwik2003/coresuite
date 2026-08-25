package net.rocky.coresuite.module.profile;

import me.clip.placeholderapi.PlaceholderAPI;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.gui.GuiButton;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;

public class ProfileModule extends Module implements CommandExecutor {

    public ProfileModule(CoreSuitePlugin plugin) {
        super(plugin, "Profile", "profile");
    }

    @Override
    public void onEnable() {
        if (!config.contains("placeholders.claim-blocks")) {
            config.set("placeholders.claim-blocks", "%griefprevention_remaining_claim_blocks%");
        }
        if (!config.contains("placeholders.vote-points")) {
            config.set("placeholders.vote-points", "%votingplugin_points%");
        }
        saveConfig();

        if (plugin.getCommand("profile") != null) {
            plugin.getCommand("profile").setExecutor(this);
        }
    }

    @Override
    public void onDisable() {
        if (plugin.getCommand("profile") != null && plugin.getCommand("profile").getExecutor() == this) {
            plugin.getCommand("profile").setExecutor(null);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (!player.hasPermission("coresuite.profile.use")) {
            TextUtil.sendMessage(player, "&cNo permission.");
            return true;
        }

        // Fetch custom fishing stats asynchronously
        plugin.getDatabaseManager().executeAsync(() -> {
            int fishLevel = 1;
            int fishXp = 0;
            int fishCatches = 0;

            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT level, xp, total_catches FROM coresuite_fishing WHERE uuid = ?")) {
                stmt.setString(1, player.getUniqueId().toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        fishLevel = rs.getInt("level");
                        fishXp = rs.getInt("xp");
                        fishCatches = rs.getInt("total_catches");
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load fishing data for profile", e);
            }

            final int fLevel = fishLevel;
            final int fXp = fishXp;
            final int fCatches = fishCatches;

            // Fetch Placeholders (must be on main thread for safety, but typically PAPI allows async, let's just do it on main thread)
            Bukkit.getScheduler().runTask(plugin, () -> {
                String claimBlocksPh = config.getString("placeholders.claim-blocks", "%griefprevention_remaining_claim_blocks%");
                String votePointsPh = config.getString("placeholders.vote-points", "%votingplugin_points%");

                String claimBlocksStr = PlaceholderAPI.setPlaceholders(player, claimBlocksPh);
                String votePointsStr = PlaceholderAPI.setPlaceholders(player, votePointsPh);

                // Check if they didn't parse
                if (claimBlocksStr.equals(claimBlocksPh)) claimBlocksStr = "0";
                if (votePointsStr.equals(votePointsPh)) votePointsStr = "0";

                openProfileGui(player, fLevel, fXp, fCatches, claimBlocksStr, votePointsStr);
            });
        });

        return true;
    }

    private void openProfileGui(Player player, int fishLevel, int fishXp, int fishCatches, String claimBlocks, String votePoints) {
        Gui gui = new Gui("&8Profile: " + player.getName(), 3);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        // Slot 10: Golden Shovel (Claim Blocks)
        List<String> claimLore = new ArrayList<>();
        claimLore.add("&7Available Claim Blocks: &e" + claimBlocks);
        claimLore.add("");
        claimLore.add("&7Use these to protect your land!");
        gui.setItem(10, GuiButton.of(Material.GOLDEN_SHOVEL, "&e&lClaim Blocks", claimLore, e -> {}));

        // Slot 11: Fishing Rod (Custom Fishing Stats)
        List<String> fishLore = new ArrayList<>();
        fishLore.add("&7Level: &b" + fishLevel);
        fishLore.add("&7XP: &b" + fishXp);
        fishLore.add("&7Total Catches: &b" + fishCatches);
        gui.setItem(11, GuiButton.of(Material.FISHING_ROD, "&b&lFishing Stats", fishLore, e -> {}));

        // Slot 12: Clock (Playtime)
        int playtimeTicks = player.getStatistic(Statistic.PLAY_ONE_MINUTE);
        long playtimeMinutes = playtimeTicks / 20 / 60;
        long hours = playtimeMinutes / 60;
        long minutes = playtimeMinutes % 60;
        List<String> playtimeLore = new ArrayList<>();
        playtimeLore.add("&7Time Played: &a" + hours + "h " + minutes + "m");
        gui.setItem(12, GuiButton.of(Material.CLOCK, "&a&lPlaytime", playtimeLore, e -> {}));

        // Slot 13: Player Head (General Info)
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.displayName(TextUtil.colorize("&6&l" + player.getName() + "'s Profile"));
            List<String> headLore = new ArrayList<>();
            SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy");
            headLore.add("&7First Joined: &f" + sdf.format(new Date(player.getFirstPlayed())));
            headLore.add("&7Current World: &f" + player.getWorld().getName());
            meta.lore(headLore.stream().map(TextUtil::colorize).toList());
            head.setItemMeta(meta);
        }
        gui.setItem(13, new GuiButton(head, e -> {}));

        // Slot 14: Emerald (Balance)
        double balance = 0.0;
        if (plugin.getEconomyManager() != null && plugin.getEconomyManager().isAvailable()) {
            balance = plugin.getEconomyManager().getBalance(player);
        }
        List<String> balanceLore = new ArrayList<>();
        balanceLore.add("&7Current Balance: &a$" + TextUtil.formatNumber(balance));
        gui.setItem(14, GuiButton.of(Material.EMERALD, "&a&lWealth", balanceLore, e -> {}));

        // Slot 15: Name Tag (Vote Points)
        List<String> voteLore = new ArrayList<>();
        voteLore.add("&7Total Vote Points: &d" + votePoints);
        voteLore.add("");
        voteLore.add("&7Vote daily for rewards!");
        gui.setItem(15, GuiButton.of(Material.NAME_TAG, "&d&lVote Points", voteLore, e -> {}));

        // Slot 21: Diamond Sword (Kills)
        int kills = player.getStatistic(Statistic.PLAYER_KILLS);
        List<String> killLore = new ArrayList<>();
        killLore.add("&7Total Player Kills: &c" + kills);
        gui.setItem(21, GuiButton.of(Material.DIAMOND_SWORD, "&c&lCombat Stats", killLore, e -> {}));

        // Slot 23: Skeleton Skull (Deaths)
        int deaths = player.getStatistic(Statistic.DEATHS);
        List<String> deathLore = new ArrayList<>();
        deathLore.add("&7Total Deaths: &8" + deaths);
        gui.setItem(23, GuiButton.of(Material.SKELETON_SKULL, "&8&lDeath Stats", deathLore, e -> {}));

        gui.open(player);
    }
}
