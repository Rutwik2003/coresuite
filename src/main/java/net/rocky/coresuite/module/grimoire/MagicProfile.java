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
  public boolean wand, magicEnabled = true;
  public String activeContract = "", pendingStock = "";
  public int pendingStockLimit;
  public final Map<String, Integer> shards = new HashMap<>(),
      duplicates = new HashMap<>(),
      contractProgress = new HashMap<>(),
      pendingMaterials = new HashMap<>(),
      pendingShards = new HashMap<>(),
      pendingFish = new HashMap<>();
  public final Map<String, Long> contractClaims = new HashMap<>();
  public String pendingDuplicate = "";
  public int pendingDuplicateCount;

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
    regenerate(now, maximum, perSecond, 3000);
  }

  public void regenerate(long now, double maximum, double perSecond, long delay) {
    maximum = SpellSpec.bounded(maximum, 100, 20, 1000);
    perSecond = SpellSpec.bounded(perSecond, 2, 0, 10);
    if (!Double.isFinite(mana)) mana = 0;
    mana = Math.max(0, Math.min(maximum, mana));
    if (now - lastCast >= Math.max(0, delay)) mana = Math.min(maximum, mana + perSecond);
  }

  public String encode() {
    var y = new YamlConfiguration();
    y.set("wand", wand);
    y.set("magic-enabled", magicEnabled);
    y.set("active-contract", activeContract);
    y.set("pending-stock", pendingStock);
    y.set("pending-stock-limit", pendingStockLimit);
    y.set("pending-duplicate", pendingDuplicate);
    y.set("pending-duplicate-count", pendingDuplicateCount);
    shards.forEach((k, v) -> y.set("shards." + k, v));
    duplicates.forEach((k, v) -> y.set("duplicates." + k, v));
    contractProgress.forEach((k, v) -> y.set("contract-progress." + k, v));
    contractClaims.forEach((k, v) -> y.set("contract-claims." + k, v));
    pendingMaterials.forEach((k, v) -> y.set("pending-materials." + k, v));
    pendingFish.forEach((k, v) -> y.set("pending-fish." + k, v));
    pendingShards.forEach((k, v) -> y.set("pending-shards." + k, v));
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

  private static void readCounts(YamlConfiguration y, String path, Map<String, Integer> target) {
    var section = y.getConfigurationSection(path);
    if (section != null)
      for (String key : section.getKeys(false))
        target.put(key, Math.max(0, Math.min(100000, section.getInt(key))));
  }

  public static MagicProfile decode(String text, long revision) throws Exception {
    var p = new MagicProfile();
    var y = new YamlConfiguration();
    y.loadFromString(text);
    p.wand = y.getBoolean("wand");
    p.magicEnabled = y.getBoolean("magic-enabled", true);
    p.activeContract = y.getString("active-contract", "");
    p.pendingStock = y.getString("pending-stock", "");
    p.pendingStockLimit = y.getInt("pending-stock-limit");
    p.pendingDuplicate = y.getString("pending-duplicate", "");
    p.pendingDuplicateCount = Math.max(0, y.getInt("pending-duplicate-count"));
    readCounts(y, "shards", p.shards);
    readCounts(y, "duplicates", p.duplicates);
    readCounts(y, "contract-progress", p.contractProgress);
    readCounts(y, "pending-materials", p.pendingMaterials);
    readCounts(y, "pending-shards", p.pendingShards);
    readCounts(y, "pending-fish", p.pendingFish);
    var claims = y.getConfigurationSection("contract-claims");
    if (claims != null)
      for (String key : claims.getKeys(false)) p.contractClaims.put(key, claims.getLong(key));
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
