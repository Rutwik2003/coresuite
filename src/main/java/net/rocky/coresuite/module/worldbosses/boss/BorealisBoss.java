package net.rocky.coresuite.module.worldbosses.boss;

import net.kyori.adventure.bossbar.BossBar;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.compat.PaperCompat;
import net.rocky.coresuite.module.worldbosses.rewards.BossLootManager;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import java.util.ArrayList;

/** Terrain-safe ice encounter. Cast locations are fixed when the warning appears. */
public final class BorealisBoss extends WorldBoss {
    private long exposedUntil;
    private long fireReadyAt;
    private long nextCastTick;
    private long siphonAt = Long.MAX_VALUE;
    private boolean bowBroken;
    private boolean dealingSpellDamage;
    private final java.util.Map<java.util.UUID, Long> hiddenSince = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Long> pressureReady = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Long> frozenUntil = new java.util.HashMap<>();
    private final java.util.Map<java.util.UUID, Long> freezeRecovery = new java.util.HashMap<>();

    public BorealisBoss(CoreSuitePlugin plugin, FileConfiguration config, BossLootManager loot) {
        super(plugin, BossType.BOREALIS, config, loot);
    }

    @Override protected void setupPhases() {
        phases.clear();
        phases.add(new BossPhase(1, 60, "Permafrost", BossBar.Color.BLUE, "PERMAFROST"));
        phases.add(new BossPhase(2, 20, "Whiteout", BossBar.Color.WHITE, "WHITEOUT"));
        phases.add(new BossPhase(3, 0, "Absolute Zero", BossBar.Color.RED, "ABSOLUTE_ZERO"));
    }

    @Override public void setupEntity(LivingEntity mob) {
        var equipment = mob.getEquipment();
        if (equipment != null) {
            equipment.setHelmet(new ItemStack(Material.BLUE_ICE));
            equipment.setChestplate(new ItemStack(Material.IRON_CHESTPLATE));
            ItemStack axe = new ItemStack(Material.DIAMOND_AXE);
            axe.addUnsafeEnchantment(org.bukkit.enchantments.Enchantment.UNBREAKING, 1);
            equipment.setItemInMainHand(axe);
            ItemStack bow = net.rocky.coresuite.common.text.TextUtil.createItem(Material.BOW, "&bFrost-Tipped Bow", "&7The string sings of winter.");
            equipment.setItemInOffHand(bow);
            equipment.setItemInOffHandDropChance(0);
            equipment.setHelmetDropChance(0);
            equipment.setChestplateDropChance(0);
            equipment.setItemInMainHandDropChance(0);
        }
        applyAttributeSafely(mob, PaperCompat.SCALE, config.getDouble("borealis.scale", 1.65));
        applyAttributeSafely(mob, PaperCompat.MAX_HEALTH, maxHealth);
        applyAttributeSafely(mob, PaperCompat.MOVEMENT_SPEED, config.getDouble("borealis.movement-speed", .29));
        applyAttributeSafely(mob, PaperCompat.ATTACK_DAMAGE, config.getDouble("borealis.melee-damage", 20));
    }

    @Override protected boolean abilityCombatAllowed() {
        return System.currentTimeMillis() >= exposedUntil;
    }

