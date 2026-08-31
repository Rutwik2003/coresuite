package net.rocky.coresuite.module.treasurehunt;

import net.kyori.adventure.text.Component;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.gui.GuiButton;
import net.rocky.coresuite.module.Module;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class TreasureHuntModule extends Module implements Listener, CommandExecutor, org.bukkit.command.TabCompleter {

    private final NamespacedKey clueKey;

    public TreasureHuntModule(CoreSuitePlugin plugin) {
        super(plugin, "Treasure Hunt", "treasure-hunt");
        this.clueKey = new NamespacedKey(plugin, "treasure_clue");
    }

    @Override
    public void onEnable() {
        if (!config.getBoolean("enabled", true)) {
            return;
        }
        
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
        var cmd = plugin.getCommand("treasurehunt");
        if (cmd != null) {
            cmd.setExecutor(this);
            cmd.setTabCompleter(this);
        }
    }

    @Override
    public void onDisable() {
        // Nothing special
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player p) {
                openVerifyGui(p);
            }
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("verify") && sender instanceof Player p) {
            openVerifyGui(p);
            return true;
        }

        // Admin commands below
        if (!sender.hasPermission("coresuite.treasurehunt.admin")) {
            TextUtil.sendMessage(sender, "&cYou don't have permission.");
            return true;
        }

        if (sub.equals("give")) {
            if (args.length < 3) {
                TextUtil.sendMessage(sender, "&cUsage: /treasurehunt give <player> <clue_number>");
                return true;
            }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) {
                TextUtil.sendMessage(sender, "&cPlayer not found.");
                return true;
            }
            int clueNum;
            try {
                clueNum = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                TextUtil.sendMessage(sender, "&cInvalid clue number.");
                return true;
            }

            ItemStack token = createToken(clueNum);
            target.getInventory().addItem(token);
            TextUtil.sendMessage(sender, "&aGiven Clue #" + clueNum + " to " + target.getName());
            return true;
        }

        if (sub.equals("setlocation") && sender instanceof Player p) {
            Location loc = p.getLocation();
            config.set("final-location.world", loc.getWorld().getName());
            config.set("final-location.x", loc.getX());
            config.set("final-location.y", loc.getY());
            config.set("final-location.z", loc.getZ());
            config.set("final-location.yaw", loc.getYaw());
            config.set("final-location.pitch", loc.getPitch());
            saveConfig();
            TextUtil.sendMessage(sender, "&aCompass target location set to your current position.");
            return true;
        }

        if (sub.equals("setclaimlocation") && sender instanceof Player p) {
            Location loc = p.getLocation();
            config.set("claim-location.world", loc.getWorld().getName());
            config.set("claim-location.x", loc.getX());
            config.set("claim-location.y", loc.getY());
            config.set("claim-location.z", loc.getZ());
            config.set("claim-location.yaw", loc.getYaw());
            config.set("claim-location.pitch", loc.getPitch());
            saveConfig();
            TextUtil.sendMessage(sender, "&aClaim teleport location set to your current position.");
            return true;
        }

        if (sub.equals("resetclaim")) {
            config.set("claimed", false);
            saveConfig();
            TextUtil.sendMessage(sender, "&aClaim status reset. The treasure can be claimed again.");
            return true;
        }
        
        if (sub.equals("resetchest")) {
            config.set("chest-location", null);
            saveConfig();
            TextUtil.sendMessage(sender, "&aVerification chest has been reset.");
            return true;
        }
        
        if (sub.equals("resetlocation")) {
            config.set("final-location", null);
            saveConfig();
            TextUtil.sendMessage(sender, "&aFinal teleport location has been reset.");
            return true;
        }

        if (sub.equals("setchest") && sender instanceof Player p) {
            org.bukkit.block.Block targetBlock = p.getTargetBlockExact(5);
            if (targetBlock == null || targetBlock.getType() == Material.AIR) {
                TextUtil.sendMessage(p, "&cYou must be looking at a block (like a chest).");
                return true;
            }
            Location loc = targetBlock.getLocation();
            config.set("chest-location.world", loc.getWorld().getName());
            config.set("chest-location.x", loc.getBlockX());
            config.set("chest-location.y", loc.getBlockY());
            config.set("chest-location.z", loc.getBlockZ());
            saveConfig();
            TextUtil.sendMessage(p, "&aVerification chest bound to the block you are looking at!");
            return true;
        }

        return true;
    }

    private ItemStack createToken(int clueNum) {
        ItemStack item;
        if (clueNum == 10) {
            item = new ItemStack(Material.COMPASS);
        } else {
            item = new ItemStack(Material.PAPER);
        }
        
        ItemMeta meta = item.getItemMeta();
        String name = config.getString("clues." + clueNum + ".token-name", "&6&lClue #" + clueNum);
        meta.displayName(TextUtil.colorize(name));
        
        List<String> lore = config.getStringList("clues." + clueNum + ".token-lore");
        if (lore == null || lore.isEmpty()) {
            lore = new ArrayList<>();
            lore.add("&7A mysterious clue from the Captain's voyage.");
            lore.add("&eRight-click to read the story.");
            lore.add("");
            lore.add("&8Keep this safe for the final verification!");
        }
        
        List<Component> compLore = new ArrayList<>();
        for (String l : lore) compLore.add(TextUtil.colorize(l));
        meta.lore(compLore);
        
        meta.getPersistentDataContainer().set(clueKey, PersistentDataType.INTEGER, clueNum);
        
        if (clueNum == 10 && meta instanceof org.bukkit.inventory.meta.CompassMeta) {
            org.bukkit.inventory.meta.CompassMeta cm = (org.bukkit.inventory.meta.CompassMeta) meta;
            String w = config.getString("final-location.world");
            if (w != null) {
                double x = config.getDouble("final-location.x");
                double y = config.getDouble("final-location.y");
                double z = config.getDouble("final-location.z");
                Location loc = new Location(Bukkit.getWorld(w), x, y, z);
                cm.setLodestone(loc);
                cm.setLodestoneTracked(false);
            }
        }
        
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            org.bukkit.block.Block clicked = e.getClickedBlock();
            if (clicked != null && config.contains("chest-location.world")) {
                String w = config.getString("chest-location.world");
                int x = config.getInt("chest-location.x");
                int y = config.getInt("chest-location.y");
                int z = config.getInt("chest-location.z");
                if (clicked.getWorld().getName().equals(w) && clicked.getX() == x && clicked.getY() == y && clicked.getZ() == z) {
                    e.setCancelled(true);
                    openVerifyGui(e.getPlayer());
                    return;
                }
            }
        }

        if (e.getAction() == Action.RIGHT_CLICK_AIR || e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            ItemStack item = e.getItem();
            if (item == null || !item.hasItemMeta()) return;
            
            Integer clueNum = item.getItemMeta().getPersistentDataContainer().get(clueKey, PersistentDataType.INTEGER);
            if (clueNum != null) {
                e.setCancelled(true);
                openVirtualBook(e.getPlayer(), clueNum);
            }
        }
    }

    private void openVirtualBook(Player p, int clueNum) {
        ConfigurationSection clueSec = config.getConfigurationSection("clues." + clueNum);
        if (clueSec == null) {
            TextUtil.sendMessage(p, "&cThis clue seems to be corrupted or missing.");
            return;
        }
        
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        meta.title(TextUtil.colorize(clueSec.getString("title", "Unknown")));
        meta.author(TextUtil.colorize(clueSec.getString("author", "Unknown")));
        
        List<String> pages = clueSec.getStringList("pages");
        for (String page : pages) {
            // Use legacy colorize for book pages to parse ampersand codes correctly
            meta.addPage(TextUtil.legacyColorize(page.replace("\\n", "\n")));
        }
        book.setItemMeta(meta);
        
        p.openBook(book);
    }

    private void openVerifyGui(Player p) {
        Gui gui = new Gui("&8Treasure Verification", 4);
        gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

        List<Integer> reqClues = config.getIntegerList("required-clues");
        boolean hasAll = true;

        int[] slots = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 23, 24, 25};
        int slotIdx = 0;

        for (int clue : reqClues) {
            if (slotIdx >= slots.length) break;
            int s = slots[slotIdx++];
            
            boolean has = hasClueInInventory(p, clue);
            if (!has) hasAll = false;
            
            String name = config.getString("clues." + clue + ".token-name", "Clue #" + clue);
            String plainName = org.bukkit.ChatColor.stripColor(org.bukkit.ChatColor.translateAlternateColorCodes('&', name));
            if (has) {
                gui.setItem(s, GuiButton.of(Material.LIME_STAINED_GLASS_PANE, "&a" + plainName + " Found", List.of("&7You have this clue in your inventory."), ev -> {}));
            } else {
                gui.setItem(s, GuiButton.of(Material.RED_STAINED_GLASS_PANE, "&c" + plainName + " Missing", List.of("&7You need to find this clue!"), ev -> {}));
            }
        }

        final boolean finalHasAll = hasAll;
        gui.setItem(22, GuiButton.of(Material.BEACON, "&b&lClaim Grand Booty", List.of(
            hasAll ? "&aYou have all clues! Click to claim!" : "&cYou are missing some clues."
        ), e -> {
            e.setCancelled(true);
            if (finalHasAll) {
                claimBooty(p, reqClues);
            } else {
                TextUtil.sendMessage(p, "&cYou don't have all the required clues yet!");
            }
        }));

        gui.open(p);
    }

    private boolean hasClueInInventory(Player p, int clueNum) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (item == null || !item.hasItemMeta()) continue;
            Integer val = item.getItemMeta().getPersistentDataContainer().get(clueKey, PersistentDataType.INTEGER);
            if (val != null && val == clueNum) {
                return true;
            }
        }
        return false;
    }

    private void claimBooty(Player p, List<Integer> reqClues) {
        if (config.getBoolean("claimed", false)) {
            TextUtil.sendMessage(p, "&cThe Grand Booty has already been claimed by another pirate!");
            p.closeInventory();
            return;
        }

        // Take tokens
        for (int clue : reqClues) {
            for (ItemStack item : p.getInventory().getContents()) {
                if (item == null || !item.hasItemMeta()) continue;
                Integer val = item.getItemMeta().getPersistentDataContainer().get(clueKey, PersistentDataType.INTEGER);
                if (val != null && val == clue) {
                    item.setAmount(item.getAmount() - 1);
                    break; // Take 1 per clue
                }
            }
        }

        config.set("claimed", true);
        saveConfig();

        p.closeInventory();

        String w = config.getString("claim-location.world");
        if (w == null) {
            // Fallback to final-location for backwards compatibility if claim-location isn't set yet
            w = config.getString("final-location.world");
            if (w == null) {
                TextUtil.sendMessage(p, "&cAdmin error: Claim location not set!");
                return;
            }
            double x = config.getDouble("final-location.x");
            double y = config.getDouble("final-location.y");
            double z = config.getDouble("final-location.z");
            float yaw = (float) config.getDouble("final-location.yaw");
            float pitch = (float) config.getDouble("final-location.pitch");
            Location loc = new Location(Bukkit.getWorld(w), x, y, z, yaw, pitch);
            p.teleport(loc);
        } else {
            double x = config.getDouble("claim-location.x");
            double y = config.getDouble("claim-location.y");
            double z = config.getDouble("claim-location.z");
            float yaw = (float) config.getDouble("claim-location.yaw");
            float pitch = (float) config.getDouble("claim-location.pitch");
            Location loc = new Location(Bukkit.getWorld(w), x, y, z, yaw, pitch);
            p.teleport(loc);
        }
        


        Bukkit.broadcast(TextUtil.colorize("&e&lTREASURE HUNT &8? &b" + p.getName() + " &7has found all the clues and claimed the &eGrand Booty!"));
    }
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> completions = new ArrayList<>();
            String[] subs = {"give", "verify", "setlocation", "setclaimlocation", "setchest", "resetclaim", "resetchest", "resetlocation"};
            for (String sub : subs) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    completions.add(sub);
                }
            }
            return completions;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return null; // Suggest player names
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            List<String> completions = new ArrayList<>();
            for (int i = 2; i <= 10; i++) {
                if (String.valueOf(i).startsWith(args[2])) completions.add(String.valueOf(i));
            }
            return completions;
        }
        return new ArrayList<>();
    }
}
