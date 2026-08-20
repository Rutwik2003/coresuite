package net.rocky.coresuite.module.store;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.gui.GuiButton;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class StoreModule extends Module implements CommandExecutor, Listener, TabCompleter {

    public StoreModule(CoreSuitePlugin plugin) {
        super(plugin, "Store", "store");
    }

    @Override
    public void onEnable() {
        registerListener(this);
        
        if (plugin.getCommand("store") != null) {
            plugin.getCommand("store").setExecutor(this);
            plugin.getCommand("store").setTabCompleter(this);
        }
        if (plugin.getCommand("storeadmin") != null) {
            plugin.getCommand("storeadmin").setExecutor(this);
            plugin.getCommand("storeadmin").setTabCompleter(this);
        }
    }

    @Override
    public void onDisable() {
        // Nothing special to disable
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("store")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Players only.");
                return true;
            }
            openMainGui(player);
            return true;
        }

        if (command.getName().equalsIgnoreCase("storeadmin")) {
            if (!sender.hasPermission("coresuite.store.admin")) {
                TextUtil.sendMessage(sender, "&cNo permission.");
                return true;
            }

            if (args.length >= 3 && args[0].equalsIgnoreCase("approve")) {
                String targetName = args[1];
                String packageKeyRaw = args[2];
                // Package key is like "ranks.VIP" or "crates.Rare"
                
                ConfigurationSection packageSec = config.getConfigurationSection(packageKeyRaw);
                if (packageSec == null) {
                    // Try lower/upper case if needed, but config keys are usually exact.
                    TextUtil.sendMessage(sender, "&cPackage not found: " + packageKeyRaw);
                    return true;
                }
                
                Player target = Bukkit.getPlayer(targetName);
                if (target == null) {
                    TextUtil.sendMessage(sender, "&cPlayer is offline, but rewarding anyway (commands run as console).");
                }

                List<String> commands = packageSec.getStringList("commands");
                for (String cmd : commands) {
                    String finalCmd = cmd.replace("%player%", targetName);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
                }
                TextUtil.sendMessage(sender, "&aSuccessfully approved package for " + targetName);
                
                if (target != null) {
                    TextUtil.sendMessage(target, "&aYour purchase of " + packageSec.getString("display-name", packageKeyRaw) + " &ahas been approved!");
                }
                return true;
            }
            TextUtil.sendMessage(sender, "&cUsage: /storeadmin approve <player> <category.Package>");
            return true;
        }
        return false;
    }

    private void openMainGui(Player player) {
        Gui gui = new Gui("&8Store", 3);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        List<String> rankLore = new ArrayList<>();
        rankLore.add("&7Click to view our Ranks!");
        gui.setItem(11, GuiButton.of(Material.DIAMOND, "&b&lRanks", rankLore, e -> {
            openCategoryGui(player, "ranks");
        }));

        List<String> crateLore = new ArrayList<>();
        crateLore.add("&7Click to view our Crates!");
        gui.setItem(15, GuiButton.of(Material.CHEST, "&6&lCrates", crateLore, e -> {
            openCategoryGui(player, "crates");
        }));

        gui.open(player);
    }

    private void openCategoryGui(Player player, String categoryName) {
        Gui gui = new Gui("&8" + categoryName.substring(0, 1).toUpperCase() + categoryName.substring(1), 5);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        ConfigurationSection categorySec = config.getConfigurationSection(categoryName);
        if (categorySec != null) {
            List<String> keys = new ArrayList<>(categorySec.getKeys(false));
            int size = keys.size();
            
            int[] slots;
            if (size == 1) slots = new int[]{22};
            else if (size == 2) slots = new int[]{21, 23};
            else if (size == 3) slots = new int[]{20, 22, 24};
            else if (size == 4) slots = new int[]{19, 21, 23, 25};
            else if (size == 5) slots = new int[]{20, 21, 22, 23, 24};
            else if (size == 6) slots = new int[]{19, 20, 21, 23, 24, 25};
            else if (size == 7) slots = new int[]{19, 20, 21, 22, 23, 24, 25};
            else {
                slots = new int[size];
                int s = 10;
                for (int i = 0; i < size; i++) {
                    while (s % 9 == 0 || s % 9 == 8) s++;
                    slots[i] = s++;
                }
            }

            int index = 0;
            for (String key : keys) {
                if (index >= slots.length) break;
                int slot = slots[index++];
                
                ConfigurationSection itemSec = categorySec.getConfigurationSection(key);
                if (itemSec == null) continue;

                String displayName = itemSec.getString("display-name", "&a" + key);
                Material material = Material.matchMaterial(itemSec.getString("material", "STONE"));
                if (material == null) material = Material.STONE;
                List<String> lore = new ArrayList<>(itemSec.getStringList("lore"));
                
                double priceIngame = itemSec.getDouble("price-ingame", -1);
                double priceIrl = itemSec.getDouble("price-irl", -1);

                lore.add("");
                if (priceIngame > 0) {
                    lore.add("&ePrice: $" + TextUtil.formatNumber(priceIngame));
                }
                if (priceIrl > 0) {
                    lore.add("&aReal Money Price: ₹" + priceIrl);
                }
                lore.add("");
                lore.add("&7Click to purchase");

                gui.setItem(slot, GuiButton.of(material, displayName, lore, e -> {
                    handlePackageClick(player, categoryName + "." + key, priceIngame, priceIrl);
                }));
                slot++;
            }
        }

        gui.setItem(40, GuiButton.of(Material.ARROW, "&cBack", List.of("&7Return to main menu"), e -> {
            openMainGui(player);
        }));

        gui.open(player);
    }

    private void handlePackageClick(Player player, String packageKey, double priceIngame, double priceIrl) {
        if (priceIngame > 0 && priceIrl > 0) {
            // Open selection GUI
            Gui gui = new Gui("&8Select Payment Method", 3);
            gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

            gui.setItem(11, GuiButton.of(Material.GOLD_INGOT, "&e&lIn-Game Balance", List.of("&7Cost: $" + TextUtil.formatNumber(priceIngame), "", "&7Click to buy with in-game money!"), e -> {
                processIngamePurchase(player, packageKey, priceIngame);
            }));

            gui.setItem(15, GuiButton.of(Material.EMERALD, "&a&lUPI (Real Money)", List.of("&7Cost: ₹" + priceIrl, "", "&7Click to buy with real money!"), e -> {
                processUpiPurchase(player, packageKey, priceIrl);
            }));

            gui.setItem(22, GuiButton.of(Material.ARROW, "&cBack", List.of("&7Return to categories"), e -> {
                String category = packageKey.split("\\.")[0];
                openCategoryGui(player, category);
            }));

            gui.open(player);
        } else if (priceIngame > 0) {
            processIngamePurchase(player, packageKey, priceIngame);
        } else if (priceIrl > 0) {
            processUpiPurchase(player, packageKey, priceIrl);
        } else {
            TextUtil.sendMessage(player, "&cThis package is currently unavailable.");
            player.closeInventory();
        }
    }

    private void processIngamePurchase(Player player, String packageKey, double price) {
        if (plugin.getEconomyManager() == null || !plugin.getEconomyManager().isAvailable()) {
            TextUtil.sendMessage(player, "&cEconomy is not enabled.");
            return;
        }

        if (!plugin.getEconomyManager().has(player, price)) {
            TextUtil.sendMessage(player, "&cYou don't have enough money! Need $" + TextUtil.formatNumber(price));
            return;
        }

        boolean success = plugin.getEconomyManager().withdraw(player, price);
        if (!success) {
            TextUtil.sendMessage(player, "&cTransaction failed.");
            return;
        }
        
        TextUtil.sendMessage(player, "&aSuccessfully purchased package!");
        
        ConfigurationSection packageSec = config.getConfigurationSection(packageKey);
        if (packageSec != null) {
            List<String> commands = packageSec.getStringList("commands");
            for (String cmd : commands) {
                String finalCmd = cmd.replace("%player%", player.getName());
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd);
            }
        }
        player.closeInventory();
    }

    private void processUpiPurchase(Player player, String packageKey, double price) {
        player.closeInventory();
        
        String txnId = "#TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String upiId = config.getString("UPI-id", "unknown@upi");
        
        TextUtil.sendMessage(player, "&a&m----------------------------------------");
        TextUtil.sendMessage(player, "&e&lUPI PAYMENT REQUIRED");
        TextUtil.sendMessage(player, "&7Please pay &a₹" + price + " &7to the UPI ID below.");
        TextUtil.sendMessage(player, "&bUPI ID: &f" + upiId);
        TextUtil.sendMessage(player, "&7Include your username and this Transaction ID in the note:");
        TextUtil.sendMessage(player, "&cTransaction ID: &f" + txnId);
        TextUtil.sendMessage(player, "&7An admin will verify and approve your purchase shortly.");
        TextUtil.sendMessage(player, "&a&m----------------------------------------");

        sendWebhook(player.getName(), packageKey, txnId, price);
    }

    private void sendWebhook(String playerName, String packageKey, String txnId, double price) {
        String webhookUrl = config.getString("webhook-url");
        if (webhookUrl == null || webhookUrl.isEmpty() || webhookUrl.equals("https://discord.com/api/webhooks/your-webhook-url")) {
            warn("Discord webhook URL is not configured. Admins won't be notified of UPI purchases!");
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                URL url = new URL(webhookUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                connection.setDoOutput(true);

                String rconCommand = "/storeadmin approve " + playerName + " " + packageKey;
                
                String jsonPayload = String.format("{\"content\": null, \"embeds\": [{\"title\": \"New Store Purchase (Pending)\", " +
                        "\"description\": \"**Player**: %s\\n**Package**: %s\\n**Price**: ₹%.2f\\n**TXN ID**: %s\\n\\n" +
                        "**RCON Command to approve**: ```%s```\", \"color\": 5814783}]}", 
                        playerName, packageKey, price, txnId, rconCommand);

                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = connection.getResponseCode();
                if (responseCode < 200 || responseCode >= 300) {
                    warn("Discord webhook returned response code: " + responseCode);
                }
            } catch (Exception e) {
                warn("Failed to send webhook notification: " + e.getMessage());
            }
        });
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("storeadmin")) {
            if (args.length == 1) {
                return List.of("approve");
            }
            if (args.length == 2 && args[0].equalsIgnoreCase("approve")) {
                return null; // suggest players
            }
            if (args.length == 3 && args[0].equalsIgnoreCase("approve")) {
                List<String> packages = new ArrayList<>();
                ConfigurationSection ranks = config.getConfigurationSection("ranks");
                if (ranks != null) {
                    for (String r : ranks.getKeys(false)) {
                        packages.add("ranks." + r);
                    }
                }
                ConfigurationSection crates = config.getConfigurationSection("crates");
                if (crates != null) {
                    for (String c : crates.getKeys(false)) {
                        packages.add("crates." + c);
                    }
                }
                return packages;
            }
        }
        return new ArrayList<>();
    }
}
