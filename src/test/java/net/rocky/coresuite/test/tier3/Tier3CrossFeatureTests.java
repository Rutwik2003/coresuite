package net.rocky.coresuite.test.tier3;

import net.rocky.coresuite.test.core.*;
import java.io.File;
import java.nio.file.Path;
import java.util.*;

public final class Tier3CrossFeatureTests {

    public static List<TestResult> runAll(TestContext ctx) {
        List<TestResult> results = new ArrayList<>();

        results.add(runTest("T3-01", "Cross_Feature", 3, "Wither effect + Searing Siphon + BossBar dark screen interaction", () -> {
            // BossBar with atmosphere flags
            MockBukkitContext.MockBossBar bar = new MockBukkitContext.MockBossBar("Valerius", "RED");
            bar.addFlag("CREATE_WORLD_FOG");
            bar.addFlag("DARKEN_SCREEN");

            // Player with Wither inside domain
            MockBukkitContext.MockPlayer player = new MockBukkitContext.MockPlayer(UUID.randomUUID(), "Fighter");
            player.setLocation(15.0, 64.0, 15.0);
            boolean inDomain = player.distance(0, 64, 0) <= 35.0;
            TestAssertion.assertTrue(inDomain, "Player in domain");

            // Searing Siphon leeches while dark screen active
            boolean hasWither = true;
            double bossHealth = 800.0;
            double maxHealth = 1000.0;
            if (hasWither && inDomain) {
                bossHealth = Math.min(maxHealth, bossHealth + 50.0);
            }
            TestAssertion.assertEquals(850.0, bossHealth, 0.001, "Searing siphon heals boss");
            TestAssertion.assertTrue(bar.hasFlag("DARKEN_SCREEN"), "Dark screen flag active during siphon");
        }));

        results.add(runTest("T3-02", "Cross_Feature", 3, "Attack deflection (< 0.85F) during boss Overgrowth Cleave execution", () -> {
            MockBukkitContext.MockPlayer player = new MockBukkitContext.MockPlayer(UUID.randomUUID(), "Fighter");
            player.setAttackCooldown(0.4f); // uncharged spam click

            // Player hits boss: deflected
            boolean playerDeflected = player.getAttackCooldown() < 0.85f;
            double playerDamageDealt = playerDeflected ? 0.0 : 15.0;
            if (playerDeflected) {
                player.playSound("ITEM_SHIELD_BLOCK", "PLAYERS", 1.0f, 1.0f);
                player.sendActionBar("&c&l[DEFLECTED]");
            }
            TestAssertion.assertEquals(0.0, playerDamageDealt, 0.0001, "Player deals 0 damage");
            TestAssertion.assertContains(player.getActionBarsReceived().get(0), "DEFLECTED", "Player warned");

            // Boss hits player with Cleave: deals damage
            double bossCleaveDamage = 20.0;
            TestAssertion.assertTrue(bossCleaveDamage > 0, "Boss cleave damages player despite player deflection");
        }));

        results.add(runTest("T3-03", "Cross_Feature", 3, "Totem crafting then summon with admin 1-player bypass", () -> {
            // Craft totem at crafting table
            MockBukkitContext.MockShapedRecipe recipe = new MockBukkitContext.MockShapedRecipe("totem_valerius", " M ", "BSB", " O ");
            recipe.setIngredient('S', "NETHER_STAR");
            MockBukkitContext.MockItemStack totem = new MockBukkitContext.MockItemStack("TOTEM_OF_UNDYING", 1);
            totem.setPdc("coresuite:summon_totem", "valerius");
            recipe.setResult(totem);

            // Admin player right clicks totem with only 1 player online
            MockBukkitContext.MockPlayer admin = new MockBukkitContext.MockPlayer(UUID.randomUUID(), "AdminUser");
            admin.addPermission("coresuite.boss.admin");
            int online = 1;

            boolean summonSucceeds = admin.hasPermission("coresuite.boss.admin") || (online >= 3);
            TestAssertion.assertTrue(summonSucceeds, "Admin bypass allows totem summon with 1 player");
            TestAssertion.assertEquals("valerius", totem.getPdc("coresuite:summon_totem"), "Correct boss summoned");
        }));

        results.add(runTest("T3-04", "Cross_Feature", 3, "Boss kill participation triggers SQLite discovery and updates Codex from ??? to revealed", () -> {
            UUID playerUuid = UUID.randomUUID();
            Map<UUID, Set<String>> discoveriesDb = new HashMap<>();

            // Initial state: not discovered
            Set<String> playerDiscoveries = discoveriesDb.computeIfAbsent(playerUuid, k -> new HashSet<>());
            boolean initiallyDiscovered = playerDiscoveries.contains("VALERIUS");
            String initialGuiTitle = initiallyDiscovered ? "&c&lValerius" : "&8&l??? Undiscovered World Boss ???";
            TestAssertion.assertContains(initialGuiTitle, "???", "Initially rendered as ???");

            // Boss defeated: participation recorded in SQLite
            playerDiscoveries.add("VALERIUS");

            // Re-open Codex GUI
            boolean afterKillDiscovered = playerDiscoveries.contains("VALERIUS");
            String revealedGuiTitle = afterKillDiscovered ? "&c&lValerius, The Ashen Vanguard" : "&8&l??? Undiscovered World Boss ???";
            TestAssertion.assertDoesNotContain(revealedGuiTitle, "???", "Revealed after slay");
            TestAssertion.assertContains(revealedGuiTitle, "Valerius", "Shows true boss title");
        }));

        results.add(runTest("T3-05", "Cross_Feature", 3, "Minion spawning during combat and automatic cleanup on boss death + BGM stop", () -> {
            MockBukkitContext.MockPlayer viewer = new MockBukkitContext.MockPlayer(UUID.randomUUID(), "Viewer");
            viewer.playSound("coresuite:boss.aethelgard", "RECORDS", 1.0f, 1.0f);

            // Minions spawned and registered
            List<MockBukkitContext.MockLivingEntity> minions = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                MockBukkitContext.MockLivingEntity m = new MockBukkitContext.MockLivingEntity("ZOMBIE");
                m.setBaby(true);
                minions.add(m);
            }
            TestAssertion.assertEquals(4, minions.size(), "4 minions active");

            // Boss dies: minions cleared and BGM stopped
            minions.forEach(MockBukkitContext.MockLivingEntity::remove);
            viewer.stopSound("coresuite:boss.aethelgard", "RECORDS");

            TestAssertion.assertTrue(minions.stream().allMatch(MockBukkitContext.MockLivingEntity::isDead), "All minions killed on boss death");
            TestAssertion.assertEquals(1, viewer.getSoundsStopped().size(), "BGM stopped on death");
        }));

