package net.rocky.coresuite.module.worldbosses.combat;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BossDamageTrackerTest {
    @Test void repeatedTrivialHitsDoNotManufactureSupportCredit() {
        BossDamageTracker tracker = new BossDamageTracker(1000);
        Player player = player("spam");
        for (int i = 0; i < 10; i++) tracker.addDamage(player, .1);
        assertFalse(tracker.qualifies(player.getUniqueId(), 20, 3));
        assertEquals(0, tracker.getContribution(player.getUniqueId()));
    }
    private static Player player(String name) {
        Player player = mock(Player.class);
        when(player.getUniqueId()).thenReturn(UUID.nameUUIDFromBytes(name.getBytes()));
        when(player.getName()).thenReturn(name);
        return player;
    }

    @Test void oneTouchDoesNotQualifyButMeaningfulDamageDoes() {
        BossDamageTracker tracker = new BossDamageTracker(1000);
        Player touch = player("touch"), fighter = player("fighter");
        tracker.addDamage(touch, 1);
        tracker.addDamage(fighter, 20);
        assertFalse(tracker.qualifies(touch.getUniqueId(), 20, 3));
        assertTrue(tracker.qualifies(fighter.getUniqueId(), 20, 3));
        assertEquals(1, tracker.getQualifiedParticipants(20, 3).size());
    }

    @Test void usefulSupportQualifiesWithoutManufacturingDamage() {
        BossDamageTracker tracker = new BossDamageTracker(1000);
        Player support = player("support");
        tracker.recordContribution(support, 2);
        assertFalse(tracker.qualifies(support.getUniqueId(), 20, 3));
        tracker.recordContribution(support, 1);
        assertTrue(tracker.qualifies(support.getUniqueId(), 20, 3));
        assertEquals(0, tracker.getDamage(support.getUniqueId()));
    }

    @Test void contributionPointsAreBoundedPerEventAndClearWithEncounter() {
        BossDamageTracker tracker = new BossDamageTracker(1000);
        Player player = player("bounded");
        tracker.recordContribution(player, 99);
        assertEquals(20, tracker.getContribution(player.getUniqueId()));
        tracker.clear();
        assertEquals(0, tracker.getContribution(player.getUniqueId()));
    }
}
