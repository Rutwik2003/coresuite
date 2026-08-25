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
    private final Map<UUID, Long> pearlCooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Long> summonCooldowns = new ConcurrentHashMap<>();

    private final Map<UUID, Long> chatStateTimestamps = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> failCounters = new ConcurrentHashMap<>();

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
                if (item != null && (item.getType() == Material.HEART_OF_THE_SEA || item.getType() == Material.RECOVERY_COMPASS || item.getType() == Material.COMPASS) && item.hasItemMeta()) {
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
                                    p.sendActionBar(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("&e&lYou have reached the spot! Right-Click to summon him!"));
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
                // Migration: add new columns if missing
                try { s.execute("ALTER TABLE coresuite_fisherman_quest ADD COLUMN times_completed INTEGER DEFAULT 0"); } catch (Exception ignored) {}
                try { s.execute("ALTER TABLE coresuite_fisherman_quest ADD COLUMN times_got_tc INTEGER DEFAULT 0"); } catch (Exception ignored) {}
                try { s.execute("ALTER TABLE coresuite_fisherman_quest ADD COLUMN times_disrespected INTEGER DEFAULT 0"); } catch (Exception ignored) {}
                try { s.execute("ALTER TABLE coresuite_fisherman_quest ADD COLUMN fail_count INTEGER DEFAULT 0"); } catch (Exception ignored) {}
                try { s.execute("ALTER TABLE coresuite_fisherman_quest ADD COLUMN has_fossilized_anchor INTEGER DEFAULT 0"); } catch (Exception ignored) {}
                try { s.execute("ALTER TABLE coresuite_fisherman_quest ADD COLUMN has_stormcaller_trident INTEGER DEFAULT 0"); } catch (Exception ignored) {}
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
        loadFailCount(uuid);
    }

    public void unloadPlayer(UUID uuid) {
        questStages.remove(uuid);
        chatState.remove(uuid);
        introOptions.remove(uuid);
        inDialogueSequence.remove(uuid);
        pearlCooldowns.remove(uuid);
        summonCooldowns.remove(uuid);
        chatStateTimestamps.remove(uuid);
        failCounters.remove(uuid);
    }
    
    public int getStage(UUID uuid) { return questStages.getOrDefault(uuid, 0); }
    
    public void setStage(UUID uuid, int stage) {
        questStages.put(uuid, stage);
        plugin.getDatabaseManager().execute("INSERT INTO coresuite_fisherman_quest (uuid, stage) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET stage = excluded.stage", uuid.toString(), stage);
    }

    public int getTimesCompleted(UUID uuid) {
        try (Connection c = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT times_completed FROM coresuite_fisherman_quest WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("times_completed");
        } catch (Exception e) { e.printStackTrace(); }
        return 0;
    }

    public void incrementTimesCompleted(UUID uuid) {
        plugin.getDatabaseManager().execute("UPDATE coresuite_fisherman_quest SET times_completed = times_completed + 1 WHERE uuid = ?", uuid.toString());
    }

    public int getTimesGotTC(UUID uuid) {
        try (Connection c = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT times_got_tc FROM coresuite_fisherman_quest WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("times_got_tc");
        } catch (Exception e) { e.printStackTrace(); }
        return 0;
    }

    public void incrementTimesGotTC(UUID uuid) {
        plugin.getDatabaseManager().execute("UPDATE coresuite_fisherman_quest SET times_got_tc = times_got_tc + 1 WHERE uuid = ?", uuid.toString());
    }

    public int getTimesDisrespected(UUID uuid) {
        try (Connection c = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT times_disrespected FROM coresuite_fisherman_quest WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("times_disrespected");
        } catch (Exception e) { e.printStackTrace(); }
        return 0;
    }

    public void incrementTimesDisrespected(UUID uuid) {
        plugin.getDatabaseManager().execute("UPDATE coresuite_fisherman_quest SET times_disrespected = times_disrespected + 1 WHERE uuid = ?", uuid.toString());
    }

    public boolean hasFossilizedAnchor(UUID uuid) {
        try (Connection c = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT has_fossilized_anchor FROM coresuite_fisherman_quest WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("has_fossilized_anchor") == 1;
        } catch (Exception e) { e.printStackTrace(); }
        return false;
    }

    public void setHasFossilizedAnchor(UUID uuid) {
        plugin.getDatabaseManager().execute("UPDATE coresuite_fisherman_quest SET has_fossilized_anchor = 1 WHERE uuid = ?", uuid.toString());
    }

    public boolean hasStormcallerTrident(UUID uuid) {
        try (Connection c = plugin.getDatabaseManager().getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT has_stormcaller_trident FROM coresuite_fisherman_quest WHERE uuid = ?")) {
            ps.setString(1, uuid.toString());
            ResultSet rs = ps.executeQuery();
            if (rs.next()) return rs.getInt("has_stormcaller_trident") == 1;
        } catch (Exception e) { e.printStackTrace(); }
        return false;
    }

    public void setHasStormcallerTrident(UUID uuid) {
        plugin.getDatabaseManager().execute("UPDATE coresuite_fisherman_quest SET has_stormcaller_trident = 1 WHERE uuid = ?", uuid.toString());
    }

    public void saveFailCount(UUID uuid, int count) {
        plugin.getDatabaseManager().execute("UPDATE coresuite_fisherman_quest SET fail_count = ? WHERE uuid = ?", count, uuid.toString());
    }

    public void loadFailCount(UUID uuid) {
        plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection c = plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT fail_count FROM coresuite_fisherman_quest WHERE uuid = ?")) {
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) failCounters.put(uuid, rs.getInt("fail_count"));
            } catch (Exception e) { e.printStackTrace(); }
        });
    }

    public boolean isInChatState(UUID uuid) { return chatState.containsKey(uuid); }

    private void setChatState(UUID uuid, String state) {
        chatState.put(uuid, state);
        chatStateTimestamps.put(uuid, System.currentTimeMillis());
        // Update NPC interaction timer
        module.getLegendaryNPCManager().updateInteraction(uuid);
    }

    public void giveMap(Player player) {
        ItemStack compass = new ItemStack(Material.RECOVERY_COMPASS);
        org.bukkit.inventory.meta.ItemMeta meta = compass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.legacyColorize("&b&lTidecaller's Pearl"));
            List<String> lore = new ArrayList<>();
            lore.add(TextUtil.legacyColorize("&7An ancient pearl whispering"));
            lore.add(TextUtil.legacyColorize("&7of a hidden coastline..."));
            lore.add(TextUtil.legacyColorize(""));
            lore.add(TextUtil.legacyColorize("&eRight-Click to reveal a magical"));
            lore.add(TextUtil.legacyColorize("&etrail, or summon him if nearby!"));
            meta.setLore(lore);
            meta.addEnchant(org.bukkit.enchantments.Enchantment.LURE, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            
            int targetX = player.getLocation().getBlockX() + java.util.concurrent.ThreadLocalRandom.current().nextInt(-5000, 5000);
            int targetZ = player.getLocation().getBlockZ() + java.util.concurrent.ThreadLocalRandom.current().nextInt(-5000, 5000);
            Location targetLoc = new Location(player.getWorld(), targetX, 62, targetZ);
            
            meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "lode_w"), org.bukkit.persistence.PersistentDataType.STRING, targetLoc.getWorld().getName());
            meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "lode_x"), org.bukkit.persistence.PersistentDataType.DOUBLE, targetLoc.getX());
            meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "lode_y"), org.bukkit.persistence.PersistentDataType.DOUBLE, targetLoc.getY());
            meta.getPersistentDataContainer().set(new org.bukkit.NamespacedKey(plugin, "lode_z"), org.bukkit.persistence.PersistentDataType.DOUBLE, targetLoc.getZ());
            meta.getPersistentDataContainer().set(compassKey, PersistentDataType.STRING, player.getUniqueId().toString());
            compass.setItemMeta(meta);
            
            java.util.Map<Integer, ItemStack> leftover = player.getInventory().addItem(compass);
            if (!leftover.isEmpty()) {
                player.getWorld().dropItem(player.getLocation(), leftover.get(0));
            }
            TextUtil.sendMessage(player, "&b🐟 &lYou found the Tidecaller's Pearl! &7Right-click it to uncover its secrets.");
        }
    }

    @EventHandler
    public void onCraft(org.bukkit.event.inventory.PrepareItemCraftEvent event) {
        if (event.getInventory().getMatrix() == null) return;
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (item != null && item.hasItemMeta()) {
                if (item.getItemMeta().getPersistentDataContainer().has(compassKey, PersistentDataType.STRING) ||
                    item.getItemMeta().getPersistentDataContainer().has(trackerKey, PersistentDataType.STRING)) {
                    event.getInventory().setResult(null);
                    break;
                }
            }
        }
    }

    @EventHandler
    public void onPrepareAnvil(org.bukkit.event.inventory.PrepareAnvilEvent event) {
        for (ItemStack item : new ItemStack[]{event.getInventory().getItem(0), event.getInventory().getItem(1)}) {
            if (item != null && item.hasItemMeta() &&
                item.getItemMeta().getPersistentDataContainer().has(
                    new NamespacedKey(plugin, "trophy_item"), PersistentDataType.STRING)) {
                event.setResult(null);
                return;
            }
        }
    }

    @EventHandler
    public void onInteractTracker(org.bukkit.event.player.PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) return;
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) return;
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
            
            if ((item.getType() == Material.HEART_OF_THE_SEA || item.getType() == Material.RECOVERY_COMPASS || item.getType() == Material.COMPASS)) {
                String wName = meta.getPersistentDataContainer().get(new org.bukkit.NamespacedKey(plugin, "lode_w"), org.bukkit.persistence.PersistentDataType.STRING);
                if (wName != null) {
                    org.bukkit.World w = org.bukkit.Bukkit.getWorld(wName);
                    if (w == null || !w.equals(player.getWorld())) {
                        TextUtil.sendMessage(player, "&cThe pearl's magic fades... (Wrong World)");
                        return;
                    }
                    double tx = meta.getPersistentDataContainer().get(new org.bukkit.NamespacedKey(plugin, "lode_x"), org.bukkit.persistence.PersistentDataType.DOUBLE);
                    double ty = meta.getPersistentDataContainer().get(new org.bukkit.NamespacedKey(plugin, "lode_y"), org.bukkit.persistence.PersistentDataType.DOUBLE);
                    double tz = meta.getPersistentDataContainer().get(new org.bukkit.NamespacedKey(plugin, "lode_z"), org.bukkit.persistence.PersistentDataType.DOUBLE);
                    Location lodestone = new Location(w, tx, ty, tz);
                    double dist = player.getLocation().distance(lodestone);
                    if (dist < 15.0) {
                        long lastSummon = summonCooldowns.getOrDefault(player.getUniqueId(), 0L);
                        if (System.currentTimeMillis() - lastSummon < 45000) {
                            TextUtil.sendMessage(player, "&cThe pearl is recharging its summoning magic... please wait.");
                            return;
                        }
                        summonCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
                        performRitual(player, item, key.equals(compassKey));
                    } else {
                        long lastTrail = pearlCooldowns.getOrDefault(player.getUniqueId(), 0L);
                        if (System.currentTimeMillis() - lastTrail < 10000) {
                            return;
                        }
                        pearlCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
                        TextUtil.sendMessage(player, "&bThe pearl hums softly... you are &e" + (int)dist + " &bblocks away.");
                        org.bukkit.util.Vector d = lodestone.toVector().subtract(player.getLocation().toVector());
                        d.setY(0);
                        if (d.lengthSquared() > 0) {
                            d.normalize();
                            org.bukkit.util.Vector up = new org.bukkit.util.Vector(0, 1, 0);
                            if (Math.abs(d.getY()) > 0.99) up = new org.bukkit.util.Vector(1, 0, 0);
                            org.bukkit.util.Vector u = d.clone().crossProduct(up).normalize();
                            org.bukkit.util.Vector v = d.clone().crossProduct(u).normalize();
                            
                            player.playSound(player.getLocation(), Sound.BLOCK_CONDUIT_AMBIENT, 1.0f, 1.5f);
                            new BukkitRunnable() {
                                int steps = 0;
                                Location startLoc = player.getLocation().add(0, 1.2, 0);
                                @Override
                                public void run() {
                                    if (steps > 40 || !player.isOnline()) {
                                        this.cancel();
                                        return;
                                    }
                                    
                                    for (int i = 0; i < 2; i++) {
                                        double forward = steps * 0.4;
                                        double radius = 0.5;
                                        double angle = steps * 0.6;
                                        
                                        org.bukkit.util.Vector offset1 = d.clone().multiply(forward)
                                                .add(u.clone().multiply(radius * Math.cos(angle)))
                                                .add(v.clone().multiply(radius * Math.sin(angle)));
                                        org.bukkit.util.Vector offset2 = d.clone().multiply(forward)
                                                .add(u.clone().multiply(radius * Math.cos(angle + Math.PI)))
                                                .add(v.clone().multiply(radius * Math.sin(angle + Math.PI)));
                                                
                                        Location pLoc1 = startLoc.clone().add(offset1);
                                        Location pLoc2 = startLoc.clone().add(offset2);
                                        
                                        pLoc1.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, pLoc1, 1, 0, 0, 0, 0.01);
                                        pLoc2.getWorld().spawnParticle(org.bukkit.Particle.SOUL_FIRE_FLAME, pLoc2, 1, 0, 0, 0, 0.01);
                                        steps++;
                                    }
                                }
                            }.runTaskTimer(plugin, 0L, 1L);
                        }
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
            lore.add(TextUtil.legacyColorize("&eRight-Click this pearl to reveal"));
            lore.add(TextUtil.legacyColorize("&ethe glowing path towards him!"));
            meta.setLore(lore);
            meta.addEnchant(org.bukkit.enchantments.Enchantment.LURE, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().remove(compassKey);
            meta.getPersistentDataContainer().set(trackerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            item.setItemMeta(meta);
            
            TextUtil.sendMessage(player, "&bThe pearl glows brightly in your hands, revealing its true form!");
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
        } else {
            TextUtil.sendMessage(player, "&bThe tracker pearl vibrates violently in your hands...");
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 1.0f, 2.0f);
        }
        
        Location spawnLoc = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(2));
        spawnLoc.setY(player.getLocation().getY());
        if (spawnLoc.getBlock().getType().isSolid()) {
            spawnLoc = player.getLocation();
        }
        
        org.bukkit.block.Block feetBlock = spawnLoc.getBlock();
        if (feetBlock.getType() == Material.WATER || feetBlock.getType() == Material.SEAGRASS || feetBlock.getType() == Material.KELP_PLANT || feetBlock.getType() == Material.KELP) {
            feetBlock.setType(Material.LILY_PAD);
            spawnLoc.add(0, 0.5, 0);
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

                    if (isFirstTime && module.getConfig().getBoolean("legendary-quest.broadcast-on-first-summon", true)) {
                        String soundName = module.getConfig().getString("legendary-quest.broadcast-sound", "ENTITY_WITHER_SPAWN");
                        float volume = (float) module.getConfig().getDouble("legendary-quest.broadcast-sound-volume", 0.5);
                        float pitch = (float) module.getConfig().getDouble("legendary-quest.broadcast-sound-pitch", 0.7);
                        Sound broadcastSound;
                        try { broadcastSound = Sound.valueOf(soundName); } catch (Exception e) { broadcastSound = Sound.ENTITY_WITHER_SPAWN; }
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            TextUtil.sendMessage(p, "&8&l\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550");
                            TextUtil.sendMessage(p, "&b&l\u2693 The Ancient Angler &7has been summoned from the");
                            TextUtil.sendMessage(p, "&7treacherous depths of the ocean by &e&l" + player.getName() + "&7...");
                            TextUtil.sendMessage(p, "&8&l\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550");
                            p.playSound(p.getLocation(), broadcastSound, volume, pitch);
                        }
                    }
                } else if (tick == 80) {
                    if (isFirstTime) {
                        setStage(u, 10);
                        int timesCompleted = getTimesCompleted(u);
                        int timesDisrespected = getTimesDisrespected(u);
                        new BukkitRunnable() {
                            int dTick = 0;
                            @Override
                            public void run() {
                                if (!player.isOnline()) { inDialogueSequence.remove(u); cancel(); return; }
                                if (dTick == 0) {
                                    // Choose intro based on history
                                    if (timesDisrespected > 0 && ThreadLocalRandom.current().nextBoolean()) {
                                        TextUtil.sendMessage(player, "&e[Ancient Angler]&f YOU DARE RETURN?!");
                                        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1f, 0.8f);
                                    } else if (timesCompleted == 0) {
                                        TextUtil.sendMessage(player, "&e[Ancient Angler]&f Who awakens the Ancient Angler from the depths...?");
                                    } else if (timesCompleted <= 2) {
                                        String[] lines = {
                                            "Ah... we meet again, mortal. The tides bring you back.",
                                            "The currents whisper your name again... Let us see what path the tides choose.",
                                            "You return. Good. The sea tests those who persist."
                                        };
                                        TextUtil.sendMessage(player, "&e[Ancient Angler]&f " + lines[ThreadLocalRandom.current().nextInt(lines.length)]);
                                    } else {
                                        String[] lines = {
                                            "You again? The depths grow weary of your visits.",
                                            "How many times must we dance this ritual, mortal?",
                                            "Your persistence borders on obsession. The sea respects that... barely."
                                        };
                                        TextUtil.sendMessage(player, "&e[Ancient Angler]&f " + lines[ThreadLocalRandom.current().nextInt(lines.length)]);
                                    }
                                } else if (dTick == 1) {
                                    if (timesDisrespected > 0 && timesCompleted == 0) {
                                        TextUtil.sendMessage(player, "&e[Ancient Angler]&f ...Fine. Perhaps you've learned.");
                                    } else if (timesCompleted == 0) {
                                        TextUtil.sendMessage(player, "&e[Ancient Angler]&f Ah... another mortal seeking the legends of the deep.");
                                    } else {
                                        TextUtil.sendMessage(player, "&e[Ancient Angler]&f The ocean remembers all, mortal. Let us continue.");
                                    }
                                } else if (dTick == 2) {
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
        } else if (stage == 601) {
            interactiveTurnIn(player, "turnin_601", "The sea hoards its riches beneath the coral thrones. Bring me a tax from the common waters.");
        } else if (stage == 602) {
            interactiveTurnIn(player, "turnin_602", "Find me the golden scale that glimmers at the bottom of murky rivers.");
        } else if (stage == 603) {
            interactiveTurnIn(player, "turnin_603", "Now bring me the armored jewels of the frozen waters. Two of them.");
        } else if (stage == 701) {
            interactiveTurnIn(player, "turnin_701", "The deep feeds on darkness. Bring me creatures born where light fears to reach.");
        } else if (stage == 702) {
            interactiveTurnIn(player, "turnin_702", "Fetch me the demons that swim through volcanic veins. Two of them.");
        } else if (stage == 703) {
            interactiveTurnIn(player, "turnin_703", "The ultimate darkness... bring me the wrasse forged in the nether's forge. Two of them.");
        } else if (stage == 801) {
            interactiveTurnIn(player, "turnin_801", "First, prove your desperation. Sacrifice the common to show your hunger.");
        } else if (stage == 802) {
            interactiveTurnIn(player, "turnin_802", "Good... now bring me something precious. A rare creature from the cold.");
        } else if (stage == 803) {
            interactiveTurnIn(player, "turnin_803", "Yes... YES! One final offering... bring me your finest catch!");
        } else if (stage == 901) {
            interactiveTurnIn(player, "turnin_901", "Lightning dances on the water's skin. Bring me the fish that rides the current.");
        } else if (stage == 902) {
            interactiveTurnIn(player, "turnin_902", "The storm's child hides in frozen rivers. Bring me the trout touched by winter.");
        } else if (stage == 903) {
            interactiveTurnIn(player, "turnin_903", "Finally... the beast that commands the jungle rivers with fury.");
        }
    }

    private void interactiveTurnIn(Player player, String state, String riddlePrompt) {
        setChatState(player.getUniqueId(), state);
        TextUtil.sendMessage(player, "&e[Ancient Angler]&f " + riddlePrompt);
        TextUtil.sendMessage(player, "");
        sendClickableOption(player, "&a&l[\u2726 Offer the Catch]", "&7Click to present your fish to the Angler", "1");
        sendClickableOption(player, "&e&l[\u2727 I Cannot Decipher]", "&7Click to admit confusion (no penalty)", "2");
        sendClickableOption(player, "&c&l[\u2717 Sacrifice for Answer]", "&7Click to pay $25,000 + a rare fish for a direct hint", "3");
        TextUtil.sendMessage(player, "");
        TextUtil.sendMessage(player, "&8&oClick an option above or type 1, 2, or 3 in chat.");
        TextUtil.sendMessage(player, "&8&oType 'cancel' to walk away. Expires in 2 minutes.");
    }

    private void sendClickableOption(Player player, String displayText, String hoverText, String number) {
        net.md_5.bungee.api.chat.TextComponent component = new net.md_5.bungee.api.chat.TextComponent(
            net.md_5.bungee.api.ChatColor.translateAlternateColorCodes('&', displayText)
        );
        component.setClickEvent(new net.md_5.bungee.api.chat.ClickEvent(
            net.md_5.bungee.api.chat.ClickEvent.Action.RUN_COMMAND, "/fish quest " + number
        ));
        component.setHoverEvent(new net.md_5.bungee.api.chat.HoverEvent(
            net.md_5.bungee.api.chat.HoverEvent.Action.SHOW_TEXT,
            new net.md_5.bungee.api.chat.ComponentBuilder(hoverText).create()
        ));
        player.spigot().sendMessage(component);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID u = player.getUniqueId();
        
        if (chatState.containsKey(u)) {
            // Check timeout (120 seconds)
            long setTime = chatStateTimestamps.getOrDefault(u, 0L);
            int timeoutSeconds = module.getConfig().getInt("legendary-quest.chat-timeout-seconds", 120);
            if (System.currentTimeMillis() - setTime > timeoutSeconds * 1000L) {
                chatState.remove(u);
                chatStateTimestamps.remove(u);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    TextUtil.sendMessage(player, "&e[Ancient Angler]&f *The Angler grows impatient and turns away*");
                    TextUtil.sendMessage(player, "&7Your dialogue with the Ancient Angler has timed out. Right-click the NPC to interact again.");
                });
                return; // let chat through
            }
            event.setCancelled(true);
            String msg = event.getMessage().trim();
            Bukkit.getScheduler().runTask(plugin, () -> processChatSync(player, u, msg));
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
            } else if (chosenPath == 6) {
                setStage(u, 601);
                sendLinesAndRelocate(player,
                    "&e[Ancient Angler]&f Fortune favors the bold, they say...",
                    "&e[Ancient Angler]&f The sea hoards its riches beneath the coral thrones. Bring me a tax from the common waters.");
            } else if (chosenPath == 7) {
                setStage(u, 701);
                sendLinesAndRelocate(player,
                    "&e[Ancient Angler]&f The depths call to those who dare listen...",
                    "&e[Ancient Angler]&f The deep feeds on darkness. Bring me creatures born where light fears to reach.");
            } else if (chosenPath == 8) {
                setStage(u, 801);
                sendLinesAndRelocate(player,
                    "&e[Ancient Angler]&f Desperation... I can smell it on you.",
                    "&e[Ancient Angler]&f First, prove your desperation. Sacrifice the common to show your hunger.");
            } else if (chosenPath == 9) {
                setStage(u, 901);
                sendLinesAndRelocate(player,
                    "&e[Ancient Angler]&f The storm respects no mortal...",
                    "&e[Ancient Angler]&f Lightning dances on the water's skin. Bring me the fish that rides the current.");
            } else if (chosenPath == 10) {
                // INSTANT TRAP - Path of Arrogance
                sendLinesAndPunishArrogance(player);
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

                else if (state.equals("turnin_601")) TextUtil.sendMessage(player, "&e[Ancient Angler]&f 20 Custom Fish. A tax from the common waters.");
                else if (state.equals("turnin_602")) TextUtil.sendMessage(player, "&e[Ancient Angler]&f A Golden Carp.");
                else if (state.equals("turnin_603")) TextUtil.sendMessage(player, "&e[Ancient Angler]&f Two Ranchu Goldfish.");
                else if (state.equals("turnin_701")) TextUtil.sendMessage(player, "&e[Ancient Angler]&f 5 Custom Fish from the deep.");
                else if (state.equals("turnin_702")) TextUtil.sendMessage(player, "&e[Ancient Angler]&f Two Demonic Netherfish.");
                else if (state.equals("turnin_703")) TextUtil.sendMessage(player, "&e[Ancient Angler]&f Two Netherwrasse.");
                else if (state.equals("turnin_801")) TextUtil.sendMessage(player, "&e[Ancient Angler]&f 10 Custom Fish.");
                else if (state.equals("turnin_802")) TextUtil.sendMessage(player, "&e[Ancient Angler]&f A Tundra Trout.");
                else if (state.equals("turnin_803")) TextUtil.sendMessage(player, "&e[Ancient Angler]&f A Legendary fish. Any of them.");
                else if (state.equals("turnin_901")) TextUtil.sendMessage(player, "&e[Ancient Angler]&f 5 Eels.");
                else if (state.equals("turnin_902")) TextUtil.sendMessage(player, "&e[Ancient Angler]&f 3 Tundra Trout.");
                else if (state.equals("turnin_903")) TextUtil.sendMessage(player, "&e[Ancient Angler]&f A Giant Snakehead.");
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
                
                // Tireless Caster diminishing chance
                int timesGotTC = getTimesGotTC(u);
                double baseChance = module.getConfig().getDouble("legendary-quest.tireless-caster-base-chance", 1.0);
                double decay = module.getConfig().getDouble("legendary-quest.tireless-caster-decay", 0.5);
                double chance = baseChance * Math.pow(decay, timesGotTC);
                
                if (ThreadLocalRandom.current().nextDouble() < chance) {
                    // Grant Tireless Caster!
                    module.getEnchantManager().setEnchantLevel(rod, CustomEnchant.TIRELESS_CASTER, currentLevel + 1);
                    incrementTimesGotTC(u);
                    sendLines(player, true, "&e[Ancient Angler]&f You pulled the heat from the earth itself. I grant you the mastery of the Tireless Caster.");
                } else {
                    // Consolation reward (random, looks like normal Path 1 reward)
                    giveConsolationReward(player);
                }
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
                checkTask(player, state, "Anchovy", 1, "Giant Snakehead", 5, 403,
                    "You are too loud. You rushed. Bring me the silver speck and the serpent kings.",
                    "Bring me the striped sentinels that patrol the deep reef walls \u2014 five of the barred wardens who watch in silence.");
            } else if (state.equals("turnin_403")) {
                if (!takeFish(player, "Barred Knifejaw", 5)) {
                    punishAndRepeat(player, state, "You are too loud. You rushed. Five barred wardens, I said.");
                    return;
                }
                // Give +5 Angler Levels
                module.addAnglerLevels(player, 5);
                // One-time Fossilized Anchor trophy
                if (!hasFossilizedAnchor(u)) {
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
                        anchorMeta.getPersistentDataContainer().set(new NamespacedKey(plugin, "trophy_item"), PersistentDataType.STRING, "fossilized_anchor");
                        anchor.setItemMeta(anchorMeta);
                        Map<Integer, ItemStack> leftover = player.getInventory().addItem(anchor);
                        for (ItemStack left : leftover.values()) player.getWorld().dropItem(player.getLocation(), left);
                    }
                    setHasFossilizedAnchor(u);
                } else {
                    TextUtil.sendMessage(player, "&e[Ancient Angler]&f You already possess the relic. Your levels grow, but the anchor calls to no one twice.");
                }
                sendLines(player, true, "&e[Ancient Angler]&f Ye truly understand the quiet of the waters. Keep this ancient relic; let it anchor yer patience.");
            }
            
            // Path 6
            else if (state.equals("turnin_601")) {
                if (countAnyCustomFish(player) < 20) { punishAndRepeat(player, state, "Your offerings are thin. Bring me a proper tax from the sea."); return; }
                takeAnyCustomFish(player, 20);
                setStage(u, 602);
                sendLinesAndRelocate(player, "&e[Ancient Angler]&f A beggar's purse. If ye want the true fortune, bring me the golden scale.");
            } else if (state.equals("turnin_602")) {
                checkTask(player, state, "Golden Carp", 1, null, 0, 603,
                    "Your offerings are thin. Bring me the golden scale.",
                    "Now bring me the armored jewels of the frozen waters. Two of them.");
            } else if (state.equals("turnin_603")) {
                if (!takeFish(player, "Ranchu Goldfish", 2)) {
                    punishAndRepeat(player, state, "Your offerings are thin. Two armored jewels, I said.");
                    return;
                }
                plugin.getEconomyManager().deposit(player, 250000.0);
                // Give random Epic fish
                giveRandomEpicFish(player);
                sendLines(player, true, "&e[Ancient Angler]&f Fortune has smiled upon you today. Take these riches and remember: the sea giveth and the sea taketh.");
            }
            
            // Path 7
            else if (state.equals("turnin_701")) {
                if (countAnyCustomFish(player) < 5) { punishAndRepeat(player, state, "The darkness rejects your meager offering. More creatures of the deep."); return; }
                takeAnyCustomFish(player, 5);
                setStage(u, 702);
                sendLinesAndRelocate(player, "&e[Ancient Angler]&f Fetch me the demons that swim through volcanic veins. Two of them.");
            } else if (state.equals("turnin_702")) {
                if (!takeFish(player, "Demonic Netherfish", 2)) {
                    punishAndRepeat(player, state, "The darkness rejects your offering. Two demons, I said.");
                    return;
                }
                setStage(u, 703);
                sendLinesAndRelocate(player, "&e[Ancient Angler]&f The ultimate darkness... bring me the wrasse forged in the nether's forge. Two of them.");
            } else if (state.equals("turnin_703")) {
                if (!takeFish(player, "Netherwrasse", 2)) {
                    punishAndRepeat(player, state, "The darkness rejects your offering. Two nether wrasse, I said.");
                    return;
                }
                // Give 5x Mythical Chum
                ItemStack baitReward = module.getBaitManager().createBaitItem("mythic_chum");
                if (baitReward != null) {
                    baitReward.setAmount(5);
                    Map<Integer, ItemStack> leftover = player.getInventory().addItem(baitReward);
                    for (ItemStack left : leftover.values()) player.getWorld().dropItem(player.getLocation(), left);
                }
                sendLines(player, true, "&e[Ancient Angler]&f The depths have acknowledged your courage. Take this mythical chum... it calls to the legends.");
            }
            
            // Path 8
            else if (state.equals("turnin_801")) {
                if (countAnyCustomFish(player) < 10) { punishAndRepeat(player, state, "More! Show me your desperation!"); return; }
                takeAnyCustomFish(player, 10);
                setStage(u, 802);
                sendLinesAndRelocate(player, "&e[Ancient Angler]&f Good... now bring me something precious. A rare creature from the cold.");
            } else if (state.equals("turnin_802")) {
                checkTask(player, state, "Tundra Trout", 1, null, 0, 803,
                    "More! Show me your desperation!",
                    "Yes... YES! One final offering... bring me your finest catch!");
            } else if (state.equals("turnin_803")) {
                // Check for ANY legendary fish
                boolean hasLegendary = false;
                String legendaryName = null;
                // Check inventory for legendary fish
                for (ItemStack item : player.getInventory().getContents()) {
                    if (item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) {
                        String sp = item.getItemMeta().getPersistentDataContainer().get(module.getSpeciesKey(), PersistentDataType.STRING);
                        if (isLegendary(sp)) { hasLegendary = true; legendaryName = sp; break; }
                    }
                }
                if (!hasLegendary) {
                    // Check bag
                    ItemStack[] bag = module.getFishBagManager().getBag(player.getUniqueId());
                    if (bag != null) {
                        for (ItemStack item : bag) {
                            if (item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) {
                                String sp = item.getItemMeta().getPersistentDataContainer().get(module.getSpeciesKey(), PersistentDataType.STRING);
                                if (isLegendary(sp)) { hasLegendary = true; legendaryName = sp; break; }
                            }
                        }
                    }
                }
                if (!hasLegendary) {
                    punishAndRepeat(player, state, "That is not your FINEST! Bring me a legendary catch or face my wrath!");
                    return;
                }
                // Take the legendary fish
                takeFish(player, legendaryName, 1);
                
                // TRAP ACTIVATES!
                sendLinesAndPunishDesperation(player);
            }
            
            // Path 9
            else if (state.equals("turnin_901")) {
                checkTask(player, state, "Eel", 5, null, 0, 902,
                    "The current rejects you. Bring me the riders of the current.",
                    "The storm's child hides in frozen rivers. Bring me the trout touched by winter.");
            } else if (state.equals("turnin_902")) {
                if (!takeFish(player, "Tundra Trout", 3)) {
                    punishAndRepeat(player, state, "The current rejects you. Three trout of the tundra.");
                    return;
                }
                setStage(u, 903);
                sendLinesAndRelocate(player, "&e[Ancient Angler]&f Finally... the beast that commands the jungle rivers with fury.");
            } else if (state.equals("turnin_903")) {
                if (!takeFish(player, "Giant Snakehead", 1)) {
                    punishAndRepeat(player, state, "The current rejects you. Bring me the serpent king.");
                    return;
                }
                // Give Stormcaller's Trident (one-time trophy)
                if (!hasStormcallerTrident(u)) {
                    giveStormcallerTrident(player);
                    setHasStormcallerTrident(u);
                } else {
                    TextUtil.sendMessage(player, "&e[Ancient Angler]&f You already wield the storm's legacy. Your courage is noted, but the trident calls to no one twice.");
                }
                sendLines(player, true, "&e[Ancient Angler]&f You have braved the tempest and survived. The storm now recognizes you as its master.");
            }

        } else {
            TextUtil.sendMessage(player, "&e[Ancient Angler]&f I do not understand those words. Type 1, 2, or 3.");
        }
    }
    
    private static final String[][] SCENARIO_TEXTS = {
        // Scenario A (original)
        { "The freezing tide numbs all panic eventually.",  // Path 1
          "A man burdened by gold sinks without a fight.",   // Path 2
          "The leviathans below dragged him down too fast.", // Path 3
          "He simply accepted the peace of the deep.",       // Path 4
          "Stop the riddles, old man. I need a favor.",      // Path 5
          "He was counting his treasures when the waves took him.", // Path 6
          "The abyss itself reached up and pulled him under.",      // Path 7
          "He clawed at the water, trading everything for one more breath.", // Path 8
          "A bolt from the heavens split the sea, and he simply... fell in.", // Path 9
          "The fool? He jumped in thinking he could command the tide." // Path 10
        },
        // Scenario B (original)
        { "The endless need to fight against the current.",
          "The shimmer of sunken empires buried in the muck.",
          "The pure adrenaline when the line snaps taut.",
          "The absolute silence when the surface breaks.",
          "You are. Now give me what I'm owed.",
          "The promise of fortune hidden beneath every wave.",
          "The pull of something ancient lurking far below.",
          "The fear that drives a man to risk everything.",
          "The crack of thunder before the heavens weep.",
          "I command the sea, old man. It bows to me."
        },
        // Scenario C (new)
        { "Endurance. He kept swimming long after hope had drowned.",
          "The weight of his purse pulled him to the bottom.",
          "Something in the dark water wanted him more than the surface did.",
          "He made peace with the silence and let it take him.",
          "Enough with your games. Just tell me what I need.",
          "He was too busy admiring his reflection in golden coins.",
          "The deep has teeth, and it was hungry that night.",
          "He traded his last breath for a chance, and lost.",
          "The sky cracked open and the sea swallowed the light.",
          "He believed the ocean owed him something. It didn't."
        },
        // Scenario D (new)
        { "The cold numbed his fear until surrender felt like warmth.",
          "He drowned clutching gold bars, refusing to let go.",
          "A hunter became the hunted in the blackwater.",
          "In the end, the quiet was all he ever wanted.",
          "Your riddles bore me. Give me what I came for.",
          "Fortune smiled, then pushed him overboard.",
          "Whatever lives in the trench claimed him without ceremony.",
          "He begged the waves for mercy. They don't speak mortal.",
          "Lightning kissed the water and something ancient stirred.",
          "He shouted orders at the tide. The tide did not listen."
        },
        // Scenario E (new)
        { "The freezing water taught him that survival is the only instinct.",
          "His pockets were heavy with treasure, his lungs empty of air.",
          "The predator's jaws closed before he could even scream.",
          "He chose the stillness. The deep embraced him willingly.",
          "Spare me the philosophy, I have things to do.",
          "He spent his life chasing riches and the sea collected the debt.",
          "From the bottomless dark, something whispered his name.",
          "Desperation made him foolish. The ocean made him vanish.",
          "The storm broke him before the water ever could.",
          "Arrogance is the heaviest anchor a fool can carry."
        }
    };

    private static final String[] SCENARIO_PROMPTS = {
        "*Cough*... I watched a man surrender to the water 'ere yesterday. Didn't thrash. Didn't scream. Just sank like a stone. Why d'ye think he stayed so quiet?",
        "Look at 'em... Every fish bites the iron thinkin' it's a blessin'. Tell me, youngster, what's the iron hook that caught your soul?",
        "I once watched a ship's captain throw his compass into the sea. His crew wept, but he smiled. Why d'ye think he destroyed his only guide?",
        "There's a fish that swallows its own young to protect them from the storm. Tell me, mortal \u2014 what would ye swallow to keep what matters safe?",
        "The deepest trench holds no light, no warmth, no mercy. Yet something lives there still. What drives a creature to thrive where everything says it should perish?"
    };

    private void startIntroDialogue(Player player) {
        UUID u = player.getUniqueId();
        inDialogueSequence.add(u);
        setChatState(u, "awaiting_intro");
        
        int scenarioIndex = ThreadLocalRandom.current().nextInt(SCENARIO_PROMPTS.length);
        
        List<Integer> availablePaths = new ArrayList<>(Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        Collections.shuffle(availablePaths);
        List<Integer> chosenPaths = new ArrayList<>(availablePaths.subList(0, 3));
        introOptions.put(u, chosenPaths);
        
        final int si = scenarioIndex;
        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (tick == 0) {
                    TextUtil.sendMessage(player, "&e[Ancient Angler]&f " + SCENARIO_PROMPTS[si]);
                } else if (tick == 1) {
                    TextUtil.sendMessage(player, "");
                    for (int i = 0; i < 3; i++) {
                        String text = SCENARIO_TEXTS[si][chosenPaths.get(i) - 1];
                        sendClickableOption(player, "&b&l[" + (i + 1) + "] &f" + text, "&7Click to choose this path", String.valueOf(i + 1));
                    }
                    TextUtil.sendMessage(player, "");
                    TextUtil.sendMessage(player, "&8&oClick an option above or type 1, 2, or 3 in chat.");
                    TextUtil.sendMessage(player, "&8&oType 'cancel' to walk away. Expires in 2 minutes.");
                    inDialogueSequence.remove(u);
                    cancel();
                }
                tick++;
            }
        }.runTaskTimer(plugin, 10L, 80L);
    }
    
    private void punishAndRepeat(Player player, String state, String failMessage) {
        UUID u = player.getUniqueId();
        int fails = failCounters.getOrDefault(u, 0) + 1;
        failCounters.put(u, fails);
        saveFailCount(u, fails);
        
        double basePenalty = module.getConfig().getDouble("legendary-quest.fail-penalty-base", 10000);
        double multiplier = module.getConfig().getDouble("legendary-quest.fail-penalty-multiplier", 2.0);
        double cap = module.getConfig().getDouble("legendary-quest.fail-penalty-cap", 500000);
        double penalty = Math.min(basePenalty * Math.pow(multiplier, fails - 1), cap);
        
        // Blindness scales with fails
        int blindnessDuration = Math.min(5 + (fails * 3), 30) * 20;
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindnessDuration, 1));
        
        // Slowness at 3+ fails
        if (fails >= 3) {
            int slowDuration = Math.min(10 + (fails * 5), 25) * 20;
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowDuration, 1));
        }
        // Nausea at 5+ fails
        if (fails >= 5) {
            int nauseaDuration = Math.min(10 + ((fails - 4) * 5), 15) * 20;
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, nauseaDuration, 1));
        }
        
        plugin.getEconomyManager().withdraw(player, penalty);
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        TextUtil.sendMessage(player, "&c[System] You lied to the Angler. $" + String.format("%,.0f", penalty) + " deducted. (Fail #" + fails + ")");
        TextUtil.sendMessage(player, "&e[Ancient Angler]&f " + failMessage);
        
        setChatState(player.getUniqueId(), state);
    }
    
    private boolean payHintCost(Player player) {
        double cost = module.getConfig().getDouble("legendary-quest.sacrifice-cost", 25000.0);
        boolean allowDebt = module.getConfig().getBoolean("legendary-quest.fail-penalty-allow-debt", true);
        
        if (!allowDebt && !plugin.getEconomyManager().has(player, cost)) {
            TextUtil.sendMessage(player, "&c[System] You need $" + String.format("%,.0f", cost) + " for a hint.");
            return false;
        }
        
        boolean found = false;
        String deductedFishName = "Unknown";
        
        // Weighted priority: Legendary 50%, Epic 30%, Rare 20%
        int legWeight = module.getConfig().getInt("legendary-quest.sacrifice-legendary-weight", 50);
        int epicWeight = module.getConfig().getInt("legendary-quest.sacrifice-epic-weight", 30);
        // rareWeight fills the rest
        
        // Collect fish by rarity
        List<int[]> legendarySlots = new ArrayList<>(); // [slot, 0=inv/1=bag]
        List<int[]> epicSlots = new ArrayList<>();
        List<int[]> rareSlots = new ArrayList<>();
        
        // Scan inventory
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) {
                String sp = item.getItemMeta().getPersistentDataContainer().get(module.getSpeciesKey(), PersistentDataType.STRING);
                String rarity = getFishRarity(sp);
                if ("legendary".equals(rarity)) legendarySlots.add(new int[]{i, 0});
                else if ("epic".equals(rarity)) epicSlots.add(new int[]{i, 0});
                else if ("rare".equals(rarity)) rareSlots.add(new int[]{i, 0});
            }
        }
        // Scan bag
        ItemStack[] bag = module.getFishBagManager().getBag(player.getUniqueId());
        if (bag != null) {
            for (int i = 0; i < bag.length; i++) {
                if (bag[i] != null && bag[i].hasItemMeta() && bag[i].getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) {
                    String sp = bag[i].getItemMeta().getPersistentDataContainer().get(module.getSpeciesKey(), PersistentDataType.STRING);
                    String rarity = getFishRarity(sp);
                    if ("legendary".equals(rarity)) legendarySlots.add(new int[]{i, 1});
                    else if ("epic".equals(rarity)) epicSlots.add(new int[]{i, 1});
                    else if ("rare".equals(rarity)) rareSlots.add(new int[]{i, 1});
                }
            }
        }
        
        // Weighted selection
        List<int[]> targetPool = null;
        double roll = ThreadLocalRandom.current().nextDouble() * 100;
        if (roll < legWeight && !legendarySlots.isEmpty()) targetPool = legendarySlots;
        else if (roll < legWeight + epicWeight && !epicSlots.isEmpty()) targetPool = epicSlots;
        else if (!rareSlots.isEmpty()) targetPool = rareSlots;
        else if (!legendarySlots.isEmpty()) targetPool = legendarySlots;
        else if (!epicSlots.isEmpty()) targetPool = epicSlots;
        
        if (targetPool != null && !targetPool.isEmpty()) {
            int[] target = targetPool.get(ThreadLocalRandom.current().nextInt(targetPool.size()));
            if (target[1] == 0) { // inventory
                ItemStack item = player.getInventory().getItem(target[0]);
                deductedFishName = item.getItemMeta().getPersistentDataContainer().get(module.getSpeciesKey(), PersistentDataType.STRING);
                item.setAmount(item.getAmount() - 1);
                if (item.getAmount() <= 0) player.getInventory().setItem(target[0], null);
            } else { // bag
                deductedFishName = bag[target[0]].getItemMeta().getPersistentDataContainer().get(module.getSpeciesKey(), PersistentDataType.STRING);
                bag[target[0]].setAmount(bag[target[0]].getAmount() - 1);
                if (bag[target[0]].getAmount() <= 0) bag[target[0]] = null;
                module.getFishBagManager().updateBag(player.getUniqueId(), bag);
                module.getFishBagManager().saveBag(player.getUniqueId());
            }
            found = true;
        }
        
        if (found) {
            plugin.getEconomyManager().withdraw(player, cost);
            TextUtil.sendMessage(player, "&a[System] Paid $" + String.format("%,.0f", cost) + " and 1x " + deductedFishName + " for a hint.");
            return true;
        } else {
            // Fallback roulette
            double seizure = module.getConfig().getDouble("legendary-quest.sacrifice-no-fish-seizure", 50000.0);
            int rouletteRoll = ThreadLocalRandom.current().nextInt(3);
            if (rouletteRoll == 0) {
                plugin.getEconomyManager().withdraw(player, cost);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 180, 1));
                TextUtil.sendMessage(player, "&c[System] Lacked fish tribute! Paid $" + String.format("%,.0f", cost) + " and cursed with Slowness (3m) for the hint.");
                player.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 1f);
                return true;
            } else if (rouletteRoll == 1) {
                plugin.getEconomyManager().withdraw(player, cost);
                player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 20 * 180, 1));
                TextUtil.sendMessage(player, "&c[System] Lacked fish tribute! Paid $" + String.format("%,.0f", cost) + " and cursed with Fatigue (3m) for the hint.");
                player.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 1f);
                return true;
            } else {
                plugin.getEconomyManager().withdraw(player, seizure);
                TextUtil.sendMessage(player, "&c[System] Lacked fish tribute! The Angler seized $" + String.format("%,.0f", seizure) + " instead for the hint.");
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return true;
            }
        }
    }

    private String getFishRarity(String speciesName) {
        ConfigurationSection speciesSection = module.getConfig().getConfigurationSection("species");
        if (speciesSection != null) {
            for (String key : speciesSection.getKeys(false)) {
                String name = speciesSection.getString(key + ".name", "");
                name = ChatColor.stripColor(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(name).content());
                if (speciesName.equalsIgnoreCase(name)) {
                    return speciesSection.getString(key + ".rarity", "Common").toLowerCase();
                }
            }
        }
        return "common";
    }

    private void giveConsolationReward(Player player) {
        int roll = ThreadLocalRandom.current().nextInt(3);
        if (roll == 0) {
            plugin.getEconomyManager().deposit(player, 100000.0);
            sendLines(player, true, "&e[Ancient Angler]&f The Angler nods slowly. 'You've proven your endurance. Take this gold.'");
        } else if (roll == 1) {
            String[] baits = {"tide_essence", "mythic_chum", "golden_lure"};
            String chosen = baits[ThreadLocalRandom.current().nextInt(baits.length)];
            ItemStack bait = module.getBaitManager().createBaitItem(chosen);
            if (bait != null) {
                bait.setAmount(2);
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(bait);
                for (ItemStack left : leftover.values()) player.getWorld().dropItem(player.getLocation(), left);
            }
            plugin.getEconomyManager().deposit(player, 50000.0);
            sendLines(player, true, "&e[Ancient Angler]&f The Angler reaches into the deep and pulls out offerings. 'Supplies for the weary.'");
        } else {
            module.addAnglerLevels(player, 2);
            plugin.getEconomyManager().deposit(player, 50000.0);
            sendLines(player, true, "&e[Ancient Angler]&f The Angler places a weathered hand on your rod. 'Your skill sharpens.'");
        }
    }

    private void giveRandomEpicFish(Player player) {
        ConfigurationSection speciesSection = module.getConfig().getConfigurationSection("species");
        if (speciesSection == null) return;
        List<String> epicSpecies = new ArrayList<>();
        for (String key : speciesSection.getKeys(false)) {
            if (speciesSection.getString(key + ".rarity", "").equalsIgnoreCase("Epic")) {
                epicSpecies.add(key);
            }
        }
        if (epicSpecies.isEmpty()) return;
        String chosen = epicSpecies.get(ThreadLocalRandom.current().nextInt(epicSpecies.size()));
        String name = speciesSection.getString(chosen + ".name", "Epic Fish");
        String material = speciesSection.getString(chosen + ".material", "COD");
        double minW = speciesSection.getDouble(chosen + ".min-weight", 1.0);
        double maxW = speciesSection.getDouble(chosen + ".max-weight", 10.0);
        double ppk = speciesSection.getDouble(chosen + ".price-per-kg", 100.0);
        double weight = minW + ThreadLocalRandom.current().nextDouble() * (maxW - minW);
        String itemModel = speciesSection.getString(chosen + ".item-model", "");
        ItemStack fish = module.createFishItem(name, material, weight, ppk, itemModel);
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(fish);
        for (ItemStack left : leftover.values()) player.getWorld().dropItem(player.getLocation(), left);
    }

    private void giveStormcallerTrident(Player player) {
        ItemStack trident = new ItemStack(Material.TRIDENT);
        ItemMeta meta = trident.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.legacyColorize("&e&lStormcaller's Trident"));
            meta.addEnchant(org.bukkit.enchantments.Enchantment.LURE, 1, true);
            meta.addItemFlags(
                org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS,
                org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES,
                org.bukkit.inventory.ItemFlag.HIDE_UNBREAKABLE
            );
            meta.setUnbreakable(true);
            // Make non-throwable by zeroing attack damage
            meta.addAttributeModifier(
                org.bukkit.attribute.Attribute.GENERIC_ATTACK_DAMAGE,
                new org.bukkit.attribute.AttributeModifier(
                    UUID.randomUUID(), "no_damage", -999,
                    org.bukkit.attribute.AttributeModifier.Operation.ADD_NUMBER,
                    org.bukkit.inventory.EquipmentSlot.HAND
                )
            );
            meta.setLore(Arrays.asList(
                TextUtil.legacyColorize("&7A relic of the storm..."),
                TextUtil.legacyColorize("&7Proof that you faced the tempest and survived."),
                TextUtil.legacyColorize(""),
                TextUtil.legacyColorize("&c&oThis ancient relic cannot be used as a weapon."),
                TextUtil.legacyColorize("&c&oIt serves only as a trophy of your courage.")
            ));
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "trophy_item"), PersistentDataType.STRING, "stormcaller_trident");
            trident.setItemMeta(meta);
            Map<Integer, ItemStack> leftover = player.getInventory().addItem(trident);
            for (ItemStack left : leftover.values()) player.getWorld().dropItem(player.getLocation(), left);
        }
    }

    private boolean isLegendary(String speciesName) {
        ConfigurationSection speciesSection = module.getConfig().getConfigurationSection("species");
        if (speciesSection != null) {
            for (String key : speciesSection.getKeys(false)) {
                String name = speciesSection.getString(key + ".name", "");
                name = ChatColor.stripColor(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(name).content());
                if (speciesName.equalsIgnoreCase(name)) {
                    return speciesSection.getString(key + ".rarity", "Common").equalsIgnoreCase("Legendary");
                }
            }
        }
        return false;
    }

    private void sendLinesAndPunishArrogance(Player player) {
        UUID u = player.getUniqueId();
        incrementTimesDisrespected(u);
        inDialogueSequence.add(u);
        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (tick == 0) {
                    TextUtil.sendMessage(player, "&e[Ancient Angler]&f COMMAND the tide? You dare speak of command before the Ancient Angler?!");
                } else if (tick == 1) {
                    TextUtil.sendMessage(player, "&e[Ancient Angler]&f The sea does not bow to mortals.");
                } else {
                    inDialogueSequence.remove(u);
                    player.getWorld().strikeLightningEffect(player.getLocation());
                    player.getWorld().strikeLightningEffect(player.getLocation().add(1, 0, 1));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 20 * 30, 1));
                    player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.3f, 1f);
                    
                    // Steal best fish
                    String bestFish = findBestFish(player);
                    if (bestFish != null) {
                        takeFish(player, bestFish, 1);
                        TextUtil.sendMessage(player, "&cThe Angler violently seized your " + bestFish + "!");
                    } else {
                        plugin.getEconomyManager().withdraw(player, 50000.0);
                        TextUtil.sendMessage(player, "&cThe Angler seized $50,000 from you!");
                    }
                    finishQuest(player);
                    cancel();
                }
                tick++;
            }
        }.runTaskTimer(plugin, 10L, 80L);
    }

    private void sendLinesAndPunishDesperation(Player player) {
        UUID u = player.getUniqueId();
        inDialogueSequence.add(u);
        new BukkitRunnable() {
            int tick = 0;
            @Override
            public void run() {
                if (tick == 0) {
                    TextUtil.sendMessage(player, "&e[Ancient Angler]&f FOOL! You handed your treasure to a phantom!");
                    player.playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1f, 0.5f);
                } else if (tick == 1) {
                    TextUtil.sendMessage(player, "&e[Ancient Angler]&f The depths thank you for your desperation!");
                } else {
                    inDialogueSequence.remove(u);
                    // Steal all fish in inventory (up to 10)
                    int stolen = 0;
                    for (int i = 0; i < player.getInventory().getSize() && stolen < 10; i++) {
                        ItemStack item = player.getInventory().getItem(i);
                        if (item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) {
                            player.getInventory().setItem(i, null);
                            stolen++;
                        }
                    }
                    plugin.getEconomyManager().withdraw(player, 50000.0);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 20 * 30, 2));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 20 * 120, 2));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 60, 1));
                    TextUtil.sendMessage(player, "&cThe Angler stole " + stolen + " fish and $50,000 from you!");
                    finishQuest(player);
                    cancel();
                }
                tick++;
            }
        }.runTaskTimer(plugin, 10L, 80L);
    }

    private String findBestFish(Player player) {
        String[] priorities = {"legendary", "epic", "rare", "uncommon", "common"};
        ConfigurationSection speciesSection = module.getConfig().getConfigurationSection("species");
        if (speciesSection == null) return null;
        for (String priority : priorities) {
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && item.hasItemMeta() && item.getItemMeta().getPersistentDataContainer().has(module.getSpeciesKey(), PersistentDataType.STRING)) {
                    String sp = item.getItemMeta().getPersistentDataContainer().get(module.getSpeciesKey(), PersistentDataType.STRING);
                    for (String key : speciesSection.getKeys(false)) {
                        String name = speciesSection.getString(key + ".name", "");
                        name = ChatColor.stripColor(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(name).content());
                        if (sp.equalsIgnoreCase(name) && speciesSection.getString(key + ".rarity", "").equalsIgnoreCase(priority)) {
                            return sp;
                        }
                    }
                }
            }
        }
        return null;
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
                TextUtil.sendMessage(player, "&bThe Angler vanished! Your tracker pearl pulses and locks onto a new location...");
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
        incrementTimesDisrespected(u);
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
        UUID u = player.getUniqueId();
        incrementTimesCompleted(u);
        failCounters.remove(u);
        saveFailCount(u, 0);
        setStage(u, 0);
        
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
