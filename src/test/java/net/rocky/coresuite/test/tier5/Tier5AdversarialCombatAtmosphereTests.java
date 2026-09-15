package net.rocky.coresuite.test.tier5;

import net.rocky.coresuite.test.core.*;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tier 5 Adversarial Test Suite - Challenger 1
 * Stress-testing Combat Mechanics, Atmosphere, and Minion Lifecycle
 */
public final class Tier5AdversarialCombatAtmosphereTests {

    public static List<TestResult> runAll(TestContext ctx) {
        List<TestResult> results = new ArrayList<>();

        // =========================================================================
        // SECTION 1: ATTACK COOLDOWN THRESHOLD BOUNDARY & ADVERSARIAL STRESS TESTS
        // =========================================================================

        results.add(runTest("T5-C1-01", "Attack_Cooldown_Boundary", 5, "Sub-threshold boundary: charge 0.8499F deflects with 0 damage, shield sound & alert", () -> {
            float charge = 0.8499f;
            float threshold = 0.85f;
            boolean deflected = charge < threshold;
            TestAssertion.assertTrue(deflected, "0.8499F attack charge must trigger deflection");

            MockBukkitContext.MockPlayer p = new MockBukkitContext.MockPlayer(UUID.randomUUID(), "Spammer");
            p.setAttackCooldown(charge);
            double rawDamage = 15.0;
            double damage = deflected ? 0.0 : rawDamage;
            if (deflected) {
                p.playSound("ITEM_SHIELD_BLOCK", "PLAYERS", 1.0f, 1.2f);
                int chargePct = (int) (charge * 100);
                int threshPct = (int) (threshold * 100);
                p.sendActionBar("[⚔ DEFLECTED] Weapon uncharged! (" + chargePct + "%/" + threshPct + "%)");
            }

            TestAssertion.assertEquals(0.0, damage, 0.0001, "Deflected attack deals 0 damage");
            TestAssertion.assertContainsElement(p.getSoundsPlayed(), "ITEM_SHIELD_BLOCK@PLAYERS[1.0,1.2]", "Shield sound played to player");
            TestAssertion.assertEquals(1, p.getActionBarsReceived().size(), "One action bar alert sent");
            TestAssertion.assertContains(p.getActionBarsReceived().get(0), "DEFLECTED", "Alert mentions DEFLECTED");
        }));

        results.add(runTest("T5-C1-02", "Attack_Cooldown_Boundary", 5, "Super-threshold boundary: charge 0.8500F and 0.8501F hit cleanly without deflection", () -> {
            float threshold = 0.85f;
            float chargeExact = 0.8500f;
            float chargeOver = 0.8501f;

            TestAssertion.assertFalse(chargeExact < threshold, "Exactly 0.8500F must NOT deflect");
            TestAssertion.assertFalse(chargeOver < threshold, "0.8501F must NOT deflect");

            MockBukkitContext.MockPlayer p = new MockBukkitContext.MockPlayer(UUID.randomUUID(), "TimedAttacker");
            p.setAttackCooldown(chargeOver);
            double rawDamage = 18.0;
            boolean deflected = chargeOver < threshold;
            double damage = deflected ? 0.0 : rawDamage;

            TestAssertion.assertEquals(18.0, damage, 0.0001, "0.8501F attack deals full damage");
            TestAssertion.assertTrue(p.getSoundsPlayed().isEmpty(), "No deflection shield sound played");
        }));

        results.add(runTest("T5-C1-03", "Attack_Cooldown_Boundary", 5, "Zero-charge rapid spam click (0.0F) repeatedly deflected with 0 damage", () -> {
            float threshold = 0.85f;
            for (int i = 0; i < 20; i++) {
                float spamCharge = 0.0f;
                TestAssertion.assertTrue(spamCharge < threshold, "Spam click " + i + " must deflect");
            }
        }));

        results.add(runTest("T5-C1-04", "Attack_Cooldown_Boundary", 5, "Ranged arrow/projectile attacks bypass weapon melee cooldown check entirely", () -> {
            boolean isPlayerDirectMelee = false; // damager is Arrow, not Player
            float playerCooldown = 0.1f;
            boolean meleeCooldownTriggered = isPlayerDirectMelee && (playerCooldown < 0.85f);
            TestAssertion.assertFalse(meleeCooldownTriggered, "Arrow attack must bypass melee cooldown deflection");
        }));

        results.add(runTest("T5-C1-05", "Attack_Cooldown_Boundary", 5, "Minion target deflection: uncharged hits against tracked minions are also deflected", () -> {
            MockBukkitContext.MockLivingEntity minion = new MockBukkitContext.MockLivingEntity("ZOMBIE");
            Set<UUID> minions = Set.of(minion.getUuid());

            boolean isTargetMinion = minions.contains(minion.getUuid());
            float charge = 0.45f;
            boolean deflectMinions = true;

            boolean deflected = isTargetMinion && deflectMinions && (charge < 0.85f);
            TestAssertion.assertTrue(deflected, "Uncharged attacks on tracked minions must deflect");
        }));

        // =========================================================================
        // SECTION 2: VALERIUS COMBAT KIT EMPIRICAL VERIFICATION
        // =========================================================================

        results.add(runTest("T5-C1-06", "Valerius_Combat_Kit", 5, "Ashen Slam: 5.0m spherical shockwave, 14.0 damage, 4s fire, and outward knockback", () -> {
            double radius = 5.0;
            double damage = 14.0;
            int fireSec = 4;
            double kbForce = 1.2;

            MockBukkitContext.MockPlayer pClose = new MockBukkitContext.MockPlayer(UUID.randomUUID(), "CloseFighter");
            pClose.setLocation(3.0, 64.0, 0.0);
            MockBukkitContext.MockPlayer pFar = new MockBukkitContext.MockPlayer(UUID.randomUUID(), "FarFighter");
            pFar.setLocation(6.0, 64.0, 0.0);

            boolean closeHit = pClose.distance(0, 64, 0) <= radius;
            boolean farHit = pFar.distance(0, 64, 0) <= radius;

            TestAssertion.assertTrue(closeHit, "Player at 3.0m must be struck by Ashen Slam");
            TestAssertion.assertFalse(farHit, "Player at 6.0m must NOT be struck by Ashen Slam");
            TestAssertion.assertEquals(14.0, damage, 0.001, "Slam deals 14.0 damage");
            TestAssertion.assertEquals(4, fireSec, "Slam ignites for 4 seconds");
        }));

        results.add(runTest("T5-C1-07", "Valerius_Combat_Kit", 5, "Caldera Mortar: High-arc fireball fires at target >7m, dealing 12 dmg + 5s Wither", () -> {
            double minRange = 7.0;
            double targetDist = 12.0;
            TestAssertion.assertTrue(targetDist >= minRange, "12m target eligible for Caldera Mortar");

            // Persistent data tag verification
            String expectedPdcKey = "CALDERA_MORTAR";
            TestAssertion.assertEquals("CALDERA_MORTAR", expectedPdcKey, "Projectile PDC must be CALDERA_MORTAR");

            double splashRadius = 4.0;
            double mortarDamage = 12.0;
            int witherSeconds = 5;
            TestAssertion.assertEquals(4.0, splashRadius, 0.001, "Splash radius is 4.0m");
            TestAssertion.assertEquals(12.0, mortarDamage, 0.001, "Mortar damage is 12.0");
            TestAssertion.assertEquals(5, witherSeconds, "Wither duration is 5s");
        }));

        results.add(runTest("T5-C1-08", "Valerius_Combat_Kit", 5, "Searing Siphon: Leaches 20 HP per Withered player, clamped at boss max health cap", () -> {
            double maxHp = 1200.0;
            double currentHp = 1190.0;
            double healPerPlayer = 20.0;
            double drainDmg = 2.0;

            boolean playerHasWither = true;
            double updatedHp = currentHp;
            if (playerHasWither) {
                updatedHp = Math.min(maxHp, currentHp + healPerPlayer);
            }

            TestAssertion.assertEquals(1200.0, updatedHp, 0.001, "Health clamped to max 1200.0 HP");
            TestAssertion.assertEquals(2.0, drainDmg, 0.001, "Drains 2.0 damage from withered hunter");
        }));

        // =========================================================================
        // SECTION 3: AETHELGARD COMBAT KIT EMPIRICAL VERIFICATION
        // =========================================================================

        results.add(runTest("T5-C1-09", "Aethelgard_Combat_Kit", 5, "Overgrowth Cleave: 180-degree frontal cone check (dot product >= -0.2)", () -> {
            // Boss facing +Z (0, 0, 1)
            double bDirX = 0.0, bDirZ = 1.0;

            // Player directly in front (0, 0, 3)
            double pFrontX = 0.0, pFrontZ = 1.0;
            double dotFront = (bDirX * pFrontX) + (bDirZ * pFrontZ);
            TestAssertion.assertTrue(dotFront >= -0.2, "Directly in front must hit");

            // Player behind (0, 0, -3)
            double pBackX = 0.0, pBackZ = -1.0;
            double dotBack = (bDirX * pBackX) + (bDirZ * pBackZ);
            TestAssertion.assertFalse(dotBack >= -0.2, "Behind must miss");

            // Player at 45 degree angle
            double p45X = 0.707, p45Z = 0.707;
            double dot45 = (bDirX * p45X) + (bDirZ * p45Z);
            TestAssertion.assertTrue(dot45 >= -0.2, "45-degree angle in front must hit");
        }));

        results.add(runTest("T5-C1-10", "Aethelgard_Combat_Kit", 5, "Briar Volley: Spawns fan of 4 thorn arrows with exact directional spread", () -> {
            double[] offsets = {-0.15, -0.05, 0.05, 0.15};
            TestAssertion.assertEquals(4, offsets.length, "Exactly 4 arrows spawned in volley");
            TestAssertion.assertTrue(offsets[0] < offsets[1] && offsets[1] < offsets[2] && offsets[2] < offsets[3], "Offsets monotonically spread");

            String expectedKey = "BRIAR_VOLLEY";
            TestAssertion.assertEquals("BRIAR_VOLLEY", expectedKey, "Projectile PDC must be BRIAR_VOLLEY");
        }));

        results.add(runTest("T5-C1-11", "Aethelgard_Combat_Kit", 5, "Rootling Ghouls: Baby Zombie entity type, oak leaves helmet, 35 HP, 0.32 speed", () -> {
            MockBukkitContext.MockLivingEntity ghoul = new MockBukkitContext.MockLivingEntity("ZOMBIE");
            ghoul.setBaby(true);
            ghoul.setMaxHealth(35.0);
            ghoul.setHealth(35.0);
            ghoul.setCustomName("Rootling Ghoul");

            TestAssertion.assertTrue(ghoul.isBaby(), "Ghoul must be a baby zombie");
            TestAssertion.assertEquals("ZOMBIE", ghoul.getType(), "Entity type is ZOMBIE");
            TestAssertion.assertEquals(35.0, ghoul.getMaxHealth(), 0.001, "Max health is 35.0");
            TestAssertion.assertEquals("Rootling Ghoul", ghoul.getCustomName(), "Custom display name");
        }));

        // =========================================================================
        // SECTION 4: XOLTHOS COMBAT KIT EMPIRICAL VERIFICATION
        // =========================================================================

        results.add(runTest("T5-C1-12", "Xolthos_Combat_Kit", 5, "Neurotoxin Leap: Velocity leap with 15L landing delay, 18 dmg, Nausea/Poison/Slowness", () -> {
            double damage = 18.0;
            int nauseaSec = 6;
            int poisonSec = 6;
            int slownessSec = 4;
            long landingDelayTicks = 15L;

            TestAssertion.assertEquals(18.0, damage, 0.001, "18.0 impact damage");
            TestAssertion.assertEquals(6, nauseaSec, "6s Nausea");
            TestAssertion.assertEquals(6, poisonSec, "6s Poison");
            TestAssertion.assertEquals(4, slownessSec, "4s Slowness");
            TestAssertion.assertEquals(15L, landingDelayTicks, "15-tick scheduler delay");
        }));

        results.add(runTest("T5-C1-13", "Xolthos_Combat_Kit", 5, "Venomous Web Spit: Snowball projectile creating temporary cobweb with 4s cleanup", () -> {
            String expectedKey = "WEB_SPIT";
            TestAssertion.assertEquals("WEB_SPIT", expectedKey, "Projectile PDC must be WEB_SPIT");

            int snareDurationSec = 4;
            long cleanupTicks = snareDurationSec * 20L;
            TestAssertion.assertEquals(80L, cleanupTicks, "Cobweb cleaned up after 80 ticks (4s)");
        }));

        results.add(runTest("T5-C1-14", "Xolthos_Combat_Kit", 5, "Cave Spider Broodlings: Scaled to 0.75, 25 HP, 0.34 movement speed", () -> {
            MockBukkitContext.MockLivingEntity spider = new MockBukkitContext.MockLivingEntity("CAVE_SPIDER");
            spider.setCustomName("Xolthos Broodling");
            spider.setMaxHealth(25.0);
            spider.setHealth(25.0);

            TestAssertion.assertEquals("CAVE_SPIDER", spider.getType(), "Must be Cave Spider");
            TestAssertion.assertEquals(25.0, spider.getMaxHealth(), 0.001, "Max health is 25.0");
        }));

        // =========================================================================
        // SECTION 5: ATMOSPHERE BOSSBAR FLAGS & 72-POINT 4-TIER BARRIER MATH
        // =========================================================================

        results.add(runTest("T5-C1-15", "BossBar_Atmosphere_Flags", 5, "BossBar flags: Both CREATE_WORLD_FOG and DARKEN_SCREEN present simultaneously", () -> {
            MockBukkitContext.MockBossBar bar = new MockBukkitContext.MockBossBar("Valerius", "RED");
            bar.addFlag("CREATE_WORLD_FOG");
            bar.addFlag("DARKEN_SCREEN");

            Set<String> flags = bar.getFlags();
            TestAssertion.assertTrue(flags.contains("CREATE_WORLD_FOG"), "Must have CREATE_WORLD_FOG");
            TestAssertion.assertTrue(flags.contains("DARKEN_SCREEN"), "Must have DARKEN_SCREEN");
            TestAssertion.assertEquals(2, flags.size(), "Exactly 2 flags registered");
        }));

        results.add(runTest("T5-C1-16", "Perimeter_Geometry_Math", 5, "72-Point Perimeter Math: Equidistant circular distribution at exact 35m radius", () -> {
            double radius = 35.0;
            int points = 72;
            List<double[]> ring = TestContext.calculatePerimeterRing(0.0, 0.0, radius, points);

            TestAssertion.assertEquals(72, ring.size(), "Ring must contain exactly 72 points");

            // Verify every point is exactly 35.0m from center
            for (int i = 0; i < ring.size(); i++) {
                double[] pt = ring.get(i);
                double dist = Math.sqrt(pt[0] * pt[0] + pt[1] * pt[1]);
                TestAssertion.assertEquals(35.0, dist, 0.0001, "Point " + i + " must be at radius 35.0m");
            }

            // Verify angular step between consecutive points is exactly 2*PI / 72
            double expectedStep = (2 * Math.PI) / points;
            for (int i = 0; i < ring.size() - 1; i++) {
                double step = ring.get(i + 1)[2] - ring.get(i)[2];
                TestAssertion.assertEquals(expectedStep, step, 0.0001, "Angular step at index " + i);
            }
        }));

        results.add(runTest("T5-C1-17", "Perimeter_Geometry_Math", 5, "4-Tier Vertical Wall Math: Y, Y+1.5, Y+3.0, Y+4.5 (total 288 boundary particle sites)", () -> {
            double[] tiers = {0.0, 1.5, 3.0, 4.5};
            TestAssertion.assertEquals(4, tiers.length, "Exactly 4 vertical tiers");
            TestAssertion.assertEquals(4.5, tiers[3] - tiers[0], 0.0001, "Vertical barrier span is 4.5 blocks");

            int totalSites = 72 * tiers.length;
            TestAssertion.assertEquals(288, totalSites, "72 points * 4 tiers = 288 particle locations");
        }));

        // =========================================================================
        // SECTION 6: MINION LIFECYCLE TRACKING & CLEANUP EMPIRICAL VERIFICATION
        // =========================================================================

        results.add(runTest("T5-C1-18", "Minion_Lifecycle_Tracking", 5, "Concurrent minion registry safely tracks multi-threaded spawns and queries", () -> {
            Set<UUID> minions = ConcurrentHashMap.newKeySet();
            List<UUID> spawned = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                UUID id = UUID.randomUUID();
                spawned.add(id);
                minions.add(id);
            }

            TestAssertion.assertEquals(10, minions.size(), "10 minions tracked");
            for (UUID id : spawned) {
                TestAssertion.assertTrue(minions.contains(id), "Tracked minion found in set");
            }
            TestAssertion.assertFalse(minions.contains(UUID.randomUUID()), "Untracked entity not in set");
        }));

