/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.OfflinePlayer
 *  org.bukkit.Particle
 *  org.bukkit.Sound
 *  org.bukkit.World
 *  org.bukkit.block.Block
 *  org.bukkit.command.CommandSender
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.EntityType
 *  org.bukkit.entity.Player
 *  org.bukkit.entity.Villager
 *  org.bukkit.entity.Villager$Profession
 *  org.bukkit.entity.Villager$Type
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.entity.EntityBreedEvent
 *  org.bukkit.event.entity.EntityDamageEvent
 *  org.bukkit.event.entity.EntityTransformEvent
 *  org.bukkit.event.entity.VillagerAcquireTradeEvent
 *  org.bukkit.event.player.PlayerInteractEntityEvent
 *  org.bukkit.event.world.ChunkLoadEvent
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.persistence.PersistentDataContainer
 *  org.bukkit.persistence.PersistentDataType
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.potion.PotionEffect
 *  org.bukkit.potion.PotionEffectType
 *  org.bukkit.scheduler.BukkitTask
 */
package net.rocky.coresuite.module.customfishing;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.customfishing.CustomFishingModule;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.entity.VillagerAcquireTradeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public class WanderingAnglerManager
implements Listener {
    private final CoreSuitePlugin plugin;
    private final CustomFishingModule module;
    private final Random random = new Random();
    private final NamespacedKey anglerKey;
    private final NamespacedKey fish1Key;
    private final NamespacedKey fish2Key;
    private final NamespacedKey fish3Key;
    private final NamespacedKey spawnTimeKey;
    private UUID activeAnglerUuid = null;
    private BukkitTask activeDespawnTask = null;
    private BukkitTask spawningTask = null;

    public WanderingAnglerManager(CoreSuitePlugin plugin, CustomFishingModule module) {
        this.plugin = plugin;
        this.module = module;
        this.anglerKey = new NamespacedKey((Plugin)plugin, "wandering_angler");
        this.fish1Key = new NamespacedKey((Plugin)plugin, "wa_fish_1");
        this.fish2Key = new NamespacedKey((Plugin)plugin, "wa_fish_2");
        this.fish3Key = new NamespacedKey((Plugin)plugin, "wa_fish_3");
        this.spawnTimeKey = new NamespacedKey((Plugin)plugin, "wa_spawn_time");
        this.checkExistingAnglers();
        this.startSpawningTask();
    }

    private void checkExistingAnglers() {
        long now = System.currentTimeMillis();
        long maxAge = 900000L;
        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (!e.getPersistentDataContainer().has(this.anglerKey, PersistentDataType.BYTE)) continue;
                Long spawnTime = (Long)e.getPersistentDataContainer().get(this.spawnTimeKey, PersistentDataType.LONG);
                if (spawnTime == null) {
                    e.remove();
                    continue;
                }
                long age = now - spawnTime;
                if (age >= maxAge) {
                    e.remove();
                    continue;
                }
                if (this.activeAnglerUuid == null) {
                    this.activeAnglerUuid = e.getUniqueId();
                    long remainingTicks = (maxAge - age) / 50L;
                    this.scheduleDespawn(this.activeAnglerUuid, remainingTicks);
                    continue;
                }
                e.remove();
            }
        }
    }

    private void startSpawningTask() {
        this.spawningTask = Bukkit.getScheduler().runTaskTimer((Plugin)this.plugin, () -> {
            ArrayList players = new ArrayList(Bukkit.getOnlinePlayers());
            if (players.isEmpty()) {
                return;
            }
            Player target = (Player)players.get(this.random.nextInt(players.size()));
            Location spawnLoc = this.findSafeSpawn(target.getLocation());
            if (spawnLoc != null) {
                this.spawnAngler(spawnLoc);
            }
        }, 54000L, 54000L);
    }

    private Location findSafeSpawn(Location playerLoc) {
        int radius = 15;
        for (int i = 0; i < 20; ++i) {
            Location spawnLoc;
            int dx = this.random.nextInt(radius * 2) - radius;
            int dz = this.random.nextInt(radius * 2) - radius;
            Location tryLoc = playerLoc.clone().add((double)dx, 0.0, (double)dz);
            Block highest = tryLoc.getWorld().getHighestBlockAt(tryLoc);
            if (!highest.getType().isSolid() || highest.getType() == Material.LAVA || !((spawnLoc = highest.getLocation().add(0.5, 1.0, 0.5)).distance(playerLoc) > 5.0)) continue;
            return spawnLoc;
        }
        return null;
    }

    public void spawnAngler(Location loc) {
        ConfigurationSection speciesSec = this.module.getConfig().getConfigurationSection("species");
        if (speciesSec == null) {
            return;
        }
        ArrayList keys = new ArrayList(speciesSec.getKeys(false));
        if (keys.isEmpty()) {
            return;
        }
        this.removeActiveAngler();
        Villager trader = (Villager)loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
        trader.setProfession(Villager.Profession.FISHERMAN);
        trader.setVillagerType(Villager.Type.JUNGLE);
        trader.customName(TextUtil.colorize("&b&lWandering Angler"));
        trader.setCustomNameVisible(true);
        trader.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 999999, 0, false, false));
        trader.setInvulnerable(true);
        trader.setAI(false);
        trader.setCollidable(false);
        trader.setCanPickupItems(false);
        trader.setPersistent(false);
        trader.setRemoveWhenFarAway(true);
        PersistentDataContainer pdc = trader.getPersistentDataContainer();
        pdc.set(this.anglerKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(this.spawnTimeKey, PersistentDataType.LONG, System.currentTimeMillis());
        ArrayList<String> common = new ArrayList<String>();
        ArrayList<String> rare = new ArrayList<String>();
        ArrayList<String> legendary = new ArrayList<String>();
        for (String k : (java.util.List<String>)(java.util.List<?>)keys) {
            String r = this.module.getConfig().getString("species." + k + ".rarity", "Common").toLowerCase();
            if (r.equals("legendary") || r.equals("mythical")) {
                legendary.add(k);
                continue;
            }
            if (r.equals("rare") || r.equals("epic") || r.equals("uncommon")) {
                rare.add(k);
                continue;
            }
            common.add(k);
        }
        ArrayList<String> chosenFish = new ArrayList<String>();
        for (int i = 0; i < 3; ++i) {
            double roll = this.random.nextDouble() * 100.0;
            ArrayList<String> pool = roll < 70.0 && !common.isEmpty() ? common : (roll < 95.0 && !rare.isEmpty() ? rare : (!legendary.isEmpty() ? legendary : (!rare.isEmpty() ? rare : common)));
            if (pool == null || pool.isEmpty()) continue;
            String chosen = (String)pool.get(this.random.nextInt(pool.size()));
            if (!chosenFish.contains(chosen)) {
                chosenFish.add(chosen);
                continue;
            }
            chosen = (String)pool.get(this.random.nextInt(pool.size()));
            if (chosenFish.contains(chosen)) continue;
            chosenFish.add(chosen);
        }
        if (chosenFish.size() > 0) {
            pdc.set(this.fish1Key, PersistentDataType.STRING, ((String)chosenFish.get(0)));
        }
        if (chosenFish.size() > 1) {
            pdc.set(this.fish2Key, PersistentDataType.STRING, ((String)chosenFish.get(1)));
        }
        if (chosenFish.size() > 2) {
            pdc.set(this.fish3Key, PersistentDataType.STRING, ((String)chosenFish.get(2)));
        }
        loc.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc.clone().add(0.0, 1.0, 0.0), 20, 0.5, 0.5, 0.5, 0.05);
        loc.getWorld().playSound(loc, Sound.ENTITY_WANDERING_TRADER_AMBIENT, 1.0f, 1.0f);
        String prefix = this.module.getConfig().getString("prefix", "&8[&b&lFishing&8] &r");
        Bukkit.broadcast((Component)TextUtil.colorize(prefix + "&dThe Wandering Angler has appeared somewhere in the world! He will stay for 15 minutes."));
        this.activeAnglerUuid = trader.getUniqueId();
        this.scheduleDespawn(this.activeAnglerUuid, 18000L);
    }

    private void scheduleDespawn(UUID entityId, long ticks) {
        if (this.activeDespawnTask != null) {
            this.activeDespawnTask.cancel();
        }
        this.activeDespawnTask = Bukkit.getScheduler().runTaskLater((Plugin)this.plugin, () -> {
            Entity entity = Bukkit.getEntity((UUID)entityId);
            if (entity != null && entity.isValid()) {
                entity.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, entity.getLocation().clone().add(0.0, 1.0, 0.0), 30, 0.5, 0.5, 0.5, 0.05);
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WANDERING_TRADER_DISAPPEARED, 1.0f, 1.0f);
                entity.remove();
                String prefix = this.module.getConfig().getString("prefix", "&8[&b&lFishing&8] &r");
                Bukkit.broadcast((Component)TextUtil.colorize(prefix + "&7The Wandering Angler has departed."));
            }
            this.activeAnglerUuid = null;
        }, ticks);
    }

    private void removeActiveAngler() {
        if (this.activeDespawnTask != null) {
            this.activeDespawnTask.cancel();
            this.activeDespawnTask = null;
        }
        if (this.activeAnglerUuid != null) {
            Entity entity = Bukkit.getEntity((UUID)this.activeAnglerUuid);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
            this.activeAnglerUuid = null;
        }
    }

    public void shutdown() {
        if (this.spawningTask != null) {
            this.spawningTask.cancel();
            this.spawningTask = null;
        }
        this.removeActiveAngler();
    }

    public int clearAllAnglers() {
        int count = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!entity.getPersistentDataContainer().has(this.anglerKey, PersistentDataType.BYTE)) continue;
                entity.remove();
                ++count;
            }
        }
        this.activeAnglerUuid = null;
        if (this.activeDespawnTask != null) {
            this.activeDespawnTask.cancel();
            this.activeDespawnTask = null;
        }
        return count;
    }

    @EventHandler
    public void onBreed(EntityBreedEvent event) {
        if (event.getMother().getPersistentDataContainer().has(this.anglerKey, PersistentDataType.BYTE) || event.getFather().getPersistentDataContainer().has(this.anglerKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onTransform(EntityTransformEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(this.anglerKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onAcquireTrade(VillagerAcquireTradeEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(this.anglerKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(this.anglerKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        Villager trader;
        PersistentDataContainer pdc;
        Entity entity = event.getRightClicked();
        if (entity instanceof Villager && (pdc = (trader = (Villager)entity).getPersistentDataContainer()).has(this.anglerKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
            this.openBlackMarket(event.getPlayer(), trader);
        }
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (!entity.getPersistentDataContainer().has(this.anglerKey, PersistentDataType.BYTE) || this.activeAnglerUuid != null && entity.getUniqueId().equals(this.activeAnglerUuid)) continue;
            entity.remove();
        }
    }

    private void openBlackMarket(Player player, Villager trader) {
        this.plugin.getDatabaseManager().executeAsync(() -> {
            HashSet<String> caughtSpecies = new HashSet<String>();
            try (Connection conn = this.plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT species_id FROM coresuite_fish_logbook WHERE uuid = ?");){
                stmt.setString(1, player.getUniqueId().toString());
                try (ResultSet rs = stmt.executeQuery();){
                    while (rs.next()) {
                        caughtSpecies.add(rs.getString("species_id"));
                    }
                }
            }
            catch (SQLException e) {
                this.plugin.getLogger().log(Level.SEVERE, "Failed to load fish logbook for NPC", e);
            }
            Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
                Gui gui = new Gui("&0Wandering Angler", 3);
                gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);
                PersistentDataContainer pdc = trader.getPersistentDataContainer();
                String f1 = (String)pdc.get(this.fish1Key, PersistentDataType.STRING);
                String f2 = (String)pdc.get(this.fish2Key, PersistentDataType.STRING);
                String f3 = (String)pdc.get(this.fish3Key, PersistentDataType.STRING);
                int[] slots = new int[]{11, 13, 15};
                String[] fishes = new String[]{f1, f2, f3};
                for (int i = 0; i < 3; ++i) {
                    
                    Material mat;
                    if (fishes[i] == null) continue;
                    String speciesKey = fishes[i];
                    ConfigurationSection sec = this.module.getConfig().getConfigurationSection("species." + speciesKey);
                    if (sec == null) continue;
                    String coloredName = sec.getString("name", speciesKey);
                    String uncoloredName = ChatColor.stripColor((String)ChatColor.translateAlternateColorCodes((char)'&', (String)coloredName));
                    ArrayList<Object> lore = new ArrayList<Object>();
                    lore.add("&7I am paying &d2.5x &7the normal");
                    lore.add("&7price for any &b" + uncoloredName + "&7 you have!");
                    lore.add("");
                    lore.add("&aClick here to sell 1x " + uncoloredName);
                    try {
                        mat = Material.valueOf((String)sec.getString("material", "COD").toUpperCase());
                    }
                    catch (Exception e) {
                        mat = Material.COD;
                    }
                    ItemStack displayItem = new ItemStack(mat);
                    ItemMeta meta = displayItem.getItemMeta();
                    if (meta != null) {
                        String itemModelStr;
                        meta.displayName(TextUtil.colorize("&6&lBounty: " + coloredName));
                        meta.lore(((java.util.List<String>)(java.util.List<?>)lore).stream().map(TextUtil::colorize).toList());
                        if (sec.contains("custom-model-data")) {
                            meta.setCustomModelData(Integer.valueOf(sec.getInt("custom-model-data")));
                        }
                        if (!(itemModelStr = sec.getString("item-model", "")).isEmpty()) {
                            meta.setItemModel(NamespacedKey.fromString((String)itemModelStr));
                        }
                        displayItem.setItemMeta(meta);
                    }
                    org.bukkit.inventory.meta.ItemMeta m = null;
                    if (!caughtSpecies.contains(uncoloredName) && ((m = (displayItem = new ItemStack(Material.PAPER)).getItemMeta()) != null)) {
                        m.displayName(TextUtil.colorize("&8???"));
                        m.setItemModel(NamespacedKey.fromString((String)"barely_default:fish_unknown"));
                        ArrayList<Object> unkLore = new ArrayList<Object>();
                        unkLore.add("&7I am looking for an undiscovered fish...");
                        unkLore.add("&cYou have not caught this species yet!");
                        List biomes = sec.getStringList("biomes");
                        if (biomes != null && !biomes.isEmpty()) {
                            unkLore.add("");
                            unkLore.add("&7Required Biomes:");
                            for (String b : (java.util.List<String>)(java.util.List<?>)(java.util.List<String>)(java.util.List<?>)biomes) {
                                unkLore.add("&8- &7" + b);
                            }
                        }
                        m.lore(((java.util.List<String>)(java.util.List<?>)unkLore).stream().map(TextUtil::colorize).toList());
                        displayItem.setItemMeta(m);
                    }
                    boolean isKnown = caughtSpecies.contains(uncoloredName);
                    String targetStrippedName = uncoloredName;
                    gui.setItem(slots[i], displayItem, click -> {
                        if (!isKnown) {
                            TextUtil.sendMessage((CommandSender)player, "&cYou have not discovered this fish yet!");
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                            return;
                        }
                        boolean sold = false;
                        for (int slot = 0; slot < player.getInventory().getSize(); ++slot) {
                            String spName;
                            NamespacedKey spKey;
                            
                            PersistentDataContainer itemPdc;
                            ItemStack item2 = player.getInventory().getItem(slot);
                            ItemMeta mLocal;
                            if (item2 == null || !item2.hasItemMeta() || !(itemPdc = (mLocal = item2.getItemMeta()).getPersistentDataContainer()).has(spKey = new NamespacedKey((Plugin)this.plugin, "fish_species"), PersistentDataType.STRING) || !targetStrippedName.equals(spName = (String)itemPdc.get(spKey, PersistentDataType.STRING))) continue;
                            double w = (Double)itemPdc.getOrDefault(new NamespacedKey((Plugin)this.plugin, "fish_weight"), PersistentDataType.DOUBLE, 1.0);
                            double price = (Double)itemPdc.getOrDefault(new NamespacedKey((Plugin)this.plugin, "fish_price"), PersistentDataType.DOUBLE, 1.0);
                            double totalValue = w * price * 2.5;
                            if (item2.getAmount() <= 1) {
                                player.getInventory().setItem(slot, null);
                            } else {
                                item2.setAmount(item2.getAmount() - 1);
                                
                            }
                            this.plugin.getEconomyManager().deposit((OfflinePlayer)player, totalValue);
                            TextUtil.sendMessage((CommandSender)player, "&aSold 1x " + coloredName + " &ato the Angler for &d" + this.plugin.getEconomyManager().format(totalValue) + "&a!");
                            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
                            sold = true;
                            break;
                        }
                        if (!sold) {
                            TextUtil.sendMessage((CommandSender)player, "&cYou don't have any " + coloredName + " &cin your inventory!");
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                        }
                    });
                }
                gui.open(player);
                player.playSound(player.getLocation(), Sound.ENTITY_WANDERING_TRADER_TRADE, 1.0f, 1.0f);
            });
        });
    }
}

