package net.rocky.coresuite.module.customfishing;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnglerIntroOfferTest {
    @Test void repeatInteractionsAndReconnectsKeepTheSameOffer() {
        UUID player = UUID.fromString("f91fb715-17c1-4564-8df0-546abf9b1fee");
        var first = AnglerIntroOffer.create(player, 0, 0, 6);
        for (int i = 0; i < 100; i++) assertEquals(first, AnglerIntroOffer.create(player, 0, 0, 6));
        assertEquals(3, new HashSet<>(first.paths()).size());
        assertTrue(first.paths().stream().allMatch(path -> path >= 1 && path <= 10));
        assertNotEquals(first, AnglerIntroOffer.create(player, 1, 0, 6));
    }
}