    @Override public void onCombatTick(long tick) {
        if (entity == null || !entity.isValid()) return;

        if (!abilityCombatAllowed()) return;
        int phase = currentPhase == null ? 1 : currentPhase.getPhaseNumber();
        frozenUntil.entrySet().removeIf(entry -> entry.getValue() <= System.currentTimeMillis());
        freezeRecovery.entrySet().removeIf(entry -> entry.getValue() <= System.currentTimeMillis());
        if (barManager.getViewers().stream().noneMatch(this::targetable)) return;
        phasePressure(tick, phase);
        if (phase == 2 && abilityReady(tick, "frostguard", 24)) {
            summonFrostguard(phase);
            if (siphonAt == Long.MAX_VALUE && liveMinionCount() > 0) siphonAt = tick + 15;
        }
        if (phase == 2 && tick >= siphonAt) {
            siphonAt = Long.MAX_VALUE;
            broadcastToDomain("&bCRUEL SIPHON! &fKill the sentinels within 3 seconds to deny his healing!");
            runAbilityLater(() -> {
                if (currentPhase == null || currentPhase.getPhaseNumber() != 2) return;
                int surviving = liveMinionCount();
                clearMinions();
                double restored = heal(Math.min(240, surviving * 60));
                broadcastToDomain("&bCruel Siphon consumed " + surviving + " sentinels and restored " + (int) restored + " HP.");
            }, 60);
        }
        // Space casts apart so the nova freeze cannot overlap an unavoidable hail impact.
        if (tick < nextCastTick) return;
        if (phase < 3 && barManager.getViewers().stream().anyMatch(p -> targetable(p)
                && p.getLocation().distanceSquared(entity.getLocation()) > 64) && abilityReady(tick, "glacial-volley", 12)) {
            volley(); nextCastTick = tick + 4; return;
        }
        if (tick % 2 == 0 && abilityReady(tick, "frost-nova", phase == 3 ? 12 : 18)) {
            nova(phase == 3 ? 10 : 8);
            nextCastTick = tick + 6;
        } else if (tick % 2 != 0 && abilityReady(tick, "hail", phase >= 2 ? 10 : 16)) {
            hail();
            nextCastTick = tick + 5;
        }
    }

    public boolean applyThermalShock() {
        long now = System.currentTimeMillis();
        if (!active || !isAwakened || now < fireReadyAt) return false;
        fireReadyAt = now + 20000;
        exposedUntil = now + 6000;
        interruptCasts();
        siphonAt = Long.MAX_VALUE;
        broadcastToDomain("&6THERMAL SHOCK! &fTrue Ice melts for 6 seconds. Switch to SHOVELS for 2.5x damage! (20s counter cooldown)");
        return true;
    }

    static boolean resistsCounter(double percent) {
        double chance = Double.isFinite(percent) ? Math.max(0, Math.min(100, percent)) : 50;
        return java.util.concurrent.ThreadLocalRandom.current().nextDouble(100) < chance;
    }

    @Override public double calculateIncomingDamage(Player player, double damage, ItemStack weapon) {
        String material = weapon == null ? "" : weapon.getType().name();
        if (!abilityCombatAllowed()) return material.endsWith("_SHOVEL") ? damage * 2.5 : damage;
        boolean resisted = material.endsWith("_SWORD") || material.endsWith("_AXE")
                || material.equals("BOW") || material.equals("CROSSBOW");
        return resisted ? damage * .5 : damage;
    }

    private boolean targetable(Player player) {
        return player != null && player.isOnline() && eligibleFighter(player)
            && barManager.getViewers().contains(player)
            && domainManager.isInDomain(player.getLocation());
    }

    private void ring(Location center, double radius) {
        for (int i = 0; i < 32; i++) {
            double angle = i * Math.PI / 16;
            center.getWorld().spawnParticle(Particle.SNOWFLAKE,
                center.clone().add(Math.cos(angle) * radius, .25, Math.sin(angle) * radius), 2, .1, .1, .1, .01);
        }
    }

    private void strike(Location center, double radius, double damage) {
        ring(center, radius);
        center.getWorld().playSound(center, Sound.BLOCK_GLASS_BREAK, 1.2f, .6f);
        for (Player player : new ArrayList<>(barManager.getViewers())) {
            if (!targetable(player) || player.getWorld() != center.getWorld()) continue;
            Location at = player.getLocation();
            double dx = at.getX() - center.getX(), dz = at.getZ() - center.getZ();
            if (dx * dx + dz * dz <= radius * radius && Math.abs(at.getY() - center.getY()) <= 4
                    && player.hasLineOfSight(entity)) {
                var previousHit = player.getLastDamageCause();
                spellDamage(player, damage);
                var hit = player.getLastDamageCause();
                if (targetable(player) && hit != null && hit != previousHit && !hit.isCancelled()
                        && hit.getFinalDamage() > 0) freeze(player);
            }
        }
    }

