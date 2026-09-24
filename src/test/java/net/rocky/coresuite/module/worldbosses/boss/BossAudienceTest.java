package net.rocky.coresuite.module.worldbosses.boss;

import net.rocky.coresuite.common.integration.AuthenticationGate;
import net.rocky.coresuite.module.worldbosses.combat.*;
import org.bukkit.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class BossAudienceTest {
    @Test void arenaCrossingAndSpectatorBufferDoNotRestartMusicOrCreateCombatTargets() {
        World world = mock(World.class);
        Player player = mock(Player.class);
        UUID id = UUID.randomUUID();
        when(player.getUniqueId()).thenReturn(id);
        when(player.isOnline()).thenReturn(true);
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        when(world.getPlayers()).thenReturn(List.of(player));
        WorldBoss boss = spy(new ValeriusBoss(null, new YamlConfiguration(), null));
        boss.entity = mock(LivingEntity.class);
        when(boss.entity.isValid()).thenReturn(true);
        // Boss stands on the north edge; player starts at the south edge.
        when(boss.entity.getLocation()).thenReturn(new Location(world, 0, 64, -34));
        boss.domainManager = mock(BossDomainManager.class);
        when(boss.domainManager.getCenter()).thenReturn(new Location(world, 0, 64, 0));
        when(boss.domainManager.getDomainRadius()).thenReturn(35.0);
        when(boss.domainManager.isInDomain(any())).thenAnswer(call ->
                ((Location)call.getArgument(0)).distanceSquared(new Location(world, 0, 64, 0)) <= 1225);
        boss.barManager = mock(BossBarManager.class);
        doNothing().when(boss).applyAtmosphere(any());
        doNothing().when(boss).clearAtmosphere(any());
        doNothing().when(boss).startBgmForPlayer(any());
        doNothing().when(boss).stopAllBgm(any());
        try (var auth = mockStatic(AuthenticationGate.class); var bukkit = mockStatic(Bukkit.class)) {
            auth.when(() -> AuthenticationGate.isAuthenticated(player)).thenReturn(true);
            bukkit.when(() -> Bukkit.getPlayer(id)).thenReturn(player);
            when(player.getLocation()).thenReturn(new Location(world, 0, 64, 34));
            boss.updateBossBarViewers();
            verify(boss.barManager).addViewer(player);
            when(player.getLocation()).thenReturn(new Location(world, 0, 64, -34));
            boss.updateBossBarViewers();
            when(player.getGameMode()).thenReturn(GameMode.SPECTATOR);
            when(player.getLocation()).thenReturn(new Location(world, 0, 64, 53));
            boss.updateBossBarViewers();
            verify(boss.barManager).removeViewer(player);
            assertTrue(boss.domainViewers.contains(id));
            verify(boss, times(1)).startBgmForPlayer(player);
            verify(boss, never()).clearAtmosphere(player);
            when(player.getLocation()).thenReturn(new Location(world, 0, 64, 56));
            boss.updateBossBarViewers();
            assertFalse(boss.domainViewers.contains(id));
            verify(boss).clearAtmosphere(player);
            verify(boss).stopAllBgm(player);
        }
    }
}
