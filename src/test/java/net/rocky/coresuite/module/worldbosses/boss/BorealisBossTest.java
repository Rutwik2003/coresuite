package net.rocky.coresuite.module.worldbosses.boss;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitTask;
import net.rocky.coresuite.module.worldbosses.combat.BossBarManager;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BorealisBossTest {
    @Test void thermalShockIsReliableAndCannotBeExtended() throws Exception {
        var boss = new BorealisBoss(null, new YamlConfiguration(), null);
        boss.active = true; boss.isAwakened = true;
        boss.barManager = mock(BossBarManager.class);
        when(boss.barManager.getViewers()).thenReturn(Set.of());
        var task = mock(BukkitTask.class); boss.abilityTasks.add(task);
        var sword = mock(org.bukkit.inventory.ItemStack.class);
        when(sword.getType()).thenReturn(org.bukkit.Material.DIAMOND_SWORD);
        var shovel = mock(org.bukkit.inventory.ItemStack.class);
        when(shovel.getType()).thenReturn(org.bukkit.Material.DIAMOND_SHOVEL);
        assertEquals(5, boss.calculateIncomingDamage(null, 10, sword));
        assertTrue(boss.applyThermalShock()); verify(task).cancel();
        assertEquals(10, boss.calculateIncomingDamage(null, 10, sword));
        assertEquals(25, boss.calculateIncomingDamage(null, 10, shovel));
        assertFalse(boss.applyThermalShock());
        var exposed = BorealisBoss.class.getDeclaredField("exposedUntil"); exposed.setAccessible(true); exposed.setLong(boss, 0);
        assertEquals(5, boss.calculateIncomingDamage(null, 10, sword));
        assertFalse(boss.applyThermalShock(), "Recovery continues after the six-second window");
    }

    @Test void novaHitsInsideButRespectsDodgeSpectatorAndDomain() throws Exception {
        var config = new YamlConfiguration(); config.set("borealis.weakness.fire-resist-chance", 0);
        var boss = new BorealisBoss(null, config, null);
        boss.entity = mock(LivingEntity.class);
        boss.active = true;
        boss.barManager = mock(BossBarManager.class);
        boss.domainManager = mock(net.rocky.coresuite.module.worldbosses.combat.BossDomainManager.class);
        var world = mock(org.bukkit.World.class);
        var player = mock(org.bukkit.entity.Player.class);
        when(player.isOnline()).thenReturn(true);
        when(player.getGameMode()).thenReturn(org.bukkit.GameMode.SURVIVAL);
        when(player.getWorld()).thenReturn(world);
        when(player.getLocation()).thenReturn(new org.bukkit.Location(world, 1, 64, 0));
        when(player.hasLineOfSight(boss.entity)).thenReturn(true);
        when(boss.barManager.getViewers()).thenReturn(Set.of(player));
        when(boss.domainManager.isInDomain(any())).thenReturn(true);
        var strike = BorealisBoss.class.getDeclaredMethod("strike", org.bukkit.Location.class, double.class, double.class);
        strike.setAccessible(true);
        var origin = new org.bukkit.Location(world, 0, 64, 0);
        try (var auth = mockStatic(net.rocky.coresuite.common.integration.AuthenticationGate.class)) {
            auth.when(() -> net.rocky.coresuite.common.integration.AuthenticationGate.isAuthenticated(player)).thenReturn(true);
            // Model a lethal hit; no potion effect should be applied after death.
            doAnswer(invocation -> { when(player.isDead()).thenReturn(true); return null; })
                .when(player).damage(22, boss.entity);
            strike.invoke(boss, origin, 7d, 22d);
            verify(player).damage(22, boss.entity);
            clearInvocations(player);
            when(player.isDead()).thenReturn(false);
            when(player.getLocation()).thenReturn(new org.bukkit.Location(world, 8, 64, 0));
            strike.invoke(boss, origin, 7d, 22d);
            when(player.getLocation()).thenReturn(origin);
            when(player.getGameMode()).thenReturn(org.bukkit.GameMode.SPECTATOR);
            strike.invoke(boss, origin, 7d, 22d);
            when(player.getGameMode()).thenReturn(org.bukkit.GameMode.SURVIVAL);
            when(boss.domainManager.isInDomain(any())).thenReturn(false);
            strike.invoke(boss, origin, 7d, 22d);
            verify(player, never()).damage(anyDouble(), any(org.bukkit.entity.Entity.class));
        }
    }
    @Test void nightVisionCannotRerollDuringDeflectionOrExtendStun() {
        var config = new YamlConfiguration(); config.set("xolthos.weakness.night-vision-resist-chance", 100);
        var boss = new XolthosBoss(null, config, null);
        assertFalse(boss.applyStun(12)); assertFalse(boss.isStunned());
        config.set("xolthos.weakness.night-vision-resist-chance", 0);
        assertFalse(boss.applyStun(12));
        var fresh = new XolthosBoss(null, config, null);
        assertTrue(fresh.applyStun(12)); assertTrue(fresh.isStunned());
        assertFalse(fresh.applyStun(12));
    }
    @Test void phasesChooseBowThenMixedThenAxeWithCappedMinions() {
        assertTrue(BorealisBoss.usesBow(1, 0));
        assertTrue(BorealisBoss.usesBow(1, 12));
        assertTrue(BorealisBoss.usesBow(2, 0));
        assertFalse(BorealisBoss.usesBow(2, 12));
        assertTrue(BorealisBoss.usesBow(2, 24));
        assertFalse(BorealisBoss.usesBow(3, 0));
        assertEquals(0, BorealisBoss.minionCap(1));
        assertEquals(4, BorealisBoss.minionCap(2));
        assertEquals(2, BorealisBoss.minionCap(3));
    }
    @Test void everyShovelGetsBonusOnlyInsideThermalWindow() throws Exception {
        var boss = new BorealisBoss(null, new YamlConfiguration(), null);
        var exposed = BorealisBoss.class.getDeclaredField("exposedUntil"); exposed.setAccessible(true);
        for (var material : org.bukkit.Material.values()) {
            if (!material.name().endsWith("_SHOVEL")) continue;
            var tool = mock(org.bukkit.inventory.ItemStack.class); when(tool.getType()).thenReturn(material);
            exposed.setLong(boss, 0);
            assertEquals(8, boss.calculateIncomingDamage(null,8,tool),material.name());
            exposed.setLong(boss, System.currentTimeMillis()+60000);
            assertEquals(20, boss.calculateIncomingDamage(null,8,tool),material.name());
            exposed.setLong(boss, System.currentTimeMillis()-1);
            assertEquals(8, boss.calculateIncomingDamage(null,8,tool),material.name());
        }
    }

    @Test void domainFrostIsClearedByOneLeatherPieceAndOnLeavingDomain() throws Exception {
        var core = mock(net.rocky.coresuite.CoreSuitePlugin.class);
        when(core.getName()).thenReturn("CoreSuite");
        var boss = spy(new BorealisBoss(core,new YamlConfiguration(),null));
        boss.active = true;
        boss.barManager = mock(BossBarManager.class);
        boss.domainManager = mock(net.rocky.coresuite.module.worldbosses.combat.BossDomainManager.class);
        var player = mock(org.bukkit.entity.Player.class);
        doReturn(false).when(boss).hasFireResistance(player);
        var inventory = mock(org.bukkit.inventory.PlayerInventory.class);
        var world = mock(org.bukkit.World.class);
        var block = mock(org.bukkit.block.Block.class);
        var uuid = java.util.UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(uuid);
        when(player.isOnline()).thenReturn(true);
        when(player.getGameMode()).thenReturn(org.bukkit.GameMode.SURVIVAL);
        when(player.getInventory()).thenReturn(inventory);
        when(inventory.getArmorContents()).thenReturn(new org.bukkit.inventory.ItemStack[4]);
        when(player.getLocation()).thenReturn(new org.bukkit.Location(world,0,64,0));
        when(world.getBlockAt(any(org.bukkit.Location.class))).thenReturn(block);
        when(block.getType()).thenReturn(org.bukkit.Material.AIR);
        when(boss.barManager.getViewers()).thenReturn(Set.of(player));
        when(boss.domainManager.isInDomain(any())).thenReturn(true);
        when(player.getMaxFreezeTicks()).thenReturn(140);
        var ticks = new java.util.concurrent.atomic.AtomicInteger();
        when(player.getFreezeTicks()).thenAnswer(call -> ticks.get());
        doAnswer(call -> { ticks.set(call.getArgument(0)); return null; }).when(player).setFreezeTicks(anyInt());
        var update = BorealisBoss.class.getDeclaredMethod("updateDomainFrost"); update.setAccessible(true);
        try (var auth = mockStatic(net.rocky.coresuite.common.integration.AuthenticationGate.class);
             var bukkit = mockStatic(org.bukkit.Bukkit.class)) {
            auth.when(() -> net.rocky.coresuite.common.integration.AuthenticationGate.isAuthenticated(player)).thenReturn(true);
            bukkit.when(() -> org.bukkit.Bukkit.getPlayer(uuid)).thenReturn(player);
            update.invoke(boss); assertEquals(49,ticks.get());
            var leather = mock(org.bukkit.inventory.ItemStack.class);
            when(leather.getType()).thenReturn(org.bukkit.Material.LEATHER_HELMET);
            when(inventory.getArmorContents()).thenReturn(new org.bukkit.inventory.ItemStack[]{leather,null,null,null});
            update.invoke(boss); assertEquals(0,ticks.get());
            when(inventory.getArmorContents()).thenReturn(new org.bukkit.inventory.ItemStack[4]);
            update.invoke(boss); assertEquals(49,ticks.get());
            var boots = mock(org.bukkit.inventory.ItemStack.class);
            var meta = mock(org.bukkit.inventory.meta.ItemMeta.class);
            var pdc = mock(org.bukkit.persistence.PersistentDataContainer.class);
            when(boots.hasItemMeta()).thenReturn(true); when(boots.getItemMeta()).thenReturn(meta);
            when(meta.getPersistentDataContainer()).thenReturn(pdc);
            when(pdc.get(new org.bukkit.NamespacedKey(core,"boss_weapon"),org.bukkit.persistence.PersistentDataType.STRING)).thenReturn("frostbound_striders");
            when(inventory.getBoots()).thenReturn(boots);
            update.invoke(boss); assertEquals(0,ticks.get());
            when(inventory.getBoots()).thenReturn(null);
            update.invoke(boss); assertEquals(49,ticks.get());
            doReturn(true).when(boss).hasFireResistance(player);
            boss.grantFrostRespite(player);
            update.invoke(boss); assertEquals(0,ticks.get());
            var warmth = BorealisBoss.class.getDeclaredField("warmthUntil"); warmth.setAccessible(true);
            @SuppressWarnings("unchecked") var windows = (java.util.Map<java.util.UUID,Long>) warmth.get(boss);
            long deadline = windows.get(uuid);
            boss.grantFrostRespite(player);
            assertEquals(deadline,windows.get(uuid), "Reapplication cannot extend an active window");
            windows.put(uuid,System.currentTimeMillis()-1);
            update.invoke(boss); assertEquals(49,ticks.get(), "Ongoing eight-minute effect cannot renew immunity");
            when(boss.domainManager.isInDomain(any())).thenReturn(false);
            update.invoke(boss);
            when(boss.domainManager.isInDomain(any())).thenReturn(true);
            update.invoke(boss); assertEquals(49,ticks.get(), "Reentry cannot renew immunity");
            boss.grantFrostRespite(player);
            update.invoke(boss); assertEquals(0,ticks.get(), "Fresh potion can grant a new window");
            doReturn(false).when(boss).hasFireResistance(player);
            update.invoke(boss); assertEquals(49,ticks.get(), "Removing the effect ends protection");
            when(boss.domainManager.isInDomain(any())).thenReturn(false);
            update.invoke(boss); assertEquals(0,ticks.get());
            verify(player,never()).setVelocity(any());
        }
    }


    @Test void healingCannotUndoPhaseProgressOrExceedTheEncounterBudget() {
        var boss = new BorealisBoss(null, new YamlConfiguration(), null);
        boss.maxHealth = 1000;
        boss.currentHealth = 590;
        boss.currentPhase = new BossPhase(2, 20, "Whiteout", net.kyori.adventure.bossbar.BossBar.Color.WHITE, "WHITEOUT");
        assertEquals(10, boss.heal(240), .0001);
        assertEquals(600, boss.currentHealth, .0001);
        assertEquals(0, boss.heal(240), .0001);
        boss.currentHealth = 400;
        assertEquals(140, boss.heal(10000), .0001);
        assertEquals(0, boss.getRemainingHealing(), .0001);
        boss.currentHealth = 100;
        boss.currentPhase = new BossPhase(3, 0, "Absolute Zero", net.kyori.adventure.bossbar.BossBar.Color.RED, "ABSOLUTE_ZERO");
        assertEquals(0, boss.heal(10000), .0001); // phase change never resets the reserve
    }

    @Test void phaseThreeHealingStopsAtTwentyPercentAndRejectsInvalidAmounts() {
        var boss = new BorealisBoss(null, new YamlConfiguration(), null);
        boss.maxHealth = 1000; boss.currentHealth = 190;
        boss.currentPhase = new BossPhase(3, 0, "Absolute Zero", net.kyori.adventure.bossbar.BossBar.Color.RED, "ABSOLUTE_ZERO");
        assertEquals(10, boss.heal(500), .0001);
        assertEquals(200, boss.currentHealth, .0001);
        assertEquals(0, boss.heal(Double.NaN));
        assertEquals(0, boss.heal(Double.POSITIVE_INFINITY));
    }
}
