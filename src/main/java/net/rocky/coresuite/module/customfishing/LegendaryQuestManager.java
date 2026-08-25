package net.rocky.coresuite.module.customfishing;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.module.customfishing.FishingEnchantManager.CustomEnchant;
import net.rocky.coresuite.module.customfishing.FishingEnchantManager.RodTier;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.configuration.ConfigurationSection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class LegendaryQuestManager implements Listener {
    private final CoreSuitePlugin plugin;
    private final CustomFishingModule module;
    
    private final NamespacedKey compassKey;
    private final NamespacedKey trackerKey;
    
    private final Map<UUID, Integer> questStages = new ConcurrentHashMap<>();
    
    // For Chat State
    private final Map<UUID, String> chatState = new ConcurrentHashMap<>();
    private final Map<UUID, List<Integer>> introOptions = new ConcurrentHashMap<>();
    
    private final Set<UUID> inDialogueSequence = ConcurrentHashMap.newKeySet();

    public LegendaryQuestManager(CoreSuitePlugin plugin, CustomFishingModule module) {
        this.plugin = plugin;
        this.module = module;
        this.compassKey = new NamespacedKey(plugin, "legendary_compass");
        this.trackerKey = new NamespacedKey(plugin, "legendary_tracker");
        
        initDatabase();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        
        Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                ItemStack item = p.getInventory().getItemInMainHand();
                if (item != null && (item.getType() == Material.RECOVERY_COMPASS || item.getType() == Material.COMPASS) && item.hasItemMeta()) {
                    org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                    if (meta.getPersistentDataContainer().has(compassKey, PersistentDataType.STRING) || 
                        meta.getPersistentDataContainer().has(trackerKey, PersistentDataType.STRING)) {
                        
                        String wName = meta.getPersistentDataContainer().get(new org.bukkit.NamespacedKey(plugin, "lode_w"), org.bukkit.persistence.PersistentDataType.STRING);
                        if (wName != null) {
                            org.bukkit.World w = org.bukkit.Bukkit.getWorld(wName);
                            if (w != null && p.getWorld().equals(w)) {
                                double tx = meta.getPersistentDataContainer().get(new org.bukkit.NamespacedKey(plugin, "lode_x"), org.bukkit.persistence.PersistentDataType.DOUBLE);
                                double ty = meta.getPersistentDataContainer().get(new org.bukkit.NamespacedKey(plugin, "lode_y"), org.bukkit.persistence.PersistentDataType.DOUBLE);
                                double tz = meta.getPersistentDataContainer().get(new org.bukkit.NamespacedKey(plugin, "lode_z"), org.bukkit.persistence.PersistentDataType.DOUBLE);
                                Location lode = new Location(w, tx, ty, tz);
                                double dist = p.getLocation().distance(lode);
                                if (dist < 15.0) {
                                    p.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("&e&lYou have reached the spot! Press 'Q' (Drop) to summon him!"));
                                } else {
                                    p.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("&bDistance to Ancient Angler: &e" + (int)dist + "m"));
                                }
                            }
                        }
                    }
                }
            }
        }, 20L, 20L);
    }
    
    private void initDatabase() {
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection c = plugin.getDatabaseManager().getConnection();
                 java.sql.Statement s = c.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS coresuite_fisherman_quest (" +
                          "uuid TEXT PRIMARY KEY, " +
                          "stage INTEGER DEFAULT 0)");
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public boolean isInChatState(java.util.UUID uuid) { return chatState.containsKey(uuid); }

    public void loadPlayer(UUID uuid) {
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection c = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT stage FROM coresuite_fisherman_quest WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    questStages.put(uuid, rs.getInt("stage"));
                } else {
                    questStages.put(uuid, 0);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void unloadPlayer(UUID uuid) {
        questStages.remove(uuid);
        chatState.remove(uuid);
        introOptions.remove(uuid);
        inDialogueSequence.remove(uuid);
    }
    
    public int getStage(UUID uuid) { return questStages.getOrDefault(uuid, 0); }
    
    public void setStage(UUID uuid, int stage) {
        questStages.put(uuid, stage);
        plugin.getDatabaseManager().execute("INSERT INTO coresuite_fisherman_quest (uuid, stage) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET stage = excluded.stage", uuid.toString(), stage);
    }

    public void giveMap(Player player) {
        ItemStack compass = new ItemStack(Material.RECOVERY_COMPASS);
        org.bukkit.inventory.meta.ItemMeta meta = compass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.legacyColorize("&6&lLegendary Fisherman's Compass"));
            List<String> lore = new ArrayList<>();
            lore.add(TextUtil.legacyColorize("&7An ancient compass pulling"));
            lore.add(TextUtil.legacyColorize("&7towards a hidden coastline..."));
            lore.add(TextUtil.legacyColorize(""));
            lore.add(TextUtil.legacyColorize("&eToss (Drop) this compass to check your"));
            lore.add(TextUtil.legacyColorize("&edistance, or to summon him if nearby!"));
            meta.setLore(lore);
            
            int targetX = player.getLocation().getBlockX() + java.util.concurrent.ThreadLocalRandom.current().nextInt(-5000, 5000);
            int targetZ = player.getLocation().getBlockZ() + java.util.concurrent.ThreadLocalRandom.current().nextInt(-5000, 5000);
            Location targetLoc = new Location(player.getWorld(), targetX, 62, targetZ);
            
            meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "lode_w"), org.bukkit.persistence.PersistentDataType.STRING, targetLoc.getWorld().getName());
            meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "lode_x"), org.bukkit.persistence.PersistentDataType.DOUBLE, targetLoc.getX());
            meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "lode_y"), org.bukkit.persistence.PersistentDataType.DOUBLE, targetLoc.getY());
            meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "lode_z"), org.bukkit.persistence.PersistentDataType.DOUBLE, targetLoc.getZ());
            meta.getPersistentDataContainer().set(compassKey, PersistentDataType.STRING, player.getUniqueId().toString());
            compass.setItemMeta(meta);
            
            player.getInventory().addItem(compass);
            TextUtil.sendMessage(player, "&bðŸŸ &lYou found the Legendary Fisherman's Compass! &7Follow it to uncover its secrets.");
        }
    }

    @EventHandler
    public void onDropItem(PlayerDropItemEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItemDrop().getItemStack();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        
        if (meta.getPersistentDataContainer().has(compassKey, PersistentDataType.STRING) || 
            meta.getPersistentDataContainer().has(trackerKey, PersistentDataType.STRING)) {
            
            event.setCancelled(true);
            NamespacedKey key = meta.getPersistentDataContainer().has(compassKey, PersistentDataType.STRING) ? compassKey : trackerKey;
            String ownerUUIDStr = meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
            
            if (!player.getUniqueId().toString().equals(ownerUUIDStr)) {
                TextUtil.sendMessage(player, "&cThis ancient device does not react to you.");
                return;
            }
            
            if (item.getType() == Material.RECOVERY_COMPASS || item.getType() == Material.COMPASS) {
                String wName = meta.getPersistentDataContainer().get(new org.bukkit.NamespacedKey(plugin, "lode_w"), org.bukkit.persistence.PersistentDataType.STRING);
                if (wName != null) {
                    org.bukkit.World w = org.bukkit.Bukkit.getWorld(wName);
                    if (w == null || !w.equals(player.getWorld())) {
                        TextUtil.sendMessage(player, "&cThe compass needle spins randomly... (Wrong World)");
                        return;
                    }
                    double tx = meta.getPersistentDataContainer().get(new org.bukkit.NamespacedKey(plugin, "lode_x"), org.bukkit.persistence.PersistentDataType.DOUBLE);
                    double ty = meta.getPersistentDataContainer().get(new org.bukkit.NamespacedKey(plugin, "lode_y"), org.bukkit.persistence.PersistentDataType.DOUBLE);
                    double tz = meta.getPersistentDataContainer().get(new org.bukkit.NamespacedKey(plugin, "lode_z"), org.bukkit.persistence.PersistentDataType.DOUBLE);
                    Location lodestone = new Location(w, tx, ty, tz);
                    double dist = player.getLocation().distance(lodestone);
                    if (dist < 15.0) {
                        performRitual(player, item, key.equals(compassKey));
                    } else {
                        TextUtil.sendMessage(player, "&bThe compass hums softly... you are &e" + (int)dist + " &bblocks away.");
                    }
                }
            }
        }
    }

    private void performRitual(Player player, ItemStack item, boolean isFirstTime) {
        UUID u = player.getUniqueId();
        if (inDialogueSequence.contains(u)) return;
        
        inDialogueSequence.add(u);
        
        if (isFirstTime) {
            org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(TextUtil.legacyColorize("&b&lAngler's Tracker"));
            List<String> lore = new ArrayList<>();
            lore.add(TextUtil.legacyColorize("&7Permanently tracks the location of"));
            lore.add(TextUtil.legacyColorize("&7the Legendary Ancient Angler."));
            lore.add(TextUtil.legacyColorize(""));
            lore.add(TextUtil.legacyColorize("&eFollow the needle, and drop this"));
            lore.add(TextUtil.legacyColorize("&eitem to summon him when close!"));
            meta.setLore(lore);
            meta.getPersistentDataContainer().remove(compassKey);
            meta.getPersistentDataContainer().set(trackerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            item.setItemMeta(meta);
            
            TextUtil.sendMessage(player, "&bThe compass shatters in your hands, revealing its true form!");
            player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 1.0f);
        } else {
            TextUtil.sendMessage(player, "&bThe tracker compass vibrates violently in your hands...");
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 1.0f, 2.0f);
        }
        
        Location spawnLoc = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(2));
        spawnLoc.setY(player.getLocation().getY());
        if (spawnLoc.getBlock().getType().isSolid()) {
            spawnLoc = player.getLocation();
        }
        
        final Location finalSpawnLoc = spawnLoc;
        
        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (!player.isOnline()) {
                    inDialogueSequence.remove(u);
                    cancel();
                    return;
                }
                
                if (tick < 40) {
                    if (tick % 5 == 0) {
                        player.getWorld().spawnParticle(org.bukkit.Particle.NAUTILUS, finalSpawnLoc.clone().add(0, 1, 0), 10, 0.5, 0.5, 0.5, 0.05);
                        player.getWorld().spawnParticle(org.bukkit.Particle.SPLASH, finalSpawnLoc.clone().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.5);
                        player.playSound(finalSpawnLoc, Sound.BLOCK_WATER_AMBIENT, 1f, 0.5f);
                    }
                    if (tick == 20) {
                        player.playSound(finalSpawnLoc, Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 1f);
                    }
                } else if (tick == 40) {
                    player.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, finalSpawnLoc.clone().add(0, 1, 0), 20);
                    player.getWorld().spawnParticle(org.bukkit.Particle.SPLASH, finalSpawnLoc.clone().add(0, 1, 0), 200, 1.0, 2.0, 1.0, 0.5);
                    player.getWorld().spawnParticle(org.bukkit.Particle.SOUL, finalSpawnLoc.clone().add(0, 1, 0), 50, 0.5, 1.0, 0.5, 0.1);
                    
                    player.playSound(finalSpawnLoc, Sound.ENTITY_GENERIC_SPLASH, 1f, 0.5f);
                    player.playSound(finalSpawnLoc, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 0.8f);
                    
                    module.getLegendaryNPCManager().spawnLegendaryNPC(u, finalSpawnLoc);
                } else if (tick == 80) { // 2 seconds after spawn
                    if (isFirstTime) {
                        setStage(u, 10);
                        new BukkitRunnable() {
                            int dTick = 0;
                            @Override
                            public void run() {
                                if (!player.isOnline()) {
                                    inDialogueSequence.remove(u);
                                    cancel();
                                    return;
                                }
                                if (dTick == 0) TextUtil.sendMessage(player, "&e[Ancient Angler]&f Who awakens the Ancient Angler from the depths...?");
                                else if (dTick == 1) TextUtil.sendMessage(player, "&e[Ancient Angler]&f Ah... another mortal seeking the legends of the deep.");
                                else if (dTick == 2) {
                                    inDialogueSequence.remove(u);
                                    startIntroDialogue(player);
                                    cancel();
                                }
                                dTick++;
                            }
                        }.runTaskTimer(plugin, 0L, 80L);
                    } else {
                        inDialogueSequence.remove(u);
                        handleNPCInteract(player);
                    }
                    cancel();
                }
                tick++;
            }
        }.runTaskTimer(plugin, 0L, 1L);
    }

    @EventHandler
    public void onInteractNPC(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof org.bukkit.entity.Entity)) return;
        
        org.bukkit.entity.Entity villager = event.getRightClicked();
        if (villager.getPersistentDataContainer().has(module.getLegendaryNPCManager().getLegendaryKey(), PersistentDataType.STRING)) {
            event.setCancelled(true);
            String ownerId = villager.getPersistentDataContainer().get(module.getLegendaryNPCManager().getLegendaryKey(), PersistentDataType.STRING);
            
            if (ownerId != null && !ownerId.equals(event.getPlayer().getUniqueId().toString())) {
                TextUtil.sendMessage(event.getPlayer(), "&cThe Ancient Angler glares at you. He is waiting for someone else.");
                return;
            }
            handleNPCInteract(event.getPlayer());
        }
    }

    private void handleNPCInteract(Player player) {
        if (inDialogueSequence.contains(player.getUniqueId())) return;
        
        int stage = getStage(player.getUniqueId());
        
        if (stage == 0) {
            TextUtil.sendMessage(player, "&cYou have not found the compass yet.");
        } else if (stage == 10) {
            startIntroDialogue(player);
        } else if (stage == 101) {
            interactiveTurnIn(player, "turnin_101", "To brave the great currents, ye must first build a shell. The shallows provide the paint, vibrant and fragile. Bring me the painted school, and I'll show ye the path they took.");
        } else if (stage == 102) {
            interactiveTurnIn(player, "turnin_102", "Aye... but the painted ones never survive the drop. Down there, the sun dies and the water bites like iron. Show me ye know what it takes to survive the freeze.");
        } else if (stage == 103) {
            interactiveTurnIn(player, "turnin_103", "Ice in the veins. Good. But surviving ain't enough; ye must conquer the pull. Bring me the red swimmer of the streams, the one that fights the inevitable.");
        } else if (stage == 104) {
            interactiveTurnIn(player, "turnin_104", "A true scrapper. But true endurance is forged at the very bottom, where the ocean boils against the earth's crust. Reach into the inferno. Hand me the ember.");
        } else if (stage == 201) {
            interactiveTurnIn(player, "turnin_201", "Gold... it be the heaviest anchor a man can tie to his own neck. The ocean has her own currency, spilled from a thousand wrecks. Spill two hands full of her varied coins before me.");
        } else if (stage == 202) {
            interactiveTurnIn(player, "turnin_202", "A beggar's purse. If ye want the true hoard, ye must understand a king's folly. Fetch me the catch that shines so bright it blinds greedy fools to the kraken behind it.");
        } else if (stage == 203) {
            interactiveTurnIn(player, "turnin_203", "Pure, untarnished vanity. But gold ain't born in the water; it's born in the crushing heat of the deep earth. Bring me the forge's beating heart.");
        } else if (stage == 301) {
            interactiveTurnIn(player, "turnin_301", "The Hunt! Har! There's no music sweeter than the snap of a line in the black water. To track the monsters, ye must first feed on the frenzied biters. Bring me a handful of razors.");
        } else if (stage == 302) {
            interactiveTurnIn(player, "turnin_302", "Vicious little demons... but they're just bait for the serpents. To learn the ambush, ye must study the beasts that hide in the rocks. Haul me three of 'em.");
        } else if (stage == 303) {
            interactiveTurnIn(player, "turnin_303", "Jaws like rusty bear traps, aye. Now, ye are ready for the apex of the dark. The one that weaves the traps. Bring me the Queen.");
        } else if (stage == 401) {
            interactiveTurnIn(player, "turnin_401", "The sea is vast, kid, and full of pointless noise. To hear the deep's true secrets, ye must first drown out the chatter. Bring me a bounty of the common rabble. A fisherman's dozen, plus two.");
        } else if (stage == 402) {
            interactiveTurnIn(player, "turnin_402", "Ah... feel how the water quiets down? Now, true silence means focusing on a single, insignificant detail among thousands. Find me a silver speck of a massive school.");
        } else if (stage == 403) {
            interactiveTurnIn(player, "turnin_403", "Just a speck... And finally, the ultimate stillness. A creature that doesn't fight, doesn't swim. It merely clings to the bottom, and watches the tides roll by.");
        }
    }

    private void interactiveTurnIn(Player player, String state, String riddlePrompt) {
        chatState.put(player.getUniqueId(), state);
        TextUtil.sendMessage(player, "&e[Ancient Angler]&f " + riddlePrompt);
        TextUtil.sendMessage(player, "&b[1]&f [Offer the catch to the old man]");
        TextUtil.sendMessage(player, "&b[2]&f I cannot decipher the tides.");
        TextUtil.sendMessage(player, "&b[3]&f [Sacrifice a prized catch for the answer] &7(Hidden Payment)");
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID u = player.getUniqueId();
        
        if (chatState.containsKey(u)) {
            event.setCancelled(true);
            String msg = event.getMessage().trim();
            
            Bukkit.getScheduler().runTask(plugin, () -> {
                processChatSync(player, u, msg);
            });
        }
    }
    
    public void processChatSync(Player player, UUID u, String msg) {
        if (!chatState.containsKey(u)) return;
        
        if (inDialogueSequence.contains(u)) {
            TextUtil.sendMessage(player, "&cWait until the Angler finishes speaking...");
            return;
        }
        
        String state = chatState.get(u);
        
        if (msg.equalsIgnoreCase("cancel") || msg.equalsIgnoreCase("leave")) {
            TextUtil.sendMessage(player, "&7You walked away from the Angler.");
            chatState.remove(u);
            return;
        }
        
        if (state.equals("awaiting_intro")) {
            List<Integer> options = introOptions.get(u);
            if (options == null || options.isEmpty()) {
                chatState.remove(u);
                return;
            }
            
            int chosenPath = -1;
            if (msg.equals("1")) chosenPath = options.get(0);
            else if (msg.equals("2")) chosenPath = options.get(1);
            else if (msg.equals("3")) chosenPath = options.get(2);
            else {
                TextUtil.sendMessage(player, "&e[Ancient Angler]&f I do not understand those words. Type 1, 2, or 3.");
                return;
            }
            
            chatState.remove(u);
            
            if (chosenPath == 1) {
                setStage(u, 101);
                sendLinesAndRelocate(player, 
                    "&e[Ancient Angler]&f You choose to fight the tide. Let us see if you break.",
                    "&e[Ancient Angler]&f To brave the great currents, ye must first build a shell. The shallows provide the paint, vibrant and fragile. Bring me the painted school, and I'll show ye the path they took."
                );
            } else if (chosenPath == 2) {
                setStage(u, 201);
                sendLinesAndRelocate(player, 
                    "&e[Ancient Angler]&f Heavy pockets... yes. Men drop their coins, but the ocean has its own currency.",
                    "&e[Ancient Angler]&f Gold... it be the heaviest anchor a man can tie to his own neck. The ocean has her own currency, spilled from a thousand wrecks. Spill two hands full of her varied coins before me."
                );
            } else if (chosenPath == 3) {
                setStage(u, 301);
                sendLinesAndRelocate(player, 
                    "&e[Ancient Angler]&f The thrill of the hunt...",
                    "&e[Ancient Angler]&f The Hunt! Har! There's no music sweeter than the snap of a line in the black water. To track the monsters, ye must first feed on the frenzied biters. Bring me a handful of razors."
                );
            } else if (chosenPath == 4) {
                setStage(u, 401);
                sendLinesAndRelocate(player, 
                    "&e[Ancient Angler]&f The ocean is vast and full of noise.",
                    "&e[Ancient Angler]&f The sea is vast, kid, and full of pointless noise. To hear the deep's true secrets, ye must first drown out the chatter. Bring me a bounty of the common rabble. A fisherman's dozen, plus two."
                );
            } else if (chosenPath == 5) {
                sendLinesAndPunish(player, 
                    "&e[Ancient Angler]&f Oh, is that how it is? Ye think the cruel sea grants favors to guppies like you?",
                    "&e[Ancient Angler]&f Look at yer hands, kid. The ocean takes what she wants, and so do I!"
                );
            }
            return;
        }
        
        // Turn-in logic: 1 (Attempt), 2 (RP Fail), 3 (Hint)
        if (msg.equals("2")) {
            TextUtil.sendMessage(player, "&e[Ancient Angler]&f The deep speaks in riddles, kid. Only those who listen survive.");
            return;
        }
        
        if (msg.equals("3")) {
            if (payHintCost(player)) {
                if (state.equals("turnin_101")) TextUtil.sendMessage(player, "&e[Ancient Angler]&f Fools always need a map. Bring me 10 Neon Tetras and 10 Angelfish.");
                else if (state.equals("turnin_102")) TextUtil.sendMessage(player, "&e[Ancient Angler]&f A Char. Bring me a Char.");
                else if (state.equals("turnin_103")) TextUtil.sendMessage(player, "&e[Ancient Angler]&f A Cherry Salmon. Go.");
                else if (state.equals("turnin_104")) TextUtil.sendMessage(player, "&e[Ancient Angler]&f Bring me a Lava Particle.");
                
                else if (state.equals("turnin_201")) TextUtil.sendMessage(player, "&e[Ancient Angler]&f Common greed requires common effort. Bring me 20 Custom Fish.");
                else if (state.equals("turnin_202")) TextUtil.sendMessage(player, "&e[Ancient Angler]&f A Golden Carp. Bring it here.");
                else if (state.equals("turnin_203")) TextUtil.sendMessage(player, "&e[Ancient Angler]&f A Lava Particle.");
                
                else if (state.equals("turnin_301")) TextUtil.sendMessage(player, "&e[Ancient Angler]&f 5 Piranhas. Do not lose your fingers.");
                else if (state.equals("turnin_302")) TextUtil.sendMessage(player, "&e[Ancient Angler]&f 3 Moray Eels.");
                else if (state.equals("turnin_303")) TextUtil.sendMessage(player, "&e[Ancient Angler]&f The Spiderfish Queen.");
                
                else if (state.equals("turnin_401")) TextUtil.sendMessage(player, "&e[Ancient Angler]&f 15 Custom Fish. Just sit and cast.");
                else if (state.equals("turnin_402")) TextUtil.sendMessage(player, "&e[Ancient Angler]&f An Anchovy. Nothing more.");
                else if (state.equals("turnin_403")) TextUtil.sendMessage(player, "&e[Ancient Angler]&f A Suckerfish.");
            }
            return;
        }
        
        if (msg.equals("1")) {
            chatState.remove(u);
            
            // Path 1
            if (state.equals("turnin_101")) {
                checkTask(player, state, "Neon Tetra", 10, "Angelfish", 10, 102, 
                    "Do not lie to the deep. Your hands are empty. Bring me a painted school.",
                    "Aye... but the painted ones never survive the drop. Down there, the sun dies and the water bites like iron. Show me ye know what it takes to survive the freeze.");
            } else if (state.equals("turnin_102")) {
                checkTask(player, state, "Char", 1, null, 0, 103, 
                    "Do not lie to the deep. Your hands are empty. Bring me a survivor of the freezing depths.",
                    "Ice in the veins. Good. But surviving ain't enough; ye must conquer the pull. Bring me the red swimmer of the streams, the one that fights the inevitable.");
            } else if (state.equals("turnin_103")) {
                checkTask(player, state, "Cherry Salmon", 1, null, 0, 104, 
                    "Do not lie to the deep. Your hands are empty. Bring me the red swimmer of the streams.",
                    "A true scrapper. But true endurance is forged at the very bottom, where the ocean boils against the earth's crust. Reach into the inferno. Hand me the ember.");
            } else if (state.equals("turnin_104")) {
                if (!takeFish(player, "Lava Particle", 1)) {
                    punishAndRepeat(player, state, "Do not lie to the deep. Your hands are empty. Pull a piece of the inferno itself.");
                    return;
                }
                ItemStack rod = player.getInventory().getItemInMainHand();
                RodTier tier = module.getEnchantManager().getRodTier(rod);
                int currentLevel = module.getEnchantManager().getEnchantLevel(rod, CustomEnchant.TIRELESS_CASTER);
                
                RodTier requiredTier = (currentLevel == 0) ? RodTier.BRONZE : RodTier.SILVER;
                String tierName = (currentLevel == 0) ? "Bronze" : "Silver";
                
                if (tier.ordinal() < requiredTier.ordinal()) {
                    sendLines(player, false, "&e[Ancient Angler]&f Your rod is too weak to handle this mastery! Hold at least a " + tierName + " Tier rod and talk to me again.");
                    ItemStack fishItem = module.createFishItem(module.getConfig().getString("species.lava_particle.name", "Lava Particle"), "MAGMA_CREAM", 1.0, 50.0, "");
                    java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(fishItem);
                    for (ItemStack left : leftover.values()) player.getWorld().dropItem(player.getLocation(), left);
                    return;
                }
                if (currentLevel >= 3) {
                    sendLines(player, false, "&e[Ancient Angler]&f You have already mastered this enchantment. You need nothing more from me.");
                    ItemStack fishItem = module.createFishItem(module.getConfig().getString("species.lava_particle.name", "Lava Particle"), "MAGMA_CREAM", 1.0, 50.0, "");
                    java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(fishItem);
                    for (ItemStack left : leftover.values()) player.getWorld().dropItem(player.getLocation(), left);
                    return;
                }
                module.getEnchantManager().setEnchantLevel(rod, CustomEnchant.TIRELESS_CASTER, currentLevel + 1);
                sendLines(player, true,
                    "&e[Ancient Angler]&f You pulled the heat from the earth itself. I grant you the mastery of the Tireless Caster."
                );
            }
            
            // Path 2
            else if (state.equals("turnin_201")) {
                if (countAnyCustomFish(player) < 20) {
                    punishAndRepeat(player, state, "Do not mock me. Your pockets are light. Bring me two hands full of the ocean's varied coins.");
                    return;
                }
                takeAnyCustomFish(player, 20);
                setStage(u, 202);
                sendLinesAndRelocate(player, 
                    "&e[Ancient Angler]&f A beggar's purse. If ye want the true hoard, ye must understand a king's folly. Fetch me the catch that shines so bright it blinds greedy fools to the kraken behind it."
                );
            } else if (state.equals("turnin_202")) {
                checkTask(player, state, "Golden Carp", 1, null, 0, 203,
                    "Do not mock me. Your pockets are light. Bring me the king's folly.",
                    "Pure, untarnished vanity. But gold ain't born in the water; it's born in the crushing heat of the deep earth. Bring me the forge's beating heart.");
            } else if (state.equals("turnin_203")) {
                if (!takeFish(player, "Lava Particle", 1)) {
                    punishAndRepeat(player, state, "Do not mock me. Your pockets are light. Bring me the forge's ember.");
                    return;
                }
                plugin.getEconomyManager().deposit(player, 500000.0);
                sendLines(player, true,
                    "&e[Ancient Angler]&f Take this Leviathan's bounty. Spend it fast, before the salt claims it back."
                );
            }
            
            // Path 3
            else if (state.equals("turnin_301")) {
                checkTask(player, state, "Piranha", 5, null, 0, 302,
                    "You smell of fear, not blood. Bring me the frenzied biters.",
                    "Vicious little demons... but they're just bait for the serpents. To learn the ambush, ye must study the beasts that hide in the rocks. Haul me three of 'em.");
            } else if (state.equals("turnin_302")) {
                checkTask(player, state, "Moray Eel", 3, null, 0, 303,
                    "You smell of fear, not blood. Bring me the serpents.",
                    "Jaws like rusty bear traps, aye. Now, ye are ready for the apex of the dark. The one that weaves the traps. Bring me the Queen.");
            } else if (state.equals("turnin_303")) {
                if (!takeFish(player, "Spiderfish Queen", 1)) {
                    punishAndRepeat(player, state, "You smell of fear, not blood. Bring me the web-spinner of the deep.");
                    return;
                }
                String[] availableBaits = {"worm", "golden_lure", "tide_essence", "mythic_chum"};
                String chosenBait = availableBaits[ThreadLocalRandom.current().nextInt(availableBaits.length)];
                ItemStack baitReward = module.getBaitManager().createBaitItem(chosenBait);
                if (baitReward != null) {
                    baitReward.setAmount(3);
                    java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(baitReward);
                    for (ItemStack left : leftover.values()) player.getWorld().dropItem(player.getLocation(), left);
                }
                sendLines(player, true,
                    "&e[Ancient Angler]&f Ye are a true hunter of the deep. Take this... don't waste it on minnows. Go hunt the legends."
                );
            }
            
            // Path 4
            else if (state.equals("turnin_401")) {
                if (countAnyCustomFish(player) < 15) {
                    punishAndRepeat(player, state, "You are too loud. You rushed. Bring me the bounty of the common chatter.");
                    return;
                }
                takeAnyCustomFish(player, 15);
                setStage(u, 402);
                sendLinesAndRelocate(player, 
                    "&e[Ancient Angler]&f Ah... feel how the water quiets down? Now, true silence means focusing on a single, insignificant detail among thousands. Find me a silver speck of a massive school."
                );
            } else if (state.equals("turnin_402")) {
                checkTask(player, state, "Anchovy", 1, null, 0, 403,
                    "You are too loud. You rushed. Bring me the silver speck.",
                    "Just a speck... And finally, the ultimate stillness. A creature that doesn't fight, doesn't swim. It merely clings to the bottom, and watches the tides roll by.");
            } else if (state.equals("turnin_403")) {
                if (!takeFish(player, "Suckerfish", 1)) {
                    punishAndRepeat(player, state, "You are too loud. You rushed. Bring me the observer of the depths.");
                    return;
                }
                player.giveExpLevels(10); 
                ItemStack anchor = new ItemStack(Material.NAUTILUS_SHELL);
                ItemMeta anchorMeta = anchor.getItemMeta();
                if (anchorMeta != null) {
                    anchorMeta.setDisplayName(TextUtil.legacyColorize("&6&lFossilized Anchor"));
                    anchorMeta.addEnchant(org.bukkit.enchantments.Enchantment.LURE, 1, true);
                    anchorMeta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                    anchorMeta.setLore(Arrays.asList(
                        TextUtil.legacyColorize("&7An ancient, heavy relic..."), 
                        TextUtil.legacyColorize("&7Proof of your ultimate patience.")
                    ));
                    anchor.setItemMeta(anchorMeta);
                    java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(anchor);
                    for (ItemStack left : leftover.values()) player.getWorld().dropItem(player.getLocation(), left);
                }
                sendLines(player, true,
                    "&e[Ancient Angler]&f Ye truly understand the quiet of the waters. Keep this ancient relic; let it anchor yer patience."
                );
            }
        } else {
            TextUtil.sendMessage(player, "&e[Ancient Angler]&f I do not understand those words. Type 1, 2, or 3.");
        }
    }
    
    private String getIntroText(boolean scenarioA, int pathId) {
        if (scenarioA) {
            switch (pathId) {
                case 1: return "The freezing tide numbs all panic eventually.";
                case 2: return "A man burdened by gold sinks without a fight.";
                case 3: return "The leviathans below dragged him down too fast.";
                case 4: return "He simply accepted the peace of the deep.";
                case 5: return "Stop the riddles, old man. I need a favor.";
            }
        } else {
            switch (pathId) {
                case 1: return "The endless need to fight against the current.";
                case 2: return "The shimmer of sunken empires buried in the muck.";
                case 3: return "The pure adrenaline when the line snaps taut.";
                case 4: return "The absolute silence when the surface breaks.";
                case 5: return "You are. Now give me what I'm owed.";
            }
        }
        return "Unknown";
    }

    private void startIntroDialogue(Player player) {
        UUID u = player.getUniqueId();
        inDialogueSequence.add(u);
        chatState.put(u, "awaiting_intro");
        
        boolean scenarioA = ThreadLocalRandom.current().nextBoolean();
        
        List<Integer> availablePaths = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5));
        Collections.shuffle(availablePaths);
        List<Integer> chosenPaths = availablePaths.subList(0, 3);
        introOptions.put(u, chosenPaths);
        
        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (scenarioA) {
                    if (tick == 0) TextUtil.sendMessage(player, "&e[Ancient Angler]&f *Cough*... I watched a man surrender to the water 'ere yesterday. Didn't thrash. Didn't scream. Just sank like a stone. Why d'ye think he stayed so quiet?");
                    else if (tick == 1) {
                        TextUtil.sendMessage(player, "&b[1]&f " + getIntroText(scenarioA, chosenPaths.get(0)));
                        TextUtil.sendMessage(player, "&b[2]&f " + getIntroText(scenarioA, chosenPaths.get(1)));
                        TextUtil.sendMessage(player, "&b[3]&f " + getIntroText(scenarioA, chosenPaths.get(2)));
                        inDialogueSequence.remove(u);
                        cancel();
                    }
                } else {
                    if (tick == 0) TextUtil.sendMessage(player, "&e[Ancient Angler]&f Look at 'em... Every fish bites the iron thinkin' it's a blessin'. Tell me, youngster, what's the iron hook that caught your soul?");
                    else if (tick == 1) {
                        TextUtil.sendMessage(player, "&b[1]&f " + getIntroText(scenarioA, chosenPaths.get(0)));
                        TextUtil.sendMessage(player, "&b[2]&f " + getIntroText(scenarioA, chosenPaths.get(1)));
                        TextUtil.sendMessage(player, "&b[3]&f " + getIntroText(scenarioA, chosenPaths.get(2)));
                        inDialogueSequence.remove(u);
                        cancel();
                    }
                }
                tick++;
            }
        }.runTaskTimer(plugin, 10L, 80L);
    }
    
    private void punishAndRepeat(Player player, String state, String failMessage) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 5, 1));
        plugin.getEconomyManager().withdraw(player, 10000.0);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        TextUtil.sendMessage(player, "&c[System] You lied to the Angler. $10,000 deducted.");
        TextUtil.sendMessage(player, "&e[Ancient Angler]&f " + failMessage);
        
        chatState.put(player.getUniqueId(), state);
    }
    
    private boolean payHintCost(Player player) {
        if (!plugin.getEconomyManager().has(player, 5000.0)) {
            TextUtil.sendMessage(player, "&c[System] You need $5,000 for a hint.");
            return false;
        }
        
        boolean found = false;
        String deductedFishName = "Unknown";
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) {
                String speciesName = item.getItemMeta().getPersistentDataContainer().get(module.getSpeciesKey(), PersistentDataType.STRING);
                if (isRareOrEpic(speciesName)) {
                    deductedFishName = speciesName;
                    item.setAmount(item.getAmount() - 1);
                    if (item.getAmount() <= 0) player.getInventory().setItem(i, null);
                    found = true;
                    break;
                }
            }
        }
        
        if (!found) {
            ItemStack[] bag = module.getFishBagManager().getBag(player.getUniqueId());
            if (bag != null) {
                for (int i = 0; i < bag.length; i++) {
                    ItemStack item = bag[i];
                    if (item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) {
                        String speciesName = item.getItemMeta().getPersistentDataContainer().get(module.getSpeciesKey(), PersistentDataType.STRING);
                        if (isRareOrEpic(speciesName)) {
                            deductedFishName = speciesName;
                            item.setAmount(item.getAmount() - 1);
                            if (item.getAmount() <= 0) bag[i] = null;
                            module.getFishBagManager().updateBag(player.getUniqueId(), bag);
                            module.getFishBagManager().saveBag(player.getUniqueId());
                            found = true;
                            break;
                        }
                    }
                }
            }
        }
        
        if (found) {
            plugin.getEconomyManager().withdraw(player, 5000.0);
            TextUtil.sendMessage(player, "&a[System] Paid $5,000 and 1x " + deductedFishName + " for a hint.");
            return true;
        } else {
            int roll = ThreadLocalRandom.current().nextInt(3);
            if (roll == 0) {
                plugin.getEconomyManager().withdraw(player, 5000.0);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 180, 1));
                TextUtil.sendMessage(player, "&c[System] Lacked fish tribute! Paid $5,000 and cursed with Slowness (3m) for the hint.");
                player.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 1f);
                return true;
            } else if (roll == 1) {
                plugin.getEconomyManager().withdraw(player, 5000.0);
                player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 20 * 180, 1));
                TextUtil.sendMessage(player, "&c[System] Lacked fish tribute! Paid $5,000 and cursed with Fatigue (3m) for the hint.");
                player.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 1f);
                return true;
            } else {
                if (plugin.getEconomyManager().has(player, 25000.0)) {
                    plugin.getEconomyManager().withdraw(player, 25000.0);
                    TextUtil.sendMessage(player, "&c[System] Lacked fish tribute! The Angler seized $25,000 instead for the hint.");
                    player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return true;
                } else {
                    TextUtil.sendMessage(player, "&c[System] You lack a Rare fish and cannot afford the $25,000 penalty fee!");
                    return false;
                }
            }
        }
    }
    
    private boolean isRareOrEpic(String speciesName) {
        ConfigurationSection speciesSection = module.getConfig().getConfigurationSection("species");
        if (speciesSection != null) {
            for (String key : speciesSection.getKeys(false)) {
                String name = speciesSection.getString(key + ".name", "");
                name = ChatColor.stripColor(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(name).content());
                if (speciesName.equalsIgnoreCase(name)) {
                    String rarity = speciesSection.getString(key + ".rarity", "Common").toLowerCase();
                    return rarity.equals("rare") || rarity.equals("epic") || rarity.equals("legendary");
                }
            }
        }
        return false;
    }
    
    private void relocateNPC(Player player) {
        module.getLegendaryNPCManager().removeNPC(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        player.getWorld().spawnParticle(org.bukkit.Particle.PORTAL, player.getLocation(), 200, 1.0, 1.0, 1.0, 0.5);
        
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(trackerKey, PersistentDataType.STRING)) {
                org.bukkit.inventory.meta.ItemMeta meta = item.getItemMeta();
                int newX = player.getLocation().getBlockX() + java.util.concurrent.ThreadLocalRandom.current().nextInt(-2000, 2000);
                int newZ = player.getLocation().getBlockZ() + java.util.concurrent.ThreadLocalRandom.current().nextInt(-2000, 2000);
                Location newLoc = new Location(player.getWorld(), newX, 62, newZ);
                meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "lode_w"), org.bukkit.persistence.PersistentDataType.STRING, newLoc.getWorld().getName());
                meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "lode_x"), org.bukkit.persistence.PersistentDataType.DOUBLE, newLoc.getX());
                meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "lode_y"), org.bukkit.persistence.PersistentDataType.DOUBLE, newLoc.getY());
                meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "lode_z"), org.bukkit.persistence.PersistentDataType.DOUBLE, newLoc.getZ());
                item.setItemMeta(meta);
                TextUtil.sendMessage(player, "&bThe Angler vanished! Your tracker compass spins wildly and locks onto a new location...");
                break;
            }
        }
    }
    
    private void sendLinesAndRelocate(Player player, String... lines) {
        UUID u = player.getUniqueId();
        inDialogueSequence.add(u);
        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (tick < lines.length) {
                    TextUtil.sendMessage(player, lines[tick]);
                    tick++;
                } else {
                    inDialogueSequence.remove(u);
                    relocateNPC(player);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 10L, 80L);
    }

    private void checkTask(Player player, String state, String fish1, int amount1, String fish2, int amount2, int nextStage, String failMessage, String... dialogues) {
        if (fish2 == null) {
            if (!takeFish(player, fish1, amount1)) {
                punishAndRepeat(player, state, failMessage);
                return;
            }
        } else {
            if (countFish(player, fish1) < amount1 || countFish(player, fish2) < amount2) {
                punishAndRepeat(player, state, failMessage);
                return;
            }
            takeFishAmount(player, fish1, amount1);
            takeFishAmount(player, fish2, amount2);
        }
        
        setStage(player.getUniqueId(), nextStage);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        
        List<String> lines = new ArrayList<>();
        for (String d : dialogues) lines.add("&e[Ancient Angler]&f " + d);
        sendLinesAndRelocate(player, lines.toArray(new String[0]));
    }

    private void sendLinesAndPunish(Player player, String... lines) {
        UUID u = player.getUniqueId();
        inDialogueSequence.add(u);
        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (tick < lines.length) {
                    TextUtil.sendMessage(player, lines[tick]);
                    tick++;
                } else {
                    inDialogueSequence.remove(u);
                    
                    player.getWorld().strikeLightningEffect(player.getLocation());
                    player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 20 * 120, 4));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 20 * 30, 2));
                    
                    int stolen = 0;
                    ItemStack[] bag = module.getFishBagManager().getBag(u);
                    if (bag != null) {
                        for (int i = 0; i < bag.length; i++) {
                            if (bag[i] != null && stolen < 5) {
                                bag[i].setAmount(bag[i].getAmount() - 1);
                                if (bag[i].getAmount() <= 0) bag[i] = null;
                                stolen++;
                            }
                        }
                        if (stolen > 0) {
                            module.getFishBagManager().updateBag(u, bag);
                            module.getFishBagManager().saveBag(u);
                        }
                    }
                    
                    if (stolen < 5) {
                        for (int i = 0; i < player.getInventory().getSize(); i++) {
                            ItemStack item = player.getInventory().getItem(i);
                            if (item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) {
                                item.setAmount(item.getAmount() - 1);
                                if (item.getAmount() <= 0) player.getInventory().setItem(i, null);
                                stolen++;
                                if (stolen >= 5) break;
                            }
                        }
                    }
                    
                    if (stolen == 0) {
                        plugin.getEconomyManager().withdraw(player, 25000.0);
                        TextUtil.sendMessage(player, "&cThe Angler stole $25,000 from you!");
                    } else {
                        TextUtil.sendMessage(player, "&cThe Angler violently stole " + stolen + " fish from you!");
                    }
                    
                    finishQuest(player);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 10L, 80L);
    }

    private void sendLines(Player player, boolean endQuest, String... lines) {
        UUID u = player.getUniqueId();
        inDialogueSequence.add(u);
        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (tick < lines.length) {
                    TextUtil.sendMessage(player, lines[tick]);
                    tick++;
                } else {
                    inDialogueSequence.remove(u);
                    if (endQuest) finishQuest(player);
                    cancel();
                }
            }
        }.runTaskTimer(plugin, 10L, 80L);
    }

    private void finishQuest(Player player) {
        setStage(player.getUniqueId(), 0);
        module.getLegendaryNPCManager().removeNPC(player.getUniqueId());
        
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.hasItemMeta() && 
                (item.getItemMeta().getPersistentDataContainer().has(trackerKey, PersistentDataType.STRING) || 
                 item.getItemMeta().getPersistentDataContainer().has(compassKey, PersistentDataType.STRING))) {
                player.getInventory().setItem(i, null);
            }
        }
        player.updateInventory();
        
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        player.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, player.getLocation(), 100, 1.0, 1.0, 1.0, 0.1);
    }

    // --- Fish Counting and Taking Methods (from Bag and Inventory) ---
    private int countAnyCustomFish(Player player) {
        int c = 0;
        for (ItemStack i : player.getInventory().getContents()) {
            if (i != null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) c += i.getAmount();
        }
        ItemStack[] bag = module.getFishBagManager().getBag(player.getUniqueId());
        if (bag != null) {
            for (ItemStack i : bag) {
                if (i != null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) c += i.getAmount();
            }
        }
        return c;
    }

    private void takeAnyCustomFish(Player player, int amount) {
        int removed = 0;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack i = player.getInventory().getItem(slot);
            if (i != null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) {
                while (removed < amount && i.getAmount() > 0) {
                    i.setAmount(i.getAmount() - 1);
                    removed++;
                }
                if (i.getAmount() <= 0) player.getInventory().setItem(slot, null);
            }
        }
        if (removed < amount) {
            ItemStack[] bag = module.getFishBagManager().getBag(player.getUniqueId());
            if (bag != null) {
                boolean bagChanged = false;
                for (int slot = 0; slot < bag.length; slot++) {
                    ItemStack i = bag[slot];
                    if (i != null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) {
                        while (removed < amount && i.getAmount() > 0) {
                            i.setAmount(i.getAmount() - 1);
                            removed++;
                            bagChanged = true;
                        }
                        if (i.getAmount() <= 0) bag[slot] = null;
                    }
                }
                if (bagChanged) {
                    module.getFishBagManager().updateBag(player.getUniqueId(), bag);
                    module.getFishBagManager().saveBag(player.getUniqueId());
                }
            }
        }
    }

    private int countFish(Player player, String species) {
        int c = 0;
        for (ItemStack i : player.getInventory().getContents()) {
            if (i != null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) {
                String sp = i.getItemMeta().getPersistentDataContainer().get(module.getSpeciesKey(), PersistentDataType.STRING);
                if (species.equalsIgnoreCase(sp)) c += i.getAmount();
            }
        }
        ItemStack[] bag = module.getFishBagManager().getBag(player.getUniqueId());
        if (bag != null) {
            for (ItemStack i : bag) {
                if (i != null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) {
                    String sp = i.getItemMeta().getPersistentDataContainer().get(module.getSpeciesKey(), PersistentDataType.STRING);
                    if (species.equalsIgnoreCase(sp)) c += i.getAmount();
                }
            }
        }
        return c;
    }

    private void takeFishAmount(Player player, String species, int amount) {
        int removed = 0;
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack i = player.getInventory().getItem(slot);
            if (i != null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) {
                String sp = i.getItemMeta().getPersistentDataContainer().get(module.getSpeciesKey(), PersistentDataType.STRING);
                if (species.equalsIgnoreCase(sp)) {
                    while (removed < amount && i.getAmount() > 0) {
                        i.setAmount(i.getAmount() - 1);
                        removed++;
                    }
                    if (i.getAmount() <= 0) player.getInventory().setItem(slot, null);
                }
            }
        }
        if (removed < amount) {
            ItemStack[] bag = module.getFishBagManager().getBag(player.getUniqueId());
            if (bag != null) {
                boolean bagChanged = false;
                for (int slot = 0; slot < bag.length; slot++) {
                    ItemStack i = bag[slot];
                    if (i != null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) {
                        String sp = i.getItemMeta().getPersistentDataContainer().get(module.getSpeciesKey(), PersistentDataType.STRING);
                        if (species.equalsIgnoreCase(sp)) {
                            while (removed < amount && i.getAmount() > 0) {
                                i.setAmount(i.getAmount() - 1);
                                removed++;
                                bagChanged = true;
                            }
                            if (i.getAmount() <= 0) bag[slot] = null;
                        }
                    }
                }
                if (bagChanged) {
                    module.getFishBagManager().updateBag(player.getUniqueId(), bag);
                    module.getFishBagManager().saveBag(player.getUniqueId());
                }
            }
        }
    }

    private boolean takeFish(Player player, String requiredSpecies, int amount) {
        if (countFish(player, requiredSpecies) >= amount) {
            takeFishAmount(player, requiredSpecies, amount);
            return true;
        }
        return false;
    }
}
