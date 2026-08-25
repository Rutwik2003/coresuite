package net.rocky.coresuite.module.autorestarter;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.module.Module;
import org.bukkit.command.PluginCommand;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

public class AutoRestarterModule extends Module {

    private RestartTask restartTask;
    private AutoRestarterCommand commandExecutor;
    private final ZoneId istZone = ZoneId.of("Asia/Kolkata");

    public AutoRestarterModule(CoreSuitePlugin plugin) {
        super(plugin, "AutoRestarter", "autorestarter");
    }

    @Override
    public void onEnable() {
        if (!getConfig().getBoolean("enabled", true)) {
            return;
        }

        commandExecutor = new AutoRestarterCommand(this);
        
        PluginCommand command = plugin.getCommand("autorestart");
        if (command != null) {
            command.setExecutor(commandExecutor);
            command.setTabCompleter(commandExecutor);
        }

        startTask();
    }

    @Override
    public void onDisable() {
        if (restartTask != null) {
            restartTask.cancel();
            restartTask = null;
        }
    }

    public void startTask() {
        if (restartTask != null) {
            restartTask.cancel();
        }

        List<String> times = getConfig().getStringList("restarts");
        List<RestartInterval> parsedTimes = new ArrayList<>();
        
        for (String timeStr : times) {
            try {
                String[] parts = timeStr.split(";");
                if (parts.length == 3) {
                    String dayStr = parts[0].toUpperCase();
                    int hour = Integer.parseInt(parts[1]);
                    int minute = Integer.parseInt(parts[2]);
                    DayOfWeek day = dayStr.equals("DAILY") ? null : DayOfWeek.valueOf(dayStr);
                    parsedTimes.add(new RestartInterval(day, hour, minute));
                } else {
                    warn("Invalid UAR time format in config: " + timeStr + ". Use Day;HH;mm or Daily;HH;mm format.");
                }
            } catch (Exception e) {
                warn("Failed to parse restart interval: " + timeStr);
            }
        }

        restartTask = new RestartTask(this, parsedTimes, istZone);
        registerTask(restartTask.runTaskTimerAsynchronously(plugin, 20L, 20L)); // run every second
    }

    public RestartTask getRestartTask() {
        return restartTask;
    }

    public static class RestartInterval {
        public final DayOfWeek day; // null means Daily
        public final int hour;
        public final int minute;

        public RestartInterval(DayOfWeek day, int hour, int minute) {
            this.day = day;
            this.hour = hour;
            this.minute = minute;
        }
    }
}
