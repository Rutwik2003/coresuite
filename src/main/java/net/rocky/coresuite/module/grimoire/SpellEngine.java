package net.rocky.coresuite.module.grimoire;

import java.util.*;
import net.rocky.coresuite.common.integration.BossProtectionGate;
import org.bukkit.*;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;

/** One bounded animation loop. No task or chunk generation per projectile. */
final class SpellEngine {
  private final GrimoireModule module;
  private final List<Bolt> bolts = new ArrayList<>();
  private final List<Field> fields = new ArrayList<>();
  private long ticks;

  private record Bolt(
      UUID caster,
      Location at,
      Vector direction,
      SpellSpec spell,
      int tier,
      int age,
      Set<UUID> hit) {}

  private record Field(UUID caster, Location center, int tier, int age) {}

  SpellEngine(GrimoireModule module) {
    this.module = module;
  }

  boolean cast(Player player, SpellSpec spell, int tier) {
    ring(player.getLocation().add(0, .1, 0), Particle.ENCHANT);
    switch (spell.id()) {
      case "dimension-door", "zephyr-leap" -> {
        if (module.inArena(player.getLocation())) return false;
        Location start = player.getEyeLocation();
        Vector direction = start.getDirection();
        Location destination = null;
        for (double d = 1; d <= Math.min(spell.range() + 2 * (tier - 1), 15); d += .5) {
          Location at = start.clone().add(direction.clone().multiply(d));
          if (!loaded(at) || !at.getBlock().isPassable()) break;
          Location feet = at.clone().subtract(0, 1, 0);
          if (feet.getBlock().isPassable()
              && feet.clone().add(0, 1, 0).getBlock().isPassable()
              && feet.clone().subtract(0, 1, 0).getBlock().getType().isSolid()
              && feet.getWorld().getWorldBorder().isInside(feet)
              && !module.inArena(feet)
              && BossProtectionGate.allows(feet)) destination = feet;
        }
        if (destination == null) return false;
        Location from = player.getLocation();
        destination.setYaw(from.getYaw());
        destination.setPitch(from.getPitch());
        if (!player.teleport(destination)) return false;
        ring(from, Particle.PORTAL);
        ring(destination, Particle.PORTAL);
        return true;
      }
      case "aura-of-the-hearth" -> {
        if (fields.size() >= 16) return false;
        if (fields.stream().anyMatch(f -> f.caster.equals(player.getUniqueId()))) return false;
        fields.add(new Field(player.getUniqueId(), player.getLocation(), tier, 0));
        return true;
      }
      case "harvesters-touch" -> {
        if (module.inArena(player.getLocation())) return false;
        int count = 0;
        Location at = player.getLocation();
        for (int x = -2; x <= 2; x++)
          for (int z = -2; z <= 2; z++) {
            Location crop = at.clone().add(x, 0, z);
            if (!loaded(crop)) continue;
            var block = crop.getBlock();
            if (!BossProtectionGate.allows(crop)) continue;
            if (!(block.getBlockData() instanceof Ageable age)
                || age.getAge() != age.getMaximumAge()) continue;
            if (!Set.of(Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS)
                .contains(block.getType())) continue;
            var event = new BlockBreakEvent(block, player);
            Bukkit.getPluginManager().callEvent(event);
            if (event.isCancelled()) continue;
            if (event.isDropItems()) block.breakNaturally();
            else block.setType(Material.AIR);
            count++;
            if (count >= 8 + tier * 4) return true;
          }
        return count > 0;
      }
      default -> {
        if (bolts.size() >= 96) return false;
        bolts.add(
            new Bolt(
                player.getUniqueId(),
                player.getEyeLocation().add(player.getLocation().getDirection().multiply(.5)),
                player.getLocation().getDirection().normalize(),
                spell,
                tier,
                0,
                new HashSet<>()));
        return true;
      }
    }
  }

