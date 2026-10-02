package net.rocky.coresuite.module.sidequests;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.module.Module;
import net.rocky.coresuite.module.grimoire.GrimoireModule;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public final class SideQuestsModule extends Module implements CommandExecutor, TabCompleter {
  public SideQuestsModule(CoreSuitePlugin plugin) {
    super(plugin, "SideQuests", "side-quests");
  }

  @Override
  public void onEnable() {
    var c = plugin.getCommand("quests");
    if (c != null) {
      c.setExecutor(this);
      c.setTabCompleter(this);
    }
  }

  @Override
  public void onDisable() {
    var c = plugin.getCommand("quests");
    if (c != null && c.getExecutor() == this) {
      c.setExecutor(null);
      c.setTabCompleter(null);
    }
  }

  @Override
  public boolean onCommand(CommandSender sender, Command c, String label, String[] args) {
    if (sender instanceof Player p && p.hasPermission("coresuite.grimoire.use")) {
      var m = plugin.getModuleManager().getModule("grimoire");
      if (m instanceof GrimoireModule g && g.isEnabled() && g.canPlay(p)) g.contracts(p, args);
      else p.sendMessage("The magic quest journal is currently unavailable.");
    }
    return true;
  }

  @Override
  public java.util.List<String> onTabComplete(
      CommandSender sender, Command cmd, String alias, String[] args) {
    java.util.List<String> values = java.util.List.of();
    if (args.length == 1)
      values = java.util.List.of("list", "accept", "claim", "abandon", "chapter");
    else if (args.length == 2
        && args[0].equals("accept")
        && config.getConfigurationSection("contracts") != null)
      values =
          new java.util.ArrayList<>(config.getConfigurationSection("contracts").getKeys(false));
    String prefix =
        args.length == 0 ? "" : args[args.length - 1].toLowerCase(java.util.Locale.ROOT);
    return values.stream().filter(v -> v.startsWith(prefix)).toList();
  }
}
