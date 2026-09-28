package net.rocky.coresuite.module.worldbosses.boss;

import net.kyori.adventure.bossbar.BossBar;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.worldbosses.rewards.BossLootManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;
import net.rocky.coresuite.module.worldbosses.combat.StatusBadge;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.CaveSpider;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Silverfish;
import org.bukkit.entity.Snowball;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Random;

public class XolthosBoss extends WorldBoss {

    private long stunnedUntil = 0;
    private final Random random = new Random();
    private boolean stealthed = false;
    private long stealthEnd = 0;
    private long lastStealthTime = 0;
    private final java.util.Map<java.util.UUID, Long> snareRecovery = new java.util.HashMap<>();
    private long stunRecoveryUntil;

    public XolthosBoss(CoreSuitePlugin plugin, FileConfiguration config, BossLootManager lootManager) {
        super(plugin, BossType.XOLTHOS, config, lootManager);
    }

    @Override
    protected void setupPhases() {
        phases.clear();
        phases.add(new BossPhase(1, 70.0, "The Trap", BossBar.Color.PURPLE, "THE_TRAP"));
        phases.add(new BossPhase(2, 30.0, "The Brood", BossBar.Color.YELLOW, "THE_BROOD"));
        phases.add(new BossPhase(3, 0.0, "Frenzy", BossBar.Color.RED, "FRENZY"));
    }

    @Override
    public void setupEntity(LivingEntity entity) {
        applyAttributeSafely(entity, net.rocky.coresuite.common.compat.PaperCompat.SCALE, config.getDouble("xolthos.scale", 2.1));
        applyAttributeSafely(entity, net.rocky.coresuite.common.compat.PaperCompat.MOVEMENT_SPEED, config.getDouble("xolthos.movement-speed", 0.35));
        applyAttributeSafely(entity, net.rocky.coresuite.common.compat.PaperCompat.ATTACK_DAMAGE, config.getDouble("xolthos.melee-damage", 22.0));
        applyAttributeSafely(entity, net.rocky.coresuite.common.compat.PaperCompat.MAX_HEALTH, this.maxHealth);
    }

    @Override
    public void onCombatTick(long tickCount) {
        if (entity == null || !entity.isValid()) return;

        Location bossLoc = entity.getLocation();
        if (lastStealthTime == 0) lastStealthTime = System.currentTimeMillis();
        entity.setAI(!isStunned());

        // Stunned glow particles
        if (isStunned()) {
            if (stealthed) {
                breakStealth(false);
            }
            bossLoc.getWorld().spawnParticle(Particle.GLOW, bossLoc.clone().add(0, 0.8, 0), 12, 0.4, 0.4, 0.4, 0.02);
            bossLoc.getWorld().spawnParticle(Particle.WAX_OFF, bossLoc.clone().add(0, 0.8, 0), 5, 0.3, 0.3, 0.3, 0.05);
            return; // Boss is stunned and takes no offensive action this tick
        }

        // Stalker Invisibility / Stealth Loop (10-15s cloaking)
        if (isStealthed()) {
            if (System.currentTimeMillis() >= stealthEnd) {
                breakStealth(false);
            } else {
                // Skittering ambient step sounds and subtle shadow dust
                bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_SPIDER_STEP, 1.2f, 1.6f);
                bossLoc.getWorld().spawnParticle(Particle.SMOKE, bossLoc.clone().add(0, 0.3, 0), 3, 0.2, 0.1, 0.2, 0.01, null, true);

                // Stalk the closest player rapidly while invisible
                Player closest = null;
                double closestDistSq = Double.MAX_VALUE;
                for (Player p : barManager.getViewers()) {
                    if (p.getWorld() != null && p.getWorld().equals(bossLoc.getWorld())) {
                        double dSq = p.getLocation().distanceSquared(bossLoc);
                        if (dSq < closestDistSq) {
                            closestDistSq = dSq;
                            closest = p;
                        }
                    }
                }
                if (closest != null) {
                    Vector stalkVec = closest.getLocation().toVector().subtract(bossLoc.toVector());
                    if (stalkVec.lengthSquared() > 0.0001) stalkVec.normalize().multiply(0.42);
                    stalkVec.setY(entity.getVelocity().getY());
                    entity.setVelocity(stalkVec);
                }
            }
        } else {
            // Periodic Stealth Trigger: randomly enters invisibility every 30s for 12s
            int stealthInterval = config.getInt("xolthos.combat.stealth.interval-seconds", 30);
            int stealthDur = config.getInt("xolthos.combat.stealth.duration-seconds", 12);
            if ((System.currentTimeMillis() - lastStealthTime) >= (stealthInterval * 1000L)) {
                enterStealth(stealthDur);
            }
        }