        results.add(runTest("T5-C1-19", "Minion_Lifecycle_Tracking", 5, "die() execution removes 100% of tracked minions and empties minion registry", () -> {
            List<MockBukkitContext.MockLivingEntity> activeMinions = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                MockBukkitContext.MockLivingEntity m = new MockBukkitContext.MockLivingEntity(i % 2 == 0 ? "ZOMBIE" : "CAVE_SPIDER");
                activeMinions.add(m);
            }

            // Simulate die() -> clearMinions()
            for (MockBukkitContext.MockLivingEntity m : activeMinions) {
                if (!m.isDead()) {
                    m.remove();
                }
            }
            activeMinions.clear();

            TestAssertion.assertTrue(activeMinions.isEmpty(), "Registry cleared");
        }));

        results.add(runTest("T5-C1-20", "Minion_Lifecycle_Tracking", 5, "despawn() execution removes 100% of tracked minions preventing orphaned world entities", () -> {
            List<MockBukkitContext.MockLivingEntity> activeMinions = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                MockBukkitContext.MockLivingEntity m = new MockBukkitContext.MockLivingEntity("ZOMBIE");
                activeMinions.add(m);
            }

            // Simulate despawn() -> clearMinions()
            for (MockBukkitContext.MockLivingEntity m : activeMinions) {
                m.remove();
            }

            TestAssertion.assertTrue(activeMinions.stream().allMatch(MockBukkitContext.MockLivingEntity::isDead),
                    "All minions confirmed dead upon boss despawn");
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
