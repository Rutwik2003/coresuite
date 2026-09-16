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
    }

    @Override
    public void onCombatTick(long tickCount) {
        if (entity == null || !entity.isValid()) return;

        Location bossLoc = entity.getLocation();
        if (isExposed()) {
            bossLoc.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, bossLoc.clone().add(0, 1.2, 0), 18, .45, .7, .45, .03);
            for (Player player : barManager.getViewers()) sendCombatAlert(player, StatusBadge.QUENCHED,
                    "&bOVERHEATED CORE EXPOSED! &fAttack now for bonus damage.", 2);
            return;
        }

        // Quenched steam particles
        if (isQuenched()) {
            bossLoc.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, bossLoc.clone().add(0, 1.5, 0), 6, 0.3, 0.4, 0.3, 0.02);
        }

        // Passive: Searing Siphon (triggers every 6 seconds, leaches health from withered hunters; quenched status suppresses it)
        int siphonInterval = Math.max(1, config.getInt("valerius.combat.searing-siphon.interval-seconds", 6));
        if (tickCount % siphonInterval == 0) {
            executeSearingSiphon(bossLoc);
        }

        // Melee: Ashen Slam (every 8 seconds when enemies within 5m)
        if (tickCount % 8 == 0) {
            executeAshenSlam(bossLoc);
        }

        // Ranged: Caldera Mortar (every 6 seconds when enemies > 7m)
        if (tickCount % 6 == 0) {
            executeCalderaMortar(bossLoc);
        }

        // Phase specific mechanics
        if (currentPhase != null) {
            int phaseNum = currentPhase.getPhaseNumber();

            // Phase 2 & 3: Volcanic Geysers (every 10s) and Magma Rush (every 12s)
            if (phaseNum >= 2) {
                if (tickCount % 10 == 0) {
                    executeVolcanicGeyser(bossLoc);
                }
                if (tickCount % 12 == 0) {
                    executeMagmaRush(bossLoc);
                }
            }

            // Phase 3 Ultimate: Cataclysmic Supernova (every 24s)
            if (phaseNum == 3) {
                if (tickCount % 24 == 0) {
                    executeCataclysmicSupernova(bossLoc);
                }
            }

            switch (phaseNum) {
                case 1 -> { // The Forge
                    for (Player p : barManager.getViewers()) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.HUNGER, 60, 1, false, false, true));
                        if (tickCount % 20 == 0) { // Every 20s
                            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false, true));
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

                        // Vaporize water in 6-block radius around boss
                        for (int x = -5; x <= 5; x++) {
                            for (int y = -2; y <= 3; y++) {
                                for (int z = -5; z <= 5; z++) {
                                    Block b = bossLoc.clone().add(x, y, z).getBlock();
                                    if (b.getType() == Material.WATER) {
                                        b.setType(Material.AIR);
                                        b.getWorld().spawnParticle(Particle.CLOUD, b.getLocation().add(0.5, 0.5, 0.5), 4, 0.2, 0.2, 0.2, 0.05);
                                    }
                                }
                            }
                        }
                    }
                }
                case 3 -> { // Ash Void
                    for (Player p : barManager.getViewers()) {
                        p.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0, false, false, true));
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

            Bukkit.getScheduler().runTaskTimer(plugin, new java.util.function.Consumer<BukkitTask>() {
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
                                    fighter.setVelocity(up);
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

        Bukkit.getScheduler().runTaskTimer(plugin, new java.util.function.Consumer<BukkitTask>() {
            int ticks = 0;
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
                    if (p.getWorld() != null && p.getWorld().equals(curr.getWorld()) && p.getLocation().distanceSquared(curr) <= 4.0) {
                        p.damage(damage, entity);
                        Vector kb = p.getLocation().toVector().subtract(curr.toVector()).normalize().multiply(1.2).setY(0.4);
                        p.setVelocity(kb);
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
            TextUtil.sendTitle(p, "&4&l⚠ CATACLYSMIC SUPERNOVA ⚠", "&cRetreat outside 12m or face lethal devastation!", 5, 30, 10);
            p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.5f, 0.8f);
        }

        entity.setVelocity(new Vector(0, 0.25, 0));

        Bukkit.getScheduler().runTaskTimer(plugin, new java.util.function.Consumer<BukkitTask>() {
            int elapsed = 0;
            @Override
            public void accept(BukkitTask task) {
                if (!active || entity == null || !entity.isValid()) {
                    task.cancel();
                    return;
                }
                elapsed += 4;
                Location bLoc = entity.getLocation();

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
                                p.setVelocity(blast);
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

        double drainDmg = config.getDouble("valerius.combat.searing-siphon.drain-damage", 2.0);
        double healAmt = config.getDouble("valerius.combat.searing-siphon.heal-per-player", 5.0);
        double maxHealPerPulse = config.getDouble("valerius.combat.searing-siphon.max-heal-per-pulse", 15.0);
        double totalHealed = 0.0;

        for (Player p : barManager.getViewers()) {
            if (p.getWorld() == null || !p.getWorld().equals(bossLoc.getWorld())) continue;
            if (p.hasPotionEffect(PotionEffectType.WITHER)) {
                if (totalHealed < maxHealPerPulse) {
                    double actualHeal = Math.min(healAmt, maxHealPerPulse - totalHealed);
                    heal(actualHeal);
                    totalHealed += actualHeal;
                }
                p.damage(drainDmg, entity);
                p.getWorld().playSound(p.getLocation(), Sound.ENTITY_WITHER_HURT, 0.9f, 0.7f);
                sendCombatAlert(p, StatusBadge.SEARING_SIPHON, "&cValerius leaches &e" + String.format("%.0f", healAmt) + " HP &cfrom your Wither affliction!", 2);

                Location pLoc = p.getLocation().add(0, 1.0, 0);
                Vector step = bossLoc.clone().add(0, 1.5, 0).toVector().subtract(pLoc.toVector()).multiply(0.2);
                for (int i = 0; i < 5; i++) {
                    p.getWorld().spawnParticle(Particle.WITCH, pLoc.clone().add(step.clone().multiply(i)), 2, 0.05, 0.05, 0.05, 0.01);
                }
            }
        }
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
                p.setVelocity(kb);
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

    private void executeCalderaMortar(Location bossLoc) {
        double minRange = config.getDouble("valerius.combat.caldera-mortar.min-range", 7.0);
        double minRangeSq = minRange * minRange;
        Player target = null;
        for (Player p : barManager.getViewers()) {
            if (p.getWorld() == null || !p.getWorld().equals(bossLoc.getWorld())) continue;
            if (p.getLocation().distanceSquared(bossLoc) >= minRangeSq) {
                target = p;
                break;
            }
        }

        if (target != null) {
            addOverheat(10, "Caldera Mortar");
            final Player finalTarget = target;
            Location fireLoc = bossLoc.clone().add(0, 2.2, 0);
            Vector toTarget = finalTarget.getEyeLocation().toVector().subtract(fireLoc.toVector());
            Vector dir = toTarget.clone().normalize();

            Fireball fb = bossLoc.getWorld().spawn(fireLoc.add(dir.clone().multiply(1.2)), Fireball.class);
            fb.setShooter(entity);
            fb.setYield(0.0f);
            fb.setIsIncendiary(false);
            fb.setDirection(dir.clone().multiply(0.95));
            fb.setVelocity(dir.clone().multiply(0.95));

            NamespacedKey mortarKey = new NamespacedKey(plugin, "boss_projectile");
            fb.getPersistentDataContainer().set(mortarKey, PersistentDataType.STRING, "CALDERA_MORTAR");

            bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_GHAST_SHOOT, 1.8f, 0.6f);
            bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_BLAZE_SHOOT, 1.5f, 0.8f);
            sendCombatAlert(finalTarget, StatusBadge.CALDERA_MORTAR, "&4Valerius launches molten mortar artillery tracking your position!", 3);

            // Active Homing Task (runs every 2 ticks for up to 60 ticks / 3 seconds)
            Bukkit.getScheduler().runTaskTimer(plugin, task -> {
                if (!fb.isValid() || fb.isDead() || !finalTarget.isOnline() || finalTarget.isDead()) {
                    task.cancel();
                    return;
                }

                Location fbLoc = fb.getLocation();
                Location targetLoc = finalTarget.getEyeLocation();
                if (fbLoc.getWorld() == null || !fbLoc.getWorld().equals(targetLoc.getWorld())) {
                    task.cancel();
                    return;
                }

                double distSq = fbLoc.distanceSquared(targetLoc);
                if (distSq <= 2.25) { // Proximity detonation within 1.5 blocks
                    WorldBossesModule module = (WorldBossesModule) plugin.getModuleManager().getModule("world-bosses");
                    if (module != null) {
                        module.explodeCalderaMortar(fbLoc, fb);
                    }
                    fb.remove();
                    task.cancel();
                    return;
                }

                // Smooth steering towards the player
                Vector desired = targetLoc.toVector().subtract(fbLoc.toVector()).normalize().multiply(0.90);
                Vector current = fb.getDirection();
                Vector steered = current.clone().multiply(0.65).add(desired.multiply(0.35)).normalize().multiply(0.90);
                fb.setDirection(steered);
                fb.setVelocity(steered);

                // Trailing flame & smoke particles with force=true
                fbLoc.getWorld().spawnParticle(Particle.FLAME, fbLoc, 4, 0.15, 0.15, 0.15, 0.02, null, true);
                fbLoc.getWorld().spawnParticle(Particle.LAVA, fbLoc, 1, 0.05, 0.05, 0.05, 0.0, null, true);
                fbLoc.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, fbLoc, 1, 0.05, 0.05, 0.05, 0.01, null, true);
            }, 2L, 2L);
        }
    }

    public void applyQuenched(int seconds) {
        this.quenchedUntil = System.currentTimeMillis() + (seconds * 1000L);
        this.overheat = Math.max(0, overheat - Math.max(1, config.getDouble("valerius.combat.overheat.water-cooling", 35.0)));
        if (entity != null && entity.isValid()) {
            entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.5f, 0.6f);
            entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1.5f, 0.8f);
            entity.getWorld().spawnParticle(Particle.SPLASH, entity.getLocation().add(0, 1.5, 0), 40, 0.5, 0.8, 0.5, 0.2);
            for (Player p : barManager.getViewers()) {
                TextUtil.sendTitle(p, "&b&l✦ QUENCHED! ✦", "&eValerius brittle! Pickaxes +150% DMG (" + seconds + "s)", 5, 35, 10);
                sendCombatAlert(p, StatusBadge.QUENCHED, "&bArmor Brittle! Pickaxes +150% DMG (" + seconds + "s)", seconds);
            }
        }
    }

    public boolean isQuenched() {
        return System.currentTimeMillis() < quenchedUntil;
    }
    public double getOverheat() { return overheat; }
    public boolean isExposed() { return System.currentTimeMillis() < exposedUntil; }
    private void addOverheat(double amount, String source) {
        if (isExposed()) return;
        double threshold = Math.max(25, config.getDouble("valerius.combat.overheat.threshold", 100));
        overheat = Math.min(threshold, overheat + Math.max(0, amount));
        if (overheat < threshold) return;
        overheat = 0;
        int seconds = Math.max(3, config.getInt("valerius.combat.overheat.exposed-seconds", 8));
        exposedUntil = System.currentTimeMillis() + seconds * 1000L;
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
        if (player.getWorld() == null || !player.getWorld().equals(entity.getWorld())) return;

        Location bLoc = entity.getLocation();
        Location pLoc = player.getLocation();

        // 1. Anti-Pillaring (4+ blocks higher Y-level)
        if (pLoc.getY() - bLoc.getY() >= config.getDouble("valerius.anti-cheese.pillaring-y-threshold", 4.0)) {
            // Gravity Tether: Pull down to boss feet
            player.teleport(bLoc.clone().add(bLoc.getDirection().multiply(1.5)));
            player.setVelocity(new Vector(0, -1.0, 0));
            player.damage(4.0, entity);
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 80, 2, false, false, true));
            player.playSound(pLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1f, 1.4f);
            TextUtil.sendActionBar(player, "&c&l[!] GRAVITY TETHER: &cValerius violently yanked you from your pillar!");
            return;
        }

        // 2. Anti-Digging / Bunker Check
        Block head = pLoc.clone().add(0, 2, 0).getBlock();
        if (head.getType().isSolid() && pLoc.getBlock().getRelative(0, -1, 0).getType().isSolid()) {
            // Enclosed in a hole -> Seismic Stomp
            entity.getWorld().spawnParticle(Particle.EXPLOSION, pLoc, 2);
            player.damage(config.getDouble("valerius.anti-cheese.burrow-shockwave-damage", 12.0), entity);
            player.playSound(pLoc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1.2f);
            TextUtil.sendActionBar(player, "&c&l[!] SEISMIC STOMP: &cBurrowing triggered a shockwave!");
        }
    }
}
