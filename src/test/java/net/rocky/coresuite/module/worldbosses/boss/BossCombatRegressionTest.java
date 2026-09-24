package net.rocky.coresuite.module.worldbosses.boss;

import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.*;
import net.rocky.coresuite.module.worldbosses.combat.BossBarManager;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BossCombatRegressionTest {
    @Test void alliedHitsAreCancelledBeforeSignatureItemEffects() throws Exception {
        var module = mock(net.rocky.coresuite.module.worldbosses.WorldBossesModule.class, CALLS_REAL_METHODS);
        var boss = mock(XolthosBoss.class); when(boss.isActive()).thenReturn(true);
        var party = mock(net.rocky.coresuite.module.worldbosses.party.BossPartyManager.class);
        var attacker = mock(org.bukkit.entity.Player.class); var victim = mock(org.bukkit.entity.Player.class);
        when(attacker.getUniqueId()).thenReturn(UUID.randomUUID()); when(victim.getUniqueId()).thenReturn(UUID.randomUUID());
        when(party.areAllies(attacker.getUniqueId(), victim.getUniqueId())).thenReturn(true);
        for (var entry : Map.of("activeBoss", boss, "partyManager", party).entrySet()) {
            var field = module.getClass().getDeclaredField(entry.getKey());
            field.setAccessible(true); field.set(module, entry.getValue());
        }
        var event = mock(org.bukkit.event.entity.EntityDamageByEntityEvent.class);
        when(event.getDamager()).thenReturn(attacker); when(event.getEntity()).thenReturn(victim);
        module.onEntityDamageByEntity(event);
        verify(event).setCancelled(true);
        verify(attacker, never()).getInventory();
        verify(victim, never()).addPotionEffect(any());
    }

    @Test void signatureTraitsRespectAwakeningDomainAndPaidAdmission() throws Exception {
        var moduleClass = net.rocky.coresuite.module.worldbosses.WorldBossesModule.class;
        var module = mock(moduleClass, CALLS_REAL_METHODS);
        var boss = mock(XolthosBoss.class); when(boss.isActive()).thenReturn(true);
        var target = mock(LivingEntity.class); when(boss.getEntity()).thenReturn(target);
        var player = mock(org.bukkit.entity.Player.class);
        var event = mock(org.bukkit.event.entity.EntityDamageByEntityEvent.class);
        when(event.getFinalDamage()).thenReturn(5d);
        var field = moduleClass.getDeclaredField("activeBoss"); field.setAccessible(true); field.set(module, boss);
        var trait = moduleClass.getDeclaredMethod("handleSignatureWeaponEffects", org.bukkit.event.entity.EntityDamageByEntityEvent.class, org.bukkit.entity.Player.class, LivingEntity.class);
        trait.setAccessible(true);
        trait.invoke(module, event, player, target);
        when(boss.isAwakened()).thenReturn(true);
        var domain = mock(net.rocky.coresuite.module.worldbosses.combat.BossDomainManager.class);
        when(boss.getDomainManager()).thenReturn(domain);
        trait.invoke(module, event, player, target);
        when(domain.isInDomain(any())).thenReturn(true);
        var portal = mock(net.rocky.coresuite.module.worldbosses.portal.BossPortalManager.class);
        field = moduleClass.getDeclaredField("portalManager"); field.setAccessible(true); field.set(module, portal);
        trait.invoke(module, event, player, target);
        verify(player, never()).getInventory();
    }

    @Test void inactivityExpiresAfterThirtyMinutesAndTankingDoesNotKeepEncounterAlive() throws Exception {
        var boss = new ValeriusBoss(null, new YamlConfiguration(), null);
        var created = WorldBoss.class.getDeclaredField("encounterCreatedAt"); created.setAccessible(true);
        long start = created.getLong(boss);
        boss.active = true; boss.isAwakened = true;
        boss.damageTracker = new net.rocky.coresuite.module.worldbosses.combat.BossDamageTracker(1000);
        var player = mock(org.bukkit.entity.Player.class);
        when(player.getUniqueId()).thenReturn(UUID.randomUUID()); when(player.getName()).thenReturn("Fighter");
        assertFalse(boss.isIdleExpired(start + 1799999));
        boss.recordTankContribution(player);
        assertTrue(boss.isIdleExpired(start + 1800000));
        var activity = WorldBoss.class.getDeclaredField("lastPlayerCombatAt"); activity.setAccessible(true);
        activity.setLong(boss, start + 600000);
        assertFalse(boss.isIdleExpired(start + 1800000));
        assertTrue(boss.isIdleExpired(start + 2400000));
        boss.recordParticipation(player);
        assertTrue(activity.getLong(boss) > 0);
    }

    @Test void quenchingAlsoStopsAttackLifesteal() {
        var boss = new ValeriusBoss(null, new YamlConfiguration(), null);
        boss.currentHealth = 100;
        assertEquals(15, boss.healFromAttack(20));
        assertEquals(115, boss.currentHealth);
        boss.applyQuenched(10);
        assertEquals(0, boss.healFromAttack(20));
        assertEquals(115, boss.currentHealth);
    }
    @Test void scalingKeepsPeakGroupSizeAndLateJoinDoesNotHeal() throws Exception {
        var config = new YamlConfiguration(); config.set("valerius.max-health", 1200);
        var boss = new ValeriusBoss(null, config, null);
        World world = mock(World.class); boss.entity = mock(LivingEntity.class);
        when(boss.entity.isValid()).thenReturn(true); when(boss.entity.getWorld()).thenReturn(world);
        when(boss.entity.getLocation()).thenReturn(new Location(world, 0, 64, 0));
        boss.barManager = mock(BossBarManager.class);
        boss.damageTracker = mock(net.rocky.coresuite.module.worldbosses.combat.BossDamageTracker.class);
        List<org.bukkit.entity.Player> fighters = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            var player = mock(org.bukkit.entity.Player.class); when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.getLocation()).thenReturn(new Location(world, i, 64, 0)); fighters.add(player);
        }
        when(world.getPlayers()).thenReturn(fighters.subList(0, 3));
        when(boss.barManager.getViewers()).thenReturn(new HashSet<>(fighters.subList(0, 3)));
        var update = WorldBoss.class.getDeclaredMethod("updateScaling"); update.setAccessible(true);
        update.invoke(boss); assertEquals(1440, boss.maxHealth); assertEquals(1440, boss.currentHealth);
        boss.currentHealth = 10;
        when(boss.damageTracker.getTotalDamage()).thenReturn(1430d);
        when(boss.damageTracker.wasRecentlyActive(any(), anyLong())).thenReturn(true);
        when(world.getPlayers()).thenReturn(fighters); update.invoke(boss);
        assertEquals(1800, boss.maxHealth); assertEquals(10, boss.currentHealth);
        when(world.getPlayers()).thenReturn(fighters.subList(0, 1)); update.invoke(boss);
        assertEquals(4, boss.getScaledPlayers()); assertEquals(1800, boss.maxHealth); assertEquals(10, boss.currentHealth);
    }

    @Test void fourWitheredPlayersShareReducedSiphonCapWithoutDamageNerf() throws Exception {
        var boss = spy(new ValeriusBoss(null, new YamlConfiguration(), null));
        boss.currentHealth = 100; boss.entity = mock(LivingEntity.class);
        boss.barManager = mock(BossBarManager.class);
        World world = mock(World.class);
        Set<org.bukkit.entity.Player> fighters = new LinkedHashSet<>();
        for (int i = 0; i < 4; i++) {
            var player = mock(org.bukkit.entity.Player.class);
            when(player.isOnline()).thenReturn(true); when(player.getWorld()).thenReturn(world);
            when(player.getLocation()).thenAnswer(c -> new Location(world, 1, 64, 1));
            doReturn(true).when(boss).isSiphonAfflicted(player);
            fighters.add(player);
        }
        when(boss.barManager.getViewers()).thenReturn(fighters);
        doNothing().when(boss).sendCombatAlert(any(), any(), anyString(), anyInt());
        var siphon = ValeriusBoss.class.getDeclaredMethod("executeSearingSiphon", Location.class); siphon.setAccessible(true);
        siphon.invoke(boss, new Location(world, 0, 64, 0));
        assertEquals(160, boss.currentHealth);
        for (var player : fighters) verify(player).damage(4d, boss.entity);
    }
    @Test void sustainedGroupFocusRotatesToAnotherFighter() throws Exception {
        var boss = new AethelgardBoss(null, new YamlConfiguration(), null);
        boss.entity = mock(org.bukkit.entity.Mob.class); boss.isAwakened = true;
        boss.barManager = mock(BossBarManager.class);
        boss.domainManager = mock(net.rocky.coresuite.module.worldbosses.combat.BossDomainManager.class);
        boss.damageTracker = mock(net.rocky.coresuite.module.worldbosses.combat.BossDamageTracker.class);
        when(boss.damageTracker.getSortedDamagers()).thenReturn(List.of());
        when(boss.entity.isValid()).thenReturn(true);
        World world = mock(World.class); Location center = new Location(world, 0, 64, 0);
        when(boss.entity.getLocation()).thenReturn(center);
        var tank = mock(org.bukkit.entity.Player.class); var teammate = mock(org.bukkit.entity.Player.class);
        for (var p : List.of(tank, teammate)) {
            when(p.getUniqueId()).thenReturn(UUID.randomUUID());
            when(p.isOnline()).thenReturn(true); when(p.isValid()).thenReturn(true);
            when(p.getLocation()).thenReturn(center);
        }
        when(boss.barManager.getViewers()).thenReturn(Set.of(tank, teammate));
        when(boss.domainManager.isInDomain(any())).thenReturn(true);
        var focused = WorldBoss.class.getDeclaredField("pressureTarget"); focused.setAccessible(true);
        focused.set(boss, tank.getUniqueId());
        boss.updateAggroAndThreat(12);
        verify((org.bukkit.entity.Mob) boss.entity).setTarget(teammate);
    }
    private AethelgardBoss rootBoss(double budget) throws Exception {
        var config = new YamlConfiguration(); config.set("balance.maximum-healing-fraction", budget);
        var boss = spy(new AethelgardBoss(null, config, null));
        boss.maxHealth = 1000; boss.currentHealth = 500; boss.active = true;
        boss.entity = mock(LivingEntity.class);
        var world = mock(World.class);
        when(boss.entity.isValid()).thenReturn(true); when(boss.entity.getWorld()).thenReturn(world);
        when(boss.entity.getLocation()).thenAnswer(call -> new Location(world, 0, 64, 0));
        boss.barManager = mock(BossBarManager.class);
        when(boss.barManager.getViewers()).thenReturn(Set.of());
        doReturn(false).when(boss).abilityReady(anyLong(), anyString(), anyInt());
        LivingEntity root = mock(LivingEntity.class); when(root.isValid()).thenReturn(true);
        when(root.getLocation()).thenAnswer(call -> new Location(world, 4, 64, 0));
        var field = AethelgardBoss.class.getDeclaredField("healingRoots"); field.setAccessible(true);
        ((Set<LivingEntity>) field.get(boss)).add(root);
        return boss;
    }
    @Test void survivingRootsKeepHealingAfterTwentySecondsAndFireSuppressesBothSources() throws Exception {
        var boss = rootBoss(1);
        boss.onCombatTick(2); assertEquals(535, boss.currentHealth);
        boss.onCombatTick(22); assertEquals(570, boss.currentHealth);
        when(boss.entity.getFireTicks()).thenReturn(100);
        boss.onCombatTick(24); assertEquals(570, boss.currentHealth);
        when(boss.entity.getFireTicks()).thenReturn(0);
        boss.onCombatTick(26); assertEquals(605, boss.currentHealth);
    }
    @Test void exhaustedReserveStopsHealingAndWithersRoots() throws Exception {
        var boss = rootBoss(.01);
        boss.onCombatTick(2); assertEquals(510, boss.currentHealth);
        boss.onCombatTick(4); assertEquals(510, boss.currentHealth);
        var field = AethelgardBoss.class.getDeclaredField("healingRoots"); field.setAccessible(true);
        assertTrue(((Set<?>)field.get(boss)).isEmpty());
    }
    @Test void destroyingRootsStopsBothRootAndPassiveHealing() throws Exception {
        var boss = rootBoss(1);
        var field = AethelgardBoss.class.getDeclaredField("healingRoots"); field.setAccessible(true);
        ((Set<?>)field.get(boss)).clear();
        boss.onCombatTick(6); assertEquals(500, boss.currentHealth);
        assertFalse(AethelgardBoss.canChannelRoots(3, 2, false, 500));
    }
    @Test void delayedAttackCleansItselfUpAndHonorsInterruptState() {
        var boss = spy(new ValeriusBoss(null, new YamlConfiguration(), null)); boss.active = true;
        var scheduler = mock(BukkitScheduler.class); var task = mock(BukkitTask.class);
        List<Runnable> callbacks = new ArrayList<>();
        when(scheduler.runTaskLater(isNull(), any(Runnable.class), anyLong())).thenAnswer(call -> {
            callbacks.add(call.getArgument(1)); return task;
        });
        try (var bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getScheduler).thenReturn(scheduler);
            Runnable attack = mock(Runnable.class);
            boss.runAbilityLater(attack, 10); assertEquals(1, boss.abilityTasks.size());
            callbacks.getFirst().run(); verify(attack).run(); assertTrue(boss.abilityTasks.isEmpty());
            boss.runAbilityLater(attack, 10);
            doReturn(false).when(boss).abilityCombatAllowed();
            callbacks.get(1).run(); verifyNoMoreInteractions(attack); assertTrue(boss.abilityTasks.isEmpty());
        }
    }
}
