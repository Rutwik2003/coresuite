/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.kyori.adventure.text.Component
 *  net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
 *  net.md_5.bungee.api.ChatColor
 *  net.md_5.bungee.api.chat.BaseComponent
 *  net.md_5.bungee.api.chat.ClickEvent
 *  net.md_5.bungee.api.chat.ClickEvent$Action
 *  net.md_5.bungee.api.chat.ComponentBuilder
 *  net.md_5.bungee.api.chat.HoverEvent
 *  net.md_5.bungee.api.chat.HoverEvent$Action
 *  net.md_5.bungee.api.chat.TextComponent
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.Location
 *  org.bukkit.Material
 *  org.bukkit.NamespacedKey
 *  org.bukkit.OfflinePlayer
 *  org.bukkit.Particle
 *  org.bukkit.Sound
 *  org.bukkit.World
 *  org.bukkit.attribute.Attribute
 *  org.bukkit.attribute.AttributeModifier
 *  org.bukkit.attribute.AttributeModifier$Operation
 *  org.bukkit.block.Block
 *  org.bukkit.block.BlockFace
 *  org.bukkit.block.data.BlockData
 *  org.bukkit.block.data.Levelled
 *  org.bukkit.command.CommandSender
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.enchantments.Enchantment
 *  org.bukkit.entity.Entity
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.Listener
 *  org.bukkit.event.inventory.PrepareAnvilEvent
 *  org.bukkit.event.inventory.PrepareItemCraftEvent
 *  org.bukkit.event.player.AsyncPlayerChatEvent
 *  org.bukkit.event.player.PlayerInteractEntityEvent
 *  org.bukkit.event.player.PlayerInteractEvent
 *  org.bukkit.inventory.EquipmentSlot
 *  org.bukkit.inventory.EquipmentSlotGroup
 *  org.bukkit.inventory.ItemFlag
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.persistence.PersistentDataType
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.potion.PotionEffect
 *  org.bukkit.potion.PotionEffectType
 *  org.bukkit.scheduler.BukkitRunnable
 *  org.bukkit.util.Vector
 */
