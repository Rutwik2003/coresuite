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
    private void verifyBosses(CoreSuitePlugin core, World world) throws Exception {
        var module = (net.rocky.coresuite.module.worldbosses.WorldBossesModule)core.getModuleManager().getModule("world-bosses");
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
            };
            try {
                if (!boss.spawn(world.getSpawnLocation().add(0, 4, 0))) throw new AssertionError("Spawn failed: " + type);
                boss.awaken();
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
        if (rewards[0] != 3) throw new AssertionError("Rewards were lost or repeated: " + rewards[0]);
        getLogger().info("CORESUITE_BOSS_PROBE_PASS: three bosses spawned, healed within budget, defeated and rewarded once");
    }

}
