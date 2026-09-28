package net.rocky.coresuite.module.worldbosses.boss;

import net.kyori.adventure.bossbar.BossBar;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.worldbosses.WorldBossesModule;
import net.rocky.coresuite.module.worldbosses.rewards.BossLootManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import net.rocky.coresuite.module.worldbosses.combat.StatusBadge;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

public class ValeriusBoss extends WorldBoss {

    private long quenchedUntil = 0;
    private double overheat;
    private long exposedUntil;

    public ValeriusBoss(CoreSuitePlugin plugin, FileConfiguration config, BossLootManager lootManager) {
        super(plugin, BossType.VALERIUS, config, lootManager);
    }

    @Override
    protected void setupPhases() {
        phases.clear();
        phases.add(new BossPhase(1, 60.0, "The Forge", BossBar.Color.BLUE, "CINDERS"));
        phases.add(new BossPhase(2, 20.0, "Meltdown", BossBar.Color.YELLOW, "MELTDOWN"));
        phases.add(new BossPhase(3, 0.0, "Ash Void", BossBar.Color.RED, "ASH_VOID"));
    }

    @Override
    public void setupEntity(LivingEntity entity) {
        EntityEquipment eq = entity.getEquipment();
        if (eq != null) {
            eq.setHelmet(new ItemStack(Material.NETHERITE_HELMET));
            eq.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
            eq.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
            eq.setBoots(new ItemStack(Material.NETHERITE_BOOTS));
            eq.setItemInMainHand(new ItemStack(Material.NETHERITE_AXE));
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
            eq.setLeggingsDropChance(0f);
            eq.setBootsDropChance(0f);
            eq.setItemInMainHandDropChance(0f);
        }

        applyAttributeSafely(entity, net.rocky.coresuite.common.compat.PaperCompat.SCALE, config.getDouble("valerius.scale", 1.35));
        applyAttributeSafely(entity, net.rocky.coresuite.common.compat.PaperCompat.MOVEMENT_SPEED, config.getDouble("valerius.movement-speed", 0.28));
        applyAttributeSafely(entity, net.rocky.coresuite.common.compat.PaperCompat.MAX_HEALTH, this.maxHealth);
        applyAttributeSafely(entity, net.rocky.coresuite.common.compat.PaperCompat.ATTACK_DAMAGE, config.getDouble("valerius.melee-damage", 14));
    }

    @Override
    public void onCombatTick(long tickCount) {
        if (entity == null || !entity.isValid()) return;

        Location bossLoc = entity.getLocation();
        entity.setAI(!isExposed());
        if (isExposed()) {
            bossLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, bossLoc.clone().add(0, 1.2, 0), 18, .45, .7, .45, .03);
            for (Player player : barManager.getViewers()) sendCombatAlert(player, StatusBadge.QUENCHED,
                    "&bTHE FORGE FALTERS! &fCracks spread across the molten core.", 2);
            return;
        }

