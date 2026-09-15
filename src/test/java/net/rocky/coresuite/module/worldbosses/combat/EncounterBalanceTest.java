package net.rocky.coresuite.module.worldbosses.combat;
import org.junit.jupiter.api.Test;
import org.bukkit.entity.Player;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class EncounterBalanceTest {
    @Test void scalesForActualGroupSize() {
        assertEquals(720, EncounterBalance.health(1200, 1, .6, .5));
        assertEquals(1080, EncounterBalance.health(1200, 2, .6, .5));
        assertEquals(3960, EncounterBalance.health(1200, 10, .6, .5));
    }
    @Test void joiningAndLeavingPreservesProgress() {
        double increased = EncounterBalance.rescale(360, 720, 3960);
        assertEquals(1980, increased);
        assertEquals(360, EncounterBalance.rescale(increased, 3960, 720));
        assertEquals(0, EncounterBalance.rescale(0, 720, 3960));
    }
    @Test void healingCannotResetAnEncounter() {
        assertEquals(150, EncounterBalance.healing(100, 1000, 9999, 0, .15));
        assertEquals(0, EncounterBalance.healing(100, 1000, 9999, .15, .15));
        assertEquals(0, EncounterBalance.healing(100, 1000, Double.NaN, 0, .15));
        assertEquals(0, EncounterBalance.healing(0, 1000, 100, 0, .15));
        assertEquals(10, EncounterBalance.healing(990, 1000, 100, 0, .15));
    }
    @Test void invalidDamageDoesNotPoisonRankingAndTanksParticipate() {
        BossDamageTracker tracker = new BossDamageTracker(1000);
        Player fighter = mock(Player.class), tank = mock(Player.class);
        when(fighter.getUniqueId()).thenReturn(UUID.randomUUID()); when(fighter.getName()).thenReturn("Fighter");
        when(tank.getUniqueId()).thenReturn(UUID.randomUUID()); when(tank.getName()).thenReturn("Tank");
        tracker.addDamage(fighter, Double.NaN); tracker.addDamage(fighter, Double.POSITIVE_INFINITY);
        assertTrue(tracker.getSortedDamagers().isEmpty());
        tracker.addDamage(fighter, 20); tracker.recordParticipation(tank);
        assertEquals(2, tracker.getSortedDamagers().size());
        assertEquals(20, tracker.getTotalDamage());
        assertEquals(fighter.getUniqueId(), tracker.getMvp().uuid());
        assertTrue(tracker.wasRecentlyActive(tank.getUniqueId(), System.currentTimeMillis()-1000));
        tracker.clear(); assertTrue(tracker.getSortedDamagers().isEmpty());
    }
}