        if (isStealthed()) return;

        // Melee: Neurotoxin Leap (every 8 seconds when not stunned)
        if (abilityReady(tickCount, "neurotoxin-leap", 8)) {
            executeNeurotoxinLeap(bossLoc);
        }

        // Ranged: Venomous Web Spit (every 6 seconds when enemies > 6m)
        if (abilityReady(tickCount, "web-spit", 14)) {
            executeVenomousWebSpit(bossLoc);
        }

        // Phase specific mechanics
        if (currentPhase != null) {
            int phaseNum = currentPhase.getPhaseNumber();

            // Phase 2 & 3: Shadow Step (every 15s)
            if (phaseNum >= 2) {
                if (abilityReady(tickCount, "shadow-step", 15)) {
                    executeShadowStep(bossLoc);
                }
            }

            // Phase 3 Ultimate: Acid Rain Bombardment (every 22s)
            if (phaseNum == 3) {
                if (abilityReady(tickCount, "acid-rain", 22)) {
                    executeAcidRain(bossLoc);
                }
            }

            switch (phaseNum) {
                case 1 -> { // The Trap: Darkness & Ambush
                    for (Player p : barManager.getViewers()) {
                        handleAntiCheese(p);
                        applyBossEffect(p, new PotionEffect(PotionEffectType.DARKNESS, 80, 0, false, false, true));
                        if (tickCount % 15 == 0) {
                            applyBossEffect(p, new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false, true));
                        }
                    }
                }
                case 2 -> { // The Brood: Web Snare & Cave Spider Broodlings
                    for (Player p : barManager.getViewers()) {
                        handleAntiCheese(p);
                        applyBossEffect(p, new PotionEffect(PotionEffectType.POISON, 60, 1, false, false, true));

                        // Cobweb trapping around fighters every 10 seconds
                        if (tickCount % 10 == 0 && random.nextDouble() < 0.4) {
                            Block b = p.getLocation().getBlock();
                            placeTemporaryBlock(b, Material.COBWEB, 120L);
                        }
                    }

                    // Cave Spider Broodling summons every 18 seconds
                    if (tickCount % 18 == 0) {
                        spawnCaveSpiderBroodlings(bossLoc);
                    }
                }
                case 3 -> { // Frenzy: Slowness & Necrosis (Regen Disabled)
                    for (Player p : barManager.getViewers()) {
                        handleAntiCheese(p);
                        applyBossEffect(p, new PotionEffect(PotionEffectType.SLOWNESS, 60, 2, false, false, true));

                        // Necrosis suppresses natural healing through the regain-health listener.
                        // Never erase food/saturation restored by consumables.
                    }
                }
            }
        }
    }

    public boolean suppressesNaturalHealing(Player player) {
        return active && isAwakened && !isStunned() && !isStealthed()
                && currentPhase != null && currentPhase.getPhaseNumber() == 3
                && config.getBoolean("xolthos.phases.phase-3.necrosis", true)
                && barManager != null && barManager.getViewers().contains(player)
                && domainManager != null && domainManager.isInDomain(player.getLocation());
    }

    private void spawnCaveSpiderBroodlings(Location bossLoc) {
        if (bossLoc == null || bossLoc.getWorld() == null || bossLoc.getWorld().getDifficulty() == org.bukkit.Difficulty.PEACEFUL) {
            return;
        }
        int maxActive = Math.max(1, config.getInt("xolthos.combat.broodlings.max-active", 6));
        int requested = Math.max(1, config.getInt("xolthos.combat.broodlings.spawn-count", 3));
        int count = Math.min(maxActive - liveMinionCount(), requested + Math.max(0, (getScaledPlayers() - 1) / 4));
        if (count <= 0) return;
        double maxHp = config.getDouble("xolthos.combat.broodlings.max-health", 25.0);
        double speed = config.getDouble("xolthos.combat.broodlings.movement-speed", 0.34);
        double scale = config.getDouble("xolthos.combat.broodlings.scale", 0.75);

        for (int i = 0; i < count; i++) {
            Location minionLoc = bossLoc.clone().add((random.nextDouble() - 0.5) * 4, 0, (random.nextDouble() - 0.5) * 4);
            CaveSpider spider = bossLoc.getWorld().spawn(minionLoc, CaveSpider.class);
            spider.customName(TextUtil.colorize("&5&lCave Spider Broodling"));
            spider.setCustomNameVisible(true);
            applyAttributeSafely(spider, net.rocky.coresuite.common.compat.PaperCompat.SCALE, scale);
            applyAttributeSafely(spider, net.rocky.coresuite.common.compat.PaperCompat.MAX_HEALTH, maxHp);
            applyAttributeSafely(spider, net.rocky.coresuite.common.compat.PaperCompat.MOVEMENT_SPEED, speed);

            NamespacedKey minionKey = new NamespacedKey(plugin, "boss_minion");
            spider.getPersistentDataContainer().set(minionKey, PersistentDataType.BYTE, (byte) 1);
            registerMinion(spider);
        }
        bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_SPIDER_AMBIENT, 1.2f, 1.4f);
    }

    private void executeNeurotoxinLeap(Location bossLoc) {
        if (barManager.getViewers().isEmpty()) return;

        java.util.List<Player> viewerList = new java.util.ArrayList<>(barManager.getViewers());
        viewerList.removeIf(p -> p == null || !p.isOnline() || p.getWorld() == null || !p.getWorld().equals(bossLoc.getWorld()));
        if (viewerList.isEmpty()) return;
        Player target = viewerList.get(random.nextInt(viewerList.size()));
        if (target == null || !target.isOnline()) return;

        Location marked = target.getLocation().clone();
        sendCombatAlert(target, StatusBadge.NEUROTOXIN_LEAP, "&dXol'thos prepares to leap! Move away!", 2);
        marked.getWorld().spawnParticle(Particle.WITCH, marked, 24, .8, .1, .8, .01);
        runAbilityLater(() -> {
            if (entity == null || !entity.isValid() || isStunned() || isStealthed()) return;
            Vector leap = marked.toVector().subtract(entity.getLocation().toVector());
            if (leap.lengthSquared() > .01) leap.normalize().multiply(1.5);
            leap.setY(0.42);
            entity.setVelocity(leap);
            bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_SPIDER_AMBIENT, 1.5f, 1.6f);

            double damage = config.getDouble("xolthos.combat.neurotoxin-leap.damage", 18.0);
            int nauseaSec = config.getInt("xolthos.combat.neurotoxin-leap.nausea-duration", 6);
            int poisonSec = config.getInt("xolthos.combat.neurotoxin-leap.poison-duration", 6);
            int slowSec = config.getInt("xolthos.combat.neurotoxin-leap.slowness-duration", 4);

            runAbilityLater(() -> {
                if (!active || entity == null || !entity.isValid()) return;
                Location landingLoc = entity.getLocation();
                landingLoc.getWorld().playSound(landingLoc, Sound.ENTITY_SPIDER_HURT, 1.5f, 0.8f);
                landingLoc.getWorld().playSound(landingLoc, Sound.BLOCK_SLIME_BLOCK_FALL, 1.5f, 0.6f);
                landingLoc.getWorld().spawnParticle(Particle.SQUID_INK, landingLoc, 20, 0.5, 0.5, 0.5, 0.05);
                landingLoc.getWorld().spawnParticle(Particle.ITEM_SLIME, landingLoc, 15, 0.5, 0.5, 0.5, 0.05);

                for (Player p : barManager.getViewers()) {
                    if (p.getWorld() == null || !p.getWorld().equals(landingLoc.getWorld())) continue;
                    if (p.getLocation().distanceSquared(landingLoc) <= 9.0) { // 3.0m radius
                        p.damage(damage, entity);
                        applyBossEffect(p, new PotionEffect(PotionEffectType.NAUSEA, nauseaSec * 20, 0, false, false, true));
                        applyBossEffect(p, new PotionEffect(PotionEffectType.POISON, poisonSec * 20, 1, false, false, true));
                        applyBossEffect(p, new PotionEffect(PotionEffectType.SLOWNESS, slowSec * 20, 2, false, false, true));
                        sendCombatAlert(p, StatusBadge.NEUROTOXIN_LEAP, "&dParalyzing neurotoxin clouds your senses!", 3);
                    }
                }
            }, 15L);
        }, Math.max(10, Math.min(80, config.getInt("xolthos.combat.neurotoxin-leap.telegraph-ticks", 20))));
    }

    private void executeVenomousWebSpit(Location bossLoc) {
        double minRange = config.getDouble("xolthos.combat.web-spit.min-range", 6.0);
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
            Location shootLoc = bossLoc.clone().add(0, 1.2, 0);
            Vector dir = target.getEyeLocation().toVector().subtract(shootLoc.toVector()).normalize();
            dir.setY(dir.getY() + 0.12);
            dir.normalize().multiply(2.1);

            Snowball spit = trackAbilityEntity(bossLoc.getWorld().spawn(shootLoc, Snowball.class));
            spit.setShooter(entity);
            spit.setVelocity(dir);

            NamespacedKey projectileKey = new NamespacedKey(plugin, "boss_projectile");
            spit.getPersistentDataContainer().set(projectileKey, PersistentDataType.STRING, "WEB_SPIT");

            bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_LLAMA_SPIT, 1.5f, 0.6f);
            sendCombatAlert(target, StatusBadge.WEB_SPIT, "&5Xol'thos spat venomous webbing directly at you!", 2);
        }
    }

    public boolean isStealthed() {
        return stealthed;
    }

    public void enterStealth(int durationSeconds) {
        if (entity == null || !entity.isValid() || isStunned()) return;
        stealthed = true;
        stealthEnd = System.currentTimeMillis() + (durationSeconds * 1000L);
        lastStealthTime = System.currentTimeMillis();

        entity.setInvisible(true);
        entity.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, durationSeconds * 20, 1, false, false, false));
        entity.setCustomNameVisible(false);

        double baseSpeed = config.getDouble("xolthos.movement-speed", 0.35);
        double speedBoost = config.getDouble("xolthos.combat.stealth.speed-boost", 0.30);
        applyAttributeSafely(entity, net.rocky.coresuite.common.compat.PaperCompat.MOVEMENT_SPEED, baseSpeed * (1.0 + speedBoost));

        Location loc = entity.getLocation();
        World w = loc.getWorld();
        if (w != null) {
            w.spawnParticle(Particle.SQUID_INK, loc.clone().add(0, 0.5, 0), 35, 0.5, 0.5, 0.5, 0.1, null, true);
            w.spawnParticle(Particle.PORTAL, loc.clone().add(0, 0.5, 0), 25, 0.5, 0.5, 0.5, 0.2, null, true);
            w.playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.5f, 0.6f);
            w.playSound(loc, Sound.AMBIENT_CAVE, 1.5f, 0.8f);
        }

        for (Player p : barManager.getViewers()) {
            TextUtil.sendTitle(p, "&8&k!&5&l STALKER INVISIBILITY &8&k!", "&7Xol'thos vanished into the shadows! Watch your back!", 5, 40, 10);
            sendCombatAlert(p, StatusBadge.EVADED, "&5Xol'thos slipped into stealth! Ambush incoming in " + durationSeconds + "s!", durationSeconds);
        }
    }

    public void breakStealth(boolean ambushHit) {
        if (!stealthed) return;
        stealthed = false;
        if (entity != null && entity.isValid()) {
            entity.setInvisible(false);
            entity.removePotionEffect(PotionEffectType.INVISIBILITY);
            entity.setCustomNameVisible(true);
            double baseSpeed = config.getDouble("xolthos.movement-speed", 0.35);
            applyAttributeSafely(entity, net.rocky.coresuite.common.compat.PaperCompat.MOVEMENT_SPEED, baseSpeed);

            Location loc = entity.getLocation();
            World w = loc.getWorld();
            if (w != null) {
                w.spawnParticle(Particle.DRAGON_BREATH, loc.clone().add(0, 0.5, 0), 25, 0.5, 0.5, 0.5, 0.05, 1.0f, true);
                w.playSound(loc, Sound.ENTITY_SPIDER_AMBIENT, 1.5f, 0.7f);
            }
        }
    }

    public void triggerAmbushStrike(Player victim) {
        if (!stealthed || entity == null || !entity.isValid() || victim == null) return;
        breakStealth(true);

        double mult = config.getDouble("xolthos.combat.stealth.ambush-damage-multiplier", 1.75);
        double baseDmg = config.getDouble("xolthos.melee-damage", 22.0);
        double bonusDmg = baseDmg * (mult - 1.0);

        victim.damage(bonusDmg, entity);
        applyBossEffect(victim, new PotionEffect(PotionEffectType.BLINDNESS, 60, 0, false, false, true));
        applyBossEffect(victim, new PotionEffect(PotionEffectType.POISON, 100, 2, false, false, true));
        victim.playSound(victim.getLocation(), Sound.ENTITY_PHANTOM_BITE, 1.5f, 0.7f);

        sendCombatAlert(victim, StatusBadge.EVADED, "&5✦ AMBUSH STRIKE! &cXol'thos struck from behind! (+75% DMG & Blindness)", 3);
    }

    private void executeShadowStep(Location bossLoc) {
        if (barManager.getViewers().isEmpty() || entity == null || !entity.isValid()) return;
        java.util.List<Player> viewers = new java.util.ArrayList<>(barManager.getViewers());
        viewers.removeIf(p -> p == null || !p.isOnline() || p.getWorld() == null || !p.getWorld().equals(bossLoc.getWorld()));
        if (viewers.isEmpty()) return;

        Player target = viewers.get(random.nextInt(viewers.size()));
        Location behind = target.getLocation().clone().subtract(target.getLocation().getDirection().normalize().multiply(1.5));
        behind.setY(target.getLocation().getY());

        bossLoc.getWorld().spawnParticle(Particle.PORTAL, bossLoc, 20, 0.3, 0.5, 0.3, 0.1, null, true);
        bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.4f, 1.2f);

        sendCombatAlert(target, StatusBadge.EVADED, "&5Shadow ambush behind you! Move away from the portal!", 2);
        behind.getWorld().spawnParticle(Particle.PORTAL, behind, 35, .8, .2, .8, .04);
        runAbilityLater(() -> {
            Location landing = net.rocky.coresuite.module.worldbosses.scheduler.BossSpawnScheduler.findNearbyLocation(
                    behind, loc -> domainManager.isInDomain(loc));
            if (landing == null || !entity.isValid() || isStunned() || !entity.teleport(landing)) return;
            landing.getWorld().playSound(landing, Sound.ENTITY_ENDERMAN_TELEPORT, 1.4f, 1.2f);
            double damage = config.getDouble("xolthos.combat.shadow-step.damage", 16.0);
            for (Player player : barManager.getViewers()) {
                if (player.getWorld().equals(landing.getWorld()) && player.getLocation().distanceSquared(landing) <= 9) {
                    player.damage(damage, entity);
                    applyBossEffect(player, new PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false, true));
                }
            }
        }, Math.max(20, config.getInt("xolthos.combat.shadow-step.telegraph-ticks", 30)));

    }
    @Override public boolean testAbility(String ability) {
        if (!active || !isAwakened || entity == null || !abilityCombatAllowed()) return false;
        switch (ability.toLowerCase(java.util.Locale.ROOT)) {
            case "shadowstep" -> executeShadowStep(entity.getLocation());
            case "leap" -> executeNeurotoxinLeap(entity.getLocation());
            case "acid" -> executeAcidRain(entity.getLocation());
            case "web" -> executeVenomousWebSpit(entity.getLocation());
            default -> { return false; }
        }
        return true;
    }

    private void executeAcidRain(Location bossLoc) {
        if (entity == null || !entity.isValid()) return;
        double radius = config.getDouble("xolthos.combat.acid-rain.radius", 7.0);
        double damage = config.getDouble("xolthos.combat.acid-rain.damage", 12.0);
        int telegraphTicks = config.getInt("xolthos.combat.acid-rain.telegraph-ticks", 30);

        for (Player p : barManager.getViewers()) {
            TextUtil.sendTitle(p, "&5&l⚠ ACID RAIN BOMBARDMENT ⚠", "&dToxic venom rain falling across the arena!", 5, 25, 10);
            p.playSound(p.getLocation(), Sound.ENTITY_LLAMA_SPIT, 1.8f, 0.5f);
        }

        for (int i = 0; i < 6; i++) {
            Location spitStart = bossLoc.clone().add(0, 1.5, 0);
            bossLoc.getWorld().spawnParticle(Particle.DRAGON_BREATH, spitStart, 15, 0.3, 0.8, 0.3, 0.1, 1.0f, true);
        }

        runAbilityTimer(new java.util.function.Consumer<BukkitTask>() {
            int elapsed = 0;
            @Override
            public void accept(BukkitTask task) {
                if (!active || entity == null || !entity.isValid()) {
                    task.cancel();
                    return;
                }
                elapsed += 3;
                Location bLoc = entity.getLocation();

                for (int deg = 0; deg < 360; deg += 30) {
                    double rad = Math.toRadians(deg);
                    double x = bLoc.getX() + (radius * Math.cos(rad));
                    double z = bLoc.getZ() + (radius * Math.sin(rad));
                    try {
                        bLoc.getWorld().spawnParticle(Particle.ITEM, x, bLoc.getY() + 0.1, z, 1, 0, 0, 0, 0, new ItemStack(Material.SLIME_BALL), true);
                    } catch (Throwable ignored) {
                        bLoc.getWorld().spawnParticle(Particle.DRAGON_BREATH, x, bLoc.getY() + 0.1, z, 1, 0, 0, 0, 0, 1.0f, true);
                    }
                }

                if (elapsed >= telegraphTicks) {
                    task.cancel();
                    bLoc.getWorld().playSound(bLoc, Sound.ENTITY_SPLASH_POTION_BREAK, 1.8f, 0.6f);
                    bLoc.getWorld().playSound(bLoc, Sound.BLOCK_FIRE_EXTINGUISH, 1.5f, 0.7f);

                    for (Player p : barManager.getViewers()) {
                        if (p.getWorld() != null && p.getWorld().equals(bLoc.getWorld())) {
                            if (p.getLocation().distance(bLoc) <= radius) {
                                p.damage(damage, entity);
                                applyBossEffect(p, new PotionEffect(PotionEffectType.POISON, 100, 1, false, false, true));
                                applyBossEffect(p, new PotionEffect(PotionEffectType.SLOWNESS, 80, 2, false, false, true));
                                sendCombatAlert(p, StatusBadge.WEB_SPIT, "&2Acidic venom scorched your skin! Drink healing potions!", 3);
                            }
                        }
                    }
                }
            }
        }, 0L, 3L);
    }

    public boolean applyStun(int seconds) {
        long now = System.currentTimeMillis();
        if (now < stunRecoveryUntil) return false;
        if (BorealisBoss.resistsCounter(config.getDouble("xolthos.weakness.night-vision-resist-chance", 50))) {
            // Shared by all throwers: a second potion cannot immediately reroll a failed attempt.
            stunRecoveryUntil = now + 10000L;
            if (barManager != null) broadcastToDomain("&5THE SHADOW HOLDS! &fThe widow recoils into the darkness.");
            return false;
        }
        seconds = Math.max(1, Math.min(12, seconds));
        if (stealthed) {
            breakStealth(false);
        }
        this.stunnedUntil = System.currentTimeMillis() + (seconds * 1000L);
        interruptCasts();
        if (entity != null && entity.isValid()) entity.setAI(false);
        this.stunRecoveryUntil = stunnedUntil + 10000L;
        if (entity != null && entity.isValid()) {
            Location loc = entity.getLocation();
            loc.getWorld().playSound(loc, Sound.ENTITY_SPIDER_DEATH, 1.8f, 0.6f);
            loc.getWorld().playSound(loc, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1.2f, 1.5f);
            try {
                loc.getWorld().spawnParticle(Particle.FIREWORK, loc.clone().add(0, 1, 0), 20, 0.3, 0.3, 0.3, 0.05);
                loc.getWorld().spawnParticle(Particle.GLOW, loc, 30, 0.5, 0.5, 0.5, 0.1);
            } catch (Throwable ignored) {}
            for (Player p : barManager.getViewers()) {
                TextUtil.sendTitle(p, "&d&l✦ BLINDED BY SIGHT! ✦", "&fThe widow reels as her shadows scatter!", 5, 35, 10);
                sendCombatAlert(p, StatusBadge.STUNNED, "&dThe widow's dance falters!", seconds);
            }
        }
        return true;
    }

    public boolean isStunned() {
        return System.currentTimeMillis() < stunnedUntil;
    }
    @Override protected boolean abilityCombatAllowed() { return !isStunned(); }

    @Override
    public double calculateIncomingDamage(Player player, double originalDamage, ItemStack weapon) {
        if (player == null) return originalDamage;

        // While stunned: Evasion drops to 0% and boss takes +50% damage
        if (isStunned()) {
            double vulnMult = config.getDouble("xolthos.weakness.stun-vulnerability-multiplier", 1.50);
            entity.getWorld().spawnParticle(Particle.CRIT, entity.getLocation().add(0, 0.8, 0), 12, 0.3, 0.3, 0.3, 0.1);
            player.playSound(entity.getLocation(), Sound.ENTITY_SPIDER_HURT, 1.2f, 0.7f);
            return originalDamage * vulnMult;
        }

        // Shadow phases replace random evasion: ordinary attacks always land outside stealth,
        // and Night Vision breaks stealth immediately through applyStun.
        if (isStealthed()) {
            Location loc = entity.getLocation();
            loc.getWorld().spawnParticle(Particle.PORTAL, loc.add(0, 0.5, 0), 20, 0.3, 0.3, 0.3, 0.1);
            loc.getWorld().playSound(loc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.2f, 1.5f);
            TextUtil.sendActionBar(player, "&5&l✦ SHADOW PHASE &7— The widow vanishes into the dark!");
            return originalDamage * .35;
        }

        return originalDamage;
    }

    @Override
    public void handleAntiCheese(Player player) {
        if (entity == null || !entity.isValid() || player == null || !player.isOnline()) return;
        if (player.getWorld() == null || !player.getWorld().equals(entity.getWorld())) return;

        Location bLoc = entity.getLocation();
        Location pLoc = player.getLocation();
        double dist = pLoc.distance(bLoc);

        // 1. Anti-Runner: Fleeing beyond 25 blocks triggers Cocoon Snare
        double runnerThresh = config.getDouble("xolthos.anti-cheese.runner-distance-threshold", 25.0);
        if (dist >= runnerThresh) {
            if (System.currentTimeMillis() < snareRecovery.getOrDefault(player.getUniqueId(), 0L)) return;
            snareRecovery.put(player.getUniqueId(), System.currentTimeMillis() + 18000L);
            int cocoonSec = config.getInt("xolthos.anti-cheese.cocoon-duration-seconds", 6);
            applyBossEffect(player, new PotionEffect(PotionEffectType.SLOWNESS, cocoonSec * 20, 4, false, false, true));
            applyBossEffect(player, new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false, true));
            player.playSound(pLoc, Sound.BLOCK_COBWEB_PLACE, 1.5f, 0.7f);
            pLoc.getWorld().spawnParticle(Particle.ITEM_COBWEB, pLoc.add(0, 1, 0), 25, 0.4, 0.4, 0.4, 0.05);
            TextUtil.sendActionBar(player, "&5&l[!] COCOON SNARE: &dSticky silk binds you for attempting to flee!");
            return;
        }

        // 2. Anti-Bunker: Phase Stalker (teleporting into bunker / summoning cave spider broodling)
        Block head = pLoc.clone().add(0, 2, 0).getBlock();
        Block feet = pLoc.getBlock();
        if (head.getType().isSolid() && feet.getRelative(0, -1, 0).getType().isSolid()) {
            if (!antiCheeseReady(player)) return;
            if (liveMinionCount() < Math.max(1, config.getInt("xolthos.combat.broodlings.max-active", 6))
                    && config.getBoolean("xolthos.anti-cheese.bunker-teleport", true) && pLoc.getWorld().getDifficulty() != org.bukkit.Difficulty.PEACEFUL) {
                CaveSpider cs = pLoc.getWorld().spawn(pLoc, CaveSpider.class);
                cs.customName(TextUtil.colorize("&5&lCave Spider Broodling"));
                cs.setCustomNameVisible(true);
                applyAttributeSafely(cs, net.rocky.coresuite.common.compat.PaperCompat.SCALE, 0.75);
                applyAttributeSafely(cs, net.rocky.coresuite.common.compat.PaperCompat.MAX_HEALTH, 25.0);
                applyAttributeSafely(cs, net.rocky.coresuite.common.compat.PaperCompat.MOVEMENT_SPEED, 0.34);
                NamespacedKey minionKey = new NamespacedKey(plugin, "boss_minion");
                cs.getPersistentDataContainer().set(minionKey, PersistentDataType.BYTE, (byte) 1);
                registerMinion(cs);

                player.playSound(pLoc, Sound.ENTITY_SPIDER_HURT, 1.2f, 1.0f);
                TextUtil.sendActionBar(player, "&5&l[!] PHASE STALKER: &dXol'thos infests your burrow!");
            }
        }
    }
}
