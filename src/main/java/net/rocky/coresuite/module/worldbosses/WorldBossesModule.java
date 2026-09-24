package net.rocky.coresuite.module.worldbosses;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.text.TextUtil;
import net.rocky.coresuite.module.Module;
import net.rocky.coresuite.module.worldbosses.boss.AethelgardBoss;
import net.rocky.coresuite.module.worldbosses.boss.BossType;
import net.rocky.coresuite.module.worldbosses.boss.BorealisBoss;
import net.rocky.coresuite.module.worldbosses.boss.ValeriusBoss;
import net.rocky.coresuite.module.worldbosses.boss.WorldBoss;
import net.rocky.coresuite.module.worldbosses.boss.XolthosBoss;
import net.rocky.coresuite.module.worldbosses.combat.BossDamageTracker;
import net.rocky.coresuite.module.worldbosses.combat.StatusBadge;
import net.rocky.coresuite.module.worldbosses.party.BossPartyManager;
import net.rocky.coresuite.module.worldbosses.rewards.BossCodexGUI;
import net.rocky.coresuite.module.worldbosses.rewards.BossDiscoveryManager;
import net.rocky.coresuite.module.worldbosses.rewards.BossItemRegistry;
import net.rocky.coresuite.module.worldbosses.rewards.BossLootManager;
import net.rocky.coresuite.module.worldbosses.rewards.HunterExchangeGUI;
import net.rocky.coresuite.module.worldbosses.portal.BossPortalManager;
import net.rocky.coresuite.module.worldbosses.scheduler.BossSpawnScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityCombustByBlockEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class WorldBossesModule extends Module implements Listener, CommandExecutor, TabCompleter {

    private BossItemRegistry itemRegistry;
    private BossDiscoveryManager discoveryManager;
    private BossLootManager lootManager;
    private HunterExchangeGUI hunterExchangeGUI;
    private BossCodexGUI bossCodexGUI;
    private BossSpawnScheduler scheduler;
    private BossPartyManager partyManager;
    private WorldBoss activeBoss;
    private BossPortalManager portalManager;
    private final List<NamespacedKey> registeredRecipeKeys = new ArrayList<>();
    private final Map<UUID, Float> lastAttackCooldownStrength = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastAttackCooldownTime = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> comboStreak = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastComboTime = new ConcurrentHashMap<>();
    private org.bukkit.scheduler.BukkitTask signatureItemsTask;

    private boolean containsSummonTotem(ItemStack[] items) {
        return Arrays.stream(items).anyMatch(item -> itemRegistry.getTotemType(item) != null);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onTotemCraftPreview(org.bukkit.event.inventory.PrepareItemCraftEvent event) {
        if (containsSummonTotem(event.getInventory().getMatrix())) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTotemCraft(org.bukkit.event.inventory.CraftItemEvent event) {
        if (containsSummonTotem(event.getInventory().getMatrix())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTotemAutoCraft(org.bukkit.event.block.CrafterCraftEvent event) {
        if (event.getBlock().getState() instanceof org.bukkit.block.Crafter crafter
                && containsSummonTotem(crafter.getInventory().getContents())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTotemBrew(org.bukkit.event.inventory.BrewEvent event) {
        if (itemRegistry.getTotemType(event.getContents().getIngredient()) != null) {
            event.setCancelled(true);
        }
    }

    public WorldBossesModule(CoreSuitePlugin plugin) {
        super(plugin, "WorldBosses", "world-bosses");
    }

    @Override
    public void onEnable() {
        upgradeEncounterDefaults();
        initDatabase();

        this.itemRegistry = new BossItemRegistry(plugin);
        this.discoveryManager = new BossDiscoveryManager(plugin);
        this.discoveryManager.initTable();

        this.lootManager = new BossLootManager(plugin, itemRegistry, config, discoveryManager);
        this.lootManager.initializeMailbox();
        this.hunterExchangeGUI = new HunterExchangeGUI(plugin, itemRegistry, config);
        this.bossCodexGUI = new BossCodexGUI(plugin, this, hunterExchangeGUI, discoveryManager);
        this.partyManager = new BossPartyManager(plugin);

        for (Player p : Bukkit.getOnlinePlayers()) {
            discoveryManager.loadPlayer(p.getUniqueId());
            net.rocky.coresuite.common.integration.AuthenticationGate.afterLogin(plugin, p, () -> { if (isEnabled()) lootManager.claimRewards(p); });
        }

        registerRecipes();

        registerListener(this);

        PluginCommand bossCmd = plugin.getCommand("boss");
        if (bossCmd != null) {
            bossCmd.setExecutor(this);
            bossCmd.setTabCompleter(this);
        }

        PluginCommand bossAdminCmd = plugin.getCommand("bossadmin");
        if (bossAdminCmd != null) {
            bossAdminCmd.setExecutor(this);
            bossAdminCmd.setTabCompleter(this);
        }

        if (plugin.getIntegrationManager().hasCitizens()) {
            registerListener(new CitizensListener());
        }

        this.portalManager = new BossPortalManager(plugin, this, config);
        registerListener(this.portalManager);
        this.scheduler = new BossSpawnScheduler(plugin, this, config);
        this.scheduler.start();

        startSignatureItemsTask();

        // Clean up any lingering or orphaned boss entities from prior crashes or restarts
        int purged = cleanupAllOrphanBosses();
        if (purged > 0) {
            log("Purged " + purged + " orphaned boss entities on startup.");
        }

        log("WorldBosses module initialized successfully.");
    }

    @Override
    public void onDisable() {
        if (hunterExchangeGUI != null) hunterExchangeGUI.invalidate();
        unregisterRecipes();

        if (signatureItemsTask != null) {
            signatureItemsTask.cancel();
            signatureItemsTask = null;
        }

        if (scheduler != null) {
            scheduler.stop();
            scheduler = null;
        }

        if (portalManager != null) {
            portalManager.cleanup();
            portalManager = null;
        }

        if (activeBoss != null && activeBoss.isActive()) {
            activeBoss.despawn();
            activeBoss = null;
        }

        log("WorldBosses module disabled.");
    }

    @Override
    public void onReload() {
        if (hunterExchangeGUI != null) hunterExchangeGUI.invalidate();
        unregisterRecipes();
        if (portalManager != null) {
            portalManager.cleanup();
            org.bukkit.event.HandlerList.unregisterAll(portalManager);
        }

        if (activeBoss != null && activeBoss.isActive()) {
            activeBoss.despawn();
            activeBoss = null;
        }

        if (scheduler != null) {
            scheduler.stop();
        }

        loadConfig();
        upgradeEncounterDefaults();
        this.lootManager = new BossLootManager(plugin, itemRegistry, config, discoveryManager);
        this.lootManager.initializeMailbox();
        this.hunterExchangeGUI = new HunterExchangeGUI(plugin, itemRegistry, config);
        this.bossCodexGUI = new BossCodexGUI(plugin, this, hunterExchangeGUI, discoveryManager);
        registerRecipes();

        this.portalManager = new BossPortalManager(plugin, this, config);
        registerListener(this.portalManager);
        this.scheduler = new BossSpawnScheduler(plugin, this, config);
        this.scheduler.start();

        log("WorldBosses module reloaded.");
    }

    private void upgradeEncounterDefaults() {
        if (!config.getBoolean("balance.borealis-thermal-upgraded", false)) {
            config.set("borealis.weakness.fire-resist-chance", null);
            if ("&b&lBorealis, The Frostbound Warden".equals(config.getString("borealis.display-name")))
                config.set("borealis.display-name", "&b&lBorealis, The Permafrost Tyrant");
            var oldLore = config.getStringList("borealis.lore");
            if (oldLore.stream().anyMatch(line -> line.contains("half of all attempts are resisted")))
                config.set("borealis.lore", java.util.List.of(
                    "&7Borealis sealed his soldiers in the Glacial Vault, where True Ice claimed their hearts.",
                    "&7Blades slide from his shell and arrows shatter. Bring the alchemist's heat of the Nether:",
                    "&6Splash Fire Resistance &7melts his armor for 6 seconds. Then wield the gravedigger's tool:",
                    "&bShovels deal 2.5x damage during Thermal Shock. &7The counter recovers every 20 seconds.",
                    "&7Slay his Frostguard before Cruel Siphon consumes them. Leather wards off Deep Freeze.",
                    "&7When his bow breaks, beware the axe: the tyrant's final winter has begun."));
            config.set("balance.borealis-thermal-upgraded", true);
            saveConfig();
        }
        boolean removedEquipmentDrops = false;
        for (BossType type : BossType.values()) {
            String path = type.getConfigKey() + ".loot.rare-drops.signature-weapon-chance";
            if (config.contains(path)) {
                config.set(path, null);
                removedEquipmentDrops = true;
            }
        }
        if (removedEquipmentDrops) saveConfig();

        if (net.rocky.coresuite.module.worldbosses.rewards.BossRewardBalance.upgradeKeyBalance(config)) saveConfig();
        if (net.rocky.coresuite.module.worldbosses.rewards.BossRewardBalance.upgradeMythicPrices(config)) saveConfig();
        if (net.rocky.coresuite.module.worldbosses.rewards.BossRewardBalance.upgrade(config)) saveConfig();
        if (!config.getBoolean("balance.crate-alias-upgraded", false)) {
            for (String key : config.getKeys(true)) {
                Object value = config.get(key);
                if (value instanceof String command && command.startsWith("crate key give %player% "))
                    config.set(key, command.replaceFirst("^crate ", "crates "));
                else if (value instanceof java.util.List<?> values && values.stream().allMatch(String.class::isInstance))
                    config.set(key, values.stream().map(Object::toString).map(command -> command.replaceFirst("^crate key give %player% ", "crates key give %player% ")).toList());
            }
            config.set("balance.crate-alias-upgraded", true);
            saveConfig();
        }
        if (config.getBoolean("balance.defaults-upgraded", false)) return;
        // Migrate only exact former stock values; retain deliberately customized settings.
        if (config.getInt("scheduler.min-players", 1) == 3) config.set("scheduler.min-players", 1);
        if (config.getDouble("xolthos.weakness.base-evasion-percent", .25) == .90)
            config.set("xolthos.weakness.base-evasion-percent", .25);
        if (config.getDouble("aethelgard.weakness.soil-regen-hp", 6) == 30)
            config.set("aethelgard.weakness.soil-regen-hp", 6);
        java.util.List<String> lore = config.getStringList("aethelgard.lore");
        if (!lore.isEmpty()) config.set("aethelgard.lore", lore.stream().map(line -> line.replace(
                "His wooden armor swallows arrows whole, turning them into vitality.",
                "His wooden armor resists arrows, but axes expose his weakness.")).toList());
        config.set("balance.defaults-upgraded", true);
        saveConfig();
    }

    private void initDatabase() {
        plugin.getDatabaseManager().execute(
            "CREATE TABLE IF NOT EXISTS coresuite_boss_kills (" +
            (plugin.getDatabaseManager().isMySQL() ? "id INTEGER PRIMARY KEY AUTO_INCREMENT, " : "id INTEGER PRIMARY KEY AUTOINCREMENT, ") +
            "boss_type TEXT NOT NULL, " +
            "mvp_uuid TEXT NOT NULL, " +
            "mvp_name TEXT NOT NULL, " +
            "total_damage REAL NOT NULL, " +
            "kill_time BIGINT NOT NULL" +
            ");"
        );

        plugin.getDatabaseManager().execute(
            "CREATE TABLE IF NOT EXISTS coresuite_boss_stats (" +
            "uuid VARCHAR(36) PRIMARY KEY, " +
            "player_name TEXT NOT NULL, " +
            "kills INTEGER DEFAULT 0, " +
            "mvps INTEGER DEFAULT 0" +
            ");"
        );
    }

    public boolean isBossActive() {
        return activeBoss != null && activeBoss.isActive();
    }

    public WorldBoss getActiveBoss() {
        return activeBoss;
    }

    private String lastSpawnFailure = "Unknown spawn failure.";
    public String getLastSpawnFailure() { return lastSpawnFailure; }
    private boolean rejectSpawn(BossType type, Location location, String reason) {
        lastSpawnFailure = reason;
        plugin.getLogger().warning("[WorldBosses] Spawn rejected for " + type + " at " + location + ": " + reason);
        return false;
    }
    public boolean spawnBoss(BossType type, Location loc) {
        if (!Bukkit.isPrimaryThread()) return rejectSpawn(type, loc, "Spawn must run on the server thread.");
        if (!isEnabled()) return rejectSpawn(type, loc, "WorldBosses module is disabled.");
        if (type == null) return rejectSpawn(type, loc, "Unknown boss type.");
        if (isBossActive()) return rejectSpawn(type, loc, "Another boss is already active.");
        if (loc == null || loc.getWorld() == null) return rejectSpawn(type, loc, "No arena world/location is available.");
        // Ensure the arena terrain chunks are loaded before inspecting terrain safety
        if (loc.getWorld() != null) {
            int cx = loc.getBlockX() >> 4;
            int cz = loc.getBlockZ() >> 4;
            int reachChunks = (int) Math.ceil(config.getDouble("domain.radius", 35.0) / 16.0) + 1;
            for (int dx = -reachChunks; dx <= reachChunks; dx++) {
                for (int dz = -reachChunks; dz <= reachChunks; dz++) {
                    if (!loc.getWorld().isChunkLoaded(cx + dx, cz + dz)) {
                        loc.getWorld().getChunkAt(cx + dx, cz + dz);
                    }
                }
            }
        }

        String problem = BossSpawnScheduler.locationProblem(loc);
        boolean checkRegions = !BossSpawnScheduler.bypassFixedArenaProtection(config, loc);
        if (problem == null && checkRegions) problem = net.rocky.coresuite.common.integration.BossProtectionGate.denialReason(loc);
        if (problem == null) problem = BossSpawnScheduler.arenaProblem(loc,
                Math.max(1, Math.min(256, config.getDouble("domain.radius", 35.0))),
                net.rocky.coresuite.common.integration.BossProtectionGate::denialReason, checkRegions);
        if (problem != null) return rejectSpawn(type, loc, problem);
        loc = new Location(loc.getWorld(), loc.getBlockX() + .5, loc.getBlockY(), loc.getBlockZ() + .5);
        if (loc.getWorld().getDifficulty() == org.bukkit.Difficulty.PEACEFUL) {
            return rejectSpawn(type, loc, "World '" + loc.getWorld().getName() + "' is set to Peaceful difficulty.");
        }

        try {
            WorldBoss boss = switch (type) {
                case VALERIUS -> new ValeriusBoss(plugin, config, lootManager);
                case AETHELGARD -> new AethelgardBoss(plugin, config, lootManager);
                case XOLTHOS -> new XolthosBoss(plugin, config, lootManager);
                case BOREALIS -> new BorealisBoss(plugin, config, lootManager);
            };

            boolean success = boss.spawn(loc);
            if (success) {
                boss.setPartyManager(this.partyManager);
                boss.setDefeatCallback(() -> {
                    if (portalManager != null) portalManager.spawnExitPortal(boss.getSpawnLocation());
                });
                this.activeBoss = boss;
                lastSpawnFailure = "";
            } else return rejectSpawn(type, loc, "Entity creation/initialization failed; a spawn event may have been cancelled. See the preceding exception if present.");
            return success;
        } catch (Throwable t) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "[WorldBosses] Exception in spawnBoss", t);
            return rejectSpawn(type, loc, "Initialization error: " + t.getClass().getSimpleName() + ": " + t.getMessage());
        }
    }

    public void despawnActiveBoss() {
        if (activeBoss != null) {
            activeBoss.despawn();
            activeBoss = null;
        }
        if (partyManager != null) {
            partyManager.reset();
        }
        if (portalManager != null) {
            portalManager.endWithoutVictory();
        }
    }

    public BossPortalManager getPortalManager() {
        return portalManager;
    }

    public boolean isEncounterPending() {
        return portalManager != null && portalManager.isActive();
    }

    public boolean initiateBossEncounter(BossType type, Location targetLoc) {
        if (!isEnabled()) return rejectSpawn(type, targetLoc, "WorldBosses module is disabled.");
        if (isBossActive()) return rejectSpawn(type, targetLoc, "Another boss is still alive. Defeat it or use /bossadmin despawn first.");
        if (isEncounterPending()) return rejectSpawn(type, targetLoc, "Arena rifts are still active. Let players exit, or use /bossadmin despawn to clear the previous encounter.");
        lastSpawnFailure = "";

        Location arenaLoc = targetLoc;
        if (arenaLoc == null && config.getBoolean("arena.fixed-location.enabled", true)) {
            String worldName = config.getString("arena.fixed-location.world", "world");
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                double x = config.getDouble("arena.fixed-location.x", 49662.0);
                double y = config.getDouble("arena.fixed-location.y", 205.0);
                double z = config.getDouble("arena.fixed-location.z", 49528.0);
                arenaLoc = new Location(world, x, y, z);
            }
        }

        if (arenaLoc == null) {
            return rejectSpawn(type, targetLoc, "No arena location is available. Check arena.fixed-location.world or specify coordinates.");
        }

        if (portalManager != null && config.getBoolean("arena.portals.enabled", true)) {
            final Location finalArenaLoc = arenaLoc;
            if (finalArenaLoc.getWorld() != null) {
                World w = finalArenaLoc.getWorld();
                int cx = finalArenaLoc.getBlockX() >> 4;
                int cz = finalArenaLoc.getBlockZ() >> 4;
                for (int dx = -4; dx <= 4; dx++) {
                    for (int dz = -4; dz <= 4; dz++) {
                        w.getChunkAtAsync(cx + dx, cz + dz);
                    }
                }
                w.getChunkAtAsync(49610 >> 4, 49528 >> 4);
                w.getChunkAtAsync(49718 >> 4, 49528 >> 4);
            }
            portalManager.spawnPortals(finalArenaLoc, () -> {
                if (isEnabled() && !isBossActive()) {
                    if (!spawnBoss(type, finalArenaLoc)) portalManager.cleanup();
                }
            });
            return true;
        } else {
            return spawnBoss(type, arenaLoc);
        }
    }

    public void killActiveBoss() {
        if (activeBoss != null) {
            activeBoss.die();
            activeBoss = null;
        }
        if (partyManager != null) {
            partyManager.reset();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Combat Event Handlers
    // ─────────────────────────────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPrePlayerAttackEntity(PrePlayerAttackEntityEvent event) {
        Player p = event.getPlayer();
        lastAttackCooldownStrength.put(p.getUniqueId(), p.getCooledAttackStrength(0.5f));
        lastAttackCooldownTime.put(p.getUniqueId(), System.currentTimeMillis());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        // Friendly Fire Guard: Raid Party members cannot damage each other
        if (activeBoss != null && activeBoss.isActive() && event.getEntity() instanceof Player targetPlayer) {
            Player attackerPlayer = null;
            if (event.getDamager() instanceof Player p) {
                attackerPlayer = p;
            } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
                attackerPlayer = p;
            }
            if (attackerPlayer != null && partyManager != null && partyManager.areAllies(attackerPlayer.getUniqueId(), targetPlayer.getUniqueId())) {
                event.setCancelled(true);
                TextUtil.sendActionBar(attackerPlayer, "&a✦ ALLIED! &7Friendly fire disabled for Raid Party members.");
                return;
            }
        }

        // 1. Signature Weapon Traits against all living entities
        if (event.getDamager() instanceof Player player && event.getEntity() instanceof LivingEntity target) {
            handleSignatureWeaponEffects(event, player, target);
        }

        if (activeBoss == null || !activeBoss.isActive()) return;

        LivingEntity bossEntity = activeBoss.getEntity();
        if (bossEntity == null || !bossEntity.isValid()) return;

        NamespacedKey minionKey = new NamespacedKey(plugin, "boss_minion");
        Entity damagerEntity = event.getDamager();
        if (damagerEntity instanceof Projectile proj && proj.getShooter() instanceof Entity shooter) {
            damagerEntity = shooter;
        }

        boolean isDamagerBoss = damagerEntity.equals(bossEntity);
        boolean isDamagerMinion = activeBoss.isMinion(damagerEntity) || damagerEntity.getPersistentDataContainer().has(minionKey, PersistentDataType.BYTE);
        boolean isBossTarget = event.getEntity().equals(bossEntity);
        boolean isMinionTarget = activeBoss.isMinion(event.getEntity()) || event.getEntity().getPersistentDataContainer().has(minionKey, PersistentDataType.BYTE);

        // Friendly Fire Guard: Boss and minions never damage each other
        if ((isDamagerBoss && isMinionTarget) || (isDamagerMinion && isBossTarget) || (isDamagerMinion && isMinionTarget)) {
            event.setCancelled(true);
            return;
        }

        if (isBossTarget || isMinionTarget) {
            // Barrier combat boundary enforcement: attackers outside barrier cannot damage boss or minions
            Location damagerLoc = event.getDamager().getLocation();
            if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof org.bukkit.entity.Entity shooter) {
                damagerLoc = shooter.getLocation();
            }
            if (activeBoss.getDomainManager() != null && !activeBoss.getDomainManager().isInDomain(damagerLoc)) {
                event.setCancelled(true);
                return;
            }

            if (!activeBoss.isAwakened()) {
                event.setCancelled(true);
                if (event.getDamager() instanceof Player p) {
                    TextUtil.sendActionBar(p, "&e⏳ Boss is awakening in &6" + activeBoss.getAwakeningRemainingSeconds() + "s&e! Prepare your gear & buffs!");
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.5f);
                } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
                    TextUtil.sendActionBar(p, "&e⏳ Boss is awakening in &6" + activeBoss.getAwakeningRemainingSeconds() + "s&e! Prepare your gear & buffs!");
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.5f);
                }
                return;
            }

            float threshold = (float) config.getDouble("combat.attack-cooldown-threshold", 0.85);
            double glancingFactor = config.getDouble("combat.glancing-damage-factor", 0.30);
            boolean comboEnabled = config.getBoolean("combat.combo.enabled", true);
            int maxCombo = config.getInt("combat.combo.max-combo", 5);
            double bonusPerCombo = config.getDouble("combat.combo.bonus-damage-per-combo", 0.05);
            long comboTimeoutMs = config.getLong("combat.combo.combo-timeout-seconds", 4) * 1000L;

            float charge = 1.0f; // Default fully charged
            Player player = null;
            ItemStack weapon = null;

            if (event.getDamager() instanceof Player p) {
                player = p;
                weapon = p.getInventory().getItemInMainHand();

                Long recordedTime = lastAttackCooldownTime.get(p.getUniqueId());
                if (recordedTime != null && (System.currentTimeMillis() - recordedTime) < 500L) {
                    Float recordedStrength = lastAttackCooldownStrength.get(p.getUniqueId());
                    if (recordedStrength != null) {
                        charge = recordedStrength;
                    }
                } else {
                    charge = p.getCooledAttackStrength(0.5f);
                }
            } else if (event.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player p) {
                player = p;
                weapon = proj instanceof org.bukkit.entity.Trident trident ? trident.getItemStack()
                        : new ItemStack(Material.BOW);
            }

            if (player != null && portalManager != null && !portalManager.canParticipate(player)) {
                event.setCancelled(true);
                TextUtil.sendActionBar(player, "&cEnter through an arena rift before joining this fight.");
                return;
            }
            if (player != null) {
                if (isMinionTarget && event.getFinalDamage() > 0) activeBoss.recordParticipation(player);
                double rawDamage = event.getDamage();
                boolean isGlancing = (event.getDamager() instanceof Player) && (charge < threshold);

                if (isGlancing) {
                    // Fluid Glancing Strike: deals smooth scaled damage without annoying metallic clangs
                    double glancingDmg = Math.max(0.0, rawDamage * glancingFactor);
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_WEAK, 0.9f, 1.2f);
                    comboStreak.remove(player.getUniqueId()); // Glancing strikes break the rhythm

                    if (isBossTarget) {
                        activeBoss.recordDamage(player, glancingDmg, weapon);
                        event.setDamage(0.0);
                        int chargePct = (int) (charge * 100);
                        activeBoss.sendCombatAlert(
                            player,
                            StatusBadge.DEFLECTED,
                            "&7Glancing Hit (" + chargePct + "%) &8| &7Charge attacks for full DMG!",
                            1
                        );
                    } else {
                        event.setDamage(glancingDmg);
                    }
                } else {
                    // Fully charged strike: builds Hunter's Rhythm (Combo Streak)
                    long now = System.currentTimeMillis();
                    int currentStreak = 1;
                    if (comboEnabled) {
                        Long lastHit = lastComboTime.get(player.getUniqueId());
                        if (lastHit != null && (now - lastHit) <= comboTimeoutMs) {
                            currentStreak = Math.min(maxCombo, comboStreak.getOrDefault(player.getUniqueId(), 1) + 1);
                        }
                        comboStreak.put(player.getUniqueId(), currentStreak);
                        lastComboTime.put(player.getUniqueId(), now);
                    }

                    double comboMultiplier = 1.0 + ((currentStreak - 1) * bonusPerCombo);
                    double finalDamage = rawDamage * comboMultiplier;

                    if (isBossTarget) {
                        activeBoss.recordDamage(player, finalDamage, weapon);
                        event.setDamage(0.0);

                        boolean isPickaxeOnValerius = (activeBoss instanceof ValeriusBoss) && weapon != null && weapon.getType().name().endsWith("_PICKAXE");
                        boolean hasSignatureWeapon = weapon != null && (itemRegistry.hasSignatureWeapon(weapon, "vanguards_cleaver") || itemRegistry.hasSignatureWeapon(weapon, "widows_fang"));

                        if (currentStreak > 1 && !isPickaxeOnValerius && !hasSignatureWeapon) {
                            player.playSound(player.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 1.0f + (currentStreak * 0.12f));
                            int bonusPct = (int) Math.round((comboMultiplier - 1.0) * 100);
                            double hpPct = (activeBoss.getCurrentHealth() / activeBoss.getMaxHealth()) * 100.0;
                            activeBoss.sendCombatAlert(
                                player,
                                null,
                                "&6⚔ Combo &e[x" + currentStreak + " &a+" + bonusPct + "%&e] &8| &c" + String.format("%.0f", hpPct) + "% HP",
                                1
                            );
                        } else if (currentStreak > 1) {
                            player.playSound(player.getLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 1.0f + (currentStreak * 0.12f));
                        }
                    } else {
                        event.setDamage(finalDamage);
                    }
                }

                if (isBossTarget && !activeBoss.isActive()) {
                    activeBoss = null;
                }
                return;
            }
            if (isBossTarget) event.setCancelled(true);
        }

        // Boss or minion damaging Player
        if ((isDamagerBoss || isDamagerMinion) && event.getEntity() instanceof Player player) {
            // Barrier combat boundary enforcement: boss/minions cannot damage players outside the barrier
            if (activeBoss.getDomainManager() != null && !activeBoss.getDomainManager().isInDomain(player.getLocation())) {
                event.setCancelled(true);
                return;
            }
            if (player.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                event.setCancelled(true);
                return;
            }

            if (!activeBoss.isAwakened()) {
                event.setCancelled(true);
                return;
            }

            if (event.getFinalDamage() > 0) activeBoss.recordTankContribution(player);
            event.setDamage(event.getDamage() * activeBoss.getOutgoingDamageMultiplier());
            // Xolthos Stalker Ambush Strike from Invisibility
            if (activeBoss instanceof XolthosBoss xb && xb.isStealthed()) {
                xb.triggerAmbushStrike(player);
            }

            // Valerius Melee Wither infliction & Lifesteal
            if (activeBoss instanceof ValeriusBoss valerius) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 80, 1));
                if (valerius.healFromAttack(event.getFinalDamage()) > 0)
                    bossEntity.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, bossEntity.getLocation().add(0, 1.2, 0), 10, 0.4, 0.4, 0.4, 0.05);
            }

            // Aethelgard Phase 3 Lifesteal Mechanic
            if (activeBoss instanceof AethelgardBoss && activeBoss.getCurrentPhase() != null && activeBoss.getCurrentPhase().getPhaseNumber() == 3) {
                double lifestealPct = config.getDouble("aethelgard.phases.phase-3.lifesteal-percent", 1.0);
                double healAmount = event.getFinalDamage() * lifestealPct;
                activeBoss.heal(healAmount);
                bossEntity.getWorld().spawnParticle(org.bukkit.Particle.HEART, bossEntity.getLocation().add(0, 1.5, 0), 4, 0.3, 0.3, 0.3, 0.05);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossEnvironmentalDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event instanceof EntityDamageByEntityEvent) && activeBoss != null
                && event.getEntity().equals(activeBoss.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile proj = event.getEntity();
        if (activeBoss != null && activeBoss.getDomainManager() != null) {
            if (proj.getShooter() instanceof LivingEntity shooter) {
                LivingEntity bossEntity = activeBoss.getEntity();
                boolean shooterIsBoss = bossEntity != null && shooter.equals(bossEntity);
                boolean shooterIsMinion = activeBoss.isMinion(shooter);
                if (shooterIsBoss || shooterIsMinion) {
                    Location hitLoc = event.getHitEntity() != null ? event.getHitEntity().getLocation()
                            : (event.getHitBlock() != null ? event.getHitBlock().getLocation() : proj.getLocation());
                    if (!activeBoss.getDomainManager().isInDomain(hitLoc)) {
                        event.setCancelled(true);
                        proj.remove();
                        return;
                    }
                    if (event.getHitEntity() instanceof Player p && !activeBoss.getDomainManager().isInDomain(p.getLocation())) {
                        event.setCancelled(true);
                        proj.remove();
                        return;
                    }
                } else if (shooter instanceof Player p) {
                    if (event.getHitEntity() != null && (event.getHitEntity().equals(bossEntity) || activeBoss.isMinion(event.getHitEntity()))) {
                        if (!activeBoss.getDomainManager().isInDomain(p.getLocation())) {
                            event.setCancelled(true);
                            proj.remove();
                            return;
                        }
                    }
                }
            }
        }
        NamespacedKey projKey = new NamespacedKey(plugin, "boss_projectile");
        if (!proj.getPersistentDataContainer().has(projKey, PersistentDataType.STRING)) return;

        String type = proj.getPersistentDataContainer().get(projKey, PersistentDataType.STRING);
        if (type == null) return;
        if (activeBoss == null || !activeBoss.isActive()
                || !(proj.getShooter() instanceof LivingEntity shooter)
                || !(shooter.equals(activeBoss.getEntity()) || activeBoss.isMinion(shooter))) {
            proj.remove();
            return;
        }

        Location hitLoc = event.getHitBlock() != null
                ? event.getHitBlock().getLocation().add(0.5, 1.0, 0.5)
                : (event.getHitEntity() != null ? event.getHitEntity().getLocation() : proj.getLocation());

        switch (type) {
            case "CALDERA_MORTAR" -> explodeCalderaMortar(hitLoc, proj);
            case "BRIAR_VOLLEY" -> {
                if (event.getHitEntity() instanceof Player p) {
                    double damage = config.getDouble("aethelgard.combat.briar-volley.damage", 10.0);
                    int poisonSec = config.getInt("aethelgard.combat.briar-volley.poison-duration", 3);
                    p.damage(damage, proj);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, poisonSec * 20, 0));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0));
                    p.playSound(p.getLocation(), Sound.ENCHANT_THORNS_HIT, 1.2f, 1.2f);
                    if (activeBoss != null) {
                        activeBoss.sendCombatAlert(p, StatusBadge.BRIAR_VOLLEY, "&aRazor-sharp brambles pierced your armor!", 2);
                    }
                }
            }
            case "WEB_SPIT" -> {
                double damage = config.getDouble("xolthos.combat.web-spit.damage", 10.0);
                int snareSec = config.getInt("xolthos.combat.web-spit.snare-duration-seconds", 4);
                int poisonSec = config.getInt("xolthos.combat.web-spit.poison-duration", 5);
                int slownessSec = config.getInt("xolthos.combat.web-spit.slowness-duration", 4);

                hitLoc.getWorld().spawnParticle(Particle.ITEM_COBWEB, hitLoc, 20, 0.5, 0.5, 0.5, 0.1);
                hitLoc.getWorld().playSound(hitLoc, Sound.BLOCK_COBWEB_PLACE, 1.5f, 0.9f);

                if (event.getHitEntity() instanceof Player p) {
                    p.damage(damage, proj);
                    p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, poisonSec * 20, 1));
                    p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slownessSec * 20, 3));
                    if (activeBoss != null) {
                        activeBoss.sendCombatAlert(p, StatusBadge.WEB_SNARE, "&dEntangled in acidic arachnid silk!", snareSec);
                    }
                }

                Block block = hitLoc.getBlock();
                if (activeBoss != null) activeBoss.placeTemporaryBlock(block, Material.COBWEB, snareSec * 20L);
            }
        }
        proj.remove();
    }

    public void explodeCalderaMortar(Location hitLoc, Entity proj) {
        if (hitLoc == null || hitLoc.getWorld() == null) return;

        hitLoc.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, hitLoc, 1, 0, 0, 0, 0, null, true);
        hitLoc.getWorld().spawnParticle(Particle.LAVA, hitLoc, 35, 1.5, 1.0, 1.5, 0.2, null, true);
        hitLoc.getWorld().playSound(hitLoc, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.8f);

        double radius = config.getDouble("valerius.combat.caldera-mortar.splash-radius", 4.0);
        double damage = config.getDouble("valerius.combat.caldera-mortar.damage", 12.0);
        int witherSec = config.getInt("valerius.combat.caldera-mortar.wither-seconds", 5);

        for (Entity e : hitLoc.getWorld().getNearbyEntities(hitLoc, radius, radius, radius)) {
            if (e instanceof Player p) {
                p.damage(damage, proj);
                p.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, witherSec * 20, 1));
                if (activeBoss != null) {
                    activeBoss.sendCombatAlert(p, StatusBadge.CALDERA_MORTAR, "&cMolten artillery bombarded your position!", 3);
                }
            }
        }

        Block block = hitLoc.getBlock();
        if (activeBoss != null) activeBoss.placeTemporaryBlock(block, Material.FIRE, 120L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        if (event.isCancelled()) return;
        if (activeBoss == null || !activeBoss.isActive()) return;

        LivingEntity bossEntity = activeBoss.getEntity();
        if (bossEntity == null || !bossEntity.isValid()) return;

        ThrownPotion potion = event.getPotion();
        if (!(potion.getShooter() instanceof Player participant) || !activeBoss.isAwakened()
                || !net.rocky.coresuite.common.integration.AuthenticationGate.isAuthenticated(participant)
                || (portalManager != null && !portalManager.canParticipate(participant))
                || (activeBoss.getDomainManager() != null && !activeBoss.getDomainManager().isInDomain(participant.getLocation()))) return;
        ItemStack item = potion.getItem();
        if (!(item.getItemMeta() instanceof PotionMeta meta)) return;

        PotionType baseType = meta.getBasePotionType();
        boolean isWater = baseType == PotionType.WATER && meta.getCustomEffects().isEmpty();
        if (isWater && activeBoss instanceof ValeriusBoss valerius) {
            // Water's affected-entity map contains water-sensitive mobs, not ordinary wither skeletons.
            if (!net.rocky.coresuite.module.worldbosses.combat.BossPotionHit.waterReaches(potion.getLocation(), bossEntity, event.getHitEntity() == bossEntity)) return;
            if (valerius.applyQuenched(config.getInt("valerius.weakness.quenched-duration-seconds", 10)))
                activeBoss.recordMechanicContribution(participant, "Water interrupted Valerius");
            return;
        }
        if (!event.getAffectedEntities().contains(bossEntity) || event.getIntensity(bossEntity) <= 0) return;
        if (activeBoss instanceof BorealisBoss borealis && potion.getEffects().stream()
                .anyMatch(effect -> effect.getType().equals(PotionEffectType.FIRE_RESISTANCE))) {
            if (borealis.applyThermalShock()) activeBoss.recordMechanicContribution(participant, "Thermal Shock shattered True Ice");
            else TextUtil.sendMessage(participant, "&bThermal Shock is recovering. Wait for its 20-second cooldown.");
            return;
        }
        boolean hasNightVision = false;

        if (baseType != null && baseType.name().contains("NIGHT_VISION")) {
            hasNightVision = true;
        }
        for (PotionEffect effect : meta.getCustomEffects()) {
            if (effect.getType().equals(PotionEffectType.NIGHT_VISION)) {
                hasNightVision = true;
                break;
            }
        }
        for (PotionEffect effect : potion.getEffects()) {
            if (effect.getType().equals(PotionEffectType.NIGHT_VISION)) {
                hasNightVision = true;
                break;
            }
        }

        // 1. Splash Water Bottle Counter on Valerius

        // 2. Splash Night Vision Counter on Xol'thos
        if (hasNightVision && activeBoss instanceof XolthosBoss xolthos) {
            int duration = config.getInt("xolthos.weakness.stun-duration-seconds", 20);
            if (xolthos.applyStun(duration)) activeBoss.recordMechanicContribution(participant, "Night Vision revealed Xol'thos");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onGlacialGuardDamage(org.bukkit.event.entity.EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || itemRegistry == null) return;
        var cause = event.getCause();
        if (cause != org.bukkit.event.entity.EntityDamageEvent.DamageCause.FREEZE
                && cause != org.bukkit.event.entity.EntityDamageEvent.DamageCause.FALL) return;
        if (!itemRegistry.hasSignatureWeapon(player.getInventory().getBoots(), "frostbound_striders")) return;
        if (cause == org.bukkit.event.entity.EntityDamageEvent.DamageCause.FREEZE) event.setCancelled(true);
        else event.setDamage(event.getDamage() * .75);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBorealisFreezeMovement(org.bukkit.event.player.PlayerMoveEvent event) {
        if (activeBoss instanceof BorealisBoss borealis) borealis.restrainFrozenMovement(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBorealisLandedAttack(EntityDamageByEntityEvent event) {
        if (!(activeBoss instanceof BorealisBoss borealis) || !(event.getEntity() instanceof Player player)
                || event.getFinalDamage() <= 0) return;
        if (event.getDamager().equals(borealis.getEntity())) borealis.onLandedAttack(player, false);
        else if (event.getDamager() instanceof org.bukkit.entity.Arrow arrow && arrow.getShooter() == borealis.getEntity())
            borealis.onLandedAttack(player, true);
    }

    private void startSignatureItemsTask() {
        if (signatureItemsTask != null) {
            signatureItemsTask.cancel();
        }
        signatureItemsTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (activeBoss != null && activeBoss.isActive() && activeBoss.isIdleExpired(System.currentTimeMillis())) {
                String name = activeBoss.getDisplayName();
                activeBoss.despawn();
                activeBoss = null;
                if (partyManager != null) partyManager.reset();
                if (portalManager != null) portalManager.endWithoutVictory();
                Bukkit.broadcast(TextUtil.colorize("&e" + name + " &7has left after 30 minutes without combat. No victory rewards were granted."));
            }
            if (portalManager != null) portalManager.tickLifecycle(System.currentTimeMillis());
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!player.isValid() || player.isDead()) continue;

                // Trait: Photosynthesis (Crown of the Forest)
                // Grants passive Regeneration I while standing in daylight or on grass/moss
                ItemStack helmet = player.getInventory().getHelmet();
                if (helmet == null || helmet.getType() != Material.NETHERITE_HELMET) continue;
                if (itemRegistry.hasSignatureWeapon(helmet, "crown_of_the_forest")) {
                    Location loc = player.getLocation();
                    World world = loc.getWorld();
                    if (world != null) {
                        Block blockUnder = loc.getBlock().getRelative(BlockFace.DOWN);
                        Material underType = blockUnder.getType();
                        Material atFeetType = loc.getBlock().getType();
                        boolean onNature = underType == Material.GRASS_BLOCK
                                || underType == Material.MOSS_BLOCK
                                || underType == Material.MOSS_CARPET
                                || underType == Material.ROOTED_DIRT
                                || underType == Material.DIRT
                                || underType == Material.COARSE_DIRT
                                || underType == Material.PODZOL
                                || underType == Material.FARMLAND
                                || underType == Material.MUD
                                || underType == Material.DIRT_PATH
                                || atFeetType == Material.MOSS_CARPET;

                        long time = world.getTime();
                        boolean isDay = time < 12500 || time > 23500;
                        int skyLight = Math.max(loc.getBlock().getLightFromSky(), loc.clone().add(0, 1, 0).getBlock().getLightFromSky());
                        boolean inDaylight = isDay && skyLight >= 10 && !world.hasStorm();

                        if (onNature || inDaylight) {
                            player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 60, 0, true, true, true));
                            try {
                                world.spawnParticle(Particle.SPORE_BLOSSOM_AIR, loc.clone().add(0, 1.2, 0), 3, 0.3, 0.4, 0.3, 0.02);
                            } catch (Throwable ignored) {}
                        }
                    }
                }
            }
        }, 40L, 40L);
    }

    private void handleSignatureWeaponEffects(EntityDamageByEntityEvent event, Player player, LivingEntity target) {
        if (event.getFinalDamage() <= 0) return;
        if (activeBoss != null && activeBoss.isActive()
                && (target.equals(activeBoss.getEntity()) || activeBoss.isMinion(target))) {
            if (!activeBoss.isAwakened()
                    || (activeBoss.getDomainManager() != null && !activeBoss.getDomainManager().isInDomain(player.getLocation()))
                    || (portalManager != null && !portalManager.canParticipate(player))) return;
        }
        ItemStack held = player.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) return;

        // Trait: Ignited Edge (The Vanguard's Cleaver)
        // Strikes against burning targets deal +20% bonus damage and erupt in scalding magma sparks
        if (itemRegistry.hasSignatureWeapon(held, "vanguards_cleaver")) {
            if (target.getFireTicks() > 0) {
                double bonusDmg = event.getDamage() * 0.20;
                event.setDamage(event.getDamage() + bonusDmg);
                try {
                    target.getWorld().spawnParticle(Particle.LAVA, target.getLocation().add(0, 1.0, 0), 10, 0.3, 0.3, 0.3, 0.05);
                    target.getWorld().playSound(target.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 0.8f, 1.2f);
                } catch (Throwable ignored) {}
                TextUtil.sendActionBar(player, "&c&l✦ IGNITED EDGE! &e+20% Bonus Damage &8(&c+" + String.format("%.1f", bonusDmg) + " DMG&8)");
            }
        }

        // Trait: Envenom (The Widow's Fang)
        // Attacks apply Poison II for 4s and grant the wielder Speed I for 3s
        if (itemRegistry.hasSignatureWeapon(held, "widows_fang")) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 80, 1, true, true, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 0, true, true, true));

            try {
                target.getWorld().spawnParticle(Particle.ITEM_SLIME, target.getLocation().add(0, 1.0, 0), 10, 0.3, 0.3, 0.3, 0.05);
                player.playSound(player.getLocation(), Sound.ENTITY_SPIDER_HURT, 0.8f, 1.4f);
            } catch (Throwable ignored) {}
            TextUtil.sendActionBar(player, "&5&l✦ ENVENOM! &dPoison II applied &8| &bSpeed I");
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.RIGHT_CLICK_AIR) return;

        ItemStack item = event.getItem();
        if (item == null || !item.hasItemMeta()) return;

        // Restrict totem interactions strictly to the main hand to avoid dual-hand duplicate execution
        if (event.getHand() != EquipmentSlot.HAND) return;

        BossType totemBoss = itemRegistry.getTotemType(item);
        if (totemBoss == null) return;

        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!net.rocky.coresuite.common.integration.AuthenticationGate.isAuthenticated(player)) return;

        if (isBossActive() || isEncounterPending()) {
            TextUtil.sendMessage(player, "&cAnother World Boss or Arena Rift is already active! Slay it first!");
            return;
        }

        int minPlayers = config.getInt("domain.totem-min-players", config.getInt("totem.min-players", 1));
        boolean isAdmin = player.hasPermission("coresuite.boss.admin") || player.isOp();
        if (!isAdmin && Bukkit.getOnlinePlayers().size() < minPlayers) {
            TextUtil.sendMessage(player, "&cAt least &e" + minPlayers + " &cplayers must be online to summon a World Boss!");
            return;
        }

        Location arenaLoc = null;
        if (config.getBoolean("arena.fixed-location.enabled", true)) {
            String worldName = config.getString("arena.fixed-location.world", "world");
            World world = Bukkit.getWorld(worldName);
            if (world != null) {
                double x = config.getDouble("arena.fixed-location.x", 49662.0);
                double y = config.getDouble("arena.fixed-location.y", 205.0);
                double z = config.getDouble("arena.fixed-location.z", 49528.0);
                arenaLoc = new Location(world, x, y, z);
            }
        }
        if (arenaLoc == null) {
            arenaLoc = event.getClickedBlock() != null ? event.getClickedBlock().getLocation().add(0.5, 1.0, 0.5) : player.getLocation();
        }

        if (arenaLoc.getWorld() != null && arenaLoc.getWorld().getDifficulty() == org.bukkit.Difficulty.PEACEFUL) {
            TextUtil.sendMessage(player, "&c[!] World Bosses cannot be summoned in Peaceful difficulty! Please switch world difficulty or summon in the survival world.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.5f);
            return;
        }

        boolean initiated = initiateBossEncounter(totemBoss, arenaLoc);
        if (initiated) {
            item.subtract(1);
            player.playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.5f, 0.8f);
            TextUtil.sendMessage(player, "&aYou have activated the Primordial Totem! Portals are tearing open across the world to &e" + totemBoss.getDefaultDisplayName() + "&a's Arena!");
        } else {
            TextUtil.sendMessage(player, "&cCannot summon: &f" + getLastSpawnFailure() + " &eYour totem was kept.");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDeath(EntityDeathEvent event) {
        if (activeBoss == null || !activeBoss.isActive()) return;

        if (event.getEntity().equals(activeBoss.getEntity())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            activeBoss.die();
            activeBoss = null;
            return;
        }
        if (activeBoss.isMinion(event.getEntity())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
            if (event.getEntity().getKiller() != null)
                activeBoss.recordMinionContribution(event.getEntity().getKiller());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        net.rocky.coresuite.common.integration.AuthenticationGate.afterLogin(plugin, event.getPlayer(), () -> {
            if (isEnabled() && lootManager != null) lootManager.claimRewards(event.getPlayer());
        });
        if (discoveryManager != null) {
            discoveryManager.loadPlayer(event.getPlayer().getUniqueId());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (discoveryManager != null) {
            discoveryManager.unloadPlayer(uuid);
        }
        comboStreak.remove(uuid);
        lastComboTime.remove(uuid);
        lastAttackCooldownStrength.remove(uuid);
        lastAttackCooldownTime.remove(uuid);

        if (activeBoss != null && activeBoss.isActive()) {
            activeBoss.removeViewerOnQuit(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getPlayer();
        if (activeBoss != null && activeBoss.isActive()) {
            activeBoss.removeViewerOnQuit(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (activeBoss != null && activeBoss.isActive()) {
            activeBoss.removeViewerOnQuit(player);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event) {
        if (activeBoss == null || !activeBoss.isActive()) return;
        net.rocky.coresuite.module.worldbosses.combat.BossDomainManager dm = activeBoss.getDomainManager();
        if (dm == null) return;

        Player player = event.getPlayer();
        if (player.hasPermission("coresuite.boss.admin") || player.isOp()) return;

        if (dm.isLockedIn(player.getUniqueId())) {
            String message = event.getMessage().trim();
            if (message.startsWith("/")) {
                message = message.substring(1);
            }
            String root = message.split(" ")[0].toLowerCase();

            List<String> blocked = config.getStringList("domain.lockdown.blocked-commands");
            if (blocked.isEmpty()) {
                blocked = Arrays.asList("spawn", "home", "tpa", "tpahere", "tpaccept", "warp", "back", "hub");
            }

            for (String cmd : blocked) {
                if (root.equalsIgnoreCase(cmd)) {
                    event.setCancelled(true);
                    player.playSound(player.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1.2f, 0.8f);
                    TextUtil.sendMessage(player, "&c&l[!] DOMAIN SEAL: &7Escape command &e/" + root + " &7is suppressed by the arena barrier!");
                    TextUtil.sendActionBar(player, "&c&l[!] Escape commands are blocked while locked in combat!");
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityCombust(EntityCombustEvent event) {
        if (activeBoss == null || !activeBoss.isActive()) return;
        if (!config.getBoolean("domain.prevent-daylight-combustion", true)) return;

        Entity entity = event.getEntity();
        boolean isBoss = entity.equals(activeBoss.getEntity());
        NamespacedKey minionKey = new NamespacedKey(plugin, "boss_minion");
        boolean isMinion = activeBoss.isMinion(entity) || entity.getPersistentDataContainer().has(minionKey, PersistentDataType.BYTE);

        if (isBoss || isMinion) {
            // Only cancel sunlight combustion.
            // Combustion by players (fire aspect/flame arrows) or blocks (lava/fire) extends EntityCombustByEntityEvent or EntityCombustByBlockEvent.
            if (!(event instanceof EntityCombustByEntityEvent) && !(event instanceof EntityCombustByBlockEvent)) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityTarget(EntityTargetLivingEntityEvent event) {
        if (activeBoss == null || !activeBoss.isActive()) return;

        Entity entity = event.getEntity();
        Entity target = event.getTarget();
        if (target == null) return;

        LivingEntity bossEntity = activeBoss.getEntity();
        if (bossEntity == null || !bossEntity.isValid()) return;

        NamespacedKey minionKey = new NamespacedKey(plugin, "boss_minion");
        boolean isEntityBoss = entity.equals(bossEntity);
        boolean isEntityMinion = activeBoss.isMinion(entity) || entity.getPersistentDataContainer().has(minionKey, PersistentDataType.BYTE);
        boolean isTargetBoss = target.equals(bossEntity);
        boolean isTargetMinion = activeBoss.isMinion(target) || target.getPersistentDataContainer().has(minionKey, PersistentDataType.BYTE);

        // Minions and boss must never target players outside the barrier or spectators
        if ((isEntityBoss || isEntityMinion) && target instanceof Player p) {
            if (p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                event.setCancelled(true);
                return;
            }
            if (activeBoss.getDomainManager() != null && !activeBoss.getDomainManager().isInDomain(p.getLocation())) {
                event.setCancelled(true);
                return;
            }
        }

        // While awakening, boss and minions remain dormant and never target fighters
        if (!activeBoss.isAwakened() && (isEntityBoss || isEntityMinion)) {
            event.setCancelled(true);
            return;
        }

        // Minions must never target the boss or other minions
        if (isEntityMinion && (isTargetBoss || isTargetMinion)) {
            event.setCancelled(true);
            return;
        }

        // Boss must never target its own minions
        if (isEntityBoss && isTargetMinion) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onNaturalHealing(org.bukkit.event.entity.EntityRegainHealthEvent event) {
        if (event.getRegainReason() != org.bukkit.event.entity.EntityRegainHealthEvent.RegainReason.SATIATED) return;
        if (event.getEntity() instanceof Player player && WorldBoss.eligibleFighter(player)
                && activeBoss instanceof XolthosBoss xolthos && xolthos.suppressesNaturalHealing(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        if (activeBoss == null || !activeBoss.isActive()) return;
        if (!config.getBoolean("domain.prevent-sleeping", true)) return;

        Player player = event.getPlayer();
        net.rocky.coresuite.module.worldbosses.combat.BossDomainManager dm = activeBoss.getDomainManager();

        // ONLY cancel if the player is actively inside the boss arena!
        if (dm != null && dm.isInDomain(player.getLocation())) {
            event.setCancelled(true);
            TextUtil.sendMessage(player, "&cYou cannot sleep while in active combat with &4&l" + activeBoss.getDisplayName() + "&c!");
            player.playSound(player.getLocation(), Sound.ENTITY_VEX_HURT, 1.0f, 0.6f);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Command Handling
    // ─────────────────────────────────────────────────────────────────────────────

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("boss")) {
            return handleBossCommand(sender, args);
        } else if (command.getName().equalsIgnoreCase("bossadmin")) {
            return handleBossAdminCommand(sender, args);
        }
        return false;
    }

    private boolean handleBossCommand(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player player) {
                bossCodexGUI.open(player);
            } else {
                sendBossGuide(sender);
            }
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "claim" -> {
                if (sender instanceof Player player) lootManager.claimRewards(player);
                return true;
            }
            case "portals", "portal", "rifts", "rift" -> {
                if (sender instanceof Player player) {
                    if (portalManager != null) portalManager.sendPortalInfo(player);
                    else TextUtil.sendMessage(player, "&cPortal system is not active.");
                } else {
                    TextUtil.sendMessage(sender, "&cOnly players can check portal locations.");
                }
                return true;
            }
            case "enter", "pay", "joinrift" -> {
                if (sender instanceof Player player) {
                    if (portalManager != null) portalManager.handlePlayerEnterCommand(player);
                    else TextUtil.sendMessage(player, "&cPortal system is not active.");
                } else {
                    TextUtil.sendMessage(sender, "&cOnly players can enter portals.");
                }
                return true;
            }
            case "codex", "lore", "menu", "gui" -> {
                if (args.length >= 2) {
                    BossType type = parseBossType(args[1]);
                    if (type != null) {
                        if (sender instanceof Player p) {
                            if (discoveryManager != null && !discoveryManager.hasDiscovered(p.getUniqueId(), type)) {
                                TextUtil.sendMessage(p, "&c🔒 &7This primordial titan has not yet been discovered. Defeat it in battle to unlock its codex entry!");
                                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
                                return true;
                            }
                            bossCodexGUI.sendBossCodexChat(p, type);
                        } else {
                            sendBossGuide(sender);
                        }
                        return true;
                    }
                }
                if (sender instanceof Player player) {
                    bossCodexGUI.open(player);
                } else {
                    sendBossGuide(sender);
                }
                return true;
            }
            case "help", "guide" -> {
                sendBossGuide(sender);
                return true;
            }
            case "info", "status" -> {
                if (!isBossActive()) {
                    int sec = (scheduler != null) ? scheduler.getSecondsRemaining() : 0;
                    String timeStr = formatTime(sec);
                    TextUtil.sendMessage(sender, "&8[&4&lBoss&8] &7No World Boss is currently active. Next scheduled awakening in &e" + timeStr + "&7.");
                    return true;
                }

                TextUtil.sendMessage(sender, "&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                TextUtil.sendMessage(sender, "  &4&l⚔ ACTIVE WORLD BOSS: &r" + activeBoss.getDisplayName());
                double hp = activeBoss.getCurrentHealth();
                double max = activeBoss.getMaxHealth();
                double pct = (hp / max) * 100.0;
                TextUtil.sendMessage(sender, "  &7Health: &c" + String.format("%,.0f", hp) + "&7/&c" + String.format("%,.0f", max) + " HP &8(&e" + String.format("%.1f", pct) + "%&8)");
                if (activeBoss.getCurrentPhase() != null) {
                    TextUtil.sendMessage(sender, "  &7Current Phase: &6Phase " + activeBoss.getCurrentPhase().getPhaseNumber() + " - " + activeBoss.getCurrentPhase().getName());
                }

                BossDamageTracker.DamageEntry mvp = activeBoss.getDamageTracker().getMvp();
                if (mvp != null) {
                    TextUtil.sendMessage(sender, "  &e👑 Current MVP: &f" + mvp.name() + " &8(&c" + String.format("%,.0f", mvp.damage()) + " DMG&8)");
                }

                Location loc = activeBoss.getSpawnLocation();
                if (loc != null && loc.getWorld() != null) {
                    TextUtil.sendMessage(sender, "  &7Domain: &e" + loc.getWorld().getName() + " &8(" + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ() + "&8)");
                }
                TextUtil.sendMessage(sender, "  &7Consult your &6/boss &7Hunting Codex for unlocked lore and combat phases.");
                TextUtil.sendMessage(sender, "  &7Type &e/boss &7to open the full Codex GUI or &e/boss shop &7to exchange relics!");
                TextUtil.sendMessage(sender, "&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
                return true;
            }
            case "next" -> {
                int sec = (scheduler != null) ? scheduler.getSecondsRemaining() : 0;
                TextUtil.sendMessage(sender, "&8[&4&lBoss&8] &7Next scheduled World Boss awakening in &e" + formatTime(sec) + "&7.");
                return true;
            }
            case "shop", "exchange" -> {
                if (!(sender instanceof Player player)) {
                    TextUtil.sendMessage(sender, "&cOnly players can open the Hunter Exchange shop.");
                    return true;
                }
                if (!config.getBoolean("exchange.allow-command-shop", true)) {
                    TextUtil.sendMessage(player, "&cThe Hunter Exchange can only be accessed by visiting Garrick the Hunter at spawn!");
                    return true;
                }
                hunterExchangeGUI.open(player);
                return true;
            }
            case "party", "team", "alliance" -> {
                if (!(sender instanceof Player player)) {
                    TextUtil.sendMessage(sender, "&cOnly players can participate in raid parties.");
                    return true;
                }
                if (!isBossActive()) {
                    TextUtil.sendMessage(player, "&cNo World Boss is currently active! Raid Parties form during boss encounters.");
                    return true;
                }
                String partyAction = (args.length >= 2) ? args[1].toLowerCase() : "join";
                switch (partyAction) {
                    case "join" -> {
                        if (partyManager == null) {
                            TextUtil.sendMessage(player, "&cParty manager is not available.");
                            return true;
                        }
                        if (partyManager.isPartyMember(player.getUniqueId())) {
                            TextUtil.sendMessage(player, "&eYou are already a member of the active Raid Party!");
                            return true;
                        }
                        partyManager.joinParty(player);
                    }
                    case "leave" -> {
                        if (partyManager == null || !partyManager.isPartyMember(player.getUniqueId())) {
                            TextUtil.sendMessage(player, "&cYou are not in the Raid Party.");
                            return true;
                        }
                        partyManager.leaveParty(player);
                    }
                    case "list" -> {
                        if (partyManager == null || partyManager.getMemberCount() == 0) {
                            TextUtil.sendMessage(player, "&8[&6&lRaid Party&8] &7No players have joined the party yet. Type &e/boss party join &7to join!");
                            return true;
                        }
                        TextUtil.sendMessage(player, "&8[&6&lRaid Party&8] &aActive Allied Hunters (&e" + partyManager.getMemberCount() + "&a):");
                        StringBuilder sb = new StringBuilder("  &7");
                        for (UUID memberUuid : partyManager.getPartyMembers()) {
                            Player m = Bukkit.getPlayer(memberUuid);
                            if (m != null && m.isOnline()) {
                                sb.append("&e").append(m.getName()).append("&7, ");
                            }
                        }
                        String listStr = sb.toString();
                        if (listStr.endsWith("&7, ")) {
                            listStr = listStr.substring(0, listStr.length() - 4);
                        }
                        TextUtil.sendMessage(player, listStr);
                    }
                    default -> {
                        TextUtil.sendMessage(player, "&eUsage: /boss party <join|leave|list>");
                    }
                }
                return true;
            }
            default -> {
                BossType directType = parseBossType(sub);
                if (directType != null) {
                    if (sender instanceof Player p) {
                        if (discoveryManager != null && !discoveryManager.hasDiscovered(p.getUniqueId(), directType)) {
                            TextUtil.sendMessage(p, "&c🔒 &7This primordial titan has not yet been discovered. Defeat it in battle to unlock its codex entry!");
                            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 0.8f);
                            return true;
                        }
                        bossCodexGUI.sendBossCodexChat(p, directType);
                    } else {
                        sendBossGuide(sender);
                    }
                    return true;
                }
                sendBossGuide(sender);
                return true;
            }
        }
    }

    private void sendBossGuide(CommandSender sender) {
        TextUtil.sendMessage(sender, "&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        TextUtil.sendMessage(sender, "  &4&l⚔ WORLD BOSSES &8- &6HUNTING CODEX");
        TextUtil.sendMessage(sender, "  &7Repeatable multi-phase raid bosses with unique combat mechanics!");
        TextUtil.sendMessage(sender, "");
        TextUtil.sendMessage(sender, "  &c&l1. Valerius, The Ashen Vanguard &8(&7Wither Skeleton&8)");
        TextUtil.sendMessage(sender, "     &7Ancient infernal sentinel clad in calcified magma armor.");
        TextUtil.sendMessage(sender, "");
        TextUtil.sendMessage(sender, "  &2&l2. Aethelgard, The Root-Bound King &8(&7Husk&8)");
        TextUtil.sendMessage(sender, "     &7Corrupted woodland sovereign commanding primeval nature.");
        TextUtil.sendMessage(sender, "");
        TextUtil.sendMessage(sender, "  &5&l3. Xol'thos, The Weeping Widow &8(&7Cave Spider&8)");
        TextUtil.sendMessage(sender, "     &7Subterranean horror lurking in venomous darkness.");
        TextUtil.sendMessage(sender, "");
        TextUtil.sendMessage(sender, "  &6Commands: &e/boss &8(open Codex GUI) | &e/boss info &8| &e/boss next &8| &e/boss shop &8| &e/boss claim");
        TextUtil.sendMessage(sender, "&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    private boolean handleBossAdminCommand(CommandSender sender, String[] args) {
        if (!sender.hasPermission("coresuite.boss.admin") && !sender.isOp()) {
            TextUtil.sendMessage(sender, "&cYou do not have permission to use this command.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("help")) {
            sendAdminHelp(sender);
            return true;
        }

        try {
            String sub = args[0].toLowerCase();
            switch (sub) {
                case "rewards" -> {
                    if (args.length == 1) lootManager.inspectPendingRewards(sender);
                    else if (args.length == 3 && (args[1].equalsIgnoreCase("retry") || args[1].equalsIgnoreCase("delivered")))
                        lootManager.reconcileReward(sender, args[2], args[1].equalsIgnoreCase("delivered"));
                    else TextUtil.sendMessage(sender, "&e/bossadmin rewards [retry|delivered <receipt>] &7- Inspect first. Retry only after verifying the reward was not delivered; otherwise mark delivered.");
                    return true;
                }
                case "spawn" -> {
                    if (args.length < 2) {
                        TextUtil.sendMessage(sender, "&cUsage: /bossadmin spawn <valerius|aethelgard|xolthos|borealis> [x y z world]");
                        return true;
                    }

                    BossType type = parseBossType(args[1]);
                    if (type == null) {
                        TextUtil.sendMessage(sender, "&cInvalid boss type! Choose: valerius, aethelgard, or xolthos.");
                        return true;
                    }

                    if (isBossActive()) {
                        TextUtil.sendMessage(sender, "&cAnother boss is already alive! Use &e/bossadmin kill &cor &e/bossadmin despawn &cfirst.");
                        return true;
                    }

                    Location spawnLoc;
                    if (args.length >= 6) {
                        try {
                            double x = Double.parseDouble(args[2]);
                            double y = Double.parseDouble(args[3]);
                            double z = Double.parseDouble(args[4]);
                            World world = Bukkit.getWorld(args[5]);
                            if (world == null) {
                                TextUtil.sendMessage(sender, "&cWorld '" + args[5] + "' not found.");
                                return true;
                            }
                            spawnLoc = new Location(world, x, y, z);
                        } catch (NumberFormatException e) {
                            TextUtil.sendMessage(sender, "&cInvalid coordinates format.");
                            return true;
                        }
                    } else {
                        spawnLoc = null; // No coordinates means the configured arena, regardless of sender position.
                    }

                    if (spawnLoc != null && spawnLoc.getWorld() != null && spawnLoc.getWorld().getDifficulty() == org.bukkit.Difficulty.PEACEFUL) {
                        TextUtil.sendMessage(sender, "&c[!] World Bosses cannot spawn in Peaceful difficulty! Please switch world difficulty to Easy, Normal, or Hard.");
                        return true;
                    }

                    try {
                        if (args.length >= 6) {
                            boolean spawned = spawnBoss(type, spawnLoc);
                            if (spawned) {
                                TextUtil.sendMessage(sender, "&aSuccessfully spawned &e" + type.getDefaultDisplayName() + "&a at specified location!");
                            } else {
                                TextUtil.sendMessage(sender, "&cCannot spawn: &f" + getLastSpawnFailure());
                            }
                        } else {
                            boolean initiated = initiateBossEncounter(type, null);
                            if (initiated) {
                                TextUtil.sendMessage(sender, "&aInitiated encounter for &e" + type.getDefaultDisplayName() + "&a! Arena portals are opening.");
                            } else {
                                TextUtil.sendMessage(sender, "&cCannot initiate encounter: &f" + getLastSpawnFailure());
                            }
                        }
                    } catch (Throwable t) {
                        plugin.getLogger().log(java.util.logging.Level.SEVERE, "[WorldBosses] Error in /bossadmin spawn", t);
                        TextUtil.sendMessage(sender, "&cError spawning boss: " + t.getMessage());
                    }
                    return true;
                }
                case "kill" -> {
                    if (!isBossActive()) {
                        if (isEncounterPending()) {
                            despawnActiveBoss();
                            TextUtil.sendMessage(sender, "&aCleared pending/stuck arena rifts. No boss was alive; no rewards given.");
                            return true;
                        }
                        int cleaned = cleanupNearbyOrphans(sender);
                        if (cleaned > 0) {
                            TextUtil.sendMessage(sender, "&aRemoved &e" + cleaned + " &aorphaned boss entities nearby!");
                            return true;
                        }
                        TextUtil.sendMessage(sender, "&cNo World Boss is currently active.");
                        return true;
                    }
                    String name = activeBoss.getDisplayName();
                    killActiveBoss();
                    cleanupNearbyOrphans(sender);
                    TextUtil.sendMessage(sender, "&aSlain &e" + name + " &aand distributed victory rewards!");
                    return true;
                }
                case "despawn", "clear" -> {
                    if (!isBossActive()) {
                        if (isEncounterPending()) {
                            despawnActiveBoss();
                            TextUtil.sendMessage(sender, "&aCleared pending/stuck arena rifts.");
                            return true;
                        }
                        int cleaned = cleanupNearbyOrphans(sender);
                        if (cleaned > 0) {
                            TextUtil.sendMessage(sender, "&aDespawned &e" + cleaned + " &aorphaned boss entities nearby!");
                            return true;
                        }
                        TextUtil.sendMessage(sender, "&cNo World Boss is currently active.");
                        return true;
                    }
                    String name = activeBoss.getDisplayName();
                    despawnActiveBoss();
                    cleanupNearbyOrphans(sender);
                    TextUtil.sendMessage(sender, "&e" + name + " &ahas been despawned cleanly (no rewards given).");
                    return true;
                }
                case "clearall", "purge" -> {
                    despawnActiveBoss();
                    int cleaned = cleanupAllOrphanBosses();
                    TextUtil.sendMessage(sender, "&aCleaned up &e" + cleaned + " &aorphaned/active boss entities across all worlds!");
                    return true;
                }
                case "portals" -> {
                    if (sender instanceof Player player && portalManager != null) {
                        portalManager.sendPortalInfo(player);
                    } else if (portalManager != null) {
                        TextUtil.sendMessage(sender, "&eActive portals: " + portalManager.getActivePortals().size() + " (Forming: " + portalManager.isForming() + ")");
                    } else {
                        TextUtil.sendMessage(sender, "&cPortal manager not active.");
                    }
                    return true;
                }
                case "reload" -> {
                    onReload();
                    TextUtil.sendMessage(sender, "&aWorldBosses configuration and scheduler reloaded successfully!");
                    return true;
                }
                case "summonitem", "totem" -> {
                    if (args.length < 2) {
                        TextUtil.sendMessage(sender, "&cUsage: /bossadmin summonitem <valerius|aethelgard|xolthos|borealis> [player]");
                        return true;
                    }
                    BossType type = parseBossType(args[1]);
                    if (type == null) {
                        TextUtil.sendMessage(sender, "&cInvalid boss type! Choose: valerius, aethelgard, or xolthos.");
                        return true;
                    }

                    Player target = (args.length >= 3) ? Bukkit.getPlayer(args[2]) : (sender instanceof Player p ? p : null);
                    if (target == null) {
                        if (!(sender instanceof Player)) {
                            TextUtil.sendMessage(sender, "&cConsole must specify a target player: /bossadmin summonitem <boss> <player>");
                        } else {
                            TextUtil.sendMessage(sender, "&cTarget player not found.");
                        }
                        return true;
                    }

                    ItemStack totem = itemRegistry.createSummonTotem(type);
                    var leftovers = target.getInventory().addItem(totem);
                    if (!leftovers.isEmpty()) {
                        target.getWorld().dropItemNaturally(target.getLocation(), totem);
                        TextUtil.sendMessage(target, "&e[WorldBosses] Your inventory was full; summon totem dropped at your feet!");
                    }
                    TextUtil.sendMessage(sender, "&aGiven 1x &e" + type.getDefaultDisplayName() + " Summon Totem &ato &e" + target.getName() + "&a.");
                    return true;
                }
                case "setnpc" -> {
                    if (args.length < 2) {
                        TextUtil.sendMessage(sender, "&cUsage: /bossadmin setnpc <npc-id>");
                        return true;
                    }
                    try {
                        int npcId = Integer.parseInt(args[1]);
                        config.set("exchange.npc-id", npcId);
                        saveConfig();
                        TextUtil.sendMessage(sender, "&aGarrick the Hunter NPC ID set to &e" + npcId + " &aand saved to world-bosses.yml!");
                    } catch (NumberFormatException e) {
                        TextUtil.sendMessage(sender, "&cNPC ID must be an integer.");
                    }
                    return true;
                }
                case "awaken", "start" -> {
                    if (!isBossActive()) {
                        TextUtil.sendMessage(sender, "&cNo World Boss is currently active.");
                        return true;
                    }
                    if (activeBoss.isAwakened()) {
                        TextUtil.sendMessage(sender, "&e" + activeBoss.getDisplayName() + " &ais already awakened and in active combat!");
                        return true;
                    }
                    activeBoss.forceAwaken();
                    TextUtil.sendMessage(sender, "&aSuccessfully fast-forwarded awakening for &e" + activeBoss.getDisplayName() + "&a! Combat initiated.");
                    return true;
                }
                case "forcenext" -> {
                    if (scheduler != null) {
                        scheduler.setSecondsRemaining(1);
                        TextUtil.sendMessage(sender, "&aForced scheduled spawn timer to 1s! Spawning imminent...");
                    } else {
                        TextUtil.sendMessage(sender, "&cScheduler is not active.");
                    }
                    return true;
                }
                case "skiptime" -> {
                    if (scheduler == null || !scheduler.isEnabled()) {
                        TextUtil.sendMessage(sender, "&cThe boss scheduler is disabled."); return true;
                    }
                    try {
                        int duration = BossSpawnScheduler.parseDuration(String.join(" ", Arrays.copyOfRange(args, 1, args.length)));
                        int remaining = scheduler.skipTime(duration);
                        TextUtil.sendMessage(sender, "&aBoss countdown reduced. Remaining: &e" + (remaining / 3600) + "h " + ((remaining % 3600) / 60) + "m " + (remaining % 60) + "s&a. Active encounters and minimum-player requirements still apply.");
                    } catch (IllegalArgumentException | ArithmeticException invalid) {
                        TextUtil.sendMessage(sender, "&cUsage: /bossadmin skiptime <30m|1hr|1h30m>");
                    }
                    return true;
                }
                default -> {
                    sendAdminHelp(sender);
                    return true;
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "[WorldBosses] Error handling /bossadmin command", t);
            TextUtil.sendMessage(sender, "&cAn unexpected error occurred while executing that command: &e" + t.getMessage());
            return true;
        }
    }

    private void sendAdminHelp(CommandSender sender) {
        TextUtil.sendMessage(sender, "&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        TextUtil.sendMessage(sender, "  &4&l⚔ WORLD BOSSES &8- &cADMIN COMMANDS");
        TextUtil.sendMessage(sender, "  &e/bossadmin spawn <boss> [x y z world] &8- &7Spawn a specific boss");
        TextUtil.sendMessage(sender, "  &e/bossadmin awaken &8- &7Awaken boss immediately (skip 45s countdown)");
        TextUtil.sendMessage(sender, "  &e/bossadmin kill &8- &7Kill active boss & drop combat loot");
        TextUtil.sendMessage(sender, "  &e/bossadmin despawn &8- &7Despawn active boss without rewards");
        TextUtil.sendMessage(sender, "  &e/bossadmin rewards &8- &7Inspect pending reward receipts");
        TextUtil.sendMessage(sender, "  &e/bossadmin rewards <retry|delivered> <receipt> &8- &7Resolve a reviewed receipt; retry only if not delivered");
        TextUtil.sendMessage(sender, "  &e/bossadmin clearall &8- &7Purge all orphaned bosses across all worlds");
        TextUtil.sendMessage(sender, "  &e/bossadmin summonitem <boss> [player] &8- &7Give summon totem");
        TextUtil.sendMessage(sender, "  &e/bossadmin setnpc <npc-id> &8- &7Set Garrick exchange NPC ID");
        TextUtil.sendMessage(sender, "  &e/bossadmin forcenext &8- &7Trigger next scheduled boss immediately");
        TextUtil.sendMessage(sender, "  &e/bossadmin skiptime <30m|1hr|1h30m> &8- &7Reduce the next boss countdown");
        TextUtil.sendMessage(sender, "  &e/bossadmin reload &8- &7Reload world-bosses.yml");
        TextUtil.sendMessage(sender, "&8&m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
    }

    public int cleanupAllOrphanBosses() {
        int count = 0;
        NamespacedKey bossKey = new NamespacedKey(plugin, "world_boss");
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity le : world.getLivingEntities()) {
                if (le.isDead()) continue;
                boolean isBoss = le.getPersistentDataContainer().has(bossKey, PersistentDataType.STRING);
                if (isBoss) {
                    if (activeBoss == null || !le.equals(activeBoss.getEntity())) {
                        le.remove();
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private int cleanupNearbyOrphans(CommandSender sender) {
        int count = 0;
        NamespacedKey bossKey = new NamespacedKey(plugin, "world_boss");
        if (sender instanceof Player p) {
            for (Entity e : p.getNearbyEntities(80, 80, 80)) {
                if (e instanceof LivingEntity le && !le.isDead()) {
                    boolean isBoss = le.getPersistentDataContainer().has(bossKey, PersistentDataType.STRING);
                    if (isBoss) {
                        if (activeBoss == null || !le.equals(activeBoss.getEntity())) {
                            le.remove();
                            count++;
                        }
                    }
                }
            }
        } else {
            count = cleanupAllOrphanBosses();
        }
        return count;
    }

    private BossType parseBossType(String raw) {
        if (raw == null) return null;
        String clean = raw.toLowerCase().trim().replace("'", "").replace("_", "").replace("-", "");
        return switch (clean) {
            case "valerius", "ashen", "vanguard" -> BossType.VALERIUS;
            case "aethelgard", "aethel", "root", "king" -> BossType.AETHELGARD;
            case "xolthos", "xol", "widow", "spider" -> BossType.XOLTHOS;
            case "borealis", "frost", "warden" -> BossType.BOREALIS;
            default -> null;
        };
    }

    private String formatTime(int seconds) {
        if (seconds <= 0) return "0s";
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        if (h > 0) return h + "h " + m + "m";
        if (m > 0) return m + "m " + s + "s";
        return s + "s";
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Tab Completion
    // ─────────────────────────────────────────────────────────────────────────────

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("boss")) {
            if (args.length == 1) {
                for (String s : Arrays.asList("claim", "codex", "lore", "info", "next", "shop", "party", "help", "valerius", "aethelgard", "xolthos", "borealis")) {
                    if (s.startsWith(args[0].toLowerCase())) completions.add(s);
                }
            } else if (args.length == 2 && args[0].equalsIgnoreCase("party")) {
                for (String s : Arrays.asList("join", "leave", "list")) {
                    if (s.startsWith(args[1].toLowerCase())) completions.add(s);
                }
            } else if (args.length == 2 && (args[0].equalsIgnoreCase("codex") || args[0].equalsIgnoreCase("lore"))) {
                for (String s : Arrays.asList("valerius", "aethelgard", "xolthos", "borealis")) {
                    if (s.startsWith(args[1].toLowerCase())) completions.add(s);
                }
            }
        } else if (command.getName().equalsIgnoreCase("bossadmin")) {
            if (!sender.hasPermission("coresuite.boss.admin") && !sender.isOp()) return completions;

            if (args.length == 1) {
                for (String s : Arrays.asList("spawn", "awaken", "kill", "despawn", "clearall", "purge", "reload", "summonitem", "setnpc", "forcenext", "skiptime", "help")) {
                    if (s.startsWith(args[0].toLowerCase())) completions.add(s);
                }
            } else if (args.length == 2 && args[0].equalsIgnoreCase("skiptime")) {
                for (String duration : Arrays.asList("15m", "30m", "1hr", "1h30m"))
                    if (duration.startsWith(args[1].toLowerCase())) completions.add(duration);
            } else if (args.length == 2 && (args[0].equalsIgnoreCase("spawn") || args[0].equalsIgnoreCase("summonitem"))) {
                for (String s : Arrays.asList("valerius", "aethelgard", "xolthos", "borealis")) {
                    if (s.startsWith(args[1].toLowerCase())) completions.add(s);
                }
            }
        }
        return completions;
    }

    public void registerRecipes() {
        unregisterRecipes();

        try {
            // 1. Valerius: Nether Star + Magma Cream + Blaze Powder + Obsidian
            NamespacedKey vKey = new NamespacedKey(plugin, "totem_valerius");
            ShapedRecipe vRecipe = new ShapedRecipe(vKey, itemRegistry.createSummonTotem(BossType.VALERIUS));
            vRecipe.shape("BMB", "MSM", "OOO");
            vRecipe.setIngredient('B', Material.BLAZE_POWDER);
            vRecipe.setIngredient('M', Material.MAGMA_CREAM);
            vRecipe.setIngredient('S', Material.NETHER_STAR);
            vRecipe.setIngredient('O', Material.OBSIDIAN);
            Bukkit.addRecipe(vRecipe);
            registeredRecipeKeys.add(vKey);

            // 2. Aethelgard: Totem of Undying + Moss Blocks + Dark Oak Logs + Heart of the Sea
            NamespacedKey aKey = new NamespacedKey(plugin, "totem_aethelgard");
            ShapedRecipe aRecipe = new ShapedRecipe(aKey, itemRegistry.createSummonTotem(BossType.AETHELGARD));
            aRecipe.shape("LHL", "MTM", "LML");
            aRecipe.setIngredient('L', Material.DARK_OAK_LOG);
            aRecipe.setIngredient('H', Material.HEART_OF_THE_SEA);
            aRecipe.setIngredient('M', Material.MOSS_BLOCK);
            aRecipe.setIngredient('T', Material.TOTEM_OF_UNDYING);
            Bukkit.addRecipe(aRecipe);
            registeredRecipeKeys.add(aKey);

            // 3. Xolthos: Eye of Ender + Fermented Spider Eyes + Cobwebs + Phantom Membrane
            NamespacedKey xKey = new NamespacedKey(plugin, "totem_xolthos");
            ShapedRecipe xRecipe = new ShapedRecipe(xKey, itemRegistry.createSummonTotem(BossType.XOLTHOS));
            xRecipe.shape("WPW", "FEF", "WPW");
            xRecipe.setIngredient('W', Material.COBWEB);
            xRecipe.setIngredient('P', Material.PHANTOM_MEMBRANE);
            xRecipe.setIngredient('F', Material.FERMENTED_SPIDER_EYE);
            xRecipe.setIngredient('E', Material.ENDER_EYE);
            Bukkit.addRecipe(xRecipe);
            registeredRecipeKeys.add(xKey);

            NamespacedKey frostKey = new NamespacedKey(plugin, "totem_borealis");
            ShapedRecipe frostRecipe = new ShapedRecipe(frostKey, itemRegistry.createSummonTotem(BossType.BOREALIS));
            frostRecipe.shape("IPI", "PDP", "IPI");
            frostRecipe.setIngredient('I', Material.BLUE_ICE);
            frostRecipe.setIngredient('P', Material.PRISMARINE_CRYSTALS);
            frostRecipe.setIngredient('D', Material.DIAMOND_BLOCK);
            Bukkit.addRecipe(frostRecipe);
            registeredRecipeKeys.add(frostKey);
            log("Registered 4 custom summon totem shaped crafting recipes.");
        } catch (Throwable t) {
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "[WorldBosses] Failed to register summon totem recipes", t);
        }
    }

    public void unregisterRecipes() {
        for (NamespacedKey key : registeredRecipeKeys) {
            try {
                Bukkit.removeRecipe(key);
            } catch (Throwable ignored) {}
        }
        registeredRecipeKeys.clear();

        try {
            Bukkit.removeRecipe(new NamespacedKey(plugin, "totem_valerius"));
            Bukkit.removeRecipe(new NamespacedKey(plugin, "totem_aethelgard"));
            Bukkit.removeRecipe(new NamespacedKey(plugin, "totem_xolthos"));
        } catch (Throwable ignored) {}
    }

    public BossDiscoveryManager getDiscoveryManager() {
        return discoveryManager;
    }

    public BossCodexGUI getBossCodexGUI() {
        return bossCodexGUI;
    }

    public BossItemRegistry getItemRegistry() {
        return itemRegistry;
    }

    public BossLootManager getLootManager() {
        return lootManager;
    }

    public BossSpawnScheduler getScheduler() {
        return scheduler;
    }

    private class CitizensListener implements Listener {
        @EventHandler
        public void onNPCClick(net.citizensnpcs.api.event.NPCRightClickEvent e) {
            int configNpcId = config.getInt("exchange.npc-id", -1);
            if (configNpcId == -1 || e.getNPC().getId() != configNpcId) return;

            Player player = e.getClicker();
            e.setCancelled(true);
            hunterExchangeGUI.open(player);
        }
    }
}
