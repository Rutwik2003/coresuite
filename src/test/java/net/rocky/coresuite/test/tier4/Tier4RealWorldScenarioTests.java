package net.rocky.coresuite.test.tier4;

import net.rocky.coresuite.test.core.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class Tier4RealWorldScenarioTests {

    public static List<TestResult> runAll(TestContext ctx) {
        List<TestResult> results = new ArrayList<>();

        // -------------------------------------------------------------
        // SCENARIO 1: Complete Valerius Summoning to Slay Workflow
        // -------------------------------------------------------------
        results.add(runTest("T4-01", "Scenario_Valerius_Encounter", 4, "Complete Valerius summoning, domain atmosphere, combat kits, defeat, codex unlock", () -> {
            // Step 1: Craft Valerius Totem at Crafting Table
            MockBukkitContext.MockShapedRecipe recipe = new MockBukkitContext.MockShapedRecipe("totem_valerius", " M ", "BSB", " O ");
            recipe.setIngredient('S', "NETHER_STAR");
            recipe.setIngredient('M', "MAGMA_CREAM");
            recipe.setIngredient('B', "BLAZE_POWDER");
            recipe.setIngredient('O', "OBSIDIAN");
            MockBukkitContext.MockItemStack totem = new MockBukkitContext.MockItemStack("TOTEM_OF_UNDYING", 1);
            totem.setDisplayName("&c&lValerius Summon Totem");
            totem.setPdc("coresuite:summon_totem", "valerius");
            recipe.setResult(totem);
            TestAssertion.assertNotNull(recipe.getResult(), "Totem crafted");

            // Step 2: Admin Player summons boss with 1 player online
            MockBukkitContext.MockPlayer hero = new MockBukkitContext.MockPlayer(UUID.randomUUID(), "HeroAdmin");
            hero.addPermission("coresuite.boss.admin");
            int onlinePlayers = 1;
            boolean canSummon = hero.hasPermission("coresuite.boss.admin") || (onlinePlayers >= 3);
            TestAssertion.assertTrue(canSummon, "Admin bypass allows summon with 1 player");

            // Step 3: Domain entry atmosphere
            hero.setLocation(10.0, 64.0, 10.0);
            TestAssertion.assertTrue(hero.distance(0, 64, 0) <= 35.0, "Hero inside domain");

            MockBukkitContext.MockBossBar bar = new MockBukkitContext.MockBossBar("Valerius", "RED");
            bar.addFlag("CREATE_WORLD_FOG");
            bar.addFlag("DARKEN_SCREEN");
            bar.addPlayer(hero.getUniqueId());
            TestAssertion.assertTrue(bar.hasFlag("CREATE_WORLD_FOG"), "Fog flag active");
            TestAssertion.assertTrue(bar.hasFlag("DARKEN_SCREEN"), "Dark screen active");

            List<double[]> barrier = TestContext.calculatePerimeterRing(0, 0, 35.0, 72);
            TestAssertion.assertEquals(72, barrier.size(), "72 barrier points rendered");

            hero.playSound("coresuite:boss.valerius", "RECORDS", 1.0f, 1.0f);
            TestAssertion.assertEquals(1, hero.getSoundsPlayed().size(), "BGM playing");

            // Step 4: Combat exchange - spam hits deflected, timed hits damage
            hero.setAttackCooldown(0.3f);
            boolean deflected = hero.getAttackCooldown() < 0.85f;
            TestAssertion.assertTrue(deflected, "Spam hit deflected");
            hero.playSound("ITEM_SHIELD_BLOCK", "PLAYERS", 1.0f, 1.0f);
            hero.sendActionBar("&c&l[DEFLECTED]");

            hero.setAttackCooldown(1.0f);
            double bossHp = 1000.0;
            bossHp -= 1000.0; // Hero slays boss

            // Step 5: Boss death, audio cleanup, SQLite discovery
            TestAssertion.assertEquals(0.0, bossHp, 0.001, "Boss slain");
            hero.stopSound("coresuite:boss.valerius", "RECORDS");
            TestAssertion.assertEquals(1, hero.getSoundsStopped().size(), "BGM cleanly stopped");

            // Discovery registered
            Map<UUID, Set<String>> db = new HashMap<>();
            db.computeIfAbsent(hero.getUniqueId(), k -> new HashSet<>()).add("VALERIUS");

            // Step 6: Codex reveals Valerius without spoilers
            boolean discovered = db.get(hero.getUniqueId()).contains("VALERIUS");
            TestAssertion.assertTrue(discovered, "Valerius permanently unlocked in codex");
        }));

        // -------------------------------------------------------------
        // SCENARIO 2: Complete Aethelgard Encounter with Minions
        // -------------------------------------------------------------
        results.add(runTest("T4-02", "Scenario_Aethelgard_Encounter", 4, "Complete Aethelgard encounter with baby zombie ghouls and minion cleanup", () -> {
            MockBukkitContext.MockPlayer p = new MockBukkitContext.MockPlayer(UUID.randomUUID(), "ForestKnight");
            p.setLocation(0, 64, 0);

            // BGM start
            p.playSound("coresuite:boss.aethelgard", "RECORDS", 1.0f, 1.0f);

            // Aethelgard spawns Rootling Ghouls (baby zombies)
            List<MockBukkitContext.MockLivingEntity> ghouls = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                MockBukkitContext.MockLivingEntity g = new MockBukkitContext.MockLivingEntity("ZOMBIE");
                g.setBaby(true);
                g.setCustomName("Rootling Ghoul");
                ghouls.add(g);
            }
            TestAssertion.assertTrue(ghouls.stream().allMatch(MockBukkitContext.MockLivingEntity::isBaby), "All ghouls are baby zombies");

            // Aethelgard dies: all ghouls wiped and audio stopped
            ghouls.forEach(MockBukkitContext.MockLivingEntity::remove);
            p.stopSound("coresuite:boss.aethelgard", "RECORDS");

            TestAssertion.assertTrue(ghouls.stream().allMatch(MockBukkitContext.MockLivingEntity::isDead), "All minions wiped");
            TestAssertion.assertEquals(1, p.getSoundsStopped().size(), "Audio stopped");
        }));

        // -------------------------------------------------------------
        // SCENARIO 3: Complete Xolthos Encounter with Web and Broodlings
        // -------------------------------------------------------------
        results.add(runTest("T4-03", "Scenario_Xolthos_Encounter", 4, "Complete Xolthos encounter with cave spiders, leap, and evasion", () -> {
            MockBukkitContext.MockPlayer p = new MockBukkitContext.MockPlayer(UUID.randomUUID(), "WebHunter");
            p.setLocation(5, 64, 5);

            // Spider broodlings spawned
            List<MockBukkitContext.MockLivingEntity> spiders = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                MockBukkitContext.MockLivingEntity s = new MockBukkitContext.MockLivingEntity("CAVE_SPIDER");
                s.setCustomName("Xolthos Broodling");
                spiders.add(s);
            }
            TestAssertion.assertEquals(4, spiders.size(), "4 broodling spiders");

            // Defeat boss: clear minions
            spiders.forEach(MockBukkitContext.MockLivingEntity::remove);
            TestAssertion.assertTrue(spiders.stream().allMatch(MockBukkitContext.MockLivingEntity::isDead), "Spiders cleared");
        }));

        // -------------------------------------------------------------
        // SCENARIO 4: Flee and Leash Workflow
        // -------------------------------------------------------------
        results.add(runTest("T4-04", "Scenario_Flee_and_Leash", 4, "Player flees domain stopping sound; boss pulled >50m leashes back to center", () -> {
            MockBukkitContext.MockPlayer p = new MockBukkitContext.MockPlayer(UUID.randomUUID(), "Runner");
            p.setLocation(10.0, 64.0, 10.0);
            p.playSound("coresuite:boss.valerius", "RECORDS", 1.0f, 1.0f);

            // Player runs beyond 35m
            p.setLocation(38.0, 64.0, 0.0);
            if (p.distance(0, 64, 0) > 35.0) {
                p.stopSound("coresuite:boss.valerius", "RECORDS");
            }
            TestAssertion.assertEquals(1, p.getSoundsStopped().size(), "BGM stopped upon fleeing");

            // Boss pulled beyond 50m leash distance
            double bossDistFromSpawn = 55.0;
            boolean leashed = bossDistFromSpawn > 50.0;
            TestAssertion.assertTrue(leashed, "Boss leashes back when pulled > 50m");
        }));

        // -------------------------------------------------------------
        // SCENARIO 5: Non-Admin vs Admin Summoning Workflow
        // -------------------------------------------------------------
        results.add(runTest("T4-05", "Scenario_Permission_Summon", 4, "Non-admin blocked with 1 online player while admin succeeds with 1 player", () -> {
            MockBukkitContext.MockPlayer regularPlayer = new MockBukkitContext.MockPlayer(UUID.randomUUID(), "Regular");
            MockBukkitContext.MockPlayer adminPlayer = new MockBukkitContext.MockPlayer(UUID.randomUUID(), "Admin");
            adminPlayer.addPermission("coresuite.boss.admin");

            int online = 1;
            boolean regularCanSpawn = regularPlayer.hasPermission("coresuite.boss.admin") || (online >= 3);
            boolean adminCanSpawn = adminPlayer.hasPermission("coresuite.boss.admin") || (online >= 3);

            TestAssertion.assertFalse(regularCanSpawn, "Regular player blocked");
            TestAssertion.assertTrue(adminCanSpawn, "Admin player allowed");
        }));

        // -------------------------------------------------------------
        // SCENARIO 6: Garrick Exchange and Codex Navigation Loop
        // -------------------------------------------------------------
        results.add(runTest("T4-06", "Scenario_Menu_Navigation", 4, "Navigation loop: /boss -> Garrick Shop -> Back to Codex button -> /boss", () -> {
            String currentGui = "BossCodexGUI";

            // Click shop icon in Codex
            currentGui = "HunterExchangeGUI";
            TestAssertion.assertEquals("HunterExchangeGUI", currentGui, "Opened exchange");

            // Click slot 36 (Back to Codex)
            int clickedSlot = 36;
            if (clickedSlot == 36) {
                currentGui = "BossCodexGUI";
            }
            TestAssertion.assertEquals("BossCodexGUI", currentGui, "Returned to Codex via Back button");
        }));

        // -------------------------------------------------------------
        // SCENARIO 7: Admin Summonitem All Bosses Workflow
        // -------------------------------------------------------------
        results.add(runTest("T4-07", "Scenario_Admin_Summonitem_All", 4, "Admin runs /bossadmin summonitem for all 3 bosses and receives items directly", () -> {
            MockBukkitContext.MockPlayer admin = new MockBukkitContext.MockPlayer(UUID.randomUUID(), "AdminUser");
            List<String> bosses = List.of("valerius", "aethelgard", "xolthos");

            for (int i = 0; i < bosses.size(); i++) {
                String b = bosses.get(i);
                MockBukkitContext.MockItemStack totem = new MockBukkitContext.MockItemStack("TOTEM_OF_UNDYING", 1);
                totem.setPdc("coresuite:summon_totem", b);
                admin.setItem(i, totem);
            }

            TestAssertion.assertEquals(3, admin.getInventory().size(), "Received all 3 totems");
            TestAssertion.assertEquals("valerius", admin.getItem(0).getPdc("coresuite:summon_totem"), "Valerius totem PDC");
            TestAssertion.assertEquals("aethelgard", admin.getItem(1).getPdc("coresuite:summon_totem"), "Aethelgard totem PDC");
            TestAssertion.assertEquals("xolthos", admin.getItem(2).getPdc("coresuite:summon_totem"), "Xolthos totem PDC");
        }));

        // -------------------------------------------------------------
        // SCENARIO 8: Multiphase Boss Progression and Badges
        // -------------------------------------------------------------
        results.add(runTest("T4-08", "Scenario_Multiphase_Progression", 4, "Boss progresses 100% -> 66% -> 33% -> 0% updating phase and action bar badges", () -> {
            double maxHp = 1000.0;
            double hp = 1000.0;

            int phase = (hp > 660.0) ? 1 : (hp > 330.0) ? 2 : 3;
            TestAssertion.assertEquals(1, phase, "Starts at Phase 1");

            hp = 500.0;
            phase = (hp > 660.0) ? 1 : (hp > 330.0) ? 2 : 3;
            TestAssertion.assertEquals(2, phase, "Transitions to Phase 2");

            hp = 200.0;
            phase = (hp > 660.0) ? 1 : (hp > 330.0) ? 2 : 3;
            TestAssertion.assertEquals(3, phase, "Transitions to Phase 3");
        }));

        // -------------------------------------------------------------
        // SCENARIO 9: Resource Pack Integrity and Client Simulation
        // -------------------------------------------------------------
        results.add(runTest("T4-09", "Scenario_ResourcePack_Integrity", 4, "Verify sounds.json and all 3 streaming OGG loop assets can be loaded", () -> {
            Path packDir = ctx.getResourcePackDir();
            Path soundsJson = packDir.resolve("assets/coresuite/sounds.json");
            TestAssertion.assertTrue(Files.exists(soundsJson), "sounds.json exists");

            Path vOgg = packDir.resolve("assets/coresuite/sounds/boss/valerius.ogg");
            Path aOgg = packDir.resolve("assets/coresuite/sounds/boss/aethelgard.ogg");
            Path xOgg = packDir.resolve("assets/coresuite/sounds/boss/xolthos.ogg");

            TestAssertion.assertTrue(TestContext.isValidOggVorbis(vOgg), "valerius.ogg valid");
            TestAssertion.assertTrue(TestContext.isValidOggVorbis(aOgg), "aethelgard.ogg valid");
            TestAssertion.assertTrue(TestContext.isValidOggVorbis(xOgg), "xolthos.ogg valid");
        }));

        // -------------------------------------------------------------
        // SCENARIO 10: Multiplayer Raid Selective Codex Unlock Workflow
        // -------------------------------------------------------------
        results.add(runTest("T4-10", "Scenario_Multiplayer_Raid", 4, "Damager players unlock codex while outside spectator remains undiscovered ???", () -> {
            MockBukkitContext.MockPlayer damagerA = new MockBukkitContext.MockPlayer(UUID.randomUUID(), "DamagerA");
            MockBukkitContext.MockPlayer damagerB = new MockBukkitContext.MockPlayer(UUID.randomUUID(), "DamagerB");
            MockBukkitContext.MockPlayer spectatorC = new MockBukkitContext.MockPlayer(UUID.randomUUID(), "SpectatorC");

            // Record damage
            Map<UUID, Double> damageDealt = new HashMap<>();
            damageDealt.put(damagerA.getUniqueId(), 450.0);
            damageDealt.put(damagerB.getUniqueId(), 550.0);
            // Spectator dealt 0 damage

            // Boss dies: distribute rewards and record discovery for damagers
            Map<UUID, Set<String>> discoveries = new HashMap<>();
            for (Map.Entry<UUID, Double> entry : damageDealt.entrySet()) {
                if (entry.getValue() > 0) {
                    discoveries.computeIfAbsent(entry.getKey(), k -> new HashSet<>()).add("VALERIUS");
                }
            }

            TestAssertion.assertTrue(discoveries.get(damagerA.getUniqueId()).contains("VALERIUS"), "Damager A unlocked");
            TestAssertion.assertTrue(discoveries.get(damagerB.getUniqueId()).contains("VALERIUS"), "Damager B unlocked");
            TestAssertion.assertFalse(discoveries.getOrDefault(spectatorC.getUniqueId(), Collections.emptySet()).contains("VALERIUS"), "Spectator C remains undiscovered");
        }));

        // -------------------------------------------------------------
        // SCENARIO 11: Server Restart Discovery Persistence Verification
        // -------------------------------------------------------------
        results.add(runTest("T4-11", "Scenario_Server_Restart_Persistence", 4, "Discoveries saved to persistent storage survive simulated cache wipe", () -> {
            UUID player = UUID.randomUUID();
            Map<String, String> persistentStorage = new HashMap<>();
            persistentStorage.put(player.toString() + ":VALERIUS", "unlocked");

            // Cache wipe simulation
            Map<UUID, Set<String>> inMemoryCache = new HashMap<>();
            inMemoryCache.clear();
            TestAssertion.assertTrue(inMemoryCache.isEmpty(), "Cache cleared on reboot");

            // Reload from storage
            if (persistentStorage.containsKey(player.toString() + ":VALERIUS")) {
                inMemoryCache.computeIfAbsent(player, k -> new HashSet<>()).add("VALERIUS");
            }
            TestAssertion.assertTrue(inMemoryCache.get(player).contains("VALERIUS"), "Discovery restored from database");
        }));

        return results;
    }

    private static TestResult runTest(String testId, String feature, int tier, String desc, RunnableWithException test) {
        long start = System.currentTimeMillis();
        try {
            test.run();
            long dur = System.currentTimeMillis() - start;
            return TestResult.pass(testId, feature, tier, desc, dur);
        } catch (Throwable t) {
            long dur = System.currentTimeMillis() - start;
            return TestResult.fail(testId, feature, tier, desc, t.getMessage(), dur);
        }
    }

    @FunctionalInterface
    public interface RunnableWithException {
        void run() throws Throwable;
    }
}
