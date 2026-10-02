package net.rocky.coresuite.module.grimoire;

import java.util.*;
import org.bukkit.configuration.file.YamlConfiguration;

/** Main-thread account state. Serialized snapshots cross the database boundary. */
public final class MagicProfile {
  public int slots = 2, research, questStage, questProgress;
  public long revision, lastCast, globalReady, lastQuestAt;
  public double mana = 100;
  public boolean started, busy;
  public String pendingUpgrade;
  public int pendingResearch;
  public double pendingCash;
  public final Map<String, Integer> spells = new LinkedHashMap<>();
  public final List<String> prepared = new ArrayList<>();
  public final Map<String, Long> cooldowns = new HashMap<>();
  public final Set<String> discoveries = new HashSet<>();
  public int selected;

  public String selectedSpell() {
    return prepared.isEmpty() ? null : prepared.get(Math.floorMod(selected, prepared.size()));
  }

  public boolean reserve(String spell, double cost, long now, long cooldown, long global) {
    if (!Double.isFinite(cost) || cost < 0 || cooldown < 0 || global < 0) return false;
    if (busy || now < globalReady || now < cooldowns.getOrDefault(spell, 0L) || mana < cost)
      return false;
    mana -= cost;
    lastCast = now;
    globalReady = now + global;
    cooldowns.put(spell, now + cooldown);
    return true;
  }

  public void regenerate(long now, double maximum, double perSecond) {
    mana = Math.max(0, Math.min(maximum, mana));
    if (now - lastCast >= 3000) mana = Math.min(maximum, mana + perSecond);
  }

  public String encode() {
    var y = new YamlConfiguration();
    y.set("pending-upgrade", pendingUpgrade);
    y.set("pending-research", pendingResearch);
    y.set("pending-cash", pendingCash);
    y.set("slots", slots);
    y.set("research", research);
    y.set("quest-stage", questStage);
    y.set("quest-progress", questProgress);
    y.set("mana", mana);
    y.set("started", started);
    y.set("prepared", prepared);
    y.set("selected", selected);
    y.set("last-cast", lastCast);
    y.set("global-ready", globalReady);
    y.set("last-quest", lastQuestAt);
    y.set("discoveries", new ArrayList<>(discoveries));
    spells.forEach((k, v) -> y.set("spells." + k, v));
    cooldowns.forEach((k, v) -> y.set("cooldowns." + k, v));
    return y.saveToString();
  }

  public static MagicProfile decode(String text, long revision) throws Exception {
    var p = new MagicProfile();
    var y = new YamlConfiguration();
    y.loadFromString(text);
    p.pendingUpgrade = y.getString("pending-upgrade");
    p.pendingResearch = y.getInt("pending-research");
    p.pendingCash = y.getDouble("pending-cash");
    p.revision = revision;
    p.slots = Math.max(2, Math.min(5, y.getInt("slots", 2)));
    p.research = Math.max(0, y.getInt("research"));
    p.questStage = Math.max(0, y.getInt("quest-stage"));
    p.questProgress = Math.max(0, y.getInt("quest-progress"));
    p.mana = y.getDouble("mana", 100);
    if (!Double.isFinite(p.mana)) p.mana = 0;
    p.started = y.getBoolean("started");
    p.prepared.addAll(y.getStringList("prepared"));
    p.selected = y.getInt("selected");
    p.lastCast = y.getLong("last-cast");
    p.globalReady = y.getLong("global-ready");
    p.lastQuestAt = y.getLong("last-quest");
    p.discoveries.addAll(y.getStringList("discoveries"));
    var spells = y.getConfigurationSection("spells");
    if (spells != null)
      for (String key : spells.getKeys(false))
        p.spells.put(key, Math.max(1, Math.min(3, spells.getInt(key))));
    var cds = y.getConfigurationSection("cooldowns");
    if (cds != null) for (String key : cds.getKeys(false)) p.cooldowns.put(key, cds.getLong(key));
    p.prepared.removeIf(id -> !p.spells.containsKey(id));
    while (p.prepared.size() > p.slots) p.prepared.removeLast();
    return p;
  }
}
