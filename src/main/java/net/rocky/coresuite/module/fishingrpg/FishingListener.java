package net.rocky.coresuite.module.fishingrpg;

import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Core fishing event listener for the FishingRPG module.
 * Intercepts PlayerFishEvent, runs the LootEngine, handles combos,
 * and distributes catches, XP, and legendary compass drops.
 */
public class FishingListener implements Listener {

    private final FishingRPGModule module;

    public FishingListener(FishingRPGModule module) {
        this.module = module;
    }

    @EventHandler
    public void onPlayerFish(PlayerFishEvent event) {
        Player player = event.getPlayer();

        if (event.getState() == PlayerFishEvent.State.FISHING) {
            // Player casts the line — decrement bait charge
            module.getBaitManager().consumeCast(player.getUniqueId(), 0.0);
            return;
        }

        if (event.getState() == PlayerFishEvent.State.BITE) {
            // Fish bites the hook — record splash time for combo
            module.getComboTracker().onBobberSplash(player.getUniqueId());
            return;
        }

        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            handleCaughtFish(event, player);
        }
    }

        private void handleCaughtFish(PlayerFishEvent event, Player player) {
        // Stop vanilla item drop but allow rod retracting!
        if (event.getCaught() != null) {
            event.getCaught().remove();
        }
        event.setExpToDrop(0); // we handle XP ourselves

        AnglerProfile profile = module.getProfileManager().getProfile(player.getUniqueId());
        if (profile == null) return;

        // Gather environment data
        String biome = player.getLocation().getBlock().getBiome().name();
        boolean isThundering = player.getWorld().isThundering();
        long time = player.getWorld().getTime();
        boolean isNight = time >= 13000 && time < 23000;

        // Hotspot & bait state
        boolean inHotspot = module.getHotspotManager().isInHotspot(player.getLocation());
        boolean hasBait = false;
        double baitCatchBonus = 0.0;
        String baitRarityBias = null;
        
        BaitManager.ActiveBait activeBait = module.getBaitManager().getActiveBait(player.getUniqueId());
        if (activeBait != null && activeBait.castsRemaining > 0) {
            hasBait = true;
            baitCatchBonus = activeBait.catchBonus;
            baitRarityBias = activeBait.rarityBias;
        }

        String prefix = module.getConfig().getString("prefix", "&8[&b&lFishing&8] &r");

        // Evaluate combo quality
        ComboTracker.CatchQuality comboQuality = module.getComboTracker().onReel(player.getUniqueId());

        // Roll the catch via LootEngine
        LootEngine.CatchResult result = module.getLootEngine().rollCatch(
                player, profile, biome, isThundering, isNight,
                inHotspot, hasBait, baitCatchBonus, baitRarityBias
        );
        if (result == null) return;

        int xpAmount = 0;

        switch (result.type()) {
            case JUNK -> {
                TextUtil.sendMessage(player, prefix + "&7You fished up some junk...");
                player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 1.0f);
                giveItem(player, getRandomJunkItem());
                xpAmount = 1;
            }
            case VANILLA_TREASURE -> {
                if (result.treasureItem() != null) {
                    String itemName = result.treasureItem().getType().name().replace('_', ' ').toLowerCase();
                    TextUtil.sendMessage(player, prefix + "&6★ Treasure! &fYou found a " + itemName + "!");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
                    giveItem(player, result.treasureItem());
                }
                xpAmount = 25;
            }
            case FISH, CORRUPTED_FISH -> {
                FishSpecies species = result.species();
                if (species == null) return;

                boolean corrupted = result.type() == LootEngine.CatchType.CORRUPTED_FISH;
                ItemStack fishItem = FishItemBuilder.buildFishItem(
                        species, result.weight(), result.price(), corrupted, module.getPlugin()
                );
                giveItem(player, fishItem);

                // Format catch message
                String rarityColor = species.getRarity().getColorCode();
                String weightStr = TextUtil.formatNumber(result.weight());
                String priceStr = TextUtil.formatNumber(result.price());
                String catchMsg = prefix + "&7You caught " + rarityColor + species.getName()
                        + " &7(" + weightStr + " kg) — &e$" + priceStr;
                if (corrupted) {
                    catchMsg = prefix + "&4☠ Corrupted Catch! " + rarityColor + species.getName()
                            + " &7(" + weightStr + " kg)";
                }
                TextUtil.sendMessage(player, catchMsg);

                // Sound based on rarity
                Sound soundToPlay = Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
                int rarityOrdinal = species.getRarity().ordinal();
                if (rarityOrdinal >= 4) { // Legendary+
                    soundToPlay = Sound.UI_TOAST_CHALLENGE_COMPLETE;
                } else if (rarityOrdinal >= 2) { // Rare+
                    soundToPlay = Sound.ENTITY_PLAYER_LEVELUP;
                }
                player.playSound(player.getLocation(), soundToPlay, 1.0f, 1.0f);

                // XP based on rarity
                int baseXp = module.getConfig().getInt("leveling.xp-per-catch", 5);
                int rarityBonus = module.getConfig().getInt("leveling.xp-rarity-bonus", 10);
                xpAmount = baseXp + (rarityOrdinal * rarityBonus);
            }
        }

        // Combo bonus XP
        if (comboQuality == ComboTracker.CatchQuality.PERFECT) {
            xpAmount += module.getConfig().getInt("leveling.perfect-catch-xp-bonus", 10);
        }

        // Apply XP and check level up
        int oldLevel = profile.getLevel();
        profile.addXP(xpAmount);
        profile.setTotalCatches(profile.getTotalCatches() + 1);

        if (profile.getLevel() > oldLevel) {
            TextUtil.sendMessage(player, prefix + "&a&l⬆ LEVEL UP! &7You are now Angler Level &b" + profile.getLevel() + "&7! &e+1 Skill Point");
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 0.5f);
            player.spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 2, 0), 20, 0.5, 0.5, 0.5, 0.1);
        }

        // Combo action bar feedback
        int combo = module.getComboTracker().getCombo(player.getUniqueId());
        if (comboQuality == ComboTracker.CatchQuality.PERFECT) {
            TextUtil.sendActionBar(player, "&a⚡ Perfect Catch! &7Combo: &bx" + combo);
        } else if (comboQuality == ComboTracker.CatchQuality.GOOD) {
            TextUtil.sendActionBar(player, "&e✓ Good! &7Combo maintained: &bx" + combo);
        } else {
            TextUtil.sendActionBar(player, "&7Combo reset.");
        }

        // Legendary Compass drop check
        double compassChance = module.getConfig().getDouble("legendary-quest.map-drop-chance", 0.00002);
        int pityThreshold = module.getConfig().getInt("legendary-quest.pity-threshold", 2500);
        profile.setLegendaryPity(profile.getLegendaryPity() + 1);

        if (LootEngine.rollLegendaryCompass(profile, compassChance, pityThreshold)) {
            profile.setLegendaryPity(0); // Reset pity counter
            Location target = generateCompassTarget(player);
            ItemStack compassItem = FishItemBuilder.buildCompassItem(player, target, module.getPlugin());
            giveItem(player, compassItem);

            TextUtil.sendMessage(player, prefix + "&d&l✦ &5You feel an ancient pull... A &d&lLegendary Compass &5materializes in your hands!");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.5f);

            // Broadcast to all players
            for (Player online : Bukkit.getOnlinePlayers()) {
                TextUtil.sendMessage(online, prefix + "&d&l" + player.getName() + " &5has found a &d&lLegendary Fisherman's Compass&5!");
            }
        }
    }

    private Location generateCompassTarget(Player player) {
        int range = module.getConfig().getInt("legendary-quest.compass-range-blocks", 5000);
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double x = player.getLocation().getX() + random.nextInt(-range, range);
        double z = player.getLocation().getZ() + random.nextInt(-range, range);
        return new Location(player.getWorld(), x, 64, z);
    }

    private void giveItem(Player player, ItemStack item) {
        if (item == null) return;
        if (player.getInventory().firstEmpty() != -1) {
            player.getInventory().addItem(item);
        } else {
            Item dropped = player.getWorld().dropItem(player.getLocation(), item);
            dropped.setPickupDelay(0);
        }
    }

    private ItemStack getRandomJunkItem() {
        Material[] junk = {Material.LILY_PAD, Material.STICK, Material.LEATHER_BOOTS, Material.BONE, Material.STRING, Material.KELP};
        return new ItemStack(junk[ThreadLocalRandom.current().nextInt(junk.length)]);
    }
}
