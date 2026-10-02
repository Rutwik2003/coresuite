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
  private final Map<String, SpellSpec> spells = new LinkedHashMap<>();
  private MagicStore store;
  private SpellEngine engine;
  private boolean running, storageReady;
  private long generation;
  private NamespacedKey itemKey, tokenKey;
  private int seconds;

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
    profiles.clear();
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
      MagicProfile p = profiles.get(player.getUniqueId());
      if (!p.started || p.busy || !canPlay(player)) continue;
      p.regenerate(
          System.currentTimeMillis(),
          maximumMana(),
          SpellSpec.bounded(config.getDouble("mana.regeneration-per-second", 2), 2, 0, 10));
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

  boolean raidAllies(Player a, Player b) {
    var m = plugin.getModuleManager().getModule("world-bosses");
    return m instanceof WorldBossesModule boss
        && boss.isEnabled()
        && boss.areRaidAllies(a.getUniqueId(), b.getUniqueId());
  }

  private String kind(ItemStack item) {
    if (item == null || !item.hasItemMeta()) return null;
    return item.getItemMeta().getPersistentDataContainer().get(itemKey, PersistentDataType.STRING);
  }

  private boolean equipped(Player p) {
    return "grimoire".equals(kind(p.getInventory().getItemInMainHand()))
        || "grimoire".equals(kind(p.getInventory().getItemInOffHand()));
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
                  if (token == null) current.save(id, old.revision, payload);
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
    if (!"grimoire".equals(kind)) return;
    if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND
        && "grimoire".equals(kind(player.getInventory().getItemInMainHand()))) return;
    if (e.getAction().isRightClick()) {
      e.setCancelled(true);
      if (player.isSneaking()) open(player);
      else cast(player);
    } else if (e.getAction().isLeftClick() && player.isSneaking()) {
      e.setCancelled(true);
      cycle(player);
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
          if (p.spells.containsKey(spell)) p.research = Math.min(100000, p.research + 5);
          else p.spells.put(spell, 1);
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
                  + " research.");
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
    double cost = spell.mana();
    if (p.mana < cost || now < p.globalReady || now < p.cooldowns.getOrDefault(spell.id(), 0L)) {
      message(player, "&7Not enough mana, or the spell is recovering.");
      return;
    }
    int tier = p.spells.getOrDefault(spell.id(), 1);
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
          objective(player, "CAST");
          player
              .getWorld()
              .playSound(
                  player.getLocation(), "coresuite:magic.cast", SoundCategory.PLAYERS, .7f, 1.0f);
        });
  }

  public void awardBossResearch(UUID player, String encounter, int amount) {
    long epoch = generation;
    MagicStore current = store;
    plugin
        .getDatabaseManager()
        .executeAsync(
            () -> {
              try {
                current.award(encounter + ":" + player, player, amount);
                main(
                    epoch,
                    () -> {
                      Player online = Bukkit.getPlayer(player);
                      if (online != null)
                        message(
                            online,
                            "&dBoss research earned: +"
                                + amount
                                + ". Collect with /magic research.");
                    });
              } catch (Exception error) {
                failure("record boss research", error);
              }
            });
  }

  private void collectResearch(Player player) {
    MagicProfile old = profile(player);
    if (old == null) return;
    old.busy = true;
    long epoch = generation;
    MagicStore current = store;
    UUID id = player.getUniqueId();
    plugin
        .getDatabaseManager()
        .executeAsync(
            () -> {
              try {
                MagicProfile claimed = current.collect(id);
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
                    + " quests.");
          });
      return;
    }
    if (p.discoveries.contains(encounter)) {
      message(player, "&7You have already studied this encounter.");
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
              "&aEncounter studied: +3 research. Open /grimoire nearby to study the scholar's"
                  + " spell.");
        });
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
    change(
        player,
        next -> {
          next.research -= price;
          next.spells.put(spell, 1);
        },
        () ->
            message(
                player, "&aLearned " + spells.get(spell).name() + "! Prepare it in /grimoire."));
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
    MagicProfile p = profiles.get(player.getUniqueId());
    if (p == null
        || p.busy
        || !p.started
        || !questType(p).equals(type)
        || p.questProgress >= questGoal(p)) return;
    var chapter = chapter(p);
    if (chapter.getBoolean("repeatable") && System.currentTimeMillis() - p.lastQuestAt < 86400000L)
      return;
    change(
        player,
        next -> next.questProgress = Math.min(questGoal(next), next.questProgress + 1),
        () -> {});
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

  private void upgrade(Player player, String spell) {
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
            1, config.getInt("upgrades.tier-" + (tier + 1) + "-research", tier == 1 ? 12 : 30));
    if (p.research < price) {
      message(player, "&eUpgrade requires " + price + " research.");
      return;
    }
    purchaseUpgrade(
        player,
        "spell:" + spell,
        price,
        config.getDouble("upgrades.tier-" + (tier + 1) + "-currency", tier == 1 ? 100000 : 350000));
  }

  private void bind(Player player) {
    MagicProfile p = profile(player);
    if (p == null || p.slots >= 5) return;
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
        },
        () -> {
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
        });
  }

  static void completeUpgrade(MagicProfile p) {
    if (p.pendingUpgrade == null) return;
    if (p.research < p.pendingResearch)
      throw new IllegalStateException("Research changed during pending purchase");
    p.research -= p.pendingResearch;
    if (p.pendingUpgrade.equals("page")) p.slots = Math.min(5, p.slots + 1);
    else if (p.pendingUpgrade.startsWith("spell:"))
      p.spells.computeIfPresent(p.pendingUpgrade.substring(6), (id, tier) -> Math.min(3, tier + 1));
    p.pendingUpgrade = null;
    p.pendingResearch = 0;
    p.pendingCash = 0;
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
              "&bMana: " + spell.mana() + " | Cooldown: " + spell.cooldown() / 1000.0 + "s",
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
                        "upgrades.tier-" + (tier + 1) + "-research", tier == 1 ? 12 : 30),
                "&6Cost: $"
                    + String.format(
                        "%,.0f",
                        config.getDouble(
                            "upgrades.tier-" + (tier + 1) + "-currency",
                            tier == 1 ? 100000 : 350000)),
                "&7Click to purchase upgrade"));
        menu.actions.put(slot + 27, () -> upgrade(player, spell.id()));
      }
    }
    menu.inventory.setItem(
        45,
        button(
            Material.PAPER,
            "&dBind a new page",
            "&7Slots: " + p.slots + "/5",
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
    menu.actions.put(49, () -> quest(player));
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
      case "quests" -> quest(p);
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
                next.pendingUpgrade = null;
                next.pendingResearch = 0;
                next.pendingCash = 0;
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
                                  71002);
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
        values = List.of("start", "research", "unlock", "tome", "pending", "reconcile");
      else if (args.length == 2)
        values =
            Bukkit.getOnlinePlayers().stream()
                .filter(p -> !(sender instanceof Player viewer) || viewer.canSee(p))
                .map(Player::getName)
                .toList();
      else if (args.length == 3) values = new ArrayList<>(spells.keySet());
    } else if (args.length == 1)
      values =
          List.of(
              "loadout", "book", "next", "cast", "quests", "research", "study", "upgrade", "bind");
    else if (args.length == 2) values = new ArrayList<>(spells.keySet());
    String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
    return values.stream().filter(v -> v.startsWith(prefix)).toList();
  }
}