  void tick() {
    ticks++;
    for (var it = bolts.listIterator(); it.hasNext(); ) {
      Bolt b = it.next();
      Player caster = Bukkit.getPlayer(b.caster);
      if (caster == null
          || !module.canPlay(caster)
          || !caster.getWorld().equals(b.at.getWorld())
          || b.age > Math.ceil(b.spell.range() / 1.2)) {
        it.remove();
        continue;
      }
      boolean ended = false;
      for (int step = 0; step < 3 && !ended; step++) {
        b.at.add(b.direction.clone().multiply(.4));
        if (!loaded(b.at) || !b.at.getBlock().isPassable()) {
          ended = true;
          break;
        }
        particle(b.at, b.spell.id());
        // Twin orbiting sparks distinguish magic from a plain arrow without spawning entities.
        double angle = (b.age * 3 + step) * .65;
        Vector side = b.direction.clone().crossProduct(new Vector(0, 1, 0));
        if (side.lengthSquared() < .01) side = new Vector(1, 0, 0);
        else side.normalize();
        Vector up = side.clone().crossProduct(b.direction).normalize();
        Vector offset =
            side.multiply(Math.cos(angle) * .16).add(up.multiply(Math.sin(angle) * .16));
        b.at.getWorld().spawnParticle(Particle.ENCHANT, b.at.clone().add(offset), 1, 0, 0, 0, 0);

        for (Entity e : b.at.getWorld().getNearbyEntities(b.at, .5, .6, .5)) {
          if (!(e instanceof LivingEntity target)
              || !validTarget(caster, target)
              || !b.hit.add(e.getUniqueId())) continue;
          if (!caster.hasLineOfSight(target)) continue;
          target.damage(b.spell.tierDamage(b.tier), caster);
          if (b.spell.id().equals("fireball")) {
            int splashes = 0;
            for (Entity nearby : b.at.getWorld().getNearbyEntities(b.at, 2, 2, 2))
              if (nearby instanceof LivingEntity secondary
                  && validTarget(caster, secondary)
                  && caster.hasLineOfSight(secondary)
                  && b.hit.add(secondary.getUniqueId())) {
                secondary.damage(b.spell.tierDamage(b.tier) * .5, caster);
                if (++splashes >= 5) break;
              }
            b.at.getWorld().spawnParticle(Particle.FLAME, b.at, 15, .4, .4, .4, .02);
          }
          // Do not change velocity/fire after damage: protection plugins may have cancelled it.
          ring(target.getLocation().add(0, 1, 0), Particle.ENCHANT);
          if (!b.spell.id().equals("zoltraak") || b.hit.size() >= b.tier) {
            ended = true;
            break;
          }
        }
      }
      if (ended) it.remove();
      else it.set(new Bolt(b.caster, b.at, b.direction, b.spell, b.tier, b.age + 1, b.hit));
    }
    for (var it = fields.listIterator(); it.hasNext(); ) {
      Field f = it.next();
      Player caster = Bukkit.getPlayer(f.caster);
      if (caster == null
          || !module.canPlay(caster)
          || !loaded(f.center)
          || f.age >= 160 + 40 * (f.tier - 1)) {
        it.remove();
        continue;
      }
      if (ticks % 5 == 0) ring(f.center, Particle.HAPPY_VILLAGER);
      if (ticks % 20 == 0)
        for (Player player : f.center.getWorld().getPlayers()) {
          if (player.getLocation().distanceSquared(f.center) > Math.pow(3 + f.tier, 2)
              || !module.canPlay(player)
              || !allied(caster, player)) continue;
          // Vanilla effect refresh does not stack healing across overlapping fields.
          player.addPotionEffect(
              new PotionEffect(PotionEffectType.REGENERATION, 40, 0, false, true, true));
        }
      it.set(new Field(f.caster, f.center, f.tier, f.age + 1));
    }
  }

  private boolean validTarget(Player caster, LivingEntity target) {
    if (target.equals(caster)
        || target.isInvulnerable()
        || target instanceof ArmorStand
        || target instanceof Villager
        || target instanceof WanderingTrader) return false;
    if (target instanceof Tameable tame && tame.isTamed()) return false;
    if (target.hasMetadata("NPC")
        || (target.getScoreboardTags().contains("coresuite_encounter")
            || target.getScoreboardTags().contains("coresuite_enchanter"))) return false;
    if (target instanceof Player)
      return false; // PvP deliberately disabled until explicit zone integration is validated.
    return target instanceof Monster || target instanceof Slime || target instanceof Phantom;
  }

  private boolean allied(Player a, Player b) {
    if (a.equals(b) || module.raidAllies(a, b)) return true;
    var board = Bukkit.getScoreboardManager().getMainScoreboard();
    var team = board.getEntryTeam(a.getName());
    return team != null && team.equals(board.getEntryTeam(b.getName()));
  }

  private static boolean loaded(Location l) {
    return l.getWorld() != null
        && l.getWorld().isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4);
  }

  private void particle(Location at, String spell) {
    Particle p =
        switch (spell) {
          case "fireball" -> Particle.FLAME;
          case "water-cannon" -> Particle.SPLASH;
          case "frost-lance" -> Particle.SNOWFLAKE;
          default -> Particle.END_ROD;
        };
    at.getWorld().spawnParticle(p, at, 1, 0, 0, 0, 0);
  }

  private void ring(Location at, Particle p) {
    for (int i = 0; i < 12; i++) {
      double angle = i * Math.PI / 6;
      at.getWorld()
          .spawnParticle(
              p, at.clone().add(Math.cos(angle) * .7, .2, Math.sin(angle) * .7), 1, 0, 0, 0, 0);
    }
  }

  void clear() {
    bolts.clear();
    fields.clear();
  }
}
