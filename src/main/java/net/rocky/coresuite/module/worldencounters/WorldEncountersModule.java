package net.rocky.coresuite.module.worldencounters;

import java.util.*;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.integration.AuthenticationGate;
import net.rocky.coresuite.common.integration.BossProtectionGate;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.Module;
import net.rocky.coresuite.module.grimoire.GrimoireModule;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;

/** Temporary encounters use loaded terrain only and never leave persistent NPCs behind. */
public final class WorldEncountersModule extends Module
    implements Listener, CommandExecutor, TabCompleter {
  private Villager npc, enchanter;
  private String encounterId, kind, spell;
  private long expires, nextSpawn;
  private final Random random = new Random();
  private static final String TAG = "coresuite_encounter";
  private static final Map<String, String> SPELLS =
      Map.of(
          "hedge-mage",
          "mana-dart",
          "river-sage",
          "water-cannon",
          "illusionist",
          "dimension-door",
          "ancient-scholar",
          "zoltraak",
          "frost-pilgrim",
          "frost-lance",
          "hearthkeeper",
          "aura-of-the-hearth",
          "ember-scribe",
          "fireball",
          "greenwarden",
          "harvesters-touch");

  public WorldEncountersModule(CoreSuitePlugin plugin) {
    super(plugin, "WorldEncounters", "world-encounters");
  }

  @Override
  public void onEnable() {
    registerListener(this);
    var cmd = plugin.getCommand("encounters");
    if (cmd != null) {
      cmd.setExecutor(this);
      cmd.setTabCompleter(this);
    }
    for (World world : Bukkit.getWorlds())
      for (Entity entity : world.getEntities())
        if (entity.getScoreboardTags().contains(TAG)) entity.remove();
    restoreEnchanter();
    nextSpawn = System.currentTimeMillis() + 60000;
    registerTask(Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20, 20));
  }

  @Override
  public void onDisable() {
    close();
    if (enchanter != null) enchanter.remove();
    enchanter = null;
    var cmd = plugin.getCommand("encounters");
    if (cmd != null && cmd.getExecutor() == this) {
      cmd.setExecutor(null);
      cmd.setTabCompleter(null);
    }
  }

  private GrimoireModule magic() {
    var m = plugin.getModuleManager().getModule("grimoire");
    return m instanceof GrimoireModule g && g.isEnabled() ? g : null;
  }

  private void tick() {
    if (enchanter == null || !enchanter.isValid()) restoreEnchanter();
    long now = System.currentTimeMillis();
    if (npc != null) {
      if (!npc.isValid() || now >= expires) close();
      else if (now % 5000 < 1000)
        npc.getWorld()
            .spawnParticle(Particle.ENCHANT, npc.getLocation().add(0, 1, 0), 6, .4, .5, .4, .1);
      return;
    }
    if (now < nextSpawn || magic() == null) return;
    var players =
        Bukkit.getOnlinePlayers().stream()
            .filter(
                p ->
                    p.getGameMode() == GameMode.SURVIVAL
                        && AuthenticationGate.isAuthenticated(p)
                        && allowedWorld(p.getWorld())
                        && !magic().inArena(p.getLocation()))
            .toList();
    if (players.isEmpty()) return;
    Player anchor = players.get(random.nextInt(players.size()));
    String biome = anchor.getWorld().getBiome(anchor.getLocation()).name();
    List<String> kinds = new ArrayList<>();
    for (String candidate : SPELLS.keySet()) {
      String path = "merchants." + candidate + ".";
      if (!config.getBoolean(path + "enabled", true)) continue;
      List<String> biomes = config.getStringList(path + "biomes");
      if (!biomes.isEmpty() && !biomes.contains(biome)) continue;
      int weight = Math.max(0, Math.min(100, config.getInt(path + "weight", 10)));
      for (int i = 0; i < weight; i++) kinds.add(candidate);
    }
    if (kinds.isEmpty()) {
      nextSpawn = now + 60000;
      return;
    }
    String type = kinds.get(random.nextInt(kinds.size()));
    if (!spawn(anchor, type)) nextSpawn = now + 60000;
  }

  private boolean allowedWorld(World world) {
    return config.getStringList("worlds").contains(world.getName());
  }

  private boolean spawn(Player player, String type) {
    if (npc != null
        || !SPELLS.containsKey(type)
        || magic() == null
        || !allowedWorld(player.getWorld())) return false;
    World world = player.getWorld();
    Location base = player.getLocation(), destination = null;
    for (int attempt = 0; attempt < 32; attempt++) {
      int x = base.getBlockX() + random.nextInt(49) - 24,
          z = base.getBlockZ() + random.nextInt(49) - 24;
      if (!world.isChunkLoaded(x >> 4, z >> 4)) continue;
      int y = world.getHighestBlockYAt(x, z) + 1;
      Location at = new Location(world, x + .5, y, z + .5);
      Material floor = at.clone().subtract(0, 1, 0).getBlock().getType();
      if (!floor.isSolid()
          || floor.name().contains("LEAVES")
          || floor == Material.MAGMA_BLOCK
          || floor == Material.CACTUS) continue;
      if (!at.getBlock().isPassable()
          || !at.clone().add(0, 1, 0).getBlock().isPassable()
          || !world.getWorldBorder().isInside(at)
          || magic().inArena(at)
          || !BossProtectionGate.allows(at)) continue;
      destination = at;
      break;
    }
    if (destination == null) return false;
    kind = type;
    spell = SPELLS.get(type);
    encounterId = UUID.randomUUID().toString();
    npc =
        world.spawn(
            destination,
            Villager.class,
            v -> {
              v.setAI(false);
              v.setInvulnerable(true);
              v.setSilent(true);
              v.setCollidable(false);
              v.setPersistent(false);
              v.setRemoveWhenFarAway(false);
              v.addScoreboardTag(TAG);
              v.setProfession(Villager.Profession.LIBRARIAN);
              v.customName(TextUtil.colorize("&d✦ " + type.replace('-', ' ') + " ✦"));
              v.setCustomNameVisible(true);
            });
    expires =
        System.currentTimeMillis()
            + Math.max(2, Math.min(30, config.getInt("duration-minutes", 15))) * 60000L;
    nextSpawn = expires + Math.max(5, config.getInt("interval-minutes", 30)) * 60000L;
    Bukkit.broadcast(
        TextUtil.colorize(
            "&d✦ A traveling scholar has appeared in "
                + world.getName()
                + " near "
                + destination.getBlockX()
                + ", "
                + destination.getBlockZ()
                + ". &7/encounters"));
    return true;
  }

  private void restoreEnchanter() {
    if (!config.getBoolean("enchanter.enabled", false)) return;
    World world = Bukkit.getWorld(config.getString("enchanter.world", "world"));
    if (world == null) return;
    Location at =
        new Location(
            world,
            config.getDouble("enchanter.x"),
            config.getDouble("enchanter.y"),
            config.getDouble("enchanter.z"));
    if (!world.isChunkLoaded(at.getBlockX() >> 4, at.getBlockZ() >> 4)
        || !at.getBlock().isPassable()) return;
    enchanter =
        world.spawn(
            at,
            Villager.class,
            v -> {
              v.setAI(false);
              v.setInvulnerable(true);
              v.setSilent(true);
              v.setCollidable(false);
              v.setPersistent(false);
              v.addScoreboardTag("coresuite_enchanter");
              v.setProfession(Villager.Profession.LIBRARIAN);
              v.customName(TextUtil.colorize("&5✦ The Mystic Enchanter ✦"));
              v.setCustomNameVisible(true);
            });
  }

  public boolean nearEnchanter(Player player) {
    return enchanter != null
        && enchanter.isValid()
        && enchanter.getWorld().equals(player.getWorld())
        && enchanter.getLocation().distanceSquared(player.getLocation()) <= 36;
  }

  public boolean offers(Player p, String spell) {
    return npc != null
        && npc.isValid()
        && System.currentTimeMillis() < expires
        && npc.getWorld().equals(p.getWorld())
        && npc.getLocation().distanceSquared(p.getLocation()) <= 36
        && this.spell.equals(spell);
  }

  @EventHandler(priority = EventPriority.HIGHEST)
  public void interact(PlayerInteractEntityEvent event) {
    if (enchanter != null
        && event.getRightClicked().getUniqueId().equals(enchanter.getUniqueId())) {
      event.setCancelled(true);
      GrimoireModule g = magic();
      if (g != null
          && g.canPlay(event.getPlayer())
          && nearEnchanter(event.getPlayer())
          && event.getHand() == org.bukkit.inventory.EquipmentSlot.HAND) g.open(event.getPlayer());
      return;
    }
    if (npc == null || !event.getRightClicked().getUniqueId().equals(npc.getUniqueId())) return;
    event.setCancelled(true);
    if (event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
    GrimoireModule g = magic();
    if (g != null && g.canPlay(event.getPlayer()) && offers(event.getPlayer(), spell))
      g.discover(event.getPlayer(), encounterId, spell);
  }

  @EventHandler
  public void damage(EntityDamageEvent event) {
    if (event.getEntity().getScoreboardTags().contains(TAG)
        || event.getEntity().getScoreboardTags().contains("coresuite_enchanter"))
      event.setCancelled(true);
  }

  @EventHandler
  public void chunk(ChunkLoadEvent event) {
    for (Entity e : event.getChunk().getEntities())
      if (e.getScoreboardTags().contains(TAG)
          && (npc == null || !npc.getUniqueId().equals(e.getUniqueId()))) e.remove();
  }

  private void close() {
    if (npc != null) npc.remove();
    npc = null;
    encounterId = null;
  }

  @Override
  public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
    if (!isEnabled()) {
      sender.sendMessage("World encounters are disabled.");
      return true;
    }
    if (args.length > 0 && sender.hasPermission("coresuite.magic.admin")) {
      if (args[0].equalsIgnoreCase("setenchanter") && sender instanceof Player p) {
        Location at = p.getLocation();
        if (enchanter != null) enchanter.remove();
        enchanter = null;
        config.set("enchanter.enabled", true);
        config.set("enchanter.world", at.getWorld().getName());
        config.set("enchanter.x", at.getX());
        config.set("enchanter.y", at.getY());
        config.set("enchanter.z", at.getZ());
        saveConfig();
        restoreEnchanter();
        sender.sendMessage("Mystic Enchanter location saved.");
        return true;
      }
      if (args[0].equalsIgnoreCase("despawn")) {
        close();
        return true;
      }
      if (args[0].equalsIgnoreCase("spawn") && sender instanceof Player p) {
        sender.sendMessage(
            spawn(p, args.length > 1 ? args[1] : "hedge-mage")
                ? "Encounter opened nearby."
                : "No safe loaded location, invalid type, or encounter already active.");
        return true;
      }
    }
    if (npc == null)
      sender.sendMessage("No traveling scholar is currently nearby. Watch for the next discovery.");
    else
      sender.sendMessage(
          "Scholar: "
              + kind
              + " | "
              + npc.getWorld().getName()
              + " "
              + npc.getLocation().getBlockX()
              + ", "
              + npc.getLocation().getBlockY()
              + ", "
              + npc.getLocation().getBlockZ()
              + " | teaches "
              + spell
              + " | "
              + Math.max(0, (expires - System.currentTimeMillis()) / 60000)
              + " minutes remaining.");
    return true;
  }

  @Override
  public List<String> onTabComplete(CommandSender sender, Command c, String a, String[] args) {
    if (!sender.hasPermission("coresuite.magic.admin")) return List.of();
    var values =
        args.length == 1
            ? List.of("spawn", "despawn", "setenchanter")
            : args.length == 2 ? new ArrayList<>(SPELLS.keySet()) : List.<String>of();
    return values.stream()
        .filter(v -> v.startsWith(args[args.length - 1].toLowerCase(Locale.ROOT)))
        .toList();
  }
}
