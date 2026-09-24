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
    @Test void freezeRestrainsMovementButExpiresAndDoesNotBlockTeleports() throws Exception {
        var boss = new BorealisBoss(null, new YamlConfiguration(), null);
        boss.active = true;
        boss.barManager = mock(BossBarManager.class);
        boss.domainManager = mock(net.rocky.coresuite.module.worldbosses.combat.BossDomainManager.class);
        var player = mock(org.bukkit.entity.Player.class);
        var id = java.util.UUID.randomUUID(); when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        when(player.getGameMode()).thenReturn(org.bukkit.GameMode.SURVIVAL);
        when(boss.barManager.getViewers()).thenReturn(Set.of(player));
        when(boss.domainManager.isInDomain(any())).thenReturn(true);
        var field = BorealisBoss.class.getDeclaredField("frozenUntil"); field.setAccessible(true);
        @SuppressWarnings("unchecked") var frozen = (java.util.Map<java.util.UUID, Long>)field.get(boss);
        frozen.put(id, System.currentTimeMillis() + 2000);
        var world = mock(org.bukkit.World.class);
        var from = new org.bukkit.Location(world, 0, 64, 0);
        var to = new org.bukkit.Location(world, 2, 65, 2, 90, 30);
        var event = new org.bukkit.event.player.PlayerMoveEvent(player, from, to);
        try (var auth = mockStatic(net.rocky.coresuite.common.integration.AuthenticationGate.class);
             var movement = mockStatic(net.rocky.coresuite.module.worldbosses.combat.BossMovement.class)) {
            auth.when(() -> net.rocky.coresuite.common.integration.AuthenticationGate.isAuthenticated(player)).thenReturn(true);
            movement.when(() -> net.rocky.coresuite.module.worldbosses.combat.BossMovement.clear(any(org.bukkit.Location.class))).thenReturn(true);
            boss.restrainFrozenMovement(event);
            assertEquals(0, event.getTo().getX()); assertEquals(64, event.getTo().getY());
            assertEquals(90, event.getTo().getYaw());
            frozen.put(id, 0L);
            event.setTo(to); boss.restrainFrozenMovement(event); assertEquals(2, event.getTo().getX());
            frozen.put(id, System.currentTimeMillis() + 2000);
            var teleport = new org.bukkit.event.player.PlayerTeleportEvent(player, from, to);
            boss.restrainFrozenMovement(teleport);
            assertEquals(to, teleport.getTo()); assertFalse(frozen.containsKey(id));
        }
    }
}