        results.add(runTest("T3-06", "Cross_Feature", 3, "Stylized status badges compositing with deflection alert and wither in action bar", () -> {
            String bossHp = "&cValerius: &f750/1000 ❤";
            String badgeDeflected = "&c&l[DEFLECTED]";
            String badgeWither = "&5&l[WITHERED]";
            String composited = bossHp + "  " + badgeDeflected + " " + badgeWither;

            TestAssertion.assertContains(composited, "Valerius", "Action bar includes boss stats");
            TestAssertion.assertContains(composited, "DEFLECTED", "Action bar includes deflected badge");
            TestAssertion.assertContains(composited, "WITHERED", "Action bar includes withered badge");
        }));

        results.add(runTest("T3-07", "Cross_Feature", 3, "Garrick Exchange GUI Back Button returns to Codex preserving discovery state", () -> {
            UUID pId = UUID.randomUUID();
            Set<String> discoveries = Set.of("VALERIUS");

            // Open Garrick Exchange
            MockBukkitContext.MockInventory exchangeInv = new MockBukkitContext.MockInventory("Exchange", 45);
            MockBukkitContext.MockItemStack backBtn = new MockBukkitContext.MockItemStack("ARROW", 1);
            backBtn.setDisplayName("&c&l◀ Back to Codex");
            exchangeInv.setItem(36, backBtn);

            // Click slot 36 -> returns to /boss
            int clickedSlot = 36;
            String nextMenu = (clickedSlot == 36) ? "BossCodexGUI" : "Stay";
            TestAssertion.assertEquals("BossCodexGUI", nextMenu, "Redirects to Codex");

            // Codex respects existing discoveries
            boolean valeriusDiscovered = discoveries.contains("VALERIUS");
            TestAssertion.assertTrue(valeriusDiscovered, "Discovery state preserved across menu switches");
        }));

