package net.rocky.coresuite.module.fishingrpg;

import net.kyori.adventure.text.Component;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class SkillTreeGUI implements Listener {

    private final FishingRPGModule module;
    private final Set<UUID> openViewers = new HashSet<>();

    public SkillTreeGUI(FishingRPGModule module) {
        this.module = module;
    }

    public void open(Player player) {
        // Assuming AnglerProfile is a valid class fetched via ProfileManager
        AnglerProfile profile = module.getProfileManager().getProfile(player.getUniqueId());
        if (profile == null) return;

        Component title = TextUtil.colorize("&b&lAngler's Skill Tree");
        Inventory inv = Bukkit.createInventory(null, 54, title);

        
        // Fill background
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        org.bukkit.inventory.meta.ItemMeta paneMeta = pane.getItemMeta();
        if (paneMeta != null) {
            paneMeta.setDisplayName(" ");
            pane.setItemMeta(paneMeta);
        }
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, pane);
        }


        // Slot 4: Player head
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta headMeta = (SkullMeta) head.getItemMeta();
        if (headMeta != null) {
            headMeta.setOwningPlayer(player);
            headMeta.displayName(TextUtil.colorize("&b&l" + player.getName() + "'s Skills"));
            List<Component> headLore = new ArrayList<>();
            headLore.add(TextUtil.colorize("&7Level: &e" + profile.getLevel()));
            headLore.add(TextUtil.colorize("&7Available Skill Points: &e" + profile.getSkillPointsAvailable()));
            headLore.add(TextUtil.colorize("&7Total Catches: &e" + profile.getTotalCatches()));
            headMeta.lore(headLore);
            head.setItemMeta(headMeta);
        }
        inv.setItem(4, head);

        int maxHunter = module.getConfig().getInt("skill-tree.hunter.max-points", 10);
        int maxScavenger = module.getConfig().getInt("skill-tree.scavenger.max-points", 10);
        int maxMystic = module.getConfig().getInt("skill-tree.mystic.max-points", 10);

        // Slot 19: Path of the Hunter
        int hunterAmount = Math.max(1, Math.min(64, profile.getHunterPoints()));
        ItemStack hunterItem = new ItemStack(Material.IRON_SWORD, hunterAmount);
        ItemMeta hunterMeta = hunterItem.getItemMeta();
        if (hunterMeta != null) {
            hunterMeta.displayName(TextUtil.colorize("&c⚔ Path of the Hunter"));
            List<Component> hunterLore = new ArrayList<>();
            hunterLore.add(TextUtil.colorize("&7Points: &e" + profile.getHunterPoints() + "/" + maxHunter));
            hunterLore.add(TextUtil.colorize("&7Fishing Power: &a+" + (profile.getHunterPoints() * 5) + "%"));
            hunterLore.add(TextUtil.colorize("&7Increases your chance to catch heavier, rarer fish."));
            hunterLore.add(Component.empty());
            if (profile.getSkillPointsAvailable() > 0 && profile.getHunterPoints() < maxHunter) {
                hunterLore.add(TextUtil.colorize("&eClick to invest 1 point"));
            } else {
                hunterLore.add(TextUtil.colorize("&c✗ No skill points available"));
            }
            hunterMeta.lore(hunterLore);
            hunterItem.setItemMeta(hunterMeta);
        }
        inv.setItem(19, hunterItem);

        // Slot 22: Path of the Scavenger
        int scavengerAmount = Math.max(1, Math.min(64, profile.getScavengerPoints()));
        ItemStack scavengerItem = new ItemStack(Material.EMERALD, scavengerAmount);
        ItemMeta scavengerMeta = scavengerItem.getItemMeta();
        if (scavengerMeta != null) {
            scavengerMeta.displayName(TextUtil.colorize("&e☘ Path of the Scavenger"));
            List<Component> scavengerLore = new ArrayList<>();
            scavengerLore.add(TextUtil.colorize("&7Points: &e" + profile.getScavengerPoints() + "/" + maxScavenger));
            scavengerLore.add(TextUtil.colorize("&7Fishing Luck: &a+" + (profile.getScavengerPoints() * 5) + "%"));
            scavengerLore.add(TextUtil.colorize("&7Increases your chance to find treasures."));
            scavengerLore.add(Component.empty());
            if (profile.getSkillPointsAvailable() > 0 && profile.getScavengerPoints() < maxScavenger) {
                scavengerLore.add(TextUtil.colorize("&eClick to invest 1 point"));
            } else {
                scavengerLore.add(TextUtil.colorize("&c✗ No skill points available"));
            }
            scavengerMeta.lore(scavengerLore);
            scavengerItem.setItemMeta(scavengerMeta);
        }
        inv.setItem(22, scavengerItem);

        // Slot 25: Path of the Mystic
        int mysticAmount = Math.max(1, Math.min(64, profile.getMysticPoints()));
        ItemStack mysticItem = new ItemStack(Material.ENDER_EYE, mysticAmount);
        ItemMeta mysticMeta = mysticItem.getItemMeta();
        if (mysticMeta != null) {
            mysticMeta.displayName(TextUtil.colorize("&d✦ Path of the Mystic"));
            List<Component> mysticLore = new ArrayList<>();
            mysticLore.add(TextUtil.colorize("&7Points: &e" + profile.getMysticPoints() + "/" + maxMystic));
            mysticLore.add(TextUtil.colorize("&7Bait Saver: &a+" + (profile.getMysticPoints() * 5) + "%"));
            mysticLore.add(TextUtil.colorize("&7Increases your chance to not consume bait."));
            mysticLore.add(Component.empty());
            if (profile.getSkillPointsAvailable() > 0 && profile.getMysticPoints() < maxMystic) {
                mysticLore.add(TextUtil.colorize("&eClick to invest 1 point"));
            } else {
                mysticLore.add(TextUtil.colorize("&c✗ No skill points available"));
            }
            mysticMeta.lore(mysticLore);
            mysticItem.setItemMeta(mysticMeta);
        }
        inv.setItem(25, mysticItem);

        // Progress indicators below
        ItemStack greenPane = TextUtil.createItem(Material.LIME_STAINED_GLASS_PANE, 1, "&aUnlocked");
        ItemStack grayPane = TextUtil.createItem(Material.GRAY_STAINED_GLASS_PANE, 1, "&7Locked");
        
        inv.setItem(28, profile.getHunterPoints() > 0 ? greenPane : grayPane);
        inv.setItem(31, profile.getScavengerPoints() > 0 ? greenPane : grayPane);
        inv.setItem(34, profile.getMysticPoints() > 0 ? greenPane : grayPane);

        // Slot 49: Close
        inv.setItem(49, TextUtil.createItem(Material.BARRIER, 1, "&c✗ Close"));

        openViewers.add(player.getUniqueId());
        player.openInventory(inv);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!openViewers.contains(player.getUniqueId())) return;

        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory().equals(player.getInventory())) return;

        AnglerProfile profile = module.getProfileManager().getProfile(player.getUniqueId());
        if (profile == null) return;

        int slot = event.getSlot();
        boolean purchased = false;

        if (slot == 19) {
            int max = module.getConfig().getInt("skill-tree.hunter.max-points", 10);
            if (profile.getSkillPointsAvailable() > 0 && profile.getHunterPoints() < max) {
                profile.setHunterPoints(profile.getHunterPoints() + 1);
                profile.setSkillPointsAvailable(profile.getSkillPointsAvailable() - 1);
                purchased = true;
            }
        } else if (slot == 22) {
            int max = module.getConfig().getInt("skill-tree.scavenger.max-points", 10);
            if (profile.getSkillPointsAvailable() > 0 && profile.getScavengerPoints() < max) {
                profile.setScavengerPoints(profile.getScavengerPoints() + 1);
                profile.setSkillPointsAvailable(profile.getSkillPointsAvailable() - 1);
                purchased = true;
            }
        } else if (slot == 25) {
            int max = module.getConfig().getInt("skill-tree.mystic.max-points", 10);
            if (profile.getSkillPointsAvailable() > 0 && profile.getMysticPoints() < max) {
                profile.setMysticPoints(profile.getMysticPoints() + 1);
                profile.setSkillPointsAvailable(profile.getSkillPointsAvailable() - 1);
                purchased = true;
            }
        } else if (slot == 49) {
            player.closeInventory();
            return;
        }

        if (purchased) {
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
            // Reopen GUI to refresh stats
            open(player);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        openViewers.remove(event.getPlayer().getUniqueId());
    }
}
