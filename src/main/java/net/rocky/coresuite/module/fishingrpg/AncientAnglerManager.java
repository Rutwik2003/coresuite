package net.rocky.coresuite.module.fishingrpg;

import net.kyori.adventure.text.Component;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.entity.Drowned;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class AncientAnglerManager implements Listener {
    private final CoreSuitePlugin plugin;
    private final FishingRPGModule module;
    private final FileConfiguration config;

    private final Map<UUID, Integer> questStages = new ConcurrentHashMap<>();
    private final Map<UUID, String> chatState = new ConcurrentHashMap<>();
    private final Set<UUID> inDialogueSequence = ConcurrentHashMap.newKeySet();
    private final Map<UUID, List<Integer>> introOptions = new ConcurrentHashMap<>();
    private final Map<UUID, Entity> spawnedNPCs = new ConcurrentHashMap<>();

    private final NamespacedKey compassKey;
    private final NamespacedKey trackerKey;
    private final NamespacedKey legendaryKey;
    private final NamespacedKey lodeWKey;
    private final NamespacedKey lodeXKey;
    private final NamespacedKey lodeYKey;
    private final NamespacedKey lodeZKey;
    private final NamespacedKey fishSpeciesKey;

    public AncientAnglerManager(CoreSuitePlugin plugin, FishingRPGModule module, FileConfiguration config) {
        this.plugin = plugin;
        this.module = module;
        this.config = config;

        this.compassKey = new NamespacedKey(plugin, "legendary_compass");
        this.trackerKey = new NamespacedKey(plugin, "legendary_tracker");
        this.legendaryKey = new NamespacedKey(plugin, "legendary_fisherman");
        this.lodeWKey = new NamespacedKey(plugin, "lode_w");
        this.lodeXKey = new NamespacedKey(plugin, "lode_x");
        this.lodeYKey = new NamespacedKey(plugin, "lode_y");
        this.lodeZKey = new NamespacedKey(plugin, "lode_z");
        this.fishSpeciesKey = new NamespacedKey(plugin, "fish_species");

        initDatabase();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private void initDatabase() {
        plugin.getDatabaseManager().execute(
            "CREATE TABLE IF NOT EXISTS fishingrpg_quest_progress (" +
            "uuid TEXT PRIMARY KEY, " +
            "stage INTEGER DEFAULT 0" +
            ")"
        );
    }

    public void giveCompass(Player player) {
        ItemStack compass = new ItemStack(Material.RECOVERY_COMPASS);
        ItemMeta meta = compass.getItemMeta();
        if (meta != null) {
            meta.displayName(TextUtil.colorize("&6&lLegendary Fisherman's Compass"));
            List<Component> lore = new ArrayList<>();
            lore.add(TextUtil.colorize("&7It vibrates slightly in your hands..."));
            lore.add(TextUtil.colorize("&7Perhaps if you follow its pull"));
            lore.add(TextUtil.colorize("&7and &cdrop&7 it where the energy is strongest..."));
            meta.lore(lore);

            PersistentDataContainer pdc = meta.getPersistentDataContainer();
            pdc.set(compassKey, PersistentDataType.STRING, player.getUniqueId().toString());

            // Set random target within 5000 blocks
            Location current = player.getLocation();
            int x = current.getBlockX() + ThreadLocalRandom.current().nextInt(-5000, 5000);
            int z = current.getBlockZ() + ThreadLocalRandom.current().nextInt(-5000, 5000);
            int y = current.getWorld().getHighestBlockYAt(x, z);

            pdc.set(lodeWKey, PersistentDataType.STRING, current.getWorld().getName());
            pdc.set(lodeXKey, PersistentDataType.INTEGER, x);
            pdc.set(lodeYKey, PersistentDataType.INTEGER, y);
            pdc.set(lodeZKey, PersistentDataType.INTEGER, z);

            compass.setItemMeta(meta);
        }
        player.getInventory().addItem(compass);
        TextUtil.sendMessage(player, "&6&l[!] &7A strange compass materializes in your pack... It points to an unknown destination.");
        player.playSound(player.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1.0f, 0.5f);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemDrop().getItemStack();
        if (!item.hasItemMeta()) return;

        PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
        boolean isCompass = pdc.has(compassKey, PersistentDataType.STRING);
        boolean isTracker = pdc.has(trackerKey, PersistentDataType.STRING);

        if (isCompass || isTracker) {
            event.setCancelled(true);
            
            String ownerUUIDStr = isCompass ? pdc.get(compassKey, PersistentDataType.STRING) : pdc.get(trackerKey, PersistentDataType.STRING);
            if (!player.getUniqueId().toString().equals(ownerUUIDStr)) {
                TextUtil.sendMessage(player, "&cThis ancient device rejects your touch.");
                return;
            }

            String targetWorld = pdc.get(lodeWKey, PersistentDataType.STRING);
            if (targetWorld == null || !targetWorld.equals(player.getWorld().getName())) {
                TextUtil.sendMessage(player, "&cThe compass spins wildly. The destination is in another realm.");
                return;
            }

            int targetX = pdc.getOrDefault(lodeXKey, PersistentDataType.INTEGER, 0);
            int targetY = pdc.getOrDefault(lodeYKey, PersistentDataType.INTEGER, 0);
            int targetZ = pdc.getOrDefault(lodeZKey, PersistentDataType.INTEGER, 0);
            Location target = new Location(player.getWorld(), targetX, targetY, targetZ);

            double distance = player.getLocation().distance(target);

            if (distance < 15.0) {
                performRitual(player, isCompass, item);
            } else {
                TextUtil.sendMessage(player, "&7The compass pulses... You are &e" + (int)distance + " &7blocks away from the anomaly.");
            }
        }
    }

    private void performRitual(Player player, boolean firstTime, ItemStack item) {
        if (inDialogueSequence.contains(player.getUniqueId())) return;
        inDialogueSequence.add(player.getUniqueId());

        if (firstTime) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.displayName(TextUtil.colorize("&b&lAngler's Tracker"));
                PersistentDataContainer pdc = meta.getPersistentDataContainer();
                pdc.remove(compassKey);
                pdc.set(trackerKey, PersistentDataType.STRING, player.getUniqueId().toString());
                item.setItemMeta(meta);
                item.setType(Material.COMPASS);
            }
        }

        Location loc = player.getLocation();
        
        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (ticks == 0) {
                    player.playSound(loc, Sound.BLOCK_WATER_AMBIENT, 1f, 1f);
                    player.playSound(loc, Sound.ENTITY_WARDEN_HEARTBEAT, 1f, 1f);
                }
                
                if (ticks < 40) {
                    loc.getWorld().spawnParticle(Particle.NAUTILUS, loc, 5, 2, 2, 2, 0.1);
                    loc.getWorld().spawnParticle(Particle.SPLASH, loc, 10, 2, 2, 2, 0.1);
                }
                
                if (ticks == 40) {
                    loc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 30, 1, 1, 1, 0.05);
                    player.playSound(loc, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 0.8f);
                    
                    Location spawnLoc = loc.clone().add(1, 0, 0);
                    if (spawnLoc.getBlock().getType() == Material.WATER) {
                        spawnLoc.getBlock().setType(Material.LILY_PAD);
                        spawnLoc.add(0, 1, 0);
                    }

                    Villager npc = loc.getWorld().spawn(spawnLoc, Villager.class, v -> {
                        v.customName(TextUtil.colorize("&e&lAncient Angler"));
                        v.setCustomNameVisible(true);
                        v.setAI(false);
                        v.setInvulnerable(true);
                        v.setGlowing(true);
                        v.setSilent(true);
                        v.setProfession(Villager.Profession.FISHERMAN);
                        v.setVillagerType(Villager.Type.SWAMP);
                        v.getPersistentDataContainer().set(legendaryKey, PersistentDataType.STRING, player.getUniqueId().toString());
                    });
                    
                    if (spawnedNPCs.containsKey(player.getUniqueId())) {
                        Entity oldNpc = spawnedNPCs.remove(player.getUniqueId());
                        if (oldNpc != null && oldNpc.isValid()) oldNpc.remove();
                    }
                    spawnedNPCs.put(player.getUniqueId(), npc);
                    
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (npc.isValid()) {
                                npc.remove();
                                spawnedNPCs.remove(player.getUniqueId());
                            }
                        }
                    }.runTaskLater(plugin, 300 * 20L);
                }
                
                if (ticks == 80) {
                    inDialogueSequence.remove(player.getUniqueId());
                    if (firstTime && getStage(player.getUniqueId()) == 0) {
                        startIntroDialogue(player);
                    } else {
                        handleNPCInteract(player);
                    }
                    cancel();
                }
                ticks += 5;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void startIntroDialogue(Player player) {
        TextUtil.sendMessage(player, "&e&lAncient Angler: &7You followed the currents, seeker... I am the Ancient Angler.");
        TextUtil.sendMessage(player, "&7My time in these waters is ending. I seek an heir. But the sea tests all.");
        
        List<Integer> options = new ArrayList<>();
        while(options.size() < 3) {
            int rand = ThreadLocalRandom.current().nextInt(1, 11);
            if(!options.contains(rand)) options.add(rand);
        }
        introOptions.put(player.getUniqueId(), options);
        chatState.put(player.getUniqueId(), "awaiting_intro");
        
        TextUtil.sendMessage(player, "&7Choose a path, but choose wisely. Speak a number in chat:");
        TextUtil.sendMessage(player, "&b1. " + getPathName(options.get(0)));
        TextUtil.sendMessage(player, "&b2. " + getPathName(options.get(1)));
        TextUtil.sendMessage(player, "&b3. " + getPathName(options.get(2)));
    }

    private String getPathName(int id) {
        switch(id) {
            case 1: return "The Path of Endurance";
            case 2: return "The Path of Wealth";
            case 3: return "The Path of the Hunter";
            case 4: return "The Path of Silence";
            case 5: return "The Path of Discovery";
            case 6: return "The Path of Greed";
            case 7: return "The Path of Hubris";
            case 8: return "The Path of the Fool";
            case 9: return "The Path of Wrath";
            case 10: return "The Path of Despair";
            default: return "Unknown Path";
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        String state = chatState.get(player.getUniqueId());
        if (state == null) return;
        
        event.setCancelled(true);
        String msg = event.getMessage().trim();
        
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (state.equals("awaiting_intro")) {
                handleIntroSelection(player, msg);
            } else if (state.startsWith("turnin_")) {
                handleTurnInSelection(player, msg, state);
            }
        });
    }

    private void handleIntroSelection(Player player, String msg) {
        int choice;
        try {
            choice = Integer.parseInt(msg);
        } catch (NumberFormatException e) {
            TextUtil.sendMessage(player, "&cType 1, 2, or 3 to choose a path.");
            return;
        }

        if (choice < 1 || choice > 3) {
            TextUtil.sendMessage(player, "&cType 1, 2, or 3 to choose a path.");
            return;
        }

        List<Integer> options = introOptions.get(player.getUniqueId());
        if (options == null || options.size() < 3) return;

        int selectedPathId = options.get(choice - 1);
        chatState.remove(player.getUniqueId());
        introOptions.remove(player.getUniqueId());
        
        processPathSelection(player, selectedPathId);
    }

    private void processPathSelection(Player player, int pathId) {
        int nextStage = 0;
        switch (pathId) {
            case 1:
                TextUtil.sendMessage(player, "&e&lAncient Angler: &7Endurance... You shall cast until your arms break. Bring me 10 Neon Tetras.");
                nextStage = 101;
                break;
            case 2:
                TextUtil.sendMessage(player, "&e&lAncient Angler: &7Wealth. Prove your worth. Bring me 20 of any fish.");
                nextStage = 201;
                break;
            case 3:
                TextUtil.sendMessage(player, "&e&lAncient Angler: &7The Hunter... Bring me 5 Piranhas from the depths.");
                nextStage = 301;
                break;
            case 4:
                TextUtil.sendMessage(player, "&e&lAncient Angler: &7Silence... the quiet catch. 15 of any fish.");
                nextStage = 401;
                break;
            case 5:
                TextUtil.sendMessage(player, "&e&lAncient Angler: &7Discovery. Bring me 3 different rare fish.");
                nextStage = 501;
                break;
            case 6:
                TextUtil.sendMessage(player, "&c&lAncient Angler: &4Greed consumes you... The ocean demands a toll.");
                plugin.getEconomyManager().withdraw(player, 25000);
                TextUtil.sendMessage(player, "&cYou have lost $25,000.");
                nextStage = 0;
                break;
            case 7:
                TextUtil.sendMessage(player, "&c&lAncient Angler: &4Hubris will blind you.");
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 200, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 600, 1));
                nextStage = 0;
                break;
            case 8:
                TextUtil.sendMessage(player, "&c&lAncient Angler: &4The Fool loses what he already holds.");
                takeAnyCustomFish(player, 5);
                player.getWorld().strikeLightningEffect(player.getLocation());
                nextStage = 0;
                break;
            case 9:
                TextUtil.sendMessage(player, "&c&lAncient Angler: &4Wrath awakens the drowned!");
                plugin.getEconomyManager().withdraw(player, 10000);
                for(int i=0; i<3; i++) {
                    player.getWorld().spawn(player.getLocation(), Drowned.class);
                }
                nextStage = 0;
                break;
            case 10:
                TextUtil.sendMessage(player, "&c&lAncient Angler: &4Despair drowns the strongest swimmers.");
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 1200, 1));
                player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 1200, 1));
                plugin.getEconomyManager().withdraw(player, 15000);
                nextStage = 0;
                break;
        }

        if (nextStage != 0) {
            setStage(player.getUniqueId(), nextStage);
            TextUtil.sendMessage(player, "&7Your compass recalibrates... The NPC vanishes into the mist.");
        } else {
            setStage(player.getUniqueId(), 0);
        }

        Entity npc = spawnedNPCs.remove(player.getUniqueId());
        if (npc != null && npc.isValid()) {
            locRelocateNPC(player, npc);
            npc.remove();
        }
    }

    private void locRelocateNPC(Player player, Entity npc) {
        Location current = player.getLocation();
        int x = current.getBlockX() + ThreadLocalRandom.current().nextInt(-5000, 5000);
        int z = current.getBlockZ() + ThreadLocalRandom.current().nextInt(-5000, 5000);
        int y = current.getWorld().getHighestBlockYAt(x, z);

        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.hasItemMeta()) {
                PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
                if (pdc.has(trackerKey, PersistentDataType.STRING)) {
                    ItemMeta meta = item.getItemMeta();
                    pdc = meta.getPersistentDataContainer();
                    pdc.set(lodeWKey, PersistentDataType.STRING, current.getWorld().getName());
                    pdc.set(lodeXKey, PersistentDataType.INTEGER, x);
                    pdc.set(lodeYKey, PersistentDataType.INTEGER, y);
                    pdc.set(lodeZKey, PersistentDataType.INTEGER, z);
                    item.setItemMeta(meta);
                    break;
                }
            }
        }
    }

    @EventHandler
    public void onNPCInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        if (entity.getPersistentDataContainer().has(legendaryKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
            String ownerUUID = entity.getPersistentDataContainer().get(legendaryKey, PersistentDataType.STRING);
            if (!event.getPlayer().getUniqueId().toString().equals(ownerUUID)) {
                TextUtil.sendMessage(event.getPlayer(), "&cThe figure ignores you completely.");
                return;
            }
            handleNPCInteract(event.getPlayer());
        }
    }

    private void handleNPCInteract(Player player) {
        int stage = getStage(player.getUniqueId());
        if (stage == 0) {
            TextUtil.sendMessage(player, "&e&lAncient Angler: &7You have no active journey with me.");
            return;
        }
        
        chatState.put(player.getUniqueId(), "turnin_" + stage);
        TextUtil.sendMessage(player, "&e&lAncient Angler: &7Ah, you return. Do you have what I require?");
        TextUtil.sendMessage(player, "&b1. Submit offering");
        TextUtil.sendMessage(player, "&b2. Ask for a hint ($5000)");
        TextUtil.sendMessage(player, "&b3. Walk away");
    }

    private void handleTurnInSelection(Player player, String msg, String stateStr) {
        int choice;
        try {
            choice = Integer.parseInt(msg);
        } catch (NumberFormatException e) {
            TextUtil.sendMessage(player, "&cType 1, 2, or 3.");
            return;
        }

        if (choice < 1 || choice > 3) {
            TextUtil.sendMessage(player, "&cType 1, 2, or 3.");
            return;
        }

        int stage = Integer.parseInt(stateStr.split("_")[1]);

        if (choice == 3) {
            chatState.remove(player.getUniqueId());
            TextUtil.sendMessage(player, "&7You walk away silently.");
            return;
        }

        if (choice == 2) {
            if (plugin.getEconomyManager().has(player, 5000)) {
                plugin.getEconomyManager().withdraw(player, 5000);
                chatState.remove(player.getUniqueId());
                sendHint(player, stage);
            } else {
                TextUtil.sendMessage(player, "&cYou cannot afford the hint.");
            }
            return;
        }

        if (choice == 1) {
            chatState.remove(player.getUniqueId());
            attemptTurnIn(player, stage);
        }
    }

    private void sendHint(Player player, int stage) {
        String hint = "Unknown.";
        switch(stage) {
            case 101: hint = "I need 10 Neon Tetras."; break;
            case 102: hint = "Bring me a Char."; break;
            case 103: hint = "Fetch me a Cherry Salmon."; break;
            case 104: hint = "The final piece is a Lava Particle."; break;
            case 201: hint = "Any 20 fish will do."; break;
            case 202: hint = "Bring me a Golden Carp."; break;
            case 203: hint = "A Lava Particle is required."; break;
            case 301: hint = "I require 5 Piranhas."; break;
            case 302: hint = "Fetch me 3 Moray Eels."; break;
            case 303: hint = "A Spiderfish Queen is needed."; break;
            case 401: hint = "Any 15 fish."; break;
            case 402: hint = "An Anchovy."; break;
            case 403: hint = "A Suckerfish."; break;
            case 501: hint = "I need 3 different rare fish."; break;
            case 502: hint = "One epic fish."; break;
            case 503: hint = "A legendary fish."; break;
        }
        TextUtil.sendMessage(player, "&e&lAncient Angler: &7" + hint);
    }

    private void attemptTurnIn(Player player, int stage) {
        boolean success = false;
        int nextStage = 0;
        String successMsg = "&e&lAncient Angler: &7Acceptable. Now, for your next task...";
        Runnable rewardAction = null;

        switch (stage) {
            case 101: 
                if (takeFish(player, "neon_tetra", 10)) { success = true; nextStage = 102; successMsg = "&e&lAncient Angler: &7Good. Next, seek the Char."; }
                break;
            case 102:
                if (takeFish(player, "char", 1)) { success = true; nextStage = 103; successMsg = "&e&lAncient Angler: &7A fine specimen. Next, a Cherry Salmon."; }
                break;
            case 103:
                if (takeFish(player, "cherry_salmon", 1)) { success = true; nextStage = 104; successMsg = "&e&lAncient Angler: &7Excellent. Finally, bring me a Lava Particle from the scorching depths."; }
                break;
            case 104:
                if (takeFish(player, "lava_particle", 1)) { 
                    success = true; nextStage = 0; successMsg = "&e&lAncient Angler: &7You have proven your endurance! Claim your power.";
                    rewardAction = () -> TextUtil.sendMessage(player, "&a[!] You earned +1 Tireless Caster level! (Not fully implemented)");
                }
                break;
            case 201:
                if (countAnyCustomFish(player) >= 20) { takeAnyCustomFish(player, 20); success = true; nextStage = 202; successMsg = "&e&lAncient Angler: &7A start. Now, a Golden Carp."; }
                break;
            case 202:
                if (takeFish(player, "golden_carp", 1)) { success = true; nextStage = 203; successMsg = "&e&lAncient Angler: &7Gold attracts gold. Now, a Lava Particle."; }
                break;
            case 203:
                if (takeFish(player, "lava_particle", 1)) {
                    success = true; nextStage = 0; successMsg = "&e&lAncient Angler: &7Your wealth is unmatched!";
                    rewardAction = () -> { plugin.getEconomyManager().deposit(player, 500000); TextUtil.sendMessage(player, "&a[!] You earned $500,000!"); };
                }
                break;
            case 301:
                if (takeFish(player, "piranha", 5)) { success = true; nextStage = 302; successMsg = "&e&lAncient Angler: &7Vicious. Now, 3 Moray Eels."; }
                break;
            case 302:
                if (takeFish(player, "moray_eel", 3)) { success = true; nextStage = 303; successMsg = "&e&lAncient Angler: &7Slippery... Finally, the Spiderfish Queen."; }
                break;
            case 303:
                if (takeFish(player, "spiderfish_queen", 1)) {
                    success = true; nextStage = 0; successMsg = "&e&lAncient Angler: &7A true hunter!";
                    rewardAction = () -> TextUtil.sendMessage(player, "&a[!] You earned 3x Rare Baits! (Not fully implemented)");
                }
                break;
            case 401:
                if (countAnyCustomFish(player) >= 15) { takeAnyCustomFish(player, 15); success = true; nextStage = 402; successMsg = "&e&lAncient Angler: &7Quietly done. Bring an Anchovy."; }
                break;
            case 402:
                if (takeFish(player, "anchovy", 1)) { success = true; nextStage = 403; successMsg = "&e&lAncient Angler: &7Small, silent. Now, a Suckerfish."; }
                break;
            case 403:
                if (takeFish(player, "suckerfish", 1)) {
                    success = true; nextStage = 0; successMsg = "&e&lAncient Angler: &7Silence is golden.";
                    rewardAction = () -> { player.giveExpLevels(10); TextUtil.sendMessage(player, "&a[!] You earned 10 XP Levels & a Fossilized Anchor! (Not fully implemented)"); };
                }
                break;
            case 501:
                // simplification for rare fish check
                if (countAnyCustomFish(player) >= 3) { takeAnyCustomFish(player, 3); success = true; nextStage = 502; successMsg = "&e&lAncient Angler: &7Interesting. Bring an epic fish."; }
                break;
            case 502:
                if (countAnyCustomFish(player) >= 1) { takeAnyCustomFish(player, 1); success = true; nextStage = 503; successMsg = "&e&lAncient Angler: &7A wonder. Lastly, a legendary fish."; }
                break;
            case 503:
                if (countAnyCustomFish(player) >= 1) { takeAnyCustomFish(player, 1);
                    success = true; nextStage = 0; successMsg = "&e&lAncient Angler: &7You are a master of discovery!";
                    rewardAction = () -> TextUtil.sendMessage(player, "&a[!] You earned 500 Fishing XP! (Not fully implemented)");
                }
                break;
        }

        if (success) {
            TextUtil.sendMessage(player, successMsg);
            if (rewardAction != null) rewardAction.run();
            setStage(player.getUniqueId(), nextStage);

            Entity npc = spawnedNPCs.remove(player.getUniqueId());
            if (npc != null && npc.isValid()) {
                if (nextStage != 0) {
                    locRelocateNPC(player, npc);
                    TextUtil.sendMessage(player, "&7Your compass recalibrates... The NPC vanishes into the mist.");
                } else {
                    TextUtil.sendMessage(player, "&7Your journey is complete. The tracker shatters.");
                    for (ItemStack item : player.getInventory().getContents()) {
                        if (item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(trackerKey, PersistentDataType.STRING)) {
                            player.getInventory().remove(item);
                            break;
                        }
                    }
                }
                npc.remove();
            }
        } else {
            TextUtil.sendMessage(player, "&c&lAncient Angler: &4You dare insult me with empty hands?");
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 100, 1));
            plugin.getEconomyManager().withdraw(player, 1000);
            TextUtil.sendMessage(player, "&cYou were blinded and lost $1,000 for your insolence.");
        }
    }

    private boolean takeFish(Player player, String speciesName, int amount) {
        int count = 0;
        List<ItemStack> toRemove = new ArrayList<>();
        
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.hasItemMeta()) {
                PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
                String species = pdc.get(fishSpeciesKey, PersistentDataType.STRING);
                if (species != null && species.equalsIgnoreCase(speciesName)) {
                    count += item.getAmount();
                    toRemove.add(item);
                }
            }
        }

        if (count >= amount) {
            int remaining = amount;
            for (ItemStack item : toRemove) {
                if (item.getAmount() <= remaining) {
                    remaining -= item.getAmount();
                    player.getInventory().remove(item);
                } else {
                    item.setAmount(item.getAmount() - remaining);
                    remaining = 0;
                }
                if (remaining <= 0) break;
            }
            return true;
        }
        return false;
    }

    private int countAnyCustomFish(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(fishSpeciesKey, PersistentDataType.STRING)) {
                count += item.getAmount();
            }
        }
        return count;
    }

    private void takeAnyCustomFish(Player player, int amount) {
        int remaining = amount;
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(fishSpeciesKey, PersistentDataType.STRING)) {
                if (item.getAmount() <= remaining) {
                    remaining -= item.getAmount();
                    player.getInventory().setItem(i, null);
                } else {
                    item.setAmount(item.getAmount() - remaining);
                    remaining = 0;
                }
                if (remaining <= 0) break;
            }
        }
    }

    public void loadPlayer(UUID uuid) {
        plugin.getDatabaseManager().executeAsync(() -> {
            try (java.sql.Connection conn = plugin.getDatabaseManager().getConnection();
                 java.sql.PreparedStatement ps = conn.prepareStatement("SELECT stage FROM fishingrpg_quest_progress WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                try (java.sql.ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        questStages.put(uuid, rs.getInt("stage"));
                    } else {
                        questStages.put(uuid, 0);
                    }
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to load Ancient Angler data for " + uuid);
            }
        });
    }

    public void unloadPlayer(UUID uuid) {
        questStages.remove(uuid);
        chatState.remove(uuid);
        inDialogueSequence.remove(uuid);
        introOptions.remove(uuid);
        Entity npc = spawnedNPCs.remove(uuid);
        if (npc != null && npc.isValid()) {
            npc.remove();
        }
    }

    public void setStage(UUID uuid, int stage) {
        questStages.put(uuid, stage);
        plugin.getDatabaseManager().execute("INSERT INTO fishingrpg_quest_progress (uuid, stage) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET stage = ?", 
            uuid.toString(), stage, stage);
    }

    public int getStage(UUID uuid) {
        return questStages.getOrDefault(uuid, 0);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        unloadPlayer(event.getPlayer().getUniqueId());
    }

    public void shutdown() {
        for (Entity npc : spawnedNPCs.values()) {
            if (npc != null && npc.isValid()) {
                npc.remove();
            }
        }
        spawnedNPCs.clear();
        questStages.clear();
        chatState.clear();
        inDialogueSequence.clear();
        introOptions.clear();
    }
}
