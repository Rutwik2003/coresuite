package net.rocky.coresuite.smoke;

import net.rocky.coresuite.CoreSuitePlugin;
import net.rocky.coresuite.common.compat.PaperCompat;
import net.rocky.coresuite.module.customfishing.CustomFishingModule;
import net.rocky.coresuite.module.customfishing.FishBagManager;
import org.bukkit.*;
import org.bukkit.entity.Pig;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.UUID;

/** Test-only plugin exercised by scripts/smoke_paper.py on an isolated server. */
public final class CompatibilityProbe extends JavaPlugin {
    @Override public void onEnable() {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            try { verify(); getLogger().info("CORESUITE_PROBE_PASS"); }
            catch (Throwable failure) { getLogger().log(java.util.logging.Level.SEVERE, "CORESUITE_PROBE_FAIL", failure); }
        }, 40L);
    }
    private void verify() throws Exception {
        CoreSuitePlugin core = CoreSuitePlugin.getInstance();
        if (core == null || !core.isEnabled()) throw new AssertionError("CoreSuite is not enabled");
        if (PaperCompat.sound("ENTITY_PLAYER_LEVELUP") != Sound.ENTITY_PLAYER_LEVELUP) throw new AssertionError("Sound adapter mismatch");
        World world = Bukkit.getWorlds().getFirst();
        Pig pig = world.spawn(world.getSpawnLocation().add(0, 3, 0), Pig.class);
        try {
            if (pig.getAttribute(PaperCompat.MAX_HEALTH) == null || pig.getAttribute(PaperCompat.MOVEMENT_SPEED) == null || pig.getAttribute(PaperCompat.SCALE) == null || PaperCompat.ATTACK_DAMAGE == null) throw new AssertionError("Missing attribute adapter");
        } finally { pig.remove(); }
        ItemStack fish = new ItemStack(Material.COD, 12);
        ItemMeta meta = fish.getItemMeta();
        NamespacedKey key = new NamespacedKey("minecraft", "cod");
        PaperCompat.setItemModel(meta, key);
        try {
            if (!key.equals(ItemMeta.class.getMethod("getItemModel").invoke(meta))) throw new AssertionError("Item model adapter mismatch");
        } catch (NoSuchMethodException expectedOnOldPaper) {}
        fish.setItemMeta(meta);
        ItemMeta legacyFish = new ItemStack(Material.COD).getItemMeta();
        PaperCompat.setItemModel(legacyFish, NamespacedKey.fromString("barely_default:fish_alexpaima"));
        try { ItemMeta.class.getMethod("setItemModel", NamespacedKey.class); }
        catch (NoSuchMethodException oldApi) {
            if (!legacyFish.hasCustomModelData() || legacyFish.getCustomModelData() != 1000000)
                throw new AssertionError("Legacy fish model mapping mismatch");
        }
        verifyBosses(core, world);
        verifyMovement(world);
        verifyArenaProtection(world);
        verifyCrateRewards(core);
        verifyShopItems(core);
        FishBagManager bags = new FishBagManager(core);
        var encode = FishBagManager.class.getDeclaredMethod("toBase64", ItemStack[].class);
        var decode = FishBagManager.class.getDeclaredMethod("fromBase64", String.class);
        encode.setAccessible(true); decode.setAccessible(true);
        ItemStack[] items = new ItemStack[450]; items[0] = fish; items[449] = new ItemStack(Material.SALMON, 3);
        String saved = (String)encode.invoke(bags, (Object)items);
        ItemStack[] restored = (ItemStack[])decode.invoke(bags, saved);
        if (restored.length != 450 || !items[0].equals(restored[0]) || !items[449].equals(restored[449])) throw new AssertionError("Fish bag serialization mismatch");
        CustomFishingModule fishing = (CustomFishingModule)core.getModuleManager().getModule("custom-fishing");
        if (fishing == null || !fishing.isEnabled()) throw new AssertionError("Fishing is not enabled");
        if (!Bukkit.getPluginManager().isPluginEnabled("Citizens")) {
            if (fishing.getLegendaryNPCManager().isAvailable()) throw new AssertionError("Citizens incorrectly detected");
            fishing.getLegendaryNPCManager().removeNPC(UUID.randomUUID());
        }
    }
    private void verifyArenaProtection(World world) throws Exception {
        Location center = world.getSpawnLocation().add(120, 0, 120);
        var provider = Bukkit.getPluginManager().getPlugin("WorldGuard");
        if (provider != null && provider.isEnabled()) {
            ClassLoader loader = provider.getClass().getClassLoader();
            Class<?> guard = Class.forName("com.sk89q.worldguard.WorldGuard", true, loader);
            Object platform = guard.getMethod("getPlatform").invoke(guard.getMethod("getInstance").invoke(null));
            Object container = platform.getClass().getMethod("getRegionContainer").invoke(platform);
            Class<?> adapter = Class.forName("com.sk89q.worldedit.bukkit.BukkitAdapter", true, loader);
            Object adapted = adapter.getMethod("adapt", World.class).invoke(null, world);
            Object manager = container.getClass().getMethod("get", Class.forName("com.sk89q.worldedit.world.World", true, loader)).invoke(container, adapted);
            Class<?> vector = Class.forName("com.sk89q.worldedit.math.BlockVector3", true, loader);
            var at = vector.getMethod("at", int.class, int.class, int.class);
            Object low = at.invoke(null, center.getBlockX() + 8, center.getBlockY() - 3, center.getBlockZ() + 3);
            Object high = at.invoke(null, center.getBlockX() + 10, center.getBlockY() + 5, center.getBlockZ() + 5);
            Class<?> regionType = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedRegion", true, loader);
            Object region = Class.forName("com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion", true, loader)
                    .getConstructor(String.class, vector, vector).newInstance("coresuite_probe_interior", low, high);
            manager.getClass().getMethod("addRegion", regionType).invoke(manager, region);
            try {
                var occupancy = net.rocky.coresuite.common.integration.BossProtectionGate.arenaOccupancy(center.clone().add(9, 0, 4), .1);
                if (!occupancy.test(center.clone().add(10, 0, 5))) throw new AssertionError("Exit cleanup missed player in arena region");
                if (occupancy.test(center.clone().add(12, 0, 5))) throw new AssertionError("Exit cleanup retained player outside arena region");
                String reason = net.rocky.coresuite.common.integration.BossProtectionGate.arenaDenialReason(center, 35);
                if (reason == null || !reason.contains("coresuite_probe_interior")) throw new AssertionError("Interior WorldGuard region missed: " + reason
                        + "; point=" + net.rocky.coresuite.common.integration.BossProtectionGate.denialReason(center.clone().add(9, 0, 4))
                        + "; regions=" + manager.getClass().getMethod("getRegions").invoke(manager));
            } finally { manager.getClass().getMethod("removeRegion", String.class).invoke(manager, "coresuite_probe_interior"); }
            if (net.rocky.coresuite.common.integration.BossProtectionGate.arenaDenialReason(center, 35) != null) throw new AssertionError("Empty WorldGuard arena rejected");
            getLogger().info("CORESUITE_WORLDGUARD_INTERIOR_PROBE_PASS");
        }
        provider = Bukkit.getPluginManager().getPlugin("GriefPrevention");
        if (provider != null && provider.isEnabled()) {
            ClassLoader loader = provider.getClass().getClassLoader();
            Object store = provider.getClass().getField("dataStore").get(provider);
            Class<?> claimType = Class.forName("me.ryanhamshire.GriefPrevention.Claim", true, loader);
            Object result = store.getClass().getMethod("createClaim", World.class, int.class, int.class, int.class,
                    int.class, int.class, int.class, UUID.class, claimType, Long.class, org.bukkit.entity.Player.class)
                    .invoke(store, world, center.getBlockX() + 8, center.getBlockX() + 17,
                            world.getMinHeight(), world.getMaxHeight() - 1, center.getBlockZ() + 3, center.getBlockZ() + 12,
                            null, null, null, null);
            Object claim = result.getClass().getField("claim").get(result);
            if (claim == null || !result.getClass().getField("succeeded").getBoolean(result)) throw new AssertionError("Test claim creation failed");
            try {
                String reason = net.rocky.coresuite.common.integration.BossProtectionGate.arenaDenialReason(center, 35);
                if (reason == null || !reason.startsWith("GriefPrevention:")) throw new AssertionError("Interior claim missed: " + reason);
            } finally { store.getClass().getMethod("deleteClaim", claimType).invoke(store, claim); }
            getLogger().info("CORESUITE_GRIEFPREVENTION_INTERIOR_PROBE_PASS");
        }
    }

    private void verifyMovement(World world) {
        Location feet = world.getSpawnLocation().add(24.5, 18, 24.5);
        feet.setX(feet.getBlockX() + .5); feet.setZ(feet.getBlockZ() + .5);
        feet.setY(feet.getBlockY());
        for (int x = -5; x <= 5; x++) for (int z = -5; z <= 5; z++) {
            world.getChunkAt((feet.getBlockX() + x) >> 4, (feet.getBlockZ() + z) >> 4);
            feet.clone().add(x, -1, z).getBlock().setType(Material.STONE);
            for (int y = 0; y <= 4; y++) feet.clone().add(x, y, z).getBlock().setType(Material.AIR);
        }
        if (!net.rocky.coresuite.module.worldbosses.combat.BossMovement.standing(feet)) throw new AssertionError("Safe floor rejected");
        feet.clone().add(0, 1, 0).getBlock().setType(Material.STONE);
        if (net.rocky.coresuite.module.worldbosses.combat.BossMovement.clear(feet)) throw new AssertionError("Head collision missed");
        Location escape = net.rocky.coresuite.module.worldbosses.combat.BossMovement.findLanding(feet, l -> l.distanceSquared(feet) <= 16);
        if (escape == null || !net.rocky.coresuite.module.worldbosses.combat.BossMovement.standing(escape)) throw new AssertionError("No safe escape found");
        feet.clone().add(0, 1, 0).getBlock().setType(Material.AIR);
        feet.getBlock().setType(Material.OAK_FENCE);
        if (net.rocky.coresuite.module.worldbosses.combat.BossMovement.clear(feet.clone().add(0, 1, 0))) throw new AssertionError("Fence extension collision missed");
        feet.getBlock().setType(Material.AIR);
        feet.clone().add(0, -1, 0).getBlock().setType(Material.MAGMA_BLOCK);
        if (net.rocky.coresuite.module.worldbosses.combat.BossMovement.standing(feet)) throw new AssertionError("Hazardous landing accepted");
        getLogger().info("CORESUITE_MOVEMENT_PROBE_PASS");
    }

    private void verifyCrateRewards(CoreSuitePlugin core) throws Exception {
        var provider = Bukkit.getPluginManager().getPlugin("ExcellentCrates");
        if (provider == null) return;
        if (!provider.isEnabled()) throw new AssertionError("ExcellentCrates test dependency failed to enable");
        Object manager = provider.getClass().getMethod("getKeyManager").invoke(provider);
        String id = "coresuite_probe_physical";
        Object key = manager.getClass().getMethod("getKeyById", String.class).invoke(manager, id);
        if (key == null) {
            manager.getClass().getMethod("createKey", String.class).invoke(manager, id);
            key = manager.getClass().getMethod("getKeyById", String.class).invoke(manager, id);
        }
        key.getClass().getMethod("setVirtual", boolean.class).invoke(key, false);
        ItemStack[][] contents = {new ItemStack[36]};
        java.util.Arrays.setAll(contents[0], slot -> new ItemStack(Material.STONE, 64));
        int[] saves = {0}, writes = {0};
        var inventory = (org.bukkit.inventory.PlayerInventory) java.lang.reflect.Proxy.newProxyInstance(getClassLoader(),
                new Class<?>[]{org.bukkit.inventory.PlayerInventory.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getStorageContents", "getContents" -> contents[0];
                    case "getMaxStackSize" -> 64;
                    case "setStorageContents", "setContents" -> { writes[0]++; contents[0] = (ItemStack[]) args[0]; yield null; }
                    default -> null;
                });
        UUID uuid = UUID.randomUUID();
        java.util.List<String> rewardMessages = new java.util.ArrayList<>();
        var player = (org.bukkit.entity.Player) java.lang.reflect.Proxy.newProxyInstance(getClassLoader(),
                new Class<?>[]{org.bukkit.entity.Player.class}, (proxy, method, args) -> switch (method.getName()) {
                    case "getName" -> "ProbeReward";
                    case "getUniqueId" -> uuid;
                    case "getInventory" -> inventory;
                    case "saveData" -> { saves[0]++; yield null; }
                    case "sendMessage" -> {
                        if (args != null) for (Object argument : args)
                            if (argument instanceof net.kyori.adventure.text.Component component)
                                rewardMessages.add(net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(component));
                        yield null;
                    }
                    default -> method.getReturnType() == boolean.class ? false : null;
                });
        var mailbox = new net.rocky.coresuite.module.worldbosses.rewards.BossRewardMailbox(core);
        var deliver = mailbox.getClass().getDeclaredMethod("deliver", org.bukkit.entity.Player.class,
                net.rocky.coresuite.module.worldbosses.rewards.BossRewardMailbox.Reward.class);
        deliver.setAccessible(true);
        var reward = net.rocky.coresuite.module.worldbosses.rewards.BossRewardMailbox.Reward.command("crate key give %player% " + id + " 1");
        if ((boolean) deliver.invoke(mailbox, player, reward) || writes[0] != 0 || saves[0] != 0)
            throw new AssertionError("Full inventory did not defer the real ExcellentCrates key cleanly");
        if (rewardMessages.stream().noneMatch(message -> message.contains("/boss claim"))) throw new AssertionError("Full inventory missing claim hint");
        rewardMessages.clear();
        contents[0][35] = null;
        if (!(boolean) deliver.invoke(mailbox, player, reward) || writes[0] != 1 || saves[0] != 1)
            throw new AssertionError("Physical key failed to deliver after freeing space");
        if (rewardMessages.stream().anyMatch(message -> message.contains("/boss claim"))
                || rewardMessages.stream().noneMatch(message -> message.contains("coresuite probe physical"))) throw new AssertionError("Delivered key message is not specific or wrongly prompts claim");
        if (manager.getClass().getMethod("getKeyByItem", ItemStack.class).invoke(manager, contents[0][35]) != key)
            throw new AssertionError("Delivered item is not a recognized ExcellentCrates key");
        var missing = net.rocky.coresuite.module.worldbosses.rewards.BossRewardMailbox.Reward.command("crates key give %player% coresuite_probe_missing 1");
        rewardMessages.clear();
        if ((boolean) deliver.invoke(mailbox, player, missing) || writes[0] != 1)
            throw new AssertionError("Unknown key was not safely deferred");
        if (rewardMessages.stream().anyMatch(message -> message.contains("/boss claim"))) throw new AssertionError("Provider failure incorrectly advertised inventory claim");
        rewardMessages.clear();
        var itemReward = net.rocky.coresuite.module.worldbosses.rewards.BossRewardMailbox.Reward.items(java.util.List.of(new ItemStack(Material.DIAMOND, 2)));
        if ((boolean) deliver.invoke(mailbox, player, itemReward)) throw new AssertionError("Full inventory accepted item reward");
        if (rewardMessages.stream().noneMatch(message -> message.contains("2 × diamond"))) throw new AssertionError("Stored item details missing");
        contents[0][34] = null; rewardMessages.clear();
        if (!(boolean) deliver.invoke(mailbox, player, itemReward)) throw new AssertionError("Item reward not delivered with space");
        if (rewardMessages.stream().anyMatch(message -> message.contains("/boss claim"))
                || rewardMessages.stream().noneMatch(message -> message.contains("2 × diamond"))) throw new AssertionError("Item delivery details incorrect");
        var registry = new net.rocky.coresuite.module.worldbosses.rewards.BossItemRegistry(core);
        var shopConfig = new org.bukkit.configuration.file.YamlConfiguration();
        shopConfig.set("exchange.trades.rare-key.command", "crates key give %player% " + id + " 1");
        var shop = new net.rocky.coresuite.module.worldbosses.rewards.HunterExchangeGUI(core, registry, shopConfig);
        var prepare = shop.getClass().getDeclaredMethod("prepareDelivery", org.bukkit.entity.Player.class, String.class);
        prepare.setAccessible(true);
        Class<?> factoryType = Class.forName("net.rocky.coresuite.module.worldbosses.rewards.HunterExchangeGUI$DeliveryFactory");
        Object factory = java.lang.reflect.Proxy.newProxyInstance(factoryType.getClassLoader(), new Class<?>[]{factoryType},
                (proxy, method, args) -> prepare.invoke(shop, player, "rare-key"));
        var purchase = shop.getClass().getDeclaredMethod("purchase", org.bukkit.entity.Player.class, String.class, int.class, double.class, factoryType);
        purchase.setAccessible(true);
        java.util.Arrays.setAll(contents[0], slot -> new ItemStack(Material.STONE, 64));
        contents[0][0] = registry.createShard("ashen", 3);
        if ((boolean) purchase.invoke(shop, player, "ashen", 2, 0d, factory)) throw new AssertionError("Shop accepted full inventory");
        if (registry.countShards(player, "ashen") != 3) throw new AssertionError("Shop lost shards on full inventory");
        contents[0][0] = registry.createShard("ashen", 2);
        if (!(boolean) purchase.invoke(shop, player, "ashen", 2, 0d, factory)) throw new AssertionError("Shop could not use slot freed by payment");
        if (registry.countShards(player, "ashen") != 0) throw new AssertionError("Key delivery restored spent shards");
        if (manager.getClass().getMethod("getKeyByItem", ItemStack.class).invoke(manager, contents[0][0]) != key)
            throw new AssertionError("Shop did not deliver configured key");
        getLogger().info("CORESUITE_SHOP_KEY_PROBE_PASS: full inventory rollback and configured key delivery without shard duplication");
        getLogger().info("CORESUITE_CRATE_REWARD_PROBE_PASS: ExcellentCrates " + provider.getDescription().getVersion()
                + "; full inventory deferred, retry delivered a recognized key, missing key deferred (simulated player inventory)");
    }
    private void verifyShopItems(CoreSuitePlugin core) throws Exception {
        var registry = new net.rocky.coresuite.module.worldbosses.rewards.BossItemRegistry(core);
        var cleaver = registry.createSignatureWeapon("vanguard-cleaver");
        var crown = registry.createSignatureWeapon("crown-of-forest");
        var fang = registry.createSignatureWeapon("widows-fang");
        var boots = registry.createSignatureWeapon("frostbound-striders");
        if (boots.getType() != Material.NETHERITE_BOOTS
                || !registry.hasSignatureWeapon(boots, "frostbound_striders")
                || boots.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.FEATHER_FALLING) != 4
                || boots.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.DEPTH_STRIDER) != 3
                || boots.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.SOUL_SPEED) != 3
                || boots.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.PROTECTION) != 4
                || boots.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.THORNS) != 3
                || boots.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.UNBREAKING) != 4)
            throw new AssertionError("Invalid Frostbound Striders");
        if (!boots.isSimilar(registry.createSignatureWeapon("frostbound_striders")))
            throw new AssertionError("Shop and drop boots differ");
        if (cleaver.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.SHARPNESS) != 5
                || crown.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.RESPIRATION) != 3
                || fang.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.SWEEPING_EDGE) != 3)
            throw new AssertionError("Missing mythic item enchantments");
        for (var item : java.util.List.of(cleaver, crown, fang, boots)) {
            if (item.getEnchantmentLevel(org.bukkit.enchantments.Enchantment.MENDING) != 1)
                throw new AssertionError("Missing Mending");
        }
        var shop = new net.rocky.coresuite.module.worldbosses.rewards.HunterExchangeGUI(core, registry, new org.bukkit.configuration.file.YamlConfiguration());
        var satchel = shop.getClass().getDeclaredMethod("alchemySatchel"); satchel.setAccessible(true);
        @SuppressWarnings("unchecked") var potions = (java.util.List<ItemStack>) satchel.invoke(shop);
        var fit = net.rocky.coresuite.module.worldbosses.rewards.BossRewardMailbox.class.getDeclaredMethod("fit", ItemStack[].class, java.util.List.class, int.class);
        fit.setAccessible(true);
        if (fit.invoke(null, new ItemStack[8], potions, 64) != null) throw new AssertionError("Satchel accepted too few slots");
        ItemStack[] plan = (ItemStack[]) fit.invoke(null, new ItemStack[9], potions, 64);
        if (plan == null || java.util.Arrays.stream(plan).anyMatch(item -> item == null || item.getAmount() != 1))
            throw new AssertionError("Satchel failed to deliver nine separate potions");
        getLogger().info("CORESUITE_SHOP_ITEMS_PROBE_PASS: upgraded gear and atomic nine-potion inventory planning");
    }
    private void verifyBosses(CoreSuitePlugin core, World world) throws Exception {
        var module = (net.rocky.coresuite.module.worldbosses.WorldBossesModule)core.getModuleManager().getModule("world-bosses");
        // Exercise the production module entry point, including clearance and protection checks.
        Location platform = world.getSpawnLocation().add(12, 12, 0);
        int px = platform.getBlockX(), py = platform.getBlockY(), pz = platform.getBlockZ();
        // The production check refuses an arena that extends into unloaded terrain. Mirror a player
        // having the configured 35-block arena loaded before testing module-level admin spawning.
        for (int cx = (px - 35) >> 4; cx <= (px + 35) >> 4; cx++) for (int cz = (pz - 35) >> 4; cz <= (pz + 35) >> 4; cz++) {
            world.getChunkAt(cx, cz);
        }
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            world.getBlockAt(px + dx, py - 1, pz + dz).setType(Material.STONE);
            for (int dy = 0; dy < 4; dy++) world.getBlockAt(px + dx, py + dy, pz + dz).setType(Material.AIR);
        }
        world.getBlockAt(px, py, pz).setType(Material.SHORT_GRASS);
        for (var type : net.rocky.coresuite.module.worldbosses.boss.BossType.values()) {
            if (!module.spawnBoss(type, platform)) throw new AssertionError("Module spawn failed: " + module.getLastSpawnFailure());
            var encounter = module.getActiveBoss();
            encounter.forceAwaken();
            if (encounter instanceof net.rocky.coresuite.module.worldbosses.boss.ValeriusBoss valerius) {
                UUID waterUser = UUID.randomUUID();
                var thrower = (org.bukkit.entity.Player) java.lang.reflect.Proxy.newProxyInstance(getClassLoader(), new Class<?>[]{org.bukkit.entity.Player.class},
                        (proxy, method, args) -> switch (method.getName()) {
                            case "getUniqueId" -> waterUser;
                            case "getName" -> "ProbeWater";
                            case "getLocation" -> platform.clone();
                            case "getWorld" -> world;
                            case "hasPermission" -> true;
                            default -> method.getReturnType() == boolean.class ? false : null;
                        });
                ItemStack water = new ItemStack(Material.SPLASH_POTION);
                var waterMeta = (org.bukkit.inventory.meta.PotionMeta) water.getItemMeta();
                waterMeta.setBasePotionType(org.bukkit.potion.PotionType.WATER); water.setItemMeta(waterMeta);
                var bottle = (org.bukkit.entity.ThrownPotion) java.lang.reflect.Proxy.newProxyInstance(getClassLoader(), new Class<?>[]{org.bukkit.entity.ThrownPotion.class},
                        (proxy, method, args) -> switch (method.getName()) {
                            case "getItem" -> water;
                            case "getShooter" -> thrower;
                            case "getLocation" -> valerius.getEntity().getLocation().add(1, .5, 0);
                            case "getWorld" -> world;
                            default -> method.getReturnType() == boolean.class ? false : null;
                        });
                var eventType = Class.forName("io.papermc.paper.event.entity.WaterBottleSplashEvent");
                org.bukkit.event.entity.PotionSplashEvent splash = null;
                for (var constructor : eventType.getConstructors()) {
                    if (constructor.getParameterCount() == 7) splash = (org.bukkit.event.entity.PotionSplashEvent) constructor.newInstance(bottle, null, null, null, new java.util.HashMap<>(), new java.util.HashSet<>(), new java.util.HashSet<>());
                    else if (constructor.getParameterCount() == 4) splash = (org.bukkit.event.entity.PotionSplashEvent) constructor.newInstance(bottle, new java.util.HashMap<>(), new java.util.HashSet<>(), new java.util.HashSet<>());
                    if (splash != null) break;
                }
                if (splash == null) throw new AssertionError("No supported water splash event constructor");
                splash.setCancelled(true); module.onPotionSplash(splash);
                if (valerius.isQuenched()) throw new AssertionError("Cancelled water splash quenched boss");
                splash.setCancelled(false); module.onPotionSplash(splash);
                if (!valerius.isQuenched()) throw new AssertionError("Plain water with empty affected map failed to quench Valerius");
                getLogger().info("CORESUITE_WATER_COUNTER_PROBE_PASS");
            }
            if (type == net.rocky.coresuite.module.worldbosses.boss.BossType.AETHELGARD) {
                encounter.testAbility("roots");
                if (encounter.getMinions().isEmpty()) throw new AssertionError("Healing roots did not spawn");
            }
            java.util.List<org.bukkit.entity.LivingEntity> children = java.util.List.copyOf(encounter.getMinions());
            org.bukkit.block.Block temporary = world.getBlockAt(px + 3, py, pz + 3);
            temporary.setType(Material.AIR);
            if (!encounter.placeTemporaryBlock(temporary, Material.COBWEB, 200)) throw new AssertionError("Temporary web creation failed");
            module.killActiveBoss();
            if (!module.getPortalManager().isActive()) throw new AssertionError("Defeat did not schedule the exit rift");
            if (module.initiateBossEncounter(type, platform) || !module.getLastSpawnFailure().contains("rifts are still active"))
                throw new AssertionError("Pending exit did not reject new encounter with current reason");
            module.despawnActiveBoss();
            if (temporary.getType() != Material.AIR || children.stream().anyMatch(org.bukkit.entity.Entity::isValid))
                throw new AssertionError("Encounter cleanup left temporary terrain or root minions");
            if (!encounter.diagnostics().contains("tasks=0")) throw new AssertionError("Encounter cleanup left delayed tasks");
        }
        world.getBlockAt(px, py + 1, pz).setType(Material.STONE);
        if (module.spawnBoss(net.rocky.coresuite.module.worldbosses.boss.BossType.VALERIUS, platform)) throw new AssertionError("Obstructed spawn allowed");
        if (!module.getLastSpawnFailure().contains("STONE")) throw new AssertionError("Missing obstruction diagnostic");
        getLogger().info("CORESUITE_MODULE_SPAWN_PROBE_PASS: all four bosses through production spawn checks; obstruction rejected with reason");
        var config = new org.bukkit.configuration.file.YamlConfiguration();
        config.loadFromString(module.getConfig().saveToString());
        config.set("domain.awakening-seconds", 0);
        config.set("domain.storm-weather", false);
        int[] rewards = {0};
        var loot = new net.rocky.coresuite.module.worldbosses.rewards.BossLootManager(core, null, config) {
            @Override public void distributeRewards(net.rocky.coresuite.module.worldbosses.boss.BossType type,
                    net.rocky.coresuite.module.worldbosses.combat.BossDamageTracker tracker, String name) {
                if (tracker.getSortedDamagers().size() != 1) throw new AssertionError("Missing participant");
                rewards[0]++;
            }
        };
        UUID participant = UUID.randomUUID();
        org.bukkit.entity.Player player = (org.bukkit.entity.Player)java.lang.reflect.Proxy.newProxyInstance(
                getClassLoader(), new Class<?>[]{org.bukkit.entity.Player.class}, (proxy, method, args) -> {
                    return switch (method.getName()) {
                        case "getUniqueId" -> participant;
                        case "getName" -> "ProbeFighter";
                        case "getWorld" -> world;
                        case "getLocation" -> world.getSpawnLocation();
                        case "hashCode" -> participant.hashCode();
                        case "equals" -> proxy == args[0];
                        default -> method.getReturnType() == boolean.class ? false : null;
                    };
                });
        for (var type : net.rocky.coresuite.module.worldbosses.boss.BossType.values()) {
            net.rocky.coresuite.module.worldbosses.boss.WorldBoss boss = switch(type) {
                case VALERIUS -> new net.rocky.coresuite.module.worldbosses.boss.ValeriusBoss(core, config, loot);
                case AETHELGARD -> new net.rocky.coresuite.module.worldbosses.boss.AethelgardBoss(core, config, loot);
                case XOLTHOS -> new net.rocky.coresuite.module.worldbosses.boss.XolthosBoss(core, config, loot);
                case BOREALIS -> new net.rocky.coresuite.module.worldbosses.boss.BorealisBoss(core, config, loot);
            };
            try {
                if (!boss.spawn(world.getSpawnLocation().add(0, 4, 0))) throw new AssertionError("Spawn failed: " + type);
                if (type == net.rocky.coresuite.module.worldbosses.boss.BossType.AETHELGARD) {
                    var husk = (org.bukkit.entity.Zombie) boss.getEntity();
                    if (husk.isBaby()) throw new AssertionError("Aethelgard spawned as a baby");
                    husk.setBaby(true);
                    boss.setupEntity(husk);
                    if (husk.isBaby()) throw new AssertionError("Aethelgard setup did not restore adult form");
                    if (Math.abs(husk.getAttribute(PaperCompat.SCALE).getBaseValue() - config.getDouble("aethelgard.scale", 1.55)) > .0001)
                        throw new AssertionError("Aethelgard lost configured scale");
                    getLogger().info("CORESUITE_AETHELGARD_ADULT_PROBE_PASS");
                }
                boss.awaken();
                if (boss instanceof net.rocky.coresuite.module.worldbosses.boss.BorealisBoss frost) {
                    if (boss.getEntity().getEquipment().getItemInMainHand().getType() != Material.DIAMOND_AXE
                            || boss.getEntity().getEquipment().getItemInOffHand().getType() != Material.BOW)
                        throw new AssertionError("Borealis stance equipment missing");
                    if (frost.calculateIncomingDamage(player, 10, new ItemStack(Material.DIAMOND_SWORD)) != 5)
                        throw new AssertionError("True Ice resistance failed");
                    if (!frost.applyThermalShock() || frost.applyThermalShock()) throw new AssertionError("Thermal Shock cooldown failed");
                    if (frost.calculateIncomingDamage(player, 10, new ItemStack(Material.DIAMOND_SHOVEL)) != 25)
                        throw new AssertionError("Thermal shovel weakness failed");
                    var guard = world.spawn(world.getSpawnLocation().add(0, 4, 0), org.bukkit.entity.Stray.class);
                    var setup = frost.getClass().getDeclaredMethod("setupFrostguard", org.bukkit.entity.Stray.class);
                    setup.setAccessible(true);
                    try {
                        setup.invoke(frost, guard);
                        if (!frost.isMinion(guard) || guard.getHealth() != 45 || guard.isPersistent())
                            throw new AssertionError("Frostguard registration or health failed");
                        frost.clearMinions();
                        if (guard.isValid()) throw new AssertionError("Frostguard cleanup failed");
                    } finally { guard.remove(); }
                    getLogger().info("CORESUITE_BOREALIS_THERMAL_PROBE_PASS");
                }
                double maximum = boss.getMaxHealth();
                boss.recordDamage(player, Double.NaN, null);
                if (boss.getCurrentHealth() != maximum) throw new AssertionError("NaN corrupted health");
                boss.setCurrentHealth(maximum / 2);
                boss.heal(maximum * 10);
                if (boss.getCurrentHealth() > maximum * .650001) throw new AssertionError("Healing exceeded budget");
                for (int hit = 0; hit < 100 && boss.isActive(); hit++) boss.recordDamage(player, maximum * 10, null);
                if (boss.isActive()) throw new AssertionError("Boss could not be defeated: " + type);
                boss.die();
                if (boss.getDamageTracker().getTotalDamage() > maximum) throw new AssertionError("Overkill inflated contribution");
            } finally { if (boss.isActive()) boss.despawn(); }
        }
        if (rewards[0] != net.rocky.coresuite.module.worldbosses.boss.BossType.values().length) throw new AssertionError("Rewards were lost or repeated: " + rewards[0]);
        getLogger().info("CORESUITE_BOSS_PROBE_PASS: four bosses spawned, healed within budget, defeated and rewarded once");
    }

}
