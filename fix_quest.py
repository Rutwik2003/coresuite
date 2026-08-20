content = '''package net.rocky.coresuite.module.customfishing;

import net.rocky.coresuite.CoreSuitePlugin;
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
                        player.getWorld().spawnParticle(org.bukkit.Particle.WATER_SPLASH, spawnLoc.clone().add(x, 0, z), 10, 0.1, 0.1, 0.1, 0.1);
                        player.getWorld().spawnParticle(org.bukkit.Particle.BUBBLE_POP, spawnLoc.clone().add(x, -1, z), 5, 0.1, 0.5, 0.1, 0.1);
                    }
                    if (ticks % 10 == 0) {
                        player.getWorld().playSound(spawnLoc, Sound.ENTITY_DROWNED_SHOOT, 1.0f, 0.8f);
                    }
                } else if (ticks == 60) {
                    player.getWorld().spawnParticle(org.bukkit.Particle.EXPLOSION, spawnLoc, 3);
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
        Gui gui = new Gui("&9Legendary Angler", 3);
        gui.fillBorder(Material.BLUE_STAINED_GLASS_PANE);
        
        if (stage == 0) {
            ItemStack info = new ItemStack(Material.PAPER);
            ItemMeta m = info.getItemMeta();
            m.setDisplayName(TextUtil.legacyColorize("&bThe First Trial"));
            List<String> l = new ArrayList<>();
            l.add(TextUtil.legacyColorize("&7The Angler looks at you silently."));
            l.add(TextUtil.legacyColorize("&7He wants proof of your dedication."));
            l.add(TextUtil.legacyColorize(""));
            l.add(TextUtil.legacyColorize("&eBring him 20 Custom Fish of any kind."));
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
                    setStage(player.getUniqueId(), 1);
                    TextUtil.sendMessage(player, "&aYou completed the first trial!");
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
                    player.closeInventory();
                } else {
                    TextUtil.sendMessage(player, "&cYou do not have enough custom fish. (" + count + "/20)");
                    player.closeInventory();
                }
            }));
        } else if (stage == 1) {
            ItemStack info = new ItemStack(Material.PAPER);
            ItemMeta m = info.getItemMeta();
            m.setDisplayName(TextUtil.legacyColorize("&bThe Second Trial"));
            List<String> l = new ArrayList<>();
            l.add(TextUtil.legacyColorize("&7The Angler nods slowly."));
            l.add(TextUtil.legacyColorize("&7Now, seek a creature of the dark caves."));
            l.add(TextUtil.legacyColorize(""));
            l.add(TextUtil.legacyColorize("&eBring him 1 Epic Batfish."));
            m.setLore(l);
            info.setItemMeta(m);
            gui.setItem(13, new GuiButton(info, e -> processFishTurnIn(player, "batfish", 2, "&aYou completed the second trial!")));
        } else if (stage == 2) {
            ItemStack info = new ItemStack(Material.PAPER);
            ItemMeta m = info.getItemMeta();
            m.setDisplayName(TextUtil.legacyColorize("&bThe Third Trial"));
            List<String> l = new ArrayList<>();
            l.add(TextUtil.legacyColorize("&7The Angler points to the icy seas."));
            l.add(TextUtil.legacyColorize("&7Only the cold-hearted survive there."));
            l.add(TextUtil.legacyColorize(""));
            l.add(TextUtil.legacyColorize("&eBring him 1 Legendary Char."));
            m.setLore(l);
            info.setItemMeta(m);
            gui.setItem(13, new GuiButton(info, e -> processFishTurnIn(player, "char", 3, "&aYou completed the third trial!")));
        } else if (stage == 3) {
            ItemStack info = new ItemStack(Material.PAPER);
            ItemMeta m = info.getItemMeta();
            m.setDisplayName(TextUtil.legacyColorize("&bThe Fourth Trial"));
            List<String> l = new ArrayList<>();
            l.add(TextUtil.legacyColorize("&7The Angler smells the humid air."));
            l.add(TextUtil.legacyColorize("&7A jungle predator is required."));
            l.add(TextUtil.legacyColorize(""));
            l.add(TextUtil.legacyColorize("&eBring him 1 Legendary Cherry Salmon."));
            m.setLore(l);
            info.setItemMeta(m);
            gui.setItem(13, new GuiButton(info, e -> processFishTurnIn(player, "cherry_salmon", 4, "&aYou completed the fourth trial!")));
        } else if (stage == 4) {
            ItemStack info = new ItemStack(Material.PAPER);
            ItemMeta m = info.getItemMeta();
            m.setDisplayName(TextUtil.legacyColorize("&bThe Final Trial"));
            List<String> l = new ArrayList<>();
            l.add(TextUtil.legacyColorize("&7The Angler whispers of the deep abyss."));
            l.add(TextUtil.legacyColorize("&7A creature forged in subterranean heat."));
            l.add(TextUtil.legacyColorize(""));
            l.add(TextUtil.legacyColorize("&eBring him 1 Legendary Lava Particle."));
            m.setLore(l);
            info.setItemMeta(m);
            gui.setItem(13, new GuiButton(info, e -> {
                boolean hasFish = false;
                for (ItemStack i : player.getInventory().getContents()) {
                    if (i != null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) {
                        String sp = i.getItemMeta().getPersistentDataContainer().get(module.getSpeciesKey(), PersistentDataType.STRING);
                        if ("lava_particle".equalsIgnoreCase(sp)) {
                            hasFish = true;
                            i.setAmount(i.getAmount() - 1);
                            break;
                        }
                    }
                }
                if (hasFish) {
                    ItemStack hand = player.getInventory().getItemInMainHand();
                    if (hand.getType() == Material.FISHING_ROD) {
                        setStage(player.getUniqueId(), 5);
                        player.closeInventory();
                        
                        module.getEnchantManager().setEnchantLevel(hand, CustomEnchant.TIRELESS_CASTER, 3);
                        TextUtil.sendMessage(player, "&a&lThe Ancient Angler blesses your fishing rod with Tireless Caster III!");
                        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                        
                        npc.remove();
                        player.getWorld().spawnParticle(org.bukkit.Particle.BUBBLE_POP, npc.getLocation(), 100, 1.0, 1.0, 1.0, 0.1);
                        player.playSound(npc.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                    } else {
                        TextUtil.sendMessage(player, "&cYou have the fish, but you must HOLD the Fishing Rod you want to enchant!");
                        player.getInventory().addItem(module.getFishItem("lava_particle", player.getName(), 10.0));
                        player.closeInventory();
                    }
                } else {
                    TextUtil.sendMessage(player, "&cYou do not have the required fish.");
                    player.closeInventory();
                }
            }));
        } else {
            ItemStack info = new ItemStack(Material.BARRIER);
            ItemMeta m = info.getItemMeta();
            m.setDisplayName(TextUtil.legacyColorize("&cQuest Completed"));
            List<String> l = new ArrayList<>();
            l.add(TextUtil.legacyColorize("&7You have already learned all"));
            l.add(TextUtil.legacyColorize("&7the secrets from the Angler."));
            m.setLore(l);
            info.setItemMeta(m);
            gui.setItem(13, new GuiButton(info, e -> player.closeInventory()));
        }
        gui.open(player);
    }
    
    private void processFishTurnIn(Player player, String requiredSpecies, int nextStage, String successMsg) {
        boolean hasFish = false;
        for (ItemStack i : player.getInventory().getContents()) {
            if (i != null && i.hasItemMeta() && i.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) {
                String sp = i.getItemMeta().getPersistentDataContainer().get(module.getSpeciesKey(), PersistentDataType.STRING);
                if (requiredSpecies.equalsIgnoreCase(sp)) {
                    hasFish = true;
                    i.setAmount(i.getAmount() - 1);
                    break;
                }
            }
        }
        if (hasFish) {
            setStage(player.getUniqueId(), nextStage);
            TextUtil.sendMessage(player, successMsg);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
            player.closeInventory();
        } else {
            TextUtil.sendMessage(player, "&cYou do not have the required fish.");
            player.closeInventory();
        }
    }
}
'''
with open("src/main/java/net/rocky/coresuite/module/customfishing/LegendaryQuestManager.java", "w") as f:
    f.write(content)
