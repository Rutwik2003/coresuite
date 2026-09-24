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
import org.bukkit.entity.Arrow;
import org.bukkit.entity.CaveSpider;
import org.bukkit.entity.EvokerFangs;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public class AethelgardBoss extends WorldBoss {

    private long fireSilenceUntil = 0;
    private final java.util.Set<LivingEntity> healingRoots = new java.util.HashSet<>();

    public AethelgardBoss(CoreSuitePlugin plugin, FileConfiguration config, BossLootManager lootManager) {
        super(plugin, BossType.AETHELGARD, config, lootManager);
    }

    @Override
    protected void setupPhases() {
        phases.clear();
        phases.add(new BossPhase(1, 50.0, "Overgrowth", BossBar.Color.GREEN, "OVERGROWTH"));
        phases.add(new BossPhase(2, 20.0, "The Harvest", BossBar.Color.YELLOW, "THE_HARVEST"));
        phases.add(new BossPhase(3, 0.0, "Deadwood", BossBar.Color.RED, "DEADWOOD"));
    }

    @Override
    public void setupEntity(LivingEntity entity) {
        // Husks can spawn as babies; scaling alone does not remove the baby variant.
        if (entity instanceof Zombie zombie) zombie.setBaby(false);
        EntityEquipment eq = entity.getEquipment();
        if (eq != null) {
            eq.setHelmet(new ItemStack(Material.CARVED_PUMPKIN));
            eq.setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
            eq.setLeggings(new ItemStack(Material.LEATHER_LEGGINGS));
            eq.setBoots(new ItemStack(Material.LEATHER_BOOTS));
            eq.setItemInMainHand(new ItemStack(Material.WOODEN_AXE));
            eq.setHelmetDropChance(0f);
            eq.setChestplateDropChance(0f);
            eq.setLeggingsDropChance(0f);
            eq.setBootsDropChance(0f);
            eq.setItemInMainHandDropChance(0f);
        }

        applyAttributeSafely(entity, net.rocky.coresuite.common.compat.PaperCompat.SCALE, config.getDouble("aethelgard.scale", 1.55));
        applyAttributeSafely(entity, net.rocky.coresuite.common.compat.PaperCompat.MOVEMENT_SPEED, config.getDouble("aethelgard.movement-speed", 0.24));
        applyAttributeSafely(entity, net.rocky.coresuite.common.compat.PaperCompat.MAX_HEALTH, this.maxHealth);
        applyAttributeSafely(entity, net.rocky.coresuite.common.compat.PaperCompat.ATTACK_DAMAGE, config.getDouble("aethelgard.melee-damage", 18));
    }

    @Override
    public void onCombatTick(long tickCount) {
        if (entity == null || !entity.isValid()) return;

        Location bossLoc = entity.getLocation();
        boolean isBurning = entity.getFireTicks() > 0 || System.currentTimeMillis() < fireSilenceUntil;

        healingRoots.removeIf(root -> !root.isValid() || root.isDead());
        int phase = currentPhase == null ? 1 : currentPhase.getPhaseNumber();
        if ((phase >= 3 || getRemainingHealing() <= 0) && !healingRoots.isEmpty()) {
            for (LivingEntity root : healingRoots) { root.remove(); minions.remove(root); }
            healingRoots.clear();
            broadcastToDomain(phase >= 3 ? "&6Deadwood phase! &fAethelgard's roots withered; healing has ended."
                    : "&6Roots exhausted! &fAethelgard has spent his healing reserve.");
        }
        if (tickCount % Math.max(15, config.getInt("aethelgard.combat.healing-roots.interval-seconds", 25)) == 0
                && healingRoots.isEmpty() && !isBurning && phase < 3 && getRemainingHealing() > 0) spawnHealingRoots(bossLoc);

        for (LivingEntity root : healingRoots) {
            root.customName(TextUtil.colorize(isBurning ? "&6Healing Root — suppressed by fire"
                    : "&aHealing Root — destroy to stop healing"));
        }
        // Both healing sources are controlled by the roots, fire counter, phase and reserve.
        if (canChannelRoots(phase, healingRoots.size(), isBurning, getRemainingHealing())) {
            double requested = 0;
            if (tickCount % 2 == 0) requested += config.getDouble("aethelgard.combat.healing-roots.heal-amount", 35);
            if (tickCount % 3 == 0) requested += config.getDouble("aethelgard.weakness.soil-regen-hp", 15);
            if (heal(requested) > 0) {
                World world = bossLoc.getWorld();
                for (LivingEntity root : healingRoots) {
                    Location from = root.getLocation().add(0, 1, 0);
                    Vector direction = bossLoc.clone().add(0, 1.5, 0).toVector().subtract(from.toVector());
                    double distance = direction.length();
                    if (distance > .1) {
                        direction.normalize();
                        for (double step = .5; step < Math.min(distance, 64); step += 1.2)
                            world.spawnParticle(Particle.COMPOSTER, from.clone().add(direction.clone().multiply(step)), 1, 0, 0, 0, 0);
                    }
                }
            }
        }

        // Melee: Overgrowth Cleave (every 7 seconds when enemies within 5m)
        if (abilityReady(tickCount, "overgrowth-cleave", 7)) {
            executeOvergrowthCleave(bossLoc);
        }

        // Ranged: Briar Volley (every 7 seconds when enemies > 6m, offset by 3s)
        if (abilityReady(tickCount, "briar-volley", 7)) {
            executeBriarVolley(bossLoc);
        }

        // Phase specific mechanics
        if (currentPhase != null) {
            int phaseNum = currentPhase.getPhaseNumber();

            // Phase 2 & 3: Strangling Brambles (every 14s)
            if (phaseNum >= 2) {
                if (abilityReady(tickCount, "strangling-brambles", 14)) {
                    executeStranglingBrambles(bossLoc);
                }
            }

            // Phase 3 Ultimate: Timber Stomp (every 20s)
            if (phaseNum == 3) {
                if (abilityReady(tickCount, "timber-stomp", 20)) {
                    executeTimberStomp(bossLoc);
                }
            }

            switch (phaseNum) {
                case 1 -> { // Overgrowth: Spawns Baby Zombie Rootling Ghouls
                    if (tickCount % 25 == 0 && !isBurning) {
                        spawnRootlingGhouls(bossLoc);
                    }
                    for (Player p : barManager.getViewers()) {
                        handleAntiCheese(p);
                    }
                }
                case 2 -> { // The Harvest: Spores & Vortex Pull
                    for (Player p : barManager.getViewers()) {
                        handleAntiCheese(p);
                        applyBossEffect(p, new PotionEffect(PotionEffectType.POISON, 60, 0, false, false, true));
                        if (tickCount % 10 == 0) {
                            applyBossEffect(p, new PotionEffect(PotionEffectType.NAUSEA, 100, 0, false, false, true));
                        }
                    }

                    // Vortex Pull every 15 seconds
                    if (tickCount % 15 == 0) {
                        bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.5f, 0.8f);
                        for (Player p : barManager.getViewers()) {
                            if (p.getWorld() == null || !p.getWorld().equals(bossLoc.getWorld())) continue;
                            Vector pull = bossLoc.toVector().subtract(p.getLocation().toVector()).normalize().multiply(1.4);
                            pull.setY(0.4);
                net.rocky.coresuite.module.worldbosses.combat.BossMovement.push(p, pull);
                            sendCombatAlert(p, StatusBadge.VORTEX_ROOTS, "&2Vortex roots pulled you inward!", 2);
                        }
                    }
                }
                case 3 -> { // Deadwood: Desolation & Lifesteal
                    for (Player p : barManager.getViewers()) {
                        handleAntiCheese(p);
                    }
                }
            }
        }
    }

    private void executeStranglingBrambles(Location bossLoc) {
        if (barManager.getViewers().isEmpty()) return;
        java.util.List<Player> targets = new java.util.ArrayList<>(barManager.getViewers());
        targets.removeIf(p -> p == null || !p.isOnline() || p.getWorld() == null || !p.getWorld().equals(bossLoc.getWorld()));
        if (targets.isEmpty()) return;

        int count = Math.min(2, targets.size());
        for (int i = 0; i < count; i++) {
            Player p = targets.get(i);
            Location targetGround = p.getLocation().clone();
            double damage = config.getDouble("aethelgard.combat.strangling-brambles.damage", 14.0);
            int cageSec = config.getInt("aethelgard.combat.strangling-brambles.cage-duration-seconds", 4);
            int telegraphTicks = config.getInt("aethelgard.combat.strangling-brambles.telegraph-ticks", 30);

            sendCombatAlert(p, StatusBadge.ROOTED, "&2Writhing brambles surround your footing! ESCAPE!", 2);

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
                        double x = targetGround.getX() + (2.0 * Math.cos(rad));
                        double z = targetGround.getZ() + (2.0 * Math.sin(rad));
                        targetGround.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, x, targetGround.getY() + 0.2, z, 1, 0, 0, 0, 0, null, true);
                        targetGround.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, x, targetGround.getY() + 0.2, z, 1, 0, 0, 0, 0, null, true);
                    }
                    targetGround.getWorld().playSound(targetGround, Sound.BLOCK_GRASS_STEP, 0.8f, 0.8f);

                    if (elapsed >= telegraphTicks) {
                        task.cancel();
                        targetGround.getWorld().playSound(targetGround, Sound.BLOCK_WOOD_BREAK, 1.5f, 0.7f);
                        targetGround.getWorld().playSound(targetGround, Sound.BLOCK_ROOTS_BREAK, 1.5f, 0.6f);
                        try {
                            targetGround.getWorld().spawnParticle(Particle.BLOCK, targetGround.clone().add(0, 1.0, 0), 30, 0.5, 0.8, 0.5, 0.1, Material.OAK_LEAVES.createBlockData());
                        } catch (Throwable ignored) {}

                        for (Player fighter : barManager.getViewers()) {
                            if (fighter.getWorld() != null && fighter.getWorld().equals(targetGround.getWorld())) {
                                if (fighter.getLocation().distanceSquared(targetGround) <= 4.0) {
                                    fighter.damage(damage, entity);
                                    applyBossEffect(fighter, new PotionEffect(PotionEffectType.SLOWNESS, cageSec * 20, 3, false, false, true));
                                    applyBossEffect(fighter, new PotionEffect(PotionEffectType.POISON, cageSec * 20, 1, false, false, true));
                                    sendCombatAlert(fighter, StatusBadge.ROOTED, "&2Strangling roots ensnared you! Movement bound!", cageSec);
                                }
                            }
                        }
                    }
                }
            }, 0L, 3L);
        }
    }
    @Override public boolean testAbility(String ability) {
        if (!active || !isAwakened || entity == null) return false;
        switch (ability.toLowerCase(java.util.Locale.ROOT)) {
            case "roots" -> { if (healingRoots.isEmpty()) spawnHealingRoots(entity.getLocation()); }
            case "cleave" -> executeOvergrowthCleave(entity.getLocation());
            case "brambles" -> executeStranglingBrambles(entity.getLocation());
            case "stomp" -> executeTimberStomp(entity.getLocation());
            default -> { return false; }
        }
        return true;
    }

    static boolean canChannelRoots(int phase, int roots, boolean burning, double reserve) {
        return phase < 3 && roots > 0 && !burning && reserve > 0;
    }

    public boolean isHealingRoot(LivingEntity target) { return healingRoots.contains(target); }

    private void spawnHealingRoots(Location origin) {
        int count = Math.max(1, Math.min(3, config.getInt("aethelgard.combat.healing-roots.count", 2)));
        for (int i = 0; i < count && liveMinionCount() < 7; i++) {
            double angle = Math.PI * 2 * i / count;
            Location at = net.rocky.coresuite.module.worldbosses.combat.BossMovement.findLanding(
                    origin.clone().add(Math.cos(angle) * 5, 0, Math.sin(angle) * 5), domainManager::isInDomain);
            if (at == null) continue;
            Zombie root = at.getWorld().spawn(at, Zombie.class);
            root.setAI(false);
            root.setSilent(true);
            root.setGlowing(true);
            root.setRemoveWhenFarAway(false);
            root.customName(TextUtil.colorize("&aHealing Root &7— destroy to stop healing"));
            root.setCustomNameVisible(true);
            applyAttributeSafely(root, net.rocky.coresuite.common.compat.PaperCompat.MAX_HEALTH, 20);
            root.setHealth(20);
            root.getEquipment().setHelmet(new ItemStack(Material.OAK_LEAVES));
            root.getEquipment().setHelmetDropChance(0);
            root.getEquipment().setItemInMainHand(null);
            registerMinion(root);
            healingRoots.add(root);
            // Roots persist until destroyed, the healing reserve is spent, or Deadwood begins.
        }
        if (!healingRoots.isEmpty()) broadcastToDomain("&aHealing roots appeared! &fDestroy the glowing roots to stop Aethelgard's healing.");
    }

    private void executeTimberStomp(Location bossLoc) {
        if (entity == null || !entity.isValid()) return;
        double damage = config.getDouble("aethelgard.combat.timber-stomp.damage", 22.0);
        int telegraphTicks = config.getInt("aethelgard.combat.timber-stomp.telegraph-ticks", 35);

        for (Player p : barManager.getViewers()) {
            TextUtil.sendTitle(p, "&a&l⚠ TIMBER STOMP ⚠", "&2Concentric root fangs are erupting!", 5, 25, 10);
            p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.5f, 0.6f);
        }

        runAbilityTimer(new java.util.function.Consumer<BukkitTask>() {
            int elapsed = 0;
            @Override
            public void accept(BukkitTask task) {
                if (!active || entity == null || !entity.isValid()) {
                    task.cancel();
                    return;
                }
                elapsed += 5;
                Location bLoc = bossLoc.clone();
                bLoc.getWorld().spawnParticle(Particle.CRIT, bLoc.clone().add(0, 1, 0), 10, 0.8, 0.5, 0.8, 0.1, null, true);
                bLoc.getWorld().playSound(bLoc, Sound.BLOCK_WOOD_FALL, 1.2f, 0.7f);

                if (elapsed >= telegraphTicks) {
                    task.cancel();

                    double[] radii = {3.5, 7.0, 11.0};
                    long[] delays = {0L, 8L, 16L};

                    for (int wave = 0; wave < radii.length; wave++) {
                        double r = radii[wave];
                        long delay = delays[wave];

                        runAbilityLater(() -> {
                            if (!active || entity == null || !entity.isValid()) return;
                            Location centerLoc = bossLoc.clone();
                            World w = centerLoc.getWorld();
                            if (w == null) return;

                            w.playSound(centerLoc, Sound.ENTITY_EVOKER_FANGS_ATTACK, 1.8f, 0.8f);
                            w.playSound(centerLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.6f);

                            int fangsCount = (int) (r * 3.5);
                            for (int f = 0; f < fangsCount; f++) {
                                double angle = 2 * Math.PI * f / fangsCount;
                                double fx = centerLoc.getX() + (r * Math.cos(angle));
                                double fz = centerLoc.getZ() + (r * Math.sin(angle));
                                int fy = w.getHighestBlockYAt((int) Math.floor(fx), (int) Math.floor(fz));
                                Location fangLoc = new Location(w, fx, fy, fz);
                                EvokerFangs fang = trackAbilityEntity(w.spawn(fangLoc, EvokerFangs.class));
                                fang.setOwner(entity);
                            }

                            for (Player p : barManager.getViewers()) {
                                if (p.getWorld() != null && p.getWorld().equals(w)) {
                                    double dist = p.getLocation().distance(centerLoc);
                                    if (Math.abs(dist - r) <= 2.2) {
                                        p.damage(damage, entity);
                                        applyBossEffect(p, new PotionEffect(PotionEffectType.SLOWNESS, 60, 2, false, false, true));
                                        sendCombatAlert(p, StatusBadge.OVERGROWTH_CLEAVE, "&aPrimeval Timber Stomp crushed you!", 2);
                                    }
                                }
                            }
                        }, delay);
                    }
                }
            }
        }, 0L, 5L);
    }

    private void spawnRootlingGhouls(Location bossLoc) {
        if (bossLoc == null || bossLoc.getWorld() == null || bossLoc.getWorld().getDifficulty() == org.bukkit.Difficulty.PEACEFUL) {
            return;
        }
        int maxActive = Math.max(1, config.getInt("aethelgard.combat.rootling-ghouls.max-active", 5));
        int requested = Math.max(1, config.getInt("aethelgard.combat.rootling-ghouls.spawn-count", 2));
        // Larger raids earn pressure through a few extra minions, never an unbounded pile.
        int count = Math.min(maxActive - liveMinionCount(), requested + Math.max(0, (getScaledPlayers() - 1) / 3));
        if (count <= 0) return;
        double maxHp = config.getDouble("aethelgard.combat.rootling-ghouls.max-health", 35.0);
        double speed = config.getDouble("aethelgard.combat.rootling-ghouls.movement-speed", 0.32);

        for (int i = 0; i < count; i++) {
            Location spawnLoc = bossLoc.clone().add((Math.random() - 0.5) * 4, 0, (Math.random() - 0.5) * 4);
            Zombie ghoul = bossLoc.getWorld().spawn(spawnLoc, Zombie.class);
            ghoul.setBaby();
            ghoul.setCustomNameVisible(true);
            ghoul.customName(TextUtil.colorize("&2&lRootling Ghoul"));

            EntityEquipment eq = ghoul.getEquipment();
            if (eq != null) {
                eq.setHelmet(new ItemStack(Material.OAK_LEAVES));
                eq.setItemInMainHand(new ItemStack(Material.IRON_SWORD));
                eq.setHelmetDropChance(0f);
                eq.setItemInMainHandDropChance(0f);
            }

            applyAttributeSafely(ghoul, net.rocky.coresuite.common.compat.PaperCompat.MAX_HEALTH, maxHp);
            applyAttributeSafely(ghoul, net.rocky.coresuite.common.compat.PaperCompat.MOVEMENT_SPEED, speed);

            NamespacedKey minionKey = new NamespacedKey(plugin, "boss_minion");
            ghoul.getPersistentDataContainer().set(minionKey, PersistentDataType.BYTE, (byte) 1);
            registerMinion(ghoul);
        }
        bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_ZOMBIE_AMBIENT, 1.2f, 1.4f);
    }

    private void executeOvergrowthCleave(Location bossLoc) {
        double radius = config.getDouble("aethelgard.combat.overgrowth-cleave.radius", 5.0);
        double damage = config.getDouble("aethelgard.combat.overgrowth-cleave.damage", 16.0);
        int slowSec = config.getInt("aethelgard.combat.overgrowth-cleave.slowness-duration", 4);
        int slowAmp = config.getInt("aethelgard.combat.overgrowth-cleave.slowness-amplifier", 1);

        Vector bossDir = entity.getLocation().getDirection().normalize();
        boolean hitAny = false;

        for (Player p : barManager.getViewers()) {
            if (p.getWorld() == null || !p.getWorld().equals(bossLoc.getWorld())) continue;
            Location pLoc = p.getLocation();
            if (pLoc.distanceSquared(bossLoc) <= (radius * radius)) {
                Vector toPlayer = pLoc.toVector().subtract(bossLoc.toVector()).normalize();
                if (bossDir.dot(toPlayer) >= -0.2) {
                    hitAny = true;
                    p.damage(damage, entity);
                    applyBossEffect(p, new PotionEffect(PotionEffectType.SLOWNESS, slowSec * 20, slowAmp, false, false, true));
                    Vector kb = toPlayer.multiply(1.1);
                    kb.setY(0.35);
                net.rocky.coresuite.module.worldbosses.combat.BossMovement.push(p, kb);
                    sendCombatAlert(p, StatusBadge.OVERGROWTH_CLEAVE, "&2Aethelgard cleaved with massive oak branches!", 2);
                }
            }
        }

        if (hitAny) {
            bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.5f, 0.6f);
            bossLoc.getWorld().playSound(bossLoc, Sound.BLOCK_WOOD_BREAK, 1.5f, 0.8f);
            bossLoc.getWorld().spawnParticle(Particle.SWEEP_ATTACK, bossLoc.clone().add(bossDir.multiply(1.5)).add(0, 1.2, 0), 3, 0.3, 0.3, 0.3, 0.05);
            bossLoc.getWorld().spawnParticle(Particle.SPORE_BLOSSOM_AIR, bossLoc.clone().add(0, 1.5, 0), 15, 0.5, 0.5, 0.5, 0.05);
            try {
                bossLoc.getWorld().spawnParticle(Particle.BLOCK, bossLoc.clone().add(0, 1.0, 0), 20, 0.5, 0.5, 0.5, 0.1, Material.OAK_LEAVES.createBlockData());
            } catch (Throwable ignored) {}
        }
    }

    private void executeBriarVolley(Location bossLoc) {
        double minRange = config.getDouble("aethelgard.combat.briar-volley.min-range", 6.0);
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
            Location shootLoc = bossLoc.clone().add(0, 1.8, 0);
            Vector baseDir = target.getEyeLocation().toVector().subtract(shootLoc.toVector()).normalize();

            double[] offsets = {-0.12, 0.0, 0.12};
            NamespacedKey projectileKey = new NamespacedKey(plugin, "boss_projectile");

            for (double offset : offsets) {
                Vector arrowDir = baseDir.clone();
                arrowDir.setX(arrowDir.getX() + (offset * -baseDir.getZ()));
                arrowDir.setZ(arrowDir.getZ() + (offset * baseDir.getX()));
                arrowDir.setY(arrowDir.getY() + 0.10); // Ballistic elevation
                arrowDir.normalize().multiply(1.85);

                Arrow arrow = trackAbilityEntity(bossLoc.getWorld().spawn(shootLoc, Arrow.class));
                arrow.setShooter(entity);
                arrow.setDamage(0); // Damage is applied once by the encounter hit handler.
                arrow.setPickupStatus(org.bukkit.entity.AbstractArrow.PickupStatus.DISALLOWED);
                arrow.setVelocity(arrowDir);
                arrow.getPersistentDataContainer().set(projectileKey, PersistentDataType.STRING, "BRIAR_VOLLEY");
            }

            bossLoc.getWorld().playSound(bossLoc, Sound.ENTITY_ARROW_SHOOT, 1.5f, 0.7f);
            bossLoc.getWorld().playSound(bossLoc, Sound.ENCHANT_THORNS_HIT, 1.5f, 1.2f);
            sendCombatAlert(target, StatusBadge.BRIAR_VOLLEY, "&aRazor-sharp brambles pierced your armor!", 2);
        }
    }

    private boolean isNaturalSoil(Material mat) {
        return mat == Material.GRASS_BLOCK || mat == Material.DIRT || mat == Material.PODZOL
                || mat == Material.COARSE_DIRT || mat == Material.MUD || mat == Material.MOSS_BLOCK
                || mat == Material.ROOTED_DIRT;
    }

    @Override
    public double calculateIncomingDamage(Player player, double originalDamage, ItemStack weapon) {
        if (weapon == null || weapon.getType() == Material.AIR) return originalDamage;

        String matName = weapon.getType().name();

        // Bow Spamming Cheese Check -> Absorbed as HEAL!
        if (matName.equals("BOW") || matName.equals("CROSSBOW")) {
            double multiplier = net.rocky.coresuite.module.worldbosses.combat.EncounterBalance.finite(config.getDouble("aethelgard.weakness.arrow-damage-multiplier", .5), .5, .1, 1);
            TextUtil.sendActionBar(player, "&aThe King's bark resists arrows. Axes and fire are more effective!");
            return originalDamage * multiplier;
        }

        // Fire silence check
        if (entity.getFireTicks() > 0) {
            this.fireSilenceUntil = System.currentTimeMillis() + (config.getInt("aethelgard.weakness.fire-silence-seconds", 6) * 1000L);
        }

        // Weakness: Axes deal +100% damage (2.0x)
        if (matName.endsWith("_AXE")) {
            double mult = config.getDouble("aethelgard.weakness.axe-damage-multiplier", 2.00);
            player.playSound(entity.getLocation(), Sound.ITEM_AXE_STRIP, 1.2f, 1.0f);
            entity.getWorld().spawnParticle(Particle.SWEEP_ATTACK, entity.getLocation().add(0, 1.2, 0), 8, 0.3, 0.3, 0.3, 0.1);
            return originalDamage * mult;
        }

        // Phase 1 Thorns Recoil for non-axe melee hits
        if (currentPhase != null && currentPhase.getPhaseNumber() == 1) {
            double recoil = originalDamage * config.getDouble("aethelgard.phases.phase-1.thorns-recoil-percent", 0.25);
            player.damage(recoil, entity);
            player.playSound(player.getLocation(), Sound.ENCHANT_THORNS_HIT, 1f, 1f);
        }

        return originalDamage;
    }

    @Override
    public void handleAntiCheese(Player player) {
        if (entity == null || !entity.isValid() || player == null || !player.isOnline()) return;
        if (player.getWorld() == null || !player.getWorld().equals(entity.getWorld())) return;

        Location bLoc = entity.getLocation();
        Location pLoc = player.getLocation();

        // Anti-Pillar or Anti-Digging: Spawns a line of Evoker Fangs
        if (pLoc.getY() - bLoc.getY() >= 4.0 || bLoc.getY() - pLoc.getY() >= 3.0) {
            if (!antiCheeseReady(player)) return;
            Vector dir = pLoc.toVector().subtract(bLoc.toVector()).normalize();
            for (int i = 1; i <= 6; i++) {
                Location fangLoc = bLoc.clone().add(dir.clone().multiply(i * 1.5));
                fangLoc.setY(pLoc.getY());
                EvokerFangs fang = trackAbilityEntity(fangLoc.getWorld().spawn(fangLoc, EvokerFangs.class));
                fang.setOwner(entity);
            }
            TextUtil.sendActionBar(player, "&2&l[!] GRASPING ROOTS: &2Living roots tear through your perch!");
        }
    }
}