    private void nova(double radius) {
        Location origin = entity.getLocation().clone();
        broadcastToDomain("&bFROST NOVA! &fLeave the marked circle within 3 seconds or be frozen for 2 seconds!");
        ring(origin, radius);
        runAbilityLater(() -> ring(origin, radius), 20);
        runAbilityLater(() -> ring(origin, radius), 40);
        runAbilityLater(() -> strike(origin, radius, config.getDouble("borealis.combat.frost-nova.damage", 22)), 60);
    }

    private void hail() {
        // One hit per player per cast, even when several marked circles overlap.
        var marks = new ArrayList<Location>();
        int phase = currentPhase == null ? 1 : currentPhase.getPhaseNumber();
        double hailRadius = phase >= 2 ? 3.25 : 2.5;
        for (Player player : new ArrayList<>(barManager.getViewers())) {
            if (targetable(player)) marks.add(player.getLocation().clone());
        }
        if (marks.isEmpty()) return;
        broadcastToDomain("&bHAIL MARKS! &fMove out of the small circles!");
        marks.forEach(mark -> ring(mark, hailRadius));
        runAbilityLater(() -> marks.forEach(mark -> ring(mark, hailRadius)), 20);
        runAbilityLater(() -> {
            for (Player player : new ArrayList<>(barManager.getViewers())) {
                    if (!targetable(player) || !player.hasLineOfSight(entity)) continue;
                if (marks.stream().anyMatch(mark -> mark.getWorld() == player.getWorld()
                        && mark.distanceSquared(player.getLocation()) <= hailRadius * hailRadius)) {
                    spellDamage(player, config.getDouble("borealis.combat.hail.damage", 16) * (phase == 3 ? 1.25 : 1));
                }
            }
            marks.forEach(mark -> mark.getWorld().spawnParticle(Particle.SNOWFLAKE, mark, 20, 1, 1, 1, .1));
        }, 50);
    }

    private void freeze(Player player) { freeze(player, 2000, 10000); }

    private void freeze(Player player, int duration, int recovery) {
        long now = System.currentTimeMillis();
        if (now < freezeRecovery.getOrDefault(player.getUniqueId(), 0L)) return;
        frozenUntil.put(player.getUniqueId(), now + duration);
        freezeRecovery.put(player.getUniqueId(), now + recovery);
        net.rocky.coresuite.common.text.TextUtil.sendActionBar(player, "&bFROZEN! &f" + (duration / 1000.0) + "s — you can still attack, eat and use potions!");
        player.getWorld().spawnParticle(Particle.SNOWFLAKE, player.getLocation().add(0, 1, 0), 25, .4, .7, .4, .02);
    }

    public void restrainFrozenMovement(org.bukkit.event.player.PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (event instanceof org.bukkit.event.player.PlayerTeleportEvent) {
            frozenUntil.remove(player.getUniqueId());
            return;
        }
        if (!active || !targetable(player) || event.getTo() == null
                || System.currentTimeMillis() >= frozenUntil.getOrDefault(player.getUniqueId(), 0L)) return;
        Location from = event.getFrom(), to = event.getTo().clone();
        if (from.getWorld() != to.getWorld()) return;
        to.setX(from.getX()); to.setZ(from.getZ());
        // Allow gravity and camera movement; never enclose a player in placed blocks.
        to.setY(Math.min(from.getY(), to.getY()));
        if (net.rocky.coresuite.module.worldbosses.combat.BossMovement.clear(to)) event.setTo(to);
        else frozenUntil.remove(player.getUniqueId());
    }

