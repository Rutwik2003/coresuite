package net.rocky.coresuite.module.combattag;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.Module;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CombatTagModule extends Module implements Listener {

    private final Map<UUID, Long> combatTimers = new ConcurrentHashMap<>();
    private final Map<UUID, String> combatOpponents = new ConcurrentHashMap<>();
    private int timerTaskId = -1;

    public CombatTagModule(CoreSuitePlugin plugin) {
        super(plugin, "CombatTag", "combat-tag");
    }

    @Override
    public void onEnable() {
        registerListener(this);
        startTimerTask();
    }

    @Override
    public void onDisable() {
        if (timerTaskId != -1) {
            Bukkit.getScheduler().cancelTask(timerTaskId);
            timerTaskId = -1;
        }
        combatTimers.clear();
        combatOpponents.clear();
    }

    private void enterCombat(Player player, String opponentName) {
        if (!combatTimers.containsKey(player.getUniqueId())) {
            TextUtil.sendMessage(player, config.getString("messages.entered-combat", "&c&l⚔ &fYou entered combat!"));
            String enterSound = config.getString("sounds.enter-sound", "");
            if (!enterSound.isEmpty()) {
                try {
                    player.playSound(player.getLocation(), org.bukkit.Sound.valueOf(enterSound), 1f, 1f);
                } catch (Exception ignored) {}
            }
        }
        long duration = config.getInt("combat-time", 15) * 1000L;
        combatTimers.put(player.getUniqueId(), System.currentTimeMillis() + duration);
        if (opponentName != null) {
            combatOpponents.put(player.getUniqueId(), opponentName);
        }
    }

    private void clearCombat(Player player) {
        combatTimers.remove(player.getUniqueId());
        combatOpponents.remove(player.getUniqueId());
    }

    private void startTimerTask() {
        timerTaskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            long now = System.currentTimeMillis();
            for (Player player : Bukkit.getOnlinePlayers()) {
                UUID uuid = player.getUniqueId();
                if (combatTimers.containsKey(uuid)) {
                    long endTime = combatTimers.get(uuid);
                    long remainingMillis = endTime - now;
                    
                    if (remainingMillis <= 0) {
                        clearCombat(player);
                        TextUtil.sendMessage(player, config.getString("messages.left-combat", "&a&l✔ &fYou are no longer in combat!"));
                        String exitSound = config.getString("sounds.exit-sound", "");
                        if (!exitSound.isEmpty()) {
                            try {
                                player.playSound(player.getLocation(), org.bukkit.Sound.valueOf(exitSound), 1f, 1f);
                            } catch (Exception ignored) {}
                        }
                    } else {
                        int remainingSecs = (int) Math.ceil(remainingMillis / 1000.0);
                        String opp = combatOpponents.get(uuid);
                        String oppText = opp != null ? " &8| &7vs &c" + opp : "";
                        
                        String actionBarTitle = config.getString("messages.actionbar", "&c&l⚔ &fYou are in combat! &c{time}s");
                        actionBarTitle = actionBarTitle.replace("{time}", String.valueOf(remainingSecs)) + oppText;
                        
                        TextUtil.sendActionBar(player, actionBarTitle);
                    }
                }
            }
        }, 20L, 20L); // every 1 second
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player victim = (Player) event.getEntity();

        Player attacker = null;
        if (event.getDamager() instanceof Player) {
            attacker = (Player) event.getDamager();
        } else if (event.getDamager() instanceof Projectile) {
            Projectile proj = (Projectile) event.getDamager();
            if (proj.getShooter() instanceof Player) {
                attacker = (Player) proj.getShooter();
            }
        }

        if (attacker != null && attacker != victim) {
            enterCombat(victim, attacker.getName());
            enterCombat(attacker, victim.getName());
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (combatTimers.containsKey(victim.getUniqueId())) {
            clearCombat(victim);
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (victim.isOnline()) {
                    TextUtil.sendActionBar(victim, "&7You died and left combat.");
                }
            }, 1L);
        }
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        clearCombat(event.getPlayer());
    }

    @EventHandler
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (combatTimers.containsKey(player.getUniqueId())) {
            long remainingMillis = combatTimers.get(player.getUniqueId()) - System.currentTimeMillis();
            if (remainingMillis > 0) {
                String cmd = event.getMessage().split(" ")[0].toLowerCase();
                java.util.List<String> blocked = config.getStringList("blocked-commands");
                if (blocked.isEmpty()) {
                    blocked = java.util.Arrays.asList("/home", "/tpa", "/spawn", "/warp", "/hub");
                }
                if (blocked.contains(cmd)) {
                    int secs = (int) Math.ceil(remainingMillis / 1000.0);
                    event.setCancelled(true);
                    String msg = config.getString("messages.command-blocked", "&cYou cannot use commands while in combat! &7({time}s remaining)");
                    TextUtil.sendMessage(player, config.getString("prefix", "&8[&c&lCombat&8] &r") + msg.replace("{time}", String.valueOf(secs)));
                }
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        if (combatTimers.containsKey(player.getUniqueId())) {
            long remainingMillis = combatTimers.get(player.getUniqueId()) - System.currentTimeMillis();
            if (remainingMillis > 0) {
                String opp = combatOpponents.get(player.getUniqueId());
                String prefix = config.getString("prefix", "&8[&c&lCombat&8] &r");
                String broadcastMsg = config.getString("messages.combat-log-broadcast", "&c&l☠ &c{player} &flogged out while fighting &c{opponent}&f!");
                
                if (opp != null) {
                    broadcastMsg = broadcastMsg.replace("{player}", player.getName()).replace("{opponent}", opp);
                } else {
                    broadcastMsg = broadcastMsg.replace("{player}", player.getName()).replace(" while fighting &c{opponent}&f!", "");
                }
                net.kyori.adventure.text.Component comp = TextUtil.colorize(prefix + broadcastMsg)
                    .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(net.kyori.adventure.text.Component.text("Click to view stats")))
                    .clickEvent(net.kyori.adventure.text.event.ClickEvent.suggestCommand("/stats " + player.getName()));
                Bukkit.broadcast(comp);
                player.setHealth(0); // kill them
            }
            clearCombat(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        clearCombat(event.getPlayer());
    }
}