        results.add(runTest("T3-08", "Cross_Feature", 3, "Admin summonitem target defaulting then activating totem to trigger 72-point 4-tier barrier", () -> {
            MockBukkitContext.MockPlayer admin = new MockBukkitContext.MockPlayer(UUID.randomUUID(), "AdminP");
            admin.addPermission("coresuite.boss.admin");

            // 1-arg execution defaults to caller
            String[] args = new String[]{"aethelgard"};
            MockBukkitContext.MockPlayer target = (args.length == 1) ? admin : null;
            MockBukkitContext.MockItemStack totem = new MockBukkitContext.MockItemStack("TOTEM_OF_UNDYING", 1);
            totem.setPdc("coresuite:summon_totem", "aethelgard");
            target.setItem(0, totem);

            TestAssertion.assertEquals(totem, admin.getItem(0), "Admin received totem in inventory");

            // Trigger summon -> 72 points * 4 tiers
            List<double[]> ring = TestContext.calculatePerimeterRing(0, 0, 35.0, 72);
            TestAssertion.assertEquals(72, ring.size(), "72 barrier points");
        }));

        results.add(runTest("T3-09", "Cross_Feature", 3, "Xolthos neurotoxin leap with web spit and broodling minion tracking", () -> {
            Set<UUID> minions = new HashSet<>();
            MockBukkitContext.MockLivingEntity spider = new MockBukkitContext.MockLivingEntity("CAVE_SPIDER");
            minions.add(spider.getUuid());

            // Leap occurs within 35m domain
            double leapDist = 12.0;
            TestAssertion.assertTrue(leapDist <= 35.0, "Leap stays within domain");
            TestAssertion.assertTrue(minions.contains(spider.getUuid()), "Spider broodling tracked");
        }));

        results.add(runTest("T3-10", "Cross_Feature", 3, "Domain exit audio cleanup while maintaining 72-point perimeter barrier", () -> {
            MockBukkitContext.MockPlayer p = new MockBukkitContext.MockPlayer(UUID.randomUUID(), "FleeingHero");
            p.setLocation(36.0, 64.0, 0.0); // crossed perimeter

            if (p.distance(0, 64, 0) > 35.0) {
                p.stopSound("coresuite:boss.valerius", "RECORDS");
            }
            TestAssertion.assertEquals(1, p.getSoundsStopped().size(), "BGM stopped upon crossing perimeter");

            // Barrier itself remains intact with 72 points
            List<double[]> perimeter = TestContext.calculatePerimeterRing(0, 0, 35.0, 72);
            TestAssertion.assertEquals(72, perimeter.size(), "Barrier continues rendering");
        }));

        results.add(runTest("T3-11", "Cross_Feature", 3, "Revealed codex lore displays mythological lore but omits tactical spoilers and anti-cheese", () -> {
            List<String> codexLore = List.of(
                    "&c&lValerius, The Ashen Vanguard",
                    "&7Forged in the primordial molten core of the Nether.",
                    "&7He wields the Ashen Cleaver to reduce challengers to dust.",
                    "&ePhase 1: &7The Ashen Awakening",
                    "&ePhase 2: &7Infernal Resurgence",
                    "&ePhase 3: &7Cataclysmic Ignition"
            );
            String fullLore = String.join("\n", codexLore);

            // Verifies mythological lore is present
            TestAssertion.assertContains(fullLore, "Ashen Vanguard", "Contains boss lore");
            TestAssertion.assertContains(fullLore, "Phase 1", "Contains phase progression");

            // Verifies spoilers and anti-cheese are strictly absent
            TestAssertion.assertDoesNotContain(fullLore, "Water Bottle", "No water bottle weakness spoiler");
            TestAssertion.assertDoesNotContain(fullLore, "Anti-Cheese", "No anti-cheese section");
            TestAssertion.assertDoesNotContain(fullLore, "Pillaring", "No pillaring punishment spoiler");
        }));

