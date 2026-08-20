package net.rocky.coresuite.module.customfishing;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.gui.Gui;
import net.rocky.coresuite.common.gui.GuiButton;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
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
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public class WanderingAnglerManager implements Listener {

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
        this.anglerKey = new NamespacedKey(plugin, "wandering_angler");
        this.fish1Key = new NamespacedKey(plugin, "wa_fish_1");
        this.fish2Key = new NamespacedKey(plugin, "wa_fish_2");
        this.fish3Key = new NamespacedKey(plugin, "wa_fish_3");
        this.spawnTimeKey = new NamespacedKey(plugin, "wa_spawn_time");

        plugin.getServer().getPluginManager().registerEvents(this, plugin);

        checkExistingAnglers();
        startSpawningTask();
    }

    private void checkExistingAnglers() {
        long now = System.currentTimeMillis();
        long maxAge = 15 * 60 * 1000L;

        for (World w : Bukkit.getWorlds()) {
            for (Entity e : w.getEntities()) {
                if (e.getPersistentDataContainer().has(anglerKey, PersistentDataType.BYTE)) {
                    Long spawnTime = e.getPersistentDataContainer().get(spawnTimeKey, PersistentDataType.LONG);
                    if (spawnTime == null) {
                        e.remove();
                        continue;
                    }

                    long age = now - spawnTime;
                    if (age >= maxAge) {
                        e.remove();
                    } else if (activeAnglerUuid == null) {
                        activeAnglerUuid = e.getUniqueId();
                        long remainingTicks = (maxAge - age) / 50L;
                        scheduleDespawn(activeAnglerUuid, remainingTicks);
                    } else {
                        e.remove();
                    }
                }
            }
        }
    }

    private void startSpawningTask() {
        spawningTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
            if (players.isEmpty()) return;

            Player target = players.get(random.nextInt(players.size()));
            Location spawnLoc = findSafeSpawn(target.getLocation());
            if (spawnLoc != null) {
                spawnAngler(spawnLoc);
            }
        }, 54000L, 54000L);
    }

    private Location findSafeSpawn(Location playerLoc) {
        int radius = 15;
        for (int i = 0; i < 20; i++) {
            int dx = random.nextInt(radius * 2) - radius;
            int dz = random.nextInt(radius * 2) - radius;
            Location tryLoc = playerLoc.clone().add(dx, 0, dz);
            Block highest = tryLoc.getWorld().getHighestBlockAt(tryLoc);
            if (highest.getType().isSolid() && highest.getType() != Material.LAVA) {
                Location spawnLoc = highest.getLocation().add(0.5, 1, 0.5);
                if (spawnLoc.distance(playerLoc) > 5) {
                    return spawnLoc;
                }
            }
        }
        return null;
    }

    public void spawnAngler(Location loc) {
        ConfigurationSection speciesSec = module.getConfig().getConfigurationSection("species");
        if (speciesSec == null) return;
        List<String> keys = new ArrayList<>(speciesSec.getKeys(false));
        if (keys.isEmpty()) return;

        removeActiveAngler();

        Villager trader = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
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
        pdc.set(anglerKey, PersistentDataType.BYTE, (byte) 1);
        pdc.set(spawnTimeKey, PersistentDataType.LONG, System.currentTimeMillis());

        Collections.shuffle(keys);
        List<String> chosenFish = new ArrayList<>();
        for (int i = 0; i < Math.min(3, keys.size()); i++) {
            chosenFish.add(keys.get(i));
        }

        if (chosenFish.size() > 0) pdc.set(fish1Key, PersistentDataType.STRING, chosenFish.get(0));
        if (chosenFish.size() > 1) pdc.set(fish2Key, PersistentDataType.STRING, chosenFish.get(1));
        if (chosenFish.size() > 2) pdc.set(fish3Key, PersistentDataType.STRING, chosenFish.get(2));

        loc.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, loc.clone().add(0, 1, 0), 20, 0.5, 0.5, 0.5, 0.05);
        loc.getWorld().playSound(loc, Sound.ENTITY_WANDERING_TRADER_AMBIENT, 1f, 1f);

        String prefix = module.getConfig().getString("prefix", "&8[&b&lFishing&8] &r");
        Bukkit.broadcast(TextUtil.colorize(prefix + "&dThe Wandering Angler has appeared somewhere in the world! He will stay for 15 minutes."));

        activeAnglerUuid = trader.getUniqueId();
        scheduleDespawn(activeAnglerUuid, 15 * 60 * 20L);
    }

    private void scheduleDespawn(UUID entityId, long ticks) {
        if (activeDespawnTask != null) {
            activeDespawnTask.cancel();
        }
        activeDespawnTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Entity entity = Bukkit.getEntity(entityId);
            if (entity != null && entity.isValid()) {
                entity.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, entity.getLocation().clone().add(0, 1, 0), 30, 0.5, 0.5, 0.5, 0.05);
                entity.getWorld().playSound(entity.getLocation(), Sound.ENTITY_WANDERING_TRADER_DISAPPEARED, 1f, 1f);
                entity.remove();
                String prefix = module.getConfig().getString("prefix", "&8[&b&lFishing&8] &r");
                Bukkit.broadcast(TextUtil.colorize(prefix + "&7The Wandering Angler has departed."));
            }
            activeAnglerUuid = null;
        }, ticks);
    }

    private void removeActiveAngler() {
        if (activeDespawnTask != null) {
            activeDespawnTask.cancel();
            activeDespawnTask = null;
        }
        if (activeAnglerUuid != null) {
            Entity entity = Bukkit.getEntity(activeAnglerUuid);
            if (entity != null && entity.isValid()) {
                entity.remove();
            }
            activeAnglerUuid = null;
        }
    }

    public void shutdown() {
        if (spawningTask != null) {
            spawningTask.cancel();
            spawningTask = null;
        }
        removeActiveAngler();
    }

    public int clearAllAnglers() {
        int count = 0;
        for (org.bukkit.World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(anglerKey, PersistentDataType.BYTE)) {
                    entity.remove();
                    count++;
                }
            }
        }
        
        activeAnglerUuid = null;
        if (activeDespawnTask != null) {
            activeDespawnTask.cancel();
            activeDespawnTask = null;
        }
        return count;
    }

    @EventHandler
    public void onBreed(EntityBreedEvent event) {
        if (event.getMother().getPersistentDataContainer().has(anglerKey, PersistentDataType.BYTE) ||
            event.getFather().getPersistentDataContainer().has(anglerKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onTransform(EntityTransformEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(anglerKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onAcquireTrade(VillagerAcquireTradeEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(anglerKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity().getPersistentDataContainer().has(anglerKey, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof Villager trader) {
            PersistentDataContainer pdc = trader.getPersistentDataContainer();
            if (pdc.has(anglerKey, PersistentDataType.BYTE)) {
                event.setCancelled(true);
                openBlackMarket(event.getPlayer(), trader);
            }
        }
    }

    @EventHandler
    public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent event) {
        for (org.bukkit.entity.Entity entity : event.getChunk().getEntities()) {
            if (entity.getPersistentDataContainer().has(anglerKey, org.bukkit.persistence.PersistentDataType.BYTE)) {
                if (activeAnglerUuid == null || !entity.getUniqueId().equals(activeAnglerUuid)) {
                    entity.remove();
                }
            }
        }
    }

    private void openBlackMarket(Player player, Villager trader) {
        plugin.getDatabaseManager().executeAsync(() -> {
            Set<String> caughtSpecies = new HashSet<>();
            try (Connection conn = plugin.getDatabaseManager().getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT species_id FROM coresuite_fish_logbook WHERE uuid = ?")) {
                stmt.setString(1, player.getUniqueId().toString());
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        caughtSpecies.add(rs.getString("species_id"));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, "Failed to load fish logbook for NPC", e);
            }
            
            Bukkit.getScheduler().runTask(plugin, () -> {
                Gui gui = new Gui("&0Wandering Angler", 3);
                gui.fillBorder(Material.BLACK_STAINED_GLASS_PANE);

                PersistentDataContainer pdc = trader.getPersistentDataContainer();
                String f1 = pdc.get(fish1Key, PersistentDataType.STRING);
                String f2 = pdc.get(fish2Key, PersistentDataType.STRING);
                String f3 = pdc.get(fish3Key, PersistentDataType.STRING);

                int[] slots = {11, 13, 15};
                String[] fishes = {f1, f2, f3};

                for (int i = 0; i < 3; i++) {
                    if (fishes[i] == null) continue;
                    String speciesKey = fishes[i];
                    ConfigurationSection sec = module.getConfig().getConfigurationSection("species." + speciesKey);
                    if (sec == null) continue;

                    String coloredName = sec.getString("name", speciesKey);
                    String uncoloredName = org.bukkit.ChatColor.stripColor(org.bukkit.ChatColor.translateAlternateColorCodes('&', coloredName));

                    List<String> lore = new ArrayList<>();
                    lore.add("&7I am paying &d2.5x &7the normal");
                    lore.add("&7price for any &b" + uncoloredName + "&7 you have!");
                    lore.add("");
                    lore.add("&aClick here to sell 1x " + uncoloredName);

                    Material mat;
                    try {
                        mat = Material.valueOf(sec.getString("material", "COD").toUpperCase());
                    } catch (Exception e) {
                        mat = Material.COD;
                    }
                    ItemStack displayItem = new ItemStack(mat);
                    ItemMeta meta = displayItem.getItemMeta();
                    if (meta != null) {
                        meta.displayName(TextUtil.colorize("&6&lBounty: " + coloredName));
                        meta.lore(lore.stream().map(TextUtil::colorize).toList());
                        
                        if (sec.contains("custom-model-data")) meta.setCustomModelData(sec.getInt("custom-model-data"));
                        
                        String itemModelStr = sec.getString("item-model", "");
                        if (!itemModelStr.isEmpty()) {
                            meta.setItemModel(NamespacedKey.fromString(itemModelStr));
                        }
                        
                        displayItem.setItemMeta(meta);
                    }
                    
                    if (!caughtSpecies.contains(uncoloredName)) {
                        displayItem = new ItemStack(Material.PAPER);
                        ItemMeta m = displayItem.getItemMeta();
                        if (m != null) {
                            m.displayName(TextUtil.colorize("&8???"));
                            m.setItemModel(NamespacedKey.fromString("barely_default:fish_unknown"));
                            List<String> unkLore = new ArrayList<>();
                            unkLore.add("&7I am looking for an undiscovered fish...");
                            unkLore.add("&cYou have not caught this species yet!");
                            List<String> biomes = sec.getStringList("biomes");
                            if (biomes != null && !biomes.isEmpty()) {
                                unkLore.add("");
                                unkLore.add("&7Required Biomes:");
                                for (String b : biomes) {
                                    unkLore.add("&8- &7" + b);
                                }
                            }
                            m.lore(unkLore.stream().map(TextUtil::colorize).toList());
                            displayItem.setItemMeta(m);
                        }
                    }

                    final boolean isKnown = caughtSpecies.contains(uncoloredName);
                    final String targetStrippedName = uncoloredName;

                    gui.setItem(slots[i], displayItem, click -> {
                        if (!isKnown) {
                            TextUtil.sendMessage(player, "&cYou have not discovered this fish yet!");
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                            return;
                        }
                        boolean sold = false;
                        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
                            ItemStack item = player.getInventory().getItem(slot);
                            if (item == null || !item.hasItemMeta()) continue;
                            
                            ItemMeta m = item.getItemMeta();
                            PersistentDataContainer itemPdc = m.getPersistentDataContainer();
                            NamespacedKey spKey = new NamespacedKey(plugin, "fish_species");
                            if (itemPdc.has(spKey, PersistentDataType.STRING)) {
                                String spName = itemPdc.get(spKey, PersistentDataType.STRING);
                                if (targetStrippedName.equals(spName)) {
                                    double w = itemPdc.getOrDefault(new NamespacedKey(plugin, "fish_weight"), PersistentDataType.DOUBLE, 1.0);
                                    double price = itemPdc.getOrDefault(new NamespacedKey(plugin, "fish_price"), PersistentDataType.DOUBLE, 1.0);
                                    
                                    double totalValue = w * price * 2.5;
                                    
                                    if (item.getAmount() <= 1) {
                                        player.getInventory().setItem(slot, null);
                                    } else {
                                        item.setAmount(item.getAmount() - 1);
                                        player.getInventory().setItem(slot, item);
                                    }
                                    
                                    plugin.getEconomyManager().deposit(player, totalValue);
                                    TextUtil.sendMessage(player, "&aSold 1x " + coloredName + " &ato the Angler for &d$" + String.format("%.2f", totalValue) + "&a!");
                                    player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                                    sold = true;
                                    break;
                                }
                            }
                        }
                        if (!sold) {
                            TextUtil.sendMessage(player, "&cYou don't have any " + coloredName + " &cin your inventory!");
                            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                        }
                    });
                }

                gui.open(player);
                player.playSound(player.getLocation(), Sound.ENTITY_WANDERING_TRADER_TRADE, 1f, 1f);
            });
        });
    }
}
