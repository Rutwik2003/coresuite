package net.rocky.coresuite.module.sidequests;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.module.Module;
import net.rocky.coresuite.module.grimoire.GrimoireModule;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public final class SideQuestsModule extends Module implements CommandExecutor {
  public SideQuestsModule(CoreSuitePlugin plugin) {
    super(plugin, "SideQuests", "side-quests");
  }

  @Override
  public void onEnable() {
    var c = plugin.getCommand("quests");
    if (c != null) c.setExecutor(this);
  }

  @Override
  public void onDisable() {
    var c = plugin.getCommand("quests");
    if (c != null && c.getExecutor() == this) c.setExecutor(null);
  }

  @Override
  public boolean onCommand(CommandSender sender, Command c, String label, String[] args) {
    if (sender instanceof Player p && p.hasPermission("coresuite.grimoire.use")) {
      var m = plugin.getModuleManager().getModule("grimoire");
      if (m instanceof GrimoireModule g && g.isEnabled() && g.canPlay(p)) g.quest(p);
      else p.sendMessage("The magic quest journal is currently unavailable.");
    }
    return true;
  }
}
