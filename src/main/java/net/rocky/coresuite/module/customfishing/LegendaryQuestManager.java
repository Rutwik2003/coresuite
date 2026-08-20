package net.rocky.coresuite.module.customfishing;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.module.customfishing.FishingEnchantManager.CustomEnchant;
import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.gui.GuiButton;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class LegendaryQuestManager implements Listener {
    private final CoreSuitePlugin plugin;
    private final CustomFishingModule module;
    
    private final NamespacedKey compassKey;
    private final NamespacedKey trackerKey;
    
    // UUID of player to current quest stage (0 = not started, 5 = finished)
    private final Map<UUID, Integer> questStages = new ConcurrentHashMap<>();
    private final java.util.Set<UUID> activeRituals = java.util.concurrent.ConcurrentHashMap.newKeySet();

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
                if (item != null && item.getType() == Material.COMPASS && item.hasItemMeta()) {
                    if (item.getItemMeta().getPersistentDataContainer().has(compassKey, PersistentDataType.STRING)) {
                        CompassMeta meta = (CompassMeta) item.getItemMeta();
                        Location lode = meta.getLodestone();
                        if (lode != null) {
                            if (p.getWorld().equals(lode.getWorld())) {
                                double dist = p.getLocation().distance(lode);
                                if (dist < 15.0) {
                                    p.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("&e&lYou have reached the spot! Press 'Q' (Drop) to summon him!"));
                                } else {
                                    p.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("&bDistance to Ancient Angler: &e" + (int)dist + "m"));
                                }
                            } else {
                                p.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("&cThe compass needle spins randomly... (Wrong World)"));
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
    }
    
    public int getStage(UUID uuid) {
        return questStages.getOrDefault(uuid, 0);
    }
    
    public void setStage(UUID uuid, int stage) {
        questStages.put(uuid, stage);
        plugin.getDatabaseManager().execute("INSERT INTO coresuite_fisherman_quest (uuid, stage) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET stage = excluded.stage", uuid.toString(), stage);
    }

    public void giveMap(Player player) {
        ItemStack compass = new ItemStack(Material.COMPASS);
        CompassMeta meta = (CompassMeta) compass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.legacyColorize("&6&lLegendary Fisherman's Compass"));
            List<String> lore = new ArrayList<>();
            lore.add(TextUtil.legacyColorize("&7An ancient compass pulling"));
            lore.add(TextUtil.legacyColorize("&7towards a hidden coastline..."));
            lore.add(TextUtil.legacyColorize(""));
            lore.add(TextUtil.legacyColorize("&eToss (Drop) this compass to check your"));
            lore.add(TextUtil.legacyColorize("&edistance, or to summon him if nearby!"));
            meta.setLore(lore);
            
            int targetX = player.getLocation().getBlockX() + ThreadLocalRandom.current().nextInt(-5000, 5000);
            int targetZ = player.getLocation().getBlockZ() + ThreadLocalRandom.current().nextInt(-5000, 5000);
            Location targetLoc = new Location(player.getWorld(), targetX, 62, targetZ);
            
            meta.setLodestone(targetLoc);
            meta.setLodestoneTracked(false);
            meta.getPersistentDataContainer().set(compassKey, PersistentDataType.STRING, player.getUniqueId().toString());
            compass.setItemMeta(meta);
            
            player.getInventory().addItem(compass);
            TextUtil.sendMessage(player, "&b? &lYou found the Legendary Fisherman's Compass! &7Follow it to uncover its secrets.");
        }
    }
    
    @EventHandler
    public void onCompassDrop(org.bukkit.event.player.PlayerDropItemEvent event) {
        if (event.getItemDrop().getItemStack().getType() != Material.COMPASS) return;
        
        Player player = event.getPlayer();
        ItemStack item = event.getItemDrop().getItemStack();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        
        if (meta.getPersistentDataContainer().has(compassKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
            
            String ownerUUIDStr = meta.getPersistentDataContainer().get(compassKey, PersistentDataType.STRING);
            if (!player.getUniqueId().toString().equals(ownerUUIDStr)) {
                TextUtil.sendMessage(player, "&cThis ancient compass does not react to you.");
                return;
            }
            
            if (meta instanceof CompassMeta) {
                Location lodestone = ((CompassMeta) meta).getLodestone();
                if (lodestone != null) {
                    if (lodestone.getWorld() != null && !lodestone.getWorld().equals(player.getWorld())) {
                        TextUtil.sendMessage(player, "&cThe compass needle spins randomly... (Wrong World)");
                        return;
                    }
                    double dist = player.getLocation().distance(lodestone);
                    if (dist < 15.0) {
                        performRitual(player, item);
                    } else {
                        TextUtil.sendMessage(player, "&bThe compass hums softly... you are &e" + (int)dist + " &bblocks away.");
                    }
                }
            }
        }
    }

    @EventHandler
    public void onCompassInteract(PlayerInteractEvent event) {
        if (event.getItem() == null || event.getItem().getType() != Material.COMPASS) return;
        if (event.getItem().getItemMeta() != null && event.getItem().getItemMeta().getPersistentDataContainer().has(compassKey, PersistentDataType.STRING)) {
            event.setCancelled(true);
            if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_AIR || event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
                TextUtil.sendMessage(event.getPlayer(), "&e&lTip: &7Press &f'Q' &7(Drop) while holding the compass to use it! (This avoids WorldEdit bugs)");
            }
        }
    }
    
    private void performRitual(Player player, ItemStack item) {
        if (activeRituals.contains(player.getUniqueId())) return;
        activeRituals.add(player.getUniqueId());

        TextUtil.sendMessage(player, "&bThe compass shatters in your hands, revealing its true form!");
        player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1.0f, 1.0f);
        
        CompassMeta meta = (CompassMeta) item.getItemMeta();
        meta.setDisplayName(TextUtil.legacyColorize("&b&lAngler's Tracker"));
        List<String> lore = new ArrayList<>();
        lore.add(TextUtil.legacyColorize("&7Permanently tracks the location of"));
        lore.add(TextUtil.legacyColorize("&7the Legendary Ancient Angler."));
        meta.setLore(lore);
        meta.getPersistentDataContainer().remove(compassKey);
        meta.getPersistentDataContainer().set(trackerKey, PersistentDataType.STRING, player.getUniqueId().toString());
        
        Location spawnLoc = player.getLocation().add(player.getLocation().getDirection().multiply(2));
        spawnLoc.setY(player.getWorld().getHighestBlockYAt(spawnLoc) + 1);
        
        meta.setLodestone(spawnLoc);
        meta.setLodestoneTracked(false);
        item.setItemMeta(meta);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!player.isOnline()) {
                    activeRituals.remove(player.getUniqueId());
                    this.cancel();
                    return;
                }
                
                if (ticks == 0) {
                    player.getWorld().strikeLightningEffect(spawnLoc);
                    player.getWorld().playSound(spawnLoc, Sound.AMBIENT_UNDERWATER_ENTER, 2.0f, 0.5f);
                    TextUtil.sendMessage(player, "&3&oThe waters begin to churn violently...");
                } else if (ticks < 60) {
                    double radius = 3.0 - (ticks / 25.0);
                    for (int i = 0; i < 5; i++) {
                        double angle = Math.random() * Math.PI * 2;
                        double x = Math.cos(angle) * radius;
                        double z = Math.sin(angle) * radius;
                        player.getWorld().spawnParticle(org.bukkit.Particle.SPLASH, spawnLoc.clone().add(x, 0, z), 10, 0.1, 0.1, 0.1, 0.1);
                        player.getWorld().spawnParticle(org.bukkit.Particle.BUBBLE_POP, spawnLoc.clone().add(x, -1, z), 5, 0.1, 0.5, 0.1, 0.1);
                    }
                    if (ticks % 10 == 0) {
                        player.getWorld().playSound(spawnLoc, Sound.ENTITY_DROWNED_SHOOT, 1.0f, 0.8f);
                    }
                } else if (ticks == 60) {
                    
                    player.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, spawnLoc, 500, 1.0, 1.0, 1.0, 0.1);
                    player.getWorld().playSound(spawnLoc, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 1.0f);
                    player.getWorld().playSound(spawnLoc, Sound.ENTITY_GENERIC_SPLASH, 2.0f, 1.0f);
                    
                    module.getLegendaryNPCManager().spawnLegendaryNPC(player.getUniqueId(), spawnLoc);
                    Bukkit.broadcastMessage(TextUtil.legacyColorize("&b&l[Server] &eThe Legendary Ancient Angler has emerged from the depths! Summoned by " + player.getName() + "!"));
                    
                    activeRituals.remove(player.getUniqueId());
                    this.cancel();
                }
                ticks += 2;
            }
        }.runTaskTimer(plugin, 0L, 2L);
    }

    @EventHandler
    public void onInteractNPC(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof org.bukkit.entity.Entity)) return;
        
        org.bukkit.entity.Entity villager = (org.bukkit.entity.Entity) event.getRightClicked();
        if (villager.getPersistentDataContainer().has(module.getLegendaryNPCManager().getLegendaryKey(), PersistentDataType.STRING)) {
            event.setCancelled(true);
            String ownerId = villager.getPersistentDataContainer().get(module.getLegendaryNPCManager().getLegendaryKey(), PersistentDataType.STRING);
            
            if (ownerId != null && !ownerId.equals(event.getPlayer().getUniqueId().toString())) {
                TextUtil.sendMessage(event.getPlayer(), "&3&oThe Ancient Angler stares deeply into the waters, awaiting his true summoner...");
                return;
            }
            
            openQuestGUI(event.getPlayer(), villager);
        }
    }
    
                private void openQuestGUI(Player player, org.bukkit.entity.Entity npc) {
        int stage = getStage(player.getUniqueId());
        Gui gui = new Gui("&9Ancient Angler", 3);
        gui.fillBorder(Material.BLUE_STAINED_GLASS_PANE);

        if (stage == 0) {
            ItemStack info = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta m = info.getItemMeta();
            m.setDisplayName(TextUtil.legacyColorize("&bThe Awakening"));
            m.setLore(wrapDialogue("&e&lAncient Angler: &7\"Who awakens me from the slumber of the deep? ...Hm? Let me look at you. My eyes aren't what they used to be... *sniff sniff* ...Ah, yes. I know that smell anywhere. The undeniable stench of sea salt, fish guts, and the open ocean. You're an angler, aren't you? Heh. You remind me of myself, back before my joints turned to coral. In my day, we didn't use fancy lures. We wrestled Leviathans with our bare hands! But I digress... an old man's memory is like the tide, always drifting out. You summoned me for a reason. Tell me, little guppy... what is it you truly seek? Do you seek unimaginable wealth, or to master the endless ocean?\""));
            info.setItemMeta(m);
            gui.setItem(4, new GuiButton(info, e -> {}));

            ItemStack greed = new ItemStack(Material.GOLD_BLOCK);
            ItemMeta mGreed = greed.getItemMeta();
            mGreed.setDisplayName(TextUtil.legacyColorize("&e&lPath of Greed"));
            List<String> lGreed = new ArrayList<>();
            lGreed.add(TextUtil.legacyColorize("&7\"I seek unimaginable wealth.\""));
            lGreed.add(TextUtil.legacyColorize(""));
            lGreed.add(TextUtil.legacyColorize("&cLocks you into the Path of Wealth."));
            mGreed.setLore(lGreed);
            greed.setItemMeta(mGreed);
            gui.setItem(11, new GuiButton(greed, e -> {
                setStage(player.getUniqueId(), 101);
                TextUtil.sendMessage(player, "&e&lAncient Angler: &7\"Oho! Unimaginable wealth, you say? Your eyes gleam like a freshly gutted cod!\"");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
                openQuestGUI(player, npc);
            }));

            ItemStack mastery = new ItemStack(Material.ENCHANTED_BOOK);
            ItemMeta mMastery = mastery.getItemMeta();
            mMastery.setDisplayName(TextUtil.legacyColorize("&b&lPath of Mastery"));
            List<String> lMastery = new ArrayList<>();
            lMastery.add(TextUtil.legacyColorize("&7\"I seek to master the endless ocean.\""));
            lMastery.add(TextUtil.legacyColorize(""));
            lMastery.add(TextUtil.legacyColorize("&cLocks you into the Path of Mastery."));
            mMastery.setLore(lMastery);
            mastery.setItemMeta(mMastery);
            gui.setItem(15, new GuiButton(mastery, e -> {
                setStage(player.getUniqueId(), 201);
                TextUtil.sendMessage(player, "&e&lAncient Angler: &7\"Mastery? Ha! A lofty goal for a guppy.\"");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_YES, 1f, 1f);
                openQuestGUI(player, npc);
            }));
            gui.open(player);
            return;
        }

        if (stage == 999) {
            ItemStack info = new ItemStack(Material.BARRIER);
            ItemMeta m = info.getItemMeta();
            m.setDisplayName(TextUtil.legacyColorize("&cThe Final Departure"));
            m.setLore(wrapDialogue("&e&lAncient Angler: &7\"Your fate is sealed. I have nothing left to teach you.\""));
            info.setItemMeta(m);
            gui.setItem(13, new GuiButton(info, e -> player.closeInventory()));
            gui.open(player);
            return;
        }

        // ---------- PATH OF GREED (100s) ----------
        if (stage == 101) {
            ItemStack info = new ItemStack(Material.PAPER);
            ItemMeta m = info.getItemMeta();
            m.setDisplayName(TextUtil.legacyColorize("&eTrial 1: The Shimmering Shoal"));
            List<String> l = wrapDialogue("&e&lAncient Angler: &7\"Greed is a heavy anchor, little one... let us see if you can carry it. Go fetch me the vibrant colors of the coral reef. Chop chop!\"");
            l.add("");
            l.add(TextUtil.legacyColorize("&cTask: &fBring 10 Neon Tetras & 10 Angelfish"));
            m.setLore(l);
            info.setItemMeta(m);
            gui.setItem(13, new GuiButton(info, e -> {
                if (countFish(player, "Neon Tetra") >= 10 && countFish(player, "Angelfish") >= 10) {
                    takeFishAmount(player, "Neon Tetra", 10);
                    takeFishAmount(player, "Angelfish", 10);
                    setStage(player.getUniqueId(), 102);
                    TextUtil.sendMessage(player, "&aYou completed the trial!");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                    openQuestGUI(player, npc);
                } else {
                    TextUtil.sendMessage(player, "&cYou do not have the required fish! (Need 10 Neon Tetras, 10 Angelfish)");
                    player.closeInventory();
                }
            }));
        } else if (stage == 102) {
            ItemStack info = new ItemStack(Material.PAPER);
            ItemMeta m = info.getItemMeta();
            m.setDisplayName(TextUtil.legacyColorize("&eTrial 2: The Sunken Gold"));
            List<String> l = wrapDialogue("&e&lAncient Angler: &7\"Hah! Pretty colors, but lacking true value. Reminds me of a mermaid I once knew... but that's a tale for another time. Dive into the dark waters and retrieve a creature of pure, untarnished gold.\"");
            l.add("");
            l.add(TextUtil.legacyColorize("&cTask: &fBring 1 Golden Carp"));
            m.setLore(l);
            info.setItemMeta(m);
            gui.setItem(13, new GuiButton(info, e -> processFishTurnIn(player, "Golden Carp", 103, "&aYou completed the trial!", npc)));
        } else if (stage == 103) {
            ItemStack info = new ItemStack(Material.GOLD_BLOCK);
            ItemMeta m = info.getItemMeta();
            m.setDisplayName(TextUtil.legacyColorize("&eFinal Trial: The Exchange"));
            List<String> l = wrapDialogue("&e&lAncient Angler: &7\"You have proven your avarice. Now, for the final sacrifice. The abyss holds a creature of magma and gold. Bring it to me, and I shall make you rich beyond your wildest dreams!\"");
            l.add("");
            l.add(TextUtil.legacyColorize("&cTask: &fBring 1 Legendary Lava Particle"));
            m.setLore(l);
            info.setItemMeta(m);
            gui.setItem(13, new GuiButton(info, e -> {
                if (takeFish(player, "Lava Particle")) {
                    setStage(player.getUniqueId(), 999);
                    ItemStack mythical = module.createFishItem("&6&lMythical Abyssal Leviathan", "TROPICAL_FISH", 500.0, 1000.0, ""); 
                    ItemMeta meta = mythical.getItemMeta();
                    if (meta != null) {
                        meta.getPersistentDataContainer().set(module.getSpeciesKey(), PersistentDataType.STRING, "mythical_leviathan");
                        mythical.setItemMeta(meta);
                    }
                    player.getInventory().addItem(mythical);
                    TextUtil.sendMessage(player, "&e&oThe Angler crafts the essence into a shimmering golden leviathan. \"May your pockets be full!\" he shouts.");
                    completeRitual(player, npc);
                }
            }));
        }

        // ---------- PATH OF MASTERY (200s) ----------
        else if (stage == 201) {
            ItemStack info = new ItemStack(Material.PAPER);
            ItemMeta m = info.getItemMeta();
            m.setDisplayName(TextUtil.legacyColorize("&bTrial 1: The Dedication"));
            List<String> l = wrapDialogue("&e&lAncient Angler: &7\"Mastery requires patience... something few young ones possess these days. Prove your dedication to the rod, just as I did centuries ago!\"");
            l.add("");
            l.add(TextUtil.legacyColorize("&cTask: &fBring 20 Custom Fish of any kind"));
            m.setLore(l);
            info.setItemMeta(m);
            gui.setItem(13, new GuiButton(info, e -> {
                int count = 0;
                for (ItemStack i : player.getInventory().getContents()) {
                    if (i != null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) {
                        count += i.getAmount();
                    }
                }
                if (count >= 20) {
                    int removed = 0;
                    for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
                        ItemStack i = player.getInventory().getItem(slot);
                        if (i != null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) {
                            while (removed < 20 && i.getAmount() > 0) {
                                i.setAmount(i.getAmount() - 1);
                                removed++;
                            }
                        }
                    }
                    setStage(player.getUniqueId(), 202);
                    TextUtil.sendMessage(player, "&aYou completed the trial!");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                    openQuestGUI(player, npc);
                } else {
                    TextUtil.sendMessage(player, "&cYou do not have enough custom fish. (" + count + "/20)");
                    player.closeInventory();
                }
            }));
        } else if (stage == 202) {
            ItemStack info = new ItemStack(Material.PAPER);
            ItemMeta m = info.getItemMeta();
            m.setDisplayName(TextUtil.legacyColorize("&bTrial 2: The Frozen Heart"));
            List<String> l = wrapDialogue("&e&lAncient Angler: &7\"Not bad... you have patience. Now, show me endurance! When I was your age, I traveled to the frozen seas, where the water bites like a blade. Go on, fetch me a catch from the ice!\"");
            l.add("");
            l.add(TextUtil.legacyColorize("&cTask: &fBring 1 Legendary Char"));
            m.setLore(l);
            info.setItemMeta(m);
            gui.setItem(13, new GuiButton(info, e -> processFishTurnIn(player, "Char", 203, "&aYou completed the trial!", npc)));
        } else if (stage == 203) {
            ItemStack info = new ItemStack(Material.PAPER);
            ItemMeta m = info.getItemMeta();
            m.setDisplayName(TextUtil.legacyColorize("&bTrial 3: The Jungle Predator"));
            List<String> l = wrapDialogue("&e&lAncient Angler: &7\"The cold didn't break you? Color me impressed! But the humid rivers hide fierce predators. Catch the king of the jungle rapids. Try not to lose a finger!\"");
            l.add("");
            l.add(TextUtil.legacyColorize("&cTask: &fBring 1 Legendary Cherry Salmon"));
            m.setLore(l);
            info.setItemMeta(m);
            gui.setItem(13, new GuiButton(info, e -> processFishTurnIn(player, "Cherry Salmon", 204, "&aYou completed the trial!", npc)));
        } else if (stage == 204) {
            ItemStack info = new ItemStack(Material.ENCHANTED_BOOK);
            ItemMeta m = info.getItemMeta();
            m.setDisplayName(TextUtil.legacyColorize("&bFinal Trial: The Ascension"));
            List<String> l = wrapDialogue("&e&lAncient Angler: &7\"Ice and earth, conquered by a mere mortal! Well, only the fire of the deep remains. Bring me the essence of the abyss, and I shall pass on my greatest secret.\"");
            l.add("");
            l.add(TextUtil.legacyColorize("&cTask: &fBring 1 Legendary Lava Particle"));
            m.setLore(l);
            info.setItemMeta(m);
            gui.setItem(13, new GuiButton(info, e -> {
                ItemStack hand = player.getInventory().getItemInMainHand();
                if (hand.getType() != Material.FISHING_ROD) {
                    TextUtil.sendMessage(player, "&cYou must be holding the Fishing Rod you want to enchant in your main hand!");
                    player.closeInventory();
                    return;
                }
                if (takeFish(player, "Lava Particle")) {
                    setStage(player.getUniqueId(), 999);
                    module.getEnchantManager().setEnchantLevel(hand, CustomEnchant.TIRELESS_CASTER, 3);
                    TextUtil.sendMessage(player, "&b&oThe Angler weaves the essence into your rod, granting you Tireless Caster III!");
                    completeRitual(player, npc);
                }
            }));
        }
        gui.open(player);
    }

    private List<String> wrapDialogue(String text) {
        List<String> wrapped = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder line = new StringBuilder();
        for (String word : words) {
            if (line.length() + word.length() > 40) {
                wrapped.add(TextUtil.legacyColorize(line.toString().trim()));
                line = new StringBuilder("&7");
            }
            line.append(word).append(" ");
        }
        if (line.length() > 0) wrapped.add(TextUtil.legacyColorize(line.toString().trim()));
        return wrapped;
    }

    private int countFish(Player player, String species) {
        int c = 0;
        // Count in inventory
        for (ItemStack i : player.getInventory().getContents()) {
            if (i != null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) {
                String sp = i.getItemMeta().getPersistentDataContainer().get(module.getSpeciesKey(), PersistentDataType.STRING);
                if (species.equalsIgnoreCase(sp)) c += i.getAmount();
            }
        }
        // Count in bag
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
        // Remove from inventory
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
        // Remove from bag
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

    private boolean takeFish(Player player, String requiredSpecies) {
        boolean removed = false;
        // Try inventory
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack i = player.getInventory().getItem(slot);
            if (i != null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) {
                String sp = i.getItemMeta().getPersistentDataContainer().get(module.getSpeciesKey(), PersistentDataType.STRING);
                if (requiredSpecies.equalsIgnoreCase(sp)) {
                    i.setAmount(i.getAmount() - 1);
                    if (i.getAmount() <= 0) player.getInventory().setItem(slot, null);
                    removed = true;
                    break;
                }
            }
        }
        // Try bag
        if (!removed) {
            ItemStack[] bag = module.getFishBagManager().getBag(player.getUniqueId());
            if (bag != null) {
                for (int slot = 0; slot < bag.length; slot++) {
                    ItemStack i = bag[slot];
                    if (i != null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) {
                        String sp = i.getItemMeta().getPersistentDataContainer().get(module.getSpeciesKey(), PersistentDataType.STRING);
                        if (requiredSpecies.equalsIgnoreCase(sp)) {
                            i.setAmount(i.getAmount() - 1);
                            if (i.getAmount() <= 0) bag[slot] = null;
                            removed = true;
                            module.getFishBagManager().updateBag(player.getUniqueId(), bag);
                            module.getFishBagManager().saveBag(player.getUniqueId());
                            break;
                        }
                    }
                }
            }
        }
        if (!removed) {
            TextUtil.sendMessage(player, "&cYou do not have the required fish (" + requiredSpecies + ")!");
            player.closeInventory();
            return false;
        }
        return true;
    }

    private void completeRitual(Player player, org.bukkit.entity.Entity npc) {
        player.closeInventory();
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        npc.remove();
        player.getWorld().spawnParticle(org.bukkit.Particle.CLOUD, npc.getLocation(), 100, 1.0, 1.0, 1.0, 0.1);
        player.playSound(npc.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
    }

    private void processFishTurnIn(Player player, String requiredSpecies, int nextStage, String successMsg, org.bukkit.entity.Entity npc) {
        if (takeFish(player, requiredSpecies)) {
            setStage(player.getUniqueId(), nextStage);
            TextUtil.sendMessage(player, successMsg);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            openQuestGUI(player, npc);
        }
    }
}

