package net.rocky.coresuite.module.worldbosses.portal;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;

class BossPortalSoundTest {
    @Test void commandEntryIsLimitedToFiveBlocksInTheSameWorld() {
        org.bukkit.World world = mock(org.bukkit.World.class);
        Location entrance = new Location(world, 0, 64, 0);
        org.junit.jupiter.api.Assertions.assertTrue(BossPortalManager.withinEntranceRange(new Location(world, 3, 64, 4), entrance));
        org.junit.jupiter.api.Assertions.assertFalse(BossPortalManager.withinEntranceRange(new Location(world, 5.01, 64, 0), entrance));
        org.junit.jupiter.api.Assertions.assertFalse(BossPortalManager.withinEntranceRange(new Location(world, 0, 70, 0), entrance));
        org.junit.jupiter.api.Assertions.assertFalse(BossPortalManager.withinEntranceRange(new Location(mock(org.bukkit.World.class), 0, 64, 0), entrance));
    }

    @Test void nearbyRiftUsesBeaconAndChimeInsteadOfPortalWhoosh() {
        Player player = mock(Player.class);
        Location location = new Location(null, 0, 64, 0);
        when(player.getLocation()).thenReturn(location);
        BossPortalManager.playRiftSound(player, "RIFT_AMBIENT", 1, 1);
        verify(player).playSound(location, Sound.BLOCK_BEACON_AMBIENT, .45f, .65f);
        verify(player).playSound(location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, .7f, .75f);
        verify(player, never()).playSound(any(Location.class), eq(Sound.BLOCK_PORTAL_AMBIENT), anyFloat(), anyFloat());
    }
    @Test void customPackKeysAndDisabledSoundAreSupported() {
        Player player = mock(Player.class);
        Location location = new Location(null, 0, 64, 0);
        when(player.getLocation()).thenReturn(location);
        BossPortalManager.playRiftSound(player, "", 1, 1);
        verifyNoInteractions(player);
        BossPortalManager.playRiftSound(player, "coresuite:rift.ambient", 1, 1);
        verify(player).playSound(location, "coresuite:rift.ambient", SoundCategory.BLOCKS, 1, 1);
    }
}
