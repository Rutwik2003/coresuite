package net.rocky.coresuite.module.grimoire;

import java.util.*;
import net.rocky.coresuite.common.integration.BossProtectionGate;
import org.bukkit.*;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.*;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;

/** One bounded animation loop. No task or chunk generation per projectile. */
final class SpellEngine {
  private final GrimoireModule module;
  private final List<Bolt> bolts = new ArrayList<>();
  private final List<Field> fields = new ArrayList<>();
  private long ticks;
  private Impact currentImpact;

  private static final class Impact {
    final Player caster;
    final LivingEntity target;
    org.bukkit.event.entity.EntityDamageByEntityEvent event;

    Impact(Player caster, LivingEntity target) {
      this.caster = caster;
      this.target = target;
    }
  }

  void observeDamage(org.bukkit.event.entity.EntityDamageByEntityEvent event) {
    if (currentImpact != null
        && event.getEntity().equals(currentImpact.target)
        && event.getDamager().equals(currentImpact.caster)) currentImpact.event = event;
  }

  private void damage(
      Player caster, LivingEntity target, SpellSpec spell, int tier, double multiplier) {
    if (target instanceof Player
        && !module.getConfig().getBoolean("spells." + spell.id() + ".pvp", true)) return;
    Impact before = currentImpact, hit = new Impact(caster, target);
    currentImpact = hit;
    try {
      target.damage(spell.tierDamage(tier) * multiplier, caster);
    } finally {
      currentImpact = before;
    }
    if (hit.event == null
        || hit.event.isCancelled()
        || hit.event.getFinalDamage() <= 0
        || target.isDead()) return;
    switch (spell.id()) {
      case "water-cannon" -> {
        target.setFireTicks(0);
        Vector push = target.getLocation().toVector().subtract(caster.getLocation().toVector());
        if (push.lengthSquared() > .01)
          target.setVelocity(push.normalize().multiply(.35).setY(.15));
      }
      case "frost-lance" ->
          target.addPotionEffect(
              new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, false, true, true));
      case "fireball" -> target.setFireTicks(Math.max(target.getFireTicks(), 40));
      default -> {}
    }
  }

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
    ring(
        player.getLocation().add(0, .1, 0),
        switch (spell.id()) {
          case "fireball", "aura-of-the-hearth" -> Particle.FLAME;
          case "water-cannon" -> Particle.SPLASH;
          case "frost-lance" -> Particle.SNOWFLAKE;
          case "harvesters-touch" -> Particle.HAPPY_VILLAGER;
          case "dimension-door", "zoltraak" -> Particle.PORTAL;
          default -> Particle.END_ROD;
        });
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
            Material cropType = block.getType();
            if (!player.breakBlock(block)) continue;
            if (module.getConfig().getBoolean("harvesting.replant", true)
                && block.getType().isAir()
                && block.getRelative(0, -1, 0).getType() == Material.FARMLAND) {
              Material seed = seedFor(cropType);
              int slot = plantingSlot(player.getInventory().getStorageContents(), seed);
              if (slot >= 0) {
                var replaced = block.getState();
                var planted = (Ageable) cropType.createBlockData();
                planted.setAge(0);
                block.setBlockData(planted, false);
                var place =
                    new BlockPlaceEvent(
                        block,
                        replaced,
                        block.getRelative(0, -1, 0),
                        new ItemStack(seed),
                        player,
                        true,
                        EquipmentSlot.HAND);
                Bukkit.getPluginManager().callEvent(place);
                ItemStack supply = player.getInventory().getItem(slot);
                if (place.isCancelled()
                    || !place.canBuild()
                    || supply == null
                    || supply.getType() != seed
                    || supply.hasItemMeta()
                    || supply.getAmount() < 1) {
                  replaced.update(true, false);
                } else {
                  supply.setAmount(supply.getAmount() - 1);
                  player.getInventory().setItem(slot, supply.getAmount() == 0 ? null : supply);
                }
              }
            }
            crop.getWorld()
                .spawnParticle(
                    Particle.HAPPY_VILLAGER, crop.clone().add(.5, .4, .5), 4, .2, .2, .2, 0);
            count++;
            if (count >= 8 + tier * 4) {
              module.objective(player, "HARVEST", count);
              return true;
            }
          }
        if (count > 0) module.objective(player, "HARVEST", count);
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
        if (b.spell.id().equals("water-cannon")) {
          particle(b.at.clone().add(offset.clone().multiply(2)), b.spell.id());
          particle(b.at.clone().subtract(offset.clone().multiply(2)), b.spell.id());
        } else if (b.spell.id().equals("frost-lance")) {
          b.at.getWorld().spawnParticle(Particle.SNOWFLAKE, b.at, 2, .12, .12, .12, 0);
        } else if (b.spell.id().equals("fireball")) {
          b.at.getWorld().spawnParticle(Particle.SMOKE, b.at, 1, .08, .08, .08, 0);
        } else if (b.spell.id().equals("zoltraak")) {
          b.at.getWorld().spawnParticle(Particle.WITCH, b.at.clone().add(offset), 1, 0, 0, 0, 0);
        }

        for (Entity e : b.at.getWorld().getNearbyEntities(b.at, .5, .6, .5)) {
          if (!(e instanceof LivingEntity target)
              || !validTarget(caster, target)
              || !b.hit.add(e.getUniqueId())) continue;
          if (!caster.hasLineOfSight(target)) continue;
          damage(caster, target, b.spell, b.tier, 1);
          if (b.spell.id().equals("fireball")) {
            int splashes = 0;
            for (Entity nearby : b.at.getWorld().getNearbyEntities(b.at, 2, 2, 2))
              if (nearby instanceof LivingEntity secondary
                  && validTarget(caster, secondary)
                  && caster.hasLineOfSight(secondary)
                  && b.hit.add(secondary.getUniqueId())) {
                damage(caster, secondary, b.spell, b.tier, .5);
                if (++splashes >= 5) break;
              }
            b.at.getWorld().spawnParticle(Particle.FLAME, b.at, 15, .4, .4, .4, .02);
          }
          // Do not change velocity/fire after damage: protection plugins may have cancelled it.
          particle(target.getLocation().add(0, 1, 0), b.spell.id());
          if (!b.spell.id().equals("zoltraak") || b.hit.size() >= b.tier) {
            ended = true;
            break;
          }
        }
      }
      if (ended) it.remove();
      else it.set(new Bolt(b.caster, b.at, b.direction, b.spell, b.tier, b.age + 1, b.hit));
    }
    Map<UUID, Double> healing = new HashMap<>();
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
      if (ticks % 10 == 0) {
        radiusRing(f.center, Particle.FLAME, 3 + f.tier, 24);
        f.center
            .getWorld()
            .spawnParticle(
                Particle.CAMPFIRE_COSY_SMOKE, f.center.clone().add(0, .3, 0), 2, .15, .1, .15, .01);
      }
      if (ticks % 20 == 0)
        for (Player player : f.center.getWorld().getPlayers()) {
          if (player.getLocation().distanceSquared(f.center) > Math.pow(3 + f.tier, 2)
              || !module.canPlay(player)
              || !allied(caster, player)) continue;
          Vector ray =
              player
                  .getEyeLocation()
                  .toVector()
                  .subtract(f.center.clone().add(0, .5, 0).toVector());
          if (ray.lengthSquared() > .01
              && f.center
                      .getWorld()
                      .rayTraceBlocks(
                          f.center.clone().add(0, .5, 0),
                          ray.clone().normalize(),
                          ray.length(),
                          FluidCollisionMode.NEVER,
                          true)
                  != null) continue;
          double amount =
              SpellSpec.bounded(
                      module.getConfig().getDouble("hearth.health-per-second", 1), 1, .1, 4)
                  * (1 + .25 * (f.tier - 1));
          healing.merge(player.getUniqueId(), amount, Math::max);
        }
      it.set(new Field(f.caster, f.center, f.tier, f.age + 1));
    }
    healing.forEach(
        (id, amount) -> {
          Player recipient = Bukkit.getPlayer(id);
          if (recipient != null && module.canPlay(recipient)) {
            recipient.heal(amount, EntityRegainHealthEvent.RegainReason.MAGIC);
            recipient
                .getWorld()
                .spawnParticle(
                    Particle.HEART, recipient.getLocation().add(0, 1.2, 0), 1, .2, .1, .2, 0);
          }
        });
  }

  static Material seedFor(Material crop) {
    return switch (crop) {
      case WHEAT -> Material.WHEAT_SEEDS;
      case BEETROOTS -> Material.BEETROOT_SEEDS;
      case CARROTS -> Material.CARROT;
      case POTATOES -> Material.POTATO;
      default -> throw new IllegalArgumentException("Unsupported crop: " + crop);
    };
  }

  static int plantingSlot(ItemStack[] items, Material seed) {
    for (int i = 0; i < items.length; i++) {
      ItemStack item = items[i];
      if (item != null && item.getType() == seed && item.getAmount() > 0 && !item.hasItemMeta())
        return i;
    }
    return -1;
  }

  boolean validTarget(Player caster, LivingEntity target) {
    if (target.equals(caster)
        || target.isInvulnerable()
        || target instanceof ArmorStand
        || target instanceof Villager
        || target instanceof WanderingTrader) return false;
    if (target instanceof Tameable tame && tame.isTamed()) return false;
    if (target.hasMetadata("NPC")
        || (target.getScoreboardTags().contains("coresuite_encounter")
            || target.getScoreboardTags().contains("coresuite_enchanter"))) return false;
    if (target instanceof Player other)
      return !other.isDead()
          && other.getGameMode() == GameMode.SURVIVAL
          && module.canMagicPvp(caster, other);
    return target instanceof Enemy
        || target instanceof Monster
        || target instanceof Slime
        || target instanceof Phantom
        || (target instanceof Animals && module.getConfig().getBoolean("targeting.animals", true));
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
    radiusRing(at, p, .7, 12);
  }

  private void radiusRing(Location at, Particle p, double radius, int points) {
    for (int i = 0; i < points; i++) {
      double angle = i * Math.PI * 2 / points;
      at.getWorld()
          .spawnParticle(
              p,
              at.clone().add(Math.cos(angle) * radius, .2, Math.sin(angle) * radius),
              1,
              0,
              0,
              0,
              0);
    }
  }

  void clear() {
    bolts.clear();
    fields.clear();
  }
}
