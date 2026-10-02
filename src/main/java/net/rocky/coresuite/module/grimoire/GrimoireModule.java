package net.rocky.coresuite.module.grimoire;

import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Level;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.integration.AuthenticationGate;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.Module;
import net.rocky.coresuite.module.worldbosses.WorldBossesModule;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;

/** All gameplay state is owned by the server thread; JDBC receives immutable snapshots. */
public final class GrimoireModule extends Module
    implements Listener, CommandExecutor, TabCompleter {
  private final Map<UUID, MagicProfile> profiles = new HashMap<>();
  private final Set<UUID> loading = new HashSet<>();
  private final CastInputGate castInputs = new CastInputGate();
  private final Map<String, SpellSpec> spells = new LinkedHashMap<>();
  private MagicStore store;
  private SpellEngine engine;
  private boolean running, storageReady;
  private long generation;
  private NamespacedKey itemKey, tokenKey;
  private int seconds;

  private record ProgressEvent(String type, int amount, String contract, int chapter) {}

  private final Map<UUID, ArrayDeque<ProgressEvent>> pendingProgress = new HashMap<>();

  public GrimoireModule(CoreSuitePlugin plugin) {
    super(plugin, "Grimoire", "grimoire");
  }

  @Override
  public void onEnable() {
    running = true;
    generation++;
    itemKey = new NamespacedKey(plugin, "magic_item");
    tokenKey = new NamespacedKey(plugin, "magic_token");
    store = new MagicStore(plugin.getDatabaseManager());
    engine = new SpellEngine(this);
    var section = config.getConfigurationSection("spells");
    if (section != null)
      for (String id : section.getKeys(false))
        if (section.isConfigurationSection(id) && section.getBoolean(id + ".enabled", true))
          spells.put(id, SpellSpec.load(id, section.getConfigurationSection(id)));
    registerListener(this);
    for (String name : List.of("grimoire", "magic", "magicadmin")) {
      var command = plugin.getCommand(name);
      if (command != null) {
        command.setExecutor(this);
        command.setTabCompleter(this);
      }
    }
    long epoch = generation;
    plugin
        .getDatabaseManager()
        .executeAsync(
            () -> {
              try {
                store.initialize();
                main(
                    epoch,
                    () -> {
                      storageReady = true;
                      Bukkit.getOnlinePlayers().forEach(this::load);
                    });
              } catch (Exception e) {
                failure("initialize", e);
              }
            });
    registerTask(Bukkit.getScheduler().runTaskTimer(plugin, engine::tick, 1, 1));
    registerTask(Bukkit.getScheduler().runTaskTimer(plugin, this::pulse, 20, 20));
  }

  @Override
  public void onDisable() {
    running = false;
    storageReady = false;
    generation++;
    if (engine != null) engine.clear();
    for (Player p : Bukkit.getOnlinePlayers())
      if (p.getOpenInventory().getTopInventory().getHolder() instanceof MagicMenu)
        p.closeInventory();
    profiles.forEach(
        (id, state) -> {
          if (!state.busy) saveSession(id, state);
        });
    pendingProgress.clear();
    profiles.clear();
    castInputs.clear();
    loading.clear();
    spells.clear();
    for (String name : List.of("grimoire", "magic", "magicadmin")) {
      var cmd = plugin.getCommand(name);
      if (cmd != null && cmd.getExecutor() == this) {
        cmd.setExecutor(null);
        cmd.setTabCompleter(null);
      }
    }
  }

  private void main(long epoch, Runnable task) {
    if (!plugin.isEnabled()) return;
    Bukkit.getScheduler()
        .runTask(
            plugin,
            () -> {
              if (running && epoch == generation) task.run();
            });
  }

  private void failure(String action, Exception error) {
    plugin.getLogger().log(Level.SEVERE, "[Grimoire] Could not " + action, error);
  }

  private void load(Player player) {
    if (!storageReady || !AuthenticationGate.isAuthenticated(player)) return;
    UUID id = player.getUniqueId();
    if (profiles.containsKey(id) || !loading.add(id)) return;
    long epoch = generation;
    MagicStore current = store;
    plugin
        .getDatabaseManager()
        .executeAsync(
            () -> {
              try {
                MagicProfile p = current.load(id);
                MagicProfile claimed = current.collect(id);
                if (claimed != null) p = claimed;
                MagicProfile loaded = p;
                main(
                    epoch,
                    () -> {
                      loading.remove(id);
                      if (player.isOnline()) {
                        loaded.mana = Math.min(maximumMana(), loaded.mana);
                        loaded.prepared.removeIf(idValue -> !spells.containsKey(idValue));
                        profiles.put(id, loaded);
                      }
                    });
              } catch (Exception e) {
                failure("load " + id, e);
                main(epoch, () -> loading.remove(id));
              }
            });
  }

  @EventHandler
  public void join(PlayerJoinEvent event) {
    load(event.getPlayer());
  }

  private void saveSession(UUID id, MagicProfile state) {
    String snapshot = state.encode();
    long revision = state.revision;
    MagicStore current = store;
    plugin
        .getDatabaseManager()
        .executeAsync(
            () -> {
              try {
                current.save(id, revision, snapshot);
              } catch (Exception e) {
                failure("save session " + id, e);
              }
            });
  }

  @EventHandler
  public void quit(PlayerQuitEvent event) {
    pendingProgress.remove(event.getPlayer().getUniqueId());
    castInputs.remove(event.getPlayer().getUniqueId());
    MagicProfile state = profiles.remove(event.getPlayer().getUniqueId());
    if (state != null && !state.busy) saveSession(event.getPlayer().getUniqueId(), state);
  }

  private void pulse() {
    seconds++;
    for (Player player : Bukkit.getOnlinePlayers()) {
      if (!profiles.containsKey(player.getUniqueId())) {
        if (seconds % 10 == 0) load(player);
        continue;
      }
      if (seconds % 5 == 0) refreshItemModels(player);
      MagicProfile p = profiles.get(player.getUniqueId());
      if (!p.started || p.busy || !canPlay(player)) continue;
      var events = pendingProgress.get(player.getUniqueId());
      if (events != null && !events.isEmpty()) {
        var e = events.removeFirst();
        if (e.contract().equals(p.activeContract) && e.chapter() == p.questStage)
          objective(player, e.type(), e.amount());
        if (events.isEmpty()) pendingProgress.remove(player.getUniqueId());
        if (p.busy) continue;
      }
      p.regenerate(
          System.currentTimeMillis(),
          maximumMana(),
          SpellSpec.bounded(config.getDouble("mana.regeneration-per-second", 2), 2, 0, 10)
              * (p.wand ? 1.1 : 1),
          (long)
              (SpellSpec.bounded(config.getDouble("mana.recovery-delay-seconds", 3), 3, 0, 30)
                  * 1000));
      if (equipped(player) && !inArena(player.getLocation())) {
        String selected = p.selectedSpell();
        String name =
            selected != null && spells.containsKey(selected)
                ? spells.get(selected).name()
                : "No prepared spell";
        TextUtil.sendActionBar(
            player,
            "&b✦ "
                + (int) p.mana
                + "/"
                + (int) maximumMana()
                + " Mana &8│ &d"
                + name
                + " &8│ &7"
                + (p.selected + 1)
                + "/"
                + p.prepared.size());
      }
    }
  }

  public boolean canPlay(Player p) {
    return running
        && p.hasPermission("coresuite.grimoire.use")
        && p.isOnline()
        && !p.isDead()
        && p.getGameMode() != GameMode.SPECTATOR
        && AuthenticationGate.isAuthenticated(p);
  }

  public boolean inArena(Location location) {
    var m = plugin.getModuleManager().getModule("world-bosses");
    return m instanceof WorldBossesModule boss
        && boss.isEnabled()
        && boss.getPortalManager() != null
        && boss.getPortalManager().inArena(location);
  }

  boolean canMagicPvp(Player caster, Player target) {
    if (!config.getBoolean("pvp.enabled", false)
        || !caster.getWorld().equals(target.getWorld())
        || !caster.getWorld().getPVP()
        || inArena(caster.getLocation())
        || inArena(target.getLocation())
        || raidAllies(caster, target)
        || !AuthenticationGate.isAuthenticated(target)) return false;
    var board = Bukkit.getScoreboardManager().getMainScoreboard();
    var team = board.getEntryTeam(caster.getName());
    if (team != null && team.equals(board.getEntryTeam(target.getName()))) return false;
    var zones = config.getConfigurationSection("pvp.zones");
    if (zones == null) return false;
    for (String key : zones.getKeys(false)) {
      var zone = zones.getConfigurationSection(key);
      if (zone != null
          && MagicZone.contains(zone, caster.getLocation())
          && MagicZone.contains(zone, target.getLocation())) return true;
    }
    return false;
  }

  boolean raidAllies(Player a, Player b) {
    var m = plugin.getModuleManager().getModule("world-bosses");
    return m instanceof WorldBossesModule boss
        && boss.isEnabled()
        && boss.areRaidAllies(a.getUniqueId(), b.getUniqueId());
  }

  static int tomeModel(String spell) {
    return switch (spell) {
      case "mana-dart" -> 71010;
      case "fireball" -> 71011;
      case "water-cannon" -> 71012;
      case "frost-lance" -> 71013;
      case "zoltraak" -> 71014;
      case "dimension-door" -> 71015;
      case "aura-of-the-hearth" -> 71016;
      case "harvesters-touch" -> 71017;
      default -> 71002;
    };
  }

  private void refreshItemModels(Player player) {
    for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
      ItemStack stack = player.getInventory().getItem(slot);
      String type = kind(stack);
      if (type == null || !type.startsWith("tome:")) continue;
      int desired = tomeModel(type.substring(5));
      var meta = stack.getItemMeta();
      if (meta.hasCustomModelData() && meta.getCustomModelData() == desired) continue;
      meta.setCustomModelData(desired);
      stack.setItemMeta(meta);
      player.getInventory().setItem(slot, stack);
    }
  }

  private String kind(ItemStack item) {
    if (item == null || !item.hasItemMeta()) return null;
    return item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
  }

  private boolean focus(String type) {
    return "grimoire".equals(type) || "wand".equals(type);
  }

  private boolean equipped(Player p) {
    if (focus(kind(p.getInventory().getItemInMainHand()))
        || focus(kind(p.getInventory().getItemInOffHand()))) return true;
    MagicProfile state = profiles.get(p.getUniqueId());
    return state != null
        && state.wand
        && Arrays.stream(p.getInventory().getStorageContents())
            .anyMatch(i -> "wand".equals(kind(i)));
  }

  private ItemStack item(String kind, String title, Material material, int model) {
    var item = new ItemStack(material);
    var meta = item.getItemMeta();
    meta.displayName(TextUtil.colorize(title));
    meta.setCustomModelData(model);
    meta.getPersistentDataContainer().set(itemKey, PersistentDataType.STRING, kind);
    item.setItemMeta(meta);
    return item;
  }

  private void giveBook(Player p) {
    for (ItemStack existing : p.getInventory().getContents())
      if ("grimoire".equals(kind(existing))) {
        message(p, "&7Your Grimoire is already in your inventory.");
        return;
      }
    if (p.getInventory().firstEmpty() < 0) {
      message(p, "&eFree one inventory slot, then use /magic book.");
      return;
    }
    p.getInventory().addItem(item("grimoire", "&d✦ Astral Grimoire", Material.BOOK, 71001));
  }

  private void message(CommandSender p, String text) {
    TextUtil.sendMessage(p, "&8[&dGrimoire&8] &r" + text);
  }

  private MagicProfile profile(Player p) {
    MagicProfile state = profiles.get(p.getUniqueId());
    if (state == null) {
      load(p);
      message(p, "&eYour spellbook is loading. Try again shortly.");
    } else if (state.busy) {
      message(p, "&eSaving your last action; try again shortly.");
      return null;
    }
    return state;
  }

  /**
   * Publish progression only after commit. A failure never grants the unlock or spends research.
   */
  private void change(Player player, Consumer<MagicProfile> mutation, Runnable success) {
    change(player, mutation, success, null, null);
  }

  private void change(
      Player player,
      Consumer<MagicProfile> mutation,
      Runnable success,
      String token,
      String spell) {
    MagicProfile old = profile(player);
    if (old == null) return;
    try {
      MagicProfile next = MagicProfile.decode(old.encode(), old.revision);
      mutation.accept(next);
      String payload = next.encode();
      old.busy = true;
      long epoch = generation;
      MagicStore current = store;
      UUID id = player.getUniqueId();
      plugin
          .getDatabaseManager()
          .executeAsync(
              () -> {
                try {
                  if (token == null
                      && old.pendingUpgrade == null
                      && next.pendingUpgrade != null
                      && !next.pendingStock.isEmpty())
                    current.saveWithStock(
                        id, old.revision, payload, next.pendingStock, next.pendingStockLimit);
                  else if (token == null) current.save(id, old.revision, payload);
                  else current.redeem(token, spell, id, old.revision, payload);
                  next.revision = old.revision + 1;
                  main(
                      epoch,
                      () -> {
                        if (profiles.get(id) == old) {
                          profiles.put(id, next);
                          if (player.isOnline()) success.run();
                        }
                      });
                } catch (Exception e) {
                  failure("save " + id, e);
                  main(
                      epoch,
                      () -> {
                        if (profiles.get(id) == old) {
                          old.busy = false;
                          message(
                              player,
                              "&cThat action could not be saved. Nothing was granted. Try again or"
                                  + " contact an admin.");
                        }
                      });
                }
              });
    } catch (Exception e) {
      failure("prepare change", e);
    }
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void spellDamage(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
    if (engine != null) engine.observeDamage(event);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void interact(PlayerInteractEvent e) {
    Player player = e.getPlayer();
    if (!canPlay(player)) return;
    ItemStack used = e.getItem();
    String kind = kind(used);
    if (kind != null && kind.startsWith("tome:") && e.getAction().isRightClick()) {
      e.setCancelled(true);
      learn(player, used, kind.substring(5));
      return;
    }
    if (!equipped(player)) return;
    // Main-hand food, blocks and other usable items retain their vanilla interactions.
    if (!focus(kind)) return;
    if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND
        && focus(kind(player.getInventory().getItemInMainHand()))) return;
    if (e.getAction().isRightClick()) {
      e.setCancelled(true);
      if (castInputs.accept(
          player.getUniqueId(),
          System.nanoTime(),
          Math.max(250, Math.min(1000, config.getInt("casting.repeat-gap-ms", 350))) * 1_000_000L))
        control(
            player,
            config.getString(
                player.isSneaking() ? "controls.sneak-right-click" : "controls.right-click",
                player.isSneaking() ? "loadout" : "cast"));
    } else if (e.getAction().isLeftClick() && player.isSneaking()) {
      e.setCancelled(true);
      control(player, config.getString("controls.sneak-left-click", "cycle"));
    }
  }

  private void control(Player player, String action) {
    switch (action.toLowerCase(Locale.ROOT)) {
      case "cast" -> cast(player);
      case "cycle" -> cycle(player);
      case "loadout" -> open(player);
      default -> {}
    }
  }

  private void learn(Player player, ItemStack tome, String spell) {
    if (!spells.containsKey(spell)) return;
    String token =
        tome.getItemMeta().getPersistentDataContainer().get(tokenKey, PersistentDataType.STRING);
    if (token == null) {
      message(player, "&cThis tome has no valid seal.");
      return;
    }
    change(
        player,
        p -> {
          if (p.spells.containsKey(spell)) {
            p.research = Math.min(100000, p.research + 5);
            p.duplicates.merge(spell, 1, (a, b) -> Math.min(100000, a + b));
          } else p.spells.put(spell, 1);
        },
        () -> {
          // Remove matching token wherever it moved; copied/replayed tokens cannot grant twice.
          for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack at = player.getInventory().getItem(slot);
            if (at != null
                && at.hasItemMeta()
                && token.equals(
                    at.getItemMeta()
                        .getPersistentDataContainer()
                        .get(tokenKey, PersistentDataType.STRING)))
              player.getInventory().setItem(slot, null);
          }
          message(
              player,
              "&aTome studied! New spells are available in /grimoire; duplicates grant 5"
                  + " research and one saved duplicate credit for upgrades.");
        },
        token,
        spell);
  }

  private void cycle(Player player) {
    MagicProfile p = profile(player);
    if (p == null || p.prepared.isEmpty()) return;
    p.selected = (p.selected + 1) % p.prepared.size();
    message(player, "&dPrepared: " + spells.get(p.selectedSpell()).name());
  }

  private void cast(Player player) {
    MagicProfile p = profile(player);
    if (p == null || !p.started || !equipped(player)) return;
    if (("wand".equals(kind(player.getInventory().getItemInMainHand()))
            || "wand".equals(kind(player.getInventory().getItemInOffHand())))
        && !p.wand) {
      message(player, "&eThis wand is not bound to you. /magic wand");
      return;
    }
    SpellSpec spell = spells.get(p.selectedSpell());
    if (spell == null) {
      message(player, "&ePrepare a spell with /grimoire first.");
      return;
    }
    if (!spell.arenaAllowed() && inArena(player.getLocation())) {
      message(player, "&cThat spell is sealed within the arena.");
      return;
    }
    if (inArena(player.getLocation())) {
      var m = (WorldBossesModule) plugin.getModuleManager().getModule("world-bosses");
      if (!m.getPortalManager().canParticipate(player)) {
        message(player, "&cEnter through a boss rift first.");
        return;
      }
    }
    long now = System.currentTimeMillis();
    int tier = p.spells.getOrDefault(spell.id(), 1);
    if (!p.magicEnabled) {
      message(player, "&7Magic is paused. /magic toggle");
      return;
    }
    double cost = spell.tierMana(tier, config.getDouble("mana.cost-reduction-per-tier", .05));
    if (p.mana < cost || now < p.globalReady || now < p.cooldowns.getOrDefault(spell.id(), 0L)) {
      if (p.mana < cost)
        message(
            player,
            "&b" + spell.name() + " needs " + cost + " mana; you have " + (int) p.mana + ".");
      else
        message(
            player,
            "&7"
                + spell.name()
                + " recovers in "
                + String.format(
                    java.util.Locale.ROOT,
                    "%.1f",
                    Math.max(p.globalReady, p.cooldowns.getOrDefault(spell.id(), 0L)) / 1000.0
                        - now / 1000.0)
                + "s.");
      return;
    }
    change(
        player,
        next -> {
          next.reserve(spell.id(), cost, now, spell.cooldown(), 500);
        },
        () -> {
          if (!canPlay(player) || !equipped(player)) return;
          if (!engine.cast(player, spell, tier)) {
            message(player, "&eNo safe target area or destination. Mana returned.");
            change(player, next -> next.mana = Math.min(maximumMana(), next.mana + cost), () -> {});
            return;
          }
          objective(player, "CAST:" + spell.id());
          player
              .getWorld()
              .playSound(
                  player.getLocation(), "coresuite:magic.cast", SoundCategory.PLAYERS, .7f, 1.0f);
        });
  }

  public void awardBossResearch(UUID player, String encounter, int amount, String boss) {
    long epoch = generation;
    MagicStore current = store;
    boolean mvp = amount == 3;
    int research =
        Math.max(
            0,
            Math.min(
                10,
                config.getInt(
                    "boss-rewards." + (mvp ? "mvp-research" : "participation-research"), amount)));
    int shards =
        Math.max(
            0,
            Math.min(
                10,
                config.getInt(
                    "boss-rewards." + (mvp ? "mvp-shards" : "participation-shards"), mvp ? 3 : 1)));
    double chance =
        SpellSpec.bounded(config.getDouble("boss-rewards.signature-chance-percent", 2), 2, 0, 100);
    String element =
        switch (boss) {
          case "VALERIUS" -> "fire";
          case "BOREALIS" -> "ice";
          case "AETHELGARD" -> "nature";
          default -> "arcane";
        };
    String possible =
        boss.equals("XOLTHOS")
            ? "zoltraak-fragment"
            : boss.equals("AETHELGARD") ? "hearth-fragment" : "";
    String fragment =
        !possible.isEmpty()
                && Math.floorMod((encounter + player + possible).hashCode(), 10000) < chance * 100
            ? possible
            : "";
    MagicProfile state = profiles.get(player);
    String active = state == null ? "" : state.activeContract;
    plugin
        .getDatabaseManager()
        .executeAsync(
            () -> {
              try {
                boolean first =
                    current.awardBossBundle(
                        encounter + ":" + player, player, research, element, shards, fragment);
                main(
                    epoch,
                    () -> {
                      Player online = Bukkit.getPlayer(player);
                      MagicProfile present = profiles.get(player);
                      if (online != null && first) {
                        if (present != null && present.activeContract.equals(active))
                          objective(online, "BOSS:" + boss);
                        message(
                            online,
                            "&dBoss magic rewards: +"
                                + research
                                + " research, +"
                                + shards
                                + " "
                                + element
                                + " shards"
                                + (fragment.isEmpty() ? "" : " and 1 " + fragment)
                                + ". Collect with /magic research.");
                      }
                    });
              } catch (Exception error) {
                failure("record boss magic rewards", error);
              }
            });
  }

  private void collectResearch(Player player) {
    MagicProfile old = profile(player);
    if (old == null) return;
    String latestPayload = old.encode();
    old.busy = true;
    long epoch = generation;
    MagicStore current = store;
    UUID id = player.getUniqueId();
    plugin
        .getDatabaseManager()
        .executeAsync(
            () -> {
              try {
                MagicProfile claimed = current.collect(id, latestPayload, old.revision);
                main(
                    epoch,
                    () -> {
                      if (profiles.get(id) == old) {
                        old.busy = false;
                        if (claimed != null) {
                          claimed.mana = old.mana;
                          claimed.selected = old.selected;
                          profiles.put(id, claimed);
                        }
                        message(
                            player,
                            "&dResearch available: "
                                + (claimed == null ? old.research : claimed.research));
                      }
                    });
              } catch (Exception e) {
                failure("collect research", e);
                main(epoch, () -> old.busy = false);
              }
            });
  }

  public void discover(Player player, String encounter, String offeredSpell) {
    MagicProfile p = profile(player);
    if (p == null) return;
    if (!p.started) {
      change(
          player,
          next -> {
            next.started = true;
            next.spells.put("mana-dart", 1);
            next.prepared.add("mana-dart");
            next.questStage = 1;
            next.discoveries.add(encounter);
          },
          () -> {
            giveBook(player);
            message(
                player,
                "&dA Dusty Traveller: your first page awakens. Cast five spells, then open /magic"
                    + " quests chapter.");
          });
      return;
    }
    if (p.discoveries.contains(encounter)) {
      message(
          player, "&7You have already met this scholar. Use /grimoire nearby for their lesson.");
      showQuestHint(player, p);
      return;
    }
    change(
        player,
        next -> {
          next.discoveries.add(encounter);
          while (next.discoveries.size() > 100)
            next.discoveries.remove(next.discoveries.iterator().next());
          next.research = Math.min(100000, next.research + 3);
          if (questType(next).equals("ENCOUNTER"))
            next.questProgress = Math.min(questGoal(next), next.questProgress + 1);
        },
        () -> {
          message(
              player,
              "&aScholar discovered: +3 research. Open /grimoire nearby to study the scholar's"
                  + " spell.");
          showQuestHint(player, profiles.get(player.getUniqueId()));
        });
  }

  private void showQuestHint(Player player, MagicProfile state) {
    if (state == null) return;
    var active = chapter(state);
    if (active == null) return;
    message(
        player,
        "&dCurrent task: &f"
            + active.getString("name", "Field Research")
            + " &7— "
            + active.getString("description", "")
            + " &e("
            + state.questProgress
            + "/"
            + questGoal(state)
            + "). &7Use /quests to view or claim it.");
  }

  public void study(Player player, String spell) {
    MagicProfile p = profile(player);
    if (p == null || !p.started || !spells.containsKey(spell)) return;
    var encounters = plugin.getModuleManager().getModule("world-encounters");
    if (!(encounters instanceof net.rocky.coresuite.module.worldencounters.WorldEncountersModule m)
        || !m.offers(player, spell)) {
      message(player, "&eFind a traveling scholar who teaches that spell.");
      return;
    }
    if (p.spells.containsKey(spell)) {
      message(player, "&7You already know that spell.");
      return;
    }
    int price = Math.max(1, config.getInt("spells." + spell + ".research-cost", 6));
    if (p.pendingUpgrade != null) {
      message(player, "&eResolve your pending upgrade before spending research.");
      return;
    }
    if (p.research < price) {
      message(
          player, "&eRequires " + price + " research. Discover encounters and complete quests.");
      return;
    }
    purchaseUpgrade(player, "learn:" + spell, price, m.lessonCurrency());
  }

  private double maximumMana() {
    return SpellSpec.bounded(config.getDouble("mana.maximum", 100), 100, 20, 1000);
  }

  private org.bukkit.configuration.ConfigurationSection chapter(MagicProfile p) {
    var quests = plugin.getModuleManager().getModule("side-quests");
    return quests != null && quests.isEnabled()
        ? quests.getConfig().getConfigurationSection("chapters." + p.questStage)
        : null;
  }

  private String questType(MagicProfile p) {
    var chapter = chapter(p);
    return chapter == null ? "" : chapter.getString("objective", "");
  }

  private int questGoal(MagicProfile p) {
    var chapter = chapter(p);
    return chapter == null
        ? Integer.MAX_VALUE
        : Math.max(1, Math.min(1000, chapter.getInt("goal", 5)));
  }

  public void objective(Player player, String type) {
    objective(player, type, 1);
  }

  public void objective(Player player, String type, int amount) {
    MagicProfile p = profiles.get(player.getUniqueId());
    if (p == null || !p.started || amount < 1) return;
    if (p.busy) {
      var queue = pendingProgress.computeIfAbsent(player.getUniqueId(), id -> new ArrayDeque<>());
      if (queue.size() < 64)
        queue.add(new ProgressEvent(type, amount, p.activeContract, p.questStage));
      return;
    }
    String chapterType = type.startsWith("CAST:") ? "CAST" : type;
    boolean chapterMatches = questType(p).equals(chapterType) && p.questProgress < questGoal(p);
    var chapter = chapter(p);
    if (chapterMatches
        && chapter.getBoolean("repeatable")
        && System.currentTimeMillis() - p.lastQuestAt < 86400000L) chapterMatches = false;
    var contract = contract(p.activeContract);
    String counter =
        contract != null
                && contract.getConfigurationSection("objectives") != null
                && contract.getConfigurationSection("objectives").contains(type)
            ? type
            : chapterType;
    boolean contractMatches =
        contract != null
            && contract.getConfigurationSection("objectives") != null
            && contract.getConfigurationSection("objectives").contains(counter);
    if (!chapterMatches && !contractMatches) return;
    final boolean updateChapter = chapterMatches, updateContract = contractMatches;
    change(
        player,
        next -> {
          if (updateChapter)
            next.questProgress = Math.min(questGoal(next), next.questProgress + amount);
          if (updateContract)
            next.contractProgress.merge(
                counter, Math.min(1000, amount), (x, y) -> Math.min(1000, x + y));
        },
        () -> {});
  }

  private org.bukkit.configuration.ConfigurationSection contract(String id) {
    var m = plugin.getModuleManager().getModule("side-quests");
    return m == null || !m.isEnabled() || id == null || id.isEmpty()
        ? null
        : m.getConfig().getConfigurationSection("contracts." + id);
  }

  public void offerContract(Player player, String merchant) {
    var m = plugin.getModuleManager().getModule("side-quests");
    if (m == null || !m.isEnabled()) return;
    var all = m.getConfig().getConfigurationSection("contracts");
    if (all == null) return;
    for (String id : all.getKeys(false))
      if (merchant.equals(all.getString(id + ".merchant")))
        message(
            player,
            "&dTask offered: " + all.getString(id + ".name", id) + ". &e/quests accept " + id);
  }

  private void openJournal(Player player) {
    MagicProfile p = profile(player);
    if (p == null) return;
    var menu = new MagicMenu(player.getUniqueId());
    menu.inventory = Bukkit.createInventory(menu, 54, "Scholar Contracts");
    var quests = plugin.getModuleManager().getModule("side-quests");
    var all = quests == null ? null : quests.getConfig().getConfigurationSection("contracts");
    int slot = 10;
    if (all != null)
      for (String id : all.getKeys(false)) {
        if (slot >= 44) break;
        var c = all.getConfigurationSection(id);
        if (c == null) continue;
        boolean active = id.equals(p.activeContract);
        menu.inventory.setItem(
            slot,
            button(
                active ? Material.ENCHANTED_BOOK : Material.WRITABLE_BOOK,
                "&d" + c.getString("name", id),
                "&7" + c.getString("description", ""),
                "&7Scholar: " + c.getString("merchant"),
                "&7Objectives: " + MagicProgression.costs(c, "objectives"),
                "&7Progress: " + (active ? p.contractProgress : "not accepted"),
                "&aResearch: "
                    + c.getInt("research")
                    + " | Shards: "
                    + MagicProgression.costs(c, "shards"),
                "&aDuplicate credit: " + c.getString("duplicate-spell", "none"),
                active ? "&eClick to claim when complete" : "&eClick near the scholar to accept"));
        menu.actions.put(
            slot,
            () -> contracts(player, active ? new String[] {"claim"} : new String[] {"accept", id}));
        slot++;
        if (slot % 9 == 8) slot += 2;
      }
    menu.inventory.setItem(
        45,
        button(Material.BOOK, "&bIntroductory chapters", "&7View or claim your current chapter"));
    menu.actions.put(45, () -> quest(player));
    menu.inventory.setItem(
        49, button(Material.PAPER, "&bProgress details", "&7Print all contract details in chat"));
    menu.actions.put(49, () -> contracts(player, new String[] {"list"}));
    menu.inventory.setItem(53, button(Material.ARROW, "&bGrimoire"));
    menu.actions.put(53, () -> open(player));
    player.openInventory(menu.inventory);
  }

  public void contracts(Player player, String[] args) {
    MagicProfile p = profile(player);
    if (p == null) return;
    if (args.length == 0) {
      openJournal(player);
      return;
    }
    if (args[0].equals("list")) {
      message(
          player, "&dActive contract: " + (p.activeContract.isEmpty() ? "none" : p.activeContract));
      var active = contract(p.activeContract);
      if (active != null)
        for (var e : MagicProgression.costs(active, "objectives").entrySet())
          message(
              player,
              "&7"
                  + e.getKey()
                  + ": "
                  + p.contractProgress.getOrDefault(e.getKey(), 0)
                  + "/"
                  + e.getValue());
      var quests = plugin.getModuleManager().getModule("side-quests");
      var all = quests == null ? null : quests.getConfig().getConfigurationSection("contracts");
      if (all != null)
        for (String id : all.getKeys(false))
          message(
              player,
              "&d"
                  + id
                  + " &7— "
                  + all.getString(id + ".name", id)
                  + " | meet "
                  + all.getString(id + ".merchant"));
      message(player, "&7/quests accept <id> | claim | abandon | chapter");
      return;
    }
    if (args[0].equals("abandon")) {
      change(
          player,
          n -> {
            n.activeContract = "";
            n.contractProgress.clear();
          },
          () -> message(player, "&7Contract abandoned; no rewards granted."));
      return;
    }
    if (args[0].equals("accept") && args.length > 1) {
      if (!p.started) {
        message(player, "&eMeet a scholar to start your Grimoire first.");
        return;
      }
      var c = contract(args[1]);
      var encounters = plugin.getModuleManager().getModule("world-encounters");
      if (c == null
          || !(encounters
              instanceof net.rocky.coresuite.module.worldencounters.WorldEncountersModule merchant)
          || !Objects.equals(merchant.kind(), c.getString("merchant"))
          || !merchant.nearScholar(player)) {
        message(player, "&eMeet the matching scholar to accept this contract.");
        return;
      }
      if (!p.activeContract.isEmpty()) {
        message(player, "&eFinish or abandon your active contract first.");
        return;
      }
      long last = p.contractClaims.getOrDefault(args[1], 0L);
      long cooldown =
          (long) (SpellSpec.bounded(c.getDouble("repeat-hours", 24), 24, 0, 720) * 3600000);
      if (last > 0 && (cooldown == 0 || System.currentTimeMillis() - last < cooldown)) {
        message(player, "&eThat contract is complete or still on cooldown.");
        return;
      }
      change(
          player,
          n -> {
            n.activeContract = args[1];
            n.contractProgress.clear();
          },
          () -> {
            message(
                player,
                "&aAccepted: "
                    + c.getString("name", args[1])
                    + ". "
                    + c.getString("description", ""));
            contracts(player, new String[0]);
          });
      return;
    }
    if (args[0].equals("claim")) {
      var c = contract(p.activeContract);
      if (c == null
          || !MagicProgression.has(p.contractProgress, MagicProgression.costs(c, "objectives"))) {
        message(player, "&eYour contract is not complete. /quests list");
        return;
      }
      var requirements = MagicProgression.costs(c, "objectives");
      if (requirements.isEmpty()) {
        message(player, "&cContract has no objectives; ask an admin.");
        return;
      }
      int reward = Math.max(0, Math.min(100, c.getInt("research", 8)));
      var shards = MagicProgression.costs(c, "shards");
      String unlock = c.getString("unlock", "");
      if (!unlock.isEmpty() && !spells.containsKey(unlock)) {
        message(player, "&cConfigured reward spell is unavailable.");
        return;
      }
      change(
          player,
          n -> {
            n.research = Math.min(100000, n.research + reward);
            shards.forEach(
                (key, value) -> n.shards.merge(key, value, (x, y) -> Math.min(100000, x + y)));
            if (!unlock.isEmpty()) n.spells.putIfAbsent(unlock, 1);
            String duplicate = c.getString("duplicate-spell", "");
            if (spells.containsKey(duplicate) && n.spells.containsKey(duplicate))
              n.duplicates.merge(duplicate, 1, (a, b) -> Math.min(100000, a + b));
            n.contractClaims.put(n.activeContract, System.currentTimeMillis());
            n.activeContract = "";
            n.contractProgress.clear();
          },
          () ->
              message(
                  player,
                  "&aContract complete: +"
                      + reward
                      + " research, shards "
                      + shards
                      + (unlock.isEmpty() ? "" : "; unlocked " + unlock)
                      + "; duplicate credit: "
                      + c.getString("duplicate-spell", "none")
                      + "."));
      return;
    }
    if (args[0].equals("chapter")) {
      quest(player);
      return;
    }
    message(player, "&7/quests <list|accept id|claim|abandon|chapter>");
  }

  @EventHandler(priority = EventPriority.MONITOR)
  public void kill(org.bukkit.event.entity.EntityDeathEvent event) {
    Player killer = event.getEntity().getKiller();
    if (killer != null && canPlay(killer) && event.getEntity() instanceof org.bukkit.entity.Enemy)
      objective(killer, "KILL:" + event.getEntityType().name());
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void fishing(PlayerFishEvent e) {
    if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH && canPlay(e.getPlayer()))
      objective(e.getPlayer(), "FISH");
  }

  public void quest(Player player) {
    MagicProfile p = profile(player);
    if (p == null) return;
    if (!p.started) {
      message(
          player,
          "&dA Dusty Traveller: find the Hedge Mage. /encounters shows active discoveries.");
      return;
    }
    var chapter = chapter(p);
    if (chapter == null) {
      message(player, "&7No active chapter. Keep exploring to discover new spells.");
      return;
    }
    int goal = questGoal(p), reward = Math.max(0, Math.min(100, chapter.getInt("research", 8)));
    boolean repeat = chapter.getBoolean("repeatable");
    message(
        player,
        "&d"
            + chapter.getString("name", "Field Research")
            + ": &f"
            + chapter.getString("description", "")
            + " &7"
            + p.questProgress
            + "/"
            + goal);
    if (repeat && System.currentTimeMillis() - p.lastQuestAt < 86400000L) {
      message(player, "&7This research can be repeated 24 hours after your last claim.");
      return;
    }
    if (p.questProgress < goal) return;
    change(
        player,
        next -> {
          next.research = Math.min(100000, next.research + reward);
          if (!repeat) next.questStage++;
          next.questProgress = 0;
          if (repeat) next.lastQuestAt = System.currentTimeMillis();
        },
        () ->
            message(
                player,
                "&aQuest complete! +" + reward + " research. Your journal has been updated."));
  }

  private void spellInfo(Player player, String id) {
    MagicProfile p = profile(player);
    SpellSpec spell = spells.get(id);
    if (p == null || spell == null) return;
    int tier = p.spells.getOrDefault(id, 0);
    message(
        player,
        "&d"
            + spell.name()
            + " — "
            + (tier == 0 ? "locked" : "Tier " + tier)
            + ", mana "
            + spell.tierMana(
                Math.max(1, tier), config.getDouble("mana.cost-reduction-per-tier", .05))
            + ", cooldown "
            + spell.cooldown() / 1000.0
            + "s, damage "
            + spell.tierDamage(Math.max(1, tier))
            + ", range "
            + spell.range()
            + " blocks. Element: "
            + MagicProgression.element(id));
    message(
        player,
        "&7"
            + config.getString(
                "spells." + id + ".description",
                "Prepare in /grimoire; cast while holding a focus."));
  }

  private void wand(Player player) {
    MagicProfile p = profile(player);
    if (p == null) return;
    if (p.wand) {
      if (Arrays.stream(player.getInventory().getContents())
          .anyMatch(i -> "wand".equals(kind(i)))) {
        message(player, "&7Your wand is already in your inventory.");
        return;
      }
      if (player.getInventory().firstEmpty() < 0) {
        message(player, "&eFree one slot for your wand.");
        return;
      }
      player
          .getInventory()
          .addItem(item("wand", "&d✦ Astral Focus Wand", Material.BLAZE_ROD, 71030));
      return;
    }
    int price = Math.max(0, config.getInt("wand.research", 100));
    if (p.slots < maximumSlots()
        || p.spells.size() < Math.max(1, config.getInt("wand.minimum-spells", 5))
        || p.research < price) {
      message(
          player,
          "&eWand binding requires 5 prepared slots, "
              + config.getInt("wand.minimum-spells", 5)
              + " learned spells, "
              + price
              + " research, $"
              + config.getDouble("wand.currency", 500000)
              + " and shards "
              + MagicProgression.costs(config, "wand.shards")
              + ".");
      return;
    }
    var merchant = plugin.getModuleManager().getModule("world-encounters");
    if (!(merchant instanceof net.rocky.coresuite.module.worldencounters.WorldEncountersModule m)
        || !m.nearEnchanter(player)) {
      message(player, "&eVisit the Mystic Enchanter to bind your wand.");
      return;
    }
    purchaseUpgrade(player, "wand", price, config.getDouble("wand.currency", 500000));
  }

  private int maximumSlots() {
    return Math.max(2, Math.min(5, config.getInt("loadout.maximum-slots", 5)));
  }

  private boolean upgradeLocation(Player player) {
    if (!config.getBoolean("enchanter.require-for-upgrades", true)) return true;
    var module = plugin.getModuleManager().getModule("world-encounters");
    if (module instanceof net.rocky.coresuite.module.worldencounters.WorldEncountersModule merchant
        && merchant.nearEnchanter(player)) return true;
    message(player, "&eVisit the Mystic Enchanter to upgrade spells or bind pages.");
    return false;
  }

  private void upgrade(Player player, String spell) {
    if (!upgradeLocation(player)) return;
    MagicProfile p = profile(player);
    if (p == null) return;
    if (!spells.containsKey(spell)) {
      message(player, "&cThat spell is currently unavailable.");
      return;
    }
    int tier = p.spells.getOrDefault(spell, 0);
    if (tier < 1 || tier >= 3) {
      message(player, "&eChoose an unlocked spell below Tier III.");
      return;
    }
    int price =
        Math.max(
            1,
            config.getInt(
                "spells." + spell + ".upgrade-tier-" + (tier + 1) + ".research",
                config.getInt("upgrades.tier-" + (tier + 1) + "-research", tier == 1 ? 12 : 30)));
    if (p.research < price) {
      message(player, "&eUpgrade requires " + price + " research.");
      return;
    }
    purchaseUpgrade(
        player,
        "spell:" + spell,
        price,
        config.getDouble(
            "spells." + spell + ".upgrade-tier-" + (tier + 1) + ".currency",
            config.getDouble(
                "upgrades.tier-" + (tier + 1) + "-currency", tier == 1 ? 100000 : 350000)));
  }

  private void bind(Player player) {
    if (!upgradeLocation(player)) return;
    MagicProfile p = profile(player);
    if (p == null || p.slots >= maximumSlots()) return;
    int price =
        Math.max(1, config.getInt("binding.slot-" + (p.slots + 1) + "-research", p.slots * 15));
    if (p.research < price) {
      message(player, "&ePage binding requires " + price + " research.");
      return;
    }
    purchaseUpgrade(
        player,
        "page",
        price,
        config.getDouble(
            "binding.slot-" + (p.slots + 1) + "-currency", 100000 * Math.pow(2, p.slots - 2)));
  }

  /**
   * Vault cannot share a SQL transaction. Persist an intent first; never replay an uncertain debit.
   */
  private void purchaseUpgrade(Player player, String upgrade, int research, double cash) {
    MagicProfile p = profile(player);
    if (p == null) return;
    if (p.pendingUpgrade != null) {
      message(
          player,
          "&eAn earlier purchase needs review. Contact an admin; you will not be charged again.");
      return;
    }
    Map<String, Integer> shardCost = new HashMap<>(),
        materials = new HashMap<>(),
        fishCost = new HashMap<>();
    String duplicate = "", stock = "";
    int duplicates = 0, stockLimit = 0;
    if (upgrade.startsWith("spell:")) {
      String id = upgrade.substring(6);
      int target = p.spells.getOrDefault(id, 1) + 1;
      String path = "spells." + id + ".upgrade-tier-" + target + ".";
      int shardCount = Math.max(0, config.getInt(path + "shards", target == 2 ? 5 : 15));
      shardCost.put(MagicProgression.element(id), shardCount);
      duplicate = id;
      duplicates = Math.max(0, config.getInt(path + "duplicates", target == 2 ? 1 : 2));
    } else if (upgrade.startsWith("inscribe:"))
      shardCost.putAll(
          MagicProgression.costs(config, "inscription." + upgrade.substring(9) + ".shards"));
    else if (upgrade.equals("wand"))
      shardCost.putAll(MagicProgression.costs(config, "wand.shards"));
    else if (upgrade.equals("page")) {
      int n = Math.max(0, config.getInt("binding.slot-" + (p.slots + 1) + "-arcane-shards", 0));
      if (n > 0) shardCost.put("arcane", n);
    } else if (upgrade.startsWith("learn:")) {
      var merchant =
          (net.rocky.coresuite.module.worldencounters.WorldEncountersModule)
              plugin.getModuleManager().getModule("world-encounters");
      String id = upgrade.substring(6);
      if (merchant == null || !merchant.offers(player, id)) return;
      materials.putAll(merchant.materialCost());
      stock = merchant.stockKey();
      stockLimit = merchant.stockLimit();
      shardCost.putAll(merchant.shardCost());
      fishCost.putAll(merchant.fishCost());
    }
    if (!MagicProgression.has(p.shards, shardCost)
        || p.duplicates.getOrDefault(duplicate, 0) < duplicates
        || !MagicProgression.hasMaterials(player.getInventory().getStorageContents(), materials)) {
      message(
          player,
          "&eRequires shards "
              + shardCost
              + ", duplicate credits "
              + duplicates
              + ", materials "
              + materials
              + ". /magic shards");
      return;
    }
    if (!fishCost.isEmpty() && fishPlan(player, fishCost) == null) {
      message(
          player,
          "&eRequires custom fish " + fishCost + " in inventory or unlocked fish-bag pages.");
      return;
    }
    final String savedDuplicate = duplicate, savedStock = stock;
    final int savedDuplicates = duplicates, savedLimit = stockLimit;
    cash = SpellSpec.bounded(cash, 100000, 0, 100000000);
    final double fee = cash;
    var economy = plugin.getEconomyManager();
    if (fee > 0
        && (economy == null || !economy.isAvailable() || economy.getBalance(player) < fee)) {
      message(
          player,
          "&eRequires $"
              + String.format("%,.0f", fee)
              + " and "
              + research
              + " research; economy must be available.");
      return;
    }
    change(
        player,
        next -> {
          next.pendingUpgrade = upgrade;
          next.pendingResearch = research;
          next.pendingCash = fee;
          next.pendingShards.putAll(shardCost);
          next.pendingMaterials.putAll(materials);
          next.pendingFish.putAll(fishCost);
          next.pendingDuplicate = savedDuplicate;
          next.pendingDuplicateCount = savedDuplicates;
          next.pendingStock = savedStock;
          next.pendingStockLimit = savedLimit;
        },
        () -> {
          if (player.getOpenInventory().getTopInventory().getHolder()
              instanceof net.rocky.coresuite.module.customfishing.FishBagManager.BagHolder)
            player.closeInventory();
          if (upgrade.startsWith("learn:")) {
            var m = plugin.getModuleManager().getModule("world-encounters");
            if (!(m
                    instanceof
                    net.rocky.coresuite.module.worldencounters.WorldEncountersModule merchant)
                || !merchant.offers(player, upgrade.substring(6))
                || !merchant.stockKey().equals(savedStock)) {
              change(
                  player,
                  MagicProgression::clearPending,
                  () ->
                      message(
                          player, "&eScholar expired or moved out of reach; no payment taken."));
              return;
            }
          }
          MagicFishPayment.Result fishPayment =
              fishCost.isEmpty() ? null : fishPlan(player, fishCost);
          if ((!fishCost.isEmpty() && fishPayment == null)
              || !MagicProgression.hasMaterials(
                  player.getInventory().getStorageContents(), materials)) {
            change(
                player,
                MagicProgression::clearPending,
                () ->
                    message(
                        player,
                        "&eMaterials changed; purchase cancelled without payment. Reserved merchant"
                            + " stock stays used."));
            return;
          }
          if (fee > 0 && !economy.withdraw(player, fee)) {
            // The provider did not confirm payment. Keep the intent for manual review rather than
            // risk a second debit.
            message(
                player,
                "&cPayment was not confirmed. Ask an admin to review your pending magic purchase.");
            plugin
                .getLogger()
                .warning(
                    "[Grimoire] Pending purchase requires review: "
                        + player.getUniqueId()
                        + " "
                        + upgrade
                        + " $"
                        + fee);
            return;
          }
          player
              .getInventory()
              .setStorageContents(
                  MagicProgression.debitMaterials(
                      fishPayment == null
                          ? player.getInventory().getStorageContents()
                          : fishPayment.inventory(),
                      materials));
          player.saveData();
          Runnable finishPayment =
              () ->
                  change(
                      player,
                      GrimoireModule::completeUpgrade,
                      () -> {
                        message(
                            player,
                            "&aUpgrade completed. Paid $"
                                + String.format("%,.0f", fee)
                                + " and "
                                + research
                                + " research.");
                        open(player);
                      });
          if (fishPayment != null && fishPayment.bagChanged()) {
            var fishing =
                (net.rocky.coresuite.module.customfishing.CustomFishingModule)
                    plugin.getModuleManager().getModule("custom-fishing");
            fishing.getFishBagManager().updateBag(player.getUniqueId(), fishPayment.bag());
            fishing
                .getFishBagManager()
                .saveBag(
                    player.getUniqueId(),
                    finishPayment,
                    () ->
                        message(
                            player,
                            "&cFish payment could not be confirmed. Your pending purchase needs"
                                + " admin review; it will not charge again."));
          } else finishPayment.run();
        });
  }

  private MagicFishPayment.Result fishPlan(Player player, Map<String, Integer> cost) {
    var m = plugin.getModuleManager().getModule("custom-fishing");
    if (!(m instanceof net.rocky.coresuite.module.customfishing.CustomFishingModule fishing)
        || !m.isEnabled()) return null;
    var data = fishing.getPlayerData(player.getUniqueId());
    if (data == null || fishing.getFishBagManager().getBag(player.getUniqueId()) == null)
      return null;
    return MagicFishPayment.plan(
        player.getInventory().getStorageContents(),
        fishing.getFishBagManager().getBag(player.getUniqueId()),
        Math.max(1, Math.min(10, data.bagPages)) * 45,
        fishing.getSpeciesKey(),
        cost);
  }

  static void completeUpgrade(MagicProfile p) {
    MagicProgression.complete(p);
  }

  private static final class MagicMenu implements InventoryHolder {
    final UUID owner;
    final Map<Integer, Runnable> actions = new HashMap<>();
    Inventory inventory;

    MagicMenu(UUID owner) {
      this.owner = owner;
    }

    @Override
    public Inventory getInventory() {
      return inventory;
    }
  }

  private ItemStack button(Material material, String name, String... lines) {
    var item = new ItemStack(material);
    var meta = item.getItemMeta();
    meta.displayName(TextUtil.colorize(name));
    meta.lore(Arrays.stream(lines).map(TextUtil::colorize).toList());
    item.setItemMeta(meta);
    return item;
  }

  public void open(Player player) {
    MagicProfile p = profile(player);
    if (p == null) return;
    var menu = new MagicMenu(player.getUniqueId());
    menu.inventory = Bukkit.createInventory(menu, 54, "Astral Grimoire");
    int index = 0;
    for (SpellSpec spell : spells.values()) {
      if (index >= 18) break;
      int slot = index++;
      int tier = p.spells.getOrDefault(spell.id(), 0);
      menu.inventory.setItem(
          slot,
          button(
              spell.icon(),
              "&d" + spell.name(),
              tier == 0 ? "&8Undiscovered" : "&7Tier " + tier,
              "&bMana: "
                  + spell.tierMana(
                      Math.max(1, tier), config.getDouble("mana.cost-reduction-per-tier", .05))
                  + " | Cooldown: "
                  + spell.cooldown() / 1000.0
                  + "s",
              p.prepared.contains(spell.id())
                  ? "&aPrepared — click to remove"
                  : "&7Click to prepare"));
      menu.actions.put(
          slot,
          () ->
              change(
                  player,
                  next -> {
                    if (!next.spells.containsKey(spell.id())) return;
                    if (!next.prepared.remove(spell.id()) && next.prepared.size() < next.slots)
                      next.prepared.add(spell.id());
                    next.selected = 0;
                  },
                  () -> open(player)));
      if (tier > 0) {
        menu.inventory.setItem(
            slot + 27,
            button(
                Material.EXPERIENCE_BOTTLE,
                "&6Upgrade " + spell.name(),
                "&7Tier " + tier + "/3",
                "&7Research: "
                    + config.getInt(
                        "spells." + spell.id() + ".upgrade-tier-" + (tier + 1) + ".research",
                        config.getInt(
                            "upgrades.tier-" + (tier + 1) + "-research", tier == 1 ? 12 : 30)),
                "&6Cost: $"
                    + String.format(
                        "%,.0f",
                        config.getDouble(
                            "spells." + spell.id() + ".upgrade-tier-" + (tier + 1) + ".currency",
                            config.getDouble(
                                "upgrades.tier-" + (tier + 1) + "-currency",
                                tier == 1 ? 100000 : 350000))),
                "&7Element: "
                    + MagicProgression.element(spell.id())
                    + " | Shards: "
                    + config.getInt(
                        "spells." + spell.id() + ".upgrade-tier-" + (tier + 1) + ".shards",
                        tier == 1 ? 5 : 15),
                "&7Duplicate credits: "
                    + config.getInt(
                        "spells." + spell.id() + ".upgrade-tier-" + (tier + 1) + ".duplicates",
                        tier == 1 ? 1 : 2),
                tier >= 3 ? "&aMaximum tier" : "&7Visit the Enchanter; click to upgrade"));
        menu.actions.put(slot + 27, () -> upgrade(player, spell.id()));
      }
    }
    menu.inventory.setItem(
        45,
        button(
            Material.PAPER,
            "&dBind a new page",
            "&7Slots: " + p.slots + "/" + maximumSlots(),
            "&7Available research: " + p.research,
            "&7Cost: "
                + config.getInt("binding.slot-" + (p.slots + 1) + "-research", p.slots * 15)
                + " research + $"
                + String.format(
                    "%,.0f",
                    config.getDouble(
                        "binding.slot-" + (p.slots + 1) + "-currency",
                        100000 * Math.pow(2, p.slots - 2)))));
    menu.actions.put(45, () -> bind(player));
    menu.inventory.setItem(
        46,
        button(
            Material.BLAZE_ROD,
            "&dAstral Wand",
            p.wand
                ? "&aBound — click to retrieve"
                : "&7Endgame focus: /magic wand for requirements",
            "&7Owned wands grant +10% mana recovery",
            "&7Carry it to use /magic cast without holding a book"));
    menu.actions.put(46, () -> wand(player));
    menu.inventory.setItem(
        48,
        button(
            Material.AMETHYST_SHARD,
            "&bMagic resources",
            "&7Shards: " + p.shards,
            "&7Duplicates: " + p.duplicates,
            "&7Click to collect boss rewards"));
    menu.actions.put(48, () -> collectResearch(player));
    var encounters = plugin.getModuleManager().getModule("world-encounters");
    if (encounters
        instanceof net.rocky.coresuite.module.worldencounters.WorldEncountersModule merchant)
      for (SpellSpec available : spells.values())
        if (merchant.offers(player, available.id())) {
          menu.inventory.setItem(
              47,
              button(
                  available.icon(),
                  "&bStudy " + available.name(),
                  "&7Nearby scholar's lesson",
                  "&6Cost: "
                      + config.getInt("spells." + available.id() + ".research-cost", 6)
                      + " research",
                  "&7Materials: " + merchant.materialCost(),
                  "&7Shards: " + merchant.shardCost(),
                  "&7Custom fish (inventory or bag): " + merchant.fishCost(),
                  "&6Money: $" + merchant.lessonCurrency(),
                  "&7Shared stock limit: " + merchant.stockLimit(),
                  "&7Click to learn permanently"));
          menu.actions.put(47, () -> study(player, available.id()));
          break;
        }
    menu.inventory.setItem(
        49,
        button(
            Material.WRITABLE_BOOK,
            "&bQuest Journal",
            "&7View objectives and claim completed quests"));
    menu.actions.put(49, () -> openJournal(player));
    menu.inventory.setItem(
        53,
        button(
            Material.ARROW,
            "&bSelect next spell",
            "&7Current: " + Objects.toString(p.selectedSpell(), "none")));
    menu.actions.put(
        53,
        () -> {
          cycle(player);
          open(player);
        });
    player.openInventory(menu.inventory);
  }

  @EventHandler
  public void click(InventoryClickEvent e) {
    if (!(e.getView().getTopInventory().getHolder() instanceof MagicMenu m)) return;
    e.setCancelled(true);
    if (e.getWhoClicked() instanceof Player p && m.owner.equals(p.getUniqueId()) && canPlay(p)) {
      Runnable action = m.actions.get(e.getRawSlot());
      if (action != null) action.run();
    }
  }

  @EventHandler
  public void craft(PrepareItemCraftEvent e) {
    for (ItemStack i : e.getInventory().getMatrix())
      if (kind(i) != null) {
        e.getInventory().setResult(null);
        break;
      }
  }

  @EventHandler
  public void anvil(PrepareAnvilEvent e) {
    for (ItemStack i : e.getInventory().getContents())
      if (kind(i) != null) {
        e.setResult(null);
        break;
      }
  }

  @EventHandler
  public void grind(PrepareGrindstoneEvent e) {
    for (ItemStack i : e.getInventory().getContents())
      if (kind(i) != null) {
        e.setResult(null);
        break;
      }
  }

  @EventHandler
  public void drag(InventoryDragEvent e) {
    if (e.getView().getTopInventory().getHolder() instanceof MagicMenu) e.setCancelled(true);
  }

  @Override
  public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
    if (!running) {
      message(sender, "&cMagic is disabled.");
      return true;
    }
    if (cmd.getName().equals("magicadmin")) return admin(sender, args);
    if (!(sender instanceof Player p) || !sender.hasPermission("coresuite.grimoire.use"))
      return true;
    if (!canPlay(p)) return true;
    String sub = args.length == 0 ? "loadout" : args[0].toLowerCase(Locale.ROOT);
    switch (sub) {
      case "loadout", "spells" -> open(p);
      case "book" -> {
        MagicProfile state = profile(p);
        if (state != null && state.started) giveBook(p);
        else quest(p);
      }
      case "next" -> cycle(p);
      case "cast" -> cast(p);
      case "quests" -> contracts(p, Arrays.copyOfRange(args, 1, args.length));
      case "toggle" ->
          change(
              p,
              n -> n.magicEnabled = !n.magicEnabled,
              () ->
                  message(
                      p,
                      "&dMagic "
                          + (profiles.get(p.getUniqueId()).magicEnabled ? "enabled" : "paused")));
      case "shards" -> {
        MagicProfile state = profile(p);
        if (state != null)
          message(
              p,
              "&dShards: "
                  + state.shards
                  + " | Duplicate credits: "
                  + state.duplicates
                  + " | Research: "
                  + state.research
                  + ". /magic research claims boss rewards.");
      }
      case "wand" -> wand(p);
      case "inscribe" -> {
        if (args.length < 2
            || !spells.containsKey(args[1])
            || !config.isConfigurationSection("inscription." + args[1])) return true;
        var merchant = plugin.getModuleManager().getModule("world-encounters");
        if (!(merchant
                instanceof net.rocky.coresuite.module.worldencounters.WorldEncountersModule m)
            || !m.nearEnchanter(p)) {
          message(p, "&eVisit the Mystic Enchanter to inscribe a signature spell.");
          return true;
        }
        String path = "inscription." + args[1] + ".";
        MagicProfile state = profile(p);
        int research = Math.max(0, config.getInt(path + "research", 20));
        if (state == null || state.research < research) {
          message(p, "&eInscription requires " + research + " research.");
          return true;
        }
        purchaseUpgrade(
            p, "inscribe:" + args[1], research, config.getDouble(path + "currency", 100000));
      }
      case "info" -> {
        if (args.length > 1) spellInfo(p, args[1]);
      }
      case "research" -> collectResearch(p);
      case "study" -> {
        if (args.length > 1) study(p, args[1]);
      }
      case "upgrade" -> {
        if (args.length > 1) upgrade(p, args[1]);
      }
      case "bind" -> bind(p);
      default -> message(p, "&7/magic <loadout|book|next|cast|quests|study|upgrade|bind>");
    }
    return true;
  }

  private boolean admin(CommandSender sender, String[] args) {
    if (!sender.hasPermission("coresuite.magic.admin")) return true;
    if (args.length < 2) {
      message(
          sender,
          "&7/magicadmin <start|research|unlock|tome|pending|reconcile> <player> [amount/spell]");
      return true;
    }
    Player p = Bukkit.getPlayerExact(args[1]);
    if (p == null) {
      message(sender, "&cPlayer must be online.");
      return true;
    }
    switch (args[0]) {
      case "mana", "slots", "tier", "shards", "duplicates" -> {
        if (args.length < 3) return true;
        String action = args[0];
        try {
          if (action.equals("mana")) {
            double value = Double.parseDouble(args[2]);
            if (!Double.isFinite(value)) throw new IllegalArgumentException();
            change(
                p,
                n -> n.mana = Math.max(0, Math.min(maximumMana(), value)),
                () -> message(sender, "&aMana updated."));
          } else if (action.equals("slots")) {
            int value = Integer.parseInt(args[2]);
            if (value < 2 || value > 5) throw new IllegalArgumentException();
            change(
                p,
                n -> {
                  n.slots = value;
                  while (n.prepared.size() > value) n.prepared.removeLast();
                },
                () -> message(sender, "&aSlots updated."));
          } else {
            if (args.length < 4) return true;
            int value = Integer.parseInt(args[3]);
            if (value < 0 || value > 100000) throw new IllegalArgumentException();
            String key = args[2];
            if (action.equals("shards")
                && !MagicProgression.ELEMENTS.contains(key)
                && !Set.of("zoltraak-fragment", "hearth-fragment").contains(key))
              throw new IllegalArgumentException();
            if (!action.equals("shards") && !spells.containsKey(key))
              throw new IllegalArgumentException();
            if (action.equals("tier") && (value < 1 || value > 3))
              throw new IllegalArgumentException();
            change(
                p,
                n -> {
                  if (n.pendingUpgrade != null)
                    throw new IllegalStateException("Resolve pending purchase first");
                  if (action.equals("tier")) n.spells.put(key, value);
                  else if (action.equals("shards")) n.shards.put(key, value);
                  else n.duplicates.put(key, value);
                },
                () -> message(sender, "&aSaved " + action + " " + key + " = " + value + "."));
          }
        } catch (IllegalArgumentException error) {
          message(sender, "&cInvalid value or ID.");
        }
      }
      case "wand" ->
          change(
              p,
              n -> n.wand = true,
              () -> {
                wand(p);
                message(sender, "&aWand entitlement granted.");
              });
      case "reset" -> {
        if (args.length < 3 || !args[2].equals("confirm")) {
          message(
              sender,
              "&eUse /magicadmin reset <player> confirm to erase magic progression. Spent tome"
                  + " seals stay spent.");
          return true;
        }
        change(
            p,
            n -> {
              if (n.pendingUpgrade != null)
                throw new IllegalStateException("Resolve pending purchases before reset");
              n.spells.clear();
              n.prepared.clear();
              n.shards.clear();
              n.duplicates.clear();
              n.contractClaims.clear();
              n.contractProgress.clear();
              n.activeContract = "";
              n.wand = false;
              n.started = false;
              n.research = 0;
              n.questStage = 0;
              n.questProgress = 0;
              n.slots = 2;
              n.mana = maximumMana();
              n.discoveries.clear();
              n.cooldowns.clear();
              n.selected = 0;
              n.lastQuestAt = 0;
              n.lastCast = 0;
              n.globalReady = 0;
              n.magicEnabled = true;
            },
            () -> {
              pendingProgress.remove(p.getUniqueId());
              message(
                  sender,
                  "&aMagic progression reset. Already redeemed tome seals remain unusable.");
            });
      }
      case "start" -> discover(p, "admin-introduction", "mana-dart");
      case "pending" -> {
        MagicProfile state = profile(p);
        if (state != null)
          message(
              sender,
              "&ePending: "
                  + state.pendingUpgrade
                  + " | $"
                  + state.pendingCash
                  + " | research "
                  + state.pendingResearch
                  + " | fish "
                  + state.pendingFish
                  + " | materials "
                  + state.pendingMaterials
                  + " | shards "
                  + state.pendingShards
                  + " | duplicate credits "
                  + state.pendingDuplicateCount
                  + ". Verify the economy transaction before /magicadmin reconcile <player>"
                  + " paid|cancel.");
      }
      case "reconcile" -> {
        if (args.length < 3) return true;
        if (args[2].equals("paid"))
          change(
              p,
              GrimoireModule::completeUpgrade,
              () -> message(sender, "&aPending upgrade applied without another charge."));
        else if (args[2].equals("cancel"))
          change(
              p,
              next -> {
                MagicProgression.clearPending(next);
              },
              () ->
                  message(
                      sender,
                      "&aPending intent cleared. No money was moved; handle any verified refund"
                          + " separately."));
      }

      case "research" -> {
        try {
          int amount = Math.max(0, Math.min(100000, Integer.parseInt(args[2])));
          change(
              p,
              next -> next.research = Math.min(100000, next.research + amount),
              () -> message(sender, "&aResearch granted."));
        } catch (RuntimeException e) {
          message(sender, "&cSupply a valid amount.");
        }
      }
      case "unlock" -> {
        if (args.length > 2 && spells.containsKey(args[2]))
          change(
              p, next -> next.spells.put(args[2], 1), () -> message(sender, "&aSpell unlocked."));
      }
      case "tome" -> {
        if (args.length < 3 || !spells.containsKey(args[2]) || p.getInventory().firstEmpty() < 0) {
          message(sender, "&cChoose a valid spell and free an inventory slot.");
          return true;
        }
        String token = UUID.randomUUID().toString(), spell = args[2];
        long epoch = generation;
        MagicStore current = store;
        plugin
            .getDatabaseManager()
            .executeAsync(
                () -> {
                  try {
                    current.mint(token, spell);
                    main(
                        epoch,
                        () -> {
                          if (!p.isOnline() || p.getInventory().firstEmpty() < 0) {
                            message(
                                sender,
                                "&eTome not delivered; player left or inventory filled. Retry.");
                            return;
                          }
                          var tome =
                              item(
                                  "tome:" + spell,
                                  "&dSealed Tome: " + spells.get(spell).name(),
                                  Material.BOOK,
                                  tomeModel(spell));
                          var meta = tome.getItemMeta();
                          meta.getPersistentDataContainer()
                              .set(tokenKey, PersistentDataType.STRING, token);
                          tome.setItemMeta(meta);
                          p.getInventory().addItem(tome);
                          message(sender, "&aSealed tome delivered.");
                        });
                  } catch (Exception e) {
                    failure("mint tome", e);
                  }
                });
      }
      default -> message(sender, "&cUnknown admin action.");
    }
    return true;
  }

  @Override
  public List<String> onTabComplete(
      CommandSender sender, Command command, String alias, String[] args) {
    List<String> values = List.of();
    if (command.getName().equals("magicadmin")) {
      if (!sender.hasPermission("coresuite.magic.admin")) return List.of();
      if (args.length == 1)
        values =
            List.of(
                "start",
                "research",
                "unlock",
                "tome",
                "pending",
                "reconcile",
                "mana",
                "slots",
                "tier",
                "shards",
                "duplicates",
                "wand",
                "reset");
      else if (args.length == 2)
        values =
            Bukkit.getOnlinePlayers().stream()
                .filter(p -> !(sender instanceof Player viewer) || viewer.canSee(p))
                .map(Player::getName)
                .toList();
      else if (args.length == 3)
        values =
            args[0].equals("shards")
                ? List.of("fire", "ice", "arcane", "nature", "zoltraak-fragment", "hearth-fragment")
                : args[0].equals("reconcile")
                    ? List.of("paid", "cancel")
                    : new ArrayList<>(spells.keySet());
      else if (args.length == 4 && args[0].equals("tier")) values = List.of("1", "2", "3");
    } else if (args.length == 1)
      values =
          List.of(
              "loadout",
              "book",
              "next",
              "cast",
              "quests",
              "research",
              "study",
              "upgrade",
              "bind",
              "shards",
              "info",
              "toggle",
              "wand",
              "inscribe");
    else if (args.length == 2) values = new ArrayList<>(spells.keySet());
    String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
    return values.stream().filter(v -> v.startsWith(prefix)).toList();
  }
}
