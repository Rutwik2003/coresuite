package net.rocky.coresuite.module.worldbosses.boss;

import net.kyori.adventure.bossbar.BossBar;
import net.rocky.coresuite.module.worldbosses.WorldBossesModule;
import net.rocky.coresuite.module.worldbosses.combat.BossBarManager;
import net.rocky.coresuite.module.worldbosses.combat.BossDomainManager;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.junit.jupiter.api.Test;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class XolthosNecrosisTest {
    @Test void necrosisOnlyAppliesToPresentFrenzyFightersAndRespectsCounterAndConfig() {
        var config = new YamlConfiguration();
        var boss = spy(new XolthosBoss(null, config, null));
        var player = mock(Player.class);
        boss.active = true; boss.isAwakened = true;
        boss.barManager = mock(BossBarManager.class);
        boss.domainManager = mock(BossDomainManager.class);
        when(boss.barManager.getViewers()).thenReturn(Set.of(player));
        when(boss.domainManager.isInDomain(any())).thenReturn(true);
        boss.currentPhase = new BossPhase(3, 0, "Frenzy", BossBar.Color.RED, "FRENZY");
        assertTrue(boss.suppressesNaturalHealing(player));
        assertFalse(boss.suppressesNaturalHealing(mock(Player.class)));
        when(boss.domainManager.isInDomain(any())).thenReturn(false);
        assertFalse(boss.suppressesNaturalHealing(player));
        when(boss.domainManager.isInDomain(any())).thenReturn(true);
        doReturn(true).when(boss).isStunned();
        assertFalse(boss.suppressesNaturalHealing(player));
        doReturn(false).when(boss).isStunned();
        config.set("xolthos.phases.phase-3.necrosis", false);
        assertFalse(boss.suppressesNaturalHealing(player));
        config.set("xolthos.phases.phase-3.necrosis", true);
        boss.currentPhase = new BossPhase(2, 30, "Brood", BossBar.Color.YELLOW, "THE_BROOD");
        assertFalse(boss.suppressesNaturalHealing(player));
        boss.currentPhase = new BossPhase(3, 0, "Frenzy", BossBar.Color.RED, "FRENZY");
        boss.active = false;
        assertFalse(boss.suppressesNaturalHealing(player));
    }

    @Test void potionAndGoldenAppleHealingAreNotCancelled() {
        var module = mock(WorldBossesModule.class, CALLS_REAL_METHODS);
        for (var reason : EntityRegainHealthEvent.RegainReason.values()) {
            if (reason == EntityRegainHealthEvent.RegainReason.SATIATED) continue;
            var event = new EntityRegainHealthEvent(mock(Player.class), 4, reason);
            module.onNaturalHealing(event);
            assertFalse(event.isCancelled(), reason.name());
        }
    }

    @Test void listenerBlocksOnlyEligibleFightersNaturalHealing() throws Exception {
        var module = mock(WorldBossesModule.class, CALLS_REAL_METHODS);
        var boss = mock(XolthosBoss.class);
        var player = mock(Player.class);
        var field = WorldBossesModule.class.getDeclaredField("activeBoss");
        field.setAccessible(true); field.set(module, boss);
        when(boss.suppressesNaturalHealing(player)).thenReturn(true);
        try (var eligibility = mockStatic(WorldBoss.class)) {
            eligibility.when(() -> WorldBoss.eligibleFighter(player)).thenReturn(true);
            var event = new EntityRegainHealthEvent(player, 1, EntityRegainHealthEvent.RegainReason.SATIATED);
            module.onNaturalHealing(event);
            assertTrue(event.isCancelled());
            eligibility.when(() -> WorldBoss.eligibleFighter(player)).thenReturn(false);
            var spectator = new EntityRegainHealthEvent(player, 1, EntityRegainHealthEvent.RegainReason.SATIATED);
            module.onNaturalHealing(spectator);
            assertFalse(spectator.isCancelled());
        }
    }
}