package net.rocky.coresuite.module.customfishing;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.BaseComponent;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.customfishing.CustomFishingModule;
import net.rocky.coresuite.module.customfishing.FishingEnchantManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public class LegendaryQuestManager
implements Listener {
    private final CoreSuitePlugin plugin;
    private final CustomFishingModule module;
    private final NamespacedKey compassKey;
    private final NamespacedKey trackerKey;
    private final Map<UUID, Integer> questStages = new ConcurrentHashMap<UUID, Integer>();
    private final Map<UUID, String> chatState = new ConcurrentHashMap<UUID, String>();
    private final Map<UUID, List<Integer>> introOptions = new ConcurrentHashMap<UUID, List<Integer>>();
    private final Set<UUID> inDialogueSequence = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> pearlCooldowns = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, Long> summonCooldowns = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, Long> chatStateTimestamps = new ConcurrentHashMap<UUID, Long>();
    private final Map<UUID, Integer> failCounters = new ConcurrentHashMap<UUID, Integer>();
    private final Map<UUID, Integer> timesCompletedCache = new ConcurrentHashMap<UUID, Integer>();
    private final Map<UUID, Integer> timesGotTCCache = new ConcurrentHashMap<UUID, Integer>();
    private final Map<UUID, Integer> timesDisrespectedCache = new ConcurrentHashMap<UUID, Integer>();
    private final Map<UUID, Boolean> fossilizedAnchorCache = new ConcurrentHashMap<UUID, Boolean>();
    private final Map<UUID, Boolean> stormcallerTridentCache = new ConcurrentHashMap<UUID, Boolean>();
    private BukkitTask compassTrackerTask = null;
    private static final String[][] SCENARIO_TEXTS = new String[][]{{"The freezing tide numbs all panic eventually.", "A man burdened by gold sinks without a fight.", "The leviathans below dragged him down too fast.", "He simply accepted the peace of the deep.", "Stop the riddles, old man. I need a favor.", "He was counting his treasures when the waves took him.", "The abyss itself reached up and pulled him under.", "He clawed at the water, trading everything for one more breath.", "A bolt from the heavens split the sea, and he simply... fell in.", "The fool? He jumped in thinking he could command the tide."}, {"The endless need to fight against the current.", "The shimmer of sunken empires buried in the muck.", "The pure adrenaline when the line snaps taut.", "The absolute silence when the surface breaks.", "You are. Now give me what I'm owed.", "The promise of fortune hidden beneath every wave.", "The pull of something ancient lurking far below.", "The fear that drives a man to risk everything.", "The crack of thunder before the heavens weep.", "I command the sea, old man. It bows to me."}, {"Endurance. He kept swimming long after hope had drowned.", "The weight of his purse pulled him to the bottom.", "Something in the dark water wanted him more than the surface did.", "He made peace with the silence and let it take him.", "Enough with your games. Just tell me what I need.", "He was too busy admiring his reflection in golden coins.", "The deep has teeth, and it was hungry that night.", "He traded his last breath for a chance, and lost.", "The sky cracked open and the sea swallowed the light.", "He believed the ocean owed him something. It didn't."}, {"The cold numbed his fear until surrender felt like warmth.", "He drowned clutching gold bars, refusing to let go.", "A hunter became the hunted in the blackwater.", "In the end, the quiet was all he ever wanted.", "Your riddles bore me. Give me what I came for.", "Fortune smiled, then pushed him overboard.", "Whatever lives in the trench claimed him without ceremony.", "He begged the waves for mercy. They don't speak mortal.", "Lightning kissed the water and something ancient stirred.", "He shouted orders at the tide. The tide did not listen."}, {"The freezing water taught him that survival is the only instinct.", "His pockets were heavy with treasure, his lungs empty of air.", "The predator's jaws closed before he could even scream.", "He chose the stillness. The deep embraced him willingly.", "Spare me the philosophy, I have things to do.", "He spent his life chasing riches and the sea collected the debt.", "From the bottomless dark, something whispered his name.", "Desperation made him foolish. The ocean made him vanish.", "The storm broke him before the water ever could.", "Arrogance is the heaviest anchor a fool can carry."}};
    private static final String[] SCENARIO_PROMPTS = new String[]{"*Cough*... I watched a man surrender to the water 'ere yesterday. Didn't thrash. Didn't scream. Just sank like a stone. Why d'ye think he stayed so quiet?", "Look at 'em... Every fish bites the iron thinkin' it's a blessin'. Tell me, youngster, what's the iron hook that caught your soul?", "I once watched a ship's captain throw his compass into the sea. His crew wept, but he smiled. Why d'ye think he destroyed his only guide?", "There's a fish that swallows its own young to protect them from the storm. Tell me, mortal \u2014 what would ye swallow to keep what matters safe?", "The deepest trench holds no light, no warmth, no mercy. Yet something lives there still. What drives a creature to thrive where everything says it should perish?"};

    public LegendaryQuestManager(CoreSuitePlugin plugin, CustomFishingModule module) {
        this.plugin = plugin;
        this.module = module;
        this.compassKey = new NamespacedKey((Plugin)plugin, "legendary_compass");
        this.trackerKey = new NamespacedKey((Plugin)plugin, "legendary_tracker");
        this.initDatabase();
        this.compassTrackerTask = Bukkit.getScheduler().runTaskTimer((Plugin)plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                World w;
                String wName;
                ItemMeta meta;
                ItemStack item = p.getInventory().getItemInMainHand();
                if (item == null || item.getType() != Material.HEART_OF_THE_SEA || !item.hasItemMeta() || !(meta = item.getItemMeta()).getPersistentDataContainer().has(this.compassKey, PersistentDataType.STRING) && !meta.getPersistentDataContainer().has(this.trackerKey, PersistentDataType.STRING) || (wName = (String)meta.getPersistentDataContainer().get(new NamespacedKey((Plugin)plugin, "lode_w"), PersistentDataType.STRING)) == null || (w = Bukkit.getWorld((String)wName)) == null || !p.getWorld().equals((Object)w)) continue;
                double tx = (Double)meta.getPersistentDataContainer().get(new NamespacedKey((Plugin)plugin, "lode_x"), PersistentDataType.DOUBLE);
                double ty = (Double)meta.getPersistentDataContainer().get(new NamespacedKey((Plugin)plugin, "lode_y"), PersistentDataType.DOUBLE);
                double tz = (Double)meta.getPersistentDataContainer().get(new NamespacedKey((Plugin)plugin, "lode_z"), PersistentDataType.DOUBLE);
                Location lode = new Location(w, tx, ty, tz);
                double dist = p.getLocation().distance(lode);
                if (dist < 15.0) {
                    p.sendActionBar((Component)LegacyComponentSerializer.legacyAmpersand().deserialize("&e&lYou have reached the spot! Right-Click to summon him!"));
                    continue;
                }
                p.sendActionBar((Component)LegacyComponentSerializer.legacyAmpersand().deserialize("&bDistance to Ancient Angler: &e" + (int)dist + "m"));
            }
        }, 20L, 20L);
    }

    private void initDatabase() {
        this.plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection c = this.plugin.getDatabaseManager().getConnection();
                 Statement s = c.createStatement();){
                s.execute("CREATE TABLE IF NOT EXISTS coresuite_fisherman_quest (uuid TEXT PRIMARY KEY, stage INTEGER DEFAULT 0)");
                try {
                    s.execute("ALTER TABLE coresuite_fisherman_quest ADD COLUMN times_completed INTEGER DEFAULT 0");
                }
                catch (Exception exception) {
                    // empty catch block
                }
                try {
                    s.execute("ALTER TABLE coresuite_fisherman_quest ADD COLUMN times_got_tc INTEGER DEFAULT 0");
                }
                catch (Exception exception) {
                    // empty catch block
                }
                try {
                    s.execute("ALTER TABLE coresuite_fisherman_quest ADD COLUMN times_disrespected INTEGER DEFAULT 0");
                }
                catch (Exception exception) {
                    // empty catch block
                }
                try {
                    s.execute("ALTER TABLE coresuite_fisherman_quest ADD COLUMN fail_count INTEGER DEFAULT 0");
                }
                catch (Exception exception) {
                    // empty catch block
                }
                try {
                    s.execute("ALTER TABLE coresuite_fisherman_quest ADD COLUMN has_fossilized_anchor INTEGER DEFAULT 0");
                }
                catch (Exception exception) {
                    // empty catch block
                }
                try {
                    s.execute("ALTER TABLE coresuite_fisherman_quest ADD COLUMN has_stormcaller_trident INTEGER DEFAULT 0");
                }
                catch (Exception exception) {
                    // empty catch block
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void loadPlayer(UUID uuid) {
        this.plugin.getDatabaseManager().executeAsync(() -> {
            try (Connection c = this.plugin.getDatabaseManager().getConnection();
                 PreparedStatement ps = c.prepareStatement("SELECT stage, times_completed, times_got_tc, times_disrespected, fail_count, has_fossilized_anchor, has_stormcaller_trident FROM coresuite_fisherman_quest WHERE uuid = ?");){
                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();
                if (rs.next()) {
                    this.questStages.put(uuid, rs.getInt("stage"));
                    this.timesCompletedCache.put(uuid, rs.getInt("times_completed"));
                    this.timesGotTCCache.put(uuid, rs.getInt("times_got_tc"));
                    this.timesDisrespectedCache.put(uuid, rs.getInt("times_disrespected"));
                    this.failCounters.put(uuid, rs.getInt("fail_count"));
                    this.fossilizedAnchorCache.put(uuid, rs.getInt("has_fossilized_anchor") == 1);
                    this.stormcallerTridentCache.put(uuid, rs.getInt("has_stormcaller_trident") == 1);
                } else {
                    this.questStages.put(uuid, 0);
                    this.timesCompletedCache.put(uuid, 0);
                    this.timesGotTCCache.put(uuid, 0);
                    this.timesDisrespectedCache.put(uuid, 0);
                    this.failCounters.put(uuid, 0);
                    this.fossilizedAnchorCache.put(uuid, false);
                    this.stormcallerTridentCache.put(uuid, false);
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void unloadPlayer(UUID uuid) {
        this.questStages.remove(uuid);
        this.timesCompletedCache.remove(uuid);
        this.timesGotTCCache.remove(uuid);
        this.timesDisrespectedCache.remove(uuid);
        this.fossilizedAnchorCache.remove(uuid);
        this.stormcallerTridentCache.remove(uuid);
        this.chatState.remove(uuid);
        this.introOptions.remove(uuid);
        this.inDialogueSequence.remove(uuid);
        this.pearlCooldowns.remove(uuid);
        this.summonCooldowns.remove(uuid);
        this.chatStateTimestamps.remove(uuid);
        this.failCounters.remove(uuid);
    }

    public void shutdown() {
        if (this.compassTrackerTask != null) {
            this.compassTrackerTask.cancel();
            this.compassTrackerTask = null;
        }
        this.questStages.clear();
        this.timesCompletedCache.clear();
        this.timesGotTCCache.clear();
        this.timesDisrespectedCache.clear();
        this.fossilizedAnchorCache.clear();
        this.stormcallerTridentCache.clear();
        this.chatState.clear();
        this.introOptions.clear();
        this.inDialogueSequence.clear();
        this.pearlCooldowns.clear();
        this.summonCooldowns.clear();
        this.chatStateTimestamps.clear();
        this.failCounters.clear();
    }

    public int getStage(UUID uuid) {
        return this.questStages.getOrDefault(uuid, 0);
    }

    public void setStage(UUID uuid, int stage) {
        this.questStages.put(uuid, stage);
        this.plugin.getDatabaseManager().execute("INSERT INTO coresuite_fisherman_quest (uuid, stage) VALUES (?, ?) ON CONFLICT(uuid) DO UPDATE SET stage = excluded.stage", uuid.toString(), stage);
    }

    public int getTimesCompleted(UUID uuid) {
        return this.timesCompletedCache.getOrDefault(uuid, 0);
    }

    public void incrementTimesCompleted(UUID uuid) {
        int next = this.timesCompletedCache.compute(uuid, (k, v) -> v == null ? 1 : v + 1);
        this.plugin.getDatabaseManager().execute("UPDATE coresuite_fisherman_quest SET times_completed = ? WHERE uuid = ?", next, uuid.toString());
    }

    public int getTimesGotTC(UUID uuid) {
        return this.timesGotTCCache.getOrDefault(uuid, 0);
    }

    public void incrementTimesGotTC(UUID uuid) {
        int next = this.timesGotTCCache.compute(uuid, (k, v) -> v == null ? 1 : v + 1);
        this.plugin.getDatabaseManager().execute("UPDATE coresuite_fisherman_quest SET times_got_tc = ? WHERE uuid = ?", next, uuid.toString());
    }

    public int getTimesDisrespected(UUID uuid) {
        return this.timesDisrespectedCache.getOrDefault(uuid, 0);
    }

    public void incrementTimesDisrespected(UUID uuid) {
        int next = this.timesDisrespectedCache.compute(uuid, (k, v) -> v == null ? 1 : v + 1);
        this.plugin.getDatabaseManager().execute("UPDATE coresuite_fisherman_quest SET times_disrespected = ? WHERE uuid = ?", next, uuid.toString());
    }

    public boolean hasFossilizedAnchor(UUID uuid) {
        return this.fossilizedAnchorCache.getOrDefault(uuid, false);
    }

    public void setHasFossilizedAnchor(UUID uuid) {
        this.fossilizedAnchorCache.put(uuid, true);
        this.plugin.getDatabaseManager().execute("UPDATE coresuite_fisherman_quest SET has_fossilized_anchor = 1 WHERE uuid = ?", uuid.toString());
    }

    public boolean hasStormcallerTrident(UUID uuid) {
        return this.stormcallerTridentCache.getOrDefault(uuid, false);
    }

    public void setHasStormcallerTrident(UUID uuid) {
        this.stormcallerTridentCache.put(uuid, true);
        this.plugin.getDatabaseManager().execute("UPDATE coresuite_fisherman_quest SET has_stormcaller_trident = 1 WHERE uuid = ?", uuid.toString());
    }

    public void saveFailCount(UUID uuid, int count) {
        this.failCounters.put(uuid, count);
        this.plugin.getDatabaseManager().execute("UPDATE coresuite_fisherman_quest SET fail_count = ? WHERE uuid = ?", count, uuid.toString());
    }

    public void loadFailCount(UUID uuid) {
        // Handled in loadPlayer
    }

    public boolean isInChatState(UUID uuid) {
        return this.chatState.containsKey(uuid);
    }

    private void setChatState(UUID uuid, String state) {
        this.chatState.put(uuid, state);
        this.chatStateTimestamps.put(uuid, System.currentTimeMillis());
        this.module.getLegendaryNPCManager().updateInteraction(uuid);
    }

    public void giveMap(Player player) {
        ItemStack compass = new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta meta = compass.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.legacyColorize("&b&lTidecaller's Pearl"));
            ArrayList<String> lore = new ArrayList<String>();
            lore.add(TextUtil.legacyColorize("&7An ancient pearl whispering"));
            lore.add(TextUtil.legacyColorize("&7of a hidden coastline..."));
            lore.add(TextUtil.legacyColorize(""));
            lore.add(TextUtil.legacyColorize("&eRight-Click to reveal a magical"));
            lore.add(TextUtil.legacyColorize("&etrail, or summon him if nearby!"));
            meta.setLore(lore);
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS});
            int targetX = player.getLocation().getBlockX() + ThreadLocalRandom.current().nextInt(-5000, 5000);
            int targetZ = player.getLocation().getBlockZ() + ThreadLocalRandom.current().nextInt(-5000, 5000);
            Location targetLoc = new Location(player.getWorld(), (double)targetX, 62.0, (double)targetZ);
            meta.getPersistentDataContainer().set(new NamespacedKey((Plugin)this.plugin, "lode_w"), PersistentDataType.STRING, targetLoc.getWorld().getName());
            meta.getPersistentDataContainer().set(new NamespacedKey((Plugin)this.plugin, "lode_x"), PersistentDataType.DOUBLE, targetLoc.getX());
            meta.getPersistentDataContainer().set(new NamespacedKey((Plugin)this.plugin, "lode_y"), PersistentDataType.DOUBLE, targetLoc.getY());
            meta.getPersistentDataContainer().set(new NamespacedKey((Plugin)this.plugin, "lode_z"), PersistentDataType.DOUBLE, targetLoc.getZ());
            meta.getPersistentDataContainer().set(this.compassKey, PersistentDataType.STRING, player.getUniqueId().toString());
            compass.setItemMeta(meta);
            HashMap leftover = player.getInventory().addItem(new ItemStack[]{compass});
            if (!leftover.isEmpty()) {
                player.getWorld().dropItem(player.getLocation(), (ItemStack)leftover.get(0));
            }
            TextUtil.sendMessage((CommandSender)player, "&b\ud83d\udc1f &lYou found the Tidecaller's Pearl! &7Right-click it to uncover its secrets.");
        }
    }

    @EventHandler
    public void onCraft(PrepareItemCraftEvent event) {
        if (event.getInventory().getMatrix() == null) {
            return;
        }
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (item == null || !item.hasItemMeta() || !item.getItemMeta().getPersistentDataContainer().has(this.compassKey, PersistentDataType.STRING) && !item.getItemMeta().getPersistentDataContainer().has(this.trackerKey, PersistentDataType.STRING)) continue;
            event.getInventory().setResult(null);
            break;
        }
    }

    @EventHandler
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        for (ItemStack item : new ItemStack[]{event.getInventory().getItem(0), event.getInventory().getItem(1)}) {
            if (item == null || !item.hasItemMeta() || !item.getItemMeta().getPersistentDataContainer().has(new NamespacedKey((Plugin)this.plugin, "trophy_item"), PersistentDataType.STRING)) continue;
            event.setResult(null);
            return;
        }
    }

    @EventHandler
    public void onInteractTracker(PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) {
            return;
        }
        final Player player = event.getPlayer();
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return;
        }
        if (meta.getPersistentDataContainer().has(this.compassKey, PersistentDataType.STRING) || meta.getPersistentDataContainer().has(this.trackerKey, PersistentDataType.STRING)) {
            String wName;
            event.setCancelled(true);
            NamespacedKey key = meta.getPersistentDataContainer().has(this.compassKey, PersistentDataType.STRING) ? this.compassKey : this.trackerKey;
            String ownerUUIDStr = (String)meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
            if (!player.getUniqueId().toString().equals(ownerUUIDStr)) {
                TextUtil.sendMessage((CommandSender)player, "&cThis ancient device does not react to you.");
                return;
            }
            if (item.getType() == Material.HEART_OF_THE_SEA && (wName = (String)meta.getPersistentDataContainer().get(new NamespacedKey((Plugin)this.plugin, "lode_w"), PersistentDataType.STRING)) != null) {
                World w = Bukkit.getWorld((String)wName);
                if (w == null || !w.equals((Object)player.getWorld())) {
                    TextUtil.sendMessage((CommandSender)player, "&cThe pearl's magic fades... (Wrong World)");
                    return;
                }
                double tx = (Double)meta.getPersistentDataContainer().get(new NamespacedKey((Plugin)this.plugin, "lode_x"), PersistentDataType.DOUBLE);
                double ty = (Double)meta.getPersistentDataContainer().get(new NamespacedKey((Plugin)this.plugin, "lode_y"), PersistentDataType.DOUBLE);
                double tz = (Double)meta.getPersistentDataContainer().get(new NamespacedKey((Plugin)this.plugin, "lode_z"), PersistentDataType.DOUBLE);
                Location lodestone = new Location(w, tx, ty, tz);
                double dist = player.getLocation().distance(lodestone);
                if (dist < 15.0) {
                    long lastSummon = this.summonCooldowns.getOrDefault(player.getUniqueId(), 0L);
                    if (System.currentTimeMillis() - lastSummon < 45000L) {
                        TextUtil.sendMessage((CommandSender)player, "&cThe pearl is recharging its summoning magic... please wait.");
                        return;
                    }
                    this.summonCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
                    this.performRitual(player, item, key.equals((Object)this.compassKey));
                } else {
                    long lastTrail = this.pearlCooldowns.getOrDefault(player.getUniqueId(), 0L);
                    if (System.currentTimeMillis() - lastTrail < 10000L) {
                        return;
                    }
                    this.pearlCooldowns.put(player.getUniqueId(), System.currentTimeMillis());
                    TextUtil.sendMessage((CommandSender)player, "&bThe pearl hums softly... you are &e" + (int)dist + " &bblocks away.");
                    final Vector d = lodestone.toVector().subtract(player.getLocation().toVector());
                    d.setY(0);
                    if (d.lengthSquared() > 0.0) {
                        d.normalize();
                        Vector up = new Vector(0, 1, 0);
                        if (Math.abs(d.getY()) > 0.99) {
                            up = new Vector(1, 0, 0);
                        }
                        final Vector u = d.clone().crossProduct(up).normalize();
                        final Vector v = d.clone().crossProduct(u).normalize();
                        player.playSound(player.getLocation(), Sound.BLOCK_CONDUIT_AMBIENT, 1.0f, 1.5f);
                        new BukkitRunnable(){
                            int steps = 0;
                            Location startLoc = player.getLocation().add(0.0, 1.2, 0.0);

                            public void run() {
                                if (this.steps > 40 || !player.isOnline()) {
                                    this.cancel();
                                    return;
                                }
                                for (int i = 0; i < 2; ++i) {
                                    double forward = (double)this.steps * 0.4;
                                    double radius = 0.5;
                                    double angle = (double)this.steps * 0.6;
                                    Vector offset1 = d.clone().multiply(forward).add(u.clone().multiply(radius * Math.cos(angle))).add(v.clone().multiply(radius * Math.sin(angle)));
                                    Vector offset2 = d.clone().multiply(forward).add(u.clone().multiply(radius * Math.cos(angle + Math.PI))).add(v.clone().multiply(radius * Math.sin(angle + Math.PI)));
                                    Location pLoc1 = this.startLoc.clone().add(offset1);
                                    Location pLoc2 = this.startLoc.clone().add(offset2);
                                    pLoc1.getWorld().spawnParticle(Particle.END_ROD, pLoc1, 1, 0.0, 0.0, 0.0, 0.01);
                                    pLoc2.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, pLoc2, 1, 0.0, 0.0, 0.0, 0.01);
                                    ++this.steps;
                                }
                            }
                        }.runTaskTimer((Plugin)this.plugin, 0L, 1L);
                    }
                }
            }
        }
    }

    private void performRitual(final Player player, ItemStack item, final boolean isFirstTime) {
        if (!module.getLegendaryNPCManager().isAvailable()) {
            TextUtil.sendMessage(player, "&cThe Ancient Angler is unavailable. This feature requires Citizens.");
            return;
        }

        final UUID u = player.getUniqueId();
        if (this.inDialogueSequence.contains(u)) {
            return;
        }
        this.inDialogueSequence.add(u);
        if (isFirstTime) {
            ItemMeta meta = item.getItemMeta();
            meta.setDisplayName(TextUtil.legacyColorize("&b&lAngler's Tracker"));
            ArrayList<String> lore = new ArrayList<String>();
            lore.add(TextUtil.legacyColorize("&7Permanently tracks the location of"));
            lore.add(TextUtil.legacyColorize("&7the Legendary Ancient Angler."));
            lore.add(TextUtil.legacyColorize(""));
            lore.add(TextUtil.legacyColorize("&eRight-Click this pearl to reveal"));
            lore.add(TextUtil.legacyColorize("&ethe glowing path towards him!"));
            meta.setLore(lore);
            meta.addEnchant(Enchantment.LURE, 1, true);
            meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS});
            meta.getPersistentDataContainer().remove(this.compassKey);
            meta.getPersistentDataContainer().set(this.trackerKey, PersistentDataType.STRING, player.getUniqueId().toString());
            item.setItemMeta(meta);
            TextUtil.sendMessage((CommandSender)player, "&bThe pearl glows brightly in your hands, revealing its true form!");
            player.playSound(player.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1.0f, 1.0f);
        } else {
            TextUtil.sendMessage((CommandSender)player, "&bThe tracker pearl vibrates violently in your hands...");
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 1.0f, 2.0f);
        }
        Location spawnLoc = player.getLocation().add(player.getLocation().getDirection().setY(0).normalize().multiply(2));
        spawnLoc.setY(player.getLocation().getY());
        if (spawnLoc.getBlock().getType().isSolid()) {
            spawnLoc = player.getLocation();
        }
        Block feetBlock = spawnLoc.getBlock();
        while (feetBlock.getType().isAir() && feetBlock.getY() > feetBlock.getWorld().getMinHeight()) {
            feetBlock = feetBlock.getRelative(BlockFace.DOWN);
        }
        if (feetBlock.getType() == Material.WATER || feetBlock.getType() == Material.SEAGRASS || feetBlock.getType() == Material.KELP_PLANT || feetBlock.getType() == Material.KELP) {
            Block topWater = feetBlock;
            while (topWater.getRelative(BlockFace.UP).getType() == Material.WATER) {
                topWater = topWater.getRelative(BlockFace.UP);
            }
            Block above = topWater.getRelative(BlockFace.UP);
            if (above.getType().isAir()) {
                spawnLoc = above.getLocation().add(0.5, 0.1, 0.5);
            } else {
                spawnLoc.add(0.0, 0.5, 0.0);
            }
        } else if (feetBlock.getType() != Material.AIR) {
            spawnLoc = feetBlock.getLocation().add(0.5, 1.0, 0.5);
        }
        final Location finalSpawnLoc = spawnLoc;
        new BukkitRunnable(){
            int tick = 0;

            public void run() {
                if (!player.isOnline()) {
                    LegendaryQuestManager.this.inDialogueSequence.remove(u);
                    this.cancel();
                    return;
                }
                if (this.tick < 40) {
                    if (this.tick % 5 == 0) {
                        player.getWorld().spawnParticle(Particle.NAUTILUS, finalSpawnLoc.clone().add(0.0, 1.0, 0.0), 10, 0.5, 0.5, 0.5, 0.05);
                        player.getWorld().spawnParticle(Particle.SPLASH, finalSpawnLoc.clone().add(0.0, 1.0, 0.0), 20, 0.5, 0.5, 0.5, 0.5);
                        player.playSound(finalSpawnLoc, Sound.BLOCK_WATER_AMBIENT, 1.0f, 0.5f);
                    }
                    if (this.tick == 20) {
                        player.playSound(finalSpawnLoc, Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 1.0f);
                    }
                } else if (this.tick == 40) {
                    player.getWorld().spawnParticle(Particle.CLOUD, finalSpawnLoc.clone().add(0.0, 1.0, 0.0), 20);
                    player.getWorld().spawnParticle(Particle.SPLASH, finalSpawnLoc.clone().add(0.0, 1.0, 0.0), 200, 1.0, 2.0, 1.0, 0.5);
                    player.getWorld().spawnParticle(Particle.SOUL, finalSpawnLoc.clone().add(0.0, 1.0, 0.0), 50, 0.5, 1.0, 0.5, 0.1);
                    player.playSound(finalSpawnLoc, Sound.ENTITY_GENERIC_SPLASH, 1.0f, 0.5f);
                    player.playSound(finalSpawnLoc, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 0.8f);
                    LegendaryQuestManager.this.module.getLegendaryNPCManager().spawnLegendaryNPC(u, finalSpawnLoc);
                    Block blockBelow = finalSpawnLoc.clone().subtract(0.0, 0.1, 0.0).getBlock();
                    Block blockBelowThat = blockBelow.getRelative(BlockFace.DOWN);
                    if (blockBelow.getType().isAir() && blockBelowThat.getType() == Material.WATER) {
                        Levelled levelled;
                        BlockData data = blockBelowThat.getBlockData();
                        if (data instanceof Levelled && (levelled = (Levelled)data).getLevel() != 0) {
                            levelled.setLevel(0);
                            blockBelowThat.setBlockData((BlockData)levelled);
                        }
                        blockBelow.setType(Material.LILY_PAD);
                        LegendaryQuestManager.this.module.getLegendaryNPCManager().registerLilyPad(player.getUniqueId(), blockBelow.getLocation());
                    }
                    if (isFirstTime && LegendaryQuestManager.this.module.getConfig().getBoolean("legendary-quest.broadcast-on-first-summon", true)) {
                        Sound broadcastSound;
                        String soundName = LegendaryQuestManager.this.module.getConfig().getString("legendary-quest.broadcast-sound", "ENTITY_WITHER_SPAWN");
                        float volume = (float)LegendaryQuestManager.this.module.getConfig().getDouble("legendary-quest.broadcast-sound-volume", 0.5);
                        float pitch = (float)LegendaryQuestManager.this.module.getConfig().getDouble("legendary-quest.broadcast-sound-pitch", 0.7);
                        try {
                            broadcastSound = net.rocky.coresuite.common.compat.PaperCompat.sound((String)soundName);
                        }
                        catch (Exception e) {
                            broadcastSound = Sound.ENTITY_WITHER_SPAWN;
                        }
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            TextUtil.sendMessage((CommandSender)p, "&8&l\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550");
                            TextUtil.sendMessage((CommandSender)p, "&b&l\u2693 The Ancient Angler &7has been summoned from the");
                            TextUtil.sendMessage((CommandSender)p, "&7treacherous depths of the ocean by &e&l" + player.getName() + "&7...");
                            TextUtil.sendMessage((CommandSender)p, "&8&l\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550\u2550");
                            p.playSound(p.getLocation(), broadcastSound, volume, pitch);
                        }
                    }
                } else if (this.tick == 80) {
                    if (isFirstTime) {
                        LegendaryQuestManager.this.setStage(u, 10);
                        final int timesCompleted = LegendaryQuestManager.this.getTimesCompleted(u);
                        final int timesDisrespected = LegendaryQuestManager.this.getTimesDisrespected(u);
                        new BukkitRunnable(){
                            int dTick = 0;

                            public void run() {
                                if (!player.isOnline()) {
                                    LegendaryQuestManager.this.inDialogueSequence.remove(u);
                                    this.cancel();
                                    return;
                                }
                                if (this.dTick == 0) {
                                    if (timesDisrespected > 0 && ThreadLocalRandom.current().nextBoolean()) {
                                        TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f YOU DARE RETURN?!");
                                        player.playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1.0f, 0.8f);
                                    } else if (timesCompleted == 0) {
                                        TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f Who awakens the Ancient Angler from the depths...?");
                                    } else if (timesCompleted <= 2) {
                                        String[] lines = new String[]{"Ah... we meet again, mortal. The tides bring you back.", "The currents whisper your name again... Let us see what path the tides choose.", "You return. Good. The sea tests those who persist."};
                                        TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f " + lines[ThreadLocalRandom.current().nextInt(lines.length)]);
                                    } else {
                                        String[] lines = new String[]{"You again? The depths grow weary of your visits.", "How many times must we dance this ritual, mortal?", "Your persistence borders on obsession. The sea respects that... barely."};
                                        TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f " + lines[ThreadLocalRandom.current().nextInt(lines.length)]);
                                    }
                                } else if (this.dTick == 1) {
                                    if (timesDisrespected > 0 && timesCompleted == 0) {
                                        TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f ...Fine. Perhaps you've learned.");
                                    } else if (timesCompleted == 0) {
                                        TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f Ah... another mortal seeking the legends of the deep.");
                                    } else {
                                        TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f The ocean remembers all, mortal. Let us continue.");
                                    }
                                } else if (this.dTick == 2) {
                                    LegendaryQuestManager.this.inDialogueSequence.remove(u);
                                    LegendaryQuestManager.this.startIntroDialogue(player);
                                    this.cancel();
                                }
                                ++this.dTick;
                            }
                        }.runTaskTimer((Plugin)LegendaryQuestManager.this.plugin, 0L, 80L);
                    } else {
                        LegendaryQuestManager.this.inDialogueSequence.remove(u);
                        LegendaryQuestManager.this.handleNPCInteract(player);
                    }
                    this.cancel();
                }
                ++this.tick;
            }
        }.runTaskTimer((Plugin)this.plugin, 0L, 1L);
    }

    @EventHandler
    public void onInteractNPC(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!(event.getRightClicked() instanceof Entity)) {
            return;
        }
        Entity villager = event.getRightClicked();
        if (villager.getPersistentDataContainer().has(this.module.getLegendaryNPCManager().getLegendaryKey(), PersistentDataType.STRING)) {
            event.setCancelled(true);
            String ownerId = (String)villager.getPersistentDataContainer().get(this.module.getLegendaryNPCManager().getLegendaryKey(), PersistentDataType.STRING);
            if (ownerId != null && !ownerId.equals(event.getPlayer().getUniqueId().toString())) {
                TextUtil.sendMessage((CommandSender)event.getPlayer(), "&cThe Ancient Angler glares at you. He is waiting for someone else.");
                return;
            }
            this.handleNPCInteract(event.getPlayer());
        }
    }

    private void handleNPCInteract(Player player) {
        if (this.inDialogueSequence.contains(player.getUniqueId())) {
            return;
        }
        int stage = this.getStage(player.getUniqueId());
        if (stage == 0) {
            TextUtil.sendMessage((CommandSender)player, "&cYou have not found the compass yet.");
        } else if (stage == 10) {
            this.startIntroDialogue(player);
        } else if (stage == 101) {
            this.interactiveTurnIn(player, "turnin_101", "To brave the great currents, ye must first build a shell. The shallows provide the paint, vibrant and fragile. Bring me the painted school, and I'll show ye the path they took.");
        } else if (stage == 102) {
            this.interactiveTurnIn(player, "turnin_102", "Aye... but the painted ones never survive the drop. Down there, the sun dies and the water bites like iron. Show me ye know what it takes to survive the freeze.");
        } else if (stage == 103) {
            this.interactiveTurnIn(player, "turnin_103", "Ice in the veins. Good. But surviving ain't enough; ye must conquer the pull. Bring me the red swimmer of the streams, the one that fights the inevitable.");
        } else if (stage == 104) {
            this.interactiveTurnIn(player, "turnin_104", "A true scrapper. But true endurance is forged at the very bottom, where the ocean boils against the earth's crust. Reach into the inferno. Hand me the ember.");
        } else if (stage == 201) {
            this.interactiveTurnIn(player, "turnin_201", "Gold... it be the heaviest anchor a man can tie to his own neck. The ocean has her own currency, spilled from a thousand wrecks. Spill two hands full of her varied coins before me.");
        } else if (stage == 202) {
            this.interactiveTurnIn(player, "turnin_202", "A beggar's purse. If ye want the true hoard, ye must understand a king's folly. Fetch me the catch that shines so bright it blinds greedy fools to the kraken behind it.");
        } else if (stage == 203) {
            this.interactiveTurnIn(player, "turnin_203", "Pure, untarnished vanity. But gold ain't born in the water; it's born in the crushing heat of the deep earth. Bring me the forge's beating heart.");
        } else if (stage == 301) {
            this.interactiveTurnIn(player, "turnin_301", "The Hunt! Har! There's no music sweeter than the snap of a line in the black water. To track the monsters, ye must first feed on the frenzied biters. Bring me a handful of razors.");
        } else if (stage == 302) {
            this.interactiveTurnIn(player, "turnin_302", "Vicious little demons... but they're just bait for the serpents. To learn the ambush, ye must study the beasts that hide in the rocks. Haul me three of 'em.");
        } else if (stage == 303) {
            this.interactiveTurnIn(player, "turnin_303", "Jaws like rusty bear traps, aye. Now, ye are ready for the apex of the dark. The one that weaves the traps. Bring me the Queen.");
        } else if (stage == 401) {
            this.interactiveTurnIn(player, "turnin_401", "The sea is vast, kid, and full of pointless noise. To hear the deep's true secrets, ye must first drown out the chatter. Bring me a bounty of the common rabble. A fisherman's dozen, plus two.");
        } else if (stage == 402) {
            this.interactiveTurnIn(player, "turnin_402", "Ah... feel how the water quiets down? Now, true silence means focusing on a single, insignificant detail among thousands. Find me a silver speck of a massive school.");
        } else if (stage == 403) {
            this.interactiveTurnIn(player, "turnin_403", "Just a speck... And finally, the ultimate stillness. A creature that doesn't fight, doesn't swim. It merely clings to the bottom, and watches the tides roll by.");
        } else if (stage == 601) {
            this.interactiveTurnIn(player, "turnin_601", "The sea hoards its riches beneath the coral thrones. Bring me a tax from the common waters.");
        } else if (stage == 602) {
            this.interactiveTurnIn(player, "turnin_602", "Find me the golden scale that glimmers at the bottom of murky rivers.");
        } else if (stage == 603) {
            this.interactiveTurnIn(player, "turnin_603", "Now bring me the armored jewels of the frozen waters. Two of them.");
        } else if (stage == 701) {
            this.interactiveTurnIn(player, "turnin_701", "The deep feeds on darkness. Bring me creatures born where light fears to reach.");
        } else if (stage == 702) {
            this.interactiveTurnIn(player, "turnin_702", "Fetch me the demons that swim through volcanic veins. Two of them.");
        } else if (stage == 703) {
            this.interactiveTurnIn(player, "turnin_703", "The ultimate darkness... bring me the wrasse forged in the nether's forge. Two of them.");
        } else if (stage == 801) {
            this.interactiveTurnIn(player, "turnin_801", "First, prove your desperation. Sacrifice the common to show your hunger.");
        } else if (stage == 802) {
            this.interactiveTurnIn(player, "turnin_802", "Good... now bring me something precious. A rare creature from the cold.");
        } else if (stage == 803) {
            this.interactiveTurnIn(player, "turnin_803", "Yes... YES! One final offering... bring me your finest catch!");
        } else if (stage == 901) {
            this.interactiveTurnIn(player, "turnin_901", "Lightning dances on the water's skin. Bring me the fish that rides the current.");
        } else if (stage == 902) {
            this.interactiveTurnIn(player, "turnin_902", "The storm's child hides in frozen rivers. Bring me the trout touched by winter.");
        } else if (stage == 903) {
            this.interactiveTurnIn(player, "turnin_903", "Finally... the beast that commands the jungle rivers with fury.");
        }
    }

    private void interactiveTurnIn(Player player, String state, String riddlePrompt) {
        this.setChatState(player.getUniqueId(), state);
        TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f " + riddlePrompt);
        TextUtil.sendMessage((CommandSender)player, "");
        this.sendClickableOption(player, "&a&l[\u2726 Offer the Catch]", "&7Click to present your fish to the Angler", "1");
        double sacCost = this.module.getConfig().getDouble("legendary-quest.sacrifice-cost", 25000.0);
        this.sendClickableOption(player, "&c&l[\u2717 Sacrifice for Answer]", "&7Click to pay " + this.plugin.getEconomyManager().format(sacCost) + " + a rare fish for a direct hint", "3");
        TextUtil.sendMessage((CommandSender)player, "");
        TextUtil.sendMessage((CommandSender)player, "&8&oClick an option above or type 1, 2, or 3 in chat.");
        TextUtil.sendMessage((CommandSender)player, "&8&oType 'cancel' to walk away. Expires in 2 minutes.");
    }

    private void sendClickableOption(Player player, String displayText, String hoverText, String number) {
        TextComponent component = new TextComponent(ChatColor.translateAlternateColorCodes((char)'&', (String)displayText));
        component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/fish quest " + number));
        component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder(hoverText).create()));
        player.spigot().sendMessage((BaseComponent)component);
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID u = player.getUniqueId();
        if (this.chatState.containsKey(u)) {
            long setTime = this.chatStateTimestamps.getOrDefault(u, 0L);
            int timeoutSeconds = this.module.getConfig().getInt("legendary-quest.chat-timeout-seconds", 120);
            if (System.currentTimeMillis() - setTime > (long)timeoutSeconds * 1000L) {
                this.chatState.remove(u);
                this.chatStateTimestamps.remove(u);
                Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> {
                    TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f *The Angler grows impatient and turns away*");
                    TextUtil.sendMessage((CommandSender)player, "&7Your dialogue with the Ancient Angler has timed out. Right-click the NPC to interact again.");
                });
                return;
            }
            event.setCancelled(true);
            String msg = event.getMessage().trim();
            Bukkit.getScheduler().runTask((Plugin)this.plugin, () -> this.processChatSync(player, u, msg));
        }
    }

    public void processChatSync(Player player, UUID u, String msg) {
        if (!this.chatState.containsKey(u)) {
            return;
        }
        if (this.inDialogueSequence.contains(u)) {
            TextUtil.sendMessage((CommandSender)player, "&cWait until the Angler finishes speaking...");
            return;
        }
        String state = this.chatState.get(u);
        if (msg.equalsIgnoreCase("cancel") || msg.equalsIgnoreCase("leave")) {
            TextUtil.sendMessage((CommandSender)player, "&7You walked away from the Angler.");
            this.chatState.remove(u);
            return;
        }
        if (state.equals("awaiting_intro")) {
            List<Integer> options = this.introOptions.get(u);
            if (options == null || options.isEmpty()) {
                this.chatState.remove(u);
                return;
            }
            int chosenPath = -1;
            if (msg.equals("1")) {
                chosenPath = options.get(0);
            } else if (msg.equals("2")) {
                chosenPath = options.get(1);
            } else if (msg.equals("3")) {
                chosenPath = options.get(2);
            } else {
                TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f I do not understand those words. Type 1, 2, or 3.");
                return;
            }
            this.chatState.remove(u);
            if (chosenPath == 1) {
                this.setStage(u, 101);
                this.sendLinesAndRelocate(player, "&e[Ancient Angler]&f You choose to fight the tide. Let us see if you break.", "&e[Ancient Angler]&f To brave the great currents, ye must first build a shell. The shallows provide the paint, vibrant and fragile. Bring me the painted school, and I'll show ye the path they took.");
            } else if (chosenPath == 2) {
                this.setStage(u, 201);
                this.sendLinesAndRelocate(player, "&e[Ancient Angler]&f Heavy pockets... yes. Men drop their coins, but the ocean has its own currency.", "&e[Ancient Angler]&f Gold... it be the heaviest anchor a man can tie to his own neck. The ocean has her own currency, spilled from a thousand wrecks. Spill two hands full of her varied coins before me.");
            } else if (chosenPath == 3) {
                this.setStage(u, 301);
                this.sendLinesAndRelocate(player, "&e[Ancient Angler]&f The thrill of the hunt...", "&e[Ancient Angler]&f The Hunt! Har! There's no music sweeter than the snap of a line in the black water. To track the monsters, ye must first feed on the frenzied biters. Bring me a handful of razors.");
            } else if (chosenPath == 4) {
                this.setStage(u, 401);
                this.sendLinesAndRelocate(player, "&e[Ancient Angler]&f The ocean is vast and full of noise.", "&e[Ancient Angler]&f The sea is vast, kid, and full of pointless noise. To hear the deep's true secrets, ye must first drown out the chatter. Bring me a bounty of the common rabble. A fisherman's dozen, plus two.");
            } else if (chosenPath == 5) {
                this.sendLinesAndPunish(player, "&e[Ancient Angler]&f Oh, is that how it is? Ye think the cruel sea grants favors to guppies like you?", "&e[Ancient Angler]&f Look at yer hands, kid. The ocean takes what she wants, and so do I!");
            } else if (chosenPath == 6) {
                this.setStage(u, 601);
                this.sendLinesAndRelocate(player, "&e[Ancient Angler]&f Fortune favors the bold, they say...", "&e[Ancient Angler]&f The sea hoards its riches beneath the coral thrones. Bring me a tax from the common waters.");
            } else if (chosenPath == 7) {
                this.setStage(u, 701);
                this.sendLinesAndRelocate(player, "&e[Ancient Angler]&f The depths call to those who dare listen...", "&e[Ancient Angler]&f The deep feeds on darkness. Bring me creatures born where light fears to reach.");
            } else if (chosenPath == 8) {
                this.setStage(u, 801);
                this.sendLinesAndRelocate(player, "&e[Ancient Angler]&f Desperation... I can smell it on you.", "&e[Ancient Angler]&f First, prove your desperation. Sacrifice the common to show your hunger.");
            } else if (chosenPath == 9) {
                this.setStage(u, 901);
                this.sendLinesAndRelocate(player, "&e[Ancient Angler]&f The storm respects no mortal...", "&e[Ancient Angler]&f Lightning dances on the water's skin. Bring me the fish that rides the current.");
            } else if (chosenPath == 10) {
                this.sendLinesAndPunishArrogance(player);
            }
            return;
        }
        if (msg.equals("2")) {
            TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f The deep speaks in riddles, kid. Only those who listen survive.");
            return;
        }
        if (msg.equals("3")) {
            if (this.payHintCost(player)) {
                if (state.equals("turnin_101")) {
                    TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f Fools always need a map. Bring me 10 Neon Tetras and 10 Angelfish.");
                } else if (state.equals("turnin_102")) {
                    TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f A Char. Bring me a Char.");
                } else if (state.equals("turnin_103")) {
                    TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f A Cherry Salmon. Go.");
                } else if (state.equals("turnin_104")) {
                    TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f Bring me a Lava Particle.");
                } else if (state.equals("turnin_201")) {
                    TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f Common greed requires common effort. Bring me 20 Custom Fish.");
                } else if (state.equals("turnin_202")) {
                    TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f A Golden Carp. Bring it here.");
                } else if (state.equals("turnin_203")) {
                    TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f A Lava Particle.");
                } else if (state.equals("turnin_301")) {
                    TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f 5 Piranhas. Do not lose your fingers.");
                } else if (state.equals("turnin_302")) {
                    TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f 3 Moray Eels.");
                } else if (state.equals("turnin_303")) {
                    TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f The Spiderfish Queen.");
                } else if (state.equals("turnin_401")) {
                    TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f 15 Custom Fish. Just sit and cast.");
                } else if (state.equals("turnin_402")) {
                    TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f An Anchovy. Nothing more.");
                } else if (state.equals("turnin_403")) {
                    TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f A Suckerfish.");
                } else if (state.equals("turnin_601")) {
                    TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f 20 Custom Fish. A tax from the common waters.");
                } else if (state.equals("turnin_602")) {
                    TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f A Golden Carp.");
                } else if (state.equals("turnin_603")) {
                    TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f Two Ranchu Goldfish.");
                } else if (state.equals("turnin_701")) {
                    TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f 5 Custom Fish from the deep.");
                } else if (state.equals("turnin_702")) {
                    TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f Two Demonic Netherfish.");
                } else if (state.equals("turnin_703")) {
                    TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f Two Netherwrasse.");
                } else if (state.equals("turnin_801")) {
                    TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f 10 Custom Fish.");
                } else if (state.equals("turnin_802")) {
                    TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f A Tundra Trout.");
                } else if (state.equals("turnin_803")) {
                    TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f A Legendary fish. Any of them.");
                } else if (state.equals("turnin_901")) {
                    TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f 5 Eels.");
                } else if (state.equals("turnin_902")) {
                    TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f 3 Tundra Trout.");
                } else if (state.equals("turnin_903")) {
                    TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f A Giant Snakehead.");
                }
            }
            return;
        }
        if (msg.equals("1")) {
            this.chatState.remove(u);
            if (state.equals("turnin_101")) {
                this.checkTask(player, state, "Neon Tetra", 10, "Angelfish", 10, 102, "Do not lie to the deep. Your hands are empty. Bring me a painted school.", "Aye... but the painted ones never survive the drop. Down there, the sun dies and the water bites like iron. Show me ye know what it takes to survive the freeze.");
            } else if (state.equals("turnin_102")) {
                this.checkTask(player, state, "Char", 1, null, 0, 103, "Do not lie to the deep. Your hands are empty. Bring me a survivor of the freezing depths.", "Ice in the veins. Good. But surviving ain't enough; ye must conquer the pull. Bring me the red swimmer of the streams, the one that fights the inevitable.");
            } else if (state.equals("turnin_103")) {
                this.checkTask(player, state, "Cherry Salmon", 1, null, 0, 104, "Do not lie to the deep. Your hands are empty. Bring me the red swimmer of the streams.", "A true scrapper. But true endurance is forged at the very bottom, where the ocean boils against the earth's crust. Reach into the inferno. Hand me the ember.");
            } else if (state.equals("turnin_104")) {
                String tierName;
                if (!this.takeFish(player, "Lava Particle", 1)) {
                    this.punishAndRepeat(player, state, "Do not lie to the deep. Your hands are empty. Pull a piece of the inferno itself.");
                    return;
                }
                ItemStack rod = player.getInventory().getItemInMainHand();
                FishingEnchantManager.RodTier tier = this.module.getEnchantManager().getRodTier(rod);
                int currentLevel = this.module.getEnchantManager().getEnchantLevel(rod, FishingEnchantManager.CustomEnchant.TIRELESS_CASTER);
                FishingEnchantManager.RodTier requiredTier = currentLevel == 0 ? FishingEnchantManager.RodTier.BRONZE : FishingEnchantManager.RodTier.SILVER;
                String string = tierName = currentLevel == 0 ? "Bronze" : "Silver";
                if (tier.ordinal() < requiredTier.ordinal()) {
                    this.sendLines(player, false, "&e[Ancient Angler]&f Your rod is too weak to handle this mastery! Hold at least a " + tierName + " Tier rod and talk to me again.");
                    ItemStack fishItem = this.module.createFishItem(this.module.getConfig().getString("species.lava_particle.name", "Lava Particle"), "MAGMA_CREAM", 1.0, 50.0, "");
                    HashMap leftover = player.getInventory().addItem(new ItemStack[]{fishItem});
                    for (ItemStack left : (java.util.Collection<org.bukkit.inventory.ItemStack>)(java.util.Collection<?>)(java.util.Collection<org.bukkit.inventory.ItemStack>)(java.util.Collection<?>)leftover.values()) {
                        player.getWorld().dropItem(player.getLocation(), left);
                    }
                    return;
                }
                if (currentLevel >= 3) {
                    this.sendLines(player, false, "&e[Ancient Angler]&f You have already mastered this enchantment. You need nothing more from me.");
                    ItemStack fishItem = this.module.createFishItem(this.module.getConfig().getString("species.lava_particle.name", "Lava Particle"), "MAGMA_CREAM", 1.0, 50.0, "");
                    HashMap leftover = player.getInventory().addItem(new ItemStack[]{fishItem});
                    for (ItemStack left : (java.util.Collection<org.bukkit.inventory.ItemStack>)(java.util.Collection<?>)(java.util.Collection<org.bukkit.inventory.ItemStack>)(java.util.Collection<?>)leftover.values()) {
                        player.getWorld().dropItem(player.getLocation(), left);
                    }
                    return;
                }
                int timesGotTC = this.getTimesGotTC(u);
                double baseChance = this.module.getConfig().getDouble("legendary-quest.tireless-caster-base-chance", 1.0);
                double decay = this.module.getConfig().getDouble("legendary-quest.tireless-caster-decay", 0.5);
                double chance = baseChance * Math.pow(decay, timesGotTC);
                if (ThreadLocalRandom.current().nextDouble() < chance) {
                    this.module.getEnchantManager().setEnchantLevel(rod, FishingEnchantManager.CustomEnchant.TIRELESS_CASTER, currentLevel + 1);
                    this.incrementTimesGotTC(u);
                    this.sendLines(player, true, "&e[Ancient Angler]&f You pulled the heat from the earth itself. I grant you the mastery of the Tireless Caster.");
                } else {
                    this.giveConsolationReward(player);
                }
            } else if (state.equals("turnin_201")) {
                if (this.countAnyCustomFish(player) < 20) {
                    this.punishAndRepeat(player, state, "Do not mock me. Your pockets are light. Bring me two hands full of the ocean's varied coins.");
                    return;
                }
                this.takeAnyCustomFish(player, 20);
                this.setStage(u, 202);
                this.sendLinesAndRelocate(player, "&e[Ancient Angler]&f A beggar's purse. If ye want the true hoard, ye must understand a king's folly. Fetch me the catch that shines so bright it blinds greedy fools to the kraken behind it.");
            } else if (state.equals("turnin_202")) {
                this.checkTask(player, state, "Golden Carp", 1, null, 0, 203, "Do not mock me. Your pockets are light. Bring me the king's folly.", "Pure, untarnished vanity. But gold ain't born in the water; it's born in the crushing heat of the deep earth. Bring me the forge's beating heart.");
            } else if (state.equals("turnin_203")) {
                if (!this.takeFish(player, "Lava Particle", 1)) {
                    this.punishAndRepeat(player, state, "Do not mock me. Your pockets are light. Bring me the forge's ember.");
                    return;
                }
                this.plugin.getEconomyManager().deposit((OfflinePlayer)player, 500000.0);
                this.sendLines(player, true, "&e[Ancient Angler]&f Take this Leviathan's bounty. Spend it fast, before the salt claims it back.");
            } else if (state.equals("turnin_301")) {
                this.checkTask(player, state, "Piranha", 5, null, 0, 302, "You smell of fear, not blood. Bring me the frenzied biters.", "Vicious little demons... but they're just bait for the serpents. To learn the ambush, ye must study the beasts that hide in the rocks. Haul me three of 'em.");
            } else if (state.equals("turnin_302")) {
                this.checkTask(player, state, "Moray Eel", 3, null, 0, 303, "You smell of fear, not blood. Bring me the serpents.", "Jaws like rusty bear traps, aye. Now, ye are ready for the apex of the dark. The one that weaves the traps. Bring me the Queen.");
            } else if (state.equals("turnin_303")) {
                if (!this.takeFish(player, "Spiderfish Queen", 1)) {
                    this.punishAndRepeat(player, state, "You smell of fear, not blood. Bring me the web-spinner of the deep.");
                    return;
                }
                String[] availableBaits = new String[]{"worm", "golden_lure", "tide_essence", "mythic_chum"};
                String chosenBait = availableBaits[ThreadLocalRandom.current().nextInt(availableBaits.length)];
                ItemStack baitReward = this.module.getBaitManager().createBaitItem(chosenBait);
                if (baitReward != null) {
                    baitReward.setAmount(3);
                    HashMap leftover = player.getInventory().addItem(new ItemStack[]{baitReward});
                    for (ItemStack left : (java.util.Collection<org.bukkit.inventory.ItemStack>)(java.util.Collection<?>)(java.util.Collection<org.bukkit.inventory.ItemStack>)(java.util.Collection<?>)leftover.values()) {
                        player.getWorld().dropItem(player.getLocation(), left);
                    }
                }
                this.sendLines(player, true, "&e[Ancient Angler]&f Ye are a true hunter of the deep. Take this... don't waste it on minnows. Go hunt the legends.");
            } else if (state.equals("turnin_401")) {
                if (this.countAnyCustomFish(player) < 15) {
                    this.punishAndRepeat(player, state, "You are too loud. You rushed. Bring me the bounty of the common chatter.");
                    return;
                }
                this.takeAnyCustomFish(player, 15);
                this.setStage(u, 402);
                this.sendLinesAndRelocate(player, "&e[Ancient Angler]&f Ah... feel how the water quiets down? Now, true silence means focusing on a single, insignificant detail among thousands. Find me a silver speck of a massive school.");
            } else if (state.equals("turnin_402")) {
                this.checkTask(player, state, "Anchovy", 1, "Giant Snakehead", 5, 403, "You are too loud. You rushed. Bring me the silver speck and the serpent kings.", "Bring me the striped sentinels that patrol the deep reef walls \u2014 five of the barred wardens who watch in silence.");
            } else if (state.equals("turnin_403")) {
                if (!this.takeFish(player, "Barred Knifejaw", 5)) {
                    this.punishAndRepeat(player, state, "You are too loud. You rushed. Five barred wardens, I said.");
                    return;
                }
                this.module.addAnglerLevels(player, 5);
                if (!this.hasFossilizedAnchor(u)) {
                    ItemStack anchor = new ItemStack(Material.NAUTILUS_SHELL);
                    ItemMeta anchorMeta = anchor.getItemMeta();
                    if (anchorMeta != null) {
                        anchorMeta.setDisplayName(TextUtil.legacyColorize("&6&lFossilized Anchor"));
                        anchorMeta.addEnchant(Enchantment.LURE, 1, true);
                        anchorMeta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ENCHANTS});
                        anchorMeta.setLore(Arrays.asList(TextUtil.legacyColorize("&7An ancient, heavy relic..."), TextUtil.legacyColorize("&7Proof of your ultimate patience.")));
                        anchorMeta.getPersistentDataContainer().set(new NamespacedKey((Plugin)this.plugin, "trophy_item"), PersistentDataType.STRING, "fossilized_anchor");
                        anchor.setItemMeta(anchorMeta);
                        HashMap leftover = player.getInventory().addItem(new ItemStack[]{anchor});
                        for (ItemStack left : (java.util.Collection<org.bukkit.inventory.ItemStack>)(java.util.Collection<?>)(java.util.Collection<org.bukkit.inventory.ItemStack>)(java.util.Collection<?>)leftover.values()) {
                            player.getWorld().dropItem(player.getLocation(), left);
                        }
                    }
                    this.setHasFossilizedAnchor(u);
                } else {
                    TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f You already possess the relic. Your levels grow, but the anchor calls to no one twice.");
                }
                this.sendLines(player, true, "&e[Ancient Angler]&f Ye truly understand the quiet of the waters. Keep this ancient relic; let it anchor yer patience.");
            } else if (state.equals("turnin_601")) {
                if (this.countAnyCustomFish(player) < 20) {
                    this.punishAndRepeat(player, state, "Your offerings are thin. Bring me a proper tax from the sea.");
                    return;
                }
                this.takeAnyCustomFish(player, 20);
                this.setStage(u, 602);
                this.sendLinesAndRelocate(player, "&e[Ancient Angler]&f A beggar's purse. If ye want the true fortune, bring me the golden scale.");
            } else if (state.equals("turnin_602")) {
                this.checkTask(player, state, "Golden Carp", 1, null, 0, 603, "Your offerings are thin. Bring me the golden scale.", "Now bring me the armored jewels of the frozen waters. Two of them.");
            } else if (state.equals("turnin_603")) {
                if (!this.takeFish(player, "Ranchu Goldfish", 2)) {
                    this.punishAndRepeat(player, state, "Your offerings are thin. Two armored jewels, I said.");
                    return;
                }
                this.plugin.getEconomyManager().deposit((OfflinePlayer)player, 250000.0);
                this.giveRandomEpicFish(player);
                this.sendLines(player, true, "&e[Ancient Angler]&f Fortune has smiled upon you today. Take these riches and remember: the sea giveth and the sea taketh.");
            } else if (state.equals("turnin_701")) {
                if (this.countAnyCustomFish(player) < 5) {
                    this.punishAndRepeat(player, state, "The darkness rejects your meager offering. More creatures of the deep.");
                    return;
                }
                this.takeAnyCustomFish(player, 5);
                this.setStage(u, 702);
                this.sendLinesAndRelocate(player, "&e[Ancient Angler]&f Fetch me the demons that swim through volcanic veins. Two of them.");
            } else if (state.equals("turnin_702")) {
                if (!this.takeFish(player, "Demonic Netherfish", 2)) {
                    this.punishAndRepeat(player, state, "The darkness rejects your offering. Two demons, I said.");
                    return;
                }
                this.setStage(u, 703);
                this.sendLinesAndRelocate(player, "&e[Ancient Angler]&f The ultimate darkness... bring me the wrasse forged in the nether's forge. Two of them.");
            } else if (state.equals("turnin_703")) {
                if (!this.takeFish(player, "Netherwrasse", 2)) {
                    this.punishAndRepeat(player, state, "The darkness rejects your offering. Two nether wrasse, I said.");
                    return;
                }
                ItemStack baitReward = this.module.getBaitManager().createBaitItem("mythic_chum");
                if (baitReward != null) {
                    baitReward.setAmount(5);
                    HashMap leftover = player.getInventory().addItem(new ItemStack[]{baitReward});
                    for (ItemStack left : (java.util.Collection<org.bukkit.inventory.ItemStack>)(java.util.Collection<?>)(java.util.Collection<org.bukkit.inventory.ItemStack>)(java.util.Collection<?>)leftover.values()) {
                        player.getWorld().dropItem(player.getLocation(), left);
                    }
                }
                this.sendLines(player, true, "&e[Ancient Angler]&f The depths have acknowledged your courage. Take this mythical chum... it calls to the legends.");
            } else if (state.equals("turnin_801")) {
                if (this.countAnyCustomFish(player) < 10) {
                    this.punishAndRepeat(player, state, "More! Show me your desperation!");
                    return;
                }
                this.takeAnyCustomFish(player, 10);
                this.setStage(u, 802);
                this.sendLinesAndRelocate(player, "&e[Ancient Angler]&f Good... now bring me something precious. A rare creature from the cold.");
            } else if (state.equals("turnin_802")) {
                this.checkTask(player, state, "Tundra Trout", 1, null, 0, 803, "More! Show me your desperation!", "Yes... YES! One final offering... bring me your finest catch!");
            } else if (state.equals("turnin_803")) {
                ItemStack[] bag;
                boolean hasLegendary = false;
                String legendaryName = null;
                for (ItemStack item : player.getInventory().getContents()) {
                    String sp;
                    if (item == null || !item.hasItemMeta() || !item.getItemMeta().getPersistentDataContainer().has(this.module.getSpeciesKey(), PersistentDataType.STRING) || !this.isLegendary(sp = (String)item.getItemMeta().getPersistentDataContainer().get(this.module.getSpeciesKey(), PersistentDataType.STRING))) continue;
                    hasLegendary = true;
                    legendaryName = sp;
                    break;
                }
                if (!hasLegendary && (bag = this.module.getFishBagManager().getBag(player.getUniqueId())) != null) {
                    for (ItemStack item : bag) {
                        String sp;
                        if (item == null || !item.hasItemMeta() || !item.getItemMeta().getPersistentDataContainer().has(this.module.getSpeciesKey(), PersistentDataType.STRING) || !this.isLegendary(sp = (String)item.getItemMeta().getPersistentDataContainer().get(this.module.getSpeciesKey(), PersistentDataType.STRING))) continue;
                        hasLegendary = true;
                        legendaryName = sp;
                        break;
                    }
                }
                if (!hasLegendary) {
                    this.punishAndRepeat(player, state, "That is not your FINEST! Bring me a legendary catch or face my wrath!");
                    return;
                }
                this.takeFish(player, legendaryName, 1);
                this.sendLinesAndPunishDesperation(player);
            } else if (state.equals("turnin_901")) {
                this.checkTask(player, state, "Eel", 5, null, 0, 902, "The current rejects you. Bring me the riders of the current.", "The storm's child hides in frozen rivers. Bring me the trout touched by winter.");
            } else if (state.equals("turnin_902")) {
                if (!this.takeFish(player, "Tundra Trout", 3)) {
                    this.punishAndRepeat(player, state, "The current rejects you. Three trout of the tundra.");
                    return;
                }
                this.setStage(u, 903);
                this.sendLinesAndRelocate(player, "&e[Ancient Angler]&f Finally... the beast that commands the jungle rivers with fury.");
            } else if (state.equals("turnin_903")) {
                if (!this.takeFish(player, "Giant Snakehead", 1)) {
                    this.punishAndRepeat(player, state, "The current rejects you. Bring me the serpent king.");
                    return;
                }
                if (!this.hasStormcallerTrident(u)) {
                    this.giveStormcallerTrident(player);
                    this.setHasStormcallerTrident(u);
                } else {
                    TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f You already wield the storm's legacy. Your courage is noted, but the trident calls to no one twice.");
                }
                this.sendLines(player, true, "&e[Ancient Angler]&f You have braved the tempest and survived. The storm now recognizes you as its master.");
            }
        } else {
            TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f I do not understand those words. Type 1, 2, or 3.");
        }
    }

    private void startIntroDialogue(final Player player) {
        final UUID u = player.getUniqueId();
        this.inDialogueSequence.add(u);
        this.setChatState(u, "awaiting_intro");
        // Stable for this quest attempt, even after cancel, timeout or reconnect.
        var offer = AnglerIntroOffer.create(u, getTimesCompleted(u), getTimesDisrespected(u), SCENARIO_PROMPTS.length);
        final List<Integer> chosenPaths = offer.paths();
        this.introOptions.put(u, chosenPaths);
        final int si = offer.scenario();
        new BukkitRunnable(){
            int tick = 0;

            public void run() {
                if (!player.isOnline() || getStage(u) != 10 || introOptions.get(u) != chosenPaths) {
                    this.cancel();
                    return;
                }

                if (this.tick == 0) {
                    TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f " + SCENARIO_PROMPTS[si]);
                } else if (this.tick == 1) {
                    TextUtil.sendMessage((CommandSender)player, "");
                    for (int i = 0; i < 3; ++i) {
                        String text = SCENARIO_TEXTS[si][(Integer)chosenPaths.get(i) - 1];
                        LegendaryQuestManager.this.sendClickableOption(player, "&b&l[" + (i + 1) + "] &f" + text, "&7Click to choose this path", String.valueOf(i + 1));
                    }
                    TextUtil.sendMessage((CommandSender)player, "");
                    TextUtil.sendMessage((CommandSender)player, "&8&oClick an option above or type 1, 2, or 3 in chat.");
                    TextUtil.sendMessage((CommandSender)player, "&8&oType 'cancel' to walk away. Expires in 2 minutes.");
                    LegendaryQuestManager.this.inDialogueSequence.remove(u);
                    this.cancel();
                }
                ++this.tick;
            }
        }.runTaskTimer((Plugin)this.plugin, 10L, 80L);
    }

    private void punishAndRepeat(Player player, String state, String failMessage) {
        UUID u = player.getUniqueId();
        int fails = this.failCounters.getOrDefault(u, 0) + 1;
        this.failCounters.put(u, fails);
        this.saveFailCount(u, fails);
        double basePenalty = this.module.getConfig().getDouble("legendary-quest.fail-penalty-base", 10000.0);
        double multiplier = this.module.getConfig().getDouble("legendary-quest.fail-penalty-multiplier", 2.0);
        double cap = this.module.getConfig().getDouble("legendary-quest.fail-penalty-cap", 500000.0);
        double penalty = Math.min(basePenalty * Math.pow(multiplier, fails - 1), cap);
        int blindnessDuration = Math.min(5 + fails * 3, 30) * 20;
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindnessDuration, 1));
        if (fails >= 3) {
            int slowDuration = Math.min(10 + fails * 5, 25) * 20;
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowDuration, 1));
        }
        if (fails >= 5) {
            int nauseaDuration = Math.min(10 + (fails - 4) * 5, 15) * 20;
            player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, nauseaDuration, 1));
        }
        this.plugin.getEconomyManager().withdraw((OfflinePlayer)player, penalty);
        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
        TextUtil.sendMessage((CommandSender)player, "&c[System] You lied to the Angler. " + this.plugin.getEconomyManager().format(penalty) + " deducted. (Fail #" + fails + ")");
        TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f " + failMessage);
        this.setChatState(player.getUniqueId(), state);
    }

    private boolean payHintCost(Player player) {
        String rarity;
        String sp;
        double cost = this.module.getConfig().getDouble("legendary-quest.sacrifice-cost", 25000.0);
        boolean allowDebt = this.module.getConfig().getBoolean("legendary-quest.fail-penalty-allow-debt", true);
        if (!allowDebt && !this.plugin.getEconomyManager().has((OfflinePlayer)player, cost)) {
            TextUtil.sendMessage((CommandSender)player, "&c[System] You need " + this.plugin.getEconomyManager().format(cost) + " for a hint.");
            return false;
        }
        boolean found = false;
        String deductedFishName = "Unknown";
        int legWeight = this.module.getConfig().getInt("legendary-quest.sacrifice-legendary-weight", 50);
        int epicWeight = this.module.getConfig().getInt("legendary-quest.sacrifice-epic-weight", 30);
        ArrayList<int[]> legendarySlots = new ArrayList<int[]>();
        ArrayList<int[]> epicSlots = new ArrayList<int[]>();
        ArrayList<int[]> rareSlots = new ArrayList<int[]>();
        for (int i = 0; i < player.getInventory().getSize(); ++i) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || !item.hasItemMeta() || !item.getItemMeta().getPersistentDataContainer().has(this.module.getSpeciesKey(), PersistentDataType.STRING)) continue;
            sp = (String)item.getItemMeta().getPersistentDataContainer().get(this.module.getSpeciesKey(), PersistentDataType.STRING);
            rarity = this.getFishRarity(sp);
            if ("legendary".equals(rarity)) {
                legendarySlots.add(new int[]{i, 0});
                continue;
            }
            if ("epic".equals(rarity)) {
                epicSlots.add(new int[]{i, 0});
                continue;
            }
            if (!"rare".equals(rarity)) continue;
            rareSlots.add(new int[]{i, 0});
        }
        ItemStack[] bag = this.module.getFishBagManager().getBag(player.getUniqueId());
        if (bag != null) {
            for (int i = 0; i < bag.length; ++i) {
                if (bag[i] == null || !bag[i].hasItemMeta() || !bag[i].getItemMeta().getPersistentDataContainer().has(this.module.getSpeciesKey(), PersistentDataType.STRING)) continue;
                sp = (String)bag[i].getItemMeta().getPersistentDataContainer().get(this.module.getSpeciesKey(), PersistentDataType.STRING);
                rarity = this.getFishRarity(sp);
                if ("legendary".equals(rarity)) {
                    legendarySlots.add(new int[]{i, 1});
                    continue;
                }
                if ("epic".equals(rarity)) {
                    epicSlots.add(new int[]{i, 1});
                    continue;
                }
                if (!"rare".equals(rarity)) continue;
                rareSlots.add(new int[]{i, 1});
            }
        }
        ArrayList<int[]> targetPool = null;
        double roll = ThreadLocalRandom.current().nextDouble() * 100.0;
        if (roll < (double)legWeight && !legendarySlots.isEmpty()) {
            targetPool = legendarySlots;
        } else if (roll < (double)(legWeight + epicWeight) && !epicSlots.isEmpty()) {
            targetPool = epicSlots;
        } else if (!rareSlots.isEmpty()) {
            targetPool = rareSlots;
        } else if (!legendarySlots.isEmpty()) {
            targetPool = legendarySlots;
        } else if (!epicSlots.isEmpty()) {
            targetPool = epicSlots;
        }
        if (targetPool != null && !targetPool.isEmpty()) {
            int[] target = (int[])targetPool.get(ThreadLocalRandom.current().nextInt(targetPool.size()));
            if (target[1] == 0) {
                ItemStack item = player.getInventory().getItem(target[0]);
                deductedFishName = (String)item.getItemMeta().getPersistentDataContainer().get(this.module.getSpeciesKey(), PersistentDataType.STRING);
                item.setAmount(item.getAmount() - 1);
                if (item.getAmount() <= 0) {
                    player.getInventory().setItem(target[0], null);
                }
            } else {
                deductedFishName = (String)bag[target[0]].getItemMeta().getPersistentDataContainer().get(this.module.getSpeciesKey(), PersistentDataType.STRING);
                bag[target[0]].setAmount(bag[target[0]].getAmount() - 1);
                if (bag[target[0]].getAmount() <= 0) {
                    bag[target[0]] = null;
                }
                this.module.getFishBagManager().updateBag(player.getUniqueId(), bag);
                this.module.getFishBagManager().saveBag(player.getUniqueId());
            }
            found = true;
        }
        if (found) {
            this.plugin.getEconomyManager().withdraw((OfflinePlayer)player, cost);
            TextUtil.sendMessage((CommandSender)player, "&a[System] Paid " + this.plugin.getEconomyManager().format(cost) + " and 1x " + deductedFishName + " for a hint.");
            return true;
        }
        double seizure = this.module.getConfig().getDouble("legendary-quest.sacrifice-no-fish-seizure", 50000.0);
        int rouletteRoll = ThreadLocalRandom.current().nextInt(3);
        if (rouletteRoll == 0) {
            this.plugin.getEconomyManager().withdraw((OfflinePlayer)player, cost);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 3600, 1));
            TextUtil.sendMessage((CommandSender)player, "&c[System] Lacked fish tribute! Paid " + this.plugin.getEconomyManager().format(cost) + " and cursed with Slowness (3m) for the hint.");
            player.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 1.0f);
            return true;
        }
        if (rouletteRoll == 1) {
            this.plugin.getEconomyManager().withdraw((OfflinePlayer)player, cost);
            player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 3600, 1));
            TextUtil.sendMessage((CommandSender)player, "&c[System] Lacked fish tribute! Paid " + this.plugin.getEconomyManager().format(cost) + " and cursed with Fatigue (3m) for the hint.");
            player.playSound(player.getLocation(), Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.0f, 1.0f);
            return true;
        }
        this.plugin.getEconomyManager().withdraw((OfflinePlayer)player, seizure);
        TextUtil.sendMessage((CommandSender)player, "&c[System] Lacked fish tribute! The Angler seized " + this.plugin.getEconomyManager().format(seizure) + " instead for the hint.");
        player.playSound(player.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
        return true;
    }

    private String getFishRarity(String speciesName) {
        ConfigurationSection speciesSection = this.module.getConfig().getConfigurationSection("species");
        if (speciesSection != null) {
            for (String key : speciesSection.getKeys(false)) {
                String name = speciesSection.getString(key + ".name", "");
                name = org.bukkit.ChatColor.stripColor((String)LegacyComponentSerializer.legacyAmpersand().deserialize(name).content());
                if (!speciesName.equalsIgnoreCase(name)) continue;
                return speciesSection.getString(key + ".rarity", "Common").toLowerCase();
            }
        }
        return "common";
    }

    private void giveConsolationReward(Player player) {
        int roll = ThreadLocalRandom.current().nextInt(3);
        if (roll == 0) {
            this.plugin.getEconomyManager().deposit((OfflinePlayer)player, 100000.0);
            this.sendLines(player, true, "&e[Ancient Angler]&f The Angler nods slowly. 'You've proven your endurance. Take this gold.'");
        } else if (roll == 1) {
            String[] baits = new String[]{"tide_essence", "mythic_chum", "golden_lure"};
            String chosen = baits[ThreadLocalRandom.current().nextInt(baits.length)];
            ItemStack bait = this.module.getBaitManager().createBaitItem(chosen);
            if (bait != null) {
                bait.setAmount(2);
                HashMap leftover = player.getInventory().addItem(new ItemStack[]{bait});
                for (ItemStack left : (java.util.Collection<org.bukkit.inventory.ItemStack>)(java.util.Collection<?>)(java.util.Collection<org.bukkit.inventory.ItemStack>)(java.util.Collection<?>)leftover.values()) {
                    player.getWorld().dropItem(player.getLocation(), left);
                }
            }
            this.plugin.getEconomyManager().deposit((OfflinePlayer)player, 50000.0);
            this.sendLines(player, true, "&e[Ancient Angler]&f The Angler reaches into the deep and pulls out offerings. 'Supplies for the weary.'");
        } else {
            this.module.addAnglerLevels(player, 2);
            this.plugin.getEconomyManager().deposit((OfflinePlayer)player, 50000.0);
            this.sendLines(player, true, "&e[Ancient Angler]&f The Angler places a weathered hand on your rod. 'Your skill sharpens.'");
        }
    }

    private void giveRandomEpicFish(Player player) {
        ConfigurationSection speciesSection = this.module.getConfig().getConfigurationSection("species");
        if (speciesSection == null) {
            return;
        }
        ArrayList<String> epicSpecies = new ArrayList<String>();
        for (String key : speciesSection.getKeys(false)) {
            if (!speciesSection.getString(key + ".rarity", "").equalsIgnoreCase("Epic")) continue;
            epicSpecies.add(key);
        }
        if (epicSpecies.isEmpty()) {
            return;
        }
        String chosen = (String)epicSpecies.get(ThreadLocalRandom.current().nextInt(epicSpecies.size()));
        String name = speciesSection.getString(chosen + ".name", "Epic Fish");
        String material = speciesSection.getString(chosen + ".material", "COD");
        double minW = speciesSection.getDouble(chosen + ".min-weight", 1.0);
        double maxW = speciesSection.getDouble(chosen + ".max-weight", 10.0);
        double ppk = speciesSection.getDouble(chosen + ".price-per-kg", 100.0);
        double weight = minW + ThreadLocalRandom.current().nextDouble() * (maxW - minW);
        String itemModel = speciesSection.getString(chosen + ".item-model", "");
        ItemStack fish = this.module.createFishItem(name, material, weight, ppk, itemModel);
        HashMap leftover = player.getInventory().addItem(new ItemStack[]{fish});
        for (ItemStack left : (java.util.Collection<org.bukkit.inventory.ItemStack>)(java.util.Collection<?>)(java.util.Collection<org.bukkit.inventory.ItemStack>)(java.util.Collection<?>)leftover.values()) {
            player.getWorld().dropItem(player.getLocation(), left);
        }
    }

    private void giveStormcallerTrident(Player player) {
        ItemStack trident = new ItemStack(Material.TRIDENT);
        ItemMeta meta = trident.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(TextUtil.legacyColorize("&e&l⚡ Stormcaller's Trident"));
            meta.addEnchant(Enchantment.LOYALTY, 3, true);
            meta.addEnchant(Enchantment.CHANNELING, 1, true);
            meta.addEnchant(Enchantment.IMPALING, 5, true);
            meta.setUnbreakable(true);
            meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
            meta.setLore(Arrays.asList(
                TextUtil.legacyColorize("&7A legendary relic forged by the tempest..."),
                TextUtil.legacyColorize("&7Proof that you faced the storm and conquered it."),
                TextUtil.legacyColorize("&8&m────────────────────────"),
                TextUtil.legacyColorize("&6✦ Stormcaller's Wrath"),
                TextUtil.legacyColorize("&7Binds lightning, tides, and eternal loyalty."),
                TextUtil.legacyColorize("&8[Ancient Quest Relic]")
            ));
            meta.getPersistentDataContainer().set(new NamespacedKey((Plugin)this.plugin, "trophy_item"), PersistentDataType.STRING, "stormcaller_trident");
            trident.setItemMeta(meta);
            HashMap leftover = player.getInventory().addItem(new ItemStack[]{trident});
            for (ItemStack left : (java.util.Collection<org.bukkit.inventory.ItemStack>)(java.util.Collection<?>)(java.util.Collection<org.bukkit.inventory.ItemStack>)(java.util.Collection<?>)leftover.values()) {
                player.getWorld().dropItem(player.getLocation(), left);
            }
        }
    }

    private boolean isLegendary(String speciesName) {
        ConfigurationSection speciesSection = this.module.getConfig().getConfigurationSection("species");
        if (speciesSection != null) {
            for (String key : speciesSection.getKeys(false)) {
                String name = speciesSection.getString(key + ".name", "");
                name = org.bukkit.ChatColor.stripColor((String)LegacyComponentSerializer.legacyAmpersand().deserialize(name).content());
                if (!speciesName.equalsIgnoreCase(name)) continue;
                return speciesSection.getString(key + ".rarity", "Common").equalsIgnoreCase("Legendary");
            }
        }
        return false;
    }

    private void sendLinesAndPunishArrogance(final Player player) {
        final UUID u = player.getUniqueId();
        this.incrementTimesDisrespected(u);
        this.inDialogueSequence.add(u);
        new BukkitRunnable(){
            int tick = 0;

            public void run() {
                if (this.tick == 0) {
                    TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f COMMAND the tide? You dare speak of command before the Ancient Angler?!");
                } else if (this.tick == 1) {
                    TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f The sea does not bow to mortals.");
                } else {
                    LegendaryQuestManager.this.inDialogueSequence.remove(u);
                    player.getWorld().strikeLightningEffect(player.getLocation());
                    player.getWorld().strikeLightningEffect(player.getLocation().add(1.0, 0.0, 1.0));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 600, 1));
                    player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.3f, 1.0f);
                    String bestFish = LegendaryQuestManager.this.findBestFish(player);
                    if (bestFish != null) {
                        LegendaryQuestManager.this.takeFish(player, bestFish, 1);
                        TextUtil.sendMessage((CommandSender)player, "&cThe Angler violently seized your " + bestFish + "!");
                    } else {
                        LegendaryQuestManager.this.plugin.getEconomyManager().withdraw((OfflinePlayer)player, 50000.0);
                        TextUtil.sendMessage((CommandSender)player, "&cThe Angler seized " + LegendaryQuestManager.this.plugin.getEconomyManager().format(50000.0) + " from you!");
                    }
                    LegendaryQuestManager.this.finishQuest(player);
                    this.cancel();
                }
                ++this.tick;
            }
        }.runTaskTimer((Plugin)this.plugin, 10L, 80L);
    }

    private void sendLinesAndPunishDesperation(final Player player) {
        final UUID u = player.getUniqueId();
        this.inDialogueSequence.add(u);
        new BukkitRunnable(){
            int tick = 0;

            public void run() {
                if (this.tick == 0) {
                    TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f FOOL! You handed your treasure to a phantom!");
                    player.playSound(player.getLocation(), Sound.ENTITY_WITHER_AMBIENT, 1.0f, 0.5f);
                } else if (this.tick == 1) {
                    TextUtil.sendMessage((CommandSender)player, "&e[Ancient Angler]&f The depths thank you for your desperation!");
                } else {
                    LegendaryQuestManager.this.inDialogueSequence.remove(u);
                    int stolen = 0;
                    for (int i = 0; i < player.getInventory().getSize() && stolen < 10; ++i) {
                        ItemStack item = player.getInventory().getItem(i);
                        if (item == null || !item.hasItemMeta() || !item.getItemMeta().getPersistentDataContainer().has(LegendaryQuestManager.this.module.getSpeciesKey(), PersistentDataType.STRING)) continue;
                        player.getInventory().setItem(i, null);
                        ++stolen;
                    }
                    LegendaryQuestManager.this.plugin.getEconomyManager().withdraw((OfflinePlayer)player, 50000.0);
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 600, 2));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 2400, 2));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 1200, 1));
                    TextUtil.sendMessage((CommandSender)player, "&cThe Angler stole " + stolen + " fish and " + LegendaryQuestManager.this.plugin.getEconomyManager().format(50000.0) + " from you!");
                    LegendaryQuestManager.this.finishQuest(player);
                    this.cancel();
                }
                ++this.tick;
            }
        }.runTaskTimer((Plugin)this.plugin, 10L, 80L);
    }

    private String findBestFish(Player player) {
        String[] priorities = new String[]{"legendary", "epic", "rare", "uncommon", "common"};
        ConfigurationSection speciesSection = this.module.getConfig().getConfigurationSection("species");
        if (speciesSection == null) {
            return null;
        }
        for (String priority : priorities) {
            for (ItemStack item : player.getInventory().getContents()) {
                if (item == null || !item.hasItemMeta() || !item.getItemMeta().getPersistentDataContainer().has(this.module.getSpeciesKey(), PersistentDataType.STRING)) continue;
                String sp = (String)item.getItemMeta().getPersistentDataContainer().get(this.module.getSpeciesKey(), PersistentDataType.STRING);
                for (String key : speciesSection.getKeys(false)) {
                    String name = speciesSection.getString(key + ".name", "");
                    name = org.bukkit.ChatColor.stripColor((String)LegacyComponentSerializer.legacyAmpersand().deserialize(name).content());
                    if (!sp.equalsIgnoreCase(name) || !speciesSection.getString(key + ".rarity", "").equalsIgnoreCase(priority)) continue;
                    return sp;
                }
            }
        }
        return null;
    }

    private boolean isRareOrEpic(String speciesName) {
        ConfigurationSection speciesSection = this.module.getConfig().getConfigurationSection("species");
        if (speciesSection != null) {
            for (String key : speciesSection.getKeys(false)) {
                String name = speciesSection.getString(key + ".name", "");
                name = org.bukkit.ChatColor.stripColor((String)LegacyComponentSerializer.legacyAmpersand().deserialize(name).content());
                if (!speciesName.equalsIgnoreCase(name)) continue;
                String rarity = speciesSection.getString(key + ".rarity", "Common").toLowerCase();
                return rarity.equals("rare") || rarity.equals("epic") || rarity.equals("legendary");
            }
        }
        return false;
    }

    private void relocateNPC(Player player) {
        this.module.getLegendaryNPCManager().removeNPC(player.getUniqueId());
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation(), 200, 1.0, 1.0, 1.0, 0.5);
        for (int i = 0; i < player.getInventory().getSize(); ++i) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || !item.hasItemMeta() || !item.getItemMeta().getPersistentDataContainer().has(this.trackerKey, PersistentDataType.STRING)) continue;
            ItemMeta meta = item.getItemMeta();
            int newX = player.getLocation().getBlockX() + ThreadLocalRandom.current().nextInt(-2000, 2000);
            int newZ = player.getLocation().getBlockZ() + ThreadLocalRandom.current().nextInt(-2000, 2000);
            Location newLoc = new Location(player.getWorld(), (double)newX, 62.0, (double)newZ);
            meta.getPersistentDataContainer().set(new NamespacedKey((Plugin)this.plugin, "lode_w"), PersistentDataType.STRING, newLoc.getWorld().getName());
            meta.getPersistentDataContainer().set(new NamespacedKey((Plugin)this.plugin, "lode_x"), PersistentDataType.DOUBLE, newLoc.getX());
            meta.getPersistentDataContainer().set(new NamespacedKey((Plugin)this.plugin, "lode_y"), PersistentDataType.DOUBLE, newLoc.getY());
            meta.getPersistentDataContainer().set(new NamespacedKey((Plugin)this.plugin, "lode_z"), PersistentDataType.DOUBLE, newLoc.getZ());
            item.setItemMeta(meta);
            TextUtil.sendMessage((CommandSender)player, "&bThe Angler vanished! Your tracker pearl pulses and locks onto a new location...");
            break;
        }
    }

    private void sendLinesAndRelocate(final Player player, final String ... lines) {
        final UUID u = player.getUniqueId();
        this.inDialogueSequence.add(u);
        new BukkitRunnable(){
            int tick = 0;

            public void run() {
                if (this.tick < lines.length) {
                    TextUtil.sendMessage((CommandSender)player, lines[this.tick]);
                    ++this.tick;
                } else {
                    LegendaryQuestManager.this.inDialogueSequence.remove(u);
                    LegendaryQuestManager.this.relocateNPC(player);
                    this.cancel();
                }
            }
        }.runTaskTimer((Plugin)this.plugin, 10L, 80L);
    }

    private void checkTask(Player player, String state, String fish1, int amount1, String fish2, int amount2, int nextStage, String failMessage, String ... dialogues) {
        if (fish2 == null) {
            if (!this.takeFish(player, fish1, amount1)) {
                this.punishAndRepeat(player, state, failMessage);
                return;
            }
        } else {
            if (this.countFish(player, fish1) < amount1 || this.countFish(player, fish2) < amount2) {
                this.punishAndRepeat(player, state, failMessage);
                return;
            }
            this.takeFishAmount(player, fish1, amount1);
            this.takeFishAmount(player, fish2, amount2);
        }
        this.setStage(player.getUniqueId(), nextStage);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f);
        ArrayList<String> lines = new ArrayList<String>();
        for (String d : dialogues) {
            lines.add("&e[Ancient Angler]&f " + d);
        }
        this.sendLinesAndRelocate(player, lines.toArray(new String[0]));
    }

    private void sendLinesAndPunish(final Player player, final String ... lines) {
        final UUID u = player.getUniqueId();
        this.incrementTimesDisrespected(u);
        this.inDialogueSequence.add(u);
        new BukkitRunnable(){
            int tick = 0;

            public void run() {
                if (this.tick < lines.length) {
                    TextUtil.sendMessage((CommandSender)player, lines[this.tick]);
                    ++this.tick;
                } else {
                    int i;
                    LegendaryQuestManager.this.inDialogueSequence.remove(u);
                    player.getWorld().strikeLightningEffect(player.getLocation());
                    player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 2400, 4));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 600, 2));
                    int stolen = 0;
                    ItemStack[] bag = LegendaryQuestManager.this.module.getFishBagManager().getBag(u);
                    if (bag != null) {
                        for (i = 0; i < bag.length; ++i) {
                            if (bag[i] == null || stolen >= 5) continue;
                            bag[i].setAmount(bag[i].getAmount() - 1);
                            if (bag[i].getAmount() <= 0) {
                                bag[i] = null;
                            }
                            ++stolen;
                        }
                        if (stolen > 0) {
                            LegendaryQuestManager.this.module.getFishBagManager().updateBag(u, bag);
                            LegendaryQuestManager.this.module.getFishBagManager().saveBag(u);
                        }
                    }
                    if (stolen < 5) {
                        for (i = 0; i < player.getInventory().getSize(); ++i) {
                            ItemStack item = player.getInventory().getItem(i);
                            if (item == null || !item.hasItemMeta() || !item.getItemMeta().getPersistentDataContainer().has(LegendaryQuestManager.this.module.getSpeciesKey(), PersistentDataType.STRING)) continue;
                            item.setAmount(item.getAmount() - 1);
                            if (item.getAmount() <= 0) {
                                player.getInventory().setItem(i, null);
                            }
                            if (++stolen >= 5) break;
                        }
                    }
                    if (stolen == 0) {
                        LegendaryQuestManager.this.plugin.getEconomyManager().withdraw((OfflinePlayer)player, 25000.0);
                        TextUtil.sendMessage((CommandSender)player, "&cThe Angler stole " + LegendaryQuestManager.this.plugin.getEconomyManager().format(25000.0) + " from you!");
                    } else {
                        TextUtil.sendMessage((CommandSender)player, "&cThe Angler violently stole " + stolen + " fish from you!");
                    }
                    LegendaryQuestManager.this.finishQuest(player);
                    this.cancel();
                }
            }
        }.runTaskTimer((Plugin)this.plugin, 10L, 80L);
    }

    private void sendLines(final Player player, final boolean endQuest, final String ... lines) {
        final UUID u = player.getUniqueId();
        this.inDialogueSequence.add(u);
        new BukkitRunnable(){
            int tick = 0;

            public void run() {
                if (this.tick < lines.length) {
                    TextUtil.sendMessage((CommandSender)player, lines[this.tick]);
                    ++this.tick;
                } else {
                    LegendaryQuestManager.this.inDialogueSequence.remove(u);
                    if (endQuest) {
                        LegendaryQuestManager.this.finishQuest(player);
                    }
                    this.cancel();
                }
            }
        }.runTaskTimer((Plugin)this.plugin, 10L, 80L);
    }

    private void finishQuest(Player player) {
        UUID u = player.getUniqueId();
        this.incrementTimesCompleted(u);
        this.failCounters.remove(u);
        this.saveFailCount(u, 0);
        this.setStage(u, 0);
        this.module.getLegendaryNPCManager().removeNPC(player.getUniqueId());
        for (int i = 0; i < player.getInventory().getSize(); ++i) {
            ItemStack item = player.getInventory().getItem(i);
            if (item == null || !item.hasItemMeta() || !item.getItemMeta().getPersistentDataContainer().has(this.trackerKey, PersistentDataType.STRING) && !item.getItemMeta().getPersistentDataContainer().has(this.compassKey, PersistentDataType.STRING)) continue;
            player.getInventory().setItem(i, null);
        }
        player.updateInventory();
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        player.getWorld().spawnParticle(Particle.CLOUD, player.getLocation(), 100, 1.0, 1.0, 1.0, 0.1);
    }

    private int countAnyCustomFish(Player player) {
        int c = 0;
        for (ItemStack i : player.getInventory().getContents()) {
            if (i == null || !i.hasItemMeta() || !i.getItemMeta().getPersistentDataContainer().has(this.module.getSpeciesKey(), PersistentDataType.STRING)) continue;
            c += i.getAmount();
        }
        ItemStack[] bag = this.module.getFishBagManager().getBag(player.getUniqueId());
        if (bag != null) {
            for (ItemStack i : bag) {
                if (i == null || !i.hasItemMeta() || !i.getItemMeta().getPersistentDataContainer().has(this.module.getSpeciesKey(), PersistentDataType.STRING)) continue;
                c += i.getAmount();
            }
        }
        return c;
    }

    private void takeAnyCustomFish(Player player, int amount) {
        ItemStack[] bag;
        int removed = 0;
        for (int slot = 0; slot < player.getInventory().getSize(); ++slot) {
            ItemStack i = player.getInventory().getItem(slot);
            if (i == null || !i.hasItemMeta() || !i.getItemMeta().getPersistentDataContainer().has(this.module.getSpeciesKey(), PersistentDataType.STRING)) continue;
            while (removed < amount && i.getAmount() > 0) {
                i.setAmount(i.getAmount() - 1);
                ++removed;
            }
            if (i.getAmount() > 0) continue;
            player.getInventory().setItem(slot, null);
        }
        if (removed < amount && (bag = this.module.getFishBagManager().getBag(player.getUniqueId())) != null) {
            boolean bagChanged = false;
            for (int slot = 0; slot < bag.length; ++slot) {
                ItemStack i = bag[slot];
                if (i == null || !i.hasItemMeta() || !i.getItemMeta().getPersistentDataContainer().has(this.module.getSpeciesKey(), PersistentDataType.STRING)) continue;
                while (removed < amount && i.getAmount() > 0) {
                    i.setAmount(i.getAmount() - 1);
                    ++removed;
                    bagChanged = true;
                }
                if (i.getAmount() > 0) continue;
                bag[slot] = null;
            }
            if (bagChanged) {
                this.module.getFishBagManager().updateBag(player.getUniqueId(), bag);
                this.module.getFishBagManager().saveBag(player.getUniqueId());
            }
        }
    }

    private int countFish(Player player, String species) {
        int c = 0;
        for (ItemStack i : player.getInventory().getContents()) {
            String sp;
            if (i == null || !i.hasItemMeta() || !i.getItemMeta().getPersistentDataContainer().has(this.module.getSpeciesKey(), PersistentDataType.STRING) || !species.equalsIgnoreCase(sp = (String)i.getItemMeta().getPersistentDataContainer().get(this.module.getSpeciesKey(), PersistentDataType.STRING))) continue;
            c += i.getAmount();
        }
        ItemStack[] bag = this.module.getFishBagManager().getBag(player.getUniqueId());
        if (bag != null) {
            for (ItemStack i : bag) {
                String sp;
                if (i == null || !i.hasItemMeta() || !i.getItemMeta().getPersistentDataContainer().has(this.module.getSpeciesKey(), PersistentDataType.STRING) || !species.equalsIgnoreCase(sp = (String)i.getItemMeta().getPersistentDataContainer().get(this.module.getSpeciesKey(), PersistentDataType.STRING))) continue;
                c += i.getAmount();
            }
        }
        return c;
    }

    private void takeFishAmount(Player player, String species, int amount) {
        ItemStack[] bag;
        int removed = 0;
        for (int slot = 0; slot < player.getInventory().getSize(); ++slot) {
            String sp;
            ItemStack i = player.getInventory().getItem(slot);
            if (i == null || !i.hasItemMeta() || !i.getItemMeta().getPersistentDataContainer().has(this.module.getSpeciesKey(), PersistentDataType.STRING) || !species.equalsIgnoreCase(sp = (String)i.getItemMeta().getPersistentDataContainer().get(this.module.getSpeciesKey(), PersistentDataType.STRING))) continue;
            while (removed < amount && i.getAmount() > 0) {
                i.setAmount(i.getAmount() - 1);
                ++removed;
            }
            if (i.getAmount() > 0) continue;
            player.getInventory().setItem(slot, null);
        }
        if (removed < amount && (bag = this.module.getFishBagManager().getBag(player.getUniqueId())) != null) {
            boolean bagChanged = false;
            for (int slot = 0; slot < bag.length; ++slot) {
                String sp;
                ItemStack i = bag[slot];
                if (i == null || !i.hasItemMeta() || !i.getItemMeta().getPersistentDataContainer().has(this.module.getSpeciesKey(), PersistentDataType.STRING) || !species.equalsIgnoreCase(sp = (String)i.getItemMeta().getPersistentDataContainer().get(this.module.getSpeciesKey(), PersistentDataType.STRING))) continue;
                while (removed < amount && i.getAmount() > 0) {
                    i.setAmount(i.getAmount() - 1);
                    ++removed;
                    bagChanged = true;
                }
                if (i.getAmount() > 0) continue;
                bag[slot] = null;
            }
            if (bagChanged) {
                this.module.getFishBagManager().updateBag(player.getUniqueId(), bag);
                this.module.getFishBagManager().saveBag(player.getUniqueId());
            }
        }
    }

    private boolean takeFish(Player player, String requiredSpecies, int amount) {
        if (this.countFish(player, requiredSpecies) >= amount) {
            this.takeFishAmount(player, requiredSpecies, amount);
            return true;
        }
        return false;
    }
}

