package net.rocky.coresuite.module.fishingrpg;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SeaMonsterManager implements Listener {
    private final CoreSuitePlugin plugin;
    private final FileConfiguration config;
    private final Random random = new Random();
    
    private final Map<UUID, BukkitTask> activeBosses = new ConcurrentHashMap<>();
    private final NamespacedKey bossKey;

    public SeaMonsterManager(CoreSuitePlugin plugin, FileConfiguration config) {
        this.plugin = plugin;
        this.config = config;
        this.bossKey = new NamespacedKey(plugin, "fishing_boss");
    }

    public boolean rollCorrupted(boolean isThundering) {
        double chance = config.getDouble("sea-monsters.corrupted-chance", 0.01);
        if (isThundering) {
            chance *= config.getDouble("sea-monsters.thunderstorm-multiplier", 3.0);
        }
        return random.nextDouble() < chance;
    }

    public boolean rollWorldBoss(boolean inHotspot) {
        if (!inHotspot) return false;
        double chance = config.getDouble("sea-monsters.boss-chance-hotspot", 0.005);
        return random.nextDouble() < chance;
    }

    public void spawnWorldBoss(Player trigger, Location waterLoc) {
        String typeName = config.getString("sea-monsters.boss-type", "ELDER_GUARDIAN");
        EntityType type;
        try {
            type = EntityType.valueOf(typeName.toUpperCase());
        } catch (IllegalArgumentException e) {
            type = EntityType.ELDER_GUARDIAN;
        }

        Entity entity = waterLoc.getWorld().spawnEntity(waterLoc, type);
        if (!(entity instanceof LivingEntity boss)) return;

        boss.customName(TextUtil.colorize("&4&l⚠ Sea Monster"));
        boss.setCustomNameVisible(true);
        
        double hpMult = config.getDouble("sea-monsters.boss-health-multiplier", 10.0);
        if (boss.getAttribute(Attribute.MAX_HEALTH) != null) {
            double baseHp = boss.getAttribute(Attribute.MAX_HEALTH).getBaseValue();
            boss.getAttribute(Attribute.MAX_HEALTH).setBaseValue(baseHp * hpMult);
            boss.setHealth(baseHp * hpMult);
        }
        boss.setGlowing(true);
        boss.getPersistentDataContainer().set(bossKey, PersistentDataType.BYTE, (byte) 1);

        String announce = config.getString("messages.boss-spawn", "&4&l⚠ &cA gigantic Sea Monster has been awoken at %x%, %z%!");
        announce = announce.replace("%x%", String.valueOf(waterLoc.getBlockX()))
                           .replace("%z%", String.valueOf(waterLoc.getBlockZ()));
        for (Player p : Bukkit.getOnlinePlayers()) {
            TextUtil.sendMessage(p, announce);
            p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.0f);
            p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.0f, 1.0f);
        }

        BukkitTask timeoutTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (boss.isValid()) {
                boss.remove();
                String fade = config.getString("messages.boss-despawn", "&8The Sea Monster has returned to the depths...");
                for (Player p : Bukkit.getOnlinePlayers()) {
                    TextUtil.sendMessage(p, fade);
                }
            }
            activeBosses.remove(boss.getUniqueId());
        }, 5 * 60 * 20L); // 5 minutes
        
        activeBosses.put(boss.getUniqueId(), timeoutTask);
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent e) {
        LivingEntity entity = e.getEntity();
        if (entity.getPersistentDataContainer().has(bossKey, PersistentDataType.BYTE)) {
            BukkitTask task = activeBosses.remove(entity.getUniqueId());
            if (task != null) task.cancel();

            Player killer = entity.getKiller();
            if (killer != null) {
                double money = config.getDouble("sea-monsters.boss-reward-money", 1000.0);
                int xp = config.getInt("sea-monsters.boss-reward-xp-levels", 10);
                plugin.getEconomyManager().deposit(killer, money);
                killer.setLevel(killer.getLevel() + xp);
                
                List<String> commands = config.getStringList("sea-monsters.boss-reward-commands");
                for (String cmd : commands) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", killer.getName()));
                }

                String killMsg = config.getString("messages.boss-killed", "&a%player% has slain the Sea Monster!");
                killMsg = killMsg.replace("%player%", killer.getName());
                for (Player p : Bukkit.getOnlinePlayers()) {
                    TextUtil.sendMessage(p, killMsg);
                }

                Firework fw = entity.getWorld().spawn(entity.getLocation(), Firework.class);
                FireworkMeta meta = fw.getFireworkMeta();
                meta.addEffect(FireworkEffect.builder().withColor(Color.RED).withFade(Color.ORANGE).with(FireworkEffect.Type.BALL_LARGE).build());
                meta.setPower(1);
                fw.setFireworkMeta(meta);
            }
        }
    }

    public void shutdown() {
        for (BukkitTask task : activeBosses.values()) {
            task.cancel();
        }
        activeBosses.clear();
    }
}