        // Quenched steam particles
        if (isQuenched()) {
            bossLoc.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, bossLoc.clone().add(0, 1.5, 0), 6, 0.3, 0.4, 0.3, 0.02);
        }

        // Passive: Searing Siphon (triggers every 6 seconds, leaches health from hunters; quenched status suppresses it)
        int siphonInterval = Math.max(1, config.getInt("valerius.combat.searing-siphon.interval-seconds", 6));
        if (tickCount % siphonInterval == 0) {
            executeSearingSiphon(bossLoc);
        }

        // Magma / Lava / Fire Attunement Regeneration (absorbs ambient heat every 2s unless quenched)
        if (tickCount % 2 == 0 && !isQuenched()) {
            Material underMat = bossLoc.getBlock().getType();
            Material inMat = bossLoc.getBlock().getRelative(0, -1, 0).getType();
            boolean inHeat = entity.getFireTicks() > 0 || underMat == Material.LAVA || inMat == Material.LAVA || inMat == Material.MAGMA_BLOCK;
            if (inHeat) {
                double heatHeal = config.getDouble("valerius.combat.heat-regen-hp", 15.0);
                heal(heatHeal);
                bossLoc.getWorld().spawnParticle(Particle.LAVA, bossLoc.clone().add(0, 1.0, 0), 3, 0.3, 0.3, 0.3, 0);
            }
        }

        // Melee: Ashen Slam (every 8 seconds when enemies within 5m)
        if (abilityReady(tickCount, "ashen-slam", 8)) {
            executeAshenSlam(bossLoc);
        }

        // Ranged: Caldera Mortar (every 6 seconds when enemies > 7m)
        if (abilityReady(tickCount, "caldera-mortar", 6)) {
            executeCalderaMortar(bossLoc);
        }

        // Phase specific mechanics
        if (currentPhase != null) {
            int phaseNum = currentPhase.getPhaseNumber();

            // Phase 2 & 3: Volcanic Geysers (every 10s) and Magma Rush (every 12s)
            if (phaseNum >= 2) {
                if (abilityReady(tickCount, "volcanic-geyser", 10)) {
                    executeVolcanicGeyser(bossLoc);
                }
                if (abilityReady(tickCount, "magma-rush", 12)) {
                    executeMagmaRush(bossLoc);
                }
            }

            // Phase 3 Ultimate: Cataclysmic Supernova (every 24s)
            if (phaseNum == 3) {
                if (abilityReady(tickCount, "cataclysmic-supernova", 24)) {
                    executeCataclysmicSupernova(bossLoc);
                }
            }

            switch (phaseNum) {
                case 1 -> { // The Forge
                    for (Player p : barManager.getViewers()) {
                        applyBossEffect(p, new PotionEffect(PotionEffectType.HUNGER, 60, 1, false, false, true));
                        if (tickCount % 20 == 0) { // Every 20s
                            applyBossEffect(p, new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false, true));
                        }
                        handleAntiCheese(p);
                    }
                }
                case 2 -> { // Meltdown
                    for (Player p : barManager.getViewers()) {
                        handleAntiCheese(p);

                        // Scalding ground fire
                        if (tickCount % 2 == 0) {
                            Block under = p.getLocation().getBlock();
                            if (under.getType() == Material.AIR && under.getRelative(0, -1, 0).getType().isSolid()) {
                                placeTemporaryBlock(under, Material.FIRE, 60L);
                            }
                        }

                        // Water remains available as a counter mechanic.

                    }
                }
                case 3 -> { // Ash Void
                    for (Player p : barManager.getViewers()) {
                        applyBossEffect(p, new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false, true));
                        handleAntiCheese(p);
                    }
                }
            }
        }
    }

    private void executeVolcanicGeyser(Location bossLoc) {
        if (barManager.getViewers().isEmpty()) return;
        java.util.List<Player> targets = new java.util.ArrayList<>(barManager.getViewers());
        targets.removeIf(p -> p == null || !p.isOnline() || p.getWorld() == null || !p.getWorld().equals(bossLoc.getWorld()));
        if (targets.isEmpty()) return;
        addOverheat(12, "Volcanic Geyser");

        int count = Math.min(2, targets.size());
        for (int i = 0; i < count; i++) {
            Player p = targets.get(i);
            Location targetGround = p.getLocation().clone();
            targetGround.setY(targetGround.getWorld().getHighestBlockYAt(targetGround.getBlockX(), targetGround.getBlockZ()) + 0.1);

            double damage = config.getDouble("valerius.combat.volcanic-geyser.damage", 16.0);
            double knockup = config.getDouble("valerius.combat.volcanic-geyser.knockup", 1.1);
            int telegraphTicks = config.getInt("valerius.combat.volcanic-geyser.telegraph-ticks", 30);

            sendCombatAlert(p, StatusBadge.CALDERA_MORTAR, "&cMolten heat gathers beneath your feet! EVADE!", 2);

            runAbilityTimer(new java.util.function.Consumer<BukkitTask>() {
                int elapsed = 0;
                @Override
                public void accept(BukkitTask task) {
                    if (!active || targetGround.getWorld() == null) {
                        task.cancel();
                        return;
                    }
                    elapsed += 3;

                    for (int deg = 0; deg < 360; deg += 30) {
                        double rad = Math.toRadians(deg);
                        double x = targetGround.getX() + (2.5 * Math.cos(rad));
                        double z = targetGround.getZ() + (2.5 * Math.sin(rad));
                        targetGround.getWorld().spawnParticle(Particle.FLAME, x, targetGround.getY(), z, 1, 0, 0, 0, 0, null, true);
                    }
                    targetGround.getWorld().spawnParticle(Particle.LAVA, targetGround, 1, 0.5, 0.1, 0.5, 0, null, true);
                    targetGround.getWorld().playSound(targetGround, Sound.BLOCK_LAVA_POP, 0.8f, 1.2f);

                    if (elapsed >= telegraphTicks) {
                        task.cancel();
                        targetGround.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, targetGround.clone().add(0, 1, 0), 1, 0, 0, 0, 0, null, true);
                        targetGround.getWorld().spawnParticle(Particle.LAVA, targetGround.clone().add(0, 1, 0), 30, 0.8, 1.5, 0.8, 0.2, null, true);
                        targetGround.getWorld().playSound(targetGround, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.85f);

                        for (Player fighter : barManager.getViewers()) {
                            if (fighter.getWorld() != null && fighter.getWorld().equals(targetGround.getWorld())) {
                                if (fighter.getLocation().distanceSquared(targetGround) <= 6.25) {
                                    fighter.damage(damage, entity);
                                    fighter.setFireTicks(100);
                                    Vector up = new Vector(0, knockup, 0);
                net.rocky.coresuite.module.worldbosses.combat.BossMovement.push(fighter, up);
                                    sendCombatAlert(fighter, StatusBadge.CALDERA_MORTAR, "&cVolcanic eruption blasted you into the air!", 2);
                                }
                            }
                        }
                    }
                }
            }, 0L, 3L);
        }
    }

    private void executeMagmaRush(Location bossLoc) {
        if (entity == null || !entity.isValid()) return;
        addOverheat(10, "Magma Rush");
        Vector dir = entity.getLocation().getDirection().setY(0).normalize();
        double speed = config.getDouble("valerius.combat.magma-rush.speed", 1.6);
        double damage = config.getDouble("valerius.combat.magma-rush.damage", 10.0);

        bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_RAVAGER_ROAR, 1.5f, 0.7f);

        entity.setVelocity(dir.multiply(speed));

        runAbilityTimer(new java.util.function.Consumer<BukkitTask>() {
            int ticks = 0;
            final java.util.Set<java.util.UUID> hitPlayers = new java.util.HashSet<>();
            @Override
            public void accept(BukkitTask task) {
                if (!active || entity == null || !entity.isValid() || ++ticks > 10) {
                    task.cancel();
                    return;
                }
                Location curr = entity.getLocation();
                curr.getWorld().spawnParticle(Particle.FLAME, curr, 10, 0.5, 0.5, 0.5, 0.05, null, true);
                curr.getWorld().spawnParticle(Particle.LAVA, curr, 3, 0.3, 0.3, 0.3, 0, null, true);

                for (Player p : barManager.getViewers()) {
                    if (p.getWorld() != null && p.getWorld().equals(curr.getWorld()) && p.getLocation().distanceSquared(curr) <= 4.0
                            && hitPlayers.add(p.getUniqueId())) {
                        p.damage(damage, entity);
                        Vector kb = p.getLocation().toVector().subtract(curr.toVector()).normalize().multiply(1.2).setY(0.4);
                        net.rocky.coresuite.module.worldbosses.combat.BossMovement.push(p, kb);
                        p.setFireTicks(60);
                    }
                }
            }
        }, 1L, 2L);
    }

    private void executeCataclysmicSupernova(Location bossLoc) {
        if (entity == null || !entity.isValid()) return;
        addOverheat(18, "Supernova charge");
        double radius = config.getDouble("valerius.combat.cataclysmic-supernova.radius", 12.0);
        double damage = config.getDouble("valerius.combat.cataclysmic-supernova.damage", 24.0);
        int telegraphTicks = config.getInt("valerius.combat.cataclysmic-supernova.telegraph-ticks", 40);

        for (Player p : barManager.getViewers()) {
            TextUtil.sendTitle(p, "&4&l⚠ CATACLYSMIC SUPERNOVA ⚠", "&cLeave the marked " + String.format("%.0f", radius) + "m blast radius!", 5, 30, 10);
            p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.5f, 0.8f);
        }

        entity.setVelocity(new Vector(0, 0.25, 0));

        runAbilityTimer(new java.util.function.Consumer<BukkitTask>() {
            int elapsed = 0;
            @Override
            public void accept(BukkitTask task) {
                if (!active || entity == null || !entity.isValid()) {
                    task.cancel();
                    return;
                }
                elapsed += 4;
                Location bLoc = bossLoc.clone();

                for (int deg = 0; deg < 360; deg += 15) {
                    double rad = Math.toRadians(deg);
                    double x = bLoc.getX() + (radius * Math.cos(rad));
                    double z = bLoc.getZ() + (radius * Math.sin(rad));
                    bLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, x, bLoc.getY(), z, 1, 0, 0, 0, 0, null, true);
                    bLoc.getWorld().spawnParticle(Particle.FLAME, x, bLoc.getY(), z, 1, 0, 0, 0, 0, null, true);
                }
                bLoc.getWorld().spawnParticle(Particle.LAVA, bLoc, 10, 2.0, 1.0, 2.0, 0.1, null, true);
                bLoc.getWorld().playSound(bLoc, Sound.BLOCK_BEACON_POWER_SELECT, 1.2f, 0.6f + (elapsed * 0.02f));

                if (elapsed >= telegraphTicks) {
                    task.cancel();

                    try {
                        bLoc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, bLoc, 4, 2.0, 1.0, 2.0, 0, null, true);
                        bLoc.getWorld().spawnParticle(Particle.FIREWORK, bLoc.clone().add(0, 1.5, 0), 25, 0.5, 0.5, 0.5, 0.1, null, true);
                    } catch (Throwable ignored) {}
                    bLoc.getWorld().playSound(bLoc, Sound.ENTITY_GENERIC_EXPLODE, 2.5f, 0.5f);
                    bLoc.getWorld().playSound(bLoc, Sound.ITEM_TOTEM_USE, 1.0f, 0.6f);

                    for (Player p : barManager.getViewers()) {
                        if (p.getWorld() != null && p.getWorld().equals(bLoc.getWorld())) {
                            double dist = p.getLocation().distance(bLoc);
                            if (dist <= radius) {
                                p.damage(damage, entity);
                                p.setFireTicks(160);
                                Vector blast = p.getLocation().toVector().subtract(bLoc.toVector()).normalize().multiply(1.8).setY(0.6);
                net.rocky.coresuite.module.worldbosses.combat.BossMovement.push(p, blast);
                                sendCombatAlert(p, StatusBadge.ASHEN_SLAM, "&4Lethal Supernova detonation struck you!", 3);
                            }
                        }
                    }
                }
            }
        }, 0L, 4L);
    }

    private void executeSearingSiphon(Location bossLoc) {
        // Quenched status completely extinguishes the siphon
        if (isQuenched()) {
            return;
        }
        addOverheat(6, "Searing Siphon");

        double drainDmg = config.getDouble("valerius.combat.searing-siphon.drain-damage", 4.0);
        double baseHeal = config.getDouble("valerius.combat.searing-siphon.heal-per-player", 20.0);
        double witheredBonus = config.getDouble("valerius.combat.searing-siphon.withered-bonus-heal", 20.0);
        double maxHealPerPulse = config.getDouble("valerius.combat.searing-siphon.max-heal-per-pulse", 80.0);
        double healingMultiplier = net.rocky.coresuite.module.worldbosses.combat.EncounterBalance.finite(
                config.getDouble("valerius.combat.searing-siphon.healing-multiplier", .75), .75, 0, 1);
        maxHealPerPulse *= healingMultiplier;
        double totalHealed = 0.0;

        for (Player p : barManager.getViewers()) {
            if (p == null || !p.isOnline() || p.isDead() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            if (p.getWorld() == null || !p.getWorld().equals(bossLoc.getWorld())) continue;
            if (domainManager != null && !domainManager.isInDomain(p.getLocation())) continue;

            boolean hasWither = isSiphonAfflicted(p);
            double healAmt = (hasWither ? (baseHeal + witheredBonus) : baseHeal) * healingMultiplier;
            double actualHeal = 0;

            if (totalHealed < maxHealPerPulse) {
                actualHeal = heal(Math.min(healAmt, maxHealPerPulse - totalHealed));
                totalHealed += actualHeal;
            }
            p.damage(drainDmg, entity);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_HURT, 0.9f, 0.7f);

            if (hasWither) {
                sendCombatAlert(p, StatusBadge.SEARING_SIPHON, "&cValerius leeches &e" + String.format("%.0f", actualHeal) + " HP &cfrom your affliction!", 2);
            } else {
                sendCombatAlert(p, StatusBadge.SEARING_SIPHON, "&cValerius drains &e" + String.format("%.0f", actualHeal) + " HP &cfrom your vital heat!", 2);
            }

            Location pLoc = p.getLocation().add(0, 1.0, 0);
            Vector step = bossLoc.clone().add(0, 1.5, 0).toVector().subtract(pLoc.toVector()).multiply(0.2);
            for (int i = 0; i < 5; i++) {
                p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, pLoc.clone().add(step.clone().multiply(i)), 2, 0.05, 0.05, 0.05, 0.01);
                p.getWorld().spawnParticle(Particle.FLAME, pLoc.clone().add(step.clone().multiply(i)), 1, 0.05, 0.05, 0.05, 0.01);
            }
        }
    }

    protected boolean isSiphonAfflicted(Player player) {
        return player.hasPotionEffect(PotionEffectType.WITHER) || player.getFireTicks() > 0;
    }

    private void executeAshenSlam(Location bossLoc) {
        if (isExposed()) return;
        double radius = config.getDouble("valerius.combat.ashen-slam.radius", 5.0);
        double damage = config.getDouble("valerius.combat.ashen-slam.damage", 14.0);
        double kbForce = config.getDouble("valerius.combat.ashen-slam.knockback", 1.2);
        int fireSec = config.getInt("valerius.combat.ashen-slam.fire-seconds", 4);

        boolean hitAny = false;
        for (Player p : barManager.getViewers()) {
            if (p.getWorld() == null || !p.getWorld().equals(bossLoc.getWorld())) continue;
            if (p.getLocation().distanceSquared(bossLoc) <= (radius * radius)) {
                hitAny = true;
                p.damage(damage, entity);
                p.setFireTicks(fireSec * 20);
                Vector kb = p.getLocation().toVector().subtract(bossLoc.toVector()).normalize().multiply(kbForce);
                kb.setY(0.45);
                net.rocky.coresuite.module.worldbosses.combat.BossMovement.push(p, kb);
                sendCombatAlert(p, StatusBadge.ASHEN_SLAM, "&cValerius shatters the earth! Knocked back!", 2);
            }
        }

        if (hitAny) {
            addOverheat(8, "Ashen Slam");
            bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.75f);
            bossLoc.getWorld().playSound(bossLoc, Sound.BLOCK_ANVIL_LAND, 1.5f, 0.6f);
            bossLoc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, bossLoc, 1);
            bossLoc.getWorld().spawnParticle(Particle.LAVA, bossLoc, 20, 1.0, 0.5, 1.0, 0.1);
            bossLoc.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, bossLoc, 25, 1.5, 0.5, 1.5, 0.05);
        }
    }
    @Override public boolean testAbility(String ability) {
        if (!active || !isAwakened || entity == null || !abilityCombatAllowed()) return false;
        switch (ability.toLowerCase(java.util.Locale.ROOT)) {
            case "mortar" -> executeCalderaMortar(entity.getLocation());
            case "slam" -> executeAshenSlam(entity.getLocation());
            case "geyser" -> executeVolcanicGeyser(entity.getLocation());
            case "rush" -> executeMagmaRush(entity.getLocation());
            case "supernova" -> executeCataclysmicSupernova(entity.getLocation());
            default -> { return false; }
        }
        return true;
    }

    private void executeCalderaMortar(Location bossLoc) {
        if (isQuenched() || isExposed()) return;
        Player target = barManager.getViewers().stream().filter(WorldBoss::eligibleFighter)
                .filter(p -> p.getWorld().equals(bossLoc.getWorld()) && p.getLocation().distanceSquared(bossLoc) >= 49)
                .findFirst().orElse(null);
        if (target == null) return;
        Location impact = target.getLocation().clone();
        double radius = Math.max(1, Math.min(8, config.getDouble("valerius.combat.caldera-mortar.splash-radius", 4)));
        int warningTicks = Math.max(25, Math.min(100, config.getInt("valerius.combat.caldera-mortar.telegraph-ticks", 40)));
        sendCombatAlert(target, StatusBadge.CALDERA_MORTAR, "&cMortar incoming! Leave the marked circle!", 3);
        runAbilityTimer(new java.util.function.Consumer<BukkitTask>() {
            int elapsed;
            public void accept(BukkitTask task) {
                if (isQuenched() || isExposed()) { task.cancel(); return; }
                for (int angle = 0; angle < 360; angle += 30) {
                    double rad = Math.toRadians(angle);
                    impact.getWorld().spawnParticle(Particle.FLAME, impact.clone().add(Math.cos(rad) * radius, .15,
                            Math.sin(rad) * radius), 1, 0, 0, 0, 0);
                }
                elapsed += 5;
                if (elapsed < warningTicks) return;
                task.cancel();
                impact.getWorld().spawnParticle(Particle.EXPLOSION, impact, 1);
                impact.getWorld().playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, .8f);
                for (Player fighter : barManager.getViewers()) {
                    if (fighter.getWorld().equals(impact.getWorld()) && fighter.getLocation().distanceSquared(impact) <= radius * radius) {
                        fighter.damage(config.getDouble("valerius.combat.caldera-mortar.damage", 12), entity);
                        applyBossEffect(fighter, new PotionEffect(PotionEffectType.WITHER,
                                Math.max(1, config.getInt("valerius.combat.caldera-mortar.wither-seconds", 5)) * 20, 0));
                    }
                }
                addOverheat(10, "Caldera Mortar");
            }
        }, 1, 5);
    }

    public boolean applyQuenched(int seconds) {
        if (isQuenched()) return false;
        seconds = Math.max(1, Math.min(20, seconds));
        this.quenchedUntil = System.currentTimeMillis() + (seconds * 1000L);
        interruptCasts();
        this.overheat = Math.max(0, overheat - Math.max(1, config.getDouble("valerius.combat.overheat.water-cooling", 35.0)));
        if (entity != null && entity.isValid()) {
            entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.5f, 0.6f);
            entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.5f, 0.8f);
            entity.getWorld().spawnParticle(Particle.SPLASH, entity.getLocation().add(0, 1.5, 0), 40, 0.5, 0.8, 0.5, 0.2);
            for (Player p : barManager.getViewers()) {
                TextUtil.sendTitle(p, "&b&l✦ QUENCHED! ✦", "&fSteam pours from the vanguard's fractured armour.", 5, 35, 10);
                sendCombatAlert(p, StatusBadge.QUENCHED, "&bThe molten shell fractures!", seconds);
            }
        }
        return true;
    }

    public boolean isQuenched() {
        return System.currentTimeMillis() < quenchedUntil;
    }
    public double healFromAttack(double damage) {
        if (isQuenched() || !Double.isFinite(damage) || damage <= 0) return 0;
        return heal(Math.min(30.0, damage * 0.75));
    }
    public double getOverheat() { return overheat; }
    public boolean isExposed() { return System.currentTimeMillis() < exposedUntil; }
    @Override protected boolean abilityCombatAllowed() { return !isExposed(); }
    private void addOverheat(double amount, String source) {
        if (isExposed()) return;
        double threshold = Math.max(25, config.getDouble("valerius.combat.overheat.threshold", 100));
        overheat = Math.min(threshold, overheat + Math.max(0, amount));
        if (overheat < threshold) return;
        overheat = 0;
        int seconds = Math.max(3, config.getInt("valerius.combat.overheat.exposed-seconds", 8));
        exposedUntil = System.currentTimeMillis() + seconds * 1000L;
        interruptCasts();
        if (entity == null || !entity.isValid()) return;
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_BLASTFURNACE_FIRE_CRACKLE, 2f, .55f);
        entity.getWorld().spawnParticle(Particle.EXPLOSION, entity.getLocation().add(0, 1.2, 0), 3, .5, .6, .5, .05);
        for (Player player : barManager.getViewers()) {
            TextUtil.sendTitle(player, "&b&lCORE EXPOSED!", "&f" + source + " overheated Valerius — strike now!", 5, seconds * 20, 8);
        }
    }

    @Override
    public double calculateIncomingDamage(Player player, double originalDamage, ItemStack weapon) {
        if (isExposed()) originalDamage *= Math.max(1.0, config.getDouble("valerius.combat.overheat.exposed-damage-multiplier", 1.35));
        if (weapon == null || weapon.getType() == Material.AIR) return originalDamage;

        String matName = weapon.getType().name();

        // Weakness: Pickaxes shatter Valerius's calcified magma armor (+150% DMG natively, 3.5x when Quenched)
        if (matName.endsWith("_PICKAXE")) {
            double baseBonus = config.getDouble("valerius.weakness.pickaxe-damage-multiplier", 2.50);
            double finalBonus = isQuenched() ? (baseBonus + 1.0) : baseBonus;

            try {
                player.playSound(entity.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.2f, 1.4f);
                entity.getWorld().spawnParticle(Particle.CRIT, entity.getLocation().add(0, 1.2, 0), 18, 0.4, 0.4, 0.4, 0.15);
            } catch (Throwable ignored) {}

            String alertText = isQuenched()
                    ? "&b&l✦ CRACKING IMPACT! &eQuenched Armor Shattered! &b(+250% DMG!)"
                    : "&e&l✦ CRACKING IMPACT! &aPickaxe shattered armor! &e(+150% DMG!)";
            sendCombatAlert(player, StatusBadge.QUENCHED, alertText, 2);
            TextUtil.sendActionBar(player, alertText);
            return originalDamage * finalBonus;
        }

        // Resistant to Swords, Bows, Crossbows, Tridents (smooth 0.70x without metallic clang)
        if (matName.endsWith("_SWORD") || matName.equals("BOW") || matName.equals("CROSSBOW") || matName.equals("TRIDENT")) {
            double mult = config.getDouble("valerius.weakness.sword-damage-multiplier", 0.70);
            return originalDamage * mult;
        }

        return originalDamage;
    }

    @Override
    public void handleAntiCheese(Player player) {
        if (entity == null || !entity.isValid() || player == null || !player.isOnline()) return;
        if (net.rocky.coresuite.module.worldbosses.combat.BossMovement.embedded(player)) return;
        if (player.getWorld() == null || !player.getWorld().equals(entity.getWorld())) return;

        Location bLoc = entity.getLocation();
        Location pLoc = player.getLocation();

        // 1. Anti-Pillaring (4+ blocks higher Y-level)
        if (pLoc.getY() - bLoc.getY() >= config.getDouble("valerius.anti-cheese.pillaring-y-threshold", 4.0)) {
            if (!antiCheeseReady(player)) return;
            // Gravity Tether: Pull down to boss feet
            if (domainManager == null || !net.rocky.coresuite.module.worldbosses.combat.BossMovement.teleport(
                    player, bLoc.clone().add(bLoc.getDirection().multiply(2.5)), domainManager::isInDomain)) return;
            player.damage(4.0, entity);
            applyBossEffect(player, new PotionEffect(PotionEffectType.SLOWNESS, 80, 2, false, false, true));
            player.playSound(pLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 1.4f);
            TextUtil.sendActionBar(player, "&c&l[!] GRAVITY TETHER: &cValerius violently yanked you from your pillar!");
            return;
        }

        // 2. Anti-Digging / Bunker Check
        Block head = pLoc.clone().add(0, 2, 0).getBlock();
        if (head.getType().isSolid() && pLoc.getBlock().getRelative(0, -1, 0).getType().isSolid()) {
            if (!antiCheeseReady(player)) return;
            // Enclosed in a hole -> Seismic Stomp
            entity.getWorld().spawnParticle(Particle.EXPLOSION, pLoc, 2);
            player.damage(config.getDouble("valerius.anti-cheese.burrow-shockwave-damage", 12.0), entity);
            player.playSound(pLoc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.2f);
            TextUtil.sendActionBar(player, "&c&l[!] SEISMIC STOMP: &cBurrowing triggered a shockwave!");
        }
    }
}
