package net.rocky.coresuite.module.worldbosses.boss;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.worldbosses.combat.BossBarManager;
import net.rocky.coresuite.module.worldbosses.combat.BossDamageTracker;
import net.rocky.coresuite.module.worldbosses.combat.BossDomainManager;
import net.rocky.coresuite.module.worldbosses.rewards.BossLootManager;
import org.bukkit.Bukkit;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.Entity;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import net.rocky.coresuite.module.worldbosses.combat.StatusBadge;
import net.rocky.coresuite.module.worldbosses.combat.EncounterState;
import net.rocky.coresuite.module.worldbosses.party.BossPartyManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public abstract class WorldBoss {

    protected final CoreSuitePlugin plugin;
    protected final BossType type;
    protected final FileConfiguration config;
    protected final BossLootManager lootManager;

    protected LivingEntity entity;
    protected Location spawnLocation;
    private final double configuredHealth;
    private double healedFraction;
    private int scaledPlayers = 1;
    private boolean hasBeenEngaged;
    private final java.util.Set<org.bukkit.Chunk> heldChunks = new java.util.HashSet<>();
    protected double maxHealth;
    protected double currentHealth;
    protected boolean active = false;

    protected BossPhase currentPhase;
    protected final List<BossPhase> phases = new ArrayList<>();

    protected BossDamageTracker damageTracker;
    protected BossBarManager barManager;
    protected BossDomainManager domainManager;
    protected BukkitTask combatTask;
    protected final java.util.Set<java.util.UUID> domainViewers = new java.util.HashSet<>();
    protected final java.util.Map<java.util.UUID, Long> bgmPlaybackStartTimes = new java.util.concurrent.ConcurrentHashMap<>();
    protected final java.util.Set<LivingEntity> minions = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final java.util.Map<java.util.UUID, Long> alertExpiry = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, net.rocky.coresuite.module.worldbosses.combat.StatusBadge> alertBadge = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<java.util.UUID, String> alertMessage = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.Map<Location, TemporaryBlock> temporaryBlocks = new java.util.concurrent.ConcurrentHashMap<>();
    private record TemporaryBlock(BlockData original, BlockData placed) {}

    protected boolean isAwakened = false;
    protected int totalAwakeningSeconds;
    protected int awakeningRemainingSeconds;
    private int emptyDomainSeconds = 0;
    protected BossPartyManager partyManager;
    private final UUID encounterId = UUID.randomUUID();
    private EncounterState encounterState = EncounterState.PREPARING;

    public WorldBoss(CoreSuitePlugin plugin, BossType type, FileConfiguration config, BossLootManager lootManager) {
        this.plugin = plugin;
        this.type = type;
        this.config = config;
        this.lootManager = lootManager;
        this.configuredHealth = net.rocky.coresuite.module.worldbosses.combat.EncounterBalance.finite(config.getDouble(type.getConfigKey() + ".max-health", 1500.0), 1500, 1, 10000000);
        this.maxHealth = healthForPlayers(1);
        this.currentHealth = maxHealth;
        this.damageTracker = new BossDamageTracker(maxHealth);
        this.totalAwakeningSeconds = config.getInt("domain.awakening-seconds", 45);
        this.awakeningRemainingSeconds = totalAwakeningSeconds;
        this.isAwakened = (totalAwakeningSeconds <= 0);
    }

    public abstract void setupEntity(LivingEntity entity);
    public abstract void onCombatTick(long tickCount);
    public abstract double calculateIncomingDamage(Player player, double originalDamage, ItemStack weapon);
    public abstract void handleAntiCheese(Player player);

    protected void applyAttributeSafely(LivingEntity entity, org.bukkit.attribute.Attribute attribute, double value) {
        if (entity == null || attribute == null) return;
        try {
            org.bukkit.attribute.AttributeInstance instance = entity.getAttribute(attribute);
            if (instance != null) {
                if (attribute == net.rocky.coresuite.common.compat.PaperCompat.MAX_HEALTH) {
                    double cap = 1024.0;
                    double safeHp = Math.min(value, cap);
                    instance.setBaseValue(safeHp);
                    entity.setHealth(safeHp);
                } else {
                    instance.setBaseValue(value);
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[WorldBosses] Could not apply attribute " + attribute + " (" + value + "): " + t.getMessage());
        }
    }

    public boolean spawn(Location location) {
        if (location == null || location.getWorld() == null) return false;
        try {
            this.spawnLocation = location.clone();
            this.encounterState = isAwakened ? EncounterState.FIGHTING : EncounterState.PREPARING;
            this.currentHealth = maxHealth;

            // Proactively purge any orphaned boss entities nearby from previous server runs or reloads
            NamespacedKey bossKey = new NamespacedKey(plugin, "world_boss");
            try {
                for (Entity nearby : location.getWorld().getNearbyEntities(location, 80.0, 80.0, 80.0)) {
                    if (nearby instanceof LivingEntity le && !nearby.isDead()) {
                        boolean isOrphanBoss = le.getPersistentDataContainer().has(bossKey, PersistentDataType.STRING);
                        if (isOrphanBoss) {
                            le.remove();
                        }
                    }
                }
            } catch (Throwable ignored) {}

            // Guard against Peaceful difficulty
            if (location == null || location.getWorld() == null) {
                return false;
            }
            if (location.getWorld().getDifficulty() == org.bukkit.Difficulty.PEACEFUL) {
                plugin.getLogger().warning("[WorldBosses] Cannot spawn " + type.name() + ": world '" + location.getWorld().getName() + "' is set to Peaceful difficulty.");
                return false;
            }

            // Spawn Base Entity
            this.entity = (LivingEntity) location.getWorld().spawnEntity(location, type.getEntityType());
            this.entity.setRemoveWhenFarAway(false);
            this.entity.setPersistent(false);
            org.bukkit.Chunk chunk = location.getChunk();
            chunk.addPluginChunkTicket(plugin);
            heldChunks.add(chunk);
            this.entity.setCustomNameVisible(true);
            this.entity.customName(TextUtil.colorize(getDisplayName()));
            this.entity.getPersistentDataContainer().set(bossKey, PersistentDataType.STRING, type.name());

            // Setup boss specific armor, weapons, attributes
            setupEntity(this.entity);

            // If still in awakening period, disable AI so boss is dormant and idle
            if (!isAwakened) {
                this.entity.setAI(false);
            }

            // Domain Manager (35m radius, 50m leash)
            double domainRadius = config.getDouble("domain.radius", 35.0);
            double leashDistance = config.getDouble("domain.leash-distance", 50.0);
            boolean antiGrief = config.getBoolean("domain.anti-grief-protection", true);
            String particleName = config.getString("domain.boundary-particle", "FLAME");
            boolean lockdownEnabled = config.getBoolean("domain.lockdown.enabled", true);
            int prepGraceSeconds = config.getInt("domain.lockdown.prep-grace-seconds", 45);

            this.domainManager = new BossDomainManager(plugin, this, spawnLocation, domainRadius, leashDistance, antiGrief, particleName, lockdownEnabled, prepGraceSeconds);
            plugin.getServer().getPluginManager().registerEvents(domainManager, plugin);
            this.domainManager.startVisualTask();

            // Initialize BossBar
            boolean darkenScreen = config.getBoolean("domain.darken-screen", true);
            boolean createFog = config.getBoolean("domain.create-world-fog", true);
            this.barManager = new BossBarManager(getDisplayName(), BossBar.Color.BLUE, darkenScreen, createFog);
            setupPhases();

            if (!isAwakened) {
                float progress = (totalAwakeningSeconds > 0) ? (float) awakeningRemainingSeconds / totalAwakeningSeconds : 1.0f;
                barManager.getBossBar().name(TextUtil.colorize("&e⏳ Awakening: &6" + getDisplayName() + " &8| &f" + awakeningRemainingSeconds + "s"));
                barManager.getBossBar().color(BossBar.Color.YELLOW);
                barManager.getBossBar().progress(Math.max(0f, Math.min(1f, progress)));
            } else {
                evaluatePhase(true);
            }

            this.active = true;

            // Start Combat Task (every 20 ticks / 1 second)
            long[] ticks = {0};
            this.combatTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                try {
                    if (!active || entity == null || !entity.isValid() || entity.isDead()) {
                        if (active) despawn();
                        return;
                    }

                    ticks[0]++;

                    // 1. Update viewer list for BossBar & Atmosphere
                    try {
                        updateBossBarViewers();
                    } catch (Throwable ignored) {}

                    // 2. Handle Awakening Phase Countdown (Top Priority)
                    if (!isAwakened) {
                        awakeningRemainingSeconds--;
                        try {
                            renderAwakeningParticles();
                        } catch (Throwable ignored) {}

                        float progress = (totalAwakeningSeconds > 0) ? (float) awakeningRemainingSeconds / totalAwakeningSeconds : 0f;
                        barManager.getBossBar().name(TextUtil.colorize("&e⏳ Awakening: &6" + getDisplayName() + " &8| &f" + Math.max(0, awakeningRemainingSeconds) + "s"));
                        barManager.getBossBar().color(BossBar.Color.YELLOW);
                        barManager.getBossBar().progress(Math.max(0f, Math.min(1f, progress)));

                        updateActionBarStats();

                        // Periodic Raid Party prompt during awakening countdown (at 35s, 20s, 10s)
                        if (awakeningRemainingSeconds == 35 || awakeningRemainingSeconds == 20 || awakeningRemainingSeconds == 10) {
                            sendPartyInvitationToViewers();
                        }

                        if (awakeningRemainingSeconds <= 0) {
                            awaken();
                        }
                        return;
                    }

                    if (ticks[0] % 5 == 0) updateScaling();
                    // 3. Abandonment Check (Deserted Domain Timer)
                    if (domainViewers.isEmpty()) {
                        emptyDomainSeconds++;
                        int allowedWait = hasBeenEngaged ? Math.max(30, config.getInt("domain.abandonment-seconds", 180)) : Math.max(60, config.getInt("domain.arrival-timeout-seconds", 600));
                        if (emptyDomainSeconds >= allowedWait) {
                            Bukkit.broadcast(TextUtil.colorize("&8[&4&lBoss&8] &e" + getDisplayName() + " &7has dissipated back into the shadows due to lack of challengers."));
                            despawn();
                            return;
                        }
                    } else {
                        emptyDomainSeconds = 0;
                    }

                    // 4. Anti-Cheese: Earth Shatter obstructing/placed blocks around boss
                    if (ticks[0] % 3 == 0) {
                        try {
                            smashObstructingBlocks();
                        } catch (Throwable ignored) {}
                    }

                    // 5. Check Leashing
                    try {
                        domainManager.checkLeash();
                    } catch (Throwable ignored) {}

                    // 5. Send Action Bar personal stats to nearby fighters
                    try {
                        updateActionBarStats();
                    } catch (Throwable ignored) {}

                    // 6. Seamless BGM Looper
                    if (ticks[0] % 5 == 0) {
                        int trackDur = getBgmDurationSeconds();
                        long now = System.currentTimeMillis();
                        for (UUID uuid : domainViewers) {
                            Long started = bgmPlaybackStartTimes.get(uuid);
                            if (started != null && (now - started) >= (trackDur * 1000L)) {
                                Player p = Bukkit.getPlayer(uuid);
                                if (p != null && p.isOnline()) {
                                    startBgmForPlayer(p);
                                }
                            }
                        }
                    }

                    // 7. Update dynamic MMORPG threat & aggro across fighters
                    try {
                        updateAggroAndThreat(ticks[0]);
                    } catch (Throwable ignored) {}

                    // 8. Subclass specific combat tick
                    try {
                        onCombatTick(ticks[0]);
                    } catch (Throwable ignored) {}
                } catch (Throwable t) {
                    plugin.getLogger().log(java.util.logging.Level.SEVERE, "[WorldBosses] Error during boss combat tick", t);
                }
            }, 20L, 20L);

            // Broadcast Spawn Announcement with Lore (Distinct & Separated formatting)
            String coordStr = "X: " + location.getBlockX() + ", Y: " + location.getBlockY() + ", Z: " + location.getBlockZ();
            Bukkit.broadcast(TextUtil.colorize(""));
            Bukkit.broadcast(TextUtil.colorize("&4&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            Bukkit.broadcast(TextUtil.colorize("  &4&l⚔ WORLD BOSS AWAKENED: &r" + getDisplayName()));
            Bukkit.broadcast(TextUtil.colorize("  &7Location: &e" + location.getWorld().getName() + " &8(" + coordStr + "&8)"));
            Bukkit.broadcast(TextUtil.colorize("  &7Health Pool: &c" + String.format("%,.0f", maxHealth) + " HP &8| &6Multi-Phase Titan"));
            Bukkit.broadcast(TextUtil.colorize(""));

            List<String> lore = config.getStringList(type.getConfigKey() + ".lore");
            if (lore != null && !lore.isEmpty()) {
                Bukkit.broadcast(TextUtil.colorize("  &e📜 Ancient Lore:"));
                for (String line : lore) {
                    Bukkit.broadcast(TextUtil.colorize("  " + line));
                }
                Bukkit.broadcast(TextUtil.colorize(""));
            }

            if (type == BossType.XOLTHOS) {
                int stunSec = config.getInt("xolthos.weakness.stun-duration-seconds", 20);
                Bukkit.broadcast(TextUtil.colorize("  &d💡 &lTACTICAL INTEL: &fXol'thos can &cevade attacks&f! Throw &e&lSplash Potions of Night Vision &fto blind and stun her for &a" + stunSec + "s &f(+50% damage)!"));
                Bukkit.broadcast(TextUtil.colorize(""));
            }

            Bukkit.broadcast(TextUtil.colorize("  &c&l⚠ DANGER DISCLAIMER:"));
            Bukkit.broadcast(TextUtil.colorize("  &cYou are at your own risk here. We are not responsible for any damage to your armour, inventory, or anything. You can die here and lose stuff — don't blame us for it!"));
            Bukkit.broadcast(TextUtil.colorize(""));
            Bukkit.broadcast(TextUtil.colorize("  &e⚔ RAID PARTY: &7Type &a/boss party join &7or click in domain to disable friendly fire!"));
            Bukkit.broadcast(TextUtil.colorize("  &7Type &e/boss &7to view your &6Hunting Codex &7and track the invasion!"));
            Bukkit.broadcast(TextUtil.colorize("&4&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
            Bukkit.broadcast(TextUtil.colorize(""));

            try {
                location.getWorld().playSound(location, Sound.ENTITY_WITHER_SPAWN, 2.0f, 0.5f);
            } catch (Throwable ignored) {}
            return true;
        } catch (Throwable t) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "[WorldBosses] Failed to spawn boss: " + type.name(), t);
            active = false;
            releaseChunks();
            if (combatTask != null) combatTask.cancel();
            if (barManager != null) barManager.removeAll();
            if (domainManager != null) { domainManager.stopVisualTask(); HandlerList.unregisterAll(domainManager); }
            clearMinions();
            if (this.entity != null && this.entity.isValid()) {
                this.entity.remove();
            }
            return false;
        }
    }

    public void recordDamage(Player player, double amount, ItemStack weapon) {
        if (!active || player == null || !Double.isFinite(amount) || amount <= 0) return;

        if (!isAwakened) {
            TextUtil.sendActionBar(player, "&e⏳ Boss is awakening in &6" + Math.max(0, awakeningRemainingSeconds) + "s&e! Prepare your gear & buffs!");
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_AMBIENT, 1.0f, 1.5f);
            return;
        }

        if (domainManager != null) {
            domainManager.lockPlayer(player.getUniqueId());
        }

        recordParticipation(player);
        updateScaling();
        // Apply boss damage reductions/multipliers
        double modifiedDamage = calculateIncomingDamage(player, amount, weapon);
        if (!Double.isFinite(modifiedDamage) || modifiedDamage <= 0) {
            return;
        }

        double applied = Math.min(this.currentHealth, modifiedDamage);
        this.currentHealth = Math.max(0.0, this.currentHealth - applied);
        damageTracker.addDamage(player, applied);

        // Update BossBar HP
        if (barManager != null) {
            barManager.update(this.currentHealth, this.maxHealth, this.currentPhase, getDisplayName());
        }

        // Update action bar combat HUD for player
        renderPlayerActionBar(player);

        // Dynamic damage particles
        if (entity != null && entity.isValid()) {
            Location bLoc = entity.getLocation().add(0, 1.0, 0);
            World w = bLoc.getWorld();
            try {
                w.spawnParticle(Particle.DAMAGE_INDICATOR, bLoc, 4, 0.2, 0.2, 0.2, 0.1);
            } catch (Throwable ignored) {}
        }

        // Evaluate phase transitions
        evaluatePhase(false);

        if (currentHealth <= 0) {
            die();
        }
    }

    private void renderAwakeningParticles() {
        if (entity == null || !entity.isValid()) return;
        Location bLoc = entity.getLocation();
        World w = bLoc.getWorld();
        if (w == null) return;

        double radius = 3.5 * ((double) Math.max(0, awakeningRemainingSeconds) / Math.max(1, totalAwakeningSeconds));
        for (int i = 0; i < 16; i++) {
            double angle = (2 * Math.PI * i / 16) + (awakeningRemainingSeconds * 0.2);
            double px = bLoc.getX() + (radius * Math.cos(angle));
            double pz = bLoc.getZ() + (radius * Math.sin(angle));
            w.spawnParticle(org.bukkit.Particle.ENCHANT, px, bLoc.getY() + 0.8, pz, 1, 0, 0, 0, 0, null, true);
        }

        switch (type) {
            case VALERIUS -> w.spawnParticle(org.bukkit.Particle.FLAME, bLoc.clone().add(0, 1.0, 0), 4, 0.3, 0.5, 0.3, 0.02, null, true);
            case AETHELGARD -> w.spawnParticle(org.bukkit.Particle.SPORE_BLOSSOM_AIR, bLoc.clone().add(0, 1.0, 0), 6, 0.4, 0.6, 0.4, 0.01, null, true);
            case XOLTHOS -> w.spawnParticle(org.bukkit.Particle.DRAGON_BREATH, bLoc.clone().add(0, 0.5, 0), 4, 0.3, 0.3, 0.3, 0.01, 1.0f, true);
        }

        if (awakeningRemainingSeconds % 5 == 0) {
            w.playSound(bLoc, Sound.BLOCK_BEACON_AMBIENT, 1.0f, 1.2f);
        }
    }

    public void awaken() {
        if (isAwakened || !active) return;
        isAwakened = true;
        encounterState = EncounterState.FIGHTING;
        awakeningRemainingSeconds = 0;

        if (entity != null && entity.isValid()) {
            entity.setAI(true);
            Location loc = entity.getLocation();
            World w = loc.getWorld();
            if (w != null) {
                w.strikeLightningEffect(loc);
                w.playSound(loc, Sound.ENTITY_WITHER_SPAWN, 2.0f, 0.6f);
                w.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 0.8f);
            }
        }

        evaluatePhase(true);
        try {
            smashObstructingBlocks();
        } catch (Throwable ignored) {}

        int graceSeconds = config.getInt("domain.awakening-grace-seconds", 25);
        int graceTicks = graceSeconds * 20;

        for (Player p : barManager.getViewers()) {
            TextUtil.sendTitle(p, "&4&lAWAKENED!", "&e" + getDisplayName() + " &chas entered combat!", 10, 50, 15);
            p.sendMessage(TextUtil.colorize("&4&l⚠ [COMBAT ENGAGED] &cEnter at your own risk! You can die and lose your items — fight carefully!"));

            // Warden's Protection Grace Period: 25s buffer time so fighters can read lore & orient
            p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, graceTicks, 1, false, false, true));
            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, graceTicks, 1, false, false, true));
            TextUtil.sendMessage(p, "&a✦ WARDEN'S PROTECTION: &7Granted &bResistance II &7and &eAbsorption II &7for &a" + graceSeconds + "s &7buffer time!");

            applyAtmosphere(p);
            if (domainManager != null) {
                domainManager.lockPlayer(p.getUniqueId());
            }
            stopAllBgm(p);
            startBgmForPlayer(p);
        }
    }

    public void sendPartyInvitationToViewers() {
        if (barManager == null) return;
        for (Player p : barManager.getViewers()) {
            if (partyManager != null && partyManager.isPartyMember(p.getUniqueId())) {
                continue;
            }
            sendPartyInviteMessage(p);
        }
    }

    private void sendPartyInviteMessage(Player p) {
        Component invite = TextUtil.colorize("&8[&6&lRaid Party&8] &eForming an alliance? ")
            .append(Component.text("[CLICK TO JOIN RAID PARTY]")
                .color(net.kyori.adventure.text.format.NamedTextColor.GREEN)
                .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD)
                .clickEvent(net.kyori.adventure.text.event.ClickEvent.runCommand("/boss party join"))
                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(TextUtil.colorize("&aClick to join the Raid Party and disable friendly-fire with other hunters!"))))
            .append(TextUtil.colorize(" &7or type &e/boss party join"));
        p.sendMessage(invite);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.8f, 1.2f);
    }

    public void forceAwaken() {
        if (!isAwakened && active) {
            awaken();
        }
    }

    public void updateBossBarViewers() {
        if (entity == null || !entity.isValid() || barManager == null) return;
        Location loc = entity.getLocation();
        World w = loc.getWorld();
        if (w == null) return;

        double radius = config.getDouble("domain.radius", 35.0);
        double leash = config.getDouble("domain.leash-distance", 50.0);
        double maxDistSq = leash * leash;
        double locX = loc.getX();
        double locZ = loc.getZ();

        java.util.Set<UUID> currentInDomain = new java.util.HashSet<>();
        for (Player p : w.getPlayers()) {
            if (!p.isOnline() || p.isDead()) continue;
            Location pLoc = p.getLocation();
            if (Math.abs(pLoc.getX() - locX) > leash || Math.abs(pLoc.getZ() - locZ) > leash) {
                continue;
            }
            if (pLoc.distanceSquared(loc) <= maxDistSq) {
                currentInDomain.add(p.getUniqueId());
                if (!domainViewers.contains(p.getUniqueId())) {
                    domainViewers.add(p.getUniqueId());
                    barManager.addViewer(p);
                    applyAtmosphere(p);
                    startBgmForPlayer(p);

                    // Entry Warning / Danger Disclaimer
                    p.sendMessage(TextUtil.colorize(""));
                    p.sendMessage(TextUtil.colorize("&4&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                    p.sendMessage(TextUtil.colorize("  &4&l⚠ BOSS DOMAIN WARNING: &r" + getDisplayName()));
                    p.sendMessage(TextUtil.colorize("  &cYou are at your own risk here. We are not responsible for any damage to your armour, inventory, or anything. You can die here and lose stuff — don't blame us for it!"));
                    if (type == BossType.XOLTHOS) {
                        int stunSec = config.getInt("xolthos.weakness.stun-duration-seconds", 20);
                        p.sendMessage(TextUtil.colorize("  &d💡 &lTACTIC: &fHit Xol'thos with &e&lSplash Night Vision &fto prevent evasion and stun her for &a" + stunSec + "s&f!"));
                    }
                    if (!isAwakened) {
                        sendPartyInviteMessage(p);
                    }
                    p.sendMessage(TextUtil.colorize("&4&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"));
                    p.sendMessage(TextUtil.colorize(""));
                }
            }
        }

        java.util.Iterator<UUID> it = domainViewers.iterator();
        while (it.hasNext()) {
            UUID uid = it.next();
            if (!currentInDomain.contains(uid)) {
                it.remove();
                Player p = Bukkit.getPlayer(uid);
                if (p != null && p.isOnline()) {
                    barManager.removeViewer(p);
                    clearAtmosphere(p);
                    stopAllBgm(p);
                }
            }
        }
    }

    public void evaluatePhase(boolean force) {
        if (phases.isEmpty()) return;
        double hpPct = (maxHealth > 0) ? (currentHealth / maxHealth) * 100.0 : 0.0;

        BossPhase newPhase = phases.get(phases.size() - 1);
        for (BossPhase phase : phases) {
            if (hpPct >= phase.getHealthPercentMin()) {
                newPhase = phase;
                break;
            }
        }

        if (!force && currentPhase != null && newPhase.getPhaseNumber() < currentPhase.getPhaseNumber()) return;
        if (force || currentPhase == null || currentPhase != newPhase) {
            BossPhase oldPhase = currentPhase;
            currentPhase = newPhase;

            if (barManager != null) {
                barManager.update(currentHealth, maxHealth, currentPhase, getDisplayName());
            }

            if (!force && oldPhase != null && oldPhase != newPhase) {
                if (entity != null && entity.isValid()) {
                    Location loc = entity.getLocation();
                    World w = loc.getWorld();
                    if (w != null) {
                        w.playSound(loc, Sound.ENTITY_ENDER_DRAGON_GROWL, 2.0f, 1.2f);
                        w.spawnParticle(org.bukkit.Particle.EXPLOSION_EMITTER, loc, 2);
                    }
                }

                for (Player p : barManager.getViewers()) {
                    TextUtil.sendTitle(p, "&c&lPHASE " + newPhase.getPhaseNumber(), "&e" + newPhase.getName(), 10, 40, 10);
                    stopAllBgm(p);
                    startBgmForPlayer(p);
                }
            } else if (force) {
                for (Player p : barManager.getViewers()) {
                    startBgmForPlayer(p);
                }
            }
        }
    }

    public float getBgmVolume() {
        return (float) config.getDouble("domain.bgm-volume", 0.45);
    }

    public float getVictoryVolume() {
        return (float) config.getDouble("domain.victory-volume", 0.50);
    }

    public String getAwakeningBgmKey() {
        String key = type.getConfigKey() + ".awakening-bgm";
        String val = config.getString(key);
        if (val != null && !val.isBlank()) return val;
        return config.getString("domain.awakening-bgm", "coresuite:boss.awakening");
    }

    public String getPhaseBgmKey(int phase) {
        String key = type.getConfigKey() + ".bgm-p" + phase;
        String val = config.getString(key);
        if (val != null && !val.isBlank()) return val;
        return config.getString(type.getConfigKey() + ".bgm", "coresuite:boss." + type.getConfigKey());
    }

    public String getBgmKey() {
        if (!isAwakened) {
            return getAwakeningBgmKey();
        }
        int phaseNum = (currentPhase != null) ? currentPhase.getPhaseNumber() : 1;
        return getPhaseBgmKey(phaseNum);
    }

    public void applyAtmosphere(Player p) {
        if (p == null || !p.isOnline()) return;
        try {
            p.setPlayerTime(18000L, false); // Midnight / Blood Eclipse
            boolean storm = config.getBoolean("domain.storm-weather", true);
            if (storm) {
                p.setPlayerWeather(org.bukkit.WeatherType.DOWNFALL); // Storm Tempest
            } else {
                p.setPlayerWeather(org.bukkit.WeatherType.CLEAR);
            }
        } catch (Throwable ignored) {}
    }

    public void clearAtmosphere(Player p) {
        if (p == null || !p.isOnline()) return;
        try {
            p.resetPlayerTime();
            p.resetPlayerWeather();
        } catch (Throwable ignored) {}
    }

    public org.bukkit.SoundCategory getBgmSoundCategory() {
        String catStr = config.getString("domain.bgm-category", "RECORDS");
        try {
            return org.bukkit.SoundCategory.valueOf(catStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return org.bukkit.SoundCategory.RECORDS;
        }
    }

    public int getBgmDurationSeconds() {
        if (!isAwakened) {
            return config.getInt("domain.awakening-bgm-duration-seconds", totalAwakeningSeconds);
        }
        int phaseNum = (currentPhase != null) ? currentPhase.getPhaseNumber() : 1;
        String key = type.getConfigKey() + ".bgm-p" + phaseNum + "-duration-seconds";
        int dur = config.getInt(key, 0);
        if (dur > 0) return dur;
        return config.getInt(type.getConfigKey() + ".bgm-duration-seconds", 180);
    }

    public void startBgmForPlayer(Player p) {
        if (p == null || !p.isOnline() || !active) return;
        String bgm = getBgmKey();
        if (bgm == null || bgm.isBlank()) return;
        org.bukkit.SoundCategory category = getBgmSoundCategory();

        try {
            stopAllBgm(p);
            // Global volume (1000.0f) ensures audio never attenuates or cuts out beyond 16 blocks across the 35m arena
            p.playSound(p.getLocation(), bgm, category, 1000.0f, 1.0f);
            bgmPlaybackStartTimes.put(p.getUniqueId(), System.currentTimeMillis());
        } catch (Throwable ignored) {}
    }

    public void stopAllBgm(Player p) {
        if (p == null || !p.isOnline()) return;
        org.bukkit.SoundCategory cat = getBgmSoundCategory();
        String awakeningBgm = getAwakeningBgmKey();
        if (awakeningBgm != null && !awakeningBgm.isBlank()) {
            try {
                p.stopSound(awakeningBgm, cat);
                p.stopSound(awakeningBgm);
            } catch (Throwable ignored) {}
        }
        for (int ph = 1; ph <= 3; ph++) {
            String b = getPhaseBgmKey(ph);
            if (b != null && !b.isBlank()) {
                try {
                    p.stopSound(b, cat);
                    p.stopSound(b);
                } catch (Throwable ignored) {}
            }
        }
        String fallbackBgm = config.getString(type.getConfigKey() + ".bgm");
        if (fallbackBgm != null && !fallbackBgm.isBlank()) {
            try {
                p.stopSound(fallbackBgm, cat);
                p.stopSound(fallbackBgm);
            } catch (Throwable ignored) {}
        }
    }

    public void updateAggroAndThreat(long tickCount) {
        if (entity == null || !entity.isValid() || !isAwakened) return;
        if (!(entity instanceof Mob mob)) return;

        // Run dynamic threat evaluation every 4 seconds (every 4 ticks of the 1s loop)
        if (tickCount % 4 != 0) return;

        List<Player> validFighters = new ArrayList<>();
        if (barManager != null) {
            for (Player p : barManager.getViewers()) {
                if (p != null && p.isOnline() && p.isValid() && !p.isDead()) {
                    if (domainManager != null && domainManager.isInDomain(p.getLocation())) {
                        validFighters.add(p);
                    }
                }
            }
        }

        if (validFighters.isEmpty()) {
            mob.setTarget(null);
            return;
        }

        // If only 1 player, focus them
        if (validFighters.size() == 1) {
            mob.setTarget(validFighters.get(0));
            distributeMinionTargets(validFighters);
            return;
        }

        // Multiple players: Dynamic MMORPG Threat Matrix
        // 1. Calculate top damagers
        List<BossDamageTracker.DamageEntry> topDamagers = damageTracker.getSortedDamagers();
        Player topDamagerPlayer = null;
        for (BossDamageTracker.DamageEntry entry : topDamagers) {
            Player p = Bukkit.getPlayer(entry.uuid());
            if (p != null && validFighters.contains(p)) {
                topDamagerPlayer = p;
                break;
            }
        }

        // 2. Find nearest player
        Player nearestPlayer = validFighters.get(0);
        double nearestDistSq = Double.MAX_VALUE;
        for (Player p : validFighters) {
            double distSq = p.getLocation().distanceSquared(entity.getLocation());
            if (distSq < nearestDistSq) {
                nearestDistSq = distSq;
                nearestPlayer = p;
            }
        }

        // 3. Selection with weighted roll:
        // 55% -> Top Damager (highest threat)
        // 30% -> Nearest Player (melee engagement)
        // 15% -> Random Flank Switch (surprise ranged/healer attack)
        double roll = Math.random();
        Player selectedTarget;
        if (roll < 0.55 && topDamagerPlayer != null) {
            selectedTarget = topDamagerPlayer;
        } else if (roll < 0.85) {
            selectedTarget = nearestPlayer;
        } else {
            selectedTarget = validFighters.get(new Random().nextInt(validFighters.size()));
        }

        if (selectedTarget != null && !selectedTarget.equals(mob.getTarget())) {
            mob.setTarget(selectedTarget);
        }

        // Distribute minions across the fighter pool
        distributeMinionTargets(validFighters);
    }

    private void distributeMinionTargets(List<Player> fighters) {
        if (fighters.isEmpty()) return;
        int idx = 0;
        for (LivingEntity minion : minions) {
            if (minion != null && minion.isValid() && minion instanceof Mob mobMinion) {
                Player target = fighters.get(idx % fighters.size());
                mobMinion.setTarget(target);
                idx++;
            }
        }
    }

    public void sendCombatAlert(Player player, StatusBadge badge, String message, int durationSeconds) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        alertExpiry.put(uuid, System.currentTimeMillis() + (durationSeconds * 1000L));
        if (badge != null) {
            alertBadge.put(uuid, badge);
        } else {
            alertBadge.remove(uuid);
        }
        if (message != null) {
            alertMessage.put(uuid, message);
        } else {
            alertMessage.remove(uuid);
        }
        renderPlayerActionBar(player);
    }

    public void renderPlayerActionBar(Player player) {
        if (player == null || !player.isOnline()) return;
        UUID uuid = player.getUniqueId();

        // 1. Column 1: Alert / Status
        String col1;
        Long expiry = alertExpiry.get(uuid);
        if (expiry != null && System.currentTimeMillis() < expiry) {
            StatusBadge badge = alertBadge.get(uuid);
            String msg = alertMessage.get(uuid);
            if (badge != null && msg != null) {
                col1 = badge.format(msg);
            } else if (msg != null) {
                col1 = msg;
            } else if (badge != null) {
                col1 = badge.getPrefix();
            } else {
                col1 = "&a⚔ IN COMBAT";
            }
        } else if (!isAwakened) {
            col1 = "&e⏳ Awakening: &6" + Math.max(0, awakeningRemainingSeconds) + "s";
        } else {
            int phaseNum = currentPhase != null ? currentPhase.getPhaseNumber() : 1;
            col1 = "&e⚔ PHASE " + phaseNum;
        }

        // 2. Column 2: Personal Stats (DMG & Rank)
        double dmg = damageTracker.getDamage(uuid);
        int rank = damageTracker.getRank(uuid);
        int contribution = damageTracker.getContribution(uuid);
        double minimumDamage = Math.max(0, config.getDouble(type.getConfigKey() + ".loot.participation.minimum-damage",
                config.getDouble("rewards.participation.minimum-damage", 20.0)));
        int minimumContribution = Math.max(1, config.getInt(type.getConfigKey() + ".loot.participation.minimum-contribution",
                config.getInt("rewards.participation.minimum-contribution", 3)));
        String qualification = damageTracker.qualifies(uuid, minimumDamage, minimumContribution)
                ? " &a✓ qualified" : " &7(" + String.format("%.0f", Math.max(0, minimumDamage - dmg)) + " DMG or "
                + Math.max(0, minimumContribution - contribution) + " assists)";
        String col2;
        if (dmg > 0 && rank > 0) {
            col2 = "&6DMG: &e" + String.format("%,.0f", dmg) + " &8(&6#" + rank + "&8)" + qualification;
        } else {
            col2 = "&7DMG: &f0" + qualification;
        }

        // 3. Column 3: Boss HP
        double hpPct = (maxHealth > 0) ? (currentHealth / maxHealth) * 100.0 : 0.0;
        String col3 = "&c" + String.format("%,.0f", currentHealth) + " HP &8(&e" + String.format("%.0f", hpPct) + "%&8)";

        String hud = col1 + " &8| " + col2 + " &8| " + col3;
        TextUtil.sendActionBar(player, hud);
    }

    public void updateActionBarStats() {
        if (barManager == null) return;
        for (Player p : barManager.getViewers()) {
            renderPlayerActionBar(p);
        }
    }

    public void registerMinion(LivingEntity minion) {
        if (minion != null) {
            minions.add(minion);
        }
    }

    public boolean isMinion(org.bukkit.entity.Entity entity) {
        if (entity instanceof LivingEntity le) {
            return minions.contains(le);
        }
        return false;
    }

    public void clearMinions() {
        for (LivingEntity minion : minions) {
            if (minion != null) {
                try {
                    minion.remove();
                } catch (Throwable ignored) {}
            }
        }
        minions.clear();
    }

    /** Places transient encounter terrain and restores it after expiry or terminal cleanup. */
    public boolean placeTemporaryBlock(Block block, Material material, long lifetimeTicks) {
        if (!active || block == null || material == null || lifetimeTicks < 1 || !block.getType().isAir()
                || !net.rocky.coresuite.common.integration.BossProtectionGate.allows(block.getLocation())) return false;
        Location location = block.getLocation();
        BlockData original = block.getBlockData();
        block.setType(material, false);
        BlockData placed = block.getBlockData();
        temporaryBlocks.put(location, new TemporaryBlock(original, placed));
        Bukkit.getScheduler().runTaskLater(plugin, () -> restoreTemporaryBlock(location), lifetimeTicks);
        return true;
    }
    private void restoreTemporaryBlock(Location location) {
        TemporaryBlock temporary = temporaryBlocks.remove(location);
        if (temporary == null || location.getWorld() == null) return;
        Block block = location.getBlock();
        if (block.getBlockData().matches(temporary.placed())) block.setBlockData(temporary.original(), false);
    }
    private void cleanupTemporaryBlocks() {
        for (Location location : java.util.List.copyOf(temporaryBlocks.keySet())) restoreTemporaryBlock(location);
        temporaryBlocks.clear();
    }

    public java.util.Set<LivingEntity> getMinions() {
        return java.util.Collections.unmodifiableSet(minions);
    }

    public void broadcastToDomain(String message) {
        Component comp = TextUtil.colorize("&8[&4&lBoss&8] &r" + message);
        for (Player p : barManager.getViewers()) {
            p.sendMessage(comp);
        }
    }

    public void die() {
        if (!active) return;
        active = false;
        encounterState = EncounterState.DEFEATED;
        releaseChunks();

        // Stop all combat tracks and reset atmosphere for all domain viewers
        for (java.util.UUID uuid : domainViewers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                clearAtmosphere(p);
                stopAllBgm(p);
            }
        }

        // Triumphant Victory Fanfare & Celebration
        String victorySound = "coresuite:boss.victory";
        float victoryVol = getVictoryVolume();
        org.bukkit.SoundCategory cat = getBgmSoundCategory();
        int victorySec = config.getInt("domain.victory-duration-seconds", 25);
        for (java.util.UUID uuid : domainViewers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                try {
                    p.stopSound(victorySound, cat);
                    p.stopSound(victorySound);
                    p.playSound(p.getLocation(), victorySound, cat, victoryVol, 1.0f);
                    TextUtil.sendTitle(p, "&6&l✦ VICTORY! ✦", "&f" + getDisplayName() + " &ehas fallen!", 10, 70, 20);
                } catch (Throwable ignored) {}
            }
        }

        // Schedule clean cutoff for victory fanfare after victorySec (default 25s)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                try {
                    p.stopSound(victorySound, cat);
                    p.stopSound(victorySound);
                } catch (Throwable ignored) {}
            }
        }, victorySec * 20L);

        // Spawn celebratory victory fireworks
        if (spawnLocation != null && spawnLocation.getWorld() != null) {
            World w = spawnLocation.getWorld();
            for (int i = 0; i < 5; i++) {
                Location fwLoc = spawnLocation.clone().add((Math.random() - 0.5) * 10, 1 + Math.random() * 2, (Math.random() - 0.5) * 10);
                Firework fw = w.spawn(fwLoc, Firework.class);
                FireworkMeta fm = fw.getFireworkMeta();
                fm.addEffect(FireworkEffect.builder()
                        .with(FireworkEffect.Type.BALL_LARGE)
                        .withColor(org.bukkit.Color.YELLOW, org.bukkit.Color.ORANGE, org.bukkit.Color.FUCHSIA)
                        .withFade(org.bukkit.Color.WHITE)
                        .trail(true)
                        .flicker(true)
                        .build());
                fm.setPower(1);
                fw.setFireworkMeta(fm);
            }
        }

        domainViewers.clear();
        bgmPlaybackStartTimes.clear();
        clearMinions();
        cleanupTemporaryBlocks();
        alertExpiry.clear();
        alertBadge.clear();
        alertMessage.clear();

        if (combatTask != null) {
            combatTask.cancel();
            combatTask = null;
        }

        if (barManager != null) {
            barManager.removeAll();
        }
        if (domainManager != null) {
            domainManager.stopVisualTask();
            HandlerList.unregisterAll(domainManager);
        }

        if (entity != null) {
            try {
                entity.remove();
            } catch (Throwable ignored) {}
        }

        if (partyManager != null) {
            partyManager.reset();
        }

        // Distribute Loot
        lootManager.distributeRewards(type, damageTracker, getDisplayName());
    }

    public void despawn() {
        if (!active) return;
        active = false;
        encounterState = EncounterState.ABANDONED;
        releaseChunks();

        for (java.util.UUID uuid : domainViewers) {
            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                clearAtmosphere(p);
                stopAllBgm(p);
            }
        }
        domainViewers.clear();
        bgmPlaybackStartTimes.clear();
        clearMinions();
        cleanupTemporaryBlocks();
        alertExpiry.clear();
        alertBadge.clear();
        alertMessage.clear();

        if (partyManager != null) {
            partyManager.reset();
        }

        if (combatTask != null) {
            combatTask.cancel();
            combatTask = null;
        }

        broadcastToDomain("&c&l[!] " + getDisplayName() + " &chas despawned.");

        if (barManager != null) {
            barManager.removeAll();
        }
        if (domainManager != null) {
            domainManager.stopVisualTask();
            HandlerList.unregisterAll(domainManager);
        }

        if (entity != null) {
            try {
                entity.remove();
            } catch (Throwable ignored) {}
        }
    }

    public void removeViewerOnQuit(Player player) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        domainViewers.remove(uuid);
        bgmPlaybackStartTimes.remove(uuid);
        alertExpiry.remove(uuid);
        alertBadge.remove(uuid);
        alertMessage.remove(uuid);
        if (barManager != null) {
            barManager.removeViewer(player);
        }
        if (domainManager != null) {
            domainManager.unlockPlayer(uuid);
        }
        clearAtmosphere(player);
        stopAllBgm(player);
    }

    public void smashObstructingBlocks() {
        if (entity == null || !entity.isValid() || !active) return;
        Location bLoc = entity.getLocation();
        World w = bLoc.getWorld();
        if (w == null) return;

        int baseX = bLoc.getBlockX();
        int baseY = bLoc.getBlockY();
        int baseZ = bLoc.getBlockZ();

        int brokenCount = 0;
        Set<Location> playerPlaced = (domainManager != null) ? domainManager.getPlayerPlacedBlocks() : null;
        boolean hasPlayerBlocks = (playerPlaced != null && !playerPlaced.isEmpty());
        int radiusXZ = hasPlayerBlocks ? 2 : 0;

        for (int dx = -radiusXZ; dx <= radiusXZ; dx++) {
            for (int dy = 0; dy <= 2; dy++) {
                for (int dz = -radiusXZ; dz <= radiusXZ; dz++) {
                    Block b = w.getBlockAt(baseX + dx, baseY + dy, baseZ + dz);
                    Material mat = b.getType();
                    if (mat == Material.AIR || mat == Material.CAVE_AIR || mat == Material.VOID_AIR || mat == Material.BEDROCK || mat == Material.BARRIER) {
                        continue;
                    }

                    boolean shouldBreak = false;
                    Location bPos = b.getLocation();
                    if (playerPlaced != null && playerPlaced.contains(bPos)) {
                        shouldBreak = true;
                    } else {
                        String matName = mat.name();
                        boolean isUtility = matName.contains("CHEST") || matName.contains("SHULKER_BOX") || matName.contains("BARREL")
                                || matName.contains("ANVIL") || matName.contains("CRAFTING_TABLE") || matName.contains("ENCHANTING_TABLE")
                                || matName.contains("BEACON") || matName.contains("FURNACE") || matName.contains("SMOKER") || matName.contains("BLAST_FURNACE");
                        if (isUtility) {
                            shouldBreak = true;
                        } else if (mat.isSolid()) {
                            if ((dx == 0 && dz == 0) || (dy >= 1 && Math.abs(dx) <= 1 && Math.abs(dz) <= 1)) {
                                shouldBreak = true;
                            }
                        }
                    }

                    if (shouldBreak) {
                        if (playerPlaced != null) {
                            playerPlaced.remove(bPos);
                        }
                        b.breakNaturally();
                        brokenCount++;
                    }
                }
            }
        }

        if (brokenCount > 0) {
            w.playSound(bLoc, Sound.ENTITY_ZOMBIE_BREAK_WOODEN_DOOR, 1.5f, 0.7f);
            w.playSound(bLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.9f);
            w.spawnParticle(Particle.EXPLOSION, bLoc.clone().add(0, 1.0, 0), 3, 0.5, 0.5, 0.5, 0.1);
            w.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, bLoc.clone().add(0, 0.5, 0), 12, 0.8, 0.5, 0.8, 0.05);

            for (Player p : w.getPlayers()) {
                if (p.isOnline() && p.getLocation().distanceSquared(bLoc) <= 16.0) {
                    org.bukkit.util.Vector push = p.getLocation().toVector().subtract(bLoc.toVector()).normalize().multiply(1.3);
                    push.setY(0.42);
                    p.setVelocity(push);
                    p.damage(6.0, entity);
                    sendCombatAlert(p, StatusBadge.DEFLECTED, "&c✦ EARTH SHATTER! &7Barricade obliterated!", 2);
                }
            }
        }
    }

    protected abstract void setupPhases();

    public String getDisplayName() {
        return config.getString(type.getConfigKey() + ".display-name", type.getDefaultDisplayName());
    }

    public BossType getType() { return type; }
    public UUID getEncounterId() { return encounterId; }
    public EncounterState getEncounterState() { return encounterState; }
    public LivingEntity getEntity() { return entity; }
    private double healthForPlayers(int players) {
        if (!config.getBoolean("balance.adaptive-scaling", true)) return configuredHealth;
        return net.rocky.coresuite.module.worldbosses.combat.EncounterBalance.health(configuredHealth, players,
                config.getDouble("balance.solo-health-multiplier", .6), config.getDouble("balance.health-per-extra-player", .5));
    }
    public void recordParticipation(Player player) {
        if (!active || !isAwakened || player == null || player.isDead()) return;
        damageTracker.recordParticipation(player);
        hasBeenEngaged = true;
    }
    public void recordTankContribution(Player player) {
        if (!active || !isAwakened) return;
        damageTracker.recordContribution(player, 2);
    }
    public void recordMechanicContribution(Player player, String objective) {
        if (!active || !isAwakened) return;
        damageTracker.recordContribution(player, 3);
        sendCombatAlert(player, StatusBadge.DEFLECTED, "&aMechanic completed: &f" + objective, 2);
    }
    public void recordMinionContribution(Player player) {
        if (!active || !isAwakened) return;
        damageTracker.recordContribution(player, 1);
    }
    private void updateScaling() {
        if (entity == null || !entity.isValid() || !config.getBoolean("balance.adaptive-scaling", true)) return;
        long since = System.currentTimeMillis() - Math.max(10, config.getInt("balance.activity-window-seconds", 60)) * 1000L;
        int nearby = 0;
        double range = config.getDouble("domain.leash-distance", 50);
        for (Player player : entity.getWorld().getPlayers()) {
            if (player.isDead() || player.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            if (damageTracker.wasRecentlyActive(player.getUniqueId(), since) && player.getLocation().distanceSquared(entity.getLocation()) <= range * range) nearby++;
        }
        nearby = Math.max(1, Math.min(20, nearby));
        if (nearby == scaledPlayers) return;
        double newMaximum = healthForPlayers(nearby);
        currentHealth = net.rocky.coresuite.module.worldbosses.combat.EncounterBalance.rescale(currentHealth, maxHealth, newMaximum);
        maxHealth = newMaximum;
        scaledPlayers = nearby;
        if (barManager != null) barManager.update(currentHealth, maxHealth, currentPhase, getDisplayName());
    }
    public double heal(double amount) {
        double actual = net.rocky.coresuite.module.worldbosses.combat.EncounterBalance.healing(currentHealth, maxHealth, amount,
                healedFraction, config.getDouble("balance.maximum-healing-fraction", .15));
        if (actual > 0) {
            healedFraction += actual / maxHealth;
            setCurrentHealth(currentHealth + actual);
        }
        return actual;
    }
    public double getOutgoingDamageMultiplier() {
        if (!config.getBoolean("balance.adaptive-scaling", true)) return 1;
        return Math.min(1.0, .65 + .05 * (scaledPlayers - 1));
    }
    private void releaseChunks() {
        for (org.bukkit.Chunk chunk : heldChunks) chunk.removePluginChunkTicket(plugin);
        heldChunks.clear();
    }

    public Location getSpawnLocation() { return spawnLocation; }
    public double getCurrentHealth() { return currentHealth; }
    public void setCurrentHealth(double currentHealth) {
        if (!Double.isFinite(currentHealth)) return;
        this.currentHealth = Math.min(this.maxHealth, Math.max(0.0, currentHealth));
        if (barManager != null) {
            barManager.update(this.currentHealth, this.maxHealth, this.currentPhase, getDisplayName());
        }
    }
    public double getMaxHealth() { return maxHealth; }
    public boolean isActive() { return active; }
    public boolean isAwakened() { return isAwakened; }
    public int getAwakeningRemainingSeconds() { return awakeningRemainingSeconds; }
    public int getTotalAwakeningSeconds() { return totalAwakeningSeconds; }
    public BossPhase getCurrentPhase() { return currentPhase; }
    public BossDamageTracker getDamageTracker() { return damageTracker; }
    public BossDomainManager getDomainManager() { return domainManager; }
    public BossPartyManager getPartyManager() { return partyManager; }
    public void setPartyManager(BossPartyManager partyManager) { this.partyManager = partyManager; }
    public FileConfiguration getConfig() { return config; }
}