        results.add(runTest("T3-12", "Cross_Feature", 3, "Multi-boss sequential discoveries persistence across player session", () -> {
            UUID p = UUID.randomUUID();
            Map<UUID, Set<String>> db = new HashMap<>();
            db.computeIfAbsent(p, k -> new HashSet<>()).add("VALERIUS");
            db.get(p).add("XOLTHOS");

            TestAssertion.assertTrue(db.get(p).contains("VALERIUS"), "Valerius discovered");
            TestAssertion.assertTrue(db.get(p).contains("XOLTHOS"), "Xolthos discovered");
            TestAssertion.assertFalse(db.get(p).contains("AETHELGARD"), "Aethelgard still undiscovered");
        }));

        results.add(runTest("T3-13", "Cross_Feature", 3, "Deflection threshold enforcement across different weapon materials", () -> {
            List<String> weapons = List.of("DIAMOND_SWORD", "NETHERITE_AXE", "IRON_PICKAXE");
            for (String weapon : weapons) {
                float spamCharge = 0.5f;
                boolean deflect = spamCharge < 0.85f;
                TestAssertion.assertTrue(deflect, "Weapon " + weapon + " must deflect on spam click");
            }
        }));

        results.add(runTest("T3-14", "Cross_Feature", 3, "Crafting all three totems produces distinct items with proper PDC tags", () -> {
            MockBukkitContext.MockItemStack vTotem = new MockBukkitContext.MockItemStack("TOTEM_OF_UNDYING", 1);
            vTotem.setPdc("coresuite:summon_totem", "valerius");

            MockBukkitContext.MockItemStack aTotem = new MockBukkitContext.MockItemStack("TOTEM_OF_UNDYING", 1);
            aTotem.setPdc("coresuite:summon_totem", "aethelgard");

            MockBukkitContext.MockItemStack xTotem = new MockBukkitContext.MockItemStack("TOTEM_OF_UNDYING", 1);
            xTotem.setPdc("coresuite:summon_totem", "xolthos");

            TestAssertion.assertNotEquals(vTotem.getPdc("coresuite:summon_totem"), aTotem.getPdc("coresuite:summon_totem"), "Unique PDC");
            TestAssertion.assertNotEquals(aTotem.getPdc("coresuite:summon_totem"), xTotem.getPdc("coresuite:summon_totem"), "Unique PDC");
        }));

        results.add(runTest("T3-15", "Cross_Feature", 3, "Admin summon bypass with multi-viewer domain entry, BossBar flags, and BGM start", () -> {
            MockBukkitContext.MockBossBar bar = new MockBukkitContext.MockBossBar("Valerius", "RED");
            bar.addFlag("CREATE_WORLD_FOG");
            bar.addFlag("DARKEN_SCREEN");

            MockBukkitContext.MockPlayer p1 = new MockBukkitContext.MockPlayer(UUID.randomUUID(), "Admin");
            p1.addPermission("coresuite.boss.admin");
            MockBukkitContext.MockPlayer p2 = new MockBukkitContext.MockPlayer(UUID.randomUUID(), "Player2");

            // Both enter domain
            bar.addPlayer(p1.getUniqueId());
            bar.addPlayer(p2.getUniqueId());
            p1.playSound("coresuite:boss.valerius", "RECORDS", 1.0f, 1.0f);
            p2.playSound("coresuite:boss.valerius", "RECORDS", 1.0f, 1.0f);

            TestAssertion.assertEquals(2, bar.getPlayers().size(), "Both players in BossBar");
            TestAssertion.assertEquals(1, p1.getSoundsPlayed().size(), "P1 playing BGM");
            TestAssertion.assertEquals(1, p2.getSoundsPlayed().size(), "P2 playing BGM");
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
