package net.rocky.coresuite.module.autorestarter;

import net.rocky.coresuite.common.text.TextUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.util.List;

public class RestartTask extends BukkitRunnable {

    private final AutoRestarterModule module;
    private final List<AutoRestarterModule.RestartInterval> scheduledTimes;
    private final ZoneId zone;

    private Long forcedRestartTime = null;
    private boolean restarting = false;
    private long lastSecondsUntil = -1;
    private boolean delayed = false;

    public RestartTask(AutoRestarterModule module, List<AutoRestarterModule.RestartInterval> scheduledTimes, ZoneId zone) {
        this.module = module;
        this.scheduledTimes = scheduledTimes;
        this.zone = zone;
    }

    @Override
    public void run() {
        if (restarting) return;

        long secondsUntil = getSecondsUntilNextRestart();

        if (secondsUntil <= 0) {
            executeRestart();
            return;
        }

        // Fix skipped seconds issue
        if (lastSecondsUntil != -1) {
            List<Integer> broadcastIntervals = module.getConfig().getIntegerList("intervals");
            if (broadcastIntervals.isEmpty()) {
                broadcastIntervals = List.of(1800, 900, 600, 300, 180, 60, 15, 5, 4, 3, 2, 1);
            }
            for (int interval : broadcastIntervals) {
                if (lastSecondsUntil > interval && secondsUntil <= interval) {
                    broadcast(interval);
                    break;
                }
            }
        }
        
        lastSecondsUntil = secondsUntil;
    }

    public void forceRestart(int seconds) {
        this.forcedRestartTime = System.currentTimeMillis() / 1000L + seconds;
        this.lastSecondsUntil = -1;
        this.delayed = false;
    }

    public void cancelRestart() {
        this.forcedRestartTime = null;
        this.restarting = false;
        this.lastSecondsUntil = -1;
        this.delayed = false;
    }

    public long getSecondsUntilNextRestart() {
        long currentSeconds = System.currentTimeMillis() / 1000L;

        if (forcedRestartTime != null) {
            return forcedRestartTime - currentSeconds;
        }

        if (scheduledTimes.isEmpty()) {
            return Long.MAX_VALUE;
        }

        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime nextRestart = null;

        for (AutoRestarterModule.RestartInterval interval : scheduledTimes) {
            ZonedDateTime candidate = now.withHour(interval.hour).withMinute(interval.minute).withSecond(0).withNano(0);
            
            if (interval.day != null) {
                while (candidate.getDayOfWeek() != interval.day) {
                    candidate = candidate.plusDays(1);
                }
            }
            
            if (candidate.isBefore(now) || candidate.isEqual(now)) {
                if (interval.day == null) {
                    candidate = candidate.plusDays(1);
                } else {
                    candidate = candidate.plusWeeks(1);
                }
            }

            if (nextRestart == null || candidate.isBefore(nextRestart)) {
                nextRestart = candidate;
            }
        }
        
        if (nextRestart == null) return Long.MAX_VALUE;

        return nextRestart.toEpochSecond() - currentSeconds;
    }

    private void broadcast(int secondsLeft) {
        // Player Delay Check (UAR Feature)
        if (secondsLeft == module.getConfig().getInt("delay.trigger-at", 60) && !delayed) {
            int online = Bukkit.getOnlinePlayers().size();
            int required = module.getConfig().getInt("delay.if-players-more-than", 50);
            if (online >= required) {
                int delaySeconds = module.getConfig().getInt("delay.delay-by", 300);
                long newTime = (System.currentTimeMillis() / 1000L) + secondsLeft + delaySeconds;
                this.forcedRestartTime = newTime;
                this.delayed = true;
                this.lastSecondsUntil = -1;
                
                String delayMsg = module.getConfig().getString("messages.delayed");
                if (delayMsg != null && !delayMsg.isEmpty()) {
                    Bukkit.broadcast(TextUtil.colorize(delayMsg.replace("{players}", String.valueOf(online))));
                }
                return;
            }
        }

        String timeStr;
        if (secondsLeft >= 60) {
            timeStr = (secondsLeft / 60) + " minute" + (secondsLeft >= 120 && secondsLeft % 60 == 0 ? "s" : "");
        } else {
            timeStr = secondsLeft + " second" + (secondsLeft > 1 ? "s" : "");
        }

        String message = module.getConfig().getString("messages.broadcast");
        if (message != null && !message.isEmpty()) {
            message = message.replace("{time}", timeStr);
            Bukkit.broadcast(TextUtil.colorize(message));
        }
        
        // Sound & Title Features
        Bukkit.getScheduler().runTask(module.getPlugin(), () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (module.getConfig().getBoolean("sounds.enabled", true)) {
                    try {
                        Sound sound = Sound.valueOf(module.getConfig().getString("sounds.type", "BLOCK_NOTE_BLOCK_PLING"));
                        p.playSound(p.getLocation(), sound, 1f, 1f);
                    } catch (Exception ignored) {}
                }
                
                if (module.getConfig().getBoolean("titles.enabled", true)) {
                    String title = module.getConfig().getString("titles.title", "&cRestarting in").replace("{time}", timeStr);
                    String sub = module.getConfig().getString("titles.subtitle", "&e{time}").replace("{time}", timeStr);
                    TextUtil.sendTitle(p, title, sub, 10, 40, 10);
                }
            }
        });
    }

    private void executeRestart() {
        restarting = true;

        Bukkit.getScheduler().runTask(module.getPlugin(), () -> {
            // Execute commands before restart
            List<String> cmds = module.getConfig().getStringList("commands");
            for (String cmd : cmds) {
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
            }

            // Broadcast final message
            String finalMsg = module.getConfig().getString("messages.restarting", "&cServer is restarting NOW!");
            Bukkit.broadcast(TextUtil.colorize(finalMsg));

            // Kick players
            String kickMsg = module.getConfig().getString("messages.kick", "&cServer is restarting!");
            for (Player player : Bukkit.getOnlinePlayers()) {
                player.kick(TextUtil.colorize(kickMsg));
            }

            // Save data
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "save-all");

            // Stop server
            Bukkit.shutdown();
        });
    }
}