    private void summonFrostguard(int phase) {
        var fighters = barManager.getViewers().stream().filter(this::targetable).toList();
        if (fighters.isEmpty()) return;
        int cap = 4;
        int count = cap - liveMinionCount();
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2 * i / Math.max(1, count);
            Location desired = entity.getLocation().add(Math.cos(angle) * 3, 0, Math.sin(angle) * 3);
            Location spawn = net.rocky.coresuite.module.worldbosses.combat.BossMovement.findLanding(desired, domainManager::isInDomain);
            if (spawn == null) continue;
            var guard = spawn.getWorld().spawn(spawn, org.bukkit.entity.Stray.class, this::setupFrostguard);
            guard.setTarget(fighters.get(i % fighters.size()));
        }
        if (count > 0) broadcastToDomain("&bTHE FROSTGUARD RISE! &fDefeat Borealis's sentinels before the next nova!");
    }

    private void setupFrostguard(org.bukkit.entity.Stray mob) {
                mob.getPersistentDataContainer().set(new NamespacedKey(plugin, "boss_minion"),
                    org.bukkit.persistence.PersistentDataType.BYTE, (byte) 1);
                registerMinion(mob);
                mob.customName(net.rocky.coresuite.common.text.TextUtil.colorize("&bFrostguard Sentinel"));
                mob.setCustomNameVisible(true);
                mob.setCanPickupItems(false);
                var gear = mob.getEquipment();
                gear.setHelmet(new ItemStack(Material.BLUE_ICE));
                gear.setItemInMainHand(new ItemStack(Material.STONE_SWORD));
                gear.setHelmetDropChance(0); gear.setItemInMainHandDropChance(0);
                applyAttributeSafely(mob, PaperCompat.MAX_HEALTH, 45);
                mob.setHealth(45);
                applyAttributeSafely(mob, PaperCompat.ATTACK_DAMAGE, 7);
                applyAttributeSafely(mob, PaperCompat.MOVEMENT_SPEED, .30);
    }

    private void phasePressure(long tick, int phase) {
        if (phase == 3 && !bowBroken) {
            bowBroken = true;
            clearMinions();
            siphonAt = Long.MAX_VALUE;
            entity.getEquipment().setItemInOffHand(null);
            applyAttributeSafely(entity, PaperCompat.MOVEMENT_SPEED, .36);
            broadcastToDomain("&cTHE SHATTERED BOW! &fBorealis rushes in with his axe. Beware Frostbite!");
        }
        double freezeHealing = 0;
        for (Player player : new ArrayList<>(barManager.getViewers())) {
            if (!targetable(player)) continue;
            handleAntiCheese(player);
            if (phase == 1) applyBossEffect(player, new PotionEffect(PotionEffectType.SLOWNESS, 30, 0));
            if (phase == 2 && tick % 3 == 0 && !wearsLeather(player) && !wearsStriders(player)) {
                var previous = player.getLastDamageCause();
                spellDamage(player, 3);
                var hit = player.getLastDamageCause();
                if (hit != null && hit != previous && !hit.isCancelled() && hit.getFinalDamage() > 0) freezeHealing += 2;
                player.spawnParticle(Particle.SNOWFLAKE, player.getLocation().add(0, 1, 0), 6, .4, .6, .4, .01);
            }
        }
        if (freezeHealing > 0) heal(Math.min(8, freezeHealing));
        if (phase == 3 && tick % 18 == 0) {
            broadcastToDomain("&bWHITEOUT INCOMING! &fA blizzard will obscure your sight for 2 seconds!");
            nextCastTick = Math.max(nextCastTick, tick + 5);
            runAbilityLater(() -> {
                for (Player player : new ArrayList<>(barManager.getViewers())) if (targetable(player)) {
                    applyBossEffect(player, new PotionEffect(PotionEffectType.BLINDNESS, 40, 0));
                    player.playSound(player.getLocation(), Sound.ITEM_ELYTRA_FLYING, .8f, .5f);
                }
            }, 40);
        }
    }

    private boolean wearsLeather(Player player) {
        for (ItemStack item : player.getInventory().getArmorContents())
            if (item != null && item.getType().name().startsWith("LEATHER_")) return true;
        return false;
    }

    private boolean wearsStriders(Player player) {
        ItemStack boots = player.getInventory().getBoots();
        return boots != null && boots.hasItemMeta() && "frostbound_striders".equals(boots.getItemMeta()
            .getPersistentDataContainer().get(new NamespacedKey(plugin, "boss_weapon"), org.bukkit.persistence.PersistentDataType.STRING));
    }

    private void spellDamage(Player player, double amount) {
        dealingSpellDamage = true;
        try { player.damage(amount, entity); }
        finally { dealingSpellDamage = false; }
    }

    public void onLandedAttack(Player player, boolean arrow) {
        if (dealingSpellDamage) return;
        if (!active || !isAwakened || !targetable(player)) return;
        if (arrow) applyBossEffect(player, new PotionEffect(PotionEffectType.WEAKNESS, 60, 1));
        else if (currentPhase != null && currentPhase.getPhaseNumber() == 3) freeze(player, 1500, 8000);
        else {
            var away = player.getLocation().toVector().subtract(entity.getLocation().toVector());
            if (away.lengthSquared() > .01) net.rocky.coresuite.module.worldbosses.combat.BossMovement.push(player,
                away.normalize().multiply(.7).setY(.25));
        }
    }

    private void volley() {
        Player target = barManager.getViewers().stream().filter(this::targetable)
            .filter(p -> p.hasLineOfSight(entity)).findFirst().orElse(null);
        if (target == null) return;
        broadcastToDomain("&bGLACIAL VOLLEY! &fMove sideways — the bow fires in 1 second!");
        Location aim = target.getEyeLocation().clone();
        runAbilityLater(() -> {
            Location start = entity.getEyeLocation();
            var direction = aim.toVector().subtract(start.toVector());
            if (direction.lengthSquared() < .01) return;
            for (int i = -2; i <= 2; i++) {
                var arrow = start.getWorld().spawnArrow(start, direction.clone().normalize().rotateAroundY(i * .14), 2.4f, 0);
                trackAbilityEntity(arrow);
                arrow.setShooter(entity); arrow.setDamage(5);
                arrow.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);
                runAbilityLater(arrow::remove, 100);
            }
        }, 20);
    }

    @Override public void handleAntiCheese(Player player) {
        if (!targetable(player) || !abilityCombatAllowed()) return;
        long now = System.currentTimeMillis();
        var id = player.getUniqueId();
        if (player.hasLineOfSight(entity)) hiddenSince.remove(id);
        else hiddenSince.putIfAbsent(id, now);
        if (now < pressureReady.getOrDefault(id, 0L)) return;
        if (now - hiddenSince.getOrDefault(id, now) >= 5000) {
            pressureReady.put(id, now + 15000); hiddenSince.remove(id);
            Location marked = player.getLocation().clone();
            ring(marked, 3);
            net.rocky.coresuite.common.text.TextUtil.sendActionBar(player, "&5RIME CLOUD! &fLeave the marked hiding spot in 2 seconds!");
            for (int pulse = 0; pulse < 3; pulse++) runAbilityLater(() -> {
                marked.getWorld().spawnParticle(Particle.DRAGON_BREATH, marked, 25, 1, .5, 1, .01, 1.0f);
                for (Player victim : new ArrayList<>(barManager.getViewers()))
                    if (targetable(victim) && victim.getWorld() == marked.getWorld()
                            && victim.getLocation().distanceSquared(marked) <= 9) spellDamage(victim, 5);
            }, 40 + pulse * 20L);
        } else if (player.getLocation().distanceSquared(entity.getLocation()) > 400) {
            pressureReady.put(id, now + 15000);
            net.rocky.coresuite.common.text.TextUtil.sendActionBar(player, "&bWHITEOUT TETHER! &fGet within 20 blocks in 2 seconds to escape the pull!");
            runAbilityLater(() -> {
                if (!targetable(player) || player.getLocation().distanceSquared(entity.getLocation()) <= 400) return;
                Location beside = entity.getLocation().add(3, 0, 0);
                if (net.rocky.coresuite.module.worldbosses.combat.BossMovement.teleport(player, beside, domainManager::isInDomain))
                    applyBossEffect(player, new PotionEffect(PotionEffectType.SLOWNESS, 40, 2));
            }, 40);
        }
    }

    @Override public boolean testAbility(String ability) {
        if (entity == null || !isAwakened || !abilityCombatAllowed()) return false;
        if ("glacial-volley".equalsIgnoreCase(ability)) { volley(); return true; }
        if ("frost-nova".equalsIgnoreCase(ability)) { nova(7); return true; }
        if ("frostguard".equalsIgnoreCase(ability)) { summonFrostguard(2); return true; }
        if ("hail".equalsIgnoreCase(ability)) { hail(); return true; }
        return false;
    }
}
